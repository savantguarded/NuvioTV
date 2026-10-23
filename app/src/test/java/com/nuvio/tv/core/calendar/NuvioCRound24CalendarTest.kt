package com.nuvio.tv.core.calendar

import com.nuvio.tv.data.mdblist.nuvioCLibraryPosterFallback
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Round 24: done memory, dated first, old films out, MDBList art, library poster fallback. */
class NuvioCRound24CalendarTest {
    private val now = Instant.parse("2026-10-23T18:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private fun film(imdb: String, airDate: String, fetched: Boolean = true) = CalendarTitleRecord(
        "movie:imdb:$imdb", CalendarTitleType.MOVIE, CalendarIds(imdb = imdb),
        fetchedAtMs = if (fetched) daysAgo(1).toEpochMilli() else 0L,
        sources = mapOf(CalendarSource.TMDB to listOf(CalendarEntry(kind = CalendarEntryKind.MOVIE_DIGITAL, airDate = airDate, source = CalendarSource.TMDB)))
    )

    private fun saved(imdb: String, type: CalendarTitleType = CalendarTitleType.MOVIE, year: Int? = null, at: Long = 1) =
        CalendarTitleCandidate(CalendarIds(imdb = imdb), type, CalendarOrigin.MDBLIST_WATCHLIST, daysAgo(at), year = year)

    @Test fun doneTitlesAreRememberedAfterTheyLeaveTheCalendar() {
        val memory = calendarDoneMemory(emptyMap(), listOf(film("tt1", "1941-10-03"), film("tt2", "2026-12-01")), now)
        assertEquals(setOf("imdb:tt1"), memory.keys)
        assertEquals(now.plus(CALENDAR_DONE_FILM_FOR).toEpochMilli(), memory["imdb:tt1"])
        // Next refresh: tt1 is no longer stored (it went over the cap) but is still known as done.
        val later = now.plus(Duration.ofDays(1))
        val kept = calendarDoneMemory(memory, emptyList(), later)
        assertEquals(memory, kept)
        // It expires, so the title is read again some day.
        assertTrue(calendarDoneMemory(memory, emptyList(), now.plus(Duration.ofDays(31))).isEmpty())
    }

    @Test fun aTitleWithSomethingAheadIsForgottenAsDone() {
        val memory = mapOf("imdb:tt1" to now.plus(Duration.ofDays(20)).toEpochMilli())
        assertTrue(calendarDoneMemory(memory, listOf(film("tt1", "2026-11-20")), now).isEmpty())
        // Not read yet: kept as done.
        assertEquals(memory, calendarDoneMemory(memory, listOf(film("tt1", "2026-11-20", fetched = false)), now))
    }

    @Test fun datedTitlesKeepTheirSlotOverTheCap() {
        val candidates = listOf(
            saved("tt10", at = 1), // newest, no date known
            saved("tt11", CalendarTitleType.SERIES, at = 400), // stored with an episode next week
            CalendarTitleCandidate(CalendarIds(tmdb = 12), CalendarTitleType.SERIES, CalendarOrigin.MDBLIST_CALENDAR_SAVED)
        )
        val live = calendarLiveAliases(
            listOf(CalendarTitleRecord("series:imdb:tt11", CalendarTitleType.SERIES, CalendarIds(imdb = "tt11"),
                sources = mapOf(CalendarSource.TVMAZE to listOf(CalendarEntry(season = 1, episode = 2, airDate = "2026-10-30"))))),
            now
        )
        val result = followCalendarTitles(candidates, CalendarFollowRules(cap = 2, liveAliases = live, datedFirst = true, libraryWins = true), now)
        assertEquals(setOf("series:imdb:tt11", "series:tmdb:12"), result.titles.map { it.key }.toSet())
        assertTrue(result.titles.all { it.dated })
        assertEquals(CalendarLeftOutReason.OVER_CAP, result.leftOut.single().reason)

        val round23 = followCalendarTitles(candidates, CalendarFollowRules(cap = 2, liveAliases = live, datedFirst = false, libraryWins = true), now)
        // Round 23 ranked by last activity: MDBList's calendar title (no activity) lost its slot.
        assertTrue("series:tmdb:12" !in round23.titles.map { it.key })
    }

