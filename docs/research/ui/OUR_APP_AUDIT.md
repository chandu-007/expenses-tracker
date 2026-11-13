# Our app's UI/UX audit (0.13.0, origin/main b899430): where it is strong, where it is not yet fluid or delightful

Audit only: no product code was changed. All images, clips and strips are OUTSIDE the repository in `~/Documents/personal/expenses_builds/ui_research/audit/` (about 900 screenshots, 24 clips and strips, 4 JSON/CSV-like result files). They are named `screen_state_config.png`. Config key: `w393f10l` = 393 dp wide, text 100%, light; `d` = dark; `f20` = text 200%; `w360` = 360 dp; `w864f10l` = fold-size window 864 dp (two panes). Example: `reports_period_w393f10d.png`.

## 0. How it was made, and its limits
- Debug build of origin/main in a clean worktree, API 34 arm64 emulator (cold boot, `tools/emulator_setup.sh`, animation scales 1), under `/tmp/emu.lock`. A throw-away instrumented test (NOT committed) seeded INVENTED data and walked ~120 screens/states; every config shot was read through contact sheets (the 393 light set in full, the other four configs for Home, Add, Entry, Search, Reports, Accounts/Budget/Bills/Categories/Recurring, More/Appearance/Data/Notifications).
- Configs: 393 light (all 12 groups), then 393 dark, 393 at 200% text, 360 dp, 864 dp fold window for Home, Add, Entry, Search, Reports, Money screens, Settings. Fold window as built by `wm size 2268x2176` leaves the launcher dock visible under the app; two-pane behaviour is real, the bottom strip is an emulator artefact.
- Fluidity: `dumpsys gfxinfo app.expenses` (reset before each scripted interaction; 3 repetitions; 1,500-entry ledger, trip on, bills, To review) plus `screenrecord` clips turned into frame strips by ffmpeg (`strip_*_relaxed.png`, `strip_*_quick.png`, clips `clip_*.mp4`). **The emulator draws in software: every frame is slow (a plain slow scroll of Home measured p50 23 ms, p95 34 ms, 5.6% janky). Read the numbers RELATIVELY, against that baseline.** `framestats` keeps only the last 120 frames, so the tables use gfxinfo's own summary (all frames) and note the tail.
- Not done / limits: no real phone; iPhone not audited (shared Compose code, same findings apply, iOS-specific behaviour untested); widget 4x2 and 4x4 could not be placed reliably through the launcher (Today 2x2 was: `widget_today_and_4x2_light.png`, masked by default; earlier design renders exist in `~/Documents/personal/expenses_builds/widget_shots/`); Home was re-checked as built (Option B) against `docs/HOME_OPTIONS.md`, not re-audited from scratch; contrast reused from that doc (not re-sampled); recomposition findings from reading code are marked [INFERENCE]; real "time to first entry" by a person was not timed (scripted floor only).
- My seed is harsher than a real person's on Home: six overdue repeating items ("Waiting", plain rows) and a bill nudge are present; that pushes entries down more than a typical user's Home would. Where it matters it is said.

## 1. Executive summary: the 10 biggest UI weaknesses, ranked

