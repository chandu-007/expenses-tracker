# Product Context and Detailed Requirements

Status: **draft for phase planning**. Date: 2026-10-05.
Evidence base: [`MARKET_RESEARCH.md`](MARKET_RESEARCH.md) (pain points `PP-xx`) and the reports in [`research/`](research/).
Working name: **the app** (a brand name is still to be chosen).

---

## 1. Vision

> **The expense tracker you can trust and that never slows you down.** It captures every rupee or dollar from every source (UPI, cards, bank, cash, shared bills) with almost no effort. The numbers are correct and update instantly, even offline. The app never sells your data, never pushes loans, and never holds your history hostage.

### Positioning

| Against | Our position |
|---|---|
| axio, Moneyview, CRED, Jupiter (trackers that fund themselves with loans) | **No lending, no ads, no data sale**, and that promise is auditable. |
| Fold (AA-first, paywalled export, no cash at launch) | Capture from many sources, first-class cash, **export always free**. |
| Monarch, YNAB, Copilot (US-centric, sync-dependent, pricey, redesign churn) | Local-first and instant, works without bank linking, India-native capture, fair pricing including a lifetime option, a stable UI. |
| Money Manager, Monefy, Cashew (manual, data-loss-prone sync) | Equally fast manual entry **plus** reliable account-based sync, automatic capture, and correct ledger semantics. |
| Splitwise, Tricount (paywalled splitters, no personal budget) | Splits live **inside** the personal ledger: enter once, see your own share, settle via UPI. |
| Apple Wallet Insights, ChatGPT Finances | Works on Android and iOS, covers cash, UPI, splits and budgets, privacy-first AI. |

### Primary market

India first, with global-ready foundations. The evidence: no global leader supports Indian banks (`research/global-budgeting.md` §12), and no Indian app has replaced Walnut (`research/india.md`). Multi-currency, locale and pluggable capture providers keep the product viable worldwide.

---

## 2. Target users

| Persona | Profile | Core jobs | Key pains addressed |
|---|---|---|---|
| **P1 UPI-heavy salaried professional (India)** | 25–40, Android or iPhone, 30–100 UPI payments a week, 1–3 credit cards, some cash | "Know where my salary went without typing every chai"; card due dates; subscriptions | PP-01, PP-03, PP-04, PP-15, PP-16, PP-19 |
| **P2 Couple / family** | Partners with separate and joint money; may be on different platforms (iOS and Android) | A shared household budget while personal spending stays private; who owes whom | PP-13, PP-14, PP-18 |
| **P3 Flatmates / trip groups** | Rent, utilities, trips; some members won't install an app | Split once, settle via UPI, see "my share" in personal totals | PP-13, PP-17, PP-02 (Splitwise caps) |
| **P4 Irregular-income earner** | Freelancer, gig worker, commission or server pay | "How much can I safely spend until the next payment?"; annual bills | PP-11, PP-12 |
| **P5 Privacy-conscious manual logger** | Avoids bank linking and SMS access; values mindful logging | Fastest manual entry, offline, own the data | PP-03, PP-05, PP-08, PP-09 |
| **P6 Traveller / expat / NRI** | Spends in several currencies; INR plus a foreign salary | Per-entry currency, the amount the card actually charged, correct historical rates | PP-17 |
| **P7 Power user** | Wants rules, tags, reports, desktop or web, an API | Bulk editing, custom reports, export, automation | PP-18, PP-23, PP-09 |

---

## 3. Product principles (non-negotiable)

1. **Instant, always.** Every interaction reads and writes local storage. Nothing in the core loop waits on the network. (PP-06)
2. **Correct before clever.** Transfers, card payments, refunds, splits and cash are modelled in the ledger itself, not patched in reports. (PP-04)
3. **Capture from many sources; no single point of failure.** Every source is optional, every auto-captured item is reviewable, and manual entry is always the 2-second fallback. (PP-01, PP-03)
4. **The user owns the data.** Free full export in open formats, account-based sync with version history, and a shutdown data guarantee. (PP-05, PP-09)
5. **Earn trust explicitly.** No ads, no lending, no data sale, honest pricing, human support. (PP-02, PP-07, PP-08, PP-10)
6. **Calm, not shaming.** Neutral colours, encouraging language, focus on "what's next" rather than red failure states. (PP-21)
7. **Stable UI.** No surprise redesigns. Big changes ship behind a toggle with a migration window. (PP-06)
8. **Simple by default, deep on demand.** Progressive disclosure: a one-number budget for beginners, envelopes and rules for power users. (PP-11, PP-22)
9. **Accessible and localized from day one.** (PP-24)

---

## 4. Core domain model (semantics that fix PP-04, PP-13, PP-15, PP-16, PP-19)

These rules are requirements, not implementation suggestions.

