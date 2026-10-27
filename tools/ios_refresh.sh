#!/bin/bash
# Keeps the free-signed iPhone app alive: a free Apple ID signature lasts 7 days, after which the app stops opening. This rebuilds the signed IPA and installs it on the phone.
#   tools/ios_refresh.sh                          rebuild (tools/ios_build.sh ipa) and install
#   tools/ios_refresh.sh --install-only           install the IPA already on the Desktop, no build
#   tools/ios_refresh.sh --if-expires-within N    do nothing unless the installed app has N days or fewer of signing left
# The remaining days come from a stamp file (~/.expenses_ios_installed_at, written after every successful install by this script; 7 days assumed): devicectl does not report the expiry.
# Needs: the Mac awake, the phone unlocked and reachable (cable, or WiFi pairing in Xcode > Window > Devices and Simulators). Exit: 0 done or nothing to do, 1 phone unreachable / build or install failed, 2 usage.
set -euo pipefail
cd "$(dirname "$0")/.."
# The phone's id comes from IOS_UDID, or else the first physical iPhone `devicectl` lists (paired ones are listed even when unreachable). It is not written in this file: the repository is public.
UDID="${IOS_UDID:-$(xcrun devicectl list devices 2>/dev/null | awk '/physical/ && /iPhone/ { for (i = 1; i <= NF; i++) if ($i ~ /^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}$/) { print $i; exit } }')}"
[ -n "$UDID" ] || { echo "No paired iPhone found by 'xcrun devicectl list devices'. Pair the phone in Xcode > Window > Devices and Simulators, or set IOS_UDID." >&2; exit 1; }
STAMP="${EXPENSES_IOS_STAMP:-$HOME/.expenses_ios_installed_at}"
SIGNING_DAYS=7
VERSION="$(sed -n 's/.*MARKETING_VERSION: "\(.*\)"/\1/p' iosApp/project.yml)"
IPA="$HOME/Desktop/Expenses-iOS-$VERSION-test.ipa"

install_only=0; within=""
while [ $# -gt 0 ]; do
  case "$1" in
    --install-only) install_only=1; shift ;;
    --if-expires-within) [ $# -ge 2 ] && [[ "$2" =~ ^[0-9]+$ ]] || { echo "--if-expires-within needs a number of days" >&2; exit 2; }; within="$2"; shift 2 ;;
    *) echo "usage: $0 [--install-only] [--if-expires-within <days>]" >&2; exit 2 ;;
  esac
done

if [ -n "$within" ]; then
  if [ -f "$STAMP" ] && installed="$(cat "$STAMP")" && [[ "$installed" =~ ^[0-9]+$ ]]; then
    left=$(( SIGNING_DAYS - ( $(date +%s) - installed ) / 86400 ))
    if [ "$left" -gt "$within" ]; then echo "Signing has about $left day(s) left (installed $(date -r "$installed" '+%Y-%m-%d %H:%M')); more than $within, nothing to do."; exit 0; fi
    echo "Signing has about $left day(s) left; refreshing."
  else
    echo "No install time recorded in $STAMP; refreshing."
  fi
fi

state="$(xcrun devicectl list devices 2>/dev/null | grep -F "$UDID" | head -1 || true)"
case "$state" in
  *unavailable*|"")
    echo "The iPhone ($UDID) is not reachable (unavailable). Connect it by cable, unlock it and tap Trust if asked; for WiFi, pair it once in Xcode > Window > Devices and Simulators (tick 'Connect via network'). Nothing was built." >&2
    exit 1 ;;
  *connected*|*available*) ;;
  *) echo "The iPhone is in an unexpected state: $state" >&2; exit 1 ;;
esac

if [ "$install_only" -eq 0 ]; then tools/ios_build.sh ipa; fi
[ -f "$IPA" ] || { echo "No IPA at $IPA; run without --install-only." >&2; exit 1; }

if xcrun devicectl device install app --device "$UDID" "$IPA"; then
  date +%s > "$STAMP"
  echo "Installed $IPA; signing runs out in about $SIGNING_DAYS days (stamp: $STAMP)."
else
  echo "Install failed. Is the phone unlocked and still reachable? The stamp file was not changed." >&2
  exit 1
fi
