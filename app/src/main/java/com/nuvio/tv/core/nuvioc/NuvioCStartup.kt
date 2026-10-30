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

    /** startup_settle_sync: true while the account's start-up sync (watched history, progress, library) runs. */
    val syncRunning = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * [fork] round 33 startup_settle_images: picture loads in flight (all of Coil's requests) and when the last
     * one ended, so the start-up screen can wait until Home's first rows have their posters on screen.
     */
    val imagesInFlight = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var lastImageEndMs: Long = 0L

    /** Counts Coil requests; installed on the app's image loader only with STARTUP_SETTLE_IMAGES. */
    object ImageActivity : coil3.EventListener() {
        override fun onStart(request: coil3.request.ImageRequest) { imagesInFlight.incrementAndGet() }
        override fun onSuccess(request: coil3.request.ImageRequest, result: coil3.request.SuccessResult) = end()
        override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) = end()
        override fun onCancel(request: coil3.request.ImageRequest) = end()
        private fun end() {
            if (imagesInFlight.decrementAndGet() < 0) imagesInFlight.set(0)
            lastImageEndMs = android.os.SystemClock.uptimeMillis()
        }
    }

    /** True when no picture has been loading for [quietMs]. */
    fun imagesQuiet(quietMs: Long = 500L): Boolean {
        val sinceEnd = android.os.SystemClock.uptimeMillis() - lastImageEndMs
        // 3 s with nothing finishing also counts (a slow straggler or a request that never reports back)
        return (imagesInFlight.get() <= 0 && sinceEnd >= quietMs) || sinceEnd >= 3_000L
    }

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

/**
 * [fork] round 33 startup_settle_images: while the start-up screen is up, the first five Home rows' first ten
 * posters are fetched to the disk cache (decoded at 1 x 1, not kept in memory), so scrolling down the first rows
 * from a cold start reads them from disk instead of the network.
 */
@androidx.compose.runtime.Composable
internal fun NuvioCStartupWarmRows(urls: List<String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(urls.isNotEmpty()) {
        if (urls.isEmpty() || !NuvioCStartup.holdingKeys) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(context)
        urls.forEach { url ->
            runCatching {
                loader.enqueue(
                    coil3.request.ImageRequest.Builder(context)
                        .data(url)
                        .size(1, 1)
                        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                        .build()
                )
            }
        }
    }
}
