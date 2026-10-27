# Release checklist

Do these in order. Nothing here replaces the real-device list in `docs/REAL_DEVICE_CHECKLIST.md`.

## 1. Build the release APK
`./gradlew :androidApp:assembleRelease` makes `androidApp/build/outputs/apk/release/androidApp-release.apk`. It is signed with the release key when the four
`EXPENSES_STORE_FILE`, `EXPENSES_STORE_PASSWORD`, `EXPENSES_KEY_ALIAS`, `EXPENSES_KEY_PASSWORD` properties are in `~/.gradle/gradle.properties` (see
`androidApp/build.gradle.kts`); without them the APK is unsigned and `tools/release_smoke.sh` installs a debug-signed copy instead (it says so).

## 2. Run the release smoke test
`tools/release_smoke.sh` (add `--no-build` to reuse the APK just built). It installs the RELEASE build, which is minified by R8, on the emulator and drives it with
adb: first launch and skip the tour, add an expense of 123, see it in the list, find it in Search, then read the invented receipt `tools/fixtures/bill_fixture.png`
with Add, Read items from a photo, Choose a photo, and check the dialog lists its three items and the total. It exits non-zero and names the failing step.

The receipt is invented (a made-up shop, three items, subtotal, GST, total). `swift tools/fixtures/make_bill_fixture.swift tools/fixtures/bill_fixture.png` draws it with CoreGraphics on
a Mac; the PNG is committed, so a run needs no drawing tool. Checked when it was written: with the three ML Kit keep rules in `androidApp/proguard-rules.pro` temporarily removed, the
smoke test fails at the last step with `Attempt to read from field ... on a null object reference`; with the rules it passes.

Why this exists: R8 once broke ML Kit text recognition only in the release build (`Attempt to read from field ... on a null object reference`) while every debug and
instrumented test passed. Only a run of the release build can see that kind of bug. Run it after ANY change to `androidApp/proguard-rules.pro`, to a library, or to the
Android Gradle plugin or Kotlin version. It takes the shared emulator lock (`/tmp/emu.lock`), so it waits for other users of the emulator; the emulator is AVD `API26`
(start it with `emulator -avd API26 -no-snapshot -no-audio -no-boot-anim`, then `adb shell pm disable-user --user 0 com.google.android.googlequicksearchbox`, whose "has
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
