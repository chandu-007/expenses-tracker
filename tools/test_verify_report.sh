#!/usr/bin/env bash
# Checks tools/verify_report.py against synthetic result files, asserting the EXIT CODE (not only the printed verdict), because commits
# are chained after verification with && and a FAIL must stop them. Exits non-zero if any expectation is wrong.
cd "$(dirname "$0")/.."
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
ok='<testsuite name="x" tests="5" failures="0" errors="0" skipped="1"><testcase name="a" classname="x.T"/></testsuite>'
bad='<testsuite name="x" tests="5" failures="1" errors="0" skipped="0"><testcase name="a" classname="x.T"><failure message="m">boom</failure></testcase></testsuite>'
crash_known='<testsuite name="x" tests="5" failures="1" errors="0" skipped="0"><system-err>Test run failed to complete. Instrumentation run failed due to Process crashed. at androidx.compose.runtime.composer.gapbuffer.SlotWriter.moveSlotGapTo(SlotTable.kt:4483)</system-err></testsuite>'
crash_other='<testsuite name="x" tests="5" failures="1" errors="0" skipped="0"><system-err>Test run failed to complete. Instrumentation run failed due to Process crashed. OutOfMemoryError</system-err></testsuite>'
echo "$ok" > $tmp/ok.xml; echo "$bad" > $tmp/bad.xml; echo "$crash_known" > $tmp/known.xml; echo "$crash_other" > $tmp/other.xml
fail=0
expect() { # name expected-exit expected-text functional-xml gate-xml step1 step2
  out=$(VERIFY_FUNCTIONAL_XML=$4 VERIFY_GATE_XML=$5 python3 tools/verify_report.py $6 $7 2>&1); code=$?
  if [ "$code" -ne "$2" ] || ! echo "$out" | grep -q "$3"; then echo "WRONG: $1 (exit $code, wanted $2; wanted text '$3')"; echo "$out" | sed 's/^/    /'; fail=1; else echo "ok:    $1 (exit $code)"; fi
}
expect "everything passes"                  0 "RESULT: PASS"  $tmp/ok.xml    $tmp/ok.xml    0 0
expect "a failing functional test"          1 "RESULT: FAIL"  $tmp/bad.xml   $tmp/ok.xml    0 0
expect "a failing timing gate"              1 "RESULT: FAIL"  $tmp/ok.xml    $tmp/bad.xml   0 0
expect "gradle step 1 failed"               1 "RESULT: FAIL"  $tmp/ok.xml    $tmp/ok.xml    1 0
expect "gradle step 2 failed"               1 "RESULT: FAIL"  $tmp/ok.xml    $tmp/ok.xml    0 1
expect "the known crash is named and fails" 1 "KNOWN CRASH"   $tmp/known.xml $tmp/ok.xml    0 0
expect "another crash fails"                1 "PROCESS CRASHED" $tmp/other.xml $tmp/ok.xml   0 0
expect "a missing gate result file"         1 "cannot read"   $tmp/ok.xml    $tmp/missing.xml 0 0
echo "not xml at all" > $tmp/junk.xml
expect "a file that is not a test result"   1 "not a test result file" $tmp/junk.xml $tmp/ok.xml 0 0
echo '<testsuite name="f" tests="111" failures="0" errors="0" skipped="0"/>' > $tmp/f111.xml; echo '<testsuite name="g" tests="7" failures="0" errors="0" skipped="0"/>' > $tmp/g7.xml
out=$(VERIFY_FUNCTIONAL_XML=$tmp/f111.xml VERIFY_GATE_XML=$tmp/g7.xml python3 tools/verify_report.py 0 0)
echo "$out" | grep -q 'functional device tests: 111 run' && echo "$out" | grep -q 'timing gate: 7 run' && echo 'ok:    functional and gate totals come from their own files' || { echo 'WRONG: totals are mixed up'; echo "$out"; fail=1; }
# the two steps read DIFFERENT files: functional totals from the saved copy, gate totals from the results directory
grep -q 'cp androidApp/build/outputs/androidTest-results/connected/debug/\*.xml /tmp/verify-functional.xml' tools/verify.sh || { echo "WRONG: step 1 results are not saved before step 2 deletes them"; fail=1; }
grep -q 'rm -rf androidApp/build/outputs/androidTest-results' tools/verify.sh || { echo "WRONG: the results directory is not cleared before step 2"; fail=1; }
last=$(grep -v '^\s*$' tools/verify.sh | tail -1)
[ "$last" = 'python3 tools/verify_report.py "$step1" "$step2"' ] && echo "ok:    verify.sh ends with the report, so its exit status is the verdict" || { echo "WRONG: verify.sh does not end with the report (last line: $last)"; fail=1; }
[ $fail -eq 0 ] && echo "verify_report checks: all as expected" || echo "verify_report checks: PROBLEMS"
exit $fail