1. **The Add / Edit form is the weakest, most-used screen.** One long scrolling form (about 2.5 screens): kind chips wrap to an orphan row ("Lent or borrowed"), then Amount, a tiny "Pad" text link, date chip, then 23 category chips, then "Paid from", then four optional options each as a lone outlined chip on its own row, then Note, Tags, Receipts, "Save as a quick entry". With the app's own keypad up, only about 35% of the screen is form and the Category chips are cut off behind it (`add_expense_empty_w393f10l.png`, `add_expense_empty_scroll_w393f10l.png`, `add_number_pad_w393f10l.png`). Note and tags are below the fold. On a fold-size window the form is one very wide column with a 1,000 dp keypad (`add_expense_empty_w864f10l.png`). No amount-first "big number + categories" layout like the market leaders.
2. **Opening the entry form hitches before it moves.** Home to entry: the row ripples, then about 430 ms of nothing, then the transition runs (`strip_home_to_entry_open_relaxed.png`; gfxinfo: p99 300 ms, one frame 417-427 ms in both relaxed and quick motion). Add open has the same 300-400 ms frame (`strip_add_open_relaxed.png`). Cause by reading the code: the whole form (about 25 chips with avatars, 13 FlowRows) is composed eagerly in one frame inside a non-lazy `Column(verticalScroll)` and one `draft` state is recreated per keystroke [INFERENCE for the per-keystroke part]. The hitch does not shrink when motion is set to "Quick": it is composition, not animation.
3. **Everything moves slower than it needs to: the default motion speed is "Relaxed" = 1.8 x.** `Motion.scale = 1.8`: Add form glide 828 ms, screen push 576 ms, tab change 396 ms. Measured visible change: Add opens in 833 ms (relaxed) vs 467 ms (quick); Reports/More/Accounts 367-433 ms vs 167-200 ms (`clip_*_relaxed.mp4` vs `clip_*_quick.mp4`). Together with finding 2 the app feels laggy where it should feel instant.
4. **No read-only entry view; tapping an entry opens the editor.** "Edit expense" with Save, 23 category chips and the keyboard-less amount; history, refund and "where this came from" are below the fold (`entry_edit_top_w393f10l.png`, `entry_edit_s2_w393f10l.png`, `entry_history_w393f10l.png`). A person who just wants to look sees a form.
5. **Inconsistent chrome across ~35 screens.** Back is always a text button "Back", but headers differ: tinted bar with coloured icon chip (Bills, Calendar, Accounts, Capture), plain bar (Reports, Search, History), `Cancel` for forms, `Close` for setup, an X in sheets; the floating "+" on Accounts/Categories/Recurring is a small pale square that covers content (`accounts_card_detail_s1_w393f10l.png`) while Add everywhere else is the bar's middle button. Corners used: 14 different radii. Section 5.
6. **Budget, Tags and Goals are hard to find or thin.** Budget has no row in More (only the Home card or the guide); the Budget screen leads with "Carry over" and five period chips before any limit, and lists 23 categories as "No limit" rows (`budget_main_w393f10l.png`, `budget_main_s2_w393f10l.png`). There is no Tags screen at all (tags only through an entry or Search filters). Goals are a plain list with text links "Add or take out / Edit / Remove" (`goals_list_w393f10l.png`). Categories are reordered with 42 dp up/down triangles and show counts but no spend (`categories_list_w393f10l.png`).
7. **Large text (200%) still breaks a few cards.** More's "Back up your expenses" nudge collapses to one word per line ("Bac / k up / your / exp / ens / es") (`more_top_w393f20l.png`); Reports amounts wrap ("₹1,85,000 / .00" in the Income tile, `reports_period_w393f20l.png`); Add's "Attach a photo or file" becomes a tall oval (`add_expense_filled_scroll_s1_w393f20l.png`); Search summary and chips row clip ("Inc", `search_results_w393f20l.png`). Home itself and the tiles are fine at 200% (the Slice 112 work held).
8. **Two things appear with no motion or a plain placeholder.** The Filters panel (a "sheet") appears and disappears in one frame (gfxinfo: 3 frames on close; `strip_filters_open_relaxed.png`), no slide, no scrim fade. Reports shows plain centred "Loading…" text for several frames when opened with 1,500 entries (`strip_tab_reports_to_more_relaxed.png`, first cells). Add-form close leaves the keypad panel hanging on screen for about 0.6 s while Home fades in under it (`strip_add_close_relaxed.png`).
9. **Density and hierarchy of text-heavy screens.** Your data, Notifications, Privacy, Capture, Currency, Appearance, Earlier spending are long explanatory paragraphs under every control (`data_page_s2_w393f10l.png`, `notifications_settings_s1_w393f10l.png`, `currency_list_w393f10l.png`). Honest and clear, but read as walls; the owner's feeling of "not delightful" is partly this tone and partly the lack of any illustration, preview or summary on settings pages (Appearance has no live preview, only 6 accents).
10. **Delight is concentrated on Home and the first minute; the rest is functional.** Home (hero, rolling total, glass bar, day totals) and the Welcome are good; Reports charts are basic (one donut, one 6-bar chart, one list; no trend line, no per-category drill chart), no haptic/animation on Goals, Budget bars or Categories, no swipe actions on Home rows except delete, no widgets preview in the app. Empty states are good on Home, Bills, Recurring, Inbox (illustrated) but plain text on Search/Reports with no data.

