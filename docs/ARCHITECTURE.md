# Nuvio C architecture map

Read this first in every session. It tells you where things are so you open only the files a task needs.
Fork-specific behaviour (what each tweak does, its switch, its screen) lives in `docs/FORK_CHANGES.md`.
Current state, last build and open issues live in `docs/STATUS.md`.

Last verified against: branch `nuvio-c` @ `122312e8`, official base `1.2.0-beta.3` (`552cacf5`), 2026-11-20.

## 1. What this is

- Nuvio C = Charles's fork of official NuvioTV (Android TV app, `NuvioMedia/NuvioTV`).
- Target device: TCL C655, Android 12, 2 GB RAM, `armeabi-v7a`.
- Installs beside the official app: package `com.nuvio.tv.c`, label "Nuvio C". Both are set at build time by CI, not in the source (source still says `com.nuvio.tv`).
- Uses official Nuvio's backend (Supabase sync, QR login) with the Nuvio team's approval.
- Rule of the fork: official structure stays; every tweak sits behind a switch and can be turned off or reverted on its own.

## 2. Stack

| Area | What |
|---|---|
| Language / UI | Kotlin 2.3, Jetpack Compose (BOM 2026.05.01) + Compose for TV (`tv-foundation`, `tv-material`) |
| DI | Hilt 2.58 (`@HiltViewModel`, modules in `core/di/`) |
| Network | Retrofit + OkHttp + Moshi; Ktor + supabase-kt for Nuvio sync |
| Images | Coil 3 |
| Storage | DataStore (`data/local/`), SharedPreferences for fork settings (`core/nuvioc/NuvioCPrefs.kt` and others) |
| Players | ExoPlayer from local Media3 AARs in `app/libs/`, plus mpv (`mpv-android-lib 0.2.1`), libass AAR for subtitles |
| Native | `app/src/main/cpp/` (Dolby Vision bridge), `DV7/libdovi/` prebuilt libs, `ffmpeg-decoder-downmix/` module |
| Build | AGP 8.13, Gradle Kotlin DSL, versions in `gradle/libs.versions.toml`, SDK compile/target 36, min 24 |

Gradle modules (`settings.gradle.kts`): `:app`, `:baselineprofile`, `:ffmpeg-decoder-downmix`.
Flavors: `full` (sideload, what Nuvio C ships) and `playstore` (unused by the fork). Code under `app/src/full/` and `app/src/playstore/` is flavor-only.

## 3. Folder map

Root (skip anything marked "ignore"):

| Path | What |
|---|---|
| `app/` | The whole app. Almost every task lives here. |
| `app/src/main/java/com/nuvio/tv/` | Source root (1,242 Kotlin files). Called `src/` below. |
| `app/src/test/java/` | JVM unit tests (528 files, 56 are `NuvioC*Test`). |
| `app/src/full/java/com/nuvio/tv/updater/` | In-app updater (fork: `NuvioCUpdateBuild.kt`, `NuvioCUpdatedReceiver.kt`). |
| `app/src/main/res/`, `app/src/main/assets/` | Resources. Fork files are prefixed `nuvio_c_` (icons, fonts, `assets/nuvio_c_subfonts/`). |
| `app/libs/` | Prebuilt AARs (Media3, libass, QuickJS, Nuvio engine). Ignore unless changing a decoder. |
| `.github/workflows/nuvio-c.yml` | The Nuvio C build robot. See section 7. |
| `.github/workflows/nuvio-c-ysos-watch.yml` | Daily check for new ysosrs123 calendar commits (opens issues only). |
| `scripts/nuvio-c-calendar-sync.sh` | Takes in ysosrs123 calendar code (vendor branch `ysos-calendar`). |
| `docs/` | These docs, plus upstream's `essential-mode.md` and `architecture/mdblist-library.md`. |
| `DV7/`, `org/`, `libmpv-android/`, `baselineprofile/`, `.idea/`, `.kotlin/` | Ignore: native/vendored/tooling. |

Inside `src/`:

