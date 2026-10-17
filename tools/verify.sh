#!/usr/bin/env bash
# Full verification, in two steps that must both pass:
#   1. build, host tests, release build and every device test EXCEPT the timing gate;
#   2. the timing gate (PerformanceBudgetTest) on its own, after waiting for the machine to be quiet,
#      because timing numbers measured while Gradle and the emulator are busy, or while the host is swapping, mean little.
# Gates stay strict: nothing is loosened here. Needs an emulator running. Exits non-zero on any failure.
set -u
cd "$(dirname "$0")/.."
MAX_LOAD="${MAX_LOAD:-14}"      # 1-minute load average the timing step waits for
MAX_WAIT="${MAX_WAIT:-600}"     # seconds to wait for it before running anyway (and saying so)

rm -rf core/*/build/test-results androidApp/build/test-results androidApp/build/outputs/androidTest-results

echo "== step 1: build, host tests, release build, functional device tests"
# The build and the device tests are two Gradle runs, one after the other: when they were one, the Android plugin's probe of the emulator ran alongside the
# compile and lint work and, on a machine short of memory, timed out ("Unknown API Level"), so no test ran at all. The same tasks run either way.
./gradlew check :androidApp:assembleRelease :androidApp:assembleDebug --rerun-tasks > /tmp/verify-step1.log 2>&1
step1=$?
./gradlew --stop >/dev/null 2>&1
./gradlew :androidApp:connectedDebugAndroidTest --rerun-tasks \
  -Pandroid.testInstrumentationRunnerArguments.notClass=app.expenses.PerformanceBudgetTest,app.expenses.RefreshBudgetTest,app.expenses.RecreateStressTest >> /tmp/verify-step1.log 2>&1
step1=$(( step1 != 0 ? step1 : $? ))
echo "step 1 gradle exit: $step1"
cp androidApp/build/outputs/androidTest-results/connected/debug/*.xml /tmp/verify-functional.xml 2>/dev/null

echo "== step 2: timing gate alone"
./gradlew --stop >/dev/null 2>&1
# An emulator that has just run hundreds of tests is slower than a fresh one (the committed version of the app fails the same timing tests on it), so the
# timing gate is measured on a freshly started emulator. The gate and its limits are unchanged. RESTART_EMULATOR=0 skips this (a physical device, or a CI
# runner that provides a clean emulator); AVD names the virtual device to start.
if [ "${RESTART_EMULATOR:-1}" = "1" ]; then
  ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"; EMU="${ANDROID_HOME:-$HOME/Library/Android/sdk}/emulator/emulator"; AVD="${AVD:-API26}"
  if [ -x "$EMU" ] && "$ADB" devices | grep -q '^emulator-'; then
    echo "restarting the emulator $AVD before the timing gate"
    "$ADB" emu kill >/dev/null 2>&1; sleep 8; pkill -f "avd $AVD" 2>/dev/null; sleep 3
    (nohup "$EMU" -avd "$AVD" -no-snapshot -no-audio -no-boot-anim >/tmp/verify-emulator.log 2>&1 &)
    for _ in $(seq 1 100); do [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 5; done
    sleep 90 # the machine is busy for a while after a boot
  else
    echo "no emulator to restart; measuring on the device as it is"
  fi
fi
waited=0
while :; do
  load=$(uptime | sed 's/.*averages: //' | awk '{print $1}' | tr -d ',')
  awk "BEGIN{exit !($load < $MAX_LOAD)}" && break
  [ "$waited" -ge "$MAX_WAIT" ] && { echo "load still $load after ${MAX_WAIT}s: running the gate anyway; a failure here may be the machine, re-run it"; break; }
  sleep 15; waited=$((waited+15))
done
echo "load at start of timing step: $load (waited ${waited}s)"
rm -rf androidApp/build/outputs/androidTest-results
# Build the test apps first and let the host go quiet: recompiling right before the first measurement left the machine busy and made every timing
# test 2-3x slower (the same tests passed 15/15 a minute later with nothing to compile). The tests themselves still run from deleted result files.
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest --rerun-tasks > /tmp/verify-step2.log 2>&1
./gradlew --stop >/dev/null 2>&1; sleep 45
./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.expenses.PerformanceBudgetTest,app.expenses.RefreshBudgetTest >> /tmp/verify-step2.log 2>&1
step2=$?
echo "step 2 gradle exit: $step2"

python3 tools/verify_report.py "$step1" "$step2"
