package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.data.nuvioc.NuvioCJustFinished

// [fork] Nuvio C round 28 `post_play_rating`: fed by the post-play controller's playback snapshots (read-only: it
// only looks at the numbers official already publishes; nothing about saved progress, Up next or scrobbling changes).
// Counts the time actually played this session (position moving forward a little at a time; seeks and skips don't
// count). A movie counts as finished when it reaches the point where the saved progress flips to watched
// (WatchProgress.COMPLETED_THRESHOLD, what Nuvio sync uploads) after at least 10 minutes covered, three quarters of
// it actually played. Resuming yesterday's half-watched film counts.
// Round 47 `rating_reliable`: also notes the film's video id, keeps the note fresh while the credits play, and logs
// each decision under NuvioCRate.
internal object NuvioCFinishTracker {
    private const val MIN_COVERED_MS = 10 * 60 * 1000L
    private const val MAX_TICK_MS = 4_000L
    private var key: String? = null
    private var lastPos = 0L
    private var startPos = 0L
    private var played = 0L
    private var decided = false
    private var marked = false

    fun onTick(contentType: String?, contentId: String?, videoId: String?, positionMs: Long, durationMs: Long, ended: Boolean) {
        if (!NuvioCFeatures.POST_PLAY_RATING) return
        if (contentType != "movie" || contentId.isNullOrBlank() || durationMs <= 0L) return
        if (key != contentId) {
            key = contentId
            lastPos = positionMs
            startPos = positionMs
            played = 0L
            decided = false
            marked = false
            return
        }
        val step = positionMs - lastPos
        if (step in 1..MAX_TICK_MS) played += step
        lastPos = positionMs
        if (decided) {
            if (marked) NuvioCJustFinished.touch(contentId)
            return
        }
        val trigger = (durationMs * com.nuvio.tv.domain.model.WatchProgress.COMPLETED_THRESHOLD).toLong()
        if (!ended && positionMs < trigger) return
        decided = true
        val covered = (minOf(positionMs, trigger) - startPos).coerceAtLeast(0L)
        marked = covered >= MIN_COVERED_MS && played >= covered * 3 / 4
        if (marked) NuvioCJustFinished.mark(contentId, listOf(videoId))
        else NuvioCJustFinished.log("not marked $contentId: covered ${covered / 1000}s, played ${played / 1000}s (needs 600s and 75%)")
    }
}
