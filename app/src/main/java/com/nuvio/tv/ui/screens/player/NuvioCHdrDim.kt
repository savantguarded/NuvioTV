package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C HDR dim: while HDR10 / HDR10+ / HLG / Dolby Vision plays, the TV shows plain white
// UI at full HDR brightness, which glares. Subtitles and the player controls are drawn at 60% of
// their brightness then (colours and transparency kept); the video itself is never touched.
//  - ExoPlayer: the subtitle frames (text, PGS and libass layers) get a colour filter.
//  - mpv: plain-text subtitles get a dimmed colour. ASS / PGS drawn by mpv itself stay as they are.
//  - Controls, clock, skip-intro and next-episode cards, media info panel, stats HUD and torrent stats:
//    dimmed with Modifier.nuvioCHdrDim.
// Switch: NuvioCFeatures.HDR_DIM.

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.media3.ui.PlayerView
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.data.local.InternalPlayerEngine
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal object NuvioCHdrDim {
    /** Brightness kept while HDR plays (Charles: 60%). */
    const val FACTOR = 0.6f
    private const val POLL_MS = 1_000L

    /** argb with its colour scaled by [factor]; alpha untouched. */
    fun dimArgb(argb: Int, factor: Float): Int {
        if (factor >= 1f) return argb
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun isHdrTag(tag: String?): Boolean = tag != null && tag != "SDR"

    internal fun pollMs() = POLL_MS
}

/**
 * True while HDR / HLG / Dolby Vision video is on screen (and the switch is on). Checked once a
 * second; it also keeps the subtitle layers' dim in step with it.
 */
@Composable
internal fun rememberNuvioCHdrDim(viewModel: PlayerViewModel, uiState: PlayerUiState): Boolean {
    if (!NuvioCFeatures.HDR_DIM) return false
    val latestUiState by rememberUpdatedState(uiState)
    val controller = viewModel.controller
    val hdr by produceState(false, controller, uiState.internalPlayerEngine) {
        while (true) {
            val now = runCatching { nuvioCIsHdrNow(controller, latestUiState) }.getOrDefault(false)
            value = now
            runCatching { nuvioCApplySubtitleDim(controller, latestUiState, if (now) NuvioCHdrDim.FACTOR else 1f) }
            delay(NuvioCHdrDim.pollMs())
        }
    }
    DisposableEffect(controller) {
        onDispose { runCatching { nuvioCApplySubtitleDim(controller, latestUiState, 1f) } }
    }
    return hdr
}

private fun nuvioCIsHdrNow(controller: PlayerRuntimeController, uiState: PlayerUiState): Boolean {
    if (!controller.hasRenderedFirstFrame) return false
    if (controller.currentInternalPlayerEngine == InternalPlayerEngine.MVP_PLAYER) {
        controller.mpvView?.nuvioCIsHdrVideo()?.let { return it }
    }
    return NuvioCHdrDim.isHdrTag(nuvioCVisualTag(controller, uiState))
}

private fun nuvioCApplySubtitleDim(controller: PlayerRuntimeController, uiState: PlayerUiState, factor: Float) {
    controller.exoPlayerView?.nuvioCSetSubtitleDim(factor)
    controller.mpvView?.nuvioCSetSubtitleDim(factor, uiState.subtitleStyle)
}

private fun PlayerView.nuvioCSetSubtitleDim(factor: Float) {
    for (id in intArrayOf(R.id.nuvio_c_subtitle_lift, R.id.libass_overlay_container, R.id.libass_overlay_container_gl)) {
        (findViewById<View>(id) as? NuvioCSubtitleLiftLayout)?.setNuvioCDim(factor)
    }
}

/** Draws this element at [NuvioCHdrDim.FACTOR] brightness while [active]; transparency is kept. */
internal fun Modifier.nuvioCHdrDim(active: Boolean): Modifier {
    if (!active || !NuvioCFeatures.HDR_DIM) return this
    val shade = Color.Black.copy(alpha = 1f - NuvioCHdrDim.FACTOR)
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            // SrcAtop: only darkens what was drawn, keeps its alpha
            drawRect(shade, blendMode = BlendMode.SrcAtop)
        }
}

/** Colour filter for a subtitle frame (Android view layer). */
internal fun nuvioCDimPaint(factor: Float): Paint = Paint().apply {
    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(factor, factor, factor, 1f) })
}
