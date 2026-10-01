package com.nuvio.tv.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.view.TextureView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs

// [fork] Nuvio C: removes letterbox bars burned into trailer frames by zooming only as much as
// the bars need. A 16:9 trailer stays at 1.0x (full clarity), a scope (2.39:1) one goes to
// about 1.34x. Lives in its own file so upstream changes to TrailerPlayer rarely touch it.

private const val SAMPLE_WIDTH = 64
private const val SAMPLE_HEIGHT = 144
private const val BRIGHT_LUMA = 28          // 0..255; anything above counts as picture
private const val MAX_BRIGHT_IN_BLACK_ROW = 2
private const val AGREE_TOLERANCE = 0.03f

/** Times (ms after the first frame) at which a frame is checked for bars. */
private val SAMPLE_TIMES_MS = longArrayOf(1_500, 3_500, 6_000, 9_000, 13_000)

/**
 * Samples the playing trailer a few times and reports the zoom that hides its letterbox bars.
 * Two samples must agree before a zoom is reported, so a dark shot or a studio logo card
 * can't trigger a wrong zoom on its own. Never reports more than [maxZoom].
 */
internal suspend fun detectTrailerLetterboxZoom(
    view: () -> PlayerView?,
    maxZoom: Float,
    onZoom: (Float) -> Unit
) {
    val samples = mutableListOf<Float>()
    var elapsed = 0L
    for (at in SAMPLE_TIMES_MS) {
        delay(at - elapsed)
        elapsed = at
        val zoom = measureLetterboxZoom(view(), maxZoom) ?: continue
        val agreeing = samples.firstOrNull { abs(it - zoom) <= AGREE_TOLERANCE }
        if (agreeing != null) {
            onZoom(minOf(agreeing, zoom))
            return
        }
        samples += zoom
    }
    // No two samples agreed: take the most conservative one we saw, if any.
    samples.minOrNull()?.let(onZoom)
}

/** One frame: zoom needed to hide its top/bottom bars, or null if the frame can't be judged. */
private fun measureLetterboxZoom(view: PlayerView?, maxZoom: Float): Float? {
    val texture = view?.videoSurfaceView as? TextureView ?: return null
    if (!texture.isAvailable) return null
    val bitmap: Bitmap = runCatching { texture.getBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT) }.getOrNull() ?: return null
    try {
        val pixels = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
        bitmap.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
        val blackRow = BooleanArray(SAMPLE_HEIGHT) { row ->
            var bright = 0
            val start = row * SAMPLE_WIDTH
            for (i in start until start + SAMPLE_WIDTH) {
                val p = pixels[i]
                val luma = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (luma > BRIGHT_LUMA) bright++
            }
            bright <= MAX_BRIGHT_IN_BLACK_ROW
        }
        // Mostly-black frame (fade, night shot, logo card): can't tell bars from picture.
        if (blackRow.count { it } > SAMPLE_HEIGHT * 0.6) return null
        val top = blackRow.indexOfFirst { !it }
        val bottom = SAMPLE_HEIGHT - 1 - blackRow.indexOfLast { !it }
        // Letterbox bars are symmetric; a dark sky or floor on one side is not a bar.
        val bar = minOf(top, bottom)
        if (bar <= 1) return 1f
        val zoom = SAMPLE_HEIGHT.toFloat() / (SAMPLE_HEIGHT - 2 * bar)
        return zoom.coerceIn(1f, maxZoom)
    } finally {
        bitmap.recycle()
    }
}
