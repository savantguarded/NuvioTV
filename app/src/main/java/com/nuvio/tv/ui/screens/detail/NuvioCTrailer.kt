package com.nuvio.tv.ui.screens.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// [fork] Nuvio C background-trailer layer. Everything the fork adds on top of upstream
// PR #3730 that doesn't have to live inside an upstream file sits here, so official edits
// to the detail page rarely touch it.

/** Synopsis brightness over a background trailer (on top of the page's own dimming), Apple TV grey. */
internal const val NUVIO_C_DESCRIPTION_ALPHA = 0.72f

/** Detail-page trailer state the fork threads from the screen down to the hero and backdrop. */
data class NuvioCTrailerUi(
    /** "Background trailers" is on: keep the fill-the-screen framing and fade the logo, never pop it. */
    val featureOn: Boolean = false,
    /** A trailer is playing with the feature on: keep the framing when the trailer button adds sound. */
    val keepFraming: Boolean = false,
    /** An in-page overlay (comments, synopsis, dialogs) covers the page: pause the background trailer. */
    val overlayOpen: Boolean = false,
    /** Page-level countdown for background mode (see NuvioCTrailerAutostart.kt). */
    val autostart: NuvioCTrailerAutostart? = null
)

internal val MetaDetailsUiState.nuvioCTrailerUi: NuvioCTrailerUi
    get() = NuvioCTrailerUi(
        featureOn = backgroundTrailerEnabled,
        keepFraming = backgroundTrailerEnabled && isTrailerPlaying,
        overlayOpen = showListPicker || removalConfirmations.isNotEmpty()
    )

/**
 * Alpha without an offscreen buffer: each draw call is faded instead, so focus rings, scaled
 * buttons and logo edges that reach past the bounds are never cut off mid-fade.
 */
internal fun Modifier.nuvioCFade(alpha: Float): Modifier = graphicsLayer {
    this.alpha = alpha
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * Like animateContentSize (height only, content pinned to the top) but without clipToBounds,
 * so the hero's logo and focused buttons are never cropped while the hero resizes.
 */
internal fun Modifier.nuvioCAnimateHeightNoClip(spec: AnimationSpec<Float>): Modifier = composed {
    val scope = rememberCoroutineScope()
    val height = remember { Animatable(-1f) }
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val target = placeable.height.toFloat()
        if (height.value < 0f) {
            scope.launch { height.snapTo(target) }
        } else if (height.targetValue != target) {
            scope.launch { height.animateTo(target, spec) }
        }
        val shown = if (height.value < 0f) placeable.height else height.value.roundToInt()
        layout(placeable.width, shown) { placeable.place(0, 0) }
    }
}
