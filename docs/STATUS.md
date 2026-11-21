# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-21 (round 57c)

## Where things stand
- Branch `nuvio-c` = round 57 on official `1.2.0-beta.3`. Official `1.1.1` (2026-11-21) is a hotfix whose three fixes are already in beta.3; CI now picks the highest version, not the newest date.
- Only branch left: `nuvio-c`. Personal details were scrubbed from code and docs (old commits still hold them).
- 549 `NuvioC*` / `Calendar*` unit tests pass locally.
- Round 57 (mockups: claude.ai canvas "Nuvio C Round 57 mockups"; plan: the claude.ai project doc "round57-plan"):
  - Modern OSD: text-only left pills; Sound section in the More sheet; Audio panel tracks only.
  - Loading band quieter and lower, FILENAME label, one-line filename cut in the middle.
  - Cold start: no Nuvio logo before profiles; profile list mirrored on disk and drawn from the first frame.
  - Continue Watching / Upcoming cards: title and episode name take turns scrolling.
  - Details: back from Sources lands on the saved spot without an animated scroll; Back on play options returns to Play.
  - Roulette: filter changes on a result never spin (dot + "Filters changed" on Shuffle again).
  - Taste round 57 look (`TASTE_V57`): loading screen, flat cards, thumbnails, rows open titles, app-theme accent rings.
- Round 57b (after the TV check): start-up logo held back while the profile screen is ahead; details return guard (Play / episode keep focus for 2.5 s or until a key press); More sheet lists every Sound line (Downmix, Amplification, Persist; Unavailable during passthrough); pills 13 sp; Taste bold only on headers, tabs fill with the accent, months three letters; Roulette watch filter is a dropdown, no "Filters changed" text; Taste / Roulette backdrops no longer pixelated.
- Round 57c: episode cards keep the page's live focus requester (root cause of focus lost after Play manually -> Back on MobLand S2E10, reproduced over adb); Taste big numbers extra bold, titles a size smaller; Roulette Movies / Shows cards without glow fill. Also fixed after reading the adb trace: the restore lock dropped the just-restored card's focus (`EPISODE_LOCK_RACE`), and the hero pill's manual Sources return jumped to the played episode's season (`DETAIL_HERO_RETURN`). `DETAIL_FOCUS_LOG` is on for checking; switch it off once the restore is confirmed.

## Still to check on the TV
- Cold start: black, then the profile screen, no logo. A profile added or renamed on another device appears a moment later.
- Details: Play manually, then Back from Sources (movie hero and an episode): focus on Play / the episode, never the season tabs.
- More sheet Sound lines: Left / Right steps, OK resets; Dialogue / Amplification only while the TV decodes the sound.
- Taste: opening on each saved tab (no Overview flash); rows open titles; held Down stops on the last row; nothing clipped.
- Upcoming card marquee; Roulette Watched / Unwatched on a result keeps the pick.
- Earlier: series details focus with Find gone, Modern Subtitles panel, per-profile settings, cast page bottom clipping.

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
