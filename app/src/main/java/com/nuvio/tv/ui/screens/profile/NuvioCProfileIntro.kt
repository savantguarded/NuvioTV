package com.nuvio.tv.ui.screens.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// [fork] Nuvio C profile intro (switch `profile_intro`, round 21, 2026-10-23). Netflix-style opening of
// the profile screen: the background fades up from black while settling from 106% to 100% (900 ms), and
// the profiles drop 22 dp into place one after another (60 ms apart, 380 ms each), Add profile last.
// Only on the "who's watching" screen (never Manage profiles), once per visit (coming back from the PIN
// pad doesn't replay it). It starts when the focused profile's background is known (max 600 ms), so the
// picture isn't still loading under the fade. One clock, read only in graphicsLayer: no recomposition,
// no offscreen layers, nothing runs once it has finished. Remote input works from the first frame.

private const val TOTAL_MS = 1_000f
private const val BG_MS = 900f
private const val ITEM_START_MS = 120f
private const val ITEM_STEP_MS = 60f
private const val ITEM_MS = 380f
private val DROP = 22.dp
private val EASE = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Elapsed intro time in ms (null = no intro, everything at rest). */
internal val LocalNuvioCProfileIntro = staticCompositionLocalOf<State<Float>?> { null }

@Composable
internal fun rememberNuvioCProfileIntro(enabled: Boolean, backgroundReady: Boolean): State<Float>? {
    if (!NuvioCFeatures.PROFILE_INTRO || !enabled) return null
    val clock = remember { Animatable(0f) }
    val ready = rememberUpdatedState(backgroundReady)
    LaunchedEffect(Unit) {
        if (clock.value >= TOTAL_MS) return@LaunchedEffect
        withTimeoutOrNull(600) { snapshotFlow { ready.value }.first { it } }
        clock.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
    }
    return clock.asState()
}

private fun progress(t: Float, start: Float, length: Float): Float =
    EASE.transform(((t - start) / length).coerceIn(0f, 1f))

/** Background: fade from black and settle from 106% to 100%. */
internal fun Modifier.nuvioCProfileIntroBackground(intro: State<Float>?): Modifier =
    if (intro == null) this else graphicsLayer {
        val p = progress(intro.value, 0f, BG_MS)
        alpha = p
        val s = 1.06f - 0.06f * p
        scaleX = s
        scaleY = s
    }

/** Profile [index] (Add profile = the index after the last profile): drop in from 22 dp above. */
internal fun Modifier.nuvioCProfileIntroItem(intro: State<Float>?, index: Int): Modifier =
    if (intro == null) this else graphicsLayer {
        val p = progress(intro.value, ITEM_START_MS + ITEM_STEP_MS * index, ITEM_MS)
        alpha = p
        translationY = -DROP.toPx() * (1f - p)
    }