| Concept | Rule |
|---|---|
| **Account** | Types: bank (savings or current), credit card, cash wallet, UPI-linked wallet or prepaid (e.g. Paytm wallet), loan or EMI, investment or asset (manual value), receivable/payable (people). Each has a currency. |
| **Transaction** | A double-entry-style posting. Fields include `amount`, `currency`, `fx_rate_at_entry`, `account`, `counter_account?`, `category?`, `splits[]`, `tags[]`, `merchant`, `note`, `attachments[]`, `source` (manual, SMS, notification, email, statement, bank, AA, receipt, voice), `source_ref` (UPI RRN, card ref), `status` (pending, cleared, reconciled), `created_at`, `occurred_at`. |
| **Transfer** | Movement between two of the user's own accounts. **Never counted as income or expense.** This covers credit card bill payments, ATM withdrawals (bank → cash wallet), wallet top-ups and moves to savings. |
| **Credit card** | Card spends are expenses on a liability account. The bill payment is a transfer from bank to card. The account tracks statement balance, due date, credit limit, available credit, add-on cards, and EMI conversions as linked loan accounts. |
| **Refund / reimbursement** | Linked to the original transaction. Category totals show **net** spend. Partial refunds are supported. A "pending reimbursement" state shows the expected amount. |
| **Split (category)** | One transaction can be split across several categories (e.g. an Amazon or Costco order). |
| **Shared expense** | Each transaction stores **paid-by** (the cash movement) and **consumed-by** shares. Personal analytics default to **my share**, with a toggle to **cash out of my accounts**. The difference is a receivable or payable on the person, not spending. |
| **Friend paid for me** | Creates my consumption share plus a payable, with no bank movement. Settling up is a transfer. |
| **P2P UPI** | Payments to a person default to a *transfer to a person*: an uncategorized person-ledger entry. The user can mark it as spend (rent, maid), a split settlement or a loan. |
| **Cash** | ATM withdrawal = transfer to the cash wallet. Cash spends draw the wallet down. A periodic "cash check" reconciles the wallet against what's actually on hand. |
| **Multi-currency** | Each entry keeps its original currency and amount, the rate at entry, and optionally the **card-settled home-currency amount** and fees. Changing the rate table **never rewrites history**. Manual rates are allowed. |
| **Recurring item** | A recurring expense, income or transfer with a schedule, an end date or number of occurrences, and "this one vs. all future" editing. Several recurring items per merchant are allowed. |
| **Sinking fund / annual bill** | A planned amount accrued monthly toward a future date. A large purchase can be spread (accrued) over N months in reports. |

---

## 5. Functional requirements

Priority: **M** = must for a credible v1 product · **S** = should, soon after · **C** = could, later or differentiator. Phase assignment comes later; priority here reflects evidence strength only.

### 5.1 Capture (FR-CAP)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-CAP-01 | **2-second manual entry**: a single screen showing amount (with calculator), category, account, date and note. The keyboard opens instantly and the last-used values are smart defaults. Save completes in ≤ 2 taps after typing the amount. | M | PP-03, PP-06 |
| FR-CAP-02 | **Quick-entry surfaces**: iOS interactive widget, Lock Screen and Control Center controls, Action button, Siri/App Intents, Shortcuts; Android home widget (Glance), Quick Settings tile, app shortcuts; watch entry (C). | M (widgets, tile, intents) / C (watch) | PP-03 |
| FR-CAP-03 | **Templates and favourites**: one-tap repeat entries (e.g. "Chai ₹20, Cash"), with the most-used templates surfaced in widgets. | M | PP-03 |
| FR-CAP-04 | **Natural-language and voice entry**: "250 swiggy dinner hdfc card", "spent 6.50 on latte". Parsed on-device where possible, with a confirmation chip before saving. | S | PP-03 |
| FR-CAP-05 | **Android SMS capture (India)** under Play's "SMS-based money management" exception. Allowlist of bank senders matched on the TRAI header root (suffix-agnostic). OTP and personal SMS never read or stored. Parsing on-device; raw SMS never uploaded. | M (India) | PP-01, PP-08 |
| FR-CAP-06 | **Notification capture (Android)** from bank and UPI apps, as a second channel for alerts not sent by SMS, with explicit opt-in. | S | PP-01 |
| FR-CAP-07 | **iOS SMS bridge**: a guided Shortcuts "message contains" automation that sends bank SMS to an App Intent, with a setup wizard and a health check. | S | PP-01, PP-18 |
| FR-CAP-08 | **Email capture via a forward-to-app address** (no Gmail restricted scopes) for card alerts, statements and e-receipts. Direct Gmail parsing only if a later CASA decision allows it. | S | PP-01 |
| FR-CAP-09 | **Statement import**: CSV, XLS, OFX, QIF, and **password-protected PDF** bank and card statements, with column mapping and saved per-bank profiles. Full-history import is supported. | M (CSV) / S (PDF) | PP-01, PP-09 |
| FR-CAP-10 | **Receipt capture**: photo or file attachment on any transaction. On-device OCR pre-fills amount, merchant and date; line-item extraction can drive category splits and item-level bill splits. | S (attach: M) | PP-26 |
| FR-CAP-11 | **Bank / AA sync (optional, pluggable)**: a provider-abstraction layer. India AA only after the regulatory path is decided (see §10). US via SimpleFIN or Plaid, EU via Enable Banking, iOS Apple Card via FinanceKit. Per-account "last synced" and health status, gap backfill, and multiple providers per region. | C | PP-01 |
| FR-CAP-12 | **Every auto-captured item lands in a review inbox** with a confidence score. High-confidence items can auto-confirm, which the user can turn on or off per source. | M | PP-01, PP-04 |
| FR-CAP-13 | **Capture health**: per-source status, a "possibly missed" detector that compares the app's balance with the bank balance quoted in an SMS, one-tap "this SMS wasn't detected" reporting, and parser rules updated over the air without an app release. | M (India) | PP-01 |
| FR-CAP-14 | **Migration importers** for Money Manager, Monefy, Wallet, Spendee, Cashew, axio export, Splitwise, YNAB, Mint/Monarch CSV. | S | PP-09 |

