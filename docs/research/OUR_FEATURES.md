# Our app: feature inventory (verified against code, version 0.7.1)

Scope: Kotlin Multiplatform app. Android (`androidApp`, minSdk 26, versionCode 29, Play and APK builds) and iPhone (`iosApp`, 0.7.1 build 26, iOS 17+, free-signed test build only). Shared UI and logic: `:shared` commonMain; `core/{domain,storage,parsers}`.

Maturity key (from the docs and the test folders; nothing here was run by me):
- **Emu** = exercised by Android instrumented tests on an API 26 emulator (`androidApp/src/androidTest`).
- **Sim** = exercised on the iOS simulator only (`iosApp/Tests`, `iosApp/UITests`).
- **Unit** = JVM/common unit tests only.
- **No device** = never run on a real phone. The docs (`REAL_DEVICE_CHECKLIST.md`, `IOS.md`) say real-device checks are mostly pending. The only real-device facts in the docs are the owner's iPhone install and real HDFC statement/message samples. Treat every row below as "not verified on a real device" unless it says otherwise.

"Both" = Android and iPhone share the code (commonMain), so the feature exists on both, but it is tested where the Maturity column says.

## 1. Entries

| Feature | What it does | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| Expense | Amount, date, account, category, payee/merchant, note | Both | `ui/EntryForm.kt`, `ledger/Model.kt` `Expense` | Emu, Sim | Amount typed on an on-screen pad; amount shown in words (`AmountWords.kt`); Indian grouping (`MoneyFormat.kt`) |
| Income | Income entry with its own (income) categories | Both | `Model.kt` `Income`, `CategoriesScreen.kt` (Expenses/Income/Both kinds) | Emu | PHASE2 doc line saying "income has no category" is stale (see Doc claims) |
| Transfer | Move between own accounts, with a different `toAmount` allowed (multi-currency) | Both | `Model.kt` `Transfer` | Emu | Card bill payment is a transfer to the card ("Pay now" opens one) |
| Lent / borrowed | "I gave / I got" with a person, per-person balance, settlement entries | Both | `ui/LoansScreen.kt`, `Model.kt` `Settlement`, `person` table | Emu, Sim (screen opened) | Free-text people; no contacts lookup (deliberate, no permission) |
| Refund | Entry linked to the original expense (`refundsEntry`), partial refunds tracked up to what remains; To review suggests the link for a credit that says refund/reversal | Both | `Model.kt` `Refund`, `EntryActions.kt:161-170`, `LedgerViewModel.kt:1818-1825` | Unit, Emu (`PairingUiTest`) | Text must carry a refund word; looks back 120 days; no "expecting this back" marker |
| Splits across categories | One expense split over several categories | Both | `EntryForm.kt` "Split", `entry_split` table | Emu | |
| Shares with people ("their share") | New expense split with friends: equally, my amounts, or "A friend paid"; "They paid; your share" views | Both | `EntryForm.kt:393-440`, `Model.kt` `Share`, `entry_share`, `LoansScreen.kt` | Emu (`SplitBillUiTest`) | Only when adding a new expense (not on edit); no group ledger; no invite, no UPI settle |
| Friend paid | Entry where a friend paid; only my share counts as spending | Both | `Model.kt` `FriendPaid` | Emu | |
| Foreign currency | Amount in another currency plus a rate YOU type; stored `original` and converted amount | Both | `ForeignAmount.kt`, `CurrencyScreen.kt` | Unit, Emu | No live rates (no internet). Rate up to 6 decimals |
| Tags | Many free-text tags per entry, case-insensitive, search/report by tag | Both | `TagRules.kt`, `entry_tag`, EntryForm "Tags" | Emu | Max per entry and max length enforced |
| Notes | Free-text note per entry | Both | `EntryForm.kt` | Emu | |
| Attachments / receipts | Attach a picture or file; take a photo (Android camera intent, iOS system camera) | Both | `AttachmentStore.kt`, `attachment` table, EntryForm "Attach"/"Take a photo" | Emu; iOS camera not testable in simulator | Stored on device; included in v2 backups |
| Read items from a bill photo | On-device text recognition (ML Kit on Android, Vision on iOS), lets you choose "one entry" or "each item as its own entry" | Both | `BillItems.kt`, `BillEntries.kt`, `ui/BillItemsUi.kt`, `OcrStatementReader.kt` | Emu (test fixture image), Sim (`BillPhotoUITests`); No device | Reads items and the printed grand total only; merchant, date and tax are not read into fields; quality on real printed bills not measured |
| Quick entries (templates) | Saved one-tap entries on Home and widgets | Both | `TemplateStore.kt`, `template` table | Emu, Sim | |
| Similar-entry detection | Offers to apply the same category/account to similar past entries ("Same as...") | Both | `SimilarEntries.kt`, `ui/SimilarDialog.kt`, `Duplicates.kt` | Emu, Sim | Undo bar |
| Duplicate detection on capture | Against the ledger: same reference, or same amount and direction within one day. Between pending items: a bank text and a payment-app or email alert for the same spend within 10 minutes (24 h with a shared reference); different last-4 digits or different references are two payments | Both | `Duplicates.kt`, `LedgerViewModel.kt:1776-1799` | Unit (`DuplicatesTest`), Emu (`PairingUiTest`) | "99% precision" target NOT measured (PHASE2); a note only, the person decides |
| Entry history / edit revisions | Per-entry history with "Undo this edit" | Both | `ui/HistoryScreen.kt`, `EntryHistory.kt` | Emu | |
| Where this came from | Shows the capture source text/origin of an entry | Both | `ui/OriginSection.kt`, `entry_origin` | Emu | |

## 2. Accounts and cards

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| Account types | BANK, CASH, WALLET, CREDIT_CARD, LOAN (liability flags) | Both | `Model.kt` `AccountType` | Emu | No investment/brokerage/property type |
| Opening balance, archive | Opening balance (negative for owed), archive hides from pickers | Both | `Account` | Emu | |
| Account cards (stacked look) | Cards with colour/look, network | Both | `ui/AccountTiles.kt`, `AccountLookStore.kt`, `account_look` | Emu | |
| Credit-card billing dates | Statement day, due day, minimum due terms | Both | `CardBilling.kt`, `CardStore.kt`, `card_terms` | Emu | |
| Credit limit and card summary | Spent this cycle, owed, limit used %, limit left, due, minimum | Both | `CardSummary.kt`, `ui/CardSummaryView.kt` | Unit, Emu | Limit is optional |
| Balances and net worth | Per-account balance from opening + entries; a "Net worth" figure on Accounts (sum of active balances, cards and loans negative) | Both | `ledger/Ledger.kt:50,82`, `AccountsScreen.kt:79-83` | Unit, Emu (`AccountsUiTest`) | No history, assets, loans-lent or goals in it; not on Home or Reports |
| Account filter | Home/Search "this account"; search inside an account | Both | `AccountsScreen.kt`, `SearchScreen.kt` | Emu | |
| Last-4 / mask matching | Captured messages matched to an account by `a/c XX1234` / `card ending 4321` | Both | `CaptureAccounts.kt`, `AccountRules.kt` | Unit | |
| Card notices | Statement ready, due, limit, cleared reminders | Both | `NotificationService`, `Notifications.kt` kinds `CARD_DUE/CARD_STATEMENT/CARD_LIMIT/CARD_CLEARED` | Unit | |

