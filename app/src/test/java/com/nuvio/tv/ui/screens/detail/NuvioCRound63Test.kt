package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCRound63Test {
    @Test
    fun `runtime reads hr and min`() {
        assertEquals("1 hr 49 min", nuvioCHrMin("1h 49m"))
        assertEquals("2 hr", nuvioCHrMin("2h"))
        assertEquals("24 min", nuvioCHrMin("24m"))
    }

    @Test
    fun `awards list one body per line`() {
        val awards = NuvioCAwards(mapOf(
            NuvioCAwardBody.OSCARS to NuvioCAwardLine(wins = 1, nominations = 2, categories = listOf("Best Picture")),
            NuvioCAwardBody.BAFTA to NuvioCAwardLine(wins = 1, nominations = 1, categories = listOf("Best Film"))
        ))
        val names = mapOf(NuvioCAwardBody.OSCARS to "Oscars", NuvioCAwardBody.BAFTA to "BAFTA")
        assertEquals("Oscars: Best Picture\nBAFTA: Best Film", NuvioCAwardBodies.winsText(awards, names))
    }
}
