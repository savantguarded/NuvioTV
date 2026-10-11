package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C: HDR10+ read from the file itself, completely outside playback.
// HDR10+ is per-frame metadata inside the HEVC / AV1 stream (an ITU-T T.35 message from Samsung:
// country B5, provider 003C, oriented code 0001, application 4). Containers don't flag it and the
// TV's decoder doesn't report it to apps, so HDR10+ files whose name doesn't say so showed HDR10.
//
// 2026-10-11 rework: the first version sat between the file and the decoder (it wrapped ExoPlayer's
// extractors) and a bug in it fed the decoder zeros, which broke Dolby Vision playback. This version
// never touches ExoPlayer, its extractors, its data sources or the decoder. It makes its own,
// separate HTTP request for the first 8 MB of the file on a background thread, looks for the
// HDR10+ message in those bytes, and throws them away. Playback is exactly official code.
// Progressive files only (MKV / MP4 from debrid and add-ons); HLS / DASH and mpv are untouched.
// Used by nuvioCVisualTag. Switch: NuvioCFeatures.HDR_DECODED.

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.nuvio.tv.NuvioCFeatures
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

internal object NuvioCHdr10PlusSniff {
    private const val TAG = "NuvioCOsdBadges"
    private const val MAX_BYTES = 8L * 1024 * 1024

    /** T.35 header of an HDR10+ message (application identifier 4 included). */
    private val SIGNATURE = byteArrayOf(0xB5.toByte(), 0x00, 0x3C, 0x00, 0x01, 0x04)

    /** Own client: shares the playback client's settings but none of its connection stats. */
    private val client: OkHttpClient by lazy {
        PlayerPlaybackNetworking.playbackHttpClient.newBuilder()
            .eventListener(EventListener.NONE)
            .build()
    }

    /** Url of the stream the latest check belongs to, and whether HDR10+ was found in it. */
    private val foundFor = mutableStateOf<String?>(null)
    @Volatile private var currentUrl: String? = null
    @Volatile private var currentCall: Call? = null

    /** True when the stream now playing carries HDR10+. Compose state, so the badge updates when found. */
    fun found(): Boolean {
        if (!NuvioCFeatures.HDR_DECODED) return false
        val url = foundFor.value ?: return false
        return url == currentUrl
    }

    /**
     * Starts the side check for a new stream (and cancels the previous one). Never throws and never
     * blocks: any failure just means no HDR10+ badge from the stream (filename detection still works).
     */
    fun start(url: String, headers: Map<String, String>) {
        if (!NuvioCFeatures.HDR_DECODED) return
        currentCall?.cancel()
        currentCall = null
        currentUrl = url
        foundFor.value = null
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return
        thread(name = "NuvioCHdr10PlusSniff", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            runCatching { check(url, headers) }
                .onFailure { if (currentUrl == url) Log.i(TAG, "HDR10+ side check skipped: ${it.javaClass.simpleName}") }
        }
    }

    private fun check(url: String, headers: Map<String, String>) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> if (!k.equals("Range", ignoreCase = true)) header(k, v) }
            header("Range", "bytes=0-${MAX_BYTES - 1}")
        }.build()
        val call = client.newCall(request)
        if (currentUrl != url) return
        currentCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) return
            val stream = response.body?.byteStream() ?: return
            val matcher = Matcher()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (total < MAX_BYTES && currentUrl == url) {
                val n = stream.read(buf, 0, minOf(buf.size.toLong(), MAX_BYTES - total).toInt())
                if (n <= 0) break
                matcher.feed(buf, 0, n)
                total += n
                if (matcher.matched) {
                    if (currentUrl == url) {
                        foundFor.value = url
                        Log.i(TAG, "HDR10+ metadata found in the video stream (side check, ${total / 1024} KB read)")
                    }
                    break
                }
            }
        }
        if (currentCall === call) currentCall = null
    }

    /** Byte matcher kept across reads (the message can straddle two reads). Unit tested. */
    internal class Matcher {
        private var state = 0
        var matched = false
            private set

        fun feed(data: ByteArray, offset: Int, length: Int) {
            if (matched) return
            for (i in offset until offset + length) {
                val b = data[i]
                state = when {
                    b == SIGNATURE[state] -> state + 1
                    b == SIGNATURE[0] -> 1
                    else -> 0
                }
                if (state == SIGNATURE.size) { matched = true; return }
            }
        }
    }
}
