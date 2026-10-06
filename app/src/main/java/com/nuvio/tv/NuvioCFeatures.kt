package com.nuvio.tv

// [fork] Nuvio C feature switches: one per tweak. `false` makes that part of the app behave
// exactly like official Nuvio (the fork's code stays in place but is never used).
//
// Switch one off without touching code: on GitHub, Settings > Secrets and variables > Actions >
// Variables, set NUVIO_C_OFF to a comma list of the names below (e.g. "osd_badges,subtitle_lift"),
// then run the Nuvio C build. The build robot flips those lines to false before compiling and the
// release notes list what was switched off. Editing a line here to `false` works too.
//
// To remove a tweak's code completely instead, revert its commits (see README / nuvio-c-setup).
object NuvioCFeatures {
    /** Player: title logo top-left of the controls. */
    const val OSD_LOGO = true
    /** Player: resolution / HDR / audio / size badges bottom-right of the controls. */
    const val OSD_BADGES = true
    /** Player: subtitles move up above the controls while they are open. */
    const val SUBTITLE_LIFT = true
    /** Player: after a panel (subtitles, audio, sources…) closes, focus returns to its button. */
    const val FOCUS_RETURN = true
    /** Player: thinner YouTube-style seek bar and taller bottom shadow. */
    const val SEEK_BAR_STYLE = true
    /** Player: no year under series titles (movies keep it). */
    const val SERIES_NO_YEAR = true
    /** Player: "via <source>" line hidden while paused. */
    const val HIDE_VIA = true
    /** Player: year filled in for titles opened from Continue Watching. */
    const val YEAR_BACKFILL = true
    /** Simkl: AUTH V2 device login with automatic token renewal (off = official PIN login). */
    const val SIMKL_V2 = true
    /** Details page: Nuvio C layer on official "Play in Background" trailers (off = official as shipped). */
    const val BACKGROUND_TRAILERS = true
    /** Details page: "Play Trailer Muted" setting for background trailers (off = always muted). */
    const val BG_TRAILER_SOUND_TOGGLE = true
    /** Details page: one Back stops the background trailer and leaves the page (off = two presses). */
    const val BG_TRAILER_BACK_EXITS = true
    /** Trailer screen: player-style seek bar. */
    const val TRAILER_SCREEN_BAR = true
    /** Trailer screen: small title logo bottom-left while the trailer plays with sound. */
    const val TRAILER_SCREEN_LOGO = true
    /** Trailers: IMDb backup when YouTube rate-limits. */
    const val IMDB_TRAILER_BACKUP = true
    /** Player: subtitles and controls at 60% brightness while HDR / HLG / Dolby Vision plays. */
    const val HDR_DIM = true
    /** Player: add-on / provider line and the release filename at the bottom of the loading screen. */
    const val LOADING_FILENAME = true
    /** Player: a "stream" that is really a short error clip is paused and the Sources panel opens. */
    const val PLACEHOLDER_CHECK = true
    /** Player: pressing Next episode skips the 3-second countdown (auto-play keeps it). */
    const val NEXT_EP_NO_COUNTDOWN = true
    /** Player: controls stay up 8 s after opening (3 s after a seek). Off = 3 s always. */
    const val OSD_TIMEOUT = true
    /** Player: Start over button right after Play/Pause. */
    const val START_OVER = true
    /** Images: bigger poster/backdrop disk cache and a 1-week default for images sent without cache rules. */
    const val IMAGE_CACHE = true
    /** Cast page: real filmography (no talk shows / "Self" credits) with the role or job under each title. */
    const val CAST_ACTING_ONLY = true
}
