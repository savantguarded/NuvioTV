package com.nuvio.tv

import com.nuvio.tv.core.nuvioc.NuvioCFeaturedTagRules
import com.nuvio.tv.core.nuvioc.NuvioCTagEpisode
import com.nuvio.tv.core.nuvioc.NuvioCTagFacts
import com.nuvio.tv.core.sync.shouldExcludePreferenceFromProfileSettingsSync
import com.nuvio.tv.ui.screens.detail.nuvioCHrMin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Round 66b: varied context lines, Calendar sync keys, hr / min. */
class NuvioCRound66bTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val zone = ZoneId.of("Africa/Lagos")

    @Test fun seriesFallbacksAreVariedAndTrue() {
        assertEquals("SEASON 3 OUT THIS WEEK", NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true, latestSeason = 3, latestSeasonAirDate = today.minusDays(2)), today))
        assertEquals("SEASON 3 OUT NOW", NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true, latestSeason = 3, latestSeasonAirDate = today.minusDays(30)), today))
        assertEquals("NEW EPISODE OUT · S2 E5", NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true, last = NuvioCTagEpisode(2, 5, today.minusDays(3))), today))
        assertEquals("PREMIERED 10 YEARS AGO THIS WEEK", NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true, releaseDate = today.minusYears(10).plusDays(1)), today))
        assertEquals("COMPLETE SERIES · 5 SEASONS", NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true, ended = true, seasons = 5), today))
        assertEquals("MORE SEASONS COMING", NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true, returning = true, seasons = 2), today))
        assertEquals("2 SEASONS · 18 EPISODES", NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true, seasons = 2, seasonEpisodes = mapOf(0 to 3, 1 to 8, 2 to 10)), today))
        assertNull(NuvioCFeaturedTagRules.seriesFallback66b(NuvioCTagFacts(isSeries = true), today))
    }

    @Test fun eveningEpisodeReadsTonight() {
        val nine = today.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        val tag = NuvioCFeaturedTagRules.tag(NuvioCTagFacts(isSeries = true, next = NuvioCTagEpisode(1, 4, today, nine)), today, zone, Locale.US)
        assertTrue(tag!!.startsWith("NEW EPISODE TONIGHT"))
    }

    @Test fun filmsGetNewReleaseAndCollection() {
        assertEquals("NEW RELEASE", NuvioCFeaturedTagRules.filmTagV2(NuvioCTagFacts(isSeries = false, releaseDate = today.minusDays(60)), today, Locale.US))
        assertEquals("PART OF THE MATRIX COLLECTION", NuvioCFeaturedTagRules.filmTagV2(NuvioCTagFacts(isSeries = false, collectionSeen = Triple(0, 4, "The Matrix Collection")), today, Locale.US))
    }

    @Test fun calendarSyncKeepsTheZoneOnTheDevice() {
        assertTrue(shouldExcludePreferenceFromProfileSettingsSync("calendar_settings", "calendar_zone_override"))
        assertFalse(shouldExcludePreferenceFromProfileSettingsSync("calendar_settings", "calendar_bell_on"))
        assertFalse(shouldExcludePreferenceFromProfileSettingsSync("calendar_settings", "calendar_hidden_titles"))
    }

    @Test fun runtimeReadsHrAndMin() {
        assertEquals("1 hr 49 min", nuvioCHrMin("1h 49m"))
        assertEquals("50 min", nuvioCHrMin("50m"))
    }
}