Also notable: the guided tour has 25 stops with long bullets (`guide_step01..25_w393f10l.png`); it highlights real screens (good) but is long.

## 2. Per-screen scores (1-5; H = hierarchy, C = consistency, D = delight, Cl = clarity, F = fluidity; F from measurements where one exists, else from reading)
| Screen | H | C | D | Cl | F | Top 3 fixes | Evidence |
|---|---|---|---|---|---|---|---|
| First launch, Guide tour | 3 | 4 | 4 | 3 | 4 | Cut 25 stops to ~8 or make the rest "tips"; shorten bullets; keep the live-screen spotlight | `guide_step01_w393f10l.png` ... `guide_step25` |
| Welcome / first entry | 4 | 3 | 4 | 5 | 4 | Hide the 0.00 month card and "Set a budget" above Welcome; after first entry show the 5-step card collapsed; keep 3-step path | `welcome_start_w393f10l.png`, `home_empty_welcome_w393f10l.png` |
| Guided capture setup, Why isn't capture working | 4 | 4 | 3 | 4 | 4 | Shorten paragraphs; status cards are the right pattern, reuse elsewhere; one primary button per step | `capture_setup_step1_w393f10l.png`, `capture_checkup_w393f10l.png` |
| Home typical | 4 | 3 | 4 | 3 | 3 (fling janky 9.6%) | First entry still ~1 screen down with a nudge and Waiting items; show Waiting as one slim row; unify "Also waiting" and Waiting list style with NudgeCard | `home_typical_w393f10l.png`, `home_typical_s1_w393f10l.png` |
| Home empty / heavy / trip / hide | 4 | 3 | 4 | 4 | 3 | Empty: wallet art is nice, add 3 starter chips; Hide: fine | `home_empty_w393f10l.png`, `home_heavy_`, `home_trip_`, `home_hide_` |
| Home selection / bulk bar | 3 | 3 | 2 | 4 | 4 | Icons on bulk actions; a count chip; haptic on select | `home_selection_mode_w393f10l.png` |
| Add expense | 2 | 2 | 2 | 3 | 2 (300-400 ms frame) | Amount-first layout with category row visible above the keypad; collapse optional chips into one "More" row; lazy/short category row (top 8 + More); note field above tags | `add_expense_empty_w393f10l.png`, `add_expense_filled_scroll_s1_w393f10l.png` |
| Add income / transfer / loan / split / tags / expect-back / foreign | 2 | 3 | 2 | 3 | 3 | Same grid; move "Paid in another currency / Split / expect back" under one "More options"; Transfer needs a swap button | `add_income_`, `add_transfer_`, `add_loan_`, `add_split_friends_`, `add_tags_added_`, `add_expect_back_`, `add_foreign_currency_` |
| Entry edit / history | 2 | 3 | 2 | 3 | 2 (430 ms gap) | A read-only detail first (big amount, category, account, notes, Edit button); history inline | `entry_edit_top_w393f10l.png`, `entry_history_w393f10l.png` |
| To review (inbox) | 4 | 4 | 4 | 3 | 4 | Cards are tall (8 controls each); show Add/Skip only, move Expense/Income/Transfer under Change | `inbox_cards_w393f10l.png` |
| Categorise | 4 | 4 | 3 | 5 | 4 | Good one-at-a-time flow; add progress bar | `categorise_start_w393f10l.png` |
| Search start/results | 4 | 4 | 3 | 4 | 3 (results scroll 43% janky) | Highlight is good; lighten result rows (they carry shared-element modifiers); show total chip | `search_start_w393f10l.png`, `search_results_w393f10l.png` |
| Search filters panel | 3 | 3 | 2 | 4 | 1 (no motion) | Animate the sheet; group chips in two columns; show active count on tabs | `search_filters_panel_w393f10l.png`, `strip_filters_open_relaxed.png` |
| Search selection / bulk | 3 | 3 | 2 | 4 | 3 | As Home | `search_select_all_bulk_bar_w393f10l.png` |
| Reports (every tab) | 3 | 4 | 3 | 4 | 3 | Plain "Loading…" -> skeleton; period chips are 2 rows of 4+4 chips before any chart; tab/mode as segmented control; trend line | `reports_period_w393f10l.png`, `reports_by_account_`, `reports_by_tag_`, `reports_by_merchant_`, `reports_mode_year_`, `reports_mode_fy_`, `reports_mode_custom_` |
| Calendar | 4 | 4 | 4 | 4 | 4 | Heat colouring by amount; fine | `calendar_month_w393f10l.png` |
| Bills / Repeating | 4 | 3 | 3 | 4 | 3 | Repeating "Waiting" rows are plain text, not cards like Bills; the "+" FAB style | `bills_list_w393f10l.png`, `recurring_list_w393f10l.png` |
| Accounts / Fix balance / New account | 3 | 2 | 4 | 3 | 3 | Stacked cards are lovely but the card detail is 9 dp-ish text lines; FAB covers VISA/amount; "Stack them" link | `accounts_list_w393f10l.png`, `accounts_card_detail_s1_w393f10l.png`, `accounts_new_account_w393f10l.png`, `fix_balance` not captured (nav failed) |
| Budget | 2 | 3 | 2 | 3 | 3 | Limits first, period settings after; a ring or progress summary on top; rows look like text, not buttons | `budget_main_w393f10l.png`, `flow_set_budget_*` |
| Categories / Suggested / Edit | 3 | 3 | 2 | 4 | 3 | Drag to reorder, spend per category, bigger arrows | `categories_list_w393f10l.png`, `categories_suggested_w393f10l.png` |
| Subscriptions | 4 | 4 | 3 | 5 | 4 | "About ₹768 a month" headline is right; add logo tiles | `subscriptions_list_w393f10l.png` |
| Transfer candidates | 4 | 4 | 3 | 4 | 4 | Fine | `transfer_candidates_list_w393f10l.png` |
| Travel mode (list, form, summary) | 4 | 3 | 3 | 4 | 4 | Summary note paragraph is long; chart bars good | `trips_summary_top_w393f10l.png`, `trips_form_w393f10l.png` |
| Lent and borrowed / People | 4 | 4 | 3 | 4 | 4 | Fine; give people avatars with colour | `loans_list_w393f10l.png`, `loans_person_detail_w393f10l.png` |
| Goals | 2 | 3 | 2 | 3 | 3 | Ring/arc per goal, icon, bigger numbers | `goals_list_w393f10l.png`, `goals_new_w393f10l.png` |
| Backups / Your data / restore | 2 | 3 | 1 | 3 | 4 | Split into Backup / Export / Import cards with one sentence each; keep full text behind "Details" | `data_top_w393f10l.png` ... `data_page_s4` |
| More (settings hub) | 3 | 4 | 4 | 3 | 4 | 28 rows; add Budget; group Settings vs Tools; fix 200% nudge card | `more_top_w393f10l.png`, `more_page_s1..s4` |
| Appearance / Customise home / Currency / App lock | 3 | 4 | 2 | 4 | 4 | Live preview, more accents, dynamic colour; Currency long warning in red | `appearance_top_w393f10l.png`, `home_layout_top_`, `currency_list_`, `security_app_lock_` |
| Capture, senders, rules, learned | 3 | 4 | 3 | 3 | 4 | Sections are many; collapse to one "Capture" card + checkup | `capture_top_w393f10l.png`, `capture_rules_` |
| Notifications settings | 3 | 4 | 2 | 4 | 4 | Shorten help text; group by kind | `notifications_settings_w393f10l.png` |
| Guide opened from More | 4 | 4 | 4 | 4 | 4 | See tour | `guide_opened_from_more_w393f10l.png` |
| Widgets (Today 2x2) | 3 | 3 | 3 | 3 | n/a | Masked by default with a big empty card: show a small sparkline or the last entry | `widget_today_and_4x2_light.png` |

