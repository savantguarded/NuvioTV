package com.nuvio.tv.core.calendar

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Round 23: MDBList calendar, library wins, Upcoming future only. */
class NuvioCRound23CalendarTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val sample = """
        {"events":[
          {"id":"a","type":"show","release_type":"release","start":"2026-10-25","title":"New Show","tmdb":10,"is_watchlist":true,"poster":"p.jpg"},
          {"id":"b","type":"episode","release_type":"episode","start":"2026-10-25","title":"New Show","show_tmdb":10,"show_tvdb":20,
           "season_number":1,"episode_number":1,"episode_title":"Pilot","is_season_premiere":true,"is_watchlist":true},
          {"id":"c","type":"episode","release_type":"watched","start":"2026-10-30","title":"Anime","show_tmdb":11,
           "season_number":1,"episode_number":30,"tvdb":{"season_number":2,"episode_number":5},"tvdb_ids":{"series":21,"season":2,"episode":5},
           "is_season_finale":true,"is_watchlist":false,"extra_field":{"x":1}},
          {"id":"d","type":"movie","release_type":"digital","start":"2026-11-02","title":"Film","tmdb":12,"is_watchlist":false},
          {"id":"e","type":"movie","release_type":"release","start":"2026-10-20","title":"Film","tmdb":12},
          {"id":"f","type":"episode","start":"2026-10-30","title":"No Ids","season_number":1,"episode_number":1}
        ]}
    """.trimIndent()

    private fun items() = mdbListCalendarItems(json.decodeFromString<MdbListCalendarResponse>(sample).events)

    @Test fun eventsBecomeOneItemPerTitle() {
        val items = items()
        assertEquals(3, items.size)
        val show = items.first { it.ids.tmdb == 10L }
        assertEquals(CalendarIds(tmdb = 10, tvdb = 20), show.ids)
        assertTrue(show.saved)
        assertEquals("p.jpg", show.poster)
        val pilot = show.entries.single()
        assertEquals("S1E1", pilot.slot)
        assertEquals("2026-10-25", pilot.airDate)
        assertEquals(CalendarEpisodeLabel.NEW_SHOW, pilot.label)
        assertEquals(CalendarSource.MDBLIST, pilot.source)
    }

    @Test fun episodesUseTvdbNumbersWhenGivenAndWatchingShowsAreNotSaved() {
        val anime = items().first { it.ids.tmdb == 11L }
        assertFalse(anime.saved)
        assertEquals(21L, anime.ids.tvdb)
        assertEquals("S2E5", anime.entries.single().slot)
        assertEquals(CalendarEpisodeLabel.SEASON_FINALE, anime.entries.single().label)
    }

    @Test fun filmsGetCinemaAndDigitalDates() {
        val film = items().first { it.type == CalendarTitleType.MOVIE }
        assertEquals(setOf(CalendarEntryKind.MOVIE_DIGITAL, CalendarEntryKind.MOVIE_THEATRICAL), film.entries.map { it.kind }.toSet())
        assertEquals(CalendarOrigin.MDBLIST_CALENDAR, mdbListCalendarCandidates(listOf(film)).single().origin)
    }

    @Test fun bridgingTakesTheOtherIdsOfAKnownTitle() {
        val bridged = bridgeMdbListIds(items(), listOf(CalendarIds(imdb = "tt10", tvdb = 20), CalendarIds(imdb = "tt99", tmdb = 99)))
        assertEquals("tt10", bridged.first { it.ids.tmdb == 10L }.ids.imdb)
        assertEquals(null, bridged.first { it.ids.tmdb == 11L }.ids.imdb)
    }

    @Test fun mdbListDatesReplaceOldOnesAndLeaveTitlesItNoLongerLists() {
        val record = CalendarTitleRecord("series:imdb:tt10", CalendarTitleType.SERIES, CalendarIds(imdb = "tt10", tvdb = 20))
        val withDates = applyMdbListCalendar(listOf(record), items(), now).single()
        assertEquals(listOf("S1E1"), withDates.sources[CalendarSource.MDBLIST].orEmpty().map { it.slot })
        assertEquals(10L, withDates.ids.tmdb)
        val gone = applyMdbListCalendar(listOf(withDates), emptyList(), now).single()
        assertTrue(CalendarSource.MDBLIST !in gone.sources)
    }

    @Test fun savedTitlesSurviveContinueWatchingRemovalAndOldWatches() {
        val candidates = listOf(
            CalendarTitleCandidate(CalendarIds(imdb = "tt1"), CalendarTitleType.SERIES, CalendarOrigin.MDBLIST_WATCHLIST, daysAgo(600)),
            CalendarTitleCandidate(CalendarIds(imdb = "tt1"), CalendarTitleType.SERIES, CalendarOrigin.TRAKT_WATCHED, daysAgo(500)),
            CalendarTitleCandidate(CalendarIds(imdb = "tt2"), CalendarTitleType.SERIES, CalendarOrigin.PROGRESS, daysAgo(3)),
            CalendarTitleCandidate(CalendarIds(imdb = "tt3"), CalendarTitleType.SERIES, CalendarOrigin.MDBLIST_LIST, daysAgo(3)),
            CalendarTitleCandidate(CalendarIds(tmdb = 4), CalendarTitleType.SERIES, CalendarOrigin.MDBLIST_CALENDAR),
            CalendarTitleCandidate(CalendarIds(tmdb = 4), CalendarTitleType.SERIES, CalendarOrigin.SIMKL_COMPLETED, daysAgo(900))
        )
        val rules = CalendarFollowRules(includePersonalLists = true, removedFromContinueWatching = setOf("tt2", "tt3"), libraryWins = true)
        val kept = followCalendarTitles(candidates, rules, now)
        assertEquals(setOf("series:imdb:tt1", "series:imdb:tt3", "series:tmdb:4"), kept.titles.map { it.key }.toSet())
        assertEquals(CalendarLeftOutReason.REMOVED_FROM_CONTINUE_WATCHING, kept.leftOut.single().reason)

        val ysos = followCalendarTitles(candidates, rules.copy(libraryWins = false), now)
        assertEquals(setOf("series:imdb:tt1", "series:imdb:tt3", "series:imdb:tt2", "series:tmdb:4"), ysos.leftOut.map { it.key }.toSet())
    }

    @Test fun overTheCapTitlesWithNothingAheadGoFirst() {
        val old = CalendarTitleRecord(
            "movie:imdb:tt5", CalendarTitleType.MOVIE, CalendarIds(imdb = "tt5"), fetchedAtMs = daysAgo(1).toEpochMilli(),
            sources = mapOf(CalendarSource.TMDB to listOf(CalendarEntry(kind = CalendarEntryKind.MOVIE_DIGITAL, airDate = "2019-01-01", source = CalendarSource.TMDB)))
        )
        val coming = old.copy(key = "movie:imdb:tt6", ids = CalendarIds(imdb = "tt6"),
            sources = mapOf(CalendarSource.TMDB to listOf(CalendarEntry(kind = CalendarEntryKind.MOVIE_DIGITAL, airDate = "2026-12-01", source = CalendarSource.TMDB))))
        val done = calendarDoneAliases(listOf(old, coming), now)
        assertEquals(setOf("imdb:tt5"), done)
        val result = followCalendarTitles(
            listOf(
                CalendarTitleCandidate(CalendarIds(imdb = "tt5"), CalendarTitleType.MOVIE, CalendarOrigin.MDBLIST_WATCHLIST, daysAgo(1)),
                CalendarTitleCandidate(CalendarIds(imdb = "tt6"), CalendarTitleType.MOVIE, CalendarOrigin.MDBLIST_WATCHLIST, daysAgo(50))
            ),
            CalendarFollowRules(cap = 1, doneAliases = done, libraryWins = true),
            now
        )
        assertEquals(listOf("movie:imdb:tt6"), result.titles.map { it.key })
    }

    @Test fun upcomingFutureOnlyDropsReleasesThatAreOut() {
        val title = CalendarTitleRecord(
            "series:imdb:tt7", CalendarTitleType.SERIES, CalendarIds(imdb = "tt7"), name = "Show",
            sources = mapOf(
                CalendarSource.TVMAZE to listOf(
                    CalendarEntry(season = 1, episode = 1, airInstantMs = daysAgo(2).toEpochMilli()),
                    CalendarEntry(season = 1, episode = 2, airInstantMs = now.plus(Duration.ofDays(5)).toEpochMilli())
                )
            )
        )
        val ysos = buildCalendarComingUp(listOf(title), null, now, ZoneOffset.UTC).single()
        assertEquals(CalendarComingUpWhen.NEW, ysos.whenKind)
        val future = buildCalendarComingUp(listOf(title), null, now, ZoneOffset.UTC, futureOnly = true).single()
        assertEquals(2, future.entry.episode)
        val onlyOut = title.copy(sources = mapOf(CalendarSource.TVMAZE to title.sources[CalendarSource.TVMAZE]!!.take(1)))
        assertTrue(buildCalendarComingUp(listOf(onlyOut), null, now, ZoneOffset.UTC, futureOnly = true).isEmpty())
    }
}
