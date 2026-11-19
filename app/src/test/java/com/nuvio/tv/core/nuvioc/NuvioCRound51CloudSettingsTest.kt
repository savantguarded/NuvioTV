package com.nuvio.tv.core.nuvioc

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCRound51CloudSettingsTest {
    @Test fun defaultsCarryTheNewSettings() {
        val d = NuvioCCloudSettings.decode(NuvioCCloudSettings.DEFAULTS)!!
        assertEquals("nuvio_c", d["osd_style"]?.content)
        assertTrue(d.containsKey("tvdb_key"))
        assertTrue(d.containsKey("night_enabled"))
    }

    @Test fun passthroughNeverTravels() {
        assertTrue(NuvioCCloudSettings.decode(NuvioCCloudSettings.DEFAULTS)!!.keys.none { it.startsWith("pt_") })
    }

    @Test fun encodeIsStableAndDecodeRejectsGarbage() {
        val a = NuvioCCloudSettings.encode(mapOf("b" to JsonPrimitive(1), "a" to JsonPrimitive("x")))
        val b = NuvioCCloudSettings.encode(mapOf("a" to JsonPrimitive("x"), "b" to JsonPrimitive(1)))
        assertEquals(a, b)
        assertNotNull(NuvioCCloudSettings.decode(a))
        assertNull(NuvioCCloudSettings.decode("not json"))
    }
}
