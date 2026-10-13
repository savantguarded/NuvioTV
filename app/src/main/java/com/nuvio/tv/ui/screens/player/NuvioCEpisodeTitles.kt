package com.nuvio.tv.ui.screens.player

import android.util.Log
import android.util.LruCache
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.domain.model.Video
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// [fork] Nuvio C episode names on Next / auto-play (switch `next_ep_title`, 2026-10-12).
// Playing from Home shows the episode name the details page had (from your meta add-on). Next and
// auto-play took the name from the player's own meta lookup instead, which can come back without
// names (blank), so only "S1 E4" showed. The details page now leaves its episode names here (memory
// only, no network) and the player uses them first; its own lookup is the fallback, and a blank
// name never wins over a real one. Logcat tag NuvioCEpisodeTitle shows which name was used.

internal object NuvioCEpisodeTitles {
    private const val TAG = "NuvioCEpisodeTitle"
    private val byVideoId = LruCache<String, String>(2000)
    private val bySeriesEpisode = LruCache<String, String>(2000)

    private fun seriesKey(videoId: String, season: Int?, episode: Int?): String? {
        if (season == null || episode == null) return null
        // Round 7: the show part of "tt123:3:4" or "tmdb:123:3:4" (before, every "tmdb:" show
        // shared the prefix "tmdb", so one show could lend its names to another).
        val parts = videoId.split(':')
        val prefix = (if (parts.size >= 3 && parts.takeLast(2).all { p -> p.isNotEmpty() && p.all(Char::isDigit) })
            parts.dropLast(2).joinToString(":") else videoId.substringBefore(':'))
            .takeIf { it.isNotBlank() } ?: return null
        return "$prefix|$season|$episode"
    }

    /** Called by the details page with the episode list it shows. */
    fun remember(videos: List<Video>) {
        if (!NuvioCFeatures.NEXT_EP_TITLE) return
        for (v in videos) {
            val title = v.title.trim().takeIf { !isPlaceholder(it) } ?: continue // round 7b: never an "Episode 4" stand-in
            byVideoId.put(v.id, title)
            seriesKey(v.id, v.season, v.episode)?.let { bySeriesEpisode.put(it, title) }
        }
    }

    /** Round 7: a name found by the player's own lookup ([nuvioCFillEpisodeTitles]). */
    fun put(videoId: String, title: String) {
        val t = title.trim().takeIf { it.isNotEmpty() } ?: return
        byVideoId.put(videoId, t)
    }

    /** Known name for [video] without logging (next-episode card, current episode). */
    fun known(video: Video?): String? {
        if (!NuvioCFeatures.NEXT_EP_TITLE || video == null) return null
        return byVideoId.get(video.id)
            ?: seriesKey(video.id, video.season, video.episode)?.let { bySeriesEpisode.get(it) }
            ?: video.title.trim().takeIf { !isPlaceholder(it) }
    }

    private val placeholder = Regex("^(episode|ep\\.?|e)\\s*\\d+$", RegexOption.IGNORE_CASE)

    /** Blank, or only the mapper's "Episode 4" stand-in (no real name). */
    fun isPlaceholder(title: String?): Boolean = title.isNullOrBlank() || placeholder.matches(title.trim())

    /** Round 7b: a known name for a video id (Stream / new player after an episode ends). */
    fun byId(videoId: String?): String? {
        if (!NuvioCFeatures.NEXT_EP_TITLE || videoId.isNullOrBlank()) return null
        return byVideoId.get(videoId)
    }

    /** True when neither the details page nor the player's meta gave [video] a name. */
    fun missing(video: Video): Boolean = known(video) == null

    private val filled = java.util.Collections.synchronizedSet(HashSet<String>())

    /** One lookup per series per app run. */
    fun startFill(contentId: String): Boolean = filled.add(contentId)

    /** Name for [target] when switching episodes in the player; null = official fallback chain. */
    fun pick(target: Video?): String? {
        if (!NuvioCFeatures.NEXT_EP_TITLE || target == null) return null
        val known = byVideoId.get(target.id)
            ?: seriesKey(target.id, target.season, target.episode)?.let { bySeriesEpisode.get(it) }
        val own = target.title.trim().takeIf { it.isNotEmpty() }
        val chosen = known ?: own
        Log.d(TAG, "id=${target.id} S${target.season}E${target.episode} details=${known != null} player='${target.title}' -> '${chosen.orEmpty()}'")
        return chosen
    }
}

/**
 * [fork] Round 7 (2026-10-13): episode names the player finds itself. The details page only fills
 * [NuvioCEpisodeTitles] when it was opened in this app run, so Continue Watching / Home launches
 * (or a restarted app) left Next and auto-play with "S3 E4" alone. When the player's own meta has
 * blank names, look once per series: (1) your meta add-on by IMDb id, the way the details page
 * asks (the player asks by the id it was given, which can be a tmdb: id add-ons don't name),
 * (2) TMDB season data for the current and next season, names only (no description, no art).
 * Runs once in the background after the player's meta arrives; nothing on the playback path.
 */