| Path | What |
|---|---|
| `MainActivity.kt` | App start, startup splash / loading, profile gate, key handling, hosts `NuvioNavHost`. 2,900 lines, heavily forked. |
| `NuvioApplication.kt` | Application class, Hilt entry. |
| `NuvioCFeatures.kt` | **All fork switches** (`const val X = true/false`), grouped by round. |
| `ui/navigation/Screen.kt` | Every route (Home, Detail, Stream, Player, Calendar, Shuffle, Taste, Settings…). |
| `ui/navigation/NuvioNavHost.kt` | Route to screen wiring. |
| `ui/screens/<feature>/` | One folder per screen: `home`, `detail`, `stream`, `player`, `calendar`, `cast`, `library`, `profile`, `search`, `settings`, `collection`, `shuffle` (Roulette), `taste`, `ambient`, `party`, `account`, `addon`, `plugin`, `tmdb`. |
| `ui/components/` | Shared composables (cards, hero carousel, sidebar, trailer player, badges). Fork ones start `NuvioC`. |
| `ui/theme/` | Colours, typography (`Type.kt` holds the extra fonts). |
| `core/` | Non-UI logic: `auth`, `sync`, `tracking`, `calendar`, `player`, `tmdb`, `streams`, `debrid`, `torrent`, `server` (local web config pages), `di`, `network`, `nuvioc` (fork core). |
| `data/` | Data layer: `repository/` (incl. all Trakt services), `remote/` (API + Supabase DTOs), `local/` (DataStores), `mdblist/`, `simkl/`, `trailer/`, `mediaserver/` (Jellyfin/Emby), `nuvioc/` (fork data: ratings, Roulette pool, Taste). |
| `domain/` | Models and repository interfaces. |

## 4. Entry points

- **App start**: `NuvioApplication` then `MainActivity.onCreate` then `setContent`: startup splash (`ui/components/StartupSplashScreen.kt`, `NuvioCOpening.kt`, `core/nuvioc/NuvioCStartup.kt`), then profile picker (`ui/screens/profile/ProfileSelectionScreen.kt`), then `NuvioNavHost` starting at Home.
- **Each screen**: a `*Screen.kt` composable plus a `*ViewModel.kt` (Hilt) in its folder.
- **Player**: `ui/screens/player/PlayerScreen.kt` (UI, 6,000 lines) driven by `PlayerViewModel.kt`, which owns `PlayerRuntimeController.kt`. The controller is split by concern into `PlayerRuntimeController*.kt` files (Initialization, Streams, Tracks, PlaybackEvents, Mpv, EngineFailover…).
- **Background jobs**: `core/sync/StartupSyncService.kt`, `core/calendar/CalendarRefresher.kt`, `core/recommendations/TvRecommendationManager.kt`.
- **Local config web pages** (phone setup over LAN): `core/server/*ConfigServer.kt`.

## 5. Module dependencies

```
ui/screens/*  ->  ViewModels  ->  domain/repository (interfaces)
                                     |
                                     v
                    data/repository/*Impl, data/mdblist, data/simkl, data/nuvioc
                                     |
              data/remote (Retrofit, Supabase)   data/local (DataStore)
core/*  : used by both ui and data (sync, tracking, player helpers, tmdb, calendar)
core/di : Hilt modules binding the above
NuvioCFeatures : read from anywhere a fork tweak branches
```

## 6. Data flow for key features

**Home**
`HomeViewModel` + `HomeViewModelCatalogPipeline` / `PresentationPipeline` / `ContinueWatching` load catalogs from add-ons (`CatalogRepositoryImpl`) and progress from the active tracker. Layouts: `ClassicHomeContent.kt`, `ModernHomeContent.kt` + `ModernHomeHero.kt`, Featured via `NuvioCHomeFeatured.kt`. Grid exists upstream but is hidden by `NO_GRID_LAYOUT`.

**Details page**
`MetaDetailsViewModel` loads meta through `MetaRepositoryImpl` (your meta add-on, e.g. AIOMetadata; TMDB enrichment via `core/tmdb/`). UI: `MetaDetailsScreen.kt`, `HeroSection.kt`, row sections (`EpisodesSection`, `CastSection`, …). Background trailer: `ui/components/BackgroundTrailerLayer.kt` + `core/player/BackgroundTrailerController.kt`; trailer sources in `data/trailer/` (YouTube, IMDb backup `NuvioCImdbTrailerResolver.kt`).

**Play a title**
Details then `ui/screens/stream/StreamScreen.kt` (`StreamRepositoryImpl` asks add-ons, debrid in `core/debrid/`) then `Screen.Player` then `PlayerViewModel`/`PlayerRuntimeController` picks ExoPlayer or mpv (`NuvioMpvSurfaceView.kt`). OSD drawn in `PlayerScreen.kt` plus fork `NuvioCOsd*.kt`. Progress and scrobbles go to `core/tracking/` providers.

