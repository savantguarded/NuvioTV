package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// [fork] Nuvio C round 27: badge names, IMAX tile, single-source chip focus.
class NuvioCRound27Test {

    private val b = NuvioCOsdBadges

    @Test
    fun dolbyVisionAndAtmosAreSpelledOut() {
        assertEquals("DOLBY VISION", b.visualTile("DV"))
        assertEquals("HDR10", b.visualTile("HDR10"))
        assertEquals("HDR10+", b.visualTile("HDR10+"))
        assertEquals("HLG", b.visualTile("HLG"))
        assertEquals("DOLBY ATMOS 7.1", b.audioDisplay("ATMOS 7.1"))
        assertEquals("DD+ 5.1", b.audioDisplay("DD+ 5.1"))
        assertEquals("DTS-HD MA 7.1", b.audioDisplay("DTS-HD MA 7.1"))
        assertEquals("TRUEHD 7.1", b.audioDisplay("TRUEHD 7.1"))
    }

    @Test
    fun imaxFromReleaseName() {
        assertTrue(b.isImax("Dune.Part.Two.2024.IMAX.2160p.WEB-DL.DV"))
        assertTrue(b.isImax("Oppenheimer 2023 IMAX Enhanced 2160p"))
        assertTrue(b.isImax("film_imax_remux"))
        assertFalse(b.isImax("Maximum.Overdrive.1986.1080p"))
        assertFalse(b.isImax("Imaxine.2020"))
    }

    @Test
    fun hiddenAllChipRedirectsToTheSource() {
        assertEquals(2, nuvioCChipTarget(1, listOf("TorBox")))
        assertEquals(0, nuvioCChipTarget(0, listOf("TorBox")))
        assertEquals(1, nuvioCChipTarget(1, listOf("TorBox", "Comet")))
        assertEquals(3, nuvioCChipTarget(3, listOf("TorBox", "Comet")))
    }
}
