package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 44b (switch `subtitle_styled_lift`): the Nuvio C subtitle lift, top jump and pause-screen lift
// for official 1.2's drawn subtitle renderer (StyledSubtitleOverlay: fonts, background box, rounded corners, shadow).
// Same rule as NuvioCSubtitleLiftLayout for the subtitle view: only the BOTTOM half of the overlay is drawn moved up,
// so top-placed lines never move. The lift is a draw-time translation (no recomposition, no relayout) worked out by
// NuvioCSubtitleLiftEffect from the OSD / pause screen positions, for ExoPlayer and mpv alike (mpv text goes through
// the same overlay when the renderer is on).

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.nuvio.tv.NuvioCFeatures

internal object NuvioCStyledLift {
    /** Pixels the lower half of the overlay is drawn moved up (0 = in place). Read at draw time. */
    val lift = mutableFloatStateOf(0f)
    /** Height of the bottom subtitle block on screen (background box included), for the top jump. */
    val block = mutableFloatStateOf(Float.NaN)

    // Overlay geometry in window px and the bottom inset official placed the block at.
    @Volatile private var top = Float.NaN
    @Volatile private var height = 0f
    @Volatile private var bottomInset = 0f

    val enabled: Boolean get() = NuvioCFeatures.SUBTITLE_LIFT && NuvioCFeatures.SUBTITLE_STYLED_LIFT

    internal fun reportGeometry(windowTop: Float, h: Float) { top = windowTop; height = h }
    internal fun reportInset(px: Float) { bottomInset = px }
    internal fun reportBlock(px: Float) { if (px > 0f) block.floatValue = px }

    /**
     * [progress] 0..1 of the OSD fade; [limit] window Y the subtitle block's bottom must stay above (OSD top minus the
     * gap, or the top-jump target). NaN or progress 0 = back in place.
     */
    fun apply(progress: Float, limit: Float) {
        if (!enabled || progress <= 0f || limit.isNaN() || top.isNaN() || height <= 0f) {
            if (lift.floatValue != 0f) lift.floatValue = 0f
            return
        }
        val subsBottom = top + height - bottomInset
        val l = (subsBottom - limit).coerceAtLeast(0f) * progress
        if (kotlin.math.abs(l - lift.floatValue) >= 0.5f) lift.floatValue = l
    }

    fun reset() { lift.floatValue = 0f }
}

/** On the StyledSubtitleOverlay: reports its place on screen and draws its lower half moved up by the lift. */
internal fun Modifier.nuvioCStyledLift(): Modifier {
    if (!NuvioCStyledLift.enabled) return this
    return this
        .onGloballyPositioned { NuvioCStyledLift.reportGeometry(it.positionInWindow().y, it.size.height.toFloat()) }
        .drawWithContent {
            val l = NuvioCStyledLift.lift.floatValue
            if (l < 0.5f) {
                drawContent()
                return@drawWithContent
            }
            val w = size.width
            val h = size.height
            val split = h / 2f
            // Top half: in place (top-placed lines and signs never move).
            clipRect(0f, 0f, w, split) { this@drawWithContent.drawContent() }
            // Bottom half: the same picture moved up.
            translate(0f, -l) {
                clipRect(0f, split, w, h) { this@drawWithContent.drawContent() }
            }
        }
}
