package com.nuvio.tv

import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.screens.detail.NuvioCAwardBodies
import com.nuvio.tv.ui.screens.detail.NuvioCAwardBody
import com.nuvio.tv.ui.screens.detail.nuvioCBasedOn
import com.nuvio.tv.ui.screens.detail.nuvioCFindEpisodes
import com.nuvio.tv.ui.screens.player.NuvioCPlaybackLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Nuvio C round 43: episode Find, award grouping, Based on, the DV-named check, resume switch. */
class NuvioCRound43Test {
    private fun ep(s: Int, e: Int, title: String = "Episode $e") =
        Video(id = "x:$s:$e", title = title, released = null, thumbnail = null, season = s, episode = e, overview = null)

    private val show = (1..3).flatMap { s -> (1..12).map { e -> ep(s, e) } } +
        listOf(ep(2, 5, "The Wedding Planner").let { it.copy(id = "w") }, ep(0, 1, "Behind the scenes"))

    @Test fun findNumberListsEveryRegularSeasonInOrder() {
        val r = nuvioCFindEpisodes(show, "12")
        assertEquals(listOf(1, 2, 3), r.take(3).map { it.season })
        assertTrue(r.all { it.episode == 12 })
    }

    @Test fun findNumberAddsAbsoluteEpisodeWhenDifferent() {
        val r = nuvioCFindEpisodes(show.filter { it.id != "w" }, "e14")
        assertEquals(1, r.size)
        assertEquals(2 to 2, r.first().season to r.first().episode) // 14th overall = S2 E2
    }

    @Test fun findSeasonEpisodeForms() {
        listOf("s3e5", "S03E05", "3x05", "3 x 5").forEach { q ->
            val r = nuvioCFindEpisodes(show, q)
            assertEquals(q, 1, r.size)
            assertEquals(q, 3 to 5, r.first().season to r.first().episode)
        }
    }

    @Test fun findWordsIgnoreCaseAndAccents() {
        assertEquals(listOf("w"), nuvioCFindEpisodes(show, "wédding plan").map { it.id })
        assertTrue(nuvioCFindEpisodes(show, "   ").isEmpty())
        assertTrue(nuvioCFindEpisodes(show, "nothing like this").isEmpty())
    }

    @Test fun awardsGroupByBodyWithWinsFirst() {
        val a = NuvioCAwardBodies.group(
            listOf(
                Triple("nom", "Academy Award for Best Actress", "Q1"),
                Triple("nom", "Academy Award for Best Director", "Q2"),
                Triple("win", "Academy Award for Best Picture", "Q0"),
                Triple("win", "Golden Globe Award for Best Actress – Motion Picture Drama", "Q1"),
                Triple("win", "Palme d'Or", "Q0"),
                Triple("win", "National Board of Review: Top Ten Films", "Q0")
            )
        )
        val oscars = a.bodies.getValue(NuvioCAwardBody.OSCARS)
        assertEquals(1, oscars.wins)
        assertEquals(3, oscars.nominations)
        assertEquals("Best Picture", oscars.categories.first())
        assertEquals(1, a.bodies.getValue(NuvioCAwardBody.GLOBES).wins)
        assertEquals(1, a.bodies.getValue(NuvioCAwardBody.FESTIVALS).wins)
        assertEquals(3, a.bodies.size) // National Board of Review is outside the five
        assertEquals("1 win · 3 nominations", NuvioCAwardBodies.countText(oscars.wins, oscars.nominations))
    }

    @Test fun awardsOverallFromOmdbText() {
        assertEquals("152 wins · 220 nominations", NuvioCAwardBodies.overall("Won 4 Oscars · 152 wins & 220 nominations"))
        assertEquals("3 nominations", NuvioCAwardBodies.overall("3 nominations"))
        assertNull(NuvioCAwardBodies.overall("Nothing here"))
        assertNull(NuvioCAwardBodies.overall(null))
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

    @Test fun resumeIsOfficialAgain() {
        assertFalse(NuvioCFeatures.DV_RESUME_HEADER_FIRST)
    }
}
