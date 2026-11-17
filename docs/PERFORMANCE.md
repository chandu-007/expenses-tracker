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

## UI S0 measured (Slice 113, 2026-11-14): A/B against the base and the frame-logger table

**Host, said plainly:** a 24 GB Mac with 16 to 18 GB of swap in use and a 1-minute load of 5 to 16 during every run (`/tmp/s0_ab/host.txt`: for example load 4.76 / swap used 15.9 GB at the start of run 1, load 16.4 at the start of run 2, load 11.4 at the end of run 5). It was never quiet, so the absolute gates fail on the BASE too (9 to 10 of 16 timing tests failed in every run, base and new alike) and only the comparison below means anything.

**Timing gate A/B** (`RefreshBudgetTest` + `PerformanceBudgetTest` + `HomeListTest`, debug builds, API 26 emulator cold-booted with `-wipe-data` and `tools/emulator_setup.sh` before EVERY run, order base, new, new, base, base, new; base = `7ffd8ec` (the commit just before S0), new = `9b7dc31`; median of the 3 runs of each; only the figures the failing tests print): 21 figures. Within 30%: 17. Over 30% worse for the new build: "account balances over the whole ledger (background)" 114 ms base (114, 117, 112) vs 152 ms new (121, 152, 154): **+34% and in all three new runs higher than all three base runs**, although S0 changed nothing in `core/storage` queries; "search, most common merchant" 9.4 vs 13.8 ms (base 14.6, 9.4, 7.3; new 13.8, 16.1, 9.8: overlapping, noise); "tag suggestions" 8.8 vs 12.6 ms (base 8.8, 9.4, 4.9; new 12.6, 13.6, 8.0: overlapping). One figure over 30% better ("save one transaction p95" 9.7 vs 4.5). So: **no consistent regression except the account-balances read, which is unexplained and must be re-measured on a quiet host before it is believed or dismissed**; the "save one expense end to end" p95 (36.7 vs 39.2 ms) and "save to new row and total visible at 100k" p50 (21.7 vs 23.4 ms) moved +7%. `RefreshBudgetUncategorisedTest` (a timing class) fails on both builds on the loaded host.

**Frame logger, six key interactions** (`FrameLogDriveTest`, API 34 emulator cold-booted before each run, 5 repetitions each on a seeded 1,500-entry ledger; before = `24fd987` (logger only, no S0 UI change), after = `9b7dc31`; order before, after, after, before; ms; the emulator draws in software, and the host load moved a lot between runs, so the first pair (load 9 to 16) is not comparable and the SECOND pair (runs 3 and 4, load 9 to 11) is the fair one):

| Interaction | before run 1 (p50 / p90 / p99, frames >100 ms) | after run 2 | after run 3 | before run 4 |
|---|---|---|---|---|
| Home fling | 192 / 1234 / 1868, 156 | 120 / 1147 / 2293, 111 | 17 / 218 / 434, 50 | 18 / 200 / 318, 50 |
| Home to entry | 32 / 56 / 149, 14 | 30 / 44 / 109, 5 | 17 / 17 / 350, 2 | 17 / 18 / 34, 1 |
| Add open | 34 / 52 / 119, 8 | 31 / 52 / 254, 8 | 17 / 35 / 800, 3 | 17 / 17 / 301, 1 |
| Tab switch (4 switches: Home, Reports, Home, More, Home) | 34 / 81 / 633, 42 | 18 / 52 / 196, 20 | 17 / 18 / 84, 4 | 17 / 18 / 51, 1 |
| Search results scroll | 112 / 566 / 1514, 96 | 17 / 190 / 953, 47 | 17 / 84 / 234, 16 | 17 / 68 / 151, 12 |
| Reports tab switches (in-page: By year, By period, Account, Merchant, Category) | 34 / 126 / 620, 81 | 17 / 51 / 168, 24 | 17 / 18 / 24, 2 | 17 / 18 / 23, 0 |

