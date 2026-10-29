package com.nuvio.tv.ui.screens.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class NuvioCRound32CalendarTest {
    private val month = YearMonth.of(2026, 10) // Oct 2026 starts on a Thursday
    private val monday = DayOfWeek.MONDAY

    private fun move(day: Int, key: CalendarMonthKey, held: Boolean = false) =
        nuvioCMonthMoveRound32(month.atDay(day), month, monday, key, held)

    @Test fun rightOnLastColumnStays() {
        assertEquals(CalendarMonthMove.To(month.atDay(4)), move(4, CalendarMonthKey.RIGHT)) // Sun 4th
    }

    @Test fun leftOnFirstColumnLeavesOnceButNotHeld() {
        assertEquals(CalendarMonthMove.Leave(CalendarMonthKey.LEFT), move(5, CalendarMonthKey.LEFT)) // Mon 5th
        assertEquals(CalendarMonthMove.To(month.atDay(5)), move(5, CalendarMonthKey.LEFT, held = true))
    }

    @Test fun leftInsideRowMovesOneDay() {
        assertEquals(CalendarMonthMove.To(month.atDay(6)), move(7, CalendarMonthKey.LEFT))
    }

    @Test fun upArrowFollowsColumn() {
        assertFalse(nuvioCMonthUpToRight(0))
        assertFalse(nuvioCMonthUpToRight(3))
        assertTrue(nuvioCMonthUpToRight(4))
        assertTrue(nuvioCMonthUpToRight(6))
    }

    @Test fun onlyNeededWeeks() {
        val grid = calendarMonthGrid(YearMonth.of(2027, 2), monday) // Feb 2027: Mon 1st to Sun 28th
        assertEquals(4, nuvioCMonthWeeks(grid, YearMonth.of(2027, 2)).size)
        assertEquals(5, nuvioCMonthWeeks(calendarMonthGrid(month, monday), month).size)
    }
}