## 3. Tap counts for the six key tasks (measured by scripting real taps; flow shots `flow_*_w393f10l.png`)
| Task | Taps | Path | Note |
|---|---|---|---|
| Add an expense | 3 (+3 digit keys) | Add, type 250, Save | Category preselected (last used); a trip chip appears automatically. Quick-entry chip: 1 tap. Choosing another category: +1 after closing the keypad (+1). `flow_add_expense_*` |
| Find last month's food spend | 2 | Reports, earlier-period arrow; "Food ₹5,934 37%" is in the donut legend and rows | Search cannot do "last month" (only "This period / Last 30 days / This year" chips); `flow_last_month_food_*` |
| Fix a wrong category | 3 (+1) | tap entry, tap category chip, Save, then the "Same as X?" dialog (Not now / Stop asking / Review one by one / Move all 4) | Smart but heavy dialog; bulk way from Home: long-press, Set category, choose = 3. `flow_fix_category_*` |
| Pay a bill | 3 from the Home nudge Open (4 via More, Bills) | Bills, Mark paid, Record and mark paid | Snackbar has no Undo. `flow_pay_bill_*` |
| Set a budget | 3 (+ typing) | Home "Set", tap "Everything" row, type, Save | Then Back to Home. `flow_set_budget_*` |
| See where money went | 1 | Reports tab | Donut + category list immediately. `flow_where_money_went_1_reports_w393f10l.png` |

