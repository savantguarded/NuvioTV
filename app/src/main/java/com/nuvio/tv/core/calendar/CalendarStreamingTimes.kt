package com.nuvio.tv.core.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// [fork] Nuvio C round 52 `calendar_streaming_estimates` (ysosrs123 243c909, matched on the network name because the
// fork's records keep no "streaming" flag): New York time at which a service puts out new episodes, for the services
// that keep to one hour. Netflix, Prime Video, Disney+, Apple TV and HBO Max vary too much to guess.
private val USUAL_RELEASE_NEW_YORK = mapOf(
    "paramount+" to LocalTime.of(3, 0),
    "paramount+ with showtime" to LocalTime.of(3, 0),
    "paramount plus" to LocalTime.of(3, 0),
    "hulu" to LocalTime.MIDNIGHT,
    "peacock" to LocalTime.MIDNIGHT,
    "peacock premium" to LocalTime.MIDNIGHT
)

private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

/** The service's usual hour on [date] in [zone]; null for other services or when that hour falls on another day there. */
fun calendarUsualStreamingTime(service: String?, date: LocalDate, zone: ZoneId): LocalTime? {
    val time = USUAL_RELEASE_NEW_YORK[service?.trim()?.lowercase()] ?: return null
    val local = date.atTime(time).atZone(NEW_YORK).withZoneSameInstant(zone)
    return local.toLocalTime().takeIf { local.toLocalDate() == date }
}
