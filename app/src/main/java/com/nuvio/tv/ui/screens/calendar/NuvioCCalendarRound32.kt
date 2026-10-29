package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.NuvioCFeatures
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// [fork] Nuvio C round 32. Own file so ysosrs123's calendar files only carry short hook lines.
// - calendar_month_keys: Left / Right stay in the week's row (no wrapping to the line above / below); Right on the
//   last column stays (OK opens the day list); Left on the first column leaves to the sidebar on a single press,
//   held Left stays; Up from the top week goes to the nearer month arrow; Back in the day list returns to the grid.
// - calendar_month_fill: only the weeks the month needs.

/** Round 31 month move with the round 32 row rules on top. */
internal fun nuvioCMonthMoveRound32(
    focused: LocalDate,
    month: YearMonth,
    firstDay: DayOfWeek,
    key: CalendarMonthKey,
    held: Boolean
): CalendarMonthMove {
    val base = nuvioCMonthMoveInside(focused, month, firstDay, key, held)
    if (!NuvioCFeatures.CALENDAR_MONTH_KEYS) return base
    val column = nuvioCMonthColumn(focused, month, firstDay) ?: return base
    return when (key) {
        CalendarMonthKey.LEFT -> when {
            column == 0 -> if (held) CalendarMonthMove.To(focused) else CalendarMonthMove.Leave(key)
            else -> base
        }
        CalendarMonthKey.RIGHT -> if (column == 6) CalendarMonthMove.To(focused) else base
        else -> base
    }
}

/** Column (0 = first weekday) of [date] in the month grid, or null when it isn't this month's day. */
internal fun nuvioCMonthColumn(date: LocalDate, month: YearMonth, firstDay: DayOfWeek): Int? {
    if (YearMonth.from(date) != month) return null
    return ((date.dayOfWeek.value - firstDay.value) + 7) % 7
}

/** Up from the top week: the right arrow from the last three columns, else the left one. */
internal fun nuvioCMonthUpToRight(column: Int?): Boolean = column != null && column >= 4

/** The grid's weeks: all six, or only those holding a day of [month]. */
internal fun nuvioCMonthWeeks(grid: List<LocalDate>, month: YearMonth): List<List<LocalDate>> {
    val weeks = grid.chunked(7)
    if (!NuvioCFeatures.CALENDAR_MONTH_FILL || nuvioCMonthShowsOtherMonths) return weeks
    return weeks.filter { week -> week.any { YearMonth.from(it) == month } }
}
