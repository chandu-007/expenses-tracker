#!/usr/bin/env python3
"""
Makes the "how to use the app" video: the real app, used part by part, with a narrator.

  python3 tools/video/make_video.py [output.mp4]

What it does, in order:
  1. speaks each line of the script below with macOS `say` and measures how long it lasts,
  2. copies tools/video/DemoVideoTest.kt.txt into the instrumentation tests and runs it once on the connected emulator: for each part of the app it records the
     screen and really uses that part (typing, tapping, swiping) with made-up data, waiting as long as each line lasts and logging when each line began,
  3. removes the test again, lays each narration clip over its recording at the moment it began, and joins the parts behind a title card.

Needs: an emulator running (Android 8 or later), ffmpeg, macOS `say`. Everything on screen is made-up data. Takes about 20 minutes.
The test method names in DemoVideoTest.kt.txt are the part names below, and line i of a part is the test's `line(i)`.
"""
import os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
WORK = "/tmp/video"
VOICE = os.environ.get("VOICE", "Isha (Premium)")
DEVICE_DIR = "/sdcard/Download"
TEST_DST = os.path.join(ROOT, "androidApp/src/androidTest/kotlin/app/expenses/DemoVideoTest.kt")
OUT = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.expanduser("~/Desktop/Expenses-how-to-use.mp4")
AUDIO_LEAD = 0.5  # the recording starts a little after the moment the test notes, so the audio is moved earlier by this much

# What the narrator says as the app is taken to each part (the part's own lines follow once it is open).
TITLES = {'add': 'Adding expenses.', 'edit': 'Changing and deleting.', 'search': 'Search.', 'reports': 'Reports.', 'calendar': 'The calendar.', 'capture': 'Capture from texts.', 'inbox': 'To review.', 'bills': 'Bills.', 'accounts': 'Accounts and cards.', 'categories': 'Categories.', 'budget': 'Budget.', 'recurring': 'Repeating expenses.', 'loans': 'Lent and borrowed.', 'notifications': 'Reminders.', 'privacy': 'Privacy.', 'data': 'Your data.', 'look': 'Appearance.'}

SCRIPT = [
    ("intro", [
        "Welcome. This video shows the app in real use, one part at a time. Everything you see stays on the phone, and the data here is made up.",
        "This is the home screen: this month's spending, your accounts, and your latest entries.",
    ]),
    ("add", [
        "To add an expense, tap Add, type the amount, pick a category, and write what it was for. Then save.",
        "It appears at once, at the top of your list.",
        "Income works the same way. Choose Income, type the amount, and save.",
        "And when you move money between your own accounts, choose Transfer. It is never counted as spending.",
    ]),
    ("edit", [
        "To change an entry, tap it, edit the amount, and save.",
        "To delete one, swipe it away. If that was a mistake, tap Undo, and it comes straight back.",
    ]),
    ("search", [
        "Search finds any entry by words in the note or the merchant. Results appear as you type.",
        "Narrow it down by category, by amount, or by dates.",
    ]),
    ("reports", [
        "Reports shows where your money goes, with a chart, by category.",
        "Tap a category to see the entries behind it.",
        "You can also group by merchant, and switch between a month, a year, or your own dates.",
    ]),
    ("calendar", [
        "The calendar shows each day's spending at a glance.",
        "Tap a day to see exactly what you spent.",
    ]),
    ("capture", [
        "Capture from texts turns your bank messages into entries, so you do not have to type them.",
        "You turn it on here, and Android asks for your permission to read texts. Only banks, cards, and a few billers are read, and nothing leaves the phone.",
    ]),
    ("inbox", [
        "When a bank text arrives, it waits in To review, and a banner on the home screen tells you.",
        "Open it, and tap Add to accept it. You can change the account and category first. Nothing is added until you say so.",
    ]),
    ("bills", [
        "When a text says a bill is due, such as a credit card statement, the app finds it and tells you.",
        "Add it to your bills, to be reminded before the due date.",
        "When you pay, tap Mark paid. For a card bill, the payment is recorded as a transfer to the card, so it is not counted twice.",
    ]),
    ("accounts", [
        "Accounts holds your bank accounts, cash, wallets, and cards, each with its own balance.",
        "Add a new account here. For a credit card, set the statement and due days, and the app shows what is left to pay on the latest statement.",
    ]),
    ("categories", [
        "Categories group your spending. Starter categories are ready, and you can add your own.",
        "Type a name, confirm, and it is ready to use.",
    ]),
    ("budget", [
        "Set a monthly budget from the home screen. Type a limit, and save.",
        "Now the home screen shows what is left, and what you can spend each day.",
    ]),
    ("recurring", [
        "Repeating expenses are for rent, subscriptions, and E M Is. Give it a name, an amount, and a category.",
        "When it falls due, it shows up, and you add it with one tap.",
    ]),
    ("loans", [
        "To track money you lend, choose Lent or borrowed when adding. Pick a person, or add a new one.",
        "Then, under Lent and borrowed, you can see who owes you, and record a repayment when it comes back.",
    ]),
    ("notifications", [
        "Reminders for bills, budgets, and a weekly summary can be switched on or off here.",
        "Quiet hours keep them silent at night.",
    ]),
    ("privacy", [
        "Hide amounts masks every figure on screen, for when others can see your phone. Tap it again to show them.",
        "App lock asks for your fingerprint or phone lock when the app opens.",
        "And the privacy page lists exactly what the app can and cannot see. It has no internet permission at all.",
    ]),
    ("data", [
        "Your data is yours. You can export every entry as Excel or C S V, free, any time.",
        "Make a backup now and then, and lock it with a passphrase. Uninstalling the app deletes everything on the phone, so a backup matters.",
        "You can also bring in a bank statement, to add past entries.",
    ]),
    ("look", [
        "Appearance lets you choose light or dark.",
        "And an accent colour. Make the app your own.",
    ]),
    ("outro", [
        "That is every part of the app. Add your first expense, and you are on your way.",
    ]),
]


