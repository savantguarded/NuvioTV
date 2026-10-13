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
        assertEquals(NuvioCSynopsisFitResult(widths[0], NuvioCSynopsisFit.MAX_LINES), r)
    }

    @Test
    fun growsOnlyAsMuchAsNeeded() {
        val r = NuvioCSynopsisFit.choose(widths) { w, lines -> w >= 470.dp && lines == NuvioCSynopsisFit.MAX_LINES }
        assertEquals(NuvioCSynopsisFitResult(470.dp, NuvioCSynopsisFit.MAX_LINES), r)
    }

    @Test
    fun tooLongUsesWidestAndEllipsizes() {
        val r = NuvioCSynopsisFit.choose(widths) { _, _ -> false }
        assertEquals(NuvioCSynopsisFitResult(550.dp, NuvioCSynopsisFit.MAX_LINES), r)
    }

    @Test
    fun neverPastAvailable() {
        assertEquals(listOf(400.dp * 0.42f, 400.dp), NuvioCSynopsisFit.widths(400.dp, 470.dp))
    }

    @Test
    fun linesFollowTheHeightLeft() {
        assertEquals(5, NuvioCSynopsisFit.linesFor(500, 50f, true)) // capped at 5 (round 7)
        assertEquals(4, NuvioCSynopsisFit.linesFor(210, 50f, true))
        assertEquals(5, NuvioCSynopsisFit.linesFor(250, 50f, true)) // exactly 5 lines
        assertEquals(1, NuvioCSynopsisFit.linesFor(10, 50f, true))
        assertEquals(5, NuvioCSynopsisFit.linesFor(0, 50f, false))
    }

    @Test
    fun fewestLinesWins() {
        // 5 lines at official width, 4 at ours, 3 at the widest: the widest keeps the logo tall.
        assertEquals(2, NuvioCSynopsisFit.pickIndex(listOf(5, 4, 3)))
    }

    @Test
    fun equalLineCountsKeepTheNarrowest() {
        assertEquals(1, NuvioCSynopsisFit.pickIndex(listOf(null, 3, 3)))
    }

    @Test
    fun shortTextStaysNarrow() {
        // Fits in 2 lines at official width: no stretching to 1 long line.
        assertEquals(0, NuvioCSynopsisFit.pickIndex(listOf(2, 2, 1)))
        assertEquals(0, NuvioCSynopsisFit.pickIndex(listOf(1, 1, 1)))
    }

    @Test
    fun nothingFitsUsesWidest() {
        assertEquals(2, NuvioCSynopsisFit.pickIndex(listOf(null, null, null)))
    }

    @Test
    fun narrowFitAtFewerLinesIsNotTakenWhenTextOverflows() {
        // Only 5 lines of room: the narrow widths overflow at 5, the widest fits.
        val r = NuvioCSynopsisFit.choose(widths, lines = 5) { w, lines -> lines == 5 && w >= 550.dp }
        assertEquals(NuvioCSynopsisFitResult(550.dp, 5), r)
    }
}