First run: cold start 670-795 ms (debug build, `am start -W`, 5 starts); Welcome button, type 250, Save = 3 taps; scripted floor from tap on "Add my first expense" to Home showing ₹250: about 3.6 s including two uiautomator dumps (so ~2.5 s of pure app/animation); the month total (time to insight) is on Home the moment the entry is saved. A person will take roughly 10-15 s [INFERENCE].

## 4. Fluidity (emulator, software drawing; relaxed = the shipped default motion; 3 repetitions each; summary = all frames of the interaction, janky = gfxinfo's own count)
| Interaction | Frames | Janky | p50 / p90 / p95 / p99 ms | Visible change (clip) | Verdict |
|---|---|---|---|---|---|
| Home slow scroll (baseline) | 839 | 5.6% | 23 / 31 / 34 / 40 | - | the emulator's floor |
| Home fling x10 | 1690 | 9.6% | 30 / 46 / 53 / 65 | - | OK |
| Home to entry (open) | 75 | 52% | 48 / 250 / 250 / 300 (one 417 ms frame) | 400 ms after a 430 ms gap | **cliff** |
| Entry close | 92 | 26% | 38 / 73 / 109 / 109 | 333 ms | slightly long frames |
| Add open | 126 | 47% | 46 / 101 / 109 / 300 | 833 ms | **cliff** (+ keypad pops in) |
| Add close | 134 | 48% | 46 / 69 / 85 / 113 | 733 + 200 ms | keypad lingers |
| Tab Home to Search | 100 | 38% | 44 / 73 / 150 / 150 | 567 ms | long frames at keyboard + page |
| Tab Search to Reports | 164 | 18% | 25 / 46 / 53 / 113 | 367 ms ("Loading…" first) | OK, placeholder |
| Tab Reports to More | 174 | 7.5% | 24 / 40 / 65 / 250 | 433 ms | one long frame |
| Tab More to Home | 177 | 9% | 26 / 36 / 53 / 109 | 267 ms | OK |
| Search typing "swiggy" | 428 | 18.5% | 36 / 53 / 65 / 77 | - | OK |
| Search results scroll | 376 | **43%** | 36 / 53 / 61 / 85 | - | heavier than Home rows |
| Filters panel open | 155 | 11.6% | 17 / 25 / 34 / 109 | **1 frame (no animation)** | hard cut |
| Filters panel close | 3 | 100% | - | **no animation** | hard cut |
| Reports scroll | 653 | 16.7% | 30 / 44 / 48 / 53 | - | OK |
| Reports By period/By year/Merchant switch | 300 | 6.7% | 22 / 38 / 42 / 69 | - | good |
| More scroll | 822 | 14% | 25 / 48 / 48 / 57 | - | OK |
| More to Accounts | 98 | 22% | 34 / 48 / 77 / 81 | 367 ms | OK |
| Home to Budget | 99 | 21% | 29 / 53 / 121 / 129 | 267 ms | one long frame |