Reading it: in the fair pair (3 and 4) the two builds are the same within the noise of one slow frame (Home fling p90 218 vs 200; Add open p99 800 vs 301 is ONE long frame in 95 and 79; Search scroll p90 84 vs 68). **No improvement and no regression can be claimed from these numbers.** The expected benefit of the Add cap (833 ms visible to 380 ms) is a visible-duration change that these per-frame figures do not show (the windows end 500 ms after idle), and the composition hitch the audit blamed on the eager form is not touched by S0 (it is S2's). The real-phone baseline (D17) is still the owner's.

**Release sanity:** `assembleRelease` ok, `tools/check_release_keeps.py`: PASS (9,647 classes, 4 ActionCallback classes, 2 widget classes); `tools/release_smoke.sh` API 26: 7 steps passed, 7 widget steps skipped (by design on API 26); API 34: 14 of 14 steps passed (bill photo, widget placed, eye, chip, from a killed process, Hide amounts, no widget error). The frame logger is not in the release build by construction (`BuildConfig.FRAME_LOG` false); a `dexdump` for `OnFrameMetricsAvailableListener` in the release APK was not done.

## UI S0 follow-up: the +34% "account balances" and the longer worst frames were measurement noise (re-measured 2026-11-15)

**Account balances** (`PerformanceBudgetTest.accountBalancesForTheHomeStripStayUnder150msPhoneBudgetInTheBackground`, API 26 cold-booted with `-wipe-data` and `tools/emulator_setup.sh` before EVERY run, 5 alternating rounds with the order rotated each round, three builds: `7ffd8ec` base, `a192eda` (S0 with Compose Multiplatform 1.12.2), `9b7dc31` head; host `uptime` 1-minute load 2.9 to 22 at the starts of the runs, swap used 19.8 to 20.6 GB, so NOT quiet; `/tmp/sA_out/host.txt` was the log):

| Build | five runs (ms) | median |
|---|---|---|
| 7ffd8ec | 135.8, 129.0, 126.1, 112.9, 75.2 | 126.1 |
| a192eda | 145.3, 136.8, 77.5, 131.5, 97.0 | 131.5 |
| head | 135.4, 103.0, 104.2, 132.5, 126.3 | 126.3 |

The ranges overlap completely (75 to 136, 77 to 145, 103 to 135): the earlier 114 against 152 ms was three runs on a swapping host. **No regression.** The code path agrees: the figure is `db.schemaQueries.accountBalances()` run by the test on its own database; S0 changed no storage query, and nothing of Home or the chrome runs inside the measured block.

**Compose Multiplatform 1.12.2 changes nothing in the Android APK:** the debug APK of the base plus only the logger patch and the same plus only the version bump are byte-identical (md5 17adb7df7fc743ef314be18037143979 for both). The app takes its Android Compose libraries from the androidx Compose BOM, which wins over the CMP version, so the bump matters to the iPhone build only. It cannot be the cause of anything measured on Android.

**Frame logger, the six interactions, three builds** (`FrameLogDriveTest`, API 34 emulator cold-booted before EVERY run, 5 alternating rounds with rotated order; `pure` = `7ffd8ec` + only the logger patch of `24fd987` (cherry-picked: no S0 code at all), `24fd987` = logger + F-01, `head` = `9b7dc31`; before each run the script waited for the 1-minute load to drop under 9 (starts: load 4.0 to 11, swap used 12.1 to 13.4 GB); median over the 5 runs of the figure of each run; ms):

| Interaction | build | frames | p50 | p90 | p99 | frames >100 ms | frames >700 ms | p99 of each run |
|---|---|---|---|---|---|---|---|---|
| Home fling | pure | 208 | 187.6 | 1264 | 2397 | 152 | 50 | 3868, 3134, 1851, 1860, 2397 |
| | 24fd987 | 206 | 185.0 | 1335 | 2684 | 152 | 50 | 2051, 2928, 2018, 2684, 3118 |
| | head | 209 | 185.0 | 1250 | 2467 | 150 | 50 | 1951, 2205, 3234, 2467, 2647 |
| Home to entry | pure | 704 | 31.5 | 50.5 | 119 | 12 | 4 | 104, 110, 121, 177, 119 |
| | 24fd987 | 761 | 32.8 | 53.3 | 124 | 13 | 3 | 128, 104, 124, 116, 152 |
| | head | 638 | 31.5 | 51.4 | 134 | 10 | 3 | 163, 95, 103, 151, 134 |
| Add open | pure | 395 | 30.7 | 45.3 | 259 | 6 | 4 | 259, 1234, 113, 1295, 112 |
| | 24fd987 | 433 | 31.6 | 49.4 | 174 | 5 | 3 | 174, 90, 207, 1581, 106 |
| | head | 343 | 31.1 | 48.2 | 170 | 6 | 3 | 1184, 87, 1319, 170, 115 |
| Tab switch (4 switches) | pure | 604 | 33.0 | 72.3 | 607 | 41 | 4 | 654, 577, 496, 688, 607 |
| | 24fd987 | 602 | 32.4 | 77.2 | 635 | 43 | 6 | 875, 724, 635, 584, 603 |
| | head | 624 | 32.8 | 75.6 | 657 | 40 | 7 | 652, 602, 657, 707, 764 |
| Search results scroll | pure | 172 | 107.2 | 570 | 1301 | 99 | 9 | 1301, 1451, 1184, 1301, 1080 |
| | 24fd987 | 174 | 102.8 | 573 | 1219 | 97 | 8 | 1013, 1784, 1034, 1357, 1219 |
| | head | 171 | 117.1 | 593 | 1145 | 96 | 10 | 1069, 1145, 1687, 1019, 1151 |
| Reports tab switches | pure | 629 | 33.9 | 110 | 290 | 74 | 2 | 471, 211, 290, 458, 268 |
| | 24fd987 | 654 | 33.3 | 105 | 335 | 75 | 1 | 217, 419, 415, 217, 335 |
| | head | 739 | 33.2 | 93.6 | 300 | 74 | 4 | 426, 245, 293, 524, 300 |

Reading it: **the three builds are the same within the spread of one build against itself.** The Add-open p99 that looked like 301 to 800 ms is one long frame: in the pure base alone the five runs give 259, 1234, 113, 1295 and 112 ms, and in head 1184, 87, 1319, 170 and 115 ms. Home to entry (p99 17 to 350 earlier) is 119 / 124 / 134 ms, tab switch 607 / 635 / 657, Search scroll 1301 / 1219 / 1145. Those worst frames are the software-drawn emulator's, not the app's. Candidates considered and not supported by data: F-02 motion tiers, F-05 keyboard handling, F-09 `FilterChip`, the Reports skeleton, the notification code (all in `head`, none moves a figure), and CMP 1.12.2 (byte-identical APK). Two earlier attempts at this table (hosts with load 29 to 36) were discarded as unusable. The emulator image hung twice during the series (an `adb logcat -c` that never returned); the script now has time limits and retries, and the hung runs were repeated, none was dropped.

## UI S1 measured (Slice 114, 2026-11-15): tabular figures, the timing A/B and the frame logger

**The iPhone width test (the 10 minutes asked for first).** A throwaway composable on the iPhone 17 simulator (iOS 26.5) measured text through Compose's `TextMeasurer` at 32 sp (`println` to the console; removed, not committed):

| Text | no feature | `tnum` | `tnum, lnum` |
|---|---|---|---|
| 1111 | 179 px | 242 | 242 |
| 8888 | 246 px | 242 | 242 |
| 1,11,111 | 314 | 420 | 420 |
| 8,88,888 | 412 | 420 | 420 |
| ₹1,11,111.00 | 518 | 630 | 630 |
| ₹8,88,888.00 | 615 | 630 | 630 |

So the iPhone's system font draws digits of different widths unless asked, and **it honours `fontFeatureSettings = "tnum"`** (every digit 242 px, every lakh figure the same width whatever its digits). Decision D4: system digits with `tnum`; **no bundled digits font** (the owner approved one only if it needed no new Gradle artifact: Compose Resources, `components.resources`, is a new first-party artifact, and the iPhone needs `tnum` to work, which it does). On Android the same test is `UiS1TokensTest` (`1111`, `8888`, `0000` and a lakh figure measure the same in the hero, body, label, title, chart and display styles).

**A global `tnum` on Android was a real regression, found by the A/B and fixed before it was pushed as the final design.** The first S1 build asked for `tnum` on every Material text style (the plain, obvious way). Roboto's digits are tabular already, so it added nothing on Android and cost frames: alternating 3 rounds on the API 34 emulator (cold boot before every run, host 1-minute load 5 to 12 at the starts, swap 18 to 20 GB), builds `head` = the code before S1, `s1d` = S1 with the global `tnum`, `v1` = S1 with the typography left as Material's (diagnostic; made from an earlier S1 state), ms, median of the 3 runs:

| Interaction | build | frames | p50 | p90 | p99 | frames >100 ms | frames >700 ms | p99 of each run |
|---|---|---|---|---|---|---|---|---|
| Home fling | head | 224 | 34.7 | 318 | 1291 | 81 | 20 | 2072, 1291, 284 |
|  | s1d | 211 | 109.4 | 1233 | 1952 | 112 | 30 | 1918, 1952, 2334 |
|  | v1 | 223 | 33.8 | 401 | 2135 | 79 | 18 | 284, 2135, 3351 |
| Home to entry | head | 276 | 26.9 | 42 | 80 | 0 | 0 | 122, 80, 18 |
|  | s1d | 479 | 31.5 | 54 | 191 | 11 | 3 | 134, 206, 191 |
|  | v1 | 250 | 18.1 | 41 | 122 | 2 | 1 | 417, 122, 88 |
| Add open | head | 188 | 26.6 | 45 | 121 | 4 | 3 | 121, 1617, 18 |
|  | s1d | 263 | 32.7 | 51 | 1120 | 5 | 2 | 223, 1695, 1120 |
|  | v1 | 156 | 18.1 | 46 | 367 | 7 | 1 | 150, 1452, 367 |
| Add close | head | 106 | 28.0 | 55 | 1119 | 6 | 2 | 1162, 1119, 151 |
|  | s1d | 249 | 31.6 | 56 | 666 | 6 | 2 | 207, 1122, 666 |
|  | v1 | 93 | 30.1 | 56 | 1024 | 4 | 1 | 134, 1024, 1951 |
| Tab switch (4 switches) | head | 454 | 17.2 | 41 | 146 | 9 | 1 | 591, 146, 84 |
|  | s1d | 606 | 26.0 | 55 | 628 | 32 | 5 | 533, 628, 769 |
|  | v1 | 514 | 17.4 | 42 | 184 | 14 | 1 | 36, 493, 184 |
| Search results scroll | head | 206 | 17.5 | 168 | 606 | 37 | 1 | 982, 606, 117 |
|  | s1d | 184 | 51.1 | 529 | 1067 | 65 | 7 | 4239, 1067, 741 |
|  | v1 | 207 | 22.3 | 144 | 603 | 44 | 0 | 134, 1250, 603 |
| Reports tab switches | head | 711 | 17.1 | 32 | 101 | 8 | 0 | 533, 101, 19 |
|  | s1d | 702 | 18.6 | 74 | 218 | 51 | 1 | 218, 662, 164 |
|  | v1 | 687 | 17.2 | 36 | 100 | 8 | 0 | 28, 218, 100 |

`s1d` is worse than `head` and `v1` on five of the seven: Home fling p50 109 against 34.7 and 33.8, Reports tab switches 51 frames over 100 ms against 8 and 8, Search scroll p50 51 against 17.5 and 22, tab switch p50 26 against 17.2 and 17.4, Home to entry 11 frames over 100 ms against 0 and 2; `v1` is the same as `head` within the noise. The cause is the feature setting itself (every text of every list row is shaped with an OpenType feature). The keyboard test `UiS0LayoutTest.theKeyboardGoesDownWithTheAddFormNotAfterIt` moved the same way on API 34 (head 2,460 and 2,469 ms, `s1a` 3,416 and 3,465 ms; with the other build between, 1,684 / 2,069 / 3,187: that test is noisy, so it is not used as evidence). **The fix:** `Platform.proportionalDigits` is true on the iPhone only, where `appTypography(allTabular = true)` asks every style for `tnum`; on Android only `NumberStyle.Hero` and `NumberStyle.Chart` carry it (a hero figure is one text, a chart label is drawn once). The same alternating 3 rounds on the final build `s1e` against `head` (ms, median of 3):

| Interaction | build | frames | p50 | p90 | p99 | frames >100 ms | frames >700 ms | p99 of each run |
|---|---|---|---|---|---|---|---|---|
| Home fling | head | 203 | 192.3 | 1411 | 2287 | 146 | 50 | 2287, 2364, 334 |
|  | s1e | 210 | 177.0 | 933 | 1979 | 151 | 46 | 2617, 1979, 501 |
| Home to entry | head | 578 | 31.3 | 52 | 333 | 14 | 3 | 179, 333, 484 |
|  | s1e | 519 | 33.1 | 55 | 166 | 16 | 3 | 525, 166, 158 |
| Add open | head | 297 | 30.9 | 53 | 1167 | 10 | 4 | 1184, 1167, 20 |
|  | s1e | 292 | 30.1 | 51 | 1317 | 9 | 3 | 1317, 1528, 351 |
| Add close | head | 265 | 29.5 | 47 | 802 | 4 | 3 | 802, 2147, 103 |
|  | s1e | 248 | 31.7 | 52 | 267 | 8 | 3 | 267, 1323, 184 |
| Tab switch (4 switches) | head | 609 | 32.1 | 68 | 616 | 36 | 4 | 616, 716, 51 |
|  | s1e | 513 | 33.4 | 67 | 624 | 37 | 3 | 851, 624, 37 |
| Search results scroll | head | 180 | 101.3 | 594 | 1132 | 90 | 10 | 1132, 1352, 117 |
|  | s1e | 165 | 118.8 | 458 | 701 | 99 | 2 | 1638, 701, 117 |
| Reports tab switches | head | 754 | 34.1 | 79 | 330 | 57 | 2 | 386, 330, 20 |
|  | s1e | 698 | 33.7 | 85 | 491 | 62 | 3 | 569, 491, 19 |

On the final build the two are the same within the spread of one build against itself (Home fling p50 192 against 177, p90 1411 against 933; Reports tab switches 57 against 62 frames over 100 ms; tab switch p50 32.1 against 33.4, 36 against 37 frames over 100 ms; Add open 10 against 9, Add close 4 against 8: single long frames). **No regression is claimed away; none is left in these numbers, and no improvement is claimed either** (the emulator draws in software; the host load stayed 5 to 16, `/tmp/s1_ab*/host.txt`).

**Timing gate A/B** (`RefreshBudgetTest` + `PerformanceBudgetTest` + `HomeListTest`, debug builds, API 26 cold-booted with `-wipe-data` and `tools/emulator_setup.sh` before EVERY run, order head, S1, S1, head, head, S1; the S1 build here is `s1d`, before the typography was scoped: these are SQL and storage tests that never draw a Text, so the later UI change cannot move them). Host: load 4 to 23, swap 18 to 20 GB, so the absolute gates fail on the base too (8, 7 and 8 of 16 failed on the base, 8, 9 and 9 on S1; seven of them fail in every run of both builds; `savingOneTransactionStaysUnder50msPhoneBudgetAtP95` failed in 2 of 3 base runs and 3 of 3 S1 runs, and `categoryTotalsForTenYearsStayUnder1sPhoneBudget` in 2 of 3 S1 runs and none of the base runs (231.7 against 257.8 ms with a limit of 250 ms): each is a "phone budget" divided by 4 for the emulator, and none of them draws anything or runs code S1 changed):

| Metric (median of 3 runs) | before S1 (`head` = 9b7dc31 code) | S1 (`s1d`) |
|---|---|---|
| Account balances over 100,000 entries (ms) | 120.5 | 122.8 |
| Category totals, 10 years, 100k rows (ms) | 231.7 | 257.8 |
| Deep scroll, last page (ms) | 1.9 | 2.5 |
| Filtered search, no text, whole ledger (ms) | 555.5 | 550.2 |
| Rebuild after the row is shown, p50 / p95 (ms) | 94.1/217.5 | 92.5/165.1 |
| Save one expense end to end, p95 of 40 (ms) | 40.6 | 43.0 |
| Save to new row and total visible at 100k, p50 / p95 (ms) | 23.2/51.0 | 22.9/42.0 |
| Search, common word + rare short word, whole ledger (ms) | 730.5 | 690.4 |
| Search, most common merchant (ms) | 14.2 | 16.3 |
| Search, shared note text (ms) | 42.0 | 40.4 |
| Tag suggestions, 33k tagged entries (ms) | 7.8 | 10.4 |
| Whole save path, p50 / p95 (ms) | 16.7/40.6 | 20.1/43.0 |

The two builds are the same within the noise of a swapping host (category totals 231.7 against 257.8 is the largest, the rows of three runs each overlap).

## Release 0.14.0 A/B against 0.13.0 (2026-11-16): timing classes, frame logger, cold start

`base` = `b899430` (0.13.0) + the logger patch `24fd987` (cherry-picked clean, nothing else); `head` = the 0.14.0 product code (`67f6f4e`) with test-only changes on top. Debug builds, run with `adb shell am instrument`. Host: swap 14.2 to 14.8 GB used, 1-minute load 3.6 to 8.6 at each start; the emulator was cold-booted before every run.

**Timing classes** (`RefreshBudgetTest`, `PerformanceBudgetTest`, `HomeListTest`; API 26; order base, head, head, base, base, head = 3 runs each; the budget tests FAIL on both builds on this emulator, 16 run, 5 to 9 failures per run, as in earlier releases: the absolute figures are over the phone budgets). Medians of the 3 runs (min to max), ms:

| `PERF` figure | base | head | ratio |
|---|---|---|---|
| save to new row and total visible at 100k, p50 | 20.1 (16.0 to 23.9) | 22.3 (21.5 to 29.8) | 1.11 |
| rebuild after the row is shown, p50 | 84.3 (61.7 to 102.9) | 104.7 (85.4 to 111.3) | 1.24 |
| deep scroll, last page | 0.87 (0.73 to 0.91) | 2.12 (0.80 to 2.29) | 2.44 |
| slowest of 60 saves | 18.0 (13.3 to 22.7) | 30.8 (13.7 to 38.6) | 1.71 |
| save one expense end to end | 28.1 (26.9 to 36.8) | 36.8 (19.3 to 53.7) | 1.31 |
| search, most common merchant | 10.5 (6.6 to 12.1) | 13.9 (6.7 to 16.0) | 1.33 |
| search, shared note text 'upi' | 41.9 (23.6 to 46.0) | 51.7 (23.2 to 53.7) | 1.23 |
| account balances, whole ledger | 112.6 (84.6 to 124.7) | 104.3 (57.3 to 134.6) | 0.93 |
| category totals, one year | 11.2 (5.7 to 14.3) | 9.5 (5.8 to 13.4) | 0.84 |
| category totals, ten years | 227.9 (132.9 to 243.9) | 229.1 (120.5 to 256.4) | 1.01 |
| filtered search, nothing matches (whole ledger) | 526 (300 to 555) | 599 (276 to 614) | 1.14 |
| 12 other figures (searches, tag suggestions, split save, recent) | | | 0.27 to 1.20 |

Reading it: seven figures are above 1.2 by median, but in every one the head's range contains the base's (or the base's range reaches inside it), and the same figures swung as much in 0.13.0's own run (deep scroll 0.33 to 1.40 base against 0.97 to 2.54 new there; slowest of 60 saves 23.6 to 32.1). Three runs each cannot show a 1.3 times effect on figures whose spread is 2 times. No consistent regression, none ruled out for deep scroll and slowest-save; both are sub-3 ms and 40 ms figures on a software-drawn emulator.

**Cold start** (`am start -W`, `TotalTime`, 7 starts after each timing run, so on the 100k-entry ledger the tests leave): base median 1,293 ms (964 to 1,744, 21 starts), head 1,459 ms (381 to 2,005, 21 starts). The ranges overlap; the head's spread includes starts of 381 and 700 ms, so the 13% in the medians is within the noise of one build against itself.

**Frame logger, six interactions** (`FrameLogDriveTest`, API 34, 2 runs each, base and head alternating, cold boot before each; swap 14.2 to 14.8 GB, load 5.4 to 8.6 at start; seeded 1,500-entry ledger; median of the 2 runs, ms; "over 100" = frames over 100 ms). Only 2 runs each: the emulator would not start a third after a run ("Running multiple emulators with the same AVD") and the first attempts were lost to that.

| Interaction | base: frames, p50 / p90 / p99, over 100 | head: frames, p50 / p90 / p99, over 100 |
|---|---|---|
| Home fling | 209, 183.6 / 1283 / 1994, 146 | 215, 184.1 / 1237 / 1858, 152 |
| Home to entry | 734, 33.0 / 55.8 / 109, 10 | 623, 33.0 / 54.5 / 136, 11 |
| Add open | 401, 32.5 / 49.7 / 680, 8 | 328, 33.2 / 57.8 / 1351, 10 |
| Add close | 262, 29.4 / 47.2 / 564, 4 | 258, 32.5 / 65.7 / 1320, 10 |
| Reports tab switches | 686, 33.0 / 103.6 / 370, 76 | 736, 33.7 / 100.5 / 399, 76 |
| the four tab switches | 634, 33.3 / 68.2 / 665, 37 | 652, 33.0 / 73.5 / 708, 39 |
| Search results scroll | 159, 129.8 / 571 / 761, 104 | 169, 113.2 / 545 / 836, 103 |

The p50 and p90 of every interaction are the same within a few percent (Home fling 183.6 against 184.1, Reports tab switches 33.0 against 33.7). Two cells are worse on the head by more than noise would usually allow: Add open p99 680 against 1,351 ms and Add close p90 47 against 66 ms, p99 564 against 1,320 ms, and "over 100 ms" 4 against 10 for Add close. Each p99 is one long frame (the earlier slices saw the same single frames of 1,184 to 1,319 ms in builds with no S0 or S1 code), but with 2 runs this is NOT proved noise. It is worth a look in S2 (the Add form is rebuilt there anyway).

## UI S2 measured (Slice 115, 2026-11-16): the Add form and the entry detail, frame logger

**Setup, said plainly.** `FrameLogDriveTest` (seeded 1,500-entry ledger, 5 repetitions per interaction, debug builds), API 34 emulator (arm64, software drawn), cold boot with `-wipe-data` and `tools/emulator_setup.sh` before EVERY run, the script waiting for a 1-minute load under 9 (up to 5 minutes), Gradle daemons stopped. Three builds, 5 runs each, alternating (rounds `s2 head b13`, `b13 s2 head`, `head b13 s2`, `s2 b13 head`, `b13 head s2`; the fifth round was cut by the tool's 1-hour limit and its two missing runs (head and S2) were made afterwards as a sixth pair): `b13` = 0.13.0 (`b899430`) + the logger patch, `head` = 0.14.0 (`8b4a2ac`), `s2` = the S2 branch (`c74b8c0`). The Mac is the owner's machine: 1-minute load 4 to 21 at the starts, swap 5.2 to 5.6 GB, Spotlight and other apps running; one run of an earlier series was dropped because the load went to 88. Raw files: `~/Documents/personal/expenses_builds/ui_s2/frames/final_series_b13_head_s2`, and the earlier before-redesign series (0.13.0 against 0.14.0, 4 and 5 runs) in `.../baseline_0.13.0_vs_0.14.0`.

| Interaction | b13: frames, p50 / p90 / p99, over 100 ms | head: same | s2: same |
|---|---|---|---|
| Home fling | 233, 17.4 / 200 / 384, 50 | 232, 17.3 / 185 / 417, 51 | 233, 17.2 / 151 / 351, 51 |
| Home to entry (S2: to the detail) | 136, 17.0 / 17.2 / 317, 2 | 117, 17.1 / 17.4 / 88, 1 | 49, 17.1 / 30.9 / 100, 1 |
| Detail to Edit (S2 only) | - | - | 35, 17.0 / 17.5 / 184, 1 |
| Add open | 77, 17.0 / 17.3 / 106, 1 | 67, 17.0 / 17.3 / 202, 1 | 42, 17.1 / 22.2 / 117, 1 |
| Add close | 50, 17.0 / 17.3 / 151, 1 | 45, 17.0 / 17.5 / 100, 1 | 35, 17.1 / 17.5 / 86, 0 |
| Home to Reports | 26, 17.0 / 17.4 / 17.7, 0 | 31, 17.0 / 17.4 / 67, 0 | 36, 17.1 / 23.6 / 134, 1 |
| Reports tab switches | 679, 17.1 / 18.5 / 32, 1 | 693, 17.1 / 17.5 / 19.5, 0 | 687, 17.1 / 18.1 / 23.6, 0 |
| the four tab switches | 434, 17.1 / 17.9 / 50, 1 | 437, 17.1 / 17.8 / 34, 1 | 435, 17.2 / 18.0 / 67, 3 |
| Search results scroll | 225, 17.1 / 67 / 117, 7 | 227, 17.1 / 67 / 101, 7 | 226, 17.2 / 67 / 117, 8 |

**Worst frame of every single repetition** (25 repetitions per build, ms): Add open median 17.7 / 17.5 / 17.6 and repetitions over 100 ms 6 / 6 / **4** (b13 / head / S2); Add close 17.6 / 17.5 / 17.7 and 7 / 4 / **2**; Home to entry (to the detail on S2) 66.9 / 17.9 / 17.8 and 10 / 7 / **5**; Detail to Edit (S2) 17.6 and 4. The worst single frames are 2,418 and 2,459 ms (b13, one run: a host stall, the same run also has a 7,220 ms frame in Home to entry), 250 (head) and 692 (S2) for Add open.

**Reading it.**
- **Nothing that S2 did not touch moved beyond the noise** (Home fling, Search scroll: the same 50 / 51 / 51 and 7 / 7 / 8 frames over 100 ms). The tab switches show 3 frames over 100 ms for S2 against 1 and 1 in the four switches and 1 against 0 for Home to Reports: S2 did not touch those screens and the p99 spread inside one build is 23 to 167 ms, so this is the host, not proved either way.
- **Home to entry has fewer, shorter frames**: 49 frames (S2) against 117 and 136 for one open, 1 frame over 100 ms in the five-run totals (head 1, b13 2), and 5 of 25 repetitions with a frame over 100 ms against 7 and 10. The p90 is higher (30.9 against 17.4 ms) because the detail's transition is now a short container transform of the tapped row alone, not the old open that ran for 117 to 136 frames. [INFERENCE: the cause is that only one row carries the shared-bounds modifier and the detail is a light screen; not profiled.]
- **Add open**: the first open of a run, the one with the long frame in the old form (317 / 289 / 334 ms in the earlier series, 250 ms for 0.14.0 here), was 17, 34, 18, 34 and 18 ms in the five S2 runs. 4 of 25 repetitions have a frame over 100 ms (6 and 6 for the old form), the worst is 692 ms (one repetition in one run). **That is the direction the work aimed at, but the numbers do not prove it: 4 against 6 of 25 is inside the noise, and the S2 p99 range over runs is 19 to 692 ms.** "No frame over 100 ms on Add open" is NOT achieved on this emulator: it has 4 repetitions in 25 with one, like the old form.
- **What was tried and did not help:** (1) an interleaved single-process experiment over the form's first-frame parts (pad, note/accounts/More, deferral on or off, kinds, tiles): on a quiet boot no variant was worse than the others (median worst frame 17.2 to 17.5 ms); (2) the FIRST Add open of a fresh process, 5 to 12 fresh processes per variant (a form with only its header: still one frame of 234 ms in 1 of 5 processes; the full form: 0 of 9 clean processes had one in the second experiment and 2 of 6 in the first, the stalls moved with the host load, not with the variant); (3) a warm-up composing a hidden text field, chips and pad once after the first frame (12 processes: not better than the form without it, 2 of 8 with a stall against 0 of 9), so it was not built. The long frame of the first open is not a cost of one component of the form that a warm-up or a smaller first frame removes reproducibly here. A baseline profile is still not shipped; a real-phone number is still the owner's.
- A tap on the row in the old build opened the editor with the same shared-container on every row; the new one is on the tapped row only. Home fling 233 / 232 / 233 frames, p50 17.2 to 17.4 ms: the rows' modifier was not a measurable cost on this emulator (Home with 1,500 entries; the 100,000-row scroll was not re-measured).

**Timing classes A/B** (`RefreshBudgetTest`, `PerformanceBudgetTest`, `HomeListTest`; 16 tests per run; API 26 emulator, cold boot with `-wipe-data` and `emulator_setup.sh` before every run; order head, S2, S2, head, head, S2 = 3 runs each; host load 5 to 12 at the starts, swap 5.3 to 5.4 GB; head = 0.14.0 `8b4a2ac`, S2 = `c74b8c0`; raw files and the comparison in `~/Documents/personal/expenses_builds/ui_s2/frames/ab_timing_api26`). The first head run failed 7 of the 16 tests (the absolute phone budgets, as the budget tests do on this emulator in a loaded moment) and the other 5 runs passed 16 of 16: the budget tests pass on S2 in all 3 runs and on the head in 2 of 3. Medians of the 3 runs of each build for the 23 figures the tests print (min to max in the raw file): **none is more than 1.3 times worse for S2; the ratios run from 0.63 to 1.07**. The figures closest to the S2 work: save to new row and total visible at 100k 3.90 ms (head) against 4.00 (S2); rebuild after the row is shown 16.0 against 15.1; whole save path 3.83 against 3.55; save one expense end to end (p95 of 40) 11.06 against 8.25; recent 50 0.28 against 0.30; account balances over the whole ledger 25.5 against 25.8. S2 changes nothing in the save path's storage except one grouped read of the category indexes done once in the background (`CategoryStore.useCounts`) and one counter update per saved entry.

**Cold start** (`am start -W`, `TotalTime`, 7 starts after each of the 3 runs, on the 100k-entry ledger the tests leave, API 26): head median 297 ms (277 to 1,346, 21 starts), S2 median 302 ms (272 to 419, 21 starts): the same.

## UI S3 measured (Slice 116, 2026-11-17): glyph tiles, neutral amounts, swipe row v2

**Setup, said plainly.** `FrameLogDriveTest` (seeded 1,500-entry ledger, 5 repetitions per interaction, debug builds), API 34 emulator, cold boot with `-wipe-data` and `tools/emulator_setup.sh` before EVERY run, the script waiting for a 1-minute load under 9, Gradle daemons stopped. Head = `origin/main` `c899c13` (S2) with the frame logger test, built from a separate worktree. Two series of 3 alternating runs each (rounds `s3 head`, `head s3`, `s3 head`). **This host was much slower than in the S2 series** (memory swap 8 to 13 GB, other applications open): the same Home fling that had a 17 ms median on 16 November has a 100 to 150 ms median today on the unchanged head. Only the alternating comparison means anything; nothing here is a phone number. Raw runs and the scripts: `~/Documents/personal/expenses_builds/ui_s3/frames/`.

**Series 1, the first S3 build (`s3f`).** Home fling: head 215 frames, p50 103.0 / p90 542.8 / p99 1,501.5 ms, 112 over 100 ms; S3 209 frames, 131.8 / 671.9 / 2,662.0, 140 over 100 ms (per round p50: head 100.4, 103.0, 107.1; S3 119.1, 136.9, 131.8). A real difference on this host, in all three rounds.

**What was tried.** (1) `FlingProbe` (throwaway, never committed): one process, the Home fling repeated per variant with parts of the row switched off by a static flag, 5 repetitions: the monogram lookup, the glyph icon, `MoneyText`, the whole tile: every variant between 115 and 134 ms median (the full row 134): none blamed alone. (2) The same with the swipe row's new parts (crossfade, custom action, haptic effect, the drawn background) and a rotated order, 8 repetitions per variant (median of p50, ms): full row 145, no crossfade 143, no custom action 163, no haptic effect 142, plain background 128, all swipe parts off 132, everything off (swipe and tile) 107. Noisy (a repetition varies by 20 ms or more), the direction is: the swipe background and the tile together. (3) **The change made:** `SwipeToDismissBox` composes its background for every row, resting or not; the background is now composed only while the row is being dragged (`derivedStateOf` on the dismiss direction, the card covers it at rest). Behaviour is the same: a drag shows it from the first pixel.

**Series 2, the final build (`s3g`)** (the same 3 alternating runs): Home fling p50 head 138.0 / 150.5 / 111.1 and S3 137.3 / 123.8 / 108.0 ms per round; totals head 209 frames, 138.0 / 754.2 / 2,027.8 ms (p50 / p90 / p99), 130 over 100 ms; S3 214 frames, 123.8 / 743.2 / 1,275.8, 139 over 100 ms: **the same as the head within the noise.**

| Interaction (series 2) | head: frames, p50 / p90 / p99, over 100 ms | S3 (`s3g`): same |
|---|---|---|
| Home fling | 209, 138.0 / 754.2 / 2,027.8, 130 | 214, 123.8 / 743.2 / 1,275.8, 139 |
| Home to entry | 164, 29.9 / 67.0 / 706.8, 8 | 155, 23.8 / 55.4 / 502.7, 4 |
| Detail to Edit | 125, 23.7 / 37.1 / 403.2, 3 | 74, 26.0 / 62.1 / 480.1, 5 |
| Add open | 126, 28.0 / 53.0 / 660.0, 6 | 104, 24.2 / 57.8 / 434.0, 6 |
| Add close | 97, 26.0 / 52.2 / 467.1, 4 | 99, 23.2 / 46.5 / 484.2, 5 |
| tab switch: Home to Reports | 101, 28.6 / 50.6 / 454.8, 4 | 83, 26.3 / 80.0 / 521.0, 8 |
| Reports tab switches | 641, 32.6 / 101.1 / 318.1, 65 | 549, 33.8 / 134.7 / 334.4, 97 |
| tab switch: Home to More | 182, 26.6 / 81.6 / 251.2, 13 | 168, 26.2 / 123.7 / 502.1, 21 |
| tab switch: More to Home | 129, 28.4 / 91.9 / 233.8, 12 | 130, 29.5 / 115.6 / 235.8, 18 |
| Search results scroll | 163, 100.6 / 406.0 / 1,084.7, 84 | 159, 109.4 / 349.7 / 610.1, 89 |
| the four tab switches | 480, 25.2 / 80.3 / 398.8, 35 | 474, 27.0 / 103.6 / 469.9, 43 |

**Reading it.**
- The Home fling is equal to the head after the change (and was 25% slower in p50 before it). The fling with S3's icons on every row is NOT worse than the head within what this host can show.
- **Looked worse, did not repeat:** in these two full-test series the Reports tab switches had more frames over 100 ms and a higher p90 for S3 than for the head (97 against 65 and p90 134.7 against 101.1 ms in series 2; 85 against 70 in series 1), and most other tab switches in series 2. Measured on their own (see "Follow-up" below) the difference is not there.
- Add open and Add close: 6 and 6, 4 and 5 frames over 100 ms (head, S3), the same as S2 within the noise; Home to entry 8 against 4.
- **Not measured:** the 100,000-row Home scroll after the rows got tiles; a phone.

**Timing classes A/B** (`RefreshBudgetTest`, `PerformanceBudgetTest`, `HomeListTest`; 16 tests per run; API 26 emulator, cold boot with `-wipe-data` and `emulator_setup.sh` before every run; order head, S3, S3, head, head, S3 = 3 runs each; host load 4 to 11 at the starts, swap 11 to 13 GB). Of the 21 figures printed in every one of the 6 runs, 19 agree within 1.1 times in the medians; the two others are sub-4 ms storage searches (search with no match 0.41 against 0.69 ms, a rare merchant 1.9 against 3.2 ms; the ranges overlap: 0.41 to 0.42 against 0.42 to 0.71; S3 changes no storage query). Both builds fail 5 to 7 of the 16 tests, the absolute phone budgets, because the host is loaded (the base's first run failed 7 in the S2 series for the same reason). Raw: `~/Documents/personal/expenses_builds/ui_s3/frames/timing_api26`.

**Cold start** (`am start -W`, `TotalTime`, 7 starts after each of 3 runs, API 26): head median 789 ms (346 to 1,731, 21 starts), S3 median 805 ms (664 to 1,401, 21 starts): the same within the spread (the S2 series had 297 and 302 ms on a quiet host).

**Release APK size:** 57,310,813 bytes against 57,294,429 for S2 (+16,384 bytes, +0.03%). The iPhone app size was not measured.

**Follow-up (2026-11-17): the tab switches on their own, and the keyboard test.** `TabSwitchDriveTest` (a throwaway copy of `FrameLogDriveTest` doing only the tab switches, 10 repetitions per run), API 34, cold boot per run, base `c899c13` against main `7f6c121`, 6 alternating runs each. Frames over 100 ms per run, the four tab switches together: base 72, 72, 63, 115, 98, 86 (mean 84.3), S3 52, 77, 88, 106, 88, 80 (mean 81.8); Home to Reports, Reports to Home, Home to More and More to Home, and the five Reports mode switches (series 2): no consistent direction (S3 higher in some runs, lower in others); p50 24 to 28 ms for both. A third build that drew the tile glyphs from cached paths measured the same (79, 87, 77) and was not kept. `UiS0LayoutTest.theKeyboardGoesDownWithTheAddFormNotAfterIt`, API 26, 12 alternating cold-boot runs, milliseconds to the keyboard gone: base 2,815 2,814 2,297 2,365 1,826 2,941, S3 2,349 1,798 2,589 2,466 3,268 2,790 (medians 2,581 and 2,528; limit 4,000; the morning's failures, 4,037 to 4,674 ms on both builds, were a loaded hour). No product change. Details in `docs/PHASE2_PROGRESS.md`, "Slice 116 follow-up"; raw runs in `~/Documents/personal/expenses_builds/ui_s3/followup/`.

## UI S4 measured (Slice 117, 2026-11-17): the charts

**Setup, said plainly.** A throwaway test, `ReportsDriveTest` (a copy of `FrameLogDriveTest`'s method that measures only the Reports interactions; kept in `~/Documents/personal/expenses_builds/ui_s4/frames/`, never committed), on the same seeded 1,500-entry ledger: Home to Reports, the five in-page switches (By year, By period, Account, Merchant, Category), a Reports scroll (three swipes up and three down), a drag along the bars (S4 only: the base has no scrubber), Reports back to Home, Home to More and back; 10 repetitions per run. API 34 emulator, cold boot with `-wipe-data` and `tools/emulator_setup.sh` before EVERY run, Gradle daemons stopped, the script waiting for a quiet host. Head = `origin/main` `fa13ecb` (built from a separate worktree), S4 = the code commit before the ring-middle fix (the fix changes text only from 140% text). Three alternating runs each (rounds `head s4f`, `s4f head`, `head s4f`). **The host was quiet in rounds 1 and 3 (a 17 ms median frame) and loaded in round 2 (other programs): read the rounds apart.**

| Interaction (per run: frames, p50 / p90 / p99 ms, over 100 ms) | head, rounds 1 / 2 / 3 | S4, rounds 1 / 2 / 3 |
|---|---|---|
| Home to Reports | 73, 17.1 / 21.9 / 117, 2 · 167, 20.6 / 45 / 387, 4 · 72, 17.0 / 17.3 / 84, 0 | 64, 17.1 / 17.5 / 84, 0 · 119, 18.0 / 36 / 321, 2 · 70, 17.0 / 17.6 / 84, 0 |
| Reports in-page switches | 1403, 17.1 / 17.5 / 22, 2 · 1392, 22.0 / 66 / 148, 53 · 1404, 17.0 / 17.5 / 18.5, 0 | 1304, 17.1 / 17.7 / 23, 0 · 1217, 17.1 / 50 / 115, 22 · 1332, 17.0 / 17.5 / 50, 4 |
| Reports scroll | 1417, 17.2 / 19.6 / 34, 3 · 1066, 31.9 / 106 / 247, 123 · 1424, 17.1 / 19.5 / 37, 2 | 1450, 17.1 / 18.5 / 34, 0 · 1277, 17.2 / 73 / 143, 62 · 1445, 17.1 / 20.1 / 51, 3 |
| Reports to Home | 67, 17.0 / 17.2 / 117, 1 · 148, 19.6 / 61 / 350, 6 · 73, 17.0 / 30.7 / 339, 1 | 68, 17.1 / 22.2 / 151, 3 · 126, 17.7 / 45 / 417, 5 · 72, 17.0 / 22.8 / 167, 3 |
| Reports bar scrub (S4 only, 20 frames a run) | - | 17.1 / 18.5 / 20, 0 · 17.1 / 74 / 132, 2 · 17.0 / 17.7 / 19, 0 |

**Reading it.**
- **No regression shown, and nothing proved better.** Frames over 100 ms, head against S4: Home to Reports 2 / 4 / 0 against 0 / 2 / 0; in-page switches 2 / 53 / 0 against 0 / 22 / 4; scroll 3 / 123 / 2 against 0 / 62 / 3. S4 is lower in rounds 1 and 2 and a little higher in round 3 (4 against 0 in the in-page switches, 3 against 2 in the scroll; the scroll's p99 in the quiet rounds is 34 and 37 ms for the head against 34 and 51 for S4). Fewer frames are drawn in the in-page switches (1,304 / 1,217 / 1,332 against 1,403 / 1,392 / 1,404): the bars no longer run one animation per bar through composition. That is a count of frames, not a claim of speed.
- **One figure is a hair worse:** Reports back to Home has 3 / 5 / 3 frames over 100 ms for S4 against 1 / 6 / 1 (p99 151 and 167 ms against 117 and 339): inside the noise of one 10-repetition run, but it is the only figure where the quiet rounds lean the wrong way; the Home screen it returns to is unchanged by S4 (apart from the line's scrubber).
- The scrub itself: 20 frames a run, p50 17 ms, no frame over 100 ms in the quiet rounds (2 in the loaded one).
- **Not measured:** the recomposition count (no Layout Inspector here; by construction the bars' only read of the grow animation in composition is the `derivedStateOf` flag `grown`, and the scrub reads its index in the draw lambda and in the bubble); a 100,000-row ledger on Reports; a phone.

**Timing classes A/B** (`RefreshBudgetTest`, `PerformanceBudgetTest`, `HomeListTest`; 16 tests per run; API 26 emulator, cold boot with `-wipe-data` and `emulator_setup.sh` before every run; order head, S4, S4, head, head, S4 = 3 runs each; host load 6 to 9 at the starts, a 5-minute load of 12 to 25, swap 12 to 13 GB: a heavily loaded host). Of the 23 figures printed in every one of the 6 runs, 21 agree within 1.1 times in the medians and none is more than 1.3 times worse for S4; the largest is "save one expense end to end (p95 of 40)": head 23.8 ms (17.3 to 26.5), S4 29.2 ms (24.8 to 30.2): 1.23 times, ranges barely overlapping; S4 does not touch the save path or the Home list, and S3 saw the same figure at 38 ms on a loaded host, so it is read as the host, not proved either way. Both builds fail 5 to 8 of the 16 tests, the absolute phone budgets, because the host is loaded. Raw: `~/Documents/personal/expenses_builds/ui_s4/frames/`.

**Cold start** (`am start -W`, `TotalTime`, 7 starts after each of 3 runs, API 26): head median 1,001 ms (752 to 2,522, 21 starts), S4 median 936 ms (798 to 3,842, 21 starts): the same within the spread (a loaded host: 297 ms on a quiet one in the S2 series).

**Release APK size:** 57,327,197 bytes against 57,310,813 for S3 (+16,384 bytes, +0.03%). The iPhone app size was not measured.

## UI S5 measured (Slice 118, 2026-11-18): pace on the month card, Budget, Goals, Categories

**Setup, said plainly.** A throwaway test, `HomeDriveTest` (a copy of `FrameLogDriveTest` with a budget set, so the month card, its pace line and the budget tile are all drawn; it measures the Home fling, Home to a first entry, Home to Budget and back, and the tab switches; kept in `~/Documents/personal/expenses_builds/ui_s5/frames/` with the raw files), was run against the base (`head`, S4 plus the test) and S5 (`s5f`, the S5 code plus the test), on the API 26 emulator, cold boot with `-wipe-data` and `emulator_setup.sh` before every run, order alternating. Two series: three rounds (head, s5f / s5f, head / head, s5f) and four rounds. **The host was loaded the whole time** (load average 5 to 9 on this Mac, other sessions working; the Home fling alone shows a median frame of 100 to 143 ms in both builds, where a quiet host gives 17 ms), so the figures say "the same", not "faster". The Gradle daemons were stopped before each series.

**Series 1 (3 rounds each; frames, p50 / p90 / p99 ms, frames over 100 ms, summed over the runs of one round shown as the median per run):**

| Interaction | head | S5 |
|---|---|---|
| Home fling | 340, 125.9 / 636.2 / 1,577.9, 223 | 333, 125.5 / 650.8 / 1,402.6, 213 |
| Home to entry | 228, 23.6 / 42.7 / 422.7, 7 | 211, 25.5 / 48.7 / 490.5, 9 |
| Home to Budget | 110, 25.5 / 83.8 / 421.9, 7 | 85, 25.7 / 100.5 / 391.9, 9 |
| Budget back to Home | 143, 21.7 / 51.7 / 449.2, 7 | 121, 24.1 / 67.7 / 506.5, 8 |
| tab switch, the four | 688, 26.5 / 107.6 / 451.7, 82 | 664, 26.3 / 116.7 / 398.3, 84 |

**Series 2 (4 rounds each):**

| Interaction | head | S5 |
|---|---|---|
| Home fling | 332, 134.1 / 669.0 / 2,215.6, 216 | 332, 131.1 / 671.9 / 1,399.7, 213 |
| Home to entry | 209, 24.1 / 52.0 / 574.8, 9 | 208, 25.3 / 58.3 / 533.2, 11 |
| Home to Budget | 114, 25.3 / 70.7 / 438.2, 9 | 87, 28.5 / 87.5 / 392.7, 8 |
| Budget back to Home | 131, 24.9 / 70.3 / 499.1, 7 | 118, 21.8 / 57.2 / 378.2, 8 |
| tab switch, the four | 657, 26.8 / 118.3 / 439.4, 77 | 669, 26.2 / 116.1 / 379.2, 73 |

**Reading it.**
- **No regression shown, nothing proved better.** The Home fling (the screen that now carries one more row on the month card, the ghost line and a tick on the tile's bar) has 223 / 227 / 205 frames over 100 ms for head against 206 / 225 / 213 for S5 in series 1, and 216 against 213 in series 2: the same.
- **One figure leaned the wrong way in series 1, and did not in series 2.** Home to entry: frames over 100 ms per run 7 / 6 / 7 for head against 7 / 9 / 10 for S5 in series 1; in series 2 the median was 9 against 11 with S5's p99 lower. Taken together (7 runs each) the two builds are inside each other's spread. The cause was not looked for further; it is listed under "not measured".
- Home to Budget has fewer frames in the S5 runs (85 to 87 against 110 to 114): the count is the frames the transition drew, not a speed, and the reason was not examined (the S5 Budget screen has fewer rows composed at first: the group without limits is folded).
- **Not measured:** recomposition counts (no Layout Inspector here), the 100,000-row Home scroll, any real-device number, and the first entry's position on Home in dp (read from the pictures instead: the first entry is on the first screen at 393 and 360 dp, 100% text).

**Timing classes A/B** (`RefreshBudgetTest`, `PerformanceBudgetTest`, `HomeListTest`; 16 tests per run; API 26 emulator, cold boot with `-wipe-data` before every run; order head, s5f, s5f, head, head, s5f; the host loaded as above). **Both builds fail the same 6 to 7 timing-limit tests in every run**: the limits are phone budgets divided by 4 and this loaded host misses them with the base build as much as with S5 (for example "save one expense end to end p95" 17.9 ms against its 12.5 ms limit on head). So the figures are compared, not the pass counts. Median of 3 runs each, head against S5: save to a new row, p50 12.6 against 12.6 ms, p95 26.7 against 37.3 ms (head 26.7 / 24.9 / 29.9, S5 43.8 / 37.3 / 29.5: two of three S5 runs higher); the rest of the screen after the row, p50 48.8 against 50.4 ms, p95 95.1 against 82.7 ms; whole save path p50 8.84 against 10.14 ms; account balances over 100,000 entries 57.7 against 60.3 ms; category totals over 10 years 125.1 against 130.3 ms. **Read plainly:** the save path is a little slower in S5 in these runs (the p95 of the save to a new row and the whole save path p50), inside the noise of a loaded host but in the same direction twice; the one extra read in S5's slow figures (the ghost line's 14 days, one period earlier) runs after the new row is on screen. Not chased further in this slice.

**Cold start** (`am start -W`, `TotalTime`, 7 starts after each of 3 runs, API 26): head median 801 ms (382 to 1,441, 21 starts), S5 median 888 ms (369 to 1,532, 21 starts): the same within the spread of a loaded host (about 4 to 1 between the fastest and slowest start in each).

**Release APK size:** 57,359,965 bytes against 57,327,197 for S4 (+32,768 bytes, +0.06%). The iPhone app size was not measured.

**Follow-up (2026-11-18): the save path and Home to entry, checked on alternating runs: no S5 effect found, no product change.** The slice's own A/B (above) leaned S5 slower on the save path twice and on Home to entry once, so both were re-measured against the base `5c2bc36` with the final main `e2d8566`, debug builds, API 26 emulator, cold boot with `-wipe-data` and `emulator_setup.sh` before every run, Gradle daemons stopped, no other work of mine running. **The host was not quiet:** load average 5 to 10 the whole time (three other Claude sessions on this Mac), so the figures are about equality, not speed. Why the save figures cannot be S5's by construction: `git diff 5c2bc36 e2d8566 -- core` is `PaceMath` and its test only, so the storage layer that `theWholeSavePathStaysUnder50msPhoneBudgetAtP95`, the account-balance and the category-total tests measure is byte-for-byte the same; what S5 adds to the refresh a save triggers is arithmetic in `budgetUi` (microseconds), and in the slow figures (which run after the row is on screen, debounced) one 14-day read for the ghost line.

**Timing classes, 5 runs each, order base, main, main, base, base, main, main, base, base, main** (`RefreshBudgetTest`, `PerformanceBudgetTest`, `HomeListTest`; both builds again fail the same 6 to 7 host-scaled limit tests every run). Median of 5 (the five values), base against main:
- save to a new row at 100,000 entries: p50 12.4 (18.1, 12.4, 13.4, 11.9, 12.3) against 12.3 (12.3, 11.7, 12.5, 12.8, 11.9) ms; p95 31.0 against 26.6 ms (base 44.5, 32.0, 26.6, 31.0, 27.4; main 22.4, 26.6, 44.0, 38.7, 22.2);
- the rest of the screen after the row: p50 47.7 against 46.1 ms; p95 84.8 against 74.6 ms;
- whole save path (storage only): p50 9.39 against 9.23 ms; p95 17.6 against 18.9 ms; one store save p50 1.10 against 1.13 ms;
- account balances over 100,000 entries 59.6 against 59.4 ms; category totals over 10 years 123.6 against 135.5 ms (base 130.4, 122.6, 117.5, 123.6, 123.8; main 135.7, 144.8, 123.1, 124.6, 135.5: the one figure that leans the wrong way in this set; it is a read S5 did not touch, so it is read as noise);
- cold start (`am start -W`, 35 starts each): median 869 ms (400 to 1,338) for base, 787 ms (433 to 1,116) for main.
**Read plainly:** the earlier "slower save path" (10.1 against 8.8 ms) did not come back: with five runs each the medians are equal and the spread inside one build (save-to-new-row p95 22 to 44 ms in main, 27 to 45 ms in base) is larger than any gap between them. It was host noise. Nothing was moved off the save path because nothing S5 adds there is measurable.

**Home to entry, 3 more series (frames over 100 ms per run, a run = 8 repetitions; base against S5):** series 3, 4 rounds: 9, 7, 6, 10 against 8, 13, 9, 11 (worst frame per repetition, median 149 against 488 ms); series 4, 2 rounds of three builds: base 10, 5; S5 6, 8; **S5 with the ghost line not drawn 13, 11** (worst frame per repetition, median 413 against 352 against 425 ms). Pooled over all four series (13 runs each, 104 repetitions each): frames over 100 ms per run mean 9.4 for base, 9.7 for S5 (median 7 against 9); the worst frame of a repetition has a median of 288 against 437 ms, and 76 of 104 repetitions have a frame over 100 ms for base against 84 of 104 for S5. **Read plainly:** the mean is the same; the tail leans a little toward S5 in three of the four series and not in the fourth, and the baseline itself moves between series by more than the difference (base's median worst frame was 149 ms in one series and 413 ms in another). The ghost line was tested as the cause by drawing it off: no better (it was worse in that series), so it is not the cause. Not isolated further: a gap this small needs a quiet machine or a phone, not this loaded emulator. **No product change was made.** Raw files of all series are in `~/Documents/personal/expenses_builds/ui_s5/frames/` (`series1`, `series2`, `series3_bhd_mhd`, `series4_bhd_mhd_nog`) and `.../timing_followup/`.
