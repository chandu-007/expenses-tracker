#!/usr/bin/env python3
"""Search A/B on the 100,000-entry dataset: cold-started app, tap the Search tab, type a query, read the Startup marks of that process.
usage: search_ab.py BASE_APK FINAL_APK [rounds=3] [per_round=8]"""
import os, re, statistics, subprocess, sys, time

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "app.expenses"; ACT = PKG + "/.MainActivity"

def adb(*a, check=True):
    r = subprocess.run([ADB, *a], capture_output=True, text=True)
    if check and r.returncode != 0: sys.exit(f"adb {a} failed: {r.stderr}")
    return r.stdout

def pid():
    o = adb("shell", "pidof", PKG, check=False).strip().split()
    return o[0] if o else None

def marks(p):
    out = []
    for line in adb("logcat", "-d", "-v", "threadtime", "-s", "Startup:I").splitlines():
        parts = line.split()
        if len(parts) > 6 and parts[2] == p:
            m = re.search(r"Startup\s*: (.+?) \+(\d+)ms", line)
            if m: out.append((m.group(1), int(m.group(2))))
    return out

def one(query):
    adb("shell", "am", "force-stop", PKG); time.sleep(1.2)
    adb("shell", "am", "start", "-n", ACT); time.sleep(3.5)
    p = pid()
    adb("shell", "input", "tap", "407", "2175"); time.sleep(2.0)   # the Search tab
    adb("shell", "input", "text", query); time.sleep(2.5)
    m = marks(p)
    tapped = next((t for n, t in m if n == "search: tapped"), None)
    composed = next((t for n, t in m if n == "search: composed"), None)
    inputs = [t for n, t in m if n == "search: input"]
    results = [t for n, t in m if n == "search: results"]
    if tapped is None or composed is None or not inputs or not results: return None
    last_in = inputs[-1]
    after = [t for t in results if t >= last_in]
    first_after = after[0] if after else None
    return composed - tapped, (first_after - last_in) if first_after is not None else None

def main():
    base, final = sys.argv[1], sys.argv[2]
    rounds = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    per = int(sys.argv[4]) if len(sys.argv) > 4 else 8
    res = {"base": ([], []), "final": ([], [])}
    loads = []
    print("device:", adb("shell", "getprop", "ro.product.model").strip(), "| Android", adb("shell", "getprop", "ro.build.version.release").strip())
    for r in range(rounds):
        for name, apk in (("base", base), ("final", final)) if r % 2 == 0 else (("final", final), ("base", base)):
            adb("install", "-r", apk); time.sleep(2)
            for _ in range(2): one("Merchant 17")   # dropped: first runs after an install are cold in other ways
            l0 = os.getloadavg()[0]
            got = 0
            for _ in range(per * 2):
                if got >= per: break
                v = one("Merchant 17")
                if v and v[1] is not None:
                    res[name][0].append(v[0]); res[name][1].append(v[1]); got += 1
            loads.append((r, name, l0, os.getloadavg()[0]))
            print(f"round {r+1} {name}: open {res[name][0][-per:]}  search {res[name][1][-per:]}  load {l0:.1f}->{os.getloadavg()[0]:.1f}", flush=True)
    for name in ("base", "final"):
        o, s = res[name]
        print(f"{name}: tab-to-composed median {statistics.median(o):.0f} ms (range {min(o)}-{max(o)}, n={len(o)}); typed-to-results median {statistics.median(s):.0f} ms (range {min(s)}-{max(s)}, n={len(s)})")
    print("host load (1-min) per phase:", [(r + 1, n, round(a, 1), round(b, 1)) for r, n, a, b in loads])

main()
