#!/bin/bash
# Keeps the free-signed iPhone app alive: a free Apple ID signature lasts 7 days, after which the app stops opening. This rebuilds the signed IPA and installs it on the phone.
#   tools/ios_refresh.sh                          rebuild (tools/ios_build.sh ipa) and install
#   tools/ios_refresh.sh --install-only           install the IPA already on the Desktop, no build
#   tools/ios_refresh.sh --if-expires-within N    do nothing unless the installed app has N days or fewer of signing left
#   tools/ios_refresh.sh --force-new-profile      always set the cached provisioning profiles aside so the build gets new ones
# WHY the profiles are moved: Xcode reuses the cached profile of the app (and of its widget and share extension) until it has expired, so a rebuild and reinstall
# before then keeps the OLD expiry. When the cached profile of app.expenses.tracker.ios has fewer than 3 days left (or with --force-new-profile) the three cached profiles
# (app, .widget, .share) are moved to ~/.expenses_profile_backup/<timestamp>/ and the build makes new ones. If the build fails they are put back and the script exits 1.
# The remaining days come from the real ExpirationDate in the profile inside the IPA on the Desktop (the one installed last); when that cannot be read, from a stamp file
# (~/.expenses_ios_installed_at, written after every successful install; 7 days assumed).
# Needs: the Mac awake, an Apple ID signed in to Xcode (Settings > Accounts), the phone unlocked and reachable (cable, or WiFi pairing in Xcode > Window > Devices and Simulators).
# Exit: 0 done or nothing to do, 1 phone unreachable / build or install failed, 2 usage.
# The EXPENSES_* variables below exist so the logic can be tested against stand-ins.
set -euo pipefail
cd "$(dirname "$0")/.."
# The phone's id comes from IOS_UDID, or else the first physical iPhone `devicectl` lists (paired ones are listed even when unreachable). It is not written in this file: the repository is public.
UDID="${IOS_UDID:-$(xcrun devicectl list devices 2>/dev/null | awk '/physical/ && /iPhone/ { for (i = 1; i <= NF; i++) if ($i ~ /^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}$/) { print $i; exit } }')}"
[ -n "$UDID" ] || { echo "No paired iPhone found by 'xcrun devicectl list devices'. Pair the phone in Xcode > Window > Devices and Simulators, or set IOS_UDID." >&2; exit 1; }
STAMP="${EXPENSES_IOS_STAMP:-$HOME/.expenses_ios_installed_at}"
PROFILES="${EXPENSES_PROFILE_DIR:-$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles}"
BACKUP_ROOT="${EXPENSES_PROFILE_BACKUP:-$HOME/.expenses_profile_backup}"
BUILD_CMD="${EXPENSES_BUILD_CMD:-tools/ios_build.sh ipa}"
APP_ID="app.expenses.tracker.ios"
SIGNING_DAYS=7
RENEW_BELOW_DAYS=3
VERSION="$(sed -n 's/.*MARKETING_VERSION: "\(.*\)"/\1/p' iosApp/project.yml)"
IPA="${EXPENSES_IPA:-$HOME/Desktop/Expenses-iOS-$VERSION-test.ipa}"

install_only=0; within=""; force=0
while [ $# -gt 0 ]; do
  case "$1" in
    --install-only) install_only=1; shift ;;
    --force-new-profile) force=1; shift ;;
    --if-expires-within) [ $# -ge 2 ] && [[ "$2" =~ ^[0-9]+$ ]] || { echo "--if-expires-within needs a number of days" >&2; exit 2; }; within="$2"; shift 2 ;;
    *) echo "usage: $0 [--install-only] [--force-new-profile] [--if-expires-within <days>]" >&2; exit 2 ;;
  esac
done

