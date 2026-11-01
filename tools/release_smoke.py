#!/usr/bin/env python3
"""Drives the RELEASE (R8-minified) build of the app on the shared emulator through the user-visible flows, with adb and `uiautomator dump` only.

Why it exists: a bug once reached the owner that existed only in the minified release build (R8 broke ML Kit text recognition, "Attempt to read from field ... on a
null object reference") while every debug and instrumented test passed. Run it on every release candidate (tools/release_smoke.sh builds the APK first).

Normally started by tools/release_smoke.sh (which builds and, if needed, signs the APK). Directly: python3 tools/release_smoke.py path/to/app.apk
Environment: ANDROID_SERIAL (default: the one running emulator, or the one with the newest Android when several run; a physical device is refused unless SMOKE_ALLOW_PHYSICAL=1, since the
app and its data are uninstalled first), ADB, SMOKE_LOCK_HELD=1 (the caller already holds the emulator lock /tmp/emu.lock and releases it), SMOKE_WIDGETS=0 (leave out the widget steps).

Steps (each is named in the summary; the first failure stops the run and the exit status is 1):
  1 take the emulator lock (/tmp/emu.lock, shared with every other user of the emulator)   2 uninstall and install the APK
  3 first launch, skip the tour   4 add an expense of 123 and save it   5 see it in the list (₹123.00)   6 find it in Search
  7 bill photo: push the invented receipt (tools/fixtures/bill_fixture.png), Add > Read items from a photo > Choose a photo > pick it in the system picker,
    assert that the dialog lists the receipt's three items and its total (the real ML Kit recognizer runs inside the release build)
  Widget steps (Android 12 and later: the launcher's own widget picker is driven; the Android 8 emulator's is not, so there they are listed as skipped; see docs/RELEASE_CHECKLIST.md):
  8 save a quick entry "Chai" of 20   9 place the "Spending: month and quick add (4 x 2)" widget on the home screen through the launcher's widget picker
  10 the widget is masked, tap the eye: the total appears (₹143.00) and the eye offers to hide it; tap again: masked   11 tap the "Chai" chip: "Added Chai" appears on the widget and
  the entry is in the app (Search)   12 the same from a COLD process (the app force-stopped before each tap)   13 Hide amounts on: the widget says "Amounts hidden", has no eye, and a chip still adds
  (the confirmation has no amount); off again   14 no `GlanceAppWidget` error in the device log during all of this (a failed tap logs one: `NoSuchMethodException` is what R8 caused in 0.10.0 to 0.10.2)
On every exit path the pushed picture is deleted, the release app is uninstalled (it is signed with another key than the debug build that other users of the emulator install)
and the lock is released. Before any of this, tools/release_smoke.sh runs tools/check_release_keeps.py on the APK (R8 must have kept the classes that are created by name).

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
def resolve_serial():
    """ANDROID_SERIAL if set; otherwise the running emulator (with several, the one with the newest Android, which is the one the widget steps need)."""
    if os.environ.get("ANDROID_SERIAL"):
        return os.environ["ANDROID_SERIAL"]
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    emulators = [l.split()[0] for l in out.splitlines()[1:] if l.startswith("emulator-") and l.split()[1] == "device"]
    if not emulators:
        return "emulator-5554"
    if len(emulators) == 1:
        return emulators[0]
    def sdk_of(serial):
        p = subprocess.run([ADB, "-s", serial, "shell", "getprop", "ro.build.version.sdk"], capture_output=True, text=True)
        return int(p.stdout.strip() or 0)
    return max(emulators, key=sdk_of)


SERIAL = resolve_serial()

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
    keep = [l for l in out.splitlines() if re.search(r"FATAL EXCEPTION|null object reference|NullPointerException|E/AndroidRuntime|Process: app\.expenses|ANR in|E/GlanceAppWidget|E/ExpensesWidget|W/ExpensesWidget|NoSuchMethodException", l)]
    return "\n".join(keep[-25:])


# ---- the lock on the shared emulator -------------------------------------------------------------------------------------------------------------------

class EmulatorLock:
    held = False
    external = False  # SMOKE_LOCK_HELD=1: whoever started this run holds /tmp/emu.lock and releases it

    @classmethod
    def take(cls):
        if os.environ.get("SMOKE_LOCK_HELD") == "1":
            cls.held = True
            cls.external = True
            return
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
            if not cls.external:
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
    sh("am", "start", "-n", f"{PACKAGE}/.MainActivity")  # not `monkey`: on Android 14 it sometimes exits with an error without starting anything
    # the tour is shown on a fresh install; it can be skipped
    end = time.time() + 40
    while True:
        screen.refresh()
        if screen.find(text="Skip the tour"):
            tap("Skip the tour", text="Skip the tour")
            for _ in range(3):  # a slow emulator can swallow the first tap
                time.sleep(2)
                if screen.refresh().find(text="Skip the tour") is None:
                    break
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
        # Android 8 shows the bare name; Android 14's picker names the row "bill_fixture.png, 77.74 kB, Oct 27" (and has a separate "Preview the file ..." button, which must not be tapped)
        n = next((x for x in screen.nodes if x.text == name or x.desc == name or x.text.startswith(name + ",") or x.desc.startswith(name + ",")), None)
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


# ---- the widget steps ----------------------------------------------------------------------------------------------------------------------------------
# Android 12 and later. The launcher's own widget picker (the Pixel Launcher of the API 34 emulator) is driven with a long press, a tap and a drag, the way a person places a widget, and the
# widget is then used through the launcher, which hosts it: taps go through the real PendingIntents and Glance's receiver, in the minified build. The widget's texts and content descriptions
# are what `uiautomator dump` reads. Widths: the 4 x 2 widget is a CARD (header with the eye and Add, the quick entries, "Where it went").

WIDGET_LABEL = "Spending: month and quick add (4 x 2)"
QUICK_NOTE = "Chai"
QUICK_AMOUNT = "20"
MASKED = "₹••••"
TOTAL_AFTER_QUICK_ENTRY = "₹143.00"   # 123 from step 4 and 20 from step 8
TOTAL_AFTER_CHIP = "₹163.00"          # and one tap on the chip
TOTAL_AFTER_COLD_CHIP = "₹183.00"     # and one more from a process that was not running


def sdk_level():
    return int(sh("getprop", "ro.build.version.sdk").strip() or 0)


def widget_steps_unavailable():
    """Why the widget steps cannot run on this device (None when they can)."""
    if os.environ.get("SMOKE_WIDGETS") == "0":
        return "SMOKE_WIDGETS=0"
    if sdk_level() < 31:
        return f"this device is Android API {sdk_level()}; the widget steps drive the Pixel Launcher of Android 12 and later: start the API34 AVD (docs/RELEASE_CHECKLIST.md)"
    return None


def launch_app():
    sh("am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(2)


def go_home():
    sh("input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.5)


def open_add_form():
    end = time.time() + 20
    while True:
        screen.refresh()
        fab = screen.find(text="Add") or screen.find(text="+")
        if fab:
            tap_xy(*fab.centre)
            time.sleep(1.2)
            return
        if time.time() > end:
            raise StepFailed(f"the Add button is not on Home. Visible: {screen.texts()[:40]}")
        time.sleep(1)


def step_save_quick_entry():
    launch_app()
    # the bill-photo step can leave its (empty) form open under the keyboard: close it first, or Add would tap the form's own tag button
    for _ in range(3):
        screen.refresh()
        if screen.find(text="Cancel") and screen.find(text="Save") and screen.find(text="Add") and screen.find(text="Receipts and files"):
            tap("Cancel", text="Cancel")
            time.sleep(1)
            if screen.refresh().find(text="Discard"):
                tap("Discard", text="Discard")
        else:
            break
    open_add_form()
    # the form can open still scrolled down from the bill-photo step: bring its top (the amount) back, or the first text box found would be the tag box
    w0, h0 = screen_size()
    for _ in range(4):
        sh("input", "swipe", str(w0 // 2), str(int(h0 * 0.25)), str(w0 // 2), str(int(h0 * 0.6)), "300")
        time.sleep(0.4)
    amount = wait_for("the amount field", cls="EditText", timeout=20)
    tap_xy(*amount.centre)
    time.sleep(0.4)
    sh("input", "text", QUICK_AMOUNT)
    time.sleep(0.6)
    scroll_to_and_tap("the note field", "Note or merchant")
    sh("input", "text", QUICK_NOTE)
    time.sleep(0.6)
    if screen.refresh().find(text="Save") is None:
        sh("input", "keyevent", "KEYCODE_BACK")  # close a keyboard that hides the checkbox
        time.sleep(0.8)
    # the checkbox beside "Save as a quick entry" (the form has another one, "I expect this back", higher up)
    w, h = screen_size()
    box = None
    for _ in range(8):
        screen.refresh()
        label = screen.find(text="Save as a quick entry")
        if label and label.centre[1] < h * 0.8 and label.bounds[3] - label.bounds[1] >= 20:
            box = next((n for n in screen.nodes if n.cls.endswith("CheckBox") and abs(n.centre[1] - label.centre[1]) < 60 and n.bounds[2] - n.bounds[0] < 200), None)
            if box:
                break
        sh("input", "swipe", str(w // 2), str(int(h * 0.47)), str(w // 2), str(int(h * 0.21)), "400")
        time.sleep(0.6)
    if box is None:
        raise StepFailed(f"the 'Save as a quick entry' checkbox never came on screen. Visible: {screen.texts()[:40]}")
    tap_xy(*box.centre)
    time.sleep(0.6)
    tap("Save", text="Save")
    wait_for(f"the quick entry {QUICK_NOTE} on Home", text=f"{QUICK_NOTE} ₹{QUICK_AMOUNT}.00", timeout=20)


def find_in_picker(text, low=0.2, tries=16):
    """Scrolls the widget picker's list until a node with exactly this text is well inside it."""
    w, h = screen_size()
    for _ in range(tries):
        screen.refresh()
        n = next((x for x in screen.nodes if x.text == text and h * low < x.centre[1] < h * 0.88), None)
        if n:
            return n
        sh("input", "swipe", str(w // 2), str(int(h * 0.74)), str(w // 2), str(int(h * 0.52)), "400")
        time.sleep(0.8)
    raise StepFailed(f"the widget picker never showed {text!r}. Visible: {screen.texts()[:40]}")


def step_place_widget():
    adb("logcat", "-c", check=False)  # what is checked at the end is only what these steps caused
    go_home()
    w, h = screen_size()
    for attempt in range(4):  # the first long press can come before the launcher has settled after the app closed
        sh("input", "swipe", str(w // 2), str(int(h * 0.45)), str(w // 2), str(int(h * 0.45)), "1300")  # a long press on the empty home screen
        time.sleep(1.5)
        if screen.refresh().find(text="Widgets"):
            break
        go_home()
    tap("the launcher's Widgets entry", text="Widgets", timeout=15)
    time.sleep(2)
    tap_xy(*find_in_picker("Expenses").centre)  # the app's entry opens its list of widgets
    time.sleep(1.5)
    label = find_in_picker(WIDGET_LABEL, low=0.35)
    # the preview is drawn above its name: drag it onto the empty part of the home screen
    sh("input", "draganddrop", str(w // 2), str(label.bounds[1] - 230), str(w // 2), str(int(h * 0.34)), "1500")
    time.sleep(3)
    # the launcher shows a resize frame after a drop and `uiautomator dump` sees nothing while it animates: tap an empty spot first (it leaves the frame), then read
    tap_xy(w // 2, int(h * 0.85))
    time.sleep(2)
    wait_for("the widget on the home screen", text="Spent this month", contains=True, timeout=30)


def step_widget_eye():
    go_home()
    wait_for("the widget, masked at first", text=MASKED, timeout=30)
    eye = wait_for("the eye (Show the amount)", desc="Show the amount", timeout=15)
    tap_xy(*eye.centre)
    wait_for(f"the total {TOTAL_AFTER_QUICK_ENTRY} on the widget after one tap on the eye", text=TOTAL_AFTER_QUICK_ENTRY, timeout=30)
    eye = wait_for("the eye now offering to hide the amount", desc="Hide the amount", timeout=15)
    tap_xy(*eye.centre)
    wait_for("the widget masked again after the second tap", text=MASKED, timeout=30)
    wait_for("the eye offering to show it again", desc="Show the amount", timeout=15)
    if screen.find(text=TOTAL_AFTER_QUICK_ENTRY):
        raise StepFailed("the total is still on the widget after hiding it")


def search_count(term, amount):
    """How many rows of the app's Search for [term] show [amount]."""
    launch_app()
    tap("the Search tab", text="Search")
    field = wait_for("the search field", cls="EditText", timeout=20)
    tap_xy(*field.centre)
    time.sleep(0.5)
    sh("input", "text", term)
    time.sleep(2)
    screen.refresh()
    count = sum(1 for t in screen.texts() if amount in t)
    tap("Back", text="Back")
    wait_for("Home again", text="Spent this month", timeout=20)
    return count


def step_widget_chip():
    go_home()
    chip = wait_for(f"the {QUICK_NOTE} chip on the widget", text=QUICK_NOTE, timeout=30)
    tap_xy(*chip.centre)
    wait_for("the confirmation on the widget", text=f"Added {QUICK_NOTE}", contains=True, timeout=30)
    eye = wait_for("the eye", desc="Show the amount", timeout=15)
    tap_xy(*eye.centre)
    wait_for(f"the total {TOTAL_AFTER_CHIP} (the chip's entry counted)", text=TOTAL_AFTER_CHIP, timeout=30)
    tap_xy(*wait_for("the eye offering to hide", desc="Hide the amount", timeout=15).centre)
    wait_for("the widget masked again", text=MASKED, timeout=30)
    n = search_count(QUICK_NOTE, f"₹{QUICK_AMOUNT}.00")
    if n < 2:
        raise StepFailed(f"Search for {QUICK_NOTE} shows {n} rows of ₹{QUICK_AMOUNT}.00; the entry typed in the app and the one the chip added should both be there")


def widget_log_ages():
    """The 'process age' of every widget tap in the device log: how old the app's process was when the tap reached it."""
    out = adb("logcat", "-d", "-s", "ExpensesWidget:I", check=False)
    return [int(m) for m in re.findall(r"tapped \(process age (\d+) ms\)", out)]


def step_widget_cold_process():
    before = len(widget_log_ages())
    sh("am", "force-stop", PACKAGE)
    time.sleep(1.5)
    go_home()
    eye = wait_for("the eye (cold process)", desc="Show the amount", timeout=30)
    tap_xy(*eye.centre)
    wait_for(f"the total {TOTAL_AFTER_CHIP} after an eye tap that had to start the app's process", text=TOTAL_AFTER_CHIP, timeout=45)
    sh("am", "force-stop", PACKAGE)
    time.sleep(1.5)
    go_home()
    chip = wait_for(f"the {QUICK_NOTE} chip (cold process)", text=QUICK_NOTE, timeout=30)
    tap_xy(*chip.centre)
    wait_for(f"the total {TOTAL_AFTER_COLD_CHIP} after a chip tap that had to start the app's process", text=TOTAL_AFTER_COLD_CHIP, timeout=45)
    ages = widget_log_ages()[before:]
    print(f"      process age at each tap in this step (ms): {ages}")
    if len(ages) < 2 or max(ages[:2]) > 30_000:
        raise StepFailed(f"the device log shows no tap (or an old process) where the process had been force-stopped: {ages}")


def toggle_hide_amounts():
    launch_app()
    tap("the More tab", text="More")
    row = screen.refresh().find(text="Hide amounts") or screen.find(text="Show amounts")
    if row is None:
        raise StepFailed(f"the More page has no 'Hide amounts' row. Visible: {screen.texts()[:40]}")
    tap_xy(*row.centre)
    time.sleep(1)


def step_widget_hide_amounts():
    toggle_hide_amounts()
    go_home()  # leaving the app redraws the widgets
    wait_for("the widget saying why it is masked", text=f"Spent this month · Amounts hidden", timeout=30)
    if screen.find(desc="Show the amount") or screen.find(desc="Hide the amount"):
        raise StepFailed("the widget offers the eye although Hide amounts is on")
    chip = wait_for(f"the {QUICK_NOTE} chip", text=QUICK_NOTE, timeout=15)
    tap_xy(*chip.centre)
    added = wait_for("the confirmation on the widget", text=f"Added {QUICK_NOTE}", contains=True, timeout=30)
    if any(c.isdigit() for c in added.text):
        raise StepFailed(f"the confirmation shows an amount although Hide amounts is on: {added.text!r}")
    toggle_hide_amounts()
    go_home()
    wait_for("the caption without the reason once Hide amounts is off", text="Spent this month", timeout=30)
    wait_for("the eye back", desc="Hide the amount", timeout=30)


def step_no_glance_errors():
    out = adb("logcat", "-d", "-s", "GlanceAppWidget:E", "ExpensesWidget:W", check=False)
    bad = [l for l in out.splitlines() if l.startswith(("E/", "W/"))]
    if bad:
        raise StepFailed("the device log has widget errors:\n    " + "\n    ".join(bad[:12]))


WIDGET_STEPS = []


def widget_step(fn):
    WIDGET_STEPS.append(fn)
    return fn


STEPS = [
    ("lock the emulator and check the device", step_lock_and_device, False),
    ("uninstall and install the release APK", step_install, True),
    ("first launch, skip the tour", step_first_launch, False),
    ("add an expense of 123 and save it", step_add_expense, False),
    ("see ₹123.00 in the list", step_see_in_list, False),
    ("find it in Search", step_search, False),
    ("bill photo: items and total in the dialog", step_bill_photo, False),
    ("widget: save a quick entry (Chai, 20)", widget_step(step_save_quick_entry), False),
    ("widget: place 'Spending: month and quick add' through the launcher", widget_step(step_place_widget), False),
    ("widget: the eye shows the total and hides it again", widget_step(step_widget_eye), False),
    ("widget: a chip adds the entry (shown in the app)", widget_step(step_widget_chip), False),
    ("widget: eye and chip from a process that was not running", widget_step(step_widget_cold_process), False),
    ("widget: Hide amounts says so, adds without an amount, and goes back", widget_step(step_widget_hide_amounts), False),
    ("widget: no widget error in the device log", widget_step(step_no_glance_errors), False),
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
        unavailable = None
        for i, (name, fn, wants_apk) in enumerate(STEPS, 1):
            t0 = time.time()
            print(f"[{i}/{len(STEPS)}] {name} ...", flush=True)
            if fn in WIDGET_STEPS:
                if unavailable is None:
                    unavailable = widget_steps_unavailable() or ""
                if unavailable:
                    results.append((name, "SKIP", 0.0))
                    print(f"      SKIP: {unavailable}", flush=True)
                    continue
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
                shot = subprocess.run([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True, timeout=30).stdout
                with open("/tmp/release_smoke_failure.png", "wb") as f:
                    f.write(shot)
                evidence += "\n(the screen at the failure: /tmp/release_smoke_failure.png)"
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
    passed = sum(1 for _, state, _ in results if state == "PASS")
    skipped = sum(1 for _, state, _ in results if state == "SKIP")
    print(f"\n  {passed} steps passed" + (f", {skipped} skipped (see above)" if skipped else "") + f" in {time.time() - started:.0f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
