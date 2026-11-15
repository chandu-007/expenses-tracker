#!/usr/bin/env bash
# Shows the last lines of a log on the Windows helper PC. Usage: tools/win_log.sh <logname> [lines]   (name as printed by tools/win_run.sh; the end of a finished run shows "EXIT <code>")
set -eu
[ $# -ge 1 ] || { echo "usage: $0 <logname> [lines]" >&2; exit 2; }
PC="${WIN_PC:-chandu@100.106.212.44}"
N="${2:-30}"
ssh -o BatchMode=yes -o ConnectTimeout=15 "$PC" "powershell -NoProfile -Command \"Get-Content -Tail $N D:\\dev\\logs\\${1%.log}.log\"" | tr -d '\r'
