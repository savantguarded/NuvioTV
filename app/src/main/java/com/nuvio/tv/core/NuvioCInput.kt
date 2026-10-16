package com.nuvio.tv.core

import android.os.SystemClock

// [fork] Nuvio C bg_trailer_smooth (2026-10-16): when the user last pressed a key (any key, set from
// MainActivity.onUserInteraction). Lets background work that briefly blocks the UI thread, like the
// trailer's black-bar sampling, wait until the remote has been still for a moment.
object NuvioCInput {
    @Volatile
    private var lastAtMs: Long = 0L

    fun onInteraction() {
        lastAtMs = SystemClock.uptimeMillis()
    }

    /** True if a key was pressed in the last [windowMs] ms (focus moving, list scrolling). */
    fun recentlyActive(windowMs: Long): Boolean =
        SystemClock.uptimeMillis() - lastAtMs < windowMs
}
