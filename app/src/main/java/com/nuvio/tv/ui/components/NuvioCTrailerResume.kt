package com.nuvio.tv.ui.components

import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C round 31 (switch `trailer_resume`): the background trailer carries on where it was.
// Going to the sources screen or the player releases the trailer's player (keeping it paused would hold a
// second video player on a 2 GB TV), so coming back used to start the trailer from the top. Now the
// position is remembered per trailer while the details page lives, and the trailer picks up from there
// (a second or two of buffering). Watching it to the end, or leaving the details page, forgets it.
object NuvioCTrailerResume {
    private const val MIN_RESUME_MS = 2_000L
    private const val KEEP = 6
    private val positions = LinkedHashMap<String, Long>()

    @Synchronized
    fun record(url: String?, positionMs: Long) {
        if (!NuvioCFeatures.TRAILER_RESUME || url.isNullOrBlank() || positionMs <= 0L) return
        positions.remove(url)
        positions[url] = positionMs
        while (positions.size > KEEP) positions.remove(positions.keys.first())
    }

    /** Where to start [url], or 0 for the beginning. */
    @Synchronized
    fun startAt(url: String?): Long {
        if (!NuvioCFeatures.TRAILER_RESUME || url.isNullOrBlank()) return 0L
        return positions[url]?.takeIf { it >= MIN_RESUME_MS } ?: 0L
    }

    @Synchronized
    fun forget(url: String?) {
        if (url != null) positions.remove(url)
    }
}
