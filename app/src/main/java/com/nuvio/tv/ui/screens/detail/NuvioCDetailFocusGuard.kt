package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 54 (switch DETAILS_FOCUS_GUARD): a safety net for the details page. When nothing on the page holds
// focus (the focused row was scrolled out and disposed, a fast-scroll landing missed, a restore failed), the remote does
// nothing and the page looks stuck. The guard notices within 300 ms and puts focus back on the last row that had it
// (the list remembers it), or the first row on screen. It never acts while the sidebar is open, while another window
// (a dialog) has focus, or while the page isn't the resumed screen. Each recovery is logged: adb logcat -s NuvioCDetailsFocus

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.gestures.animateScrollBy

@Stable
internal class NuvioCDetailFocusGuard {
    var hasFocus by mutableStateOf(false)
    val list = FocusRequester()
    var listAttached = false
    var stepping = false
    /** Entry requesters of the rows on screen, nearest the middle first (set by the page). */
    var rowsOnScreen: () -> List<FocusRequester> = { emptyList() }

    /** Focus the last focused row if the list can restore it, else the row nearest the middle, else Play. */
    fun recover(): Boolean {
        for (r in rowsOnScreen()) {
            if (runCatching { r.requestFocus() }.getOrDefault(false)) return true
        }
        return listAttached && runCatching { list.requestFocus() }.getOrDefault(false)
    }
    val rootModifier: Modifier
        get() = if (!NuvioCFeatures.DETAILS_FOCUS_GUARD) Modifier else Modifier.onFocusChanged { hasFocus = it.hasFocus }
}

internal val LocalNuvioCDetailGuard = staticCompositionLocalOf<NuvioCDetailFocusGuard?> { null }

@Composable
internal fun rememberNuvioCDetailFocusGuard(): NuvioCDetailFocusGuard {
    val guard = remember { NuvioCDetailFocusGuard() }
    if (!NuvioCFeatures.DETAILS_FOCUS_GUARD) return guard
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sidebarOpen by rememberUpdatedState(LocalSidebarExpanded.current)
    LaunchedEffect(guard) {
        // wait until the page has had focus once, so the first load and its own entry focus aren't touched
        snapshotFlow { guard.hasFocus }.first { it }
        while (true) {
            snapshotFlow { guard.hasFocus }.first { !it }
            delay(150)
            if (guard.hasFocus) continue
            if (sidebarOpen || !view.hasWindowFocus() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                // focus went somewhere legitimate; wait for it to come back before watching again
                snapshotFlow { guard.hasFocus }.first { it }
                continue
            }
            var ok = guard.recover()
            var tries = 0
            while (!ok && tries < 10) { androidx.compose.runtime.withFrameNanos { }; ok = guard.hasFocus || guard.recover(); tries++ }
            Log.w("NuvioCDetailsFocus", "page lost focus; restored=$ok")
            delay(100)
        }
    }
    return guard
}

/**
 * On the details list: a requester the guard can call, a restorer so that call lands on the last focused row, and the
 * stuck-key fix. Many rows point Up / Down at a requester on another row; when that row has scrolled off it isn't
 * composed, Compose cancels the move ("FocusRequester is not initialized") and the remote seems dead. If an Up / Down
 * press hasn't moved focus within 120 ms and nothing is scrolling, the list glides a step that way and the move is
 * retried (up to three steps).
 */
@Composable
internal fun nuvioCDetailListGuardModifier(listState: androidx.compose.foundation.lazy.LazyListState): Modifier {
    if (!NuvioCFeatures.DETAILS_FOCUS_GUARD) return Modifier
    val guard = LocalNuvioCDetailGuard.current ?: return Modifier
    guard.listAttached = true
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 200.dp.toPx() }
    return Modifier
        .focusRequester(guard.list).focusRestorer()
        // bubble phase: only Up / Down that nothing on the page handled reach here; make the move ourselves so a blocked
        // move (target row not loaded) is seen, then glide a step toward it and retry
        .onKeyEvent { e ->
            val code = e.nativeKeyEvent.keyCode
            if (code != android.view.KeyEvent.KEYCODE_DPAD_UP && code != android.view.KeyEvent.KEYCODE_DPAD_DOWN) return@onKeyEvent false
            if (e.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
            val up = code == android.view.KeyEvent.KEYCODE_DPAD_UP
            val dir = if (up) androidx.compose.ui.focus.FocusDirection.Up else androidx.compose.ui.focus.FocusDirection.Down
            if (runCatching { focusManager.moveFocus(dir) }.getOrDefault(false)) return@onKeyEvent true
            if (up && !listState.canScrollBackward || !up && !listState.canScrollForward) return@onKeyEvent true
            if (guard.stepping) return@onKeyEvent true
            guard.stepping = true
            Log.w("NuvioCDetailsFocus", "${if (up) "Up" else "Down"} blocked; stepping")
            scope.launch {
                try {
                    repeat(4) {
                        runCatching { listState.animateScrollBy(if (up) -stepPx else stepPx) }
                        androidx.compose.runtime.withFrameNanos { }
                        if (runCatching { focusManager.moveFocus(dir) }.getOrDefault(false)) return@launch
                        if (up && !listState.canScrollBackward || !up && !listState.canScrollForward) return@launch
                    }
                } finally { guard.stepping = false }
            }
            true
        }
}
