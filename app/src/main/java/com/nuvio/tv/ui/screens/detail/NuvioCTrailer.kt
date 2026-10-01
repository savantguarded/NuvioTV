package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C background-trailer layer. Everything the fork adds on top of upstream
// PR #3730 that doesn't have to live inside an upstream file sits here, so official edits
// to the detail page rarely touch it.

/** Detail-page trailer state the fork threads from the screen down to the hero and backdrop. */
data class NuvioCTrailerUi(
    /** "Background trailers" is on and a trailer is playing: keep the fill-the-screen framing. */
    val keepFraming: Boolean = false,
    /** Muted background trailer has had no remote input for a while: fade the page text. */
    val idle: Boolean = false
)

internal val MetaDetailsUiState.nuvioCTrailerUi: NuvioCTrailerUi
    get() = NuvioCTrailerUi(
        keepFraming = backgroundTrailerEnabled && isTrailerPlaying,
        idle = nuvioCBackgroundIdle && isBackgroundTrailerPlaying
    )

/** Seconds of no remote input before the page text fades behind a muted background trailer. */
internal const val NUVIO_C_BACKGROUND_IDLE_MS = 6_000L
