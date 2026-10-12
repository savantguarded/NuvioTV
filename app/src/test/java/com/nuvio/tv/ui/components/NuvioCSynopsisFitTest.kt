package com.nuvio.tv.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCSynopsisFitTest {
    private val widths = NuvioCSynopsisFit.widths(available = 872.dp, ours = 470.dp)

    @Test
    fun widthsAreOfficialOursAndOursPlus80() {
        assertEquals(listOf(872.dp * 0.42f, 470.dp, 550.dp), widths)
    }

    @Test
    fun shortTextKeepsOfficialWidth() {
        val r = NuvioCSynopsisFit.choose(widths) { _, _ -> true }
        assertEquals(NuvioCSynopsisFitResult(widths[0], 6), r)
    }

    @Test
    fun growsOnlyAsMuchAsNeeded() {
        val r = NuvioCSynopsisFit.choose(widths) { w, lines -> w >= 470.dp && lines == 6 }
        assertEquals(NuvioCSynopsisFitResult(470.dp, 6), r)
    }

    @Test
    fun tooLongUsesWidestAndEllipsizes() {
        val r = NuvioCSynopsisFit.choose(widths) { _, _ -> false }
        assertEquals(NuvioCSynopsisFitResult(550.dp, 6), r)
    }

    @Test
    fun neverPastAvailable() {
        assertEquals(listOf(400.dp * 0.42f, 400.dp), NuvioCSynopsisFit.widths(400.dp, 470.dp))
    }

    @Test
    fun linesFollowTheHeightLeft() {
        assertEquals(6, NuvioCSynopsisFit.linesFor(500, 50f, true))
        assertEquals(4, NuvioCSynopsisFit.linesFor(210, 50f, true))
        assertEquals(6, NuvioCSynopsisFit.linesFor(300, 50f, true)) // exactly 6 lines
        assertEquals(1, NuvioCSynopsisFit.linesFor(10, 50f, true))
        assertEquals(6, NuvioCSynopsisFit.linesFor(0, 50f, false))
    }

    @Test
    fun narrowFitAtFewerLinesIsNotTakenWhenTextOverflows() {
        // Only 5 lines of room: the narrow widths overflow at 5, the widest fits.
        val r = NuvioCSynopsisFit.choose(widths, lines = 5) { w, lines -> lines == 5 && w >= 550.dp }
        assertEquals(NuvioCSynopsisFitResult(550.dp, 5), r)
    }
}
