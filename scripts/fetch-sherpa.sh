#!/usr/bin/env bash
# Downloads the sherpa-onnx Android library (on-device Piper and Kokoro voices) into app/libs/.
# It's a 47 MB file, so it isn't kept in git: CI runs this before every build, and you run it once locally.
# Pinned to 1.13.8 because 2.0 removed espeak-ng, which the published Piper voices still need.
set -euo pipefail
cd "$(dirname "$0")/.."
version=1.13.8
sha256=633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96
out=app/libs/sherpa-onnx-$version.aar
if [ -f "$out" ] && echo "$sha256  $out" | shasum -a 256 -c --status; then
  echo "$out is up to date"
  exit 0
fi
mkdir -p app/libs
curl -sSfL --retry 3 --max-time 600 -o "$out.tmp" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$version/sherpa-onnx-$version.aar"
echo "$sha256  $out.tmp" | shasum -a 256 -c --status || { echo "Checksum mismatch for $out" >&2; rm -f "$out.tmp"; exit 1; }
mv "$out.tmp" "$out"
echo "Downloaded $out"
