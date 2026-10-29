package com.nuvio.tv.ui.util

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C (switch `detail_row_throttle`, 2026-10-15): official's D-pad repeat gate (one step
// per 80 ms while a direction is held), which Home, collections, Search and See all already use,
// also on the details-page rows (cast, more like this, collection, trailers, companies) and the cast
// page filmography rows. Without it a held Left queued more focus moves and scroll animations than
// the TV could draw, and the row stalled. Single presses are untouched.
fun Modifier.nuvioCRowThrottle(): Modifier =
    if (NuvioCFeatures.DETAIL_ROW_THROTTLE) this.dpadRepeatThrottle() else this

// [fork] Nuvio C round 31 (switch `list_repeat_gate`): the same gate for held Up / Down only, on screens
// that are long vertical lists (Settings, the player's Cast / About panel, Calendar, Roulette). A held key
// there queued focus moves faster than the list could compose the next rows, so focus stuck until the key
// was released. Held Left / Right is left alone (steppers and sliders in Settings use the repeats).
fun Modifier.nuvioCListGate(gateMs: Long = 112L): Modifier =
    if (!NuvioCFeatures.LIST_REPEAT_GATE) this else composed {
        val focusManager = LocalFocusManager.current
        val last = remember { longArrayOf(0L) }
        onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val vertical = native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || native.keyCode == KeyEvent.KEYCODE_DPAD_UP
            if (!vertical || native.action != KeyEvent.ACTION_DOWN || native.repeatCount == 0) return@onPreviewKeyEvent false
            val now = SystemClock.uptimeMillis()
            if (now - last[0] >= gateMs) {
                last[0] = now
                focusManager.moveFocus(if (native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) FocusDirection.Down else FocusDirection.Up)
            }
            true
        }
    }
