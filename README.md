# Nuvio C

A personal build of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV: the official app with small changes on top. It installs alongside the official app.

**Download:** [latest release](https://github.com/savantguarded/NuvioTV/releases/latest)

## What's different

**Home**
- **Featured layout** (Settings > Layout): a full-screen carousel of your hero catalogs on top of Modern home, with Play, Details and a next-title arrow, a "#3 in Top 20" line for ranked catalogs, a NEW / NEW SEASON tag and up to six lines of synopsis. It loops both ways; Down goes to the rows, Up or the sidebar's Home comes back.
- The hero carousel actually rotates (every 10 s without a key press) and draws a fresh mix of 18 titles each launch.
- Featured carousel: up to three ratings, a small five-dot page indicator, and the sidebar's Home always lands back on Continue Watching.
- **Calendar** (from [ysosrs123's fork](https://github.com/ysosrs123/NuvioTV-Fork)): a Calendar page in the menu (List, Week and Month) with the next episodes and film dates of everything in your library, in your time zone. Bell a title for a reminder card on Home when something new is out. Optional **From Your Library** row under Upcoming (Settings > Layout > Home Content); Calendar options, including your own lists, under Settings > Layout > Calendar. Episode times from TVmaze.

**Player**
- Title logo and stream badges (resolution, HDR / Dolby Vision, audio, source, size) on the controls.
- Subtitles move up out of the way while the controls or pause screen are showing.
- Netflix-style pause screen; Start over button; Next skips the countdown.
- Subtitles and controls dimmed during HDR and Dolby Vision, drawn as cheaply as official's own subtitle colours.
- Watch Party tucked into the More row (back in the main row while a party runs).
- Full release filename on the loading screen; fake short placeholder streams are caught.
- Prefer SDH subtitles, subtitle font picker, correct episode names on Next and auto-play.

**Details page**
- Rate titles 1 to 10 to Trakt and MDBList.
- TMDB tagline under the logo; collections on their own row.
- Background trailers: muted by default, pause behind popups and other pages, skipped for titles you've started, no stutter as they start.
- IMDb trailers when YouTube rate-limits or only offers low quality (one small request, no hidden browser, never during playback).

**Library and cast**
- Watched tab for Trakt, MDBList and Simkl.
- Cast pages show the real filmography with the role or job under each title.

**Look and feel**
- Clearlogos at a consistent size, dark logos made readable.
- Extra app fonts, clock on Home, cleaner profile screen.
- Smoother scrolling on Home, collections and the details page, including moving between its sections.
- Even spacing next to a focused button; the app font everywhere, ambient screen included.
- Source picker without an "All" tab when there's only one source.

**Stability on 2 GB TVs**
- Lighter player: less memory held during 4K playback, no second video decoder or web browser running behind a film.

**Accounts and updates**
- Simkl device login with automatic renewal.
- Trakt stays signed in through server hiccups; the reason for a disconnect is shown under Trakt in Settings.
- The in-app updater also offers rebuilds of the same official release, and installs each update already compiled for the TV (Android 9+).

## Switching a tweak off

Every tweak has its own switch; switched off, that part behaves exactly like official Nuvio.

1. GitHub **Settings > Secrets and variables > Actions > Variables**: set `NUVIO_C_OFF` to a comma list of switch names, e.g. `osd_badges,subtitle_lift`.
2. **Actions > Nuvio C build > Run workflow** with "Build even if this official release was already built" ticked.
3. Clear the variable to turn everything back on.

Switch names and what each does are in [`NuvioCFeatures.kt`](app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt) (lower-case line name, e.g. `OSD_BADGES` → `osd_badges`).

## Updates

A build runs every hour. When Nuvio publishes a release, these changes are applied on top and a new APK is posted to Releases. If an official update clashes with an optional part (details and trailers, IMDb backup, cast filmography), the APK is still built without it and an issue says so.

Licence: [GNU GPL v3.0](./LICENSE), like the official app.
