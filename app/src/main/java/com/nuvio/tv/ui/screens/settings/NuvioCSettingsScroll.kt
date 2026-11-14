package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C round 46 `settings_smooth`: Settings (left menu, options pane, pickers) scroll the way the Taste and
// Calendar lists do. The app-wide spec aims the focused row at the middle of the list and skips small moves, so a
// held key gave uneven steps (no move, then a jump) and each new step restarted a stiff spring. Here the focused row
// settles at a fixed line 35% down (rows above it stay readable), every step scrolls by exactly one row, and the
// spring is critically damped and soft enough that a new step carries the motion on instead of restarting it.
// A row near the top or bottom of the list simply stops the list at its edge (LazyColumn clamps).
// Round 46b: a held key steps every 80 ms here (was 112), so each scroll is retargeted before it settles and the
// list keeps moving instead of stopping and starting between rows.

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalFoundationApi::class)
internal object NuvioCSettingsScroll {
    val spec: BringIntoViewSpec = object : BringIntoViewSpec {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override val scrollAnimationSpec: AnimationSpec<Float> = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 520f
        )

        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            if (containerSize <= 0f || size <= 0f) return 0f
            // something taller than the room under the line (a long text block) goes to the top instead
            val line = containerSize * 0.35f
            return if (size > containerSize - line) offset else offset - line
        }
    }
}

/** Wraps Settings content in the spec above when the switch is on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NuvioCSettingsSmooth(content: @Composable () -> Unit) {
    if (!com.nuvio.tv.NuvioCFeatures.SETTINGS_SMOOTH) { content(); return }
    CompositionLocalProvider(LocalBringIntoViewSpec provides NuvioCSettingsScroll.spec, content = content)
}

/**
 * [fork] round 46b `settings_hold_log`: while Up / Down is held in Settings, frame times are measured and one line
 * is logged when the key is let go: `adb logcat -s NuvioCJank`, e.g. "hold 2400 ms, 140 frames, 9 over 33 ms,
 * worst 71 ms, steps 22". Costs nothing when no key is held.
 */
internal object NuvioCSettingsJank {
    private var holding = false
    private var start = 0L
    private var last = 0L
    private var frames = 0
    private var slow = 0
    private var worst = 0L
    private var steps = 0
    private val callback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!holding) return
            if (last != 0L) {
                val ms = (frameTimeNanos - last) / 1_000_000
                frames++
                if (ms > 33) slow++
                if (ms > worst) worst = ms
            }
            last = frameTimeNanos
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun onKey(event: android.view.KeyEvent) {
        if (!com.nuvio.tv.NuvioCFeatures.SETTINGS_HOLD_LOG) return
        val vertical = event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN || event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
        if (!vertical) return
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
            if (!holding) {
                holding = true; start = android.os.SystemClock.uptimeMillis(); last = 0L; frames = 0; slow = 0; worst = 0L; steps = 0
                android.view.Choreographer.getInstance().postFrameCallback(callback)
            }
            steps++
        } else if (event.action == android.view.KeyEvent.ACTION_UP && holding) {
            holding = false
            android.view.Choreographer.getInstance().removeFrameCallback(callback)
            android.util.Log.i("NuvioCJank", "hold ${android.os.SystemClock.uptimeMillis() - start} ms, $frames frames, $slow over 33 ms, worst $worst ms, steps $steps")
        }
    }
}
