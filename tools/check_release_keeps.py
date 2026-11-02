#!/usr/bin/env python3
"""Fails when R8 broke a class that something creates BY NAME at run time. Looks at the built release APK itself (dexdump, apkanalyzer) plus the build's mapping.txt.

Why it exists: in 0.10.0 to 0.10.2 the home-screen widget's eye and quick-entry chips did nothing on every phone. Glance creates the class behind each tap with
Class.forName(name).getDeclaredConstructor().newInstance(); R8 had deleted the no-argument constructors of ToggleTotalAction, QuickAddAction and UndoAction (nothing in the app calls them),
so every tap ended in `NoSuchMethodException: <init> []` in the log and nothing on the screen. It also merged TodayWidget into QuickAddWidget (one class, one name), which Glance, which tells
widgets apart by class name, cannot cope with. Debug builds and every instrumented test passed, because they never run R8. Only the release build can show this, so check it there.

Checks (each failure is printed with the class):
  1 every class that implements Glance's ActionCallback has a no-argument constructor (at least ToggleTotalAction, QuickAddAction, UndoAction exist);
  2 QuickAddWidget and TodayWidget (the GlanceAppWidget subclasses) exist as two separate classes, each with one;
  3 every component declared in the merged manifest (application, activities, services, receivers, providers) exists with a no-argument constructor;
  4 every WorkManager worker of the app (hourly check, snooze, automatic copy, the widget's Undo-line expiry) still has its (Context, WorkerParameters) constructor: WorkManager creates it by name.

Usage: tools/check_release_keeps.py path/to/app.apk [path/to/mapping.txt]   (mapping defaults to androidApp/build/outputs/mapping/release/mapping.txt)
Exit status: 0 all good, 1 a check failed, 2 the tools or inputs are missing. Needs the Android SDK build-tools (dexdump) and cmdline-tools (apkanalyzer); ANDROID_HOME or ~/Library/Android/sdk.
Called by tools/release_smoke.sh; also run it by hand after any change to androidApp/proguard-rules.pro, a library, or the Android Gradle plugin or Kotlin version.
"""
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SDK = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Library/Android/sdk")
DEFAULT_MAPPING = os.path.join(ROOT, "androidApp", "build", "outputs", "mapping", "release", "mapping.txt")
ACTION_CALLBACK = "androidx.glance.appwidget.action.ActionCallback"
GLANCE_APP_WIDGET = "androidx.glance.appwidget.GlanceAppWidget"
EXPECTED_CALLBACKS = ["app.expenses.widget.ToggleTotalAction", "app.expenses.widget.QuickAddAction", "app.expenses.widget.UndoAction"]
EXPECTED_WIDGETS = ["app.expenses.widget.QuickAddWidget", "app.expenses.widget.TodayWidget"]
EXPECTED_WORKERS = ["app.expenses.NotificationWorker", "app.expenses.SnoozedNoticeWorker", "app.expenses.AutoExportWorker", "app.expenses.widget.WidgetUndoExpiryWorker"]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def tool(pattern):
    found = sorted(glob.glob(os.path.join(SDK, pattern)), key=lambda p: [int(x) if x.isdigit() else x for x in re.split(r"(\d+)", p)])
    if not found:
        print(f"missing tool: {SDK}/{pattern}")
        sys.exit(2)
    return found[-1]


def descriptor(name):
    return "L" + name.replace(".", "/") + ";"


def readable(desc):
    return desc[1:-1].replace("/", ".") if desc.startswith("L") and desc.endswith(";") else desc


class DexClass:
    def __init__(self, desc):
        self.desc = desc
        self.superclass = None
        self.interfaces = []
        self.direct = []  # (name, type)

    def has_default_constructor(self):
        return ("<init>", "()V") in self.direct


def parse_dex(apk):
    out = subprocess.run([tool("build-tools/*/dexdump"), apk], capture_output=True, text=True, check=True).stdout
    classes = {}
    cur = None
    section = None
    pending_name = None
    for line in out.splitlines():
        m = re.match(r"\s+Class descriptor\s+: '(.*)'", line)
        if m:
            cur = DexClass(m.group(1)); classes[cur.desc] = cur; section = None; continue
        if cur is None:
            continue
        m = re.match(r"\s+Superclass\s+: '(.*)'", line)
        if m:
            cur.superclass = m.group(1); continue
        m = re.match(r"  (Interfaces|Static fields|Instance fields|Direct methods|Virtual methods)\s+-", line)
        if m:
            section = m.group(1); continue
        if section == "Interfaces":
            m = re.match(r"\s+#\d+\s+: '(.*)'", line)
            if m:
                cur.interfaces.append(m.group(1))
        elif section == "Direct methods":
            m = re.match(r"\s+name\s+: '(.*)'", line)
            if m:
                pending_name = m.group(1); continue
            m = re.match(r"\s+type\s+: '(.*)'", line)
            if m and pending_name is not None:
                cur.direct.append((pending_name, m.group(1))); pending_name = None
    return classes


