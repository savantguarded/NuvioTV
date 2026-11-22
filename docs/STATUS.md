# Nuvio C status

Rewrite this file at the end of every build session (do not append). History lives in git log.

Updated: 2026-11-22 (round 60)

## Where things stand
- Branch `nuvio-c` = round 60 on official `1.2.0-beta.3` (still the newest official tag on 2026-11-22). CI picks the highest version.
- Only branch left: `nuvio-c`.
- Round 60:
  - Details quality badge line (`DETAIL_QUALITY_LINE`): built from the owner's spec "Nuvio details page quality badges" (2026-11-22) and his v3 mockup (grey outlines, generic dim labels). Agreed changes to the spec: 5+ seeder releases win before weaker ones; a film release that names a different year is ignored (bogus IDs such as tt9999999 give QUALITY UNAVAILABLE); NOT YET ON DIGITAL only for a film out in cinemas within the last year. The leaf has its own view model (`NuvioCQualityViewModel`) instead of a flow in `MetaDetailsViewModel`, and draws its own badges in the owner's design instead of reusing the OSD badge view, so the OSD is untouched.
  - QualiCache: no key built in. The shared key is no longer in the PostersPlus README and the server answers 401 without one. Add a QUALICACHE_API_KEY line to the CI secret LOCAL_PROPERTIES to switch the fallback on; until then Torrentio is the only source.
  - Marquee on more cut text (`MARQUEE_MORE`).
  - Featured carousel badges: on hold by the owner (mockup canvas "Nuvio C: Featured carousel quality badges (mockup)", option A picked, not built).
  - "Full exit on close" (Settings > Advanced) already gives double-Back exit; left at official default, the owner switches it on himself.
- Tests: `NuvioCRound60Test` uses trimmed Torrentio answers saved 2026-11-22 (`app/src/test/resources/quality/`).

## Still to check on the TV
- Quality line: nothing above the line moves between the first frame and the result (cached title, cold title, unavailable); badges match the design; series pages (latest aired episode); a `tmdb:` id title; a title from Torrentio in Lagos (owner confirmed the URL loads in a browser).
- Marquee: Taste rows / Spin card / faces, Modern Home card titles, collection folders, Calendar reminder rows.
- From round 59: Taste two-line names and rows, Wrapped chart headroom, Stats strip; Library on Nuvio Sync; Modern OSD right icons.

## Open ideas (not approved for building)
- Featured carousel quality badges (on hold, option A).
- Optional focus prefetch for the quality line (spec: ship off; not built).
- Seek thumbnails on the device, no external API. Investigating only.
- On-the-fly episode ordering per show (Miami Vice S1, Kengan Ashura) with correct scrobbling. The quality line asks Torrentio with the add-on's season / episode numbers, so a show whose TVDB order differs from IMDb can show the wrong episode's quality.
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
