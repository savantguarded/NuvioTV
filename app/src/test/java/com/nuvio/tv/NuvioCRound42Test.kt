package com.nuvio.tv

import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.computeTasteRecapDetail
import com.nuvio.tv.data.nuvioc.computeTasteReview
import com.nuvio.tv.data.nuvioc.tasteMonthWindow
import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** [fork] Nuvio C round 42: recap panel numbers and the 5-title Month in review. */
class NuvioCRound42Test {
    private val zone = ZoneId.of("Africa/Lagos")
    private val month = YearMonth.of(2026, 10)
    private fun at(d: Int, h: Int = 20) = LocalDate.of(2026, 10, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun film(i: Int, genre: String) = NuvioCTasteTitle(
        key = "movie:tt$i", isShow = false, tmdbId = i, imdbId = "tt$i", name = "Film $i", year = 2024,
        genres = listOf(genre), runtime = 120, schema = 2
    )
    private val show = NuvioCTasteTitle(key = "tv:tt900", isShow = true, tmdbId = 900, imdbId = "tt900", name = "Farm", genres = listOf("Reality"), runtime = 50, schema = 2)

    private fun lines(): List<WatchedItem> =
        (1..4).map { WatchedItem(contentId = "tt$it", contentType = "movie", title = "Film $it", watchedAt = at(it)) } +
            (1..6).map { e -> WatchedItem(contentId = "tt900", contentType = "series", title = "Farm", season = 3, episode = e, watchedAt = at(18, 10 + e)) } +
            // a September line stays out
            listOf(WatchedItem(contentId = "tt5", contentType = "movie", title = "Film 5",
                watchedAt = LocalDate.of(2026, 9, 30).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()))

    private val titles = (1..5).associate { "movie:tt$it" to film(it, if (it <= 3) "Thriller" else "Drama") } + ("tv:tt900" to show)

    @Test fun recapCountsFilmsEpisodesAndTheBiggestDay() {
        val d = computeTasteRecapDetail(lines(), titles, tasteMonthWindow(month, zone), month, zone)
        assertNotNull(d); d!!
        assertEquals(5, d.titles)
        assertEquals(4, d.films)
        assertEquals(6, d.episodes)
        assertEquals(1, d.shows)
        assertEquals(LocalDate.of(2026, 10, 18), d.biggestDay)
        assertEquals(300, d.biggestDayMinutes)
        assertEquals("Farm", d.mostWatched) // 6 x 50 min beats one 120 min film
        assertEquals("Thriller" to 60, d.genres.first())
        assertEquals(13, d.hours) // 4 x 120 + 6 x 50 = 780 min
    }

    @Test fun reviewShowsFromFiveTitles() {
        val window = tasteMonthWindow(month, zone)
        assertNotNull(computeTasteReview(lines(), titles, window, month, false, zone, minTitles = 5))
        assertNull(computeTasteReview(lines(), titles, window, month, false, zone)) // old 10-title floor
    }
}
