package com.nuvio.tv

import com.nuvio.tv.data.nuvioc.NuvioCTasteInsight
import com.nuvio.tv.data.nuvioc.NuvioCTasteNextAir
import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.computeTasteInsights
import com.nuvio.tv.data.nuvioc.computeTasteWeek
import com.nuvio.tv.data.nuvioc.pickTasteSpotlightV3
import com.nuvio.tv.data.nuvioc.tasteBecauseTitle
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
