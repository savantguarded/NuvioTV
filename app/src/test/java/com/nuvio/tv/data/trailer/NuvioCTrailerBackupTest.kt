package com.nuvio.tv.data.trailer

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// [fork] Nuvio C: IMDb-backup decision rules.
class NuvioCTrailerBackupTest {

    private var clock = 1_000_000L
    private val good = TrailerPlaybackSource(videoUrl = "https://yt/adaptive", audioUrl = "https://yt/audio")
    private val low = TrailerPlaybackSource(videoUrl = "https://yt/itag18")
    private val imdb = TrailerPlaybackSource(videoUrl = "https://imdb/1080.mp4")
    private var youtubeCalls = 0
    private var imdbCalls = 0

    @Before
    fun setUp() {
        NuvioCYouTubeHealth.resetForTest()
        NuvioCYouTubeHealth.now = { clock }
    }

    @After
    fun tearDown() = NuvioCYouTubeHealth.resetForTest()

    private suspend fun choose(youtube: TrailerPlaybackSource?, fromImdb: TrailerPlaybackSource?) =
        NuvioCTrailerBackup.choose(
            youtube = { youtubeCalls++; youtube },
            imdb = { imdbCalls++; fromImdb }
        )

    @Test
    fun goodYouTubeNeverTouchesImdb() = runTest {
        assertEquals(good, choose(good, imdb))
        assertEquals(0, imdbCalls)
    }

    @Test
    fun noTrailerOnYouTubeDoesNotStartImdb() = runTest {
        assertNull(choose(null, imdb))
        assertEquals(0, imdbCalls)
    }

    @Test
    fun lowQualityYouTubeIsReplacedByImdb() = runTest {
        NuvioCYouTubeHealth.markDegraded(low)
        assertEquals(imdb, choose(low, imdb))
        assertFalse(NuvioCYouTubeHealth.cacheable(low))
    }

    @Test
    fun lowQualityYouTubeKeptWhenImdbHasNothing() = runTest {
        NuvioCYouTubeHealth.markDegraded(low)
        assertEquals(low, choose(low, null))
    }

    @Test
    fun rateLimitStillAsksYouTubeFirst() = runTest {
        NuvioCYouTubeHealth.onWatchPageFailed(429)
        assertEquals(good, choose(good, imdb))
        assertEquals(1, youtubeCalls)
        assertEquals(0, imdbCalls)
    }

    @Test
    fun rateLimitUsesImdbWhenYouTubeFails() = runTest {
        NuvioCYouTubeHealth.onWatchPageFailed(429)
        assertEquals(imdb, choose(null, imdb))
        assertEquals(1, imdbCalls)
    }

    @Test
    fun rateLimitLowQualityStillTriesImdb() = runTest {
        NuvioCYouTubeHealth.onWatchPageFailed(429)
        NuvioCYouTubeHealth.markDegraded(low)
        assertEquals(imdb, choose(low, imdb))
    }

    @Test
    fun backoffLastsFifteenMinutes() {
        NuvioCYouTubeHealth.onWatchPageFailed(429)
        assertTrue(NuvioCYouTubeHealth.backingOff())
        assertEquals(15, NuvioCYouTubeHealth.backoffMinutesLeft())
        clock += NuvioCYouTubeHealth.BACKOFF_MS - 1
        assertTrue(NuvioCYouTubeHealth.backingOff())
        clock += 1
        assertFalse(NuvioCYouTubeHealth.backingOff())
    }

    @Test
    fun otherWatchPageErrorsDoNotBackOff() {
        NuvioCYouTubeHealth.onWatchPageFailed(500)
        NuvioCYouTubeHealth.onWatchPageFailed(403)
        assertFalse(NuvioCYouTubeHealth.backingOff())
    }

    @Test
    fun normalLinksStayCacheable() {
        assertTrue(NuvioCYouTubeHealth.cacheable(good))
    }
}
