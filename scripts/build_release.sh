#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[[ -f keystore.properties ]] || { echo "Missing keystore.properties" >&2; exit 1; }
./gradlew assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
APKSIGNER="${ANDROID_HOME:-$HOME/android-sdk}/build-tools/35.0.0/apksigner"
"$APKSIGNER" verify --print-certs "$APK"
FINGERPRINT=$("$APKSIGNER" verify --print-certs "$APK" 2>&1 | grep 'Signer #1 certificate SHA-256 digest' | sed 's/.*: //')
if grep -q '^certificateSha256=' docs/RELEASE.md; then
  EXPECTED=$(grep '^certificateSha256=' docs/RELEASE.md | cut -d= -f2)
  [[ "$FINGERPRINT" == "$EXPECTED" ]] || { echo "توقيع مختلف عن البصمة المسجلة" >&2; exit 1; }
fi
sha256sum "$APK"
echo "APK: $ROOT/$APK"
