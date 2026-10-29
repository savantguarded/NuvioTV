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
    suspend fun items(today: LocalDate, region: String): List<TmdbSoonItem>? =
        if (com.nuvio.tv.NuvioCFeatures.TMDB_SOON_TOP20_RULES) top20Items(today) else round30Items(today, region)

    private suspend fun round30Items(today: LocalDate, region: String): List<TmdbSoonItem>? = try {
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

    // ---- [fork] round 31 tmdb_soon_top20_rules: the Top 20 add-on's Coming Soon rules ('notable' mode) ----
    //
    // Films: the first US digital release falls tomorrow..+30 days, the film isn't already for rent or sale in the
    // US, and it is a real release: a US cinema run (popularity 3+), a streamer as a production company, or on a
    // US subscription service. No popularity floor (that was what dropped most of them), no cinema-only titles.
    // Shows: English, no kids / news / reality / soap / talk, no anime, on a major streamer or US / UK network,
    // and something starts in the window: a premiere, a new season (episode 1 of season 2+) or the next episode
    // after a break of more than 14 days. Up to 5 pages of each discover list, each title's check kept 24 h.

    private class Checked(val at: Long, val keep: Boolean)
    private val checks = java.util.concurrent.ConcurrentHashMap<String, Checked>()
    private fun cached(key: String): Boolean? = checks[key]?.takeIf {
        // [fork] round 32 tmdb_soon_recheck: a show's "no" is kept 6 h (a new season can be dated later that day)
        val ttl = if (com.nuvio.tv.NuvioCFeatures.TMDB_SOON_RECHECK && key.startsWith("s") && !it.keep) SHOW_NO_TTL_MS else CHECK_TTL_MS
        System.currentTimeMillis() - it.at < ttl
    }?.keep

    private suspend fun top20Items(today: LocalDate): List<TmdbSoonItem>? = try {
        val from = today.plusDays(1)
        val to = today.plusDays(TMDB_SOON_DAYS)
        val films = mutableListOf<TmdbSoonItem>()
        val filmRaw = mutableListOf<NuvioCCalendarDiscoverItem>()
        for (page in 1..TOP20_PAGES) {
            val body = api.discoverSoonMovies(apiKey, TOP20_REGION, "4", from.toString(), to.toString(), page = page)
            if (!body.isSuccessful) { if (page == 1) throw CalendarFetchException("TMDB ${body.code()}") else break }
            val results = body.body()?.results.orEmpty()
            filmRaw += results
            if (results.size < 20) break
        }
        var filmChecks = 0
        for (raw in filmRaw.distinctBy { it.id }) {
            if (filmChecks >= TOP20_MAX_CHECKS) break
            val item = raw.toItem(CalendarTitleType.MOVIE) ?: continue
            if (!raw.originalLanguage.equals("en", true) && (raw.voteCount ?: 0) < TMDB_SOON_FOREIGN_MIN_VOTES) continue
            val id = raw.id ?: continue
            val keep = cached("m$id") ?: run {
                filmChecks++
                val details = api.getSoonMovie(id, apiKey).takeIf { it.isSuccessful }?.body() ?: return@run null
                top20KeepFilm(details, raw.popularity ?: 0.0, from, to).also { checks["m$id"] = Checked(System.currentTimeMillis(), it) }
            } ?: continue
            if (keep) films += item
        }
        val showRaw = mutableListOf<NuvioCCalendarDiscoverItem>()
        for (first in listOf(true, false)) {
            for (page in 1..TOP20_PAGES) {
                val body = if (first) api.discoverSoonShows(apiKey, firstFrom = from.toString(), firstTo = to.toString(), withoutGenres = TMDB_SOON_SKIP_GENRES, page = page)
                else api.discoverSoonShows(apiKey, airFrom = from.toString(), airTo = to.toString(), withoutGenres = TMDB_SOON_SKIP_GENRES, page = page)
                if (!body.isSuccessful) break
                val results = body.body()?.results.orEmpty()
                showRaw += results
                if (results.size < 20) break
            }
        }
        val shows = mutableListOf<TmdbSoonItem>()
        var showChecks = 0
        for (raw in showRaw.distinctBy { it.id }.sortedByDescending { it.popularity ?: 0.0 }) {
            if (showChecks >= TOP20_MAX_CHECKS) break
            if (!top20WantedShow(raw.originalLanguage, raw.genreIds.orEmpty(), raw.originCountry.orEmpty())) continue
            val item = raw.toItem(CalendarTitleType.SERIES) ?: continue
            val id = raw.id ?: continue
            val keep = cached("s$id") ?: run {
                showChecks++
                val details = api.getTvDetails(id, apiKey).takeIf { it.isSuccessful }?.body() ?: return@run null
                top20KeepShow(details, today, from, to).also {
                    checks["s$id"] = Checked(System.currentTimeMillis(), it)
                    if (!it && com.nuvio.tv.NuvioCFeatures.TMDB_SOON_RECHECK) Log.d(TAG, "Coming soon: left out ${details.name} (${top20ShowReason(details, today, from, to)})")
                }
            } ?: continue
            if (keep) shows += item
        }
        Log.d(TAG, "Top 20 rules: ${films.size} films, ${shows.size} shows (checked $filmChecks + $showChecks new)")
        (films + shows).sortedByDescending { it.popularity }.take(TOP20_MAX_TITLES)
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
        const val TOP20_REGION = "US"
        const val TOP20_PAGES = 5
        const val TOP20_MAX_CHECKS = 70
        const val TOP20_MAX_TITLES = 60
        const val CHECK_TTL_MS = 24L * 60 * 60 * 1000
        const val SHOW_NO_TTL_MS = 6L * 60 * 60 * 1000
    }
}

