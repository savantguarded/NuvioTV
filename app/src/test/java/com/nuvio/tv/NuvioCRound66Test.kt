package com.nuvio.tv

import com.nuvio.tv.data.nuvioc.nuvioCHour12
import com.nuvio.tv.data.nuvioc.nuvioCHourTick
import com.nuvio.tv.data.quality.NuvioCEditionBlocklist
import com.nuvio.tv.data.quality.QualityEdition
import com.nuvio.tv.data.quality.QualityStreamText
import com.nuvio.tv.data.quality.TitleQualityParser
import com.nuvio.tv.data.quality.TitleQualityPlanner
import com.nuvio.tv.ui.components.nuvioCReelLandingNeedsFullSpin
import com.nuvio.tv.ui.screens.cast.nuvioCFilmoSortKey
import com.nuvio.tv.ui.screens.detail.NUVIO_C_GRAPH_SEASONS
import com.nuvio.tv.ui.screens.detail.nuvioCGraphGridRight
import com.nuvio.tv.ui.screens.detail.nuvioCGraphPanel
import com.nuvio.tv.ui.screens.detail.nuvioCGraphTicks
import com.nuvio.tv.ui.screens.detail.nuvioCHeroPast
import com.nuvio.tv.ui.screens.home.NuvioCFeaturedKey
import com.nuvio.tv.ui.screens.home.nuvioCFeaturedKey
import com.nuvio.tv.ui.screens.player.NuvioCAudioSyncNow
import com.nuvio.tv.ui.screens.player.nuvioCAudioSyncNowCheck
import com.nuvio.tv.ui.screens.taste.nuvioCWrappedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Round 66: graph layout, reel landing, quality refresh, editions share / groups / blocklist, Taste clock and Wrapped, filmography, hero check, carousel keys, audio sync button. */
class NuvioCRound66Test {
    private fun fixture(name: String): List<QualityStreamText> =
        TitleQualityParser.streamsOf(javaClass.classLoader!!.getResource("quality/$name.json")!!.readText())

    // ---- 2.1 / 2.3 series graph ----
    @Test fun graphPanelNeverOverlapsTheGrid() {
        val (x, w) = nuvioCGraphPanel()
        for (seasons in 1..NUVIO_C_GRAPH_SEASONS) assertTrue(x >= nuvioCGraphGridRight(seasons) + 24)
        assertTrue(w >= 300)
        assertEquals(960 - 40, x + w)
    }

    @Test fun graphTicksStayFewAndEndOnTheLastSeason() {
        assertEquals(emptyList<Int>(), nuvioCGraphTicks(0))
        val t38 = nuvioCGraphTicks(38)
        assertTrue(t38.size <= 10)
        assertEquals(37, t38.last())
        assertEquals(0, t38.first())
        assertEquals(t38.sorted().distinct(), t38)
    }

    // ---- 3.1 reel ----
    @Test fun landingWithoutAPendingPhaseSpinsInFull() {
        assertTrue(nuvioCReelLandingNeedsFullSpin(continues = true, cycleEmpty = true, previousPickIndex = -1))
        assertTrue(nuvioCReelLandingNeedsFullSpin(continues = true, cycleEmpty = false, previousPickIndex = 7))
        assertFalse(nuvioCReelLandingNeedsFullSpin(continues = true, cycleEmpty = false, previousPickIndex = -1))
        assertFalse(nuvioCReelLandingNeedsFullSpin(continues = false, cycleEmpty = true, previousPickIndex = 3))
    }

    // ---- 7.1 quality refresh ----
    @Test fun newReleasesAreCheckedAgainSooner() {
        val today = LocalDate.of(2026, 11, 26)
        val twelveHours = 12 * 60 * 60_000L
        assertEquals(twelveHours, TitleQualityPlanner.refreshTtlMs(today.minusDays(30), today, has4k = true))
        assertEquals(twelveHours, TitleQualityPlanner.refreshTtlMs(today.minusDays(200), today, has4k = false))
        assertEquals(7 * TitleQualityPlanner.DAY_MS, TitleQualityPlanner.refreshTtlMs(today.minusDays(200), today, has4k = true))
        assertEquals(7 * TitleQualityPlanner.DAY_MS, TitleQualityPlanner.refreshTtlMs(today.minusDays(800), today, has4k = false))
        assertEquals(7 * TitleQualityPlanner.DAY_MS, TitleQualityPlanner.refreshTtlMs(null, today, has4k = false))
    }

    // ---- 7.2 editions ----
    @Test fun avengersShowsNoFakeExtended() {
        val eds = TitleQualityParser.editions(fixture("avengers_2012"), "The Avengers", 2012)
        assertFalse(QualityEdition.EXTENDED in eds)
        assertFalse(QualityEdition.DIRECTORS_CUT in eds)
        assertEquals(listOf(QualityEdition.IMAX), eds) // 85 of 537 seeders (16%), EVO + FLUX
    }