## 3. Categories

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| Default categories | Built-in catalog in groups ("Daily life" ...), India-flavoured (kirana, UPI-era) | Both | `CategoryCatalog.kt` | Unit | English names only |
| Subcategories | One level of parent/child, reports roll up to parent | Both | `Category.kt`, `CategoryStore.kt` | Emu | |
| Kinds | Expense / Income / Both | Both | `CategoriesScreen.kt` | Emu | |
| Category rules (capture) | Phrase to category rules; test against history | Both | `CaptureRuleStore.kt`, `capture_rule`, `DefaultRules.kt` | Emu | |
| Merchant map and starter list | Remembers payee to category (`merchant_map`); built-in starter list of 97 Indian merchant names in 7 categories (`DefaultRules.kt`) used when the person has no rule, no learned choice and no history | Both | `merchant_map` table, `CategorySuggest.kt:50-57`, `DefaultRules.kt` | Unit (`CategorySuggestTest`) | No VPA-handle or alias-to-name dictionary; starter list matches by category NAME, so renaming a category switches it off |
| Suggested categories | Screen proposing categories from your data | Both | `SuggestedCategoriesScreen.kt` | Emu | |
| Merge / delete | Merge into another; delete only if unused | Both | `CategoriesScreen.kt` | Emu | Merge not undoable |

## 4. Budgets and goals

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| Overall and per-category limits | `budget.scope` '' = overall, else category id; a category limit includes its subcategories | Both | `Schema.sq:52` `budget`, `BudgetStore.kt`, `BudgetMath.progress` | Emu (`BudgetUiTest.aCategoryLimitIsFlaggedOnTheListWhenItIsOver`) | One limit per scope; limit currency is one |
| Budget periods | Six in the UI: Monthly (start day 1..28), Weekly, Every 2 weeks, Twice a month, Custom length (N days), Financial year | Both | `BudgetPeriod.kt`, `BudgetScreen.kt:166-190`, `BudgetStore.kt:64-81` | Emu (`BudgetUiTest.theFinancialYearFortnightAndHalfMonthPeriods...`) | |
| Rollover | One switch "Carry over what is left": last period's unspent amount under each limit is added once | Both | `BudgetScreen.kt:156`, `BudgetStore.kt:100-110` | Emu | Does not pile up beyond one period; no per-category choice |
| Safe to spend | Overall limit left minus repeating expenses still due, divided by days left | Both | `BudgetMath.kt:74-89`, `BudgetScreen.kt:229-235`, Today widget | Emu | Needs an overall limit; card dues and found bills not subtracted |
| Budget alerts | Near, over, pace, wrap-up notifications | Both | `Notifications.kt` `BUDGET_*` | Unit | iOS Budgets screen not exercised on iOS |
| Goals | Target, saved by hand, or follows an account balance; due date, monthly amount needed | Both | `Goals.kt`, `GoalsScreen.kt` | Emu | Stored as a text setting, not a table |

## 5. Recurring, subscriptions, bills

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| Repeating items | Expense/income/transfer; day/week/month/year; ends never/date/count; amount may be "ask me" | Both | `Schedule.kt`, `Recurring.kt`, `RecurringStore.kt`, `RecurringScreen.kt` | Emu, Sim | |
| Post / skip | Post due item, skip (Undo), or auto-post | Both | `RecurringScreen.kt`, `recurring_done` | Emu | Auto-post runs when the app/worker runs |
| Subscription finder | Detects repeating payments from history (cadence, next date) | Both | `Subscriptions.kt`, `SubscriptionsScreen.kt` | Unit, Emu | |
| Bills from messages | Bill texts become "found" bills, then tracked; mark paid / not paid | Both | `BillParser.kt`, `BillStore.kt`, `BillsScreen.kt` | Emu, Sim | Parser tested on constructed texts and owner samples |
| Reminders | Bill due, card due, tracked bill, found bill | Both | `NotificationService.kt` | Unit | iOS: best effort, plus planned 7-day local notifications |
| Notification actions | Mark paid, Later today, Tomorrow, Pay now | Both | `NoticeActionReceiver.kt`, `IosNotifications.kt` | Emu, Sim | iOS snooze not re-checked at fire time |
| Weekly recap | Notification summary | Both | `WeeklyRecap.kt` | Unit | Notification only, no recap screen |

## 6. Reports, charts, calendar

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| Reports | Spent, income, income minus spending; by period/year/financial year; grouped by category, account, tag, merchant; drill-in; compare with last period | Both | `ReportsScreen.kt`, `ReportMath.kt`, `PeriodCompare.kt`, `ReportStore.kt` | Emu, Sim | |
| Charts | Category donut ring (`BreakdownRing`) and period bars (`TrendChart`), Home line/bar visuals | Both | `ReportCharts.kt:99,182`, `HomeVisuals.kt` | Emu | No Sankey, no custom chart builder, no export of charts |
| Calendar | Month calendar of spending | Both | `CalendarScreen.kt`, `CalendarMath.kt` | Emu, Sim | |
| Financial-year summary export | FY category rows to a file | Both | `FinancialYearExport.kt`, DataScreen | Unit, Emu | Not a tax computation (80C etc. is roadmap only) |
| Spend basis | "My share" vs cash out accounting | Both | `Model.kt` `SpendBasis`, `SpendLines.kt` | Unit | |

## 7. Search

| Feature | What | Plat | Where | Maturity |
|---|---|---|---|---|
| Text, amount range, category, tag, kind (expense/income), quick range, custom dates, account | Combined filters over full-text search (FTS) | Both | `SearchScreen.kt`, `Search.kt`, `SearchDates.kt`, `FtsTuning.kt` | Emu, Sim |
| Saved searches | Name and re-run | Both | `SavedSearchStore.kt`, `saved_search` | Emu |
| Bulk edit | Select many (Home and Search): delete, category, account, add/remove tag, with Undo | Both | `Bulk.kt` | Emu |
| More-menu search | Search settings and tools | Both | `MoreMenu.kt` | Emu |

