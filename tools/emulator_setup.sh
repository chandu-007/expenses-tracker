#!/usr/bin/env bash
# Prepares a freshly booted emulator for the device tests and the release smoke test. Usage: tools/emulator_setup.sh [serial]   (default: ANDROID_SERIAL, else the only device)
# What it does and why:
#  - waits for the boot to finish (sys.boot_completed), then a short settle;
#  - animation scales to 1: a re-created AVD can come up with them at 0 (an old developer setting) and then MotionUiTest, which measures that screens really move, fails on a clean tree;
#  - on a Google APIs image, disables the Google app (com.google.android.googlequicksearchbox): its "has stopped" popup closes dialogs at random in the middle of a run;
#  - leaves the screen on and unlocked.
# For the widget steps of the smoke test use the API34 AVD (docs/RELEASE_CHECKLIST.md): the same script works there.
set -u
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
S="${1:-${ANDROID_SERIAL:-}}"
A=("$ADB"); [ -n "$S" ] && A=("$ADB" -s "$S")
for i in $(seq 1 120); do [ "$("${A[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 5; done
sleep 30
for k in window_animation_scale transition_animation_scale animator_duration_scale; do "${A[@]}" shell settings put global $k 1.0; done
"${A[@]}" shell pm disable-user --user 0 com.google.android.googlequicksearchbox >/dev/null 2>&1 || true
"${A[@]}" shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true
"${A[@]}" shell input keyevent KEYCODE_WAKEUP; "${A[@]}" shell wm dismiss-keyguard 2>/dev/null || true
echo "emulator ready: sdk $("${A[@]}" shell getprop ro.build.version.sdk | tr -d '\r'), animation scales $("${A[@]}" shell settings get global window_animation_scale | tr -d '\r')/$("${A[@]}" shell settings get global transition_animation_scale | tr -d '\r')/$("${A[@]}" shell settings get global animator_duration_scale | tr -d '\r')"
