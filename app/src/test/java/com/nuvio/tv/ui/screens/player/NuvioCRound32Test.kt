package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.components.nuvioCImdbValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NuvioCRound32Test {
    @Test fun oldSavedOrderGetsPlayFirstAndKeepsItsOrder() {
        val parsed = NuvioCOsdOrder.parse("subtitles:m,start_over:m,info:x")
        assertEquals(NuvioCOsdButton.PLAY, parsed.first().button)
        assertEquals(NuvioCOsdButton.SUBTITLES, parsed[1].button)
        assertEquals(NuvioCOsdButton.entries.size, parsed.size)
    }

    @Test fun playNeverGoesBehindMore() {
        val parsed = NuvioCOsdOrder.parse("play:x")
        assertFalse(parsed.first { it.button == NuvioCOsdButton.PLAY }.inMore)
        val i = parsed.indexOfFirst { it.button == NuvioCOsdButton.PLAY }
        assertFalse(NuvioCOsdOrder.toggled(parsed, i)[i].inMore)
    }

    @Test fun imdbValue() {
        assertEquals("8.4", nuvioCImdbValue("8.40"))
        assertEquals("7.0", nuvioCImdbValue("7/10"))
        assertEquals("N/A", nuvioCImdbValue("N/A"))
    }
}
