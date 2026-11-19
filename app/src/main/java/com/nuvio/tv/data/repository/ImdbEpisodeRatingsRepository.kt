package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.nuvio.tv.data.remote.api.ImdbTapframeApi
import com.nuvio.tv.data.remote.api.SeriesGraphApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImdbEpisodeRatingsRepository @Inject constructor(
    private val imdbTapframeApi: ImdbTapframeApi,
    private val seriesGraphApi: SeriesGraphApi,
    @ApplicationContext private val context: Context
) {
    private data class CacheEntry(
        val episodes: List<ExternalEpisodeRating>,
        val expiresAtMs: Long
    )

    private val tag = "ImdbEpisodeRatingsRepo"
    private val cacheTtlMs = 30L * 60L * 1000L
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<List<ExternalEpisodeRating>>>()
    private val inFlightMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // [fork] round 50 imdb_ratings_lean: the primary host is skipped for a day once it fails to answer at all
    // (DNS gone, refused, timed out), instead of failing once per show before the fallback answers.
    @Volatile private var primaryDownUntilMs = 0L
    private fun primaryUsable(): Boolean =
        !com.nuvio.tv.NuvioCFeatures.IMDB_RATINGS_LEAN || System.currentTimeMillis() >= primaryDownUntilMs
    private fun primaryFailedHard(e: Exception) {
        if (!com.nuvio.tv.NuvioCFeatures.IMDB_RATINGS_LEAN) return
        if (e is java.io.IOException) primaryDownUntilMs = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
    }

    // [fork] round 50 imdb_ratings_lean: a show's ratings are kept on disk for 24 h (nothing is written for an empty
    // answer), so a cold start reads Continue Watching's ratings from disk instead of downloading every show again.
    private fun diskFile(cacheKey: String): File =
        File(File(context.cacheDir, "nuvioc_episode_ratings"), cacheKey.replace(Regex("[^A-Za-z0-9]"), "_") + ".txt")

    private fun readDisk(cacheKey: String): List<ExternalEpisodeRating>? {
        if (!com.nuvio.tv.NuvioCFeatures.IMDB_RATINGS_LEAN) return null
        return runCatching {
            val file = diskFile(cacheKey)
            if (!file.exists()) return null
            val lines = file.readLines()
            val savedAt = lines.firstOrNull()?.toLongOrNull() ?: return null
            if (System.currentTimeMillis() - savedAt > 24L * 60L * 60L * 1000L) return null
            lines.drop(1).mapNotNull { line ->
                val p = line.split('|')
                if (p.size < 4) return@mapNotNull null
                ExternalEpisodeRating(
                    season = p[0].toIntOrNull() ?: return@mapNotNull null,
                    episode = p[1].toIntOrNull() ?: return@mapNotNull null,
                    airDate = p[2].takeIf { it.isNotEmpty() },
                    rating = p[3].toDoubleOrNull()
                )
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun writeDisk(cacheKey: String, episodes: List<ExternalEpisodeRating>) {
        if (!com.nuvio.tv.NuvioCFeatures.IMDB_RATINGS_LEAN || episodes.none { it.rating != null }) return
        runCatching {
            val file = diskFile(cacheKey)
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                append(System.currentTimeMillis()).append('\n')
                episodes.forEach { append(it.season).append('|').append(it.episode).append('|').append(it.airDate.orEmpty()).append('|').append(it.rating ?: "").append('\n') }
            })
        }
    }

    internal suspend fun getEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
        addonEpisodes: List<AddonEpisodeAirDate>
    ): Map<Pair<Int, Int>, Double> {
        return alignEpisodeRatings(addonEpisodes, getExternalEpisodes(imdbId, tmdbId))
    }

    private suspend fun getExternalEpisodes(
        imdbId: String?,
        tmdbId: Int?
    ): List<ExternalEpisodeRating> {
        val normalizedImdbId = imdbId
            ?.trim()
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }
            ?.substringBefore(':')
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedImdbId == null && normalizedTmdbId == null) return emptyList()

        val cacheKey = when {
            !normalizedImdbId.isNullOrBlank() -> "imdb:$normalizedImdbId"
            else -> "tmdb:$normalizedTmdbId"
        }

        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { cached ->
            if (cached.expiresAtMs > now) return cached.episodes
            cache.remove(cacheKey)
        }

        readDisk(cacheKey)?.let { fromDisk ->
            cache[cacheKey] = CacheEntry(episodes = fromDisk, expiresAtMs = now + cacheTtlMs)
            return fromDisk
        }

        val deferred = inFlightMutex.withLock {
            inFlight[cacheKey] ?: scope.async {
                try {
                    fetchEpisodeRatings(
                        imdbId = normalizedImdbId,
                        tmdbId = normalizedTmdbId
                    ).also { result ->
                        writeDisk(cacheKey, result)
                        cache[cacheKey] = CacheEntry(
                            episodes = result,
                            // [fork] round 33 player_episode_ratings_fix: an empty answer (a failed or timed-out call)
                            // is only kept 2 min, so one bad answer no longer hides a show's ratings for 30 min.
                            expiresAtMs = System.currentTimeMillis() +
                                if (result.isEmpty() && com.nuvio.tv.NuvioCFeatures.PLAYER_EPISODE_RATINGS_FIX) 2L * 60L * 1000L else cacheTtlMs
                        )
                    }
                } finally {
                    inFlightMutex.withLock {
                        inFlight.remove(cacheKey)
                    }
                }
            }.also { created ->
                inFlight[cacheKey] = created
            }
        }

        return deferred.await()
    }

    private suspend fun fetchEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?
    ): List<ExternalEpisodeRating> {
        if (!imdbId.isNullOrBlank() && primaryUsable()) {
            val primary = fetchFromImdbTapframe(imdbId)
            if (primary.any { it.rating != null }) return primary
            Log.w(tag, "Primary episode ratings empty for imdbId=$imdbId, trying fallback.")
        }

        if (tmdbId != null && tmdbId > 0) {
            return fetchFromSeriesGraph(tmdbId)
        }

        return emptyList()
    }

    private suspend fun fetchFromImdbTapframe(imdbId: String): List<ExternalEpisodeRating> {
        return try {
            val response = imdbTapframeApi.getSeasonRatings(imdbId)
            if (!response.isSuccessful) {
                Log.w(tag, "Failed primary season ratings for imdbId=$imdbId (${response.code()})")
                return emptyList()
            }
            toEpisodeRatings(response.body().orEmpty())
        } catch (e: Exception) {
            primaryFailedHard(e)
            Log.w(tag, "Error fetching primary season ratings for imdbId=$imdbId" + if (com.nuvio.tv.NuvioCFeatures.IMDB_RATINGS_LEAN) " (skipped for a day)" else "", if (com.nuvio.tv.NuvioCFeatures.IMDB_RATINGS_LEAN) null else e)
            emptyList()
        }
    }

    private suspend fun fetchFromSeriesGraph(tmdbId: Int): List<ExternalEpisodeRating> {
        return try {
            val response = seriesGraphApi.getSeasonRatings(tmdbId)
            if (!response.isSuccessful) {
                Log.w(tag, "Failed fallback season ratings for tmdbId=$tmdbId (${response.code()})")
                return emptyList()
            }
            toEpisodeRatings(response.body().orEmpty())
        } catch (e: Exception) {
            Log.w(tag, "Error fetching fallback season ratings for tmdbId=$tmdbId", e)
            emptyList()
        }
    }

    private fun toEpisodeRatings(payload: List<com.nuvio.tv.data.remote.api.SeriesGraphSeasonRatingsDto>): List<ExternalEpisodeRating> {
        return payload.flatMap { season ->
            season.episodes.orEmpty().mapNotNull { episode ->
                ExternalEpisodeRating(
                    season = episode.seasonNumber ?: return@mapNotNull null,
                    episode = episode.episodeNumber ?: return@mapNotNull null,
                    airDate = episode.airDate,
                    rating = episode.voteAverage
                )
            }
        }
    }
}
