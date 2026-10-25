package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.data.nuvioc.NuvioCJustFinished

// [fork] Nuvio C round 28 `post_play_rating`: fed by the post-play controller's playback snapshots (no extra
// work in the player). Counts the time actually played this session (position moving forward a little at a
// time; seeks and skips don't count). A movie counts as finished when it reaches the trigger (the same point
// official's movie post-play screen uses: the IntroDB end-credits marker, else the threshold) after at least
// 10 minutes covered, three quarters of it actually played. Resuming yesterday's half-watched film counts.
internal object NuvioCFinishTracker {
    private const val MIN_COVERED_MS = 10 * 60 * 1000L
    private const val MAX_TICK_MS = 4_000L
    private var key: String? = null
    private var lastPos = 0L
    private var startPos = 0L
    private var played = 0L
    private var decided = false

    fun onTick(contentType: String?, contentId: String?, positionMs: Long, durationMs: Long, ended: Boolean, triggerMs: () -> Long) {
        if (!NuvioCFeatures.POST_PLAY_RATING) return
        if (contentType != "movie" || contentId.isNullOrBlank() || durationMs <= 0L) return
        if (key != contentId) {
            key = contentId
            lastPos = positionMs
            startPos = positionMs
            played = 0L
            decided = false
            return
        }
        val step = positionMs - lastPos
        if (step in 1..MAX_TICK_MS) played += step
        lastPos = positionMs
        if (decided) return
        val trigger = triggerMs()
        if (!ended && positionMs < trigger) return
        decided = true
        val covered = (minOf(positionMs, trigger) - startPos).coerceAtLeast(0L)
        if (covered >= MIN_COVERED_MS && played >= covered * 3 / 4) NuvioCJustFinished.mark(contentId)
    }
}
