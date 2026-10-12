# Nuvio C

A personal build of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV: the official app with small changes on top. It installs alongside the official app.

**Download:** [latest release](https://github.com/savantguarded/NuvioTV/releases/latest)

## Main features

**Player**
- Title logo top-left and stream badges (resolution, HDR / Dolby Vision, audio, source, size) under the title on the controls.
- Bottom subtitles move up while the controls are open; focus goes back to the button that opened a panel.
- Subtitles and controls dimmed to 60% during HDR and Dolby Vision.
- Start over button, 8-second controls, next episode without the countdown.
- Netflix-style pause screen.
- Loading screen shows the full release filename; short placeholder clips are caught and the Sources panel opens.
- Prefer SDH subtitles (kept for the next episode) and a subtitle font picker; episode names on Next and auto-play.

**Details page**
- Rate a title (1 to 10) to Trakt and MDBList; TMDB tagline under the logo.
- Same hero size and text scale as Home, a movie collection always on its own row, directors who act also listed in the cast.
- Smooth scroll to the end of the page.

**Trailers**
- Background trailers muted by default, paused behind popups and other pages, skipped for titles you've started.
- IMDb trailers when YouTube rate-limits or only offers low quality.

**Cast pages**
- Real filmography with the character or job under each title.

**Appearance**
- Extra app fonts, a clock on Home and in collections, a cleaner profile screen, focus rings that are never cut off.
- Clearlogos trimmed of empty borders and sized to the same visual weight; dark logos made readable.
- Add to library lists your Library service first.

**Accounts**
- Simkl device login with automatic renewal.

## Switching a tweak off

Every tweak has its own switch. Switched off, that part behaves exactly like official Nuvio.

1. GitHub **Settings > Secrets and variables > Actions > Variables**: set `NUVIO_C_OFF` to a comma list of switch names, e.g. `osd_badges,subtitle_lift`.
2. **Actions > Nuvio C build > Run workflow**, tick "Build even if this official release was already built". The "off" box there switches names off for one build only.
3. Clear the variable to turn everything back on.

The names, with what each one does, are in [`NuvioCFeatures.kt`](app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt) (a name is the line's name in lower case, e.g. `OSD_BADGES` → `osd_badges`). An unknown name stops the build and lists the valid ones. The release notes say what was switched off.

## Updates

A build runs every hour. When Nuvio publishes a release, the same changes are applied on top and a new APK is posted to Releases. If an official update clashes with the details-page and trailer tweaks, the IMDb backup or the cast filmography, the APK is still built without that part and an issue says so.

Licence: [GNU GPL v3.0](./LICENSE), like the official app.
