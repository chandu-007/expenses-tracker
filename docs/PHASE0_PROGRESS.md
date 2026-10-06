# Phase 0 progress

Last updated: 2026-10-06.

## Done
- **Project scaffold builds and tests pass.** `./gradlew check :androidApp:assembleRelease` succeeds.
  - `:core:domain` is a Kotlin Multiplatform module (JVM + Android). It contains `Money` and `Currency` (integer minor units, overflow-checked, currency-mismatch rejected). 6 property and unit tests pass on the JVM and 6 on Android host tests.
  - `:backend` is a Ktor server with a `/health` route. 1 test passes.
  - `:androidApp` is a Jetpack Compose app, minSdk 26. The debug APK is about 11.8 MB and the R8-minified release APK about 0.7 MB.
- Gradle wrapper (9.7.0) is committed, so no global Gradle is needed.
- Library versions are pinned in `gradle/libs.versions.toml`.

## Pinned versions (stable only, checked 2026-10-06)

| Item | Version | Note |
|---|---|---|
| Kotlin | 2.4.20 | Fully supports Gradle ≤ 9.7.0 and AGP ≤ 9.3.1 ([Kotlin docs](https://kotlinlang.org/docs/gradle-configure-project.html)) |
| Gradle | 9.7.0 | 9.8.0 exists but is above what Kotlin 2.4.20 fully supports |
| Android Gradle Plugin | 9.3.1 | KMP modules use `com.android.kotlin.multiplatform.library`; the app uses `com.android.application` with built-in Kotlin |
| Compose (Jetpack BOM) | 2026.09.00 (ui 1.12.1) | Matches Compose Multiplatform 1.12.1 |
| Compose Multiplatform | 1.12.1 | In the catalog; not applied yet (needed when UI moves to shared code for iOS) |
| SQLDelight | 2.4.0 | Applied in `:core:storage`; works with AGP 9.3.1 and needs the SQLite 3.38 dialect |
| Ktor | 3.6.0 | Backend |
| Coroutines / serialization / datetime | 1.11.0 / 1.11.0 / 0.8.0 | |
| Kotest (property tests) | 6.2.5 | |
| SQLCipher for Android | 4.19.1 | In the catalog; not wired yet |
| compileSdk / minSdk / targetSdk | 37 / 26 / 36 | Current Compose 1.12.1 and Lifecycle 2.11.0 require compileSdk 37. **targetSdk 36 must be rechecked against Play's current requirement before the first upload.** |

## Findings
- **Xcode is not installed.** Only the Command Line Tools are present, so `xcrun` cannot find the iOS simulator SDK and iOS targets cannot link or test here. (An earlier note in this project said Xcode was present; that was wrong.) iOS targets are therefore **opt-in** with `-PenableIos=true` (default `false` in `gradle.properties`). Phase 0 spike (d) and the iOS simulator CI job need full Xcode on a machine or macOS CI runner.
- IntelliJ IDEA does not support AGP 9. Use Android Studio for the Android modules.
- `<latest>` in Maven metadata includes alpha, beta and RC releases; always filter to stable.
- AGP downloaded Android platform 37 on its own; no `sdkmanager` was needed.

## Not yet verified
- The app has not been launched on an emulator or device; only built.
- The backend has been tested in-process only; no Docker image yet.

## Next steps (in order)
1. ~~Ledger engine~~ Done (see update below).
2. `:core:storage` remaining: SQLCipher driver factory with an Android Keystore key and an instrumented test on an emulator; entries/ledger mapping layer; sync log writer.
3. Performance harness: synthetic 100k-transaction dataset, Macrobenchmark and Baseline Profiles, CI gates for the §6.1 budgets.
4. Design system, privacy baseline (dependency allowlist), backend Dockerfile.
5. Spikes: SMS parser on 10 banks, Compose list with 100k rows, iOS items once Xcode is available.

## Update: ledger engine and storage (2026-10-06)
- **Ledger engine** (`core/domain/.../ledger/`): accounts, transfers, card liability, refunds, cash, category splits, shared expenses, friend-paid items, settlements, person balances. Property tests cover: transfers never change net worth or spend; allocation never loses a minor unit; refund chains net exactly and never exceed the original or any single category; balances are independent of entry order across mixed entry types.
- **Bug found by the property test and fixed:** chained partial refunds were split independently by rounding, so one category could be refunded more than it was charged. Refunds are now applied against the amount remaining per category, in (time, id) order. A fixed regression test pins the case.
- **Hybrid logical clock** (`core/domain/.../sync/Hlc.kt`): monotonic per device, merges remote timestamps, rejects clocks too far ahead, string encoding sorts like the timestamp.
- **Storage** (`:core:storage`): SQLDelight 2.4.0 works with AGP 9.3.1 (verified by a real build). Needs the SQLite 3.38 dialect for upserts. Schema: accounts, entries with partial indexes, append-only op-log keyed by HLC. Baseline `databases/1.db` is committed; migrations are verified against it in `check`.
- **Schema bug found and fixed:** `INSERT OR REPLACE` silently deleted an existing entry when a re-sent SMS (same source and reference, new id) arrived. Inserts are now upserts by id and a clash fails loudly.
- Tests: 57 pass (JVM, Android host). Storage tests run on JVM SQLite only.

### Not yet done / not verified
- **SQLCipher is a dependency but not wired.** No encrypted database has been opened on Android, and nothing has run on an emulator or device. Needs a driver factory plus an instrumented test.
- No `.git` repository exists in this folder yet.
- Refunds of shared expenses are rejected (documented limitation).
- iOS targets still need full Xcode.

## Update: encrypted storage on a real Android runtime (2026-10-06)
- **SQLCipher is wired** (`EncryptedDriver`): random 32-byte database key, wrapped with an AES-GCM key in the Android Keystore, stored under `noBackupFilesDir`. The database opens through SQLCipher's own SQLite.
- **4 instrumented tests pass on the emulator** (Pixel_7 AVD, Android 37, arm64): file on disk is not plain SQLite; data survives reopen; SQLite is 3.38+ with upserts, FTS5 and trigram working; a duplicate source reference fails and keeps the original row.
- **Limit:** the only emulator image installed is API 37, so the old-system-SQLite problem on Android 8 to 10 (API 26-29) is not reproduced. The code never uses the framework SQLite, but that is unproven on those versions. Needs an API 26-29 image or a real device.
- **Measured on that emulator** (100k rows, SQLCipher, Apple-silicon host; a mid-range phone will be slower, so treat as best case):

| Query / operation | Before | After |
|---|---|---|
| Category totals, all time (scattered dates) | 3,450 ms | 55 ms (covering index `entry_spend`) |
| Search, very common term (8,000 matches) | 70 ms | 19 ms (newest 2,000 matches, then date order) |
| Search, rare term / no match | 1.5 ms / 0.06 ms | same |
| Last 50 entries | 0.5 ms | same |
| Bulk import 100k, dates ascending | 3.6 s per-row triggers | 2.2 s (`bulkLoad`) |
| Bulk import 100k, dates in random order | not measured | 20 s |

- **Importer rule:** insert in ascending date order (documented on `bulkLoad`).
- **Search tradeoff:** queries with more than 2,000 matches skip the oldest-added matches.
- **Decided:** keep suspending the FTS triggers rather than dropping them (1.6 s vs 2.2 s on Android; not worth DDL in importers).
- **Wrong hypothesis recorded:** a larger SQLCipher page cache did not help.
- Totals: 69 host/JVM tests and 4 emulator tests pass.

### Still open
- No performance gate in CI yet; the numbers above came from a throwaway probe. Next: a permanent harness with budgets (the emulator is a poor proxy for the reference mid-range phone).
- Ledger-to-storage mapping layer and sync log writer are not written.
- iOS still needs full Xcode; nothing about iOS has run.

## Update: older Android verified, key-loss handling, exact search (2026-10-06)
- **Android 8.0 (API 26) verified.** Installed `cmdline-tools` and the arm64 `google_apis` API 26 image; AVD `API26`. Its system SQLite is 3.18.2 (no upserts), and SQLCipher's bundled SQLite is 3.53.4. All 9 instrumented tests pass there, including a guard that fails if SQLite is older than 3.38. API 37 passed the same tests earlier. Still not covered: API 27-35 and real phones.
- **Key-loss handling** (`DatabaseKeyUnavailableException`): never creates a key next to an existing database; a missing key file, lost Keystore key or corrupt key file raises a typed exception for the app to handle (restore, or start fresh with consent via `EncryptedDriver.reset`). Tests cover each case, a wrong key being rejected, and `cipher_version`. The `readNBytes` call (API 33+) was replaced. `allowBackup="false"` is set, so the database and the unbacked-up key are never restored separately.
- **Search is now exact** (the earlier 2,000-match cap is removed). `ExpensesDb.search()` counts matches (up to 2,001, about 2 ms), then picks: sort-all-matches for small sets, or a forced walk of the date index (`INDEXED BY entry_time`) for large ones. A property-style test checks both plans give identical answers on random data, including soft-deleted rows. User text is quoted as an FTS5 phrase (`ftsPhrase`).
- **Measured on the emulator, 100k rows, scattered dates, SQLCipher** (API 26 / API 37 identical within noise; host is Apple silicon, so a phone will be slower):

| Operation | Time |
|---|---|
| Search, 100k matches | 12.6 ms |
| Search, 8,300 matches | 1.6-1.8 ms |
| Search, ~11 / 1 / 0 matches | 0.7 / 0.4 / 0.1 ms |
| Search, 2-character text (scan) | 19 ms |
| Category totals, 10,220 rows (verified count) | 1.8 ms |
| Category totals, all 100k rows | 47-53 ms |
| Last 50 entries | 0.5 ms |
| Bulk import 100k, scattered dates | 19.3-19.6 s |
| Bulk import 100k, ascending dates | 2.2 s |

- Earlier doubts resolved: the 20 s import was date order, not the new index (2.2 s ascending with the index in place); the earlier "one year" timing covered about 3,650 rows and was misleading, so it was redone with a counted window.
- Totals: 73 host/JVM tests and 9 device tests pass.

### Still open
- No permanent performance gate in the build; numbers came from a probe that was removed. The harness should use counted data windows and run on a pinned AVD, with budgets set for a mid-range phone (emulator timings are a best case).
- Importers must sort by date (about 10x faster); nothing enforces it yet.
- Ledger-to-storage mapping and the sync log writer are not written. iOS still needs full Xcode.
