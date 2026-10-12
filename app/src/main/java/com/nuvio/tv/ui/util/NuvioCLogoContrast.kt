package com.nuvio.tv.ui.util

import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.nuvio.tv.NuvioCFeatures
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// [fork] Nuvio C dark clearlogos (switch `logo_dark_lift`). Some logos have black parts (My Spy:
// black "MY" and "THE ETERNAL CITY" next to a red "SPY") that vanish on the dark hero / OSD.
// Each logo is looked at once when it decodes (Coil keeps the result in its memory cache, keyed by
// URL + this transformation), nothing runs per frame:
//  - LIFT (A): the logo has solid black / near-black parts (>= 12% of its solid pixels) that are not
//    just an outline around light letters -> its grey / black pixels are brightened along one curve
//    that keeps their order (texture and shading stay), coloured parts stay as they are (SPY stays
//    red). Soft shadows away from the solid shapes are left alone, so no white halo.
//  - OUTLINE (B): the dark parts are mostly coloured (navy, maroon...), lifting would change the
//    branding -> a thin 70% white outline (~1 dp at display size) around the whole logo instead.
//  - NONE: everything else (light logos, light letters with a black stroke / shadow).

object NuvioCLogoContrast {
    enum class Mode { NONE, LIFT, OUTLINE }

    val enabled: Boolean get() = NuvioCFeatures.LOGO_DARK_LIFT

    private const val SOLID = 0.85f
    private const val DARK = 0.20f
    private const val LIGHT = 0.50f
    const val MIN_DARK_SHARE = 0.12f
    private const val OUTLINE_LIKE = 0.60f
    private const val NEUTRAL_SHARE = 0.60f
    private const val NEUTRAL_CHROMA = 0.12f
    private const val LIFT_LO = 0.62f
    private const val LIFT_HI = 0.96f
    private const val LIFT_FLAT = 0.92f
    private const val FLAT_SPREAD = 0.08f
    private const val TRIM_ALPHA = 10
    private const val OUTLINE_ALPHA = 0.70f
    /** Largest side worked on; logos are drawn well under this, so nothing visible is lost. */
    const val MAX_SIDE = 720

    private fun a(p: Int) = ((p ushr 24) and 0xFF) / 255f
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun luma(p: Int) = (0.299f * r(p) + 0.587f * g(p) + 0.114f * b(p)) / 255f
    private fun chroma(p: Int) = (max(r(p), max(g(p), b(p))) - min(r(p), min(g(p), b(p)))) / 255f

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** What to do with a logo; [px] is non-premultiplied ARGB, row-major. */
    fun decide(px: IntArray, w: Int, h: Int): Mode {
        var solid = 0
        var darkSolid = 0
        var neutralDark = 0
        for (p in px) {
            if (a(p) < SOLID) continue
            solid++
            if (luma(p) < DARK) {
                darkSolid++
                if (chroma(p) < NEUTRAL_CHROMA) neutralDark++
            }
        }
        if (solid == 0 || darkSolid.toFloat() / solid < MIN_DARK_SHARE) return Mode.NONE
        // Dark strokes hugging light letters (black outline / shadow) already read fine.
        var nearLight = 0
        for (y in 0 until h) for (x in 0 until w) {
            val p = px[y * w + x]
            if (a(p) < SOLID || luma(p) >= DARK) continue
            var found = false
            loop@ for (dy in -2..2) for (dx in -2..2) {
                val xx = x + dx; val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val q = px[yy * w + xx]
                if (a(q) >= SOLID && luma(q) > LIGHT) { found = true; break@loop }
            }
            if (found) nearLight++
        }
        if (nearLight.toFloat() / darkSolid > OUTLINE_LIKE) return Mode.NONE
        return if (neutralDark.toFloat() / darkSolid >= NEUTRAL_SHARE) Mode.LIFT else Mode.OUTLINE
    }

