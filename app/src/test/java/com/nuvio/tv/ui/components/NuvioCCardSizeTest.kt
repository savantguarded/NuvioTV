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
            assertEquals(w.toFloat(), NuvioCCardSize.modernLandscapeBase(w), 0.001f)
            assertEquals(w, NuvioCCardSize.otherWidth(w))
            assertEquals(screen.value * 0.52f, NuvioCCardSize.rowsViewport(screen, false, w, w * 3 / 2).value, 0.01f)
            assertEquals(screen.value * 0.49f, NuvioCCardSize.rowsViewport(screen, true, w, w * 3 / 2).value, 0.01f)
            assertEquals(14f, NuvioCCardSize.rowTitleBottom(w).value, 0f)
        }
    }

    @Test
    fun bigPresetsPerLayout() {
        assertEquals(148, NuvioCCardSize.classicWidth(156))
        assertEquals(156, NuvioCCardSize.classicWidth(172))
        assertEquals(276f, NuvioCCardSize.modernLandscapeBase(156) * 1.24f * 1.34f, 0.01f)
        assertEquals(320f, NuvioCCardSize.modernLandscapeBase(172) * 1.24f * 1.34f, 0.01f)
        assertEquals(140, NuvioCCardSize.otherWidth(172))
        assertEquals(210, NuvioCCardSize.otherHeight(258))
    }

    @Test
    fun rowsAreaFitsFocusedRowAndPeek() {
        // Portrait Huge: 34 focus line + 234 card + 14 gap + 24 title + 2 = 308
        assertEquals(308f, NuvioCCardSize.rowsViewport(screen, false, 172, 258).value, 0.6f)
        // Landscape Huge: 34 + 181 + 14 + 24 + 8 + 20 = 281
        assertEquals(281f, NuvioCCardSize.rowsViewport(screen, true, 172, 258).value, 0.6f)
        // Landscape Extra large fits today's 49% area
        assertEquals(screen.value * 0.49f, NuvioCCardSize.rowsViewport(screen, true, 156, 234).value, 0.01f)
    }
}