    @Test fun oldSavedFilmsAreLeftOutUnlessDatedOrBelled() {
        val candidates = listOf(
            saved("tt20", year = 1941),
            saved("tt21", year = 2024),
            saved("tt22", year = 2025),
            saved("tt23", year = null),
            saved("tt24", year = 2019),
            CalendarTitleCandidate(CalendarIds(imdb = "tt24", tmdb = 24), CalendarTitleType.MOVIE, CalendarOrigin.MDBLIST_CALENDAR_SAVED),
            saved("tt25", year = 2010),
            saved("tt26", CalendarTitleType.SERIES, year = 2005)
        )
        val rules = CalendarFollowRules(
            oldFilmsBefore = 2026 - CALENDAR_OLD_FILM_YEARS + 1,
            bellOn = setOf("movie:imdb:tt25"),
            libraryWins = true
        )
        val result = followCalendarTitles(candidates, rules, now)
        assertEquals(setOf("movie:imdb:tt20", "movie:imdb:tt21"), result.leftOut.filter { it.reason == CalendarLeftOutReason.OLD_FILM }.map { it.key }.toSet())
        assertTrue(result.titles.any { it.key == "movie:imdb:tt22" })
        assertTrue(result.titles.any { it.key == "movie:imdb:tt23" })
        assertTrue(result.titles.any { "imdb:tt24" in it.ids.aliases() })
        assertTrue(result.titles.any { it.key == "movie:imdb:tt25" })
        assertTrue(result.titles.any { it.key == "series:imdb:tt26" })
        // Off: nothing is left out for its age.
        assertTrue(followCalendarTitles(candidates, rules.copy(oldFilmsBefore = null), now).leftOut.isEmpty())
    }

    @Test fun releaseYearsAreReadFromReleaseInfo() {
        assertEquals(2019, calendarReleaseYear("2019"))
        assertEquals(2019, calendarReleaseYear("2019-2021"))
        assertEquals(1941, calendarReleaseYear("1941-10-03"))
        assertNull(calendarReleaseYear("12345"))
        assertNull(calendarReleaseYear(null))
    }

    @Test fun mdbListBackdropsAndPathsBecomeArt() {
        val events = json.decodeFromString<MdbListCalendarResponse>(
            """{"events":[
              {"type":"episode","start":"2026-10-30","title":"Show","show_tmdb":30,"season_number":1,"episode_number":2,"backdrop":"/still.jpg"},
              {"type":"show","start":"2026-10-30","title":"Show","tmdb":30,"poster":"/poster.jpg","backdrop":"/show.jpg"}
            ]}"""
        ).events
        val item = mdbListCalendarItems(events).single()
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", item.poster)
        assertEquals("https://image.tmdb.org/t/p/w1280/show.jpg", item.backdrop)

        val record = CalendarTitleRecord("series:tmdb:30", CalendarTitleType.SERIES, CalendarIds(tmdb = 30), contentId = "tmdb:30")
        val applied = applyMdbListCalendar(listOf(record), listOf(item), now).single()
        assertEquals(item.backdrop, applied.backdrop)
        assertEquals(item.poster, applied.poster)
        // Add-on art already read is never replaced.
        val withArt = applyMdbListCalendar(listOf(record.copy(backdrop = "addon.jpg")), listOf(item), now).single()
        assertEquals("addon.jpg", withArt.backdrop)
    }

    @Test fun anImdbIdFoundForATmdbTitleIsKept() {
        val record = CalendarTitleRecord("series:tmdb:30", CalendarTitleType.SERIES, CalendarIds(tmdb = 30), contentId = "tmdb:30")
        val found = record.withImdb("tt30")
        assertEquals("tt30", found.contentId)
        assertEquals("tt30", found.ids.imdb)
        assertEquals("series:tmdb:30", found.key)
        assertEquals("tt30", calendarContentIds(found).first())
        // Next refresh the followed title still says "tmdb:30"; the stored IMDb id wins.
        val followed = CalendarFollowedTitle("series:tmdb:30", CalendarIds(tmdb = 30), CalendarTitleType.SERIES, setOf(CalendarOrigin.MDBLIST_CALENDAR), null, contentId = "tmdb:30")
        assertEquals("tt30", reconcileCalendarRecords(listOf(followed), listOf(found)).single().contentId)
    }

    @Test fun libraryPosterFallbackUsesTheImdbId() {
        assertEquals("https://images.metahub.space/poster/medium/tt12590266/img", nuvioCLibraryPosterFallback("tt12590266"))
        assertNull(nuvioCLibraryPosterFallback(null))
        assertFalse(nuvioCLibraryPosterFallback("12590266") != null)
    }
}
