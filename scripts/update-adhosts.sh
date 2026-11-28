#!/usr/bin/env bash
# Rebuilds app/src/main/assets/adhosts.txt from EasyList's plain domain rules (`||host^`).
# Rules with paths, options ($...), exceptions or element hiding need a real filter engine, so they're skipped.
# The APK build compresses assets, so the file stays plain text (AGP would unpack a .gz anyway).
set -euo pipefail
cd "$(dirname "$0")/.."
out=app/src/main/assets/adhosts.txt
curl -sSfL --retry 3 --max-time 120 https://easylist.to/easylist/easylist.txt \
  | grep -E '^\|\|[a-z0-9.-]+\^$' \
  | sed -E 's/^\|\|(.*)\^$/\1/' \
  | LC_ALL=C sort -u > "$out.tmp"
# A truncated or changed download must not replace a good list.
test "$(wc -l < "$out.tmp")" -gt 10000
mv "$out.tmp" "$out"
echo "$(wc -l < "$out" | tr -d ' ') hosts"
