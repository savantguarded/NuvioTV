# Nuvio C fork changes

How Nuvio C differs from official NuvioTV, grouped by area: what you see, the switches, the files.
Per-switch detail: every switch in `app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt` has a one-line comment. This file is the overview, not a copy of that list.
Paths below are relative to `app/src/main/java/com/nuvio/tv/` unless they start with `app/`, `.github/` or `scripts/`.

Base: official `1.2.0-beta.3` (`552cacf5`). Fork footprint vs that tag (round 59): 404 files added, 175 official files edited, about 62,800 lines added; round 60 adds 6 files, round 61 adds 4 (one Kotlin file, three logo drawables). 439 switches, 1 off.
Check: `git diff --stat 552cacf5 HEAD` (swap in the new tag after each upstream merge).

## Build and distribution
- App "Nuvio C", package `com.nuvio.tv.c`, `armeabi-v7a` only, beside the official app. Set at build time in `.github/workflows/nuvio-c.yml`, not in source.
- Hourly CI replays the fork onto the official release with the highest version (not the newest date). Optional patches (trailer layer, IMDb backup, cast filmography) drop alone on conflict. Issue opened when upstream edits OSD files. Repeat conflict mails are quieted.
- In-app updater points at this repo, offers forced rebuilds and compiles on install: `app/src/full/java/com/nuvio/tv/updater/NuvioCUpdate*.kt`, `UPDATE_REOPEN`.
- English-only APK (`ENGLISH_ONLY`, `app/build.gradle.kts`), no crash reports to Nuvio (`NO_CRASH_REPORTING`), no launcher channels (`NO_TV_CHANNELS`), debug logs stripped in release (`app/proguard-rules.pro`), fork baseline profile `app/src/main/baselineProfiles/nuvio-c.txt`.

## Player controls (OSD)
- Two control styles via Settings "Player controls style" (`OSD_CLEAN`): **Default** (code id `nuvio_c`, official row plus fork tweaks) and **Modern** (code id `clean`, files `NuvioCOsdClean.kt` / `NuvioCCleanLayout.kt`: left pills, centred playback, right icon pill, More sheet, icons `res/drawable/nuvio_c_ic_clean_*`). Code and switches say "Clean"; screens say "Modern".
- Clearlogo top-left, badges (resolution / HDR / audio / size, full Dolby names), focused-button label, Start over (then Previous episode), Next label with episode name, chapter double-press jump, 8 s timeout, YouTube-style seek bar, shades, Watch Party in More, button order setting.
- Cast / About / crew side panel (`OSD_CAST*`, `OSD_ABOUT*`, `OSD_CREW`), Auto-skip-intro switch, Skip Intro sits above the seek bar.
- Modern right icon group: glyphs 72% of the 32 dp button (was 60%); pill, button and focus circle unchanged (`CLEAN_ICONS_LARGER`).
- Modern left pills are text only, centred, 13 sp, at least 80 dp wide (`CLEAN_PILL_TEXT_ONLY`). The More sheet has a Sound section with every line of the old Sound tab: Audio delay, Amplification (PCM), Downmix centre mix level, Night mode, Persist between sessions; decode-only lines read Unavailable during passthrough (`MORE_SOUND`); the Modern Audio panel is tracks only (`AUDIO_PANEL_NO_TABS`).
- Files: `ui/screens/player/NuvioCOsd*.kt`, `NuvioCCleanLayout.kt`, `NuvioCFocusReturn.kt`, `NuvioCOsdTimeout.kt`, `NuvioCPreviousEpisode.kt`, hooks in `PlayerScreen.kt` (`PlayerControlsOverlay`). Settings: `ui/screens/settings/NuvioCOsdButtonsSettings.kt`.

## Subtitles
- Lift above the controls (bottom subtitles only), drawn renderer kept, SDH preference and hide, subtitle font picker, Outline width row, plain language names, one safety re-check after start.
- Modern Subtitles panel: one list, no tabs, every row two lines (source pill; language, with SDH / Forced / CC on built-in tracks) (`SUB_PANEL_NO_TABS`). Subtitle delay opens from the More sheet (`SUB_DELAY_IN_MORE`); size, font and style live in Settings > Subtitles. Modern Audio panel keeps Tracks / Sound tabs (`TRACK_PANELS_V2`, `PANEL_*`). Modern style only.
- Files: `player/NuvioCSubtitle*.kt`, `NuvioCStyledLift.kt`, `NuvioCSdh.kt`, `NuvioCTrackNames.kt`, `SubtitleSelectionOverlay.kt`, `ui/screens/settings/NuvioCSubtitleSettings.kt`.

