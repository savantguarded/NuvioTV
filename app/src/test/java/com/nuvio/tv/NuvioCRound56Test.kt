package com.nuvio.tv

import com.nuvio.tv.core.nuvioc.NuvioCCloudSettings
import com.nuvio.tv.ui.screens.player.NuvioCTrackNames
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Nuvio C round 56: per-profile fork settings, built-in subtitle tags. */
class NuvioCRound56Test {
    private fun settings(style: String) = NuvioCCloudSettings.encode(mapOf("osd_style" to JsonPrimitive(style), "night_enabled" to JsonPrimitive(true)))

    @Test fun profileOwnCopyWinsOverCloudAndTv() {
        val seed = NuvioCCloudSettings.seedFor(own = settings("clean"), remote = settings("nuvio_c"), migrated = true)
        assertEquals("clean", seed?.get("osd_style")?.content)
    }

    @Test fun cloudCopyUsedWhenThisTvHasNone() {
        val seed = NuvioCCloudSettings.seedFor(own = null, remote = settings("clean"), migrated = true)
        assertEquals("clean", seed?.get("osd_style")?.content)
    }

    @Test fun firstRunKeepsThisTvsValues() {
        assertNull(NuvioCCloudSettings.seedFor(own = null, remote = null, migrated = false))
    }

    @Test fun newProfileGetsDefaultsNotThePreviousProfiles() {
        val seed = NuvioCCloudSettings.seedFor(own = null, remote = null, migrated = true)!!
        assertEquals("nuvio_c", seed["osd_style"]?.content)
        assertTrue(seed.keys.none { it.startsWith("calendar_") || it.startsWith("roulette_") })
    }

    @Test fun switchingBackAndForthNeverCrossesOver() {
        // A and B each keep their own copy; switching A -> B -> A reads each one's own copy every time
        val copies = mapOf(1 to settings("clean"), 2 to settings("nuvio_c"))
        listOf(1, 2, 1, 2).forEach { p ->
            val seed = NuvioCCloudSettings.seedFor(own = copies[p], remote = copies[3 - p], migrated = true)
            assertEquals(if (p == 1) "clean" else "nuvio_c", seed?.get("osd_style")?.content)
        }
    }

    @Test fun builtInSubtitleTags() {
        assertEquals("SDH", NuvioCTrackNames.shortTag("English SDH", forced = false))
        assertEquals("Forced", NuvioCTrackNames.shortTag("English", forced = true))
        assertEquals("SDH · Forced", NuvioCTrackNames.shortTag("eng (Hearing Impaired) [Forced]", forced = false))
        assertNull(NuvioCTrackNames.shortTag("English", forced = false))
        assertNull(NuvioCTrackNames.shortTag(null, forced = false))
    }
}
