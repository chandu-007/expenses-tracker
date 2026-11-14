# Performance notes and measured decisions

Every row is a measurement made on the same cold-booted API 34 emulator (arm64, 1080 x 2340, 440 dpi), the `benchmark` build (release code, R8, debug-signed), with the
100,000-entry dataset of `tools/measure_perf.py` (`pm clear`, then `--ei seed_rows 100000 --ez seed_full true`), two builds installed alternately with `adb install -r` (the data is
kept), 3 rounds x 8 cold starts per build after 3 dropped starts, `force-stop` between starts. The script is a copy of the cold-start part of `tools/measure_perf.py` with the
alternation added (it was a scratch file in `/tmp/perf`, not committed). **The host was never quiet** (1-minute load average 12 to 18 during the runs, other jobs on the same Mac), so
the absolute numbers are higher than the ones in `docs/PHASE1_PROGRESS.md` (Slice 33, 155 ms cold start on a quiet host) and differences below about 20 ms are noise. An emulator is a best
case for storage and CPU and a worst case for drawing (its GPU is software): do NOT read any of this as a phone's numbers.

"Base" is the head of 0.10.6 (commit f507619, code 39); "final" is this work. The Play build, the version and the artifacts are untouched.

## Decisions (slice 103)

| Item | Before | After | Verdict |
|---|---|---|---|
| Cold start, `am start -W` TotalTime (all the work of slice 103 together: window rule, config changes, fold watching, keyboard shortcuts) | base median 322 ms (range 237-421, n = 24) | final median 304 ms (233-446, n = 24); an earlier pair of 24 runs each: 282 and 282; **on the final commit (84ea840), host load 8-14: base 289 ms (202-397), final 281 ms (225-392), n = 24 each** | **kept**: equal within noise |
| Cold start, data composed (mark "ready composed", ms since process start) | base 747 | final 712 (an earlier pair: 700 and 713); final commit: base 704, final 694 | **kept**: equal within noise |
| Idle memory (PSS after 9 s, `dumpsys meminfo`, 3 alternating pairs) | base 62.8, 63.0, 63.0 MB | final 63.1, 63.4, 63.2 MB; after the fold watcher was limited to phones that have a hinge sensor: 64.5, 64.5, 64.6 against base 63.8, 64.3, 64.6 in that later session | **kept**: +0.1 to +0.3 MB (under 0.5%), inside the drift of the session itself (the base alone moved 62.8 to 64.6 MB between sessions). On the final commit, 3 alternating pairs: base 60.4, 61.2, 61.0 MB; final 61.1, 61.0, 61.0 MB: equal |
| APK size (benchmark build, universal) | 57,044,417 bytes | 57,060,849 bytes (+16 KB; the final commit's build is the same size) | **kept**: the window library was already in the APK (Compose and Glance bring `androidx.window` 1.5.0), only declared |
| Work in `Application.onCreate` (channels, two WorkManager schedules): move to the background? | marks at the start and end of `onCreate`: 52 ms and 52 ms (the same millisecond: it costs under 1 ms; the 52 ms before it are the system and the content providers) | none | **no change**: nothing to win |
| WorkManager started on first use instead of by its provider (`Configuration.Provider`, initializer removed in the manifest, the two schedules put in from a background thread) | 'onCreate' mark 55 ms, TotalTime median 282 ms (n = 24) | 'onCreate' mark 48 ms (-7), TotalTime median 296 ms (n = 24) | **reverted**: the start of the process moved by 7 ms but TotalTime did not improve (it was 14 ms worse, noise), and WorkManager is then started at an unpredictable moment (a receiver in a fresh process). Not clearly better, so not kept |
| Whole app compiled ahead of time (`cmd package compile -m speed -f`) as a test of whether a baseline profile for the app's own code could help | TotalTime median 312 ms before, 287 ms after (n = 10 each, same build) | | **not done**: on this emulator ahead-of-time compiling the whole app changes cold start by under 25 ms, so a baseline profile would be unmeasurable here. It is likely to help on a phone (the Compose libraries ship their own profile, the app's code has none): see "Suggestions" |
| Where the 700 ms to "data composed" goes | a sampling method trace of a cold start (`am start --start-profiler ... --sampling 1000`, read with a small parser) shows the main thread inside `HardwareRenderer.syncAndDrawFrame` (the emulator's software drawing) for most of it, not in the app's code | | **no change**: emulator drawing, not something to fix in the app |

## Size, what the APK is made of (benchmark build, release code)

| What | Bytes | Note |
|---|---|---|
| ML Kit text recognition, `libmlkit_google_ocr_pipeline.so`, four ABIs | 11.6 + 11.6 + 11.1 + 6.8 MB | The Play bundle serves one ABI to a phone, so a user downloads about a quarter. The universal APK (the sideloaded one) carries all four. Dropping x86 and x86_64 would break emulators and Chromebooks (the manifest says tablets and Chromebooks install): not done |
| SQLCipher `libsqlcipher.so`, four ABIs | 2.2 + 2.2 + 2.1 + 1.0 MB | needed |
| `classes.dex` | 7.9 MB (3.7 MB compressed) | R8 and resource shrinking are already on |
| PDFBox CJK character maps (`assets/com/tom_roush/fontbox/resources/cmap/Uni*`, `Adobe-*`) | about 1.5 MB (0.6 MB compressed) | would break a statement PDF in a CJK encoding: not removed, the saving is not worth a behaviour change |
| `resources.arsc` | 0.7 MB | library locales; trimming them would save under 0.3 MB |

The Play bundle of 0.10.6 (`bundlePlaySms`) is 35,182,606 bytes; the same bundle built locally from the final commit is **35,219,660 bytes (+37,054, +0.11%)** (not kept, not uploaded).

## Timing gate A/B against 06c540e (API 26, cold-booted before every run)

`PerformanceBudgetTest` + `RefreshBudgetTest` (15 tests), order base, new, new, base, base, new (debug builds of 06c540e and of the final commit, installed fresh after each cold boot with `-wipe-data` and `tools/emulator_setup.sh`). Host load average at the start of the six runs: 5.3, 5.1, 6.6, 4.2, 4.4, 4.8 (a quiet host). **All six runs passed 15 of 15.** Median of every `PERF` figure (34 of them) over the three runs of each build: the new/base ratio runs from 0.92 to 1.14; the largest are "save one transaction (p95 of 60 autocommit inserts)" 0.73 -> 0.83 ms (1.14) and "save one expense end to end (p95 of 40)" 6.68 -> 7.40 ms (1.11), both in the storage path this work did not touch, and well inside the 30% threshold. **No regression.**

## Suggestions that were NOT done (no measurement could show a gain here), ranked

1. **A baseline profile for the app's own code.** Generated and tried in Slice 111 (see "Baseline profile" below): the file is easy to make and safe to build, but the gain could not be shown on a loaded Mac with an emulator, so it is NOT shipped. The measurement on a phone, or on a quiet host, is what is missing; the steps are in `tools/baseline_profile_drive.py`.
2. **`Modifier.sharedContainer` on every Home row** (`Screens.kt:501`) is a `composed {}` modifier (it reads three composition locals and remembers a shared-content state per row). On a long list it costs allocation per row as it scrolls in. Giving the shared key only to the row that was tapped would remove it; it touches the motion code (`MotionUiTest`), so it needs a scroll measurement on a phone first. Effort small, risk medium (the grow-out-of-the-row motion).
3. **The hourly notification check and the twelve-hourly auto-export** (`NotificationWork`, `AutoExportWorker`) and five widgets at `updatePeriodMillis` 1 h: each wake opens the encrypted database. Battery cost is small; a `setRequiresBatteryNotLow` constraint would cut it on a low battery but delays bill reminders then. Not changed: it is a product decision.
4. **Drag and drop** of an entry out of the list (a share target for another app) and **an edge-to-edge detail pane** with the amount keypad beside the form on 8-inch screens: large-screen guidelines suggest both; neither is trivial.
5. ~~Two panes for Search~~: done in Slice 111.

## Baseline profile (Slice 111): made, measured, NOT shipped

What the owner asked: no new library or permission. Nothing new is needed at run time: `androidx.profileinstaller` 1.4.0 is already in the app (Compose and Glance bring it; it is why the release APK already holds a 10 KB `assets/dexopt/baseline.prof` made of the libraries' own profiles), and AGP 9.3.1 merges a file `androidApp/src/main/baseline-prof.txt` with them and has R8 rewrite its names to the shipped ones. No Gradle change is needed to ship it, and no Macrobenchmark or profile plugin was added: the profile was collected from a throwaway build type in a throwaway worktree (never merged) by driving the app with `adb` (`tools/baseline_profile_drive.py`, steps in its header): 6 cold starts, the first-run tour, Home scrolling, an entry, adding an expense, Search with Filters and an opened result, Reports in its modes, and More with its pages. API 34 emulator, 3,000 seeded entries, commit bda2bc2.

| What | Number |
|---|---|
| ART's own profile after the run (`primary.prof`) | 38.8 KB binary; `profman --dump-classes-and-methods`: 34,074 lines (3.8 MB), of which 5,519 are the app's own methods and classes |
| After dropping the test and seeding code and the libraries that ship their own profile (androidx compose, activity, lifecycle, collection) | 11,672 lines, 1.08 MB: the candidate `baseline-prof.txt` |
| What AGP and R8 made of it (`profgen dumpProfile` of the built APK, with the R8 mapping) | entries 9,749 -> 12,784; the app's own 464 -> 1,326 (R8 inlines and merges, so most of the 5,519 source methods have no entry of their own any more) |
| `assets/dexopt/baseline.prof` in the APK | 10,056 -> 15,589 bytes |
| Size | benchmark APK +16,384 bytes (57,175,645 -> 57,192,029); Play bundle (`bundlePlaySms`) +3,331 bytes (35,641,715 -> 35,645,046); the release APK has the same size to the byte |
| `tools/check_release_keeps.py` on the release APK with the profile | PASS (9,569 classes) |

Measured, the same release code with and without the profile, `adb install -r` of each in turn, 24 cold starts each per pass, API 34, the Mac **never quiet** (its 1-minute load was 14 to 138 during these, another job was building and testing all the time):

| Pass | What was compared | Cold start `am start -W` | Data composed | CPU time of the process 3 s after the start (user + system) |
|---|---|---|---|---|
| 1 | both left as `adb install` leaves them (compile filter `verify`: no ahead-of-time code for either, the profile is not used) | without 683 ms, with 496 ms | 1,472 / 1,060 ms | not measured |
| 2 | after the app ran once (it installs the profile itself a few seconds after starting) and `cmd package compile -f -m speed-profile` | without 456 ms, with 545 ms | 996 / 1,178 ms | not measured |
| 3 | the same | without 449, with 422 | 1,025 / 929 | 1,910 / 1,695 ms |
| 4 (with first) | the same | with 574, without 718 | 1,202 / 1,300 | 2,065 / 2,420 ms |
| 5 | the same | without 606, with 586 | 1,201 / 1,190 | 2,260 / 2,190 ms |

Pass 1 compared two builds in the SAME state and still differed by 30%, which is the size of the noise. In the four passes that did use the profile the cold start was lower in three and 20% higher in one; the CPU time was lower in all three passes that measured it (3% to 15%). Idle memory after the compile (PSS, 3 alternating pairs): without 71.6, 72.1, 72.7 MB; with 63.6 (an outlier), 73.3, 73.5 MB: about 1% higher in two of three, the compiled code being mapped from the file. The rule for this work is "keep it only if the numbers are equal or better", and these are mixed, so **nothing is shipped**; no gain is claimed.

To decide it properly, on a quiet host or a phone: build the two Play-style bundles (without and with `baseline-prof.txt` made by the steps in `tools/baseline_profile_drive.py`), install each with `bundletool` or from an internal Play track (so the install compiles with the profile as Play does), force `cmd package compile -f -m speed-profile app.expenses` on both, and take 30 cold starts each alternating (`tools/measure_perf.py` or the script in the header of this file), the CPU time as above, and the PSS; ship the file when the start is lower and the PSS is not higher. Removing it again is deleting one file.

## Two-pane Search (Slice 111): cost

Search now draws its whole page in the list pane on a two-pane window, and `SearchFields` (a new class that keeps the words, filters, ticks, panel state and scroll position outside the page) lives in the app root. The timing gate (`PerformanceBudgetTest` + `RefreshBudgetTest`, API 26, cold-booted per run, order base, new, new, base, base, new, against 648a179) is in the Slice 111 notes of `docs/PHASE2_PROGRESS.md`.

## Frame logger (UI S0, F-15): how it is used, and what was NOT measured

**Android, debug build only** (`BuildConfig.FRAME_LOG`; a constant false in release, benchmark and Play builds, so R8 removes it; `dexdump` of a release build for the reference was not done in Slice 113): `Window.addOnFrameMetricsAvailableListener`, no library, no permission. Each burst of frames (less than 250 ms between them) is one `adb logcat -s FrameLog` line, named after the screen change that started it ("Home to Add") or "scroll or animation on Search": `frames= p50= p90= p99= max= >16.7= >8.3= >100= >700=` (ms; counts of frames over 60 Hz, 120 Hz, 100 ms and frozen). `am start ... --ez frame_overlay true` shows the last line in a box. **The six interactions** (Home fling, Home to entry, Add open, tab switch, Search results scroll, Reports tab switches) on a seeded 1,500-entry ledger: `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.expenses.FrameLogDriveTest`, then `adb logcat -d -s FRAMELOG_RESULT`. 5 repetitions each, the totals as one line per interaction. Cold-boot the emulator first and compare two builds on the same boot, alternating, as above; the emulator draws in software, so only the comparison means anything.

**iPhone:** launch with the argument `-frame-log` (Xcode scheme, or `xcrun simctl launch ... -frame-log`): a `CADisplayLink` times its own callbacks, one window of 700 ms per screen change, an `NSLog` line `FrameLog: Home to Add frames=... target=8.3ms`. Compiled (see Slice 113), never run on a phone.

**Not measured in Slice 113 (the run ended on its request budget):** the before/after numbers for the six interactions, and the A/B timing runs of `RefreshBudgetTest`/`PerformanceBudgetTest`/`HomeListTest` against the base commit. The expected effects are only hypotheses: Add open is capped at 380 ms at 1.8x (it was 833 ms visible), tab changes at 250 ms. `FrameLogDriveTest` is ready to produce them.
