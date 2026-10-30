package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.ui.screens.calendar.nuvioCCardHue
import com.nuvio.tv.ui.screens.player.NuvioCLoadingLaneText
import com.nuvio.tv.ui.screens.player.NuvioCPlayerRatings
import com.nuvio.tv.ui.screens.player.episodeRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCRound33Test {
    @Test fun tallLogoLeavesRoomForFourSynopsisLines() {
        // 600 px free, badges/ratings 120 px, four lines of 30 px, 8 px gap -> logo may take 352 px at most
        assertEquals(352, NuvioCFeaturedYieldRules.logoMax(600, 0, 120, 120, 8, 128))
        // tight space: the logo keeps its minimum
        assertEquals(128, NuvioCFeaturedYieldRules.logoMax(300, 0, 120, 120, 8, 128))
    }

    @Test fun synopsisGetsWhatIsLeft() {
        assertEquals(232, NuvioCFeaturedYieldRules.synMax(600, 240, 120, 8))
        assertEquals(0, NuvioCFeaturedYieldRules.synMax(100, 240, 120, 8))
    }

    @Test fun titleCardHueIsStableAndInRange() {
        val a = nuvioCCardHue("Murderbot")
        assertEquals(a, nuvioCCardHue("  murderbot "), 0f)
        assertTrue(a in 0f..360f)
        assertEquals(220f, nuvioCCardHue(null), 0f)
    }

    @Test fun addonRatingsFillGapsButImdbWins() {
        val r = NuvioCPlayerRatings(
            episodeRatings = mapOf((1 to 1) to 8.5),
            addonEpisodeRatings = mapOf((1 to 1) to 7.0, (1 to 2) to 7.4)
        )
        assertEquals("8.5", r.episodeRating(1, 1))
        assertEquals("7.4", r.episodeRating(1, 2))
        assertNull(r.episodeRating(1, 3))
    }

    @Test fun filenameSplitKeepsExtension() {
        val (base, ext) = NuvioCLoadingLaneText.splitExtension("Rick.and.Morty.S08E04.2160p-FLUX.mkv")
        assertEquals("Rick.and.Morty.S08E04.2160p-FLUX", base)
        assertEquals(".mkv", ext)
    }
}
