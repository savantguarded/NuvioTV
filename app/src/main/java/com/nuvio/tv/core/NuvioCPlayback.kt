package com.nuvio.tv.core

import java.util.concurrent.atomic.AtomicInteger

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

    fun onPlayerOpened() {
        open.incrementAndGet()
    }

    fun onPlayerClosed() {
        if (open.decrementAndGet() < 0) open.set(0)
    }
}
