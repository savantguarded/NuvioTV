# Nuvio C

Nuvio C is a personal fork of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV. It tracks every official release, betas included, and layers a set of user-experience features on top of it. Accounts, Nuvio Sync, add-ons and integrations behave exactly as in the official app, and Nuvio C installs side by side with it (`com.nuvio.tv.c`).

**Download:** [latest release](https://github.com/savantguarded/NuvioTV/releases/latest) (Android TV, armeabi-v7a)

## Features

**Player**
- Two control layouts: Default and Modern (compact pills, centred transport, icon group, More sheet).
- Title logo, release quality badges and per-chapter seeking in the on-screen controls; cast, crew and synopsis side panel.
- Subtitle engine refinements: lift above the controls, font and style options, SDH handling, HDR and Dolby Vision dimming.
- Per-format audio passthrough with on-device multichannel decoding for formats the receiver does not support, plus a scheduled night mode.
- Stream information panel and a redesigned loading and pause screen.

**Details**
- Redesigned hero with title logo, facts, ratings from MDBList, IMDb and TMDB, and a quality line built from real releases (resolution, source, HDR, audio and editions).
- Series ratings graph: every episode rating in a season-by-episode grid with season averages.
- Multi-tracker ratings (Trakt, Simkl, MDBList), background trailers with an IMDb fallback, and a full filmography on cast pages.

**Home and browsing**
- Featured layout: a full-screen carousel with contextual tags.
- Calendar with list and month views, release reminders and an Upcoming row on Home.
- Roulette for picking something to watch from a large film and series pool, with filters.
- Taste: per-profile viewing statistics, discovery and a yearly Wrapped, each card opening a detailed view.
- Library list picker, Watched tab and random pick for every source, including Nuvio Sync.

**Profiles**
- Profile screen with a per-profile background from trending titles or chosen catalogs.
- Fork settings stored per profile and synced through the Nuvio account.

## Switches and builds

Every change sits behind a switch in [`NuvioCFeatures.kt`](app/src/main/java/com/nuvio/tv/NuvioCFeatures.kt); off restores official behaviour. Switches can also be turned off without code changes by setting the repository variable `NUVIO_C_OFF` to a comma-separated list of lower-case switch names and running **Actions > Nuvio C build** with *force* ticked.

A scheduled workflow picks up new official releases, applies the Nuvio C changes and publishes the build.

## Credits and licence

Built on Nuvio TV by the Nuvio team, with ideas adapted from [ysosrs123/NuvioTV-Fork](https://github.com/ysosrs123/NuvioTV-Fork) and [TitansparkDev/NuvioTV](https://github.com/TitansparkDev/NuvioTV). Licensed under the [GNU GPL v3.0](./LICENSE).
