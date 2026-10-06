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

## Update: performance gate and paging (2026-10-06)
- **Permanent gate** (`androidApp/src/androidTest/.../PerformanceBudgetTest.kt`, 7 tests): 100k rows, 10 years in ascending date order, 500 merchants with skewed frequency (top merchant about 11%), 40 categories, SQLCipher. Budgets are the section 6.1 numbers divided by `PHONE_SLOWDOWN = 4.0`. **That factor is an unverified assumption**; measure on a real mid-range phone and replace it. Run on one pinned AVD in CI. It runs under `connectedDebugAndroidTest` (needs an emulator), not `check`.
- Results on the API 26 emulator (limit = phone budget / 4):

| Check | Measured | Limit |
|---|---|---|
| Search, most common merchant (11% of rows) | 2.0 ms | 25 ms |
| Search, rare merchant (22 rows) / no match | 0.24 / 0.10 ms | 25 ms |
| Category totals, one year (10,000 rows, window verified) | 1.9 ms | 75 ms |
| Category totals, 10 years (100k rows) | 51 ms | 250 ms |
| First 50 rows of the list | 0.65 ms | 25 ms |
| Deep scroll, last page, keyset paging | 0.24 ms | 25 ms |
| Save one transaction, p95 of 60 autocommit inserts | 3.8 ms | 12.5 ms |

- **Gate found two real problems before the dataset was fixed:** (1) OFFSET paging cost 24 ms at the last page of 100k rows; replaced with keyset paging (`entriesBefore`, row-value comparison on `(occurred_at, seq)`), now 0.24 ms. `entry_time` is now `(occurred_at DESC, seq DESC)` so list order is total and pages never repeat or skip rows with equal timestamps (tested with heavy ties). (2) A search phrase made of substrings that nearly every row contains ("order 1" in data where every row said "... order N") took 26 ms against a 25 ms limit, because many large trigram lists must be intersected. That is a known slow shape, not a realistic search; it is not gated. Real merchant text rarely looks like that.
- Two failures during development were bugs in the test data and cursor, not the app, and were fixed.
- **Backup rules:** explicit `dataExtractionRules` and `fullBackupContent` exclude the database, preferences and files from cloud backup and phone-to-phone transfer, so a database can never land on a phone without its key. Until sync (Phase 3), moving to a new phone needs an export. This is a known user-facing gap against the research's "history lost on phone change" pain point; do not ship publicly without the export or sync.
- Totals: 74 host/JVM tests, 16 device tests (7 gate + 9 encrypted-storage).

### Still open
- Ledger-to-storage mapping and the sync log writer.
- Real mid-range phone measurements to replace `PHONE_SLOWDOWN`.
- iOS still needs full Xcode.

## Update: ledger-to-storage mapping and sync log (2026-10-06)
- **Spend lines.** SQL category totals used to sum one `category_id` per entry, which was wrong for split, shared and refunded entries. Spending is now defined once, in `SpendLines` (domain), used by both `Ledger` and the database. The store persists signed per-category lines (`spend_line`, covered by one index), refunds as negative lines. `spendByCategory` reads them. The old covering index on `entry` is gone.
- **`LedgerStore`** saves accounts and every entry kind (expense with splits and shares, income, transfer, refund, friend-paid, settlement) in one transaction each, validated through the same `Ledger` rules, so the database cannot hold an entry the ledger would reject. It rebuilds a full `Ledger` with `loadLedger()`, keeps spend lines in step (all refunds of an expense are re-allocated together when an expense or refund changes, is deleted or restored), and appends a sync-log operation only for fields that changed, in strictly increasing HLC order. Deleting an expense that has refunds is refused; restoring a refund that no longer fits is refused. Currency decimal places are recorded on first use in a `currency` table, so no hard-coded list.
- **Tests:** 40 random histories (back-dated refunds, deletes, shares, splits, friend-paid, settlements) check that the SQL reports, the reloaded ledger and an in-memory reference ledger agree over several date windows and both bases. **Mutation checks:** I broke the code twice on purpose (no refund re-allocation on delete; none on expense edit). The first survived the original tests, which exposed a weak test; I added a deterministic case and made the random test delete any refund. Both mutations are now caught. Known gap: the random test does not edit expenses; that path is covered by one deterministic test.
- **Device:** a new instrumented test runs the store on the encrypted database on Android 8.0: balances, person balance, both spend bases, search and rollback after a rejected refund all match the in-memory reference.
- **Gate** updated for spend lines (the one-year window is now verified by counting spend lines: about 10,000). All limits still pass with the same margins (category totals 10 years: 49 ms vs 250 ms).
- Totals: 83 host/JVM tests, 18 device tests.