internal fun PlayerRuntimeController.nuvioCFillEpisodeTitles() {
    if (!NuvioCFeatures.NEXT_EP_TITLE) return
    val type = contentType?.lowercase() ?: return
    if (type != "series" && type != "tv") return
    val id = contentId?.takeIf { it.isNotBlank() } ?: return
    val videos = metaVideos
    // Round 7b (2026-10-13): a player opened after an episode ends (Stream screen, new player) gets
    // no name in its arguments and, with the lookup already done for this series, never used the
    // names it knew. Always put the known name (meta or looked up) on the current episode first.
    NuvioCEpisodeTitles.remember(videos) // the meta's own real names, reachable by id (Stream screen, next player)
    nuvioCApplyKnownEpisodeTitles()
    if (videos.isEmpty() || videos.none { NuvioCEpisodeTitles.missing(it) }) return
    if (!NuvioCEpisodeTitles.startFill(id)) return
    scope.launch {
        val found = HashMap<Pair<Int, Int>, String>()
        runCatching {
            // 1) Meta add-on by IMDb id.
            val base = id.substringBefore(':').let { if (it == "tmdb") id.split(':').take(2).joinToString(":") else it }
            val imdb = when {
                base.startsWith("tt") -> null // same id the player already asked with
                base.startsWith("tmdb:") -> base.removePrefix("tmdb:").toIntOrNull()?.let { tmdbService.tmdbToImdb(it, "tv") }
                else -> null
            }?.takeIf { it.startsWith("tt") }
            if (imdb != null) {
                val result = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    metaRepository.getMetaFromAllAddons(type = contentType ?: "series", id = imdb)
                        .first { it !is com.nuvio.tv.core.network.NetworkResult.Loading }
                }
                (result as? com.nuvio.tv.core.network.NetworkResult.Success)?.data?.videos?.forEach { v ->
                    val s = v.season ?: return@forEach
                    val e = v.episode ?: return@forEach
                    v.title.trim().takeIf { it.isNotEmpty() }?.let { found[s to e] = it }
                }
            }
        }.onFailure { Log.w(TAG_FILL, "add-on names: ${it.message}") }

        runCatching {
            // 2) TMDB, only for the seasons still missing near the current one.
            val season = currentSeason ?: return@runCatching
            val stillMissing = metaVideos.filter { v ->
                NuvioCEpisodeTitles.missing(v) && v.season != null && v.episode != null &&
                    (v.season!! to v.episode!!) !in found && (v.season == season || v.season == season + 1)
            }
            if (stillMissing.isEmpty()) return@runCatching
            val settings = tmdbSettingsDataStore.settings.first()
            if (!settings.enabled) return@runCatching
            val tmdbId = tmdbService.ensureTmdbId(id, "series") ?: return@runCatching
            val seasons = stillMissing.mapNotNull { it.season }.distinct().sorted()
            tmdbMetadataService.fetchEpisodeEnrichment(tmdbId, seasons, settings.language).forEach { (key, ep) ->
                if (key !in found) ep.title?.trim()?.takeIf { it.isNotEmpty() }?.let { found[key] = it }
            }
        }.onFailure { Log.w(TAG_FILL, "TMDB names: ${it.message}") }

        var named = 0
        metaVideos.forEach { v ->
            val s = v.season ?: return@forEach
            val e = v.episode ?: return@forEach
            if (NuvioCEpisodeTitles.missing(v)) found[s to e]?.let { NuvioCEpisodeTitles.put(v.id, it); named++ }
        }
        Log.d(TAG_FILL, "id=$id looked up ${found.size} names, filled $named blank episodes")
        nuvioCApplyKnownEpisodeTitles()
    }
}

/** Puts a now-known name on the current episode and the Up next card when they're still blank. */
internal fun PlayerRuntimeController.nuvioCApplyKnownEpisodeTitles() {
    if (!NuvioCFeatures.NEXT_EP_TITLE) return
    val current = metaVideos.firstOrNull { it.id == currentVideoId }
        ?: metaVideos.firstOrNull { it.season == currentSeason && it.episode == currentEpisode }
    if (NuvioCEpisodeTitles.isPlaceholder(currentEpisodeTitle)) {
        NuvioCEpisodeTitles.known(current)?.let { name ->
            currentEpisodeTitle = name
            _uiState.update { it.copy(currentEpisodeTitle = name) }
        }
    }
    _uiState.update { state ->
        val next = state.nextEpisode ?: return@update state
        if (!NuvioCEpisodeTitles.isPlaceholder(next.title)) return@update state
        val video = metaVideos.firstOrNull { it.id == next.videoId } ?: return@update state
        val name = NuvioCEpisodeTitles.known(video) ?: return@update state
        val updated = next.copy(title = name)
        state.copy(nextEpisode = updated, postPlayMode = state.postPlayMode?.copyWithNextEpisode(updated))
    }
}

private const val TAG_FILL = "NuvioCEpisodeTitle"
