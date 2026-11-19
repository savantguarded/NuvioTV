package com.nuvio.tv.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Nuvio C round 52 calendar_no_rereleases. */
class NuvioCRound52CalendarTest {
    private fun film(kind: CalendarEntryKind, date: String, src: CalendarSource) = CalendarEntry(kind = kind, airDate = date, source = src)

    @Test
    fun oldFilmWithNewDigitalDateShowsNothingNew() {
        val entries = listOf(
            film(CalendarEntryKind.MOVIE_THEATRICAL, "1999-08-06", CalendarSource.TMDB),
            film(CalendarEntryKind.MOVIE_DIGITAL, "2026-11-18", CalendarSource.MDBLIST)
        )
        val kept = nuvioCDropFilmReReleases(CalendarTitleType.MOVIE, entries)
        assertEquals(listOf("1999-08-06"), kept.map { it.airDate })
    }

    @Test
    fun newFilmKeepsCinemaAndDigital() {
        val entries = listOf(
            film(CalendarEntryKind.MOVIE_THEATRICAL, "2026-08-01", CalendarSource.TMDB),
            film(CalendarEntryKind.MOVIE_DIGITAL, "2026-10-20", CalendarSource.MDBLIST)
        )
        assertEquals(2, nuvioCDropFilmReReleases(CalendarTitleType.MOVIE, entries).size)
    }

    @Test
    fun recordMergeNoLongerLetsTheReReleaseWinTheCinemaSlot() {
        val record = CalendarTitleRecord(
            key = "movie|tmdb:913", type = CalendarTitleType.MOVIE, ids = CalendarIds(tmdb = 913),
            sources = mapOf(
                CalendarSource.TMDB to listOf(film(CalendarEntryKind.MOVIE_THEATRICAL, "1999-08-06", CalendarSource.TMDB)),
                CalendarSource.MDBLIST to listOf(film(CalendarEntryKind.MOVIE_THEATRICAL, "2026-11-20", CalendarSource.MDBLIST))
            )
        )
        assertTrue(record.entries.none { it.airDate == "2026-11-20" })
    }

    @Test
    fun belledFilmKeepsEveryDate() {
        val record = CalendarTitleRecord(
            key = "movie|tmdb:913", type = CalendarTitleType.MOVIE, ids = CalendarIds(tmdb = 913),
            origins = setOf(CalendarOrigin.BELL),
            sources = mapOf(
                CalendarSource.TMDB to listOf(film(CalendarEntryKind.MOVIE_THEATRICAL, "1999-08-06", CalendarSource.TMDB)),
                CalendarSource.MDBLIST to listOf(film(CalendarEntryKind.MOVIE_DIGITAL, "2026-11-20", CalendarSource.MDBLIST))
            )
        )
        assertTrue(record.entries.any { it.airDate == "2026-11-20" })
    }

    @Test
    fun showsAreUntouched() {
        val entries = listOf(CalendarEntry(season = 1, episode = 1, airDate = "2010-01-01"), CalendarEntry(season = 9, episode = 1, airDate = "2026-11-20"))
        assertEquals(2, nuvioCDropFilmReReleases(CalendarTitleType.SERIES, entries).size)
    }
}
