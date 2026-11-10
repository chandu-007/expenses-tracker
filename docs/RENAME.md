# Rename checklist (not applied)

The app is still called **Expenses**. A new name has been considered (a first attempt, "Rupee Log", was made and withdrawn before release; nothing of it is in the repository). This page is the list to use when a name is chosen: what must never change, and what a rename touches by hand. `IdentifierGuardTest` (host: backup format and seal headers; device: ids, package, actions, widget receivers, channel ids, auto-copy names) fails if one of the identifiers below is changed by accident.

## What must NEVER change (an update or a restore would break)
| | Keep |
|---|---|
| Application id | `io.github.chandu007.expenses` (play, playSms), `app.expenses` (direct build, debug) |
| Kotlin packages and class names | `app.expenses...`, `ExpensesApp`, the widget receivers `QuickAddReceiver`, `QuickAddSmallReceiver`, `QuickAddWideReceiver`, `QuickAddLargeReceiver`, `TodayReceiver` (a placed widget is found by these names) |
| iPhone | bundle ids `app.expenses.tracker.ios`, `.share`, `.widget`; App Group `group.app.expenses.tracker.ios`; target, scheme and `Expenses.app` names (the tests' host path); widget kinds `ExpensesQuickAdd`, `ExpensesToday`; URL scheme `expenses://` |
| Data | database file names, `app_lock` preferences, Keystore / Keychain aliases (`expenses_secrets_wrap_key`, `expenses_db_wrap_key`), settings keys |
| Backups | the format line `"format":"expenses-backup"`, the `.expbak` extension, the headers `EXPENC1`, `EXPENC2`, `EXPENC3`, the key-derivation labels. Old backups restore by their contents, not their names |
| Android | intent actions `app.expenses.action.*`, the file provider authority `<applicationId>.files`, notification channel IDS `bills`, `recap`, `budgets` (a channel keeps the person's own sound and on/off setting by id), test tags, log tags (`ExpensesWidget`), the ADB package names in `tools/` |
| Automatic copies | their file names `expenses-backup-YYYY-MM-DD.expbak` and `expenses-YYYY-MM-DD.xlsx` are how earlier copies are found and pruned. If new names are ever used, the old ones must be counted and pruned together with the new ones (the newest 7 of both), and old backups of any name must still restore |

## What a rename touches by hand (the name people read)
- **Android:** `app_name` in `androidApp/src/main/res/values/strings.xml` (the launcher label and the label of every activity and receiver); the keyboard-shortcuts group in `MainActivity.onProvideKeyboardShortcuts`; the clipboard label in `AndroidPlatform`; the widget caption for unreadable data (`WidgetUi.caption`); the widget names stay "Spending: ...".
- **iPhone:** `CFBundleDisplayName` and `CFBundleName` of the app, the share extension and the widget (`iosApp/project.yml` and the three Info.plist files), the Face ID reason, the Shortcuts category and descriptions (`Intents.swift`), the Share sheet confirmation, the iPhone widget text. Siri and Shortcuts phrases use the app's name automatically.
- **In the app:** the text-permission disclosure ("Allow Expenses to read your bank texts?"), the iPhone Shortcuts steps, the capture messages "Captured, review it in Expenses", "Expenses could not open its data", the feedback e-mail subjects (`FeedbackMail`), and the tests that assert them (`FeedbackMailTest`, `FeedbackUiTest`, `CaptureTests.swift`, `CaptureUITests.swift`).
- **Files made new:** the default names in `DataScreen` (`expenses-backup-<date>.expbak`, `expenses-<date>.xlsx/.csv`) and `AutoExportKeys` (see the table above).
- **Docs people read:** `docs/PRIVACY_POLICY.md`, `docs/site/privacy.html`, `docs/PLAY_SMS_DECLARATION.md`, `docs/PLAY_LISTING.md`, `README.md`, `docs/index.html`, `docs/IOS.md`, `docs/REAL_DEVICE_CHECKLIST.md`; the release file names in `tools/ios_build.sh` and `tools/ios_refresh.sh`; `tools/release_smoke.py` (it finds the app in the launcher's widget picker by its name).
- Judge every hit of "Expenses": the word meaning spending ("Expenses with no category", the Expenses/Income category kinds) stays.

## What has to be done by hand outside the repository
1. **Play Console, Store listing:** the app name, the short and full descriptions, and every screenshot and the feature graphic that shows the old name or launcher label.
2. **App content / Data safety / SMS declaration:** paste the updated text if the name appears there.
3. **Privacy policy:** the page title and first paragraph; publish the page again (the URL need not change).
4. **Trademark and name availability:** not checked by us; search the Play Store, the App Store and the trade-mark registers first.
5. **Repository:** the GitHub repository name and description.
6. **iPhone / App Store Connect:** only the display name changes; the bundle id stays, so an install over the existing app keeps its data.
7. **The icon** is a separate piece of work.
8. After the change: install over the previous release on a phone and check that the data, the placed widgets and the notification channel settings are still there (`tools/release_smoke.sh` and an `adb install -r` of the old APK over the new one is the quickest way).
