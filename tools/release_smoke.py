#!/usr/bin/env python3
"""Drives the RELEASE (R8-minified) build of the app on the shared emulator through the user-visible flows, with adb and `uiautomator dump` only.

Why it exists: a bug once reached the owner that existed only in the minified release build (R8 broke ML Kit text recognition, "Attempt to read from field ... on a
null object reference") while every debug and instrumented test passed. Run it on every release candidate (tools/release_smoke.sh builds the APK first).

Normally started by tools/release_smoke.sh (which builds and, if needed, signs the APK). Directly: python3 tools/release_smoke.py path/to/app.apk
Environment: ANDROID_SERIAL (default emulator-5554; a physical device is refused unless SMOKE_ALLOW_PHYSICAL=1, since the app and its data are uninstalled first), ADB.

Steps (each is named in the summary; the first failure stops the run and the exit status is 1):
  1 take the emulator lock (/tmp/emu.lock, shared with every other user of the emulator)   2 uninstall and install the APK
  3 first launch, skip the tour   4 add an expense of 123 and save it   5 see it in the list (₹123.00)   6 find it in Search
  7 bill photo: push the invented receipt (tools/fixtures/bill_fixture.png), Add > Read items from a photo > Choose a photo > pick it in the system picker,
    assert that the dialog lists the receipt's three items and its total (the real ML Kit recognizer runs inside the release build)
On every exit path the pushed picture is deleted, the release app is uninstalled (it is signed with another key than the debug build that other users of the emulator install)
and the lock is released.

Screens are found by their TEXT or content description: testTags are not visible to uiautomator. If a label in the app changes, change it here.
"""
import os
import re
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADB = os.environ.get("ADB") or os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PACKAGE = "app.expenses"
LOCK = "/tmp/emu.lock"
FIXTURE = os.path.join(ROOT, "tools", "fixtures", "bill_fixture.png")
DEVICE_FIXTURE = "/sdcard/Download/bill_fixture.png"
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")

# What the fixture receipt says (see tools/fixtures/make_bill_fixture.swift): three items, 5% GST, a grand total.
EXPECTED_ITEMS = [("Lemon Rice", "₹80.00"), ("Almond Butter", "₹325.00"), ("Green Tea", "₹140.00")]
EXPECTED_TOTAL = "₹572.25"
NOTE = "Coffee"  # one word: `adb shell input text` needs %s for a space


class StepFailed(Exception):
    pass


class Node:
    def __init__(self, e):
        self.text = e.get("text", "")
        self.desc = e.get("content-desc", "")
        self.cls = e.get("class", "")
        self.clickable = e.get("clickable") == "true"
        self.enabled = e.get("enabled") == "true"
        self.focused = e.get("focused") == "true"
        l, t, r, b = map(int, re.findall(r"-?\d+", e.get("bounds", "[0,0][0,0]")))
        self.bounds = (l, t, r, b)

    @property
    def centre(self):
        l, t, r, b = self.bounds
        return (l + r) // 2, (t + b) // 2

    def __repr__(self):
        return f"<{self.cls.split('.')[-1]} text={self.text!r} desc={self.desc!r} {self.bounds}>"


def adb(*args, check=True, timeout=120):
    p = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=timeout)
    if check and p.returncode != 0:
        raise StepFailed(f"adb {' '.join(args)} failed: {p.stderr.strip() or p.stdout.strip()}")
    return p.stdout


class Screen:
    """The current screen as uiautomator sees it."""

    def __init__(self):
        self.nodes = []

    def refresh(self):
        # `uiautomator dump` is flaky right after a transition ("could not get idle state"): retry a few times
        for _ in range(6):
            out = adb("shell", "uiautomator", "dump", "/sdcard/robust_ui.xml", check=False)
            if "dumped to" in out:
                xml = adb("exec-out", "cat", "/sdcard/robust_ui.xml", check=False)
                try:
                    self.nodes = [Node(e) for e in ET.fromstring(xml).iter("node")]
                    return self
                except ET.ParseError:
                    pass
            time.sleep(1)
        raise StepFailed("uiautomator dump kept failing")

    def find(self, text=None, desc=None, contains=False, cls=None):
        """The first node whose text (or content description) is [text] (exact, or a substring with contains=True)."""
        def match(value, want):
            return want in value if contains else value == want
        for n in self.nodes:
            if cls and not n.cls.endswith(cls):
                continue
            if text is None and desc is None:
                return n  # by class alone
            if text is not None and (match(n.text, text) or match(n.desc, text)):
                return n
            if desc is not None and match(n.desc, desc):
                return n
        return None

    def texts(self):
        return [t for n in self.nodes for t in (n.text, n.desc) if t]


