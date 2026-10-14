package com.nuvio.tv.ui.screens.player

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView

// [fork] Nuvio C subtitle_top (2026-10-14): with the OSD or pause screen up, bottom subtitles go to the
// top band with their TOP edge 24 dp from the screen top, whatever the number of lines. Before, the band
// fixed the BOTTOM edge at 24 dp + 2 lines, so a 3rd or 4th line grew upwards past the screen edge.
// The height of what is on screen is measured the way media3's SubtitlePainter lays text out
// (same text size, typeface, width and inner padding), so the lift is exact for ExoPlayer text cues.
// PGS / bitmap cues carry their own height. libass draws its own picture and keeps the 2-line estimate.

/** media3 SubtitlePainter.INNER_PADDING_RATIO: horizontal padding around each cue, as a share of the text size. */
private const val INNER_PADDING_RATIO = 0.125f

/** Gap kept between stacked cues (media3 places separate cues next to each other; a few px of room). */
private const val CUE_GAP_RATIO = 0.15f

internal object NuvioCSubtitleTop {

    /**
     * Height in px of the bottom-placed cues currently on screen, or NaN when none can be measured
     * (no cues, or only cues that place themselves, such as top-positioned or positioned bitmaps).
     */
    fun bottomBlockHeight(
        cues: List<Cue>,
        view: SubtitleView,
        textSizePx: Float,
        typeface: Typeface?
    ): Float {
        if (cues.isEmpty() || view.width <= 0 || view.height <= 0) return Float.NaN
        val parentWidth = view.width - view.paddingLeft - view.paddingRight
        val parentHeight = view.height - view.paddingTop - view.paddingBottom
        if (parentWidth <= 0 || parentHeight <= 0) return Float.NaN
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            this.typeface = typeface ?: Typeface.DEFAULT
        }
        val padX = Math.round(textSizePx * INNER_PADDING_RATIO)
        var total = 0f
        var counted = 0
        for (cue in cues) {
            if (!isBottomPlaced(cue)) continue
            val h = if (cue.bitmap != null) {
                if (cue.bitmapHeight != Cue.DIMEN_UNSET) cue.bitmapHeight * parentHeight else Float.NaN
            } else {
                val text = cue.text ?: continue
                if (text.isBlank()) continue
                var width = parentWidth - padX * 2
                if (cue.size != Cue.DIMEN_UNSET) width = (width * cue.size).toInt()
                if (width <= 0) continue
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(true)
                    .build()
                    .height.toFloat()
            }
            if (h.isNaN()) continue
            if (counted > 0) total += textSizePx * CUE_GAP_RATIO
            total += h
            counted++
        }
        return if (counted == 0) Float.NaN else total
    }

    /** Cues media3 stacks from the bottom: no line set, or a line counted up from the bottom edge. */
    private fun isBottomPlaced(cue: Cue): Boolean {
        if (cue.line == Cue.DIMEN_UNSET) return true
        if (cue.lineType == Cue.LINE_TYPE_NUMBER) return cue.line < 0 // negative = counted from the bottom
        // Fractions: only cues anchored in the lower half are moved (same split as the lift layout).
        return cue.lineType == Cue.LINE_TYPE_FRACTION && cue.line > 0.5f
    }
}
