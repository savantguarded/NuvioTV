package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.core.calendar.CalendarTitleType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NuvioCRound51SeasonDropTest {
    private fun ep(title: String, season: Int, episode: Int, date: String = "2026-11-20") = CalendarRow(
        key = "$title:$season:$episode", titleKey = title, name = title, poster = null, contentId = null,
        type = CalendarTitleType.SERIES, kind = CalendarRowKind.EPISODE, season = season, episode = episode,
        episodeTitle = "e$episode", date = LocalDate.parse(date)
    )

    @Test fun wholeSeasonBecomesOneRow() {
        val out = calendarGroupSeasons((1..6).map { ep("A", 5, it) })
        assertEquals(1, out.size)
        assertEquals(1, out[0].episode); assertEquals(6, out[0].episodeEnd); assertNull(out[0].episodeTitle)
    }

    @Test fun twoEpisodesStaySeparate() {
        assertEquals(2, calendarGroupSeasons(listOf(ep("A", 1, 1), ep("A", 1, 2))).size)
    }

    @Test fun differentDaysOrTitlesAreNotMerged() {
        val rows = listOf(ep("A", 1, 1), ep("A", 1, 2), ep("A", 1, 3, "2026-11-27"), ep("B", 1, 1), ep("B", 1, 2), ep("B", 1, 3))
        val out = calendarGroupSeasons(rows)
        assertEquals(4, out.size) // A e1, A e2, A e3 (other day), B e1-3
        assertEquals(3, out.last().episodeEnd)
    }
}
