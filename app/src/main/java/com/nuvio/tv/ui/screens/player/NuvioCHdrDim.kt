package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C HDR dim: while HDR10 / HDR10+ / HLG / Dolby Vision plays, the TV shows plain white
// UI at full HDR brightness, which glares. Subtitles and the player controls are drawn at 60% of
// their brightness then (colours and transparency kept); the video itself is never touched.
//  - ExoPlayer: text subtitles are drawn with dimmed text / background / outline colours (the way
//    official's HDR subtitle style does it, no extra drawing). Picture subtitles (PGS) and libass get
//    one "multiply" pass over their frame, only while such a picture is on screen.
//  - mpv: plain-text subtitles get dimmed text / background / outline colours. ASS / PGS drawn by mpv
//    itself stay as they are.
// Round 14 (2026-10-21, `hdr_dim_light`): no offscreen layers any more. Measured over adb, the
// previous layers doubled the GPU time per frame against official (10 ms vs 5 ms) and added ~50 MB
// of graphics memory: the subtitle frame kept a full-screen hardware layer for the whole of any HDR
// film, and each dimmed panel / card rendered offscreen first. Now each dimmed element draws one
// rectangle in "modulate" blend over itself (colour x 0.6, alpha kept), straight onto the screen.
// The video is a separate surface under the window, so it is never touched.
//  - Controls (logo and badges included), clock, skip-intro and next-episode cards, pause screen,
//    buffering spinner, parental guide, HDR/DV popup, small indicator pills, end-of-episode prompt,
//    panels, media info panel, stats HUD and torrent stats: dimmed with Modifier.nuvioCHdrDim.
//  - Dims whenever any HDR is detected (see nuvioCIsHdrNow), the same rule as the OSD picture badge.
// Switch: NuvioCFeatures.HDR_DIM.

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
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
    // hdr_dim_light: the exo subtitle frame only needs its multiply pass while a picture cue (PGS /
    // VobSub / DVB) is up; text cues are already drawn in dimmed colours.
    if (NuvioCFeatures.HDR_DIM_LIGHT) {
        DisposableEffect(controller, uiState.internalPlayerEngine) {
            val player = controller.exoPlayer
            fun update(cues: List<androidx.media3.common.text.Cue>) {
                val bitmaps = cues.any { it.bitmap != null }
                (controller.exoPlayerView?.findViewById<View>(R.id.nuvio_c_subtitle_lift) as? NuvioCSubtitleLiftLayout)
                    ?.setNuvioCPictureCues(bitmaps)
            }
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) = update(cueGroup.cues)
            }
            player?.addListener(listener)
            onDispose { player?.removeListener(listener) }
        }
    }
    DisposableEffect(controller) {
        onDispose { runCatching { nuvioCApplySubtitleDim(controller, latestUiState, 1f) } }
    }
    return hdr
}

/**
 * Any HDR seen counts: the OSD's picture badge (track info, decoder, stream name) or, on mpv, mpv's
 * own decoded gamma. So whenever the badge says DV / HDR10 / HDR10+ / HLG, everything dims with it.
 */
private fun nuvioCIsHdrNow(controller: PlayerRuntimeController, uiState: PlayerUiState): Boolean {
    if (!controller.hasRenderedFirstFrame) return false
    if (controller.currentInternalPlayerEngine == InternalPlayerEngine.MVP_PLAYER &&
        controller.mpvView?.nuvioCIsHdrVideo() == true
    ) return true
    return NuvioCHdrDim.isHdrTag(nuvioCVisualTag(controller, uiState))
}

private fun nuvioCApplySubtitleDim(controller: PlayerRuntimeController, uiState: PlayerUiState, factor: Float) {
    controller.exoPlayerView?.nuvioCSetSubtitleDim(factor)
    controller.mpvView?.nuvioCSetSubtitleDim(factor, uiState.subtitleStyle)
}

private fun PlayerView.nuvioCSetSubtitleDim(factor: Float) {
    for (id in intArrayOf(R.id.nuvio_c_subtitle_lift, R.id.libass_overlay_container_gl)) {
        (findViewById<View>(id) as? NuvioCSubtitleLiftLayout)?.setNuvioCDim(factor)
    }
}

/**
 * Draws this element at [NuvioCHdrDim.FACTOR] brightness while [active]; transparency is kept.
 * hdr_dim_light (round 14): one rectangle in BlendMode.Modulate over the element after it is drawn
 * (colour x 0.6, alpha x 1), no offscreen layer. Transparent pixels stay transparent, black scrims
 * stay black, and the video (its own surface below the window) is never touched. [overflow] extends
 * the rectangle past the element's bounds for parts that grow when focused (Skip Intro's 1.1x zoom).
 * Off = the previous offscreen-layer version.
 */
internal fun Modifier.nuvioCHdrDim(active: Boolean, overflow: Dp = 0.dp): Modifier {
    if (!active || !NuvioCFeatures.HDR_DIM) return this
    if (NuvioCFeatures.HDR_DIM_LIGHT) {
        return drawWithContent {
            drawContent()
            val o = overflow.toPx()
            drawRect(
                color = NUVIO_C_MODULATE,
                topLeft = androidx.compose.ui.geometry.Offset(-o, -o),
                size = androidx.compose.ui.geometry.Size(size.width + 2 * o, size.height + 2 * o),
                blendMode = BlendMode.Modulate
            )
        }
    }
    val shade = Color.Black.copy(alpha = 1f - NuvioCHdrDim.FACTOR)
    val dimmed = { m: Modifier ->
        m.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // SrcAtop: only darkens what was drawn, keeps its alpha
                drawRect(shade, blendMode = BlendMode.SrcAtop)
            }
    }
    if (overflow <= 0.dp) return dimmed(this)
    return dimmed(this.nuvioCShrinkBy(overflow)).nuvioCGrowBy(overflow)
}

