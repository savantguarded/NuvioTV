# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-27 (round 67)

## Where things stand
- Branch `nuvio-c` = round 67 on official `1.2.0-beta.4` (merged in full in round 66; all conflicts decided by the owner, list in the merge commit "Merge official 1.2.0-beta.4 into Nuvio C"). No newer official release at the round 67 start (checked 2026-11-27).
- Round 67 (plan: project doc claude/round67-build-plan.md, canvas boards 1-5d), all built in one build:
  - `PROFILE_SHADE_V67`: Netflix-style profile shade (38% dim, left ramp clear at 62% of the screen, top and bottom fades, dithered, page colour); splash identical.
  - `REEL_BLUR_V67` (1/4 decode plus a one-off stack blur, Roulette and Library Random pick), `PILL_GAP_V67` (14 dp between side-by-side pills), `ROULETTE_TILE_ACCENT` (Movies / Shows cards ring in the theme accent; the other Roulette rings already used it).
  - `FEATURED_PLAY_TEXT_V67`: carousel Play text from Continue watching / progress / watched films only (watched list = the active provider's watched films).
  - `QUALITY_DIGITAL_GATE`: films in cinemas within a year with no digital / disc date in any country show NOT YET ON DIGITAL with no Torrentio / QualiCache call. Deviation from the plan, to make the gate work on a first visit: the details page waits up to 1.2 s + 3 s for the Information block's dates (that block itself only fetches after the 1.2 s settle), only for films released within about a year; the carousel makes one cached TMDB release-dates call per recent film (it has no Information block).
  - `EPISODE_RATINGS_EARLY`: episode card ratings behind the opening gate (slot 5), leaf state read only by the episodes row; the Series graph reuses it.
  - `COLLECTION_META_ORDER`: collection titles take the add-on meta (the existing background prefetch, reused) first, TMDB on top per its settings.
  - Series graph and the background trailer: no change needed (full-screen opaque Dialog; the trailer holds paused and silent, resumes on Back).
  - `ADDON_HEALTH`: Service health (card, summary page, Status sources page, phone page field, per-profile synced sources, TorBox account and status page) with the board 5d security rules. No sources by default; add IbbyLabs (`https://uptime.ibbylabs.dev`) yourself.
- Round 66 to 66e: see FORK_CHANGES.md and git history of this file.
- Choices left for the owner: Editions share (Apocalypse Now Final Cut, 14.2% under the 15% rule; one constant in TitleQualityParser.kt). Health trackers column shows Trakt / Simkl / MDBList / Nuvio entries found on the sources, whether or not this profile uses them (they never count in the add-on totals).
- Tests: all 650 `NuvioC*` and `Calendar*` tests pass (round 67 adds `NuvioCRound67Test`: shade stops, Play text cases, digital gate dates in any country, collection meta order, verdict table; and `NuvioCHealthSecurityTest`: address checks, redirect rule, control / bidi stripping, sentinel TorBox key never in any output, hostname-only add-on addresses, 2 MB cap, parsers on trimmed real IbbyLabs / Gatus / Better Stack fixtures).
- Local builds: one Gradle run at a time (two at once get one killed by the 8 GB memory limit); `-Xmx4g` is enough; Maven Central answers 429 now and then, `/home/claude/gradle-retry.sh` style loops.

## Still to check on the TV
- Round 67: profile shade on a bright picture, splash match, no banding; Roulette / Library Random smooth blur, 14 dp pill gap, accent ring on Movies / Shows; each carousel Play text (unstarted show, mid-episode, next up, new season, watched film); a film in cinemas shows NOT YET ON DIGITAL and no badges, a Nollywood film with only a Nigerian digital date shows badges; episode ratings on the cards ~1.2 s after opening, page opening speed unchanged (perfetto or `dumpsys gfxinfo`), graph opens with ratings; a collection title keeps the add-on info when its details open; Add-ons page focus and delete / move return spots, dots fill in without rows moving, summary focus and Back, OK on an add-on returns to its card, add IbbyLabs and a Gatus page, then a bad address (http, user:pass, a non-status page), the TorBox account line.
- Round 66e and earlier: carousel order and ratings line, seasons, filled resolution badges, two-part context lines, CW move up / down; 66d badge readability; 66c graph averages, More panel sync entry; 66b Taste "9pm", Calendar bells on a second device; 66 graph on 7+ seasons, Roulette later spins, details darkening and Pause on scroll, Sync to audio now; after the beta.4 merge: Simkl rewatch, subtitle styles on mpv, Disc titles, Usenet.

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