### 5.2 Ledger correctness and deduplication (FR-LED)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-LED-01 | Implement all semantics in §4 (transfers, card model, refunds, reimbursements, shared expenses, P2P, cash, multi-currency). | M | PP-04, PP-15, PP-16, PP-17, PP-19 |
| FR-LED-02 | **Deduplication engine** across all sources. Matches on UPI RRN or reference number, amount, account mask and a time window. Detects SMS + notification + email + statement duplicates, pending→posted transitions, and card replacements. Never silently deletes: suspected duplicates go to a "possible duplicates" queue with merge or keep. | M | PP-04, PP-01 |
| FR-LED-03 | **Auto-pairing**: card bill payment ↔ card account (transfer); failed UPI ↔ its refund; ATM withdrawal ↔ cash wallet; transfers between own accounts on both sides. | M | PP-04, PP-16 |
| FR-LED-04 | **Edits survive re-import**: user edits are keyed to a stable identity and are never lost when a source re-sends or re-IDs a transaction. | M | PP-04 |
| FR-LED-05 | **Cleared / reconciled status** and an account reconciliation flow against a statement balance. | S | PP-19 |
| FR-LED-06 | **Split a transaction** across categories (amounts or percentages) and by receipt line items. | M | PP-19, PP-26 |
| FR-LED-07 | **Payment-source attribution**: bank, debit card, credit card, RuPay-credit-on-UPI, wallet, cash. Filterable and reportable. | M (India) | PP-16 |
| FR-LED-08 | **Full undo and per-transaction edit history.** | M | PP-05 |
| FR-LED-09 | **Bulk edit** (category, tags, account, delete) from search results or a multi-select. | M | PP-03 |

### 5.3 Categorization and rules (FR-CAT)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-CAT-01 | A layered pipeline: user rules (deterministic) → per-user learned merchant map → shared merchant/VPA dictionary → on-device model → optional cloud model on de-identified strings. | M (first three layers) / S (models) | PP-04, PP-03 |
| FR-CAT-02 | **Corrections stick.** Correcting an item offers "always do this for <merchant/VPA>", applied to future items and optionally to past ones. | M | PP-04 |
| FR-CAT-03 | **Rules engine**: if merchant, VPA, amount range, account, source or text matches, then set category, tags, rename, mark as transfer, split, or require review. Rule priority is visible and can be reordered. Rules can be tested against history before saving. | M | PP-04 |
| FR-CAT-04 | **UPI VPA → merchant mapping** (`razorpay@…`, `paytm-…@…`, `…@ok*`), user rename rules, and P2P vs P2M detection. | M (India) | PP-16 |
| FR-CAT-05 | **Fully custom categories**: hierarchy (category → sub-category), icons, colours, reorder, hide, merge (with re-mapping), and inline creation while adding a transaction. | M | PP-22 |
| FR-CAT-06 | **Tags** (many per transaction) for trips, projects, tax (80C/80D/HRA) and reimbursable items. | M | PP-19 |
| FR-CAT-07 | **Micro-payment roll-ups**: optionally group small repetitive spends (chai, auto) into a daily or weekly summary line in views. | C | PP-03 |

