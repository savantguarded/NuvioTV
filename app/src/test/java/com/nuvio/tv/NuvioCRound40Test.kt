package com.nuvio.tv

import com.nuvio.tv.data.nuvioc.NuvioCRoulettePool
import com.nuvio.tv.data.nuvioc.NuvioCRouletteRefine
import com.nuvio.tv.data.nuvioc.NuvioCTasteCollection
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.NuvioCTasteVisual
import com.nuvio.tv.data.nuvioc.computeTasteInsights
import com.nuvio.tv.data.nuvioc.computeTasteRecaps
import com.nuvio.tv.data.nuvioc.computeTasteStats
import com.nuvio.tv.data.nuvioc.pickTasteSpotlight
import com.nuvio.tv.data.nuvioc.tasteMonthWindow
import com.nuvio.tv.data.nuvioc.NuvioCRatingService
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.ui.screens.taste.NuvioCTasteCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** [fork] Nuvio C round 40: Taste insights, recaps, month windows, Roulette popularity, Trakt rating dates. */
class NuvioCRound40Test {
    private val zone = ZoneId.of("Africa/Lagos")
    private val now = LocalDate.of(2026, 11, 12).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private fun film(i: Int, genre: String, year: Int, collection: Int? = null) = NuvioCTasteTitle(
        key = "movie:tt$i", isShow = false, tmdbId = i, imdbId = "tt$i", name = "Film $i", year = year,
        genres = listOf(genre), countries = listOf(if (i % 3 == 0) "GB" else "US"), runtime = 100 + (i % 5) * 10,
        vote = 7.5, votes = 6_000, lang = if (i % 4 == 0) "ko" else "en", studios = listOf("A24"),
        collectionId = collection, schema = 2
    )

    private fun history(n: Int, startDaysAgo: Long) = (1..n).map { i ->
        WatchedItem(contentId = "tt$i", contentType = "movie", title = "Film $i", watchedAt = now - (startDaysAgo - i) * day)
    }

    @Test fun lightHistoryGivesNoSpotlight() {
        val titles = (1..3).associate { "movie:tt$it" to film(it, "Crime", 1995) }
        val cards = computeTasteInsights(history(3, 10), titles, emptyMap(), Long.MIN_VALUE..now, now, zone, true)
        assertTrue(cards.none { it.id == "eras" || it.id == "countries" || it.id == "crowd" })
    }

    @Test fun fullHistoryHasCoreCardsAndRatingsNeedRatings() {
        val titles = (1..40).associate { "movie:tt$it" to film(it, if (it % 2 == 0) "Crime" else "Drama", 1960 + (it % 6) * 10) }
        val noRatings = computeTasteInsights(history(40, 60), titles, emptyMap(), Long.MIN_VALUE..now, now, zone, true)
        val ids = noRatings.map { it.id }.toSet()
        assertTrue("eras" in ids && "countries" in ids && "runtime" in ids && "mainstream" in ids && "language" in ids && "studio" in ids)
        assertTrue("crowd" !in ids && "ratings" !in ids) // Nuvio Sync only: no rating cards
        val scores = (1..40).associate { "movie:tt$it" to 6 }
        val rated = computeTasteInsights(history(40, 60), titles, scores, Long.MIN_VALUE..now, now, zone, true)
        val crowd = rated.first { it.id == "crowd" }
        assertEquals("1.5 below TMDB on average", crowd.value)
        assertTrue(crowd.visual is NuvioCTasteVisual.Versus)
    }

    @Test fun franchiseNeedsTheCollectionParts() {
        val titles = (1..12).associate { "movie:tt$it" to film(it, "Action", 2000, collection = if (it <= 3) 99 else null) }
        val parts = listOf(1 to "One", 2 to "Two", 3 to "Three", 500 to "Four", 501 to "Five")
        val cards = computeTasteInsights(history(12, 30), titles, emptyMap(), Long.MIN_VALUE..now, now, zone, true,
            mapOf(99 to NuvioCTasteCollection(99, "Saga Collection", parts)))
        val f = cards.first { it.id == "franchise" }
        assertEquals("3 of 5 Saga films", f.value)
        assertEquals("Next: Four", f.line)
    }

