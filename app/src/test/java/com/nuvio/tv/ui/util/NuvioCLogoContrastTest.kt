package com.nuvio.tv.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCLogoContrastTest {
    private val black = 0xFF101010.toInt()
    private val red = 0xFFE3262B.toInt()
    private val white = 0xFFF5F5F5.toInt()
    private val navy = 0xFF0A1A4A.toInt()
    private val clear = 0x00000000

    private fun img(w: Int, h: Int, f: (Int, Int) -> Int) = IntArray(w * h) { f(it % w, it / w) }

    @Test
    fun mySpyLikeLogoIsLifted() {
        // Left third black block ("MY"), right two thirds red ("SPY"), thin black tagline under it.
        val w = 90; val h = 30
        val px = img(w, h) { x, y ->
            when {
                y < 20 && x < 28 -> black
                y < 20 && x >= 32 -> red
                y in 24..27 && x in 10..80 -> black
                else -> clear
            }
        }
        assertEquals(NuvioCLogoContrast.Mode.LIFT, NuvioCLogoContrast.decide(px, w, h))
        NuvioCLogoContrast.lift(px, w, h)
        assertTrue((px[5 * w + 5] and 0xFF) > 0xE0) // black part now near-white
        assertEquals(red, px[5 * w + 50]) // red untouched
        assertTrue((px[25 * w + 40] and 0xFF) > 0xE0) // tagline lifted
    }

    @Test
    fun whiteLogoIsLeftAlone() {
        val px = img(60, 20) { x, _ -> if (x in 5..55) white else clear }
        assertEquals(NuvioCLogoContrast.Mode.NONE, NuvioCLogoContrast.decide(px, 60, 20))
    }

    @Test
    fun whiteLettersWithBlackStrokeAreLeftAlone() {
        // 3 px white bars separated by 1 px black strokes: dark always next to light.
        val px = img(60, 20) { x, y -> if (y in 2..17 && x in 2..57) (if (x % 4 == 0 || y == 2 || y == 17) black else white) else clear }
        assertEquals(NuvioCLogoContrast.Mode.NONE, NuvioCLogoContrast.decide(px, 60, 20))
    }

    @Test
    fun darkColouredLogoGetsOutline() {
        val w = 60; val h = 20
        val px = img(w, h) { x, y -> if (y in 4..15 && x in 4..55) navy else clear }
        assertEquals(NuvioCLogoContrast.Mode.OUTLINE, NuvioCLogoContrast.decide(px, w, h))
        val out = NuvioCLogoContrast.outline(px, w, h, NuvioCLogoContrast.outlineRadius(w, h))
        val r = NuvioCLogoContrast.outlinePad(NuvioCLogoContrast.outlineRadius(w, h))
        val ow = w + 2 * r
        assertEquals(navy, out[(10 + r) * ow + 30 + r]) // logo itself unchanged
        val edge = out[(10 + r) * ow + 3 + r] // just outside the logo: light outline
        assertTrue((edge ushr 24) > 0x80 && (edge and 0xFF) > 0xE0)
        assertEquals(0, out[0]) // far corner stays clear
    }

    @Test
    fun softShadowAwayFromShapesIsNotLifted() {
        val w = 60; val h = 20
        val shadow = 0x40000000
        val px = img(w, h) { x, y -> if (y < 10 && x < 25) black else if (y < 10 && x >= 30) red else if (y >= 14) shadow else clear }
        assertEquals(NuvioCLogoContrast.Mode.LIFT, NuvioCLogoContrast.decide(px, w, h))
        NuvioCLogoContrast.lift(px, w, h)
        assertEquals(shadow, px[18 * w + 40])
    }
}
