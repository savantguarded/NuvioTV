package com.nuvio.tv.data.nuvioc

import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** [fork] Nuvio C round 53 taste_tabs_v2: Overview, Stats strip and the year view. */
class NuvioCRound53TasteTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(d: LocalDate, hour: Int = 21) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val titles = mapOf(
        "movie:tt1" to NuvioCTasteTitle(key = "movie:tt1", isShow = false, tmdbId = 1, name = "New Film", year = 2026, runtime = 120, lang = "en", countries = listOf("US"),
            directors = listOf(NuvioCTastePerson(10, "Dir A"))),
        "movie:tt3" to NuvioCTasteTitle(key = "movie:tt3", isShow = false, tmdbId = 3, name = "Old Film", year = 1972, runtime = 175, lang = "en", countries = listOf("US"),
            directors = listOf(NuvioCTastePerson(11, "Dir B"))),
        "tv:tt2" to NuvioCTasteTitle(key = "tv:tt2", isShow = true, tmdbId = 2, name = "Ended Show", year = 2025, runtime = 30, episodes = 3, ended = true, lang = "ko", countries = listOf("KR")),
        "tv:tt4" to NuvioCTasteTitle(key = "tv:tt4", isShow = true, tmdbId = 4, name = "Running Show", year = 2024, runtime = 50, episodes = 10, ended = false, lang = "en")
    )
    private val d = LocalDate.of(2026, 11, 10)
    private val history = listOf(
        WatchedItem("tt1", "movie", "New Film", watchedAt = at(d)),
        WatchedItem("tt3", "movie", "Old Film", watchedAt = at(d.minusDays(30))),
        WatchedItem("tt2", "series", "Ended Show", 1, 1, watchedAt = at(d, 10)),
        WatchedItem("tt2", "series", "Ended Show", 1, 2, watchedAt = at(d, 11)),
        WatchedItem("tt2", "series", "Ended Show", 1, 3, watchedAt = at(d, 12)),
        WatchedItem("tt4", "series", "Running Show", 1, 1, watchedAt = at(d.minusDays(1)))
    )

    @Test
    fun overviewCountsWhatWasWatched() {
        val now = at(d.plusDays(1))
        val o = computeTasteOverview(history, titles, Long.MIN_VALUE..now, now, zone)
        assertEquals(4, o.titles)
        assertEquals(2, o.films)
        assertEquals(2, o.shows)
        assertEquals(4, o.episodes)
        assertEquals("Ended Show", o.mostShow?.name)
        assertEquals(3, o.mostShow?.episodes)
        assertEquals(1, o.finishedShows)
        assertEquals(1, o.inProgressShows)
        assertEquals(0, o.upToDateShows)
        assertEquals("Old Film" to 1972, o.oldest)
        // binge: three episodes of one show in a day; a single episode is no binge
        assertEquals(1, o.binges.size)
        assertEquals("3 episodes", o.binges.first().sub)
        // biggest day: the film plus three episodes on the 10th
        assertEquals(d, o.biggestDay?.date)
        assertEquals(120 + 90, o.biggestDay?.minutes)
        assertNull(o.prevMinutes) // All time has nothing before it
    }

    @Test
    fun freshMeansWithinAYearOfRelease() {
        val now = at(d.plusDays(1))
        val o = computeTasteOverview(history, titles, Long.MIN_VALUE..now, now, zone)
        // New Film (2026 watched 2026) and Ended Show (2025 watched 2026) are fresh; Old Film and Running Show (2024) aren't
        assertEquals(50, o.freshPercent)
    }

    @Test
    fun monthComparesWithTheMonthBefore() {
        val nov = java.time.YearMonth.of(2026, 11)
        val window = tasteMonthWindow(nov, zone)
        val prev = tastePreviousWindow(window, zone, at(d.plusDays(1)))
        assertEquals("October", prev?.second)
        val o = computeTasteOverview(history, titles, window, at(d.plusDays(1)), zone)
        assertEquals(175, o.prevMinutes) // Old Film on 11 October
    }

    @Test
    fun stripNamesLanguagesAndRuntimes() {
        val s = computeTasteStatsStrip(history, titles, Long.MIN_VALUE..Long.MAX_VALUE)
        assertEquals("English", s.languages.first().first)
        assertEquals(75, s.languages.first().second)
        assertEquals(2, s.countries)
        assertEquals((120 + 175) / 2, s.avgFilmMinutes)
        assertEquals("Old Film" to 175, s.longestFilm)
        assertEquals(40, s.avgEpisodeMinutes)
    }

    @Test
    fun yearViewRanksAndCountsNewThings() {
        val y = computeTasteYear(history, titles, mapOf("movie:tt1" to 8), 2026, zone, listOf(2026))!!
        assertEquals("Ended Show", y.topShows.first().name)
        assertEquals("New Film", y.topFilms.first().name)
        assertTrue(y.topFilms.first().sub.startsWith("8/10"))
        assertEquals(2, y.newShows)
        assertEquals(2, y.newDirectors)
        assertEquals(listOf(2026 to y.hours), y.hoursByYear)
        assertNull(computeTasteYear(history, titles, emptyMap(), 2019, zone, listOf(2026)))
    }

    @Test
    fun nothingLeaksAcrossWindows() {
        val now = at(d.plusDays(1))
        val empty = computeTasteOverview(history, titles, (now + 1)..(now + 2), now, zone)
        assertTrue(empty.empty)
        assertEquals(0, empty.minutes)
    }
}
