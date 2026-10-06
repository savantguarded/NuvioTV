package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [fork] Nuvio C: placeholder check, loading lane text, HDR dim colour and OSD timeout rules.
class NuvioCPlayerExtrasTest {

    private val min = 60_000L
    private val gb = 1024L * 1024L * 1024L

    @Test
    fun placeholderNeedsAShortClipAndALongTitle() {
        val p = NuvioCPlaceholderRules
        assertTrue(p.isPlaceholder(40_000L, 120 * min, null))
        assertTrue(p.isPlaceholder(150_000L, 45 * min, null))
        assertTrue(p.isPlaceholder(40_000L, null, 9 * gb))
        assertFalse(p.isPlaceholder(4 * min, 120 * min, 9 * gb)) // 3 min or more is never judged
        assertFalse(p.isPlaceholder(40_000L, null, null)) // nothing says it should be long
        assertFalse(p.isPlaceholder(40_000L, 10 * min, null)) // short title
        assertFalse(p.isPlaceholder(40_000L, null, 200L * 1024 * 1024)) // small file
        assertFalse(p.isPlaceholder(0L, 120 * min, 9 * gb))
    }

    @Test
    fun runtimeParsing() {
        val p = NuvioCPlaceholderRules
        assertEquals(130, p.parseRuntimeMinutes("2h 10min"))
        assertEquals(130, p.parseRuntimeMinutes("130 min"))
        assertEquals(60, p.parseRuntimeMinutes("1h"))
        assertEquals(45, p.parseRuntimeMinutes("45m"))
        assertEquals(98, p.parseRuntimeMinutes("98"))
        assertNull(p.parseRuntimeMinutes(""))
        assertNull(p.parseRuntimeMinutes(null))
    }

    @Test
    fun loadingLaneText() {
        val t = NuvioCLoadingLaneText
        assertEquals("Torrentio · TorBox", t.sourceLine("Torrentio", "[TB+] Torrentio\n4k"))
        assertEquals("Comet · Real-Debrid", t.sourceLine("Comet", "[RD⚡] Comet 4K"))
        assertEquals("Torrentio", t.sourceLine("Torrentio", "Torrentio 1080p"))
        assertNull(t.sourceLine(null, null))
        assertEquals("Movie.2019.mkv", t.filename("Movie.2019.mkv", "whatever"))
        assertEquals("Movie.2019.2160p.WEB-DL.DV.mkv", t.filename(null, "Movie.2019.2160p.WEB-DL.DV.mkv\n💾 12 GB"))
        assertEquals("Movie.2019.2160p.WEB-DL", t.filename(null, "Movie.2019.2160p.WEB-DL\n👤 4"))
        assertNull(t.filename(null, "Great quality, 4K\n💾 12 GB"))
    }

    @Test
    fun hdrDimScalesColourKeepsAlpha() {
        assertEquals(0xFF999999.toInt(), NuvioCHdrDim.dimArgb(0xFFFFFFFF.toInt(), 0.6f))
        assertEquals(0x80999999.toInt(), NuvioCHdrDim.dimArgb(0x80FFFFFF.toInt(), 0.6f))
        assertEquals(0xFFFFFFFF.toInt(), NuvioCHdrDim.dimArgb(0xFFFFFFFF.toInt(), 1f))
        assertTrue(NuvioCHdrDim.isHdrTag("DV"))
        assertTrue(NuvioCHdrDim.isHdrTag("HLG"))
        assertFalse(NuvioCHdrDim.isHdrTag("SDR"))
    }

    @Test
    fun osdTimeoutIsShortOnlyRightAfterASeek() {
        assertEquals(8_000L, NuvioCOsdTimeout.hideDelayMs())
        NuvioCOsdTimeout.markSeek()
        assertEquals(3_000L, NuvioCOsdTimeout.hideDelayMs())
        assertEquals(8_000L, NuvioCOsdTimeout.hideDelayMs())
    }
}
