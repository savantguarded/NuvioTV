package com.nuvio.tv.core.calendar

// [fork] Nuvio C round 37 `calendar_name_guard`: the Calendar's titles could keep a show name taken from a saved
// watch-progress entry that held release text (the file or stream name, e.g. "Sweetpea S01E03"). Round 34 fixed the
// Upcoming row; the Calendar builds its own list, so the same guard runs here: a name that looks like release text
// loses to any clean name (your Library, TVmaze, TMDB), and a title stored with one is looked up again on the next
// refresh, which puts the right name in the List and Month views, the day panel and reminders.

import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.screens.home.NuvioCNameGuard

internal fun nuvioCCalendarReleaseName(name: String?): Boolean =
    NuvioCFeatures.CALENDAR_NAME_GUARD && NuvioCNameGuard.looksLikeRelease(name)

/** [preferred] unless it is missing or release text and [fallback] is a clean name. */
internal fun nuvioCCalendarBetterName(preferred: String?, fallback: String?): String? {
    val p = preferred?.takeIf { it.isNotBlank() }
    val f = fallback?.takeIf { it.isNotBlank() }
    if (p == null) return f
    if (f != null && nuvioCCalendarReleaseName(p) && !nuvioCCalendarReleaseName(f)) return f
    return p
}