screen = Screen()


def sh(*args):
    return adb("shell", *args)


def tap_xy(x, y):
    sh("input", "tap", str(x), str(y))


def wait_for(what, text=None, desc=None, contains=False, cls=None, timeout=20):
    """Waits until a node with the given text or description is on screen; returns it."""
    end = time.time() + timeout
    while True:
        screen.refresh()
        n = screen.find(text=text, desc=desc, contains=contains, cls=cls)
        if n:
            return n
        if time.time() > end:
            raise StepFailed(f"waiting for {what}: not on screen after {timeout}s. Visible: {screen.texts()[:40]}")
        time.sleep(0.7)


def tap(what, text=None, desc=None, contains=False, cls=None, timeout=20):
    n = wait_for(what, text=text, desc=desc, contains=contains, cls=cls, timeout=timeout)
    tap_xy(*n.centre)
    time.sleep(0.8)
    return n


def screen_size():
    m = re.search(r"(\d+)x(\d+)", sh("wm", "size"))
    return int(m.group(1)), int(m.group(2))


def scroll_to_and_tap(what, text, max_swipes=8):
    """The form scrolls: swipes up until the text is on screen, then taps it."""
    _, h = screen_size()
    for _ in range(max_swipes):
        screen.refresh()
        n = screen.find(text=text)
        # only when it is well inside the form: a node cut off by the pinned Save bar would be tapped on the bar
        if n and n.centre[1] < h * 0.58 and n.bounds[3] - n.bounds[1] >= 20:
            tap_xy(*n.centre)
            time.sleep(0.8)
            return n
        w, h = screen_size()
        sh("input", "swipe", str(w // 2), str(int(h * 0.53)), str(w // 2), str(int(h * 0.18)), "400")  # inside the form, above the pinned Save bar and the keyboard
        time.sleep(0.6)
    raise StepFailed(f"scrolling to {what}: never found {text!r}. Visible: {screen.texts()[:40]}")


def logcat_evidence(since_epoch):
    """The lines of the device log that matter after a failure."""
    out = adb("logcat", "-d", "-v", "time", check=False)
    keep = [l for l in out.splitlines() if re.search(r"FATAL EXCEPTION|null object reference|NullPointerException|E/AndroidRuntime|Process: app\.expenses|ANR in", l)]
    return "\n".join(keep[-25:])


# ---- the lock on the shared emulator -------------------------------------------------------------------------------------------------------------------

class EmulatorLock:
    held = False

    @classmethod
    def take(cls):
        waited = 0
        while True:
            try:
                os.mkdir(LOCK)
                cls.held = True
                return
            except FileExistsError:
                if waited % 120 == 0:
                    print(f"  waiting for the emulator lock {LOCK} ({waited}s so far)", flush=True)
                time.sleep(20)
                waited += 20

    @classmethod
    def release(cls):
        if cls.held:
            try:
                os.rmdir(LOCK)
            except OSError:
                pass
            cls.held = False


def on_signal(signum, _frame):
    EmulatorLock.release()
    sys.exit(128 + signum)


# ---- the steps -----------------------------------------------------------------------------------------------------------------------------------------

def step_lock_and_device():
    if not SERIAL.startswith("emulator-") and os.environ.get("SMOKE_ALLOW_PHYSICAL") != "1":
        raise StepFailed(f"{SERIAL} is not an emulator; the smoke test uninstalls the app (its data) first. Set SMOKE_ALLOW_PHYSICAL=1 to allow it.")
    EmulatorLock.take()
    state = adb("get-state", check=False).strip()
    if state != "device":
        raise StepFailed(f"{SERIAL} is not available (get-state: {state or 'none'}). Start the emulator first (see the notes in tools/release_smoke.sh).")


def step_install(apk):
    adb("uninstall", PACKAGE, check=False)
    out = adb("install", "-r", apk, timeout=300)
    if "Success" not in out:
        raise StepFailed(f"install failed: {out.strip()}")
    # the release build has no test-seeding, so the first launch is a real first launch
    sh("input", "keyevent", "KEYCODE_WAKEUP")
    sh("wm", "dismiss-keyguard")


def step_first_launch():
    sh("am", "force-stop", PACKAGE)
    sh("monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    # the tour is shown on a fresh install; it can be skipped
    end = time.time() + 40
    while True:
        screen.refresh()
        if screen.find(text="Skip the tour"):
            tap("Skip the tour", text="Skip the tour")
            break
        if screen.find(text="Add my first expense"):
            break  # no tour this time
        if time.time() > end:
            raise StepFailed(f"the tour or the welcome card never appeared. Visible: {screen.texts()[:40]}")
        time.sleep(1)
    wait_for("the welcome card", text="Add my first expense", timeout=20)


def step_add_expense():
    tap("Add my first expense", text="Add my first expense")
    amount = wait_for("the amount field", cls="EditText", timeout=20)
    tap_xy(*amount.centre)
    time.sleep(0.5)
    sh("input", "text", "123")
    time.sleep(0.8)
    screen.refresh()
    typed = screen.find(cls="EditText")
    if not typed or "123" not in typed.text:
        raise StepFailed(f"the amount field does not show 123 after typing it. Visible: {screen.texts()[:40]}")
    # a note, so that Search (which looks in notes and merchants, not in amounts) can find the entry
    scroll_to_and_tap("the note field", "Note or merchant")
    sh("input", "text", NOTE)
    time.sleep(0.8)
    if screen.refresh().find(text="Save") is None:
        sh("input", "keyevent", "KEYCODE_BACK")  # close a keyboard that hides Save
    tap("Save", text="Save")


def step_see_in_list():
    wait_for("the saved expense (₹123.00) in the list", text="₹123.00", contains=True, timeout=20)


def step_search():
    tap("the Search tab", text="Search")
    field = wait_for("the search field", cls="EditText", timeout=20)
    tap_xy(*field.centre)
    time.sleep(0.5)
    sh("input", "text", NOTE)
    wait_for("the expense in the search results", text="₹123.00", contains=True, timeout=20)
    if screen.find(text="Nothing found."):
        raise StepFailed("Search shows the expense and also 'Nothing found.'")
    # back to Home for the next step
    tap("Back", text="Back")
    wait_for("Home again", text="Spent this month", timeout=20)


def step_bill_photo():
    if not os.path.exists(FIXTURE):
        raise StepFailed(f"the fixture picture {FIXTURE} is missing; make it with: swift tools/fixtures/make_bill_fixture.swift tools/fixtures/bill_fixture.png")
    adb("push", FIXTURE, DEVICE_FIXTURE)
    sh("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file://" + DEVICE_FIXTURE)
    time.sleep(2)
    # the Add button on Home shows "+ Add", or only "+" once the list has been scrolled
    end = time.time() + 20
    while True:
        screen.refresh()
        fab = screen.find(text="Add") or screen.find(text="+")
        if fab:
            tap_xy(*fab.centre)
            time.sleep(1)
            break
        if time.time() > end:
            raise StepFailed(f"the Add button is not on Home. Visible: {screen.texts()[:40]}")
        time.sleep(1)
    scroll_to_and_tap("'Read items from a photo'", "Read items from a photo")
    scroll_to_and_tap("'Choose a photo'", "Choose a photo")
    pick_in_system_picker()
    # recognition runs on the phone; the dialog, or a plain problem message, follows
    end = time.time() + 90
    while True:
        screen.refresh()
        if screen.find(text="Items on the bill") or screen.find(text="Nothing readable"):
            break
        problem = next((t for t in screen.texts() if "could not be read" in t or "Could not read" in t or "could not be opened" in t), None)
        if problem:
            raise StepFailed(f"reading the bill photo failed in the release build: {problem}")
        if time.time() > end:
            raise StepFailed(f"no bill dialog appeared within 90 s. Visible: {screen.texts()[:40]}")
        time.sleep(1)
    if screen.find(text="Nothing readable"):
        raise StepFailed("the dialog says 'Nothing readable' for the fixture receipt")
    seen = screen.texts()
    for name, amount in EXPECTED_ITEMS:
        if not any(name in t for t in seen):
            raise StepFailed(f"the item {name!r} is not in the dialog. Visible: {seen}")
        if amount not in seen:
            raise StepFailed(f"the price {amount} is not in the dialog. Visible: {seen}")
    if not any("Total on the bill" in t and EXPECTED_TOTAL in t for t in seen):
        raise StepFailed(f"'Total on the bill: {EXPECTED_TOTAL}' is not in the dialog. Visible: {seen}")
    if not any("Amount to use" in t and EXPECTED_TOTAL in t for t in seen):
        raise StepFailed(f"'Amount to use: {EXPECTED_TOTAL}' is not in the dialog. Visible: {seen}")
    # close the dialog and leave the form
    tap("Cancel", text="Cancel")
    sh("input", "keyevent", "KEYCODE_BACK")


def pick_in_system_picker():
    """The system file picker (DocumentsUI): shows the picture under Recent, or under Downloads from the side drawer."""
    name = "bill_fixture.png"
    end = time.time() + 40
    drawer_tried = False
    while time.time() < end:
        screen.refresh()
        n = screen.find(text=name)
        if n:
            tap_xy(*n.centre)
            time.sleep(1.5)
            return
        if not drawer_tried:
            roots = screen.find(desc="Show roots") or screen.find(desc="Show roots", contains=True)
            if roots:
                tap_xy(*roots.centre)
                time.sleep(1)
                screen.refresh()
                downloads = screen.find(text="Downloads")
                if downloads:
                    tap_xy(*downloads.centre)
                    time.sleep(1.5)
                    drawer_tried = True
                    continue
        time.sleep(1)
    raise StepFailed(f"the file picker never listed {name}. Visible: {screen.texts()[:40]}")


STEPS = [
    ("lock the emulator and check the device", step_lock_and_device, False),
    ("uninstall and install the release APK", step_install, True),
    ("first launch, skip the tour", step_first_launch, False),
    ("add an expense of 123 and save it", step_add_expense, False),
    ("see ₹123.00 in the list", step_see_in_list, False),
    ("find it in Search", step_search, False),
    ("bill photo: items and total in the dialog", step_bill_photo, False),
]


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        print(__doc__)
        print("usage: release_smoke.py path/to/app.apk")
        return 2
    apk = sys.argv[1]
    signal.signal(signal.SIGTERM, on_signal)
    signal.signal(signal.SIGINT, on_signal)
    results = []
    failure = None
    started = time.time()
    try:
        for i, (name, fn, wants_apk) in enumerate(STEPS, 1):
            t0 = time.time()
            print(f"[{i}/{len(STEPS)}] {name} ...", flush=True)
            try:
                fn(apk) if wants_apk else fn()
                results.append((name, "PASS", time.time() - t0))
                print(f"      PASS ({time.time() - t0:.1f}s)", flush=True)
            except StepFailed as e:
                results.append((name, "FAIL", time.time() - t0))
                failure = (name, str(e))
                print(f"      FAIL: {e}", flush=True)
                break
            except subprocess.TimeoutExpired as e:
                results.append((name, "FAIL", time.time() - t0))
                failure = (name, f"timed out: {e}")
                print(f"      FAIL: timed out: {e}", flush=True)
                break
            except Exception as e:  # a bug in this driver: still a named, failing step, and the lock is still released
                results.append((name, "FAIL", time.time() - t0))
                failure = (name, f"driver error {type(e).__name__}: {e}")
                print(f"      FAIL: driver error {type(e).__name__}: {e}", flush=True)
                break
    finally:
        # always: delete the pushed picture and the release app (it is signed with another key than the debug build, which other users of the emulator install:
        # leaving it would make their install fail), then release the lock
        if EmulatorLock.held:
            try:
                adb("shell", "rm", "-f", DEVICE_FIXTURE, "/sdcard/robust_ui.xml", check=False, timeout=30)
            except Exception:
                pass
        evidence = ""
        if failure and EmulatorLock.held:
            try:
                evidence = logcat_evidence(started)
            except Exception:
                pass
        if EmulatorLock.held:
            try:
                adb("uninstall", PACKAGE, check=False, timeout=60)
            except Exception:
                pass
        EmulatorLock.release()
    print("\n==== release smoke test: " + ("FAIL" if failure else "PASS") + " ====")
    for name, state, secs in results:
        print(f"  {state:4}  {name}  ({secs:.1f}s)")
    for name, _, _ in STEPS[len(results):]:
        print(f"  skip  {name}")
    if failure:
        print(f"\nFAILED STEP: {failure[0]}\n  {failure[1]}")
        if evidence:
            print("\ndevice log (matching lines):\n" + evidence)
        return 1
    print(f"\n  all {len(STEPS)} steps passed in {time.time() - started:.0f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
