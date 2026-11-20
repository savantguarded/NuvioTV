package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.core.calendar.CalendarTitleType
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarWeekModelTest {
    private val monday = LocalDate.of(2026, 10, 19)

    private fun episode(show: String, s: Int, e: Int, date: LocalDate, time: LocalTime? = LocalTime.of(20, 0)) = CalendarRow(
        key = "series:imdb:$show|S${s}E$e",
        titleKey = "series:imdb:$show",
        name = show,
        poster = null,
        contentId = show,
        type = CalendarTitleType.SERIES,
        kind = CalendarRowKind.EPISODE,
        season = s,
        episode = e,
        date = date,
        time = time
    )

    private fun film(name: String, date: LocalDate) = CalendarRow(
        key = "movie:imdb:$name|MOVIE_DIGITAL",
        titleKey = "movie:imdb:$name",
        name = name,
        poster = null,
        contentId = name,
        type = CalendarTitleType.MOVIE,
        kind = CalendarRowKind.FILM_DIGITAL,
        date = date
    )







    @Test fun nextFromListsTheShowsLaterEpisodes() {
        val e8 = episode("tt1", 2, 8, monday)
        val days = mapOf(
            monday to listOf(e8, episode("tt2", 1, 1, monday)),
            monday.plusWeeks(1) to listOf(episode("tt1", 2, 9, monday.plusWeeks(1))),
            monday.plusWeeks(2) to listOf(episode("tt1", 2, 10, monday.plusWeeks(2))),
            monday.plusWeeks(3) to listOf(episode("tt1", 2, 11, monday.plusWeeks(3))),
            monday.minusWeeks(1) to listOf(episode("tt1", 2, 7, monday.minusWeeks(1)))
        )
        val next = calendarNextFrom(calendarRowsByTitle(days), e8)
        assertEquals(listOf(9, 10), next.map { it.episode })
        assertTrue(calendarNextFrom(calendarRowsByTitle(days), episode("tt1", 2, 11, monday.plusWeeks(3))).isEmpty())
    }

    @Test fun theMosaicFollowsHowManyTitlesAreOn() {
        fun mosaic(n: Int) = calendarMosaic((1..n).map { episode("tt$it", 1, 1, monday) })
        assertEquals(CalendarMosaicLayout.NONE, calendarMosaic(null).layout)
        assertEquals(CalendarMosaicLayout.FULL, mosaic(1).layout)
        assertEquals(CalendarMosaicLayout.SPLIT, mosaic(2).layout)
        assertEquals(CalendarMosaicLayout.GRID, mosaic(3).layout)
        assertEquals(4, mosaic(4).tiles.size)
        assertEquals(0, mosaic(4).more)
        assertEquals(3, mosaic(6).tiles.size)
        assertEquals(3, mosaic(6).more)
    }

    @Test fun aShowWithSeveralEpisodesTakesOneTile() {
        val rows = listOf(episode("tt1", 1, 1, monday), episode("tt1", 1, 2, monday), episode("tt2", 1, 1, monday))
        assertEquals(CalendarMosaicLayout.SPLIT, calendarMosaic(rows).layout)
    }
}
