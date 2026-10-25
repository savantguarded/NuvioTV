package com.nuvio.tv.core.calendar

// [fork] calendar_tmdb_soon: a "coming soon" list from TMDB as a Calendar source (Nuvio C only). Needs no
// account. Inspired by the Top 20 add-on's Coming Soon filters: 30 days from tomorrow, films with a cinema or
// digital release in the region and some buzz, English-language new shows and season premieres, no kids, news,
// reality, soap or talk shows. It only names the titles; their dates come from the normal TMDB / TVmaze refresh.
// A title MDBList's calendar also lists carries both origins and is one card (ids are shared).

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.remote.api.NuvioCCalendarDiscoverItem
import com.nuvio.tv.data.remote.api.NuvioCCalendarTmdbApi
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

data class TmdbSoonItem(
    val ids: CalendarIds,
    val type: CalendarTitleType,
    val title: String?,
    val poster: String?,
    val popularity: Double,
    val year: Int? = null
)

internal const val TMDB_SOON_DAYS = 30L
internal const val TMDB_SOON_MIN_POPULARITY = 10.0
internal const val TMDB_SOON_FOREIGN_MIN_VOTES = 50
internal const val TMDB_SOON_MAX_TITLES = 40
private const val TMDB_SOON_RETURNING_CHECKS = 25
private const val TMDB_SOON_FILM_MAX_AGE_DAYS = 90L
/** Kids, news, reality, soap, talk (TMDB tv genre ids), as in the Top 20 add-on. */
private const val TMDB_SOON_SKIP_GENRES = "10762,10763,10764,10766,10767"

@Singleton
class TmdbSoonClient @Inject constructor(
    private val api: NuvioCCalendarTmdbApi,
    private val tmdbService: TmdbService
) {
    private val apiKey: String get() = tmdbService.apiKey()

    /** Tomorrow to 30 days on, in popularity order; null when TMDB could not be read at all. */
    suspend fun items(today: LocalDate, region: String): List<TmdbSoonItem>? = try {
        val from = today.plusDays(1)
        val to = today.plusDays(TMDB_SOON_DAYS)
        val films = mutableListOf<TmdbSoonItem>()
        for (page in 1..2) {
            val body = api.discoverSoonMovies(apiKey, region, "3|4", from.toString(), to.toString(), page = page)
            if (!body.isSuccessful) throw CalendarFetchException("TMDB ${body.code()}")
            films += body.body()?.results.orEmpty().mapNotNull { it.toItem(CalendarTitleType.MOVIE) }
                .filter { keepFilm(it, body.body()?.results.orEmpty(), today) }
        }
        val shows = linkedMapOf<Long, TmdbSoonItem>()
        // New shows first airing in the window.
        val fresh = api.discoverSoonShows(apiKey, firstFrom = from.toString(), firstTo = to.toString(), withoutGenres = TMDB_SOON_SKIP_GENRES)
        if (!fresh.isSuccessful) throw CalendarFetchException("TMDB ${fresh.code()}")
        fresh.body()?.results.orEmpty().mapNotNull { it.toItem(CalendarTitleType.SERIES) }
            .filter { it.popularity >= TMDB_SOON_MIN_POPULARITY }
            .forEach { it.ids.tmdb?.let { id -> shows[id] = it } }
        // Shows with any episode in the window; kept only when that is a season premiere.
        val airing = api.discoverSoonShows(apiKey, airFrom = from.toString(), airTo = to.toString(), withoutGenres = TMDB_SOON_SKIP_GENRES)
        if (airing.isSuccessful) {
            airing.body()?.results.orEmpty().mapNotNull { it.toItem(CalendarTitleType.SERIES) }
                .filter { it.popularity >= TMDB_SOON_MIN_POPULARITY && it.ids.tmdb !in shows }
                .sortedByDescending { it.popularity }
                .take(TMDB_SOON_RETURNING_CHECKS)
                .forEach { item ->
                    val id = item.ids.tmdb ?: return@forEach
                    val next = api.getTvDetails(id.toInt(), apiKey).body()?.nextEpisodeToAir ?: return@forEach
                    val date = next.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
                    if (next.episodeNumber == 1 && !date.isBefore(from) && !date.isAfter(to)) shows[id] = item
                }
        }
        (films + shows.values).sortedByDescending { it.popularity }.take(TMDB_SOON_MAX_TITLES)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "TMDB coming soon skipped: ${error.javaClass.simpleName} ${error.message}")
        null
    }

    private fun keepFilm(item: TmdbSoonItem, raw: List<NuvioCCalendarDiscoverItem>, today: LocalDate): Boolean {
        val source = raw.firstOrNull { it.id?.toLong() == item.ids.tmdb } ?: return false
        if (item.popularity < TMDB_SOON_MIN_POPULARITY) return false
        // Only new films: TMDB also matches old ones with a re-release or a new 4K / digital date (Jurassic World 2015).
        // The film's own (primary) release date must be no older than 90 days.
        val primary = source.releaseDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return false
        if (primary.isBefore(today.minusDays(TMDB_SOON_FILM_MAX_AGE_DAYS))) return false
        // Films in other languages need a following already (Top 20 does the same).
        return source.originalLanguage.equals("en", ignoreCase = true) || (source.voteCount ?: 0) >= TMDB_SOON_FOREIGN_MIN_VOTES
    }

    private fun NuvioCCalendarDiscoverItem.toItem(type: CalendarTitleType): TmdbSoonItem? {
        val tmdb = id?.toLong() ?: return null
        if (adult == true) return null
        return TmdbSoonItem(
            ids = CalendarIds(tmdb = tmdb),
            type = type,
            title = (if (type == CalendarTitleType.MOVIE) title else name)?.takeIf(String::isNotBlank),
            poster = tmdbPosterUrl(posterPath),
            popularity = popularity ?: 0.0,
            year = releaseDate?.take(4)?.toIntOrNull()
        )
    }

    private companion object {
        const val TAG = "CalendarTmdbSoon"
    }
}

/**
 * TMDB gives TMDB ids only. Each item takes the other ids (IMDb, Trakt…) of any title the Calendar already knows
 * or another source gives that shares its TMDB id, so the same title is one card, not two.
 */
fun bridgeTmdbSoonIds(items: List<TmdbSoonItem>, known: List<CalendarIds>): List<TmdbSoonItem> {
    if (items.isEmpty()) return items
    val byTmdb = HashMap<Long, CalendarIds>()
    known.forEach { ids -> ids.tmdb?.let { byTmdb.merge(it, ids) { a, b -> a.merged(b) } } }
    return items.map { item ->
        val match = item.ids.tmdb?.let { byTmdb[it] } ?: return@map item
        item.copy(ids = item.ids.merged(match))
    }
}

fun tmdbSoonCandidates(items: List<TmdbSoonItem>): List<CalendarTitleCandidate> = items.map { item ->
    CalendarTitleCandidate(
        item.ids, item.type, CalendarOrigin.TMDB_SOON,
        name = item.title, contentId = item.ids.imdb ?: item.ids.tmdb?.let { "tmdb:$it" }, poster = item.poster,
        year = item.year.takeIf { item.type == CalendarTitleType.MOVIE }
    )
}

/** Six hours between asks; a settings change or a manual refresh asks at once. */
val CALENDAR_TMDB_SOON_REFRESH: java.time.Duration = java.time.Duration.ofHours(6)
