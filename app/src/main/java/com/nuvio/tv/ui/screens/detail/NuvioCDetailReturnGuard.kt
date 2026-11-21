package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 57b `DETAIL_RETURN_GUARD`.
//
// Coming back to a details page from Sources (Play, Play manually, a held episode card), the page is rebuilt and its
// rows reload (watched marks, progress). Focus was put back on Play or the episode, then lost again when a row
// rebuilt, and Compose moved it to the nearest thing it could find: a season tab or a random card. For a short
// window after the return, the target keeps asking for focus until the first key press, so a rebuild can't steal it.

import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

object NuvioCDetailReturnGuard {
    @Volatile private var until = 0L

    /** Starts the window (called when a restore begins after coming back). */
    fun arm(ms: Long = 2_500L) {
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_RETURN_GUARD) until = System.currentTimeMillis() + ms
    }

    /** Any key press ends it: from then on focus is the user's. */
    fun cancel() { until = 0L }

    val active: Boolean get() = System.currentTimeMillis() < until

    /** Keeps [target] focused while the window is open (a no-op when it already has focus). */
    suspend fun hold(target: () -> FocusRequester?) {
        while (active) {
            target()?.let { r -> runCatching { r.requestFocus() } }
            delay(120)
        }
    }
}
