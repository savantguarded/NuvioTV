package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NightModeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [fork] Nuvio C round 52 decoded_dialogue_lift: one level, night mode first, never both. */
class NuvioCRound52AudioTest {
    @Test
    fun liftAppliesWhenNightModeIsOff() {
        val p = GainAudioProcessor()
        p.nuvioCSetDecodedLift(NightModeLevel.MEDIUM)
        assertEquals(NightModeLevel.MEDIUM, p.nuvioCEffectiveLevel())
    }

    @Test
    fun nightModeWinsAndIsNotStacked() {
        val p = GainAudioProcessor()
        p.nuvioCSetDecodedLift(NightModeLevel.MEDIUM)
        p.setNightMode(NightModeLevel.LIGHT) // e.g. the 6 PM schedule
        assertEquals(NightModeLevel.LIGHT, p.nuvioCEffectiveLevel())
        p.setNightMode(NightModeLevel.OFF)
        assertEquals(NightModeLevel.MEDIUM, p.nuvioCEffectiveLevel())
    }

    @Test
    fun nonDecodedTrackHasNoLift() {
        val p = GainAudioProcessor()
        p.nuvioCSetDecodedLift(NightModeLevel.OFF)
        assertEquals(NightModeLevel.OFF, p.nuvioCEffectiveLevel())
    }

    @Test
    fun mimeTypesMapToFormats() {
        assertEquals(NuvioCPassthrough.Format.DTS, NuvioCPassthrough.formatOf("audio/vnd.dts"))
        assertEquals(NuvioCPassthrough.Format.DTSHD, NuvioCPassthrough.formatOf("audio/vnd.dts.hd"))
        assertEquals(NuvioCPassthrough.Format.DTSHD, NuvioCPassthrough.formatOf("audio/vnd.dts.uhd;profile=p2"))
        assertEquals(NuvioCPassthrough.Format.DDP, NuvioCPassthrough.formatOf("audio/eac3-joc"))
        assertNull(NuvioCPassthrough.formatOf("audio/mp4a-latm"))
    }
}