## 8. Capture

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| SMS capture | Bank texts to a review inbox; sender allowlist; OTP/person/declined dropped | Android (not in the Play `bundlePlay` build; the SMS build is `bundlePlaySms`) | `SmsReceiver.kt`, `Capture.kt`, `CaptureClassifier.kt` | Emu; Doze/real carrier NOT tested | Needs RECEIVE_SMS. Owner reports the Play SMS declaration was accepted and testers use the SMS build; the acceptance is not visible in the repo |
| Payment-app notifications | Google Pay, PhonePe, Paytm, BHIM only | Android | `NotificationCaptureService.kt`, `AppSources.kt` | Emu; real notification wording partly unverified | Needs Notification access |
| Bank emails via notifications | Gmail, Outlook, Yahoo Mail notifications, second opt-in | Android | `AppSources.EMAIL_PACKAGES`, `classifyEmail` | Unit/Emu; code comment says the apps' real notification fields "were not checked" | Only sender name, subject, snippet |
| Shortcuts automation | App Intents "Capture bank message", "Capture bank email", "Add expense" + Siri phrases | iPhone | `iosApp/Sources/Intents.swift`, `IosCapture.kt`, `MessageIngest.kt` | Sim (`CaptureTests`, `CaptureUITests`); not on iOS 27 phone | Automation must be set up by the user; Unknown Senders may not trigger |
| Share extension | Share text/email into the app | iPhone | `iosApp/ShareExtension` | Sim; No device | |
| Paste | "Paste a bank message" with conservative rules | iPhone | `InboxScreen`, `PasteRules` | Sim | |
| Review inbox | Confidence, approve/skip with Undo, "This is my bank" | Both | `CaptureScreens.kt`, `InboxStore.kt`, `inbox` | Emu, Sim | Nothing auto-posts; per-source auto-confirm deliberately NOT built |
| Capture health | 30-day counts per outcome, last seen, per-bank found/ignored, unlisted senders with "This is my bank"; never shows message words | Both | `HealthScreens.kt:34-75`, `CaptureHealth.kt`, `capture_event` | Emu (`HealthPrivacyUiTest`), unit (`CaptureHealthTest`) | No redacted-sample report; allowlist is code (42 headers) plus the person's own additions |
| Flip direction / name a payee | In To review: "read the wrong way round" turns debit and credit; give a UPI address a name that later texts reuse | Both | `LedgerViewModel.kt:1973`, `:1966` | Emu | |
| Quick Settings tile | Add expense tile | Android | `QuickAddTileService` | Emu | |
| Launcher shortcut | Static "add_expense" | Android | `xml/shortcuts.xml` | Emu | Only one shortcut |

## 9. Import

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| CSV | Column mapping, bank profiles | Both | `Csv.kt`, `ImportProfiles.kt`, `StatementDetector.kt` | Unit, Emu | |
| Excel | .xlsx and legacy .xls readers | Both | `XlsReader.kt`, `XlsxReader.kt`, `IosXlsx.kt` | Unit; Sim | A web-page-named-xls gets a message |
| PDF | Text PDFs, password-protected asked, scanned via OCR | Both | `PdfStatementReader.kt` (PDFBox), `IosPdf.kt` (PDFKit) | Emu, Sim; real HDFC statements only on owner's phone (files not in repo) | |
| Photo statement | On-device OCR to rows | Both | `OcrStatementReader.kt`, `OcrRows.kt`, `IosOcr` | Emu, Sim | |
| Totals check | Compares parsed totals with statement summary | Both | `StatementSummary.kt` | Unit | |
| Dedupe on import | Skips rows already present | Both | `StatementImporter.kt`, `StatementImport.kt` | Unit, Emu | Import not undoable (preview with counts first) |
| Card payments in a statement | A bank statement's "credit card payment" lines (wording plus last four digits or bank name against the card accounts) are shown as a group "Looks like a card payment" with the choice: record as a transfer to the card, as spending, or skip; a card's own "payment received" lines may be a transfer from the bank. A transfer twin check against Mark paid, texts and the other side's statement | Both | `CardPayments.kt`, `ImportPlan.kt`, `StatementImporter.kt` (`transfersLikeStatementRow`), `ImportScreen.kt` | Unit, Emu (`CardPaymentImportUiTest`) | Wording is an assumption from how Indian banks write these lines (no real statement in the repo); several cards that cannot be told apart need the person's choice |
| Other apps' exports | Money Manager, YNAB, Splitwise, Cashew, Spendee files recognised by their exact column titles, read with category, account, note and labels; categories matched by name, new ones created only on a tick; "Read it as a plain statement instead" | Both | `ImportPresets.kt`, `ImportCategories.kt`, `ImportPlan.kt` | Unit, Emu (`PresetImportUiTest`) | Layouts from the apps' help pages and third-party tools, no real export was available; Wallet by BudgetBakers (sources disagree) and Axio/Walnut (no documented export) are not covered; transfers and other-currency rows are left out and counted |

## 10. Export, backup, restore

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| CSV export | All entries | Both | `CsvExport.kt` | Emu | |
| Excel export | .xlsx | Both | `XlsxExport.kt` | Emu | No PDF/OFX/QIF/JSON export |
| Backup (.expbak) | Own file format v2 (chunked); with or without photos and files | Both | `BackupFormat.kt`, `BackupStream.kt`, `BackupStore.kt`, `docs/BACKUP_FORMAT.md` | Emu, Sim; Android/iOS interop checked with Java reference | Restore only merges, deletes nothing |
| Encrypted backup | Passphrase, PBKDF2 + AES-256-GCM (`EXPENC1`/`EXPENC2`; `EXPENC3` for protected automatic copies) | Both | `BackupSeal.kt`, `BackupCrypto.kt`, `CryptoServices.swift` | Emu, Sim | Forgotten passphrase = unrecoverable |
| Auto copies | Daily/weekly/monthly backup + Excel to a chosen folder, keeps newest 7 | Both | `AutoExportRunner.kt`, `AutoExport.kt`, `IosAutoExport.kt` | Emu; iOS best-effort via BGAppRefresh | iOS also makes one on foreground. **Plain files by default** (`DataScreen.kt`: the screen tells the person to choose a folder only they can reach); opt-in "Protect copies with a passphrase" seals the backup (`EXPENC3`, `AutoCopyProtection.kt`, `AutoCopyControls.kt`): the key made from the passphrase is kept in the Android Keystore (`KeystoreSecretStore.kt`) / iPhone Keychain (`IosSecretStore.kt`, after first unlock, this device only), so it protects the FILES in the folder or drive, not against someone with the unlocked phone; no Excel file while on; key unreadable = nothing written, status "Could not protect this copy: set the passphrase again." (`docs/BACKUP_FORMAT.md` "Automatic copies"). Not exercised on a real Keystore/Keychain device or a real SAF folder |
| Send/save backup | System share/save | Both | `DataScreen.kt` | Emu, Sim | |
| Backup nudge | Reminds when no recent backup | Both | `BackupNudge.kt` | Unit | |

## 11. Security and privacy