**Tracking / sync**
`core/tracking/Tracking*Provider.kt` abstract Trakt (`data/repository/Trakt*`), Simkl (`data/simkl/`), MDBList (`data/mdblist/`). Nuvio account sync (library, progress, settings, profiles) in `core/sync/*SyncService.kt` over Supabase (`data/remote/supabase/`). Profiles: `core/profile/ProfileManager.kt`; per-profile stores are profile-scoped.

**Calendar / Upcoming**
`core/calendar/` (refresh, merge, reminders, TVDB/TVMaze/MDBList/TMDB sources) feeds `ui/screens/calendar/` and the Home Upcoming row (`ui/screens/home/HomeComingUp.kt`). Largely ported from ysosrs123; keep `scripts/nuvio-c-calendar-files.txt` in mind before editing.

**Fork-only screens**
Roulette `ui/screens/shuffle/` + `data/nuvioc/NuvioCShuffle*.kt`, `NuvioCRoulette*.kt`. Taste `ui/screens/taste/` + `data/nuvioc/NuvioCTaste*.kt`. Ratings `data/nuvioc/NuvioCRatingService.kt`.

## 7. Build, run, test

**How releases are made (CI, `nuvio-c.yml`)**
1. Hourly (or manual "force"): reads the newest official release tag.
2. Takes the diff of `nuvio-c` against official and replays it onto that release as patches: a core patch plus three optional patches (trailer, IMDb backup, cast filmography; file lists in the workflow's `TRAILER_PATHS` / `IMDB_PATHS` / `FILMOGRAPHY_PATHS`). An optional patch that conflicts is dropped, not the build.
3. Applies branding (name, `com.nuvio.tv.c`, updater points at this repo, `armeabi-v7a` only) and the `NUVIO_C_OFF` switch list.
4. `./gradlew :app:assembleFullRelease`, publishes release tag `<official>+nuvioc` with the APK.
5. Opens an issue if upstream edited OSD files listed in `OSD_FILES`.
CI does not run unit tests. Run them locally before pushing.

Manual build: `gh workflow run nuvio-c.yml -R savantguarded/NuvioTV --ref nuvio-c -f force=true`

**Local compile in a Claude session (about 7 GB RAM)**
- SDK into `/home/claude/android-sdk` (`platforms;android-36`, `build-tools;36.0.0`, `platform-tools`), `local.properties`: `sdk.dir=...`. Dummy `TRAKT_CLIENT_ID` / `TRAKT_CLIENT_SECRET` needed for Trakt tests. Keys list: `local.example.properties`.
- Compile: `./gradlew :app:compileFullDebugKotlin --no-daemon -q --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx5g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"`
- Tests (same flags): `:app:testFullDebugUnitTest --tests '*NuvioC*' --tests '*Calendar*'`
- Known noise: Maven Central 429 on cold cache (retry, `--max-workers=1`); `checkFullDebugAarMetadata` "path is null" once (retry).

**TV checks**: adb from Charles's PC folder `nuvio-debug`, TV `192.168.100.5:5555`, `adb connect` on every call.

## 8. Conventions

- **Switches**: every tweak reads a `NuvioCFeatures` constant. `false` = official behaviour. New work goes in a new `// ---- Round N ----` block. Switches set to `false` are reverted tweaks whose code is still present.
- **Markers**: fork edits inside official files carry a `// [fork]` comment (about 2,400 of them). New fork files are named `NuvioC*.kt`; fork resources `nuvio_c_*`; fork UI text in `app/src/main/res/values/strings_nuvio_c.xml`.
- **Keep official files lean**: put fork logic in a `NuvioC*` file and call it from the official file with a small hook. Fewer edited lines = easier upstream merges.
- **Commits**: `Nuvio C: <what>` or `Nuvio C round N: <what>`; upstream merges as `Merge official <tag> into Nuvio C`. One commit per item where possible.
- **Git**: upstream's `.gitignore` ignores `docs/` and `scripts/*`, so new files there need `git add -f`.
- **Tests**: fork tests are `NuvioC*Test.kt` mirroring the source package.
- **Per profile**: anything personal (Taste, ratings, Roulette history) must be keyed by profile and work for Nuvio-Sync-only profiles.
- **Never**: touch decoder selection for fork features; change official sync payloads; ship partial builds of an approved round.

## 9. Where to look first

| Task | Open first |
|---|---|
| Player controls (OSD), badges, buttons | `ui/screens/player/NuvioCOsd*.kt`, then `PlayerScreen.kt` (`PlayerControlsOverlay`) |
| Subtitles (lift, font, SDH, style) | `player/NuvioCSubtitle*.kt`, `NuvioCSdh.kt`, `SubtitleSelectionOverlay.kt`, `PlayerRuntimeControllerTracks.kt` |
| Audio, passthrough, night mode | `player/NuvioCPassthrough.kt`, `NuvioCNightMode.kt`, `AudioSelectionOverlay.kt`, `GainAudioProcessor.kt` |
| HDR / Dolby Vision dim or detection | `player/NuvioCHdrDim.kt`, `NuvioCDecodedColor.kt`, `NuvioCSpsColor.kt` |
| Playback start, sources, engine | `PlayerRuntimeControllerStreams.kt`, `…Initialization.kt`, `StreamSourcesSidePanel.kt`, `ui/screens/stream/` |
| Stats / info panel | `player/NuvioCStatsPanel.kt`, `NuvioCPlayerInfo.kt`, `NuvioCPlaybackStats.kt` |
| Home rows, hero, Featured | `ui/screens/home/` (`ModernHome*`, `ClassicHomeContent.kt`, `NuvioCHome*`, `NuvioCHero*`), `ui/components/HeroCarousel.kt` |
| Card sizes, posters | `ui/components/NuvioCCardSize.kt`, `ContentCard.kt`, `PosterCardDefaults.kt` |
| Details page | `ui/screens/detail/MetaDetailsScreen.kt`, `HeroSection.kt`, `NuvioCDetail*.kt` |
| Background trailers | `ui/components/BackgroundTrailerLayer.kt`, `NuvioCTrailer*.kt`, `core/player/BackgroundTrailerController.kt`, `data/trailer/` |
| Ratings dialog / sync to trackers | `data/nuvioc/NuvioCRatingService.kt`, `core/tracking/TrackingRatings.kt` |
| Calendar / Upcoming row | `core/calendar/`, `ui/screens/calendar/`, `ui/screens/home/HomeComingUp.kt` |
| Cast page / filmography | `ui/screens/cast/`, `core/tmdb/NuvioCFilmography.kt` |
| Roulette | `ui/screens/shuffle/`, `data/nuvioc/NuvioCShuffle*.kt`, `NuvioCRoulette*.kt` |
| Taste page | `ui/screens/taste/`, `data/nuvioc/NuvioCTaste*.kt` |
| Profile screen | `ui/screens/profile/` (`NuvioCProfile*.kt`), `ui/components/NuvioCProfileBg.kt` |
| Startup / splash / jank | `MainActivity.kt`, `core/nuvioc/NuvioCStartup*.kt`, `ui/components/StartupSplashScreen.kt`, `NuvioCOpening.kt`, `core/NuvioCPerf.kt` |
| Sidebar | `ui/components/SidebarNavigation.kt`, `NuvioCSidebarSettle.kt`, `src/ModernSidebarBlurPanel.kt` |
| Settings UI for a fork option | `ui/screens/settings/NuvioC*Settings.kt`, then the matching `Layout*Settings.kt` / `Playback*Settings.kt` |
| Fork prefs storage | `core/nuvioc/NuvioCPrefs.kt`, `NuvioCCloudSettings.kt` (fork toggles synced per profile) |
| Trakt / Simkl / MDBList | `data/repository/Trakt*.kt`, `data/simkl/`, `data/mdblist/`, `core/tracking/` |
| API keys (TVDB, OMDb, TMDB) | `core/nuvioc/NuvioCApiKeys.kt`, `ui/screens/settings/NuvioCApiKeySettings.kt`, `app/build.gradle.kts` |
| Fonts | `ui/theme/Type.kt`, `res/font/nuvio_c_*`, `player/NuvioCSubtitleFont.kt` |
| In-app updater | `app/src/full/java/com/nuvio/tv/updater/`, `src/updater/`, `ui/screens/settings/` (update section) |
| Build robot / releases | `.github/workflows/nuvio-c.yml` |
| Merging a new official release | `docs/FORK_CHANGES.md` (hotspot files), then `git diff <old-tag> <new-tag> --stat` |

**Hotspots** (official files with the most fork lines, check these first after any upstream merge): `PlayerScreen.kt` (~1,750), `SubtitleSelectionOverlay.kt` (~580), `MainActivity.kt` (~440), `HeroSection.kt`, `ProfileSelectionScreen.kt`, `MetaDetailsScreen.kt` (~270 each), `AudioSelectionOverlay.kt` (~200).

## 10. Keeping this file true

Update it in the same commit as any change that adds, moves or removes a folder, screen, entry point, build step or convention. Bump "Last verified against" at the end of every build session, then run `python3 scripts/check-docs.py` (fails on dead paths, unknown switches, a wrong "Switched off" list or a doc over its line limit).