### 5.4 Budgets and planning (FR-BUD)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-BUD-01 | **Budget modes**, switchable: (a) a single overall monthly cap, (b) category budgets, (c) envelope / zero-based with rollover. Default is (a) for new users. | M (a, b) / S (c) | PP-11, PP-22 |
| FR-BUD-02 | **Flexible periods**: monthly with a custom start day (salary cycle), weekly, bi-weekly, semi-monthly, custom. Indian financial year for annual views. | M | PP-11 |
| FR-BUD-03 | **Rollover** of unspent or overspent amounts per budget (optional). | S | PP-12 |
| FR-BUD-04 | **Budgets span accounts and wallets** by default, with an optional account filter. | M | PP-19 |
| FR-BUD-05 | **Safe-to-spend**: available money minus upcoming bills, sinking-fund contributions and goals, shown as today, this week or until the next income. Supports irregular income through expected-income entries or a rolling average. | M | PP-11, PP-12 |
| FR-BUD-06 | **Sinking funds and annual bills** accrue monthly. A large purchase can be spread over N months in reports. | S | PP-12 |
| FR-BUD-07 | **Goals** linked to real accounts or a virtual allocation, with the ability to spend from a goal. | S | wishlist |
| FR-BUD-08 | **Pace alerts**: gentle notifications when spending pace would exceed the budget, with configurable thresholds and quiet hours. | S | wishlist |
| FR-BUD-09 | **Short-horizon cash-flow forecast** (30–90 days) per account and overall, built from recurring items and the pace. | C | wishlist |

### 5.5 Recurring, bills, subscriptions, debts (FR-REC)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-REC-01 | Recurring expenses, income and transfers with flexible schedules, end conditions, "this vs. future" edits, and auto-post or confirm-to-post. | M | PP-19, PP-20 |
| FR-REC-02 | **Recurring detection** from history with a confidence score. Always shown as a *suggestion*, never auto-created. Several items per merchant are supported. | S | PP-20 |
| FR-REC-03 | **Bill and card due-date reminders**: statement balance, minimum due, due date, and a reminder schedule. | M | PP-19 (top India adoption driver) |
| FR-REC-04 | **Subscription radar**: list of active subscriptions with monthly and annual cost, price-change detection, and a "cancel" deep link. India: a **UPI AutoPay mandate** view built from parsed alerts, deep-linking to UPI apps or UPI Help. | S | PP-20 |
| FR-REC-05 | **EMI and loan tracking**: principal, rate, tenure, schedule, remaining balance, and card-EMI conversions. | S | PP-19 |
| FR-REC-06 | **IOUs and personal loans**: money lent or borrowed with people, due dates and reminders. Shares the person ledger with splits. | M | PP-13 |

### 5.6 Splits and shared money (FR-SPL)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-SPL-01 | **Split any transaction** (manual or auto-captured) with people in one action from the feed: equal, exact, percentage, shares or weights, item-level. "Fixed amount for one person, rest split equally." | M | PP-13 |
| FR-SPL-02 | **Groups** (trip, flat, event) with a group currency, members, default weights (e.g. couple = 2), and **debt simplification**. | M | PP-13 |
| FR-SPL-03 | **Non-app participants**: placeholder people, plus a shareable read-only link where they can view balances and claim receipt items. No sign-up required to view. | S | PP-13 |
| FR-SPL-04 | **Settlement**: UPI deep link or QR with a pre-filled amount and note (India); Venmo/PayPal/bank details elsewhere. Settlements are recorded as transfers and can be auto-matched to a captured UPI transaction. | M (UPI) | PP-13 |
| FR-SPL-05 | **No caps on the number of expenses**, no ads, no wait screens. Group features are free to use. | M | PP-02, PP-07 |
| FR-SPL-06 | **Multi-currency trips**: per-expense currency, actual card-settled home amount, and group totals in the group currency. | S | PP-17 |
| FR-SPL-07 | **Offline group entry** with conflict-free merge when back online. | M | PP-05 |
| FR-SPL-08 | **Audit trail and notifications** for adds, edits and deletes in groups, plus a settled-vs-open marker. | S | wishlist |
| FR-SPL-09 | **Recurring group bills** (rent, utilities, internet). | S | wishlist |

### 5.7 Household and sharing (FR-SHR)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-SHR-01 | **Privacy scopes**: Personal (only me), Shared (household/partner), Group (trip/flat). Each account and budget belongs to a scope. Partners see only Shared, plus optionally balances or summaries of Personal. | M | PP-14 |
| FR-SHR-02 | **Roles**: owner, editor, viewer. Invite by link. Cross-platform (iOS ↔ Android ↔ web) in real time. | M | PP-14, PP-18 |
| FR-SHR-03 | **Comments and reactions** on shared transactions. | C | wishlist |
| FR-SHR-04 | **Per-member views and filters** ("what I spent", "what we spent"). | S | PP-13 |

