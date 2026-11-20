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

@Stable
internal class NuvioCDetailFocusGuard {
    var hasFocus by mutableStateOf(false)
    val list = FocusRequester()
    var listAttached = false
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
            delay(300)
            if (guard.hasFocus) continue
            if (sidebarOpen || !view.hasWindowFocus() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                // focus went somewhere legitimate; wait for it to come back before watching again
                snapshotFlow { guard.hasFocus }.first { it }
                continue
            }
            val ok = guard.listAttached && runCatching { guard.list.requestFocus() }.getOrDefault(false)
            Log.w("NuvioCDetailsFocus", "page lost focus; restored=$ok")
            if (!ok) {
                delay(300)
                if (!guard.hasFocus && guard.listAttached) runCatching { guard.list.requestFocus() }
            }
            delay(200)
        }
    }
    return guard
}

/** On the details list: a requester the guard can call, and a restorer so that call lands on the last focused row. */
@Composable
internal fun nuvioCDetailListGuardModifier(): Modifier {
    if (!NuvioCFeatures.DETAILS_FOCUS_GUARD) return Modifier
    val guard = LocalNuvioCDetailGuard.current ?: return Modifier
    guard.listAttached = true
    return Modifier.focusRequester(guard.list).focusRestorer()
}
