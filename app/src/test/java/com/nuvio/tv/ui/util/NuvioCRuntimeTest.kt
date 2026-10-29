package com.nuvio.tv.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCRuntimeTest {
    @Test
    fun `add-on formats become h m`() {
        assertEquals("2h 37m", nuvioCRuntime("2hr37min"))
        assertEquals("2h 37m", nuvioCRuntime("2h37m"))
        assertEquals("2h 37m", nuvioCRuntime("2 hr 37 min"))
        assertEquals("2h 37m", nuvioCRuntime("157 min"))
        assertEquals("2h 37m", nuvioCRuntime("157"))
        assertEquals("2h", nuvioCRuntime("2hrs"))
        assertEquals("45m", nuvioCRuntime("45min"))
        assertEquals("1h 5m", nuvioCRuntime("1 hour 5 minutes"))
        assertEquals("2h 37m", nuvioCRuntime("2:37"))
    }

    @Test
    fun `unknown text is kept`() {
        assertEquals("Varies", nuvioCRuntime("Varies"))
        assertEquals("2h 75m", nuvioCRuntime("2h 75m"))
        assertEquals(null, nuvioCRuntime(null))
    }
}
