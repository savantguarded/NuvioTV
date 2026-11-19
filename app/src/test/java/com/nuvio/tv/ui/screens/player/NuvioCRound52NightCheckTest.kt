package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NightModeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Nuvio C round 52 night_mode_check: the 6 PM to 8 AM schedule window and the one-level rule still hold. */
class NuvioCRound52NightCheckTest {
    private val from = NuvioCNight.DEFAULT_FROM_MIN
    private val until = NuvioCNight.DEFAULT_UNTIL_MIN

    @Test
    fun scheduleWindowCrossesMidnight() {
        assertTrue(NuvioCNight.inWindow(18 * 60, from, until))
        assertTrue(NuvioCNight.inWindow(23 * 60 + 59, from, until))
        assertTrue(NuvioCNight.inWindow(7 * 60 + 59, from, until))
        assertFalse(NuvioCNight.inWindow(8 * 60, from, until))
        assertFalse(NuvioCNight.inWindow(12 * 60, from, until))
    }

    @Test
    fun stepsStayInRange() {
        assertEquals(NightModeLevel.OFF, NuvioCNight.next(NightModeLevel.OFF, -1))
        assertEquals(NightModeLevel.STRONG, NuvioCNight.next(NightModeLevel.STRONG, 1))
    }

    @Test
    fun scheduledNightReplacesDecodedLiftInsteadOfStacking() {
        val p = GainAudioProcessor()
        p.nuvioCSetDecodedLift(NightModeLevel.MEDIUM)
        p.setNightMode(NightModeLevel.STRONG)
        assertEquals(NightModeLevel.STRONG, p.nuvioCEffectiveLevel())
    }
}
