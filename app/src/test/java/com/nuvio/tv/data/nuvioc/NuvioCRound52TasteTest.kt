package com.nuvio.tv.data.nuvioc

import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** [fork] Nuvio C round 52 taste_tabs: the tabs' numbers. */
class NuvioCRound52TasteTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(d: LocalDate, hour: Int = 21) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val titles = mapOf(
        "movie:tt1" to NuvioCTasteTitle(key = "movie:tt1", isShow = false, tmdbId = 1, name = "Long Film", runtime = 200, genres = listOf("Drama"), studios = listOf("A24")),
        "tv:tt2" to NuvioCTasteTitle(key = "tv:tt2", isShow = true, tmdbId = 2, name = "Show", runtime = 30, genres = listOf("Comedy"), studios = listOf("HBO"))
    )

    @Test
    fun streaksCountRunsAndToday() {
        val today = LocalDate.of(2026, 11, 19)
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(10), today.minusDays(11))
        val (current, longest, _) = tasteStreaks(days, today)
        assertEquals(3, current)
        assertEquals(3, longest)
        assertEquals(0, tasteStreaks(emptySet(), today).first)
    }

    @Test
    fun factsAddUp() {
        val d = LocalDate.of(2026, 11, 10)
        val history = listOf(
            WatchedItem("tt1", "movie", "Long Film", watchedAt = at(d)),
            WatchedItem("tt1", "movie", "Long Film", watchedAt = at(d.plusDays(1))), // rewatch
            WatchedItem("tt2", "series", "Show", 1, 1, watchedAt = at(d, 1)),
            WatchedItem("tt2", "series", "Show", 1, 2, watchedAt = at(d, 1))
        )
        val f = computeTasteFacts(history, titles, Long.MIN_VALUE..at(d.plusDays(2)), at(d.plusDays(2)), zone)
        assertEquals(1, f.films)
        assertEquals(1, f.rewatches)
        assertEquals(2, f.episodes)
        assertEquals(400, f.filmMinutes)
        assertEquals(60, f.showMinutes)
        assertEquals(2, f.byHour[1])
        assertEquals("Long Film" to 200, f.longestFilm)
        assertTrue(f.studios.any { it.first == "A24" })
    }

    @Test
    fun wrappedForAYearAndNothingForAnEmptyOne() {
        val d = LocalDate.of(2025, 6, 1)
        val history = (0 until 12).map { WatchedItem("tt2", "series", "Show", 1, it + 1, watchedAt = at(d.plusDays(it.toLong()), 23)) }
        val w = computeTasteWrapped(history, titles, 2025, zone)!!
        assertEquals(12, w.episodes)
        assertEquals(12, w.longestStreak)
        assertEquals(3, w.peakPart) // late at night
        assertEquals("Show", w.topShows.first().first)
        assertEquals(360, w.byMonth[5])
        assertNull(computeTasteWrapped(history, titles, 2024, zone))
        assertEquals(listOf(2025), tasteWrappedYears(history, zone))
    }
}
