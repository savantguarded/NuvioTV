package com.nuvio.tv

import com.nuvio.tv.core.calendar.CalendarIds
import com.nuvio.tv.core.calendar.CalendarTitleType
import com.nuvio.tv.core.calendar.withBellForTitle
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.NuvioCWatchFilter
import com.nuvio.tv.data.nuvioc.computeTasteStats
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.ui.screens.player.NuvioCSubtitleRows
import com.nuvio.tv.ui.screens.taste.NuvioCTasteCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class NuvioCRound39Test {

    // ---- bell_sync ----

    @Test fun bellOnWritesEveryIdAndOffClearsThemAll() {
        val ids = CalendarIds(imdb = "tt0944947", tmdb = 1399)
        val on = emptySet<String>().withBellForTitle(CalendarTitleType.SERIES, ids, true)
        assertEquals(setOf("series:imdb:tt0944947", "series:tmdb:1399"), on)
        // a key the other screen wrote under the TMDB id alone is cleared too
        val mixed = on + "series:tmdb:1399"
        assertTrue(mixed.withBellForTitle(CalendarTitleType.SERIES, CalendarIds(imdb = "tt0944947", tmdb = 1399), false).isEmpty())
        // a different title stays
        val other = on + "series:imdb:tt1234567"
        assertEquals(setOf("series:imdb:tt1234567"), other.withBellForTitle(CalendarTitleType.SERIES, ids, false))
    }

    // ---- subtitle_panel_clean ----

    @Test fun subtitleTokensAreHidden() {
        assertTrue(NuvioCSubtitleRows.looksLikeToken("v3_e1_ASjL1QVNZKYvOpXOaqv1K6G5GgOVxGfTxuPflOwi2IMYuhKoO6eoKok0Qg5Wfk3"))
        assertTrue(NuvioCSubtitleRows.looksLikeToken("1957263718"))
        assertFalse(NuvioCSubtitleRows.looksLikeToken("Clarksons.Farm.S03E01 WEB"))
        assertFalse(NuvioCSubtitleRows.looksLikeToken("SDH"))
        assertEquals("SRT", NuvioCSubtitleRows.format("https://x.y/sub/123.srt?x=1"))
        assertEquals("ASS", NuvioCSubtitleRows.format("https://x.y/dl?format=ass"))
        assertNull(NuvioCSubtitleRows.format("https://x.y/dl/abc"))
    }

    // ---- roulette_watched_cycle ----

    @Test fun watchFilterCycles() {
        assertEquals(NuvioCWatchFilter.WATCHED, NuvioCWatchFilter.UNWATCHED.next())
        assertEquals(NuvioCWatchFilter.ALL, NuvioCWatchFilter.WATCHED.next())
        assertEquals(NuvioCWatchFilter.UNWATCHED, NuvioCWatchFilter.ALL.next())
    }

    // ---- taste_page ----

    private val zone = ZoneId.of("Africa/Lagos")
    private val now = 1_790_000_000_000L // Sept 2026
    private val day = 24L * 60 * 60 * 1000

    private fun film(i: Int, genre: String, year: Int, vote: Double) = NuvioCTasteTitle(
        key = "movie:tt$i", isShow = false, tmdbId = i, imdbId = "tt$i", name = "Film $i", year = year,
        genres = listOf(genre), runtime = 120, vote = vote, votes = 5000
    )

    @Test fun emptyHistoryIsEmpty() {
        val s = computeTasteStats(emptyList(), emptyMap(), emptyMap(), NuvioCTasteRange.ALL, now, zone)
        assertTrue(s.empty)
        assertEquals(NuvioCTasteCopy.empty(), NuvioCTasteCopy.summary(s))
    }

    @Test fun lightHistoryHedges() {
        val titles = (1..4).associate { "movie:tt$it" to film(it, "Thriller", 1995, 7.0) }
        val history = (1..4).map { WatchedItem("tt$it", "movie", "Film $it", watchedAt = now - it * day) }
        val s = computeTasteStats(history, titles, emptyMap(), NuvioCTasteRange.ALL, now, zone)
        assertFalse(s.showNumbers)
        assertEquals("Early days, but you're leaning towards thrillers.", NuvioCTasteCopy.summary(s))
    }

    @Test fun fullHistorySummaryAndCritic() {
        val titles = (1..30).associate { "movie:tt$it" to film(it, if (it <= 20) "Crime" else "Comedy", 1990 + (it % 10), 7.5) }
        val history = (1..30).map { WatchedItem("tt$it", "movie", "Film $it", watchedAt = now - it * day) }
        val scores = (1..30).associate { "movie:tt$it" to 6 } // 1.5 below the crowd everywhere
        val s = computeTasteStats(history, titles, scores, NuvioCTasteRange.ALL, now, zone)
        assertTrue(s.showNumbers)
        assertTrue(s.showRatings)
        assertEquals("Crime", s.genres.first().name)
        assertEquals(1990, s.topDecade)
        assertEquals(60, s.hours)
        assertEquals("Crime-heavy, 90s-leaning, tougher critic than the crowd.", NuvioCTasteCopy.summary(s))
        assertTrue(NuvioCTasteCopy.ratingLine(s)!!.startsWith("You rate 1.5 below the crowd"))
    }

    @Test fun noRatingsUsesWhatYouPick() {
        val titles = (1..12).associate { "movie:tt$it" to film(it, "Drama", 2012, 7.4) }
        val history = (1..12).map { WatchedItem("tt$it", "movie", "Film $it", watchedAt = now - it * day) }
        val s = computeTasteStats(history, titles, emptyMap(), NuvioCTasteRange.ALL, now, zone)
        assertFalse(s.showRatings)
        assertEquals("You pick titles the crowd rates 7.4 on average, above the 6.6 norm.", NuvioCTasteCopy.pickLine(s))
        assertEquals("Drama-heavy, with a soft spot for the 2010s.".substringBefore(","), NuvioCTasteCopy.summary(s).substringBefore(","))
    }
}
