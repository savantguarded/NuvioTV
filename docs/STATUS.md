# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-20 (evening)

## Where things stand
- Branch `nuvio-c` @ `122312e8` = round 55 on official `1.2.0-beta.3`.
- Last release: `1.2.0-beta.3+nuvioc`, built and installed on the TV 2026-11-20 (with the `.dm` file). 567 unit tests passed locally.
- Round 55 (verified on TV): "Your rating" focus label, parental guidance removed from Details, Themes / Keywords / Original title removed from Information.
- beta.3 merge: official rating pill now drives Nuvio C multi-tracker sync (`RATINGS_MULTI`); fork trailer behaviour runs on official's `BackgroundTrailerController`.

## Still to check on the TV
- Season posters on, with the Find tile.
- Cast page: bottom clipping.
- Episode rating panel.
- Trailers: countdown, mute and fade, hold, resume, Home handoff.
- Post-play rating prompt.
- Round 50 start-up / CPU measurement (adb script vs the 2026-11-18 baseline: CPU in first 3 min halved, under 8 GCs in 4 min, Home settle under 4 s). Charles asked to be reminded of this before the next build. Not recorded as done.

## Open ideas (not approved for building)
- Seek thumbnails on the device, no external API (AIOStreams app's version works on the TV). Investigating only.
- On-the-fly episode ordering per show (Miami Vice S1, Kengan Ashura) with correct scrobbling.
- MDBList API call growth during playback (raised 2026-10-31). Current status unknown, re-check with a request log.
- Watchly C lives in its own repo, not here.

## Cleanup (branch `cleanup-ab`, NOT compiled yet)
- Groups A and B approved by Charles and done as 17 commits on branch `cleanup-ab` (on top of `nuvio-c` d9b6eff0): 22 switches and their dead code removed, 6 unused strings removed, CI now skips removed switch names in `NUVIO_C_OFF` instead of failing.
- Next build session: compile and test `cleanup-ab` first. If green, fast-forward `nuvio-c` to it (`git merge --ff-only origin/cleanup-ab`), then do the new work on top.
- Group C (older versions kept as fallbacks: start-up loader / halo, older Taste, panel and OSD versions): list each case for Charles to pick. Keep the Calendar Week view (ysosrs123 code).
- Project knowledge: fold the claude/round* project docs into one history file, delete the rest; add the project instruction block.

## Start of every build session
1. Read `docs/ARCHITECTURE.md`, `docs/FORK_CHANGES.md` and this file.
2. Upstream check: `git ls-remote --tags https://github.com/NuvioMedia/NuvioTV`. Newer than `1.2.0-beta.3`? Scan it for new servers / hosts / secrets, then merge it first. Ask Charles before keeping or dropping any fork or official feature.
3. Environment and commands: `docs/ARCHITECTURE.md` section 7. Attach the repo with push access, clone `nuvio-c`.

## End of every build session
1. Compile, run `NuvioC*` and `Calendar*` tests, forced GitHub build.
2. Update `docs/FORK_CHANGES.md` and `docs/ARCHITECTURE.md` if anything changed, rewrite this file, run `scripts/check-docs.py`, commit with the code.

## Standing rules (Charles)
- Mockups before building anything new; no dead space or clipping in designs.
- An approved round is built in full in one build; if something can't be done, say so before building.
- Every tweak behind its own switch; never touch decoder selection.
- Works for Nuvio-Sync-only profiles; personal data strictly per profile.
- Always build on the newest fork code. During builds, wait quietly and report once.
