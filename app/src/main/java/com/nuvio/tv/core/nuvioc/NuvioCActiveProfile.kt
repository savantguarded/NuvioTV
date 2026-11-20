package com.nuvio.tv.core.nuvioc

// [fork] Nuvio C round 56 profile_local_history: the active Nuvio profile for small stores that aren't injected
// (Roulette's seen list, the post-play rating marker). Set by ProfileManager.
object NuvioCActiveProfile {
    @Volatile var id: () -> Int = { 1 }
}
