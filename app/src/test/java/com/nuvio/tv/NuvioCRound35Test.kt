package com.nuvio.tv

import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.components.NuvioCSynopsisFit
import com.nuvio.tv.ui.screens.player.NuvioCTrackNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NuvioCRound35Test {
    @Test
    fun homeHeroCapsAtFourLines() {
        assertEquals(4, NuvioCSynopsisFit.homeLineCap())
        assertEquals(4, NuvioCSynopsisFit.linesFor(500, 50f, true, NuvioCSynopsisFit.homeLineCap()))
        assertEquals(4, NuvioCSynopsisFit.linesFor(0, 50f, false, NuvioCSynopsisFit.homeLineCap()))
    }

    @Test
    fun homeHeroWidthsAreTwentyPercentNarrower() {
        val base = NuvioCSynopsisFit.widths(872.dp, 470.dp)
        val home = NuvioCSynopsisFit.homeWidths(872.dp, 470.dp)
        assertEquals(base.size, home.size)
        base.zip(home).forEach { (b, h) -> assertEquals(b.value * 0.8f, h.value, 0.01f) }
    }

    @Test
    fun subtitleDescriptionFallsBackToTags() {
        assertEquals("SDH", NuvioCTrackNames.subtitleDescription("SDH", "English", forced = false))
        assertEquals("English [Forced] PGS", NuvioCTrackNames.subtitleDescription("English [Forced] PGS", "English", forced = true))
        assertEquals("Forced", NuvioCTrackNames.subtitleDescription("English", "English", forced = true))
        assertNull(NuvioCTrackNames.subtitleDescription("English", "English", forced = false))
    }
}
