# UI backlog: making Expenses look, move and explain money better than the market (design and synthesis only)

Status: design document, 2026-11-14, written against origin/main `3750bd0` (0.13.0 + the four UI research docs). **No product code, version, emulator, Gradle or Xcode was touched.** Nothing in this file has been run on a phone.

What this merges: `docs/research/ui/OUR_APP_AUDIT.md` (our own screens, ~900 screenshots, fluidity numbers), `GLOBAL_APPS.md`, `INDIA_APPS.md`, `CRAFT.md`, plus `docs/HOME_OPTIONS.md`, `HOME_NUDGES.md`, `TRAVEL_MODE.md`, `PERFORMANCE.md`, `research/OUR_FEATURES.md`, `research/COMPETITOR_ANALYSIS.md`. Where two reports say the same thing the item cites all of them.

**Mock-ups** live OUTSIDE the repository (the owner opens them): `~/Documents/personal/expenses_builds/ui_vision/`. Start with `00_index_contact_sheet.png`. Every file has a `_light` and (where it matters) a `_dark` version. "Today" phones in them are real emulator screenshots from the audit; "proposed" phones are drawn mock-ups in the app's real theme (Material 3 baseline, violet `#6750A4`, Option B Home look, invented data, INR with lakh grouping). They are pictures, not builds.

**Citation labels.** `AUDIT W1..W10` = the ten weaknesses in the audit's section 1; `AUDIT QW` = its quick-win list; `CRAFT #n` = CRAFT's top-20 rank; `GLOBAL L#` / `GLOBAL I#` = GLOBAL's 15 lessons / 50 "wow" ideas; `INDIA L#` / `INDIA I#` = INDIA's 15 lessons / 36 ideas; `HO f#` = HOME_OPTIONS finding. Screenshot names are in `~/Documents/personal/expenses_builds/ui_research/audit/` (`screen_state_config.png`, `w393f10l` = 393 dp, 100% text, light).

**Evidence quality in one paragraph (read before trusting any number).** The audit measured our app on a software-drawing API 34 emulator (every frame is slow there; numbers are only good *relatively*). No researcher saw a competitor's motion on video (motion and haptic claims about other apps come from open-source code or documents). No idea in this file has been tried on a phone, and nothing was measured on an iPhone at all. Durations, thresholds and colours proposed here are **[INFERENCE]**: starting values to be felt and then tuned. What is solid: the screenshots, the audit's measured gaps, and the code facts read in this repo (e.g. `Notifier.kt`, `Motion.kt`, `HomeStyle.kt`).

---

# 1. OWNER DECISIONS (one page)

Each row: what only you can decide, my recommendation, and the reason in one line. "Slice" = the slice that is blocked until you answer. Everything not listed here I treat as a normal engineering call.

| # | Decision | Recommendation | Why (one line) | Slice |
|---|---|---|---|---|
| D1 | Outgoing amounts: **neutral** (normal text colour, with the minus sign) instead of red? | **Yes, neutral.** Red only for over-limit, overdue, failed. Money in stays green with "+". Add ONE Appearance switch "Show spending in red" (off) as the escape. | Every row red makes red mean nothing; Monzo/YNAB reserve it for "wrong". Sign already carries direction (WCAG 1.4.1). GLOBAL L8, I39 | S3 |
| D2 | Habit rhythm: weekly dots + ring + Pause (the "streak-lite"), or only quiet milestones? | **Milestones only** (about 6 a year, opt-in, no counter, no red state) **plus** a "Pause nudges until..." switch. Do not build the dots/ring. | Your own no-streak decision (PRODUCT_CONTEXT); evidence for retention is vendor/old, evidence of anxiety is real. GLOBAL L10, CRAFT #14 | S6 |
| D3 | Default motion speed: keep Relaxed 1.8x everywhere, or cap taps/tabs/Add? | **Keep Relaxed for arrivals** (hero, sheets, screen pushes) **but cap actions**: feedback 90 ms unscaled, chips 150, tab change 250, Add form 380. Feel it on your phone; if it still drags, switch the default to 1.2x. | Add open is 833 ms today (467 ms at Quick) and a 300-400 ms composition hitch sits on top. CRAFT #2, GLOBAL L13, AUDIT W3 | S0 |
| D4 | Numbers: system digits with tabular figures, or bundle a figures font (OFL, +165 to +240 KB, a new first-party resources artifact)? | **System digits + `tnum` first.** Run the 10-minute width test on the iPhone. Bundle a digits-only font only if iOS ignores `tnum` or you want a signature "number voice". | `tnum` is free; bundling needs a new Gradle coordinate. CRAFT #1, GLOBAL L11, INDIA L12 | S1 |
| D5 | Category tiles: glyph tiles, and allow user-typed emoji? | **Glyph tiles** (Material icons-extended, already a dependency) on a stable colour; user can pick another glyph and colour; **no emoji** for defaults, emoji only as a later opt-in "custom". | Letters collide (Food and Fun are both "F"); emoji differ by OS and can be tofu on old Android (Slice 48 saw it). CRAFT #6, GLOBAL L7, INDIA L10 | S3 |
| D6 | Accept that every category's colour changes once (stable slots replace the name hash) and the chart palette changes to a colour-blind-safe set? | **Yes**, with a one-time "What's new" line. | A rename currently changes a colour; orange, light-blue and green fail 3:1 on white (1.94 / 2.30 / 2.36). CRAFT #6/#9, GLOBAL L6 | S3, S4 |
| D7 | Home shows one "a day" figure or two (budget-based and balance-based)? | **One** in the hero line ("₹1,565 a day · 16 days to payday"); the balance-based one stays on the Safe-to-spend tile behind "How is this worked out?". | You kept both in Slice 112 (Q2); research says one primary figure and no exact-amount fixation (Think Forward). GLOBAL L1, 4.2 | S5 |
| D8 | Guided tour: keep the automatic 19-stop tour or make it opt-in (5 stops + contextual one-time tips)? | **Opt-in**, with a "Show me around" row on the welcome card. | NN/g: tutorials gave no success gain; India playbook says hints help first-timers, so keep the spotlight but make it chosen. AUDIT tour, GLOBAL L3 | S6 |
| D9 | Amounts in notification text: should notices respect Hide amounts (and App lock)? | **Yes**: when Hide amounts or App lock is on, no amount in any notification on either phone (iPhone already does this for Hide amounts); otherwise keep amounts (bill and card amounts are useful), relying on the system's own lock-screen setting. Say if you want amounts never in notification text. | `Notifier.kt` ignores `hide_amounts`; the system already redacts PRIVATE notices when it is set to hide sensitive content. GLOBAL L9, I32 | S0 |
| D10 | "Classic" escape: a global "old look" switch? | **No global switch** (doubles the test matrix). Keep **Compact density** as the classic mode, add a rows-visible / tap-count regression check to every slice, and give taste-dependent changes their own small switch (D1). | The loudest 2026 backlashes (YNAB, Spendee) are density and forced redesigns. GLOBAL L2, anti-patterns 1-5 | all |
| D11 | Hinglish/Hindi UI: when? | **Later (L).** Now: extract strings as slices touch them; ship a Hindi/Hinglish *permission primer* first (S-M). Keep Latin digits. | ~600 string calls to extract; biggest open ground in India but the largest effort. INDIA L4, I5, I9 | after S9 |
| D12 | Bump Compose Multiplatform 1.12.1 to the 1.12.2 patch? | **Yes.** | Fixes shadow flicker (wallet stack, glass bar) and an iOS crash (TextField loses focus when a Dialog appears). GLOBAL 4.6 | S0 |
| D13 | Interactive predictive back may need a new Gradle artifact (`navigationevent-compose`). | **Defer to S7; check `:shared:dependencies` first.** If it is a new coordinate, ask again. | Rule "no new libraries without your say". CRAFT 3.1 delta 8 | S7 |
| D14 | Haptics: re-map meanings (Undo is the "error" pattern today) and add an in-app "Haptics: On / Off" switch? | **Yes**, default On. | Apple and Android guidance: documented meanings, optional. CRAFT #3, GLOBAL L12 | S0/S7 |
| D15 | Low-RAM phones: auto-degrade motion/blur/spark animation? | **Yes**, automatically (`isLowRamDevice` or sustained slow frames), with a line in Appearance. | ~57% of India's 2025 phone shipments under $200; BHIM/CRED jitter complaints. INDIA L9, I32 | S7 |
| D16 | Things I recommend you do **not** approve: Live Activities, Android 16 Live Updates, Wear, real backdrop blur, confetti, a streak counter. | **Do not build.** | Poor fit / new permission / GPU cost / against your decisions. CRAFT 3.9, 3.5 | - |
| D17 | Owner action (not a decision): run the **real-phone baseline** (section 6) on your iPhone and your Android. | **Please do, before S2 ships.** 10 minutes. | Nothing in this doc is measured on a phone; every "feels" claim depends on it. | S0 |
| D18 | True-black dark theme and a "match my wallpaper" accent (Android)? | **Yes, both, late** (S9). | Cheap, liked by OLED/Material You users; money colours stay fixed. CRAFT #18 | S9 |

---

# 2. The slices at a glance (recommended order)

Effort is working days for one engineer (S = under 2, M = 3-6, L = over 6). Totals are my estimate **[INFERENCE]**.

| Slice | Name | Effort | Needs | Depends on | Shippable alone because |
|---|---|---|---|---|---|
| **S0** | Fix now + quick wins + measurement | ~11 d | D3 D9 D12 D14(part) D17 | none | every item is local; the bug fix ships in a patch release |
| **S1** | Tokens + consistent chrome + tabular figures | ~9 d | D4 | S0 | pure restyle; no flow changes |
| **S2** | Add/Edit redesign + read-only entry detail | ~11 d (+1 maths pad, +4 smart field, optional) | none | S1 | the form and detail are two screens; old tags kept |
| **S3** | Category identity + rows + swipe + neutral amounts | ~9 d | D1 D5 D6 | S1 | only row/tile/colour changes |
| **S4** | Charts system (signature charts, scrub, table, safe palette) | ~10 d | D6 | S3 (colour slots) | Reports and Home chart only |
| **S5** | Pace: Home hero, Budget screen, Goals/Categories depth | ~8 d | D7 | S1, S4 (bar and ring) | Home/Budget/Goals/Categories only |
| **S6** | First run, review ritual, success moments, tone | ~7 d | D2 D8 | S1, S3 | first-run and To-review surfaces only |
| **S7** | Haptics, motion system, low-RAM, predictive back, glass edge | ~8 d | D13 D14 D15 | S0, S1 | engine-level; no layout change |
| **S8** | Widgets, Lock Screen, Control Center | ~7 d (+ Swift) | none | S3, S5 (tiles, pace) | separate processes (Glance, WidgetKit) |
| **S9** | Large screens, fold, dark/contrast/text-size polish, theme options | ~8 d | D18 | S1-S5 | adaptive layout and theme options |

Order rationale: S0 first because it contains a privacy gap (notices ignore Hide amounts), the measuring tools that make later slices judgeable, and the cheapest felt wins. S1 before everything else visual because one token set stops every later slice from inventing its own radius. S2 next because Add is the most used screen and has the biggest measured cliffs. S3 before S4 because charts reuse the category colour slots. S5-S6 then reuse the bar/ring and the tile. S7-S9 are the polish that is easiest to judge once the structure stays still. The ten highest-impact UI items are marked **(top 10)** below: F-02 motion tiers, U-01 Add form, U-02 entry detail, U-03 category glyph tiles, U-04 tabular figures and the hero number style, U-05 neutral amounts, U-06 pace on the hero and bars, U-07 tokens and one chrome, U-08 charts, U-10 first-run ghost month. F-01 (notices ignore Hide amounts) is first in time but is not a look-and-feel item.