| Feature | What | Plat | Where | Maturity | Limits |
|---|---|---|---|---|---|
| Database encryption | **Android:** SQLCipher; 32 random bytes passphrase wrapped by an AES-GCM key in Android Keystore (`expenses_db_wrap_key`), raw-key mode. **iPhone: the database is NOT separately encrypted**; it relies on iOS Data Protection (`NSFileProtectionCompleteUntilFirstUserAuthentication`) | Android / iPhone | `EncryptedDriver.kt`; `IosPlatform.kt` | Emu | iPhone file is readable after first unlock; `IOS.md` says so; Play data-safety wording "encrypted at rest" is true for Android only |
| Key lost handling | If DB exists but key missing, a Key-lost screen with two-step "start fresh" | Android | `DatabaseKeyUnavailableException`, `KeyLostScreen` | Emu | |
| App lock | Biometric/device credential, timeout (immediate/1 min..), covers app-switcher | Both | `AppLock.kt`, `IosAppLock.kt`, `LockScreens.kt`, `domain/AppLock.kt` | Emu; iOS Face ID not testable in sim | |
| Hide amounts | Mask amounts everywhere incl. widgets; FLAG_SECURE on Android when set | Both | `PrivateText.kt`, `AmountMask.kt`, `MainActivity.kt` | Emu | iOS Hide amounts not exercised |
| No internet permission | Manifest removes INTERNET and ML Kit datatransport backend | Android | `AndroidManifest.xml` | Checked in manifest; release smoke tool in `tools/` | iPhone: no network code found by me in shell (not audited) |
| Backups excluded from OS cloud | `allowBackup=false`, data-extraction rules | Android | manifest | Emu | |
| Privacy screen | In-app privacy text | Both | `ui/Screens.kt` Privacy | Emu | |

## 12. Notifications, widgets

| Feature | Plat | Where | Maturity |
|---|---|---|---|
| Notification settings (kinds, quiet hours, reminder hour) | Both | `NotificationSettingsScreen.kt`, `NotificationService.kt` | Emu, Sim |
| Android hourly work + boot restart | Android | `NotificationWork.kt`, `ExpensesApp.kt` | Emu |
| Android widgets (5 receivers): "Expenses: standard (4 x 2)" (quick add), "today (2 x 2)", "small (2 x 1)", "wide (4 x 1)", "large (4 x 4)" | Android | `androidApp/.../widget/*.kt`, `res/values/strings.xml:4-9`, `res/xml/*widget_info.xml` | Emu (`QuickAddWidgetTest`); No device |
| iPhone widgets (2): Today (small) with spent today and "a day left", Quick add (medium, up to six quick entries); no lock-screen families | iPhone | `iosApp/Widget/*.swift` (`supportedFamilies` systemSmall / systemMedium) | Sim; queued taps recorded when the app next runs |
| Widget Undo, eye to hide figures | Both | widget code | Emu, Sim |

## 13. UX, accessibility, theming

| Feature | Plat | Where | Maturity | Limits |
|---|---|---|---|---|
| Theme: system/light/dark, 3+ accent colours, comfortable/compact density | Both | `Appearance.kt`, `AppearanceScreen.kt` | Emu | Dark mode not accessibility-checked |
| Customise Home layout | Both | `HomeLayout.kt`, `HomeLayoutScreen.kt` | Emu | |
| Wide screens | Two-pane layout at 840 dp | Both | `ui/WidePane.kt` | Emu (resized) | Code comment: "no tablet was available" |
| Accessibility checks | ATF scan over 9 screens, 4 touch-target errors fixed | Android | `AccessibilityUiTest.kt`, `docs/ACCESSIBILITY_FINDINGS.md` | Emu API 26 only | Dark, large text, other languages, TalkBack hardware NOT checked; iOS VoiceOver not checked |
| Motion follows "remove animations" | Both | `Motion.kt` | Emu | |
| Onboarding | Opening/first-run, "first week" checklist card, tour spots, empty states | Both | `FirstRun.kt`, `FirstWeekCard.kt`, `TourSpots.kt`, `EmptyStates.kt` | Emu, Sim | |
| In-app guide/help | Searchable guide | Both | `Guide.kt`, `GuideScreen.kt` | Emu | |
| Feedback | Opens mail app with prefilled message (nothing sent by the app) | Both | `Feedback.kt`, `FeedbackMail.kt` | Emu | |
| Undo | Undo bar for deletes/bulk/skips; audit in `docs/UNDO_AUDIT.md` | Both | `UndoBar.kt`, `EntryActions.kt` | Emu | Import, category merge, capture-rule removal not undoable |
| Free-signing expiry notice | Shows days left on the iPhone build | iPhone | `SigningExpiry.kt`, `SigningNotice.kt` | Not seen against a real new profile | |
| Performance work | WAL mode, FTS tuning, single-flight loads, bulk load, perf script | Both | `EncryptedDriver.kt`, `FtsTuning.kt`, `BulkLoad.kt`, `SingleFlight.kt`, `tools/measure_perf.py` | Emu API 26 numbers in docs | No real-device numbers |

## 14. Currency

| Feature | Plat | Where | Limits |
|---|---|---|---|
| Base currency, per-account currency, currency table, minor-unit digits | Both | `BaseCurrency.kt`, `CurrencyScreen.kt`, `currency` table | Rates typed by hand, never updated; reports in one base currency |

## 15. Localisation, sync, sharing, platforms (checked)

- **Languages: English only.** `androidApp/src/main/res` has `values/` (9 strings: app and widget labels) and `values-night/` (colours); no `values-xx`; no compose-resources, `.lproj` or `Localizable` anywhere. 614 `Text("` calls in `shared/.../ui` plus the English `Guide.kt` in `core/domain` carry the UI text. Number format is Indian grouping (`MoneyFormat.kt`) and amount-in-words uses lakh/crore (`AmountWords.kt`).
- **Sync / multi-device: none, with groundwork and one manual path.** Groundwork: `domain/sync/Hlc.kt` and an `op_log` table that every store writes field-level ops into (`LedgerStore.kt:307`); nothing reads it but tests (`opsSince`). Manual path: a backup restore merges by id (adds what is new, never overwrites or deletes), so a backup of another phone can be merged additively; edits and deletions do not travel and independently captured duplicates are not collapsed. See 17b item 12.
- **Family / shared ledgers: none.** Only per-person lent/borrowed and shares inside one person's ledger.
- **Web / desktop: none** (`docs/site` is a marketing page, not an app). **Wear OS / watch: none.**

## 16. ABSENT features (checked in code; typical apps have them)