    /**
     * A: the logo's neutral (grey / black) pixels brightened with one curve that keeps their order,
     * so texture and shading survive (2026-10-12: The Dark Knight's metal letters were flattened,
     * because only the darkest pixels were pushed to white and the mid greys stayed put).
     * The darkest neutral tone (5th percentile) goes to [LIFT_LO], the lightest (95th) to [LIFT_HI],
     * anything lighter still up to white; a flat black part (My Spy) goes to [LIFT_FLAT].
     * Coloured pixels are untouched; soft shadows away from the solid shapes are left alone.
     */
    fun lift(px: IntArray, w: Int, h: Int) {
        val neutralSolid = BooleanArray(px.size) { val p = px[it]; a(p) >= SOLID && chroma(p) < NEUTRAL_CHROMA }
        val hist = IntArray(256)
        var n = 0
        for (i in px.indices) if (neutralSolid[i]) { hist[(luma(px[i]) * 255f).roundToInt().coerceIn(0, 255)]++; n++ }
        if (n == 0) return
        fun pct(q: Float): Float {
            val target = (n * q).roundToInt().coerceIn(1, n)
            var acc = 0
            for (v in 0..255) { acc += hist[v]; if (acc >= target) return v / 255f }
            return 1f
        }
        val p5 = pct(0.05f)
        val p95 = max(pct(0.95f), p5)
        val spread = p95 - p5
        fun curve(l: Float): Float = when {
            spread < FLAT_SPREAD -> (LIFT_FLAT + (l - p5) * 0.5f)
            l <= p95 -> LIFT_LO + (LIFT_HI - LIFT_LO) * ((l - p5) / spread)
            else -> LIFT_HI + (1f - LIFT_HI) * ((l - p95) / max(1f - p95, 1e-3f))
        }.coerceIn(0f, 1f)
        val near = BooleanArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            if (!neutralSolid[y * w + x]) continue
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) near[yy * w + xx] = true
            }
        }
        for (i in px.indices) {
            val p = px[i]
            val alpha = a(p)
            if (alpha <= 0f) continue
            if (alpha < 0.6f && !near[i]) continue // soft shadow away from the shapes: leave it
            val k = 1f - smooth(0.10f, 0.20f, chroma(p)) // 1 = neutral, 0 = coloured
            if (k <= 0f) continue
            val l = luma(p)
            val target = curve(l)
            if (target <= l) continue // never darken
            fun ch(c: Int): Int {
                val mapped = if (l > 0.02f) c * (target / l) else target * 255f
                return (c + (mapped - c) * k).roundToInt().coerceIn(0, 255)
            }
            px[i] = (p and 0xFF000000.toInt()) or (ch(r(p)) shl 16) or (ch(g(p)) shl 8) or ch(b(p))
        }
    }

    /**
     * Bounds of the visible part of a logo (alpha above ~4%), with 1 px kept around it, or null when
     * the margins are too small to matter (under 2% of a side) or nothing is visible. Many TMDB logos
     * ship with wide transparent borders, which made them look small at the same box size.
     */
    fun trimBounds(px: IntArray, w: Int, h: Int): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > TRIM_ALPHA) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        if (r < 0) return null
        l = max(0, l - 1); t = max(0, t - 1); r = min(w - 1, r + 1); b = min(h - 1, b + 1)
        val cw = r - l + 1; val chh = b - t + 1
        if (cw >= w * 0.98f && chh >= h * 0.98f) return null
        return intArrayOf(l, t, cw, chh)
    }

    /** Outline radius in pixels for a w x h logo (~1 dp at the equal-area display size). */
    fun outlineRadius(w: Int, h: Int): Int = max(1, (0.011f * sqrt(w.toFloat() * h)).roundToInt())

    /** Padding added on each side by [outline] for radius [rad] (the soft last pixel included). */
    fun outlinePad(rad: Int): Int = rad + 1

    /** B: logo on a soft-edged white silhouette grown by [rad] px (+1 px soft edge); returns a (w+2p) x (h+2p) image, p = [outlinePad]. */
    fun outline(px: IntArray, w: Int, h: Int, rad: Int): IntArray {
        val pad = outlinePad(rad)
        val ow = w + 2 * pad
        val oh = h + 2 * pad
        val out = IntArray(ow * oh)
        val offsets = ArrayList<Triple<Int, Int, Float>>()
        for (dy in -rad - 1..rad + 1) for (dx in -rad - 1..rad + 1) {
            val cov = (rad + 1f - sqrt((dx * dx + dy * dy).toFloat())).coerceIn(0f, 1f) // full strength out to rad, soft last pixel
            if (cov > 0f) offsets += Triple(dx, dy, cov)
        }
        for (y in 0 until oh) for (x in 0 until ow) {
            val sx = x - pad; val sy = y - pad
            var halo = 0f
            for ((dx, dy, cov) in offsets) {
                val xx = sx + dx; val yy = sy + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val v = a(px[yy * w + xx]) * cov
                if (v > halo) { halo = v; if (halo >= 1f) break }
            }
            val ao = halo * OUTLINE_ALPHA
            val src = if (sx in 0 until w && sy in 0 until h) px[sy * w + sx] else 0
            val asrc = a(src)
            val outA = asrc + ao * (1f - asrc)
            if (outA <= 0f) continue
            fun ch(c: Int) = ((c * asrc + 255f * ao * (1f - asrc)) / outA).roundToInt().coerceIn(0, 255)
            out[y * ow + x] = ((outA * 255f).roundToInt() shl 24) or (ch(r(src)) shl 16) or (ch(g(src)) shl 8) or ch(b(src))
        }
        return out
    }
}

