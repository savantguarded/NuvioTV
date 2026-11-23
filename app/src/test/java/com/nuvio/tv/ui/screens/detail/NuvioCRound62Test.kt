package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCRound62Test {

    @Test
    fun `labels go resolution, source, TrueHD, visual, Atmos`() {
        assertEquals(listOf("4K", "BLURAY", "DOLBY VISION", "ATMOS"), nuvioCLabels62(listOf("4K", "DOLBY VISION", "ATMOS", "REMUX")))
        assertEquals(listOf("4K", "BLURAY", "TRUEHD", "HDR10"), nuvioCLabels62(listOf("4K", "HDR10", "TRUEHD", "BLURAY")))
    }

    @Test
    fun `other sound labels are dropped`() {
        assertEquals(listOf("1080P", "WEB-DL", "HDR10"), nuvioCLabels62(listOf("1080P", "HDR10", "DTS-HD", "WEB-DL")))
        assertEquals(listOf("1080P", "WEB-DL"), nuvioCLabels62(listOf("1080P", "DOLBY DIGITAL PLUS", "WEB-DL")))
        assertEquals(listOf("720P"), nuvioCLabels62(listOf("720P")))
    }

    @Test
    fun `show years read cleanly`() {
        assertEquals("2023", nuvioCYears62("2023-"))
        assertEquals("2023", nuvioCYears62("2023–"))
        assertEquals("2019–2023", nuvioCYears62("2019-2023"))
        assertEquals("2021", nuvioCYears62("2021-2021"))
        assertEquals("2026", nuvioCYears62("2026"))
    }
}