With motion set to Quick (same device): Add open 467 ms, Reports/More/Accounts 167-200 ms, tab change 533 ms (Home to Search is keyboard-bound); the long first frames (entry open p99 300 ms, Add open p95 300 ms, Home to Search p95 150 ms) stay, which proves they are composition cost. Files: `fluidity_relaxed.json`, `fluidity_quick.json`, strips `strip_*_relaxed.png` / `strip_*_quick.png`.

Animations that exist (code count): `animate*AsState` 18, `AnimatedVisibility` 12, `AnimatedContent` 4 (the screen host), `animateItem` 22 (rows), `Animatable` 16, container transform (`sharedContainer`) on Home rows (4 uses), rolling total, glass tab pill with spring, press scale on bar items, haptics on To review swipe and bar. Missing or hard cuts: Filters panel (no enter/exit), Reports "Loading…" text (no skeleton), keypad leaves late on Add close, Search-to-Reports tab content swap shows a blank page first, no predictive-back preview (`BackHandler` only), no swipe actions beyond delete on Home rows, no shared-element from the entry list to the *detail* because there is no detail, dialogs (Budget limit, New account, New category, Goal) appear without scale/fade [INFERENCE from the platform AlertDialog default; not clipped]. Recomposition / overdraw hot spots by reading: `EntryForm` (eager Column, 13 FlowRows, ~25 chips with avatar tiles, one `draft` value copied per change so every chip lambda changes); `Home` hero `HeroGlow` (two 48/40 dp RenderEffect blurs inside the first LazyColumn item); `sharedContainer` is a `composed {}` modifier on every Home row (already known, `docs/PERFORMANCE.md`); Search results rows carry the same modifier [INFERENCE for the cost]; one giant `UiState.Ready` with 40 fields means any change re-evaluates Home (strong skipping hides most of it) [INFERENCE].

## 5. Inconsistency inventory
- **Corner radii in code**: 14 distinct values (16 x20, 12 x14, 20 x12, 24 x7, 8, 28 x5, 14, 22, 21, 18, 10, 6, 3, 2). Cards seen: 28 (hero, Reports hero), 24-22 (nudges, Home tiles, More tiles), 20 (list cards, rows), 14 (category chips, More icon tiles), 12-16 (inputs), 8.
- **Card styles on screens** (about 9): gradient hero (Home, Reports); tinted nudge/tile (Home money tiles, nudges); entry card group (rounded group of rows); More list card with gradient icon tiles; coloured More nudge tiles (green/purple/blue); Bills card (tinted by urgency); account gradient card; plain divider list rows (Budget, Categories, Repeating, Goals); trip summary grey card.
- **Buttons**: filled `Button` 55, `OutlinedButton` 97, `TextButton` 220, `FilledTonalButton` 2, text-link "Pad"/"Stack them"/"Back"/"Cancel", pill nudge buttons; the primary action is a pill on some screens and a full-width bar on others (Add Save full width; Bills "Add to my bills" pill-wide; Home "Add" round pill).
- **Chips**: `FilterChip` 94 (selected = grey, not accent: low contrast between on/off in `add_expense_empty_w393f10l.png`), `AssistChip` 18, outlined buttons used as chips (Goal accounts, "Paid in another currency"), category chips with letter avatars (round 14 dp), square-ish quick-entry chips.
- **Dialogs / sheets**: `AlertDialog` 40 (grey surface, 28 dp, text buttons), custom `Dialog` 100 (Safe to spend, Guide card), Filters panel (custom sheet, not `ModalBottomSheet`, 0 uses), Add/Edit form (custom sheet look: drag handle + Cancel), pay-bill dialog, similar-entries dialog with four text buttons. At least 5 visual styles for sheets/dialogs.
- **Headers**: tinted `AreaBar` with icon chip (Bills, Calendar, Accounts, Capture, Appearance...) vs plain `TopAppBar` (Reports, Search, History, Edit category) vs form bars ("Cancel").
- **Type scale**: 12 MaterialTheme styles used, `bodySmall` 241 times (long 12 sp captions everywhere), two literal sizes (10 sp, 12 sp). Day headings, section labels ("MONEY", "Category", "Account") use 3 different label styles.
- **Icons**: outlined Material icons in More; letter avatars on entries; emoji-free; no category icons (letters only: Food/Fun both "F").
- **Switches/selection**: 14 Switch, 10 Checkbox, 2 RadioButton: segmented choices (Theme, Period, Motion) are chip rows, not segmented buttons.

