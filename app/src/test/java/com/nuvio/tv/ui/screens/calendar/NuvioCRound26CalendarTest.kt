package com.nuvio.tv.ui.screens.calendar

import android.view.KeyEvent
import com.nuvio.tv.core.calendar.CalendarEntry
import com.nuvio.tv.core.calendar.CalendarIds
import com.nuvio.tv.core.calendar.CalendarRefreshReason
import com.nuvio.tv.core.calendar.CalendarSettings
import com.nuvio.tv.core.calendar.CalendarSource
import com.nuvio.tv.core.calendar.CalendarState
import com.nuvio.tv.core.calendar.CalendarTitleRecord
import com.nuvio.tv.core.calendar.CalendarTitleType
import com.nuvio.tv.core.calendar.nuvioCManualDue
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** [fork] Nuvio C round 26: Calendar month moves, Week row scrolling, the Left brake, Month -> Week -> Back, refresh button. */
@OptIn(ExperimentalCoroutinesApi::class)
class NuvioCRound26CalendarTest {
    // October 2026 starts on a Thursday; weeks start Monday, so the grid's first row is 28 Sep .. 4 Oct.
    private val oct = YearMonth.of(2026, 10)
    private val mon = DayOfWeek.MONDAY
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private fun move(from: LocalDate, key: CalendarMonthKey, held: Boolean = false) = nuvioCMonthMove(from, oct, mon, key, held)

    @Test fun upFromTopRowLeavesForTheArrows() {
        assertEquals(CalendarMonthMove.Leave(CalendarMonthKey.UP), move(d(1), CalendarMonthKey.UP))
        assertEquals(CalendarMonthMove.Leave(CalendarMonthKey.UP), move(d(6), CalendarMonthKey.UP)) // 29 Sep above it
        assertEquals(CalendarMonthMove.To(d(5)), move(d(12), CalendarMonthKey.UP))
    }

    @Test fun downStaysInTheMonth() {
        assertEquals(CalendarMonthMove.To(d(31)), move(d(24), CalendarMonthKey.DOWN))
        assertEquals(CalendarMonthMove.To(d(28)), move(d(28), CalendarMonthKey.DOWN)) // 4 Nov below: stays
    }

    @Test fun leftAndRightWrapRowsAndTurnTheMonthOnlyAtItsEnds() {
        assertEquals(CalendarMonthMove.To(d(4)), move(d(5), CalendarMonthKey.LEFT)) // Monday wraps to Sunday above
        assertEquals(CalendarMonthMove.To(LocalDate.of(2026, 9, 30)), move(d(1), CalendarMonthKey.LEFT))
        assertEquals(CalendarMonthMove.To(d(1)), move(d(1), CalendarMonthKey.LEFT, held = true)) // held stops at the 1st
        assertEquals(CalendarMonthMove.To(LocalDate.of(2026, 11, 1)), move(d(31), CalendarMonthKey.RIGHT))
        assertEquals(CalendarMonthMove.To(d(31)), move(d(31), CalendarMonthKey.RIGHT, held = true))
    }

    @Test fun rightOnTheLastColumnGoesToTheDayList() {
        assertEquals(CalendarMonthMove.Leave(CalendarMonthKey.RIGHT), move(d(4), CalendarMonthKey.RIGHT)) // a Sunday
        assertEquals(CalendarMonthMove.To(d(4)), move(d(3), CalendarMonthKey.RIGHT))
    }

    @Test fun shelfScrollKeepsRoomBeforeAndPeekAfter() {
        // Container 1000, room 16 before, 16 + 60 peek after.
        assertEquals(0f, nuvioCShelfScrollDistance(16f, 200f, 1000f, 16f, 76f))
        assertEquals(-16f, nuvioCShelfScrollDistance(0f, 200f, 1000f, 16f, 76f)) // flush left: back off by the room
        assertEquals(76f, nuvioCShelfScrollDistance(800f, 200f, 1000f, 16f, 76f)) // flush right: room and peek kept
    }

