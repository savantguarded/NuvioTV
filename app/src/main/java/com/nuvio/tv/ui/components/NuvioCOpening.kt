package com.nuvio.tv.ui.components

// [fork] Nuvio C round 37 `startup_opening` + `profile_to_home`.
//
// startup_opening: on a cold start that goes to the profile screen, the still Nuvio logo sits on black from the
// first frame (instead of a plain black wait), stays over the profile screen while it builds and its background
// picture loads, then fades away (300 ms, shrinking slightly) as the profile screen's own intro fades up beneath
// it. Nothing moves while the app is busy loading: the only motion starts once the screen is ready.
//
// profile_to_home: after a profile is picked, the start-up screen shows that profile's avatar, still, in the
// centre (it eases in during the existing 300 ms dissolve). When Home has settled, a circle opens from the
// centre and reveals Home (520 ms) instead of a plain fade.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.brandWordmarkResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

object NuvioCOpening {
    data class Picked(val name: String, val colorHex: String, val avatarUrl: String?)

    /** True from a cold start that expects the profile screen until the opening logo has faded. */
    internal val pending = mutableStateOf(false)
    /** Set when the profile screen is ready (its intro starts): the logo fades. */
    internal val released = mutableStateOf(false)
    /** The profile just picked, shown on the start-up screen. */
    internal val picked = mutableStateOf<Picked?>(null)

    fun begin() {
        if (!NuvioCFeatures.STARTUP_OPENING) return
        pending.value = true
        released.value = false
    }

    fun release() { if (pending.value) released.value = true }

    fun pick(name: String, colorHex: String, avatarUrl: String?) {
        if (NuvioCFeatures.PROFILE_TO_HOME) picked.value = Picked(name, colorHex, avatarUrl)
    }
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

/** The picked profile's avatar for the start-up screen (null = show the logo as before). */
@Composable
internal fun NuvioCPickedAvatar(appear: () -> Float, modifier: Modifier = Modifier): Boolean {
    val p = NuvioCOpening.picked.value ?: return false
    if (!NuvioCFeatures.PROFILE_TO_HOME) return false
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer {
                    val a = appear().coerceIn(0f, 1f)
                    val s = 0.9f + 0.1f * a
                    scaleX = s
                    scaleY = s
                }
                .size(126.dp)
                .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            ProfileAvatarCircle(
                name = p.name,
                colorHex = p.colorHex,
                size = 112.dp,
                avatarImageUrl = p.avatarUrl,
                imageCrossfade = false
            )
        }
    }
    return true
}

/**
 * The start-up screen's way out: a circle from the centre opens onto Home as [exit] goes 1 -> 0, the screen fading
 * over the last part so nothing (the logo view included) can linger. [exiting] false = drawn as usual.
 */
fun Modifier.nuvioCCircleReveal(exiting: () -> Boolean, exit: () -> Float): Modifier =
    if (!NuvioCFeatures.PROFILE_TO_HOME) this else drawWithContent {
        if (!exiting()) { drawContent(); return@drawWithContent }
        val t = 1f - exit().coerceIn(0f, 1f) // 0 -> 1 while it opens
        val r = hypot(size.width, size.height) / 2f * t
        if (r <= 0.5f) { drawContent(); return@drawWithContent }
        val hole = Path().apply { addOval(Rect(center, r)) }
        clipPath(hole, clipOp = ClipOp.Difference) { this@drawWithContent.drawContent() }
    }
