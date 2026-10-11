package com.nuvio.tv.ui.screens.detail

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// [fork] Nuvio C: background-trailer autostart rules.
@OptIn(ExperimentalCoroutinesApi::class)
class NuvioCTrailerAutostartTest {

    private class Harness(scope: TestScope, var canStart: Boolean = true) {
        var starts = 0
        val autostart = NuvioCTrailerAutostart(scope, delayMs = { 7_000L }) {
            if (canStart) starts++
            canStart
        }
    }

    @Test
    fun startsAfterDelayWithoutFocus() = runTest {
        val h = Harness(this)
        h.autostart.arm()
        advanceTimeBy(6_900); runCurrent()
        assertEquals(0, h.starts)
        advanceTimeBy(200); runCurrent()
        assertEquals(1, h.starts)
        assertEquals(NuvioCTrailerAutostart.Phase.DONE, h.autostart.phase)
    }

    @Test
    fun overlayStopsAndRestartsFromFullDelay() = runTest {
        val h = Harness(this)
        h.autostart.arm()
        advanceTimeBy(5_000); runCurrent()
        h.autostart.setOverlayOpen(true)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, h.starts)
        h.autostart.setOverlayOpen(false)
        advanceTimeBy(6_900); runCurrent()
        assertEquals(0, h.starts)
        advanceTimeBy(200); runCurrent()
        assertEquals(1, h.starts)
    }

    @Test
    fun castPageResetsAndAppAwayPauses() = runTest {
        val h = Harness(this)
        h.autostart.arm()
        advanceTimeBy(3_000); runCurrent()
        h.autostart.setChildPageOpen(true)
        h.autostart.setAppAway(true)
        advanceTimeBy(30_000); runCurrent()
        h.autostart.setChildPageOpen(false)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, h.starts)
        h.autostart.setAppAway(false)
        advanceTimeBy(6_900); runCurrent()
        assertEquals(0, h.starts) // child page closing reset it to the full 7 s
        advanceTimeBy(200); runCurrent()
        assertEquals(1, h.starts)
    }

    @Test
    fun leavingThePageEndsIt() = runTest {
        val h = Harness(this)
        h.autostart.arm()
        advanceTimeBy(3_000); runCurrent()
        h.autostart.finish()
        h.autostart.arm()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, h.starts)
    }

    @Test
    fun neverRestartsAfterPlaying() = runTest {
        val h = Harness(this)
        h.autostart.arm()
        advanceTimeBy(7_100); runCurrent()
        h.autostart.arm()
        h.autostart.setOverlayOpen(true)
        h.autostart.setOverlayOpen(false)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, h.starts)
    }

    @Test
    fun waitsForScrollToSettleButNotForever() = runTest {
        val h = Harness(this)
        h.autostart.arm()
        h.autostart.setScrolling(true)
        advanceTimeBy(7_500); runCurrent()
        assertEquals(0, h.starts)
        h.autostart.setScrolling(false)
        runCurrent()
        assertEquals(1, h.starts)

        val h2 = Harness(this)
        h2.autostart.arm()
        h2.autostart.setScrolling(true)
        advanceTimeBy(8_100); runCurrent()
        assertEquals(1, h2.starts)
    }

    @Test
    fun trailerAlreadyPlayingCountsAsDone() = runTest {
        val h = Harness(this, canStart = false)
        h.autostart.arm()
        advanceTimeBy(7_100); runCurrent()
        assertEquals(0, h.starts)
        assertEquals(NuvioCTrailerAutostart.Phase.DONE, h.autostart.phase)
    }

    @Test
    fun watchHistorySkipsAutostart() {
        val progress = com.nuvio.tv.domain.model.WatchProgress(
            contentId = "tt1", contentType = "movie", name = "x", poster = null, backdrop = null,
            logo = null, videoId = "tt1", season = null, episode = null, episodeTitle = null,
            position = 60_000, duration = 6_000_000, lastWatched = 0
        )
        val fresh = MetaDetailsUiState()
        assertEquals(false, nuvioCHasWatchHistory(fresh))
        assertEquals(true, nuvioCHasWatchHistory(fresh.copy(isMovieWatched = true)))
        assertEquals(true, nuvioCHasWatchHistory(fresh.copy(watchedEpisodes = setOf(1 to 1))))
        assertEquals(true, nuvioCHasWatchHistory(fresh.copy(episodeProgressMap = mapOf((1 to 2) to progress))))
        assertEquals(
            true,
            nuvioCHasWatchHistory(
                fresh.copy(
                    nextToWatch = com.nuvio.tv.domain.model.NextToWatch(
                        watchProgress = progress, isResume = true, nextVideoId = "tt1",
                        nextSeason = null, nextEpisode = null, displayText = "Resume"
                    )
                )
            )
        )
    }
}
