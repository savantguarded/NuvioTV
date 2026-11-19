package com.nuvio.tv.core.nuvioc

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NuvioCRound51TagsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 19) // a Thursday
    private val en = Locale.US

    private fun tag(f: NuvioCTagFacts) = NuvioCFeaturedTagRules.tag(f, today, zone, en)

    @Test fun airsToday_withTime() {
        val ms = Instant.parse("2026-11-19T21:00:00Z").toEpochMilli()
        val f = NuvioCTagFacts(isSeries = true, next = NuvioCTagEpisode(2, 4, today, instantMs = ms))
        assertEquals("NEW EPISODE TODAY · 9:00 PM", tag(f))
    }

    @Test fun airsToday_withoutTime() {
        val f = NuvioCTagFacts(isSeries = true, next = NuvioCTagEpisode(1, 2, today))
        assertEquals("NEW EPISODE TODAY", tag(f))
    }

    @Test fun finaleThisWeek() {
        val f = NuvioCTagFacts(isSeries = true, next = NuvioCTagEpisode(2, 10, today.plusDays(2), finale = true))
        assertEquals("SEASON FINALE SATURDAY", tag(f))
    }

    @Test fun nextEpisodeTomorrow_andWeekday() {
        assertEquals("NEW EPISODE TOMORROW", tag(NuvioCTagFacts(true, next = NuvioCTagEpisode(1, 3, today.plusDays(1)))))
        assertEquals("NEW EPISODE MONDAY", tag(NuvioCTagFacts(true, next = NuvioCTagEpisode(1, 3, today.plusDays(4)))))
    }

    @Test fun wholeSeasonOut() {
        val f = NuvioCTagFacts(
            isSeries = true,
            latestSeason = 2,
            latestSeasonAirDate = today.minusDays(5),
            seasonEpisodes = mapOf(2 to 8),
            last = NuvioCTagEpisode(2, 8, today.minusDays(5))
        )
        assertEquals("SEASON 2 · ALL 8 EPISODES OUT", tag(f))
    }

    @Test fun weeklySeasonIsNotWholeSeason() {
        val f = NuvioCTagFacts(
            isSeries = true,
            latestSeason = 2,
            latestSeasonAirDate = today.minusDays(20),
            seasonEpisodes = mapOf(2 to 8),
            last = NuvioCTagEpisode(2, 3, today.minusDays(6)),
            next = NuvioCTagEpisode(2, 4, today.plusDays(1))
        )
        assertEquals("NEW EPISODE TOMORROW", tag(f))
    }

    @Test fun premiereAndReturn() {
        assertEquals("SEASON 3 PREMIERES MONDAY", tag(NuvioCTagFacts(true, next = NuvioCTagEpisode(3, 1, today.plusDays(4)))))
        assertEquals("SEASON 3 RETURNS MAR 14", tag(NuvioCTagFacts(true, returning = true, next = NuvioCTagEpisode(3, 1, LocalDate.of(2027, 3, 14)))))
        assertEquals("RETURNS MAR 14", tag(NuvioCTagFacts(true, returning = true, next = NuvioCTagEpisode(3, 4, LocalDate.of(2027, 3, 14)))))
    }

    @Test fun filmNew_andLibraryFallback_andNothing() {
        assertEquals("NEW RELEASE", tag(NuvioCTagFacts(false, releaseDate = today.minusDays(10))))
        assertEquals("IN YOUR LIBRARY", tag(NuvioCTagFacts(false, releaseDate = today.minusDays(400), inLibrary = true)))
        assertNull(tag(NuvioCTagFacts(false, releaseDate = today.minusDays(400))))
    }

    @Test fun contextBeatsLibrary() {
        val f = NuvioCTagFacts(true, next = NuvioCTagEpisode(1, 2, today), inLibrary = true)
        assertEquals("NEW EPISODE TODAY", tag(f))
    }
}