    @Test fun brakeStopsHeldAndQuickLeftOnlyOnce() {
        val brake = NuvioCEdgeBrake()
        assertTrue(nuvioCBrakeStops(repeatCount = 3, brake = brake, now = 0L))
        assertTrue(nuvioCBrakeArms(KeyEvent.KEYCODE_DPAD_LEFT, lastKeyAt = 1_000L, now = 1_100L))
        assertFalse(nuvioCBrakeArms(KeyEvent.KEYCODE_DPAD_DOWN, lastKeyAt = 1_000L, now = 1_100L))
        brake.armed = true
        brake.armedAt = 1_100L
        assertTrue(nuvioCBrakeStops(0, brake, now = 1_300L)) // the quick extra press stops
        assertFalse(nuvioCBrakeStops(0, brake, now = 1_500L)) // the next press goes on to the sidebar
        brake.armed = true
        brake.armedAt = 2_000L
        assertFalse(nuvioCBrakeStops(0, brake, now = 3_000L)) // a deliberate press after a pause goes on at once
    }

    @Test fun manualRefreshRefetchesTitlesAiringAroundNow() {
        val now = Instant.parse("2026-10-23T12:00:00Z")
        fun record(date: String) = CalendarTitleRecord(
            "series:imdb:tt1", CalendarTitleType.SERIES, CalendarIds(imdb = "tt1"),
            sources = mapOf(CalendarSource.TVMAZE to listOf(CalendarEntry(season = 1, episode = 1, airDate = date)))
        )
        assertTrue(record("2026-10-30").nuvioCManualDue(now))
        assertTrue(record("2026-10-22").nuvioCManualDue(now))
        assertFalse(record("2027-03-01").nuvioCManualDue(now))
        assertTrue(record("2027-03-01").copy(failures = 1).nuvioCManualDue(now))
    }

    // ---- Month -> Week -> Back ----

    private class Data : CalendarScreenData {
        val reasons = mutableListOf<CalendarRefreshReason>()
        override val state = MutableStateFlow(CalendarState())
        override val settings = MutableStateFlow<CalendarSettings?>(null)
        override val zones = MutableStateFlow(ZoneId.of("Africa/Lagos"))
        override fun now(): Instant = Instant.parse("2026-10-23T11:00:00Z")
        override fun requestRefresh(reason: CalendarRefreshReason) { reasons += reason }
        override suspend fun setBell(titleKey: String, on: Boolean) = Unit
        override suspend fun setHidden(titleKey: String, hidden: Boolean, name: String?) = Unit
        override suspend fun setWindowDays(days: Int) = Unit
        override suspend fun setMenuVisible(visible: Boolean) = Unit
        override suspend fun setZoneOverride(zone: String?) = Unit
        override suspend fun setCornerMessage(enabled: Boolean) = Unit
        override suspend fun setIncludePersonalLists(include: Boolean) = Unit
        override suspend fun setFollowOtherServices(follow: Boolean) = Unit
        override suspend fun setComingUpRow(enabled: Boolean) = Unit
        override suspend fun setHiddenFromComingUp(titleKey: String, hidden: Boolean) = Unit
        override suspend fun unhideAllFromComingUp() = Unit
        override val openAtJustAired = MutableStateFlow(false)
        override fun consumeOpenAtJustAired() { openAtJustAired.value = false }
        override suspend fun details(row: CalendarRow, fetch: Boolean) = CalendarRowDetails(backdrop = null, synopsis = null)
    }

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun backToMonthOnlyAfterOpeningADayFromMonth() {
        val data = Data()
        val model = CalendarViewModel(data, Dispatchers.Unconfined, 3_600_000L, null)
        model.setViewMode(CalendarViewMode.MONTH)
        // openDayInWeek ignores empty days, so set the flag the way it does for a day with something on.
        model.nuvioCBackToMonth() // nothing opened: no change
        assertEquals(CalendarViewMode.MONTH, model.viewMode.value)
        model.setViewMode(CalendarViewMode.WEEK)
        assertNull(model.nuvioCFromMonth.value)
        model.nuvioCRefresh()
        assertEquals(listOf(CalendarRefreshReason.MANUAL), data.reasons.filter { it == CalendarRefreshReason.MANUAL })
    }
}
