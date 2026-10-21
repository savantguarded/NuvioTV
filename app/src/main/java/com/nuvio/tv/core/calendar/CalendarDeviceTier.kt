package com.nuvio.tv.core.calendar

import com.nuvio.tv.ui.screens.settings.MemoryBudget
import javax.inject.Inject
import javax.inject.Singleton

// [nuvio c] ysosrs123's version also reads his visual-quality setting (Performance / Enhanced / Maximum),
// which this app doesn't have; here a small heap alone counts as a weak box. Same numbers as his.

/** A small heap counts as weak: two titles fetched at a time, else four. */
fun calendarRefreshConcurrency(lowRamTier: Boolean): Int = if (lowRamTier) 2 else 4

@Singleton
class CalendarDeviceTier @Inject constructor() {
    suspend fun refreshConcurrency(): Int = calendarRefreshConcurrency(MemoryBudget.isLowRamTier)
}
