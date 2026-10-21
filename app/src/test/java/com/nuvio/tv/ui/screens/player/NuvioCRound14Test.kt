package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.NuvioCPlayback
import com.nuvio.tv.data.local.SubtitleStyleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCRound14Test {

    @Test
    fun hdrSubtitleStyle_dimsTextBackgroundAndOutline_keepsAlpha() {
        val style = SubtitleStyleSettings(
            textColor = 0xFFFFFFFF.toInt(),
            backgroundColor = 0x80FFFFFF.toInt(),
            outlineColor = 0xFF000000.toInt()
        )
        val dimmed = nuvioCHdrSubtitleStyle(style, hdr = true)
        assertEquals(0xFF999999.toInt(), dimmed.textColor) // 255 * 0.6 = 153 = 0x99
        assertEquals(0x80999999.toInt(), dimmed.backgroundColor) // alpha 0x80 kept
        assertEquals(0xFF000000.toInt(), dimmed.outlineColor) // black stays black
        assertEquals(style.size, dimmed.size)
    }

    @Test
    fun hdrSubtitleStyle_sdrKeepsSavedStyle() {
        val style = SubtitleStyleSettings()
        assertSame(style, nuvioCHdrSubtitleStyle(style, hdr = false))
    }

    @Test
    fun playbackGate_countsNestedPlayers() {
        assertFalse(NuvioCPlayback.active)
        NuvioCPlayback.onPlayerOpened()
        NuvioCPlayback.onPlayerOpened()
        NuvioCPlayback.onPlayerClosed()
        assertTrue(NuvioCPlayback.active)
        NuvioCPlayback.onPlayerClosed()
        assertFalse(NuvioCPlayback.active)
        NuvioCPlayback.onPlayerClosed() // never below zero
        NuvioCPlayback.onPlayerOpened()
        assertTrue(NuvioCPlayback.active)
        NuvioCPlayback.onPlayerClosed()
        assertFalse(NuvioCPlayback.active)
    }
}
