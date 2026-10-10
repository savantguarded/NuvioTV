package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCSdhTest {
    @Test
    fun sdhLabels() {
        assertTrue(NuvioCSdh.isSdhText("English SDH"))
        assertTrue(NuvioCSdh.isSdhText("English [SDH]"))
        assertTrue(NuvioCSdh.isSdhText("English (CC)"))
        assertTrue(NuvioCSdh.isSdhText("English - Hearing Impaired"))
        assertTrue(NuvioCSdh.isSdhText(null, "eng.sdh"))
        assertFalse(NuvioCSdh.isSdhText("English"))
        assertFalse(NuvioCSdh.isSdhText("English Forced"))
        assertFalse(NuvioCSdh.isSdhText("Accent commentary"))
        assertFalse(NuvioCSdh.isSdhText(null, null))
    }
}
