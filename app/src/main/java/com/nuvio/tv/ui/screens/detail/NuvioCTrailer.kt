package com.nuvio.tv.ui.screens.detail

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.tv.ui.theme.NuvioMotion
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] Nuvio C trailer screen (trailer button): small clearlogo bottom-left.
// Background trailers are official as shipped (the fork's layer was removed 2026-10-09).

/** Alpha without an offscreen buffer, so logo edges are never cut off mid-fade. */
internal fun Modifier.nuvioCFade(alpha: Float): Modifier = graphicsLayer {
    this.alpha = alpha
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * Small clearlogo bottom-left while the trailer plays with sound (trailer button), at official's
 * trailer-logo size: 60dp tall, at most a quarter of the width. It sits on an invisible copy of
 * the trailer seek bar, so it lines up exactly above the real bar and never moves when the bar
 * appears. Fixed box + Fit + alpha without an offscreen layer: the logo is never cropped.
 */
@Composable
internal fun NuvioCTrailerScreenLogo(
    logo: String?,
    contentDescription: String?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!com.nuvio.tv.NuvioCFeatures.TRAILER_SCREEN_LOGO) return
    val url = logo?.takeIf { it.isNotBlank() } ?: return
    var failed by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible && !failed) 1f else 0f,
        animationSpec = tween(NuvioMotion.tokens.durations.overlay),
        label = "nuvioCTrailerScreenLogo"
    )
    if (!visible && alpha <= 0f) return
    Column(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { failed = true },
            modifier = Modifier
                .padding(start = NuvioTheme.spacing.xxl)
                .fillMaxWidth(0.25f)
                .height(60.dp)
                .nuvioCFade(alpha)
        )
        Box(modifier = Modifier.nuvioCFade(0f)) {
            TrailerSeekOverlay(currentPosition = 0L, duration = 0L)
        }
    }
}
