# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-23 (round 62)

## Where things stand
- Branch `nuvio-c` = round 62 on official `1.2.0-beta.3` (still the newest official tag on 2026-11-23). CI picks the highest version.
- Only branch left: `nuvio-c`.
- Round 61 (canvas "Nuvio C: Details page round 61 (mockup)", boards A to J): hero lines, Dolby / DTS logos, trailer block dim, two-line company chips, opening placeholder + fade, deferred loads, Taste top stack. 61b fixed a crash on opening details (defer-gate fields declared after `init`).
- Round 62 (same canvas, boards K to R + N2), owner decisions: hairline A, logos 12 dp, order resolution / source / TrueHD / visual / Atmos, keep one sound label per release (no parser change), REMUX reads BLURAY, block dim 60%, 12 dp rhythm only (no 3-line synopsis, no ratings cap), no MDBList shimmer.
- Opening jank, measured on the TV 2026-11-23 (perfetto + simpleperf, release build is profileable): the first page frame ~210 ms (137 ms composition + 64 ms draw), then ~180 ms of main-thread work with no frames, which was the MDBList user-rating lookup building `MdbListMediaIndex` over the whole history on every call (official code; cached per snapshot under `DETAIL_OPEN_FAST`). The black frame came from the fade's underlay having no picture.
- adb from the desktop VM: Linux platform-tools in the VM home, the folder's `adb-key` copied to ~/.android; target in `grab-crash.bat`. Release APK names are obfuscated; use simpleperf + androguard on the APK to map hot classes.
- Tests: `NuvioC*` and `Calendar*` pass, incl. `NuvioCRound62Test` (label order, years).

## Still to check on the TV
- Round 62: opening feel (cached and cold titles); badge order and 12 dp logos; ratings row icon sizes; a show without ratings (no gap); not-released titles (one separator).
- Round 61:  70% block over a trailer (focus lift on each button, synopsis readable); opening fade on a cold and a cached title; Down soon after open loads rows at once; trailer autostart still fires; a series without MDBList shows IMDb / TMDB and the countdown; company names with two lines and the scroll; Taste cards with one or two entries.
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
