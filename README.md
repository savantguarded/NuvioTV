# Nuvio C

A personal build of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV. It follows every official release, betas included, and adds small refinements to playback, Home and browsing. Sync, integrations and login work as in the official app, and Nuvio C installs alongside it.

**Download:** [latest release](https://github.com/savantguarded/NuvioTV/releases/latest) (armeabi-v7a Android TV)

## What it adds

**Playback**
- Two control layouts in Settings > Playback: Default and Modern. Modern is compact: Subtitles and Audio pills on the left, Play in the centre, icons in one rounded pill on the right, the rest under More. The chapter name sits above the elapsed time.
- Press Left or Right twice on the seek bar to jump chapters; Start over then Previous episode.
- Subtitles lift clear of the controls; subtitles and controls dim during HDR and Dolby Vision.
- Subtitle styling (fonts, background, outline width, shadow), night mode for passthrough setups, a stream information panel, cleaner track names, a pause screen with titles only.

**Home**
- Featured carousel: Play, Details icon and Previous / Next arrows, with one context tag (airs today, finale, season out, new film, in your library). Soft glow on focused posters.
- Upcoming row fed by the Calendar, slim sidebar, a smooth start-up, and a start-up queue that keeps background work off the first seconds.

**Calendar, details, taste**
- List and Month views, library and TMDB "coming soon" sources, bell on any title, exact air times with your TheTVDB key.
- Half-star ratings to Trakt, Simkl and MDBList, richer details pages, trailers, Roulette and Shuffle, and a per-profile Taste page.

## Switches and builds

Every change sits behind a switch in [`NuvioCFeatures.kt`](app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt); off means official behaviour. To turn some off without editing code, set the repository variable `NUVIO_C_OFF` to a comma-separated list of lower-case switch names, then run **Actions > Nuvio C build** with *force* ticked.

A workflow checks for new official releases hourly, rebuilds each with the Nuvio C changes and publishes it. A conflict is reported once per release, not hourly.

## Credits and licence

Built on Nuvio TV by the Nuvio team, with ideas adapted from [ysosrs123/NuvioTV-Fork](https://github.com/ysosrs123/NuvioTV-Fork) and [TitansparkDev/NuvioTV](https://github.com/TitansparkDev/NuvioTV). [GNU GPL v3.0](./LICENSE).