### Still open
- Not yet written: the thin layer the UI will call (use cases, flows for live updates); categories and people as named entities (only their ids are stored); the importer ordering rule is documented but not enforced.
- Real mid-range phone measurements to replace `PHONE_SLOWDOWN`; iOS needs full Xcode.
- Phase 0 exit criteria still to confirm: cold start on a reference device, and the spike list (SMS parser on 10 banks, Compose list with 100k rows).

## Update: SMS parser spike (2026-10-06)
**Question:** how much per-bank work does Phase 2 need, and what goes wrong without it?
**Method.** A new module `:core:parsers` holds a bank-independent extractor (`GenericAlertParser`, pure common Kotlin, body text only, refuses OTP messages first). I measured it against 152 public sample messages from 17 issuers (the MIT-licensed `akhilnarang/bank-sms-parser` fixtures: 144 transactions and 8 non-transactions). Its own parser, run in Python 3.14, served as the answer key. The corpus lives in `/tmp`, not in this repository; the measurement is an opt-in test (`CorpusReportTest`, set `SMS_CORPUS_DIR`). The permanent tests (11) use synthetic messages written for this project.

**Results, generic rules only, no per-bank rules:**

| Stage | Fully right (all five fields) | Recognised as a transaction |
|---|---|---|
| First version | 62/144 (43%) | 118/144 |
| After a round of cheap generic fixes | 77/144 (53%) | 128/144 |
| After a second round | 89/144 (62%) | 129/144 |

Final per-field agreement: direction 127, amount 129 (all recognised), date 128, reference 103, account 117 of 144; 7 of 8 non-transactions rejected. Remaining misses are mostly reference and account formats (alphanumeric NEFT/RTGS references, card payments) plus a few layouts with no direction word at all.

**Caveats, which make these numbers optimistic:** I tuned the rules on this same corpus; the fixtures are sanitised samples written by one contributor, not live traffic; the answer key is one library's output, not ground truth; sender-ID handling and OTP-adjacent messages were not tested.

**Findings that change the design:**
1. **Silent sign errors exist.** Of 129 recognised messages, 2 had the wrong direction: an outgoing RTGS worded "Money Deposited ... To NAME" read as a credit, and a credit-card bill payment ("bill ... has been paid") read as a debit. Guessing a sign corrupts the books without any visible failure, so the parser leaves a message with no direction word unrecognised instead of guessing (tested), and FR-CAP-12 stands: captured items go to a review inbox with a confidence level, never auto-confirmed for layouts the bank rules do not cover.
2. **Foreign-currency alerts carry two amounts** ("USD 12.00 | Rs. 1,000.00 spent ..."). A first version took the first one, 1,200 instead of 100,000 paise, and it was caught only because the answer key disagreed. The parser now returns the rupees charged plus the original currency and amount, which matches the ledger's `original` field.
3. **Card bill payments are not income or spending.** "Your credit card bill ... has been paid" is a transfer to the card; generic wording cannot tell, so the parser needs a message kind and the ledger's auto-pairing (FR-LED-03) must handle it.
4. **"Credited to the beneficiary account" means an outgoing payment** seen from the sender's bank. Handled generically for "beneficiary"; other outgoing phrasings need per-bank rules.
5. **Non-transaction notices are valuable, not just noise:** due-date and statement messages carry the data for FR-REC-03 (bill reminders), so they should be classified, not thrown away.

**Sizing for Phase 2 [INFERENCE from one public corpus]:** that corpus needed about 150 sample messages for 17 issuers, with HDFC at 31, IDFC at 22 and ICICI at 19. A launch target of the top 25 issuers therefore implies on the order of 200 to 300 message variants to write rules and fixtures for, after the generic layer. Rules should be data (patterns shipped over the air, per FR-CAP-13), with each rule carrying fixtures. A consented corpus from closed testers is needed because public samples are not live traffic.

Totals: 85 host/JVM tests across modules plus 11 parser tests on two targets.

### Still open for Phase 0 exit
- Cold start on a reference device and a Compose list with 100k rows (both need the real UI shell).
- iOS: full Xcode (spike a and d).

