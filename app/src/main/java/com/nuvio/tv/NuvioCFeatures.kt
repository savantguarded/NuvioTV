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
    /** Details page: no background-trailer countdown for titles you've started or watched. */
    const val BG_TRAILER_SKIP_WATCHED = true
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
    /** Player: the next episode starts without the 3-second countdown (Next press and auto-play). */
    const val NEXT_EP_NO_COUNTDOWN = true
    /** Player: controls stay up 8 s after opening (3 s after a seek). Off = 3 s always. */
    const val OSD_TIMEOUT = true
    /** Player: Start over button right after Play/Pause. */
    const val START_OVER = true
    /** Cast page: real filmography (no talk shows / "Self" credits) with the role or job under each title. */
    const val CAST_ACTING_ONLY = true
    /** Cast page: Back past the first filmography title jumps to the first one (like official home rows). */
    const val CAST_BACK_TO_FIRST = true
    /** Player: focus returns to the player when the focused Skip Intro button hides (official loses it). */
    const val SKIP_INTRO_FOCUS = true
    /** Details page: Shuffle / random episode is a round icon button next to Play, same size, colour and focus as Watched / Rate / Trailer (white with a stop icon while shuffle is on). */
    const val SHUFFLE_BUTTON_SIZE = true
    /** Profile screen: Netflix-style (wordmark top-left, round profiles down the left, pencil for the menu). Manage profiles stays official. */
    const val PROFILE_MINIMAL = false
    /** Player: HDR read from the video's own header (and mpv's gamma) too, so HDR titles whose file/name don't say so still get the badge and HDR dim. */
    const val HDR_DECODED = true
    /** Details page: the hero keeps the MDBList ratings line's space while it loads (no jump when it lands). */
    const val HERO_RATINGS_SPACE = true
    /** Player: Skip Intro and the next-episode card sit 16 dp above the seek bar while the controls are open (never overlap or clip). */
    const val SKIP_ABOVE_SEEK = true
    /** Player: playback buttons left, subtitle / audio / sources buttons right, time under the seek bar. Off since 2026-10-11 (official row). */
    const val OSD_SPLIT = false
    /** Player: the focused control's name (and current subtitle / audio track) shown small under it. */
    const val OSD_FOCUS_LABEL = true
    /** Appearance: 12 extra app fonts in the font picker (Google Sans Flex, Manrope, Figtree, Geist…). Off = official three. */
    const val EXTRA_FONTS = true
    /** Subtitles: "Prefer SDH subtitles" setting (embedded tracks only, off until you turn it on). */
    const val PREFER_SDH = true
    /** Details page: Creator and Cast / Ratings / More like this tabs drawn as season-tab pills. */
    const val DETAIL_TABS_PILLS = true
    /** Details page: Rate button (1-10) sent to Trakt and / or MDBList, one way, remembered on this TV. */
    const val DETAIL_RATING = true
    /** Library: read-only "Watched" tab for Trakt and MDBList (newest first, Movies / Series filter); Simkl's Completed tab is named Watched. */
    const val LIBRARY_WATCHED = true
    /** Player: "Switch player engine" sits in the More row (the hidden buttons) instead of the main row. */
    const val ENGINE_IN_MORE = true
    /** Subtitles: "Font" picker (player subtitle style panel and Settings) with the app's extra fonts. Off = official default font. */
    const val SUBTITLE_FONT = true
    /** Profile screen: official layout without "Who's watching?", the hold hint, star and Primary text; Add Profile as a button under the profiles. Manage profiles stays official. */
    const val PROFILE_CLEAN = true
    /** Home and collection folders: small clock top-right (like Plex). Not in the player (it has its own). */
    const val BROWSE_CLOCK = true
    /** App font everywhere: the player's Episodes and Sources panels (and other Material 3 text) follow Settings > Appearance > Font. Off = those stay on the system font. */
    const val FONT_EVERYWHERE = true
    /** Lists (subtitle style, Episodes / Sources panels, file picker, settings): room so a focused item's ring and zoom are never cut at the list edge. */
    const val FOCUS_ROOM = true
    /** Home / collection folders (Modern) and details page: one hero spec (260 × 90 dp logo, 13 sp synopsis to half the screen, home 5 lines, darker home fade). */
    const val HERO_SPEC = true
    /** Details page: the last rows (network / production) scroll the page to its end in one normal move (official stops short, then nudges). */
    const val DETAIL_SCROLL_END = true
    /** Details page: a movie collection always gets its own row (official puts it in the tabs when there are 3 tabs or fewer). */
    const val COLLECTION_OWN_ROW = true
    /** Details page: a director (or writer) who also acts is listed under crew and in the cast (official drops the acting credit). */
    const val DIRECTOR_IN_CAST = true
    /** Focus ring always matches the focused item's shape: round subtitle colour swatches, add-on / catalog order buttons (incl. the disabled first / last arrows). */
    const val FOCUS_RING_SHAPE = true
    /** Clearlogos (Home / collections, details, player OSD) sized to the same visual weight, Apple TV style: wide logos wide and short, tall ones compact. */
    const val LOGO_WEIGHT = true
    /** 40 dp side margin on Home / collections (official 52) and the details page (official 48). */
    const val PAGE_MARGIN_40 = true
    /** Clearlogos (Home / collections, details, player OSD, trailer screen): black parts lifted to white so they show on the dark background (My Spy); dark coloured logos get a thin light outline instead. Bright coloured letters with a dark stroke or bevel (Spider-Man: Brand New Day) are left as they are. */
    const val LOGO_DARK_LIFT = true
    /** Details page: a background trailer ending leaves focus where it is (official sends it to Play and scrolls to the top). Full-screen trailers still return to Play. */
    const val BG_TRAILER_KEEP_FOCUS = true
    /** Details page: focus and layout stay put while scrolling (no jump to Play when the hero comes back into view or after a cast / filmography page, no synopsis slide when the logo or ratings land), and the last rows ease to the page end. */
    const val DETAIL_FOCUS_STABLE = true
    /** Home / collections: synopsis up to 5 lines with even line lengths; of the three widths (official, ours, ours + 80 dp) the one needing the fewest lines wins, so the logo keeps its height (a synopsis of 2 lines or less stays narrow); the logo keeps at least 48 dp. */
    const val HOME_SYNOPSIS_FIT = true
    /** Clearlogos: transparent borders trimmed when the logo loads, so sizing uses the visible logo (padded TMDB logos looked small). */
    const val LOGO_TRIM = true
    /** Player: Next / auto-play shows the episode name the details page had (your meta add-on), never a blank or the previous episode's. Round 7: when no details page left names (Continue Watching, app restarted) the player looks them up itself: your meta add-on by IMDb id, then TMDB, names only. */
    const val NEXT_EP_TITLE = true
    /** Player: the automatic subtitle pick (Prefer SDH included) carries to the next episode in the same session instead of the series' older saved pick; a manual pick still wins and is saved. */
    const val SDH_SESSION_PICK = true
    /** Add to library dialog (details, Home, poster menu): your Library source's lists first, no focus zoom so nothing clips at the top or bottom, a check at the end for selected lists. */
    const val LIBRARY_PICKER = true
    /** Player OSD: badges in a row under the title block (movies: "Title (2008)" with the year in the title), file size on a solid light tile. Off = badges bottom-right as before. */
    const val OSD_BADGES_LEFT = true
    /** Player: Start over button uses a simpler restart icon (one unbroken arc with its arrow head, Shuffle's line style). */
    const val START_OVER_ICON = true
    /** Player: Netflix-style pause screen replaces official's (same trigger and keys): screen darkened a little, "You're watching" + logo + episode line on the left, clock, slim progress line with "Paused · ends at". */
    const val PAUSE_SCREEN_C = true
    /** Details page: the TMDB tagline between the logo and Play (one line, two if long; hidden while a trailer plays). Needs TMDB enrichment on; add-on meta is untouched. */
    const val DETAIL_TAGLINE = true
    /** Collections: with TMDB Basic info off, TMDB-built collections take the hero description from your meta add-on (like the details page); logo, backdrop and the rest stay TMDB-enriched. */
    const val FOLDER_ADDON_DESCRIPTION = true
    /** Player: while the controls or the pause screen show, bottom subtitles jump to the top of the screen (under the logo and clock) instead of sitting just above the controls; one step, no slide. Needs subtitle_lift. Off since 2026-10-14 (clashed with the OSD logo; subtitle_lift alone keeps them 12 dp above the title block). */
    const val SUBTITLE_TOP = false
    /** Details: coming back from the sources / player to a title opened from More like this, a cast page or a company page keeps that page as it was (official throws it away and loads it again). */
    const val NESTED_DETAIL_KEEP = true
    /** Profile screen (with profile_clean): the profiles in one column on the left, names beside the avatars, same cards; the rotating background stays clear. Off = clean row in the middle. */
    const val PROFILE_LEFT = true
    /** Home hero carousel (Classic, Grid, Featured): changes slide after 10 s without a key press, also while it has focus (official only moved when unfocused, so it sat still at app start); keeps the same title when the list refreshes; stops while the app is in the background. */
    const val HERO_AUTOADVANCE = true
    /** Home: fourth layout "Featured" in Settings > Layout: a full-screen carousel (Play / Details) from your hero catalogs on top of Modern home; Down shows the rows, Up on the first row returns. Stored as Modern + a flag, so official installs on the account just show Modern. */
    const val HOME_FEATURED = true
    /** Featured home: Down focuses the first card of the first row at once (the 260 ms slide runs underneath, keys work during it). Off = focus waits for the 380 ms slide to end. */
    const val FEATURED_FAST_FOCUS = true
    /** Clearlogos (with logo_weight): square and tall logos get up to 25% more area and a taller height cap (1:1 logo: Home 140, details 92, OSD 68 dp tall; was 110 / 64 / 52), so they no longer look small next to wide ones. Wide logos unchanged. */
    const val LOGO_TALL_BOOST = true
    /** Collections (Follow home layout = Modern): MDBList ratings for a row land in one screen update instead of one per title (official redrew the whole folder 20-40 times per row move). */
    const val FOLDER_RATINGS_BATCH = true
    /** Home (Modern / Featured): MDBList ratings for the focused row and the next one land in one update instead of one per title, so moving between rows doesn't redraw Home dozens of times. */
    const val HOME_RATINGS_BATCH = true
    /** Details page rows (cast, more like this, collection, trailers, companies) and cast page filmography: holding Left / Right steps one card per 80 ms, like official Home and collections, so a fast scroll no longer stalls. */
    const val DETAIL_ROW_THROTTLE = true
}
