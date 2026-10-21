#!/usr/bin/env bash
# Nuvio C: take in ysosrs123's latest calendar code without losing the Nuvio C tweaks to it.
#
# How it works (a "vendor branch", same idea as tracking official):
#   - Branch `ysos-calendar` holds ysosrs123's calendar files exactly as he wrote them, one commit per
#     version taken. The files are listed in scripts/nuvio-c-calendar-files.txt, his commit in
#     scripts/nuvio-c-calendar-upstream.txt.
#   - `nuvio-c` has merged that branch, so git knows which of his versions our tweaked copies started from.
#   - This script puts his newer files on `ysos-calendar` and merges it into `nuvio-c`. Git does a 3-way
#     merge: his changes come in, our tweaks stay, and it only stops where he and we changed the same lines.
#
# Usage (repo root, on nuvio-c, clean tree):   scripts/nuvio-c-calendar-sync.sh [ysos ref, default main]
# Afterwards: fix any conflicts, compile + run the calendar tests, push nuvio-c AND ysos-calendar.
# His edits to official files (Home, settings, MainActivity hooks...) are listed, not merged: port by hand.
set -euo pipefail

YSOS_URL="https://github.com/ysosrs123/NuvioTV-Fork"
LIST=scripts/nuvio-c-calendar-files.txt
PIN=scripts/nuvio-c-calendar-upstream.txt
VENDOR=ysos-calendar
# Calendar-looking files in his repo that we deliberately don't take (dropped locales, screenshots).
SKIP_RE='^(docs/|app/src/main/res/values-(pt|sr|zh)/)'
CAL_RE='(calendar|tvmaze|comingup)'

ref="${1:-main}"
[ "$(git rev-parse --abbrev-ref HEAD)" = "nuvio-c" ] || { echo "Run this on the nuvio-c branch."; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Commit or stash your changes first."; exit 1; }

old="$(tr -d '[:space:]' < "$PIN")"
git fetch -q --depth=1 "$YSOS_URL" "$ref"
new="$(git rev-parse FETCH_HEAD)"
if [ "$new" = "$old" ]; then echo "Already on ysosrs123 ${new:0:7}. Nothing to take in."; exit 0; fi
git fetch -q --depth=1 "$YSOS_URL" "$old"   # his previous version, to list what changed
echo "ysosrs123: ${old:0:7} -> ${new:0:7}"

changed="$(git diff --name-only "$old" "$new")"
vendored="$(grep -Fxf "$LIST" <<<"$changed" || true)"
added="$(grep -iE "$CAL_RE" <<<"$changed" | grep -Evf <(printf '%s\n' "$SKIP_RE") | grep -Fxvf "$LIST" || true)"
added="$(while read -r f; do [ -n "$f" ] && git cat-file -e "$new:$f" 2>/dev/null && echo "$f"; done <<<"$added")"
others="$(grep -Fxvf "$LIST" <<<"$changed" | grep -Fxvf <(printf '%s\n' "$added") || true)"

echo; echo "Calendar files he changed (will be merged):"; printf '  %s\n' ${vendored:-"(none)"}
echo; echo "New calendar files (will be added):";         printf '  %s\n' ${added:-"(none)"}
echo; echo "His other changed files (NOT merged; check for calendar hooks and port by hand):"
printf '  %s\n' ${others:-"(none)"}

# 1. Vendor branch: his exact files at $new.
work="$(mktemp -d)"
trap 'git worktree remove --force "$work" >/dev/null 2>&1 || true' EXIT
if git show-ref -q "refs/heads/$VENDOR"; then :
elif git ls-remote -q --exit-code origin "refs/heads/$VENDOR" >/dev/null; then git fetch -q origin "$VENDOR:$VENDOR"
else echo "No $VENDOR branch (local or on GitHub)."; exit 1; fi
git worktree add -q "$work" "$VENDOR"
{ cat "$LIST"; printf '%s\n' $added; } | sort -u > "$work/.files"
while read -r f; do
  [ -n "$f" ] || continue
  if git cat-file -e "$new:$f" 2>/dev/null; then
    mkdir -p "$work/$(dirname "$f")"; git show "$new:$f" > "$work/$f"; git -C "$work" add "$f"
  elif [ -e "$work/$f" ]; then
    git -C "$work" rm -q "$f"; echo "  (he deleted $f)"
  fi
done < "$work/.files"
rm "$work/.files"
git -C "$work" commit -q -m "ysosrs123 calendar @ ${new:0:7}" -m "$YSOS_URL/commit/$new"
echo; echo "$VENDOR now at ysosrs123 ${new:0:7}."

# 2. Merge into nuvio-c (3-way: his old -> his new, applied on top of our tweaked copies).
if git merge --no-ff --no-commit "$VENDOR" >/dev/null 2>&1; then status=ok; else status=conflict; fi
{ cat "$LIST"; printf '%s\n' $added; } | sort -u | while read -r f; do git cat-file -e "$new:$f" 2>/dev/null && echo "$f"; done > "$LIST.tmp"
mv "$LIST.tmp" "$LIST"; echo "$new" > "$PIN"; git add "$LIST" "$PIN"
if [ "$status" = ok ]; then
  git commit -q -m "Nuvio C: take in ysosrs123 calendar ${new:0:7}" -m "$YSOS_URL/compare/$old...$new"
  echo "Merged cleanly. Next: compile, run calendar tests, then: git push origin nuvio-c $VENDOR"
else
  echo; echo "Conflicts (he and Nuvio C changed the same lines). Resolve these, then git commit:"
  git diff --name-only --diff-filter=U | sed 's/^/  /'
  exit 2
fi
