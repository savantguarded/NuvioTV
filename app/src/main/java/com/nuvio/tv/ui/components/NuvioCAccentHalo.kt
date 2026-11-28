package com.nuvio.tv.ui.components

// [fork] Nuvio C round 68 `REEL_GLOW_V68`: a soft accent halo drawn only OUTSIDE a rounded shape (BlurMaskFilter
// OUTER, so nothing inside the frame changes). The paint and path are built once per size / colour in drawWithCache;
// a frame only replays them. Used by the Roulette / Library Random reel frame and the Roulette Movies / Shows cards.

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Halo strength and spread (board 6): about 50% accent, ~14 dp soft spread. */
object NuvioCHalo {
    const val ALPHA = 0.5f
    val SPREAD = 14.dp
}

/**
 * Draws a halo of [color] around this element's bounds with corner radius [corner], when [visible]. [zoom] grows the
 * halo with a focus zoom drawn inside (e.g. a Card's 1.04 lift), so it hugs the scaled edge.
 */
fun Modifier.nuvioCAccentHalo(
    color: Color,
    corner: Dp,
    visible: Boolean = true,
    spread: Dp = NuvioCHalo.SPREAD,
    zoom: Float = 1f
): Modifier = if (!visible) this else drawWithCache {
    val r = corner.toPx()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        this.color = color.copy(alpha = color.alpha * NuvioCHalo.ALPHA).toArgb()
        maskFilter = BlurMaskFilter(spread.toPx().coerceAtLeast(1f), BlurMaskFilter.Blur.OUTER)
    }
    val w = size.width
    val h = size.height
    onDrawBehind {
        scale(zoom) {
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, w, h, r, r, paint) }
        }
    }
}

/**
 * Clips only left and right (content sliding sideways stays inside), leaving [room] above and below free for a halo.
 * Layout is unchanged.
 */
fun Modifier.nuvioCClipSidesOnly(room: Dp = 24.dp): Modifier = drawWithContent {
    val extra = room.toPx()
    clipRect(left = 0f, top = -extra, right = size.width, bottom = size.height + extra) { this@drawWithContent.drawContent() }
}
