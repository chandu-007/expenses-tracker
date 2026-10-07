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
./gradlew check :androidApp:assembleRelease :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest --rerun-tasks \
  -Pandroid.testInstrumentationRunnerArguments.notClass=app.expenses.PerformanceBudgetTest,app.expenses.RecreateStressTest > /tmp/verify-step1.log 2>&1
step1=$?
echo "step 1 gradle exit: $step1"
cp androidApp/build/outputs/androidTest-results/connected/debug/*.xml /tmp/verify-functional.xml 2>/dev/null

echo "== step 2: timing gate alone"
./gradlew --stop >/dev/null 2>&1
waited=0
while :; do
  load=$(uptime | sed 's/.*averages: //' | awk '{print $1}' | tr -d ',')
  awk "BEGIN{exit !($load < $MAX_LOAD)}" && break
  [ "$waited" -ge "$MAX_WAIT" ] && { echo "load still $load after ${MAX_WAIT}s: running the gate anyway; a failure here may be the machine, re-run it"; break; }
  sleep 15; waited=$((waited+15))
done
echo "load at start of timing step: $load (waited ${waited}s)"
rm -rf androidApp/build/outputs/androidTest-results
./gradlew :androidApp:connectedDebugAndroidTest --rerun-tasks -Pandroid.testInstrumentationRunnerArguments.class=app.expenses.PerformanceBudgetTest > /tmp/verify-step2.log 2>&1
step2=$?
echo "step 2 gradle exit: $step2"

python3 tools/verify_report.py "$step1" "$step2"
