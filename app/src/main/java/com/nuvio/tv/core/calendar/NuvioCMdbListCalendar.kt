package com.nuvio.tv.core.calendar

// [fork] calendar_mdblist: MDBList's own release calendar as a Calendar source (Nuvio C only).

import android.util.Log
import com.nuvio.tv.data.mdblist.MdbListApiClient
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One title from MDBList's calendar with its dated releases in the window. [saved] = on the MDBList watchlist (a show
 * MDBList lists because you are watching it is not saved). Titles come from the website's Preferences > Calendar.
 */
data class MdbListCalendarItem(
    val ids: CalendarIds,
    val type: CalendarTitleType,
    val title: String?,
    val poster: String?,
    val saved: Boolean,
    val backdrop: String? = null, // [fork] calendar_mdblist_art
    val entries: List<CalendarEntry>,
    /** [fork] calendar_mdblist_extra_calendar_only: listed only for a favourite cast/crew member or as a popular release. */
    val extra: Boolean = false
)

@Serializable
internal data class MdbListCalendarResponse(val events: List<MdbListCalendarEvent> = emptyList())

@Serializable
internal data class MdbListCalendarEvent(
    val type: String? = null,
    @SerialName("release_type") val releaseType: String? = null,
    val start: String? = null,
    val title: String? = null,
    val tmdb: Long? = null,
    @SerialName("show_tmdb") val showTmdb: Long? = null,
    @SerialName("show_tvdb") val showTvdb: Long? = null,
    val poster: String? = null,
    val backdrop: String? = null, // [fork] calendar_mdblist_art
    @SerialName("is_watchlist") val isWatchlist: Boolean? = null,
    @SerialName("is_watched") val isWatched: Boolean? = null, // [fork] from a show you are watching (in progress or completed)
    @SerialName("favorite_person") val favoritePerson: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("season_number") val season: Int? = null,
    @SerialName("episode_number") val episode: Int? = null,
    val tvdb: MdbListCalendarTvdbNumber? = null,
    @SerialName("tvdb_ids") val tvdbIds: MdbListCalendarTvdbIds? = null,
    @SerialName("episode_type") val episodeType: String? = null,
    @SerialName("is_season_premiere") val seasonPremiere: Boolean? = null,
    @SerialName("is_season_finale") val seasonFinale: Boolean? = null,
    @SerialName("is_mid_season_finale") val midSeasonFinale: Boolean? = null
)

@Serializable
internal data class MdbListCalendarTvdbNumber(
    @SerialName("season_number") val season: Int? = null,
    @SerialName("episode_number") val episode: Int? = null
)

@Serializable
internal data class MdbListCalendarTvdbIds(val series: Long? = null)

@Singleton
class MdbListCalendarClient @Inject constructor(private val api: MdbListApiClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** Today - 7 to today + 112 (MDBList allows 120 days per call) in one call; null when it failed. */
    suspend fun events(today: LocalDate): List<MdbListCalendarItem>? = try {
        val body = api.get(
            "/calendar/events",
            mapOf(
                "start" to today.minusDays(MDBLIST_CALENDAR_PAST_DAYS).toString(),
                "end" to today.plusDays(MDBLIST_CALENDAR_AHEAD_DAYS).toString(),
                "limit" to "1000"
            )
        ).body
        val events = json.decodeFromString<MdbListCalendarResponse>(body).events
        if (events.size >= 1000) Log.w(TAG, "MDBList calendar hit its 1000-event limit; later dates may be missing")
        mdbListCalendarItems(events)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "MDBList calendar skipped: ${error.javaClass.simpleName} ${error.message}")
        null
    }

    private companion object {
        const val TAG = "CalendarMdbList"
    }
}

internal const val MDBLIST_CALENDAR_PAST_DAYS = 7L
internal const val MDBLIST_CALENDAR_AHEAD_DAYS = 112L

/**
 * Events grouped into titles. Episodes use TVDB numbering when MDBList gives it (TVmaze numbers like TVDB, so the same
 * episode is not listed twice), otherwise TMDB's. A show's own premiere event only adds the title; its first episode
 * carries the date. Films: `release` = cinema, `digital` = digital.
 */
