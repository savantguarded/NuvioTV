package com.nuvio.tv.core.calendar

import java.time.Duration
import java.time.Instant

// [fork] Nuvio C round 26 `calendar_refresh_button`: what the Calendar's refresh button asks again.
// Trakt's and MDBList's calendars are fetched at once (CalendarRefresher), and on top of the titles that are
// due anyway, every title with something airing in the last two days or the next 30, or whose last fetch
// failed, is fetched again. Titles further out keep their schedule, so a press costs tens of requests, not 300.

private val MANUAL_BEHIND: Duration = Duration.ofDays(2)
private val MANUAL_AHEAD: Duration = Duration.ofDays(30)

/** True when the refresh button should fetch this title again although it is not due. */
fun CalendarTitleRecord.nuvioCManualDue(now: Instant): Boolean {
    if (failures > 0) return true
    val from = now.minus(MANUAL_BEHIND)
    val to = now.plus(MANUAL_AHEAD)
    return entries.any { entry -> entry.sortInstant()?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
}
