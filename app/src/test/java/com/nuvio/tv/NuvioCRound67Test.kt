package com.nuvio.tv

import com.nuvio.tv.core.nuvioc.health.HealthVerdict
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules
import com.nuvio.tv.core.nuvioc.health.SourceState
import com.nuvio.tv.core.nuvioc.health.TvCheck
import com.nuvio.tv.core.tmdb.TmdbEnrichment
import com.nuvio.tv.data.quality.NuvioCDigitalGate
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.screens.collection.NuvioCFolderMetaOrder
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.NextUpInfo
import com.nuvio.tv.ui.screens.home.NuvioCFeaturedRules
import com.nuvio.tv.ui.screens.home.NuvioCFeaturedRules.PlayKind67
import com.nuvio.tv.ui.screens.home.nuvioCPlayTextShort67
import com.nuvio.tv.ui.screens.profile.NUVIO_C_PROFILE_BOX_START
import com.nuvio.tv.ui.screens.profile.nuvioCProfileBottomStops67
import com.nuvio.tv.ui.screens.profile.nuvioCProfileLeftAlpha67
import com.nuvio.tv.ui.screens.profile.nuvioCProfileShadeStops67
import com.nuvio.tv.ui.screens.profile.nuvioCProfileTopStops67
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Round 67: profile shade, Play pill text, digital gate, collection meta order, health verdicts. */
class NuvioCRound67Test {
    private val today = LocalDate.of(2026, 11, 27)

    // ---- 1. profile shade ----------------------------------------------------------------------------------------
    @Test fun leftRampFollowsTheBoardStops() {
        assertEquals(1f, nuvioCProfileLeftAlpha67(0f), 1e-4f)
        assertEquals(0.97f, nuvioCProfileLeftAlpha67(0.14f), 1e-4f)
        assertEquals(0.70f, nuvioCProfileLeftAlpha67(0.34f), 1e-4f)
        assertEquals(0.16f, nuvioCProfileLeftAlpha67(0.54f), 1e-4f)
        assertEquals(0f, nuvioCProfileLeftAlpha67(0.62f), 1e-4f)
        assertEquals(0f, nuvioCProfileLeftAlpha67(0.9f), 1e-4f)
        assertEquals((0.90f + 0.70f) / 2, nuvioCProfileLeftAlpha67(0.29f), 1e-3f)
    }

    @Test fun boxStopsMapScreenPositionsIntoTheBox() {
        val stops = nuvioCProfileShadeStops67(NUVIO_C_PROFILE_BOX_START)
        // starts at full page colour (the box edge never shows) and is clear by the 62% screen mark
        assertEquals(0f to 1f, stops.first())
        assertTrue(stops.zipWithNext().all { (a, b) -> b.first > a.first })
        val clearAt = stops.first { it.second == 0f }.first
        assertEquals((0.62f - NUVIO_C_PROFILE_BOX_START) / (1f - NUVIO_C_PROFILE_BOX_START), clearAt, 1e-4f)
        assertEquals(1f, stops.last().first, 0f)
        // without a box the stops are the screen stops
        val plain = nuvioCProfileShadeStops67(0f)
        assertEquals(listOf(0f to 1f, 0.14f to 0.97f, 0.24f to 0.90f, 0.34f to 0.70f, 0.44f to 0.42f, 0.54f to 0.16f, 0.62f to 0f, 1f to 0f), plain)
    }

    @Test fun topAndBottomFades() {
        assertEquals(listOf(0f to 0.40f, 0.18f to 0f, 1f to 0f), nuvioCProfileTopStops67())
        assertEquals(listOf(0f to 0f, 0.55f to 0f, 0.80f to 0.50f, 1f to 0.85f), nuvioCProfileBottomStops67())
    }

    // ---- 3. Play pill text ---------------------------------------------------------------------------------------
    private fun progress(season: Int?, episode: Int?, pos: Long, dur: Long) = ContinueWatchingItem.InProgress(
        WatchProgress("tt1", if (season == null) "movie" else "series", "X", null, null, null, "v", season, episode, null, pos, dur, 0L)
    )

    private fun nextUp(season: Int, episode: Int, aired: Boolean = true, alert: Boolean = false, newSeason: Boolean = false) = ContinueWatchingItem.NextUp(
        NextUpInfo("tt2", "series", "Y", null, null, null, "v", season, episode, null, thumbnail = null, hasAired = aired,
            lastWatched = 0L, sortTimestamp = 0L, isReleaseAlert = alert, isNewSeasonRelease = newSeason)
    )

