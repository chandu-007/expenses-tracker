# Release readiness: next Google Play release (proposed 0.15.0, versionCode 44, iPhone build 41)

Written 2026-11-21 against `origin/main` `fd2bb04` and branch `origin/s7-wip` `4dbc043` (Slice 120, not merged). Read-only review: no code was changed, nothing was built or run except reading the existing 0.14.0 artifacts in `~/Documents/personal/expenses_builds/` with `aapt2`/`unzip`/`security cms` and fetching the hosted pages with `curl`. Every claim names its evidence as `file:line`. `[INFERENCE]` marks reasoning that was not observed.

Where things stand: production is 0.10.6 (code 39) (`docs/PHASE2_PROGRESS.md:1142`), open testing has 0.13.0 (code 42, the `playSms` AAB, `docs/PHASE2_PROGRESS.md:1403`), 0.14.0 (code 43) was built and never uploaded (`docs/PHASE2_PROGRESS.md:1494`, `:1500`). Since then: Slices 115 to 119 (UI S2 to S6), the Verify page "Free to use" change, the `node_id`/`db_key.bin` durability fix and the pre-release gate are on `main` (`docs/PHASE2_PROGRESS.md:1510-1766`); Slice 120 (UI S7) is on `origin/s7-wip` and **not merged** because its full API 26 suite did not finish (`origin/s7-wip:docs/PHASE2_PROGRESS.md:1791`).

---

## Top findings that need action or an owner decision