/** Coil step for clearlogos (see [NuvioCLogoContrast]). */
class NuvioCLogoContrastTransformation : Transformation() {
    override val cacheKey: String = "nuvio_c_logo_contrast_v2" +
        (if (NuvioCFeatures.LOGO_DARK_LIFT) "_lift" else "") + (if (NuvioCFeatures.LOGO_TRIM) "_trim" else "")

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        var src = input
        val longest = max(src.width, src.height)
        if (longest > NuvioCLogoContrast.MAX_SIDE) {
            val s = NuvioCLogoContrast.MAX_SIDE.toFloat() / longest
            src = Bitmap.createScaledBitmap(src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true)
        }
        if (src.config != Bitmap.Config.ARGB_8888) src = src.copy(Bitmap.Config.ARGB_8888, false) ?: return input
        var w = src.width
        var h = src.height
        var px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        // [fork] logo_trim (2026-10-12): drop transparent borders first, so sizing sees the real logo.
        var trimmed = false
        if (NuvioCFeatures.LOGO_TRIM) {
            NuvioCLogoContrast.trimBounds(px, w, h)?.let { (l, t, cw, ch) ->
                val out = IntArray(cw * ch)
                for (y in 0 until ch) System.arraycopy(px, (t + y) * w + l, out, y * cw, cw)
                px = out; w = cw; h = ch; trimmed = true
            }
        }
        val mode = if (NuvioCFeatures.LOGO_DARK_LIFT) NuvioCLogoContrast.decide(px, w, h) else NuvioCLogoContrast.Mode.NONE
        return when (mode) {
            NuvioCLogoContrast.Mode.NONE ->
                if (trimmed) Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) } else input
            NuvioCLogoContrast.Mode.LIFT -> {
                NuvioCLogoContrast.lift(px, w, h)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }
            }
            NuvioCLogoContrast.Mode.OUTLINE -> {
                val rad = NuvioCLogoContrast.outlineRadius(w, h)
                val out = NuvioCLogoContrast.outline(px, w, h, rad)
                val pad = NuvioCLogoContrast.outlinePad(rad)
                val ow = w + 2 * pad
                val oh = h + 2 * pad
                Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888).apply { setPixels(out, 0, ow, 0, 0, ow, oh) }
            }
        }
    }
}

/** Adds the clearlogo step (dark lift and / or transparent-border trim) to a request when either switch is on. */
fun ImageRequest.Builder.nuvioCLogoContrast(): ImageRequest.Builder =
    if (NuvioCFeatures.LOGO_DARK_LIFT || NuvioCFeatures.LOGO_TRIM) transformations(NuvioCLogoContrastTransformation()) else this