### 5.8 Insights, reports, search (FR-RPT)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-RPT-01 | **Home**: safe-to-spend, this period's spend vs budget, upcoming bills, review-inbox count, and cash and card balances. Customizable and reorderable, with sections that can be hidden. | M | PP-22 |
| FR-RPT-02 | **Reports**: by category, merchant, tag, account, payment method and person; any date range; period comparison (month-over-month, year-over-year); trend lines; income vs expense; net cash flow. **Every chart drills down to its transactions.** | M | PP-23 |
| FR-RPT-03 | **Calendar view** of daily spend and upcoming bills. | M | love |
| FR-RPT-04 | **Instant search** across all history: text, amount ranges, filters, saved searches. | M | PP-06 |
| FR-RPT-05 | **Net worth**: assets and liabilities, manual asset values (FD, PPF, EPF, gold, property, MF/stocks as manual or imported CAS), with a history chart. | S | love |
| FR-RPT-06 | **Weekly recap**: a short summary ("₹X spent, top 3 changes, 2 things to review"). This is the core retention ritual and assumes a weekly review, not daily logging. | M | retention (§7 of research) |
| FR-RPT-07 | **Indian tax helpers**: FY reports, tag-based 80C/80D/HRA summaries, rent receipts export. | C | India wishlist |
| FR-RPT-08 | **Printable / PDF reports** and per-group trip summaries. | S | wishlist |

### 5.9 AI assistant (FR-AI)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-AI-01 | **Ask your money**: natural-language Q&A over your own data ("how much on food delivery since April vs last year?"). It answers with the numbers *and* the query or filter it used, so the user can verify it. | S | wishlist |
| FR-AI-02 | **Privacy-first execution**: on-device model where available. Cloud use is an explicit opt-in, sends minimal de-identified data, and is never used for training. | M (if AI ships) | PP-08 |
| FR-AI-03 | **Insight nudges**: unusual charges, price increases, duplicate subscriptions, forgotten trials. Neutral tone, rate-limited. | S | PP-20, PP-21 |
| FR-AI-04 | **Bring-your-own-AI**: optional local or E2EE-safe MCP or API access to the user's own data. | C | wishlist |

### 5.10 Data ownership and sync (FR-DATA)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-DATA-01 | **Local-first store** is the source of truth. The app is fully usable offline indefinitely. | M | PP-05, PP-06 |
| FR-DATA-02 | **Account-based multi-device sync** that is conflict-free (field-level merge), near real-time, with visible sync status per device and **no bring-your-own-cloud file sync**. | M | PP-05, PP-18 |
| FR-DATA-03 | **End-to-end encryption option** for sync. Features that need server processing are labelled clearly. | S | PP-08 |
| FR-DATA-04 | **Free full export** at any time: CSV, XLSX, JSON (documented schema), and attachments as a zip. Scheduled automatic export to the user's own Drive or iCloud or Files. | M | PP-09 |
| FR-DATA-05 | **Backup and restore** with integrity verification, versioned snapshots, and a test-restore preview. Restoring never duplicates data. | M | PP-05 |
| FR-DATA-06 | **New-device continuity**: sign in and everything is restored, including categories, rules, attachments and settings. SMS is not re-parsed into duplicates. | M | PP-05 |
| FR-DATA-07 | **Shutdown guarantee**: a public commitment and mechanism (open export format plus an offline-capable app build) so data stays usable if the service ends. | S | PP-09 |
| FR-DATA-08 | **Public API / webhooks** for power users. | C | PP-09 |

### 5.11 Security and privacy (FR-SEC)

| ID | Requirement | Pri | Fixes |
|---|---|---|---|
| FR-SEC-01 | Encrypted local database (SQLCipher or equivalent). Keys live in Keychain/Keystore. | M | PP-08 |
| FR-SEC-02 | App lock with biometrics bound to a hardware-backed key, plus a PIN fallback and a configurable timeout. **No forced re-login or MFA loops** for local use. | M | PP-25 |
| FR-SEC-03 | Privacy dashboard: what each permission is used for, what leaves the device, per-purpose consent toggles, in-app consent withdrawal and account deletion. | M | PP-08, DPDP |
| FR-SEC-04 | **No third-party ad or tracking SDKs.** The analytics SDK is privacy-preserving and opt-in. Store privacy labels target "Data Not Linked to You" or better. | M | PP-08 |
| FR-SEC-05 | Hide balances / privacy mode (blur amounts) for use in public. | S | love |
| FR-SEC-06 | Sensitive-permission rationale screens that state exactly what is read (e.g. "bank alerts only; never OTPs or personal messages"). | M | PP-08 |

### 5.12 Notifications (FR-NOT)

| ID | Requirement | Pri |
|---|---|---|
| FR-NOT-01 | Actionable notifications: a captured transaction (with one-tap category or split from the notification), a bill due, a budget pace alert, the weekly recap. | M |
| FR-NOT-02 | Every notification type can be configured individually; quiet hours; **zero promotional notifications**. | M |

### 5.13 Settings, personalization, localization (FR-SET)