    @Test fun playTextCases() {
        val r = NuvioCFeaturedRules
        assertEquals(PlayKind67.PLAY_FIRST_EPISODE, r.playText67(null, isShow = true, filmWatched = false).kind)
        assertEquals(PlayKind67.PLAY, r.playText67(null, isShow = false, filmWatched = false).kind)
        assertEquals(PlayKind67.WATCH_AGAIN, r.playText67(null, isShow = false, filmWatched = true).kind)
        val mid = r.playText67(progress(2, 4, 24 * 60_000L, 42 * 60_000L), isShow = true, filmWatched = false)
        assertEquals(PlayKind67.RESUME_EPISODE, mid.kind); assertEquals(2, mid.season); assertEquals(4, mid.episode); assertEquals(18, mid.minutesLeft)
        val film = r.playText67(progress(null, null, 60 * 60_000L, 102 * 60_000L), isShow = false, filmWatched = false)
        assertEquals(PlayKind67.RESUME_FILM, film.kind); assertEquals(42, film.minutesLeft)
        assertEquals(PlayKind67.PLAY_NEXT, r.playText67(nextUp(2, 5), true, false).kind)
        assertEquals(PlayKind67.START_SEASON, r.playText67(nextUp(3, 1), true, false).kind)
        assertEquals(PlayKind67.START_SEASON, r.playText67(nextUp(3, 1, newSeason = true), true, false).kind)
        assertEquals(PlayKind67.PLAY_NEW_EPISODE, r.playText67(nextUp(4, 6, alert = true), true, false).kind)
        // a next episode that isn't out yet keeps a plain Play
        assertEquals(PlayKind67.PLAY, r.playText67(nextUp(4, 7, aired = false), true, false).kind)
    }

    @Test fun minutesLeftRoundsUpAndHidesUnknown() {
        assertEquals(1, NuvioCFeaturedRules.minutesLeft67(59 * 60_000L + 1, 60 * 60_000L))
        assertNull(NuvioCFeaturedRules.minutesLeft67(10, 0))
        assertNull(NuvioCFeaturedRules.minutesLeft67(100, 100))
    }

    @Test fun tailDropsFirst() {
        assertEquals("Resume S2 E4", nuvioCPlayTextShort67("Resume S2 E4 · 18 min left"))
        assertEquals("Play next", nuvioCPlayTextShort67("Play next · S2 E5"))
        assertEquals("Watch again", nuvioCPlayTextShort67("Watch again"))
    }

    // ---- 4. digital gate -----------------------------------------------------------------------------------------
    @Test fun gateOnlyForRecentFilmsWithNoHomeDateAnywhere() {
        val g = NuvioCDigitalGate
        assertTrue(g.gated(false, "2026-10-10", null, today))
        assertTrue(g.gated(false, "2026-10-10", "2026-12-20", today)) // digital date still ahead
        assertFalse(g.gated(false, "2026-10-10", "2026-11-20", today)) // on digital somewhere
        assertFalse(g.gated(false, "2026-10-10", "2026-11-27", today)) // today counts
        assertFalse(g.gated(false, "2025-10-10", null, today)) // cinema more than a year ago
        assertFalse(g.gated(false, "2026-12-10", null, today)) // not in cinemas yet (NOT YET RELEASED handles it)
        assertFalse(g.gated(true, "2026-10-10", null, today)) // series unchanged
        assertFalse(g.gated(false, null, null, today))
    }

    @Test fun earliestHomeDateAcrossAllCountries() {
        // a Nollywood film with only a Nigerian digital date
        val rd = Json.parseToJsonElement("""
            {"results":[
              {"iso_3166_1":"US","release_dates":[{"type":3,"release_date":"2026-09-01T00:00:00.000Z"}]},
              {"iso_3166_1":"NG","release_dates":[{"type":3,"release_date":"2026-08-20T00:00:00.000Z"},{"type":4,"release_date":"2026-11-02T00:00:00.000Z"}]},
              {"iso_3166_1":"GB","release_dates":[{"type":5,"release_date":"2026-12-15T00:00:00.000Z"}]}
            ]}""").jsonObject
        assertEquals("2026-11-02", g4(rd))
        assertEquals("2026-08-20", NuvioCDigitalGate.earliestCinemaAnyCountry(rd))
        assertFalse(NuvioCDigitalGate.gated(false, "2026-09-01", g4(rd), today))
        assertNull(NuvioCDigitalGate.earliestAnyCountry(null, 4, 5))
    }

    private fun g4(rd: kotlinx.serialization.json.JsonObject) = NuvioCDigitalGate.earliestAnyCountry(rd, 4, 5)

    @Test fun mayBeGatedSkipsOldFilmsAndSeries() {
        assertTrue(NuvioCDigitalGate.mayBeGated(false, null, today))
        assertTrue(NuvioCDigitalGate.mayBeGated(false, "2026-03-01", today))
        assertFalse(NuvioCDigitalGate.mayBeGated(false, "2024-03-01", today))
        assertTrue(NuvioCDigitalGate.mayBeGated(false, "2025", today))
        assertFalse(NuvioCDigitalGate.mayBeGated(false, "2019", today))
        assertFalse(NuvioCDigitalGate.mayBeGated(true, null, today))
    }

