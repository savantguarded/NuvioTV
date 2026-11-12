package com.nuvio.tv

import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.computeTasteInsights
import com.nuvio.tv.data.nuvioc.computeTasteReview
import com.nuvio.tv.data.nuvioc.computeTasteStats
import com.nuvio.tv.data.nuvioc.tasteMonthWindow
import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** [fork] Nuvio C round 41: card titles, Month in review, recent backdrops and Spin posters. */
class NuvioCRound41Test {
    private val zone = ZoneId.of("Africa/Lagos")
    private val now = LocalDate.of(2026, 11, 12).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private fun film(i: Int, genre: String, year: Int) = NuvioCTasteTitle(
        key = "movie:tt$i", isShow = false, tmdbId = i, imdbId = "tt$i", name = "Film $i", year = year,
        genres = listOf(genre), countries = listOf(if (i % 3 == 0) "GB" else "US"), runtime = 120,
        vote = 7.5, votes = 6_000, lang = if (i % 4 == 0) "ko" else "en", studios = listOf("A24"),
        poster = "https://image.tmdb.org/t/p/w342/p$i.jpg", backdrop = "https://image.tmdb.org/t/p/w1280/b$i.jpg", schema = 2
    )

    private fun history(n: Int, startDaysAgo: Long) = (1..n).map { i ->
        WatchedItem(contentId = "tt$i", contentType = "movie", title = "Film $i", watchedAt = now - (startDaysAgo - i) * day)
    }

    @Test fun everyCardKeepsTheTitlesItCounted() {
        val titles = (1..40).associate { "movie:tt$it" to film(it, if (it % 2 == 0) "Crime" else "Drama", 1960 + (it % 6) * 10) }
        val cards = computeTasteInsights(history(40, 60), titles, emptyMap(), Long.MIN_VALUE..now, now, zone, true)
        assertTrue(cards.isNotEmpty())
        cards.forEach { assertTrue("${it.id} has no titles", it.keys.isNotEmpty()) }
        val language = cards.first { it.id == "language" }
        assertEquals(10, language.keys.size) // every 4th of 40 is Korean
        assertTrue(language.keys.all { titles.getValue(it).lang == "ko" })
    }

    @Test fun reviewNeedsTenTitlesAndReadsTheMonth() {
        val month = YearMonth.of(2026, 11)
        val window = tasteMonthWindow(month, zone)
        val titles = (1..12).associate { "movie:tt$it" to film(it, "Thriller", 2020) }
        // 12 films between 1 and 12 November, two on the 9th
        val lines = (1..12).map { i ->
            val d = if (i == 12) 9 else i
            WatchedItem(contentId = "tt$i", contentType = "movie", title = "Film $i",
                watchedAt = LocalDate.of(2026, 11, d).atTime(20, 0).atZone(zone).toInstant().toEpochMilli())
        }
        val review = computeTasteReview(lines, titles, window, month, true, zone)
        assertNotNull(review)
        review!!
        assertEquals(12, review.titles)
        assertEquals(24, review.hours)
        assertEquals("Thriller", review.topGenre)
        assertEquals(LocalDate.of(2026, 11, 9), review.biggestDay)
        assertEquals("Mostly thrillers, and a long Monday", review.headline) // 4 h on Monday the 9th
        assertEquals(12, review.posters.size)
        assertTrue(review.posters.all { "/w154/" in it })
        assertNull(computeTasteReview(lines.take(9), titles, window, month, true, zone))
    }

    @Test fun statsKeepRecentBackdropsAndSpinPosters() {
        val titles = (1..20).associate { "movie:tt$it" to film(it, if (it <= 15) "Action" else "Drama", 2021) }
        val s = computeTasteStats(history(20, 30), titles, emptyMap(), NuvioCTasteRange.ALL, now, zone)
        assertEquals(5, s.recentBackdrops.size)
        assertTrue(s.recentBackdrops.first().endsWith("/b20.jpg")) // the most recent title first
        assertEquals(3, s.spinPosters.size)
        assertTrue(s.spinPosters.all { "/w154/" in it })
    }
}
