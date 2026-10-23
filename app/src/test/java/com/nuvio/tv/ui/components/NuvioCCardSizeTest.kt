package com.nuvio.tv.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCCardSizeTest {
    private val screen = 540.dp

    @Test
    fun officialPresetsPassThrough() {
        for (w in listOf(104, 112, 120, 126, 134, 140)) {
            assertEquals(w, NuvioCCardSize.classicWidth(w))
            assertEquals(w, NuvioCCardSize.normalizeWidth(w))
            assertEquals(w * 3 / 2, NuvioCCardSize.normalizeHeight(w * 3 / 2))
            assertEquals(w.toFloat(), NuvioCCardSize.modernLandscapeBase(w), 0.001f)
            assertEquals(w, NuvioCCardSize.otherWidth(w))
            assertEquals(screen.value * 0.52f, NuvioCCardSize.rowsViewport(screen, false, w, w * 3 / 2).value, 0.01f)
            assertEquals(screen.value * 0.49f, NuvioCCardSize.rowsViewport(screen, true, w, w * 3 / 2).value, 0.01f)
            assertEquals(14f, NuvioCCardSize.rowTitleBottom(w).value, 0f)
            assertEquals(40f, NuvioCCardSize.focusInset(w, 40.dp).value, 0f)
            assertEquals(24f, NuvioCCardSize.rowGap(w, 24.dp).value, 0f)
        }
    }

    @Test
    fun extraLargePerLayout() {
        assertEquals(148, NuvioCCardSize.classicWidth(156))
        assertEquals(276f, NuvioCCardSize.modernLandscapeBase(156) * 1.24f * 1.34f, 0.01f)
        assertEquals(140, NuvioCCardSize.otherWidth(156))
        assertEquals(210, NuvioCCardSize.otherHeight(234))
        assertEquals(32f, NuvioCCardSize.focusInset(156, 40.dp).value, 0f)
        assertEquals(12f, NuvioCCardSize.rowGap(156, 24.dp).value, 0f)
        assertEquals(8f, NuvioCCardSize.rowTitleBottom(156).value, 0f)
    }

    @Test
    fun oldHugeReadsAsExtraLarge() {
        assertEquals(156, NuvioCCardSize.normalizeWidth(172))
        assertEquals(234, NuvioCCardSize.normalizeHeight(258))
        assertEquals(148, NuvioCCardSize.classicWidth(172))
        assertEquals(276f, NuvioCCardSize.modernLandscapeBase(172) * 1.24f * 1.34f, 0.01f)
    }

    @Test
    fun extraLargeKeepsLargeRowsArea() {
        // Hero (logo, synopsis lines and width) matches Large because the rows area does.
        // Portrait: 32 focus line + 212 card + 12 gap + 24 next title = 280 <= 280.8
        assertEquals(screen.value * 0.52f, NuvioCCardSize.rowsViewport(screen, false, 156, 234).value, 0.01f)
        // Landscape: 32 + 156 + 12 + 24 + 8 + 20 = 252 <= 264.6
        assertEquals(screen.value * 0.49f, NuvioCCardSize.rowsViewport(screen, true, 156, 234).value, 0.01f)
        // A profile still on the old Huge gets the same
        assertEquals(screen.value * 0.52f, NuvioCCardSize.rowsViewport(screen, false, 172, 258).value, 0.01f)
    }

    @Test
    fun shorterScreenGrowsInsteadOfClipping() {
        // Never clips the next row's title: on a short screen the rows area grows to fit it.
        assertEquals(280f, NuvioCCardSize.rowsViewport(500.dp, false, 156, 234).value, 0.6f)
    }

    @Test
    fun heroTextWidthMatchesLargeAtExtraLarge() {
        val large = NuvioCCardSize.heroTextWidth((140 * 1.24f * 1.34f).dp, 12.dp)
        val xl = NuvioCCardSize.heroTextWidth((NuvioCCardSize.modernLandscapeBase(156) * 1.24f * 1.34f).dp, 12.dp)
        assertEquals(large.value, xl.value, 0.01f)
        // Smaller presets keep their own (narrower) width
        val small = NuvioCCardSize.heroTextWidth((120 * 1.24f * 1.34f).dp, 12.dp)
        assertEquals(120 * 1.24f * 1.34f * 2 + 12f, small.value, 0.01f)
    }
}
