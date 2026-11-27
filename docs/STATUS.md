# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-27 (round 67b)

## Where things stand
- Branch `nuvio-c` = round 67b on official `1.2.0-beta.4` (merged in full in round 66). No newer official release (checked 2026-11-27).
- Round 67 (plan: project doc claude/round67-build-plan.md): profile shade, Roulette blur, pill gap, accent ring, carousel Play text, digital gate, early episode ratings, collection meta order, Service health. Details in FORK_CHANGES.md.
- Round 67b `HEALTH_V67B` (canvas "Nuvio C: Service health redesign", Round 4): today's Service health layout kept, with colour and font tweaks (counts and status words in Space Grotesk, green / amber / red, amber and red washes on problem rows); no addresses or response times anywhere; columns are your add-ons / debrid, trackers and sync / websites (the "Elsewhere" column is gone); only what this profile uses is tracked; no built-in TorBox status page (TorBox account line kept). Status sources: This TV (built in) plus up to 4 you add, each row only name, checked time and a switch (Remove on yours); exactly one is on at a time, switching yours off hands back to This TV; switch focus now shows a ring and lift.
- Choices left for the owner: Editions share (Apocalypse Now Final Cut, 14.2% under the 15% rule; one constant in TitleQualityParser.kt).
- Tests: all 659 `NuvioC*` and `Calendar*` tests pass (round 67b adds `NuvioCRound67bTest`: one source on, single-source verdicts, website checks, instance names, This TV vs a source deciding, no addresses or timings in texts, toned summary, checked line; `NuvioCHealthSecurityTest` updated to the new merge).
- Local builds: one Gradle run at a time (two at once get one killed by the 8 GB memory limit); `-Xmx4g` is enough; Maven Central answers 429 now and then, `/home/claude/gradle-retry.sh` style loops.

## Still to check on the TV
- Round 67b: Service health columns and colours, This TV and a source switching (only one on), switch focus ring visible, new source starts off, Checked X ago lines, TorBox line with plan and days.
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
