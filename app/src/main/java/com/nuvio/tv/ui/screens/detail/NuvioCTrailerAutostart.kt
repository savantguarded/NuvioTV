package com.nuvio.tv.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// [fork] Nuvio C: background-trailer autostart. With "Background trailers" on, the countdown
// runs for the whole details page instead of only while Play is focused. This file holds all
// of it; upstream files only feed it events through a few `[fork]` lines. Toggle off = never
// armed, and the upstream Play-focus countdown runs unchanged.
//
// Rules (one visit = one countdown, never restarted once the trailer has played):
// - In-page overlays (comments, synopsis, play options, shuffle, season options, list picker,
//   a trailer from the trailers row), a cast page or another title, or the app going to the
//   background pause the countdown; it resumes with the time that was left.
// - Leaving the page (starting playback, the stream picker, Back) ends it for the visit.
// - When it runs out mid-scroll, the trailer waits for the rows to settle (max ~1 s).

private const val TICK_MS = 100L
private const val SCROLL_SETTLE_MAX_MS = 1_000L

class NuvioCTrailerAutostart internal constructor(
    private val scope: CoroutineScope,
    private val delayMs: () -> Long,
    /** Starts the background trailer. Returns false if it can't any more (no link, already played). */
    private val start: () -> Boolean
) {
    enum class Phase { IDLE, COUNTING, DONE }

    var phase = Phase.IDLE
        private set
    private var remainingMs = 0L
    private var job: Job? = null
    private var overlayOpen = false
    private var childPageOpen = false
    private var appAway = false
    private val scrolling = MutableStateFlow(false)

    /** Trailer link is ready on this page: start the countdown, once per visit. */
    fun arm() {
        if (phase != Phase.IDLE) return
        phase = Phase.COUNTING
        remainingMs = delayMs().coerceAtLeast(0L)
        reschedule()
    }

    fun setOverlayOpen(open: Boolean) { if (overlayOpen != open) { overlayOpen = open; reschedule() } }
    fun setChildPageOpen(open: Boolean) { if (childPageOpen != open) { childPageOpen = open; reschedule() } }
    fun setAppAway(away: Boolean) { if (appAway != away) { appAway = away; reschedule() } }
    fun setScrolling(active: Boolean) { scrolling.value = active }

    /** Left the details page: no more autostart this visit. */
    fun finish() {
        job?.cancel()
        job = null
        phase = Phase.DONE
    }

    private fun reschedule() {
        job?.cancel()
        job = null
        if (phase != Phase.COUNTING || overlayOpen || childPageOpen || appAway) return
        job = scope.launch {
            // Count down in small steps so a pause keeps the time that was left.
            while (remainingMs > 0) {
                val step = minOf(TICK_MS, remainingMs)
                delay(step)
                remainingMs -= step
            }
            withTimeoutOrNull(SCROLL_SETTLE_MAX_MS) { scrolling.first { !it } }
            phase = Phase.DONE
            job = null
            start()
        }
    }
}

/** Screen-level inputs: cast page / another title open on top, app sent to the background, page left. */
@Composable
internal fun NuvioCTrailerAutostartEffect(autostart: NuvioCTrailerAutostart, childPageOpen: Boolean) {
    LaunchedEffect(autostart, childPageOpen) { autostart.setChildPageOpen(childPageOpen) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(autostart, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> autostart.setAppAway(true)
                Lifecycle.Event.ON_RESUME -> autostart.setAppAway(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            autostart.finish() // the page itself left composition: playback, stream picker or Back
        }
    }
}

/** Content-level inputs: in-page overlays and row scrolling. */
@Composable
internal fun NuvioCTrailerAutostartInputs(trailer: NuvioCTrailerUi, overlayOpen: Boolean, scrolling: Boolean) {
    val autostart = trailer.autostart ?: return
    LaunchedEffect(autostart, overlayOpen) { autostart.setOverlayOpen(overlayOpen) }
    LaunchedEffect(autostart, scrolling) { autostart.setScrolling(scrolling) }
}
