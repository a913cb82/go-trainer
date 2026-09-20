#!/usr/bin/env bash
# Vendor the KataGo arm64 engine binaries into app/src/main/jniLibs/.
# Source: BadukAI v1.20.13 APK (KataGo v1.16.0, Eigen CPU, humanSLProfile).
# The binary HAS a package path check (/data/data/net.kir.baduk_ai, enforced on
# the resolved cwd with an exact-length-27 requirement) that exits 0 silently —
# see docs/ON_DEVICE.md. patch-engine.sh neutralizes it for our package; the
# app additionally launches with cwd=/data/data (see KataGoGtpEngine).
#
#   ./android/scripts/vendor-engine.sh
set -euo pipefail
APK_URL="https://github.com/aki65/aki65.github.io/releases/download/v1.20.13/baduk_ai__arm64-v8a-rel-1.21.apk"
OUT="$(cd "$(dirname "$0")/../app/src/main/jniLibs/arm64-v8a" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

echo ">> Downloading BadukAI v1.20.13 APK (proves KataGo v1.16.0, ~160MB)…"
curl -L --fail --progress-bar -o "$TMP/badukai.apk" "$APK_URL"

echo ">> Extracting engine libs…"
unzip -o -q "$TMP/badukai.apk" "lib/arm64-v8a/libkatago.so" "lib/arm64-v8a/libSNPE.so" "lib/arm64-v8a/libtensorflowlite.so" -d "$TMP"
cp "$TMP/lib/arm64-v8a/libSNPE.so" "$TMP/lib/arm64-v8a/libtensorflowlite.so" "$OUT/"

echo ">> Patching the package gate for com.gotrainer.nine…"
"$(dirname "$0")/patch-engine.sh" "$TMP/lib/arm64-v8a/libkatago.so" "$OUT/libkatago.so"

echo ">> Verifying (KataGo version, humanSLProfile, patched check)…"
strings "$OUT/libkatago.so" | grep -m1 "KataGo v1"
if strings "$OUT/libkatago.so" | grep -q "net.kir.baduk_ai"; then
  echo "!! FAIL: original package check still present"; exit 1
fi
if ! strings "$OUT/libkatago.so" | grep -q "humanSLProfile"; then
  echo "!! FAIL: libkatago.so lacks humanSLProfile — do not use"; exit 1
fi
echo ">> OK:"; md5sum "$OUT"/*.so
