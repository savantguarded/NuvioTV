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
//    just an outline around light letters -> those neutral dark pixels are lifted to near-white,
//    coloured parts stay as they are (SPY stays red). Soft shadows away from the solid dark shapes
//    are left alone, so no white halo.
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
    private const val LIFT_TO = 0xF2
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

    /** A: neutral dark pixels of the solid shapes (and their anti-aliased edges) lifted to near-white, in place. */
    fun lift(px: IntArray, w: Int, h: Int) {
        val core = BooleanArray(px.size) { val p = px[it]; a(p) >= SOLID && luma(p) < DARK && chroma(p) < NEUTRAL_CHROMA }
        val near = BooleanArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            if (!core[y * w + x]) continue
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
            val k = (1f - smooth(0.12f, 0.30f, luma(p))) * (1f - smooth(0.10f, 0.20f, chroma(p)))
            if (k <= 0f) continue
            fun mix(c: Int) = (c + (LIFT_TO - c) * k).roundToInt().coerceIn(0, 255)
            px[i] = (p and 0xFF000000.toInt()) or (mix(r(p)) shl 16) or (mix(g(p)) shl 8) or mix(b(p))
        }
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
    override val cacheKey: String = "nuvio_c_logo_contrast_v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        var src = input
        val longest = max(src.width, src.height)
        if (longest > NuvioCLogoContrast.MAX_SIDE) {
            val s = NuvioCLogoContrast.MAX_SIDE.toFloat() / longest
            src = Bitmap.createScaledBitmap(src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true)
        }
        if (src.config != Bitmap.Config.ARGB_8888) src = src.copy(Bitmap.Config.ARGB_8888, false) ?: return input
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        return when (NuvioCLogoContrast.decide(px, w, h)) {
            NuvioCLogoContrast.Mode.NONE -> input
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

/** Adds the dark-logo step to a clearlogo request when the switch is on. */
fun ImageRequest.Builder.nuvioCLogoContrast(): ImageRequest.Builder =
    if (NuvioCLogoContrast.enabled) transformations(NuvioCLogoContrastTransformation()) else this
