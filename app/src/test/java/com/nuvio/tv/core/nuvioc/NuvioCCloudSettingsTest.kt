package com.nuvio.tv.core.nuvioc

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NuvioCCloudSettingsTest {
    @Test
    fun encodingIgnoresInsertionOrder() {
        val a = NuvioCCloudSettings.encode(linkedMapOf("b" to JsonPrimitive(true), "a" to JsonPrimitive(3)))
        val b = NuvioCCloudSettings.encode(linkedMapOf("a" to JsonPrimitive(3), "b" to JsonPrimitive(true)))
        assertEquals(a, b)
    }

    @Test
    fun roundTrip() {
        val text = NuvioCCloudSettings.encode(mapOf("subtitle_font" to JsonPrimitive("geist"), "calendar_window_days" to JsonPrimitive(60)))
        val back = NuvioCCloudSettings.decode(text)!!
        assertEquals("geist", back["subtitle_font"]?.content)
        assertEquals("60", back["calendar_window_days"]?.content)
    }

    @Test
    fun brokenTextIsIgnored() {
        assertNull(NuvioCCloudSettings.decode("not json"))
    }

    @Test
    fun defaultsCoverEveryTravellingSetting() {
        val keys = NuvioCCloudSettings.decode(NuvioCCloudSettings.DEFAULTS)!!.keys
        assertEquals(14, keys.size) // 13 settings + version
    }
}
