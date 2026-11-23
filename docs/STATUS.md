# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-23 (round 61)

## Where things stand
- Branch `nuvio-c` = round 61 on official `1.2.0-beta.3` (still the newest official tag on 2026-11-23). CI picks the highest version.
- Only branch left: `nuvio-c`.
- Round 61 (mockup canvas "Nuvio C: Details page round 61 (mockup)", all approved in one build):
  - Hero lines (`HERO_LINES_V61`): `[age rating] • genres • N seasons` under the buttons; `year • duration • badges` under the synopsis; without MDBList, IMDb / TMDB take the ratings row; next-episode badge at the end of the ratings row.
  - Logos (`QUALITY_LOGOS`): Dolby Vision, Dolby Atmos, DTS (any DTS) as white vectors, whole line 17 dp. Sources: Wikimedia Commons "Dolby Vision 2021 logo.svg", "Dolby Atmos.png" (traced), "DTS logo.svg" (letters only). Dolby Digital / DD+ / TrueHD stay text.
  - Background trailer (`TRAILER_BLOCK_DIM`): logo, tagline, buttons, facts, ratings, synopsis, quality line all at 70%; synopsis drops its 72% grey during the trailer (owner left it to Claude); focused button back to 100%.
  - Company chips (`COMPANY_CHIP_TWO_LINES`): two lines at 13 sp, still cut = scroll on focus.
  - Opening (`DETAIL_OPEN_SMOOTH`): Nuvio C hero-shaped placeholder, 180 ms fade, rows join one frame later (fresh opens only). `DETAIL_DEFER_LOADS`: More like this, comments, collection, episode ratings, trailer link wait 1.2 s or until the page scrolls.
  - Taste (`TASTE_TOP_STACK`): list rows fixed 40 dp from the top; Faces from the first slot when the row isn't full.
- Round 61b: opening details crashed (NullPointerException in MetaDetailsViewModel): the defer-gate fields were declared below `init`, which calls loadMeta() first. Moved above `init`.
- Round 60 quality line (Torrentio first, QualiCache fallback, leaf view model) unchanged underneath.
- Tests: `NuvioC*` and `Calendar*` pass. No new tests this round (UI only).
- TV adb from the owner's nuvio-debug folder works from the desktop VM: Linux platform-tools downloaded into the VM home, the folder's adb-key copied to ~/.android.

## Still to check on the TV
- Round 61: logo legibility at 17 dp; 70% block over a trailer (focus lift on each button, synopsis readable); opening fade on a cold and a cached title; Down soon after open loads rows at once; trailer autostart still fires; a series without MDBList shows IMDb / TMDB and the countdown; company names with two lines and the scroll; Taste cards with one or two entries.
- Round 60: quality line for a `tmdb:` id title; series latest-aired episode.
- Marquee: Taste rows / Spin card / faces, Modern Home card titles, collection folders, Calendar reminder rows.

## Open ideas (not approved for building)
- Featured carousel quality badges (on hold, option A).
- Optional focus prefetch for the quality line (spec: ship off; not built).
- Seek thumbnails on the device, no external API. Investigating only.
- On-the-fly episode ordering per show (Miami Vice S1, Kengan Ashura) with correct scrobbling.
- MDBList API call growth during playback (raised 2026-10-31).
- Watchly C lives in its own repo, not here.

## Start of every build session
1. Read `docs/ARCHITECTURE.md`, `docs/FORK_CHANGES.md` and this file.
2. Upstream check: `git ls-remote --tags https://github.com/NuvioMedia/NuvioTV`. Newer than `1.2.0-beta.3`? Scan it for new servers / hosts / secrets, then merge it first. Ask the owner before keeping or dropping any fork or official feature.
3. Environment and commands: `docs/ARCHITECTURE.md` section 7. Attach the repo with push access, clone `nuvio-c`. Tests need `--max-workers=1` and `-Xmx4g`. Maven Central often answers 429 on a cold cache: wait a few minutes and retry.

## End of every build session
1. Compile, run `NuvioC*` and `Calendar*` tests, forced GitHub build.
2. Update `docs/FORK_CHANGES.md` and `docs/ARCHITECTURE.md` if anything changed, rewrite this file, run `scripts/check-docs.py`, commit with the code.

## Standing rules (owner)
- Mockups before building anything new; no dead space or clipping in designs.
- An approved round is built in full in one build; if something can't be done, say so before building.
- Every tweak behind its own switch; never touch decoder selection.
- Works for Nuvio-Sync-only profiles; personal data strictly per profile.
- Always build on the newest fork code. During builds, wait quietly and report once.
