package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C subtitle lift: the frame around the ExoPlayer subtitle view and the libass overlays
// (see exo_player_view.xml). While the OSD is open only the BOTTOM half of the subtitle picture is
// drawn moved up; the top half is drawn where it is, so top subtitles and signs never move.
// With the lift at 0, or the subtitle_lift switch off, it is a plain FrameLayout.

import com.nuvio.tv.NuvioCFeatures
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout

class NuvioCSubtitleLiftLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var liftPx = 0f
    private var splitY = 0f

    /** [lift] pixels to move the lower part up; [split] the line (px from the top) above which nothing moves. */
    fun setSubtitleLift(lift: Float, split: Float) {
        val l = if (lift.isNaN()) 0f else lift.coerceAtLeast(0f)
        if (l == liftPx && split == splitY) return
        liftPx = l
        splitY = split
        invalidate()
    }

    // [fork] Nuvio C HDR dim (NuvioCHdrDim.kt): 1 = normal; below 1 the whole subtitle picture
    // is drawn at that brightness through a colour-filtered view layer.
    private var dimFactor = 1f

    fun setNuvioCDim(factor: Float) {
        val f = factor.coerceIn(0f, 1f)
        if (f == dimFactor) return
        dimFactor = f
        if (f < 1f) setLayerType(LAYER_TYPE_HARDWARE, nuvioCDimPaint(f)) else setLayerType(LAYER_TYPE_NONE, null)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val lift = liftPx
        if (!NuvioCFeatures.SUBTITLE_LIFT || lift < 0.5f || width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val split = splitY.coerceIn(0f, h)

        // Top part: drawn in place.
        var save = canvas.save()
        canvas.clipRect(0f, 0f, w, split)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)

        // Lower part: the same picture, moved up by the lift.
        save = canvas.save()
        canvas.clipRect(0f, split - lift, w, h - lift)
        canvas.translate(0f, -lift)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