1. Bank account aggregation (Account Aggregator, Plaid, etc.): none; no network.
2. Cloud sync / multi-device live sync: none (groundwork only).
3. Multi-user / household sharing: none.
4. Web app: none. 5. Desktop app: none. 6. Wear OS / Apple Watch: none.
7. Investment tracking (stocks, MFs, FDs): none (no account type).
8. Net worth history, manual assets (property, gold, investments), and a net-worth line on Home or Reports: none. A single "Net worth" figure (sum of account balances) exists on the Accounts screen (17b item 6).
9. Splitwise-style groups and settle-up through UPI or the other person's app: none. Splits with named people, "A friend paid", per-person balances and a "paid it back" settlement entry exist (17b items 8, 11).
10. Receipt reading that fills merchant, date and tax into the entry: none. The bill-photo reader gives items and the printed total only (17b item 9); no receipt gallery.
11. Voice entry: none in app. (iPhone Siri phrase for "Add expense" exists through App Intents, not in-app voice.)
12. Location tagging / geofencing: none.
13. Live exchange rates / auto-updated conversion: none, by design (no internet).
14. Localised UI / categories per language: none.
15. Export to PDF, JSON, OFX, QIF: none (CSV, Excel, own `.expbak`).
16. Importers tuned to other apps (Money Manager, Splitwise, YNAB, Walnut/axio...): none. Generic CSV/Excel/PDF/photo import with auto-detected columns and saved mappings exists (17b item 7); no preset, no category/account/tag columns.
17. Tax tools (80C/80D/HRA): not built beyond the FY summary.
18. Zero-based/envelope budgeting, income-based budget plans: none; limits are per-category/overall with optional one-period rollover.
19. Cash-flow forecast (projected balance by date): none; only "upcoming repeating expenses" inside safe-to-spend.
20. Debt payoff planner, loan amortisation, interest/principal split, EMI schedules: LOAN account type only; EMI texts are found as bills (`BillKind.LOAN_EMI`) but no schedule is kept.
21. Per-source auto-confirm of captured items: deliberately not built.
22. SMS/notification capture on iPhone: impossible on iOS; replaced by Shortcuts/share/paste.
23. Custom reports builder, saved reports, scheduled report email: none.
24. Receipts gallery / document vault view: attachments exist per entry only.
25. Multiple attachments limit / cloud storage links: no cloud links.
26. Balance-based "augmented balance" (account balance minus card dues minus upcoming bills): none. (Budget-based "left a day" exists: see 17b item 5.)
27. Contacts integration (people picker): none, deliberate.
28. Premium/subscription billing, in-app purchases: none.
29. Recap screen/history of recaps: none (notification only).
30. Interactive/Live Activity widgets, lock-screen widgets, StandBy: not found in `iosApp/Widget` (only 2 home-screen widgets).
31. Real-device-proven performance numbers: none.
32. Over-the-air parser rules, in-app human support, public parser-health page: not built.
33. Redacted "report this message" for an undetected bank text, and updatable bank-format rules (data instead of code): none (17b item 1).
34. UPI payment-request or `upi://` link: none (17b item 11).
35. "Expecting this back" / reimbursement marker and pending-reimbursements list: none (17b item 8).
36. Per-category rollover choice; yearly-bill spreading across months in budgets: none.

## 17. Doc claims the code does not support

1. `docs/PHASE2_PROGRESS.md` line 21: "Notification capture, per-source auto-confirm and capture-health screens are not built" is stale. Notification capture (`NotificationCaptureService`) and capture-health (`HealthScreens.kt`) exist; only auto-confirm is truly not built.
2. `docs/PHASE2_PROGRESS.md` ~line 209: "income has no category in this app" is stale. `Income.category` and income categories exist (`CategoriesScreen.kt`).
3. `docs/IOS.md` line 64: "Version 0.4.3 (13)" is stale; `iosApp/project.yml` says 0.7.1 (26).
4. `docs/PHASE2_PROGRESS.md` "bottom bar text labels only, no icons": `Screens.kt` imports Material icons (Inbox, Sms, AccountBalance...), so icons exist now.
5. `docs/PLAY_LISTING.md:31` "Security practices: data encrypted at rest on the device." is true for the Android app that the Play listing describes (SQLCipher + Keystore). It would be false if reused for an iPhone store listing (OS Data Protection only: `IosPlatform.kt:38,65,75`). `docs/PRIVACY_POLICY.md:8` already separates the two correctly. (First pass called the Play line unsupported; that was too strong.)
6. `docs/ROADMAP.md` Phase 3 rows (sync, accounts, backend `Ktor` skeleton, `sync-model` module) describe things that do not exist in the repo: there is no `backend` and no `sync-model` module, only `domain/sync/Hlc.kt` and the `op_log` table.
7. `docs/PRODUCT_CONTEXT.md`/`ROADMAP.md` list FR-CAP-10 receipt OCR with line items as a later phase while the code already has bill-photo item reading (docs lag the code, not the reverse).
8. `docs/PLAY_LISTING.md` mentions "Capture from payment apps" tile and App Store text; Play `bundlePlay` truly has no SMS receiver, but several help strings still say "texts" (the doc itself says so; code not changed).
9. (Resolved in the second pass.) The Budgets UI exposes six period kinds: Monthly, Weekly, Every 2 weeks, Twice a month, Custom length, Financial year (`BudgetScreen.kt:166-190`, test `BudgetUiTest.theFinancialYearFortnightAndHalfMonthPeriodsCanBeChosenAndAreNamedAndKept`). Only the `Schema.sq` comment at `budget_period` is out of date (it names monthly and weekly only).
10. (Second pass.) My first-pass ABSENT item 8 "no net worth screen" was wrong. `AccountsScreen.kt:79-83` shows a "Net worth" figure (`net_worth` tag), tested in `AccountsUiTest.anAccountIsCreatedWithAnOpeningBalanceAndShowsItAndTheNetWorth`. It is only the sum of active account balances: no history, no manual assets, not on Home or Reports.
11. (Second pass.) My first-pass note "No pie chart" was wrong: `ReportCharts.kt:99` `BreakdownRing` is a donut ring, and `TrendChart` (line 182) draws period bars.
12. (Second pass.) `docs/PHASE2_PROGRESS.md` line 31 says the shared merchant/VPA dictionary is "not built". A starter list of 97 Indian merchant names does exist (`DefaultRules.kt`); what is not built is a curated alias/VPA-to-name dictionary.

## 17b. Verified absence claims (second pass, 2026-10-28, HEAD 825b814, read from code, nothing run)

Verdict: EXISTS / PARTIAL / ABSENT. Test = the test that exercises it.

