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

    // [fork] Nuvio C HDR dim (NuvioCHdrDim.kt): 1 = normal; below 1 the subtitle picture is drawn at
    // that brightness.
    private var dimFactor = 1f
    // hdr_dim_light (round 14): the frame around the ExoPlayer SubtitleView only dims while a picture
    // cue (PGS / VobSub / DVB) is up, because text cues are already drawn in dimmed colours. The two
    // libass frames dim whenever libass draws in them.
    private var pictureCues = false
    private var modulatePaint: android.graphics.Paint? = null

    fun setNuvioCDim(factor: Float) {
        val f = factor.coerceIn(0f, 1f)
        val changed = f != dimFactor
        dimFactor = f
        if (NuvioCFeatures.HDR_DIM_LIGHT) {
            if (layerType != LAYER_TYPE_NONE) setLayerType(LAYER_TYPE_NONE, null)
            if (changed) {
                modulatePaint = if (f < 1f) nuvioCModulatePaint(f) else null
                invalidate()
            }
            return
        }
        updateDimLayer(changed)
    }

    /** Whether a picture subtitle is on screen (ExoPlayer cues), for the frame around the SubtitleView. */
    fun setNuvioCPictureCues(present: Boolean) {
        if (present == pictureCues) return
        pictureCues = present
        if (dimFactor < 1f) invalidate()
    }

    private val isTextFrame: Boolean
        get() = id == com.nuvio.tv.R.id.nuvio_c_subtitle_lift

    private fun modulateNow(): Boolean =
        NuvioCFeatures.HDR_DIM_LIGHT && dimFactor < 1f && hasVisibleChild() && (!isTextFrame || pictureCues)

    // Previous version (switch off): the layer is a full-screen offscreen copy, kept only on a frame
    // that has something visible in it.
    private fun updateDimLayer(force: Boolean = false) {
        val want = dimFactor < 1f && hasVisibleChild()
        val has = layerType == LAYER_TYPE_HARDWARE
        when {
            want && (!has || force) -> setLayerType(LAYER_TYPE_HARDWARE, nuvioCDimPaint(dimFactor))
            !want && has -> setLayerType(LAYER_TYPE_NONE, null)
        }
    }

    private fun hasVisibleChild(): Boolean {
        for (i in 0 until childCount) if (getChildAt(i).visibility == VISIBLE) return true
        return false
    }

    override fun onViewAdded(child: android.view.View?) {
        super.onViewAdded(child)
        if (dimFactor < 1f && !NuvioCFeatures.HDR_DIM_LIGHT) updateDimLayer()
    }

    override fun onViewRemoved(child: android.view.View?) {
        super.onViewRemoved(child)
        if (dimFactor < 1f && !NuvioCFeatures.HDR_DIM_LIGHT) updateDimLayer()
    }

    /** One multiply pass over what the children drew (colour x factor, alpha kept; the video is a separate surface). */
    private fun drawModulate(canvas: Canvas) {
        val paint = modulatePaint ?: return
        if (!modulateNow()) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val lift = liftPx
        if (!NuvioCFeatures.SUBTITLE_LIFT || lift < 0.5f || width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
            drawModulate(canvas)
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
        drawModulate(canvas)
    }
}
