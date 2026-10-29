package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.NuvioCFeatures
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// [fork] Nuvio C round 31. Kept in its own file so ysosrs123's calendar files only carry short hook lines.
// - calendar_no_week: Week view removed; List and Month only. A saved or chosen Week opens Month.
// - calendar_month_wide: only the month on show (other months' days are blank), the grid sits between the
//   month arrows and is wider, the day list is 15% narrower, and moving around never leaves the month
//   (the arrows change month). OK on a day moves focus into the day list.
// - calendar_back_leaves: Back no longer jumps to the List / Month buttons; Up from the top gets there.

// ---- calendar_no_week ----

/** [this] with Week replaced by Month while Week is removed. */
internal fun CalendarViewMode.nuvioCNoWeek(): CalendarViewMode =
    if (NuvioCFeatures.CALENDAR_NO_WEEK && this == CalendarViewMode.WEEK) CalendarViewMode.MONTH else this

/** The view buttons in the header, in order. */
internal val nuvioCCalendarModes: List<CalendarViewMode>
    get() = if (NuvioCFeatures.CALENDAR_NO_WEEK) {
        listOf(CalendarViewMode.LIST, CalendarViewMode.MONTH)
    } else {
        CalendarViewMode.entries
    }

/** The "Start in" choices in settings (Week left out while it's removed). */
internal val nuvioCCalendarStarts: List<NuvioCCalendarStart>
    get() = NuvioCCalendarStart.entries.filter { !(NuvioCFeatures.CALENDAR_NO_WEEK && it == NuvioCCalendarStart.WEEK) }

// ---- calendar_month_wide ----

/** Share of the row the grid takes; the day list takes the rest (0.38 before, now 15% narrower). */
internal val NUVIO_C_MONTH_GRID_WEIGHT: Float get() = if (NuvioCFeatures.CALENDAR_MONTH_WIDE) 0.677f else 0.62f
internal val NUVIO_C_MONTH_LIST_WEIGHT: Float get() = if (NuvioCFeatures.CALENDAR_MONTH_WIDE) 0.323f else 0.38f

/** Whether days of the next / previous month are drawn in the grid. */
internal val nuvioCMonthShowsOtherMonths: Boolean get() = !NuvioCFeatures.CALENDAR_MONTH_WIDE

/**
 * Round 26 month moves, kept inside the month: Left on the 1st stays put (the month arrows change month),
 * Right on the last day stays unless the day list is the next stop (last column, handled by the caller).
 */
internal fun nuvioCMonthMoveInside(
    focused: LocalDate,
    month: YearMonth,
    firstDay: DayOfWeek,
    key: CalendarMonthKey,
    held: Boolean
): CalendarMonthMove {
    val move = nuvioCMonthMove(focused, month, firstDay, key, held)
    if (!NuvioCFeatures.CALENDAR_MONTH_WIDE) return move
    return when (move) {
        is CalendarMonthMove.To -> if (YearMonth.from(move.date) == month) move else CalendarMonthMove.To(focused)
        is CalendarMonthMove.Leave -> move
    }
}

// ---- calendar_back_leaves ----

/** Whether Back on a card jumps to the view buttons (off while Back leaves the page). */
internal val nuvioCBackToTabs: Boolean get() = !NuvioCFeatures.CALENDAR_BACK_LEAVES
