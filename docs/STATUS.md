# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-23 (round 64c)

## Where things stand
- Branch `nuvio-c` = round 64c on official `1.2.0-beta.3` (still the newest official tag on 2026-11-23). CI picks the highest version.
- Only branch left: `nuvio-c`.
- Round 61 (canvas "Nuvio C: Details page round 61 (mockup)", boards A to J): hero lines, Dolby / DTS logos, trailer block dim, two-line company chips, opening placeholder + fade, deferred loads, Taste top stack. 61b fixed a crash on opening details (defer-gate fields declared after `init`).
- Round 62 (same canvas, boards K to R + N2), owner decisions: hairline A, logos 12 dp, order resolution / source / TrueHD / visual / Atmos, keep one sound label per release (no parser change), REMUX reads BLURAY, block dim 60%, 12 dp rhythm only (no 3-line synopsis, no ratings cap), no MDBList shimmer.
- Opening jank, measured on the TV 2026-11-23 (perfetto + simpleperf, release build is profileable): the first page frame ~210 ms (137 ms composition + 64 ms draw), then ~180 ms of main-thread work with no frames, which was the MDBList user-rating lookup building `MdbListMediaIndex` over the whole history on every call (official code; cached per snapshot under `DETAIL_OPEN_FAST`). The black frame came from the fade's underlay having no picture.
- adb from the desktop VM: Linux platform-tools in the VM home, the folder's `adb-key` copied to ~/.android; target in `grab-crash.bat`. Release APK names are obfuscated; use simpleperf + androguard on the APK to map hot classes.
- Round 63 (owner asked to build without a mockup while away; no adb this time, so not checked on the TV): runtime hr / min, wider dot and badge spacing, boxed Dolby logos, row / Continue Watching picture handoff, staggered held-back loads, facts line off the info store, Taste return focus, Awards one body per line. Round 62 measured: first page frame ~120 ms, no frozen gap; remaining 60 to 80 ms frames as rows join.
- Round 64 (mockups in chat 2026-11-23, owner approved all in one build): quality line order resolution / source / visual / sound / Dolby Vision / Atmos, DD and DD+ official logos, DTS family back, editions from any release (two max, Extended and Director's Cut first); OSD badges in the same order with the same logos, 1080P in capitals, no channels, no hairline (size tile is enough); More like this and Ratings as rows, Trailers gone, one row header look; OSD icon warm-up. Live check 2026-11-23: Torrentio answered (Apocalypse Now, 367 streams; saved as the `apocalypse_now` test fixture), QualiCache answered 401 without a key (up; the CI key is not readable here).
- Round 64b: series pages stuck at the cast (Down jumped to Comments, not built yet) fixed; Collection above More like this; DTS suffix on the logo's bottom edge; DD+ Atmos was decoded to PCM whenever a passthrough switch was off (FFmpeg preferred for every track since round 51; checked on the TV: Dark Matter DDP5.1 Atmos WEB-DL went out as 6 ch PCM over eARC; the soundbar's eARC list is AC3, E-AC3, E-AC3 JOC, DTS, DTS-HD, no TrueHD / MAT) fixed by `PASSTHROUGH_KEEP_OTHERS`; trace labels for the slow-spot session (`TRACE_LABELS`).
- Round 64c (traced with `TRACE_LABELS` on 64b): scrolling a series page down / up: frames avg 5-7 ms, hitches 30-70 ms were rows rebuilt as they came back into view (`DETAIL_CACHE_WINDOW`); Back out of the player held the key ~190 ms in ExoPlayer.release() (`PLAYER_EXIT_FAST`); details open first frame still ~260-400 ms (composition of the page; not attributed further, release names are obfuscated). Also Ratings before More like this, `RATINGS_STEADY`, `ROW_LOADING_NAV`, `OSD_DDP_ATMOS_NAME`, `PASSTHROUGH_TRACK_PICK`. Owner noted (not built): a series-graph button opening ratings in its own window, only if the Ratings row still hurts scrolling.
- Trace 2026-11-23 (owner using the app, `logs/r64.pftrace` on the PC): 8% janky frames; first OSD open read ~20 vector icons in one frame (fixed by `OSD_WARMUP`); one 537 ms key press and 110-180 ms details-open frames remain unattributed (release build is obfuscated; needs simpleperf next time).
- Tests: `NuvioC*` and `Calendar*` pass (`CalendarRefresherTest.aShowGoneFromASourceReadInFullLeavesTheCalendar` failed once on 2026-11-23 and passed on rerun: flaky), incl. `NuvioCRound64Test` (order, sound formats, editions, fixture, cache), `NuvioCRound64OsdTest`, `NuvioCRound62Test` (label order, years) and `NuvioCRound63Test` (hr / min, awards lines).

## Still to check on the TV
- Round 64c: DTS + DD+ release starts on DTS (when it is the default / better track); Back out of the player feel; scrolling back up a series page; Search rows while loading; OSD Atmos logo on Dark Matter.
- Round 64b: DD+ Atmos passes through with DTS switched off (audio_flinger shows a non-PCM direct output); DTS still decoded; series scroll past the cast; small resistance scrolling up from lower rows (not yet looked at).
- Round 64: Apocalypse Now (Extended + Final Cut expected by the rules), a DD+ WEB-DL title, a DTS Blu-ray, editions fading in after the badges, row headers and focus moving Cast -> More like this -> Ratings -> Collection on a series, OSD badges with logos, first OSD open feel.
- Round 63: everything (built while the owner was away): opening from a Home row and from Continue Watching (picture behind the placeholder), boxed Dolby logos, runtime text, Taste Back from a list row, Awards lines.
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
