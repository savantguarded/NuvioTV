#!/usr/bin/env bash
# Nuvio C: open an issue once per title. If an open issue with the same title already exists,
# add a short comment to it instead of opening a duplicate (the hourly build used to open one per run).
# usage: nuvio-c-issue.sh "<title>" <body-file>
set -u
title="$1"; body="$2"
existing="$(gh api "repos/${GITHUB_REPOSITORY}/issues?state=open&per_page=100" --jq ".[] | select(.title == \"${title//\"/\\\"}\") | .number" 2>/dev/null | head -n1)"
if [ -n "$existing" ]; then
  gh issue comment "$existing" --body "Still the case on run ${GITHUB_RUN_ID:-?} ($(date -u +%Y-%m-%dT%H:%MZ))." >/dev/null && echo "Updated issue #$existing"
else
  gh issue create --title "$title" --body-file "$body"
fi
