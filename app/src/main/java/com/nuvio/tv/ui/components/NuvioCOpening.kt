package com.nuvio.tv.ui.components

// [fork] Nuvio C round 37 `startup_opening`.
//
// startup_opening: on a cold start that goes to the profile screen, the still Nuvio logo sits on black from the
// first frame (instead of a plain black wait), stays over the profile screen while it builds and its background
// picture loads, then fades away (300 ms, shrinking slightly) as the profile screen's own intro fades up beneath
// it. Nothing moves while the app is busy loading: the only motion starts once the screen is ready.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.brandWordmarkResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

object NuvioCOpening {

    /** True from a cold start that expects the profile screen until the opening logo has faded. */
    internal val pending = mutableStateOf(false)
    /** Set when the profile screen is ready (its intro starts): the logo fades. */
    internal val released = mutableStateOf(false)

    fun begin() {
        if (!NuvioCFeatures.STARTUP_OPENING) return
        pending.value = true
        released.value = false
    }

    fun release() { if (pending.value) released.value = true }
}

/** The still logo on black, exactly where the start-up screen draws it. */
@Composable
fun NuvioCOpeningLogo(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        NuvioCStartupStatic(
            logoRes = NuvioTheme.currentTheme.brandWordmarkResource,
            contentDescription = stringResource(R.string.cd_nuvio_logo),
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Drawn on top of the profile screen on a cold start; fades once released (safety: 2.5 s). */
@Composable
fun NuvioCOpeningCover() {
    if (!NuvioCFeatures.STARTUP_OPENING || !NuvioCOpening.pending.value) return
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        withTimeoutOrNull(2_500) { snapshotFlow { NuvioCOpening.released.value }.first { it } }
        // [fork] round 40 startup_profile_gate: two frames after release, so the profile screen has been drawn under the
        // logo before it fades (no glimpse of a half-built screen).
        if (NuvioCFeatures.STARTUP_PROFILE_GATE) { androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { } }
        // [fork] round 39 startup_still: a plain fade, no shrink, so the logo stays still and sharp to the end.
        if (NuvioCFeatures.STARTUP_STILL) fade.animateTo(0f, tween(240, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        else fade.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        NuvioCOpening.pending.value = false
    }
    NuvioCOpeningLogo(
        Modifier.graphicsLayer {
            alpha = fade.value
            val s = if (NuvioCFeatures.STARTUP_STILL) 1f else 0.96f + 0.04f * fade.value
            scaleX = s
            scaleY = s
        }
    )
}
