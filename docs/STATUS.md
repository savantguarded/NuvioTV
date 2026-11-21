# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-21 (round 58)

## Where things stand
- Branch `nuvio-c` = round 58 on official `1.2.0-beta.3` (still the newest official tag; `1.1.1` is an older-numbered hotfix already in beta.3). CI picks the highest version.
- Only branch left: `nuvio-c`. Personal details were scrubbed from code and docs (old commits still hold them).
- 550 `NuvioC*` / `Calendar*` unit tests pass locally.
- Rounds 57 to 57d (see git log and FORK_CHANGES): Modern OSD text pills and Sound in More, quiet loading band, profiles before any logo, CW marquee, details focus fixes confirmed on the TV (MobLand S2E10 manual Sources, hero Play, scrolling up into the episodes), Roulette dropdown and hold, Taste round 57 look.
- Round 58 (mockups: boards 12 to 17 on the claude.ai canvas "Nuvio C mockups"):
  - Library: Nuvio Sync gets "Library" and "Watched" lists (`LIBRARY_WATCHED_LOCAL`), same picker, filters, sorts and focus as the trackers.
  - Modern OSD right icons larger inside the same 36 dp pill (`CLEAN_ICONS_LARGER`).
  - Taste (`TASTE_V58`): Month / Year / All time, opens on This month every time, empty month offers last month; New releases without chart; wider Discover posters; Languages card in Stats; Wrapped re-laid with Top films top five beside Top shows and Firsts in the top row.
  - `DETAIL_FOCUS_LOG` switched off.

## Still to check on the TV
- Library on Nuvio Sync: list picker shows Library / Watched; Watched newest first, Movies / Series filter, Back keeps focus; posters fill in for history items without one.
- Taste: sidebar entry opens on This month even after leaving it on All time; Stats four cards fit without clipping; Discover faces row not squeezed by the taller posters; Wrapped Firsts fits its shorter card.
- Modern OSD: right icons larger, focus circle not clipped.
- Earlier: cold start (black, then profiles); More sheet Sound lines; Upcoming marquee; Roulette filter hold.

## Open ideas (not approved for building)
- Seek thumbnails on the device, no external API. Investigating only.
- On-the-fly episode ordering per show (Miami Vice S1, Kengan Ashura) with correct scrobbling.
- MDBList API call growth during playback (raised 2026-10-31). Re-check with a request log.
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
