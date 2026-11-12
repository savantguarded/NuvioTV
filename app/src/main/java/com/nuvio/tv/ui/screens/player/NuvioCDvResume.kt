package com.nuvio.tv.ui.screens.player

// [fork] round 42 dv_resume_header_first
//
// Captured on the TV (2026-11-12): the same Dolby Vision MKV came out as a plain HEVC track (HDR10+) whenever the
// player was prepared straight at a resume position, and as Dolby Vision whenever it was prepared at the start. With a
// resume position the extractor jumps to the file's index before playing; on those starts the Dolby Vision config
// never reached the track. Fix: on a Dolby Vision TV, an MKV resume is prepared at 0 and the resume position is applied
// through the player's existing pending-seek step once the tracks are read. The decoder and extractor are untouched.

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

internal object NuvioCDvResume {
    /** True when the current start used the header-first resume (for the playback log line). */
    @Volatile var lastHeaderFirst: Boolean = false

    fun shouldReadHeaderFirst(context: Context, positionMs: Long, url: String?, filename: String?, mime: String?): Boolean {
        if (!com.nuvio.tv.NuvioCFeatures.DV_RESUME_HEADER_FIRST || positionMs <= 0L) return false
        val mkv = mime?.contains("matroska", ignoreCase = true) == true ||
            listOfNotNull(filename, url?.substringBefore('?')).any { it.endsWith(".mkv", ignoreCase = true) }
        if (!mkv) return false
        return runCatching {
            @Suppress("DEPRECATION")
            context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.hdrCapabilities?.supportedHdrTypes?.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) == true
        }.getOrDefault(false)
    }
}
