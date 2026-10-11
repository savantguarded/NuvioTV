package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C: HDR as mpv reports it (video-params/gamma: pq / hlg), for the badge and HDR dim
// when the mpv engine plays. Read-only property lookup.
// 2026-10-11: the ExoPlayer side (a per-frame decoder callback, round 3) is gone: it never fired on
// the TCL and sat on the decoder. ExoPlayer HDR now comes from the track info and the video's own
// header (NuvioCSpsColor). Used by nuvioCVisualTag. Switch: NuvioCFeatures.HDR_DECODED.

import androidx.media3.common.C
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.data.local.InternalPlayerEngine

internal object NuvioCDecodedColor {

    /** C.COLOR_TRANSFER_* from mpv's decoded gamma, or null (ExoPlayer, or not known yet). */
    fun transfer(controller: PlayerRuntimeController): Int? {
        if (!NuvioCFeatures.HDR_DECODED) return null
        if (controller.currentInternalPlayerEngine != InternalPlayerEngine.MVP_PLAYER) return null
        return gammaToC(controller.mpvView?.nuvioCVideoGamma())
    }

    fun gammaToC(gamma: String?): Int? = when (gamma) {
        "pq" -> C.COLOR_TRANSFER_ST2084
        "hlg" -> C.COLOR_TRANSFER_HLG
        null -> null
        else -> C.COLOR_TRANSFER_SDR
    }
}
