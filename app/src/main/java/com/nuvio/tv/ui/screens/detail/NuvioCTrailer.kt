package com.nuvio.tv.ui.screens.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.theme.NuvioMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// [fork] Nuvio C layer on top of official's background trailers ("Play in Background").
// Everything the fork adds that doesn't have to live inside an official file sits here, so
// official edits to the detail page rarely touch it. Switch: NuvioCFeatures.BACKGROUND_TRAILERS
// (off = official background trailers exactly as shipped).
//
// What the layer changes while official's background trailer plays:
// - muted; the trailer button carries on with the same playback and fades the sound in over 1 s
// - countdown from anywhere on the page (NuvioCTrailerAutostart.kt)
// - lighter page scrim (75%), detail text 85%, synopsis dimmer still, full when focused
// - every overlay, and cast / production / another-title pages, pause it; it resumes on close
//   (only a trailer from the trailers row stops it, as official)
// - first Back stops it and keeps focus, second Back leaves

/** Page scrim over a muted background trailer (official keeps the full scrim). */
internal const val NUVIO_C_BACKGROUND_SCRIM_ALPHA = 0.75f

/** Detail text (credits, ratings, year line) over a background trailer. */
internal const val NUVIO_C_DETAIL_TEXT_ALPHA = 0.85f

/** Synopsis over a background trailer, on top of the detail-text dimming (Apple TV grey, ~60%). */
internal const val NUVIO_C_DESCRIPTION_ALPHA = 0.72f

/** Detail-page trailer state the fork threads from the screen down to the hero and backdrop. */
data class NuvioCTrailerUi(
    /** Layer active: switch on and official "Play in Background" on. */
    val featureOn: Boolean = false,
    /** Page-level countdown (see NuvioCTrailerAutostart.kt). */
    val autostart: NuvioCTrailerAutostart? = null
)

internal fun nuvioCLayerOn(playInBackground: Boolean): Boolean =
    com.nuvio.tv.NuvioCFeatures.BACKGROUND_TRAILERS && playInBackground

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
