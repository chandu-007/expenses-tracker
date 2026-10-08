#!/usr/bin/env python3
"""Measures the PRODUCT_CONTEXT 6.1 numbers that need the real app on a device: cold start, warm start, opening the add screen, and list scrolling,
on the full dataset (100,000 entries over 10 years, 508 categories, 500 tags, 1,000 attachment records).

Needs the `benchmark` build (release code, R8, debug-signed, with the measurement hooks): ./gradlew :androidApp:assembleBenchmark
Usage: python3 tools/measure_perf.py [--runs 23] [--skip-seed]

It reports medians and ranges, the machine load during each phase and which build and device it ran on, so a number is never separated
from its conditions. An emulator on a fast Mac is a best case: these are NOT numbers for a mid-range phone.
"""
import argparse, os, re, statistics, subprocess, sys, time

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "app.expenses"
ACT = f"{PKG}/.MainActivity"
APK = "androidApp/build/outputs/apk/benchmark/androidApp-benchmark.apk"


def adb(*args, check=True):
    r = subprocess.run([ADB, *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def load():
    return float(os.getloadavg()[0])


def pid():
    out = adb("shell", "pidof", PKG, check=False).strip().split()
    return out[0] if out else None


def startup_marks(p):
    """name -> ms since process start, for process [p] only (logcat cannot be cleared on this image, so older runs are in the buffer)."""
    marks = {}
    for line in adb("logcat", "-d", "-v", "threadtime", "-s", "Startup:I").splitlines():
        parts = line.split()
        if len(parts) > 6 and parts[2] == p:
            m = re.search(r"Startup\s*: (.+?) \+(\d+)ms", line)
            if m:
                marks[m.group(1)] = int(m.group(2))
    return marks


def am_start_w():
    out = adb("shell", "am", "start", "-W", "-n", ACT)
    total = re.search(r"TotalTime: (\d+)", out)
    return int(total.group(1)) if total else None


def summary(name, values, unit="ms"):
    v = sorted(values)
    q = lambda f: v[min(len(v) - 1, int(f * len(v)))]
    print(f"| {name} | {statistics.median(v):.0f} | {v[0]:.0f}-{v[-1]:.0f} | p90 {q(0.9):.0f} | {len(v)} |")


def tap_fab():
    adb("shell", "input", "tap", "958", "2080")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=23)
    ap.add_argument("--skip-seed", action="store_true")
    a = ap.parse_args()
    drop = 3  # the first runs after install or seeding are cold in every other way too (page cache, JIT profile)

    print("device:", adb("shell", "getprop", "ro.product.model").strip(), "| Android", adb("shell", "getprop", "ro.build.version.release").strip(),
          "| abi", adb("shell", "getprop", "ro.product.cpu.abi").strip())
    if not a.skip_seed:
        adb("install", "-r", APK)
        adb("shell", "pm", "clear", PKG)
        adb("shell", "am", "start", "-n", ACT, "--ei", "seed_rows", "100000", "--ez", "seed_full", "true")
        print("seeding 100,000 entries, 500 categories, 500 tags, 1,000 attachment records ...")
        time.sleep(75)
        adb("shell", "am", "force-stop", PKG)
        time.sleep(1)

    # ---- cold start ----
    cold_total, cold_ready, cold_open = [], [], []
    l0 = load()
    for i in range(a.runs + drop):
        adb("shell", "am", "force-stop", PKG)
        time.sleep(1.2)
        total = am_start_w()
        time.sleep(1.8)
        marks = startup_marks(pid())
        if i >= drop and total and "ready composed" in marks:
            cold_total.append(total); cold_ready.append(marks["ready composed"]); cold_open.append(marks.get("open: first page loaded", 0))
    l1 = load()

    # ---- warm start: the process is alive, the screen is brought back ----
    warm = []
    w0 = load()
    adb("shell", "am", "start", "-n", ACT)
    time.sleep(2)
    for i in range(a.runs + drop):
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(1.0)
        t = am_start_w()
        time.sleep(0.8)
        if i >= drop and t:
            warm.append(t)
    w1 = load()

    # ---- add screen: from the tap handler to the amount field holding focus ----
    add = []
    d0 = load()
    adb("shell", "am", "start", "-n", ACT)
    time.sleep(2)
    p = pid()
    for i in range(a.runs + drop):
        tap_fab()
        time.sleep(1.4)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")  # closes the keyboard
        time.sleep(0.4)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")  # closes the form
        time.sleep(0.6)
    # logcat keeps the marks in order: pair each tap with the next focus
    tapped = focused = None
    pairs = []
    for line in adb("logcat", "-d", "-v", "threadtime", "-s", "Startup:I").splitlines():
        parts = line.split()
        if len(parts) < 7 or parts[2] != p:
            continue
        m = re.search(r"Startup\s*: (.+?) \+(\d+)ms", line)
        if not m:
            continue
        if m.group(1) == "add: tapped":
            tapped = int(m.group(2))
        elif m.group(1) == "add: amount focused" and tapped is not None:
            pairs.append(int(m.group(2)) - tapped); tapped = None
    add = pairs[drop:]
    d1 = load()

    # ---- scrolling the real list ----
    s0 = load()
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    for i in range(40):
        y1, y2 = ("1800", "500") if i % 2 == 0 else ("500", "1800")
        adb("shell", "input", "swipe", "540", y1, "540", y2, "120")
        time.sleep(0.25)
    time.sleep(1)
    gfx = adb("shell", "dumpsys", "gfxinfo", PKG)
    s1 = load()
    def grab(pattern, cast=float):
        m = re.search(pattern, gfx)
        return cast(m.group(1)) if m else None
    frames = grab(r"Total frames rendered: (\d+)", int)
    janky = grab(r"Janky frames: (\d+)", int)
    p50, p90, p95, p99 = (grab(rf"{n}th percentile: (\d+)ms", int) for n in (50, 90, 95, 99))

    print(f"\nRuns: {a.runs} after dropping {drop}. Host load (1 min) at the start and end of each phase: cold {l0:.1f}/{l1:.1f}, warm {w0:.1f}/{w1:.1f}, add {d0:.1f}/{d1:.1f}, scroll {s0:.1f}/{s1:.1f}\n")
    print("| Measurement | median ms | range | p90 | n |")
    print("|---|---|---|---|---|")
    summary("cold start: am start -W TotalTime", cold_total)
    summary("cold start: data composed (since process start)", cold_ready)
    summary("cold start: first page loaded", cold_open)
    summary("warm start: am start -W TotalTime", warm)
    summary("add screen: tap handler to amount focused", add)
    print(f"\nScroll, 40 swipes on the 100,000-entry list: {frames} frames, {janky} janky ({(100.0 * janky / frames) if frames else float('nan'):.1f}%), frame time p50 {p50} ms, p90 {p90} ms, p95 {p95} ms, p99 {p99} ms")


if __name__ == "__main__":
    main()
