package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 37 `roulette_episodes`: Roulette's Episodes mode. One random episode from a show you've
// watched or saved (Continue Watching / watch history and your Library).
//
// "Watchable at random": every show can come up, but episodic ones lead. A drawn show is kept with a chance that
// follows its kind (comedy, animation, kids 6/6; documentary, crime, mystery, procedurals 4/6; others 2/6; a show
// with a single season, often one story told in order, 1/6), otherwise another show is drawn. Episode lists,
// names and order come from your meta add-on (the same videos the details page lists), so playback and
// scrobbling match the rest of the app. Unwatched = only episodes you haven't seen. Moods narrow by genre;
// Refine's Decade (show's first year) and Length (episode runtime) apply; Region, Acclaim and Pool size don't.

import android.util.Log
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** The episode the reel landed on. [videoId] is the meta add-on's id for it (what the sources screen asks for). */
data class NuvioCRouletteEpisode(
    val videoId: String,
    val season: Int,
    val episode: Int,
    val title: String?,
    val runtime: Int?,
    val released: String?,
    val still: String?
)

internal class NuvioCRouletteEpisodes(
    private val watchProgress: WatchProgressRepository,
    private val library: LibraryRepository,
    private val meta: MetaRepository,
    private val random: java.util.Random
) {
    private data class Show(val id: String, val name: String, val poster: String?, val genres: List<String>)

    private val metaCache = HashMap<String, Meta?>()

    /** Your shows: Library series first in the list, then watched / in-progress series (each once). */
    private suspend fun shows(): List<Show> {
        val out = LinkedHashMap<String, Show>()
        withTimeoutOrNull(5_000) { library.libraryItems.first() }.orEmpty()
            .filter { it.type.equals("series", true) || it.type.equals("tv", true) }
            .forEach { e ->
                val id = e.imdbId ?: e.id
                out.putIfAbsent(id, Show(id, e.name, e.poster, e.genres.map(::normalizeShuffleGenre)))
            }
        withTimeoutOrNull(5_000) { watchProgress.allProgress.first() }.orEmpty()
            .filter { it.contentType.equals("series", true) || it.season != null }
            .forEach { p -> out.putIfAbsent(p.contentId, Show(p.contentId, p.name, p.poster, emptyList())) }
        runCatching { watchProgress.getWatchedShowEpisodes().keys }.getOrNull()?.forEach { id ->
            out.putIfAbsent(id, Show(id, "", null, emptyList()))
        }
        return out.values.filter { it.id.isNotBlank() }
    }

    /** Posters for the Episodes tile and the reel (no requests). */
    suspend fun posters(count: Int): List<String> =
        shows().mapNotNull { it.poster?.takeIf(String::isNotBlank) }.shuffled(random).distinct().take(count)

    suspend fun pick(
        mood: NuvioCShuffleMood,
        unwatched: Boolean,
        refine: NuvioCRouletteRefine,
        skip: (String) -> Boolean
    ): Pair<NuvioCShufflePick, NuvioCRouletteEpisode>? {
        val shows = shows()
        if (shows.isEmpty()) return null
        val watched = if (unwatched) runCatching { watchProgress.getWatchedShowEpisodes() }.getOrDefault(emptyMap()) else emptyMap()
        val r = refine.effective()
        var fallback: Pair<NuvioCShufflePick, NuvioCRouletteEpisode>? = null
        val tried = HashSet<String>()
        repeat(MAX_TRIES) {
            val candidates = shows.filter { it.id !in tried && (it.genres.isEmpty() || nuvioCMoodMatchesGenres(it.genres, mood)) }
            if (candidates.isEmpty()) return fallback
            val show = candidates[random.nextInt(candidates.size)]
            tried += show.id
            val m = metaFor(show.id) ?: return@repeat
            val genres = m.genres.map(::normalizeShuffleGenre)
            if (!nuvioCMoodMatchesGenres(genres, mood)) return@repeat
            if (!decadeOk(m, r.decade)) return@repeat
            val seen = watched[show.id] ?: m.imdbId?.let { watched[it] } ?: emptySet()
            val today = LocalDate.now()
            val episodes = m.videos.filter { v ->
                val s = v.season ?: return@filter false
                val e = v.episode ?: return@filter false
                s > 0 && e > 0 && released(v, today) && lengthOk(v, r.length) &&
                    (s to e) !in seen && !skip(identity(show.id, s, e))
            }
            if (episodes.isEmpty()) return@repeat
            val v = episodes[random.nextInt(episodes.size)]
            val result = build(show, m, v)
            if (fallback == null) fallback = result
            val seasons = m.videos.mapNotNull { it.season }.filter { it > 0 }.distinct().size
            val weight = if (seasons <= 1) 1 else episodicWeight(genres)
            if (random.nextInt(6) < weight) return result
        }
        return fallback
    }

    private suspend fun metaFor(id: String): Meta? {
        metaCache[id]?.let { return it }
        if (metaCache.containsKey(id)) return null
        val m = runCatching {
            withTimeoutOrNull(7_000) { meta.getMetaFromPrimaryAddon("series", id).first { it !is NetworkResult.Loading } }
        }.getOrNull()
        val data = (m as? NetworkResult.Success)?.data
        if (metaCache.size > 60) metaCache.clear()
        metaCache[id] = data
        if (data == null) Log.d(TAG, "no details for $id, skipped")
        return data
    }

    private fun build(show: Show, m: Meta, v: Video): Pair<NuvioCShufflePick, NuvioCRouletteEpisode> {
        val s = v.season ?: 0
        val e = v.episode ?: 0
        val ep = NuvioCRouletteEpisode(
            videoId = v.id,
            season = s,
            episode = e,
            title = v.title.takeIf { it.isNotBlank() && !NuvioCEpisodeNames.isPlaceholder(it) },
            runtime = v.runtime,
            released = v.released,
            still = v.thumbnail
        )
        val item = NuvioCShuffleItem(
            id = m.imdbId ?: show.id,
            type = "series",
            name = m.name.ifBlank { show.name },
            poster = m.poster ?: show.poster,
            background = m.background ?: v.thumbnail,
            logo = m.logo,
            description = v.overview?.takeIf { it.isNotBlank() } ?: m.description,
            year = v.released?.take(4),
            released = v.released,
            genres = m.genres.map(::normalizeShuffleGenre),
            imdbId = m.imdbId,
            from = "episodes"
        )
        return NuvioCShufflePick(item, runtime = v.runtime?.let { "$it min" }) to ep
    }

    private fun released(v: Video, today: LocalDate): Boolean {
        if (v.available == false) return false
        val d = v.released?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return true
        return !d.isAfter(today)
    }

    private fun lengthOk(v: Video, length: NuvioCRouletteLength): Boolean {
        val rt = v.runtime ?: return true
        if (length.tvMin != null && rt < length.tvMin) return false
        if (length.tvMax != null && rt > length.tvMax) return false
        return true
    }

    private fun decadeOk(m: Meta, decade: NuvioCRouletteDecade): Boolean {
        if (decade == NuvioCRouletteDecade.ANY) return true
        val year = (m.releaseInfo ?: m.released)?.take(4)?.toIntOrNull() ?: return true
        if (decade.from != null && year < decade.from) return false
        if (decade.to != null && year > decade.to) return false
        return true
    }

    companion object {
        private const val TAG = "NuvioCEpisodes"
        private const val MAX_TRIES = 10

        fun identity(showId: String, season: Int, episode: Int) = "e:$showId:$season:$episode"

        /** 6 = comedy / animation / kids, 4 = documentary / crime / mystery, 2 = everything else (out of 6). */
        fun episodicWeight(genres: List<String>): Int = when {
            genres.any { it in setOf("comedy", "animation", "kids", "family") } -> 6
            genres.any { it in setOf("documentary", "crime", "mystery", "reality") } -> 4
            else -> 2
        }
    }
}

/** Mood match on genre names (ANY matches everything). */
internal fun nuvioCMoodMatchesGenres(genres: List<String>, mood: NuvioCShuffleMood): Boolean =
    mood == NuvioCShuffleMood.ANY || genres.any { it in mood.genres } ||
        (mood == NuvioCShuffleMood.TRUE_STORY && genres.any { it == "documentary" })

/** "Episode 3"-style add-on placeholders read as no name. */
internal object NuvioCEpisodeNames {
    private val placeholder = Regex("""(?i)^\s*(episode|ep\.?)\s*\d+\s*$""")
    fun isPlaceholder(title: String): Boolean = placeholder.matches(title)
}
