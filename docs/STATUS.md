# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-20 (round 56)

## Where things stand
- Branch `nuvio-c` = round 56 on official `1.2.0-beta.3`. Official `1.1.1` (2026-11-21) is a hotfix whose three fixes are already in beta.3; CI now picks the highest version, so it no longer tries 1.1.1.
- Only branch left: `nuvio-c` (old branches deleted 2026-11-21).
- Cleanup Groups A, B and C are merged into `nuvio-c`. Compiling it found that the Week view removal had taken four pieces Month / List still use; they now live in `ui/screens/calendar/CalendarShared.kt`.
- 549 `NuvioC*` / `Calendar*` unit tests pass locally.
- Round 56:
  - Modern Subtitles panel: no tabs, two-line rows (pill; language, with SDH / Forced / CC on built-in tracks). Subtitle delay is a line in the Modern More sheet (shown while a subtitle is on).
  - Episode Find removed completely (the series details page lost focus around it).
  - Fork settings per profile: each profile keeps its own copy on the TV, loaded on every switch before syncing; the player's cached choices (controls style, button orders, night mode) reload. Roulette's seen list and the post-play rating marker are per profile.

## Still to check on the TV
- Series details page: focus never lost, scrolling always works (Find gone).
- Modern Subtitles panel opens on the active track; Up on the top row stays put; More > Subtitle delay opens the delay bar.
- Profiles: change a fork setting on profile A, switch to B, B unchanged; back to A, A's value back. Profiles whose cloud copy already holds another profile's values (from the old leak) need their settings checked once by hand.
- Cast page: bottom clipping. Episode rating panel. Trailers: countdown, mute and fade, hold, resume, Home handoff. Post-play rating prompt.

## Open ideas (not approved for building)
- Seek thumbnails on the device, no external API (AIOStreams app's version works on the TV). Investigating only.
- On-the-fly episode ordering per show (Miami Vice S1, Kengan Ashura) with correct scrobbling.
- MDBList API call growth during playback (raised 2026-10-31). Current status unknown, re-check with a request log.
- Watchly C lives in its own repo, not here.

## Start of every build session
1. Read `docs/ARCHITECTURE.md`, `docs/FORK_CHANGES.md` and this file.
2. Upstream check: `git ls-remote --tags https://github.com/NuvioMedia/NuvioTV`. Newer than `1.2.0-beta.3`? Scan it for new servers / hosts / secrets, then merge it first. Ask the owner before keeping or dropping any fork or official feature.
3. Environment and commands: `docs/ARCHITECTURE.md` section 7. Attach the repo with push access, clone `nuvio-c`. Tests need `--max-workers=1` and `-Xmx4g` (the test run ran out of memory at 5g / 2 workers).

## End of every build session
1. Compile, run `NuvioC*` and `Calendar*` tests, forced GitHub build.
2. Update `docs/FORK_CHANGES.md` and `docs/ARCHITECTURE.md` if anything changed, rewrite this file, run `scripts/check-docs.py`, commit with the code.

## Standing rules (owner)
- Mockups before building anything new; no dead space or clipping in designs.
- An approved round is built in full in one build; if something can't be done, say so before building.
- Every tweak behind its own switch; never touch decoder selection.
- Works for Nuvio-Sync-only profiles; personal data strictly per profile.
- Always build on the newest fork code. During builds, wait quietly and report once.
