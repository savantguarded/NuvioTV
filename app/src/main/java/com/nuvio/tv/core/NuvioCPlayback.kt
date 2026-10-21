package com.nuvio.tv.core

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// [fork] Nuvio C round 14 (2026-10-21): whether the full-screen player is open. Set by PlayerViewModel
// (init / onCleared). Background work that competes with 4K playback for the TV's 2 GB checks it:
//  - the IMDb trailer backup stays off while a film plays (`imdb_no_browser`; the post-play prefetch
//    asks for trailers at ~80% of an episode, see NuvioCTrailerBackup.kt),
//  - the shared trailer player isn't rebuilt behind the film (`trailer_pool_guard`),
//  - the image memory cache is trimmed while the film plays (`player_memory_trim`).
// A counter, not a flag, so a player opened on top of another (next episode) is handled.
object NuvioCPlayback {
    private val open = AtomicInteger(0)

    val active: Boolean
        get() = open.get() > 0

    private val _state = MutableStateFlow(false)

    /** [active] as a flow (round 15, library_calendar: reminders wait while something plays). */
    val state: StateFlow<Boolean> = _state.asStateFlow()

    fun onPlayerOpened() {
        open.incrementAndGet()
        _state.value = true
    }

    fun onPlayerClosed() {
        if (open.decrementAndGet() < 0) open.set(0)
        _state.value = open.get() > 0
    }
}