| # | Claim ("we do not have X") | Verdict | Evidence (file:line) | Test |
|---|---|---|---|---|
| 1 | Parser health: counter of unrecognised senders, health screen, redacted report, rules as data | **PARTIAL.** Counter EXISTS (`capture_event`, `CaptureHealth.report`). Screen EXISTS. Redacted report/share of an undetected message ABSENT. Bank rules hard-coded | `core/domain/.../CaptureHealth.kt:23-56`; `HealthScreens.kt:34-75` shows 30-day counts (found, bills, same text again, OTPs, people, bank notices, declined), last-seen times, per-bank found/ignored, and "Senders not on the list" with a "This is my bank" button (`:64-74`) that saves the header in a setting (`LedgerViewModel.kt:1954`). The allowlist is Kotlin code: 42 headers (`SenderRules.kt:26-38`); bank formats are regexes (`GenericAlertParser.kt:54,132`, `MessageKind.kt` `KindRules`, `BillParser.kt:93-98`); only the person's extra headers are data. No code path builds, redacts or shares a sample of a message the parser failed on (grep for redact/undetected/report-a-message: no hit). "Bank notices ignored (not a payment)" is counted without the words | `CaptureHealthTest` (5 tests), `HealthPrivacyUiTest.theHealthScreenShowsOnlyCountsAndOffersToAddAnUnknownSenderWhoseTextsAreThenRead`, `CaptureUITests.swift` |
| 2 | Card bill payment and card spend double-count | **MOSTLY EXISTS.** Transfer handling and cross-source de-duplication exist. UPI-on-credit-card is not specially disambiguated | `LedgerViewModel.kt:1810-1817`: a CARD_PAYMENT text becomes a suggested TRANSFER bank to card ("credit card payment"), and a plain credit into a card becomes a transfer "payment received on the card", not income. `MessageKind.kt:22-35` words. Pay now / Mark paid on a card bill records a TRANSFER, never an expense (`LedgerViewModel.kt:1709-1735`, `BillsScreen.kt:221` "recorded as a transfer to the card, not as spending"). Statement import drops "PAYMENT RECEIVED" lines of a card statement and counts them (`StatementTextParser.kt:24-25`). Cross-source: `Duplicates.repeatsOfPending` (`Duplicates.kt:58-75`) pairs a bank text with a payment-app or email alert within 10 minutes (24 h with the same reference) and the row says "Probably the same payment as the text from X above" (`LedgerViewModel.kt:1796`); against the ledger `Duplicates.best` (same reference, or same amount within one day) gives "Already in your ledger"/"Looks like one you already added" (`:1795`). Missing: the suggestion needs a CREDIT_CARD and a BANK account to exist (else plain payment: `:1810`); no rule for a UPI payment made from a credit card (the account comes from last-4 digits or what was remembered: `:1801-1802`); no hard "likely double" flag beyond the note | `PairingUiTest.aCardPaymentSeenFromBothSidesIsOneTransferAndTheSecondTextIsFlaggedAsTheSame`, `...withNoCardAccountACardPaymentIsJustAPayment...`, `...aPlainCreditOnACreditCardIsOfferedAsAPaymentOnTheCardNotAsIncome`, `DuplicatesTest` (11 tests), `MessageKindTest`, `BillsUiTest.kt:144` |
| 2b | (follow-up to 2) Statement import and card payments | **GAP (confirmed in code).** Imports can only create EXPENSE or INCOME entries | `StatementImporter.kt:57` (`kind = if (flow == SPENDING) "EXPENSE" else "INCOME"`), `:70`; no transfer path and no card-payment word list in `StatementImport.kt` / `StatementDescriptions.kt`. A card statement's "PAYMENT RECEIVED" line is dropped and counted (`StatementTextParser.kt:24-25`), but the matching debit on the bank statement ("CREDIT CARD", "CC PAYMENT") is imported as an expense, so importing both statements counts the bill twice (the bank debit, and the card purchases). The same-day twin check works inside one account only (`entriesLikeStatementRow` by `account_id`, `StatementImporter.kt:63`) | none for the cross-account case (`StatementImporterTest` covers one account) |
| 3 | Languages: nothing externalised, English only | **CONFIRMED ABSENT for translation; Indian number format EXISTS.** | 9 strings in `strings.xml` (labels only); no `values-xx`, no compose-resources, no `.lproj` or `Localizable` in `iosApp`; 614 `Text("` calls in `shared/.../ui`; `Guide.kt` English in domain. No `Locale` use in shared/core. Indian grouping and lakh/crore short form: `MoneyFormat.kt:4,16,24,62` (₹12,34,567.89; ₹1.2L; ₹3.4Cr); amount in words in the Indian system `AmountWords.kt:3` | `MoneyFormatTest`, `AmountWordsTest` |
| 4 | Merchant/VPA dictionary | **PARTIAL.** Starter merchant to category list EXISTS (97 names). No alias/VPA to display-name dictionary | `DefaultRules.kt:8-17` (7 category names: Food 16 names, Groceries 12, Transport 16, Bills 21, Shopping 12, Health 9, Fun 11; whole-word match so `swiggy@icici` matches; used only when no user rule, no learned choice and no history: `CategorySuggest.kt:50-57`). Also: user rules (`capture_rule`, `CaptureRule`), learned payee to category (`merchant_map`; `CategorySuggest.merchantKey` strips the VPA handle), history by payee (`SimilarEntries.topCategory`), "name this payee" (`LedgerViewModel.kt:1966`, a per-address setting), person/business shape of a VPA (`PayeeKind.kt`), payee guess from the text (`PayeeGuess.kt`), 12 biller names (`BillParser.kt:93-98`), 4 payment-app packages, `CategoryCatalog.kt` default categories. Not found: a VPA handle map (`@ybl`, `@okhdfcbank` to bank/app), a merchant alias table, logos | `CategorySuggestTest` (starter cases), `StatementDescriptionsTest`, `StatementImportTest` |
| 5 | Safe-to-spend / augmented balance | **PARTIAL.** "Safe to spend per day" EXISTS, budget-based. Balance-based augmented balance ABSENT | `BudgetMath.safeToSpend` (`BudgetMath.kt:74-89`): overall limit left minus repeating expenses still due this period, divided by days left. Home card "Budget and safe to spend" (`HomeLayout.kt:5`), Budget screen text "After X of repeating bills still to come, you can spend about Y a day" (`BudgetScreen.kt:229-235`), Today widget "X a day left" (`TodayWidget.kt:66-68`; iOS `TodayWidget.swift:12-25`). Not subtracted: card dues, tracked bills found from messages (`BudgetStore.status` counts only `recurring.unhandled` of kind EXPENSE with a fixed amount, `BudgetStore.kt:110-114`), planned savings (`BudgetMath.kt:80`). Needs an overall limit; without one nothing is shown. Nothing subtracts dues from account balances | `BudgetTest`, `BudgetStoreTest`, `BudgetUiTest.anOverallLimitShowsWhatIsLeftAndAPerDayFigure...`, `...goingOverALimitSaysSoAndStopsOfferingAPerDayFigure`, `QuickAddWidgetTest` |
| 6 | Net worth | **PARTIAL.** A single "Net worth" figure on Accounts. No history, assets, goals or loans-lent | `AccountsScreen.kt:79-83` = sum of active accounts' balances (cards and loans negative); domain `Ledger.netWorth` (`Ledger.kt:82`). Person balances (`LoansScreen.kt`, owed to you/you owe) and goals are not included; no trend; not on Home or Reports; one currency | `AccountsUiTest.anAccountIsCreatedWithAnOpeningBalanceAndShowsItAndTheNetWorth`, `...aCardIsEnteredAsAmountOwedAndCountsAgainstNetWorth...`, `...transfersBetweenAccountsMoveTheBalanceButNeverTheNetWorth`, `LedgerTest` |
| 7 | Importers from other apps | **PARTIAL (generic only).** Header auto-detection and saved mappings EXIST. No app-specific presets | `StatementDetector.kt:8-13` synonym lists (date, description, debit, credit, amount, type; header found in the first 40 rows); `StatementMapping` (`StatementImport.kt:99`) editable on the Import screen; `ImportProfiles.signature/encode/decode` remember a mapping per header row. The app's own CSV is recognised and refused with an explanation (`OwnExport.kt:3-17`; `LedgerViewModel.kt:2131`). A file imports as date, description, amount, direction only: no category, account, tag, person or currency columns. Grep for Money Manager, Splitwise, YNAB, Walnut, axio, Spendee, Monefy, Bluecoins in code: no hit | `StatementImportTest`, `ImportProfilesTest`, `OwnExportTest`, `ImportUiTest` |
| 8 | Refund and reimbursement matching | **PARTIAL.** Refund linking EXISTS (manual and from texts); people balances and splits EXIST; "expecting this back" marker ABSENT | Manual: "Record a refund for this expense" (`EntryForm.kt:481`, `EntryActions.startRefund:168`); refunded amount tracked, cannot exceed what remains (`EntryActions.kt:161`, `EntryDraft.kt:116`). From texts: a credit with refund/reversal/reversed/revoked words (`MessageKind.kt:26,31`) is matched in To review to an earlier debit of the same amount, or whose merchant name appears in the text, within 120 days (`LedgerViewModel.kt:561,1818-1825`), shown as "refund of X". Splits: equally, own amounts, or "A friend paid" (`EntryForm.kt:393-440`, new expenses only); "Lent or borrowed" with "I gave / I got / X paid it back" settle button (`LoansScreen.kt:109`). Missing: a flag on an expense that someone will reimburse it, a list of pending reimbursements, matching an incoming credit to a lent amount or friend's share automatically | `PairingUiTest.aRefundIsLinkedToTheMatchingPurchase...`, `...aPurchaseAlreadyRefundedInFullIsNotMatchedAgain`, `SplitBillUiTest`, `LoansUiTest.lendingToANewPersonThenPartAndFullRepayment...`, `EntryDraftTest`, `PeopleStoreTest` |
| 9 | Receipt OCR auto-fill | **PARTIAL (items and total only).** | `BillItems.parse` returns `ParsedBill(items, printedTotalMinor)` (`BillItems.kt:10`); date lines, GST, tax, phone, header and footer are skipped on purpose (`:22,39,169`). The entry gets the printed grand total or the ticked lines; "Tax and other charges" can be a separate entry (`BillEntries.kt:8-28`, `BillItemsUi.kt:208`). Merchant, date and tax breakdown are NOT read into fields; each entry takes the form's date, account, category and tags (`BillItemsUi.kt:215`). The photo is attached to the first entry. Plain photo attachment without reading also exists (`EntryForm.kt` "Attach"/"Take a photo") | `BillItemsTest`, `BillItemsQtyColumnTest`, `BillEntriesTest`, `BillEntriesSaveTest`, `BillItemsUiTest`, `iosApp/UITests/BillPhotoUITests.swift` |
| 10 | iPhone DB encryption | **CONFIRMED: not encrypted by the app on iPhone.** | iPhone: `NativeSqliteDriver(ExpensesDb.Schema, "expenses.db", basePath = Application Support/expenses)` (`IosPlatform.kt:75`), directory attribute `NSFileProtectionCompleteUntilFirstUserAuthentication` (`:65`); class doc says "no separate key of the app's own" (`:38`). No SQLCipher in `shared/src/iosMain` or `iosApp`; the only Keychain code is the key of protected automatic copies (`IosSecretStore.kt`). Options and a recommendation: `docs/IOS_DB_ENCRYPTION.md`. Android: SQLCipher with a 32-byte random key wrapped by an AES-GCM Keystore key (`EncryptedDriver.kt:20-50`, alias `expenses_db_wrap_key`). Docs: `PLAY_LISTING.md:31` "Security practices: data encrypted at rest on the device." (Play listing, i.e. the Android app: true). `PRIVACY_POLICY.md:8` "encrypted on Android with a key kept in the phone's secure hardware (Android Keystore) and, on iPhone, protected by iOS Data Protection." (accurate). `IOS.md:46` "A separate database key: the file relies on iOS Data Protection (readable after the first unlock)." Also: the automatic copies written to a folder are NOT encrypted (`DataScreen.kt:150` "They are not encrypted, so choose a folder only you can reach") | `EncryptedStorageTest` (Android), `BackupInteropTests.swift` (backup crypto only; no test of DB encryption on iOS) |
| 11 | UPI settlement link | **ABSENT** (settle-up recording EXISTS) | No `upi://` string, intent filter or `<queries>` entry (manifest has only IMAGE_CAPTURE and mailto). The only external openers are mailto, file view and settings (`AndroidPlatform.kt:131`, `MainActivity.kt:111`). "X paid it back" pre-fills a settlement entry with the remaining balance (`LoansScreen.kt:109`); no payment request, no share of a balance | `LoansUiTest` |
| 12 | Folder sync / multi-device | **ABSENT (groundwork + manual merge by restore)** | Every store writes field-level ops with an HLC into `op_log` (`LedgerStore.kt:307`, `BudgetStore.kt:88`, `TemplateStore.kt:36`, etc.; table `Schema.sq:376`); the only reader is the query `opsSince` (`Schema.sq:1087`), used by tests only. No transport, no merge of ops, no device-id exchange. Backup restore is "a merge by id": ids already on the device (live or deleted) are left untouched, new ones are added, in one transaction (`BackupStore.kt:52-54`). So restoring phone B's backup on phone A adds B's new entries, accounts, categories and so on; it never copies later edits, never propagates deletions, and an item captured separately on both phones (different ids) would appear twice. Automatic copies can be written to a user-chosen folder (`AutoExportRunner.kt`, newest 7 kept, NOT encrypted) so the folder can be a sync target, but restore is a manual step | `BackupStoreTest.restoringTwiceOrOverTheSameDeviceDuplicatesNothing`, `...editsAndDeletionsMadeSinceTheBackupAreNeverUndone`, `...restoringIntoADeviceWithOtherDataMergesAndTheTotalsAddUp`, `sync/HlcTest` (clock only) |
| 13 | Exports | **CONFIRMED: CSV, Excel, own backup only** | `DataTransfer.kt:87` `exportCsv` (14 columns, `OwnExport.HEADER`), `:94` `exportXlsx`, `:101` `exportFinancialYear` (Excel, "FY 2025-26" summary by category), backup `.expbak` (JSON payload with SHA-256, optional photos, optional passphrase: `BACKUP_FORMAT.md`). No PDF, print, OFX, QIF, JSON-for-humans; the Reports screen has no share or export button; Search results and single accounts cannot be exported; the CSV is whole-ledger only | `CsvExportTest`, `XlsxExportTest`, `FinancialYearExportTest`, `DataTest` |
| 14 | Annual bills, sinking funds, EMI schedules | **PARTIAL** | Repeating items can repeat yearly (`RepeatUnit.YEAR`, `Schedule.kt:10,49,93`). Goals compute "put aside X a month" by a due date (`GoalMath.monthly`, `Goals.kt`, shown in `GoalsScreen.kt:79`) but are not linked to the budget. A bill text for an EMI or premium is found as `BillKind.LOAN_EMI` / INSURANCE with an `autoDebit` flag (`BillParser.kt:9,15,44`). Missing: amortisation or interest/principal split, loan schedule, an "annual bill spread over 12 months" in budgets, EMI detection from repeated debits (the subscription finder finds repeats, `Subscriptions.kt`, but does not label EMIs) | `ScheduleTest`, `GoalsTest`, `SubscriptionsTest`, `BillParserTest`, `RecurringUiTest`, `GoalsUiTest` |
| 15 | Quick-add surfaces | **EXISTS except lock-screen, watch and Assistant** | Android: 5 home-screen widgets: "Expenses: standard (4 x 2)", "today (2 x 2)", "small (2 x 1)", "wide (4 x 1)", "large (4 x 4)" (`strings.xml:4-9`, `AndroidManifest.xml` receivers); Quick Settings tile (`QuickAddTileService.kt`); one static launcher shortcut `add_expense` (`xml/shortcuts.xml`). iPhone: Today (systemSmall) and Quick add (systemMedium) widgets (`iosApp/Widget/*.swift`), URL `expenses://add`; App Intents `Capture bank message`, `Capture bank email`, `Add expense` as App Shortcuts with Siri phrases (`Intents.swift:88-105`), widget-internal intents Add a quick entry, Undo, Show or hide amounts. Absent: lock-screen or StandBy widgets (no accessory families), Control Center control, Wear OS or watchOS target, Android Assistant/voice action, Android share target (no `SEND` filter in the manifest) | `QuickAddWidgetTest`, `QuickAddTests.swift`, `iosApp/Tests/WidgetDataTests.swift`, `CaptureTests.swift` |

