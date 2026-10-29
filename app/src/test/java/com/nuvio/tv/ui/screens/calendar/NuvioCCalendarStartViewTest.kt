package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.core.calendar.CalendarRefreshReason
import com.nuvio.tv.core.calendar.CalendarSettings
import com.nuvio.tv.core.calendar.CalendarState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** [fork] Nuvio C calendar_start_view */
@OptIn(ExperimentalCoroutinesApi::class)
class NuvioCCalendarStartViewTest {
    private class Data : CalendarScreenData {
        override val state = MutableStateFlow(CalendarState())
        override val settings = MutableStateFlow<CalendarSettings?>(null)
        override val zones = MutableStateFlow(ZoneId.of("Africa/Lagos"))
        override fun now(): Instant = Instant.parse("2026-10-21T11:00:00Z")
        override fun requestRefresh(reason: CalendarRefreshReason) = Unit
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

    private class Prefs(start: NuvioCCalendarStart, var last: CalendarViewMode = CalendarViewMode.WEEK) : NuvioCCalendarViewPrefs {
        private val _start = MutableStateFlow(start)
        override val start: StateFlow<NuvioCCalendarStart> get() = _start
        override fun setStart(start: NuvioCCalendarStart) { _start.value = start }
        override fun lastUsed() = last
        override fun rememberUsed(mode: CalendarViewMode) { last = mode }
    }

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(prefs: Prefs?) = CalendarViewModel(Data(), Dispatchers.Unconfined, 3_600_000L, prefs)

    @Test fun withoutPrefsStartsInWeekLikeYsos() = assertEquals(CalendarViewMode.WEEK.nuvioCNoWeek(), vm(null).viewMode.value) // [fork] calendar_no_week

    @Test fun lastUsedOpensWhereYouLeftAndRemembers() {
        val prefs = Prefs(NuvioCCalendarStart.LAST_USED, last = CalendarViewMode.MONTH)
        val model = vm(prefs)
        assertEquals(CalendarViewMode.MONTH, model.viewMode.value)
        model.setViewMode(CalendarViewMode.LIST)
        assertEquals(CalendarViewMode.LIST, prefs.last)
        model.onScreenShown()
        assertEquals(CalendarViewMode.LIST, model.viewMode.value)
    }

    @Test fun fixedChoiceAppliesEachTimeThePageOpens() {
        val prefs = Prefs(NuvioCCalendarStart.LIST)
        val model = vm(prefs)
        assertEquals(CalendarViewMode.LIST, model.viewMode.value)
        model.setViewMode(CalendarViewMode.MONTH)
        model.onScreenShown()
        assertEquals(CalendarViewMode.LIST, model.viewMode.value)
    }
}
