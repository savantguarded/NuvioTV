#!/usr/bin/env python3
"""Nuvio C: check that docs/ARCHITECTURE.md, FORK_CHANGES.md and STATUS.md still match the code.

Run from the repo root before every push: python3 scripts/check-docs.py
Fails (exit 1) when a doc is over its line limit, names a file or folder that no longer exists,
names a switch that is not in NuvioCFeatures.kt, or when the "Switched off" list in
FORK_CHANGES.md doesn't match the switches that are actually off.
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = "app/src/main/java/com/nuvio/tv/"
FEATURES = SRC + "NuvioCFeatures.kt"
LIMITS = {"docs/ARCHITECTURE.md": 250, "docs/FORK_CHANGES.md": 150, "docs/STATUS.md": 80}
# Names that look like paths but aren't repo files (local-only or shorthand).
IGNORE = {"local.properties", "nuvio-c.yml", "/home/claude/android-sdk"}
EXT = (".kt", ".kts", ".java", ".md", ".yml", ".sh", ".py", ".txt", ".toml", ".pro", ".xml", ".properties", ".cpp")

os.chdir(ROOT)
errors = []

code = open(FEATURES, encoding="utf-8").read()
switches = dict(re.findall(r"const val ([A-Z0-9_]+) = (true|false)", code))
off = {k for k, v in switches.items() if v == "false"}


def path_exists(token):
    token = token.replace("…", "*")
    if token.startswith("src/"):
        token = SRC + token[4:]
    bases = ["", "docs/", SRC, SRC + "ui/screens/", SRC + "ui/components/", SRC + "core/", SRC + "data/",
             "app/src/main/", "app/src/main/res/"]
    for b in bases:
        if glob.glob(b + token) or glob.glob(b + token.rstrip("/")):
            return True
    # Short form like `player/NuvioCHdrDim.kt` or a bare file name: find it anywhere under the source root.
    name = token.rstrip("/").split("/")[-1]
    hits = glob.glob(SRC + "**/" + name, recursive=True) + glob.glob("app/src/**/" + name, recursive=True)
    return bool(hits)


for doc, limit in LIMITS.items():
    if not os.path.exists(doc):
        errors.append(f"{doc}: missing")
        continue
    text = open(doc, encoding="utf-8").read()
    n = text.count("\n")
    if n > limit:
        errors.append(f"{doc}: {n} lines, limit {limit}. Trim it.")
    for tok in set(re.findall(r"`([^`\n]+)`", text)):
        tok = tok.strip()
        # Switch names (optionally with a trailing * for a family, e.g. CALENDAR_*)
        m = re.fullmatch(r"([A-Z][A-Z0-9_]+)(\*)?", tok)
        if m and "_" in m.group(1):
            name, fam = m.group(1), m.group(2)
            if fam:
                if not any(s.startswith(name) for s in switches):
                    errors.append(f"{doc}: no switch starts with {name}")
            elif name not in switches and name not in {"NUVIO_C_OFF", "TRAILER_PATHS", "IMDB_PATHS",
                                                        "FILMOGRAPHY_PATHS", "OSD_FILES", "TRAKT_CLIENT_ID",
                                                        "TRAKT_CLIENT_SECRET", "IMDB_TAPFRAME_API_BASE_URL"}:
                errors.append(f"{doc}: switch {name} not in NuvioCFeatures.kt")
            continue
        if " " in tok or "<" in tok or tok in IGNORE or tok.startswith(("-", "./", ":", "http")):
            continue
        looks_like_path = "/" in tok and (tok.endswith("/") or tok.split("/")[-1].endswith(EXT) or "*" in tok)
        if looks_like_path or (tok.endswith(EXT) and re.fullmatch(r"[\w.*…-]+", tok)):
            if not path_exists(tok):
                errors.append(f"{doc}: path not found: {tok}")

fork = open("docs/FORK_CHANGES.md", encoding="utf-8").read() if os.path.exists("docs/FORK_CHANGES.md") else ""
sec = re.search(r"## Switched off.*?\n(.*?)(?:\n## |\Z)", fork, re.S)
listed = set(re.findall(r"`([A-Z][A-Z0-9_]+)`", sec.group(1))) if sec else set()
listed_off = {s for s in listed if switches.get(s) == "false"}
for s in sorted(off - listed):
    errors.append(f"docs/FORK_CHANGES.md: switch {s} is off in code but not in 'Switched off'")
for s in sorted(listed - off - {s for s in listed if switches.get(s) == "true" and s == "IMDB_QUIET_START"}):
    errors.append(f"docs/FORK_CHANGES.md: 'Switched off' lists {s}, which is not off in code")

m = re.search(r"(\d+) switches, (\d+) off", fork)
if m and (int(m.group(1)), int(m.group(2))) != (len(switches), len(off)):
    errors.append(f"docs/FORK_CHANGES.md: says {m.group(1)} switches / {m.group(2)} off, code has {len(switches)} / {len(off)}")

if errors:
    print("Docs check FAILED:")
    print("\n".join("  - " + e for e in sorted(errors)))
    sys.exit(1)
print(f"Docs check OK ({len(switches)} switches, {len(off)} off).")
