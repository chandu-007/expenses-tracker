# Windows helper runner (CHANDU-PC)

A second build/test machine: the owner's Windows 11 PC (Dell OptiPlex 3060, i5-8500T 6 cores, 8 GB RAM, Intel UHD 630). It is **the owner's personal machine in daily use**: never touch the personal folders on D: (outside `D:\dev`), OneDrive or Google Drive, never stop Docker/WSL or other processes, never install or enable anything that needs a reboot. Everything we put there lives in `D:\dev` (C: has only ~20 GB free: keep it that way).

## Reach it

`ssh -o BatchMode=yes -o ConnectTimeout=15 chandu@100.106.212.44 '<cmd>'` (key login over Tailscale; the default shell is cmd.exe, use `powershell -NoProfile -Command ...` for PowerShell). Processes started inside an ssh session can die with it, so long jobs are started detached (see below). Never poll in a tight loop; use `sleep` of 1-5 minutes between log reads.

## Paths and environment (user-level, already set)

| What | Where |
|---|---|
| JDK | `D:\dev\jdk17` (Temurin 17.0.21), `JAVA_HOME` |
| Android SDK | `D:\dev\Android\Sdk` (`ANDROID_HOME`, `ANDROID_SDK_ROOT`): cmdline-tools, platform-tools 37.0.1, platforms android-36 and android-37.0, build-tools 36.1.0, emulator 37.2.13, system image `android-34;google_apis;x86_64` |
| Gradle home | `D:\dev\gradle` (`GRADLE_USER_HOME`); user `gradle.properties` there: `org.gradle.jvmargs=-Xmx3g`, `org.gradle.daemon=true`, `org.gradle.workers.max=4`, longer http timeouts |
| Repo | `D:\dev\expenses_tracker` (clone of `git@github.com:chandu-007/expenses-tracker.git`; the PC's own GitHub key works, no secrets copied) |
| Logs | `D:\dev\logs\*.log` |
| Run script | `D:\dev\run.bat <git-ref> <gradle args...>` (below) |
| AVD | `D:\dev\avd` (`ANDROID_AVD_HOME`, set per run, not global), AVD `bench`: 2 GB RAM, 4 cores, `swiftshader_indirect`, 4 GB data |

No signing keys, no `~/.gradle/gradle.properties` from the Mac and no Apple files are on the PC. Debug builds need no signing properties (the release `signingConfig` only exists if `EXPENSES_STORE_FILE` is set). **Release/Play builds must not be made here.**

iOS: `shared/build.gradle.kts` only adds the iOS targets when `-PenableIos=true` is passed (default `enableIos=false` in `gradle.properties`), so a plain build on Windows never configures them. Never pass it here.

## Run something (detached, with a log)

From the Mac, after the commit is pushed to GitHub:

```
tools/win_run.sh <branch|tag|full-sha> :core:domain:jvmTest :core:parsers:jvmTest :core:storage:jvmTest
tools/win_log.sh run-YYYYMMDD-HHMMSS [lines]     # tail; a finished run ends with "EXIT <code>"
```

`win_run.sh` starts `D:\dev\run.bat` through WMI (`Win32_Process.Create`, survives the ssh session), which does `git fetch origin <ref>`, `git checkout --detach -f FETCH_HEAD`, then `gradlew.bat --console=plain <args>` with output in `D:\dev\logs\run-<time>.log`. Gradle args must not contain spaces or cmd metacharacters (`& | < > ^ %`). Pass `--no-daemon` when RAM is tight (the user `gradle.properties` enables the daemon). To sync a branch by hand: `ssh ... 'git -C D:\dev\expenses_tracker fetch origin && git -C D:\dev\expenses_tracker checkout --detach origin/<branch>'`.

`D:\dev\run.bat` (recreate it if lost):

```
@echo off
set JAVA_HOME=D:\dev\jdk17
set ANDROID_HOME=D:\dev\Android\Sdk
set ANDROID_SDK_ROOT=D:\dev\Android\Sdk
set GRADLE_USER_HOME=D:\dev\gradle
cd /d D:\dev\expenses_tracker || exit /b 2
for /f "tokens=1,* delims= " %%a in ("%*") do (set REF=%%a& set GARGS=%%b)
echo START %date% %time% ref=%REF% args=%GARGS%
git fetch origin %REF% || (echo FETCH FAILED & exit /b 3)
git checkout --detach -f FETCH_HEAD || exit /b 4
git log --oneline -1
call gradlew.bat --console=plain %GARGS%
echo EXIT %errorlevel% END %date% %time%
```

Commands that work: the three host suites above; `:androidApp:assembleDebug`; `:androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.expenses.MotionUiTest` with the emulator running. Test counts: read the XML in `core\<module>\build\test-results\jvmTest\*.xml` (sum `tests`/`failures`/`errors` of each `<testsuite>`).

## Emulator (only when really needed)

The accel check passes: `emulator.exe -accel-check` says "WHPX (10.0.26300) is installed and usable" (Windows Hypervisor Platform is already on, no reboot). Start it detached in a scheduled task or via WMI, `cmd /c`:

```
set ANDROID_HOME=D:\dev\Android\Sdk & set ANDROID_AVD_HOME=D:\dev\avd
D:\dev\Android\Sdk\emulator\emulator.exe -avd bench -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect
```

Poll `D:\dev\Android\Sdk\platform-tools\adb.exe shell getprop sys.boot_completed` until `1`. Stop with `adb emu kill`, then `adb kill-server`. The emulator takes about 2.5 GB RAM (it raises the 2048 MB to 2560 MB itself): do not run it together with a Gradle build unless the owner is not using the PC; run the build first, start the emulator when Gradle is done, or accept swapping.

## Measured (origin/main fe111f6, 2026-11-15; machine shared with the owner, so timings are NOT comparable with the Mac)

| Job | Result | Time |
|---|---|---|
| First `:core:domain:jvmTest :core:parsers:jvmTest :core:storage:jvmTest`, `--no-daemon`, nothing cached, incl. Gradle download | domain 816, parsers 221, storage 599 tests, 0 failures, 0 errors, 0 skipped (the Mac reference 812/221/597 was 0.13.0+S0; origin/main has 18 newer commits) | 17 m 56 s |
| `:androidApp:assembleDebug`, `--no-daemon`, cold | success, debug APK, no signing keys needed | 9 m 59 s |
| Emulator boot, API 34 x86_64 google_apis, headless, swiftshader | `sys.boot_completed` after about 2 min (emulator reports 172 s) | ~2-3 min |
| `:androidApp:connectedDebugAndroidTest` class `app.expenses.MotionUiTest` (12 tests) | 12/12 pass | 14 m 6 s total (about 5 min compile and package, about 9 min for 12 tests) |

Sizes on D: JDK 0.3 GB, Android SDK about 5.9 GB (system image 4.2 GB, of which the AVD adds 3.8 GB), Gradle home 1.9 GB, repo with build output 0.5 GB. Free RAM with nothing of ours running is about 4 GB; during a Gradle build about 0.5-2 GB; with Gradle plus the emulator it dropped to 150-400 MB (very tight, the owner will feel it). C: free 19.6 GB, D: free about 300 GB.

## Limits

- No iOS (no Xcode); never pass `-PenableIos=true`.
- Timings are not comparable with the Mac; the PC is shared with the owner's work. Prefer running when the owner is away; do not run more than one Gradle job at once.
- 8 GB RAM: Gradle heap is 3 GB; emulator 2.5 GB; do both only for short tests. Do not use `-Xmx` above 3g.
- Downloads are slow (about 2-3 MB/s); the Gradle wrapper download times out at 10 s on this PC, so the wrapper zip for 9.7.0 was placed by hand in `D:\dev\gradle\wrapper\dists\gradle-9.7.0-bin\<hash>\` (`curl.exe -L -C -` then it is used). A new Gradle version in `gradle-wrapper.properties` needs the same trick.
- Release signing, Play bundles and anything with secrets stay on the Mac.

## Cleanup

- Between runs nothing needs to be cleaned. To free disk: `rmdir /s /q D:\dev\expenses_tracker\build` and the module `build` dirs, `D:\dev\gradle\caches` (about 1.5 GB, redownloaded next time), `D:\dev\avd\bench.avd` (3.8 GB) or `sdkmanager --uninstall "system-images;android-34;google_apis;x86_64"`.
- Stop leftovers: `taskkill /f /im emulator.exe`, `adb kill-server`, `D:\dev\gradle` daemons stop themselves after 3 h idle or `gradlew.bat --stop` in the repo dir.
- Remove everything of ours: delete `D:\dev`, then `reg delete HKCU\Environment /v JAVA_HOME /f` (same for `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME`). Small leftovers on C: `C:\Users\CHANDU\.android` (adb key, emulator state) and `%TEMP%\AndroidEmulator`.