1. **The privacy policy has gone out of date and its "Effective" date was never moved.** The page text changed on 2026-11-03 and 2026-11-11 (`git log -- docs/site/privacy.html`: `ead5a7b`, `7ee6a23`, `61be781`, `6c80088`) but still says "Effective 24 October 2026" (`docs/PRIVACY_POLICY.md:3`, `docs/site/privacy.html:3`), while its own promise is "I will update this page and the date above" (`docs/PRIVACY_POLICY.md:33`). It also leaves out bill photos and the camera, ML Kit reading photos on the phone, the feedback and "report a message" emails, the skipped-sender names it keeps, iPhone device backups, Android widgets, and Travel mode/milestones data (section 1.3). **Action:** update both files, then push to `main` (the hosted page updates by itself, section 1.4).
2. **The feedback and "report a message" emails send data to the developer when the person taps Send** (app version, system, device model, and a redacted bank message; `core/domain/src/commonMain/kotlin/app/expenses/core/domain/FeedbackMail.kt:8,15-16,26-27`). The policy says "I collect nothing about you" (`docs/PRIVACY_POLICY.md:5`) and does not mention these emails. **Owner decision:** in the Data safety form, answer "No data collected" (recommended: the app sends nothing, and the person's own mail app sends a message they wrote and can read first) or take the cautious route and declare it (section 2.1). Either way the policy has to say it.
3. **Email and payment-app notification words are read in memory before the on/off switch is checked.** `NotificationCaptureService.kt:40` builds the words of a payment-app notification and `:58-61` the email notes, and only after that `:44` and `:65` check the switch. So with Notification access given for one source and that source's switch off, the text is read and then thrown away. The text is never kept, but the class comment (`:12-15`), the policy (`docs/PRIVACY_POLICY.md:16`), the in-app permission text (`androidApp/src/main/kotlin/app/expenses/PermissionNotes.kt:34`) and the Play justification (`docs/PLAY_LISTING.md:34`) all say these apps are read only while the switch is on. `SmsReceiver.kt:20` vs `:26` does the same for texts once the permission is already granted. **Action (code owner):** check the switch before reading the extras or the message body. If the code stays as it is, soften the wording.
4. **Slice 120 (S7) is not merged, and no full device suite has run on any release candidate since S6.** S7's full API 26 suite stopped after 4 of 7 chunks (`origin/s7-wip:docs/PHASE2_PROGRESS.md:1791`). The pre-release gate ran neither full suite (`docs/PHASE2_PROGRESS.md:1759`). Notification channel settings across an upgrade have never been checked (`:1506`, `:1766`). **Owner decision:** release with or without S7 (and S8/S9). Then run both full suites on the final commit, after the version bump (`docs/RELEASE_CHECKLIST.md:51-52`).
5. **Several documents still assume a paid tier or an earlier release, and the iPhone signing runs out in about two days.** `docs/PRODUCT_CONTEXT.md:19,49-50,326,332-343,385,388` describe Premium, lifetime pricing and an entitlements backend, but the owner says the app is free and the Verify page says so (`shared/src/commonMain/kotlin/app/expenses/ui/VerifyScreen.kt:78`, `docs/PHASE2_PROGRESS.md:1729`). Section 4 has replacement wording for the owner to decide. The store listing draft predates Slices 103 to 120 and leaves out SMS capture, which the SMS declaration requires the listing to state (`docs/PLAY_SMS_DECLARATION.md:28-31` vs `docs/PLAY_LISTING.md:8-19`). The newest iPhone dev profile expires **2026-11-23 17:20:26 UTC = 22:50 IST** (read from `embedded.mobileprovision` in `Expenses-iOS-0.14.0-s6-test.ipa`). `docs/IOS.md:103` still records the older 2026-11-22 15:07 UTC expiry of the 0.14.0 IPA.

---

## 1. Privacy policy vs what the app does

### 1.1 What the shipped Android app declares (Play `playSms` build)

Manifest sources: `androidApp/src/main/AndroidManifest.xml` (all builds), `androidApp/src/play/AndroidManifest.xml` (the no-SMS `play` build only). `androidApp/src/playSms/` has no manifest, just `res/xml/shortcuts.xml`, so `playSms` uses the main manifest unchanged (`androidApp/build.gradle.kts:60-67`). Application id `io.github.chandu007.expenses` for `play`/`playSms` (`androidApp/build.gradle.kts:131-132`), `app.expenses` for the direct APK (`:15`).

| Item | Evidence | In the policy? |
|---|---|---|
| `POST_NOTIFICATIONS` | `main/AndroidManifest.xml:4` | Yes (`PRIVACY_POLICY.md:24` "Notifications (reminders)") |
| `RECEIVE_SMS` (the `play` build removes it) | `main/AndroidManifest.xml:6`; `play/AndroidManifest.xml:7,10` | Yes (`:14`, `:24`) |
| `INTERNET` removed on purpose (ML Kit's datatransport adds it) | `main/AndroidManifest.xml:7-10`, `:160` | Yes (`:11`) |
| Merged-in by libraries: `USE_BIOMETRIC`, `USE_FINGERPRINT`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, and the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `aapt2 dump permissions Expenses-0.14.0-release.apk` (run for this review); the same strings are in the 0.14.0 `playSms` AAB's `base/manifest/AndroidManifest.xml`; the in-app list `PermissionNotes.kt:14-23` | Partly. `:24` covers biometrics, "start after restart" and "short background work". **`ACCESS_NETWORK_STATE` is not named** (harmless: `PermissionNotes.kt:20` explains it). |
| `<queries>` for `IMAGE_CAPTURE` and `mailto:` | `main/AndroidManifest.xml:14-21` | **No.** Neither the camera hand-off nor Send feedback is mentioned |
| `ACTION_SEND text/plain` filter (Share to "Check a message") | `main/AndroidManifest.xml:49-53` | Yes (`:17`) |
| `allowBackup=false` + data extraction rules that exclude database, prefs and files from cloud backup and device transfer | `main/AndroidManifest.xml:28-30`; `res/xml/data_extraction_rules.xml:8-19`; `res/xml/backup_rules.xml:3-6` | **No.** A good fact to state: Android never copies the data to Google's backup |
| FileProvider `${applicationId}.files`: paths `attachments/`, cache `camera/`, cache `backups/` | `main/AndroidManifest.xml:59-65`; `res/xml/file_paths.xml:3-5` | **No** (attachments and camera not mentioned) |
| `SmsReceiver` (`BROADCAST_SMS`-protected) | `main/AndroidManifest.xml:67-74`; `SmsReceiver.kt` | Yes |
| `NoticeActionReceiver` (not exported; "Mark paid" from a notice) | `main/AndroidManifest.xml:76` | Not needed |
| `NotificationCaptureService` (`BIND_NOTIFICATION_LISTENER_SERVICE`) | `main/AndroidManifest.xml:78-86`; package lists `core/parsers/.../AppSources.kt:9-14` (Google Pay, PhonePe, Paytm, BHIM), `:21-25` (Gmail, Outlook, Yahoo Mail) | Yes (`:15-16`). The lists match the code. The switch order is in finding 3 |
| Quick Settings tile `QuickAddTileService` | `main/AndroidManifest.xml:88-97` | No (it holds no data; not needed) |
| Five home-screen widgets (Quick add 4x2, Today, small, wide, large) | `main/AndroidManifest.xml:99-157` | **No for Android.** The policy mentions only the iPhone's Today widget (`:18`). Android widgets show spending totals on the home screen (masked with the eye or Hide amounts) and keep their state in Glance preferences (`widget/TodayWidget.kt:57`) |
| Camera: no `CAMERA` permission; the system camera app via `TakePicture` | `shared/src/androidMain/.../ui/PlatformHooks.android.kt:110,116` | **No** (bill photos are not in the policy) |
| Files: Storage Access Framework only (`OpenDocument`, `OpenDocumentTree`), no media permission | `PlatformHooks.android.kt:97,99` | Yes ("Files you choose", `:21`) |
| On-device text recognition (ML Kit, bundled model) for statement and bill photos; PDF and Excel statement reading | `gradle/libs.versions.toml:75-76`; `androidApp/src/main/kotlin/app/expenses/OcrStatementReader.kt`, `PdfStatementReader.kt`, `XlsxReader.kt` | **No.** "Contains no third-party software that collects data" (`:11`) is true (datatransport and its backend are removed: `main/AndroidManifest.xml:10,160`), but naming ML Kit and saying it works offline would be more transparent |
| Haptics (S7): `View.performHapticFeedback` only, no vibrator, no permission | `origin/s7-wip:shared/src/androidMain/kotlin/app/expenses/ui/PlatformHooks.android.kt:33` | Not needed |
| S7 "Simplify" slow-frame watch: stores nothing, logs nothing | `origin/s7-wip:docs/PHASE2_PROGRESS.md:1796` | Not needed |
| S7 manifest/dependency changes | `git diff origin/main...origin/s7-wip -- '*AndroidManifest.xml' gradle/libs.versions.toml androidApp/build.gradle.kts iosApp/project.yml iosApp/Resources/*` is empty | Nothing new |

Not present anywhere in the merged manifest (checked in the 0.14.0 APK and AAB): `CAMERA`, `READ_SMS`, `READ_MEDIA_*`, `READ_EXTERNAL_STORAGE`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `QUERY_ALL_PACKAGES`, `ACCESS_*_LOCATION`, `com.google.android.gms.permission.AD_ID`, and any `FOREGROUND_SERVICE_<TYPE>` permission. No code calls `setForeground`/`ForegroundInfo` (repo-wide search in `androidApp/src/main` and `shared/src/androidMain`).

### 1.2 What the iPhone app declares

| Item | Evidence | In the policy? |
|---|---|---|
| App Group `group.app.expenses.tracker.ios` (app, widget, share extension) | `iosApp/project.yml:24,65,88`; `iosApp/Resources/Expenses.entitlements:5-8`; `IosWidget.kt:41,44` | Yes (`PRIVACY_POLICY.md:18`) |
| `NSCameraUsageDescription` "Take a photo of a bill to keep it with an expense. The photo stays on this iPhone." | `iosApp/project.yml:49`; `iosApp/Resources/Info.plist:40-41` | **No** |
| `NSFaceIDUsageDescription` | `project.yml:48` | Yes (`:20`) |
| Background fetch for reminders (`UIBackgroundModes: [fetch]`, `BGTaskSchedulerPermittedIdentifiers`) | `project.yml:45-46`; `Info.plist:46-49` | Partly (`:19` "Reminders") |
| URL scheme `expenses`, App Intents (Shortcuts: capture a bank message or email, add an expense) | `project.yml:47`; `iosApp/Sources/Intents.swift:8,33,61` | Yes (`:18`) |
| Privacy manifest: no tracking, no collected data types, required-reason APIs UserDefaults (CA92.1, 1C8F.1), FileTimestamp (C617.1), SystemBootTime (35F9.1) | `iosApp/Resources/PrivacyInfo.xcprivacy:5-6,9-10,11-40` | Consistent with "collect nothing" |
| Database and attachments in Application Support under `NSFileProtectionCompleteUntilFirstUserAuthentication`. **Not excluded from iCloud or Finder device backups** (no `NSURLIsExcludedFromBackupKey` anywhere) | `shared/src/iosMain/kotlin/app/expenses/IosPlatform.kt:70,75,150`; `docs/IOS_DB_ENCRYPTION.md` section 1.1 | **No.** "Everything you enter stays on your phone" (`:5`) is not quite true on iPhone: the person's own iCloud/Finder device backup copies the database and photos |
| No networking code in the app (no URLSession/NSURLSession/Ktor client in `iosApp`, `shared/src/iosMain`, `shared/src/commonMain`; Ktor appears only as `ktor-server-*` for `backend/`) | repo search; `gradle/libs.versions.toml:46-47,69-71` | Consistent |

### 1.3 Mismatches and missing disclosures (proposed fixes; nothing here was edited)

| # | Policy says (`docs/PRIVACY_POLICY.md`, same text in `docs/site/privacy.html`) | What the code does | Proposed change |
|---|---|---|---|
| P1 | `:3` / html `:3` "Effective 24 October 2026" | The text changed 2026-11-03 and 2026-11-11 (`git log -- docs/site/privacy.html`) | Set the date of the new version. Keep `:33`'s promise |
| P2 | `:5` "I collect nothing about you"; `:11` "Nothing" | Send feedback opens the mail app filled with app version, system and device model (`FeedbackMail.kt:13-16`). "Report a message" fills in the redacted bank message the person has just seen (`FeedbackMail.kt:22-27`; redaction `core/parsers/.../MessageReport.kt`, gap fixed per `docs/PHASE2_PROGRESS.md:903`). It goes only if the person sends it (`AndroidPlatform.kt:150-153`) | Add a section "If you email me": what the prefilled mail contains, that nothing is sent unless they press Send in their own mail app, that I use it only to answer and fix, and that they can ask me to delete it. Change `:5` to "...I collect nothing about you unless you choose to email me" |
| P3 | `:14` "Texts from people, one-time passwords and unknown senders are discarded without being kept; only a count is recorded" | For a skipped sender the app also keeps the sender header's root, a count, the last time and the suffix letters (at most 20 roots, setting `skipped_sender:<ROOT>`), never words or digits (`docs/PHASE2_PROGRESS.md:899`, re-checked at `:947`). The heartbeat chip stores one timestamp of the last bank text (`:1674`) | Say: "only the sender's name (for example AX-HDFCBK), how many texts and when the last came are kept, so you can tell the app a sender is your bank. No words or numbers from the text are kept" |
| P4 | `:15-16`: payment apps and email apps are read "if you turn it on" | Words are read in memory before the switch is checked (`NotificationCaptureService.kt:40` before `:44`; `:58-61` before `:65`). Nothing is kept when the switch is off | Fix the order in code (finding 3). Until then: "...when Notification access is on, a notification from these apps may be read on the phone and is dropped at once unless its switch is on" |
| P5 | Not mentioned | Bill and receipt photos and files attached to entries are kept in app-private storage (`AttachmentFiles.kt:11,17`; iPhone `IosPlatform.kt:150`). The camera opens through the system camera app (Android `PlatformHooks.android.kt:110`, no `CAMERA` permission) or the iPhone camera (`Info.plist:40-41`). Photos of statements and bills are read on the phone by ML Kit (Android, bundled model, `gradle/libs.versions.toml:75-76`) | Add "Photos and attachments": stored only on the phone, read on the phone, never uploaded; ML Kit's usage reporting is removed from the Android app |
| P6 | `:8` "stored in a database on your phone, encrypted on Android with a key kept in the phone's secure hardware (Android Keystore)" | The database is SQLCipher with a Keystore-wrapped key (`core/storage/build.gradle.kts:31`; `AppLock.kt:15`). **Attachments are plain files** in app-private storage (`AttachmentFiles.kt:11`), protected by Android's own file encryption, not by the app's key. On phones without hardware-backed Keystore the key is in software `[INFERENCE: Android behaviour]` | "...encrypted with a key protected by the Android Keystore (in secure hardware where the phone has it); attached photos are kept in the app's private storage, which Android encrypts" |
| P7 | `:5` "everything you enter stays on your phone" | Android: no cloud backup, no device transfer (`data_extraction_rules.xml:8-19`, `AndroidManifest.xml:28`). iPhone: no backup exclusion, so the person's iCloud/Finder device backup includes the data (`IosPlatform.kt:70,75`) | Add "Backups by your phone": Android never copies the data to Google backup; on iPhone your own iCloud or computer backup includes it, as for any app |
| P8 | `:18` mentions only the iPhone Today widget | Android has 5 widgets showing totals and quick-add chips on the home screen (`AndroidManifest.xml:99-157`), with state in Glance preferences (`widget/TodayWidget.kt:57`). Notifications leave amounts out while Hide amounts or App lock is on (`docs/research/OUR_FEATURES.md:357`, Slice 113 F-01) | One line: "Widgets you place show figures on your home screen; Hide amounts masks them. Reminders leave amounts out when Hide amounts or the App lock is on" |
| P9 | `:24` Permissions list | Leaves out `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` (both added by WorkManager; `PermissionNotes.kt:20,22`) | List them: "see whether there is a connection, and run a short task (both added by a system library; the app still cannot use the internet)" |
| P10 | `:8` "What the app stores" | New kinds of data since 24 Oct: trips (`docs/PHASE2_PROGRESS.md:1308`), merchant aliases (Slice 108), milestones and Pause nudges settings (`:1676`), tips seen (`:1671`), backup status (`:1678`). All stay on the phone; milestones are cards, not push notices (`:1676`); Travel mode uses entry dates, not location (`:1308`; no location permission, section 1.1) | Add "trips" and "your settings and the app's own counters (for example when you last backed up)" to the list. State that the app never uses location |
| P11 | `:11` "contains no third-party software that collects data" | True. ML Kit's datatransport is removed (`AndroidManifest.xml:7-10,160`) and the Verify page proves no INTERNET at run time (`docs/PHASE2_PROGRESS.md:1677`) | Optional: name the libraries (ML Kit text recognition, PdfBox, SQLCipher, AndroidX) and point to "Verify it yourself" (More) |
| P12 | Not mentioned | The app is free, with no ads, subscriptions, in-app purchases or account (`VerifyScreen.kt:78`; no billing or ads library, `docs/PHASE2_PROGRESS.md:1729`) | Optional sentence under "What I collect and share" |
| P13 | `docs/PLAY_SMS_DECLARATION.md:20` "only a count is kept (no words, no number)" | Same as P3 (the sender header root is kept) | When P3 changes, keep the declaration's data-handling text in step. Do not re-submit unless Play asks; the use itself has not changed |

Checked and true today: the four payment apps and three email apps (`AppSources.kt:9-14,21-25`); clipboard read only on Paste and shared words not saved (`docs/PHASE2_PROGRESS.md:1213,1285`); no inbox reading (no `READ_SMS`, `:1285`); Start fresh exists (`LedgerViewModel.kt:946`); App lock via the system (`PermissionNotes.kt:17`); passphrase backups (`AutoExport.kt:106`).

### 1.4 How `docs/site/` is published

- **GitHub Pages, "deploy from a branch", `main`, folder `/docs`.** Evidence: the repository is public (`https://api.github.com/repos/chandu-007/expenses-tracker`: `"private": false`, `"has_pages": true`, `"default_branch": "main"`). `https://chandu-007.github.io/expenses-tracker/` serves `docs/index.html` (which links `site/privacy.html`, `docs/index.html:4`). `https://chandu-007.github.io/expenses-tracker/site/privacy.html` is **byte-identical** to `docs/site/privacy.html` at `fd2bb04` (`diff` clean, 2026-11-21). Its `last-modified: Thu, 19 Nov 2026 14:03:38 GMT` is the time of the last push (`fd2bb04`, 22:03 +0800), so every push to `main` redeploys.
- There is no `.github/workflows/` directory and no `CNAME` file in the repo, so this is the classic branch deploy on the default `github.io` domain.
- **Nothing has to be uploaded by hand.** Edit `docs/site/privacy.html` (and `docs/PRIVACY_POLICY.md`, the same text), push to `main`, then check with `curl -s https://chandu-007.github.io/expenses-tracker/site/privacy.html | diff - docs/site/privacy.html`. **Needs owner:** confirm which URL is entered in Play Console > App content > Privacy policy. The repo does not record it (`docs/PLAY_LISTING.md:20` says "yours").

---

## 2. Google Play Data safety and permission declarations

### 2.1 Data safety form (proposed answers)

Google's rule: data that is processed only on the device and never sent off it is not "collected" (Play Console Help, "Provide information for Google Play's Data safety section", https://support.google.com/googleplay/android-developer/answer/10787469).

| Question | Proposed answer | Why (evidence) |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | No INTERNET permission in the shipped build (`AndroidManifest.xml:10`; 0.14.0 APK/AAB permission dump, section 1.1). SMS, notifications, emails, photos and statements are read on the device (`SmsReceiver.kt`, `NotificationCaptureService.kt`, `OcrStatementReader.kt`). No analytics, ads or crash SDK (`gradle/libs.versions.toml`; `docs/PHASE2_PROGRESS.md:1729`) |
| (If the form still shows them) Is all user data encrypted in transit? | Not applicable: nothing is transmitted | as above |
| Do you provide a way for users to request that their data is deleted? | Data is only on the device: delete entries, **Start fresh**, or uninstall | `LedgerViewModel.kt:946`; `PRIVACY_POLICY.md:27` |
| Data shared with third parties | None | as above |
| Account creation | The app has no accounts | `VerifyScreen.kt:78` |
| **Owner decision: Send feedback / Report a message emails** | Recommended: still **No**. The app opens the person's mail app with a draft (`AndroidPlatform.kt:150-153`); the person's mail app sends what they chose to send, and the app transmits nothing. **Cautious alternative:** declare "Personal info > Email address" and "App info and performance > Other app performance data / Diagnostics (device model, version)" as collected, optional, purpose "Developer communications", not shared, encrypted in transit (by the mail service), deletable on request by email. Either way, disclose it in the policy (P2) | `FeedbackMail.kt:3-27` |

`docs/PLAY_LISTING.md:27-31` already lists the same "No" answers. Keep it, and add the feedback-email note there when the owner decides.

### 2.2 Permission declarations

| Permission or API | In the `playSms` AAB? | Does Play require a declaration? | Status |
|---|---|---|---|
| `RECEIVE_SMS` | Yes (`main/AndroidManifest.xml:6`; 0.14.0 AAB strings) | **Yes**, the SMS and Call Log permissions form ("SMS-based money management" exception) | **Accepted**: `docs/PHASE2_PROGRESS.md:898` ("The Play SMS declaration was accepted on the promise that only texts from listed or trusted senders are read"), and the open-testing release is the `play-sms` AAB (`:1403`). The use has not changed since. Do not add `READ_SMS` or any other SMS permission. Keep the prominent disclosure unchanged (`docs/PLAY_SMS_DECLARATION.md:25-26`). If the Console shows the form again for the new bundle, re-submit the same text |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Yes (`:78-86`) | **No Console form exists** for it. It falls under the general sensitive-permissions/user-data policy: core feature, prominent disclosure, and data used only for that feature. Google may ask during review | Answer text ready in `docs/PLAY_LISTING.md:34`. Its claim "only after a second, separate opt-in switch" needs finding 3 fixed to be literally true |
| Camera | No `CAMERA` permission (system camera via `TakePicture`, `PlatformHooks.android.kt:110`) | No | Nothing to do |
| Photos and videos (`READ_MEDIA_IMAGES`/`VIDEO`) | Not requested; Storage Access Framework only (`PlatformHooks.android.kt:97,99`) | No (the Photo and video permissions declaration applies only when those permissions are requested) | Nothing to do |
| Foreground service | Only the plain `FOREGROUND_SERVICE` from WorkManager's `SystemForegroundService` (AAB strings). No `FOREGROUND_SERVICE_<TYPE>` permission; the app never calls `setForeground` | No `[INFERENCE]`: Play's foreground service declaration covers FGS types for apps targeting API 34+ (`targetSdk = 36`, `androidApp/build.gradle.kts:17`), and none is declared | If the Console asks anyway, answer "the app does not use foreground services" |
| Exact alarms (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) | No | No | Reminders use WorkManager (`NotificationWork.kt:6,14`) |
| Full-screen intent | No | No | — |
| `QUERY_ALL_PACKAGES` | No (`<queries>` for two intents, `:14-21`) | No | — |
| Advertising ID | No `AD_ID` permission | **Yes, a yes/no question** (App content > Advertising ID): answer **No** | Check the answer is still "No" |
| Accessibility service, location, contacts, VPN | None | No | — |
| Financial features declaration | — | Yes (App content) | `docs/PLAY_LISTING.md:25`: "no financial features" (a tracker that does not move money) |

---

## 3. Store listing text: what the repo has and what is outdated

The only listing text in the repo is `docs/PLAY_LISTING.md:5-21` (title, short and full description, all "draft") plus the add-on paragraph in `docs/PLAY_SMS_DECLARATION.md:28-31`. There is no `fastlane/` folder, no `metadata/`, and no release notes or "What's new" file. **Needs owner:** the live Play Console text is not in the repo; compare it with the points below.

Outdated or missing, given Slices 103 to 120:
1. **SMS capture is not in the full description** (`PLAY_LISTING.md:16` mentions only payment-app notifications), yet the uploaded build is `playSms` (`docs/RELEASE_CHECKLIST.md:59-62`) and `docs/PLAY_SMS_DECLARATION.md:28-31` says Google wants the feature in the description. Also missing: bank alert emails (Gmail/Outlook/Yahoo).
2. `PLAY_LISTING.md:10` "Add an expense in seconds..." leaves out the S2 keypad that does sums, the amount-first form and the entry detail (`docs/research/OUR_FEATURES.md:368-369`).
3. `:11` "Accounts shown as cards" has been replaced by Home Option B's small account tiles and "owed · due in N d" (`docs/PHASE2_PROGRESS.md:1356`).
4. `:12` Budgets: say pace ("On pace / Ahead of pace"), the Budget ring and the daily figure to month end or payday (`OUR_FEATURES.md:390-393`).
5. `:14` Reports: the S4 charts (drag to read, Show as table, one-sentence summary; `OUR_FEATURES.md:382-386`).
6. Not mentioned at all: Travel mode (`docs/PHASE2_PROGRESS.md:1304`), category icons and colours (`OUR_FEATURES.md:375`), fold and tablet two panes (Slices 103, 111), "Why isn't capture working?" and guided setup (Slice 107), Fix this balance (Slice 105), statement import from PDF/Excel/photo, Verify it yourself (`OUR_FEATURES.md:406`), milestones off by default (`:405`), widgets (4 sizes plus Today), and "free: no ads, no subscriptions, no in-app purchases".
7. **Screenshots and the feature graphic**: `PLAY_LISTING.md:21` asks for them, and every UI slice from S0 to S7 changed Home, Add, Reports, Budget and first run. Any screenshot taken before 2026-11-14 is out of date. New ones exist only as test pictures (`~/Documents/personal/expenses_builds/ui_s2..ui_s7/after/`, invented data). **Needs owner** to pick or retake.
8. Release-process text in `PLAY_LISTING.md:3` ("upload `bundlePlay`") and `:41` (closed testing, 12 testers for 14 days) is stale: the checklist uploads only `playSms` (`docs/RELEASE_CHECKLIST.md:59-62`), and the app is already in production.

Proposed short description (72 characters, `printf %s ... | wc -m`): `Free, private expense tracker that reads your bank alerts on your phone.`

Proposed additions to the full description (owner to edit): "Reads your bank's text alerts (with your permission) to find payments and bills, on your phone only. Nothing is sent anywhere; every found item waits in To review until you approve it. Optional: payment-app notifications (Google Pay, PhonePe, Paytm, BHIM) and bank alert emails." (from `PLAY_SMS_DECLARATION.md:30`). Then one line each for: the keypad that does sums; Travel mode; charts you can drag or read as a table; budget pace; category icons and colours; two panes on foldables; Verify it yourself; free, with no ads, subscriptions or in-app purchases.

---

## 4. `docs/PRODUCT_CONTEXT.md`: paid Premium wording (not edited)

The owner says the app is free. The shipped Verify page already says "Expenses is free. There are no ads, no subscriptions and no in-app purchases, and you do not need an account." (`shared/src/commonMain/kotlin/app/expenses/ui/VerifyScreen.kt:78`; decision recorded at `docs/PHASE2_PROGRESS.md:1729`).

| Line | Current text (quoted) | Proposed replacement (**FOR OWNER DECISION**) |
|---|---|---|
| 19 | "...Local-first and instant, works without bank linking, India-native capture, fair pricing including a lifetime option, a stable UI." | "...Local-first and instant, works without bank linking, India-native capture, **free with no ads or in-app purchases**, a stable UI." |
| 49 | "4. **The user owns the data.** Free full export in open formats, account-based sync with version history, and a shutdown data guarantee. (PP-05, PP-09)" | "4. **The user owns the data.** Free full export in open formats, backups the user controls, and a shutdown data guarantee (the data never depended on a server). (PP-05, PP-09)" (sync stays a later, free option if it is ever built) |
| 50 | "5. **Earn trust explicitly.** No ads, no lending, no data sale, honest pricing, human support. (PP-02, PP-07, PP-08, PP-10)" | "5. **Earn trust explicitly.** No ads, no lending, no data sale, **no charges**, human support. (PP-02, PP-07, PP-08, PP-10)" |
| 326 | "**Backend (all stages):** Docker containers deployed in an India region (§6.3): sync relay, email-forwarding ingest, account/entitlements, Postgres. The app stays fully usable offline without it." | "**Backend (later, optional):** none is needed today; the shipped app has no internet permission. If sync or email forwarding is built, it would run in an India region (§6.3) with no entitlements or billing service. The app stays fully usable offline without it." |
| 332 | "These are derived from PP-02, PP-07, PP-08 and PP-09. The exact pricing is an open decision (§10)." | "These are derived from PP-02, PP-07, PP-08 and PP-09. **The app is free.**" |
| 335 | "2. A **generous free tier** that is a complete tracker: unlimited manual entries, accounts, categories, budgets, splits and groups, local storage, **full export**, dark mode." | "2. **Everything is free**: unlimited entries, accounts, categories, budgets, capture, reports, local storage, **full export** and backups." |
| 336 | "3. **Premium** (subscription **and** a fairly priced lifetime option) covers costs that scale or that we pay for: multi-device and household sync, automatic capture sources that need servers (email forwarding, bank/AA), cloud AI, receipt OCR in the cloud, advanced reports, web app." | "3. **No paid tier is planned.** Features that would need servers (sync, email forwarding, bank/AA, cloud AI) are not built; if any is ever considered, the owner decides first, and the app's privacy promise (no internet permission) changes only with a new version and a clear notice (`docs/PRIVACY_POLICY.md:33`)." |
| 337-342 | "4. **Billing honesty:** pricing shown before any setup / in-app cancellation / trial reminders 48 hours before charging / price changes announced 30+ days ahead, with existing lifetime licences honoured / entitlements restored on any device or platform from one account" | Delete. Alternatively: "4. **No billing.** The app has no payments, trials or licences." |
| 343 | "5. **Group fairness:** group participants never need Premium to view, add or settle." | "5. **Group fairness:** everyone can view, add and settle; nothing is behind a payment." |
| 385 | "| D7 | Pricing | Free + Premium subscription + lifetime; price points for IN vs. global | `research/reviews.md` business model | Validate willingness to pay; anchor below Fold Plus in India and below Monarch globally |" | "| D7 | Pricing | Free; Free + Premium; Free + lifetime | `research/reviews.md` business model | **Decided: free, with no ads, subscriptions or in-app purchases** (owner, 2026-11-18; `VerifyScreen.kt`) |" |
| 388 | "| D10 | Hosting / deployment | ... | Docker images (sync relay, email ingest, accounts/entitlements, Postgres) deployed in an India region. ..." | "...Not needed for the shipped app. If ever built: Docker images (sync relay, email ingest, Postgres) in an India region; **no accounts/entitlements service**. ..." |
| 397 | "| PP-02 Price / billing | §7, FR-SPL-05 |" | "| PP-02 Price / billing | §7 (free) |" |

---

## 5. Release notes drafts (Google Play "What's new")

The notes are for Android only. Counts are `printf %s "<text>" | wc -m` on the exact text (lines joined by newlines, no trailing newline), run on 2026-11-21. Play allows 500. These drafts stay at or under 480.

The S7 line (haptics, rolling numbers, Simplify) belongs in the notes **only if Slice 120 is merged** before the build (`origin/s7-wip:docs/PHASE2_PROGRESS.md:1791`). Remove it otherwise.

### (a) vs open testing 0.13.0 (Slices 113 to 120 plus the fixes since 0.13.0)

```
• Add: amount first; the keypad does sums
• Entry details with edit, copy and delete
• Category icons and colours; neutral spending colours
• Charts: drag to read, or view as a table
• Budget pace and a daily figure to payday
• Simpler first run; optional milestones
• Haptics switch, rolling numbers, Simplify for slow phones
• Fixes: bill photos on older Android; alerts obey Hide amounts
```
Count: **390**. Without the S7 line: 330.
Insert S8/S9 here, after the "Haptics" line, if they land:
```
• Refreshed home screen widgets
• Large screens, dark mode polish and a true-black theme
```
With both lines added: **479**.

Sources: S2 `docs/research/OUR_FEATURES.md:368-370`; S3 `:375-377`; S4 `:382-384`; S5 `:390-391`; S6 `:399-405`; S7 `origin/s7-wip:docs/PHASE2_PROGRESS.md:1774-1778`. "Bill photos on older Android" is the `List.removeLast` crash in `BillItems.readItem` on Android 14 and older, present in 0.13.0 and fixed in 0.14.0 (`docs/PHASE2_PROGRESS.md:1502`). "Alerts obey Hide amounts" is S0 F-01 (`:1411`; `OUR_FEATURES.md:357`).

### (b) vs production 0.10.6 (Slices 103 to 120)

```
• Travel mode: trips tag their own spending
• New Home, Search and Add; the keypad does sums
• To review: Expense, Income or Transfer
• Card bill payments recognised; fix a balance
• Capture checkup and guided setup
• Category icons and colours
• Charts you can drag or read as a table
• Two panes on foldables
• Haptics switch; Simplify for slow phones
• Fixes, incl. Hide amounts in alerts
```
Count: **391**. Without the S7 line: 348.
Insert S8/S9 here, after the "Haptics" line, if they land: the same two lines as in (a). With both: **480**.

Sources: Travel mode Slice 110 (`docs/PHASE2_PROGRESS.md:1304`); Home Option B Slice 112 (`:1354`); Search Slice 106 (`:1245`); Add S2 (`:1510`); To review type and card bill payments Slice 104 (`:1103`, `:1142`); Fix this balance Slice 105 (`:1222`); checkup and guided setup Slice 107 (`:1208-1215`); category look S3 (`:1540`); charts S4 (`:1586`); fold two panes Slices 103 and 111 (`:1078`, `:1329`); hidden amounts in alerts S0 (`:1411`).

---

## 6. Release checklist for this release

From `docs/RELEASE_CHECKLIST.md`, plus the items asked for. Status as of `fd2bb04` / `s7-wip` `4dbc043`. **Done** means done on a tree that may not be the final one, so steps marked "redo" must be repeated on the final commit.

| # | Step | Status | Evidence / what is left |
|---|---|---|---|
| 0 | **Owner: decide the scope** (S7 merged? wait for S8/S9?) | **Needs owner** | S7 not merged (`origin/s7-wip:docs/PHASE2_PROGRESS.md:1791`); S8/S9 not started (`docs/research/ui/UI_BACKLOG.md:56-57,489-503`) |
| 1 | Version bump: `versionCode 44`, `versionName "0.15.0"`; iPhone `MARKETING_VERSION "0.15.0"`, `CURRENT_PROJECT_VERSION "41"` | **Not done** | Now `androidApp/build.gradle.kts:18-19` = 43 / "0.14.0"; `iosApp/project.yml:12-13` = "0.14.0" / "40". Code 43 was never uploaded, so the rule "next unused code" (`docs/RELEASE_CHECKLIST.md:55-57`) would allow 43, but a final 0.14.0/43 AAB exists (`docs/PHASE2_PROGRESS.md:1494`). Use 44 so two different files never share a code. The iPhone extensions now follow the app's version (`docs/PHASE2_PROGRESS.md:1761`) |
| 2 | Host suites (`--rerun-tasks`), native guard, `tools/ios_build.sh unit` and `interop` on the final commit | **Redo** | Last on `f9d17da`: domain 900, parsers 221, storage 604; native 893/207; unit 41; interop ok (`docs/PHASE2_PROGRESS.md:1760`). S7: 918/221/604 (`origin/s7-wip:docs/PHASE2_PROGRESS.md:1787`) |
| 3 | **Full API 26 suite** (about 155 to 159 classes, cold boot per chunk) on the final commit after the bump | **Not done** | Last complete run: S6 tree `97cf40b`, 1,022 passed, 0 app failures (`docs/PHASE2_PROGRESS.md:1705-1718`). Gate: 14 classes only (`:1759`). S7: 4 of 7 chunks (`origin/s7-wip:...:1791`) |
| 4 | **Full API 34 suite** on the final commit | **Not done** | Gate: 21 classes + 4 tests, 166 passed (`docs/PHASE2_PROGRESS.md:1759`). There has never been a full API 34 run; earlier releases ran "affected classes" only (`:1490`). Known API 34 image issue: `AlertsUiTest` fails on base and S7 alike (`origin/s7-wip:...:1793`) |
| 5 | Upgrade tests to the release build: from rebuilt 0.10.6, from 0.13.0, from 0.14.0 (data, balances, trip, widget, `node_id`, `db_key.bin`) | **Done on `f9d17da`; redo on final** | All three PASS with byte-identical backup payloads (`docs/PHASE2_PROGRESS.md:1755-1757`). The S7 code has not been upgrade-tested |
| 6 | **Notification channel settings survive the upgrade** (a channel turned off or set to silent before the upgrade stays that way after it; channels `bills`, `recap`, `budgets`) | **Not done** | `androidApp/src/main/kotlin/app/expenses/Notifier.kt:24-26,28-33`; listed as not verified at `docs/PHASE2_PROGRESS.md:1506` and `:1766`. Check with `adb shell dumpsys notification` (channel importance) before and after `adb install -r` |
| 7 | Backup restore (0.10.6 and 0.13.0 backups into a fresh release install) | **Done on `f9d17da`; redo on final** | PASS, byte-identical (`docs/PHASE2_PROGRESS.md:1758`) |
| 8 | Release build, `tools/check_release_keeps.py`, `tools/release_smoke.sh` (API 34 14/14, API 26 7 + 7 skipped) | **Done on `f9d17da` and on S7; redo on final** | `docs/PHASE2_PROGRESS.md:1762`; S7 `origin/s7-wip:...:1794-1795` (the S7 edit to `check_release_keeps.py` must come with S7) |
| 9 | **AAB manifest diff** vs the 0.13.0 AAB (strings or `aapt2`): only the version should change; RECEIVE_SMS present; no INTERNET; no new permission | **Not done for the new AAB** | Done for 0.14.0 vs 0.13.0 (`docs/PHASE2_PROGRESS.md:1491`). Expected permission set: section 1.1. S7 changes no manifest (section 1.1) |
| 10 | Upload only `bundlePlaySms` (`androidApp-playSms.aab`, id `io.github.chandu007.expenses`) | Not done | `docs/RELEASE_CHECKLIST.md:59-62` |
| 11 | Real-phone checks (install over the previous version; add and search; read a real shop bill photo; backup and restore; `docs/REAL_DEVICE_CHECKLIST.md` for S2 to S7) | **Needs owner** | `docs/RELEASE_CHECKLIST.md:64-70`. Every UI slice lists "nothing on a phone" (for example `docs/PHASE2_PROGRESS.md:1693-1694`; S7 haptics never felt, `origin/s7-wip:...:1803`) |
| 12 | **iPhone signing renewal** before it expires **2026-11-23 17:20:26 UTC (22:50 IST)**; build 0.15.0 (41) and install | **Needs owner** (Xcode signed in, phone unlocked; back up the phone's data first) | Expiry read from `embedded.mobileprovision` in `Expenses-iOS-0.14.0-s6-test.ipa` on 2026-11-21. `docs/IOS.md:103` records the older 0.14.0 IPA's 2026-11-22T15:07Z. Steps: `docs/IOS.md:102-103`, `tools/ios_refresh.sh` (`docs/IOS.md:85`) |
| 13 | **Privacy policy update** (P1 to P12), new effective date, push to `main`, check the hosted page | **Not done** | Section 1.3, 1.4. Do this **before** the Play upload so the reviewed URL shows the new text (`docs/PLAY_SMS_DECLARATION.md:34`) |
| 14 | Data safety form re-check; Advertising ID "No"; SMS declaration still on file | **Needs owner** (Console) | Section 2 |
| 15 | Store listing text and screenshots | **Needs owner** | Section 3 |
| 16 | Play Console: **open testing first** (new release on the Open testing track with the code 44 `playSms` AAB, release notes (a)), wait for review, test on a phone from the Play install | Not done | `docs/RELEASE_CHECKLIST.md:59-62` (it still says "Closed testing first"; open testing is the track in use) |
| 17 | Play Console: **production with a staged rollout of about 20%** (promote the same release, notes (b)), watch Android vitals and reviews for 2 to 3 days, then raise to 50% and 100%; halt the rollout if crashes or ANRs rise | Not done | No vitals or crash reporting inside the app (no network), so Play's Android vitals is the only crash signal `[INFERENCE]` |
| 18 | After upload: raise `versionCode` to 45 in the commit that starts the next work; copy artifacts (`Expenses-0.15.0-play-sms-code44.aab`, release APK, IPA, md5) to `~/Documents/personal/expenses_builds/` | Not done | `docs/RELEASE_CHECKLIST.md:55-57`; pattern at `docs/PHASE2_PROGRESS.md:1494` |
| 19 | Docs: `docs/PLAY_LISTING.md:3,41` (stale upload and closed-test steps), `docs/RELEASE_CHECKLIST.md:62` ("Closed testing first"), `docs/IOS.md` (new expiry), `PRODUCT_CONTEXT` (section 4, owner) | Not done | — |