| ID | Requirement | Pri |
|---|---|---|
| FR-SET-01 | Currency and locale formatting including **lakh/crore grouping** and compact ₹1.2L / ₹3.4Cr. A base currency plus additional currencies. | M |
| FR-SET-02 | Languages: English at launch. Hindi plus top Indian languages next (DPDP notices must be available in Eighth Schedule languages). | M (EN) / S (HI+) |
| FR-SET-03 | Themes: light, dark (free) and accent colours; density setting (compact or comfortable). | M |
| FR-SET-04 | Layout stability: a "classic layout" toggle for major UI changes, plus in-app changelogs. | S |
| FR-SET-05 | First-run onboarding in under 60 seconds to the first logged expense. Optional guided setup for accounts and capture sources. Contextual tips, no tutorial walls. | M |

### 5.14 Support (FR-SUP)

| ID | Requirement | Pri |
|---|---|---|
| FR-SUP-01 | In-app support with **human escalation**, plus diagnostics attached with consent (no financial data unless the user opts in). | M |
| FR-SUP-02 | Public status page: capture-parser health per bank, known issues, fix ETAs. | S |
| FR-SUP-03 | Public feature-request board with status. | C |

---

## 6. Non-functional requirements

### 6.1 Performance budgets (the "very responsive" requirement)

Reference devices: **mid-range Android** (e.g. a 2023 Snapdragon 6-series or Dimensity 7000-class phone with 6 GB RAM) and **iPhone 12 or newer**. Dataset: **100,000 transactions over 10 years**, 500 categories and tags, 1,000 attachments.

Android budgets apply from the first release. iPhone budgets apply from the iOS release (§6.5).

| Metric | Budget |
|---|---|
| Cold start to interactive home | ≤ 1.0 s (Android mid-range), ≤ 0.6 s (iPhone) |
| Warm start | ≤ 300 ms |
| Open the add-expense screen (in app) | ≤ 100 ms to keyboard focus |
| Widget, tile or Control Center to saved entry | ≤ 2 s of user time, ≤ 3 interactions |
| Save transaction (local commit, UI updated) | ≤ 50 ms |
| Transaction list scroll | 60 fps sustained (120 fps on ProMotion), no blank cells |
| Search across full history | First results ≤ 100 ms |
| Any report or chart render (1 year) | ≤ 300 ms; 10 years ≤ 1 s |
| Sync propagation to another online device | ≤ 5 s (p95) |
| SMS/notification → review inbox | ≤ 2 s after receipt |
| App size (install) | ≤ 40 MB baseline (excluding optional on-device models) |
| Battery | No measurable drain from background capture (event-driven, no polling) |

Every interaction gets visual feedback within 100 ms. No ads, network calls or heavy animations on the critical path. Performance budgets are enforced in CI on the reference dataset.

### 6.2 Reliability and data integrity

- Zero data loss: atomic local writes, a sync op-log with idempotent replay, and verified backups.
- Deduplication precision ≥ 99% on the labelled test corpus; recall tracked separately.
- Target SMS parser coverage: ≥ 98% of transactional alerts from the top 25 Indian banks and card issuers, plus top UPI apps. Format regressions are detected within 24 h via the health telemetry the user opted into.
- Crash-free sessions ≥ 99.8%.

### 6.3 Security and compliance

- **India DPDP Act 2023 / Rules 2025**, ready before 13 May 2027:
  - itemised per-purpose consent and withdrawal
  - erasure
  - grievance officer, with responses within 90 days
  - 72-hour breach runbook
  - 1-year log retention
- **Data residency:** India user data stored in India (e.g. AWS ap-south-1). This also eases any future AA/FIU route.
- **Google Play:** Permissions Declaration for SMS; Financial features declaration; no exfiltration of non-financial SMS.
- **App Store:** publish under a company (legal entity) developer account.
- **GDPR** readiness before any EU launch.
- **Security programme:** OWASP MASVS L2 for the mobile apps; annual third-party penetration test. Pursue ISO 27001 / SOC 2 Type II only when partners require it.

### 6.4 Accessibility

- WCAG 2.2 AA on every platform.
- Full VoiceOver and TalkBack labels; Dynamic Type and font scaling up to 200% without clipping.
- Colour is never the only signal: colourblind-safe palette, plus icons and text for states.
- Large touch targets; keyboard and switch control on iPad, desktop and web.

### 6.5 Platforms and delivery strategy (decided 2026-10-06: Android first, then iOS)

**Stage 1: native Android app (Play Store, India).**
- Written in Kotlin with Jetpack Compose. Every Android capture feature and quick-entry surface ships natively, with no bridge layer in between:
  - SMS reader (FR-CAP-05), under Play's "SMS-based money management" declaration
  - notification listener (FR-CAP-06)
  - Glance home-screen widgets and a Quick Settings tile (one-tap add on Android 13+ via `requestAddTileService()`)
  - app shortcuts, share-to-app for receipts and statements
  - biometric unlock bound to a Keystore key; encrypted local database (SQLCipher)
