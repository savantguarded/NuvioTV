package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C round 46 `settings_smooth`: Settings (left menu, options pane, pickers) scroll the way the Taste and
// Calendar lists do. The app-wide spec aims the focused row at the middle of the list and skips small moves, so a
// held key gave uneven steps (no move, then a jump) and each new step restarted a stiff spring. Here the focused row
// settles at a fixed line 35% down (rows above it stay readable), every step scrolls by exactly one row, and the
// spring is critically damped and soft enough that a new step carries the motion on instead of restarting it.
// A row near the top or bottom of the list simply stops the list at its edge (LazyColumn clamps).

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
