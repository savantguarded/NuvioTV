package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Round 64 osd_badges_v64: details-page order, logos, capital resolution, no channels, IMAX and size last. */
class NuvioCRound64OsdTest {
    private fun show(b: List<NuvioCBadge>) = b.map { if (it.kind == NuvioCBadgeKind.LOGO) "[${it.logo}${it.text}]" else it.text }

    @Test fun dolbyLastNoChannels() {
        val b = NuvioCOsdBadges.order64("4K", "REMUX", "DV", "ATMOS 7.1", imax = true, size = "58.2 GB")
        assertEquals(listOf("4K", "REMUX", "[dolby_vision]", "[dolby_atmos]", "IMAX", "58.2 GB"), show(b))
        assertEquals(NuvioCBadgeKind.FILLED, b.last().kind)
    }

    @Test fun resolutionInCapitalsAndSoundLogos() {
        assertEquals(listOf("1080P", "WEB-DL", "[dolby_digital_plus]"), show(NuvioCOsdBadges.order64("1080p", "WEB-DL", "SDR", "DD+ 5.1", false, null)))
        assertEquals(listOf("720P", "[dolby_digital]"), show(NuvioCOsdBadges.order64("720p", null, null, "DD 2.0", false, null)))
        assertEquals(listOf("1080P", "BLURAY", "HDR10", "[dtsHD MA]"), show(NuvioCOsdBadges.order64("1080p", "BLURAY", "HDR10", "DTS-HD MA 7.1", false, null)))
        assertEquals(listOf("4K", "[dts:X]", "[dolby_vision]"), show(NuvioCOsdBadges.order64("4K", null, "DV", "DTS:X 7.1", false, null)))
        assertEquals(listOf("4K", "BLURAY", "TRUEHD", "[dolby_vision]"), show(NuvioCOsdBadges.order64("4K", "BLURAY", "DV", "TRUEHD 7.1", false, null)))
        assertEquals(listOf("1080P", "AAC"), show(NuvioCOsdBadges.order64("1080p", null, null, "AAC 2.0", false, null)))
    }
}