// [fork] round 31 tmdb_soon_top20_rules: the rules, kept as plain functions so they can be tested.

private val TOP20_MAJOR_OUTLETS = listOf(
    "netflix", "appletv", "apple", "primevideo", "hulu", "disney", "peacock", "paramount", "hbo", "max",
    "amc", "starz", "mgm", "showtime", "fx", "cbs", "nbc", "abc", "fox", "thecw", "pbs", "usanetwork",
    "syfy", "bravo", "freeform", "tnt", "tbs", "adultswim", "cartoonnetwork", "nickelodeon",
    "comedycentral", "bet", "shudder", "acorntv", "britbox", "bbc", "itv", "channel4", "sky",
    "nationalgeographic", "history", "ae", "epix", "cinemax", "youtubepremium", "roku",
    "cbc", "ctv", "citytv", "global"
)
private val TOP20_KIDS_NETWORKS = setOf(
    "disneyxd", "disneyjunior", "disneychannel", "nickjr", "nickelodeon", "nicktoons", "cartoonnetwork",
    "cartoonito", "pbskids", "universalkids", "cbeebies", "cbbc", "babytv", "discoveryfamily"
)
private val TOP20_STREAMER_STUDIOS = Regex("netflix|apple|amazon|prime video|disney\\+|hbo|max original|peacock|paramount\\+|hulu", RegexOption.IGNORE_CASE)
private const val TOP20_THEATRICAL_MIN_POPULARITY = 3.0
private const val TOP20_BREAK_DAYS = 14L

internal fun top20ServiceKey(name: String?): String =
    (name ?: "").lowercase().replace(Regex("\\bamazon\\b|\\bwith ads\\b|\\bplus\\b|\\+"), "").replace(Regex("[^a-z0-9]"), "")

internal fun top20MajorOutlet(name: String?): Boolean {
    val k = top20ServiceKey(name)
    return k.length >= 2 && TOP20_MAJOR_OUTLETS.any { k == it || k.startsWith(it) }
}

/** English, not kids / anime; discover's genre ids already leave out news, reality, soap and talk. */
internal fun top20WantedShow(language: String?, genreIds: List<Int>, originCountry: List<String>): Boolean {
    if (!language.equals("en", true)) return false
    if (16 in genreIds && 10751 in genreIds) return false // animated family = kids
    if (16 in genreIds && originCountry.any { it in setOf("JP", "CN", "KR", "TW") }) return false // anime / donghua
    return listOf(10762, 10763, 10764, 10766, 10767).none { it in genreIds }
}