# the profile file's decoded plist: `security cms -D -i FILE`
profile_field() { security cms -D -i "$1" 2>/dev/null | plutil -extract "$2" raw - 2>/dev/null; }
iso_to_epoch() { date -j -u -f "%Y-%m-%dT%H:%M:%SZ" "$1" +%s 2>/dev/null; }
# the cached profile files for the app and its two extensions, one path per line
cached_profiles() {
  local f id
  [ -d "$PROFILES" ] || return 0
  for f in "$PROFILES"/*.mobileprovision; do
    [ -f "$f" ] || continue
    id="$(profile_field "$f" Entitlements.application-identifier || true)"
    case "$id" in *."$APP_ID"|*."$APP_ID".widget|*."$APP_ID".share) printf '%s\n' "$f" ;; esac
  done
}
# the ExpirationDate (epoch) of the app's own cached profile, empty when there is none
cached_app_expiry() {
  local f id e
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    id="$(profile_field "$f" Entitlements.application-identifier || true)"
    case "$id" in *."$APP_ID") e="$(profile_field "$f" ExpirationDate || true)"; [ -n "$e" ] && iso_to_epoch "$e" && return 0 ;; esac
  done < <(cached_profiles)
  return 0
}
# the ExpirationDate (epoch) of the profile inside an IPA, empty when it cannot be read
ipa_expiry() {
  local tmp e
  [ -f "$1" ] || return 0
  tmp="$(mktemp -d)"
  if unzip -q -o "$1" 'Payload/*/embedded.mobileprovision' -d "$tmp" 2>/dev/null; then
    e="$(profile_field "$(ls "$tmp"/Payload/*/embedded.mobileprovision | head -1)" ExpirationDate || true)"
    [ -n "$e" ] && iso_to_epoch "$e" || true
  fi
  rm -rf "$tmp"
}
human() { date -r "$1" '+%Y-%m-%d %H:%M'; }

if [ -n "$within" ]; then
  real="$(ipa_expiry "$IPA")"
  if [ -n "$real" ]; then
    left=$(( ( real - $(date +%s) ) / 86400 ))
    if [ "$left" -gt "$within" ]; then echo "Signing has about $left day(s) left (profile runs out $(human "$real")); more than $within, nothing to do."; exit 0; fi
    echo "Signing has about $left day(s) left (profile runs out $(human "$real")); refreshing."
  elif [ -f "$STAMP" ] && installed="$(cat "$STAMP")" && [[ "$installed" =~ ^[0-9]+$ ]]; then
    left=$(( SIGNING_DAYS - ( $(date +%s) - installed ) / 86400 ))
    if [ "$left" -gt "$within" ]; then echo "Signing has about $left day(s) left (installed $(human "$installed"), profile date unreadable); more than $within, nothing to do."; exit 0; fi
    echo "Signing has about $left day(s) left (from the install time); refreshing."
  else
    echo "No profile date or install time recorded; refreshing."
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

moved_to=""
if [ "$install_only" -eq 0 ]; then
  cached="$(cached_app_expiry)"
  if [ "$force" -eq 1 ] || { [ -n "$cached" ] && [ $(( ( cached - $(date +%s) ) / 86400 )) -lt "$RENEW_BELOW_DAYS" ]; }; then
    moved_to="$BACKUP_ROOT/$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$moved_to"
    n=0
    while IFS= read -r f; do [ -n "$f" ] || continue; mv "$f" "$moved_to/"; n=$((n + 1)); done < <(cached_profiles)
    echo "Cached profile ${cached:+(runs out $(human "$cached")) }set aside: $n file(s) moved to $moved_to; the build will ask Apple for new ones."
  elif [ -n "$cached" ]; then
    echo "The cached profile runs out $(human "$cached"), more than $RENEW_BELOW_DAYS days away; kept (use --force-new-profile to renew it anyway)."
  fi
  if ! $BUILD_CMD; then
    if [ -n "$moved_to" ]; then
      for f in "$moved_to"/*.mobileprovision; do [ -f "$f" ] && mv -n "$f" "$PROFILES/"; done
      echo "Build failed; the cached profiles were put back in $PROFILES." >&2
    else
      echo "Build failed." >&2
    fi
    exit 1
  fi
fi
[ -f "$IPA" ] || { echo "No IPA at $IPA; run without --install-only." >&2; exit 1; }
new="$(ipa_expiry "$IPA")"
if [ -n "$new" ]; then echo "The IPA's profile runs out $(human "$new")."; else echo "The IPA's profile date could not be read."; fi

if xcrun devicectl device install app --device "$UDID" "$IPA"; then
  date +%s > "$STAMP"
  echo "Installed $IPA; signing runs out ${new:+$(human "$new") }(stamp: $STAMP)."
else
  echo "Install failed. Is the phone unlocked and still reachable? The stamp file was not changed." >&2
  exit 1
fi
