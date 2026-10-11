package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C (2026-10-11): HDR read from the video's own header, already in the track info.
// Every HEVC video starts with a small header (the SPS) that states its transfer: SDR, PQ (HDR10,
// HDR10+, Dolby Vision 8.1 base) or HLG. MediaInfo and mpv read the same field. ExoPlayer copies it
// into the track's colour info for MP4 files, but for MKV only when the file has a separate Colour
// block, which many encodes leave out: those HDR files looked SDR to the badge and the HDR dim.
// This reads that header from Format.initializationData (the bytes ExoPlayer already loaded with
// the track, before the first frame) using media3's own parser. Read-only: nothing is downloaded,
// and the player, its extractors and the decoder are never touched. Parsed once per track.
// Used by nuvioCVisualTag (badge + HDR dim). Switch: NuvioCFeatures.HDR_DECODED.

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil

@androidx.annotation.OptIn(UnstableApi::class)
internal object NuvioCSpsColor {
    private class Cached(val format: Format, val transfer: Int?)

    @Volatile private var cached: Cached? = null

    /** C.COLOR_TRANSFER_* stated in the HEVC header of [format], or null when unknown / not HEVC. */
    fun transfer(format: Format?): Int? {
        if (format == null || !com.nuvio.tv.NuvioCFeatures.HDR_DECODED) return null
        cached?.let { if (it.format === format) return it.transfer }
        val result = runCatching { parse(format) }.getOrNull()
        cached = Cached(format, result)
        return result
    }

    private fun parse(format: Format): Int? {
        val mime = format.sampleMimeType
        val codecs = format.codecs.orEmpty().lowercase()
        val hevcLike = mime == MimeTypes.VIDEO_H265 ||
            (mime == MimeTypes.VIDEO_DOLBY_VISION && (codecs.startsWith("dvh") || codecs.isEmpty()))
        if (!hevcLike) return null
        for (data in format.initializationData) {
            fromCsd(data)?.let { return it }
        }
        return null
    }

    /** Scans one codec-config buffer (start-code separated NAL units) for the VPS / SPS. */
    internal fun fromCsd(data: ByteArray): Int? {
        val starts = nalStarts(data)
        var vps: NalUnitUtil.H265VpsData? = null
        for (i in starts.indices) {
            val offset = starts[i]
            val limit = if (i + 1 < starts.size) nextLimit(data, starts[i + 1]) else data.size
            if (limit - offset < 3) continue
            val type = (data[offset].toInt() shr 1) and 0x3F
            val layerId = ((data[offset].toInt() and 1) shl 5) or ((data[offset + 1].toInt() shr 3) and 0x1F)
            if (layerId != 0) continue
            when (type) {
                NalUnitUtil.H265_NAL_UNIT_TYPE_VPS ->
                    vps = runCatching { NalUnitUtil.parseH265VpsNalUnit(data, offset, limit) }.getOrNull()
                NalUnitUtil.H265_NAL_UNIT_TYPE_SPS -> {
                    val t = NalUnitUtil.parseH265SpsNalUnit(data, offset, limit, vps).colorTransfer
                    return when (t) {
                        C.COLOR_TRANSFER_ST2084, C.COLOR_TRANSFER_HLG, C.COLOR_TRANSFER_SDR -> t
                        else -> null
                    }
                }
            }
        }
        return null
    }

    /** Offsets of NAL headers (the byte right after each 00 00 01 start code). */
    private fun nalStarts(data: ByteArray): List<Int> {
        val out = ArrayList<Int>(4)
        var i = 0
        while (i + 2 < data.size) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 1) {
                out += i + 3
                i += 3
            } else i++
        }
        return out
    }

    /** End of a NAL unit: the start of the next start code (00 00 01, or 00 00 00 01). */
    private fun nextLimit(data: ByteArray, nextHeader: Int): Int {
        var end = nextHeader - 3
        if (end > 0 && data[end - 1].toInt() == 0) end--
        return end
    }
}
