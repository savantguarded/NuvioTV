package com.nuvio.tv

import com.nuvio.tv.data.nuvioc.NuvioCTasteInsight
import com.nuvio.tv.data.nuvioc.NuvioCTasteNextAir
import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.computeTasteInsights
import com.nuvio.tv.data.nuvioc.computeTasteWeek
import com.nuvio.tv.data.nuvioc.pickTasteSpotlightV3
import com.nuvio.tv.data.nuvioc.tasteAirLabel
import com.nuvio.tv.data.nuvioc.tasteBecauseTitle
import com.nuvio.tv.data.nuvioc.tasteDuration
import com.nuvio.tv.data.nuvioc.tasteRatingCompare
import com.nuvio.tv.data.nuvioc.tasteWeekCompare
import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** [fork] Nuvio C round 46: Taste This week, Spotlight picks, copy. */
class NuvioCRound46Test {
    private val zone = ZoneId.of("Africa/Lagos")
    // Wednesday 12 November 2026, 21:00
    private val now = LocalDate.of(2026, 11, 11).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private fun film(i: Int) = NuvioCTasteTitle(
        key = "movie:tt$i", isShow = false, tmdbId = i, imdbId = "tt$i", name = "Film $i", year = 2020,
        genres = listOf("Drama"), countries = listOf("US"), runtime = 100, vote = 7.5, votes = 6_000,
        poster = "https://image.tmdb.org/t/p/w342/p$i.jpg", schema = 2
    )

    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun weekComparesWithTheSamePointOfEarlierWeeks() {
        val titles = (1..20).associate { "movie:tt$it" to film(it) }
        val monday = LocalDate.of(2026, 11, 9)
        // this week: three films (Mon, Tue, Wed); each earlier week: one film on its Monday, and one on its Saturday
        val lines = mutableListOf(
            WatchedItem(contentId = "tt1", contentType = "movie", title = "1", watchedAt = at(monday, 20)),
            WatchedItem(contentId = "tt2", contentType = "movie", title = "2", watchedAt = at(monday.plusDays(1), 20)),
            WatchedItem(contentId = "tt3", contentType = "movie", title = "3", watchedAt = at(monday.plusDays(2), 20))
        )
        (1..4).forEach { w ->
            lines += WatchedItem(contentId = "tt${10 + w}", contentType = "movie", title = "x", watchedAt = at(monday.minusWeeks(w.toLong()), 20))
            lines += WatchedItem(contentId = "tt${15 + w}", contentType = "movie", title = "y", watchedAt = at(monday.minusWeeks(w.toLong()).plusDays(5), 20))
        }
        val week = computeTasteWeek(lines, titles, emptyMap(), emptyMap(), now, zone)
        assertEquals(300, week.minutes)
        assertEquals(100, week.usualMinutes) // Saturdays haven't happened yet this week, so they don't count
        assertEquals("3 h 20 m more than usual", tasteWeekCompare(week))
    }

    @Test fun ratingAverageThisMonthAgainstLast() {
        val scores = mapOf("movie:tt1" to 8, "movie:tt2" to 10, "movie:tt3" to 6)
        val dates = mapOf(
            "movie:tt1" to at(LocalDate.of(2026, 11, 2), 20),
            "movie:tt2" to at(LocalDate.of(2026, 11, 5), 20),
            "movie:tt3" to at(LocalDate.of(2026, 10, 20), 20)
        )
        val week = computeTasteWeek(emptyList(), emptyMap(), scores, dates, now, zone)
        assertEquals(4.5, week.avgThisMonth!!, 0.001)
        assertEquals(3.0, week.avgLastMonth!!, 0.001)
        assertEquals("Up from 3.0 last month", tasteRatingCompare(week))
    }

    @Test fun labelsReadNaturally() {
        assertEquals("6 h 40 m", tasteDuration(400))
        assertEquals("45 m", tasteDuration(45))
        val tonight = NuvioCTasteNextAir(at(LocalDate.of(2026, 11, 11), 22), true, "Show", "S2 E4")
        assertEquals("Tonight 10:00 pm", tasteAirLabel(tonight, now, zone))
        val thu = NuvioCTasteNextAir(at(LocalDate.of(2026, 11, 13), 21), true, "Show", null)
        assertEquals("Fri 9:00 pm", tasteAirLabel(thu, now, zone))
        assertEquals("Because you gave Heat 5 stars", tasteBecauseTitle("Heat", 10))
        assertNull(tasteBecauseTitle("Heat", 8))
    }

    @Test fun lastVisitsCardsSitOutWhileOthersExist() {
        val all = (1..14).map { NuvioCTasteInsight("c$it", "K", "v$it") }
        val last = (1..6).map { "c$it" }.toSet()
        val picked = pickTasteSpotlightV3(all, 42L, 21, false, last, emptySet())
        assertEquals(6, picked.size)
        assertTrue(picked.none { it.id in last })
        // too few others: they come back, still six
        val small = all.take(8)
        assertEquals(6, pickTasteSpotlightV3(small, 7L, 21, false, last, emptySet()).size)
        // same seed, same six
        assertEquals(picked, pickTasteSpotlightV3(all, 42L, 21, false, last, emptySet()))
    }

    @Test fun cardsCarryPostersAndPlainNames() {
        val titles = (1..30).associate { "movie:tt$it" to film(it) }
        val lines = (1..30).map { WatchedItem(contentId = "tt$it", contentType = "movie", title = "$it", watchedAt = now - (40 - it) * day) }
        val cards = computeTasteInsights(lines, titles, emptyMap(), Long.MIN_VALUE..now, now, zone, true)
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.any { it.posters.isNotEmpty() })
        assertTrue(cards.flatMap { it.posters }.all { "/w154/" in it })
    }
}
