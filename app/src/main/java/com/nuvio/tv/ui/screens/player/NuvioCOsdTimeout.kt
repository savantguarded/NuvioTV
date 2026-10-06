package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C: how long the player controls stay up. Official hides them 3 s after the last
// press. Nuvio C keeps them 8 s after they open or after a button press, but still hides 3 s after
// a seek (left/right on the seek bar), so skipping around doesn't leave them over the picture.
// Switch: NuvioCFeatures.OSD_TIMEOUT (off = 3 s always, as official).

import com.nuvio.tv.NuvioCFeatures

internal object NuvioCOsdTimeout {
    private const val OFFICIAL_MS = 3_000L
    private const val OPEN_MS = 8_000L

    @Volatile
    private var seekPending = false

    /** A seek is about to (re)schedule the hide: that one uses the short official delay. */
    fun markSeek() {
        seekPending = true
    }

    /** Delay for the hide being scheduled now. Consumes a pending seek mark. */
    fun hideDelayMs(): Long {
        val afterSeek = seekPending
        seekPending = false
        if (!NuvioCFeatures.OSD_TIMEOUT) return OFFICIAL_MS
        return if (afterSeek) OFFICIAL_MS else OPEN_MS
    }
}