- From day one the code is structured as **Kotlin Multiplatform (KMP)**, so all logic sits in shared modules: domain/ledger rules, deduplication, rules engine, parsers, sync, storage. The UI uses Compose, which Compose Multiplatform can later reuse on iOS.
- Android-only modules (SMS, notification listener, widgets, tile) sit behind interfaces in the shared core, so iOS supplies its own implementations.
- **Minimum Android version: `[to validate against Play distribution data for India]`**. Proposed: API 26 (Android 8.0).

**Stage 2: iOS app.**
- Reuses the shared KMP core and the Compose Multiplatform UI. Compose Multiplatform for iOS has been stable since 1.8.0 (May 2025) (`research/tech.md` §3.2).
- Swift/SwiftUI for iOS-only surfaces: WidgetKit widgets, Control Center / Lock Screen / Action button controls and Siri via App Intents, Live Activities, Keychain biometrics, Apple Foundation Models, FinanceKit (Apple Card).
- iOS can't read SMS, so capture comes from the Shortcuts SMS bridge into an App Intent (FR-CAP-07), email forwarding, statement import and receipts.
- Requires an App Store account registered to a company (§6.3).

**Known Stage-1 gap:** a partner or flatmate on iPhone can't use household sharing (FR-SHR-02) until Stage 2. In the meantime they can see group balances and settle up through the read-only link (FR-SPL-03), which doesn't need the app.

**Later:** web app for bulk editing, reports and desktop use (**S**, after iOS). Wear OS and watchOS quick entry (**C**).

**Backend (all stages):** Docker containers deployed in an India region (§6.3): sync relay, email-forwarding ingest, account/entitlements, Postgres. The app stays fully usable offline without it.

---

## 7. Business model principles

These are derived from PP-02, PP-07, PP-08 and PP-09. The exact pricing is an open decision (§10).

1. **No ads, no lending or credit products, no data sale, ever.** This is written into the terms of service.
2. A **generous free tier** that is a complete tracker: unlimited manual entries, accounts, categories, budgets, splits and groups, local storage, **full export**, dark mode.
3. **Premium** (subscription **and** a fairly priced lifetime option) covers costs that scale or that we pay for: multi-device and household sync, automatic capture sources that need servers (email forwarding, bank/AA), cloud AI, receipt OCR in the cloud, advanced reports, web app.
4. **Billing honesty:**
   - pricing shown before any setup
   - in-app cancellation
   - trial reminders 48 hours before charging
   - price changes announced 30+ days ahead, with existing lifetime licences honoured
   - entitlements restored on any device or platform from one account
5. **Group fairness:** group participants never need Premium to view, add or settle.

---

## 8. Success metrics

| Metric | Target (hypothesis to validate) | Rationale |
|---|---|---|
| Time to first logged expense | < 60 s median | Onboarding (PP-22) |
| Day-30 retention | ≥ 15% (category benchmark 4–6%) | Retention reality |
| Weekly active reviewers / MAU | ≥ 50% | Weekly ritual (FR-RPT-06) |
| % of transactions auto-captured (India Android users with SMS on) | ≥ 85% | PP-01, PP-03 |
| Manual corrections per 100 auto-captured transactions | ≤ 5 after 30 days | Categorization learning |
| Duplicate reports per 1,000 transactions | ≤ 1 | PP-04 |
| p95 cold start on reference Android | ≤ 1.0 s | PP-06 |
| Data-loss incidents | 0 | PP-05 |
| Store rating | ≥ 4.7 with < 5% of reviews citing bugs or slowness | Benchmark |

---

## 9. Explicit non-goals

- Lending, credit cards, BNPL, insurance or investment *selling*; any affiliate monetization of user data.
- Bill payment or money movement inside the app. We deep-link to UPI and payment apps for settlement; we don't process payments.
- Business accounting or GST invoicing (a possible later product, not this one).
- Investment portfolio analytics beyond net-worth valuation (no cost basis or XIRR in early scope).
- Gamification built on streaks or leaderboards. Evidence says streaks don't drive retention; we use gentle weekly recaps instead.

---

## 10. Decisions (accepted 2026-10-05)

The user accepted all recommendations below. On 2026-10-06 the user chose mobile first: Android, then iOS (D2, D8, §6.5).

