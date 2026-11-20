package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 54 (switch TASTE_MONTHS_3): fixed three-letter month and day names. Android's CLDR data gives
// four letters for some ("Sept"), so Taste uses its own list.

import com.nuvio.tv.NuvioCFeatures
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val MONTHS3 = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAYS3 = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

internal fun Month.nuvioC3(): String =
    if (NuvioCFeatures.TASTE_MONTHS_3) MONTHS3[ordinal] else getDisplayName(TextStyle.SHORT, Locale.US)

internal fun DayOfWeek.nuvioC3(): String =
    if (NuvioCFeatures.TASTE_MONTHS_3) DAYS3[ordinal] else getDisplayName(TextStyle.SHORT, Locale.US)