    @Test fun shareAndGroupRules() {
        assertTrue(TitleQualityParser.nuvioCEditionHoldsShare(30, 200, 2))
        assertFalse(TitleQualityParser.nuvioCEditionHoldsShare(24, 100, 2)) // under 25 seeders
        assertFalse(TitleQualityParser.nuvioCEditionHoldsShare(29, 200, 2)) // under 15%
        assertFalse(TitleQualityParser.nuvioCEditionHoldsShare(100, 200, 1)) // one release group
        assertEquals("sparks", TitleQualityParser.releaseGroup(QualityStreamText("Torrentio", "Film.2010.1080p.BluRay.x264-SPARKS\n👤 5", null)))
        assertEquals(null, TitleQualityParser.releaseGroup(QualityStreamText("Torrentio", "Film 2010 1080p WEB-DL\n👤 5", null)))
    }

    @Test fun blocklistMatchesTitlesLooselyAndLeavesOthers() {
        assertEquals(setOf(QualityEdition.EXTENDED, QualityEdition.DIRECTORS_CUT), NuvioCEditionBlocklist.blocked("Avengers: Endgame"))
        assertEquals(setOf(QualityEdition.EXTENDED, QualityEdition.DIRECTORS_CUT), NuvioCEditionBlocklist.blocked("Marvel's The Avengers"))
        assertEquals(setOf(QualityEdition.EXTENDED, QualityEdition.DIRECTORS_CUT), NuvioCEditionBlocklist.blocked("Deadpool & Wolverine"))
        assertTrue(NuvioCEditionBlocklist.blocked("Spider-Man: No Way Home").isEmpty())
        assertTrue(NuvioCEditionBlocklist.blocked("Aliens").isEmpty())
    }

    // ---- 10.8 / 10.9 Taste ----
    @Test fun twelveHourClock() {
        assertEquals("12 AM", nuvioCHour12(0))
        assertEquals("9 AM", nuvioCHour12(9))
        assertEquals("12 PM", nuvioCHour12(12))
        assertEquals("9 PM", nuvioCHour12(21))
        assertEquals(listOf("12a", "3a", "6a", "9a", "12p", "3p", "6p", "9p"), (0 until 24).map(::nuvioCHourTick).filter { it.isNotEmpty() })
        assertTrue((0 until 24).map(::nuvioCHourTick).all { it.length <= 3 })
    }

    @Test fun wrappedIsAlwaysAYear() {
        assertEquals("y:2024", nuvioCWrappedKey(2024, 2025, null, 2026))
        assertEquals("y:2025", nuvioCWrappedKey(null, 2025, 2023, 2026))
        assertEquals("y:2023", nuvioCWrappedKey(null, null, 2023, 2026))
        assertEquals("y:2026", nuvioCWrappedKey(null, null, null, 2026))
    }

    // ---- 11.1 filmography ----
    @Test fun undatedTitlesComeFirst() {
        val sorted = listOf("2019-05-01", "", "2027-01-10", "2001").sortedByDescending(::nuvioCFilmoSortKey)
        assertEquals(listOf("", "2027-01-10", "2019-05-01", "2001"), sorted)
    }

    // ---- 10.5 hero on screen ----
    @Test fun heroPastWhenMostlyOffScreen() {
        assertFalse(nuvioCHeroPast(offset = 0, size = 800, viewportStart = 0))
        assertFalse(nuvioCHeroPast(offset = -300, size = 800, viewportStart = 0))
        assertTrue(nuvioCHeroPast(offset = -500, size = 800, viewportStart = 0))
    }

    // ---- 11.3 carousel keys ----
    @Test fun carouselLeftRight() {
        assertEquals(NuvioCFeaturedKey.NEXT, nuvioCFeaturedKey(right = true, focusedButton = 3, lastButton = 3, leftArmed = false, count = 5))
        assertEquals(NuvioCFeaturedKey.PASS, nuvioCFeaturedKey(right = true, focusedButton = 1, lastButton = 3, leftArmed = false, count = 5))
        assertEquals(NuvioCFeaturedKey.PREVIOUS, nuvioCFeaturedKey(right = false, focusedButton = 0, lastButton = 3, leftArmed = false, count = 5))
        assertEquals(NuvioCFeaturedKey.PASS, nuvioCFeaturedKey(right = false, focusedButton = 0, lastButton = 3, leftArmed = true, count = 5))
        assertEquals(NuvioCFeaturedKey.PASS, nuvioCFeaturedKey(right = true, focusedButton = 3, lastButton = 3, leftArmed = false, count = 1))
    }

    // ---- 11.4 audio sync button ----
    @Test fun syncToAudioNowChecks() {
        assertEquals(NuvioCAudioSyncNow.NO_ADDON_SUBTITLE, nuvioCAudioSyncNowCheck(false, true, false, false))
        assertEquals(NuvioCAudioSyncNow.SETTING_OFF, nuvioCAudioSyncNowCheck(true, false, false, false))
        assertEquals(NuvioCAudioSyncNow.MPV_SAMPLING_OFF, nuvioCAudioSyncNowCheck(true, true, true, true))
        assertEquals(NuvioCAudioSyncNow.STARTED, nuvioCAudioSyncNowCheck(true, true, false, true))
    }
}