def obfuscated_names(mapping, wanted):
    """The name each library class has in the built app, from R8's mapping.txt ('original -> obfuscated:')."""
    names = {}
    pattern = re.compile(r"^(" + "|".join(re.escape(w) for w in wanted) + r") -> (\S+):$")
    with open(mapping, encoding="utf-8", errors="replace") as f:
        for line in f:
            if line[:1] in (" ", "#"):
                continue
            m = pattern.match(line.rstrip("\n"))
            if m:
                names[m.group(1)] = m.group(2)
    return names


def manifest_components(apk):
    xml = subprocess.run([tool("cmdline-tools/*/bin/apkanalyzer"), "manifest", "print", apk], capture_output=True, text=True, check=True).stdout
    root = ET.fromstring(xml)
    package = root.get("package", "")
    names = []
    app = root.find("application")
    if app is not None:
        if app.get(ANDROID_NS + "name"):
            names.append(app.get(ANDROID_NS + "name"))
        for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            names += [e.get(ANDROID_NS + "name") for e in app.findall(tag) if e.get(ANDROID_NS + "name")]
    full = []
    for n in names:
        full.append(package + n if n.startswith(".") else (n if "." in n else package + "." + n))
    return sorted(set(full))


def main():
    if len(sys.argv) not in (2, 3) or not os.path.isfile(sys.argv[1]):
        print(__doc__)
        return 2
    apk = sys.argv[1]
    mapping = sys.argv[2] if len(sys.argv) == 3 else DEFAULT_MAPPING
    if not os.path.isfile(mapping):
        print(f"no mapping file at {mapping}: build the release APK first (./gradlew :androidApp:assembleRelease) or pass the mapping.txt of this APK")
        return 2
    classes = parse_dex(apk)
    failures = []

    obf = obfuscated_names(mapping, [ACTION_CALLBACK])
    callback_desc = descriptor(obf.get(ACTION_CALLBACK, ACTION_CALLBACK))  # a library class R8 left alone is not in mapping.txt and keeps its own name
    if callback_desc not in classes:
        failures.append(f"{ACTION_CALLBACK} (as {readable(callback_desc)}) is not in the APK: the widgets are not in this build, or the mapping belongs to another build")
    callbacks = [c for c in classes.values() if callback_desc in c.interfaces]
    # GlanceAppWidget itself may be merged into another class by R8 (its entry is then missing from mapping.txt), so the widgets are found by their own names and by what they extend
    widgets = [classes[descriptor(n)] for n in EXPECTED_WIDGETS if descriptor(n) in classes]
    base = {c.superclass for c in widgets}
    widgets += [c for c in classes.values() if c.superclass in base and c.desc.startswith("Lapp/expenses/") and c not in widgets]
    print(f"  {len(callbacks)} ActionCallback classes, {len(widgets)} widget classes, {len(classes)} classes in the APK")

    for c in callbacks:
        if not c.has_default_constructor():
            failures.append(f"{readable(c.desc)} implements ActionCallback but has no no-argument constructor: Glance creates it by name on every tap, which would fail with NoSuchMethodException")
    for name in EXPECTED_CALLBACKS:
        if descriptor(name) not in {c.desc for c in callbacks}:
            failures.append(f"{name} is not found as an ActionCallback in the APK (renamed, removed, or the check cannot see it)")

    for name in EXPECTED_WIDGETS:
        c = classes.get(descriptor(name))
        if c is None:
            failures.append(f"{name} is not a class of its own in the APK (merged into another class by R8, or renamed? Glance tells widgets apart by class name)")
        elif not c.has_default_constructor():
            failures.append(f"{name} has no no-argument constructor: Glance creates widgets by name")
    if len(base) > 1:
        failures.append(f"the widget classes do not share one superclass: {sorted(base)}")
    for c in widgets:
        if not c.has_default_constructor():
            failures.append(f"{readable(c.desc)} extends the widget base class but has no no-argument constructor")

    for name in EXPECTED_WORKERS:
        c = classes.get(descriptor(name))
        if c is None:
            failures.append(f"{name} is not in the APK (removed or renamed by R8)")
        elif not any(n == "<init>" and t.startswith("(Landroid/content/Context;") and t.endswith(")V") and t.count(";") == 2 for n, t in c.direct):
            failures.append(f"{name} has no (Context, WorkerParameters) constructor: WorkManager creates it by name")

    for name in manifest_components(apk):
        c = classes.get(descriptor(name))
        if c is None:
            failures.append(f"the manifest declares {name} but the APK has no such class")
        elif not c.has_default_constructor():
            failures.append(f"the manifest declares {name} but it has no no-argument constructor: Android creates it by name")

    if failures:
        print("FAIL: R8 broke classes that are created by name:")
        for f in failures:
            print("  - " + f)
        print("Fix: a keep rule in androidApp/proguard-rules.pro (see the widget rules there).")
        return 1
    print("PASS: every class created by name (widget callbacks, widgets, manifest components) survived R8 with its constructor")
    return 0


if __name__ == "__main__":
    sys.exit(main())
