# Nuvio C

Nuvio C is a personal build of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV. It tracks every official release, including betas, and adds a focused set of refinements to playback, the home screen and day-to-day browsing. Sync, integrations and login work exactly as in the official app, and Nuvio C installs alongside it.

**Download:** [latest release](https://github.com/savantguarded/NuvioTV/releases/latest) · built for armeabi-v7a Android TV devices

## Highlights

### Playback
- **Information-rich controls.** Title logo plus resolution, HDR / Dolby Vision, audio (Dolby Atmos spelled out), source and file-size badges.
- **Subtitles that stay readable.** Bottom subtitles lift clear of the controls and pause screen; subtitles and controls are dimmed during HDR and Dolby Vision playback.
- **Night mode for passthrough setups.** Lifts dialogue and quiet passages, switching a title from bitstream to decoded audio without interrupting playback, with an optional evening schedule.
- **Stream information panel.** Source server, decoder, display mode, HDR metadata, buffering and network health in one panel.
- **Cleaner track lists.** Audio and subtitle tracks are named by language with clear SDH, Forced and Commentary tags.
- **Thoughtful details.** Start over or jump to the previous episode, a pause screen with title and synopsis, cast and about panel, configurable button order.

### Home
- **Featured layout.** A full-screen carousel of hero catalogs above Modern home, with ranks, ratings and a timed page indicator.
- **Extra large cards** for Classic, Modern and Featured, with spacing tuned so the hero is never crowded.
- **Upcoming from your library.** One Upcoming row fed by the Calendar, with release-day tags, watched episodes removed and accurate show names.
- **Smooth start-up.** Opens straight onto the profile screen and hands over to a fully settled Home.

### Calendar
- **List and Month views** of everything you follow, in your time zone, with reminders.
- **Multiple sources.** Your library, MDBList calendar and TMDB "coming soon" titles, de-duplicated.
- **Fresh on open.** Refreshed in the background after a cold start, so stale entries are never shown.

### Details and discovery
- **Ratings in one place.** Rate with half-star precision to Trakt, Simkl and MDBList at once, with a prompt after finishing a film.
- **Richer pages.** Tagline, an Information ledger (release dates, budget, box office, status), next-episode countdown and acting-only filmographies with roles.
- **Roulette.** A genuine shuffle across tens of thousands of well-rated TMDB films and shows, filtered by type and mood, with no repeats within 30 days.

### Performance and reliability
- Tuned for 2 GB TVs: lighter player memory use, compiled-on-install updates, smoother scrolling and sidebar transitions.
- Simkl device login with automatic token renewal; Trakt sessions survive server hiccups.
- In-app updates from this repository, and Nuvio C settings that follow your Nuvio profile to a new TV.

## Feature switches

Every change is isolated behind a switch in [`NuvioCFeatures.kt`](app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt). Switched off, that part behaves exactly like the official app.

To turn changes off without editing code, set the repository variable `NUVIO_C_OFF` (Settings › Secrets and variables › Actions › Variables) to a comma-separated list of lower-case switch names, for example `osd_badges,subtitle_lift`, then run **Actions › Nuvio C build** with *force* ticked.

## How builds work

A workflow checks for new official releases every hour. Each release is rebuilt with the Nuvio C changes applied and published to Releases. If an official change conflicts with an optional part (detail-page trailers, IMDb trailer fallback, cast filmography), the build still ships without that part and opens a single tracking issue.

## Credits

Built on [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) by the Nuvio team, with ideas and code adapted from [ysosrs123/NuvioTV-Fork](https://github.com/ysosrs123/NuvioTV-Fork) (Calendar, stats panel layout, night mode) and [TitansparkDev/NuvioTV](https://github.com/TitansparkDev/NuvioTV) (Roulette concept).

## Licence

[GNU GPL v3.0](./LICENSE), as the official app.
