package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** [fork] Nuvio C round 53 passthrough_per_profile: each profile's soundbar switches live under their own keys. */
class NuvioCRound53AudioTest {
    @Test
    fun keysFollowTheActiveProfile() {
        val before = NuvioCPassthrough.profileIdProvider
        try {
            NuvioCPassthrough.profileIdProvider = { 2 }
            val two = NuvioCPassthrough.key("pt_dts")
            NuvioCPassthrough.profileIdProvider = { 3 }
            val three = NuvioCPassthrough.key("pt_dts")
            assertEquals("p2_pt_dts", two)
            assertEquals("p3_pt_dts", three)
            assertNotEquals(two, three)
        } finally {
            NuvioCPassthrough.profileIdProvider = before
        }
    }
}
