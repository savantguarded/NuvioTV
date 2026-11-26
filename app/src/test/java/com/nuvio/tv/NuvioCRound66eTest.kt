package com.nuvio.tv

import com.nuvio.tv.core.nuvioc.NuvioCFeaturedTag66e
import com.nuvio.tv.core.nuvioc.NuvioCFeaturedTagRules
import com.nuvio.tv.core.nuvioc.NuvioCTagFacts
import com.nuvio.tv.ui.screens.detail.nuvioCIsResolutionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Round 66e: context line second part, cinema lines retired, filled resolutions. */
class NuvioCRound66eTest {
    private val today = LocalDate.of(2026, 11, 26)

    private fun id(
        isSeries: Boolean = true, type: String? = "Scripted", status: String? = "Returning Series", next: Boolean = false,
        latest: LocalDate? = null, networks: List<String> = emptyList(), studios: List<String> = emptyList()
    ) = NuvioCFeaturedTag66e.identity(isSeries, type, status, next, latest, networks, studios, today)

    @Test fun limitedSeriesWinsFirst() {
        assertEquals("LIMITED SERIES", id(type = "Miniseries", networks = listOf("HBO")))
    }

    @Test fun finalSeasonOnlyForFinishedShowsStillAiringOrJustStarted() {
        assertEquals("FINAL SEASON", id(status = "Ended", next = true))
        assertEquals("FINAL SEASON", id(status = "Ended", latest = today.minusDays(20)))
        // ended long ago: not "final season"
        assertNull(id(status = "Ended", latest = today.minusDays(400)))
        // still returning: never
        assertNull(id(status = "Returning Series", next = true))
    }

    @Test fun streamerOriginalOnlyForShowsAndFirstNetwork() {
        assertEquals("APPLE TV+ ORIGINAL", id(networks = listOf("Apple TV+")))
        assertEquals("NETFLIX ORIGINAL", id(networks = listOf("Netflix")))
        assertNull(id(networks = listOf("BBC One", "Netflix")))
        // films never read "original"
        assertNull(id(isSeries = false, type = null, status = "Released", networks = listOf("Netflix")))
    }

    @Test fun studiosForFilmsAndShows() {
        assertEquals("A24", id(isSeries = false, type = null, status = "Released", studios = listOf("Plan B", "A24")))
        assertEquals("STUDIO GHIBLI", id(isSeries = false, type = null, status = "Released", studios = listOf("Studio Ghibli")))
        assertNull(id(isSeries = false, type = null, status = "Released", studios = listOf("Warner Bros. Pictures")))
    }

    @Test fun editionAndAcclaim() {
        assertEquals("DIRECTOR'S CUT AVAILABLE", NuvioCFeaturedTag66e.edition(listOf("OPEN MATTE", "DIRECTOR'S CUT")))
        assertEquals("EXTENDED EDITION AVAILABLE", NuvioCFeaturedTag66e.edition(listOf("EXTENDED")))
        assertNull(NuvioCFeaturedTag66e.edition(listOf("B&W")))
        assertEquals("CRITICALLY ACCLAIMED", NuvioCFeaturedTag66e.acclaimed(84.0, null, false))
        assertEquals("CRITICALLY ACCLAIMED", NuvioCFeaturedTag66e.acclaimed(null, 97.0, true))
        assertNull(NuvioCFeaturedTag66e.acclaimed(70.0, 97.0, false))
    }

    @Test fun joinTwoPartsOrOne() {
        assertEquals("NEW EPISODE TUESDAY  •  APPLE TV+ ORIGINAL", NuvioCFeaturedTag66e.join("NEW EPISODE TUESDAY", "APPLE TV+ ORIGINAL"))
        assertEquals("A24", NuvioCFeaturedTag66e.join(null, "A24"))
        assertEquals("NEW RELEASE", NuvioCFeaturedTag66e.join("NEW RELEASE", null))
        assertNull(NuvioCFeaturedTag66e.join(null, " "))
    }

    @Test fun cinemaLinesRetiredDigitalKept() {
        val inCinemas = NuvioCTagFacts(isSeries = false, cinemaDate = today.minusDays(5))
        val line = NuvioCFeaturedTagRules.filmTagV2(inCinemas, today, Locale.US)
        assertFalse(line?.contains("CINEMAS") == true)
        val digital = NuvioCTagFacts(isSeries = false, digitalDate = today.minusDays(2))
        assertTrue(NuvioCFeaturedTagRules.filmTagV2(digital, today, Locale.US)!!.startsWith("NEW ON DIGITAL"))
    }

    @Test fun resolutionsAreFilled() {
        listOf("4K", "1080P", "720P", "480P").forEach { assertTrue(it, nuvioCIsResolutionLabel(it)) }
        listOf("WEB-DL", "BLURAY", "HDR10", "DOLBY VISION").forEach { assertFalse(it, nuvioCIsResolutionLabel(it)) }
    }
}
