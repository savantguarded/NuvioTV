package com.nuvio.tv.core.nuvioc

import android.app.job.JobScheduler
import android.content.Context

// [fork] Nuvio C round 26b: smooth start-up.
// Measured on the TCL (2026-10-23, cold start, profile pick at ~1.6 s): Home leaves its loader at ~9 s, then the
// first rows and a big memory clean-up froze the screen for 2.2 s and 2.5 s within the next 4 s, in front of you.
// The start-up screen now stays over Home until it has settled (STARTUP_SETTLE) and its loading sweep is animated
// by the render thread (STARTUP_SWEEP), so the wait is smooth and Home appears in one piece.

object NuvioCStartup {
    /** While true the start-up screen is up: remote keys are held so nothing moves behind it. */
    @Volatile
    var holdingKeys: Boolean = false

    /** no_tv_channels: cancels the 15-minute and boot-time channel refresh jobs scheduled by earlier builds. */
    fun stopTvChannels(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            scheduler.allPendingJobs.filter { it.id == JOB_PERIODIC || it.id == JOB_IMMEDIATE }.forEach { scheduler.cancel(it.id) }
        }
    }

    // TvChannelRefreshJobService's job ids.
    private const val JOB_PERIODIC = 10_010
    private const val JOB_IMMEDIATE = 10_011
}

/**
 * Settle test for the start-up screen: Home counts as settled once the main thread has answered every frame
 * within [maxGapMs] for [quietMs] in a row. [gaps] are the frame-to-frame times (ms) since Home was ready, newest last.
 */
internal fun nuvioCStartupSettled(gaps: List<Long>, quietMs: Long = 900L, maxGapMs: Long = 50L): Boolean {
    var quiet = 0L
    for (gap in gaps.asReversed()) {
        if (gap > maxGapMs) return false
        quiet += gap
        if (quiet >= quietMs) return true
    }
    return false
}
