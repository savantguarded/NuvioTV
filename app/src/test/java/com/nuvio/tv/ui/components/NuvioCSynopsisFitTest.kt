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
        assertEquals(NuvioCSynopsisFitResult(widths[0], 5), r)
    }

    @Test
    fun growsOnlyAsMuchAsNeeded() {
        val r = NuvioCSynopsisFit.choose(widths) { w, lines -> w >= 470.dp && lines == 5 }
        assertEquals(NuvioCSynopsisFitResult(470.dp, 5), r)
    }

    @Test
    fun sixthLineOnlyAtWidest() {
        val r = NuvioCSynopsisFit.choose(widths) { _, lines -> lines >= 6 }
        assertEquals(NuvioCSynopsisFitResult(550.dp, 6), r)
    }

    @Test
    fun neverPastAvailable() {
        assertEquals(listOf(400.dp * 0.42f, 400.dp), NuvioCSynopsisFit.widths(400.dp, 470.dp))
    }
}
