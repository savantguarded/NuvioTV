# Nuvio C

Nuvio C is a personal build of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV. It tracks every official release, including betas, and adds a focused set of refinements to playback, the home screen and day-to-day browsing. Sync, integrations and login work exactly as in the official app, and Nuvio C installs alongside it.

**Download:** [latest release](https://github.com/savantguarded/NuvioTV/releases/latest) · built for armeabi-v7a Android TV devices

## Highlights

### Playback
- **Information-rich controls.** Title logo plus resolution, HDR / Dolby Vision, audio (Dolby Atmos spelled out), source and file-size badges, a slimmer high-contrast seek bar and a label under the focused button.
- **Subtitles that stay readable.** Bottom subtitles lift clear of the controls and pause screen; subtitles and controls are dimmed during HDR and Dolby Vision playback.
- **Subtitle styling.** Choice of subtitle font, background box, outline width and an option to hide SDH tracks. Your saved subtitle carries to the next episode, with SDH only as a fallback.
- **Night mode for passthrough setups.** Lifts dialogue and quiet passages, switching a title from bitstream to decoded audio without interrupting playback, with an optional evening schedule.
- **Stream information panel.** Source server, decoder, display mode, HDR metadata, buffering and network health in one panel that fits the screen.
- **Cleaner track lists.** Audio and subtitle tracks are named by language, with the track's own description underneath.
- **Faster sources.** Up always reaches the first source, and the filter tabs disappear when only one source is installed.
- **Thoughtful details.** Start over or jump to the previous episode, a pause screen with title and synopsis, cast and about panel, filename on the loading screen, no forced next-episode countdown, and Watch Party tucked into the extra controls.

### Home
- **Featured layout.** A full-screen carousel of hero catalogs above Modern home, with ranks, top ratings and a timed page indicator.
- **Extra large cards** for Classic, Modern and Featured, with spacing tuned so the hero is never crowded.
- **Readable hero text.** A compact four-line description that scrolls like the official app when it runs long.
- **Upcoming from your library.** One Upcoming row fed by the Calendar, with release-day tags, watched episodes removed and accurate show names.
- **Profiles and personalisation.** A cleaner profile screen, a choice of app fonts and an on-screen clock while browsing.
- **Smooth start-up.** The Nuvio logo from the first frame with a soft, angled shimmer while loading, the profile screen fading in once it is ready, then a plain fade onto Home.
- **Slim sidebar.** A narrower Modern sidebar with Settings pinned at the bottom.
- **Ambient mode you can act on.** Press OK on a screensaver slide to open that title.

### Calendar
- **Week, List and Month views** of everything you follow, in your time zone, with reminders.
- **Multiple sources.** Your library, MDBList calendar and TMDB "coming soon" digital releases, de-duplicated.
- **Bell any title.** A bell next to Play on any details page follows that title in the Calendar and the Upcoming row and reminds you once it is out, no library needed.
- **Fresh on open.** Refreshed in the background after a cold start, so stale entries are never shown.
- **Exact air times.** Add your own TheTVDB key in Settings > Integrations to fill in times TVmaze lacks.

### Details and discovery
- **Ratings in one place.** Rate with half-star precision to Trakt, Simkl and MDBList at once, with a prompt once a film counts as watched.
- **Richer pages.** Tagline, an Information ledger (release dates, budget, box office, status, awards with your own OMDb key), next-episode countdown and acting-only filmographies with roles.
- **Trailers.** Background trailers with a mute setting and one-press exit, a full seek bar and logo on the trailer screen, and IMDb trailers as a fallback when YouTube limits quality.
- **Roulette and Shuffle.** A genuine shuffle across tens of thousands of TMDB films and shows (Curated, Wide or Deep pool), filtered by type and mood and refined by decade, region, length and acclaim, an Episodes mode for a random episode of your shows, every rating you use shown and no repeats within 30 days, plus a Shuffle button on series pages and a random pick in the Library.

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
