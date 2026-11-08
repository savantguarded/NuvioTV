package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.ui.screens.player.NuvioCNight
import com.nuvio.tv.ui.screens.player.NuvioCTrackNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCRound34Test {
    @Test fun releaseTextNamesFallBackToMeta() {
        assertEquals("Sweetpea", NuvioCNameGuard.showName("Sweetpea S01E03", "Sweetpea"))
        assertEquals("Sweetpea", NuvioCNameGuard.showName("Sweetpea.2024.1080p.WEB-DL", "Sweetpea"))
        assertEquals("Sweetpea S01E03", NuvioCNameGuard.showName("Sweetpea S01E03", null)) // nothing better to show
        assertEquals("The Bear", NuvioCNameGuard.showName("The Bear", "The Bear (2022)"))   // a normal saved name wins
        assertEquals("Meta", NuvioCNameGuard.showName("  ", "Meta"))
    }

    @Test fun ordinaryTitlesAreNotReleaseText() {
        assertFalse(NuvioCNameGuard.looksLikeRelease("Se7en"))
        assertFalse(NuvioCNameGuard.looksLikeRelease("2001: A Space Odyssey"))
        assertFalse(NuvioCNameGuard.looksLikeRelease("Blade Runner 2049"))
        assertTrue(NuvioCNameGuard.looksLikeRelease("Show 1x05"))
    }

    @Test fun placeholderEpisodeTitles() {
        assertEquals("Season 2 premiere", NuvioCNameGuard.episodeTitle("Episode 1", 1) { "Season 2 premiere" })
        assertNull(NuvioCNameGuard.episodeTitle("Episode 4", 4) { "x" })
        assertEquals("Pilot", NuvioCNameGuard.episodeTitle("Pilot", 1) { "x" })
        assertNull(NuvioCNameGuard.episodeTitle(null, 1) { "x" })
    }

    @Test fun nightWindowCrossesMidnight() {
        val from = 18 * 60; val until = 8 * 60
        assertTrue(NuvioCNight.inWindow(23 * 60, from, until))
        assertTrue(NuvioCNight.inWindow(2 * 60, from, until))
        assertTrue(NuvioCNight.inWindow(18 * 60, from, until))
        assertFalse(NuvioCNight.inWindow(8 * 60, from, until))
        assertFalse(NuvioCNight.inWindow(12 * 60, from, until))
        assertTrue(NuvioCNight.inWindow(10 * 60, 9 * 60, 17 * 60))
        assertFalse(NuvioCNight.inWindow(10 * 60, 9 * 60, 9 * 60))
    }

    @Test fun trackNamesReadAsLanguagePlusTags() {
        assertEquals("English", NuvioCTrackNames.subtitleTitle("Eng", "en", forced = false))
        assertEquals("English · SDH", NuvioCTrackNames.subtitleTitle("SDH", "eng", forced = false))
        assertEquals("English · Forced", NuvioCTrackNames.subtitleTitle("English [Forced]", "en", forced = true))
        assertNull(NuvioCTrackNames.subtitleTitle("Track 3", "und", forced = false))
        assertEquals("SDH", NuvioCTrackNames.original("SDH", "English · SDH"))
        assertNull(NuvioCTrackNames.original("English", "English"))
    }
}
