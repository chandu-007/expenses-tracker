# Redesign: calm and clear (design spec for releases 0.17 to 0.20)

The target look for every screen of the app. Design only: nothing in the code was changed when this was written (25 Nov 2026, against `main` at 0b3e357c).

## Where the designs are

| What | Where | Use it for |
|---|---|---|
| This spec | `docs/REDESIGN.md` | What changes on each screen, what to keep, the order of work |
| Screen sources (37 boards) | `docs/redesign/screens/*.dc.html` | Exact values: every colour, size, radius, gap and string is in the inline `style="…"` and the text. Data for a board (rows, amounts) is in the `renderVals()` script at the bottom of each file. |
| Board index | `docs/redesign/screens/canvas.json` | Board titles, which canvas page each belongs to, board size |
| Pictures of every board | `../expenses_builds/redesign_final/png/01_Main.png` … `37_System.png` (outside the repository, like the other design shots) | What it should look like |
| All boards in one file | `../expenses_builds/redesign_final/Expenses_redesign.pdf` | Overview |

Board file → screen: `Main` Home, `Add` Add form, `Entry` Entry detail, `Review` To review, `Activity` Search tab, `Filters` Search filters, `Reports`, `Budget`, `More`, `Appearance`, `Notifications`, `Privacy` (Privacy + Verify merged), `Data` Your data, `HomeLayout` Customise home, `Locked` App lock, `CaptureSettings` Capture from texts, `Setup` Guided setup, `Checkup` Why isn't capture working?, `Bills`, `Categorise` Fill in missing categories, `Accounts`, `Categories`, `Recurring` Repeating, `Subscriptions`, `Calendar`, `Goals`, `Trip` Trip summary, `Loans` Lent and borrowed, `Earlier` Bring in earlier spending, `Transfers` Transfer candidates, `Welcome` First run, `LargeText` Home at 360 dp and 200% text, `HomeDark`, `HomeHidden`, `HomeOver` (over budget), `Widgets`, `System` Tokens and components. `HomeDark`, `HomeHidden` and `HomeOver` draw `Main` with a prop (`dark`, `hide`, `state="over"`).

The boards are web mock-ups (px ≈ dp). Build them in Compose with the app's own components, tokens and tags; do not copy the HTML structure.

## Decisions (taken 25 Nov 2026)

| # | Question | Decision |
|---|---|---|
| D1 | Sign on expenses | Expenses unsigned (₹1,240); income `+₹` in the accent; the existing "Show spending in red" switch adds the minus sign and red. Adopted on the designer's recommendation (owner: no preference). |
| D2 | Loan on the Add form | Removed from the type switch (Expense, Income, Transfer stay). "I gave / I got" moves into the Details sheet. Adopted on recommendation. |
| D3 | Privacy and Verify | Merged into one screen, "Privacy". Both More rows become one ("Privacy and verify it yourself"). Adopted on recommendation. |
| D4 | First run | The ₹0 month card and "Set a budget" are no longer drawn above Welcome (answers question 12 of `docs/HOME_OPTIONS.md`). Adopted on recommendation. |
| D5 | Fonts | Bundle Inter (400, 500, 600, 700) and Space Grotesk (500, 600, 700), SIL Open Font License, in shared resources for Android and iPhone. Owner: yes. |
| D6 | Bottom bar | Home · Activity · Add · Reports · More. Only the label of the Search tab changes; keys, tags and the two-pane behaviour stay. |

## 1. Rules that hold everywhere

1. **One accent, two signal colours.** Accent teal `#0E6B5C` (dark theme `#4CC2A6`); amber `#A34A06` (dark `#F2A65A`) for "watch" and bills due; red `#B42318` (dark `#F28B82`) only for over a limit. Ground `#F5F5F2`, surface `#FFFFFF`, ink `#17191B`, muted text `#5C6166`; dark ground `#0F1112`, surface `#181B1D`. No gradients, no multi-colour section tiles. The "From your wallpaper" accent stays as a choice in Appearance; teal is the default.
2. **Type.** Inter for text; Space Grotesk only for headline figures and screen titles. Headline figures stop growing at 54 sp at large text (the Slice 112 rule).
3. **Money.** Whole rupees with lakh grouping on headline figures and tiles (₹1,82,828); exact amounts where they already show paise (rows, day totals, entry, Search, edit fields), as `WholeAmounts` does today. Signs per D1.
4. **Targets and text.** 48 dp minimum touch target (44 pt on iPhone). At 150% text and up: rows put the amount under the title, nudge buttons go under the words, the account tiles become one "Accounts" row, the bottom bar shows a label only on the selected tab (board `LargeText`). Nothing clipped at 360 dp and 200%.
5. **Copy.** One short line per idea. Explanations sit behind a "How?" link or the screen's footer, not in cards.
6. **Hide amounts.** Every figure becomes `₹••••` and every bar loses its length (board `HomeHidden`, widgets too), as decided in Slice 112.
7. **Statuses.** Every budget-like bar uses the same three states and words: *On pace* / *Well under* (accent), *A little ahead* (amber), *Over by ₹X* (red). The tick on a bar always means "today".
8. **Shapes.** Keep `Tokens.kt` (`Space`, `Radius`, `Elevation`, `Sheet`); the boards use radius 12 (tiles), 14 (inputs, keys), 16–20 (rows, cards), 24 (hero cards), pill for chips. Map them onto the existing radius steps rather than adding new ones.