Matrix rows re-checked: per-category budgets EXIST (`budget.scope`, `BudgetStore`; `BudgetUiTest.aCategoryLimitIsFlaggedOnTheListWhenItIsOver`); rollover is one on/off switch that carries last period's unspent amount once (`BudgetStore.kt:100-110`), no per-category rollover choice, no pile-up; reports: Category / Account / Tag / Merchant groups (`ReportGroup`, `ReportStore.kt:12`), By period / By year / Financial year, custom range, drill-in, a donut and bars; multi-currency: manual rate only (`ForeignAmount.kt:4`); Android widgets 5; Wear none; goals, subscription finder, calendar, tags, saved searches, undo/history, app lock, encrypted backups, auto export all exist as listed in sections 1-13.

## 18. Numbers

| What | Count | Command |
|---|---|---|
| Version Android | 0.7.1 (versionCode 29) | `grep -n versionName androidApp/build.gradle.kts` |
| Version iPhone | 0.7.1 (build 26) | `grep MARKETING_VERSION iosApp/project.yml` |
| UI files in `shared/.../ui` | 54 | `ls shared/src/commonMain/kotlin/app/expenses/ui \| wc -l` |
| `*Screen(` composables | 32 | `grep -rhoE "^(internal )?fun [A-Z][A-Za-z]*Screen\(" shared/src/commonMain \| sort -u \| wc -l` |
| Kotlin `@Test` total | 1363 | `grep -rE "^\s*@Test" --include=*.kt . \| grep -v /build/ \| wc -l` |
| core/domain | 440 | same grep on `core/domain` |
| core/storage | 321 | same on `core/storage` |
| core/parsers | 86 | same on `core/parsers` |
| androidApp (instrumented) | 515 | same on `androidApp` |
| shared | 0 | same on `shared` |
| Swift tests (`func test`) | 54 | `grep -rE "func test" --include=*.swift . \| grep -v /build/ \| wc -l` |
| Test files total | 211 | `grep -rEl "@Test\|func test" --include=*.kt --include=*.swift . \| grep -v /build/ \| wc -l` |
| Instrumented test files | 89 (incl. assets) | `ls androidApp/src/androidTest/kotlin/app/expenses \| wc -l` |
| DB tables | 27 (19 indexes) | `grep -E '^CREATE (TABLE\|INDEX)' Schema.sq \| awk '{print $1,$2}' \| sort \| uniq -c` |
| Languages | 1 (English) | `ls androidApp/src/main/res` shows `values`, `values-night` only |
| Android widgets | 5 receivers | `grep -c "AppWidgetProvider\|appwidget.provider" AndroidManifest.xml` equivalent: 5 `<receiver>` with `appwidget.provider` |
| iOS widgets | 2 | `ls iosApp/Widget` |
| Notification kinds | 12 | `grep "enum class NoticeKind" Notifications.kt` |
| Git commits | 180 (at the first pass); HEAD 825b814 at the second | `git log --oneline \| wc -l`; `git rev-parse --short HEAD` |
| Built-in sender headers (`SenderRules.BUILT_IN`) | 42 in total (bank, card-issuer and payments-bank headers about 27, four payment apps, utility/telecom/insurer and card-startup headers about 11; the 42 is the counted figure, the split is approximate) | `awk '/val BUILT_IN/,/^    \)/' core/parsers/src/commonMain/kotlin/app/expenses/core/parsers/SenderRules.kt \| grep -o '"[A-Z0-9]*" to "' \| wc -l` |
| Starter merchant names (`DefaultRules`) | 97 names in 7 category names (Food, Groceries, Transport, Bills, Shopping, Health, Fun) | `sed -n 8,17p core/domain/src/commonMain/kotlin/app/expenses/core/domain/DefaultRules.kt \| grep -o '"[a-z0-9 ]*"' \| wc -l` |
| Strings in `strings.xml` | 9 (app name, "Add expense", widget labels only) | `grep -c "<string " androidApp/src/main/res/values/strings.xml` |
| Hard-coded `Text("` calls in `shared/.../ui` | 614 (plus about 550 multi-word English literals in all `shared/src/commonMain`, and `Guide.kt` with about 109 quoted strings in `core/domain`) | `grep -rhoE 'Text\("' shared/src/commonMain/kotlin/app/expenses/ui \| wc -l`; `grep -rhoE '"[A-Z][a-z]+( [A-Za-z,.'"'"'₹-]+)+[.!?]?"' shared/src/commonMain \| wc -l` |
| Budget period kinds in the Budgets UI | 6 (Monthly, Weekly, Every 2 weeks, Twice a month, Custom length, Financial year) | `sed -n 160,200p shared/src/commonMain/kotlin/app/expenses/ui/BudgetScreen.kt \| grep -o 'label = { Text("[^"]*")'` |
| App Intents in the iPhone app target | 3 (`CaptureBankMessageIntent`, `CaptureBankEmailIntent`, `AddExpenseIntent`) + 3 inside the widget extension (`QuickAddIntent`, `UndoQuickAddIntent`, `ToggleAmountsIntent`) | `grep -n "struct .*Intent" iosApp/Sources/Intents.swift iosApp/Widget/*.swift` |

I did not run builds or tests. Counts are text counts of annotations, not pass results.