---

# 3. SLICE 0: fix now (real bugs and the cheapest high-impact wins)

Everything here is small, local and individually testable. Items F-01 to F-14 are one release (about 10 days); F-15 and F-16 make the rest measurable. Unless an item says otherwise: no performance risk, iOS identical (shared Compose), no owner decision.

### F-01 · Notices ignore Hide amounts (severity: medium privacy gap, NOT a leak by default)
- **Correction (2026-11-14):** an earlier version of this item called this "rupee amounts on a locked screen: a real bug". That overstated it. Android's default notification visibility is already `VISIBILITY_PRIVATE`, so on a secure lock screen with the system setting **Hide sensitive content** every notice (recap, budget, card, bill-due, bill) is already redacted by the system to a generic placeholder. With **Show all notification content**, every notice shows its text, bills included (`setPublicVersion` is only used when content is hidden). `Notifier.kt:84-91` merely adds a *custom* public version for `BILL_FOUND` / `BILL_TRACKED`, which differs from the system placeholder only in wording.
- **Screens:** notifications (Android and iPhone local notifications).
- **Real gaps (read in code):** (1) on Android, the non-bill kinds (`WEEKLY_RECAP`, `BUDGET_NEAR/OVER/PACE/WRAPUP`, `BILL_DUE`, `CARD_DUE/STATEMENT/LIMIT`) carry rupee text and nothing in `androidApp` looks at the app's own Hide amounts (`hide_amounts`; the widgets do, `QuickAddWidget.kt:211`; core already leaves amounts out of found/tracked bills): a person who hides amounts in the app still gets amounts in notification text whenever the system shows all content. (2) The bill public version is cosmetic. (3) iPhone is different and further along: `IosNotifications.kt:176-217` already masks every kind with a plain line when Hide amounts is on (`masked`) and marks those notices private for iOS's Show Previews setting; so iPhone has no gap of kind (1). Not checked: any iOS "App lock" interaction. GLOBAL L9/I32 and INDIA L15 raised this; their wording ("only bill notices set a private version") is accurate but the "leak" reading is not.
- **Step 0 (mandatory, before sizing):** REPRODUCE on the emulator with a secure lock screen under both system settings (Settings > Notifications > Lock screen: "Show all notification content" vs "Hide sensitive content"), with Hide amounts off and on, and take screenshots of a recap, a budget, a card and a bill-due notice. Only then decide the effort below; if the system placeholder already covers the default case, the fix shrinks to the Hide amounts / App lock behaviour.
- **Change (proposal):** when **Hide amounts or App lock is on**, build the Android notice text without amounts (same plain lines iPhone already uses); give every amount-carrying notice a `setPublicVersion` for consistency; otherwise keep amounts as today. Reuse the iPhone `masked` wording so both platforms read the same.
- **Replaces:** the single `isBill` branch in `Notifier.show` and the Android/iPhone asymmetry.
- **Effort:** S (0.5-1 d after Step 0; host test that with Hide amounts on no kind's text contains a digit or ₹).
- **Perf risk:** none. **A11y:** plain full sentences. **iOS parity:** already done for Hide amounts; verify on a phone under each Show Previews value.
- **Dependencies:** none. **Owner decision:** D9 (should amounts ever appear in notification text, and should App lock also hide them?).
- Mock-up `notification_privacy_before_after.png` shows the **Hide-amounts case** (what the notice would say with Hide amounts on vs today's text when the system shows all content), **not a default leak**.

### F-02 · Motion tiers: feedback is instant, actions are capped, arrivals stay relaxed **(top 10)**
- **Screens:** every screen change; Add open; tabs; chips; press feedback.
- **Evidence:** default 1.8x makes QUICK 216 / STANDARD 396 / EMPHASIZED 576 / GLIDE 828 ms; Add opens in **833 ms** (467 ms at Quick), Reports/More/Accounts 367-433 ms (167-200 ms at Quick), tab change 396 ms (`AUDIT W3`, `clip_*_relaxed.mp4`). The tweens that ignore the setting: `TrendChart` 500 ms, `SwipeableRow` flash 1100 ms (also plays under Reduce motion), `BreakdownRing` partly (CRAFT 3.1). CRAFT #2, GLOBAL L13/I50, INDIA L9.
- **Change:** in `Motion.kt` add `FEEDBACK = 90` (never scaled: press, tick, colour flash), `effects()` spring (1.0 / 1600, no overshoot), `spatial()` / `spatial(fast)` springs, and per-use caps: chips and switches 150 ms, tab change 250 ms, screen push 400 ms, **Add form `min(GLIDE, 380)`** with focus on the amount at once. Relaxed stays 1.8x for hero/sheet/arrivals. Route `TrendChart`, the swipe flash and the ring through `Motion` (flash through `Motion.tween(reduce, ...)`). Reduce motion becomes a 100 ms cross-fade instead of "instant" (CRAFT delta 2). Storyboard: `motion_storyboards_light.png` (blocks "Add open" and "Tab switch"); numbers in `design_tokens_sheet_light.png`.
- **Replaces:** the single global multiplier for everything.
- **Effort:** S-M (2-3 d: `Motion.kt`, `ScreenHost` transitions, a few call sites, `MotionUiTest` additions: Add settles within the cap at 1.8x; Reduce motion shows a fade).
- **Perf risk / measure:** lower risk (shorter animations); watch that a shorter Add glide does not push the composition hitch into the animation: measure with the F-15 logger, Add open frames over 100 ms (target none) and `strip_add_open_*`-style recording.
- **A11y:** Reduce motion honoured (fade, charts drawn complete); `LocalLeavingScreen` hiding stays.
- **iOS parity:** same shared code; the iOS Reduce Motion setting is read once (CMP 1.12.1; a live toggle is ignored: GLOBAL 4.6).
- **Dependencies:** none. **Owner decision:** D3.

### F-03 · Filters panel appears and disappears in one frame
- **Screens:** Search > Filters. **Evidence:** gfxinfo 3 frames on close, "1 frame (no animation)" on open (`AUDIT W8`, `strip_filters_open_relaxed.png`, `search_filters_panel_w393f10l.png`). 
- **Change:** slide up 24 dp + scrim fade, 220 ms EaseOut in, 150 ms EaseIn out, same sheet style as the future one sheet (U-08). **Replaces:** the custom hard-cut panel. **Effort:** S (0.5 d). **Perf:** negligible; check one close = more than 3 frames. **A11y:** focus moves into the sheet, Back closes it. **iOS:** same. **Deps:** none. **Owner:** no.

### F-04 · Reports shows plain "Loading..." text for several frames
- **Screens:** Reports. **Evidence:** `strip_tab_reports_to_more_relaxed.png` first cells (white page with "Loading…"), `AUDIT W8`, CRAFT #13. Mock-up: `motion_storyboards_light.png` (the "Today" strip of the tab block).
- **Change:** keep the last Reports content on screen and refresh in place; on the very first open draw the real chrome (segmented rows, hero card frame) with a one-shot skeleton (no looping pulse). **Replaces:** the centred text. **Effort:** S (1 d). **Perf:** do not start the aggregate while the tab transition runs (start on transition end) [INFERENCE]; measure Reports open p95 on the 1,500-entry seed (today 53 ms, p99 113 ms). **A11y:** skeleton hidden from screen readers; announce "Reports loaded" only if the wait exceeds 1 s. **iOS:** same. **Deps:** none.

### F-05 · The keypad hangs on screen for ~0.6 s after the Add form closes
- **Evidence:** `strip_add_close_relaxed.png`, `AUDIT W8`, `AUDIT QW 3`. **Change:** the pad is part of the form's layer and leaves with it (same transform, same curve). **Replaces:** pad leaving on its own timer. **Effort:** S (0.5 d). **Perf:** none. **A11y:** focus returns to the Add button. **iOS:** the system keyboard variant, if used, dismisses at the start of the close. **Deps:** none (superseded and absorbed by U-01 when S2 lands).

### F-06 · 200% text still breaks four cards
- **Evidence:** More "Back up your expenses" nudge one word per line (`more_top_w393f20l.png`); Reports Income tile "₹1,85,000 / .00" (`reports_period_w393f20l.png`); Add "Attach a photo or file" becomes a tall oval (`add_expense_filled_scroll_s1_w393f20l.png`); Search summary chips clip "Inc" (`search_results_w393f20l.png`); `AUDIT W7`.
- **Change:** stack the buttons under the text like `NudgeCard`; no mid-amount wrap (shrink or drop paise at 200%); chip as a full-width outlined button; the Search summary row scrolls or wraps to two lines. **Effort:** S (1 d + extend `AccessibilityUiTest` to font scale 2.0). **Perf:** none. **A11y:** this is the item. **iOS:** CMP 1.12.1 does not scale iOS text with Dynamic Type at all (arrives in 1.13.0-alpha01: GLOBAL 4.10), so the 200% promise is **unverified on iPhone**: test the app's own large-text mode there. **Deps:** none.

### F-07 · No Budget row in More
- **Evidence:** `AUDIT W6`, `more_top_w393f10l.png` (28 rows, no Budget); budget reachable only from the Home card or the guide. **Change:** add "Budget" with a live subtitle ("₹28,178 left of ₹60,000") in the Money group. **Effort:** S (0.25 d). **Perf/a11y/iOS:** none. **Deps:** none.

### F-08 · Bulk bar actions are text-only
- **Evidence:** `home_selection_mode_w393f10l.png`, `search_select_all_bulk_bar_w393f10l.png`; `AUDIT` per-screen table (D = 2). **Change:** icons on Set category / Make transfer / Delete / More, a count chip ("2 selected"), one LONG_PRESS haptic when selection starts. **Effort:** S (0.5 d). **A11y:** icon buttons keep text labels for TalkBack. **iOS/perf:** none.

### F-09 · Selected chips are grey, not accent
- **Evidence:** `FilterChip` 94 uses; selected = grey, low on/off contrast (`add_expense_empty_w393f10l.png`; `AUDIT` inconsistency inventory). **Change:** selected = accent container + bold label + check (as the segmented control in `add_form_before_after_light.png`). **Effort:** S (0.25 d, one `FilterChipDefaults.filterChipColors`). **A11y:** raises on/off contrast.

### F-10 · The small floating "+" covers content on Accounts / Categories / Recurring
- **Evidence:** it covers VISA and the amount on a card (`accounts_card_detail_s1_w393f10l.png`) and the up/down arrows (`categories_list_w393f10l.png`); `AUDIT W5`. **Change:** extended FAB ("Add account") inside the content padding (80 dp bottom padding) so it never covers amounts. **Effort:** S (0.5 d). **Deps:** replaced by the U-08 header-action style later.

### F-11 · "Same as X?" dialog has four text buttons
- **Evidence:** `flow_fix_category_3_after_save_w393f10l.png`; the dialog is "Not now / Stop asking / Review one by one / Move all 4" (`AUDIT` tap counts). **Change:** two buttons (Move all 4 / Not now) plus a "More choices" link. **Effort:** S (0.25 d). **A11y:** fewer targets. **iOS:** same.

### F-12 · Bill "Mark paid" snackbar has no Undo
- **Evidence:** `AUDIT` tap-count table ("Snackbar has no Undo", `flow_pay_bill_3_after_w393f10l.png`); the rest of the app has Undo everywhere. **Change:** reuse the one-line Undo bar with the same 10 s lifetime (CRAFT 3.6). **Effort:** S (0.5 d; reversal must restore the bill and the entry: verify `UNDO_AUDIT.md` pattern). 

### F-13 · Undo buzzes like an error; limit crossed feels like a long-press
- **Evidence:** `Feel.UNDO` = Android REJECT / iOS notification error; `Feel.LIMIT` = LONG_PRESS (CRAFT 3.2; GLOBAL 4.6 confirms the iOS mapping at v1.12.1: Reject = notification error, KeyboardTap silent). **Change (S0 part):** UNDO soft neutral (GESTURE_END / KEYBOARD_TAP fallback, iOS soft impact); LIMIT warning (iOS `UINotificationFeedbackGenerator.warning` through an `iosMain` actual). The full vocabulary and the Haptics switch are in U-14 (S7). **Effort:** S (0.5 d). **Owner:** D14. **A11y:** haptics never the only cue.

### F-14 · Compose Multiplatform 1.12.1 to 1.12.2 patch
- **Evidence:** GLOBAL 4.6 / feasibility table: 1.12.2 fixes "flickering when animating non-rectangular `graphicsLayer` shadows" (wallet stack, glass bar) and an iOS crash when a `TextField` loses focus because a `Dialog` appears. **Effort:** S (0.5 d + a full test pass). **Risk:** any CMP bump can move layout; run the screenshot suite. **Owner:** D12 (build dependency version, not a new library).

### F-15 · A frame-time logger on both platforms, and a real-phone baseline **(unlocks everything)**
- **Evidence:** CRAFT #8 and 3.12; PERFORMANCE.md numbers are all emulator; "nothing measured on an iPhone" (AUDIT section 0, CRAFT G10). 
- **Change:** debug-build-only `FrameLog`: Android `Window.addOnFrameMetricsAvailableListener` (platform API, no library, no permission) aggregating `TOTAL_DURATION` into p50/p90/p99 and counts over 16.7 ms / 8.3 ms / 700 ms per screen change, written as one `Log` line and a tiny overlay; iPhone a `CADisplayLink` in `iosMain` doing the same from `targetTimestamp - timestamp`. Plus the owner's baseline protocol (section 6). **Effort:** S-M (2-3 d). **Perf:** debug only; compile-time excluded from release. **Owner:** D17 (run the baseline).

### F-16 · Haptics and motion tests for the above
Extend `MotionUiTest` (Add within cap at 1.8x; Reduce motion fade), `AccessibilityUiTest` (dark theme, font scale 2.0), and add a host test for F-01. **Effort:** included in the items above.

**Slice 0 total: about 11 days.** Release it in two parts if you like: S0a (F-01, F-03 to F-12; about 4 days, no decision except D9) and S0b (F-02, F-13, F-14, F-15; needs D3, D12, D14, D17).

---

# 4. LIST 1: improvements to screens that already exist (ranked by visual and interaction impact)

Rank 1 is the biggest change in how the app looks and moves day to day. F-items above are not repeated.

### U-01 · Add / Edit form: compact, amount-first, progressive **(top 10)** · Slice S2
- **Screens:** Add (expense, income, transfer, loan, split, foreign currency, expect back, tags), Edit expense, quick-entry form, trip chip.
- **Problem / evidence:** one scrolling form of about 2.5 screens; kind chips wrap to an orphan "Lent or borrowed"; with the app keypad up only ~35% of the screen is form and the Category chips are cut off behind it; four optional options are lone outlined chips; Note, Tags, Receipts, "Save as a quick entry" below the fold; on a fold-size window one very wide column with a 1,000 dp keypad (`add_expense_empty_w393f10l.png`, `add_expense_empty_scroll_w393f10l.png`, `add_number_pad_w393f10l.png`, `add_expense_filled_scroll_s1_w393f10l.png`, `add_expense_empty_w864f10l.png`; `AUDIT W1`, per-screen scores H2 C2 D2 F2). **Opens with a 300-400 ms first frame** (about 25 chips with avatars and 13 `FlowRow`s composed eagerly in one non-lazy `Column(verticalScroll)`; `strip_add_open_relaxed.png`, p95 109 ms / p99 300 ms, 47% janky, `AUDIT W2`) and the hitch does not shrink at Quick (it is composition, not animation). GLOBAL L4/I14-I16 (Monefy, Cashew, Wallet, Lunch Money recents first, "Add 'Chai'" button echo); CRAFT 3.12 (prefetch).
- **Change:** top bar: close, "New expense", date chip. A real segmented kind control (Expense / Income / Transfer / "...") instead of two chip rows. **Amount first and big** (tabular, ₹ small) with the amount in words under it (exists) and the pad that stays up. **Category as one horizontal row of 6 glyph tiles, most used first, plus "More"** (the full 23 in a sheet composed lazily). Account and trip chips on one line plus a single **"More" chip** that opens split, expect back, foreign currency, tags, receipts and "save as quick entry". The big button echoes the result: "Add ₹1,250 · Food". The pad and the form are one layer that rises together (kills F-05) and the amount takes focus at once. Compose only what is visible in the first frame (tiles, chips); the rest after the glide. Mock-up: `add_form_before_after_light.png` / `_dark.png`.
- **Replaces:** the long form, the "Pad" text link (becomes a real "Number pad / Keyboard" toggle), the four lone chips, the 23 chips composed up front.
- **Effort:** L (6-8 d including income/transfer/loan/split variants and tests; ~120 tests use the tags `add`, `amount` etc.: keep every `testTag`).
- **Perf risk / measure:** the item is *for* performance; budget: no frame over 100 ms on Add open on the API 34 emulator A/B against the base commit (6 alternating runs, 30% threshold as in PERFORMANCE.md), `RefreshBudgetTest` unchanged (new row and total within 50 ms), per-keystroke recomposition checked with Layout Inspector counts [INFERENCE: the per-keystroke `draft` recreation is from reading code].
- **A11y:** tiles carry their text label; selected tile has a check (not colour only); 48 dp targets; 200% text: tiles wrap to two lines, "More" always reachable; amount keeps the Indian-words semantics.
- **iOS parity:** shared Compose; the system decimal pad is not used (app pad), so no iOS keyboard variant; verify focus-with-pad on the simulator and the owner's phone.
- **Dependencies:** S1 tokens (radii, tiles); U-02 not required. **Owner decision:** none for the layout. (Maths pad and smart field are List 2: N-02, N-01.)

### U-02 · Read-only entry detail, and the row grows into it **(top 10)** · Slice S2
- **Screens:** Home list, Search results, History, Bills "from texts", To review > entry.
- **Problem / evidence:** tapping any entry opens the editor ("Edit expense" with Save, 23 chips); history, refund and "where this came from" are below the fold (`entry_edit_top_w393f10l.png`, `entry_edit_s2_w393f10l.png`, `entry_history_w393f10l.png`; `AUDIT W4`); the open has a **430 ms gap** then 400 ms of motion (`strip_home_to_entry_open_relaxed.png`; gfxinfo p99 300 ms, one 417-427 ms frame, 52% janky); `sharedContainer` is a `composed {}` modifier on **every** Home row (`PERFORMANCE.md` suggestion 2, CRAFT #11 / delta 5).
- **Change:** a light detail screen: glyph tile (or merchant monogram) + merchant, category and account, a big tabular amount with a small ₹ and paise, date and time, a one-line insight when it exists ("₹120 above your usual at Starbucks"), a facts card (Category with a "Change" link, Account, Note, Tags), a **"Read from a bank text" card** with "Show text" (the where-this-came-from section that exists today), history inline, and a bottom action row: **Edit** (primary), Duplicate, Delete (with Undo bar as today). The tapped row grows into it (container transform, 320 ms; corners 20 to 28 dp); `sharedBounds` attached **only to the tapped row** (tap sets a `lastTapped` key one frame before the screen key changes). Tapping Edit reuses the existing form. Mock-up: `entry_detail_before_after_light.png`; storyboard `motion_storyboards_light.png` block "Entry open".
- **Replaces:** "tap = editor"; the per-row composed shared-container modifier.
- **Effort:** M (4-5 d; the detail is new but its data already exists in the editor and Search/History).
- **Perf risk / measure:** removes per-row cost (A/B scroll of Home with 100,000 entries: today 0.3-0.6% janky, p99 12-14 ms; must not rise), and the open: no frame over 100 ms in the new open (today 417 ms), measured with F-15 and `strip_home_to_entry_open_*`. `MotionUiTest` shared-container test is rewritten for the one tapped row.
- **A11y:** the detail is a proper heading structure; Edit is reachable in one swipe; amount semantics unchanged (exact text); the leaving-screen hiding stays.
- **iOS parity:** shared; interactive swipe-back is not required.
- **Dependencies:** U-03 for the tile/monogram (can ship with letter tiles first). **Owner decision:** none (first listed in the owner's "read-only entry detail first" prompt, D-list note).

### U-03 · Category identity: glyph tiles, 12 stable slots, merchant monograms **(top 10)** · Slice S3
- **Screens:** every row (Home, Search, History, To review, Bills), Add category row, Categories, Reports legend/bars, Budget rows, widgets, calendar.
- **Problem / evidence:** the tile is the category's first letter; Food and Fun are both "F"; 23 default categories hash into 8 pale tints by **name** (rename = recolour); the chart palette is a third colour list; the person's own colour (`Category.colorArgb`) is ignored by avatars (CRAFT 3.3/3.5, `HomeStyle.kt:92-98`; HO f9; `AUDIT` inconsistency "no category icons (letters only)"). Users asking for icons: PennyWise ("just small dots"), Fold ("logos"), axio rename praised (INDIA L10, I34). CRAFT #6, GLOBAL L7/I37.
- **Change:** one resolver `categoryHue(category)`: the person's colour if set, else a stable slot from the category id in a fixed 12-slot list (first seven Okabe-Ito), with 4 precomputed constants per slot (container/glyph x light/dark = 48 constants, no runtime colour library). Tile = tonal container + glyph from `compose.materialIconsExtended` (already a dependency): about 24 default glyphs (restaurant, cart, bus, receipt, bag, movie, medical, home, bolt, school, fuel, flight, phone, etc.). Rows with a known merchant (`MerchantTable`, 250+ names) show a **monogram tile coloured by the payee key with a small category badge** (no logos: no network, no licence). A user-picked glyph and colour per category in Categories. Mock-up: `rows_glyph_tiles_before_after_light.png` (slot row at the bottom right), `design_tokens_sheet_light.png` (12 slots).
- **Replaces:** `CategoryAvatar` letter + name hash, `reportColor`, the 8-colour avatar list.
- **Effort:** M (5 d: resolver + constants + glyph set + monogram + a host colour-vision test; R8 icon shrink and iOS klib size to check once).
- **Perf risk / measure:** vector icons per row: check Home fling (today 9.6% janky on the emulator seed) and the 100,000-row scroll do not regress; icons are `ImageVector`s cached by `remember`.
- **A11y:** tile is decorative if the category name is in the row text; chart marks keep names/percent; a Machado-matrix unit test (about 20 lines in `core/domain`: dichromat simulation, CIELAB distance >= 12-15 for the top 6 slots) gates the palette; CRAFT 3.3.
- **iOS parity:** same vectors; unverified: Material icon availability names in the iOS klib build (**verify each glyph name exists in icons-extended**; I typed path data into the mock-ups from memory).
- **Dependencies:** S1 tokens. **Owner decision:** D5, D6.

### U-04 · Numbers have a voice: tabular figures, a hero number style, quiet ₹ and paise **(top 10)** · Slice S1
- **Screens:** every amount: Home hero and tiles, rows, day totals, Reports, Budget, Accounts, widgets (Glance cannot), Add amount.
- **Problem / evidence:** no `fontFeatureSettings` in the repo; no `Typography` override; digits are proportional so rolling totals jitter and right-aligned amounts do not align (CRAFT #1, 3.4; GLOBAL L11/I38; INDIA L12/I12; `design_tokens_sheet_light.png` "proportional vs tabular"). Hero and rows show `₹1,23,456.78` at one size.
- **Change:** `Number.Hero` (36-50 sp SemiBold/Bold, `tnum`, -0.5 sp tracking; ₹ and paise at 55-60% on the same baseline; capped at 54 sp at large text as today), `Number.Row` (bodyLarge SemiBold `tnum`), `Number.Label` (labelMedium `tnum`), `Heading.Day` (one label style instead of three), `Caption.Chart` (11 sp). Paise quieter in rows via `AnnotatedString` built **after** masking, with semantics keeping the exact text. First task, 10 minutes: a width test (`"111,111"` vs `"888,888"` through `TextMeasurer`) on Android and iPhone to learn whether iOS honours `tnum`.
- **Replaces:** `displaySmall`/`bodyLarge` ad-hoc amounts; the lone `FontFamily.Monospace` on card digits.
- **Effort:** S (1-2 d); the optional bundled font is M (D4).
- **Perf risk / measure:** none beyond text measurement; keep `maxLines = 1`; the rolling total keeps working (U-24 improves it).
- **A11y:** exact amount text stays in semantics; lakh/crore compact forms get spoken words ("1.2 lakh rupees"); large-text caps stay.
- **iOS parity:** the main unknown (CRAFT 3.4 note, GLOBAL Appendix B); fallback is the bundled digits font (D4).
- **Dependencies:** none. **Owner decision:** D4.

### U-05 · Neutral outgoing amounts: red only for "wrong" **(top 10)** · Slice S3
- **Screens:** every list row and amount (`amountColor(Flow3.OUT)`), Loans, Search, Reports.
- **Evidence:** every outgoing amount is `#B3261E` / `#FF8A80` (`HomeStyle.kt:256-263`, `home_typical_s1_w393f10l.png`); Monzo: red "usually means something has gone horribly wrong"; YNAB: red = urgent; the sign already carries direction (GLOBAL L8, I39; INDIA anti-pattern 10-11). 
- **Change:** outgoing = `onSurface` with "-"; incoming = green with "+"; transfer = neutral, no sign; red only for over-limit, overdue, failed capture; one palette for "owed to me / I owe" everywhere. Appearance switch "Show spending in red" (default off). Mock-up: `rows_glyph_tiles_before_after_light.png` / `_dark.png`.
- **Replaces:** `amountColor` OUT branch. **Effort:** S (0.5-1 d incl. the switch and screenshot updates). **Perf:** none. **A11y:** sign + word carry meaning (already); contrast improves (computed: onSurface on the card `#F3EDF7` is 14.9:1 in light and 12.6:1 in dark; the red is 5.7:1 on the card in light and 7.1:1 in dark). **iOS:** same. **Dependencies:** none. **Owner decision:** D1.

### U-06 · Pace, not just a total: Home hero, budget bar, payday line **(top 10)** · Slice S5
- **Screens:** Home month card, Budget-left and Safe-to-spend tiles, trip bar, Budget screen.
- **Problem / evidence:** the hero says how much was spent, not whether you are on track; the budget bar has no sense of "today"; two "a day" figures ("about ₹16,213 a day from your balances" next to "bills use it all") (`home_typical_w393f10l.png`; HO decisions Q2). Cashew, Monarch, Toshl, Goodbudget, Bluecoins, Spendee, Copilot, Wallet all show a today tick or pace words (GLOBAL L1, I1-I8); a Jupiter reviewer asks for "days remaining to salary", Spenrol shows "left for today" (INDIA L5, I14); CRAFT #10; also HO f14 (small text on the hero gradient measured 2.9-6.1:1, still open).
- **Change:** `AnimatedBudgetBar` gets a **pace tick** (fraction of the period gone) labelled "today", a state **word** ("On pace", "A little ahead", "Over by ₹1,655"; amber means projected overshoot, red only when over), and one sentence ("You can keep spending ₹1,565 a day for 16 more days"). The hero gets a payday-aware line **"₹1,565 a day · 16 days to payday"** (uses the existing monthly start day and budget), last month as a **dashed ghost line** under the 14-day line, and the compare chip on a translucent pill (fixes the contrast finding). The Safe-to-spend tile keeps its number and gets **"How is this worked out?"** (tap shows the arithmetic: budget - bills still due - spent, over N days). Whole-rupee headlines and Hide-amounts masking (also bars) unchanged. Mock-up: `home_hero_pace_before_after_light.png` / `_dark.png`.
- **Replaces:** second "a day" figure on the tile; the plain bar.
- **Effort:** S-M (3 d). **Perf risk / measure:** one extra float in the draw lambda; the ghost line needs the prior-month series from the view-model (one more aggregate on the Home read: A/B `RefreshBudgetTest`, must stay within budget, HO "no option adds a query to Home open" rule: this one does, so measure). **A11y:** state in words, semantics "on pace / ahead / behind"; negative copy "1,200 over this month" not "-1,200 left". **iOS:** same. **Dependencies:** none strict; U-08 (charts) shares the tick/ghost drawing. **Owner decision:** D7.

### U-07 · Design tokens and one chrome (header, cards, sheet, buttons, chips) **(top 10)** · Slice S1
- **Screens:** all ~35.
- **Problem / evidence:** 3 header styles (tinted `AreaBar` with an icon chip, plain, "Cancel"/"Close" forms) with a text "Back"; **14 corner radii** (16 x20, 12 x14, 20 x12, 24 x7, 28 x5, 8, 14, 22, 21, 18, 10, 6, 3, 2); ~9 card styles; 5+ sheet/dialog styles (40 `AlertDialog`, 100 custom `Dialog`, a custom Filters panel, the Add form); 220 `TextButton`s; segmented choices drawn as chip rows; day headings use 3 label styles; `bodySmall` x241 (`AUDIT W5` and section 5; HO f15; CRAFT section 5; GLOBAL 4.7).
- **Change:** one token set in code (names for what exists): spacing 4/8/12/16/20/24/32; **radii 8 / 14 / 20 / 28 / full** (entry card 22 to 20, nudge 24 to 20, inputs 12-16 to 14); elevation 0 (flat tinted card), 1 (chrome), 2 (held object, 8 dp shadow: wallet cards, dragged row), glass (the bar pill); **one header** (back arrow, title, at most 2 actions; the area-icon chip and "Back" text go); **three cards** (hero, standard, inline/nudge); **one sheet** (28 dp, handle, 32% scrim, slide + fade) used by Filters, pickers and the "More" in Add; one button hierarchy (52 dp pill primary, tonal, outlined, text link); real segmented controls for Theme/Period/Motion/Reports modes; accent-tinted selected chips. Then migrate screens in a fixed order (Home, Add, Search, Reports, More, Budget, then the rest). Mock-up: `design_tokens_sheet_light.png` / `_dark.png`.
- **Replaces:** the radii, headers and card styles listed above.
- **Effort:** L (7-9 d: 1 d tokens, 6-8 d migration + screenshot refresh).
- **Perf risk / measure:** none in principle; fewer distinct shapes helps; run the scroll A/B once after migration.
- **A11y:** header actions 48 dp; the new segmented control is a radio group for TalkBack; check 200% on every migrated screen.
- **iOS parity:** shared; the iOS back swipe stays a system behaviour of `ScreenHost`.
- **Dependencies:** F-09 first. **Owner decision:** D10 (no global old-look switch); visible changes are mild (22 to 20 etc.).

### U-08 · Charts: one signature style, scrub everywhere, accessible and colour-blind-safe **(top 10)** · Slice S4
- **Screens:** Reports (donut, six-period bars, rows), Home 14-day line, Trip summary bars, Calendar legend, Budget.
- **Problem / evidence:** three chart implementations with three interaction models: `HomeSpark` scrubs, `BreakdownRing` taps, `TrendChart` is `Box`es with a literal 500 ms tween, one recomposition per bar per frame; donut has butt caps; palette: orange 1.94 / light-blue 2.30 / green 2.36 vs white (3:1 needed), indigo/purple/brown share luminance (0.166/0.168/0.177); chart accessibility is a one-line description; no value labels, no table (`reports_period_w393f10l.png`, `reports_period_s1_w393f10l.png`; AUDIT W10; CRAFT #9/#12, 3.3, 3.11; GLOBAL L6, I33-I36, 4.5).
- **Change:** signature style (2.5 dp round caps and joins, 2 dp gaps, one accent per chart, "today" = filled dot, forecast/average/last month dashed, direct labels at 11-12 sp, no grid); **round-capped donut** with the total in the centre and a ranked legend of mini bars with glyph tiles (top 5 + Others, as today); **Canvas bars with a shared `ChartScrubber`** (drag = haptic STEP per bar + a bubble "Oct · ₹12,430 · Rent ₹9,000 · Food ₹1,400"; tap still opens the period), a dashed average or budget line; one sentence headline above ("Bills is 70% of what you spent, ₹1,240 more than last month"); **"Show as table"** under each chart (the drill-down rows are already the table) and spoken summaries plus step-through actions for TalkBack/VoiceOver; the Okabe-Ito-based slots (U-03) and the Machado test. Mock-ups: `reports_charts_before_after_light.png` / `_dark.png`.
- **Replaces:** `TrendChart` Boxes, `BreakdownRing` butt caps, `reportColor`.
- **Effort:** L (8-10 d: scrubber M, bars M, ring S, table S, headline S, a11y S).
- **Perf risk / measure:** `drawWithCache` for `Path`/`Brush`, one `Animatable` per chart, reads in draw only; Reports scroll (today 16.7% janky, p99 53 ms) and Reports open must not regress (A/B); count recompositions of `TrendChart` before/after with Layout Inspector.
- **A11y:** the table twin; per-mark semantics via `stateDescription` (not live regions on frequent updates); VoiceOver mapping of `customActions`/`progressBarRangeInfo` is **unverified in CMP 1.12.1** (test on the owner's phone); all-colour-not-alone (labels, percent, gaps).
- **iOS parity:** shared; no Apple Audio Graphs equivalent (the table/step actions are the substitute).
- **Dependencies:** U-03 (slots); F-02 (scaled tweens). **Owner decision:** D6.

### U-09 · Budget screen: ring and pace first, limits next, settings behind the pencil · Slice S5
- **Evidence:** the screen opens on "Carry over" and five period chips before any limit and lists 23 categories as "No limit" rows (`budget_main_w393f10l.png`, `_s2`; `AUDIT W6`, scores H2 D2); "rows look like text, not buttons"; no Budget row in More (F-07). GLOBAL L1/I1-I3/I9/I10.
- **Change:** a summary card with a ring (pace tick, state word, one sentence), then LIMITS as rows with glyph tile, spent of limit, pace-tick bar and a state word; categories without a limit folded into "19 more categories have no limit: Show"; Period, Carry over and Starts-on-day move into one sheet behind the pencil; "Move budget" (rebalance with preview) is List 2 (N-17). Mock-up: `budget_pace_before_after_light.png`.
- **Replaces:** the Carry-over-first screen. **Effort:** M (4-5 d). **Perf:** ring is a draw-phase arc; row bars animate once; measure Home-to-Budget (today p95 121 ms, one long frame). **A11y:** ring has a text equivalent; every state has a word. **iOS:** same. **Dependencies:** U-06, U-08 (bar/ring). **Owner decision:** none.

### U-10 · First run: a ghost "Example" month, a shorter Welcome, an opt-in tour **(top 10)** · Slice S6
- **Evidence:** a 0.00 month card and "Set a budget" sit above a plain "Welcome", two buttons and one dense privacy paragraph (`home_empty_welcome_w393f10l.png`, `welcome_start_w393f10l.png`); the tour is 19 stops (the audit's `guide_step01..25` shots show 25 in the guide) and auto-shows per version; HO Q12 left it open. Cashew's Preview Demo, NN/g (tutorials: ease 4.92 vs 5.49, p=0.047), Apple HIG, Up (GLOBAL L3, 4.1; CRAFT 3.7; AUDIT W10 text walls).
- **Change:** heading "Let's note your first expense" / "Type an amount. That's it. Sort it later."; one primary button and one outlined "I already have data"; **two privacy lines** ("Stays on this phone. No account. Nothing is sent anywhere." + the backup line smaller); below, a dimmed **Example month** (the same hero and row components at 50%, ribbon "Example · yours replaces this", dashed line, **never written to the database**: no totals, widgets or backups touched); tapping a ghost row opens Add pre-filled. The month card and "Set a budget" stop showing above Welcome. The tour becomes "Show me around" (5 stops) plus 6-8 one-time contextual tips with local "seen" flags. Mock-up: `first_run_ghost_before_after_light.png`.
- **Replaces:** the 0.00 hero above Welcome; the automatic tour. **Effort:** M (4-5 d; the risk is the many tests on the first-run path: keep tags `add`, `welcome_*`). **Perf:** one-shot only; no infinite animation. **A11y:** the ghost is hidden from screen readers except the ribbon text "Example"; skippable. **iOS:** same. **Dependencies:** U-03 (tiles), U-04. **Owner decision:** D8, plus HO Q12.

### U-11 · To review becomes a ritual that ends · Slice S6
- **Evidence:** cards are tall with 8 controls each (`inbox_cards_w393f10l.png`); the Categorise flow is good but has no progress (`categorise_start_w393f10l.png`); Home nudge counts exist. Copilot To Review + dot, Lunch Money "All cleared!", YNAB Card Mode "N to go" (GLOBAL L5, I24-I27); axio's silent-capture failure (INDIA L2, I3).
- **Change:** each card shows Add / Skip as the primary pair and puts Expense / Income / Transfer under "Change"; a thin progress bar and "3 to go"; a closure state with a small drawn picture "All cleared. The next text will land here."; a small review dot on unreviewed rows everywhere (Home, Search); a capture **heartbeat chip** on Home or Capture: "Last bank text seen yesterday 8:14 pm", amber after a quiet stretch with one tap to "Why isn't capture working?" (stores a timestamp only). **Effort:** M (3-4 d). **Perf:** none. **A11y:** progress announced; dot has the text "not reviewed". **iOS:** heartbeat counts messages that reached the app via Shortcuts (no SMS permission on iOS). **Dependencies:** U-03 for the dot colour; U-07 card. **Owner:** none (review-before-save stays).

### U-12 · Swipe row v2 and selection feel · Slice S3
- **Evidence:** `SwipeableRow` shows a flat red "Delete" text, no icon, no threshold haptic; `home_selection_mode_w393f10l.png` (CRAFT #4, 3.6; GLOBAL I41-style row actions; AUDIT per-screen D2). 
- **Change:** icon + label scale 0.8 to 1 with drag progress, background interpolates surface to errorContainer (read in draw), a THRESHOLD haptic once when the commit point is crossed (and again crossing back), optional leading "Category" swipe (off by default), selection mode: the avatar tile crossfades to a checkbox in 120 ms and the row lifts 2 dp. **Effort:** S (1-2 d). **Perf:** progress read in `graphicsLayer`/`drawBehind`; Home fling A/B. **A11y:** keep the long-press and the bulk bar as alternatives; TalkBack custom actions "Delete" and "Set category". **iOS:** haptic via U-14. **Dependencies:** F-13, U-14.

### U-13 · Categories, Goals and Tags: depth where it is thin · Slice S5
- **Evidence:** Categories reorder with 42 dp up/down triangles, counts but no spend (`categories_list_w393f10l.png`); Goals are a plain list with text links "Add or take out / Edit / Remove" (`goals_list_w393f10l.png`); there is no Tags screen (tags only through an entry or Search filters) (`AUDIT W6`).
- **Change:** Categories: this-period spend per row with a mini bar and drag-to-reorder (long-press handle; up/down stay as the accessible alternative, WCAG 2.5.7); Goals: a ring per goal with a glyph, large tabular numbers, "Add or take out" as the primary action and Edit/Remove in an overflow; a Tags list is List 2 (N-16). **Effort:** M (3-4 d). **Perf:** drag in layout phase (`Modifier.offset {}`); `animateItem` for neighbours. **A11y:** drag has the button alternative. **iOS:** same. **Dependencies:** U-07, U-08 (ring).

### U-14 · One haptic vocabulary with an off switch (the full version) · Slice S7
- **Evidence:** three code paths (`Feel`, 8 direct `LocalHapticFeedback` calls in `CaptureScreens.kt`, `pressScale`); UNDO/LIMIT meanings (F-13); no in-app switch; iPhone has no warning pattern; stock Switch/Checkbox/SwipeToDismiss play no haptics (14 + 9-10 + 10 call sites) (CRAFT #3, 3.2; GLOBAL L12, 4.6, I49).
- **Change:** `Feel { PRESS, SAVE, PAID, UNDO, THRESHOLD, STEP, WARN, LONG_PRESS, KEY }` with the table in `design_tokens_sheet_light.png`; replace every direct call and the `pressScale(haptic=)` argument with `Feel`; `LocalHapticsEnabled` fed by Appearance "Haptics: On / Off"; iOS `expect/actual` using `UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator` with `prepare()` on touch-down for swipe and scrub; throttle scrub ticks (one per 40 ms). Never on typing, scrolling, rolling digits, widgets. **Effort:** S-M (2-3 d). **Perf:** none. **A11y:** never the only cue. **iOS:** CMP-10050 (SegmentTick silent in 1.11.0-alpha04) is fixed for 1.11.0-beta03+; the scrub tick "should work" but nobody has felt it: feel it on the owner's iPhone. **Dependencies:** F-13. **Owner:** D14.

### U-15 · Odometer digit roll on change (not on open) · Slice S7
- **Evidence:** `RollingAmount` interpolates a `Float` and re-formats the whole string each frame, reading `roll.value` in composition (recomposes `Text` for 576 ms); Robinhood Ticker rolls per digit (CRAFT #5, 3.4; GLOBAL 4.6/feasibility).
- **Change:** split the formatted string; only digits that changed roll (clipped `Box`, `graphicsLayer { translationY }`, shorter direction), others stay; used on Home month figure, Budget left, Safe to spend, account tile balances, Reports Spent; **only on change**, never on open; semantics carry the final text. **Effort:** M (3-4 d). **Perf:** draw-phase only, `tnum` required (U-04); measure Home with the total rolling (frames over 16.7 ms), compared with today's count-up. **A11y:** no roll under Reduce motion (one cross-fade). **iOS:** same. **Dependencies:** U-04, F-02.

### U-16 · Success micro-moments, no confetti · Slice S6
- **Evidence:** `MotionUiTest.payingABillShowsAPaidCheck` exists; CRAFT #14 (drawn check with `PathMeasure`, "no-spend day" ring, "all reviewed"); GLOBAL L10, 4.8, I45; Monzo's repeated-confetti complaint (anti-pattern).
- **Change:** one shared `SuccessCheck` (a tick drawn over 280 ms, SAVE haptic) on Save, Paid, "All cleared", "Backup done"; a small ring on no-spend days in the Calendar ("6 no-spend days this month" in the summary); under Reduce motion it appears complete. **Effort:** S (1-2 d). **Perf:** draw-phase. **A11y:** announce the result text. **Owner:** D2 (milestone moments belong here, about six a year, opt-in).

### U-17 · Reports layout: segmented controls, one sentence, a trend · Slice S4
- **Evidence:** two rows of chips (4+4) before any chart (`reports_period_w393f10l.png`, `AUDIT` Reports H3 D3); By period / year / FY / Any dates and Category / Account / Tag / Merchant are chip rows, not segmented buttons.
- **Change:** two compact segmented rows (Month | Year | FY | Dates; Category | Account | Tag | Merchant), the period arrows, **one sentence headline** (U-08), a slimmer summary (Spent, Income, Net as three numbers, not a full hero card), then the chart. **Effort:** S-M (2-3 d, partly shared with U-08). **Perf:** the mode switch is already good (p95 42 ms); keep. **A11y:** segmented = radio group. **iOS:** same.

### U-18 · Text walls become summaries with "Details" · Slice S1 (pattern) and S9 (all pages)
- **Evidence:** Your data, Notifications, Privacy, Capture, Currency, Appearance, Earlier spending are long paragraphs under every control (`data_page_s2_w393f10l.png`, `notifications_settings_s1_w393f10l.png`, `currency_list_w393f10l.png`); `AUDIT W9`; Currency's long warning in red. Data page scored H2 D1.
- **Change:** each page leads with a one-sentence summary and cards (Backup / Export / Import, one sentence each), full text behind "Details"; red only for a real danger; Appearance gets a **live preview** (a mini card in the chosen accent/density) and more room for the accent row. **Effort:** M (4 d across pages). **Perf:** none. **A11y:** "Details" is a disclosure widget (expanded state announced). **iOS:** same. **Dependencies:** U-07 (card, expander).

### U-19 · Instant first frame, no spinner · Slice S7
- **Evidence:** `OpeningScreen` is a `CircularProgressIndicator`; data composed in 0.2-0.7 s on the emulator (CRAFT #13/3.12; GLOBAL I-none; PERFORMANCE cold start 280-300 ms).
- **Change:** draw the real Home chrome (frame of the month card in the accent gradient, the bar) at once and fade numbers in; persist the last month total in a settings row so even the first frame can show a real figure. **Effort:** S-M (2 d). **Perf:** measure cold start `am start -W` unchanged (A/B 24 starts) and first-frame time; the stored figure is one settings read. **A11y:** nothing announced until data is ready. **iOS:** launch screen is static; same Home chrome after.

### U-20 · Predictive back with a preview inside the app · Slice S7
- **Evidence:** `PlatformBackHandler` is the plain `BackHandler`, so no finger-following preview (CRAFT #7, delta 8; AUDIT section 4 "no predictive-back preview"). **Change:** `NavigationBackHandler` progress-driven scale/shift in `ScreenHost`, iOS edge swipe through the same handler (iOS interactivity **unverified**). **Effort:** M (4 d). **Risk:** may add the `navigationevent-compose` coordinate (D13). **Perf:** progress read in `graphicsLayer`. **A11y:** Back button always present.

### U-21 · Low-RAM degrade · Slice S7
- **Evidence:** Motion is a manual setting only; BHIM "animations are still jittery", CRED memory-heavy; ~57% of 2025 Indian shipments under $200 (INDIA L9, I32, anti-pattern 15; GLOBAL anti-pattern 24).
- **Change:** `isLowRamDevice` (Android) or three consecutive slow windows from the frame logger switches to Quick motion, no `HeroGlow` blur, no spark draw-in, no wallet-stack spring, with a line in Appearance ("Simplified for this phone") and an override. **Effort:** S-M (2 d). **Perf:** it is the perf feature; verify on a 2-3 GB phone (**none available in this repo's measurements**). **A11y:** none. **Owner:** D15.

### U-22 · Glass bar v2: scroll-edge scrim, Reduce-Transparency path · Slice S7
- **Evidence:** the bar is an opaque surface plus a lit pill under the content, no scroll-edge treatment, no Reduce-Transparency/high-contrast path (CRAFT #15, 3.5; HIG Materials). `home_typical_w393f10l.png` shows content cut hard above the bar.
- **Change:** a 24-32 dp vertical gradient scrim above the bar and below the top area (drawn once, no blur); optional floating bar over content with bottom inset padding on every `HomeBar` screen; `expect fun systemReduceTransparency()` (iOS `UIAccessibilityIsReduceTransparencyEnabled`, Android high-contrast text) makes the bar opaque with a visible border. **No real backdrop blur** (D16). **Effort:** M (3 d). **Perf:** scrim is free; if the floating bar is chosen, scroll A/B. **A11y:** the Reduce-Transparency path. **iOS:** benefits most.

### U-23 · Widgets that show something, not an empty masked card · Slice S8
- **Evidence:** Today 2x2 is masked by default with a big empty card (`widget_today_and_4x2_light.png`); the iPhone Today widget is `.systemSmall` only with a hard-coded purple capsule and no `widgetRenderingMode` handling (GLOBAL 4.7; CRAFT 3.9; AUDIT per-screen "Widgets" row).
- **Change:** unmasked Today shows one number and the pace line; 4x2 adds the pace bar and two quick-entry chips; masked keeps the chips (capture still works) and a "Amounts hidden" line; iPhone widgets handle tinted/clear rendering modes; an "Allow widgets" kill switch (GLOBAL I31). Mock-up: `widgets_lockscreen_concepts_light.png` (Android part). **Effort:** M (3-4 d). **Perf:** hourly update unchanged (PERFORMANCE suggestion 3). **A11y:** Glance has no tabular figures or animation (RemoteViews). **iOS:** Swift. **Dependencies:** U-06.

### U-24 · Large screens, fold and pointer · Slice S9
- **Evidence:** the fold-size window shows one very wide Add column with a 1,000 dp keypad (`add_expense_empty_w864f10l.png`); no navigation rail (CRAFT #19, G14, 3.10); AUDIT medium item 7.
- **Change:** Add/Edit opens in the detail pane beside the list, pad no wider than ~480 dp; `NavigationRail`-style `HomeBar` from 600 dp wide (the pill glides vertically); hover overlay and right-click = long-press; the summary on the empty detail pane stays "No" (decided HO Q10). **Effort:** M (4-5 d). **Perf:** none expected. **A11y:** keyboard shortcuts exist; add focus rings. **iOS:** iPad is not targeted (`TARGETED_DEVICE_FAMILY "1"`): product decision, not craft.

### U-25 · Dark theme, high contrast, theme options · Slice S9
- **Evidence:** dark and 200% are untested by the accessibility framework (light, default size, API 26 only: CRAFT 3.11, ACCESSIBILITY_FINDINGS); hero small text contrast 2.9-6.1:1; no true-black, no dynamic colour. 
- **Change:** extend `AccessibilityUiTest` to dark and font scale 2.0; `expect fun systemHighContrast()` (Android UiModeManager contrast, iOS darker system colours) strengthens outlines and removes glass alpha; "Dark (black)" theme option; Android 12+ "Match my wallpaper" accent (iOS returns null). **Effort:** S-M (3 d). **Owner:** D18.

### U-26 · Microcopy pass and quieter tone · Slice S6
- **Evidence:** copy is accurate and long (toasts and nudges heavy); the owner's feeling of "not delightful" is partly tone (`AUDIT W9`); CRAFT 3.13 (Monzo, Mailchimp), GLOBAL L15, INDIA L8 (neutral, specific, confirm/dismiss; no moralising; PhonePe titles of 5 words or less).
- **Change:** rules: toasts at most 7 words and a number ("Added. Chai ₹20"), buttons are verbs (never "Submit"/"OK"), errors say what to do first ("Type an amount to save"), one calm exclamation a month at most and none about debt or limits, numbers in copy match the figure beside them, "bank text" on iPhone where it is a Shortcut, report not judge ("₹1,200 over Food"). A copy audit of nudges, toasts, empty states. **Effort:** S (2 d). **Perf:** none. **iOS:** the Shortcut wording. **Dependencies:** none.

### U-27 · Search results rows are heavier than Home rows · Slice S3
- **Evidence:** Search results scroll **43% janky** (p50 36 / p95 61 ms) vs Home fling 9.6%: results rows carry shared-element modifiers; Search typing 18.5% (`AUDIT` section 4); `AUDIT` medium item 6. **Change:** drop `sharedContainer` except on the tapped row (done with U-02); the total chip "12 results · ₹6,090.00 spent" stays. **Effort:** S (0.5 d, inside U-02). **Perf:** A/B the same scroll; target to match Home rows.

### U-28 · Home modules: drag to reorder (up/down stay) · Slice S5
- **Evidence:** Customise-home reorders with up/down text buttons (CRAFT #17, 3.8). **Change:** long-press handle drag with LONG_PRESS + STEP haptics; the two data-rich modules from HOME_OPTIONS option C ("Where it went", "Coming up") as **off-by-default** sections (data already computed). **Effort:** M (3-4 d). **A11y:** keep buttons (WCAG 2.5.7). **Owner:** HO Q8 was "no": ask only if you want them.

(List 1 has 28 ranked items plus the 16 Slice 0 items.)

---

# 5. LIST 2: NEW features the research suggests (kept separate, scored, not part of the "look and feel" ask)

Score columns: **Pull** = my judgement of daily-opening pull 1-5; **Evidence** = A (primary/source code), B (secondary/reviews), C (inference); **Effort** S/M/L; **Risk** = privacy/permission/test churn. "Verdict" is my recommendation, not a decision.

| ID | Feature | Pull | Evidence | Effort | Risk / needs | Verdict |
|---|---|---|---|---|---|---|
| N-01 | **Smart "chai 20" field** with a preview chip (never auto-saved); Hinglish tokens (`ke`, `diye`, `auto`) | 5 | A (Fina, Cashew, Mera Kharcha: GLOBAL L4/I13; INDIA I10) | M (4 d; pure Kotlin in `core:domain`, host-tested) | none; Hinglish tokens need a native-speaker check | **Build with S2** (optional extra) |
| N-02 | **Maths in the number pad** (`120+45`, `450*1.18`, "00" key) with live result | 4 | A (Actual, Monefy, YNAB 2026-06: GLOBAL I14) | S (1 d) | none; verified absent in our code | **Build with S2** |
| N-03 | **Monday recap card on Home** (3 lines) with the notification as a pointer; tap-for-why listing the 3 entries behind an insight | 4 | B (Monarch, Up; GLOBAL I42/I43; INDIA I30) | M (3-4 d) | tone risk: descriptive only, opt-in | After S6 |
| N-04 | **Weekly rhythm dots + ring + Pause** | 3 | B (Apple rings, Finch; GLOBAL I44) | M | touches the no-streak decision | **Do not build** (D2); keep Pause switch only |
| N-05 | **Festival envelope** ("Diwali fund", days left, last year's total) + opt-in festival accent | 3 | B (GPay/Jupiter facts; INDIA L11/I21-I23) | M (needs a bundled festival-date table) | none | Later, only if you like it |
| N-06 | **Khata person page** ("You gave / You got", running balance, Settled) + polite **reminder composer** (WhatsApp/SMS share sheet) | 4 | A/B (Khatabook/OkCredit; INDIA L7/I27-I29) | S-M / M | none (text composed locally, shared by the user's tap) | Good fit for Lent/Borrowed; after S6 |
| N-07 | **Assets-lite** (gold, PPF/EPF/FD/RD/SIP, property) and **kameti/chit tracker** | 3 | B (INDmoney, Fold Custom Entries; INDIA L14/I25-I26) | M-L | scope creep: new tables, backup format | Later |
| N-08 | **Voice entry** ("auto ke 80 diye") through the on-device recogniser | 4 | B (Mera Kharcha is server-based; INDIA I10; GLOBAL I22) | M-L | **new microphone permission + policy**; Hindi on-device quality unverified | **Owner decision**; not now |
| N-09 | **Hindi / Hinglish UI** (Latin digits kept) | 5 in India | A (Kantar-IAMAI; INDIA L4/I9) | L (extract ~600 string calls first) | none | **Later (D11)**; primer first |
| N-10 | **iPhone Lock Screen widgets** (circular, rectangular, inline; privacy-sensitive) and **Control Center / Action-button control** (iOS 18) | 4 | A (WidgetKit/ControlWidget docs; CRAFT #16; GLOBAL I21) | M (Swift only) | free-signing limits on extensions [INFERENCE]: try one first | **S8 (optional)** with mock-ups `widgets_lockscreen_concepts_light.png` |
| N-11 | **"Verify it yourself" page**: "Permissions we do NOT have" (reads the manifest on Android), airplane-mode check, how the app is paid for, fraud-awareness line | 4 | A (Actual, BachatPe, Klera; GLOBAL L14/I28; INDIA L1/I1-I4/I8) | S (1-2 d) | none; iPhone page states its own facts and points to App Privacy Report | **Build in S6** |
| N-12 | **Capture "ledger"** (what was read / ignored by kind, never content) and "Stop reading texts and delete what was read" | 3 | C (white space; INDIA I6/I7) | S-M | none | Later |
| N-13 | **Receipt / UPI screenshot capture** that pre-fills merchant, date, amount | 3 | B (Wise, Mooncard; GLOBAL I23) | L | ML Kit exists on device | Later |
| N-14 | **Tax tags and ring** (80C/80D/80G/HRA, advance-tax dates) | 2 | C (INDIA I24) | M | regime-dependent; keep opt-in | Later |
| N-15 | **Your year** (December, opt-in) and **swipeable month cards** with blurred-amount sharing | 3 | B (Monzo, GPay Rewind; GLOBAL I47; INDIA I30) | M-L | Monzo's Guardian lesson: descriptive only | Later |
| N-16 | **Tags page** (list, count, rename) | 2 | A (AUDIT W6) | S-M | new screen | Later |
| N-17 | **Rebalance/"Move budget" sheet** and **budget presets** (last period / average of three) | 3 | B (Copilot, Lunch Money; GLOBAL I9/I10) | M | none | After S5 |
| N-18 | **Committed vs free** line ("₹2,100 of what's left is already spoken for by bills due") | 4 | B (Fina, Emma, Monzo; GLOBAL I5) | S-M (data exists in safe-to-spend rows) | none | After S5 |
| N-19 | **Evening "Nothing to add today"** one-tap action and **notification direct reply** (Android RemoteInput) | 3 | B (NoBuy; GLOBAL I19/I20) | S-M / M | notification text input; feeds N-04 | Later |
| N-20 | **Quick-entry shortcuts**: Android dynamic launcher shortcuts (`ShortcutManagerCompat`, no new library), long-press Add for pinned entries | 4 | A (CRAFT #20; GLOBAL I18) | S | none | **S8 (cheap)** |
| N-21 | **Per-entry "hide this one"** (shows "Private" until unlock) | 2 | B (Paytm Hide Payments; INDIA I33) | S-M | export/widget masking rules | Later |
| N-22 | **Cash-in-hand glance** on Home and the ATM-withdrawal prompt | 3 | B (INDIA I19) | S-M | none | Later |
| N-23 | **Backup status line + "Test restore"** into a scratch database | 3 | B (Signal; GLOBAL I29) | S / M | none | **Build the status line in S6** |
| N-24 | **Day bars filled with that day's category colours**; **6-month micro-bars per category row**; **hatched future bars** | 3 | B (Apple Card, Tufte, Spendee; GLOBAL I36/I41/I11) | M each | needs a by-day-by-category aggregate [INFERENCE] | After S4 |
| N-25 | Transliteration-aware search ("kapde" finds Clothing) | 3 | B (INDIA I11) | M | alias table | Later |
| N-26 | **Month-close moment** (calm 2 s summary when a month ends under budget) | 3 | C (INDIA I31) | S | tone | With S6 milestones |
| N-27 | **Live Activities / Dynamic Island, Android 16 Live Updates, Wear tiles, real backdrop blur, confetti** | 1 | A | L | new permission, poor fit, GPU cost | **Do not build (D16)** |

---

# 6. HOW WE WILL MEASURE FLUIDITY

**Principle:** one table of the same interactions, measured on the same three setups, before and after each slice. Emulator numbers are relative only (software drawing); real-phone numbers decide.

**Android (no new library, no permission):**
1. **FrameMetrics debug logger** (F-15): `Window.addOnFrameMetricsAvailableListener` aggregates `TOTAL_DURATION` into p50 / p90 / p99 and counts over 16.7 ms, 8.3 ms (120 Hz) and 700 ms (frozen), one `Log` line per screen change and a small overlay; compiled out of release.
2. **Scripted relative A/B on the emulator** exactly as the audit did: `dumpsys gfxinfo app.expenses` reset before each interaction, 3 repetitions, `screenrecord` clips to frame strips (`fluidity_*.json`, `strip_*`), plus the PERFORMANCE.md protocol for timing gates (alternating installs, 6 runs, 30% threshold, quiet host). Always read against the plain-scroll baseline (Home slow scroll p50 23 ms, p95 34 ms).
3. **`PerformanceBudgetTest` and `RefreshBudgetTest` stay the hard gate** (new row and total within the 50 ms phone budget, rest within 200 ms); add relative gates: Add-form first idle, entry-detail open, Reports first draw, each against the base commit (A/B), not absolute numbers.
4. **Perfetto FrameTimeline** (Android 12+) on a real phone for the cause of any bad frame; Layout Inspector recomposition counts for `TrendChart`, `RollingAmount`, `WalletStack`; Compose compiler reports for unstable classes in `RowUi`/`ReportUi`.
5. Play Console Android vitals (slow/frozen frames) if the Play build has enough users [INFERENCE].

**iPhone:**
1. A debug-only **`CADisplayLink` logger** in `iosMain` (same p50/p90/p99 report). `CADisableMinimumFrameDurationOnPhone` is already true (120 Hz).
2. **Instruments > Animation Hitches** run from Xcode on the owner's phone (owner action); MetricKit scroll-hitch ratio is optional.
3. There is **no iPhone number anywhere today** (CRAFT G10): the first deliverable is the table below on the owner's phone.

**Budgets (starting values [INFERENCE], tune on data):**
| Interaction | Target on a real phone |
|---|---|
| Touch response (press visible) | under 100 ms (feedback tier 90 ms) |
| Add open: first frame with content, no frame over 100 ms | settled by 380 ms |
| Entry open (row to detail) | settled by 320 ms, no frame over 100 ms |
| Tab change | settled by 250 ms, p90 under 16.7 ms (8.3 ms on 120 Hz) for tab glide, Add form and digit roll |
| Home / Search / Reports scroll with 100,000 rows | janky frames under 2% on a low-end phone (emulator today: 0.3-0.6% on Home, Search results 43%: bring to Home's level) |
| Cold start `am start -W` | no regression vs the base commit (today ~300 ms emulator) |

**First real-phone baseline the owner can run (10 minutes, needs F-15 or a stock build):**
1. Seed the 1,500-entry ledger (the debug seed used in the audit) or use your real data.
2. Android: Developer options > "Profile HWUI rendering" off; run `adb shell dumpsys gfxinfo app.expenses reset`, do the script, then `adb shell dumpsys gfxinfo app.expenses` and send the "Janky frames / 50th/90th/95th/99th percentile" lines. Script: cold start 5x; open Add 10x; tap an entry 10x; Home to Search to Reports to More to Home 5 times; scroll Home for 30 s.
3. iPhone: Xcode > Product > Profile > Animation Hitches, same script, export the trace; or the in-app logger line.
4. Rate by feel 1-5 (not measured): Add open, entry open, tab change, scroll, "does anything feel late?". Note the phone model and whether Low Power Mode was on.

---

# 7. THE SLICES IN DETAIL

Every slice: scope, before/after, test plan, performance budget, risks, effort, owner decisions. "Screenshot set" = 393 dp and 360 dp, light and dark, 200% text, 864 dp fold window, taken like the audit (`tools/emulator_setup.sh`, `/tmp/emu.lock`).

### S0 · Fix now + quick wins + measurement (about 11 d)
- **Scope:** F-01 to F-16.
- **Before / after:** notices respect Hide amounts on Android as they do on iPhone; Filters slide in; Reports never shows bare "Loading..."; the keypad leaves with the form; four 200% breaks fixed; Budget in More; icons on the bulk bar; accent-selected chips; Undo no longer feels like an error; Add opens in a capped time; frame logger exists. Mock-ups: `notification_privacy_before_after.png`, `motion_storyboards_light.png`.
- **Test plan:** reproduction of F-01 on the emulator first, then a host test (with Hide amounts on, no notice text has a digit or ₹); `MotionUiTest` (Add within cap at 1.8x, Reduce motion fade, flash obeys Motion); `AccessibilityUiTest` extended to dark and font scale 2.0; `PrivacyUiTest` for Hide amounts in notices; screenshot set for the six touched screens.
- **Performance budget:** no regression in `PerformanceBudgetTest`/`RefreshBudgetTest`; Add open frames over 100 ms (today a 300-417 ms frame) must not get worse; Filters close more than 3 frames; measured with F-15 and the audit scripts.
- **Risks:** shortening Add exposes the composition hitch (it is fixed properly in S2); CMP bump moves layouts; iPhone notification privacy cannot be unit-tested.
- **Owner decisions:** D3, D9, D12, D14 (part), D17.

**S0 status (2026-11-14, Slice 113 in `docs/PHASE2_PROGRESS.md`; ids as made, a rebase may renumber them):**
- DONE `8f57333` F-01 (reproduced first on the emulator; Hide amounts or App lock: no amount on either phone; public versions for every amount kind; evidence in `~/Documents/personal/expenses_builds/ui_s0/`).
- DONE `24fd987` F-15 (frame logger on both phones; the real-phone baseline, D17, is still the owner's).
- DONE `fae12a4` F-02 (tiers: feedback 90 unscaled, chips 150, tab 250, push 400, Add 380, arrivals still 1.8x, Slow keeps the caps growing with it; Reduce motion = 100 ms cross-fade; ring, bars, swipe flash through Motion), F-03, F-04 (skeleton, and Reports reopens at once when the ledger signature is unchanged), F-05 (the keyboard hides when the form starts to leave), F-06 (Reports amounts one line + tiles stack from 140% text, Attach buttons wrap, backup nudge stacks, Search chips wrap; checked on a device only by the tests listed in Slice 113, the 393/360 dp screenshots at 200% were NOT taken), F-07, F-08 (icons; the count text "N selected" was already there; the LONG_PRESS haptic is NOT done), F-09 (one `FilterChip` for the app: accent container, bold label, check), F-13 (Android constants tested; iPhone UIKit generators compiled only).
- DONE (commit "UI S0 F-14", after `3773348`) F-14 / D12: `compose-multiplatform` 1.12.1 to 1.12.2 in `gradle/libs.versions.toml` (no other version moved; no new library or permission). API 34 device batch on that build: 101 tests, 0 failures (HomeBaseUiTest, HomeOptionBUiTest, MotionUiTest, UiS0 x3 classes, ReportsUiTest, SearchFiltersUiTest, AccessibilityUiTest, UxPolishUiTest, WidePaneUiTest, NoticePrivacyUiTest); iPhone `:shared:compileKotlinIosSimulatorArm64` compiled with no error output. The screenshot suite for layout moves was NOT run.
- NOT DONE: F-10, F-11, F-12, the tests and measurements listed under "Not done" in Slice 113.

### S1 · Tokens + consistent chrome + tabular figures (about 9 d)
- **Scope:** U-07, U-04, U-18 (pattern), F-09 follow-ups.
- **Before / after:** 14 radii to 5, 3 header styles to 1, ~9 card styles to 3, 5+ sheet styles to 1; selected chips in the accent; segmented controls; numbers do not jitter. Mock-up: `design_tokens_sheet_light.png`.
- **Test plan:** host tests for tokens; screenshot set for 12 migrated screens first (Home, Add, Search, Reports, More, Budget, Accounts, Bills, Categories, Goals, Recurring, Capture); `AccessibilityUiTest` dark and 200%; width test of `tnum` on both platforms.
- **Performance budget:** Home fling and the 100,000-row scroll equal or better (A/B); cold start unchanged.
- **Risks:** a long tail of one-off shapes; test tags must survive (the tag `add` is used in 102 test files per HOME_OPTIONS); iOS `tnum` may not work.
- **Owner decisions:** D4, D10.

### S2 · Add/Edit redesign + read-only entry detail (about 11 d; +1 N-02; +4 N-01)
- **Scope:** U-01, U-02 (+ U-27), optional N-02 maths pad, optional N-01 smart field.
- **Before / after:** a form of ~2.5 screens becomes one screen with amount, category row and the Save button visible with the pad up; a 300-400 ms first-frame hitch and an 833 ms glide become a short composed-in-one-frame sheet in <= 380 ms; tapping an entry shows a detail instead of an editor and a 430 ms gap disappears. Mock-ups: `add_form_before_after_light.png`, `entry_detail_before_after_light.png`, storyboards.
- **Test plan:** keep every `testTag`; update the ~120 device tests that open Add; add: Add with income/transfer/loan/split/foreign/tags/expect-back still saves; category row "More" sheet; detail view actions (Edit, Duplicate, Delete with Undo); screenshot set incl. fold (Add beside the list).
- **Performance budget:** first idle of Add and of the detail A/B against the base (30% gate); no frame over 100 ms on either open; `RefreshBudgetTest` unchanged; Home scroll A/B (shared container removed from rows) must not rise above today's 0.3-0.6%.
- **Risks:** biggest test churn of any slice; income/transfer/loan variants multiply layouts; the amount-in-words and keypad focus logic; the "More" sheet must not hide a needed option (split, expect back).
- **Owner decisions:** none required (N-01 Hinglish tokens need a native speaker).

### S3 · Category identity + rows + neutral amounts (about 9 d)
- **Scope:** U-03, U-05, U-12, U-27 (if not done).
- **Before / after:** letter tiles and all-red amounts become glyph tiles in stable colours with neutral amounts and a swipe that shows its action; mock-up `rows_glyph_tiles_before_after_light.png`.
- **Test plan:** host test on the resolver (stable per id, rename does not recolour), Machado test, merchant monogram tests; screenshot set Home/Search/To review/Categories; `PrivacyUiTest` (Hide amounts hides tile-adjacent amounts); haptic switch honoured.
- **Performance budget:** Home fling and 100,000-row scroll equal or better; icons `remember`ed; APK/IPA size delta recorded (icon set).
- **Risks:** a one-time colour change for every user (D6); icon availability on iOS klib; some people will miss red (D1 switch).
- **Owner decisions:** D1, D5, D6.

### S4 · Charts system (about 10 d)
- **Scope:** U-08, U-17.
- **Before / after:** three chart styles to one; butt-cap donut and un-scrubbable bars become a round-capped donut with the total, scrubbable bars with a bubble and an average line, a sentence headline, and "Show as table". Mock-up `reports_charts_before_after_light.png`.
- **Test plan:** device tests: scrub changes the bubble, tap still opens the period, table shows the same numbers; `AccessibilityUiTest` for chart semantics; Machado palette test; screenshot set (Reports modes: period, year, FY, custom, account, tag, merchant).
- **Performance budget:** Reports open and scroll A/B equal or better (today scroll 16.7% janky); no `Path` rebuilt per frame; recomposition count per bar drops to ~0 during the sweep.
- **Risks:** VoiceOver mapping of custom actions unverified; by-day-by-category aggregate is NOT in this slice (N-24); chart text cost (`TextMeasurer`): cache labels.
- **Owner decisions:** D6.

### S5 · Pace: Home hero, Budget, Goals/Categories (about 8 d)
- **Scope:** U-06, U-09, U-13, U-28 (drag only), N-18 optional.
- **Before / after:** hero and budget bar gain "today" and words; the Budget screen opens on a ring and the limits; Goals get rings; Categories show spend and drag. Mock-ups `home_hero_pace_before_after_light.png`, `budget_pace_before_after_light.png`.
- **Test plan:** host tests for pace maths (fraction of period, projection, the words at boundaries, financial-year and salary-day periods); keep tags `month_spend`, `budget_summary`, `balance_chip_*`, `home_trip_card`; Hide amounts hides bars, ring and ghost line; 150%/200% layouts (graph dropped as today).
- **Performance budget:** `RefreshBudgetTest` (the ghost-line series is one more aggregate: must stay within 50 ms for the new row and total, 200 ms for the rest); first entry still on the first screen (HO measurement table: dp from the top) at 393 and 360.
- **Risks:** "On pace" copy wrong for irregular budgets (rent due late in the month): bills still to come must be excluded from the projection the way Safe-to-spend does; people dislike judgement words: keep descriptive.
- **Owner decisions:** D7.

### S6 · First run, review ritual, success moments, tone (about 7 d)
- **Scope:** U-10, U-11, U-16, U-26, N-11, N-23 (status line), N-26 optional.
- **Before / after:** a blank-ish first screen becomes one sentence, one button and a ghost Example month; To review ends in "All cleared"; saved/paid/cleared get one drawn tick; the copy gets shorter and calmer; a "Verify it yourself" page. Mock-up `first_run_ghost_before_after_light.png`.
- **Test plan:** first-run path tests (many); ghost never writes (host test: DB empty, totals zero, widget data empty, backup contents unchanged); tour opt-in flag; heartbeat chip states; screenshot set first run + To review.
- **Performance budget:** cold start unchanged (A/B); no infinite animation anywhere.
- **Risks:** touches tests that use the first-run path; ghost semantics for screen readers; "All cleared" must not appear while texts are pending.
- **Owner decisions:** D2, D8, HO Q12.

### S7 · Haptics, motion system, first frame, low-RAM, back, glass edge (about 8 d)
- **Scope:** U-14, U-15, U-19, U-20, U-21, U-22.
- **Before / after:** one haptic vocabulary with a switch; digits roll only where they change; an instant Home shell; a back swipe that previews; automatic simplification on weak phones; content slides under a scrim above the bar.
- **Test plan:** `Feel` unit mapping table; `MotionUiTest` additions (roll only on change, Reduce motion), a Reduce-Transparency screenshot, low-RAM mode screenshot; felt-review on the owner's phone and one low-end Android.
- **Performance budget:** frame logger p90 under 16.7 ms for tab glide, Add form and digit roll on a real phone; cold start A/B (24 starts); battery: no infinite animations.
- **Risks:** haptic taste differs by actuator (constants only, no custom waveforms: no VIBRATE permission); predictive-back dependency (D13); iOS interactive back unverified.
- **Owner decisions:** D13, D14, D15.

### S8 · Widgets, Lock Screen, Control Center (about 7 d + Swift)
- **Scope:** U-23, N-20, optional N-10.
- **Before / after:** useful Today/4x2 widgets and a masked state that keeps chips; iPhone tinted-mode support; optionally Lock Screen widgets and an Add-expense control. Mock-up `widgets_lockscreen_concepts_light.png`.
- **Test plan:** Glance previews at the standard sizes, masked and unmasked; iOS widget snapshots in light/dark/tinted; privacy redaction (`.privacySensitive()`) checked **on a device** (the simulator cannot show the locked state reliably).
- **Performance budget:** widget update cadence unchanged (hourly); no extra wakeups.
- **Risks:** free-signing limits for extensions and controls (try one first); Glance cannot do tabular figures or animation.
- **Owner decisions:** none (Live Activities and Wear: D16 no).

### S9 · Large screens, fold, dark and contrast polish, theme options (about 8 d)
- **Scope:** U-24, U-25, U-18 (remaining pages).
- **Before / after:** Add/Edit beside the list on a fold; a rail at 600 dp; hover and right-click; dark and 200% proven by test; high-contrast path; true-black and wallpaper accent.
- **Test plan:** `WidePaneUiTest` additions; screenshot set incl. 864 dp; accessibility tests in dark/200%; hover tests are manual.
- **Performance budget:** two-pane Search cost (PERFORMANCE.md) unchanged; rail adds no per-frame work.
- **Risks:** fold test devices are emulator-only; iPad is not targeted.
- **Owner decisions:** D18.

---

# 8. DEFINITION OF DONE (every slice)

1. **Code:** every changed screen uses the tokens (S1 onwards); no new radius, header or card style; old `testTag`s kept (`add`, `bar_add`, `home_trip_card`, `month_spend`, `budget_summary`, `balance_chip_*`); no new library or permission (D12/D13 asked first).
2. **Tests:** host tests for every pure rule (pace maths, resolver, notification private text, amount formatting); device tests for the new flow and for the old flow it replaces; `MotionUiTest` (speed at 1.8x, Reduce motion, no stuck transition); `AccessibilityUiTest` run in **light and dark, at default and 200% text**; `PrivacyUiTest` (Hide amounts hides every new amount, bar, ring, ghost line, notification text).
3. **Screenshots:** 393 and 360 dp, light, dark, 200% text, and the 864 dp fold window for every touched screen, compared with the mock-up file named in the slice; touch targets still 48 dp (`touch_targets_*.json` scan); first entry on the first screen at 393/360 (HOME_OPTIONS table); rows visible and tap counts for the six key tasks not worse (audit tap-count table).
4. **Performance A/B:** `PerformanceBudgetTest` and `RefreshBudgetTest` green; relative A/B against the base commit (alternating installs, 6 runs, 30% threshold) for cold start, the slice's hero interaction and the 100,000-row scroll; the frame logger line for the three hero motions attached to the slice notes; once per release, the owner's real-phone numbers.
5. **Platforms:** iOS simulator screenshots of the same screens; known iOS gaps listed (tabular figures, Dynamic Type in CMP 1.12.1, VoiceOver mapping, notification previews) and tested on the owner's iPhone where the slice depends on them.
6. **Docs:** a short entry in `PHASE2_PROGRESS.md`, this backlog updated (item done / changed), `docs/PERFORMANCE.md` row if a budget moved, copy changes listed.
7. **Owner sign-off:** the slice's "Needs" decisions answered before the build starts (section 1).

---

# 9. WHAT I COULD NOT VERIFY

- **Nothing was run on a phone**, and nothing on an iPhone at all. Durations, thresholds and colours are starting values [INFERENCE]. "Faster" is a hypothesis until F-15 and the owner's baseline say so.
- The 300-430 ms hitches are the audit's emulator numbers (software drawing): their size on a real phone is unknown; their *cause* (eager composition of ~25 chips and 13 `FlowRow`s in a non-lazy column) is read from code, not profiled. The per-keystroke `draft` recreation is [INFERENCE].
- `Notifier.kt:84-91` and the notice texts were read and are as described; what the iPhone shows on a locked screen depends on the person's Show Previews setting and was not tested.
- Whether `fontFeatureSettings = "tnum"` is honoured by San Francisco in CMP 1.12.1; whether the 12-slot palette beyond the seven Okabe-Ito hues passes a colour-vision test (the test is part of S3: I chose the slot colours by eye); that every glyph in the mock-ups exists under that name in `compose.materialIconsExtended` and in the iOS klib (I typed Material icon path data into HTML; **check each name**); icon cost in APK/IPA size.
- Whether Compose semantics (`customActions`, `stateDescription`) reach VoiceOver correctly in 1.12.1; whether interactive back is available on iOS; whether `navigationevent-compose` is a new coordinate; Dynamic Type scaling on iOS (arrives in CMP 1.13.0-alpha01, not 1.12.1), so the 200% claim is Android-only evidence.
- Competitor behaviour: motion and haptics are known only from source code (Cashew, CRED NeoPop) and documents; the audits did not see video. Review quotes come from Apple's public feed and snippets (Reddit and many Play pages were unreadable); "N of M reviews cite UI" counts are researchers' keyword counts.
- The mock-ups are drawn in headless Chrome with the macOS system font (SF), not Roboto; they match the real theme values (read from `AppRoot.kt`, `Appearance.kt`, `HomeStyle.kt`, `EntryListStyle.kt`; light background `#FEF7FF`, card ≈ `#F3EDF7`, dark `#141218`, accent `#6750A4`, sampled from audit screenshots), but widths of text, glyph shapes and shadows will differ slightly in the app. Android widgets (Glance/RemoteViews) cannot do everything the widget mock-up draws (tabular figures, animation); the Lock Screen and Control Center images are concepts.
- The audit's seed data is harsher than a real person's Home (six overdue repeating items, a bill nudge); first-entry-on-first-screen claims are measured against that seed.