def run(cmd, **kw):
    return subprocess.run(cmd, shell=isinstance(cmd, str), check=kw.pop("check", True), capture_output=kw.pop("capture", True), text=True, **kw)


def speak():
    os.makedirs(WORK, exist_ok=True)
    lengths = {}
    clips = []
    for scene, lines in SCRIPT:
        if scene in TITLES:
            clips.append((f"{scene}_t", TITLES[scene]))
        clips += [(f"{scene}_{i}", text) for i, text in enumerate(lines)]
    for key, text in clips:
        f = f"{WORK}/{key}.aiff"
        run(["say", "-v", VOICE, "-o", f, text])
        lengths[key] = float(run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", f]).stdout.strip())
    with open(f"{WORK}/narr.txt", "w") as fh:
        fh.write("".join(f"{k} {d:.3f}\n" for k, d in lengths.items()))
    run([ADB, "push", f"{WORK}/narr.txt", f"{DEVICE_DIR}/narr.txt"])
    return lengths


def failed_scenes():
    import glob
    out = []
    for f in glob.glob(os.path.join(ROOT, "androidApp/build/outputs/androidTest-results/connected/**/*.xml"), recursive=True):
        out += re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure', open(f).read())
    return out


def gradle(tests):
    cls = "app.expenses.DemoVideoTest" if tests is None else ",".join(f"app.expenses.DemoVideoTest#{t}" for t in tests)
    cmd = ["./gradlew", ":androidApp:connectedDebugAndroidTest", f"-Pandroid.testInstrumentationRunnerArguments.class={cls}"]
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    open(f"{WORK}/gradle.log", "a").write(r.stdout + r.stderr)
    run([ADB, "shell", "pkill -2 screenrecord; sleep 3"], check=False)
    return r.returncode


def record_all():
    run([ADB, "logcat", "-c"], check=False)
    open(f"{WORK}/gradle.log", "w").close()
    rc = gradle(None)
    # A part can miss a wait on a busy machine. Only the parts that failed are recorded again (twice at most), and it is said so.
    for attempt in (1, 2):
        if rc == 0:
            break
        bad = failed_scenes()
        if not bad:
            raise SystemExit(f"recording failed with no failed part; see {WORK}/gradle.log")
        print(f"recording again, because they failed (attempt {attempt}): {', '.join(bad)}", flush=True)
        rc = gradle(bad)
    if rc != 0:
        raise SystemExit(f"recording failed; see {WORK}/gradle.log")
    return collect_marks()


def collect_marks():
    """When each line began in each part, from the log, and the recordings fetched from the device."""
    log = run([ADB, "logcat", "-d", "-s", "DEMOMARK:W"]).stdout
    marks = {}
    for m in re.finditer(r"DEMOMARK: (\S+) (\S+) (\d+)", log):
        scene, key, ms = m.group(1), m.group(2), int(m.group(3))
        if key == "REC_START":
            marks[scene] = {}  # an older run's lines may still be in the log: keep only the latest run of each part
        marks.setdefault(scene, {})[key] = ms
    for scene, _ in SCRIPT:
        assert scene in marks and "REC_START" in marks[scene] and "END" in marks[scene], f"no timeline for {scene}"
        run([ADB, "pull", f"{DEVICE_DIR}/rec_{scene}.mp4", f"{WORK}/rec_{scene}.mp4"])
    return marks


def assemble(scene, lines, marks):
    m = marks[scene]
    t0, end = m["REC_START"], (m["END"] - m["REC_START"]) / 1000.0
    keys = ([f"{scene}_t"] if scene in TITLES else []) + [f"{scene}_{i}" for i in range(len(lines))]
    clips = [(k, max(0.0, (m[k] - t0) / 1000.0 - AUDIO_LEAD)) for k in keys]
    inputs = ["-i", f"{WORK}/rec_{scene}.mp4"] + sum((["-i", f"{WORK}/{k}.aiff"] for k, _ in clips), [])
    filt = ";".join(f"[{i + 1}:a]adelay={int(s * 1000)}|{int(s * 1000)}[a{i}]" for i, (_, s) in enumerate(clips))
    filt += ";" + "".join(f"[a{i}]" for i in range(len(clips))) + f"amix=inputs={len(clips)}:normalize=0:duration=longest[a]"
    out = f"{WORK}/part_{scene}.mp4"
    run(["ffmpeg", "-y", *inputs, "-filter_complex", filt, "-map", "0:v", "-map", "[a]", "-t", f"{end + 1.0:.2f}", "-r", "30",
         "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "128k", out])
    return out


def title_card(text, secs, name):
    out = f"{WORK}/{name}.mp4"
    draw = f"drawtext=fontfile=/System/Library/Fonts/Helvetica.ttc:text='{text}':fontcolor=white:fontsize=34:x=(w-text_w)/2:y=(h-text_h)/2"
    run(["ffmpeg", "-y", "-f", "lavfi", "-i", f"color=c=0x4F378B:s=540x1170:d={secs}:r=30", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo", "-t", str(secs),
         "-vf", draw, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", out])
    return out


def main():
    if os.environ.get("ASSEMBLE_ONLY"):
        marks = collect_marks()  # the parts are already recorded on the device and their timeline is still in the log
    else:
        speak()
        run(["cp", os.path.join(ROOT, "tools/video/DemoVideoTest.kt.txt"), TEST_DST])
        try:
            print("recording every part ...", flush=True)
            marks = record_all()
        finally:
            if os.path.exists(TEST_DST):
                os.remove(TEST_DST)
    pieces = [title_card("How to use the app", 2.5, "title")] + [assemble(s, l, marks) for s, l in SCRIPT] + [title_card("Everything stays on your phone", 2.5, "end")]
    with open(f"{WORK}/concat.txt", "w") as fh:
        fh.write("".join(f"file '{p}'\n" for p in pieces))
    run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", f"{WORK}/concat.txt", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-c:a", "aac", "-b:a", "128k", OUT])
    with open(os.path.splitext(OUT)[0] + "-script.txt", "w") as fh:
        for scene, lines in SCRIPT:
            for i, t in enumerate(lines):
                fh.write(f"[{scene}/{i}] {t}\n")
    d = run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", OUT]).stdout.strip()
    print(f"done: {OUT} ({float(d):.0f} s)")


if __name__ == "__main__":
    main()
