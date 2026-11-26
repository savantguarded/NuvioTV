# Nuvio C fork changes

How Nuvio C differs from official NuvioTV, grouped by area: what you see, the switches, the files.
Per-switch detail: every switch in `app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt` has a one-line comment. This file is the overview, not a copy of that list.
Paths below are relative to `app/src/main/java/com/nuvio/tv/` unless they start with `app/`, `.github/` or `scripts/`.

Base: official `1.2.0-beta.4` (`77cc0bb5`, merged round 66; every conflict decided by the owner, list in the merge commit). Fork footprint (round 59 vs beta.3): 404 files added, 175 official files edited, about 62,800 lines added; rounds 60-65 add 19 files, round 66 adds 2 Kotlin files. 498 switches, 1 off.
Check: `git diff --stat 77cc0bb5 HEAD` (swap in the new tag after each upstream merge).

## Build and distribution
- App "Nuvio C", package `com.nuvio.tv.c`, `armeabi-v7a` only, beside the official app. Set at build time in `.github/workflows/nuvio-c.yml`, not in source.
- Hourly CI replays the fork onto the official release with the highest version (not the newest date). Optional patches (trailer layer, IMDb backup, cast filmography) drop alone on conflict. Issue opened when upstream edits OSD files. Repeat conflict mails are quieted.
- In-app updater points at this repo, offers forced rebuilds and compiles on install: `app/src/full/java/com/nuvio/tv/updater/NuvioCUpdate*.kt`, `UPDATE_REOPEN`.
- English-only APK (`ENGLISH_ONLY`, `app/build.gradle.kts`), no crash reports to Nuvio (`NO_CRASH_REPORTING`), no launcher channels (`NO_TV_CHANNELS`), debug logs stripped in release (`app/proguard-rules.pro`), fork baseline profile `app/src/main/baselineProfiles/nuvio-c.txt`.

## Player controls (OSD)
- Two control styles via Settings "Player controls style" (`OSD_CLEAN`): **Default** (code id `nuvio_c`, official row plus fork tweaks) and **Modern** (code id `clean`, files `NuvioCOsdClean.kt` / `NuvioCCleanLayout.kt`: left pills, centred playback, right icon pill, More sheet, icons `res/drawable/nuvio_c_ic_clean_*`). Code and switches say "Clean"; screens say "Modern".
- Clearlogo top-left, badges (round 64 `OSD_BADGES_V64`: details-page order resolution in capitals / source / visual / sound / Dolby Vision / Atmos, then IMAX and the filled size; Dolby and DTS logos; no channels), focused-button label, Start over (then Previous episode), Next label with episode name, chapter double-press jump, 8 s timeout, YouTube-style seek bar, shades, Watch Party in More, button order setting.
- Cast / About / crew side panel (`OSD_CAST*`, `OSD_ABOUT*`, `OSD_CREW`), Auto-skip-intro switch, Skip Intro sits above the seek bar.
- Modern right icon group: glyphs 72% of the 32 dp button (was 60%); pill, button and focus circle unchanged (`CLEAN_ICONS_LARGER`).
- Modern left pills are text only, centred, 13 sp, at least 80 dp wide (`CLEAN_PILL_TEXT_ONLY`). The More sheet has a Sound section with every line of the old Sound tab: Audio delay, Amplification (PCM), Downmix centre mix level, Night mode, Persist between sessions; decode-only lines read Unavailable during passthrough (`MORE_SOUND`); the Modern Audio panel is tracks only (`AUDIO_PANEL_NO_TABS`).
- Files: `ui/screens/player/NuvioCOsd*.kt`, `NuvioCCleanLayout.kt`, `NuvioCFocusReturn.kt`, `NuvioCOsdTimeout.kt`, `NuvioCPreviousEpisode.kt`, hooks in `PlayerScreen.kt` (`PlayerControlsOverlay`). Settings: `ui/screens/settings/NuvioCOsdButtonsSettings.kt`.

