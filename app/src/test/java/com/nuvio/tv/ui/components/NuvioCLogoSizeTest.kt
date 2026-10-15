package com.nuvio.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCLogoSizeTest {
    @Test
    fun squareLogosGetTheTallCap() {
        assertEquals(92f, NuvioCLogoSize.DETAIL.sizeFor(1f).height.value, 0.5f)
        assertEquals(68f, NuvioCLogoSize.OSD.sizeFor(1f).height.value, 0.5f)
        assertEquals(140f, NuvioCLogoSize.HOME.sizeFor(1f).height.value, 0.5f)
    }

    @Test
    fun wideLogosUnchanged() {
        val s = NuvioCLogoSize.DETAIL.sizeFor(5f)
        assertEquals(212f, s.width.value, 1f)
        assertEquals(42f, s.height.value, 1f)
    }

    @Test
    fun unknownAspectIsTheMaxBox() {
        val s = NuvioCLogoSize.OSD.sizeFor(null)
        assertEquals(200f, s.width.value, 0.01f)
        assertEquals(52f, s.height.value, 0.01f)
    }
}
