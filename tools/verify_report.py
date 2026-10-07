#!/usr/bin/env python3
"""Reads the test result files left by tools/verify.sh and prints one verdict. Exit 0 only if everything passed.

A process crash is never excused: a run that lost its instrumentation process fails. One crash signature is *named* in the output (a
Compose slot-table error, ArrayIndexOutOfBounds in SlotWriter.moveSlotGapTo, seen twice and not yet explained) so it is reported as
that, and the run has to be repeated by hand and the occurrence recorded in docs/PHASE1_PROGRESS.md.

Environment overrides, for testing this script: VERIFY_FUNCTIONAL_XML and VERIFY_GATE_XML (paths), VERIFY_HOST_DIRS (glob root).
"""
import glob, os, re, sys

step1, step2 = int(sys.argv[1]), int(sys.argv[2])
functional = os.environ.get("VERIFY_FUNCTIONAL_XML", "/tmp/verify-functional.xml")
gate_files = [os.environ["VERIFY_GATE_XML"]] if "VERIFY_GATE_XML" in os.environ else glob.glob("androidApp/build/outputs/androidTest-results/connected/debug/*.xml")

tests = failed = 0
for p in glob.glob("*/build/test-results/**/*.xml", recursive=True) + glob.glob("core/*/build/test-results/**/*.xml", recursive=True):
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', open(p).read())
    if m:
        a, b, c = map(int, m.groups()); tests += a; failed += b + c


def device(path):
    x = open(path).read()
    r = re.search(r'tests="(\d+)" failures="(\d+)" errors="(\d+)"', x).groups()
    return int(r[0]), int(r[1]) + int(r[2]), len(re.findall(r"<skipped", x)), "crashed" in x, "SlotWriter.moveSlotGapTo" in x


try:
    fn = device(functional)
except Exception:
    fn = (0, 1, 0, True, False)
gate = device(gate_files[0]) if gate_files else (0, 1, 0, True, False)

ok = step1 == 0 and step2 == 0 and failed == 0 and fn[1] == 0 and not fn[3] and gate[1] == 0 and not gate[3] and fn[0] > 0 and gate[0] > 0
print(f"host/JVM: {tests} tests, {failed} failed")
print(f"functional device tests: {fn[0]} run, {fn[1]} failed, {fn[2]} skipped{', PROCESS CRASHED' if fn[3] else ''}")
print(f"timing gate: {gate[0]} run, {gate[1]} failed{', PROCESS CRASHED' if gate[3] else ''}")
if (fn[3] and fn[4]) or (gate[3] and gate[4]):
    print("KNOWN CRASH: the Compose slot-table error (SlotWriter.moveSlotGapTo) killed a device test process. Not retried here. Repeat the run by hand and record this occurrence.")
print("RESULT:", "PASS" if ok else "FAIL")
open("/tmp/verify-totals.txt", "w").write(f"{tests} host/JVM tests (both targets counted), {fn[0]} functional device tests ({fn[2]} skipped) and {gate[0]} timing-gate tests; verified in two steps by tools/verify.sh from deleted result files")
sys.exit(0 if ok else 1)
