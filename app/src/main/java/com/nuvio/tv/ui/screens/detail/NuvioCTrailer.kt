package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C background-trailer layer. Everything the fork adds on top of upstream
// PR #3730 that doesn't have to live inside an upstream file sits here, so official edits
// to the detail page rarely touch it.

/** Detail-page trailer state the fork threads from the screen down to the hero and backdrop. */
data class NuvioCTrailerUi(
    /** "Background trailers" is on: keep the fill-the-screen framing and fade the logo, never pop it. */
    val featureOn: Boolean = false,
    /** A trailer is playing with the feature on: keep the framing when the trailer button adds sound. */
    val keepFraming: Boolean = false,
    /** An in-page overlay (comments, synopsis, dialogs) covers the page: pause the background trailer. */
    val overlayOpen: Boolean = false
)

internal val MetaDetailsUiState.nuvioCTrailerUi: NuvioCTrailerUi
    get() = NuvioCTrailerUi(
        featureOn = backgroundTrailerEnabled,
        keepFraming = backgroundTrailerEnabled && isTrailerPlaying,
        overlayOpen = showListPicker || removalConfirmations.isNotEmpty()
    )
