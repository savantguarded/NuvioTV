package com.nuvio.tv.ui.screens.player

// [fork] Playback log helper (round 43 dv_log_more): whether this start used official's Custom Network
// (parallel range) source. Read-only; the header-first resume experiment (round 42) was removed.

internal object NuvioCDvResume {
    /** [fork] round 43 dv_log_more: whether this start used official's Custom Network (parallel range) source, for the playback log. */
    @Volatile var lastParallel: Boolean? = null
}