## 2. Daily loop

| # | Place (code) | Final design | Keep |
|---|---|---|---|
| 1 | **Home** (`Screens.kt` EntryListScreen, `HomeMoney`, `NudgeCard`, `TripCards`, `HomeAccounts`) | One white hero card: *Left to spend*, pace bar with today's tick, "▼ ₹492 less than October by today", the two labelled a-day figures side by side (budget / balances). Trip line with a bar. ONE nudge card in amber with Open and ×; "Also waiting: To review (2) · No category (4)" as links with counts. Three account tiles; the capture heartbeat line; quick-entry chips; entries by day with day totals. No graph. | Nudge order and snooze (`HomeNudges`), Customise-home order, tags `home_top`, `month_spend`, `budget_summary`, `home_trip_card`, `add`, `balance_chip_*`. |
| 2 | **Add** (`EntryForm.kt`, `SmartEntryField`) | Type switch (Expense, Income, Transfer), amount with live sum ("450 + 120" above "₹570"), one note field that is also the smart line, all 8 categories in a 4×2 grid (no sideways scroll), one chip row: account, date, trip, **Details** (tags, split, receipt, other currency, expect it back, I gave / I got), keypad with ⌫ and operators, a save button that names what it saves ("Add ₹570 · Food"). | Amount focus at once; keypad maths; quick-entry saving; tag `add` and the form's tags. |
| 3 | **Entry detail** (`EntryDetail.kt`) | Big figure; every field a row you tap to change (no Edit mode); a line with this merchant's total for the year; Split, Refund, Duplicate, Edit history as a quiet list; Delete alone at the bottom in red. | Receipts and files, history, refund. |
| 4 | **To review** (`CaptureScreens.kt`, `ReviewParts`) | Headline "2 entries to check · ₹1,439 out"; one compact card per text with category and account chips and a one-line reason ("Matched 14 earlier Zomato entries"); Skip / Add; sticky "Add all 2 · ₹1,439"; an "All caught up" end state. | Swipe right/left, same-shop and same-payment prompts, undo until you leave. |
| 5 | **Activity** (`SearchScreen.kt`) | Search field and Filters button with a count badge, quick chips (month, account, kind, trip), an out / in / count summary line, entries by day with totals. Saved and recent searches when the field is focused. | Two-pane on folds, bulk select (`Bulk.kt`). |
| 6 | **Filters** (`SearchFilters.kt`) | One sheet: Dates, Kind, Account, Category, Tag as chip rows, Amount from/to, "Save this search", a button that says how many entries it will show. | Press-and-hold to remove a filter chip. |
| 7 | **Reports** (`ReportsScreen.kt`, `ReportCharts.kt`) | Month stepper with "so far · day 23 of 30"; summary card (Spent, the vs-last-month chip, Income, Saved %); 6-month bars you can tap (the open month dashed); *Where it went* as one 100% bar plus a list, with Category / Merchant / Account / Tag as one switch. No back arrow on a tab. | Slice 128 rules (partial month out of the average, spoken amounts, 200% rows). |
| 8 | **Budget** (`BudgetScreen.kt`) | Back arrow; hero with left / spent / pace tick; two tiles (a-day, held for bills); category limits sorted by attention with status chips; "19 categories have no limit · Set a limit". | Period settings and carry-over under the pencil. |

## 3. More and settings

