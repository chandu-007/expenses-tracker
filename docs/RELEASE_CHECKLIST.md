# Release checklist

Do these in order. Nothing here replaces the real-device list in `docs/REAL_DEVICE_CHECKLIST.md`.

## 1. Build the release APK
`./gradlew :androidApp:assembleRelease` makes `androidApp/build/outputs/apk/release/androidApp-release.apk`. It is signed with the release key when the four
`EXPENSES_STORE_FILE`, `EXPENSES_STORE_PASSWORD`, `EXPENSES_KEY_ALIAS`, `EXPENSES_KEY_PASSWORD` properties are in `~/.gradle/gradle.properties` (see
`androidApp/build.gradle.kts`); without them the APK is unsigned and `tools/release_smoke.sh` installs a debug-signed copy instead (it says so).

## 2. Run the release smoke test
`tools/release_smoke.sh` (add `--no-build` to reuse the APK just built). First it runs `tools/check_release_keeps.py` on the APK (see below), then it installs the RELEASE build, which is
minified by R8, on the emulator and drives it with adb: first launch and skip the tour, add an expense of 123, see it in the list, find it in Search, then read the invented receipt
`tools/fixtures/bill_fixture.png` with Add, Read items from a photo, Choose a photo, and check the dialog lists its three items and the total. On Android 12 and later it then does the
**widget steps**: it saves a quick entry (Chai, 20), places the "Spending: month and quick add (4 x 2)" widget through the launcher's own widget picker (long press, Widgets, the app's entry,
drag), taps the eye (the total ₹143.00 appears, the eye offers to hide it, a second tap masks it again), taps the Chai chip ("Added Chai" appears on the widget and Search shows both entries),
repeats the eye and the chip from a process that `am force-stop` had killed (the log's `process age` shows the tap started the process), turns Hide amounts on (the widget says
"Amounts hidden", has no eye, a chip still adds and its confirmation has no amount) and off again, and finally requires that the device log has no `GlanceAppWidget` error. It exits non-zero
and names the failing step (with the matching log lines and a screenshot at `/tmp/release_smoke_failure.png`).
Known weakness (0.10.3): step 9, dragging the widget out of the Pixel Launcher's picker with `input draganddrop`, is flaky on the emulator (it failed in some runs because the picker stayed open); steps 10 to 14 have not had a green scripted run yet. When step 9 fails, place the widget by hand (long-press the home screen, Widgets, Expenses, drag "Spending: month and quick add") and check the eye, a chip and `adb logcat -s ExpensesWidget GlanceAppWidget:E` yourself, as `docs/PHASE2_PROGRESS.md` (Slice 96) describes. `tools/check_release_keeps.py` has no such weakness and always runs.

**The widget steps need the API34 AVD** (Android 14 with Google APIs, the Pixel Launcher); on the API26 emulator they are listed as `SKIP`, and the rest runs as before. Create it once:
`sdkmanager --install "system-images;android-34;google_apis;arm64-v8a"`, `avdmanager create avd -n API34 -k "system-images;android-34;google_apis;arm64-v8a" -d pixel_6`, then in
`~/.android/avd/API34.avd/config.ini` set `hw.lcd.width=1080`, `hw.lcd.height=2340`, `hw.lcd.density=440` (393 dp wide like the API26 one) and `disk.dataPartition.size=2G`. Start it with
`emulator -avd API34 -no-snapshot -no-audio -no-boot-anim -port 5556` (a second emulator can run beside API26 on port 5554). With more than one emulator running the script uses the one with the
newer Android, or the one in `ANDROID_SERIAL`; `SMOKE_LOCK_HELD=1` says the caller already holds `/tmp/emu.lock`, `SMOKE_WIDGETS=0` leaves the widget steps out.

**`tools/check_release_keeps.py`** (also run by the smoke script, and by hand after anything that can change what R8 keeps): reads the built APK with `dexdump` and `apkanalyzer` and fails if a class
that Glance or Android creates BY NAME lost its no-argument constructor or was merged into another class: every Glance `ActionCallback` (the widget's eye, chips and Undo), the two widget classes
(`QuickAddWidget`, `TodayWidget`: Glance tells widgets apart by class name), and every component in the merged manifest. Why: in 0.10.0 to 0.10.2 R8 deleted the constructors of `ToggleTotalAction`,
`QuickAddAction` and `UndoAction` (nothing in the app calls them), so on every phone the eye and the chips did nothing, silently (`adb logcat -s GlanceAppWidget:E` showed
`NoSuchMethodException: ...ToggleTotalAction.<init> []`), while the debug build and every instrumented test passed. The rules that fix it are at the end of `androidApp/proguard-rules.pro`. Checked when
it was written: with those two rules removed, the check fails and names the three callbacks and both widgets; with them it passes. `adb logcat -s ExpensesWidget` shows what every widget tap did
(which tap, how old the process was, how long it took, never an amount).

The receipt is invented (a made-up shop, three items, subtotal, GST, total). `swift tools/fixtures/make_bill_fixture.swift tools/fixtures/bill_fixture.png` draws it with CoreGraphics on
a Mac; the PNG is committed, so a run needs no drawing tool. Checked when it was written: with the three ML Kit keep rules in `androidApp/proguard-rules.pro` temporarily removed, the
smoke test fails at the last step with `Attempt to read from field ... on a null object reference`; with the rules it passes.

Why this exists: R8 once broke ML Kit text recognition only in the release build (`Attempt to read from field ... on a null object reference`), and then the widget callbacks (above), while every debug and
instrumented test passed. Only a run of the release build can see that kind of bug. Run it after ANY change to `androidApp/proguard-rules.pro`, to a library, or to the
Android Gradle plugin or Kotlin version. It takes the shared emulator lock (`/tmp/emu.lock`), so it waits for other users of the emulator; the AVDs are `API26` and `API34`
(start with `emulator -avd API26 -no-snapshot -no-audio -no-boot-anim`, then `adb shell pm disable-user --user 0 com.google.android.googlequicksearchbox`, whose "has
stopped" popup otherwise closes dialogs at random).

## 3. Run the full suite
`tools/verify.sh`: host tests, the release build, every device test, and the timing gate on a freshly started emulator. A red result is a red release. Run it on the
final commit, after the version bump, not before.

## 4. Version
`versionCode` in `androidApp/build.gradle.kts` is the NEXT UNUSED code: the number in the file is the one this release uploads with. It must go up by exactly one per
release (Play refuses an upload whose code it has already seen). After uploading, raise it by one in the same commit that starts the next release's work, so that the number
in the file is always the next unused one. Change `versionName` to match what the release notes say. Never upload two different files with the same `versionCode`.

## 5. Upload only the SMS Play bundle
`./gradlew :androidApp:bundlePlaySms` makes `androidApp/build/outputs/bundle/playSms/androidApp-playSms.aab` (application id `io.github.chandu007.expenses`, the Play
build WITH the text permission: see `docs/PLAY_SMS_DECLARATION.md`). Upload that file and no other: not the APK, not `bundlePlay`, not the debug or benchmark builds.
Closed testing first; production only after the SMS declaration is approved.

## 6. On a phone before telling anyone
The smoke test runs on an emulator, never on hardware. On a real phone, with the build that will be uploaded (or its APK):
- [ ] Install over the previous version: your data is still there.
- [ ] Add an expense, find it in Search.
- [ ] Read items from a photo of a REAL shop bill: items and total appear (the exact thing R8 broke).
- [ ] Take a backup from More, Your data, and restore it.
- [ ] The rest of `docs/REAL_DEVICE_CHECKLIST.md` for anything this release touched.
