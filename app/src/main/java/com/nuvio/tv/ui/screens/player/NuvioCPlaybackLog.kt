package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 39 `playback_log`: one line per playback start, to catch Dolby Vision dropping to HDR10 /
// HDR10+ / SDR on files that normally play in DV. Read-only: it listens to ExoPlayer's analytics callbacks (which
// decoder was picked, the video format it was given) and writes a line once the video is ready. It never touches
// decoder selection, the renderer or the stream.
//
// Where to read it: logcat tag NuvioCPlaybackLog, or the file
//   /sdcard/Android/data/com.nuvio.tv.c/files/nuvio-c-playback.log   (adb pull; last 300 lines kept)
//
// What a fallback looks like: a "dvhe"/"dvh1" codecs string or video/dolby-vision mime with a decoder name that
// isn't a Dolby Vision one (no "dolby"/"dvhe" in it), or the same file logging a different decoder on another start.

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
internal class NuvioCPlaybackLog(
    context: Context,
    private val describe: () -> String
) : AnalyticsListener {
    private val file: File? = runCatching { context.applicationContext.getExternalFilesDir(null)?.let { File(it, FILE_NAME) } }.getOrNull()
    private var decoder: String? = null
    private var format: Format? = null
    private var logged = false
    private var starts = 0

    private fun reset() {
        decoder = null; format = null; logged = false
    }

    override fun onMediaItemTransition(eventTime: AnalyticsListener.EventTime, mediaItem: androidx.media3.common.MediaItem?, reason: Int) = reset()

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        if (logged && decoderName != decoder) logged = false // a new decoder mid-play (source switch, restart) gets its own line
        decoder = decoderName
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        this.format = format
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        if (state != Player.STATE_READY || logged) return
        val f = format ?: return
        logged = true
        starts++
        val line = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            append(" | ").append(runCatching(describe).getOrDefault("?"))
            append(" | start ").append(starts)
            append(" | decoder=").append(decoder ?: "?")
            append(" | mime=").append(f.sampleMimeType ?: "?")
            append(" codecs=").append(f.codecs ?: "?")
            append(" | ").append(f.width).append('x').append(f.height)
            append(" | transfer=").append(transferName(f.colorInfo?.colorTransfer))
            append(" | dv=").append(if (looksDolbyVision(f)) "file" else "no")
            append(" decoderDv=").append(if (decoderIsDolby(decoder)) "yes" else "no")
        }
        Log.i(TAG, line)
        val target = file ?: return
        WRITER.execute { append(target, line) }
    }

    companion object {
        private const val TAG = "NuvioCPlaybackLog"
        private const val FILE_NAME = "nuvio-c-playback.log"
        private const val KEEP_LINES = 300
        private val WRITER = Executors.newSingleThreadExecutor { r -> Thread(r, "NuvioCPlaybackLog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

        internal fun looksDolbyVision(f: Format): Boolean {
            val mime = f.sampleMimeType.orEmpty().lowercase(Locale.US)
            val codecs = f.codecs.orEmpty().lowercase(Locale.US)
            return "dolby-vision" in mime || codecs.startsWith("dvh") || codecs.startsWith("dva") || codecs.startsWith("dav1")
        }

        internal fun decoderIsDolby(name: String?): Boolean {
            val n = name.orEmpty().lowercase(Locale.US)
            return "dolby" in n || "dvhe" in n || ".dv." in n || n.endsWith(".dv")
        }

        internal fun transferName(t: Int?): String = when (t) {
            C.COLOR_TRANSFER_ST2084 -> "PQ"
            C.COLOR_TRANSFER_HLG -> "HLG"
            C.COLOR_TRANSFER_SDR -> "SDR"
            C.COLOR_TRANSFER_LINEAR -> "linear"
            null, Format.NO_VALUE -> "unknown"
            else -> t.toString()
        }

        private fun append(file: File, line: String) {
            runCatching {
                val old = if (file.exists()) file.readLines() else emptyList()
                file.writeText((old + line).takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }
}