## 6. Quick wins (<1 day), medium, big
**Quick (< 1 day each)**
1. Default motion to a faster setting (scale 1.0-1.2): halves transition times, no layout change (`Motion.scale`, Appearance default).
2. Animate the Filters panel (slide + scrim fade); replace Reports "Loading…" with the screen's skeleton or keep last content.
3. Close the keypad panel with the Add form (same transition, not after).
4. Fix the 200% text cases: More backup nudge (stack buttons under text like `NudgeCard`), Reports Income tile (no mid-amount wrap), Add "Attach" chip, Search summary row.
5. Add Budget to More; add icons to bulk bar actions; make "Pad" a real toggle ("Number pad / Keyboard") with an icon.
6. One header style: give Reports/Search/History the tinted `AreaBar`, or remove tint everywhere; replace the Accounts/Categories floating "+" by the bar's Add style or an extended FAB placed above the content padding so it never covers amounts.
7. Make the selected `FilterChip` accent-tinted (stronger on/off contrast).
8. Group the four optional Add chips into one "More options" row.
9. Shorten the Fix-category "Same as X?" dialog to two buttons (Move all 4 / Not now) with the rest in a "More" link.

**Medium (days)**
1. Add form re-layout: amount + category row above the keypad, categories as one horizontal row of top 8 + More, optional sections collapsed; lazy composition of the rest. Expected to remove the 300-400 ms first-frame hitch.
2. Read-only entry detail with an Edit button (reuses Search/History data); history inline.
3. Budget screen: limits first, ring summary, period settings in a sheet; Categories: spend per category, drag reorder.
4. A real segmented control for Reports mode/group (two rows of eight chips become two segmented rows), trend line chart.
5. Shorten the 25-stop tour to ~8 stops and move the rest to "tips"; one-sentence page summaries with "Details" expanders on Your data / Notifications / Capture / Currency.
6. Search results rows: drop `sharedContainer` composed modifier except on the tapped row (already listed in `docs/PERFORMANCE.md`).
7. Fold size: Add/Edit form beside the list in the detail pane, a keypad no wider than ~480 dp, summary on the empty detail pane.

**Big (a week or more)**
1. Design system pass: one card, one button hierarchy, one sheet/dialog style, 4 corner radii, 6 type styles, then migrate the ~35 screens (this is the real fix for finding 5).
2. Category icons (not letters) and colours across entries, charts and chips; richer Reports (trend, top merchants, budget vs actual, goal progress).
3. Predictive back with shared-element previews; swipe actions on rows (edit/categorise); widget previews and a more informative masked widget.

## 7. Already great: keep
- Home as built (Option B): one slim nudge with one close, day totals in headings, whole-rupee headline figures with exact amounts for screen readers, hide-amounts that hides bars too, trip card, account strip, Add in the middle of the bar; works at 200% and 360 dp (`home_typical_w393f20l.png`, `home_nudges_many_w360f10l.png`).
- Glass tab bar with spring pill and press scale; consistent bar on Home, Search, Reports, More.
- First run: Welcome, 3-step first entry, "Added expense ₹250.00 Undo", Getting started card, honest privacy copy.
- To review cards and Categorise one-at-a-time flow; "Why isn't capture working?" status cards; guided capture setup.
- Reports hero card and category rows with share bars; Calendar heat view; Subscriptions headline; Transfer candidates; Bills cards tinted by urgency; Mark paid dialog with the transfer-to-card explanation; Trip summary with daily bars.
- Search: box + Filters + one chip row, summary line with count and spent, term highlight, saved searches, no-results help (`search_start_w393f10l.png`).
- Touch targets: no clickable under 48 dp except the Categories up/down triangles (58 x 42 dp visible bounds, Compose pads to 48 dp) in a scan of 14 screens (`touch_targets_w393f10l.json`); dark theme is coherent (`home_typical_w393f10d.png`, `more_top_w393f10d.png`); one-nudge rule; undo everywhere.
- Accessibility attention: semantics hold exact amounts, `CappedFontScale` on the hero figure, label-shrinking bar.
