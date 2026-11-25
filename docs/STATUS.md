# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-25 (round 65)

## Where things stand
- Branch `nuvio-c` = round 65 on official `1.2.0-beta.3`, plus beta.4's trailer "audio without video" fix alone. The beta.4 merge (Usenet, Simkl rewatch runs, BDAV, subtitle auto-sync, stream ranking) was held back by the owner for a later build; plan item 1 in the project doc claude/round65-build-plan.md still applies.
- Round 65 (plan items 2.1 to 2.15, all built, owner chose not to install over adb this time): README, Calendar zone line + steady side panel, filmography tab counts, YOU RATED THIS, ratings line everywhere, Roulette / Library reel spin, editions by seeders, series ratings graph (Ratings row gone; slimmer hero row and Play / Open pills app-wide: Featured, Roulette result, Library Random pick), Taste card pages, Featured and profile backdrops whole, profile background per profile + sync, Roulette settings sync, steady stream info, synopsis logo, lists menu Save jump.
- Tests: `NuvioC*` and `Calendar*` pass (588; `CalendarRefresherTest.aShowGoneFromASourceReadInFullLeavesTheCalendar` flaky again, passed on rerun). New: `NuvioCRound65Test` (graph model / window, stats rows, Roulette catalogs sync, reel window, Taste pages), `NuvioCRound65EditionsTest` (fixture `mcu_fan_edits`); `NuvioCRound64Test` editions updated to the round 65 rules (Apocalypse Now now leads with Redux).
- Local builds: Maven Central answers 429 for minutes at a time; the compile / test scripts retry every 2 to 3 minutes.

## Still to check on the TV
- Round 65: trailer picture + sound on details; series page focus Cast -> Collection -> More like this and back up; graph open / Back to its button, a 30+ season show, Mine toggle on a Trakt profile, OK opening an episode; hero row width on a running show; Featured / Roulette / Library pills; Roulette and Library spin (blur, "?", landing); Taste card focus, each page type, Back to the card, a title opened from a page and Back; stream info steady with mpv and ExoPlayer; lists menu Left / Right / Up; profile screen picture; Featured picture; an MCU film's editions; Calendar side panel art.
- Round 64c and earlier: see git history of this file (items carried over unchanged: DTS + DD+ start track, Back out of the player, series scroll up, Search rows, OSD Atmos logo).

## Open ideas (not approved for building)
- Official 1.2.0-beta.4 merge (deferred by the owner at round 65).
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
