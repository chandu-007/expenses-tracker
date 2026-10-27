#!/usr/bin/env bash
# Smoke test of the RELEASE (R8-minified) build, which the debug and instrumented tests never run. Builds ./gradlew :androidApp:assembleRelease, installs the APK
# on the shared emulator and drives the app through its main flows, including reading items from a photo of a bill (ML Kit, the part R8 once broke).
# See tools/release_smoke.py for the steps and docs/RELEASE_CHECKLIST.md for where this fits in a release.
#
# Usage: tools/release_smoke.sh [--no-build]
#   --no-build     reuse the APK already in androidApp/build/outputs/apk/release (use after a build you just made)
# Environment: ANDROID_SERIAL (default emulator-5554; a physical device is refused unless SMOKE_ALLOW_PHYSICAL=1, because the app is uninstalled first),
#              ADB (default ~/Library/Android/sdk/platform-tools/adb). Needs the emulator running (AVD API26: see docs/RELEASE_CHECKLIST.md).
# The release build is signed with the owner's key when the EXPENSES_STORE_* Gradle properties exist (androidApp/build.gradle.kts); otherwise it is unsigned and a
# copy signed with the debug key is made in /tmp so that it installs. Exit status: 0 PASS, 1 a step failed (named in the output), 2 the build or signing failed.
set -u
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
OUT=androidApp/build/outputs/apk/release

if [ "${1:-}" != "--no-build" ]; then
  echo "== building the release APK (./gradlew :androidApp:assembleRelease)"
  if ! ./gradlew :androidApp:assembleRelease > /tmp/release_smoke_build.log 2>&1; then
    echo "BUILD FAILED, see /tmp/release_smoke_build.log"; tail -20 /tmp/release_smoke_build.log; exit 2
  fi
fi

# the build leaves exactly one of the two names; if both exist (an old one from before the signing properties changed), the newer is the one just built
SIGNED="$OUT/androidApp-release.apk"; UNSIGNED="$OUT/androidApp-release-unsigned.apk"
if [ -f "$SIGNED" ] && { [ ! -f "$UNSIGNED" ] || [ "$SIGNED" -nt "$UNSIGNED" ]; }; then
  APK="$SIGNED"; echo "== signed release APK: $APK"
elif [ -f "$UNSIGNED" ]; then
  # no release key on this machine: sign a copy with the debug key (never the build output itself) so that it installs
  APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
  [ -x "$APKSIGNER" ] || { echo "no apksigner under $SDK/build-tools"; exit 2; }
  KEYSTORE="$HOME/.android/debug.keystore"
  [ -f "$KEYSTORE" ] || { echo "no $KEYSTORE to sign the unsigned release APK with"; exit 2; }
  APK=/tmp/release_smoke_signed.apk
  "$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$APK" "$OUT/androidApp-release-unsigned.apk" || exit 2
  echo "== the release build is unsigned here; installing a debug-signed copy: $APK"
else
  echo "no release APK in $OUT"; exit 2
fi

# say which key signed it, so that nobody mistakes a debug-signed copy for the owner's build
APKSIGNER="${APKSIGNER:-$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)}"
[ -x "$APKSIGNER" ] && "$APKSIGNER" verify --print-certs "$APK" 2>&1 | grep -m1 "certificate DN" | sed 's/^/   signer: /'
ls -l "$APK" | awk '{print "   size: " $5 " bytes"}'

exec python3 tools/release_smoke.py "$APK"