    @Test fun spotlightIsStableForASeedAndPrefersTheHour() {
        val titles = (1..40).associate { "movie:tt$it" to film(it, "Crime", 1990 + it % 3 * 10) }
        val all = computeTasteInsights(history(40, 60), titles, emptyMap(), Long.MIN_VALUE..now, now, zone, true)
        val a = pickTasteSpotlight(all, 42L, 22, false)
        val b = pickTasteSpotlight(all, 42L, 22, false)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(minOf(6, all.size), a.size)
        assertEquals(a.size, a.map { it.id }.toSet().size)
    }

    @Test fun recapsSkipThinAndCurrentMonths() {
        val oct = YearMonth.of(2026, 10)
        val octStart = oct.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val lines = (1..12).map { WatchedItem("tt$it", "movie", "F", watchedAt = octStart + it * day) } +
            (13..15).map { WatchedItem("tt$it", "movie", "F", watchedAt = octStart - 20 * day) } +
            (16..30).map { WatchedItem("tt$it", "movie", "F", watchedAt = now - day) }
        val titles = (1..30).associate { "movie:tt$it" to film(it, "Crime", 2000) }
        val recaps = computeTasteRecaps(lines, titles, now, zone)
        assertEquals(listOf(oct), recaps.map { it.month })
        assertEquals(12, recaps.first().titles)
        assertEquals("Crime", recaps.first().topGenre)
        val month = computeTasteStats(lines, titles, emptyMap(), NuvioCTasteRange.ALL, now, zone, window = tasteMonthWindow(oct, zone))
        assertEquals(12, month.titles)
        assertEquals("October was mostly crime.", NuvioCTasteCopy.monthSummary(month, oct))
    }

    @Test fun pageSummaryForLightProfiles() {
        val titles = (1..3).associate { "movie:tt$it" to film(it, "Comedy", 2010) }
        val s = computeTasteStats(history(3, 5), titles, emptyMap(), NuvioCTasteRange.ALL, now, zone)
        assertEquals("Three titles in. Comedy leads so far.", NuvioCTasteCopy.pageSummary(s))
        val none = computeTasteStats(emptyList(), titles, emptyMap(), NuvioCTasteRange.ALL, now, zone)
        assertEquals("Nothing watched on this profile yet.", NuvioCTasteCopy.pageSummary(none))
    }

    @Test fun popularityLevelsLowerTheFloor() {
        val wide = NuvioCRoulettePool.WIDE.floor("movie", false, 1990)
        val cuts = NuvioCRoulettePool.CUTS.floor("movie", false, 1990)
        assertTrue(cuts.first < NuvioCRoulettePool.DEEP.floor("movie", false, 1990).first)
        assertTrue(cuts.first < wide.first && cuts.second < wide.second)
        val r = NuvioCRouletteRefine(pool = NuvioCRoulettePool.CUTS)
        assertEquals(NuvioCRoulettePool.CUTS, NuvioCRouletteRefine.decode(r.encode()).pool)
        assertEquals(0, r.active) // popularity isn't a filter
    }

    @Test fun traktRatedAtIsParsedPerTitle() {
        val body = """[{"rated_at":"2026-11-02T20:00:00.000Z","rating":9,"type":"movie","movie":{"ids":{"imdb":"tt0113277","tmdb":949}}},
            {"rating":7,"type":"show","show":{"ids":{"tmdb":1}}}]"""
        val dates = NuvioCRatingService.parseTraktRatedAt(body)
        assertNotNull(dates["movie:tt0113277"])
        assertNull(dates["show:tmdb1"])
        assertEquals(1, dates.size)
    }
}
