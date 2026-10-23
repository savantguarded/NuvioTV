# Nuvio C

A personal build of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV: the official app with small changes on top. It installs alongside the official app.

**Download:** [latest release](https://github.com/savantguarded/NuvioTV/releases/latest)

## What's different

**Home**
- **Featured layout** (Settings > Layout): a full-screen carousel of your hero catalogs on top of Modern home, with Play, Details and a next-title arrow, a "#3 in Top 20" line for ranked catalogs, a NEW / NEW SEASON tag and up to six lines of synopsis. The carousel only moves forward: Right from the arrow goes to the next title and loops from the last to the first, and Left from Play opens the sidebar on any title; Down goes to the rows, Up or the sidebar's Home comes back.
- The hero carousel actually rotates (every 10 s without a key press) and draws a fresh mix of 18 titles each launch.
- **Extra large** card size (Settings > Layout > Card width), sized per layout for Home and for collection folders that follow the Home layout. Rows sit tighter, so the hero, logo and synopsis stay exactly as at Large and the next row still peeks. Library and Discover stay at Large. The Grid layout is hidden.
- Featured carousel: up to three ratings, a small five-dot page indicator, and the sidebar's Home always lands back on Continue Watching. On a cold start it fades in once its first picture is ready, with the rows already in place underneath.
- **Calendar** (from [ysosrs123's fork](https://github.com/ysosrs123/NuvioTV-Fork)): a Calendar page in the menu (List, Week and Month) with the next episodes and film dates of everything in your library, in your time zone. Bell a title for a reminder card on Home when something new is out. Optional **Calendar in Upcoming** (Settings > Layout > Home Content): the Calendar fills the one Upcoming row, with official-style tags (Tonight 9:00 pm, Tomorrow, In 3 Days). Calendar options, including your own lists and which view it opens in (**Start in**: last used, List, Week or Month), under Settings > Layout > Calendar. Back jumps to the List / Week / Month buttons, Up always lands on the current one; no star rating in Week or List view; only dated releases (no "No date yet" group, films from their digital release). The reminder popup's buttons always show their full labels. Shows with a new episode waiting in Continue Watching stay followed, and Upcoming leaves out what Continue Watching can already play. Signed in to MDBList, the Calendar also follows **your MDBList calendar** (what mdblist.com > Preferences > Calendar includes) and uses its release dates; Settings > Layout > Calendar > Use my MDBList calendar. Titles saved in a library stay followed even if removed from Continue Watching or last watched long ago, and Library adds / removes show up within seconds. The Upcoming row lists only what is still to come. A **refresh button** beside List / Week / Month asks every source again. Week keeps the focus ring clear of the edges, shows a slice of the next card, and Up / Down always land on a day's first card; OK on a Month day opens its Week and Back returns to Month. Month moves stay inside the month, and held Left stops at the start of a row instead of opening the sidebar. Episode times from TVmaze. New calendar code from ysosrs123 is watched daily and merged with `scripts/nuvio-c-calendar-sync.sh`, keeping these tweaks.

**Player**
- Title logo and stream badges (resolution, HDR / Dolby Vision, audio, source, size) on the controls.
- Subtitles move up out of the way while the controls or pause screen are showing (text and picture subtitles; ASS stays where the file puts it, as official's libass now draws it on its own surface).
- Netflix-style pause screen; Start over button; Next skips the countdown.
- Subtitles and controls dimmed during HDR and Dolby Vision, drawn as cheaply as official's own subtitle colours, without darkening what's behind an open panel. Official's separate HDR subtitle style setting is removed, so subtitles are only dimmed once.
- Watch Party tucked into the More row (back in the main row while a party runs).
- Full release filename on the loading screen; fake short placeholder streams are caught.
- Prefer SDH subtitles, subtitle font picker, correct episode names on Next and auto-play.

**Details page**
- Rate titles with five stars (half steps, 1 to 10) to Trakt, Simkl and MDBList, in a dialog that fits all three.
- The plus button opens your library lists (ticks lined up in one column); hold it for the one-step add.
- TMDB tagline under the logo; collections on their own row.
- Background trailers: muted by default, pause behind popups and other pages, skipped for titles you've started, no stutter as they start.
- IMDb trailers only when YouTube rate-limits or only offers low quality (one small request, no hidden browser, never during playback).

**Library and cast**
- Watched tab for Trakt, MDBList and Simkl.
- Cast pages show the real filmography with the role or job under each title.

**Look and feel**
- Clearlogos at a consistent size, dark logos made readable.
- Extra app fonts, clock on Home, cleaner profile screen that opens Netflix-style (background fades up, profiles drop in).
- Smoother scrolling on Home, collections and the details page, including moving between its sections.
- Even spacing next to a focused button; the app font everywhere, ambient screen included.
- Source picker without an "All" tab when there's only one source.
- Modern sidebar closes smoothly, blur included; a page picked from it is built once the sidebar has closed, then fades in.

**Stability on 2 GB TVs**
- Lighter player: less memory held during 4K playback, no second video decoder or web browser running behind a film.

**Accounts and updates**
- Simkl device login with automatic renewal.
- Trakt stays signed in through server hiccups; the reason for a disconnect is shown under Trakt in Settings.
- The in-app updater offers every new Nuvio C build, rebuilds of the same official release included, and each one installs already compiled for the TV (official's updater). With "Install updates automatically" on (Android 12+) they download and install while the TV is idle; after an update you confirmed, Nuvio C opens again by itself.
- Nuvio C's own settings follow your Nuvio profile to a new TV (carried inside official's settings sync).
- English only, for a smaller download.

## Switching a tweak off

Every tweak has its own switch; switched off, that part behaves exactly like official Nuvio.

1. GitHub **Settings > Secrets and variables > Actions > Variables**: set `NUVIO_C_OFF` to a comma list of switch names, e.g. `osd_badges,subtitle_lift`.
2. **Actions > Nuvio C build > Run workflow** with "Build even if this official release was already built" ticked.
3. Clear the variable to turn everything back on.

Switch names and what each does are in [`NuvioCFeatures.kt`](app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt) (lower-case line name, e.g. `OSD_BADGES` → `osd_badges`).

## Updates

A build runs every hour. When Nuvio publishes a release, these changes are applied on top and a new APK is posted to Releases. If an official update clashes with an optional part (details and trailers, IMDb backup, cast filmography), the APK is still built without it and an issue says so.

Licence: [GNU GPL v3.0](./LICENSE), like the official app.
