package com.nuvio.tv

import com.nuvio.tv.ui.screens.detail.nuvioCIsLogoLabel
import com.nuvio.tv.ui.screens.home.NuvioCFeaturedKey
import com.nuvio.tv.ui.screens.home.nuvioCFeaturedKey
import com.nuvio.tv.ui.screens.player.NuvioCAudioSyncNow
import com.nuvio.tv.ui.screens.player.nuvioCAudioSyncNowCheck
import com.nuvio.tv.ui.screens.profile.nuvioCProfileShadeStops66c
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 66c: profile shade stops, logo vs filled badges, carousel Right, More panel sync reasons. */
class NuvioCRound66cTest {

    @Test fun profileShadeEndsAt30PercentAndOnlyFades() {
        val stops = nuvioCProfileShadeStops66c()
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(0.97f, stops.first().second, 0.001f)
        // fully clear from 30% of the width on
        assertEquals(0f, stops.first { it.first >= 0.30f - 1e-4f }.second, 0f)
        assertEquals(1f to 0f, stops.last())
        // positions rise, darkness never goes back up
        stops.zipWithNext().forEach { (a, b) ->
            assertTrue(b.first >= a.first)
            assertTrue(b.second <= a.second)
        }
        // enough steps for a smooth curve
        assertTrue(stops.count { it.first <= 0.30f } >= 8)
    }

    @Test fun soundLogosStayLogosWordBadgesGetFilled() {
        assertTrue(nuvioCIsLogoLabel("DOLBY VISION"))
        assertTrue(nuvioCIsLogoLabel("ATMOS"))
        assertTrue(nuvioCIsLogoLabel("DTS-HD MA"))
        listOf("4K", "WEB-DL", "HDR10", "HDR10+", "REMUX", "IMAX").forEach { assertFalse(it, nuvioCIsLogoLabel(it)) }
    }

    @Test fun rightOnDetailsGoesToNextTitle() {
        // buttons: 0 = Play, 1 = Details
        assertEquals(NuvioCFeaturedKey.NEXT, nuvioCFeaturedKey(right = true, focusedButton = 1, lastButton = 1, leftArmed = false, count = 5))
        assertEquals(NuvioCFeaturedKey.PASS, nuvioCFeaturedKey(right = true, focusedButton = 0, lastButton = 1, leftArmed = false, count = 5))
        assertEquals(NuvioCFeaturedKey.PREVIOUS, nuvioCFeaturedKey(right = false, focusedButton = 0, lastButton = 1, leftArmed = false, count = 5))
        assertEquals(NuvioCFeaturedKey.PASS, nuvioCFeaturedKey(right = false, focusedButton = 0, lastButton = 1, leftArmed = true, count = 5))
    }

    @Test fun moreSyncReasons() {
        assertEquals(NuvioCAudioSyncNow.NO_ADDON_SUBTITLE, nuvioCAudioSyncNowCheck(false, true, true, false))
        assertEquals(NuvioCAudioSyncNow.SETTING_OFF, nuvioCAudioSyncNowCheck(true, false, false, false))
        assertEquals(NuvioCAudioSyncNow.MPV_SAMPLING_OFF, nuvioCAudioSyncNowCheck(true, true, true, true))
        assertEquals(NuvioCAudioSyncNow.STARTED, nuvioCAudioSyncNowCheck(true, true, false, true))
    }

    @Test fun switchesOn() {
        assertTrue(NuvioCFeatures.FEATURED_V66C)
        assertTrue(NuvioCFeatures.GRAPH_AVG_PLAIN)
        assertTrue(NuvioCFeatures.PROFILE_SHADE_V66C)
        assertTrue(NuvioCFeatures.SYNC_AUDIO_IN_MORE)
    }
}
