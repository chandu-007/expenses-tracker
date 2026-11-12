#!/usr/bin/env python3
# Collects what ART's JIT records while the app is used, for a BASELINE PROFILE (docs/PERFORMANCE.md, "Baseline profile": the steps, and why none is shipped yet).
# Not part of the build: run by hand, on an emulator (adb root works on the google_apis images) or a rooted phone, against a build WITHOUT R8 so the names are the source names.
#   1. a throwaway build type in androidApp/build.gradle.kts (never merged):  create("nonMinified") { initWith(getByName("release")); isMinifyEnabled = false; isShrinkResources = false;
#        signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release"; buildConfigField("boolean", "SEEDING_ENABLED", "true") }
#      ./gradlew :androidApp:assembleNonMinified ; adb install -r androidApp/build/outputs/apk/nonMinified/androidApp-nonMinified.apk
#   2. adb shell pm clear app.expenses ; adb shell am start -n app.expenses/.MainActivity --ei seed_rows 3000 ; sleep 25 ; adb shell am force-stop app.expenses
#      adb root ; adb shell "cmd package compile --reset app.expenses; rm -f /data/misc/profiles/cur/0/app.expenses/primary.prof"
#   3. python3 tools/baseline_profile_drive.py SERIAL 3     (cold starts, the first-run tour, Home, an entry, add, Search, Reports, More and its pages); run it twice: the tour shows once
#   4. adb shell am start -n app.expenses/.MainActivity ; sleep 4 ; adb shell killall -s SIGUSR1 app.expenses ; sleep 4        (makes ART write the profile)
#      APK=$(adb shell pm path app.expenses | sed 's/package://') ; adb shell "profman --dump-classes-and-methods --profile-file=/data/misc/profiles/cur/0/app.expenses/primary.prof --apk=$APK --dex-location=$APK" > dump.txt
#   5. drop the lines of the test and seeding code (LedgerViewModel;->seed, androidx/test) and of libraries that ship their own profile (androidx/compose, activity, lifecycle, collection);
#      what is left (about 11,700 lines, 1.1 MB) is the text of androidApp/src/main/baseline-prof.txt, which AGP merges with the libraries' profiles and R8 rewrites to the shipped names.
# Usage: baseline_profile_drive.py SERIAL cold_runs
"""Drives the nonMinified build through the app's key flows with adb (uiautomator dump to find labels), so the JIT records what a person does.
Usage: drive.py SERIAL cold_runs"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
S = sys.argv[1]; COLD = int(sys.argv[2]) if len(sys.argv) > 2 else 6
PKG = "app.expenses"; ACT = PKG + "/.MainActivity"
def adb(*a, check=False): return subprocess.run([ADB, "-s", S, *a], capture_output=True, text=True).stdout
def sh(c): return adb("shell", c)
def dump():
    for _ in range(3):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
        x = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in x:
            try: return ET.fromstring(x)
            except ET.ParseError: pass
        time.sleep(0.5)
    return None
def nodes(): 
    r = dump(); return [] if r is None else list(r.iter("node"))
def center(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds")); l, t, rr, b = map(int, m.groups()); return (l + rr) // 2, (t + b) // 2
def find(label, exact=True):
    for n in nodes():
        for k in ("text", "content-desc"):
            v = n.get(k) or ""
            if (v == label) if exact else (label.lower() in v.lower()):
                return n
    return None
def tap(label, exact=True, wait=1.0):
    n = find(label, exact)
    if n is None: print("  (not found:", label, ")"); return False
    x, y = center(n); sh(f"input tap {x} {y}"); time.sleep(wait); return True
def back(w=0.8): sh("input keyevent KEYCODE_BACK"); time.sleep(w)
def swipe_up(n=1, d=300): [ (sh(f"input swipe 540 1700 540 600 {d}"), time.sleep(0.3)) for _ in range(n) ]
def swipe_down(n=1, d=300): [ (sh(f"input swipe 540 700 540 1800 {d}"), time.sleep(0.3)) for _ in range(n) ]
def start(): sh("am start -W -n " + ACT + " >/dev/null"); time.sleep(2.2)
def stop(): sh("am force-stop " + PKG); time.sleep(1.0)

print("cold starts"); 
for i in range(COLD): stop(); start()
print("the tour, every step"); start()
for i in range(26):
    if not tap("Next", wait=1.3): break
for lab in ("Done", "Finish", "Close", "Skip the tour", "Try it"): 
    if tap(lab, exact=False, wait=1.2): break
print("home"); start(); swipe_up(6); swipe_down(6); swipe_up(3); swipe_down(3)
print("open an entry"); 
n = [x for x in nodes() if (x.get("content-desc") or x.get("text") or "").startswith("Merchant ")]
if n: x, y = center(n[0]); sh(f"input tap {x} {y}"); time.sleep(1.5); swipe_up(2); swipe_down(2); back()
print("add an expense"); start()
if tap("+") or tap("Add"): 
    time.sleep(1.0); sh("input text 123"); time.sleep(0.6); tap("Save", wait=1.5)
print("search"); start()
if tap("Search"):
    time.sleep(0.8); sh("input text Merchant"); time.sleep(1.2); sh("input keyevent KEYCODE_ENTER"); time.sleep(1.0); swipe_up(5); swipe_down(5)
    if tap("Filters", exact=False): time.sleep(1.0); tap("Expenses", wait=0.8); tap("Show", exact=False, wait=1.0)
    n = [x for x in nodes() if (x.get("text") or "").startswith("Merchant ")]
    if n: x, y = center(n[0]); sh(f"input tap {x} {y}"); time.sleep(1.5); back()
    time.sleep(0.5)
print("reports"); start()
if tap("Reports"):
    time.sleep(2.0); swipe_up(2); swipe_down(2)
    for lab in ("By year", "Financial year", "By period", "Account", "Merchant", "Category"): tap(lab, wait=1.0)
    back()
print("more and its pages"); start()
for page in ("More", ):
    if tap(page):
        time.sleep(1.0)
        for lab in ("To review", "Accounts", "Categories", "Bills", "Calendar", "Your data", "Appearance", "Notifications", "Home layout"):
            if tap(lab, exact=False, wait=1.2): swipe_up(1); swipe_down(1); back()
            if find("More") is None: start(); tap("More")
print("home again, hide amounts"); start(); swipe_up(2); swipe_down(2)
for i in range(3): stop(); start()
print("done")
