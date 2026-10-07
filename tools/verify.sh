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
  -Pandroid.testInstrumentationRunnerArguments.notClass=app.expenses.PerformanceBudgetTest > /tmp/verify-step1.log 2>&1
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

python3 - "$step1" "$step2" <<'E'
import glob,re,sys
s1,s2=int(sys.argv[1]),int(sys.argv[2])
t=f=0
for p in glob.glob("*/build/test-results/**/*.xml",recursive=True)+glob.glob("core/*/build/test-results/**/*.xml",recursive=True):
    m=re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"',open(p).read())
    if m: a,b,c=map(int,m.groups()); t+=a; f+=b+c
def device(path):
    x=open(path).read(); r=re.search(r'tests="(\d+)" failures="(\d+)" errors="(\d+)"',x).groups()
    return int(r[0]),int(r[1])+int(r[2]),len(re.findall(r'<skipped',x)),"crashed" in x
ok = s1==0 and s2==0 and f==0
try: fn=device("/tmp/verify-functional.xml")
except Exception: fn=(0,1,0,True); ok=False
g=glob.glob("androidApp/build/outputs/androidTest-results/connected/debug/*.xml")
tg=device(g[0]) if g else (0,1,0,True)
ok = ok and fn[1]==0 and not fn[3] and tg[1]==0 and not tg[3] and fn[0]>0 and tg[0]>0
print(f"host/JVM: {t} tests, {f} failed")
print(f"functional device tests: {fn[0]} run, {fn[1]} failed, {fn[2]} skipped{', PROCESS CRASHED' if fn[3] else ''}")
print(f"timing gate: {tg[0]} run, {tg[1]} failed{', PROCESS CRASHED' if tg[3] else ''}")
print("RESULT:", "PASS" if ok else "FAIL")
open("/tmp/verify-totals.txt","w").write(f"{t} host/JVM tests (both targets counted), {fn[0]} functional device tests ({fn[2]} skipped) and {tg[0]} timing-gate tests; verified in two steps by tools/verify.sh from deleted result files")
sys.exit(0 if ok else 1)
E
