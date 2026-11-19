package com.nuvio.tv.core.nuvioc

import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.NuvioCPlayback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [fork] Nuvio C round 50 `startup_job_queue`: the TCL's first minutes after a cold start were one long burst of
 * background work (watch-progress merge, calendar refresh, ratings for every Continue Watching show, the Taste
 * refresh) all competing with Home. Background jobs that are not needed to draw Home now line up here: each waits
 * until the start-up screen has gone and Home settled, then for its own delay, then for any film to stop, and then
 * takes its turn alone. Continue Watching itself is never queued: only the extras (episode ratings) are.
 */
object NuvioCStartupQueue {
    private val released = CompletableDeferred<Unit>()
    private val turn = Mutex()

    /** Home has settled: queued jobs may start. Called once by MainActivity; later calls do nothing. */
    fun release() { released.complete(Unit) }

    val isReleased: Boolean get() = released.isCompleted

    /**
     * Runs [block] in its turn. [afterMs] is the wait after the release. If the release never comes (an unusual
     * start path) the job runs after [maxWaitMs] anyway, so nothing is lost, only delayed.
     */
    suspend fun <T> inTurn(afterMs: Long = 0L, maxWaitMs: Long = 45_000L, block: suspend () -> T): T {
        if (!NuvioCFeatures.STARTUP_JOB_QUEUE) return block()
        withTimeoutOrNull(maxWaitMs) { released.await() }
        if (afterMs > 0L) delay(afterMs)
        NuvioCPlayback.state.first { !it }
        return turn.withLock { block() }
    }
}
