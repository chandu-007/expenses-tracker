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
