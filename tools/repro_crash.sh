#!/usr/bin/env bash
# Tries to reproduce the unexplained Compose slot-table crash (see docs/PHASE1_PROGRESS.md): runs the whole functional device suite, including
# RecreateStressTest, N times and counts the runs that were killed by it. Usage: tools/repro_crash.sh [label] [runs]. Needs an emulator.
cd "$(dirname "$0")/.."
label=${1:-repro}; loops=${2:-8}; crashes=0; other=0; clean=0
for i in $(seq 1 $loops); do
  rm -rf androidApp/build/outputs/androidTest-results
  ./gradlew :androidApp:connectedDebugAndroidTest --rerun-tasks -Pandroid.testInstrumentationRunnerArguments.notClass=app.expenses.PerformanceBudgetTest > /tmp/repro_${label}_$i.log 2>&1
  r=$(python3 - <<'E'
import glob,re
for p in glob.glob("androidApp/build/outputs/androidTest-results/connected/debug/*.xml"):
    x=open(p).read(); t=re.search(r'tests="(\d+)" failures="(\d+)" errors="(\d+)"',x).groups()
    print(("SLOT" if "SlotWriter" in x else ("OTHERCRASH" if "crashed" in x else ("FAILS" if t[1]!="0" or t[2]!="0" else "CLEAN"))), t[0])
E
)
  echo "$label loop $i: $r"
  case "$r" in SLOT*) crashes=$((crashes+1));; CLEAN*) clean=$((clean+1));; *) other=$((other+1));; esac
done
echo "== $label: $loops runs: $crashes slot-table crashes, $clean clean, $other other"
