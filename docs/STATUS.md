# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-26 (round 66d)

## Where things stand
- Branch `nuvio-c` = round 66 on official `1.2.0-beta.4` (merged in full; all 15 conflicting files decided by the owner, list in the merge commit "Merge official 1.2.0-beta.4 into Nuvio C"). Usenet kept as official. CI builds without a `release` pin again.
- Round 66 (plan: project doc claude/round66-build-plan.md), all built in one build: series graph fixes (`GRAPH_V66`), reel spin on later shuffles and stronger blur (`REEL_V66`), Featured carousel option A (`FEATURED_V66`, includes the flicker fix and the lighter picture), official details left darkening and hero-on-screen check for Pause on scroll (`DETAIL_SCRIM_V66`), profile background one box / no grey / splash match (`PROFILE_BG_V66`), quality line 12 h recheck for new releases (`QUALITY_REFRESH_V66`), editions share / groups / MCU blocklist (`EDITIONS_V66`), Taste fixes (`TASTE_V66`), details-to-details trailer view (`TRAILER_HANDOVER_V66`), undated filmography first (`FILMO_UNDATED_FIRST`), narrower Modern pills (`CLEAN_PILLS_NARROW`), Sync to audio now (`SYNC_AUDIO_NOW`), subtitle format tag (`SUB_FORMAT_TAG`), Customize ASS in Settings (`SUB_ASS_IN_SETTINGS`).
- Round 66b (owner-approved fixes after testing 66, one build): carousel `FEATURED_V66B` (context line only, no rank / Starring / Watchlist, picture edge fade, genres line + details badge line max 4 with background lookups, Play names the episode, Details pill, details trailer button opening the full-screen trailer, 380 ms crossfade into Continue watching), `TAGS_V66B` (more varied context lines), series graph OK does nothing (round 66 jump code removed), `TRAILER_LOAD_PAUSED` (no sound low on the page), `PROFILE_SHADE_V66B`, `TASTE_HOURS_PM`, `CALENDAR_SYNC`.
- Round 66c (owner-approved fixes after testing 66b, one build): carousel `FEATURED_V66C` (trailer button and all its code removed; Play + Details only, 14 dp apart; Play names the episode in the slide actually shown; Right on Details keeps focus there; details' age rating chip leads the genres line; word badges filled like the Modern size badge, Dolby / DTS stay logos, max 4; 3-line synopsis, no tagline; edge fade as an overlay; round 52 move into Continue watching, 300 ms, ModulateAlpha), `GRAPH_AVG_PLAIN` (SEASON AVERAGES only), `PROFILE_SHADE_V66C` (page colour, clear by 30%, dithered), `SYNC_AUDIO_IN_MORE` (Modern More panel entry; greyed with "Add-on subtitles only" / the reason when it can't run, because audio sync only re-times add-on subtitle files, never embedded tracks). Release notes are one line ("Nuvio C, a personal fork of NuvioTV, based on official <tag>.").
- Round 66d (owner-approved option B from the canvas page "Round 66d: smaller badges", one build): `FEATURED_BADGES_V66D`: carousel badges 11 dp (were 15), outlined with text at 80% instead of filled tiles, Dolby / DTS logos in the same 11 dp box, 4 dp apart; shows read "2022 • 5 seasons" on the year line (length when the season count is unknown) and the genres line is genres only; synopsis 11.5 sp at 64% (was 12 sp at 72%), still three lines; 8 dp above the genres line.
- Dropped by the owner at build start: per-title "Hide this edition".
- Choices made in round 66 (owner to confirm on the TV): Taste cards lose the drawn "Open ›" hint; Sync to audio now is in the Modern subtitles panel and (66c) the More panel; no neighbour profile picture preload.
- Calendar sync starts when a device with 66b pushes its settings (any settings change, or the next push); older installs ignore it.
- Editions finding: under the approved 15% share rule Apocalypse Now loses its (real) Final Cut, which holds 14.2% of that film's seeders; only Redux shows. Owner to decide whether to lower the share (e.g. 10%, one constant in TitleQualityParser.kt).
- Tests: all 613 `NuvioC*` and `Calendar*` tests pass (66d adds `NuvioCRound66dTest`: carousel badge height, logo labels; 66c added `NuvioCRound66cTest`: shade stops, logo vs filled badges, Right on Details, More sync reasons; 66b added `NuvioCRound66bTest`), round 66 added `NuvioCRound66Test` (graph layout / ticks, reel landing, 12 h TTL, Avengers fixture `avengers_2012`, share / group rules, blocklist, 12-hour clock, Wrapped key, filmography order, hero check, carousel keys, sync checks).
- Local builds: Maven Central answers 429 for minutes at a time; `/home/claude/gradle-retry.sh` style loops (retry on 429 and on a vanished Gradle daemon, `-Xmx4g`).

## Still to check on the TV
- Round 66d: carousel badge line size and readability from the sofa (4K / WEB-DL text, Dolby Vision / Atmos / DTS logos), "5 seasons" on the year line, synopsis contrast.
- Round 66c: carousel (age rating chip, filled badges, Play S/E text, Right on Details, 14 dp gap, 3-line synopsis, lighter move into Continue watching), graph averages header, profile shade at 30% (no banding), More panel sync entry with an add-on subtitle and with an embedded one (greyed), one-line release notes.
- Round 66b: carousel context line over the logo, no bar on the left, badges on every slide; trailer silent when the page opens scrolled down, profile shade, Taste "9pm", Calendar bells on a second device.
- Round 66: graph on a 7+ season show (no overlap, years visible, long-show ticks); Roulette second / third shuffle spins; blur strength; Featured carousel (tags, logo size, badges, Starring, Watchlist / Trailer, Left / Right between titles, no flicker, speed into Continue watching); details left darkening and Pause on scroll; profile switching and Add Profile, splash hand-over; Taste lists held Down / Up, 12-hour labels, Wrapped cards; Home > details > More like this > details trailer picture; Sync to audio now with ExoPlayer and mpv; Modern pills; an MCU film's editions; filmography of an actor with announced films.
- After the beta.4 merge: Simkl rewatch, subtitle font / HDR dim / lift with official's new ASS styles on mpv, Disc titles button on a Blu-ray folder, Usenet.
- Round 65 list (not reported yet): see git history of this file.

## Open ideas (not approved for building)
- Optional focus prefetch for the quality line (spec: ship off; not built).
- Seek thumbnails on the device, no external API. Investigating only.
- On-the-fly episode ordering per show (Miami Vice S1, Kengan Ashura) with correct scrobbling.
- MDBList API call growth during playback (raised 2026-10-31).
- Watchly C lives in its own repo, not here.

## Start of every build session
1. Read `docs/ARCHITECTURE.md`, `docs/FORK_CHANGES.md` and this file.
2. Upstream check: `git ls-remote --tags https://github.com/NuvioMedia/NuvioTV`. Newer than `1.2.0-beta.4`? Scan it for new servers / hosts / secrets, then merge it first. Every conflict goes to the owner before anything is kept or dropped.
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
