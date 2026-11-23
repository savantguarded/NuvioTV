package com.nuvio.tv

import com.nuvio.tv.ui.screens.detail.NuvioCAwardBodies
import com.nuvio.tv.ui.screens.detail.NuvioCAwardBody
import com.nuvio.tv.ui.screens.detail.NuvioCAwards
import com.nuvio.tv.ui.screens.detail.nuvioCBasedOn
import com.nuvio.tv.ui.screens.player.NuvioCPlaybackLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Nuvio C round 43: award grouping, Based on, the DV-named check, resume switch. */
class NuvioCRound43Test {

    @Test fun awardsWinsOnly() { // [fork] round 47: wins only, nominations ignored, shared categories once
        val a = NuvioCAwardBodies.group(
            listOf(
                Triple("nom", "Academy Award for Best Actress", "Q1"),
                Triple("win", "Academy Award for Best Picture", "Q0"),
                Triple("win", "Academy Award for Best Visual Effects", "Q5"),
                Triple("win", "Academy Award for Best Visual Effects", "Q6"),
                Triple("win", "Golden Globe Award for Best Actress – Motion Picture Drama", "Q1"),
                Triple("win", "Palme d'Or", "Q0"),
                Triple("win", "National Board of Review: Top Ten Films", "Q0")
            )
        )
        val oscars = a.bodies.getValue(NuvioCAwardBody.OSCARS)
        assertEquals(2, oscars.wins)
        assertEquals(listOf("Best Picture", "Best Visual Effects"), oscars.categories)
        assertEquals(3, a.bodies.size)
        val names = mapOf(NuvioCAwardBody.OSCARS to "Academy Awards", NuvioCAwardBody.GLOBES to "Golden Globes")
        assertEquals(
            "Academy Awards: Best Picture, Best Visual Effects\nGolden Globes: Best Actress – Motion Picture Drama\nPalme d'Or",
            NuvioCAwardBodies.winsText(a, names)
        )
        assertNull(NuvioCAwardBodies.winsText(NuvioCAwards(emptyMap()), names))
    }

    @Test fun basedOnFromCredits() {
        val credits = Json.parseToJsonElement(
            """{"crew":[{"job":"Screenplay","name":"Donald E. Westlake"},{"job":"Novel","name":"Jim Thompson"}]}"""
        ).jsonObject
        assertEquals("Novel by Jim Thompson", nuvioCBasedOn(credits))
        assertNull(nuvioCBasedOn(Json.parseToJsonElement("""{"crew":[{"job":"Director","name":"A"}]}""").jsonObject))
        assertNull(nuvioCBasedOn(null))
    }

    @Test fun dolbyVisionNamesAreWords() {
        assertTrue(NuvioCPlaybackLog.namesDolbyVision("Show.S01E01.2160p.DV.HDR.mkv"))
        assertTrue(NuvioCPlaybackLog.namesDolbyVision("Film 2024 DoVi HDR10"))
        assertTrue(NuvioCPlaybackLog.namesDolbyVision("Film [Dolby Vision]"))
        assertFalse(NuvioCPlaybackLog.namesDolbyVision("DVD.Rip.Advanced.mkv"))
        assertFalse(NuvioCPlaybackLog.namesDolbyVision("Dvorak.2019.mkv"))
    }
}
