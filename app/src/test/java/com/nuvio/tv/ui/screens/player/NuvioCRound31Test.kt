package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCRound31Test {
    @Test
    fun `dolby digital spelled out`() {
        assertEquals("DOLBY DIGITAL PLUS 5.1", NuvioCOsdBadges.dolbyFull("DD+ 5.1"))
        assertEquals("DOLBY DIGITAL 5.1", NuvioCOsdBadges.dolbyFull("DD 5.1"))
        assertEquals("DOLBY ATMOS 7.1", NuvioCOsdBadges.dolbyFull("DOLBY ATMOS 7.1"))
        assertEquals("DTS-HD MA 7.1", NuvioCOsdBadges.dolbyFull("DTS-HD MA 7.1"))
    }
}
