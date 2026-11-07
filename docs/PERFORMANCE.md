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
| Cold start, `am start -W` TotalTime (all the work of slice 103 together: window rule, config changes, fold watching, keyboard shortcuts) | base median 322 ms (range 237-421, n = 24) | final median 304 ms (233-446, n = 24); an earlier pair of 24 runs each: 282 and 282 | **kept**: equal within noise |
| Cold start, data composed (mark "ready composed", ms since process start) | base 747 | final 712 (an earlier pair: 700 and 713) | **kept**: equal within noise |
| Idle memory (PSS after 9 s, `dumpsys meminfo`, 3 alternating pairs) | base 62.8, 63.0, 63.0 MB | final 63.1, 63.4, 63.2 MB; after the fold watcher was limited to phones that have a hinge sensor: 64.5, 64.5, 64.6 against base 63.8, 64.3, 64.6 in that later session | **kept**: +0.1 to +0.3 MB (under 0.5%), inside the drift of the session itself (the base alone moved 62.8 to 64.6 MB between sessions) |
| APK size (benchmark build, universal) | 57,044,417 bytes | 57,060,849 bytes (+16 KB) | **kept**: the window library was already in the APK (Compose and Glance bring `androidx.window` 1.5.0), only declared |
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

The Play bundle of 0.10.6 (`bundlePlaySms`) is 35,182,606 bytes; see the slice 103 notes for the bundle of this work.

## Suggestions that were NOT done (no measurement could show a gain here), ranked

1. **A baseline profile for the app's own code** (generated with Macrobenchmark on a phone: start, scroll Home, open an entry, Search, Reports). Expected: the biggest cold-start gain on a real phone (typical 15 to 30% for a Compose app); risk low (a profile file, +100 to 300 KB); effort a day with a phone. Needs a phone: this emulator cannot show it.
2. **`Modifier.sharedContainer` on every Home row** (`Screens.kt:501`) is a `composed {}` modifier (it reads three composition locals and remembers a shared-content state per row). On a long list it costs allocation per row as it scrolls in. Giving the shared key only to the row that was tapped would remove it; it touches the motion code (`MotionUiTest`), so it needs a scroll measurement on a phone first. Effort small, risk medium (the grow-out-of-the-row motion).
3. **The hourly notification check and the twelve-hourly auto-export** (`NotificationWork`, `AutoExportWorker`) and five widgets at `updatePeriodMillis` 1 h: each wake opens the encrypted database. Battery cost is small; a `setRequiresBatteryNotLow` constraint would cut it on a low battery but delays bill reminders then. Not changed: it is a product decision.
4. **Drag and drop** of an entry out of the list (a share target for another app) and **an edge-to-edge detail pane** with the amount keypad beside the form on 8-inch screens: large-screen guidelines suggest both; neither is trivial.
5. **Two panes for Search** (results on the left, the entry on the right): today an entry opened from Search covers it, full width up to 960 dp. A natural next step on a Fold; it moves the one-pane rule of every screen but Home, so it needs its own slice.