internal fun mdbListCalendarItems(events: List<MdbListCalendarEvent>): List<MdbListCalendarItem> {
    data class Key(val type: CalendarTitleType, val tmdb: Long?, val tvdb: Long?)
    val grouped = linkedMapOf<Key, MutableList<MdbListCalendarEvent>>()
    events.forEach { event ->
        val key = when (event.type?.lowercase()) {
            "movie" -> Key(CalendarTitleType.MOVIE, event.tmdb, null)
            "show" -> Key(CalendarTitleType.SERIES, event.tmdb, null)
            "episode" -> Key(CalendarTitleType.SERIES, event.showTmdb, event.showTvdb ?: event.tvdbIds?.series)
            else -> return@forEach
        }
        if (key.tmdb == null && key.tvdb == null) return@forEach
        grouped.getOrPut(key) { mutableListOf() } += event
    }
    // A show's premiere event and its episodes share the TMDB id; fold them together.
    val merged = linkedMapOf<String, Pair<Key, MutableList<MdbListCalendarEvent>>>()
    grouped.forEach { (key, list) ->
        val id = "${key.type}|${key.tmdb ?: ("tvdb" + key.tvdb)}"
        val existing = merged[id]
        if (existing == null) merged[id] = key to list
        else merged[id] = Key(key.type, key.tmdb, existing.first.tvdb ?: key.tvdb) to existing.second.apply { addAll(list) }
    }
    return merged.values.map { (key, list) ->
        val ids = CalendarIds(tmdb = key.tmdb, tvdb = key.tvdb)
        val entries = list.mapNotNull { it.toEntry(key.type) }.distinctBy { it.slot }
        MdbListCalendarItem(
            ids = ids,
            type = key.type,
            title = list.firstNotNullOfOrNull { it.title?.takeIf(String::isNotBlank) },
            poster = list.firstNotNullOfOrNull { it.poster?.takeIf(String::isNotBlank)?.let { url -> mdbListCalendarImage(url) } },
            // [fork] calendar_mdblist_art: the show's or film's own backdrop (an episode event may carry a still).
            backdrop = list.sortedBy { if (it.type.equals("episode", true)) 1 else 0 }
                .firstNotNullOfOrNull { it.backdrop?.takeIf(String::isNotBlank)?.let { url -> mdbListCalendarImage(url, "w1280") } },
            saved = list.any { it.isWatchlist == true || it.releaseType == "episode" },
            // [fork] calendar_mdblist_extra_calendar_only: watchlist / in progress / completed win over favourite cast and popular.
            extra = list.none { it.isWatchlist == true || it.isWatched == true || it.releaseType == "watched" },
            entries = entries
        )
    }
}

private fun MdbListCalendarEvent.toEntry(type: CalendarTitleType): CalendarEntry? {
    val date = start?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }?.toString() ?: return null
    return when (this.type?.lowercase()) {
        "movie" -> CalendarEntry(
            kind = if (releaseType == "digital") CalendarEntryKind.MOVIE_DIGITAL else CalendarEntryKind.MOVIE_THEATRICAL,
            airDate = date,
            source = CalendarSource.MDBLIST
        )
        "episode" -> {
            if (type != CalendarTitleType.SERIES) return null
            val s = tvdb?.season ?: season ?: return null
            val e = tvdb?.episode ?: episode ?: return null
            CalendarEntry(
                season = s,
                episode = e,
                title = episodeTitle?.takeIf(String::isNotBlank),
                airDate = date,
                label = mdbListEpisodeLabel(this, s, e),
                source = CalendarSource.MDBLIST
            )
        }
        else -> null
    }
}

/** Episode type in Trakt's words (season_finale…) or TMDB's (finale, mid_season), else MDBList's flags. */
private fun mdbListEpisodeLabel(event: MdbListCalendarEvent, season: Int, episode: Int): CalendarEpisodeLabel {
    val type = event.episodeType?.takeIf(String::isNotBlank)
    if (type != null) {
        traktEpisodeLabel(type, season, episode).takeIf { it != CalendarEpisodeLabel.NONE }?.let { return it }
        tmdbEpisodeLabel(type).takeIf { it != CalendarEpisodeLabel.NONE }?.let { return it }
    }
    return flagLabel(event, season, episode)
}

private fun flagLabel(event: MdbListCalendarEvent, season: Int, episode: Int): CalendarEpisodeLabel = when {
    event.seasonFinale == true -> CalendarEpisodeLabel.SEASON_FINALE
    event.midSeasonFinale == true -> CalendarEpisodeLabel.MID_SEASON_FINALE
    event.seasonPremiere == true -> if (season == 1) CalendarEpisodeLabel.NEW_SHOW else CalendarEpisodeLabel.SEASON_PREMIERE
    else -> deriveEpisodeLabel(season, episode)
}

/**
 * MDBList gives TMDB / TVDB ids only. Before following, each item takes the other ids (IMDb, Trakt…) of any title the
 * Calendar already knows or another source gives that shares one of them, so the same show is one card, not two.
 */
fun bridgeMdbListIds(items: List<MdbListCalendarItem>, known: List<CalendarIds>): List<MdbListCalendarItem> {
    if (items.isEmpty()) return items
    val byAlias = HashMap<String, CalendarIds>()
    known.forEach { ids ->
        ids.tmdb?.let { byAlias.merge("tmdb:$it", ids) { a, b -> a.merged(b) } }
        ids.tvdb?.let { byAlias.merge("tvdb:$it", ids) { a, b -> a.merged(b) } }
    }
    return items.map { item ->
        val extra = listOfNotNull(
            item.ids.tmdb?.let { byAlias["tmdb:$it"] },
            item.ids.tvdb?.let { byAlias["tvdb:$it"] }
        )
        if (extra.isEmpty()) item else item.copy(ids = extra.fold(item.ids) { acc, ids -> acc.merged(ids) })
    }
}