## Audio
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

## Playback flow
- Next with no countdown (unattended auto-play keeps it), episode titles from your meta add-on, sources warmed after 15 s, playing source matched by filename, single-source picker without tabs, Sources Up-jump to the first result.
- Files: `player/NuvioCEpisodeTitles.kt`, `NuvioCFinishTracker.kt`, `PlayerRuntimeControllerStreams.kt`, `StreamSourcesSidePanel.kt`, `ui/screens/stream/`.

## Details page
- Back from Sources (Play / Play manually / a held episode card) jumps straight to the saved spot; for 2.5 s or until a key press, Play or the episode card keeps asking for focus so a row rebuild can't move it (`DETAIL_RETURN_FOCUS`, `DETAIL_RETURN_GUARD`, `ui/screens/detail/NuvioCDetailReturnGuard.kt`). Back on the play options returns to Play. Episode cards always use the page's current focus requester and none are dropped, so focus can reach them after a return (`EPISODE_REQUESTER_LIVE`); `DETAIL_FOCUS_LOG` writes info lines tagged NuvioCFocus for adb checks. The restore lock never blocks a card once the restore id is cleared (the restored card lost focus in that frame, `EPISODE_LOCK_RACE`). Sources opened by hand from the Play / Resume pill return to the pill and keep the season (`DETAIL_HERO_RETURN`). Focus arriving at the episodes row goes to the card last focused in that season, or the nearest card on screen, never an invisible spot (`EPISODE_ENTRY_FALLBACK`, `NuvioCDetailSmooth.kt`).
- Hero spec (logo box, facts line above, wider synopsis, tagline), round Shuffle / Rate / Bell buttons with focus labels, Information ledger with fixed slots (Awards wins via OMDb), collection on its own row (episode Find was removed in round 56: it caused the lost focus on series pages), smoother scrolling and focus.
- Files: `ui/screens/detail/NuvioCDetail*.kt`, `NuvioCCredits.kt`, `HeroSection.kt`, `MetaDetailsScreen.kt`, `EpisodesSection.kt`, `ui/screens/calendar/NuvioCDetailBell.kt`.
- Round 61 hero lines (`HERO_LINES_V61`): under the buttons `[age rating] • genres • N seasons`; the quality line under the synopsis starts with `year • duration` (a show's episode length), a dot, then the badges. Without MDBList, IMDb / TMDB take the ratings row; the next-episode badge sits at the end of the ratings row either way. Dolby Vision, Dolby Atmos and DTS (any DTS) are white logos (`res/drawable/nuvio_c_logo_*.xml`, from Wikimedia Commons; Atmos and DTS traced from bitmaps), the whole line 17 dp (`QUALITY_LOGOS`).
- Over a background trailer the whole hero block (logo, tagline, buttons, facts, ratings, synopsis, quality line) sits at one 70% level; the synopsis drops its own grey then; a focused button is back at 100% (`TRAILER_BLOCK_DIM`, level in `ui/screens/detail/NuvioCTrailer.kt`).
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
- Files: `ui/components/BackgroundTrailerLayer.kt`, `NuvioCTrailerHold.kt`, `NuvioCTrailerResume.kt`, `data/trailer/NuvioCImdbTrailerResolver.kt`, `NuvioCTrailerBackup.kt`, `ui/screens/settings/NuvioCTrailerSettings.kt`.

## Cast page
- Acting credits only with the character under each title, director / writer job lines, All / Movies / Shows chips, Back jumps to first. Optional patch `FILMOGRAPHY_PATHS`.
- Files: `ui/screens/cast/NuvioCCast*.kt`, `core/tmdb/NuvioCFilmography.kt`.

## Home
- Continue Watching / Upcoming landscape cards: title and episode name scroll on focus, taking turns (`CW_TURN_MARQUEE`, `ui/components/ContinueWatchingSection.kt`).
- More cut text scrolls on focus: Taste list rows (title and details take turns), Spin card genre line, faces' role line, Modern Home card titles, collection folder titles, Calendar reminder rows (`MARQUEE_MORE`).
- Fourth layout "Featured" (full-screen carousel, contextual tags, split arrows, top-3 ratings), hero pool shuffle, auto-advance, synopsis fit, Extra large card size, Grid hidden, offline card, clock, poster glow.
- Switches: `HOME_FEATURED`, `FEATURED_*`, `HERO_*`, `CARD_SIZE_XL`, `NO_GRID_LAYOUT`, `OFFLINE_*`, `BROWSE_CLOCK`, `POSTER_GLOW*`.
- Files: `ui/screens/home/NuvioC*.kt`, `ModernHomeHero.kt`, `ModernHomeContent.kt`, `ui/components/NuvioCCardSize.kt`, `NuvioCHeroSpec.kt`, `NuvioCClock.kt`, `NuvioCPosterGlow.kt`, `core/nuvioc/NuvioCFeaturedTags.kt`, `core/di/NuvioCFeaturedModule.kt`.

## Look and feel (app-wide)
- Extra app fonts everywhere, logo sizing / trimming / dark lift, focus ring matching shapes, 40 dp page margin, focus room in lists, held-key brakes.
- Files: `ui/theme/Type.kt`, `res/font/nuvio_c_*`, `ui/components/NuvioCLogoSize.kt`, `NuvioCFocusRoom.kt`, `NuvioCFocusShape.kt`, `NuvioCPageMargin.kt`, `core/NuvioCInput.kt`.

## Sidebar, profile screen, start-up
- Slim Modern sidebar with dim and smooth close, Up to profile.
- Profile screen: no "Who's watching", centred block, greeting, check badge, built-in TMDB background (optionally from catalogs).
- Start-up: no Nuvio logo on a cold start to profiles (the start-up logo is held back while the profile screen is still ahead); the profile list saved on the last run draws the profile screen from the first frame (`STARTUP_PROFILES_FIRST`, `data/local/ProfileDataStore.kt` mirror, `core/profile/ProfileManager.kt`); still logo only on the Home path, waits for profiles, sync and Home to settle, background job queue.
- Files: `ui/components/SidebarNavigation.kt`, `NuvioCSidebarSettle.kt`, `ui/screens/profile/NuvioCProfile*.kt`, `ProfileSelectionScreen.kt`, `ui/components/NuvioCProfileBg.kt`, `NuvioCOpening.kt`, `core/nuvioc/NuvioCStartup*.kt`, `MainActivity.kt`.

## Library
- Watched tab, Random pick, list picker with your source first, poster fallback, Back keeps focus.
- Nuvio Sync source gets the list picker too: "Library" (saved items) and "Watched" (Nuvio watch history, one card per film / show, newest first; date sorts use when it was watched). Same filters, sorts, poster fallback and focus handling as the tracker sources (`LIBRARY_WATCHED_LOCAL`).
- Files: `ui/screens/library/NuvioCLibrary*.kt`, `ui/components/NuvioCLibraryPicker.kt`, `data/nuvioc/NuvioCWatchedLibrary.kt`.

## Calendar and Upcoming (from ysosrs123, fully Nuvio C's since 2026-11-20)
- Calendar page (List / Month; the Week view was removed), sources TVDB, Trakt, MDBList calendar, TMDB Coming soon (digital only), bells shared with Details, Upcoming row (14 days, future only).
- Switches: `LIBRARY_CALENDAR`, `CALENDAR_*`, `UPCOMING_*`, `TMDB_SOON_*`, `BELL_SYNC`.
- Files: `core/calendar/`, `ui/screens/calendar/`, `ui/screens/home/HomeComingUp.kt`, `ui/screens/settings/CalendarSettingsSection.kt`.

## Roulette (sidebar "Roulette", action "Shuffle")
- On the reel / result, changing filters never spins; an amber dot sits on Shuffle again (`ROULETTE_HOLD`). Unwatched / Watched / All titles is a dropdown (`ROULETTE_WATCH_DROPDOWN`). Movies / Shows cards have no glow or fill on focus (`ROULETTE_TILE_NO_GLOW`). Mood-screen backdrop decoded larger and softly blurred (`BACKDROP_SMOOTH`).
- Movies / Shows mood screen over a large raw TMDB pool, Refine dialog (popularity, decade, region, theme…), watched cycle, ratings line, backdrop.
- Files: `ui/screens/shuffle/`, `data/nuvioc/NuvioCShuffle*.kt`, `NuvioCRoulette*.kt`, `ui/screens/settings/NuvioCShuffleSettings.kt`.

## Taste page
- Per-profile stats and discovery: Overview / Discover / Stats / Wrapped tabs.
- Round 57 look (`TASTE_V57`): one loading screen (placeholders + progress) until the tab list and opening tab are ready, then one fade; flat cards with caps labels, tabular numbers, poster thumbnails; Overview / Wrapped rows open their title; card headers bold, big numbers extra bold, titles regular and smaller; tabs fill with the app theme accent when focused, other focus rings in the accent (not the profile colour); months as three letters; backdrop decoded larger and softly blurred (`BACKDROP_SMOOTH`).
- Round 58 (`TASTE_V58`): scope pills Month / Year / All time, and the page opens on This month every time (sidebar entry resets it); an empty This month offers last month; New releases without its ring chart; Discover posters 120 x 170 dp; Stats has Languages as its own card (top five) beside Genres / Decades / Studios, bottom strip four tiles; Wrapped top row totals / Hours by month / Firsts, bottom row Top shows / Top films (top five by rating) / Highlights.
- Round 61 (`TASTE_TOP_STACK`): list cards stack fixed 40 dp rows from the top (one or two entries no longer spread apart); Faces start at the first position with a 24 dp gap when the row isn't full.
- Round 59 (`TASTE_V59`): long names get two lines: Top show name wraps (note one line); Biggest day / New releases put the date / oldest year beside the number and the title gets two lines below; list rows (Recently finished, Binges, Top shows, Top films) are title line + details line. Wrapped chart keeps a line above the bars for the peak number; Wrapped dates read "11 Nov". Stats bottom strip is four cards lined up under the four cards above; Avg episode notes how many shows.
- Files: `ui/screens/taste/`, `data/nuvioc/NuvioCTaste*.kt`. Rule: strictly per profile.

## Trackers and sync
- Simkl AUTH V2 device login (`SIMKL_V2`, `data/simkl/SimklAuthRepository.kt`), Trakt keeps login on soft refresh failures (`TRAKT_KEEP_LOGIN`), MDBList rating batching.
- Fork settings travel with the Nuvio profile (`NUVIO_C_CLOUD_SETTINGS`, `CLOUD_SETTINGS_*`, `core/nuvioc/NuvioCCloudSettings.kt`). Each profile also keeps its own copy on the TV and it is loaded on every profile switch before any sync, so one profile's settings never land in another's (`CLOUD_SETTINGS_PER_PROFILE`). Passthrough switches stay on this TV, per profile, never synced. Roulette's seen list and the post-play rating marker are per profile (`PROFILE_LOCAL_HISTORY`, `core/nuvioc/NuvioCActiveProfile.kt`). TVDB key in Integrations (`API_KEYS`, `core/nuvioc/NuvioCApiKeys.kt`).

## Performance
- Player memory trim, trailer pool guard, lean episode ratings and calendar logging, image cache cap on 2 GB TVs, idle quiet, start-up job queue, catalog sweep order.
- Files: `core/NuvioCPerf.kt`, `core/NuvioCPlayerMemory.kt`, `core/nuvioc/NuvioCStartupQueue.kt`, `ui/screens/home/NuvioCHomeCatalogSweep.kt`.

## Switched off (reverted, code still present: cleanup candidates)
- `DETAIL_FOCUS_LOG`: diagnostics only ("NuvioCFocus" log lines for the details focus restore). Off since round 58 once the restore was confirmed; turn on to trace a focus bug over adb.

Before that, none. The 2026-11-20 cleanups removed every reverted tweak, every switch nothing read, and the older versions kept as fallbacks behind newer ones (start-up loader / halo, Calendar Week view, Taste rounds 39 to 52, round 52 panels, round 46 controls, the old HDR dim layer): 47 switches in all.

## Keeping this file true
Add, change or remove a line here in the same commit as any feature that is added, reworked or removed. Removed tweaks leave this file entirely. Update the Base line after each upstream merge.