| # | Decision | Options | Evidence pointer | Recommendation |
|---|---|---|---|---|
| D1 | Launch geography | India-only, then global; or India + global simultaneously | India gap (`research/india.md`); no global app supports Indian banks | India first with global-ready foundations (multi-currency, locale, pluggable capture) |
| D2 | Client stack | Flutter + native extensions; KMP core + native/Compose UI; React Native (New Arch) + Expo widgets | `research/tech.md` §3.2, §3.3, R2; §6.5 | **Kotlin Multiplatform (KMP) with Compose UI.** Android ships first in Kotlin, with direct access to SMS, notification listener, Glance and QS tile, and no platform-channel layer. The shared KMP core plus Compose Multiplatform carry over to iOS; iOS-only surfaces are written in Swift. Storage: SQLDelight (KMP) over SQLite with SQLCipher; iOS SQLCipher driver `[validate in Stage-1 spike]`. |
| D3 | Sync architecture | Own op-log/CRDT + E2EE relay (Actual-style); PowerSync (Postgres ↔ SQLite); Evolu (RN only) | `research/tech.md` §3.1, R1 | Own HLC op-log sync over local SQLite, for privacy and no vendor risk. PowerSync only if speed to market dominates. |
| D4 | E2EE by default vs. opt-in | Default E2EE (no server AI); opt-in E2EE | `research/tech.md` §3.4 | Opt-in E2EE at launch. Server features clearly labelled; on-device AI preferred. |
| D5 | India AA path | None (SMS/email/statement only); partner with a regulated FIU; register as a SEBI RIA (Fold's route) | `research/india.md` AA section | Defer. Launch without AA; revisit after traction. |
| D6 | Gmail direct parsing | Forwarding only; Gmail API + CASA (~$540–675/yr) | `research/tech.md` §2.2 | Forwarding first |
| D7 | Pricing | Free + Premium subscription + lifetime; price points for IN vs. global | `research/reviews.md` business model | Validate willingness to pay; anchor below Fold Plus in India and below Monarch globally |
| D8 | Platform order | Web first; Android + iOS together; Android then iOS | §6.5 | **Android first, then iOS**, then web |
| D9 | AI provider for cloud fallback | Gemini Flash-Lite, GPT-5 nano, self-hosted small model | `research/tech.md` §2.5 | Start with a cheap hosted nano model on de-identified strings; move to a fine-tuned small model later |
| D10 | Hosting / deployment | Docker containers on a cloud provider's India region; managed PaaS | §6.3 data residency | Docker images (sync relay, email ingest, accounts/entitlements, Postgres) deployed in an India region. Backend in Kotlin (Ktor), so it shares the KMP serialization models with the app. |

---

## 11. Traceability: pain point → requirements

| Pain point | Requirements |
|---|---|
| PP-01 Unreliable capture | FR-CAP-05…13, FR-LED-02, FR-SUP-02 |
| PP-02 Price / billing | §7, FR-SPL-05 |
| PP-03 Logging fatigue | FR-CAP-01…04, FR-CAP-12, FR-CAT-01, FR-CAT-07, FR-LED-09, FR-RPT-06, FR-NOT-01 |
| PP-04 Untrustworthy numbers | §4, FR-LED-01…04, FR-CAT-02, FR-CAT-03 |
| PP-05 Data loss | FR-DATA-01…06, FR-LED-08, FR-SPL-07, §6.2 |
| PP-06 Slow / redesign churn | §6.1, FR-DATA-01, FR-RPT-04, FR-SET-04, principle 7 |
| PP-07 Ads | §7, FR-SPL-05, FR-NOT-02 |
| PP-08 Loan funnel / privacy | §7, FR-CAP-05, FR-SEC-01…06, FR-AI-02, FR-DATA-03 |
| PP-09 Lock-in | FR-DATA-04, FR-DATA-07, FR-DATA-08, FR-CAP-14 |
| PP-10 Bot support | FR-SUP-01…03 |
| PP-11 Method rigidity | FR-BUD-01, FR-BUD-02, FR-BUD-05 |
| PP-12 Irregular expenses | FR-BUD-03, FR-BUD-05, FR-BUD-06 |
| PP-13 Shared double entry | §4 shared semantics, FR-SPL-01…09, FR-REC-06, FR-SHR-04 |
| PP-14 Couples privacy / roles | FR-SHR-01, FR-SHR-02 |
| PP-15 Cash invisible | §4 cash, FR-LED-01, FR-LED-03 |
| PP-16 UPI misclassification | FR-CAT-04, FR-LED-03, FR-LED-07 |
| PP-17 Multi-currency | §4 multi-currency, FR-SPL-06, FR-SET-01 |
| PP-18 Platform gaps | §6.5, FR-CAP-07, FR-SHR-02, FR-DATA-02 |
| PP-19 Ledger fundamentals | FR-LED-05, FR-LED-06, FR-REC-01, FR-REC-03, FR-REC-05, FR-CAT-06 |
| PP-20 Recurring detection | FR-REC-02, FR-REC-04, FR-AI-03 |
| PP-21 Shame UX | Principle 6, FR-AI-03 |
| PP-22 Onboarding / clutter | FR-SET-05, FR-RPT-01, FR-BUD-01, FR-CAT-05 |
| PP-23 Weak reports | FR-RPT-02, FR-RPT-03, FR-RPT-08 |
| PP-24 Accessibility | §6.4 |
| PP-25 Auth friction | FR-SEC-02 |
| PP-26 Merchant / receipt splitting | FR-CAP-10, FR-LED-06 |