| # | Place (code) | Final design |
|---|---|---|
| 1 | **More** (`MoreMenu.kt`, sections built in `Screens.kt` around lines 345–393) | Search box, two waiting cards with big counts, the backup reminder as one slim row, then six short groups with one neutral icon style: *Plan and track* (Budget, Bills, Repeating, Subscriptions, Goals, Travel mode, Calendar, Lent and borrowed), *Your ledger* (Accounts, Categories, Earlier spending, Transfer candidates), *Capture* (Capture from texts, Why isn't capture working?, Notifications), *Make it yours* (Appearance, Customise home, Currency), *Privacy and data* (Hide amounts switch, App lock, Privacy and verify it yourself, Your data), *Help* (Tour, Feedback). "Set up reading bank texts" moves inside Capture from texts; "Fill in missing categories" is reached from its waiting card. Keep every row tag. |
| 2 | **Appearance** (`AppearanceScreen.kt`) | Live preview card on top; Theme, List spacing and Motion as segmented switches with a one-line hint; accent swatches (Teal default, Indigo, Plum, Ochre, From wallpaper); True black, Haptics and Spending in red as switches; "Show tips again". |
| 3 | **Notifications** (`NotificationSettingsScreen.kt`) | "Pause suggestions" first, with A week / A month / Until I turn it back on, and the line that bills, card dues and budget alerts still come through; then one list of switches with the timing under each; test notification at the bottom. Keep `pause_nudges_*` and `milestones_switch`. |
| 4 | **Privacy** (`HealthScreens.kt` PrivacyScreen + `VerifyScreen.kt`, D3) | "Nothing leaves this phone."; *Asks for, and why* (with on/off); *Never asks for* as a grid of ticks; the airplane-mode test as 3 steps; the OTP warning in amber. The permission list stays read from the installed app. |
| 5 | **Your data** (`DataScreen.kt`) | Backup status card with a Due / Safe chip, Back up now, weekly backup switch; *Open in a spreadsheet* (Excel, CSV, FY summary); *Bring back* (Restore, Test, Import). |
| 6 | **Customise home** (`HomeLayoutScreen.kt`, `HomeSection`) | The 4 movable blocks between two locked rows (Month summary on top, Entries last), each with a drag handle, ▲/▼ buttons (TalkBack, switch access) and a show switch; Reset. |
| 7 | **App lock** (`LockScreens.kt`) | Lock screen: one icon, "Expenses is locked", Unlock, "Use phone PIN instead"; widgets show no figures while locked. |
| 8 | **Currency**, **Guide / tour** | Same layout and flow, new styles. |

## 4. Capture and bills

| # | Place (code) | Final design |
|---|---|---|
| 1 | **Capture from texts** (`CaptureScreens.kt`, `TrustedSendersEditor`, `SkippedSenders`, `LearnedPayees`) | Main switch with live status; three counters (found, to review, skipped); the promise line; *Your banks* with the account each maps to and a "Choose account" warning; links to skipped senders, learned payees, check-up and setup. |
| 2 | **Guided setup** (`GuidedSetup.kt`) | 4 steps with a segmented progress bar: permission (4 ticked promises), banks (chips), digits matched to accounts, an all-set check list. Back / Skip this step always visible. |
| 3 | **Check-up** (`CaptureCheckupScreen.kt`) | Headline with the number of problems; each check a row with ✓ or ! and, for a problem, one fix button; *Test a text that was missed* with the parsed result. |
| 4 | **Bills** (`BillsScreen.kt`, `BillItemsUi`) | Total left this week; due bills with a date block and a tick to mark paid (tap again to undo); card statement card with total, minimum, limit used, Record a payment; bills found in texts with Skip / Track it; a link to what was paid. |
| 5 | **Fill in missing categories** (`CategoriseScreen.kt`) | One expense at a time ("1 of 4"), suggested category first, "Also file 2 earlier Decathlon expenses", Skip, a done state. |
| 6 | **iPhone Shortcuts capture** (`ShortcutsCaptureScreen.kt`) | Same step pattern as Guided setup. No board: use the same components. |

## 5. Money tools

| # | Place (code) | Final design |
|---|---|---|
| 1 | **Accounts** (`AccountsScreen.kt`, `AccountTiles`, `FixBalance`) | Net worth with held / owed; Bank and cash list; a card row with limit bar and due date; "A balance looks wrong?" (Fix balance); Archived collapsed. |
| 2 | **Categories** (`CategoriesScreen.kt`, `SuggestedCategoriesScreen`) | Expenses / Income switch with counts; each row with its icon, colour, what is inside and this month's total; ideas row; edit, nest and merge behind a tap. |
| 3 | **Repeating** (`RecurringScreen.kt`) | Monthly total and its share of the budget; *Waiting for you* card (post or skip); *Coming up* with next date and amount; *Put yearly bills aside* as one sentence. |
| 4 | **Subscriptions** (`SubscriptionsScreen.kt`) | Yearly total; each subscription with next charge, yearly cost and a bell for a reminder. |
| 5 | **Calendar** (`CalendarScreen.kt`) | Month grid shaded by spending, a ring for no-spend days, an amber dot for bills due, a legend, a day panel. |
| 6 | **Savings goals** (`GoalsScreen.kt`) | One card per goal: saved of target, bar, "₹12,000 a month reaches it in 4 months", Put in / Take out. |
| 7 | **Trip** (`TripSummaryScreen.kt`, `TripsScreen.kt`) | Spent of budget with status and a-day for the days left; day-by-day bars against the daily budget; where it went; tag switch; all trip entries; End the trip today. |
| 8 | **Lent and borrowed** (`LoansScreen.kt`) | Net "you get back"; people with who owes whom; I gave / I got; waiting-to-get-back cards with Got it back / Not expecting it. |
| 9 | **Earlier spending** (`EarlierSpendingScreen.kt`, `ImportScreen.kt`) | Three numbered cards in order (statement, what it missed, balances) and the "never reads old texts" note. Import preview keeps its checks, restyled. |
| 10 | **Transfer candidates** (`TransferCandidatesScreen.kt`) | The total that may be counted as spending wrongly, then one card each with *Real spending* / the right action (Move to Cash, Mark card payment, Mark as transfer). |

## 6. First run and states

| # | Place | Final design |
|---|---|---|
| 1 | **First run** (`FirstRun.kt`, `ExampleMonth`) | One promise headline, a dimmed "Example" card (never stored), Add my first expense, I have old data, Show me around, privacy line. D4 applies. |
| 2 | **Dark** | Same layout, dark tokens; no gradient hero. |
| 3 | **Hidden amounts** | Every figure `₹••••`, bars empty, quick chips keep their names only. |
| 4 | **Over budget** | Hero turns red: "₹2,140 over", "Over by ₹2,140" chip, a full red bar, a line that new spending is all over the limit. |
| 5 | **360 dp, 200% text** | Section 1, rule 4. |
| 6 | **Empty states** (`EmptyStates.kt`) | One line, one action, no illustration (e.g. Bills: "No bills yet · Set up reading texts"). |
| 7 | **Widgets** (`androidApp/.../widget`, `iosApp/Widget`) | 4×2: left this month, pace bar, three quick-add chips, Add. 2×2: spent today against today's share. Locked or hidden: no figures and no bar. iPhone Lock Screen: round Add control and a one-line figure. |

## 7. Made-up figures in the boards (not product data)

Fun limit ₹9,000 and the over-budget figures; "4 visits · ₹4,860"; quick entries Lunch ₹150 and Auto ₹60; the three bill names (they add up to the demo ledger's ₹3,666), Tata Play, the card minimum and limit; repeating, subscriptions, goals, people, transfer candidates; calendar daily totals (add up to ₹49,305); trip day split (adds up to ₹12,400). They show states; build from real data.

## 8. Releases and work packages

Estimate: 55 to 78 engineer-days including tests; about 3 to 4 weeks at the pace of Slices 107 to 128.

| Release | Packages | Engineer-days |
|---|---|---|
| 0.17 | P1 Foundation (colours light and dark, fonts D5, status chip, nudge, row and tile components, the 40 hard-coded `Color(0x…)` in `ui/`) · P2 Search tab label (D6) · P3 Home (incl. D4) · P4 Add form (incl. D2) | 13–19 |
| 0.18 | P5 Entry detail · P6 To review · P7 Activity and Filters · P8 Reports restyle · P9 Budget · P10 More regroup and Privacy merge (D3) | 13–18 |
| 0.19 | P11 Appearance, Notifications, Your data, Customise home, App lock · P12 Capture settings, setup, check-up · P13 Bills, Fill in missing categories | 11–14 |
| 0.20 | P14 Money tools (10 screens) · P15 first run, empty, hidden, over, 200%, dark · P16 widgets (Android, iPhone Swift) · P17 iPhone checks · P18 release pictures and gate | 18–27 |

P1 first: every later package uses its tokens and components. P4 (Add) is the riskiest change (about 100 test files use the `add` tag).
