package com.nuvio.tv.core.calendar

import com.nuvio.tv.ui.screens.home.nuvioCCalendarOnly
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Round 25: Upcoming 14 days, release-day New for shows you are behind on, MDBList extras Calendar only. */
class NuvioCRound25UpcomingTest {
    private val now = Instant.parse("2026-10-23T20:00:00Z")
    private val zone = ZoneOffset.UTC
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private fun show(imdb: String, vararg episodes: Pair<Int, Instant>) = CalendarTitleRecord(
        "series:imdb:$imdb", CalendarTitleType.SERIES, CalendarIds(imdb = imdb), name = imdb,
        sources = mapOf(CalendarSource.TVMAZE to episodes.map { (e, at) -> CalendarEntry(season = 1, episode = e, airInstantMs = at.toEpochMilli()) })
    )
    private fun inDays(d: Long, hours: Long = 0) = now.plus(Duration.ofDays(d)).plus(Duration.ofHours(hours))

    @Test fun upcomingStopsAtFourteenDays() {
        val near = show("tt1", 1 to inDays(14, -1))
        val far = show("tt2", 1 to inDays(15))
        val items = buildCalendarComingUp(listOf(near, far), null, now, zone, futureOnly = true, maxDaysAhead = 14)
        assertEquals(listOf("tt1"), items.map { it.title.name })
        assertEquals(2, buildCalendarComingUp(listOf(near, far), null, now, zone, futureOnly = true).size)
    }

    @Test fun aShowYouAreBehindOnShowsTodaysEpisodeOnlyAsNew() {
        // Out two hours ago, next one next week.
        val behind = show("tt3", 5 to now.minus(Duration.ofHours(2)), 6 to inDays(7))
        val dayOnly: (CalendarTitleRecord) -> Boolean = { it.key == behind.key }
        val today = buildCalendarComingUp(listOf(behind), null, now, zone, futureOnly = true, maxDaysAhead = 14, releaseDayOnly = dayOnly).single()
        assertEquals(CalendarComingUpWhen.NEW, today.whenKind)
        assertEquals(5, today.entry.episode)
        // Next day: nothing (its next episode stays in the Calendar while you are behind).
        val tomorrow = now.plus(Duration.ofHours(6))
        assertTrue(buildCalendarComingUp(listOf(behind), null, tomorrow, zone, futureOnly = true, maxDaysAhead = 14, releaseDayOnly = dayOnly).isEmpty())
        // Caught up: Continue Watching already shows that episode, so Upcoming does not.
        val caughtUp = buildCalendarComingUp(listOf(behind), null, now, zone, { _, entry -> entry.episode == 5 },
            futureOnly = true, maxDaysAhead = 14, releaseDayOnly = dayOnly)
        assertTrue(caughtUp.isEmpty())
    }

    @Test fun aCaughtUpShowNotInContinueWatchingLeavesWhenItAirs() {
        val aired = show("tt4", 5 to now.minus(Duration.ofHours(2)))
        assertTrue(buildCalendarComingUp(listOf(aired), null, now, zone, futureOnly = true, maxDaysAhead = 14).isEmpty())
    }

    @Test fun mdbListFavouriteCastAndPopularAreCalendarOnly() {
        val events = json.decodeFromString<MdbListCalendarResponse>(
            """{"events":[
              {"type":"movie","release_type":"digital","start":"2026-10-30","title":"Watchlist Film","tmdb":1,"is_watchlist":true},
              {"type":"episode","release_type":"watched","start":"2026-10-30","title":"Watching","show_tmdb":2,"season_number":1,"episode_number":2,"is_watched":true},
              {"type":"episode","release_type":"episode","start":"2026-10-30","title":"Cast And Watching","show_tmdb":3,"season_number":1,"episode_number":2,"is_watched":true,"favorite_person":{"tmdb":9,"name":"X"}},
              {"type":"movie","release_type":"release","start":"2026-10-30","title":"Fav Cast Film","tmdb":4,"favorite_person":{"tmdb":9,"name":"X"}},
              {"type":"movie","release_type":"release","start":"2026-10-30","title":"Popular Film","tmdb":5},
              {"type":"episode","release_type":"episode","start":"2026-10-30","title":"Popular Show","show_tmdb":6,"season_number":2,"episode_number":1}
            ]}"""
        ).events
        val origins = mdbListCalendarCandidates(mdbListCalendarItems(events)).associate { it.name to it.origin }
        assertEquals(CalendarOrigin.MDBLIST_CALENDAR_SAVED, origins["Watchlist Film"])
        assertEquals(CalendarOrigin.MDBLIST_CALENDAR, origins["Watching"])
        assertTrue(origins["Cast And Watching"] != CalendarOrigin.MDBLIST_CALENDAR_EXTRA)
        assertEquals(CalendarOrigin.MDBLIST_CALENDAR_EXTRA, origins["Fav Cast Film"])
        assertEquals(CalendarOrigin.MDBLIST_CALENDAR_EXTRA, origins["Popular Film"])
        assertEquals(CalendarOrigin.MDBLIST_CALENDAR_EXTRA, origins["Popular Show"])
    }

    @Test fun onlyTitlesFollowedJustForMdbListExtrasStayOutOfUpcoming() {
        val extra = CalendarTitleRecord("movie:tmdb:5", CalendarTitleType.MOVIE, CalendarIds(tmdb = 5), origins = setOf(CalendarOrigin.MDBLIST_CALENDAR_EXTRA))
        assertTrue(nuvioCCalendarOnly(extra))
        assertFalse(nuvioCCalendarOnly(extra.copy(origins = setOf(CalendarOrigin.MDBLIST_CALENDAR_EXTRA, CalendarOrigin.LIBRARY))))
        assertFalse(nuvioCCalendarOnly(extra.copy(origins = setOf(CalendarOrigin.MDBLIST_CALENDAR_EXTRA, CalendarOrigin.BELL))))
        assertFalse(nuvioCCalendarOnly(extra.copy(origins = emptySet())))
    }
}