## Subtitles
- Lift above the controls (bottom subtitles only), drawn renderer kept, SDH preference and hide, subtitle font picker, Outline width row, plain language names, one safety re-check after start.
- Modern Subtitles panel: one list, no tabs, every row two lines (source pill; language, with SDH / Forced / CC on built-in tracks) (`SUB_PANEL_NO_TABS`). Subtitle delay opens from the More sheet (`SUB_DELAY_IN_MORE`); size, font and style live in Settings > Subtitles. Modern Audio panel keeps Tracks / Sound tabs (`TRACK_PANELS_V2`, `PANEL_*`). Modern style only.
- Round 66 (after official beta.4's plain / ASS styles and audio sync): fork font, HDR dim and lift rebuilt on official's mpv styling; `SUB_FORMAT_TAG` (format at the end of line two), `SYNC_AUDIO_NOW` ("Sync to audio now" row under the tracks for an add-on subtitle, `PlayerRuntimeControllerNuvioCAudioSync.kt`), `SUB_ASS_IN_SETTINGS` (Customize ASS in Settings > Subtitles).
- Files: `player/NuvioCSubtitle*.kt`, `NuvioCStyledLift.kt`, `NuvioCSdh.kt`, `NuvioCTrackNames.kt`, `SubtitleSelectionOverlay.kt`, `ui/screens/settings/NuvioCSubtitleSettings.kt`.

## Audio
- Round 64c `PASSTHROUGH_TRACK_PICK`: with a format switched off, audio tracks are ranked as if all on the main decoder, so language / default flag / channels / bitrate pick the starting track (DTS + DD+ releases started on DD+ after 64b). Audio renderers only.
- Round 64b `PASSTHROUGH_KEEP_OTHERS`: with a format switched off, only that format loses its platform decoder and goes to FFmpeg; the device decoder order stays, so DD+ Atmos / DD still pass through (round 51 put FFmpeg first for every track).
- Night mode (compressor) with schedule and toast (also in the Modern More sheet's Sound section), per-format passthrough switches per profile (formats switched off are decoded on the TV as multichannel PCM).
- Switches: `NIGHT_MODE*`, `NIGHT_SCHEDULE`, `PASSTHROUGH_FORMATS`, `PASSTHROUGH_PER_PROFILE`, `DECODED_*`.
- Files: `player/NuvioCNightMode.kt`, `NuvioCNightToast.kt`, `NuvioCPassthrough.kt`, `GainAudioProcessor.kt`, `AudioSelectionOverlay.kt`, `ui/screens/settings/NuvioCNightScheduleSettings.kt`.

## HDR and Dolby Vision
- 60% dim of subtitles and controls during HDR / HLG / DV, detected from filename, official's detection and the video header (read only, never touches decoder choice). Official's HDR subtitle style setting hidden.
- Switches: `HDR_DIM*`, `HDR_DECODED`, `HDR_OFFICIAL_DETECT`, `NO_OFFICIAL_HDR_SUB_STYLE`, `AFR_LOOP_GUARD`.
- Files: `player/NuvioCHdrDim.kt`, `NuvioCDecodedColor.kt`, `NuvioCSpsColor.kt`, `NuvioCPlaybackLog.kt`.

## Loading, pause, stats
- Loading screen: provider line, filename, ratings and synopsis in a bottom band; quiet version (`LOADING_QUIET`): lower and dimmer, synopsis 11 sp / 3 lines, filename on one line cut in the middle under a FILENAME label. Netflix-style pause screen. Placeholder-clip detection opens Sources.
- Stream info HUD on the right (ysosrs123 style), fixed box, larger text.
- Files: `player/NuvioCLoading*.kt`, `NuvioCPauseScreen.kt`, `NuvioCPlaceholder.kt`, `NuvioCStatsPanel.kt`, `NuvioCPlayerInfo.kt`, `NuvioCPlaybackStats.kt`.
- Round 66: `CLEAN_PILLS_NARROW` (Modern left pills 3 dp narrower per side). Official beta.4's Disc titles button sits after the Default row's buttons.
- Round 65 `STATS_STEADY`: stream info rows fixed per engine from the start ("—" / "OK"), scale worked out once, tabular digits.

## Playback flow
- Next with no countdown (unattended auto-play keeps it), episode titles from your meta add-on, sources warmed after 15 s, playing source matched by filename, single-source picker without tabs, Sources Up-jump to the first result.
- Files: `player/NuvioCEpisodeTitles.kt`, `NuvioCFinishTracker.kt`, `PlayerRuntimeControllerStreams.kt`, `StreamSourcesSidePanel.kt`, `ui/screens/stream/`.

## Details page
- Back from Sources (Play / Play manually / a held episode card) jumps straight to the saved spot; for 2.5 s or until a key press, Play or the episode card keeps asking for focus so a row rebuild can't move it (`DETAIL_RETURN_FOCUS`, `DETAIL_RETURN_GUARD`, `ui/screens/detail/NuvioCDetailReturnGuard.kt`). Back on the play options returns to Play. Episode cards always use the page's current focus requester and none are dropped, so focus can reach them after a return (`EPISODE_REQUESTER_LIVE`); `DETAIL_FOCUS_LOG` writes info lines tagged NuvioCFocus for adb checks. The restore lock never blocks a card once the restore id is cleared (the restored card lost focus in that frame, `EPISODE_LOCK_RACE`). Sources opened by hand from the Play / Resume pill return to the pill and keep the season (`DETAIL_HERO_RETURN`). Focus arriving at the episodes row goes to the card last focused in that season, or the nearest card on screen, never an invisible spot (`EPISODE_ENTRY_FALLBACK`, `NuvioCDetailSmooth.kt`).
- Hero spec (logo box, facts line above, wider synopsis, tagline), round Shuffle / Rate / Bell buttons with focus labels, Information ledger with fixed slots (Awards wins via OMDb), collection on its own row (episode Find was removed in round 56: it caused the lost focus on series pages), smoother scrolling and focus.
- Files: `ui/screens/detail/NuvioCDetail*.kt`, `NuvioCCredits.kt`, `HeroSection.kt`, `MetaDetailsScreen.kt`, `EpisodesSection.kt`, `ui/screens/calendar/NuvioCDetailBell.kt`.
- Round 65: `SERIES_GRAPH` (no Ratings row; a round Ratings graph hero button after Rate opens a full-screen season x episode grid like the synopsis, IMDb / Mine (Trakt + this TV's episode ratings), watched / next marks, season averages; ratings load when it opens; `NuvioCSeriesGraph.kt`), `HERO_ROW_V65` (pill 44 dp, 16 dp sides, round buttons 44 dp, 8 dp gaps; also Featured, Roulette, Library Random pick; `NuvioCHeroRow65.kt`), `SYNOPSIS_LOGO`, `EDITIONS_BY_SEEDERS` (2+ releases, 10+ seeders, by seeders; no fan edits, no Criterion; cache marked `e65`).
- Round 66: `GRAPH_V66` (panel after the grid's real edge, two-line season header, overlay ticks, focused season bar only, no hint lines, one-line empty state; OK on an episode does nothing since 66b), `DETAIL_SCRIM_V66` (official left darkening; hero-on-screen check for scrim and Pause on scroll), `QUALITY_REFRESH_V66` (12 h recheck for new releases, old line shown while refreshing), `EDITIONS_V66` (15% share, 25+ seeders, 2+ release groups, `NuvioCEditionBlocklist.kt`, cache `e66`).
- Round 61 hero lines (`HERO_LINES_V61`): under the buttons `[age rating] • genres • N seasons`; the quality line under the synopsis starts with `year • duration` (a show's episode length), a dot, then the badges. Without MDBList, IMDb / TMDB take the ratings row; the next-episode badge sits at the end of the ratings row either way. Dolby Vision, Dolby Atmos and DTS (any DTS) are white logos (`res/drawable/nuvio_c_logo_*.xml`, from Wikimedia Commons; Atmos and DTS traced from bitmaps), the whole line 17 dp (`QUALITY_LOGOS`).
- Round 62 (`QUALITY_LINE_V62`, `HERO_RHYTHM_V62`, `RATINGS_ROW_V62`, `DETAIL_OPEN_FAST`): quality line back to 15 dp with 10 sp pills, Dolby logos 12 dp inside; `year • duration`, a hairline, then resolution, source (REMUX reads BLURAY), TrueHD, visual, Atmos (one sound label per release as before; DTS / DD / DD+ not shown); show years read `2023` (running) or `2019–2023`. No dot after the age rating chip; one 12 dp gap between facts, ratings, synopsis and the quality line. Ratings row: square marks 16 dp, IMDb 26 x 13 dp, even-width digits, TMDB whole, 16 dp between sources; the empty-space hold gives up after 2.5 s; no empty fallback row. Opening: the MDBList user-rating lookup reuses one media index per sync (`data/mdblist/MdbListUserRatings.kt`; it rebuilt one over the whole watch history per lookup, ~180 ms on the main thread at open), the unused official rating pill lookup is skipped, the fork's MDBList score read runs off the main thread, and the picture stays under the placeholder while the page fades in.
- Round 63: quality line runtime reads `1 hr 49 min` / `24 min`, 7 dp either side of the dot between year and runtime, 6 dp between badges, Dolby Vision / Atmos in an outlined box the badges' height (`QUALITY_LINE_V63`). Opening: Home rows and Continue Watching hand their picture to the details page like the hero does (`DETAIL_OPEN_PICTURE`, `ui/screens/home/HomeScreen.kt`); held-back loads start 200 ms apart and the facts line no longer listens to the info store (`DETAIL_TRIM_63`). Information Awards: one award body per line (`AWARDS_BY_LINE`).
- Round 64 quality line (`QUALITY_LINE_V64`): resolution, source, one visual tag, one sound tag, then Dolby Vision and Atmos last; DD / DD+ use the official logos (`nuvio_c_logo_dolby_digital*.xml`, Wikimedia Commons, public domain), DTS / DTS-HD / DTS:X the DTS logo with `HD` / `:X`; TrueHD and FLAC stay pills (the parser knows DTS core and FLAC again). One line: second edition, first edition, visual pill, sound pill drop in that order; resolution and source never. Editions (`QUALITY_EDITIONS`): after a hairline, up to two of Extended, Director's Cut, Special Edition, IMAX, Final Cut, Ultimate Cut, Redux, Criterion, Open Matte, B&W, seen in ANY release of a film; read from the rest of the Torrentio list (up to 400 streams) after the badge pick is handed over, so badges don't wait; only lines that name the film count and its own title is taken out; films only; cached with the badges (round 60-63 cache entries are read again once).
- Round 64 rows (`DETAIL_ROWS_V64`): More like this and episode Ratings are rows of their own under Creator and Cast and Collection (64c order: Cast, Collection, Ratings, More like this; Down from the cast goes to the next row, no jump to Comments) (no tab bar left), the Trailers tab is gone, every row header is the 22 sp title with 20 dp above.
- Over a background trailer the whole hero block (logo, tagline, buttons, facts, ratings, synopsis, quality line) sits at one level (60% since round 62); the synopsis drops its own grey then; a focused button is back at 100% (`TRAILER_BLOCK_DIM`, level in `ui/screens/detail/NuvioCTrailer.kt`).
- Production / network chips without a logo: name on two lines at 13 sp; still cut = scrolls on focus (`COMPANY_CHIP_TWO_LINES`, `CompanyLogosSection.kt`).
- Opening (`DETAIL_OPEN_SMOOTH`, `ui/screens/detail/NuvioCDetailOpen.kt`): placeholder in the Nuvio C hero shape, the page fades in over it in 180 ms, rows under the hero join one frame later; fresh opens only. `DETAIL_DEFER_LOADS`: More like this, comments, collection, episode ratings and the trailer link wait 1.2 s from the first of them, or start at once when the page scrolls.
- Quality line as the last line under the synopsis: age rating (moved off the facts line, `QUALITY_LINE_RATING`), a hairline, then soft-fill badges (synopsis colour and opacity) from ONE real release (4K | DOLBY VISION | ATMOS | WEB-DL): public Torrentio first (2 calls at a time, 15 min pause after 403 / 429, its HTTP cache kept), QualiCache only when Torrentio has nothing (key from the repo secret NUVIO_C_QUALICACHE_KEY, appended by CI; blank = off). Drops CAM / TS / zero seeders, 4K only with two 5+ seeder 4K releases, 5+ seeder releases win, a film release naming another year is ignored. Series: latest aired episode, then the one before. Dim (65%) NOT YET RELEASED / NOT YET ON DIGITAL (film out in cinemas within a year, no TMDB digital date yet); nothing usable = just the rating; nothing drawn for the badges while checking, no line without an IMDb ID; fixed height from the first frame. Compact result cached on the TV (7 days, 1 day near release). A layer like the ratings: the first frame only checks the ID and a remembered result (no planning, no disk), the lookup waits 1.2 s for the page to settle, and the Torrentio answer is read as it downloads on low-priority threads, stopping (and closing the download) once nothing further down its quality-sorted list can change the pick. Saved results load into memory after start-up, so titles seen before show at once. Own leaf view model, not in `MetaDetailsUiState` (`DETAIL_QUALITY_LINE`, `data/quality/`, `ui/screens/detail/NuvioCQualityLine.kt`).

## Ratings
- Official's rating pill and panel driving multi-tracker sync: Trakt, MDBList, Simkl (films and shows), episodes on Trakt and MDBList. Post-play prompt after a finished film.
- Switches: `RATINGS_MULTI`, `RATING_*`, `POST_PLAY_RATING`, `RATE_CONNECTED_ONLY`.
- Files: `data/nuvioc/NuvioCRatingService.kt`, `NuvioCJustFinished.kt`, `core/tracking/TrackingRatings.kt`.

## Trailers
- Layer on official background trailers: sound toggle, one Back exits, skip watched titles, resume after Sources, keep focus. Trailer screen gets the seek bar and a logo. IMDb backup when YouTube rate-limits.
- Optional patches (see CI): file lists in `nuvio-c.yml` `TRAILER_PATHS` and `IMDB_PATHS`.
- Round 65: official 1.2.0-beta.4's trailer "audio without video" fix taken alone (`TrailerPlayer.kt`, `BackgroundTrailerLayer.kt`); beta.4 is merged in full since round 66. Round 66 `TRAILER_HANDOVER_V66`: one player view per trailer link (details-to-details). Round 66b `TRAILER_LOAD_PAUSED`: a trailer that loads while the page asks for paused stays paused.
- Files: `ui/components/BackgroundTrailerLayer.kt`, `NuvioCTrailerHold.kt`, `NuvioCTrailerResume.kt`, `data/trailer/NuvioCImdbTrailerResolver.kt`, `NuvioCTrailerBackup.kt`, `ui/screens/settings/NuvioCTrailerSettings.kt`.

## Cast page
- Acting credits only with the character under each title, director / writer job lines, All / Movies / Shows chips, Back jumps to first. Optional patch `FILMOGRAPHY_PATHS`.
- Files: `ui/screens/cast/NuvioCCast*.kt`, `core/tmdb/NuvioCFilmography.kt`.
- Round 65 `FILMOGRAPHY_TABS_NO_COUNT`: no counts inside the Movies / Series tabs. Round 66 `FILMO_UNDATED_FIRST`: titles with no date first.

## Home
- Continue Watching / Upcoming landscape cards: title and episode name scroll on focus, taking turns (`CW_TURN_MARQUEE`, `ui/components/ContinueWatchingSection.kt`).
- More cut text scrolls on focus: Taste list rows (title and details take turns), Spin card genre line, faces' role line, Modern Home card titles, collection folder titles, Calendar reminder rows (`MARQUEE_MORE`).
- Fourth layout "Featured" (full-screen carousel, contextual tags, split arrows, top-3 ratings), hero pool shuffle, auto-advance, synopsis fit, Extra large card size, Grid hidden, offline card, clock, poster glow.
- Switches: `HOME_FEATURED`, `FEATURED_*`, `HERO_*`, `CARD_SIZE_XL`, `NO_GRID_LAYOUT`, `OFFLINE_*`, `BROWSE_CLOCK`, `POSTER_GLOW*`.
- Round 65: `FEATURED_BACKDROP_WHOLE` (whole 16:9 picture ~78% wide, top right, fading left / bottom), `FEATURED_YOU_RATED` ("YOU RATED THIS 5/10"), `RATINGS_LINE_EVERYWHERE` (details ratings-line look on every ratings line).
- Round 66 `FEATURED_V66` (option A): picture full height ~91% wide, soft left darkening; tag chips, fixed logo box, certificate / genres / year / length / cached quality badges, 2-line synopsis, Starring (3), Play / Details / Watchlist / Trailer, Left / Right between titles (no arrows), slides keyed by title (no reload flicker). Round 66b `FEATURED_V66B`: one context line (round 65 style, no rank, no chips) 10 dp over the logo's real top, picture's left edge fades in, genres line then details' year • length | rating + badges line (`NuvioCQualityLineStatic`, max 4, background lookups in `NuvioCHomeFeaturedViewModel.prefetchQuality`), no Starring / Watchlist, Play names the episode, Details pill, details' trailer button, 380 ms crossfade into Continue watching; `TAGS_V66B` (more context lines).
- Files: `ui/screens/home/NuvioC*.kt`, `ModernHomeHero.kt`, `ModernHomeContent.kt`, `ui/components/NuvioCCardSize.kt`, `NuvioCHeroSpec.kt`, `NuvioCClock.kt`, `NuvioCPosterGlow.kt`, `core/nuvioc/NuvioCFeaturedTags.kt`, `core/di/NuvioCFeaturedModule.kt`.

## Look and feel (app-wide)
- Extra app fonts everywhere, logo sizing / trimming / dark lift, focus ring matching shapes, 40 dp page margin, focus room in lists, held-key brakes.
- Files: `ui/theme/Type.kt`, `res/font/nuvio_c_*`, `ui/components/NuvioCLogoSize.kt`, `NuvioCFocusRoom.kt`, `NuvioCFocusShape.kt`, `NuvioCPageMargin.kt`, `core/NuvioCInput.kt`.

## Sidebar, profile screen, start-up
- Slim Modern sidebar with dim and smooth close, Up to profile.
- Profile screen: no "Who's watching", centred block, greeting, check badge, built-in TMDB background (optionally from catalogs).
- Start-up: no Nuvio logo on a cold start to profiles (the start-up logo is held back while the profile screen is still ahead); the profile list saved on the last run draws the profile screen from the first frame (`STARTUP_PROFILES_FIRST`, `data/local/ProfileDataStore.kt` mirror, `core/profile/ProfileManager.kt`); still logo only on the Home path, waits for profiles, sync and Home to settle, background job queue.
- Files: `ui/components/SidebarNavigation.kt`, `NuvioCSidebarSettle.kt`, `ui/screens/profile/NuvioCProfile*.kt`, `ProfileSelectionScreen.kt`, `ui/components/NuvioCProfileBg.kt`, `NuvioCOpening.kt`, `core/nuvioc/NuvioCStartup*.kt`, `MainActivity.kt`.
- Round 65: `PROFILE_BG_WHOLE` (picture ~85% wide, full height, right; left fade only, 65b), `PROFILE_BG_PER_PROFILE` (from catalogs / catalogs per profile, own pool file). Round 66b `PROFILE_SHADE_V66B` (darker, dithered shade). Round 66 `PROFILE_BG_V66`: same box for every profile, page colour (no grey) without a picture, Add Profile keeps the last picture, splash drawn identically (`NuvioCProfileShade`).

## Library
- Watched tab, Random pick, list picker with your source first, poster fallback, Back keeps focus.
- Nuvio Sync source gets the list picker too: "Library" (saved items) and "Watched" (Nuvio watch history, one card per film / show, newest first; date sorts use when it was watched). Same filters, sorts, poster fallback and focus handling as the tracker sources (`LIBRARY_WATCHED_LOCAL`).
- Files: `ui/screens/library/NuvioCLibrary*.kt`, `ui/components/NuvioCLibraryPicker.kt`, `data/nuvioc/NuvioCWatchedLibrary.kt`.
- Round 65 `LISTS_SAVE_JUMP`: lists menu Left / Right to Save, Up back to the row; whole 40 dp rows, 6 dp focus room.

## Calendar and Upcoming (from ysosrs123, fully Nuvio C's since 2026-11-20)
- Calendar page (List / Month; the Week view was removed), sources TVDB, Trakt, MDBList calendar, TMDB Coming soon (digital only), bells shared with Details, Upcoming row (14 days, future only).
- Switches: `LIBRARY_CALENDAR`, `CALENDAR_*`, `UPCOMING_*`, `TMDB_SOON_*`, `BELL_SYNC`.
- Files: `core/calendar/`, `ui/screens/calendar/`, `ui/screens/home/HomeComingUp.kt`, `ui/screens/settings/CalendarSettingsSection.kt`.
- Round 66b `CALENDAR_SYNC`: Calendar settings (bells, hidden titles, sources, window) sync with the profile's settings; the zone override stays on the device.
- Round 65: `CALENDAR_NO_ZONE_LINE`, `CALENDAR_PANEL_STEADY` (side panel picture / logo chosen once per row after up to 300 ms, kept until the next decodes; `NuvioCCalendarPanelArt.kt`).

## Roulette (sidebar "Roulette", action "Shuffle")
- On the reel / result, changing filters never spins; an amber dot sits on Shuffle again (`ROULETTE_HOLD`). Unwatched / Watched / All titles is a dropdown (`ROULETTE_WATCH_DROPDOWN`). Movies / Shows cards have no glow or fill on focus (`ROULETTE_TILE_NO_GLOW`). Mood-screen backdrop decoded larger and softly blurred (`BACKDROP_SMOOTH`).
- Movies / Shows mood screen over a large raw TMDB pool, Refine dialog (popularity, decade, region, theme…), watched cycle, ratings line, backdrop.
- Files: `ui/screens/shuffle/`, `data/nuvioc/NuvioCShuffle*.kt`, `NuvioCRoulette*.kt`, `ui/screens/settings/NuvioCShuffleSettings.kt`.
- Round 65 `ROULETTE_SPIN_V65`: the reel moves at once over posters in memory, eases onto the pick; spinning posters blurred (1/8 decode) and dimmed, pulsing "?", the pick sharpens with YOUR PICK; Library Random pick too (`ui/components/NuvioCPosterReelV65.kt`). Round 66 `REEL_V66`: later shuffles always spin, 1/16 blur, ~33% brightness, desaturated.

## Taste page
- Per-profile stats and discovery: Overview / Discover / Stats / Wrapped tabs.
- Round 57 look (`TASTE_V57`): one loading screen (placeholders + progress) until the tab list and opening tab are ready, then one fade; flat cards with caps labels, tabular numbers, poster thumbnails; Overview / Wrapped rows open their title; card headers bold, big numbers extra bold, titles regular and smaller; tabs fill with the app theme accent when focused, other focus rings in the accent (not the profile colour); months as three letters; backdrop decoded larger and softly blurred (`BACKDROP_SMOOTH`).
- Round 58 (`TASTE_V58`): scope pills Month / Year / All time, and the page opens on This month every time (sidebar entry resets it); an empty This month offers last month; New releases without its ring chart; Discover posters 120 x 170 dp; Stats has Languages as its own card (top five) beside Genres / Decades / Studios, bottom strip four tiles; Wrapped top row totals / Hours by month / Firsts, bottom row Top shows / Top films (top five by rating) / Highlights.
- Round 63 (`TASTE_RETURN_FOCUS`): Back from a title opened from a list card lands on that row again (`NuvioCTasteReturn`); falls back to the tab after 0.9 s if the row is gone.
- Round 61 (`TASTE_TOP_STACK`): list cards stack fixed 40 dp rows from the top (one or two entries no longer spread apart); Faces start at the first position with a 24 dp gap when the row isn't full.
- Round 59 (`TASTE_V59`): long names get two lines: Top show name wraps (note one line); Biggest day / New releases put the date / oldest year beside the number and the title gets two lines below; list rows (Recently finished, Binges, Top shows, Top films) are title line + details line. Wrapped chart keeps a line above the bars for the peak number; Wrapped dates read "11 Nov". Stats bottom strip is four cards lined up under the four cards above; Avg episode notes how many shows.
- Files: `ui/screens/taste/`, `data/nuvioc/NuvioCTaste*.kt`. Rule: strictly per profile.
- Round 65 `TASTE_CARDS_OPEN`: every card with data focuses and opens a page (breakdown + titles, timeline, ranked list) for the page's scope; Firsts / Highlights lines open their target; Back lands on the card (`NuvioCTastePages65.kt`, `data/nuvioc/NuvioCTasteCards.kt`). Round 66 `TASTE_V66`: list focus room, no Open hint, header baseline, held keys stay in lists, 12-hour clock, Wrapped year only. Round 66b `TASTE_HOURS_PM` ("9pm").

## Trackers and sync
- Simkl AUTH V2 device login (`SIMKL_V2`, `data/simkl/SimklAuthRepository.kt`), Trakt keeps login on soft refresh failures (`TRAKT_KEEP_LOGIN`), MDBList rating batching.
- Fork settings travel with the Nuvio profile (`NUVIO_C_CLOUD_SETTINGS`, `CLOUD_SETTINGS_*`, `core/nuvioc/NuvioCCloudSettings.kt`). Each profile also keeps its own copy on the TV and it is loaded on every profile switch before any sync, so one profile's settings never land in another's (`CLOUD_SETTINGS_PER_PROFILE`). Passthrough switches stay on this TV, per profile, never synced. Roulette's seen list and the post-play rating marker are per profile (`PROFILE_LOCAL_HISTORY`, `core/nuvioc/NuvioCActiveProfile.kt`). TVDB key in Integrations (`API_KEYS`, `core/nuvioc/NuvioCApiKeys.kt`).
- Round 65 `CLOUD_SETTINGS_65`: profile background settings and Roulette sidebar / catalogs / weights sync too.

## Performance
- Round 64c: `PLAYER_EXIT_FAST` (plain Back out of the player releases the stopped player 0.9 s later; the key press held ~190 ms in ExoPlayer.release()), `DETAIL_CACHE_WINDOW` (details rows stay built ~1 screen above / half below, no rebuild hitch scrolling back up), `RATINGS_STEADY` (Ratings row shows tabs and dimmed chips while loading, no height jump, one less page-state pass), `ROW_LOADING_NAV` (Home / Search rows no longer block Up / Down while a focused row swaps placeholders for cards; `ui/components/CatalogRowSection.kt`), `OSD_DDP_ATMOS_NAME` (DD+ track in an Atmos-named release shows the Atmos logo).
- Round 64b `TRACE_LABELS`: named slices in a perfetto trace of the release build (`NuvioC key …`, details stages, OSD open / close), `core/NuvioCPerf.kt`.
- Round 64 `OSD_WARMUP`: the OSD icons and badge logos are read one per frame 1.5 s after the player opens (the first OSD open read ~20 vectors in one frame, ~75 ms, traced on the TV).
- Player memory trim, trailer pool guard, lean episode ratings and calendar logging, image cache cap on 2 GB TVs, idle quiet, start-up job queue, catalog sweep order.
- Files: `core/NuvioCPerf.kt`, `core/NuvioCPlayerMemory.kt`, `core/nuvioc/NuvioCStartupQueue.kt`, `ui/screens/home/NuvioCHomeCatalogSweep.kt`.

## Switched off (reverted, code still present: cleanup candidates)
- `DETAIL_FOCUS_LOG`: diagnostics only ("NuvioCFocus" log lines for the details focus restore). Off since round 58 once the restore was confirmed; turn on to trace a focus bug over adb.

## Keeping this file true
Add, change or remove a line here in the same commit as any feature that is added, reworked or removed. Removed tweaks leave this file entirely. Update the Base line after each upstream merge.