    // ---- 6. collection meta order --------------------------------------------------------------------------------
    private val preview = MetaPreview("tt9", ContentType.MOVIE, name = "Catalog name", poster = null, posterShape = PosterShape.POSTER,
        background = "cat.jpg", logo = null, description = "catalog text", releaseInfo = "2020", imdbRating = null, genres = listOf("Drama"))

    private val addon = Meta("tt9", ContentType.MOVIE, name = "Addon name", poster = null, posterShape = PosterShape.POSTER, background = "addon.jpg",
        logo = "addon-logo.png", description = "addon text", releaseInfo = "2020–", imdbRating = 7.1f, genres = listOf("Thriller"), runtime = "1h 50m",
        director = emptyList(), cast = emptyList(), videos = emptyList(), country = null, awards = null, language = "en", links = emptyList())

    private fun tmdb() = TmdbEnrichment(
        localizedTitle = "TMDB title", description = "tmdb text", genres = listOf("Crime"), backdrop = "tmdb.jpg", logo = "tmdb-logo.png", poster = null,
        directorMembers = emptyList(), writerMembers = emptyList(), castMembers = emptyList(), releaseInfo = "2020", rating = 7.0, runtimeMinutes = 110,
        director = emptyList(), writer = emptyList(), productionCompanies = emptyList(), networks = emptyList(), ageRating = "R", status = "Released",
        countries = null, language = "en", collectionId = null, collectionName = null
    )

    @Test fun addonMetaFirstThenOnlyAllowedTmdbFields() {
        // all TMDB switches off: the add-on's info stays, no switch later
        val off = NuvioCFolderMetaOrder.merge(preview, addon, tmdb(), useBasicInfo = false, useArtwork = false, useDetails = false, isModern = true)
        assertEquals("addon text", off.description); assertEquals("addon.jpg", off.background); assertEquals("addon-logo.png", off.logo)
        assertEquals(listOf("Thriller"), off.genres); assertEquals("1h 50m", off.runtime)
        // artwork only: TMDB's pictures on top, add-on text kept
        val art = NuvioCFolderMetaOrder.merge(preview, addon, tmdb(), useBasicInfo = false, useArtwork = true, useDetails = false, isModern = true)
        assertEquals("tmdb.jpg", art.background); assertEquals("addon text", art.description)
        // basic info on: TMDB text wins, as on Home
        val basic = NuvioCFolderMetaOrder.merge(preview, addon, tmdb(), useBasicInfo = true, useArtwork = false, useDetails = true, isModern = true)
        assertEquals("tmdb text", basic.description); assertEquals("TMDB title", basic.name); assertEquals("110", basic.runtime)
        // no add-on answer: TMDB alone as before
        assertEquals("catalog text", NuvioCFolderMetaOrder.merge(preview, null, tmdb(), false, true, false, true).description)
    }

    // ---- 8. health verdict table ---------------------------------------------------------------------------------
    @Test fun verdictTable() {
        val v = NuvioCHealthRules::verdict
        assertEquals(HealthVerdict.WORKING, v(TvCheck.OK, SourceState.UP))
        assertEquals(HealthVerdict.WORKING, v(TvCheck.OK, null))
        assertEquals(HealthVerdict.WORKING_SOURCE_DISAGREES, v(TvCheck.OK, SourceState.DOWN))
        assertEquals(HealthVerdict.CANT_REACH, v(TvCheck.NO_ANSWER, SourceState.UP))
        assertEquals(HealthVerdict.CANT_REACH, v(TvCheck.NO_ANSWER, null))
        assertEquals(HealthVerdict.RATE_LIMITED, v(TvCheck.RATE_LIMITED, SourceState.DOWN))
        assertEquals(HealthVerdict.DOWN, v(TvCheck.NO_ANSWER, SourceState.DOWN))
        assertEquals(HealthVerdict.CHECKING, v(TvCheck.RUNNING, null))
        assertEquals(HealthVerdict.OFF, v(TvCheck.OFF, SourceState.DOWN))
    }

    @Test fun tvCheckFromAnswers() {
        assertEquals(TvCheck.OK, NuvioCHealthRules.tvCheck(200, true))
        assertEquals(TvCheck.NO_ANSWER, NuvioCHealthRules.tvCheck(200, false))
        assertEquals(TvCheck.RATE_LIMITED, NuvioCHealthRules.tvCheck(429, false))
        assertEquals(TvCheck.BLOCKED, NuvioCHealthRules.tvCheck(403, false))
        assertEquals(TvCheck.BLOCKED, NuvioCHealthRules.tvCheck(401, false))
        assertEquals(TvCheck.NO_ANSWER, NuvioCHealthRules.tvCheck(null, false))
        assertEquals(TvCheck.NO_ANSWER, NuvioCHealthRules.tvCheck(502, false))
    }
}