/** Grey at the HDR-dim factor: Modulate with it scales colour by the factor and keeps alpha. */
private val NUVIO_C_MODULATE = Color(NuvioCHdrDim.FACTOR, NuvioCHdrDim.FACTOR, NuvioCHdrDim.FACTOR, 1f)

/** Reports the size minus [by] per side and places the (grown) child back over the real spot. */
private fun Modifier.nuvioCShrinkBy(by: Dp): Modifier = layout { measurable, constraints ->
    val m = by.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * m, 2 * m))
    val w = (placeable.width - 2 * m).coerceAtLeast(0)
    val h = (placeable.height - 2 * m).coerceAtLeast(0)
    layout(w, h) { placeable.place(-m, -m) }
}

/** Reports the size plus [by] per side, with the content centred, so the layer above has room. */
private fun Modifier.nuvioCGrowBy(by: Dp): Modifier = layout { measurable, constraints ->
    val m = by.roundToPx()
    val placeable = measurable.measure(constraints.offset(-2 * m, -2 * m))
    layout(placeable.width + 2 * m, placeable.height + 2 * m) { placeable.place(m, m) }
}

/**
 * HDR dim for the player controls without any layer (2026-10-11): the colours themselves are drawn
 * at [NuvioCHdrDim.FACTOR] brightness, transparency kept, so the controls stay solid (the
 * 2026-10-10 version lowered their opacity instead, and the picture showed through them). Text,
 * icons, focus pills, seek bar, badges, logo, clock and time read their colours through
 * [nuvioCOsd] / [nuvioCOsdColorFilter] / [nuvioCOsdShade]; gradients stay full strength.
 * PlayerScreen sets [LocalNuvioCHdrDim] around the controls, clock and seek-only bar.
 */
internal val LocalNuvioCHdrDim = compositionLocalOf { false }

/** [color] at HDR-dim brightness while the controls are dimmed; alpha untouched. */
@Composable
@ReadOnlyComposable
internal fun nuvioCOsd(color: Color): Color =
    if (LocalNuvioCHdrDim.current && NuvioCFeatures.HDR_DIM) color.nuvioCDimmed() else color

internal fun Color.nuvioCDimmed(): Color {
    val f = NuvioCHdrDim.FACTOR
    return Color(red * f, green * f, blue * f, alpha)
}

/**
 * Own render layer for a part of the controls (2026-10-11 round 4, HDR lag). The 2026-10-10 alpha
 * dim happened to give the bottom block, logo, clock and seek-only bar their own layers, so a seek-bar
 * tick or the clock only redrew that part; the colour dim dropped those layers and the whole OSD
 * redrew with every tick, which is what felt less fluid on 4K HDR. These layers are plain (no
 * transparency, no offscreen copy), so nothing turns see-through and nothing is clipped.
 */
internal fun Modifier.nuvioCOsdLayer(): Modifier =
    if (NuvioCFeatures.HDR_DIM) graphicsLayer { } else this

/** Colour filter for images (OSD logo) while dimmed, else null. */
@Composable
@ReadOnlyComposable
internal fun nuvioCOsdColorFilter(): ColorFilter? =
    if (LocalNuvioCHdrDim.current && NuvioCFeatures.HDR_DIM) NUVIO_C_DIM_FILTER else null

private val NUVIO_C_DIM_FILTER: ColorFilter = NuvioCHdrDim.FACTOR.let { f ->
    ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(f, f, f, 1f) }
    )
}

/**
 * For fully opaque fills drawn with a brush (seek bar played part, thumb): black at 40% drawn over
 * the element's own shape. Only valid where the element itself is opaque.
 */
@Composable
@ReadOnlyComposable
internal fun nuvioCOsdShade(): Color? =
    if (LocalNuvioCHdrDim.current && NuvioCFeatures.HDR_DIM) Color.Black.copy(alpha = 1f - NuvioCHdrDim.FACTOR) else null

/** Colour filter for a subtitle frame (Android view layer). */
internal fun nuvioCDimPaint(factor: Float): Paint = Paint().apply {
    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(factor, factor, factor, 1f) })
}

/**
 * hdr_dim_light: the ExoPlayer text-subtitle style with text, background and outline colours at
 * [NuvioCHdrDim.FACTOR] brightness while [hdr] (what official's HDR subtitle style does). Off or SDR =
 * the saved style.
 */
internal fun nuvioCHdrSubtitleStyle(
    style: com.nuvio.tv.data.local.SubtitleStyleSettings,
    hdr: Boolean
): com.nuvio.tv.data.local.SubtitleStyleSettings {
    if (!hdr || !NuvioCFeatures.HDR_DIM || !NuvioCFeatures.HDR_DIM_LIGHT) return style
    val f = NuvioCHdrDim.FACTOR
    return style.copy(
        textColor = NuvioCHdrDim.dimArgb(style.textColor, f),
        backgroundColor = NuvioCHdrDim.dimArgb(style.backgroundColor, f),
        outlineColor = NuvioCHdrDim.dimArgb(style.outlineColor, f)
    )
}

/** hdr_dim_light: paint for one modulate pass over a subtitle frame (Android view). */
internal fun nuvioCModulatePaint(factor: Float): Paint = Paint().apply {
    val c = (factor.coerceIn(0f, 1f) * 255f).roundToInt()
    color = android.graphics.Color.argb(255, c, c, c)
    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
}
