package com.nuvio.tv.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.data.trailer.YoutubeChunkedDataSourceFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// [fork] Nuvio C: hold a playing background trailer while its page is covered (a popup, a cast /
// production page, another title) and carry on from the same spot when it's uncovered.
//
// The app has one shared trailer player. A child page may use it meanwhile (its own trailer, a
// poster preview) and, when it closes, stop it and take its picture output with it. So on release:
//   1. wait a moment for the closing page to let go of the player,
//   2. always point the player's picture back at this page's view (the old frozen-frame bug: the
//      player kept drawing into the closed page's view, so this page showed its last frame),
//   3. reload this trailer at the held spot if the player no longer holds it, restore the volume,
//   4. keep watching for a short while and reload again if a late stop from the closed page
//      clears it.
// If the player itself was released for full playback meanwhile, the trailer ends and the
// backdrop returns. Returns "busy" (held or resuming): TrailerPlayer ignores ended events and
// pause changes for that time, so another page's trailer ending never ends this one.

private const val NUVIO_C_HOLD_SETTLE_MS = 250L
private const val NUVIO_C_HOLD_WATCH_MS = 2_000L
private const val NUVIO_C_HOLD_POLL_MS = 100L

private fun Player.nuvioCHolds(trailerUrl: String): Boolean =
    currentMediaItem?.localConfiguration?.uri?.toString() == trailerUrl

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun NuvioCTrailerHoldEffect(
    player: ExoPlayer?,
    pool: TrailerPlayerPool?,
    isPlaying: Boolean,
    hold: Boolean,
    /** TrailerPlayer's full isPaused (hold included): other pauses such as Pause When Scrolling win. */
    isPaused: Boolean,
    muted: Boolean,
    trailerUrl: String?,
    trailerAudioUrl: String?,
    /** Points the shared player's picture back at this page's view. */
    reattachView: () -> Unit,
    onLost: () -> Unit
): State<Boolean> {
    val busy = remember { mutableStateOf(false) }
    val currentHold by rememberUpdatedState(hold)
    val currentIsPaused by rememberUpdatedState(isPaused)
    val currentMuted by rememberUpdatedState(muted)
    val currentReattach by rememberUpdatedState(reattachView)
    val currentOnLost by rememberUpdatedState(onLost)

    LaunchedEffect(player, isPlaying, trailerUrl, trailerAudioUrl) {
        busy.value = false
        if (player == null || !isPlaying || trailerUrl == null) return@LaunchedEffect

        fun reload(atMs: Long) {
            if (!trailerAudioUrl.isNullOrBlank()) {
                val factory = DefaultMediaSourceFactory(YoutubeChunkedDataSourceFactory())
                player.setMediaSource(
                    MergingMediaSource(
                        factory.createMediaSource(MediaItem.fromUri(trailerUrl)),
                        factory.createMediaSource(MediaItem.fromUri(trailerAudioUrl))
                    ),
                    atMs
                )
            } else {
                player.setMediaItem(MediaItem.fromUri(trailerUrl), atMs)
            }
            player.prepare()
        }

        fun resume(atMs: Long) {
            currentReattach()
            if (!player.nuvioCHolds(trailerUrl)) reload(atMs)
            player.volume = if (currentMuted) 0f else 1f
            player.playWhenReady = !currentIsPaused
        }

        var resumeAtMs = 0L
        while (true) {
            snapshotFlow { currentHold }.first { it }
            busy.value = true
            if (player.nuvioCHolds(trailerUrl)) resumeAtMs = player.currentPosition.coerceAtLeast(0L)
            snapshotFlow { currentHold }.first { !it }
            delay(NUVIO_C_HOLD_SETTLE_MS)
            if (currentHold) continue
            if (pool != null && pool.acquire() !== player) {
                busy.value = false
                currentOnLost()
                return@LaunchedEffect
            }
            resume(resumeAtMs)
            busy.value = false
            val watchUntil = SystemClock.uptimeMillis() + NUVIO_C_HOLD_WATCH_MS
            while (!currentHold && SystemClock.uptimeMillis() < watchUntil) {
                delay(NUVIO_C_HOLD_POLL_MS)
                if (player.nuvioCHolds(trailerUrl)) {
                    resumeAtMs = player.currentPosition.coerceAtLeast(0L)
                } else if (!currentHold) {
                    resume(resumeAtMs)
                }
            }
        }
    }
    return busy
}
