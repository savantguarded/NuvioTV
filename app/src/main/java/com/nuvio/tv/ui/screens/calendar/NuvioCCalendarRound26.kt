package com.nuvio.tv.ui.screens.calendar

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// [fork] Nuvio C round 26. Kept in its own file so ysosrs123's calendar files only carry short hook lines.
// - calendar_refresh_button: the round refresh button after Week / List / Month.
// - calendar_week_edges: room around Week rows, a peek of the next card, scrolling that keeps both.
// - calendar_edge_brake: Left that runs onto the first item stops there; one more press goes on to the sidebar.
// - calendar_month_nav: Month moves that stay inside the month.

// ---- calendar_refresh_button ----

/** Same height as the view buttons beside it. */
private val REFRESH_SIZE = 40.dp

@Composable
internal fun NuvioCCalendarRefreshButton(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val turn = remember { Animatable(0f) }
    // Turns only while a refresh runs (no frame callbacks otherwise); a turn in progress finishes before it stops.
    LaunchedEffect(refreshing) {
        while (refreshing) {
            turn.animateTo(360f, tween(durationMillis = 900, easing = LinearEasing))
            turn.snapTo(0f)
        }
        if (turn.value != 0f) {
            turn.animateTo(360f, tween(durationMillis = ((360f - turn.value) / 360f * 900).toInt(), easing = LinearEasing))
            turn.snapTo(0f)
        }
    }
    CalendarRoundButton(
        icon = Icons.Default.Refresh,
        description = stringResource(R.string.nuvio_c_calendar_refresh),
        performance = true,
        onClick = { if (!refreshing) onRefresh() },
        size = REFRESH_SIZE,
        modifier = modifier.graphicsLayer { rotationZ = turn.value }
    )
}

// ---- calendar_week_edges ----

internal const val NUVIO_C_WEEK_ACROSS = 4.3f
internal val NUVIO_C_WEEK_ROOM: Dp = 16.dp

/**
 * Scrolls a Week row only as far as it takes to show the focused card with [startPx] free before it and [endPx]
 * after it (the focus room, plus the peek of the next card on the right), so neither the ring nor the peek is lost.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class NuvioCShelfScroll(private val startPx: Float, private val endPx: Float) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = spring()
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        nuvioCShelfScrollDistance(offset, size, containerSize, startPx, endPx)
}

internal fun nuvioCShelfScrollDistance(offset: Float, size: Float, containerSize: Float, startPx: Float, endPx: Float): Float {
    val lo = startPx
    val hi = containerSize - endPx
    val end = offset + size
    return when {
        size > hi - lo || offset < lo -> offset - lo
        end > hi -> end - hi
        else -> 0f
    }
}

// ---- calendar_edge_brake ----

/** One per view: remembers the last key and arms the first item when Left brought focus onto it. */
internal class NuvioCEdgeBrake {
    internal var lastKey = 0
    internal var lastKeyAt = 0L
    internal var armed = false
    internal var armedAt = 0L
}

@Composable
internal fun rememberNuvioCEdgeBrake(): NuvioCEdgeBrake = remember { NuvioCEdgeBrake() }

/** The Calendar page's brake; its tracker sits on the page, the edges on the first items of each row. */
internal val LocalNuvioCEdgeBrake = staticCompositionLocalOf { NuvioCEdgeBrake() }

private const val BRAKE_WINDOW_MS = 600L
/** A Left this long after arriving is a deliberate press and goes on to the sidebar. */
private const val BRAKE_HOLD_MS = 700L

/** On a parent of the whole view: sees every key before the view's own handlers. */
internal fun Modifier.nuvioCBrakeTrack(brake: NuvioCEdgeBrake): Modifier =
    if (!NuvioCFeatures.CALENDAR_EDGE_BRAKE) this else onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.action == AndroidKeyEvent.ACTION_DOWN) {
            brake.lastKey = native.keyCode
            brake.lastKeyAt = SystemClock.uptimeMillis()
        }
        false
    }

/**
 * On the first item of a row: held Left always stops here; when quick Left presses ran onto it, the next quick one
 * stops too; a press after a short pause (or onto an item reached any other way) goes on to the sidebar.
 */
internal fun Modifier.nuvioCBrakeEdge(brake: NuvioCEdgeBrake): Modifier =
    if (!NuvioCFeatures.CALENDAR_EDGE_BRAKE) this else this
        .onFocusChanged { state ->
            if (state.hasFocus) {
                val now = SystemClock.uptimeMillis()
                brake.armed = nuvioCBrakeArms(brake.lastKey, brake.lastKeyAt, now)
                brake.armedAt = now
            }
        }
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.keyCode != AndroidKeyEvent.KEYCODE_DPAD_LEFT || native.action != AndroidKeyEvent.ACTION_DOWN) {
                return@onPreviewKeyEvent false
            }
            nuvioCBrakeStops(native.repeatCount, brake, SystemClock.uptimeMillis())
        }

internal fun nuvioCBrakeArms(lastKey: Int, lastKeyAt: Long, now: Long): Boolean =
    lastKey == AndroidKeyEvent.KEYCODE_DPAD_LEFT && now - lastKeyAt <= BRAKE_WINDOW_MS

/** True = the Left press is used up here: a held key, or the first quick press after Left ran onto this item. */
internal fun nuvioCBrakeStops(repeatCount: Int, brake: NuvioCEdgeBrake, now: Long): Boolean {
    if (repeatCount > 0) return true
    val stop = brake.armed && now - brake.armedAt < BRAKE_HOLD_MS
    brake.armed = false
    return stop
}

// ---- calendar_month_nav ----

/**
 * Month moves that stay in the month: Up / Down move a week while that is still in the month (Up off the top leaves
 * for the month arrows, Down off the bottom stays put); Left / Right move a day and wrap rows, turning the month only
 * from the 1st or the last day, and a held key stops at that edge. Right on the last column leaves for the day's list.
 */
internal fun nuvioCMonthMove(
    focused: LocalDate,
    month: YearMonth,
    firstDay: DayOfWeek,
    key: CalendarMonthKey,
    held: Boolean
): CalendarMonthMove {
    val grid = calendarMonthGrid(month, firstDay)
    val index = grid.indexOf(focused).takeIf { it >= 0 && YearMonth.from(focused) == month }
        ?: return CalendarMonthMove.To(month.atDay(1))
    val column = index % 7
    fun inMonth(date: LocalDate) = YearMonth.from(date) == month
    fun day(to: LocalDate) = if (!inMonth(to) && held) CalendarMonthMove.To(focused) else CalendarMonthMove.To(to)
    return when (key) {
        CalendarMonthKey.LEFT -> day(focused.minusDays(1))
        CalendarMonthKey.RIGHT -> if (column == 6) CalendarMonthMove.Leave(key) else day(focused.plusDays(1))
        CalendarMonthKey.UP -> focused.minusWeeks(1).let { if (inMonth(it)) CalendarMonthMove.To(it) else CalendarMonthMove.Leave(key) }
        CalendarMonthKey.DOWN -> focused.plusWeeks(1).let { if (inMonth(it)) CalendarMonthMove.To(it) else CalendarMonthMove.To(focused) }
    }
}