private fun String?.date(): LocalDate? = this?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal fun top20KeepFilm(details: com.nuvio.tv.data.remote.api.NuvioCCalendarSoonMovie, popularity: Double, from: LocalDate, to: LocalDate): Boolean {
    val us = details.releaseDates?.results.orEmpty().firstOrNull { it.iso31661 == "US" }?.releaseDates.orEmpty()
    val firstDigital = us.filter { it.type == 4 }.mapNotNull { it.releaseDate.date() }.minOrNull() ?: return false
    if (firstDigital.isBefore(from) || firstDigital.isAfter(to)) return false
    val region = details.watchProviders?.results?.get("US")
    if (!region?.rent.isNullOrEmpty() || !region?.buy.isNullOrEmpty()) return false // out already, TMDB's date is stale
    val types = us.mapNotNull { it.type }
    return (popularity >= TOP20_THEATRICAL_MIN_POPULARITY && (3 in types || 2 in types)) ||
        details.productionCompanies.orEmpty().any { TOP20_STREAMER_STUDIOS.containsMatchIn(it.name.orEmpty()) } ||
        !region?.flatrate.isNullOrEmpty()
}

internal fun top20KeepShow(details: com.nuvio.tv.data.remote.api.NuvioCCalendarTmdbShow, today: LocalDate, from: LocalDate, to: LocalDate): Boolean {
    val networks = details.networks.orEmpty()
    if (networks.any { top20ServiceKey(it.name) in TOP20_KIDS_NETWORKS }) return false
    val genreIds = details.genres.orEmpty().mapNotNull { it.id }
    if (16 in genreIds && 10751 in genreIds) return false
    if (networks.none { top20MajorOutlet(it.name) }) return false
    fun inWindow(d: LocalDate?) = d != null && !d.isBefore(from) && !d.isAfter(to)
    val last = details.lastEpisodeToAir
    val next = details.nextEpisodeToAir
    val firstAir = details.firstAirDate.date()
    if (firstAir != null && firstAir.isAfter(today) && inWindow(firstAir)) return true // premiere
    // [fork] round 32 tmdb_soon_recheck: TMDB often leaves next_episode_to_air empty until days before a new season
    // (Dexter: Resurrection S2) while the season list already has its date.
    if (com.nuvio.tv.NuvioCFeatures.TMDB_SOON_RECHECK &&
        details.seasons.orEmpty().any { (it.seasonNumber ?: 0) >= 2 && inWindow(it.airDate.date()) }
    ) return true
    val upcoming = listOfNotNull(last, next).firstOrNull { inWindow(it.airDate.date()) } ?: return false
    if (upcoming.episodeNumber == 1 && (upcoming.seasonNumber ?: 0) >= 2) return true // new season
    if (upcoming === next) {
        val prior = last?.airDate.date()
        val nextDate = next.airDate.date() ?: return false
        if (prior == null || java.time.temporal.ChronoUnit.DAYS.between(prior, nextDate) > TOP20_BREAK_DAYS) return true // back from a break
    }
    return false
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

/** [fork] round 32: why [top20KeepShow] left a show out, for the log line only. */
internal fun top20ShowReason(details: com.nuvio.tv.data.remote.api.NuvioCCalendarTmdbShow, today: LocalDate, from: LocalDate, to: LocalDate): String {
    val networks = details.networks.orEmpty()
    return when {
        networks.any { top20ServiceKey(it.name) in TOP20_KIDS_NETWORKS } -> "kids network"
        networks.none { top20MajorOutlet(it.name) } -> "network not listed: ${networks.joinToString { it.name.orEmpty() }}"
        else -> "nothing starts in $from..$to (next ${details.nextEpisodeToAir?.let { "S${it.seasonNumber}E${it.episodeNumber} ${it.airDate}" } ?: "none"})"
    }
}
