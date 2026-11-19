package com.nuvio.tv.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** [fork] Nuvio C round 52 calendar_streaming_estimates / calendar_sources. */
class NuvioCRound52CalendarSourcesTest {
    @Test
    fun usualHours() {
        val day = LocalDate.of(2026, 11, 20)
        assertEquals(LocalTime.of(3, 0), calendarUsualStreamingTime("Paramount+", day, ZoneId.of("America/New_York")))
        assertEquals(LocalTime.of(6, 0), calendarUsualStreamingTime("Hulu", day, ZoneId.of("Africa/Lagos")))
        assertNull(calendarUsualStreamingTime("Netflix", day, ZoneId.of("Africa/Lagos")))
    }

    @Test
    fun tvdbTimeWins() {
        val tvdb = CalendarEntry(season = 1, episode = 2, airInstantMs = 2_000L, airDate = "2026-11-20", source = CalendarSource.TVDB)
        val trakt = CalendarEntry(season = 1, episode = 2, airInstantMs = 1_000L, airDate = "2026-11-20", source = CalendarSource.TRAKT_NEXT)
        val merged = mergeCalendarEntries(listOf(trakt, tvdb), mapOf(CalendarSource.TRAKT_NEXT to 5L, CalendarSource.TVDB to 1L))
        assertEquals(CalendarSource.TVDB, merged.single().source)
    }
}
