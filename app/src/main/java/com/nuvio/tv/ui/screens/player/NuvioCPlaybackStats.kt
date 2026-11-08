package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 34 `stats_panel_full`: counters for the stream info panel, taken from ExoPlayer's own
// analytics callbacks (nothing downloaded, nothing changed in playback, no network layer). Reset when a new
// title starts. Attached for the whole playback so the numbers cover it, not only while the panel is open.

import android.app.Activity
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

@OptIn(UnstableApi::class)
internal class NuvioCPlaybackStats : AnalyticsListener {
    @Volatile var rebuffers = 0; private set
    @Volatile var underruns = 0; private set
    @Volatile var loadErrors = 0; private set
    @Volatile var loadedBytes = 0L; private set
    @Volatile var lastRequestMs = -1L; private set
    @Volatile var videoDecoder: String? = null; private set
    @Volatile var audioDecoder: String? = null; private set
    @Volatile var frameLeadMs: Float? = null; private set
    private var wasReady = false

    private fun reset() {
        rebuffers = 0; underruns = 0; loadErrors = 0; loadedBytes = 0L; lastRequestMs = -1L
        frameLeadMs = null; wasReady = false
    }

    override fun onMediaItemTransition(eventTime: AnalyticsListener.EventTime, mediaItem: androidx.media3.common.MediaItem?, reason: Int) = reset()

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        if (state == Player.STATE_READY) wasReady = true
        else if (state == Player.STATE_BUFFERING && wasReady) rebuffers++
    }

    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        underruns++
    }

    override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        if (!wasCanceled) loadErrors++
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        loadedBytes += loadEventInfo.bytesLoaded.coerceAtLeast(0L)
        lastRequestMs = loadEventInfo.loadDurationMs
    }

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        videoDecoder = decoderName
    }

    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        audioDecoder = decoderName
    }

    override fun onVideoFrameProcessingOffset(eventTime: AnalyticsListener.EventTime, totalProcessingOffsetUs: Long, frameCount: Int) {
        if (frameCount > 0) frameLeadMs = totalProcessingOffsetUs / frameCount / 1000f
    }

    companion object {
        /** One per playback screen; the panel reads it. */
        @Volatile var current: NuvioCPlaybackStats? = null

        /** "MaxCLL 1000 · MaxFALL 400" from the video format's static HDR metadata (CTA-861.3 layout). */
        fun hdrStatic(info: ByteArray?): String? {
            if (info == null || info.size < 25) return null
            return runCatching {
                val b = ByteBuffer.wrap(info).order(ByteOrder.LITTLE_ENDIAN)
                val maxLum = b.getShort(17).toInt() and 0xFFFF
                val cll = b.getShort(21).toInt() and 0xFFFF
                val fall = b.getShort(23).toInt() and 0xFFFF
                listOfNotNull(
                    cll.takeIf { it > 0 }?.let { "MaxCLL $it" },
                    fall.takeIf { it > 0 }?.let { "MaxFALL $it" },
                    maxLum.takeIf { it > 0 }?.let { "Mastering $it nits" }
                ).joinToString(" · ").ifBlank { null }
            }.getOrNull()
        }

        fun displayMode(context: Context): String? = runCatching {
            @Suppress("DEPRECATION")
            val display = (context as? Activity)?.windowManager?.defaultDisplay ?: return null
            val mode = display.mode
            String.format(Locale.US, "%d×%d @ %.2f", mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
        }.getOrNull()

        fun cpuClock(): String? = runCatching {
            val khz = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").readText().trim().toLong()
            String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
        }.getOrNull()
    }
}
