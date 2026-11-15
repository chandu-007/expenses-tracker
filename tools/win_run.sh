#!/usr/bin/env bash
# Runs a Gradle command on the Windows helper PC (docs/WINDOWS_RUNNER.md), detached, with a log on the PC.
# Usage: tools/win_run.sh <git-ref> <gradle args...>   e.g. tools/win_run.sh main :core:domain:jvmTest
# <git-ref> is a branch, tag or full commit id that exists on GitHub (push first). Prints the log name; follow it with tools/win_log.sh <name>.
# Gradle args must not contain spaces or cmd metacharacters.
set -eu
[ $# -ge 2 ] || { echo "usage: $0 <git-ref> <gradle args...>" >&2; exit 2; }
PC="${WIN_PC:-chandu@100.106.212.44}"
REF="$1"; shift
NAME="run-$(date +%Y%m%d-%H%M%S)"
ssh -o BatchMode=yes -o ConnectTimeout=15 "$PC" "powershell -NoProfile -Command \"(Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{CommandLine='cmd /c D:\\dev\\run.bat $REF $* > D:\\dev\\logs\\$NAME.log 2>&1'}).ReturnValue\"" | tr -d '\r' | grep -qx 0 || { echo "start failed" >&2; exit 1; }
echo "$NAME"
echo "log: D:\\dev\\logs\\$NAME.log   follow: tools/win_log.sh $NAME"
