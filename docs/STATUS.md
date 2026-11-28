# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-28 (round 68)

## Where things stand
- Branch `nuvio-c` = round 68 on official `1.2.0-beta.4`. No newer official release (checked 2026-11-28).
- Round 68 (plan: project doc claude/round68-build-plan.md, canvas "Nuvio C: Round 68"): Service Health copy / hold / check order (`HEALTH_V68`, `HEALTH_HOLD_5MIN`, `HEALTH_CHAIN_V68`), OSD About (`OSD_ABOUT_V68`), reel and Roulette halo (`REEL_GLOW_V68`), Taste faces and 12-hour axis (`TASTE_V68`), Calendar List view (`CALENDAR_V68`). Details in FORK_CHANGES.md.
- Built: forced GitHub build of 2026-11-28 compiled and released `1.2.0-beta.4+nuvioc`; no optional patch dropped.
- Not yet run: `NuvioC*` / `Calendar*` unit tests. Maven Central answered 429 all session, so the local compile never got its dependencies; the owner chose to push and let GitHub compile. New `NuvioCRound68Test` and the updated `NuvioCRound67bTest` must be run in the next session before anything else.
- OSD About: only the safe parts were built (loaded flag, per-title lookup cache, trace slice); no perfetto trace yet, so no before / after frame times.
- A status source switched, moved, added or removed checks at once, past the 5-minute hold (the hold only stops page opens and Check Again).
- Choices left for the owner: Editions share (Apocalypse Now Final Cut, 14.2% under the 15% rule; one constant in TitleQualityParser.kt).
- Local builds: one Gradle run at a time; `-Xmx4g` is enough; Maven Central 429s can last a whole session.

## Requests audit (round 68, item 3)
- Service Health: no background runs; page open (after the hold) or Check Again only; with 2 sources on a run is 2 source GETs + TorBox account + TV checks for leftovers only (debug log line `NuvioCHealth run:`); a 429 from a source ends it for that run.
- TMDB background callers: Taste enrichment one details call (credits appended) per new title, 4 at a time, up to 1,500 per build, 60-day cache (a first build on a big history is the only burst that could near ~50/s); Calendar TMDB Coming soon up to 10 discover pages + 70 / 300 detail checks one at a time, 24 h check cache; Roulette pool 3 at a time, 12 h pool, totals cached 24 h; profile background pool once a day; digital gate one release-dates call per recent film, 12 h; Featured quality checks start 4 s after open, one at a time. No repeat-without-cache found; nothing changed.

## Still to check on the TV
- Round 68: Add-ons row shield, no dots, "Checked … ago"; Service Health capitals, Uptime Tracker header, hosts under names with the dot on the name line, columns to the bottom, Check Again greyed with "Available in N min", reopening within 5 min doesn't re-check; Status Sources: two on, Move Up / Down, This TV last, missing item handed to source 2, bad source skipped; OSD About no gap without ratings, no jump with them; reel ring and halo through the spin and YOUR PICK (halo not cut top / bottom), Movies / Shows cards halo; Taste 12-hour axis, no "Titles together", face list (one-title face too), Back to row then face; Calendar rows without bell, Right / Left with the side card, side card sizes, no rating, no clipping at the top or bottom of the list or card.
- Round 67b and earlier: see git history of this file (67b Service health columns, 67 profile shade, Roulette blur, carousel Play texts, digital gate, episode ratings timing, collection meta order).

## Open ideas (not approved for building)
- Optional focus prefetch for the quality line (spec: ship off; not built).
- Seek thumbnails on the device, no external API. Investigating only.
- On-the-fly episode ordering per show (Miami Vice S1, Kengan Ashura) with correct scrobbling.
- MDBList API call growth during playback (raised 2026-10-31).
- Watchly C lives in its own repo, not here.

## Start of every build session
1. Read `docs/ARCHITECTURE.md`, `docs/FORK_CHANGES.md` and this file.
2. Upstream check: `git ls-remote --tags https://github.com/NuvioMedia/NuvioTV`. Newer than `1.2.0-beta.4`? Scan it for new servers / hosts / secrets, then merge it first. Every conflict goes to the owner before anything is kept or dropped.
3. Environment and commands: `docs/ARCHITECTURE.md` section 7. Attach the repo with push access, clone `nuvio-c`. Tests and compile need `--max-workers=1` and `-Xmx4g`. Maven Central often answers 429 on a cold cache: wait a few minutes and retry.

## End of every build session
1. Compile, run `NuvioC*` and `Calendar*` tests, forced GitHub build.
2. Update `docs/FORK_CHANGES.md` and `docs/ARCHITECTURE.md` if anything changed, rewrite this file, run `scripts/check-docs.py`, commit with the code.

## Standing rules (owner)
- Mockups before building anything new; no dead space or clipping in designs.
- An approved round is built in full in one build; if something can't be done, say so before building.
- Every tweak behind its own switch; never touch decoder selection.
- Works for Nuvio-Sync-only profiles; personal data strictly per profile.
- Always build on the newest fork code. During builds, wait quietly and report once.
