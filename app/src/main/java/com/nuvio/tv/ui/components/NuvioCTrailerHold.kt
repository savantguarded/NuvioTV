package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.data.trailer.YoutubeChunkedDataSourceFactory
import kotlinx.coroutines.delay

// [fork] Nuvio C: hold a playing trailer in place while its page is covered (an overlay, a cast /
// production page, another title) and carry on from the same spot when it's uncovered.
//
// The pause itself goes through TrailerPlayer's normal isPaused. This effect only covers the case
// where something else used the app's single shared trailer player meanwhile (another title's
// trailer, a poster preview): it gives the picture back to this page's view, reloads the trailer
// and seeks back to where it was held. If the
// player itself was handed over to full playback (released), the trailer ends instead and the
// page shows its backdrop again.

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun NuvioCTrailerHoldEffect(
    player: ExoPlayer?,
    pool: TrailerPlayerPool?,
    isPlaying: Boolean,
    hold: Boolean,
    /** TrailerPlayer's full isPaused (hold included): other pauses such as Pause When Scrolling win. */
    isPaused: Boolean,
    trailerUrl: String?,
    trailerAudioUrl: String?,
    /** Points the shared player's picture back at this page's view (another view took it). */
    reattachView: () -> Unit,
    onLost: () -> Unit
) {
    var heldAtMs by remember(trailerUrl) { mutableLongStateOf(-1L) }
    val currentOnLost by rememberUpdatedState(onLost)
    val currentIsPaused by rememberUpdatedState(isPaused)
    LaunchedEffect(hold, player) {
        if (player == null || !isPlaying || trailerUrl == null) return@LaunchedEffect
        if (hold) {
            heldAtMs = player.currentPosition.coerceAtLeast(0L)
            return@LaunchedEffect
        }
        val at = heldAtMs
        if (at < 0L) return@LaunchedEffect
        heldAtMs = -1L
        if (pool != null && pool.acquire() !== player) {
            currentOnLost()
            return@LaunchedEffect
        }
        if (player.currentMediaItem == null) {
            reattachView()
            if (!trailerAudioUrl.isNullOrBlank()) {
                val factory = DefaultMediaSourceFactory(YoutubeChunkedDataSourceFactory())
                player.setMediaSource(
                    MergingMediaSource(
                        factory.createMediaSource(MediaItem.fromUri(trailerUrl)),
                        factory.createMediaSource(MediaItem.fromUri(trailerAudioUrl))
                    ),
                    at
                )
            } else {
                player.setMediaItem(MediaItem.fromUri(trailerUrl), at)
            }
            player.prepare()
        }
        delay(16) // after TrailerPlayer's own isPaused step for the same frame
        player.playWhenReady = !currentIsPaused
    }
}
