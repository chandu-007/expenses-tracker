# Home page: audit and three layout options (design only)

Nothing in the app was changed. This is an audit of today's Home (main at c888934, Travel mode and two-pane Search included) and three layouts to choose from. Images are in `~/Documents/personal/expenses_builds/home_options/` (outside the repository): `audit_*.png` are real screenshots, `option_*.png` are mock-ups drawn in the app's real theme (Material 3 baseline, violet accent, Roboto, the app's colours for tiles, entries, amounts) with invented data. The mock-up figures are simplified and differ slightly from the audit's seeded month; only the layout is being compared.

## 1. How the audit was made
- Debug build of origin/main in a clean worktree, API 34 arm64 emulator (cold boot, `tools/emulator_setup.sh`), one at a time under `/tmp/emu.lock`.
- A throw-away instrumented test (not committed) seeded invented data, took screenshots with UiAutomation and measured every Home block (unclipped position, in dp, from the top of the list). 195 screenshots: 11 configurations x 17 states, plus 8 account-strip shots.
- Configurations (name in the file): `w393f10l` = 393 dp wide, text 100%, light; `d` = dark; `f20` = text 200%; `w360` = 360 dp; `w864` / `w840` = fold-size window (two panes: the list pane is 42% of the window, so about 363 / 353 dp). The list area is 711 dp tall at 393 dp, 644 dp at 360 dp, 654 dp at fold size.
- States: `a_empty_welcome` (brand-new), `a_empty` (Welcome skipped), `b_typical` (46 entries over 40 days, salary, rent, three accounts incl. a credit card with a limit and a bill due in 6 days, a budget of 60,000, three quick entries), `c_heavy` (700 entries), `d_nudge_<type>` (each of the seven nudges on its own, `MANY` = four waiting), `e_trip`, `e_trip_and_nudge`, `f_hide`, `g_strip_start/end` (account strip, card tile with digits).
- "First entry" = top of the first entry row from the top of the list: the top block is one list item, so it is the bottom of its last block plus its spacers plus the day heading (about 40 dp). Checked against the screenshots (typical: heading seen at about 702 dp, estimate 677 to 717). Measured numbers are in `audit_measurements.csv`.
- Limits: emulator, not a phone; invented data; contrast was measured by sampling pixels of the screenshots; nothing was timed.
- Sheets (many shots on one image): `audit_sheet_1_phone393_light.png`, `audit_sheet_2_nudges_393_light.png`, `audit_sheet_3_font20_393_light.png`, `audit_sheet_4_phone360_font20_light.png`, `audit_sheet_5_phone360_font10_light.png`, `audit_sheet_6_dark.png`, `audit_sheet_7_fold.png`.

## 2. What the first screen is made of today (typical month, 393 dp, 711 dp tall)
| Block | dp | Share |
|---|---|---|
| Month card (amount, comparison sentence, graph) | about 203 | 29% |
| Bills-due card (the one nudge) | 88 (+12 gap) | 14% |
| Budget card | 107 (+12) | 17% |
| Safe-to-spend line | 68 | 10% |
| Account tiles (196 x 100 each) | 112 | 16% |
| Quick-entry chips | 41 (+16) | 8% |
| Entries | 0 | 0% |

Money summary alone (month card + budget + safe) is about 390 dp, 55% of the screen. The first entry starts at 717 dp: nothing from the list is visible. Where the first entry starts (dp from the top of the list; the screen shows 711 / 644 / 654):

| State | 393, 100% | 393, 200% | 360, 100% | 360, 200% | Fold 864 |
|---|---|---|---|---|---|
| Typical month | 717 | 1162 | 730 | 1190 | 716 |
| Heavy data | 751 | 1260 | 748 | 1320 | 750 |
| Nudge: 2 bills due | 809 | 1353 | 822 | 1413 | 808 |
| Nudge: To review (4) | 857 | 1305 | 870 | 1406 | 856 |
| Nudge: new bill | 763 | 1242 | 776 | 1302 | 762 |
| Nudge: money to get back | 725 | 1140 | 738 | 1168 | 724 |
| Nudge: no category | 701 | 1324 | 730 | 1522 | 701 |
| Nudge: set up bank texts | 765 | 1175 | 778 | 1260 | 765 |
| Nudge: getting started | 697 | 1052 | 710 | 1080 | 696 |
| Four things waiting | 857 | 1449 | 870 | 1541 | 856 |
| Trip + bill due | 773 | 1261 | 794 | 1289 | 781 |
| Trip + 2 bills + review | 913 | 1516 | 934 | 1576 | 921 |
| Hide amounts | 709 | 1088 | 714 | 1116 | 716 |
| Brand-new (Welcome) | 686 | 1166 | 708 | 1194 | 710 |

Nudge heights at 393 dp, 100%: 2 bills 180, To review 229, new bill 134, money back 96, no category 56, set up texts 136, getting started 68. At 200% on 360 dp: bills 467, To review 460, no category 560 (a screen is 644).

At a glance, what is there: spent this month (yes), against last month (a long sentence), safe to spend (yes, separate from budget), budget bar (yes), bills (only as a nudge within 7 days), card limit used (no, only on Accounts), trip (a line, no budget bar), entries by day (yes, no day totals), top categories (no), To review (only as a nudge; "Also waiting" gives no count).

## 3. Findings
| # | Sev. | Problem | Evidence |
|---|---|---|---|
| 1 | High | Not one entry is on the first screen (typical month: list starts at 717 dp of 711; with a trip, two bills and To review: 913 dp; at 200% text: 1,162 dp, about 1.6 screens). The page is a dashboard before it is a list. | `audit_b_typical_w393f10l.png`, `audit_e_trip_and_nudge_w393f10l.png`, `audit_b_typical_w393f20l.png` |
| 2 | High | Three money blocks stacked (month card, budget, safe to spend) = 55% of the screen, and two different "a day" figures 70 dp apart: "about 1,565.44 a day" (from the budget) and "about 4,059.27 a day" (from balances). Nothing says which to trust. | `audit_b_typical_w393f10l.png` |
| 3 | High | The floating Add button covers content: the "Lunch" chip and the Cash tile on the first screen; the amount of the row under it while scrolling ("Rent -24,00..."); at 200% the Welcome button label and the "Tap +" text. | `audit_b_typical_scrolled_w393f10l.png`, `audit_a_empty_welcome_w393f20l.png`, `audit_a_empty_w393f20l.png` |
| 4 | High | At 200% text the account tiles (fixed 196 x 100 dp, strip height stays 112) break: the credit-card tile shows its name and digits but loses its balance (the owed amount); the second tile runs off the right edge with no hint; the last chip is cut. | `audit_g_strip_end_w393f20l.png`, `audit_g_strip_start_w393f20l.png`, `audit_g_strip_end_w360f20l.png` |
| 5 | High | At 200% on 360 dp the "N expenses have no category" card puts its sentence in a one-word-wide column ("6 / exp / ens / es / hav / e no / cat / ego"), 560 dp tall; the bills card is 467 dp and its Open button overlaps the text. | `audit_d_nudge_CATEGORISE_w360f20l.png`, `audit_d_nudge_BILLS_DUE_w360f20l.png`, `audit_sheet_4_phone360_font20_light.png` |
| 6 | High | At 200% entry rows cut to one line: title "BookMySh...", and the second line shows only the date, so account and category vanish. | `audit_g_strip_end_w393f20l.png` |
| 7 | High (fold) | Fold-size window: list pane about 363 dp, first entry at 716 dp of 654: the left pane shows no entries and the right pane says "Select an entry": half the screen is empty, the other half is cards. | `audit_b_typical_w864f10l.png`, `audit_sheet_7_fold.png` |
| 8 | Medium | Seven nudge designs: heights 56 to 229 dp, corner 24 vs 20, three ways to dismiss (X, "Not now", "Hide"), button colours differ (To review is brown-pink). The three tinted cards (nudge, budget, nothing else) have the same weight, so nothing leads. "Also waiting: To review. See More" gives no count. | `audit_sheet_2_nudges_393_light.png` |
| 9 | Medium | Entries: no day totals; the date is repeated under every row although the heading says "Today"; the round tile is the category's first letter, so Food and Fun are both "F", Bills "B". | `audit_b_typical_scrolled_w393f10l.png` |
| 10 | Medium | The credit card, the account with a bill due, is the third tile and off screen; card limit used and due date are not on the Home tile. | `audit_g_strip_start_w393f10l.png` |
| 11 | Medium | Brand-new: a 0.00 month card and a "Set a budget" card sit above the Welcome card; after Skip, two 0.00 gradient tiles and "Tap + to add one" (the button says Add). | `audit_a_empty_welcome_w393f10l.png`, `audit_a_empty_w393f10l.png` |
| 12 | Medium | Hide amounts masks every figure but leaves the budget bar (53%), the graph and "+158%". | `audit_f_hide_w393f10l.png` |
| 13 | Medium | Bottom bar at 200%: labels 24 sp, "Search" and "Reports" almost touch, "Home" is wider than its pill. | `audit_b_typical_scrolled_w393f20l.png` |
| 14 | Low | Month card small text is white at 85 to 90% on a gradient with a bright corner glow: measured about 2.9:1 near the glow, 3.3 to 6.1:1 along the comparison line, so a few places are under 4.5:1. | `audit_b_typical_w393f10l.png` (pixel sampling) |
| 15 | Low | Inconsistent cards: corners 28/24/22/20/18/14/8, gaps 12/12/0/8/4, titles in four type styles; the safe-to-spend line has no container. | all |
| 16 | Low | "Overdue ones are included" is shown when nothing is overdue. | `audit_d_nudge_BILLS_DUE_w393f10l.png` |

What works: no touch target under 48 dp (touch bounds, all 195 shots; chips and close buttons look 40 dp but get 48), nothing but the scrolling strips goes past the edge, dark theme is consistent, one-nudge rule works as documented, the trip card is slim and clear, amounts are right-aligned and coloured.

## 4. The three options
All three: one nudge slot with the existing order in `docs/HOME_NUDGES.md` and ONE look and ONE dismiss control for every nudge; day totals in day headings (money spent that day) and no repeated date under rows; 48 dp targets; "Add" as a round button in the middle of the bottom bar (the tag `add` is kept; a floating alternative is `option_B_typical_floating_add_light.png`); at text 150% and larger the extras drop out (graph, sub-lines), account strip becomes one "Accounts" row, big numbers stop growing at 54 px, nothing is clipped; at fold size the list pane starts with entries.

### A. Calm and minimal ("the list first")
Order: (1) one summary card: spent this month, vs last month, budget bar, "left of 60,000", "about 1,565 a day" (the graph and the balance-based safe-to-spend line leave Home; safe to spend stays one tap away in Budget) (2) one slim nudge row (3) accounts as 48 dp chips (4) quick entries (5) entries by day with totals.
First entry (typical): 414 dp (393) / 418 (360), 3 / 2 entries visible; busy day 516; 200%: 722. Images: `option_A_typical_light.png`, `option_A_typical_dark.png`, `option_A_empty_light.png`, `option_A_empty_dark.png`; more: `option_A_typical_font20_light.png`, `option_A_typical_w360_light.png`, `option_A_busy_light.png`, `option_A_hide_light.png`, `option_A_typical_fold864_light.png`.
Empty: "Nothing here yet", one big Add button, import link, privacy line, no tiles.
Queries: none new. Day totals come from the rows already loaded (needs the amount number added to the row; only complete days get a total: the last loaded day is skipped until the next page or the end of the list arrives).
Effort 4 to 5 days incl. tests; risk low.

### B. Balanced dashboard ("what matters, at a glance") 
Order: (1) month card, shorter, small graph, "+158%" chip (2) two tiles side by side: Budget left (bar) and Safe to spend, each clearly named, which fixes finding 2 (3) trip card with a progress bar (only while a trip is on) (4) one slim nudge row, then "Also waiting: To review (3)" with the count (5) account tiles, smaller (114 x 72), the card tile says "owed, due in 6 d" (6) quick entries (7) entries by day with totals.
First entry (typical): 522 dp (393) / 536 (360), 2 / 1 entries visible; busy 642; 200%: 778. Images: `option_B_typical_light.png`, `_dark`, `option_B_empty_light.png`, `_dark`; more: `option_B_typical_font20_light.png`, `option_B_typical_w360_light.png`, `option_B_busy_light.png`, `option_B_hide_light.png`, `option_B_typical_fold864_light.png`.
Empty: month card, a "Start here" checklist of three steps (first expense, budget, read bank texts), import link.
Queries: none new. Trip progress is arithmetic on the trip total already read while a trip is on; the card tile's due text and owed amount come from the balances pass that already runs.
Effort 6 to 8 days; risk medium (tiles reflow, trip card, tag moves).

### C. Data-rich ("the money desk")
Order: (1) month card with this month vs last month at the same day, budget bar, safe to spend (2) trip card with progress (3) one slim nudge row (4) a row of glance cards that peeks at the right edge: Where it went (category bar and top 3), Coming up (next bills and dues with days), To review (count) (5) account tiles with card limit used and due (6) quick entries (7) entries by day with totals.
First entry (typical): 614 dp / 618 (360), 1 / 0 entries visible; busy 734; 200%: 880 (cards collapse to a "Where it went and what is coming up" row). Images: `option_C_typical_light.png`, `_dark`, `option_C_empty_light.png`, `_dark`; more: `option_C_typical_font20_light.png`, `option_C_typical_w360_light.png`, `option_C_busy_light.png`, `option_C_hide_light.png`, `option_C_typical_fold864_light.png`, and the fold idea `option_C_typical_fold864_dashboard_light.png` / `_dark` (the empty "Select an entry" pane shows the cards until an entry is opened; the list pane then starts with entries).
Empty: month card with two dashed "will appear here" cards, one Add button.
Queries: Where it went: the category breakdown of this period is already computed for the "usual" insight line in `LedgerViewModel.sparkOf` and thrown away (only when the budget period has a "last" name; otherwise one more aggregate over this period only, same cost as the period total, in the slow pass). Coming up: `SafeToSpendUi.rows` already lists tracked bills and repeating items up to the horizon (only exists while safe to spend is computed, i.e. while balances are on Home). Limit used: already in the card summary. None of this is timed [INFERENCE from reading the code, to be confirmed].
Effort 10 to 12 days; risk medium-high (carousel inside the list, two new Customise-home sections that need the layout setting to stay readable by older backups: `HomeLayout.decode` already adds unknown sections at the end).

### Comparison (typical month, 393 dp)
| | Today | A | B | C |
|---|---|---|---|---|
| First entry at (dp) | 717 | 414 | 522 | 614 |
| Entries visible, 393 / 360 dp | 0 / 0 | 3 / 2 | 2 / 1 | 1 / 0 |
| First entry at 200% text | 1162 | 722 | 778 | 880 |
| Busy day (trip, bill, review) | 913 | 516 | 642 | 734 |
| Safe to spend on Home | yes | tap away | yes | yes |
| Graph | yes | no | small | no |
| Where it went / coming up | no | no | no | yes |

Side by side: `option_compare_typical_light.png`, `option_compare_typical_dark.png`, `option_compare_empty_light.png`, `option_compare_empty_dark.png`, `option_compare_busy_light.png`, `option_compare_font20_light.png`, `option_compare_w360_light.png`, `option_compare_hide_light.png`, `option_compare_fold864_light.png`.
Hide amounts in all options: figures become "₹••••"; the proposal also hides bars and the graph's shape (question 6).

## 5. Recommendation
B, built in two steps. Step 1 is common to all options and fixes findings 3 to 13 (one nudge look, 200% layout, Add in the bar, day totals, account strip, empty states, fold pane). Step 2 is B's two tiles, trip progress and smaller tiles. Reasons: it answers the questions you ask a money app at a glance (spent, against last month, left, safe, bill coming) without hiding the safe-to-spend feature, keeps the app's look, puts the first entry about 195 dp higher and two entries on screen, and costs about two thirds of C. A is the safe fallback if you want the list first; C's two cards can later come back as optional Customise-home sections, off by default.

## 6. Tests and performance checks a build would need
- Host: `HomeLayout` decode of old layouts with the new sections; day totals (complete days only, expense-only, other currency, page boundary); nudge order unchanged (`HomeNudgesTest`).
- Device (API 34, and API 26 for the keyboard dialog): first entry visible within a set dp at 393 and 360 with no nudge and with one; 48 dp touch targets; 200% text: no clipped balance, no one-word column, nothing wider than the window (extend `AccessibilityUiTest`, `TripModeSizesUiTest` pattern); Hide amounts (`PrivacyUiTest`); Customise home; two-pane (`WidePaneUiTest`); Add in the bar (the tag `add` is used in 102 test files, `home_trip_card` 16 uses, `budget_summary`, `balance_chip_`, `month_spend` 36 uses: keep the tags); iPhone UI tests; dark and light screenshots; TalkBack and VoiceOver reading order.
- Performance: `RefreshBudgetTest` (new row and total within the 50 ms phone budget, rest within 200 ms) and `PerformanceBudgetTest`, A/B against the base commit as in `docs/PERFORMANCE.md` (6 alternating runs, 30% threshold), cold start and a scroll over 100,000 entries. No option adds a query to Home open (see above); the check proves it.

## 7. Questions for you
1. Add button: floating (today) or in the middle of the bottom bar (the mock-ups)?
2. Two "a day" numbers (from the budget, and from balances): keep both, labelled (B, C), or show only one?
3. Keep a graph on Home (small in B, none in A and C)?
4. Show whole rupees (₹31,822) on big summary figures, with .00 kept in the entries (the mock-ups do this)?
5. Day total: money spent that day only, or income too?
6. Hide amounts: also hide the bars and the graph shape?
7. Accounts on Home: tiles, chips, or one "Accounts" line; do you tap an account to filter the list?
8. Do you want "Where it went" and "Coming up" on Home at all, or leave them in Reports and Bills?
9. At 150% text and more, OK to drop the graph and small lines?
10. Fold: show the summary cards in the empty entry pane (C's fold image)?
11. Category tile: keep the first letter, or use an icon or colour per category?
12. First run: should the 0.00 month card and "Set a budget" show above Welcome at all?

## 8. Decisions taken (Slice 112: Option B built in two steps)
The owner approved building B and answered Q4 ("follow the mock-ups: whole rupees on the headline figures"); the other eleven were answered by the builder with the doc's recommendations, conservatively. Anything risky stayed off.

| # | Question | Decision and why |
|---|---|---|
| 1 | Add button | In the middle of the bottom bar (tag `add` on Home; `bar_add` on Search and Reports, where it leaves the page first, as Ctrl+N does). The floating button covered the Lunch chip, the Cash tile and the amount of the row under it. |
| 2 | Two "a day" figures | Both stay, side by side as two tiles, each saying its source: "about ₹1,698 a day from your budget" and "about ₹8,003 a day from your balances". Safe to spend is the feature people may use most; hiding it would have been a loss. |
| 3 | Graph | A small one (40 dp) stays at normal text. It goes at 150% text and up, in a two-pane list pane (the entries must start on its first screen), and while Hide amounts is on. |
| 4 | Whole rupees | Owner's answer: yes on the Home SUMMARY headline figures and tiles only: month spent, budget left and its limit and a-day figure, safe to spend and its a-day figure, the account tiles' balances, the "more than last month" sentence. Exact paise stay in list rows, the day totals, the entry, Search, reports, the trip line, every edit field and in the trip budget line. Rules (`WholeAmounts`, host-tested): half up on the size of the amount; an amount that would read zero but is not zero keeps its paise; compact figures and non-amounts are untouched; only the drawn text is rounded. The exact amount is what a screen reader says and what the tests read (the node's text), so rounding can never be the only thing the app says. |
| 5 | Day total | Money SPENT that day only: expenses, in the ledger's currency, complete days only (the oldest loaded day waits for the next page). Income, refunds, transfers, settlements and other currencies are not in it. |
| 6 | Hide amounts and bars | Hides them too: the budget bar, the trip bar and its percent, the graph, the change chip, the card limit bar. A bar's length is the figure. Every figure is masked as before. |
| 7 | Accounts | Smaller tiles (112 x 76 dp, whole units, a card says "owed · due in 6 d" and has a thin limit bar when a limit was entered) at normal text; one "Accounts" row that opens into 48 dp rows at 150% text and up. Tapping an account still narrows Home to it. |
| 8 | Where it went / Coming up | Not on Home (Reports and Bills keep them). That is option C. |
| 9 | Drop the graph and small lines at 150%+ | Yes: no graph, no insight line, no card limit bar, nudge buttons under the words, the big figure stops growing, rows put the amount under the title. |
| 10 | Fold: summary cards in the empty detail pane | No. The fold's "empty left list" was the consequence of a 717 dp top block in a 654 dp pane, not a layout bug: the top block is now about 490 dp there (no graph in the list pane), so the first entry is on the first screen. |
| 11 | Category tile | Unchanged: the category's first letter. |
| 12 | First run | Unchanged: the month card and the "Set a budget" card show above Welcome. Changing it touches a first-run path many tests use; it can come later. The "Start here" checklist of B's empty state was not built. |

Also decided: the setup nudge's close puts it off for a day and a bar then offers "Hide for good" (the owner accepted this as the one-close-button design); Getting started's close hides it for good, as its Hide did. B's budget/safe tiles come right under the month card while Budget is the first block of the person's Customise-home layout (the default); moved lower, they stay where the person put them.

### Measured: where the first entry starts (dp from the top of the list; the screen shows 711 dp at 393, 644 at 360, 654 in the fold pane)
Method: a throw-away instrumented test (not committed) seeds the audit's states and reads the unclipped height of the Home top block (the tag `home_top`); the first entry row is that plus the day heading (about 40 dp). Debug build, API 34 arm64 emulator, invented data; "before" is origin/main 19f2582, "after" is this slice. The "typical" state has one card bill due (one nudge), like the audit's. Top block height:

| State | 393, 100% | 393, 200% | 360, 100% | 360, 200% | Fold 864 |
|---|---|---|---|---|---|
| Typical (bill due) before | 677 | 1090 | 690 | 1150 | 677 |
| Typical after | 537 | 1021 | 534 | 1017 | 489 |
| Bill due (2 bills) before | 723 | 1234 | 736 | 1262 | 723 |
| Bill due after | 537 | 1021 | 534 | 1017 | 489 |
| Trip + bill + 3 to review before | 827 | 1397 | 840 | 1424 | 827 |
| Trip + bill + 3 to review after | 664 | 1234 | 660 | 1261 | 616 |
| Four things waiting before | 771 | 1330 | 784 | 1358 | 771 |
| Four things waiting after | 585 | 1117 | 582 | 1113 | 537 |
| Hide amounts before | 715 | 1128 | 720 | 1156 | 723 |
| Hide amounts after | 471 | 928 | 468 | 956 | 471 |

So the first entry at 393 dp, 100% text (typical with one nudge) moves from about 717 dp to about 577 dp (140 dp, 20%), and with the bill nudge put away from about 515 dp [INFERENCE: the nudge row is 62 dp + its gap; not measured separately], close to B's 522 dp. The mock-ups used no decimals in tiles and a lighter nudge; the reflowed text under whole rupees is the same size. At 200% text the page is still longer than a screen (1021 dp): each block is one full-width line or two, by design.
