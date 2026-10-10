package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C: HDR seen by the decoder, for titles whose file and stream name don't say HDR.
// Many WEB releases ("2160p.WEB.h265") carry HDR only inside the video stream itself: the MKV has
// no colour block, so ExoPlayer's track info says nothing and the stream title has no HDR word,
// yet the TV switches to HDR. Without this the OSD badge was missing and the HDR dim never kicked in.
//  - ExoPlayer: the video decoder's output format (MediaFormat color-transfer), read from the
//    frame-metadata callback. Stored together with the track it belongs to, so a new stream never
//    inherits the last one's value.
//  - mpv: video-params/gamma (pq / hlg) as mpv decoded it.
// Used by nuvioCVisualTag (badges and HDR dim). Switch: NuvioCFeatures.HDR_DECODED.

import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.data.local.InternalPlayerEngine
import java.lang.ref.WeakReference

internal object NuvioCDecodedColor {

    private class Seen(val format: Format, val transfer: Int)

    @Volatile private var seen: Seen? = null
    private var watched: WeakReference<ExoPlayer>? = null

    private val listener = VideoFrameMetadataListener { _, _, format, mediaFormat ->
        val transfer = mediaFormat?.toC() ?: return@VideoFrameMetadataListener
        val last = seen
        if (last == null || last.transfer != transfer || !sameTrack(last.format, format)) seen = Seen(format, transfer)
    }

    /** Hooks the decoder callback onto the current ExoPlayer once (cheap to call every poll). */
    fun watch(controller: PlayerRuntimeController) {
        if (!NuvioCFeatures.HDR_DECODED) return
        val player = controller._exoPlayer ?: return
        if (watched?.get() === player) return
        runCatching { player.setVideoFrameMetadataListener(listener) }
            .onSuccess { watched = WeakReference(player) }
    }

    /**
     * C.COLOR_TRANSFER_* the decoder reports for the video on screen, or null when unknown.
     * ExoPlayer: only when it belongs to [videoFormat]. mpv: from its decoded gamma.
     */
    fun transfer(controller: PlayerRuntimeController, videoFormat: Format?): Int? {
        if (!NuvioCFeatures.HDR_DECODED) return null
        if (controller.currentInternalPlayerEngine == InternalPlayerEngine.MVP_PLAYER) {
            return gammaToC(controller.mpvView?.nuvioCVideoGamma())
        }
        val last = seen
        val known = last != null && videoFormat != null && sameTrack(last.format, videoFormat)
        // The callback runs for every decoded frame: listen only until this track's colour is known,
        // then let go (re-hooked when the track changes). 2026-10-10 OSD lag fix.
        if (known) unwatch() else watch(controller)
        return if (known) last!!.transfer else null
    }

    private fun unwatch() {
        val player = watched?.get() ?: return
        runCatching { player.clearVideoFrameMetadataListener(listener) }
        watched = null
    }

    /** Same video track: identical object, or same codec, size and codec string. */
    private fun sameTrack(a: Format, b: Format): Boolean = a === b || (
        a.sampleMimeType == b.sampleMimeType && a.codecs == b.codecs &&
            a.width == b.width && a.height == b.height
        )

    fun gammaToC(gamma: String?): Int? = when (gamma) {
        "pq" -> C.COLOR_TRANSFER_ST2084
        "hlg" -> C.COLOR_TRANSFER_HLG
        null -> null
        else -> C.COLOR_TRANSFER_SDR
    }

    private fun MediaFormat.toC(): Int? {
        if (!containsKey(MediaFormat.KEY_COLOR_TRANSFER)) return null
        return when (getInteger(MediaFormat.KEY_COLOR_TRANSFER)) {
            MediaFormat.COLOR_TRANSFER_ST2084 -> C.COLOR_TRANSFER_ST2084
            MediaFormat.COLOR_TRANSFER_HLG -> C.COLOR_TRANSFER_HLG
            MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> C.COLOR_TRANSFER_SDR
            else -> null
        }
    }
}