fun mdbListCalendarCandidates(items: List<MdbListCalendarItem>): List<CalendarTitleCandidate> = items.map { item ->
    CalendarTitleCandidate(
        item.ids, item.type,
        when {
            item.extra && com.nuvio.tv.NuvioCFeatures.CALENDAR_MDBLIST_EXTRA_CALENDAR_ONLY -> CalendarOrigin.MDBLIST_CALENDAR_EXTRA
            item.saved -> CalendarOrigin.MDBLIST_CALENDAR_SAVED
            else -> CalendarOrigin.MDBLIST_CALENDAR
        },
        name = item.title, contentId = item.ids.imdb ?: item.ids.tmdb?.let { "tmdb:$it" }, poster = item.poster
    )
}

/** The latest MDBList dates replace every title's [CalendarSource.MDBLIST] dates; titles it no longer lists lose them. */
fun applyMdbListCalendar(
    records: List<CalendarTitleRecord>,
    items: List<MdbListCalendarItem>,
    now: java.time.Instant
): List<CalendarTitleRecord> = records.map { record ->
    val mine = items.filter { it.type == record.type && it.ids.sharesIdWith(record.ids) }
    if (mine.isEmpty()) return@map record.withoutSources(CalendarSource.MDBLIST)
    val backdrop = if (com.nuvio.tv.NuvioCFeatures.CALENDAR_MDBLIST_ART) mine.firstNotNullOfOrNull { it.backdrop } else null
    record.copy(
        ids = mine.fold(record.ids) { ids, item -> ids.merged(item.ids) },
        // [fork] calendar_mdblist_art: MDBList's backdrop until the add-on's art is read (never replaces it).
        backdrop = record.backdrop?.takeIf(String::isNotBlank) ?: backdrop,
        poster = record.poster?.takeIf(String::isNotBlank) ?: mine.firstNotNullOfOrNull { it.poster }
    ).withSource(CalendarSource.MDBLIST, mine.flatMap { it.entries }.distinctBy { it.slot }, now)
}

/** [fork] calendar_mdblist_art: MDBList gives full URLs or TMDB paths ("/abc.jpg"); paths become TMDB URLs. */
internal fun mdbListCalendarImage(value: String, size: String = "w500"): String =
    if (value.startsWith("/") && !value.startsWith("//")) "https://image.tmdb.org/t/p/$size$value" else value

/** [fork] calendar_mdblist_art: the record with an IMDb id found for it; "tmdb:N" content ids become the IMDb id. */
fun CalendarTitleRecord.withImdb(imdb: String): CalendarTitleRecord = copy(
    ids = ids.merged(CalendarIds(imdb = imdb)),
    contentId = if (contentId == null || contentId.startsWith("tmdb:")) imdb else contentId
)

/** Six hours between asks, as for Trakt; a Library change or settings change asks at once. */
val CALENDAR_MDBLIST_REFRESH: java.time.Duration = java.time.Duration.ofHours(6)

/** [fork] calendar_origin_log: short names for where a title comes from. */
fun calendarOriginCode(origin: CalendarOrigin): String = when (origin) {
    CalendarOrigin.LIBRARY -> "nuvio-library"
    CalendarOrigin.PROGRESS -> "nuvio-progress"
    CalendarOrigin.WATCHED -> "nuvio-watched"
    CalendarOrigin.TRAKT_WATCHED -> "trakt-watched"
    CalendarOrigin.TRAKT_WATCHLIST -> "trakt-watchlist"
    CalendarOrigin.TRAKT_LIST -> "trakt-list"
    CalendarOrigin.TRAKT_CALENDAR -> "trakt-calendar"
    CalendarOrigin.SIMKL_WATCHING -> "simkl-watching"
    CalendarOrigin.SIMKL_COMPLETED -> "simkl-completed"
    CalendarOrigin.SIMKL_ON_HOLD -> "simkl-on-hold"
    CalendarOrigin.SIMKL_PLAN_TO_WATCH -> "simkl-plan"
    CalendarOrigin.MDBLIST_WATCHED -> "mdblist-watched"
    CalendarOrigin.MDBLIST_PLAYBACK -> "mdblist-playback"
    CalendarOrigin.MDBLIST_WATCHLIST -> "mdblist-watchlist"
    CalendarOrigin.MDBLIST_LIST -> "mdblist-list"
    CalendarOrigin.MDBLIST_CALENDAR -> "mdblist-cal-watching"
    CalendarOrigin.MDBLIST_CALENDAR_SAVED -> "mdblist-cal-watchlist"
    CalendarOrigin.DROPPED -> "dropped"
    CalendarOrigin.BELL -> "bell"
    CalendarOrigin.MDBLIST_CALENDAR_EXTRA -> "mdblist-cal-extra"
    CalendarOrigin.TMDB_SOON -> "tmdb-soon"
}
