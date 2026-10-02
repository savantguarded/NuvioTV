# Nuvio C

A personal build of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) for Android TV. It is the official app with a few small changes, and installs alongside it.

**Download:** [latest Nuvio C release](https://github.com/savantguarded/NuvioTV/releases/latest)

## What's different from official

**Player**
- The title's logo shows top-left on the player controls, appearing a few seconds after playback starts so it doesn't clash with the parental guide, HDR/Dolby Vision popups or the stats screen.
- Year shown for movies only, not series. Fixes the missing year on titles opened from Continue Watching.
- Thinner, brighter seek bar (YouTube style) and a taller bottom shadow. The "via" source line is hidden.

**Details page: background trailers** (optional, off by default)
- Turn on in Settings > Layout > Background trailers.
- The trailer plays muted and full-screen behind the page, once per visit, with letterbox bars zoomed away.
- It starts after your autoplay delay wherever you are on the page, and pauses while comments, menus or cast pages are open.
- Press the trailer button for sound. Press Back to stop it.
- With the setting off, the page works exactly like official.

**Accounts and updates**
- Simkl sign-in uses Simkl's newer login, so it stays signed in on its own.
- Settings > About > Check for updates gets new Nuvio C releases from this repo.

## How it's kept up to date

A build runs automatically every hour. When Nuvio publishes a new release, the same changes are applied on top and a new Nuvio C APK is posted to Releases. If an official update clashes with the background trailers, the APK is still built, just without them.

Everything else (sync, Trakt, MDBList, TorBox, add-ons) is the official app, unchanged.

---

*The official Nuvio TV readme follows.*

<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio" width="300" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioTV/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Get Nuvio TV

- [Android TV on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android TV APK](https://github.com/NuvioMedia/NuvioTV/releases/latest)

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioTV.git
cd NuvioTV
./gradlew :app:assembleFullDebug
```

Nuvio TV is built with Kotlin, Jetpack Compose, TV Material 3, and Media3. Development requires Android Studio, a JDK, and the Android SDK.

## License

[GNU General Public License v3.0](./LICENSE)