### Spike follow-up (2026-10-06)
- **Corpus is now in the repository** (`core/parsers/src/jvmTest/resources/corpus/`, 152 MIT-licensed samples, upstream commit `add4c02...` pinned, licence and provenance in `SOURCE.md`). `tools/sms-corpus/build_answer_key.py` reproduces `expected.tsv` byte for byte (checked). `CorpusReportTest` reads it from the classpath, so the measurement repeats on any machine and when per-bank rules arrive. Every figure from it is **synthetic, unvalidated**: sanitised samples from one contributor, scored against that contributor's own parser.
- **Generic parser tuning is closed.** Further fixes belong in per-bank rules with their own fixtures, because tuning on the same 152 samples it is scored on is partly overfitting (and one such tweak had already broken a foreign-currency amount, found by the answer key).
- **Confidence:** `ParsedAlert.confidence` is `GENERIC` for everything the generic parser returns; only a tested per-bank rule may reach `BANK_RULE`, the only level the review inbox may auto-confirm. The corpus test asserts the generic parser never claims `BANK_RULE`.
- **Confidently wrong vs unrecognised, now reported separately:** 129 recognised, of which 2 have a wrong direction (`hdfc/account_rtgs_deposited`, `slice/cc_bill_paid`) and 0 a wrong amount; 15 unrecognised (these only go to the review inbox). The test caps wrong results at 2 and wrong amounts at 0.
- **Variants per issuer (variants / fully right / recognised), generic rules only:** axis 5/5/5, bob 4/4/4, canara 3/1/3, equitas 3/2/3, hdfc 31/19/28, hsbc 4/4/4, icici 19/7/19, idfc 22/15/20, indian_bank 4/3/4, indusind 9/6/8, jupiter 3/0/1, kotak 8/6/8, onecard 7/6/6, revolut 2/2/2, sbi 6/3/4, slice 10/5/9, union 4/1/1. Where the generic layer falls short is mostly reference formats (alphanumeric NEFT/RTGS/IMPS ids, `RRN`, `UTR`) and account or card masks; the remaining unrecognised messages use no debit or credit word at all.
- **Real data option (not done):** the public samples cannot say how many real formats a bank sends today. If you want a real measurement, your own bank alerts can be exported from your phone with USB debugging (`adb shell content query --uri content://sms/inbox`), then redacted and kept out of git. That is your decision and your data; nothing has been read from your phone.

## Update: first app shell and the last Phase 0 measurements (2026-10-06)
**Built:** a transaction list (keyset paging, newest first), an add-expense screen (amount with up to 2 decimals, category, account, note; focus and numeric keyboard on open), a "spent this month" header, and a recovery screen for a lost database key that deletes nothing without an explicit second confirmation. The encrypted database opens on a background thread behind a spinner. Amounts use Indian grouping (`Money.formatted()`, `parseAmount()`, with property tests). A non-debuggable `benchmark` build type (R8, signed with the debug key) exists for honest measurement; a seeding hook (`--ei seed_rows N`) compiles in only for `debug` and `benchmark`, never `release`.

**Verified:** 2 UI tests drive the real screens on Android 8.0 (add an expense, see it in the list and month total as `₹12,34,567.50`; an amount with 3 decimals cannot be saved). Device tests now total 20, host tests 118 (both targets counted), all passing.

**Bugs found by measuring, fixed:**
1. **Cold start spent about 270 ms on password stretching.** SQLCipher stretches a passphrase with 256,000 PBKDF2 rounds on every open. Our key is already 256 random bits, so it now uses SQLCipher's raw-key form (`x'<64 hex>'`), which skips that step; the salt still lives in the file. Empty-database cold start to data on screen fell from about **405 ms to about 132 ms** (median of 10). Databases created before this change cannot be opened; none were shipped.
2. **Seeding raced the database opening** and silently did nothing (the screenshot showed an empty app). Seeding and "start fresh" now wait for the open to finish.

**Cold start, API 26 emulator on an Apple-silicon Mac, `benchmark` build, `am start -W` and `reportFullyDrawn` (first screen with data):**

| Database | Median | Range | Runs |
|---|---|---|---|
| Empty | 132 ms | 108-192 | 10 |
| 100,000 rows | 240 ms, 244 ms | 173-370 | 2 x 20 (a first run right after seeding was noisier: 261 ms, 171-580) |

The budget is 1.0 s on a mid-range phone, so this meets it **only if a phone is no more than about 4x slower than this emulator. That ratio is unmeasured.** The extra 110 ms with 100k rows over an empty database is not yet explained.

**List with 100,000 rows:** 40 flings scrolled far down the list and loaded further pages with no crash, and the screenshot shows correct rows and a correct month total. **Frame statistics are not usable here:** 98.7% "janky" frames, but 1,157 of 1,176 were slow GPU draw commands against 387 slow UI-thread frames, which points at this emulator's software rendering, not the app. Real scroll smoothness is **unmeasured** until a phone is tested.

**Phase 0 exit criteria status:**
- Ledger property tests: met.
- Save latency (p95 3.8-4.3 ms, limit 12.5 ms at the assumed 4x) and all query budgets: met on the emulator at the assumed factor.
- Cold start: met on the emulator at the assumed factor; **not verified on a reference phone.**
- Spike outcomes recorded (SMS parser, Compose list): done, apart from real-phone scroll smoothness.
- iOS spikes (SQLCipher driver, Compose Multiplatform startup): **not run**, need full Xcode.

**Known gaps in the shell, deliberately not built yet:** list rows show no category; no edit or delete; no accessibility pass (TalkBack labels, font scaling) and dark theme not checked on screen; quick-entry widget and tile (Phase 1); no navigation library (two screens only).
