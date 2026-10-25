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
    /** Home hero carousel (Featured, Classic, Grid): 18 titles from your hero catalogs instead of official's 7, a new random pick and order on every app launch, mixed across catalogs (official took the same 7 by a fixed hash, so it showed the same few all day). Order stays put within a run. */
    const val HERO_POOL_SHUFFLE = true
    /** Featured home: Left from Play on the first title goes to the last (Right from the arrow already wraps to the first), so the carousel loops both ways. The sidebar is reached with Back. Off = Left on the first title goes to the sidebar (official; Right on the last title still wraps to the first). Off since round 17 (Charles: Left at the edge should open the sidebar). */
    const val FEATURED_WRAP = false
    /** Featured home: the carousel only moves forward by hand. Left from Play opens the sidebar on every title (no stepping back); Right from the arrow still goes to the next title and loops from the last to the first. Off = Left on Play steps back a title (except on the first). */
    const val FEATURED_RIGHT_ONLY = true
    /** Details background trailer: no stutter while moving around as it starts. The black-bar check (official, a GPU read-back on the UI thread, dearer on HD trailers) waits while a key was pressed in the last 0.9 s and stops after 10 clean frames with no bars. Off = official sampling. (Hero dim, backdrop crossfade and scroll state now redraw at draw time in both cases.) */
    const val BG_TRAILER_SMOOTH = true
    /** Details page: moving between sections (hero, seasons / episodes, cast and the rows below) no longer rebuilds the whole page. Official rebuilt it on every episode focus, every change of D-pad direction and when the hero scrolled away; Up into the hero / back to the episodes now uses the page's normal scroll motion. Off = official. */
    const val DETAIL_SECTION_SMOOTH = true
    /** Pill buttons (details Play / Resume, Featured Play and Details): on focus they grow by the same 2.4 dp per side as the round buttons, so the gap to the next button stays even (~8 dp). Official 1.1x zoom made a wide pill swallow the gap. */
    const val PILL_FOCUS_GROWTH = true
    /** Continue Watching: the shuffle badge on a shuffled series uses the Nuvio C line shuffle icon (same as the details Shuffle button). Off = official Material icon. */
    const val CW_SHUFFLE_ICON = true
    /** App font on every text style: the ambient screen title, stream screen headings, collection folders and some dialogs used the system font (official left two text styles out of the font setting). Off = official. */
    const val FONT_ALL_STYLES = true
    /** Featured carousel: "#3 in Top 20" line above the logo (titles from a top / trending / popular hero catalog, ranks 1-20, top-10 tile for 1-10) and a NEW / NEW SEASON chip (aired in the last 60 days). From catalogs and TMDB data already loaded, no extra requests. */
    const val FEATURED_BADGES = true
    /** Featured carousel: a title opened with Details (or Play without Continue Watching) shows its own backdrop while the details page loads. Before, the page opened on the first Continue Watching card's picture (Modern's hidden hero) and then switched. */
    const val FEATURED_SEED = true
    /** Hero carousel timer (Featured, Classic, Grid): a key press restarts the 10 s wait without redrawing the carousel (it counted presses in screen state, so every press rebuilt the carousel). Off = previous counter. */
    const val IDLE_TIMER_LIGHT = true
    /** Featured home: while you browse the rows the hidden carousel is not kept built, so Home updates (ratings, Continue Watching, hero enrichment) no longer rebuild it each time. Up / sidebar Home builds it again before it slides down. Off = carousel always built. */
    const val FEATURED_ROWS_LIGHT = true
    /** Player: official's subtitle lift (Skip Intro, next-episode card, seek-only bar) no longer rebuilds the whole player screen each time it moves; only the Nuvio C lift reacts, and mpv is re-told only on 2 px steps. Off = previous wiring. */
    const val PLAYER_LIFT_LIGHT = true
    /** Retired in round 14 (the IMDb backup no longer uses a hidden browser). Kept so an old NUVIO_C_OFF list still builds; does nothing. */
    const val IMDB_QUIET_START = true

    // ---- Round 14 (2026-10-21): stability and smoothness, measured over adb on the TCL C655 ----
    /** IMDb trailer backup without a hidden browser: one request to IMDb's own data service. The browser's renderer was killed for memory during 4K playback and took the app with it (the crash at ~80% of an episode). Also: no IMDb lookups at all while the player is open. Off = no-op (the browser code is gone). */
    const val IMDB_NO_BROWSER = true
    /** Player open: the in-memory picture cache is cut to a quarter (refills as you browse). Frees memory for 4K playback on the 2 GB TV. */
    const val PLAYER_MEMORY_TRIM = true
    /** The shared trailer player is not rebuilt behind the full-screen film (only post-play, which asks explicitly). */
    const val TRAILER_POOL_GUARD = true
    /** HDR dim, same coverage, lighter method: subtitle text / background / outline in dimmed colours, and one "multiply" pass over each dimmed element instead of an offscreen copy (GPU time per frame had doubled against official). Off = previous layers. */
    const val HDR_DIM_LIGHT = true
    /** Subtitle lift: bottom subtitles move above the OSD in one step when it opens (and back when it closes) instead of a 200 ms slide that redrew the whole subtitle area twice every frame. */
    const val OSD_LIFT_SNAP = true
    /** OSD focus label (the button name under the focused button): only the focused button carries the text measurer, timer and draw step; the other buttons add nothing. */
    const val OSD_FOCUS_LABEL_SHARED = true
    /** Featured carousel only: synopsis up to six lines (was five), same width. The text block can't rise above a 40 dp top margin; when a tall logo, tagline, rank line and ratings leave less room, the synopsis drops lines (never under three) instead of anything being cut off. */
    const val FEATURED_SYNOPSIS_SIX = true
    /** Player OSD: the Watch Party button sits in the hidden More row like speed and aspect ratio; while a party is running it shows in the main row (highlight colour) so you can see it and leave quickly. */
    const val PARTY_IN_MORE = true
    /** Featured home: sidebar Home from deep in the rows brings the carousel back the same way Up from the first row does, so the Continue Watching row in the peek strip isn't left in its enlarged look with its title cut off. */
    const val FEATURED_HOME_CLEAN = true
    /** Source picker (source screen, player Sources and Episodes panels): with only one source the "All" tab is hidden; Refresh and the source's tab stay. A second source brings "All" back. */
    const val SINGLE_SOURCE_NO_ALL = true
    /** Trakt: a failed token refresh only removes the login when Trakt clearly says it's dead (invalid_grant and similar); a 403, server error or rate limit keeps it and retries later. The reason for the last disconnect shows under Trakt in Settings > Tracking (and in logcat, tag NuvioCTrakt). Official wiped the login on any 400 / 401 / 403. */
    const val TRAKT_KEEP_LOGIN = true
    /** Featured home: sidebar Home also makes Continue Watching (first row, first card) the remembered spot, so the carousel's peek shows Continue Watching instead of the catalog you came from. */
    const val FEATURED_HOME_TOP = true
    /** Featured carousel: at most three MDBList ratings (first three in your rating order), like Modern's hero. */
    const val FEATURED_TOP3_RATINGS = true
    /** Featured carousel: five-dot page indicator that slides along (edge dots smaller) instead of one dash per title. */
    const val FEATURED_DOTS_FIVE = true
    /** Calendar (ported from ysosrs123's fork): follows the titles in your library, fetches episode and release dates (Trakt, TVmaze, TMDB) and fills the Upcoming row ("Calendar in Upcoming"). Off = none of the calendar runs. */
    const val LIBRARY_CALENDAR = true
    /** Calendar page in the sidebar (needs LIBRARY_CALENDAR). */
    const val CALENDAR_PAGE = true
    /** Calendar bells: per-title reminders shown as a card on Home and a corner message (needs LIBRARY_CALENDAR). */
    const val CALENDAR_BELLS = true
    /** Upcoming row cards from the Calendar read like official's: Today/Tonight with the time, Tomorrow, In 2 Days to In 7 Days, then the date. Off = ysosrs123's weekday names. */
    const val CALENDAR_IN_DAYS = true
    /** Calendar "Start in" setting (Settings > Layout > Calendar): Last used (default), List, Week or Month. Off = always Week, as in ysosrs123's fork. */
    const val CALENDAR_START_VIEW = true
    /** Calendar: Back from any card jumps to the List / Week / Month buttons (Back again leaves), Up onto the buttons always lands on the current view's button, Down from them returns to the card you left. Off = ysosrs123's focus. */
    const val CALENDAR_TAB_FOCUS = true
    /** Calendar Week view: no star rating in the info block above the days (genre, network, runtime stay). Since round 18 also covers the List view's info panel. */
    const val CALENDAR_WEEK_NO_RATING = true
    /** Details page plus button: click opens the library list picker, press-and-hold does the one-step add/remove to your default library (official: the other way round). */
    const val DETAIL_PLUS_PICKER = true
    /** English only: the APK leaves out the app's and its libraries' other languages (localeFilters "en" in app/build.gradle.kts, which reads this line). Smaller APK; no effect on speed or memory. */
    const val ENGLISH_ONLY = true
    /** In-app updater: Nuvio C opens itself again once an update you confirmed is installed (only after in-app updates, within 15 min). Since beta.8 the install itself is official's (APK + its embedded compile profile in one session); automatic idle installs never reopen the app. */
    const val UPDATE_REOPEN = true

    // ---- Round 18 (2026-10-21) ----
    /** Library list picker (details plus button, Home and poster long-press): the ticks sit in one column at the right edge, whatever the list name's length (a fixed slot, kept even on unticked rows). Off = round-12 layout. */
    const val LIBRARY_PICKER_TICKS = true
    /** Calendar reminder popup (one title): Watch now / Open show / Later take the width their text needs, in a full-width row under the poster and text, so no label is cut off. The several-titles popup's Show / Film button gets a minimum width too. Off = ysosrs123's layout. */
    const val CALENDAR_REMINDER_BUTTONS = true

    // ---- Round 19 (2026-10-22) ----
    /** Featured carousel on a cold start: Home's rows already sit in their place under the carousel, the carousel fades in once (about 0.3 s) when its first picture is ready (never waits more than 1.2 s), and its ratings line keeps its height until MDBList answers. Off = the carousel snaps in over the rows and its pictures pop in. */
    const val FEATURED_COLD_START = true
    /** Modern sidebar closing: starts at once (no 95 ms wait), one fade on one curve over 220 ms, the blur fades with the panel instead of switching off on the first frame, and the small pill fades in on the same curve. Opening is unchanged. Off = official. */
    const val SIDEBAR_CLOSE_SMOOTH = true
    /** Rate dialog: five stars with half steps (one Right press = half a star = one point, 1 to 10 as before) in a single focus stop, with "8/10 · Great" under it. Off = the round-12 row of ten numbered circles. */
    const val RATING_STARS = true
    /** Rate dialog: "Sync to Simkl" for movies and shows (Simkl stores your 1-10 score; a title not yet on your Simkl list is added to it). The details page star also lights up for titles you rated on Simkl. */
    const val RATING_SIMKL = true
    /** Calendar: only dated releases. List view drops the "No date yet" group (returning / in production shows), and List, Week and Month drop cinema-only film rows (films show from their digital release). Off = ysosrs123's lists. */
    const val CALENDAR_DATED_ONLY = true
    /** Search: the empty screen says "Enter at least 2 characters" without "Discover is disabled." in front. */
    const val SEARCH_NO_DISCOVER_COPY = true
    /** Nuvio C's own settings (the toggles and choices in its settings screens) travel with your Nuvio account's settings, so a new TV signed in to the same profile gets them. Stored as one extra entry inside official's settings sync; the TV's own copy always wins when the cloud has none. */
    const val NUVIO_C_CLOUD_SETTINGS = true

    // ---- Round 20 (2026-10-22, on official 1.1.0-beta.8) ----
    /** Rate dialog: tracker rows 48 dp (was the settings row's 62 dp, which left the label and switch sitting high) and tighter gaps, so three trackers, Remove rating and Save fit on a 1080p TV; the tracker list scrolls if it ever grows. Off = round-19 layout. */
    const val RATE_DIALOG_FIT = true
    /** Modern sidebar: a page picked from the sidebar (Search, Discover, Library, Calendar, Settings) is built once the sidebar has closed (about 0.24 s), then fades in over 0.15 s, instead of being built during the close and its blur. Home and Back are untouched. Off = official timing. */
    const val SIDEBAR_PAGE_SMOOTH = true
    /** Official's "HDR / Dolby Vision subtitle style" (beta.8) is hidden from Settings and never applied, so subtitles are only ever dimmed once, by Nuvio C's HDR dim. Off = official's setting is back. */
    const val NO_OFFICIAL_HDR_SUB_STYLE = true

    // ---- Round 21 (2026-10-23) ----
    /** Settings > Layout > Card width gets "Extra large" and "Huge" after Large. Home only, each layout its own size (see NuvioCCardSize); Modern / Featured rows tighten at those sizes so the next row still peeks. Library, Discover and folders stay at Large. Off = the two presets are gone and a saved 156 / 172 is drawn at its raw width like official. */
    const val CARD_SIZE_XL = true
    /** Modern / Featured Home: the hero text block (synopsis width) never grows past its width at the Large card size, so Extra large / Huge don't widen it. Smaller sizes are unchanged. */
    const val HERO_WIDTH_CAP = true
    /** Grid home layout hidden: gone from Settings > Layout and the first-run picker, and a profile saved as Grid opens as Classic (read only, never written back, so another TV on Grid keeps it). Grid's code stays in place untouched for clean official merges. Off = Grid is back. */
    const val NO_GRID_LAYOUT = true
    /** "Prefer IMDb Trailers" removed from Settings and never applied: trailers are YouTube first, IMDb only as the backup when YouTube rate-limits (imdb_trailer_backup). The stored choice and its cloud entry are left alone. Off = the toggle is back. */
    const val NO_PREFER_IMDB = true
    /** Profile screen opening (not Manage profiles): the background fades up from black while settling from 106% to 100%, and the profiles drop into place one after another, 60 ms apart (about 1 s in all, once per visit, after the background is known, max 0.6 s wait). Transform and fade only, nothing runs afterwards, keys work from the first frame. Off = official (no intro). */
    const val PROFILE_INTRO = true
    /** HDR dim fix: the full-screen player layers (audio and subtitle panels, media info, pause screen, buffering spinner) dim only their own pixels. Before, their dim pass also darkened whatever was under them, so the OSD fading out behind a just-opened panel changed brightness mid-fade (and subtitles were dimmed twice while one was open). Costs one layer while such a panel is on screen. Off = round-14 behaviour. */
    const val HDR_DIM_ISOLATED = true

    // ---- Round 23 (2026-10-23) ----
    /** Calendar: MDBList's own release calendar (Settings > Layout > Calendar > "Use my MDBList calendar", on by default) when signed in to MDBList. Its titles (from Preferences > Calendar on mdblist.com) are followed and its dates used; TVmaze still adds air times. Asked every 6 h and a few seconds after anything is added to or removed from a library. Off = ysosrs123's sources only. */
    const val CALENDAR_MDBLIST = true
    /** Calendar: a title saved in a library (or listed by MDBList's calendar) is never dropped for being removed from Continue Watching or for an old watch; over the 300-title cap, titles with something still ahead and saved ones go first. MDBList's saved data is loaded before it is read. Off = ysosrs123's rules. */
    const val CALENDAR_LIBRARY_WINS = true
    /** Calendar: one log block (tag CalendarRefresher, "followed N") naming every followed title and where it came from, written only when that list changes. Off = no block. */
    const val CALENDAR_ORIGIN_LOG = true
    /** Upcoming row: only releases still to come; an episode or film that is out leaves the row (the Calendar's Just aired still lists it). Off = ysosrs123's "New" cards for the last 7 days first. */
    const val UPCOMING_FUTURE_ONLY = true
    /** Calendar press-and-hold menu (List and Week views): the same buttons as Home's poster menu. Off = ysosrs123's pill rows. */
    const val CALENDAR_OFFICIAL_DIALOG = true

    // ---- Round 24 (2026-10-23) ----
    /** Calendar: titles found done (films out, shows ended) are remembered 30 / 90 days even after they leave the 300 list, so they stop winning their slot back every refresh. Off = done is only known while stored (titles rotate over the cap). */
    const val CALENDAR_DONE_MEMORY = true
    /** Calendar: over the 300-title cap, titles with a known date (MDBList calendar, stored dates, Continue Watching's upcoming episode) come right after belled ones. Off = round 23 order. */
    const val CALENDAR_DATED_FIRST = true
    /** Calendar: saved films from two or more calendar years ago are left out unless dated or belled (no re-releases). Off = followed like any saved film. */
    const val CALENDAR_OLD_FILMS_OUT = true
    /** Calendar art: MDBList's event backdrop is kept; a title known only by TMDB id gets its IMDb id from TMDB before asking add-ons, so it gets a backdrop, logo and opens normally. Off = round 23 art. */
    const val CALENDAR_MDBLIST_ART = true
    /** Library: an item whose source gave no poster (MDBList) shows the add-on's cached poster, else the poster by IMDb id, else TMDB's by TMDB id. Off = empty card. */
    const val LIBRARY_POSTER_FALLBACK = true

    // ---- Round 25 (2026-10-23) ----
    /** Upcoming row: releases up to 14 days ahead only; later ones are in the Calendar. Off = no limit. */
    const val UPCOMING_14_DAYS = true
    /** Upcoming row: a show you are behind on in Continue Watching shows its newest episode on its release day only ("New" once out). Caught up = Continue Watching shows it and Upcoming does not. Off = shows with something in Continue Watching are left out. */
    const val UPCOMING_RELEASE_DAY_NEW = true
    /** MDBList calendar: titles listed only for a favourite cast/crew member or as popular releases are in the Calendar only, never the Upcoming row; watchlist, in-progress and completed shows win. Off = all in both. */
    const val CALENDAR_MDBLIST_EXTRA_CALENDAR_ONLY = true
    /** Featured carousel cold start: opens on a title whose backdrop is already saved on the TV and waits up to 4 s for its backdrop and logo before fading in (was 1.2 s, then the slide showed on black for a few seconds). Off = round 23 reveal. */
    const val FEATURED_COLD_START_WAIT = true

    // ---- Round 26 (2026-10-23) ----
    /** Calendar: a refresh button after Week / List / Month; asks every source again (Trakt and MDBList calendars at once, titles airing around now refetched). The icon turns while a refresh runs. Off = no button. */
    const val CALENDAR_REFRESH_BUTTON = true
    /** Calendar: Back in Week after OK on a Month day goes straight back to Month on that day (only then). Off = Back goes to the view buttons. */
    const val CALENDAR_MONTH_BACK = true
    /** Calendar Week: Up / Down (and Month -> Week) always land on the day's first card; the day's row is scrolled back to its start first. Off = ysosrs123's focus (could restore a later card). */
    const val CALENDAR_WEEK_FIRST_CARD = true
    /** Calendar Week: 16 dp room around the rows and scrolling that keeps it, so the focus ring of the first and last cards is never cut; about 4.3 cards across so the next card peeks. Off = 8 dp, 4 across, cards scrolled flush to the edge. */
    const val CALENDAR_WEEK_EDGES = true
    /** Calendar: held or fast Left stops on the first card of a Week row, the week strip and the view buttons; one more press opens the sidebar. Off = Left runs straight into the sidebar. */
    const val CALENDAR_EDGE_BRAKE = true
    /** Calendar Month: Up / Down stay in the month (Up from the top row goes to the month arrows, then the view buttons); Left / Right wrap rows, only Left on the 1st or Right on the last day turns the month (held keys stop there); Right on the last column still goes to the day's list. Off = ysosrs123's moves. */
    const val CALENDAR_MONTH_NAV = true
    /** Library: changing the list (or genre, year, watched filter) goes to the top and focuses the first poster, as a sort change does. Off = focus goes back to a poster remembered from the previous list. */
    const val LIBRARY_LIST_FIRST = true

    // ---- Round 26b (2026-10-24): smooth start-up ----
    /** Start-up screen: the spinner (drawn by the app, so it froze whenever the app was busy) is replaced by a slim light sweep under the Nuvio logo, animated by Android's render thread so it keeps moving while the app works. Off = official spinner. */
    const val STARTUP_SWEEP = true
    /** Start-up screen stays until Home has settled (its first rows built and the app answering every frame for a moment), capped at 10 s after Home is ready, then fades out once. Remote keys are held while it shows. Off = it leaves as soon as Home is ready (official). */
    const val STARTUP_SETTLE = true
    /** No Android TV launcher channels: Nuvio C no longer publishes Continue Watching to the launcher, and no longer wakes every 15 minutes or at boot to refresh them (Projectivy doesn't show them). Off = official channels. */
    const val NO_TV_CHANNELS = true
    /** With STARTUP_SETTLE: on a cold start the start-up screen also waits for the account's start-up sync (full watched history and progress) to finish, so Home appears with nothing left to freeze it and Continue Watching current; capped at 15 s after Home is ready. Warm starts only fetch changes, so they barely wait. Off = settle on Home alone (10 s cap). */
    const val STARTUP_SETTLE_SYNC = true
    /** Start-up screen look: the logo drawn smoothly (mipmapped, no rough edges) over a soft halo that slowly breathes, animated by the render thread; replaces the sweep. Off = the sweep (STARTUP_SWEEP). */
    const val STARTUP_BREATHE = true
    /** No crash reporting to the Nuvio team (Nuvio C crashes aren't theirs to fix; we read them over adb). Saves a blocking read at launch. Off = official setting decides. */
    const val NO_CRASH_REPORTING = true
    // Also round 26b, not a switch: release builds drop debug-level logging (proguard-rules.pro, "[fork] round 26b").
    // About 1,500 debug lines were written in the first 25 s of each launch; info, warning and error logs stay.

    // ---- Round 27 (2026-10-25) ----
    /** OSD badges: the Dolby Vision tile reads DOLBY VISION and Atmos reads DOLBY ATMOS (the tags behind them stay short for the HDR dim and badge rules); an IMAX tile when the release name says IMAX. Off = DV / ATMOS, no IMAX tile. */
    const val OSD_BADGE_NAMES = true
    /** Details page: the focused round button (library, watched, rate, trailer, shuffle) shows its name just above it for 2.5 s, like the player's buttons; the logo / tagline sit 7 dp higher to make room, nothing below moves. Off = no names. */
    const val DETAIL_FOCUS_LABEL = true
    /** Source picker: with one source (the "All" tab hidden), Up from the first stream and Down from Close land on that source's tab, so the tabs and Refresh can be reached again. Off = round 14 behaviour (focus went nowhere). */
    const val SINGLE_SOURCE_CHIP_FOCUS = true
    /** Library: Down from the list pickers or the Manage lists / Sync row always lands on the first poster (official picked whichever poster sat below the control). */
    const val LIBRARY_DOWN_FIRST = true
    /** Library: a Random pick button after Sync; it picks a title from what the page shows (list, type and filters), avoiding the last few picks, with Pick again and Open. Off = no button. */
    const val LIBRARY_RANDOM = true
    /** Calendar Week: the logo / title, meta line and summary at the top keep fixed heights, so the poster rows stay on one line whatever title is focused. Off = heights follow the text. */
    const val CALENDAR_WEEK_FIXED_HERO = true
    /** Profile screen: avatars and their rings 10% smaller (86 dp, 92 focused); names, gaps and layout unchanged. Off = 96 / 102 dp. */
    const val PROFILE_AVATAR_SMALL = true
    /** Ten more app fonts (Manrope, Figtree, Noto Sans, Nunito Sans, Public Sans, Schibsted Grotesk, Montserrat, Archivo, Familjen Grotesk, Bricolage Grotesque); the first five also as subtitle fonts. mpv gets only the chosen subtitle font. Off = round 3 font list. */
    const val MORE_FONTS = true
    /** Start-up: the profile screen dissolves into the start-up screen (300 ms) and Home fades in while settling from 104% to 100% (500 ms). Off = hard cut and plain 400 ms fade (official). */
    const val STARTUP_SETTLE_TRANSITION = true
    /** Start-up screen: just the still Nuvio logo, nothing animated (no halo, sweep or loader), so nothing can stutter while the app loads. Off = round 26c breathing halo. */
    const val STARTUP_STATIC = true
    /** Start-up: when the last launch went to the profile screen, the app opens straight onto it (black while the saved profiles load, at most 2 s), with no start-up screen flashing first; the start-up screen comes after the pick. Off = official (start-up screen first). */
    const val STARTUP_TO_PROFILES = true
    /** Home: the first 8 catalogs load in the order the rows appear (official: the first 4 in addon order), then the rest load quietly in the background, one at a time, never ahead of a row you're looking at; Classic looks 3 rows ahead. Idea from ysosrs123's fork. Off = official lazy loading. */
    const val HOME_CATALOG_SWEEP = true
    /** Stream badges: once start-up has settled (and whenever the pack changes) every imported badge logo is fetched and decoded once, so the sources list shows them all at once and nothing downloads during playback; badges use the app's TV-tuned connection (16 at a time, IPv4 first) and a failed one is tried again instead of staying blank. Off = official on-demand loading. */
    const val BADGE_PREFETCH = true
}
