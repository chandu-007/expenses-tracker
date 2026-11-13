# Global money apps: UI/UX teardown and what we should learn (research only, deltas against what we already ship)

**Research date: 2026-11-13.** Written against origin/main `3c51688` (0.13.0 plus the two sibling UI docs). Scope: how the best money, expense, budget and bank-spending apps in the world look, move, explain money and ask for trust; what users praise and complain about in the UI; and how much of it we can build in Compose Multiplatform (CMP 1.12.1, Kotlin 2.4.20) with no new libraries, no internet and no new permissions. Research only: no product code, version, emulator, Gradle or Xcode was touched. Nothing here has been run on a device.

**Evidence tags (every claim carries one; `S-xx` ids point to the URL list in Appendix A).**
- **[A]** primary: the app's own site, store listing, help centre, release notes, official docs or open-source code, read by a researcher.
- **[B]** an image a researcher actually opened and looked at (the URL is in Appendix A; no image was saved or copied).
- **[C]** a teardown, case study, press piece or review site (named).
- **[D]** a user review or forum post (platform, stars where shown, date). Apple shows no year on current-year reviews; those are treated as 2026 and marked "(year inferred)".
- **[I]** my inference or my own computation. **(snippet)** = seen only in a search result, page not opened; do not quote as fact. **[code]** read in our repo this session.
- Effort: **S** under 2 days, **M** 3 to 6 days, **L** more than a week (my estimate [I]). Delta tags: **NEW** = nothing like it ships in our app; **REFINES-x** = improves something we ship.

**How the research was done.** Eleven parallel passes (eight app slices, three cross-cutting theme slices) plus my own read of the repo. Raw worker notes (with every URL) are kept outside the repo in `~/Documents/personal/expenses_builds/ui_research/global/notes/`. A few repo claims were re-checked by me and are marked [code]; the palette contrast numbers and the Motion arithmetic were recomputed by me.

**Biggest limits (read before quoting).** (1) **No researcher saw a video or screen recording**, so motion, haptics and animation timing are stated for a competitor only where a source or open-source code states it; otherwise "unknown". (2) Google Play pages and Reddit were largely unreadable; user quotes come mostly from Apple's public review feed, App Store pages, Hacker News, GitHub issues and a few blogs. (3) Most store/marketing screenshots are curated; several (Toshl, Wallet, Money Manager, Goodbudget) are years old and show older designs. (4) The 2026 redesign backlash evidence (YNAB, Spendee) is strong; evidence for Copilot's motion and haptics is one review line. (5) Section 5 and Appendix B list every Compose/iOS claim I could not verify, each with a 5-minute check.

---

## 1. Already researched / already in our app (not repeated here)

**Earlier research in `docs/` (read first; this file adds UI evidence, not features or prices)**
- Features, prices, ratings, SMS policy, Account Aggregator, DPDP, SWOT and 15 ranked improvements: `COMPETITOR_ANALYSIS.md`, `india.md`, `tech.md`, `MARKET_RESEARCH.md`.
- Redesign churn is punished: YNAB (63 of 101 recent 1-2 star reviews cite updates/UI), Spendee 6 ("wait between every tap"), Bluecoins v13 ("awful and bloated"), PocketGuard's Insights rewrite, Emma v3: `reviews.md`, `global-budgeting.md`, `manual-trackers.md`.
- "Confusing UI, clutter" is 5.8% of substantive low reviews; "too many concepts" (YNAB, Money Manager); "Design" is about 7% of what users ask for; users want "stop changing the UI, or make it toggleable", a "simple mode", colour-blind-safe states and VoiceOver/TalkBack basics: `reviews.md` wishlist items 12, 13, 16.
- Praised looks already on record: Copilot "best-in-class design", Monarch "visually pleasing", Fold "UI is so satisfying", Ivy Wallet "satisfying to use", Cashew "NO ADS": `global-budgeting.md`, `india.md`, `manual-trackers.md`.
- Safe-to-spend family (Copilot Free to Spend, Simplifi Spending Plan, PocketGuard Leftover and Pace): `global-budgeting.md`.
- India: CRED's "casino vibe" and animation lag, "very responsive" as a wedge, 2-second entry target, OS quick-entry surfaces need native code: `india.md` 145/193/328, `tech.md` 117/173.
- Decision: **no streak or leaderboard gamification** (gentle weekly recaps instead); RevenueCat "streaks aren't the secret to retention", fintech gamification reviews: `PRODUCT_CONTEXT.md` 369, `tech.md` 149.
- AI/recap features exist elsewhere (Monarch Weekly Recap and assistant, Cleo 3.0, MoneyCoach on Apple's on-device model, Apple Wallet Insights): `tech.md` 144-152, `MARKET_RESEARCH.md`.
- Our own Home audit (16 findings) and Option B (built as Slice 112): `HOME_OPTIONS.md`, `HOME_NUDGES.md`.

**Already shipped in our UI [code: `PHASE2_PROGRESS.md` Slices 46-112, `OUR_FEATURES.md`, `shared/.../ui/*.kt`]**
- One motion language (`Motion.kt`: QUICK/STANDARD/EMPHASIZED/GLIDE = 216/396/576/828 ms at the default 1.8x, two bezier curves, spring helper) with Appearance > Motion Off/Quick/Relaxed/Slow, following the phone's remove-animations.
- Haptics enum `Feel` {SAVE, UNDO, LIMIT, PAID, TICK} (Android `HapticFeedbackConstants`, iOS via Compose `LocalHapticFeedback`).
- Container transform (row grows into its editor, `sharedBounds`), keyed `AnimatedContent` screen host, glass tab bar whose lit pane glides on a spring (translucent, not blurred), rolling month total, animated budget bar (calm/amber/red) with a limit haptic, 14-day spending line you can drag (haptic tick per day), donut ring (sweep-in, tap a slice or legend row) and rounded bar trend, calendar heat shades.
- Wallet-stack account tiles with network/tier gradients (no logos); amount read back in Indian words on the add form; optional number pad; rounded entry cards with a coloured first-letter category tile; day totals in day headings.
- Home Option B: short month card (whole rupees, `+158%` chip, one sentence, small graph), Budget-left and Safe-to-spend tiles each naming their source, trip card, ONE nudge in ONE look, account tiles, quick-entry chips, Add in the middle of the bottom bar, Customise home (4 movable sections), layout that drops extras at 150% text, two panes at 600 dp.
- Hide-amounts eye (also hides bars and graph), app lock, widget eye and masked-widget reasons, 5 Android Glance widgets, 2 iPhone widgets, Quick Settings tile, 3 App Intents.
- Seven drawn empty-state pictures, welcome card, first-week checklist, 19-stop spotlight tour, guided capture setup, searchable guide, one-line action bar and Undo.
- Opt-in weekly-recap notification, one "usual for you" insight sentence (quiet unless at least 25% and 500 rupees above usual), "left a day" figures, near/over/pace budget alerts, card/bill due reminders.
- Indian grouping, lakh/crore compact figures that truncate and never round up, amount in words, 6 accent colours (violet default), light/dark/system, comfortable/compact density, default system font, 8 report colours, swipe-to-add/skip on review cards, salary-day/monthly-start budget periods.

**Sibling research already on origin/main (cross-referenced, not repeated)**
- `docs/research/ui/CRAFT.md` (motion, haptics, charts, numbers, tokens, tablets; top-20 list): where my findings coincide I write **= CRAFT #n** and add *independent app evidence* it did not have.
- `docs/research/ui/INDIA_APPS.md` (Indian apps; 36 ideas): written **= INDIA #n** where they coincide.

---

## 2. Top 15 lessons, ranked by impact on "people want to open it daily"

Impact = my judgement [I] of the effect on daily pull, given what we already ship. "Also in" shows where a sibling doc reached the same point from other evidence.

| # | Lesson | Best evidence (apps, source ids) | vs our app | Effort | Also in |
|---|---|---|---|---|---|
| 1 | **Show pace, not only a total.** A "today" tick on the budget bar, one plain sentence ("You can keep spending 24.91 for 3 more days"), a dotted ideal-spend line, and a state word next to the colour. Make yellow mean "on pace to overspend" (a projection), not a threshold. Keep ONE primary "left" figure and frame it as not-a-target (rollover) because an exact amount left can raise late-period spending. | Cashew (bar tag + sentence, code), Monarch (tick, yellow = projection), Toshl (tick on every bar, praised 2017), Goodbudget (tick + "ahead by"), Spendee (orb + Today pill + "On track"), Bluecoins (TODAY tick on child rows), Copilot (dotted ideal line, pill "$268 under"), Wallet ("Risk of overspend" words), Think Forward study. S-CA3, S-CA5, S-MO1, S-MO7, S-TO3, S-GB2, S-SP2, S-BC2, S-CP2, S-CP3, S-WA1, S-TF1 | REFINES-AnimatedBudgetBar, HomeSpark, Safe-to-spend tiles | S | = CRAFT #10 |
| 2 | **Protect density and tap count; ship every visual change with an escape.** The two loudest 2026 backlashes are about extra taps, big cards and a hero that eats the list. Monzo published how it measured change aversion for its new home screen (a researcher's note: an 11% difference in session time on day 2 that recovered by day 14; the direction was not checked) and shipped a snooze. | YNAB (Home tab, circles replace bars, 42 of the 80 one- and two-star reviews in a 250-review sample mention UI (a researcher's keyword count)), Spendee 6 ("only two items from a long list fit"), Monzo, bunq, N26, Bluecoins. S-YN7, S-SP5, S-MZ2 | Our Slice 112 won 140 dp; guard it with a tap-count and rows-visible check, keep Compact as the "classic" mode | S (process) | |
| 3 | **First run should show the finished product, not a blank.** A dimmed "Example" month that is never saved, a shorter Welcome, privacy split into two lines, one verifiable claim, and contextual one-time tips instead of an automatic 19-stop tour. | Cashew (Preview Demo, budget set on screen 2), NN/g (tutorials and coach marks add no success, ease 4.92 vs 5.49, p=0.047), Apple HIG (optional, contextual), Up (one question per screen, "Up Yeah!" moment), Buddy (live "left of income" bar, Continue Anonymous). S-CA9, S-NN1, S-UP1, S-BU2 | REFINES-Welcome/EmptyStates/tour (HOME_OPTIONS Q12 left unresolved) | S-M | = CRAFT 3.7 |
| 4 | **Fastest entry is one smart field plus a calculator pad plus "most used first".** "chai 20" parsed on device into a preview chip (never auto-saved); `120+45` in the pad; the three most recent/frequent categories on top; the button echoes the choice ("Add 'Chai'"); title learns the category after two letters. | Fina (NL pill + confirm sheet), Cashew (title suggestion row, long-press + for pinned), Actual (math in amount), Monefy (amount, then category grid, "ADD 'CAR'"), Lunch Money (3 recent categories), Wallet (Most frequent row), Spendee 6.0.12 (swipe to flip expense/income), YNAB (calculator multiply/divide, 2026-06). S-FI1, S-CA6, S-AB1, S-MF1, S-LM2, S-WA1, S-SP4, S-YN6 | NEW (no expression evaluator in the pad [code]) | M | INDIA #10 (voice) |
| 5 | **Make review a ritual that ends.** A To-review card with "N to go", one-tap "mark as reviewed", a small dot on unreviewed rows everywhere, a widget that lists them, and a closure state ("All cleared!"). Never remove review-before-save. | Copilot (To Review card, dot, widget; suggestions start only after 30 reviewed), YNAB Card Mode (Undo, N to go, revisit skipped), Lunch Money ("All cleared!", "Everything looks good!"), Monarch (widget filter Needs review), Mooncard ("Complete the expenditure (6)"), Buddy (review removed = "Terrible interface"). S-CP2, S-CP7, S-YN3, S-LM4, S-MO5, S-MC1, S-BU1 | REFINES-To review (swipe, count, progress already ship) | S-M | |
| 6 | **Charts must work without colour.** Direct labels, 2 dp gaps, redundant encoding, a colour-blind-safe default palette, "show as table", a dashed last-period ghost line, hold-a-bar for total + top 3. I recomputed our palette: orange 1.94, light-blue 2.30, green 2.36 against white (3:1 needed for graphic marks); indigo/purple/brown share luminance (0.166/0.168/0.177). | Apple HIG Charts, WCAG 1.4.11, Okabe-Ito, Copilot (dotted previous period, same colour per category everywhere after a Sep 2026 fix), Monzo (hold bar overlay), Cashew (donut scrub), Apple Card (2019 art: day bars filled with that day's category colours). S-HIGCH, S-WCAG, S-OKI, S-CP5, S-CP18, S-MZ7, S-CA3, S-AC3 | REFINES-BreakdownRing/TrendChart | S-M | = CRAFT #12 |
| 7 | **Category identity = glyph (or emoji) on a stable colour; no merchant logos.** First-letter tiles make Food and Fun both "F" (HOME_OPTIONS finding 9). Logos need a network or a trademark licence. | Copilot (emoji pills), Monzo (logo + emoji; users fought, compromise = small category badge), Starling (tinted pictogram tile), Cashew (icon + emoji, outlined toggle), Ivy (user-chosen icon + colour), Fold reviewer asking for logos. S-CP14, S-MZ5, S-ST1, S-CA9, S-IV2, S-FO1 | REFINES-category tile | M | = CRAFT #6, INDIA #34 |
| 8 | **Keep red for "something is wrong".** Every spend row is red today. Monzo: "red shades usually mean something has gone horribly wrong"; YNAB: red = cash overspent, act now. The +/- sign already carries meaning. | Monzo, YNAB, Copilot ("+/-" and Accessible Colors toggles). S-MZ-BR, S-YN4, S-CP10; [code] `HomeStyle.kt:256` | REFINES-amountColor (owner decision) | S | |
| 9 | **A private coach is rare, descriptive and tappable for "why".** One recap a week as a Home card plus an optional notification; amounts hidden on a locked screen; a sparkle/"why" that shows the 3 entries behind an insight; no jokes about food or life goals; opt-in. | Monarch (Weekly Recap card + sparkle "why"), Apple Wallet Insights ("so far you've spent less than last month at this time"), Up (Monday notification opens last week), Monzo (Year in Monzo: Guardian story and Ombudsman complaint over judgemental copy), N26 ("vs your usual 3-month average"), Copilot (assistant asks before acting). **Verified bug [code]:** only bill notices set a private lock-screen version; the weekly recap puts rupee amounts on a locked screen. S-MO4, S-AC4, S-UP3, S-MZ4, S-N261, S-CP8; `Notifier.kt:84-91` | REFINES-weekly recap | S (bug), M (card) | |
| 10 | **Habit without guilt: a weekly rhythm with pause, rare quiet milestones, no streak counter.** Evidence that streaks raise retention and that escape valves raise it further sits next to evidence of streak anxiety. Needs the owner's OK because it touches the PRODUCT_CONTEXT non-goal. | Apple Activity (pause up to 90 days, Monday review), Finch (repair hammer, no failure state), Gentler Streak (ADA 2024: "optimistic and encouraging"), Duolingo (Weekend Amulet: 5% less likely to lose streak), Guardian/Smashing on anxiety, Toss (congratulates a loan paid off), Simplifi (weekly/monthly achievements, cannot be disabled). S-APPLEPAUSE, S-AD24, S-DUO, S-GUARD, S-SMASH, S-TS1, S-SF2 | NEW (needs decision) | M | CRAFT #14 (milestones only) |
| 11 | **Give numbers a voice: tabular figures, a figures-only OFL font, quiet rupee sign and small paise.** No `tnum` anywhere in our code [code]. | Toss (fixed-width numerals chosen on purpose), Cash Sans (mono cut), Ivy ("USD" thin, "9,326" heavy, ".55" tiny and raised), Lunch Money (mono identity), Fina (serif numerals). Measured: Plus Jakarta Sans 176 KB and Manrope 165 KB have `tnum` and the rupee glyph; DM Sans has no `tnum`. S-TS-FONT, S-IV2, S-LM3, S-FI1 | NEW | S-M | = CRAFT #1, INDIA #12 |
| 12 | **Haptics: documented meanings, an off switch, and silent stock controls fixed.** Apple HIG and Android guidance: use each pattern for its documented meaning, make haptics optional. Our UNDO maps to the error pattern; LIMIT to a medium impact; the iOS `KeyboardTap` type is silent. Sync a haptic to the visual peak (Robinhood) and fire a press haptic on touch-down and touch-up (CRED NeoPop sample). | HIG Playing haptics, Android haptics principles, CMP source at v1.12.1, Robinhood, CRED neopop code, Cashew number-pad haptics (code). S-HIGHAP, S-ANDHAP, S-CMPSRC, S-RHT, S-CR2, S-CA6 | REFINES-Feel | S | = CRAFT #3 |
| 13 | **Motion: feedback quick, arrivals relaxed, springs for interruptible moves.** At the default 1.8x our tap/tab motion is 396 ms; Apple HIG says avoid motion on frequent interactions. Stock Material controls still run on `MotionScheme.standard()` because `MaterialTheme` gets only a colour scheme [code]. CMP 1.12.2 fixes two bugs we are exposed to. | Apple HIG Motion, Android Compose animation docs (springs keep velocity), M3 Expressive tokens, CRED (latency complaints), Monarch ("no feedback of any kind"), Cashew (sluggish animations on iPhone 16). S-HIGMOT, S-CMPSRC, S-CP-CL, S-MO6, S-CA10 | REFINES-Motion (a design decision for the owner) | S-M | = CRAFT #2 |
| 14 | **Show trust; do not only claim it.** A "Verify it yourself" panel (no internet permission; where to look), a backup status line, a Reads/Ignores/Keeps/Sends-nowhere template on permission screens, a widget kill switch. | Actual ("unabashedly local-first", admits local data is unencrypted), Up ("Allow Widgets" switch), Revolut (flip to hide), Signal (confirm the key), Apple (App Privacy Report, "Data Not Collected"). S-AB-HOME, S-UP-REL, S-RV3, S-SIG, S-APPLEPR | NEW | S | INDIA #1, #2, #6 |
| 15 | **Tone is a dial with a calm default.** Plain Indian English by default; an opt-in Warm/Playful voice set; Toss rules (every button says what happens next, "Close" not "Cancel", no permission sheet at launch); never roast by default. | Monzo (operational dial: kindness maxed, magic low, wit none), Copilot (same fact in 7 voices), Fina (3 tones), Cleo (roast is opt-in), Toss, Emma ("child-like" complaint). S-MZ3, S-CP1, S-FI1, S-CL1, S-TS1, S-TS4, S-EM1 | NEW (doc + strings) | S-M | CRAFT 3.13 |

---
## 3. Per-app teardown tables

Column key: **Home & month glance** (what the first screen leads with and how the month is summarised) · **Add + capture** (how an entry gets in; taps only where a source gives them) · **Charts, budgets, coach** · **Motion, widgets, theme, tone** (motion and haptics are "unknown" unless a source or code says) · **Praise / Complaint** (short verbatim [D] with platform, stars, date; Apple dates without a year are marked "year inferred"). "Copy this" lines under each group name the reusable patterns. Source ids: Appendix A. Anything I could not verify says so.

### 3.1 Budgeters and personal-finance managers (US/EU, bank-sync first)

| App (who) | Home & month glance | Add + capture | Charts, budgets, coach | Motion, widgets, theme, tone | Praise / Complaint |
|---|---|---|---|---|---|
| **YNAB** (zero-based budgeters wanting behaviour change; iOS/Android/web) | Home tab (since 2025-09-15) is an ordered stack of action rows ("$400.00 Ready to assign - Assign", "N New transactions - Review"), Pinned categories, Current Goal, "[Month] Summary" 2x2 tiles (Total Targets, Assigned, Underfunded, Spent). Hero = Ready to Assign: a to-do, not a status. [A S-YN1, S-YN2][B S-YN8] | "+ Transaction" floating pill, about 5-7 taps (my count from help) [A S-YN5]; long-press a category on Plan = pre-filled add; widget tap on a category = pre-filled form; **Card Mode** (iOS 2026-09-22): swipe right approve, left skip, buttons too, Undo, "2 to go", list toggle remembered, revisit-skipped end state [A S-YN3][B S-YN11] | Available pill: red = cash overspent (act now), yellow = underfunded or credit overspend, green, grey; bars + status words under them ("Funded", "Fully Spent"); Aug 2026 circles replaced bars [A S-YN4, S-YN6]. Copy is anti-guilt ("Plan Reset... nothing is saved until you say so", "Time to celebrate!") [A S-YN6]. No streaks | Motion and haptics unknown (only a tilt in the Card Mode image). Widgets show 1/3/7 categories. Light/dark/system; no accent choice. Greeting "Morning, YNABer / Just one thing to do" [B S-YN8] | + "the app looks modern and sleek and is a pleasure to use." (5 stars iOS 2026-08-05). - "Way less information density. So hard to read" (1 star 2026-09-01); "They're introducing dating-app-style cards... Huge failure." (2 stars 2026-09-24) [D S-YN7] |
| **Monarch Money** (couples, net-worth people; US/CA) | Customisable dashboard of cards (Weekly Recap, Budget, Net worth, Goals, Recurring); Budget tab: sticky "Left to budget $4,640" and three bars (Income/Expenses/Goals) each with a **tick marking today in the month** [A S-MO2][B S-MO7] | Import-and-review first; swipe to review; share-sheet "Share with Monarch" for receipts (Dec 2025) [A S-MO4] | Traffic light: green under, **yellow = on pace to be over at the current pace** (Flexible bar), red over; Flex budget = one flexible number; goals "On track / Ahead / At risk"; Sankey; **sparkle on cards opens "what changed"**; Weekly Recap card + email [A S-MO1, S-MO3] | Widgets: Budget ring (amounts hideable), Transactions (filter Needs review), Receipts; no Apple Watch; dark mode not verified. Tone calm: "No drama." | + "Easy to use, clean and professional UI." (5 stars 2026-10-29). - "you tap a transaction... nothing happens, no loading symbol, no feedback of any kind" (2 stars 2026-08-15) [D S-MO6] |
| **Copilot Money** (design-minded Apple users; ADA 2024 finalist; no Android, US only) | Dashboard hero "**$477 left** out of $4,120 budgeted" with a pace line: solid = actual, dotted = ideal, end dot with pill "$268 under"; then **TO REVIEW** card (MARK AS REVIEWED), budget **rings** row (green/orange/red), Upcoming recurrings, Net this month [A S-CP2][B S-CP13] | Bank sync + ML first; manual "+" on Transactions; assistant can create entries; suggestions begin only after **30 reviewed** transactions [A S-CP7, S-CP18] | Line colour rule: green if used% < ideal, light orange if under 20 points ahead, dark orange over 20, red over budget [A S-CP3]; Cash Flow stacked bars by category, tap = value + comparison, **double-tap = drill**, dotted previous period [A S-CP5]; rebalance sheet with "Only this month / From now on" and a preview [A S-CP6] | Native Swift Charts [A S-CP11]; Liquid Glass refresh Sep 2025; haptics only as one reviewer line [C]; **no Dynamic Type** in native apps [A S-CP10]. Widgets: Daily Spending, Budgets, To Review... [A S-CP9]. Seven assistant voices (Zen, Bestie, CFO, Explainer, Narrator, Roast) [A S-CP1] | + "The visual dashboards are beautiful, intuitive, and actually make you want to check your finances." (5 stars, 2026). - "the inability to view, let alone manage rules that you create" (Jan 23) [D S-CP12] |
| **Lunch Money** (multi-currency, engineers; web-first with a phone companion) | Dark mono-spaced home: amber "2 Unreviewed Transactions" card, period picker, **Net income + savings rate** hero, expandable totals, stacked Spending Breakdown bar; web dashboard has tick-box checklist cards ("All cleared!", "Everything looks good!") [B S-LM3, S-LM4] | Teal centre + in the tab bar, form-style edit; **3 most recent categories on top** of the picker (2026-08) [A S-LM2] | Budget input opens presets ("Last period's spend", "Last period's budgeted", "Avg spend last 3 periods"); per-transaction **change history** "BY RULE / BY SYSTEM"; suggested rules [B S-LM4, A S-LM1] | Widgets: Net Worth, Period Summary, Recent Transactions; amber on near-black, monospace numerals | + "It's not flashy but it's solid and well-supported." (5 stars 2026-06-22). - "Rules doesn't work consistently" (3 stars 2026-04-28) [D S-LM5]. Only 13 text reviews: thin |
| **Actual Budget** (local-first, ex-YNAB, self-hosters; desktop/web, iOS TestFlight) | Opens on Budget; month header "Available funds / Overspent / Budgeted / For next month" and a huge green **To Budget** (zero = done) [B S-AB6][A S-AB1] | Keyboard-first: T opens a transaction, Enter adds and keeps the form open, J/K, single-key bulk actions, Cmd-K palette; **math in amount fields** ("16.99*1.1") [A S-AB1] | Envelope; month menu copy last / 3-6-12 month average; theme catalogue with 6-swatch previews and 9 chart variables [A S-AB3] | "Scramble" privacy keeps layout (digits scrambled, names visible); no widgets; mobile is the weak part | + "The interface is snappy in a way that web apps rarely are." (blog 2026-02-15) [C S-AB7]. - "it never tells you that something needs attention" (GitHub 2026-11-01) [D S-AB5] |
| **Quicken Simplifi** (people who find zero-based exhausting) | 12 default tiles; **Customize = drag handles, toggles, Reset**; Spending Plan ends in "Left this month" pie and an "average daily spending allowance" [A S-SF1, S-SF4] | not verified | Watchlists tile (this month / average / YTD / projected; orange average line, slatted target); **Achievements are weekly/monthly streak-style badges and cannot be disabled** [A S-SF2, S-SF3] | not verified | - "a pointy, super low fidelity graph that you can't even drag on" and "last updated should be prominent on the Home Screen" (2025-09-09) [D S-SF5] |
| **PocketGuard** (one-number budgeters) | "In My Pocket" = income - bills - goals - allotments, example "$65 per day" [C S-PG1] | not verified | Insights rewrite Sep/Nov 2026 (company post, seen only via a summary): trends, comparisons, "Donut charts are back, too!" [C S-PG2 (snippet)] | Site returned 403; screenshots not viewed | - "There's not an option for a light theme" (2025-01-08) [D S-PG3] |
| **Rocket Money** (subscription cleanup) | Recurring tab: 7-day calendar strip of upcoming charges + list [A S-RM1] | n/a | Row = amount + yearly cost + "Cancel This For Me" / "View Instructions" [A S-RM1] | not verified | - "Seeing the bill I now owe you was SO MUCH MORE STRESSFUL than the $2 relief" (2023) [D S-RM2]. Older |
| **Mint / Credit Karma** (legacy) | Trends line graphs and goals remembered fondly; Credit Karma dropped budgets and categories [C S-MT1] | n/a | Ads dressed as offers drove users away [C S-MT1] | all secondary, no screenshots | Lesson only: users remember **draggable graphs** and want a visible "last updated" |

**Copy this (3.1):** pace tick and "left of" sentence (Monarch, Cashew, Copilot); a review ritual with a dot and a closure state (Copilot, Lunch Money); status words beside colour (YNAB); presets menus for budget input (Lunch Money, Actual); sparkle-for-why (Monarch); drag-to-hide dashboard tiles (Simplifi). **Avoid:** a home that becomes a to-do list (YNAB), repeating "Fully spent" on every row (YNAB complaint), silent taps with no pressed state (Monarch).

### 3.2 Manual and mobile-first trackers

| App (who) | Home & month glance | Add expense | Charts, budgets, coach | Motion, widgets, theme, tone | Praise / Complaint |
|---|---|---|---|---|---|
| **Spendee v6** (design-led, Aug 2026 redesign) | Marketing render: category **budget rings** row, "Cash Flow" number, 6-month green/red bars with a **hatched scheduled bar**, pinned "Scheduled - 2 transactions" card, day headers with day total [B S-SP3] | Green + FAB; label chips with emoji; 6.0.12 (2026-10-27): **swipe sideways to flip expense/income categories** [A S-SP4] | Budget = filled "**liquid orb**", a `Today` pill on the rim, chip "On track", "$235/day - 22 days left" [B S-SP2] | Motion unknown; the half-second toast was a complaint. Dark mode yes | + "The new UI is so clean! The top budget tracker is insanely useful" (Instagram, ~Aug 2026). - "only two items from a long list fit in the viewport" (24 Aug 2026); "Dates are hard to see between huge transaction bubbles"; "forcibly scrolls me away"; "I have to wait between every tap" [D S-SP5] |
| **Wallet (BudgetBakers)** | User-arranged dashboard cards (long-press drag, per-card filter) [A S-WA2]; default window "Last 30 days" with "vs past period +x%" | **"Most frequent" row** above the category list; swipe right mark, left split/clone [B S-WA3, A S-WA4] | Budget rows in **three states with words**: green, orange "Risk of overspend 855 / 1,000", red "Overspend"; predicted = spent + planned payments + past-period average [A S-WA1] | Push copy dramatic ("you're spending on 'Kids' like crazy!") [B]; dark mode unverified; no 2025-26 UI quotes found | Evidence old (store art 2017-2019); gap |
| **Toshl** (playful; strong budgets; web + mobile) | Monthly overview: big green "**$612.12 left to spend**", day bars, `Today` line, "left per day"; **today tick on every budget row** [B S-TO3, older art] | Claims "only 4 taps"; **drag the + in an arc** to choose expense/income/transfer (2016 post) [A S-TO1] | River-flow (Sankey) month view; monster tips capped at 2/day free [A] | Liquid Glass iOS beta 29 Oct 2026 ("monsters have warmed up to liquidity") [A S-TO2] | + "the fastest app I'd found for inputting expenses and viewing budgets" (2017); - "everything much bigger... I can see three, max" (v2.0.13, 2017) [D, old] |
| **Money Manager (Realbyte)** (manual, Asia, ads in free) | Top strip Daily / Calendar / Weekly / Monthly / Total; month income/expense/total trio [B S-MM1] | Income/Expense/Transfer segmented; **bookmarks** for frequent entries; three new Quick-Add widgets (4.12, theme + transparency) [A S-MM2] | **Calendar cells show income and expense numbers plus category dots** [B S-MM1] | Plain look, ads strip; dark yes | + "everything is customizable, down to the subcategories" (Aug, year inferred); - "sync", plain look [D S-MM3] |
| **Monefy** (fastest-entry benchmark) | One screen: donut with income/expense in the hole and **category icons ringing it**; balance bar turns red when negative; two giant outlined **-/+** buttons [B S-MF1] | Tap -, type amount on a calculator keypad, **category grid visible on the same screen**, full-width button reads "ADD 'CAR'"; coach mark "Tap to add a new expense record" [B S-MF1]; vendor claim "under 10 seconds" [A S-MF2] | Single donut; no trends screen verified | Soft paywall after a 3-step carousel; dark mode paywalled | + "It keeps me accountable too... I see when it goes from green to red" (2021); - "constant and I mean constant ads" (2023) [D, old] |

**Copy this (3.2):** orb or bar with a Today pill and a status chip (Spendee, Toshl); three-state budget rows with words (Wallet); "most frequent" row and echo-label CTA (Wallet, Monefy); swipe to flip type (Spendee); hatched future bars + pinned Scheduled card (Spendee); calendar cell = numbers + dots (Money Manager). **Spendee v6 is the cautionary tale (see anti-patterns).**

### 3.3 Open-source, indie and power apps

| App (who) | Home & month glance | Add expense | Charts, budgets, coach | Motion, widgets, theme, tone | Praise / Complaint |
|---|---|---|---|---|---|
| **Cashew** (Flutter, GPL-3, solo developer; manual, no account; Play 4.8) | Hero is a **budget card**: "$124.54 left of $500", pill bar with a black **Today** tag, period ends labelled, grey sentence "**You can keep spending $24.91 for 3 more days**"; dotted limit and today lines on a cumulative chart. Home is user-composable (Edit Home: drag sections, per-section range) [B S-CA7][A S-CA1] | (+) opens a sheet asking **Title, then Category, then Amount**; after 2 characters one **suggestion row** (category tile + bold-matched title) fills both; number pad with inline calculator; **long-press (+)** = pinned entries (one tap duplicates to now); Android widgets; notification/SMS scanning; prompts reorderable [A S-CA1, S-CA6] | Budget screen: big pastel pie with **icon badges on the arc**, rows with a ring around the category icon; donut: tap to select (slice grows), tap background to clear, **long-press-drag to scrub**, pinwheel reveal 850 ms, badges pop 70 ms apart [A S-CA3, B S-CA8]. Budget bar fill 1500 ms; percent label fades when under 40%; **shake after a 600 ms delay when over 100%** [A S-CA5] | Numbers count from the **previous** value, skip the first render, drop decimals when whole; setting "Number Animation", "App Animations", "Battery Saver" [A S-CA4, S-CA9]. Haptics: digit = selection click, operator/backspace = medium, long-press = heavy, gated by a setting [A S-CA6]. Material You + accent, outlined-icons toggle, bundled fonts (Avenir default, Inter, DM Sans...). **20 rotating reminder lines**; Preview Demo button in onboarding [A S-CA9]. No streaks found | + "I cant find any other budgeting app that has beautiful UI/UX similar to these." (iOS MY 2024-04-28); "It looks beautiful, and the animations are intuitive" [C S-CA11]. - "The app will feel sluggish and animations would lag (I am using the iPhone 16)" (iOS MY 2025-01-15) [D S-CA10] |
| **Bluecoins** (dense "check register", Android/iOS) | Dark navy tab row (Main / Transactions / Reminders / Balance Sheet / Budget); Daily Summary 7 bars with amounts + "7 days average / 30 days average"; Budget tab bars each with a **TODAY tick, including child categories** [B S-BC2] | not verified from UI | v13 (Jun 2026) "from-the-ground-up rebuild"; export now shows a paywall sheet at the moment of intent rather than a greyed button [A S-BC1] | AMOLED dark; motion unknown | + "a fantastic pie chart... along with month to month comparisons" (iOS Feb 6, year inferred). - "Just updated to v13... changing from 5 to 4 stars" (Play 2026-05-29) [D S-BC3] |
| **Ivy Wallet** (Kotlin + Compose; archived Nov 2024) | Hero typography: "USD" thin, "**9,326**" heavy, ".55" small and raised; two gradient Income/Expenses cards; full-width status bands "6 Upcoming - 750 USD" (orange) and "1 Overdue" (red) [B S-IV2] | Swipe up on (+) for expense (developer reply 2021); a reviewer reports hidden gestures are hard to discover [A S-IV1][D] | Dark donut with centre icon; category cards with percent top-right | Dark theme "an integral part"; motion unknown | + "the design of this app is just too beautiful. it prompted me to get the upgrade." (Play 2021, older than 2024) [D S-IV1] |
| **Fold Money** (India; auto-tracking via AA; UI only) | Tabs Home / Transactions / You / Tips; Total Balance widget with mini trend and account pills (from site alt text) [A S-FO1] | Manual cash: sheet **morphs into a calculator keypad** (60fps.design title; frames not seen) [C S-FO4] | Cash Flow graph: drag **snaps to each month with a spring, selected point scales up, header/badges/list crossfade** (description, not video) [C S-FO2] | "Slide to connect" handle with spring-back [C S-FO3]. Copy: "Stop recording expenses manually."; search demo "Just type in McDonald's... Look yourself in the mirror" [A S-FO1] | + "What stands out the most is the UI. It's clean, thoughtfully designed" (App Store IN, May, year inferred). - wants real merchant logos (Mar) [D S-FO5] |
| **Dime** (iOS indie, free, no ads) | Quick actions, home + lock-screen widgets, biometric lock [A S-DI1] | **Swipe to switch expense/income**; transaction suggestions; undo delete | **Month start aligned to payday**; "Save a day's transactions as an image for sharing"; Dynamic Type [A S-DI1] | motion unknown | + "absolutely very pleasing to look at" (Jan 8, year inferred) [D S-DI1] |
| **Waterfly III, Financisto, MyExpenses** | Waterfly: Bluecoins-style cards; Financisto/MyExpenses: README only, UI not verified [A S-WF1] | | | | not assessed |

**Copy this (3.3):** Today tag + pace sentence (Cashew); donut scrub and pinwheel reveal in plain Canvas (Cashew code); title-to-category suggestion row (Cashew); count from the previous value (Cashew); quiet currency, loud integer (Ivy); month-snapping scrub where every number follows (Fold); payday month and share-a-day-as-image (Dime). **Caution:** Cashew's add page is one 5,000-line file with 44 `setState` calls [C S-CA12], a plausible cause of its iPhone lag.

### 3.4 Shared, envelope, couples, business and small apps

| App (who) | Home & month glance | Add expense | Charts, budgets, coach | Motion, widgets, theme, tone | Praise / Complaint |
|---|---|---|---|---|---|
| **Splitwise** (shared expenses; 10M+) | One coloured sentence at the top: "**Overall, you owe 775.25 and you are owed $132.06**"; groups are rounded rectangles, friends are circles ("new visual language") [B S-SW3][A S-SW1] | Sentence row "**Paid by [you] and split [equally].**" with tappable word pills; custom numeric pad; free tier daily cap then a **full-screen Pro interstitial with a "continuing in 9 seconds" countdown** [B S-SW4] | Charts are Pro; settle-up amount pre-filled, "Record a cash payment" neutral wording | Dark mode only 18 May 2026 (in-app toggle) [A S-SW2]; motion unknown | + "Alot of thought and polish has gone into it." (iOS 5 stars 2025). - "There is absolutely no reason why a free account should be limited on the number of expenses it can enter per day" (iOS, Aug 4) [D S-SW5] |
| **Goodbudget** (envelopes, couples) | Envelope rows: **remaining as the big number**, budget small beneath, bar with a **black tick**; red beyond the tick; envelope face mascot; sentence "Great! You're ahead by 90.32." [B S-GB3] | Payee/amount/envelope form with remaining shown in the dropdown ("Cell Phone [100.00 left]"); location widget "3 touches" [B S-GB4, A S-GB1] | 2026 iPhone **running balances**: greyed "[Scheduled] Utility Bill" with a projected remainder so you see an envelope go red before the bill posts [A S-GB2] | Face ID added 2026-10; no motion data | - looks "like software from a decade ago next to Copilot or Monarch" (review site, 2026, snippet) [C S-GB5] |
| **Buddy** (couples; iOS/Watch) | Overview / Budget / Tools; 13.52 (Nov 2026) rolling out redesigned Overview + assistant [A S-BU1] | **First-budget wizard** with live "$500 left of income" bar, Skip always visible; "**Continue Anonymous**" button beside Continue [B S-BU3] | Plan / Remaining / Insights; ring per category; daily budget pushes, **including a positive "you have extra today"** [C S-BU4] | Small widget "what's left of income" (13.51) | - "It no longer lets you review transactions before it adds them" (iOS Jul 17, year inferred); "The wallet system definitely needs to go" [D S-BU5] |
| **Emma** (UK/US open banking) | Budgeting: payday-to-payday range pill, donut "**$1,000 left of $2,182.10**", legend splits "Spending" and "**Committed spending**", card "**Daily allowance $192.78 - Until 29 Nov**", Category / Merchant toggle, "Rolling active" [B S-EM2] | Bank sync first; manual add buried | Subscription card lifts with a big **Cancel**, price-hike alerts; net-worth line with 1W/1M/3M/6M/1Y chips [B S-EM3] | "Scramble mode" demo; playful tone ("Look, no hands!") | - "Child-like feel to the overall theme" (review site 2026-10-14) [C S-EM4]; a promo FAB sat where Add should be (2020) [D, old] |
| **Mooncard** (French business card + expenses) | Hero balance + pill "Alert threshold: 300"; orange row "**Expenses to be processed 23**" [B S-MC2] | **Camera button in the header of every main screen**; receipt-first: pre-filled form under strip "This expense must be completed" [B S-MC2] | Status chips (New, Expense report); All / Mine segment | Release 1.9.1: auto-retry of receipt uploads after a network cut [A S-MC1] | - photo capture "unreliable... pas de message d'erreur" (iOS FR, May, year n/a); asks for **red flags on incomplete items** (2023) [D S-MC1] |
| **Fina (Rata y Fina)** (on-device, no sign-up; 8 ratings) | Rotating greeting; hero "AVAILABLE BALANCE" in **serif numerals**, "You've got $40.66 a day until 31 Oct", "$102.99 is already spoken for"; **a streak chip "17 days"** (conflicts with our decision) [B S-FI3] | **NL pill "uber 15"** (or dictation) opens a confirm sheet with category and account chips and a one-line quip; Save [B S-FI4] | Stats card with "Only what you can change" toggle and a regenerate "Another" pill; setting to **choose the hero number** [B S-FI3, A S-FI2] | Three tones (Good Vibes / Real Talk / No Filter) for all copy; hard paywall after one expense and one income [A S-FI1] | thin evidence |

**Copy this (3.4):** coloured one-sentence balance (Splitwise); remaining-as-big-number plus pace tick plus "ahead by" (Goodbudget); projected remainder for scheduled bills (Goodbudget); committed-vs-free split and daily allowance as its own line (Emma); NL entry pill and tone setting (Fina); "Needs attention" count banner and capture-first pre-filled form (Mooncard); wizard with a live bar and Skip (Buddy).

### 3.5 Neobanks (spending-insight surfaces inside bank apps; no bank feed for us)

| App (who) | Home & month glance | Add + row design | Charts, budgets, coach | Motion, widgets, theme, tone | Praise / Complaint |
|---|---|---|---|---|---|
| **Monzo** (UK, 16M customers) | Server-driven "overview" with **spotlight cards** the user can reorder/hide ("Edit layout"); Trends tab Spending / Balance / Target; "**Left to spend**" = balance minus upcoming scheduled payments [A S-MZ1, S-MZ6]. The Summary dial was retired Dec 2025 and users protested [D S-MZ8] | Bank feed; row = merchant logo + emoji, pending grey, settled black (snippet); instant push per spend [D] | "Tap and hold any bar" shows exact spend + **top 3 categories**; weekly bars with dashed rolling average (2021 post) [A S-MZ7]. **Year in Monzo** drew a Guardian story and an Ombudsman complaint: "But mainly, you fast fooded." [C S-MZ4] | Confetti on "Get paid early" annoyed repeat users; setting to turn animations off exists (forum) [D]. Tone dial for operations: kindness maxed, magic low, wit none [A S-MZ3] | + "The app is clean, super fast, and makes managing money actually enjoyable" (App Store GB 5 stars 2026-10-22). - "Please stop making so many design changes... cluttered and confusing" (1 star 2026-11-01) [D S-MZ9] |
| **Revolut** | Balance in the middle, four round action buttons, "Upcoming" card; analytics: "Spent" and "Income" widgets with value + comparison [A S-RV1][B S-RV5][C S-RV2] | Bank feed; chat-bubble payments | **One dataset, many lenses**: bar / pie / line x group by category, merchant, country, currency, card x 1 week to custom; threshold pushes 25/50/75/100% (paid tiers) [A S-RV1][C] | Dark-first store art; "Hide balances" blurs on flip, on by default [A S-RV3] | + "The concept, design & execution of the app for managing my money works really well for me" (GB 2026-11-11) [D S-RV4] |
| **N26** (DE) | Spending vs "**your usual three-month average**"; Monthly Wrap-Up; budget widget shows days left [A S-N261, 2022] | Spaces with Income Sorter, round-ups | 2026 redesign drew "looks like a widget" and in-feed ads complaints [D S-N262] | "Wave to hide" discreet mode (buggy in 2026) [D] | + "Makes banking fun" (5 stars 2026-10-31). - "Annoying ads everywhere" (1 star 2026-09-21) [D S-N262] |
| **Starling** (UK) | Spending Insights: month total, **swipe the bar chart through months, tap a bar for its total**, "align the first day of month to your payday", weekly buckets 1-7, 8-14... with a floating tooltip [A S-ST1][B S-ST2] | Tinted pictogram tile + "17:34 - Eating Out" subtitle; per-transaction **behavioural tags** (Impulse Buy, Relationships, Project); category change "one-off or all past and future" [A S-ST1] | Spaces as photo goals; push "You spent 25.00 ($33.50) at Surf Board Hire" shows home currency [B] | Account switcher as a **wallet stack of coloured cards** (as ours) [B] | + "I love the interface" (5 stars 2026-11-05). - "the silly icons add no value... One line per transaction" (4 stars 2026-11-09) [D S-ST3] |
| **Wise** | Transfer-first; Insights in 15 auto-categories (2020 post, old) [A S-WI1] | "Snap it. Send it." scan an invoice/QR into a transfer [A S-WI2] | Promised to remember re-categorisations | - "eye candy... turned into a social app" (2 stars 2026-11-07) [D S-WI3] | |
| **Up** (AU; best motion and tone evidence) | Dark header "$420 Spendable", Upcoming / bills emoji strip / Insights; **home needs no login (read-only)** [B S-UP4][A S-UP1] | **Pull-to-save**: pull the feed and flick the leftover cents into a Saver (over $1M in 18 months); Round-Ups make the feed whole numbers [A S-UP1] | Savers as emoji rows; **particle "pool" goals** that move with the accelerometer; Oct 2026 graph made of particles you can scrub [A S-UP1, S-UP2]; Monday notification opens last week's Insights; locked saver with a 3-hour cooldown [A S-UP-REL] | Atomic onboarding, bottom-anchored buttons, "Up Yeah!" moment; release notes with jokes and Easter eggs; lock-screen widgets + "Allow Widgets" switch [A S-UP-REL] | + "implemented with care without bloating" (5 stars 2026-09-22). - "very cool interface, but also very important to be usable" + no search (4 stars 2026-10-04) [D S-UP5] |
| **bunq** (NL) | Insights at top; 23 categories; limit alerts at **80 / 95 / 100%**; weekly "earned this week" + next-payday countdown [A S-BQ1 (snippet)][B] | | Finn assistant | Spin-the-wheel cashback hated | - "Changing the ux interface every fortnight" (2 stars 2026-10-23) [D S-BQ2] |
| **Cleo / Chime** (light) | Cleo: warm greeting hero; roast/hype modes are opt-in [C (snippet)] | | | Chime: release notes written as jokes; **VoiceOver complaint: buttons merged so "I can't tap either one"** (4 stars 2026-11-02) [D S-CH1] | |

**Copy this (3.5):** "left to spend after bills" as a sentence that names what is included (Monzo); hold-a-bar overlay (Monzo); chart-type x group-by x period lenses (Revolut); weekly-bucket bars with a darker selected bar and tooltip (Starling); behavioural tags and one-off-vs-always re-categorise (Starling); physical gesture that commits a small action (Up); threshold alerts (Revolut, bunq, Buddy); widget kill switch (Up); recap with guardrails (Monzo's lesson).

### 3.6 Platform spend views and Indian UPI apps

| App (who) | Home & month glance | Add + row design | Charts, budgets, coach | Motion, widgets, theme, tone | Praise / Complaint |
|---|---|---|---|---|---|
| **Apple Card / Wallet Insights** (iOS 27; Insights UK-first) | Card art, two tiles (Card Balance, Payment Due), "Monthly Activity" tile with 5 mini bars; tap opens **Week / Month / Year** pill, "Total Spending", delta sentence with an arrow: "Your family spent $998.13 more than the previous month." [B S-AC2][A S-AC1] | Search accepts amount operators ("equal / over / under $8.00") and typed date ranges; multi-select re-categorise [A S-AC1] | **Each day bar is a vertical gradient of that day's category colours** (2019 art) so colour is the legend; Insights "so far, you've spent less than last month at this time"; **Recurring: month calendar with a rounded total under each date + Upcoming list + opt-in pre-charge notification + "Mark as Not Recurring" + an estimate disclaimer** [B S-AC3, C S-AC4, S-AC5] | Haptics, dark mode and widgets not verified. Privacy line: "Your purchase history. For your eyes. Not ours." [A S-AC6] | + "may look familiar if you use Apple's Health app" (9to5Mac 2026-06-22) [C]. - Insights "reports; it doesn't plan" (Lode Finance 2026-10-27) [C S-AC7] |
| **Google Pay India** | 2026: opt-in Gemini **Ask Google Pay** in the Money tab: prompt chips ("Analyse my spends from last week."), 10 Indian languages, thumbs on every answer, history kept 42 days; "can make errors" [A S-GP1] | | Home/Money-tab visuals not verified | | A Sept 2026 reviewer could not see spend by category (in `COMPETITOR_ANALYSIS.md`) |
| **Paytm Spend Summary** | Month total shown atop Payment History; tap Total Spent for a breakdown by **tag** (chart type not verified) [A S-PT1] | Tag under each row; **tap a tag to list its payments**; re-tag one merchant and "all future payments to the same person or merchant" follow [A S-PT1] | Tone: "Understanding your spending does not have to feel like a budgeting exercise." [A S-PT1] | Hide single payments; Receive Money widget | |
| **PhonePe** | No shipped spend summary found; third-party guides say no monthly totals [C S-PP1] | | | | absence = our opening |
| **CRED** (NeoPop; dark-only; members) | Home not verified in 2026 [C S-CR3, 2022] | n/a | | **Press-depth "plunk" buttons**: Flutter library animation 50 ms, tilt 100 ms, haptic on touch-down **and** touch-up in its README example; shimmer 2 s sweep, 5 s rest; serif accent type [A S-CR2, S-CR1] | + "ONE STAR IS JUST FOR UI" (2024-11-04). - "heavy ui that is busy... Focus on basic payment... in seconds" (2024-04-02); "slight lag after clicks... very evident with the haptic vibration" (2022) [D S-CR4][C S-CR3] |
| **Samsung Wallet** | No insights UI found | | | | |

**Copy this (3.6):** category-gradient day bars; delta sentence with arrow and "so far... at this time"; Recurring calendar with rounded totals and a pre-charge alert; tags that learn; prompt chips as the entry to analysis (as a local search, not an LLM); press-depth button for one hero CTA only. **CRED's lesson:** praised craft and criticised latency are separate: polish earned goodwill, lag and reward theatre burned it [I from S-CR3, S-CR4].

---

## 4. Per-theme best practice (with "how we do it in Compose Multiplatform, offline")

Each theme: best-in-class examples, why they work, then a CMP note with an honest feasibility call. "Verified" means read in official docs or in the CMP source at the `v1.12.1` tag by a researcher; "unverified" items have a 5-minute check in Appendix B.

### 4.1 First 30 seconds, onboarding, time to value

**Best in class and why**
- **Cashew** needs nothing before value: a default account exists, onboarding is three pages, page 2 sets a budget so the hero card is filled before any entry, and a **"Preview Demo" button** fills the app with sample data [A S-CA9, S-CA1]. Time-to-value is felt on screen two [I].
- **Up** asks one question per screen, with bottom-anchored full-width buttons and conversational prompts ("What is your mobile number?"), and ends on a brand moment ("Up Yeah! account created") because fast sign-up "feels clinical and unimpressive" [A S-UP1, 2020 post, older than 2024]. **Buddy** adds a wizard with a live "left of income" bar, Skip always visible, and a "Continue Anonymous" button beside Continue [B S-BU3].
- **Monefy**: a 3-step carousel, then home with a coach mark on the add button ("Tap to add a new expense record") [B S-MF1]. **Monarch**: "You don't have to do everything at once" and "Monarch still works with partial data" [A S-MO8]. **YNAB** documents 20-30 minutes of setup [A S-YN-GUIDE].
- **Length is set by what you must give the app.** Mobbin's study of 1,460 flows: finance averages 40.1 screens (Monzo 114); a no-account, no-linking ledger has no such excuse [C S-MOB1].
- **Tutorials do not help.** NN/g, 70 users on 4 apps: success 91% (read tutorial) vs 94% (skipped), perceived ease 4.92 vs 5.49 (p=0.047); "we also observed this effect with coach marks and instructional overlays" [A S-NN1]. Apple HIG: onboarding "fast, fun, and optional", prefer context-specific tips; ask permission when the feature is first used [A S-HIGON]. Counter-weight: Google's India playbook says visual tooltips and voice hints help first-time users and that users fear making a mistake [A S-GIN].

**Our first run [code/B].** A 0.00 month card and "Set a budget" sit above a plain "Welcome", two buttons and one dense privacy-and-backup paragraph; the tour is 19 stops and appears automatically per version; HOME_OPTIONS Q12 left this unchanged on purpose.

**Deltas (all tagged):**
- NEW (S-M): **ghost "Example" month** on the empty Home: 8-10 dimmed rows (Chai 20, Auto 80, Groceries 640...), a faded 14-day line, an "Example - yours replaces this" ribbon, never written to the database (so no totals, widgets or backups are polluted). Tapping a ghost row opens Add pre-filled. = CRAFT 3.7 #1.
- REFINES-Welcome (S): shorter heading that says what happens ("Let's note your first expense"), body "Type an amount. That's it. Sort it later.", and **two privacy lines** ("Stays on this phone. No account. Nothing is sent anywhere." then, smaller, "Uninstalling deletes it, so back up now and then: More > Your data."). On Android add the checkable line "This app has no internet permission" (true: the manifest removes `INTERNET` [code]); the iPhone has no equivalent, so do not say it there.
- REFINES-tour (M): replace the automatic 19-stop tour with at most 8 **one-time contextual tips** attached to a first real use (first long-press chip, first swipe, first widget), local "seen" flags, no analytics; keep the tour opt-in ("Show me around", 5 stops) because the India playbook cuts the other way.
- NEW (S): after the first saved expense, offer the **Quick Settings tile** once (`StatusBarManager.requestAddTileService`, Android 13+ [A S-ANDQS]).
- REFINES-RollingAmount/Feel.SAVE (S): the first saved entry gets one bigger "it landed" moment (total counts up, SAVE haptic) and nothing else.
- Slow-boil notifications (S): nothing the person did not switch on for the first 3 days; SmartNews found early pushes cost lifetime engagement [C S-SMN, one engineering blog].

**CMP note.** All plain Compose: the ghost month reuses the existing row composables at 40% alpha with an "Example" semantic label; loading skeletons are `rememberInfiniteTransition` + `Brush.linearGradient` and must follow Motion Off; tips are state flags next to the existing guide flags. No library, no permission.

### 4.2 The "at a glance" hero

| App | Leads with | How defined | Source |
|---|---|---|---|
| Copilot | "$477 left out of $4,120" + pace line | Free to Spend: can still be spent without going over budget; expected recurrings excluded from the line | S-CP2 |
| Monzo | "Left to spend" | balance minus upcoming scheduled payments (copy adapts to what is included) | S-MZ6 |
| Monarch | "Left to budget" + bars with a today tick | cash-flow based | S-MO7 |
| YNAB | Ready to Assign (a to-do) | zero = done | S-YN1 |
| PocketGuard | "In My Pocket" | income - bills - goals - allotments | S-PG1 |
| Cashew | budget card "left of" + Today tag + sentence | period configurable | S-CA7 |
| Spendee / Emma / Toshl | orb or donut with "$235/day" or "Daily allowance until 29 Nov"; Toshl "left to spend" | pace, committed split | S-SP2, S-EM2, S-TO3 |
| Fina / Daily Drip | choose your hero / today's number | surplus rolls over | S-FI2 |
| Apple Card | balance + a spending summary one tap away | not a budget | S-AC1 |
| Actual | "To Budget" (zero = done) | envelope | S-AB6 |

**Evidence about control and risk.**
- Optimistic budgets cut spending 21.9% in a 70,000-user, 350-million-transaction study that persisted six months [C S-DAR, abstract only].
- **Counter-finding:** Think Forward Initiative + University of Arizona report that showing an exact amount left raised spending late in the period (26% to 33% in their field studies); ranges, mid-period budget updates and rollover reminders reduced it [C S-TF1; press coverage, paper not read; one app type].
- **Users love "left to spend" and hate setting it up.** Monzo retired its Summary dial in Dec 2025: "Summary's left to spend was the number 1 thing that helped me get my finances on track" (2025-11-06); the replacement made users "keep telling it what was normal spending vs what needed excluding" (2025-11-07) [D S-MZ8]. Monzo staff had earlier called the number "confusing or annoying" for some and shipped a Labs "hide" switch [A S-MZ-LAB].
- No controlled study was found on "red for spending causes anxiety" or on light vs bold money figures (gap). Do not cite one.

**Deltas.** Keep ONE primary figure (left this month) and demote the second "a day" source behind a tap (my judgement [I]; Slice 112 Q2 kept both) (S); rollover/"not a target" copy under it, which pairs with the whole-rupee "about" rounding we already do (S); a **pace tick** on `AnimatedBudgetBar` plus semantics text "on pace / ahead / behind" (S); tap the hero for "how this is worked out" with the arithmetic (S); a **committed vs free** line ("2,100 of what's left is already spoken for by bills due") using data Safe-to-spend already has (S-M); optional "choose your big number" (left this month / left today) as a Customise-home choice (S-M); negative copy "1,200 over this month" rather than "-1,200 left" or "You overspent" [I]. Hide amounts must also hide the new tick.

**CMP note.** Layout and arithmetic only; a tick is one extra float in the existing Canvas draw. Verified feasible, no platform code.

### 4.3 Fastest possible entry

**Benchmarks (vendor or own claims; none independently timed).** Monefy "under 10 seconds" [A S-MF2]; Cashew: long-press + then a pinned chip = 2 taps, natural-language "Intelligence" add is experimental with a preview card [A S-CA1][C]; Toshl "only 4 taps", drag the + to pick the type [A S-TO1]; Fina: type "Lunch 12" or dictate, review, save [A S-FI2]; Mera Kharcha: Hindi/Hinglish voice but Premium, internet and a server [A S-MK1]; NoBuy: an end-of-day Yes/No widget [A S-NB1, snippet]. We start at: tap Add, digits, Save; widgets and chips are one tap [code].

**Verified OS facts (official docs, read):** iOS Control Center / Lock Screen / Action-button controls run an App Intent or open the app (iOS 18) [A S-WKC]; Lock Screen widget families exist since iOS 16 [A S-WKC]; iOS `UNTextInputNotificationAction` and Android `RemoteInput` direct reply allow typing into a notification [A S-NOTIF]; Android guidance: use app shortcuts to launch and tiles for fast actions, at most two tiles per app [A S-ANDQS]; Android `createOnDeviceSpeechRecognizer` (API 31) vs plain recogniser that "is likely to stream audio to remote servers" [A S-ANDSPEECH]; iOS `requiresOnDeviceRecognition` only when `supportsOnDeviceRecognition` [A S-IOSSPEECH]. **Compose Multiplatform has no watchOS target**, so a watch app is a separate native project [A S-CMPCOMPARE].

**Ideas (full list in section 7):** a smart "chai 20" field with a preview chip (NEW, M); `+` chaining and `120+45` in the number pad (NEW, S; verified absent [code]); recents/most-frequent first and a CTA that echoes the choice (REFINES, S); title-to-category suggestion row while typing (NEW/REFINES, S-M; Slice 36/68 do it on capture and after save, so check the add form first); swipe sideways in the add form to flip expense/income (NEW, S); long-press Add for pinned entries from any screen (REFINES, S); an evening "Nothing to add today" action (NEW, S-M); Android notification direct-reply quick add (NEW, M); iOS Lock Screen accessory widget and a Control that opens the number pad (NEW, S-M each, Swift); on-device voice into the same parser (NEW, M-L). The Swift items cannot be Compose; the parser lives in `core:domain` and is host-testable.

**Unverified:** Hindi/Hinglish on-device recognition quality; `import platform.Speech.*` from Kotlin/Native; a Control button running an intent that needs a typed parameter; a finance "add expense" Assistant built-in intent (Appendix B).

### 4.4 Insight surfaces that feel like a private coach

**Best examples.** Monarch: Weekly Recap as a Home card and email, sparkle on cards that opens "what changed", the assistant "always asks before changing anything" [A S-MO3, S-MO4]. Copilot: Daily Briefing items are each dismissable and reversible, "Proactive Suggestions" individually toggleable, "we do not train on your financial data" [A S-CP8]. Apple Wallet Insights (iOS 27): Trends, Averages, Large transactions, Frequent merchants, each with "See All"; Recurring with a calendar of rounded totals and an opt-in pre-charge notification [A S-AC4, S-AC5]. N26: spending vs "your usual three-month average" [A S-N261, 2022]. Up: the Monday notification opens last week's Insights instead of a separate summary screen [A S-UP3]. Rocket Money: price-rise alert only when above 1.99 **and** above 5% (two-part threshold keeps it rare) [A S-RM3, vendor page].
**Guardrails learned the hard way.** Year in Monzo: Guardian story and Ombudsman complaint over generated copy ("But mainly, you fast fooded.") [C S-MZ4]; Monzo's reply: optional, opt-out. Monzo's own tone page: humour only in expected light moments, "handle deeper emotions with care" [A S-MZ3]. A small NextLeap study (n=50 survey, 6 interviews) says monthly totals "trigger regret and guilt" [S-INDIA]. Cleo's roast mode is opt-in [C, snippet].
**Fatigue.** Controlled evidence is thin; the "6+ pushes a week = 3.4x uninstall" figures are vendor blogs (do not quote). SmartNews (one engineering blog): more pushes lift short-term engagement but raise churn visible only after 6-12 months, worst for new users [C S-SMN].
**On-device generation, honestly.** Templates and rules first: `WeeklyRecap.compose` and `SpendingInsight.best` already exist, are deterministic and work on every phone. Apple Foundation Models needs an Apple Intelligence device [A S-FM]; Gemini Nano via ML Kit GenAI is limited to listed devices, has a per-app quota and is **foreground only**, so it cannot write the Monday recap in the background [A S-GEMINI]. An LLM would at best be an optional "say it nicer" polish of an already computed fact (L, low value).

**Rule set proposed [I].** At most one insight push a week (the recap) plus bills as today; nothing in the first 3 days; an insight only when a threshold is met; the same fact never twice; every insight dismissable with Undo; a "Pause nudges until..." switch (Travel mode could set it).

**Deltas.** **Fix first (S):** apply the lock-screen-private pattern (`VISIBILITY_PRIVATE` plus a public version) to the recap and budget notices, and drop amounts entirely while Hide amounts is on; today only bill notices do [code `Notifier.kt:84-91`]. A **Monday recap card** on Home (3 lines: spent vs last week, top category, one thing to look at; the notification becomes an optional pointer) (M). A **tap-for-why** on the existing "usual for you" sentence that lists the 3 entries behind it (S-M). More rules in the same quiet style: month-end projection, new recurring detected, subscription price change (already detected at 5%), quiet week "Nothing unusual" (S each). An opt-in **Your year** in December with personal facts only (busiest weekday, longest no-spend stretch) and a share image with amounts hidden by default (M-L; `GraphicsLayer.toImageBitmap()` on iOS is unverified).

### 4.5 Charts that are beautiful and readable

**Checklist with sources.**
- Prefer familiar types; bars for category comparison; line for change over time; zero baseline for bars; fewer grid lines; "maximize the width of the plot area" [A S-HIGCH].
- "Don't require interaction to reveal critical information"; make the whole plot area the scrub target; animate changes **and** signal them another way for VoiceOver and Reduce-motion users [A S-HIGCH].
- Separate contiguous colour areas with a narrow gap (iPhone Storage bar); never colour alone; use shapes or patterns [A S-HIGCH].
- Okabe-Ito: not only colours but "a combination of different shapes, positions, line types and coloring patterns"; "label elements of the graph on the graph itself rather than making a separate color-coded key"; avoid same-brightness pairs [A S-OKI].
- WCAG 2.2 1.4.11: parts of a graphic needed to understand it need 3:1 against neighbours; if labels and values are visible, slices are not required for understanding [A S-WCAG].
- Pie/donut for a glance, not for comparison (Few, snippet); add a ranked list with inline bars for the precise view [I]. Lunch Money's single stacked bar + mini-bar rows is the phone-friendly version [B S-LM4].
- Direct labelling beats legends [C Datawrapper S-DW]. Highlight the selected mark and dim the rest to neutral rather than only toggling [C, snippet].

**What the leaders do.** Copilot Cash Flow: tap = value and comparison, **double-tap = category/transaction detail**, dotted previous period, like-for-like month-to-date (day 24 vs day 24) [A S-CP5]; no scrub documented. Monzo: weekly bars with a dashed rolling average; **hold any bar** for exact spend and the top 3 categories [A S-MZ7, 2021]. Cashew: donut tap/clear/long-press-drag scrub, pinwheel reveal [A S-CA3]. Starling: weekly buckets (1-7, 8-14...) with a darker selected bar and floating value [B S-ST2]. Apple Card: day bars as gradients of that day's category colours [B S-AC3]. Fold: month-snapping scrub where header numbers, badges and list crossfade [C S-FO2]. Copilot fixed a bug where bars and charts used different colours for one category (Sep 2026) [A S-CP18].

**Our palette, recomputed by me.** Against white: orange 1.94, light-blue 2.30, green 2.36 (all below 3:1), teal 3.00, pink 3.76, brown 4.62, purple 4.82, indigo 4.86. Neighbour contrast in the current order is 1.01 to 2.01; **indigo (0.166), purple (0.168) and brown (0.177) have almost the same luminance, as do green and light-blue (0.394, 0.406)**. A colour-vision simulation by another researcher (Machado 2009 matrices, CIE76, approximate [I]) puts indigo vs purple at ΔE 6.8-9.2 under protan/deutan against 38.9 normally. Okabe-Ito itself fails 3:1 on white for orange (2.25), yellow and sky, which is why the rule is labels + gaps + outline, not only "swap the palette". CVD-safe sets: Okabe-Ito `#E69F00 #56B4E9 #009E73 #F0E442 #0072B2 #D55E00 #CC79A7` + black [A S-NYU]; Paul Tol bright `#4477AA #EE6677 #228833 #CCBB44 #66CCEE #AA3377 #BBBBBB` [A S-TOL].

**Deltas.** (S) 2 dp surface-colour gaps between slices and bars, order slices by size and alternate light/dark so neighbours differ in luminance by at least 1.5:1; (S-M) direct labels with value and percent on the top 3 slices and the peak bar; (S-M) "Show as table" under both charts; (S-M) a dashed **last-month ghost line** under the 14-day line and a budget pace marker; (S) hold-a-bar overlay and scrub across bars; (M) category-gradient day bars (needs a by-day-by-category aggregate [I]); (M) 6-month micro-bars per category row on one shared axis (small multiples); (S) headline sentence above each chart ("Food is 31% of spend, up 1,240 vs last month"). = CRAFT #9, #12.

**CMP note (verified in docs/source).** `drawWithCache` keeps Path/Brush objects across frames; `TextMeasurer` + `DrawScope.drawText` draws labels (expensive: cache them); `PathMeasure.getSegment` for line draw-in; animate in `graphicsLayer`/draw, not layout. Per another researcher's read of our code: `HomeSpark` builds two `Path`s and a gradient per draw frame and `TrendChart` animates bar height in layout with a literal 500 ms that Motion does not scale (CRAFT.md read the same TrendChart [R]). Canvas text is invisible to screen readers, so keep a summary node + per-mark semantics; Compose docs say not to use live regions on frequently updating content, so announce the scrub value through `stateDescription`. **Apple Audio Graphs has no CMP equivalent**: a descriptor would need Swift/UIKit interop (L, unverified); the pragmatic version is the summary sentence plus "Show as table".

### 4.6 Motion, transitions and haptics that feel fluid

**What the sources say.**
- **Material 3 Expressive** (Google, 2025): "a system of more natural, springy animations"; when you dismiss a notification "the others next to it subtly respond to your drag", and snapping it off the stack gives "a satisfying haptic rumble" [A S-M3E]. The spring tokens (read from the CMP source at v1.12.1): Expressive default spatial damping 0.8 / stiffness 380, fast spatial 0.6 / 800, slow spatial 0.8 / 200; effects springs (colour, alpha) 1.0 / 1600, 3800, 800; Standard spatial 0.9 / 700, 1400, 300. Spatial springs overshoot; effects springs never do. "Expressive is for prominent UI elements and hero interactions; standard is for utilitarian UI and recurring interactions" [A S-CMPSRC, S-M3API]. That maps onto us: hero total, sheets and the wallet stack expressive; tabs, lists and switches standard.
- **Apple HIG Motion:** "Aim for brevity and precision in feedback animations"; "generally avoid adding motion to UI interactions that occur frequently"; "don't make people wait for an animation to complete" [A S-HIGMOT]. **Android:** `spring` "can handle interruptions more smoothly than duration-based AnimationSpec types because it guarantees the continuity of velocity" [A S-ANDANIM].
- **Haptics.** Android principles: "less is more", prefer `HapticFeedbackConstants`, very frequent events "should be very subtle", co-design with the visual or the user "may perceive the haptic actuator to be broken", "given the choice of buzzy haptics or no haptics... choose no haptics" [A S-ANDHAP]. Apple HIG: use each system pattern for its documented meaning, keep cause and effect consistent, avoid overuse, **make haptics optional** [A S-HIGHAP].
- **Competitor facts that bound the design space.** Cashew (code): bar fill 1,500 ms, pinwheel 850 ms, count-up 1,000 ms from the previous value, overspend shake after 600 ms, 20 rotating reminders; and its iPhone users report sluggish animations [A S-CA3, S-CA4, S-CA5][D S-CA10]. Fold: spring on the scrub indicator and crossfading numbers (description) [C S-FO2]. Robinhood: a haptic synced to the visual peak made a launch "memorable" [C S-RHT2]. CRED: NeoPop press animation 50 ms (tilt 100 ms) with a haptic on touch-down and touch-up; its reviewers praise polish and attack latency [A S-CR2][D S-CR4]. Up: pull-to-save and accelerometer-driven particle pools [A S-UP1]. Monarch: no pressed state under lag, so taps queue and open duplicates [D S-MO6]. Spendee 6: the + froze and the toast lasted half a second [D S-SP5].

**Verified facts about our stack (CMP source at `v1.12.1` and official docs, read by a researcher).**
- CMP 1.12.1 ships `material3` `1.12.0-alpha03`, which is Jetpack Material3 `1.5.0-alpha22`; anything added after that is absent. `MotionScheme`, `ButtonGroup`, `ToggleButton`, `FloatingToolbar`, wavy progress and `Button(shapes=...)` are in `commonMain` with no opt-in; `LoadingIndicator` and `MaterialShapes` still need `@ExperimentalMaterial3ExpressiveApi` [A S-CP-CL, S-CMPSRC].
- **Our `MaterialTheme` receives only a colour scheme** (`AppRoot.kt:185` [code]), so every stock control (switch, chip, dialog, sheet) uses `MotionScheme.standard()` while our custom motion is a different system. `MaterialExpressiveTheme` must not be adopted wholesale: its default scheme is light-only.
- Default 1.8x speed: QUICK 216, STANDARD 396, EMPHASIZED 576, GLIDE 828 ms (I recomputed). Apple's advice and the springs-for-interruption doc point at `ScreenHost`'s tween transitions. Whether to keep Relaxed is an owner decision; a compromise is Relaxed for arrivals (hero, sheets) and QUICK/STANDARD caps for taps, tabs and list open. = CRAFT #2.
- Haptic mapping at v1.12.1 on iOS: Confirm = notification success; **Reject = notification error**; ContextClick, LongPress = medium impact; GestureEnd, GestureThresholdActivate, ToggleOn/Off, VirtualKey = light impact; SegmentTick and SegmentFrequentTick = the same selection generator; TextHandleMove = none; **KeyboardTap has no branch (silent)**; no generator calls `prepare()` [A S-CMPSRC]. So our UNDO (Reject) is the iOS error pattern and LIMIT (LongPress) a medium impact, not the documented warning pattern. = CRAFT #3.
- The stock Material `Switch`, `Checkbox` and `SwipeToDismissBox` play no haptics in commonMain at this tag; 14 `Switch`, 9 `Checkbox` and 10 `SwipeToDismiss` call sites would need a thin wrapper [grep by researcher].
- **CMP 1.12.2** (patch) fixes "flickering when animating non-rectangular `graphicsLayer` shadows" (our wallet stack and glass bar) and an iOS crash when a `TextField` loses focus because a `Dialog` appears [A S-CP-CL]. iOS Dynamic Type font scaling arrived in 1.13.0-alpha01 and the iOS Reduce Motion fix in 1.13.0-beta02: neither is in 1.12.1. Reduce Motion is read once per composition on both platforms, so a live toggle is ignored.
- Custom waveforms need no new library: Android `VibrationEffect.Composition` (API 30; no automatic fallback, so check `arePrimitivesSupported`, and a normal `VIBRATE` permission), iOS `platform.CoreHaptics` ships with Kotlin/Native. ActivityKit, WidgetKit and AppIntents have no Kotlin/Native bindings, so Live Activities, Controls and Lock Screen widgets are Swift [A S-ANDHAP, S-KNCH].

**Deltas (all REFINES unless stated).** Re-map `Feel` to documented meanings: UNDO to a soft tick, LIMIT to the warning pattern (custom iOS actual), add KEY, THRESHOLD, TOGGLE and FAIL, with an Off/Subtle/Full setting (S) = CRAFT #3. Give `MaterialTheme` a `MotionScheme` built from our scale (S preset, M scaled) (NEW). Use springs in `AnimatedContent` transitions (M). Rounded-corner morph in the container transform via `clipInOverlayDuringTransition = OverlayClip(shape)` (S). Swipe-row: neighbours react and a haptic at the commit threshold (S) = CRAFT #4. Bump CMP to 1.12.2 (S). Add `prepare()` before predictable haptics (S). A press-depth "plunk" for one hero CTA only, haptic on down and up (NEW, S) with the CRED lesson: no GPU framework, and keep latency under control. Slide-to-confirm only for rare weighty actions such as Restore backup (NEW, S). **Unverified:** interactive iOS swipe-back for our hand-rolled `ScreenHost`; backdrop blur of a recorded `GraphicsLayer` on iOS; skiko `RuntimeEffect` in `iosMain`; `MeshGradientPainter` rendering on iOS (Appendix B).

### 4.7 Visual identity: type, numbers, colour, shape, icons, illustration

**Type and numbers.** Every leader whose face could be confirmed uses a custom or licensed face: Monzo Sans (a custom cut of Universal Sans "for maximum readability"), Wise Sans, Toss Product Sans (Sandoll, 2021: **fixed-width numerals chosen because numerals are used so often**), Cash Sans (customised Sohne with a mono cut), Robinhood (Capsule Sans, then Phonic), Revolut (Aeonik), CRED (Cirka serif headings, Gilroy, Overpass Mono, "numbers were the key focus"); Copilot, Up and Klarna typefaces were not found [A/C S-TS-FONT, S-MZ-BR, S-WISE-SANS, S-CASH, S-RHF, S-CRED-ID]. Most keep the expressive face for short high-impact moments while a neutral face carries dense data. We cannot license a bespoke face; we can ship one OFL face.
- Measured by a researcher (fontTools on the `google/fonts` variable TTFs): **Plus Jakarta Sans 176 KB and Manrope 165 KB have `tnum`, `pnum` and the rupee sign; Inter 877 KB has them too; DM Sans (240 KB) has no `tnum`; Noto Sans Devanagari is 642 KB** (needed only if Hindi ships). All are SIL OFL 1.1; the licence text must ship in the app [A S-OFL].
- **`tnum` is not used anywhere in our code** [code]. `TextStyle.fontFeatureSettings` is common Compose API; whether the bare `"tnum"` tag is honoured on iOS in 1.12.1 is unverified (Appendix B).
- Currency and decimals: Ivy draws "USD" thin, "9,326" heavy and ".55" small and raised [B S-IV2]; practice articles suggest the symbol at 60-70% and cents at 50-60% [C, weak]. We round headlines to whole rupees already; rows show `₹1,23,456.78` at one size. Delta: smaller, lighter paise in rows via `AnnotatedString` built **after** masking, with semantics keeping the exact text (S-M). Indian grouping, lakh/crore and words already ship; nothing to add.
- Bundling: `compose.components.resources` is a first-party JetBrains artifact at the plugin version (a new line in `commonMain.dependencies`, not a third-party library); variable fonts work on all targets since CMP 1.8.0; resources are embedded in XCFrameworks since 1.8.2 (our build is a static XCFramework, so verify the font lands in the bundle). **Never use `ui-text-google-fonts`: it downloads fonts** [A S-CMPRES, S-CP-CL]. Recommendation: **figures-only** font (amounts, big numbers, day totals), system face for sentences so Dynamic Type and Bold Text keep working. = CRAFT #1, INDIA #12.

**Colour.**
- **Red.** Monzo uses its coral "very intentionally: in UI land, red shades usually mean 'something has gone horribly wrong'" [A S-MZ-BR]; YNAB: "Whenever you see red in YNAB, it's urgent" [A, snippet S-YN4]. Ours: every outgoing amount is `#B3261E` (light, 6.54:1) / `#FF8A80` (dark) with a minus sign, incoming `#17744A` / `#6FD3A0` [code `HomeStyle.kt:256`; contrast recomputed]. The sign already satisfies WCAG 1.4.1. Delta: outgoing amounts in `onSurface` with the sign, green for money in, red only for over-limit, failed or overdue (S; a visual-identity decision for the owner). Blue `#0072B2` and vermilion `#D55E00` are the Okabe-Ito alternative if up/down colours are ever needed (5.19:1 and 3.87:1 on white; use vermilion for shapes or large text only).
- **Calm palette.** a brand-guide breakdown says about three quarters of Monzo's deck is about not overusing its one loud colour, and Cash App limits itself to four colours [C, snippets]. REFINES-accent system: reserve saturated fills for the hero and the primary action, keep list rows neutral [I].
- **Dynamic colour** (Material You) is Android-only (`dynamicLightColorScheme` is `@RequiresApi(31)` in `androidMain`); a 7th "match my wallpaper" look needs an `expect fun` returning null on iOS; keep fixed money colours and shift them by at most about 15 degrees toward the primary, then re-check contrast (S-M; harmonisation has no Compose helper, so a small OKLab hue rotation would be our own code [I]).

**Category icons: options.**

| Option | Evidence | Verdict |
|---|---|---|
| First-letter tile (shipped) | Food and Fun both "F"; users of PennyWise ask for icons instead of dots; a Fold reviewer asks for logos | weakest at a glance |
| **Glyph on a coloured tile (Material Symbols, Apache-2.0)** | Starling tinted pictogram tile; Copilot emoji pills; Ivy user-chosen icon + colour; we already ship icons-extended | **recommended**: about 24 glyphs for the Indian default categories (groceries, food, fuel, rent, bills, recharge, EMI, travel, health, school, shopping...) |
| User-typed emoji | Copilot and Cashew allow it; system emoji differ by OS and Android version (newer ones can show as tofu on old phones [I, unverified]) | allow as a user choice, never for defaults |
| Merchant logos | Plaid Enrich returns a `logo_url` (needs network) [A S-PLAID]; Simple Icons is CC0 for files but "doesn't mean to imply that all icons within the project are also CC0", no trademark rights are waived [A S-SIMPLE]; nominative fair use is a defence, not a permission [C S-DMLP] | **do not ship logos**; use a coloured monogram or glyph per known merchant from our 97-name list (Monzo's compromise: logo big with a small category badge, which we cannot do without logos) |

**Shape, spacing, elevation.** Our corners are 28/24/22/20/18/14/8 and gaps 12/12/0/8/4 with four title styles (HOME_OPTIONS finding 15, still open). Delta: one shape scale (8/16/24 dp), spacing 4/8/12/16/24, three card roles (hero, standard, inline); stay flat (tonal colour + hairline) because our glass is faked and shadows cost on mid-range phones [I] (S-M) = CRAFT section 5.

**Illustration, mascot, icon, widgets.** Cleo's mascot is bounded ("Cleo isn't real"), Up is playful without points or badges; for us a mascot is a loud addition and PRODUCT_CONTEXT excludes gamification: add 3-4 more drawn "moment" pictures (first entry saved, month closed, backup done, all reviewed), no character (S). iOS 26 widgets in tinted or clear mode strip the background and tint content; our iPhone Today widget is `.systemSmall` only with a hard-coded purple capsule and no `widgetRenderingMode` handling [grep by researcher]; add a lock-screen accessory family with `.privacySensitive()` (S-M, Swift). Check the iOS app icon against Icon Composer's tinted/clear looks (S-M).

### 4.8 Delight and habit without guilt

**What the evidence says.**
- Streaks work commercially: Duolingo's 2017 post reports a Streak Wager lifting day-7 retention by 14%, and a Weekend Amulet making learners 5% less likely to lose the streak; "losing a streak can be very discouraging" [A S-DUO, older than 2024].
- Escape valves are now standard: Apple Fitness lets you **pause rings up to 90 days without breaking your award streak**, with a Monday review and a suggested goal [A S-APPLEPAUSE]; Finch shows a repair hammer on a missed day and has no failure state [C S-MOBBINS]; Alma pauses up to 14 days; Duolingo says two freezes instead of one brought "200,000 more people came back every day" [C S-MOBBINS].
- Anxiety is documented: a psychotherapist in the Guardian: "If the streak becomes the focus and not the learning... it taps into our negative beliefs" [C S-GUARD]; Smashing (2026-02) proposes a test: do you "make money by selling solutions to anxiety that your product created?" and prefers decay to hard reset and break copy like "You showed up for 42 days straight" [C S-SMASH]. I found no peer-reviewed streak-anxiety study that a researcher opened.
- **Award juries reward the opposite of nagging.** Apple Design Awards 2024 Social Impact winner Gentler Streak: "powered not by insistent reminders but an optimistic and encouraging vibe"; its Monthly Summary "is less about hard comparisons and more about progress" [A S-AD24]. 2025 Delight winner Denim (India): "smooth scroll transitions and elegant text and mesh gradients... custom haptics"; Opal: "fun touches like haptics when unlocking 'gems'" [A S-AD25]. I found no finance-app Apple Design Award winner in 2022-2026; Copilot was a 2024 Interaction **finalist** (name only, no jury text) [A S-AD24].
- Toss writes a congratulation push when a loan is fully repaid because repayment is "the reward for the effort so far"; users wrote that it gave them strength [A S-TS1].
- None of the apps in Sections 3.1-3.3 uses a streak or leaderboard in the evidence read; **Simplifi** ships weekly/monthly achievements that cannot be disabled and **Fina** shows a "17 days" streak chip [A S-SF2][B S-FI3]. Habit levers elsewhere: a daily reminder at a user-chosen time (Toshl, Money Manager, Cashew's 20 rotating lines), a balance bar that turns red (Monefy), left-per-day.

**An evidence-compatible compromise (needs the owner; touches the non-goal).** A **weekly rhythm**: seven dots under the month card (filled if you logged anything or tapped "Nothing to add today", hollow with a moon for a rest day), a ring that closes at 5 of 7, resets every Monday, **no counter of consecutive weeks, no red state, no push about it**; a **Pause** switch (1-14 days or until a date; it also pauses nudges and the recap; "Welcome back", no catch-up list); a Monday recap with one suggestion accepted in one tap that compares you with your own history only. Copy leads with what happened ("4 of 7 days this week"), never what was lost. = partly CRAFT #14.

**Rare quiet milestones, at most about six a year:** first expense, first week of 5 dots, first month under budget, 100th entry, a loan cleared, a bill paid early. One warm line, `Feel.PAID`, and a short Canvas sparkle (core Compose, `withFrameNanos`, skipped under Reduce motion); no share prompt, no badge screen. **Confetti on every save would be the Monzo mistake** (repeat users asked to turn off a 33-times-a-year animation) [D S-MZ-CONF].

### 4.9 Trust cues for a finance app

- **Say only what is true, in short negations.** Actual: "Unabashedly local-first software. Actual is a local app, plain and simple.", and it admits "Data on your local device is still unencrypted" [A S-AB-HOME]. An offline tracker's listing: "All data is stored locally on your device. No servers, no cloud, no sync, no analytics, no third-party SDKs." [C S-ZERO]. Fold's "only transaction-related data is sent to our servers" sits near "doesn't read your emails or messages in any case", which reads as contradictory [A S-FO-PRIV]. Ours: "everything you enter stays on your phone, the app has no way to send it anywhere" is **literally true on Android** because the manifest removes `INTERNET` [code].
- **Verify it yourself (NEW, S).** An About > Privacy panel: Android: "This app asks for no permission to use the internet; Android apps need that permission to connect" with the path Settings > Apps > Expenses > Permissions (and `aapt dump permissions` for technical users); iPhone: Settings > Privacy & Security > App Privacy Report > App Network Activity (iOS 15.2+) [A S-APPLEPR]; and the store-level echoes (Play Data safety, Apple privacy label). No shield icons or padlock stock art. = INDIA #1, #2.
- **Lock, hide, redact.** Shipped: app lock, hide from recents and screenshots while locked, Hide amounts (also bars), masked widgets. Gaps: **recap and budget notifications on a locked screen show amounts** [code]; Android 15 `View.setContentSensitivity(CONTENT_SENSITIVITY_SENSITIVE)` hides content during screen sharing (API 35, one line in `androidApp`, no-op below) [A, snippet S-AND15]; Revolut hides balances on a flip, on by default [A S-RV3]; N26's "wave to hide" is reported buggy [D]; Up has an "Allow Widgets" switch in Security [A S-UP-REL]; Paytm hides single payments (INDIA #33). Actual's scramble keeps the layout intact (digits scrambled, names visible), useful for screenshots [A S-AB1].
- **"No account, no cloud" without scaring people (backup UX).** Local-only apps are trusted for privacy and doubted for loss. Signal makes the user confirm the recovery key and says there is no password reset [C S-SIGNAL]. Ours has encrypted backups and a reminder card. Delta (S): a calm persistent status line "Last copy 3 days ago, saved to Files" turning amber after N days and framed as "your safety net you control"; after the first backup offer a "Test restore" into a scratch database (M).
- **Permission screens.** Play requires a prominent in-app disclosure before the OS prompt [A S-PLAYDISC]. Template (mine [I]): **Reads** incoming bank/payment texts. **Ignores** personal messages, OTPs, unknown senders (only a count is kept). **Keeps** amount, merchant, date. **Sends** nothing anywhere: this app has no internet access. Then Allow. = INDIA #6. Ask in context, never at launch (Toss, HIG).
- **India.** DPDP Rules 2025 notified 13 Nov 2025; most duties from 13 May 2027; notices must be plain and available in English or any of 22 languages [C S-DPDP]. Whether an app that never transmits data is a Data Fiduciary is a legal question: ask counsel. Do not imply RBI/NPCI/bank endorsement or use bank/UPI logos as decoration.

### 4.10 Accessibility and large screens

**State against WCAG 2.2 and the platforms.**
- **1.4.1 / 1.4.11:** amounts carry +/-; chart marks fail 3:1 (see 4.5); verify the budget bar and any "over" chip use a word or glyph, not hue alone (not audited). YNAB tuned its Available pills for contrast in 2019 and admitted red/green is not ideal for red-green colour blindness [C S-YN9, older than 2024].
- **1.4.3:** the month-card small text on the gradient measured about 2.9:1 near the glow (HOME_OPTIONS finding 14) and is **still open**. Dark mode and large font are untested in the Android audit (ACCESSIBILITY_FINDINGS).
- **1.4.4 / Dynamic Type:** Android 200% layouts were fixed in Slice 112. **On iPhone, Dynamic Type font scaling arrived in CMP 1.13.0-alpha01, not 1.12.1, so the "text up to 200%" promise is unverified there**; test AX1-AX5 live, then Bold Text with a bundled face [A S-CP-CL; check in Appendix B]. Copilot's native apps have **no Dynamic Type** at all ("planned for the future") [A S-CP10]: a place to beat the benchmark.
- **High contrast:** CMP's iOS docs say Material3 `ColorScheme` "lacks out-of-the-box support for high-contrast colours"; since we build our own `colorSchemeFor`, react to `UIAccessibilityDarkerSystemColorsEnabled` on iOS and `UiModeManager.getContrast()` (API 34) on Android (M) [A S-CMPA11Y, S-UIMODE].
- **Screen readers:** semantics map to native accessibility on iOS and `testTag` maps to `accessibilityIdentifier`; `performAccessibilityAudit()` works from XCTest, a free iOS counterpart to our Android ATF test (S) [A S-CMPA11Y]. Chime's VoiceOver complaint ("two buttons combined so I can't tap either") is a concrete checklist item: never merge clickable children [D S-CH1]. A researcher found **no iOS accessibility measurement anywhere in the repo**.
- **Reduce motion:** read once, not observed live; replace large movement with a short crossfade rather than "instant" (CRAFT G13).
- **EU Accessibility Act** (28 Jun 2025) covers consumer banking services; a local ledger is likely out of scope [I]; do the WCAG work for users.

**Large screens.** Our two-pane threshold (600 dp wide, 480 dp high), fold awareness and keyboard shortcuts already ship. Android 16 ignores orientation and resizability restrictions for apps targeting API 36 on windows of 600 dp or more; Android 17 removes the opt-out [A, snippets S-AND16]; we have `resizeableActivity="true"` [code]. Copilot's iPad app is the benchmark: sidebar that hides, split view / slide over / centre window, "we'll also tell you when the window gets a little too small", all Mac shortcuts, multi-select with bulk actions [A S-CP-IPAD][C 9to5Mac]. Deltas: a "window too small" notice instead of silent breakage; bulk category change parity on wide windows; iPad hardware-key commands (our iOS has none; whether CMP 1.12.1 delivers Cmd keys to `onKeyEvent` on iPad is unverified); check `UIRequiresFullScreen` and the orientation list in `Info.plist` (3 of 4 orientations; an old App Store check rejects iPad apps without all four unless full-screen [C S-ITMS]) (S-M). A navigation rail from 600 dp is CRAFT #19. India: Hindi strings run longer than English [I, unsourced]; our fixed 112 x 76 dp tiles must wrap when Hindi ships; keep Latin digits (Google/Kantar playbook).

---

## 5. Compose Multiplatform feasibility table (no new libraries, no internet)

Status key: **A+** = verified in official docs or in the CMP source at the `v1.12.1` tag, compiled for iOS in `commonMain`; **A-Android** = Android only; **unverified** = Appendix B has a 5-minute check. Effort S/M/L as above; L also means "needs Swift or other native glue".

| Technique | What users get | Compose API | Status | Effort | Risk / caveat |
|---|---|---|---|---|---|
| Our own `MotionScheme` for stock controls (preset `MotionScheme.expressive()` or a scaled custom one) | Switches, chips, sheets move like our custom parts and follow Off/Quick/Relaxed/Slow | `MaterialTheme(colorScheme, motionScheme = ...)`; `MotionScheme` is a public non-sealed interface | A+ | S / M | Behaviour change for every stock control; do not use `MaterialExpressiveTheme` wholesale (light-only default) |
| Shape morph on press (`ButtonDefaults.shapes()`) | tactile squish on stock buttons | `Button(onClick, shapes = ...)` | A+ (no opt-in at this tag) | S | alpha artifact: API may be renamed on upgrade |
| `LoadingIndicator`, `MaterialShapes`/`Morph` | a branded "working" indicator for import/OCR | `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` | A+ source, still experimental | S | experimental opt-in |
| `LinearWavyProgressIndicator` | lively progress for backup/import (not for budgets) | stable at this tag | A+ | S | drop amplitude under Reduce motion |
| Haptic vocabulary re-map + Off/Subtle/Full setting + `prepare()` | consistent meaning; learnable feel | `LocalHapticFeedback` (13 common types) + iOS `expect/actual` for warning/soft/rigid | A+ | S | none |
| Haptics on stock Switch/Checkbox/swipe threshold | controls feel native on iPhone | `HapticFeedbackType.ToggleOn/ToggleOff/GestureThresholdActivate` in a wrapper | A+ | S | 14 + 9 + 10 call sites |
| Custom waveform ("coin drop", "limit crossed") | a signature touch | Android `VibrationEffect.startComposition()`, iOS `platform.CoreHaptics` | A+ (Kotlin/Native ships CoreHaptics) | M | no Android fallback below 16; always fall back to a `HapticFeedbackType` |
| Rounded container transform | row grows with its corner radius, no hard clip | `sharedBounds(clipInOverlayDuringTransition = OverlayClip(shape))` | A+ (shared elements since CMP 1.7.0) | S | needs an animated corner |
| Spring screen transitions (interruptible) | fast double-tap/back does not snap | `AnimatedContent` with spring/`Motion.spring` | A+ | M | |
| Android predictive-back preview inside the app | the back swipe previews Home | `NavigationBackHandler` (replaces deprecated `PredictiveBackHandler`) + `graphicsLayer` | A+ API; extra Gradle artifact possible (unverified) | M | `PlatformBackHandler` today is a plain `BackHandler` that suppresses the system preview |
| iOS interactive swipe-back for our `ScreenHost` | the edge swipe drags the screen | docs promise it only for `NavHost` default transitions | **unverified** | M-L | may fire only on release |
| Live Reduce motion | respects a toggle while the app runs | `UIAccessibilityReduceMotionStatusDidChangeNotification`; Android `ANIMATOR_DURATION_SCALE` observer | A+ platform APIs | S | read once today |
| Charts: `drawWithCache`, animate in draw/`graphicsLayer`, `PathMeasure.getSegment`, `TextMeasurer` direct labels, dashed ghost line | smoother scrubbing; labels on the chart | all common APIs | A+ | S-M | Canvas text invisible to screen readers: keep semantics |
| Per-digit odometer for `RollingAmount` | digits roll only where they change | `AnimatedContent` per digit keyed from the right; `TextMeasurer` or `tnum` for fixed cells | A+ | M | keep `clearAndSetSemantics`; Indian grouping adds a separator width when digits grow |
| Tabular figures | digits stop jittering | `TextStyle(fontFeatureSettings = "tnum")` | API common; **iOS honour unverified** | S | system digit widths vary; bundled font that has `tnum` is safer |
| Figures-only bundled font | one number voice on both platforms | `Font(Res.font.X)`, `FontVariation.Settings` | A+ (variable fonts on all targets since 1.8.0; XCFramework resources since 1.8.2) | S-M | new first-party artifact `compose.components.resources`; +165-240 KB; verify the font lands in our static XCFramework |
| Real backdrop blur behind the glass bar | frosted glass instead of translucent | record content in `rememberGraphicsLayer()`, draw twice with `BlurEffect` | API common; **iOS backdrop unverified**; Android 12+ only | L | re-records the list layer each frame; gate on Reduce Transparency and low RAM |
| Shader or mesh-gradient hero | premium animated background | Android 13+ `RuntimeShader`; iOS skiko `RuntimeEffect`; or `MeshGradientPainter` (CMP 1.12.0) | A-Android for AGSL; iOS **unverified** | M-L | two shader dialects, battery; static gradient fallback |
| Confetti or particle burst | a rare celebration | `Canvas` + `withFrameNanos` | A+ | S-M | opt-in, rare, off under Reduce motion |
| Material You "match my wallpaper" look | native feel on Pixel/Samsung | `dynamicLightColorScheme(context)` in `androidMain` | A-Android | S-M | iOS returns null; keep money colours fixed |
| iOS Live Activity for Trip mode; Android 16 Live Update | glance during a trip | ActivityKit (Swift only; 8 h limit then up to 4 h on the Lock Screen; 4 KB state); `Notification.ProgressStyle` | Swift / `androidMain` | L / M | Android guidance: only ongoing, user-initiated, time-sensitive activities, so Trip mode qualifies and a "today's spend" ticker does not |
| Lock Screen widgets, Control Center / Action-button control | one-press capture and a glance | `accessoryCircular/Rectangular/Inline`; `ControlWidgetButton` + App Intent | Swift only | S-M each | control must hand off to the Kotlin store safely |
| Bump CMP 1.12.1 to 1.12.2 | fixes shadow flicker and an iOS TextField+Dialog crash | Gradle version | A+ | S | stay off 1.13 betas for release |

**Quotable verified facts (each re-checked at the v1.12.1 tag or in official docs by a researcher).** (1) CMP 1.12.1 = material3 1.12.0-alpha03 = Jetpack Material3 1.5.0-alpha22. (2) `MotionScheme` is a public interface; `MaterialTheme.Values.motionScheme` defaults to `standard()`. (3) Expressive spring tokens: default spatial 0.8/380, fast 0.6/800, slow 0.8/200; effects 1.0/1600, 1.0/3800, 1.0/800; Standard spatial 0.9/700, 0.9/1400, 0.9/300. (4) `ButtonGroup`, `ToggleButton`, `FloatingToolbar`, wavy progress, `Button(shapes=)` need no opt-in; `LoadingIndicator`/`MaterialShapes` do. (5) iOS haptic mapping as in 4.6. (6) Android constants: CONFIRM/REJECT/GESTURE_END API 30; SEGMENT_TICK, SEGMENT_FREQUENT_TICK, TOGGLE_ON/OFF, GESTURE_THRESHOLD_ACTIVATE API 34, with compat fallbacks below. (7) `dynamicLightColorScheme` is Android 12+ only; `Modifier.blur` is a no-op below Android 12; `RuntimeShader` needs Android 13+. (8) Shared elements since CMP 1.7.0; variable fonts all targets 1.8.0; `PredictiveBackHandler` deprecated for `NavigationBackHandler` in 1.10. (9) iOS Dynamic Type font scaling 1.13.0-alpha01; iOS Reduce Motion fix 1.13.0-beta02; CMP 1.12.2 fixes shadow flicker and the TextField+Dialog crash. (10) skiko `RuntimeEffect.makeForShader` exists; `CoreHaptics.def` ships with Kotlin/Native, `ActivityKit`/`WidgetKit`/`AppIntents` do not.

---

## 6. Anti-patterns to avoid (each with the evidence that it hurt someone)

1. **Hero that eats the list.** Spendee 6: "About the 70% of the Home Screen is taken up by information I don't need... only two items from a long list fit in the viewport" (24 Aug 2026) [D S-SP5]. Guard: at least N rows visible under the hero (HOME_OPTIONS target: first entry on the first screen), hero under about 40% of the screen [I].
2. **Bigger controls, fewer facts.** Spendee: "All ui elements components increased in size and now there is less information readily available" (13 Sep 2026); YNAB: "Way less information density" (1 Sep 2026); Toshl v2.0.13 in 2017 said the same [D]. Density is a feature; keep Compact.
3. **Replacing a glanceable bar with a decorative glyph.** YNAB circles and status words: "visual noise that greatly reduces readability" (1 star 2026-08-15); "Why do I want to see 'Fully spent' repeated over and over again?" (3 stars 2026-08-13) [D S-YN7].
4. **A home screen that turns into a to-do list.** YNAB: "a list of tasks like a to-do app" (1 star 2026-08-13); and "extra taps" (1 star 2026-10-12) [D S-YN7]. Our one-nudge rule is the right instinct.
5. **Changing the UI with no revert.** YNAB, Spendee ("Please give us a way to change back to the old UI"), Bluecoins, Monzo, bunq ("Changing the ux interface every fortnight"), N26 [D]. Monzo's snooze and change-aversion measurement is the model [A S-MZ2].
6. **Silent taps.** Monarch: "nothing happens, no loading symbol, no feedback of any kind... those other taps you did? Those were all queued" (2 stars 2026-08-15); Spendee: the + froze [D]. Always show a pressed state (our `pressScale` does) and never queue taps.
7. **Toasts that vanish and scroll hijacks.** Spendee: the "added" toast "lasts for half a second", "Adding a new transaction forcibly scrolls me away from where i left off" [D S-SP5]. Keep our Undo bar at a readable duration and never scroll after an add.
8. **Gating entry speed.** Splitwise: a daily cap and a full-screen Pro interstitial with a "continuing in 9 seconds" countdown [B S-SW4]; "There is absolutely no reason why a free account should be limited on the number of expenses it can enter per day" [D S-SW5]. We have no ads and no caps: say so.
9. **Ads, promos and unrelated pushes inside the money feed.** N26 ("Annoying ads everywhere"), Wise ("non banking nonsense"), Up (unrelated pushes), Emma (a referral FAB where Add should be), Monefy (ads on open) [D]. Review-prompt fatigue: Starling "Stop asking me to write a review every time I send money" (5 stars 2026-11-06) [D].
10. **Removing review-before-save for imports.** Buddy: "It no longer lets you review transactions before it adds them" (Jul 17) [D S-BU5]. Our captures never auto-post; keep it.
11. **Silent capture failure.** Mooncard: photo capture "unreliable... pas de message d'erreur" [D S-MC1]; Monarch: "There's essentially no UI for telling you that your accounts haven't been synced" (1 star 2026-10-24) [D S-MO6]; axio (INDIA_APPS lesson 2). Always show a result or a queued state and a "last text read" line.
12. **Judgemental or over-personal recaps.** Year in Monzo: "But mainly, you fast fooded." and references to "life goals" (Guardian, Ombudsman complaint) [C S-MZ4]. Descriptive, opt-in, no jokes about food or health.
13. **Streak guilt.** Duolingo repair nagging and "I can't bear the thought of throwing away such a long streak" [C S-GUARD]; Simplifi's un-disableable achievements [A S-SF2].
14. **Reward theatre and bloat.** CRED: "heavy ui that is busy... Focus on basic payment" (2024); bunq spin-the-wheel cashback "Temu-like" [D]. Delight must never cost speed.
15. **A tutorial overlay at first launch.** NN/g: no success gain, lower perceived ease [A S-NN1]. Our automatic 19-stop tour should become opt-in.
16. **Soft or hard paywalls before value.** Monefy (carousel then paywall), Buddy (paywall ~20 s in), Fina (hard paywall after one expense, which blocks judging the UI), YNAB ("immediately asks for money", 1 star 2026-10-28) [B/D]. Cashew's dismissable "No, I don't want free stuff" is the gentle version [A S-CA9].
17. **Cartoon tone that reads as childish to adults.** Emma ("Child-like feel to the overall theme", 2026-10-14) [C S-EM4]; a Toshl reviewer on the one-eyed monster: "Doesn't produce a sense of trust" (2018) [D]. Make tone a setting with a calm default.
18. **Colour as the only signal.** YNAB 2019 admission; WCAG 1.4.1; our palette (4.5). Pair every state with a word or glyph.
19. **Hidden gestures with no coach.** Ivy: "I keep pressing in random areas as they all somewhat expand" (2021, old) [D S-IV1]; Cashew: "whats clickable... and whats not" (2025) [D]. If you add a gesture shortcut, add one contextual tip.
20. **Forced updates and lock-outs.** Monarch: "They force you to update the app and COMPLETELY lock you out until you do." (1 star 2026-10-30) [D S-MO6]. Irrelevant offline; a reason to keep the app independent of any server.
21. **"Looks like Excel".** Goodbudget's dated look is the standing complaint (2024-2026) [C S-GB5, S-EL]; usefulness does not save a flat, uncared-for surface.
22. **Merged accessibility elements.** Chime: "views with a close button combined so a voiceover user can't tap them" [D S-CH1].
23. **Roast or slang as the default voice.** Cleo keeps roast opt-in; Monzo: "careful about idioms"; Toss: avoid slang and memes ("Universal words") [A/C S-TS1, S-MZ3].
24. **Unreadable motion defaults on cheap phones.** BHIM "animations are still jittery" (2025-09), CRED memory heavy (INDIA_APPS lesson 9); about 57% of India's 2025 smartphone shipments were under 200 USD [S, via INDIA_APPS]. Add a low-RAM mode (INDIA #32).

---

## 7. "What would make users say wow": 50 ideas

One line each (example figures in the ideas are illustrative). **Tag** = NEW (nothing like it ships) or REFINES-x. **Effort** S (under 2 days), M (3-6 days), L (over a week) [I]. Everything works offline from data the app already holds; "Swift" means it cannot be shared Compose code. "= CRAFT #n / INDIA #n" marks overlap with the sibling docs (the idea is then confirmed by extra evidence here, not new). Ideas that touch the "no streaks" decision are marked **(owner)**.

### Home, hero and budget
| # | Idea | Inspired by | Tag | Effort | Note |
|---|---|---|---|---|---|
| 1 | A **Today tick** on the budget bar plus one sentence: "You can keep spending 1,698 a day for 9 more days" | Cashew, Monarch, Toshl, Goodbudget, Bluecoins, Spendee | REFINES-AnimatedBudgetBar | S | = CRAFT #10 |
| 2 | **Dotted ideal-pace diagonal** on the 14-day line with an end-dot pill ("under by 2,100"); line colour by how far ahead of pace | Copilot | REFINES-HomeSpark | S-M | recurring items excluded from the line so rent does not spike |
| 3 | Budget state **in words next to the colour**: "On pace", "Risk of overspend 855", "Over by 1,655"; yellow means projected, not a threshold | Wallet, Monarch, YNAB status words | REFINES-budget bar | S | accessibility win |
| 4 | Tap the hero to see **how it is worked out** ("budget 60,000 - bills still due 9,400 - spent 31,822, over 9 days") | Copilot "?", PocketGuard, Monzo | REFINES-Safe-to-spend | S | |
| 5 | **Committed vs free** line: "2,100 of what's left is already spoken for by bills due" | Fina, Emma, Monzo | REFINES-Safe-to-spend | S-M | data exists in the safe-to-spend rows |
| 6 | "**Choose your big number**": left this month / left today / spent so far | Fina, Spendee "one number for today", Daily Drip | REFINES-Customise home | S-M | pairs with demoting the second "a day" figure |
| 7 | Rollover/"not a target" micro-copy under the left figure | Think Forward study, Daily Drip | REFINES-WholeAmounts | S | |
| 8 | "**Days to payday**" beside the hero | bunq next-payday countdown | REFINES-Safe-to-spend | S | = INDIA #14 |
| 9 | **Budget presets** when typing a limit: last period's spend / last budgeted / average of three periods | Lunch Money, Actual | NEW | S | |
| 10 | **Rebalance sheet**: move budget between categories keeping the total, preview first, "Only this month / From now on" | Copilot | NEW | M | reframes "over" as a trade-off |
| 11 | **Hatched future bars** and a pinned "Scheduled - 2 - 4,300" card above Today | Spendee, Apple Recurring | NEW | M | |
| 12 | **Upcoming/projected remainder** on bill rows ("will go to -18 before the bill posts") | Goodbudget running balances | REFINES-Bills/Budget | S-M | |

### Entry and capture
| # | Idea | Inspired by | Tag | Effort | Note |
|---|---|---|---|---|---|
| 13 | **"chai 20" smart field** with a preview chip, never auto-saved; Hinglish tokens (`ke`, `diye`, `auto`) | Fina, Cashew preview card, Mera Kharcha | NEW | M | pure Kotlin in `core:domain`, host-testable; voice later = INDIA #10 |
| 14 | **Maths in the number pad** (`120+45`, `450*1.18`) with live result and amount in words | Actual, Monefy, YNAB (2026-06) | NEW | S | verified absent in our code |
| 15 | **Title learns the category** while typing: one suggestion row, tile + bold match | Cashew | NEW/REFINES-LearnedPayees | S-M | check the add form first |
| 16 | **Most-used categories first** and a button that echoes the choice ("Add 'Chai'") | Wallet, Lunch Money, Monefy | REFINES-quick entries | S | |
| 17 | **Swipe sideways** in the add form to flip expense/income | Spendee 6.0.12, Dime | NEW | S | |
| 18 | **Long-press Add** opens pinned quick entries from any screen | Cashew | REFINES-quick entries | S | |
| 19 | **Evening "Nothing to add today"** one-tap action (notification or widget) that marks the day reviewed | NoBuy, Spendee reminder | NEW | S-M | feeds idea 38 |
| 20 | **Notification direct-reply** quick add (Android) and a text-input action on the evening notice (iPhone) | Android/Apple docs | NEW | M | text never leaves the phone |
| 21 | **iOS Lock Screen widget** (Add + Today) and a **Control Center / Action-button** control that opens the number pad | Up, Monarch, YNAB lock-screen shortcut | NEW | S-M each (Swift) | = CRAFT #16 |
| 22 | **On-device voice** feeding the same parser | Mera Kharcha (server version) | NEW | M-L | needs mic permission and policy updates; Hindi quality unverified |
| 23 | **Receipt/UPI-screenshot capture** that pre-fills merchant, date and amount | Wise "Snap it. Send it.", Mooncard | REFINES-bill photo reader | L | ML Kit/Vision already on device |

### Review and trust
| # | Idea | Inspired by | Tag | Effort | Note |
|---|---|---|---|---|---|
| 24 | **To-review closure state**: "All cleared. The next text will land here." plus a small dot on unreviewed rows everywhere | Lunch Money, Copilot | REFINES-To review | S | |
| 25 | **"Needs review" widget** and widget filter | Copilot, Monarch | NEW | S-M | widget privacy rules apply |
| 26 | **Why this category?** sheet: "You put SWIGGY in Food 14 times" with "Always / Just this one" | Nuuke concept, Starling, Paytm, Lunch Money "by RULE" | REFINES-capture evidence | S-M | `CategoryEvidence` exists |
| 27 | **Data-freshness chip**: "Last bank text read 2 min ago", amber after a quiet stretch | Simplifi reviewer ask, Monarch silent-sync complaint | REFINES-capture health | S | = INDIA #3 |
| 28 | **Verify it yourself** panel and an airplane-mode check | Actual, BachatPe | NEW | S | = INDIA #1, #2 |
| 29 | **Backup status line** ("Last copy 3 days ago, in Files") and a "Test restore" | Signal | REFINES-backup reminder | S / M | |
| 30 | **Reads / Ignores / Keeps / Sends nowhere** template on every permission screen | Play disclosure rules | REFINES-GuidedSetup | S | = INDIA #6 |
| 31 | **Widget kill switch** ("Allow widgets") and tinted/clear previews | Up, iOS 26 | REFINES-widgets | S | |
| 32 | Make **recap and budget notifications private** on a locked screen | Android/Apple notification guidance | REFINES-recap (bug) | S | verified in `Notifier.kt` |

### Charts, categories, numbers
| # | Idea | Inspired by | Tag | Effort | Note |
|---|---|---|---|---|---|
| 33 | **Colour-blind-safe chart default** with gaps, direct labels and "Show as table" | HIG, Okabe-Ito, WCAG | REFINES-BreakdownRing/TrendChart | S-M | = CRAFT #12 |
| 34 | **Last-month ghost line** under the 14-day line and month bars | Copilot, Monzo, Robinhood | NEW | S-M | needs the prior series from the view-model |
| 35 | **Hold a bar** for total + top 3 categories; scrub across bars | Monzo, HIG | REFINES-TrendChart | S | |
| 36 | **Day bars filled with that day's category colours** | Apple Card | NEW | M | by-day-by-category aggregate |
| 37 | **Glyph (or emoji) on a stable-colour category tile**; coloured monograms for the known merchants | Copilot, Starling, Ivy | REFINES-category tile | M | = CRAFT #6, INDIA #34 |
| 38 | **Figures-only bundled font with tabular digits**, quiet rupee sign, small paise | Toss, Cash Sans, Ivy | NEW | S-M | = CRAFT #1, INDIA #12 |
| 39 | **Neutral outgoing amounts**; red only for over-limit, failed or overdue | Monzo, YNAB | REFINES-amountColor | S | owner decision |
| 40 | **Month-snapping scrub** where header numbers and the list follow the selection | Fold | REFINES-HomeSpark | M | |
| 41 | **6-month micro-bars** on category rows with one shared axis | Tufte small multiples, Simplifi watchlist | NEW | M | |

### Coach, delight, tone
| # | Idea | Inspired by | Tag | Effort | Note |
|---|---|---|---|---|---|
| 42 | **Monday recap card** on Home (3 lines) with the notification as a pointer | Monarch, Up | REFINES-weekly recap | M | |
| 43 | **Tap-for-why** on the insight sentence: the 3 entries behind it | Monarch sparkle | REFINES-usual-for-you | S-M | rule-based, no LLM |
| 44 | **Weekly rhythm** dots + **Pause** + "Nothing to add today"; no counter, no red state **(owner)** | Apple rings, Finch, Duolingo amulet | NEW | M | |
| 45 | **Rare milestone moments** (about six a year) with one warm line, a Canvas sparkle and `Feel.PAID` | Toss, Opal, Gentler Streak | REFINES-Feel | S | = CRAFT #14 |
| 46 | **Voice setting** for insight copy: Plain (default) / Warm / Playful, shown as the same fact in three voices | Copilot, Fina, Cleo, Monzo | NEW | M | no roast by default |
| 47 | **Your year** (December, opt-in) with personal facts and a share image that hides amounts | Monzo, Strava, Any Distance | NEW | M-L | = INDIA #30 (Wrapped cards); iOS image export unverified |
| 48 | **Release notes with personality** inside the app ("What's new"), with an occasional Easter egg | Up, Chime, Monzo | NEW | S | |

### Motion and feel
| # | Idea | Inspired by | Tag | Effort | Note |
|---|---|---|---|---|---|
| 49 | **One haptic vocabulary** by documented meaning plus an Off/Subtle/Full switch; `prepare()` on iOS | HIG, Android principles | REFINES-Feel | S | = CRAFT #3 |
| 50 | **Motion tiers**: feedback quick, arrivals relaxed; stock controls on our `MotionScheme`; springs for transitions; CMP 1.12.2 | HIG, M3 Expressive | REFINES-Motion | S-M | = CRAFT #2; owner decision on the default speed |

Also worth a look if the above land (not counted): a press-depth "plunk" for one hero CTA (CRED, S); slide-to-confirm for Restore backup (Fold, S); an Apple-style Recurring calendar with rounded totals and a pre-charge alert (Apple, M); search that understands "over 500" and date ranges in one box (Apple Card, S-M; check our Search first); a goal drawn as a filling pool with an emoji (Up, M-L); share-a-day-as-image (Dime, M); an iPad "window too small" notice (Copilot, S); Spendee-style hatched/dashed future bars (idea 11).

### The ten I would build first (my judgement [I])
1 (pace tick and sentence) with 3 (state in words); 32 (private recap notifications: a real bug); 14 (maths in the pad) and 13 (smart field); 24 (review closure) with 27 (freshness chip); 33 (colour-blind-safe, labelled charts); 39 (neutral outgoing amounts) with 37 (glyph tiles); 38 (tabular figures, bundled digits); 49 (haptic vocabulary) with 50 (motion tiers); the first-run ghost month (section 4.1); 28 (verify it yourself).

---

## 8. Suggested order and decisions only the owner can make

1. **Bugs and cheap wins (days):** 32 private recap notifications; 49 haptic re-map and setting; CMP 1.12.2; 1, 3 and 4 (pace tick, words, formula); 24 closure state; 27 freshness chip; 28/29/30 trust copy; the tour becomes opt-in.
2. **Entry speed:** 14, 16, 17, 13, 15 (all pure Kotlin).
3. **Charts and identity:** 33, 35, 34; 38 (after the `tnum` iOS check); 37; 39.
4. **Coach:** 42, 43, 46; 47 later.
5. **iPhone-only Swift work:** 21, 31 (lock-screen and Control Center), then widgets previews.
6. **Large or risky:** 22 voice, 23 receipt fill, real backdrop blur, shader hero, interactive swipe-back, Live Activity.

**Decisions for the owner.** (a) Outgoing amounts in neutral instead of red (idea 39). (b) A weekly rhythm with pause, given the no-streak decision (idea 44), or keep only milestones. (c) Keep Relaxed (1.8x) as the default speed or cap taps/tabs/lists at Quick/Standard (idea 50). (d) A bundled figures font (+165-240 KB) or system digits. (e) Glyph tiles for categories (idea 37) and whether to ever allow user-typed emoji. (f) Keep two "a day" figures on Home or demote one behind a tap (4.2). (g) Make the 19-stop tour opt-in.

---

## 9. Evidence-quality caveats (read before quoting)

- **No video, no motion evidence.** Haptics and animation timing for competitors are known only from: Cashew's source code, CRED's open-source NeoPop library defaults and README, Material/Apple documentation, release notes, and a few written descriptions (Fold on 60fps.design, titles only for several shots). Copilot's haptics rest on one review line [C]; Apple Card's motion and haptics are unknown.
- **Screenshots.** Researchers opened store and marketing images and described only what they saw; none was saved. Marketing renders can differ from live screens; Toshl, Wallet, Money Manager and Goodbudget images are years old. Image URLs are in `expenses_builds/ui_research/global/LINKS.md`.
- **Reviews.** Apple's public review feed was read for YNAB (250 reviews, 2026-06-10 to 2026-11-11), Monarch (250), Lunch Money (13 text reviews) and several neobanks; counts such as "42 of 80 mention UI" are a researcher's keyword counts [I]. Apple omits the year on current-year reviews. Google Play pages (404 for several packages) and Reddit were mostly unreadable; Reddit items are titles or snippets.
- **Snippet-only claims** are tagged (snippet): Pace at PocketGuard, Cleo's roast mode, several streak mechanics, some Material data-visualisation text, a few Apple/Android doc passages. Do not quote them without opening the page.
- **Vendor and weak sources:** push-fatigue percentages (vendor blogs), "66% quit because of shame" (a vendor stat), Mobbin counts (text only), practice articles on currency-symbol sizes. Marked weak where used.
- **Concept vs shipped:** the Nuuke Copilot case study and Maria Vareva's Copilot notes are designer write-ups (concept or self-reported); the "Why this category?" sheet is a concept, not a Copilot feature. Blake Crosley's Copilot hex values come from the marketing site and are approximate.
- **Old sources** are flagged: Wise Insights (2020), N26 (2022), Up evolutionary design (2020) and custom insights (2022), Monzo graphs (2021), Duolingo streak post (2017), CRED UXPlanet (2022), Toshl 2016-2018, Goodbudget reviews 2011-2020.
- **The Think Forward study** was read as press coverage only (paper not opened); effect sizes are theirs and apply to a field setting with one app type.
- **My own computations** are approximate: WCAG ratios (exact formula, two researchers and I agree), the CVD ΔE numbers (one researcher's script, Machado 2009, CIE76).
- **Compose claims about iOS** are source-reading, not a build. Appendix B lists every unverified one with a 5-minute check. Nothing in this file was run on a device or simulator.


---

## Appendix A. Sources by id

Tags as in the legend. A URL with "(snippet)" or "(search citation)" was not opened by the researcher. Review feeds are Apple's public iTunes RSS (`itunes.apple.com/<cc>/rss/customerreviews/page=N/id=<ID>/sortby=mostrecent/json`), human pages `apps.apple.com/<cc>/app/id<ID>?see-all=reviews`. Repo docs cited are in `docs/` and `docs/research/` of this repo.

**Budgeters (YNAB, Monarch, Copilot, Lunch Money, Actual, Simplifi, PocketGuard, Rocket Money, Mint)**
- `S-YN1` [A] https://www.ynab.com/whats-new/the-great-ynab-remodel : YNAB remodel: Home tab description
- `S-YN2` [A] https://support.ynab.com/en_us/spotlight-BkdHBZUokg.md : YNAB Home tab help (also https://support.ynab.com/en_us/spaces-in-the-mobile-app-S1iIZQoqgg.md)
- `S-YN3` [A] https://www.ynab.com/whats-new/meet-card-mode-transactions-meant-to-be-swiped : Card Mode
- `S-YN4` [A] https://support.ynab.com/en_us/colors-and-icons-in-your-plan-HJQv_XHko.md : Available pill colours (the "red is urgent" line was read in a search citation)
- `S-YN5` [A] https://support.ynab.com/en_us/how-to-add-transactions-in-ynab-HyDwA_byi.md : Adding transactions
- `S-YN6` [A] https://www.ynab.com/whats-new?339368af_page=1 : What's new (page 1; page 2 is `...?339368af_page=2`): Plan Reset copy, progress bars, calculator multiply/divide
- `S-YN7` [D] https://itunes.apple.com/us/rss/customerreviews/page=1/id=1010865877/sortby=mostrecent/json : Apple US review feed, pages 1-5 (250 reviews, 2026-06-10 to 2026-11-11)
- `S-YN8` [B] https://cdn.bfldr.com/NIEXAZS5/at/q8348t6b3n7m6bkfsb8swhnc/Spaces-Home-Tab-iOS-Android-Help-Article.png : Home tab help screenshot
- `S-YN9` [C] https://racheleditullio.com/blog/2019/05/ynab-addresses-color-accessibility : Pill contrast change (2019, older than 2024)
- `S-YN11` [B] https://cdn.prod.website-files.com/6410766c6141dc6c15cfe33c/6ab2df86863570c1003fd64b_Mini%20Card.png : Card Mode card image
- `S-YN-GUIDE` [A] https://www.ynab.com/guide/the-ultimate-get-started-guide : "about 20-30 minutes" setup
- `S-MO1` [A] https://help.monarch.com/hc/en-us/articles/32125337244052-Using-Flex-Budgeting : Flex budgeting; green/yellow/red = under / on pace to overspend / over; the tick
- `S-MO2` [A] https://help.monarch.com/hc/en-us/articles/360058127551-Customizing-Your-Dashboard : Dashboard cards
- `S-MO3` [A] https://help.monarch.com/hc/en-us/articles/16116906962452-About-Monarch-s-AI-Features : AI features, sparkle
- `S-MO4` [A] https://www.monarch.com/blog/winter-release : Winter release: Weekly Recap, receipts, goals
- `S-MO5` [A] https://help.monarch.com/hc/en-us/articles/8611706513684-Using-Mobile-Widgets : Widgets
- `S-MO6` [D] https://itunes.apple.com/us/rss/customerreviews/page=1/id=1459319842/sortby=mostrecent/json : Apple US review feed (250 reviews, 2026-07-29 to 2026-11-11)
- `S-MO7` [B] https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/02/fa/d0/02fad01b-abd7-c244-8a32-55ea237a21a0/AppStore_Mobile_Frame3_1179x2556.jpg/600x1300bb.jpg : Budget tab with today ticks
- `S-MO8` [A] https://help.monarch.com/hc/en-us/articles/360048393272-Getting-Started-with-Monarch : Setup steps
- `S-LM1` [A] https://lunchmoney.app/ : Positioning; features pages https://lunchmoney.app/features/budgeting/ and /features/rules
- `S-LM2` [A] https://feedback.lunchmoney.app/changelog : Changelog (3 recent categories, widgets)
- `S-LM3` [B] https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/c2/41/32/c2413259-e5dd-b024-e28a-4bed47eacc22/Screen__U00231.png/600x1300bb.jpg : iOS home screenshot
- `S-LM4` [B] https://lunchmoney.app/assets/images/screenshots/dashboard.png : Web dashboard checklist cards; rules/history image https://lunchmoney.app/assets/images/cms/feature-rules-full.png
- `S-LM5` [D] https://itunes.apple.com/us/rss/customerreviews/page=1/id=6739028463/sortby=mostrecent/json : Apple US review feed (13 text reviews)
- `S-AB1` [A] https://actualbudget.org/docs/getting-started/tips-tricks/ : Keyboard shortcuts, math in amount, scramble
- `S-AB3` [A] https://actualbudget.org/docs/custom-themes/ : Theme catalogue, chart variables
- `S-AB5` [D] https://github.com/actualbudget/actual/issues/9400 : "never tells you that something needs attention"
- `S-AB6` [B] https://actualbudget.org/assets/images/tour-budget-overview@2x-4fd3965f33841ca721be84524abf0e7d.webp : Budget tour image
- `S-AB7` [C] https://www.jgiesing.com/actual-budget-finally-a-budgeting-app-that-respects-your-data/ : "snappy" blog (2026-02-15)
- `S-AB-HOME` [A] https://actualbudget.org/ : "Unabashedly local-first"; sync doc https://actualbudget.org/docs/getting-started/sync/
- `S-CP1` [A] https://www.copilot.money/ : Home page: assistant voices, hero mock
- `S-CP2` [A] https://help.copilot.money/en/articles/6045480-dashboard-tab-overview : Dashboard
- `S-CP3` [A] https://intercom.help/copilotmoney/en/articles/10309907-dashboard-line-colors : Line colour rule
- `S-CP5` [A] https://help.copilot.money/en/articles/9682232-cash-flow-tab-overview : Cash Flow: tap, double-tap, dotted previous period
- `S-CP6` [A] https://help.copilot.money/en/articles/6206302-rebalancing-your-budget : Rebalance sheet
- `S-CP7` [A] https://help.copilot.money/en/articles/11157550-quick-start-guide : Quick start (30 reviewed transactions)
- `S-CP8` [A] https://help.copilot.money/en/articles/14462982-money-assistant-overview : Money assistant
- `S-CP9` [A] https://help.copilot.money/en/articles/9834331-adding-widgets : Widgets
- `S-CP10` [A] https://help.copilot.money/en/articles/9828946-display-settings-for-vision : Accessible Colors, +/-, no Dynamic Type
- `S-CP12` [D] https://apps.apple.com/us/app/copilot-track-budget-money/id1447330651?see-all=reviews : App Store reviews
- `S-CP13` [B] https://swipefile.design/_astro/000.DgZbxTiA.webp : Dashboard hero image
- `S-CP14` [B] https://swipefile.design/_astro/001.D1awAYUp.webp : Cash Flow image; category detail https://swipefile.design/_astro/002.C4Srm7pp.webp
- `S-CP18` [A] https://www.copilot.money/dispatch : Release posts (same colour per category, Sep 2026)
- `S-CP-IPAD` [A] https://help.copilot.money/en/articles/10003978-copilot-money-for-ipad : iPad app
- `S-CP11` [A] https://developer.apple.com/articles/copilot-money : Apple developer story (native, local data)
- `S-SF1` [A] https://support.simplifi.quicken.com/en/articles/3620741-using-the-spending-plan-on-the-mobile-app : Simplifi Spending Plan
- `S-SF2` [A] https://support.simplifi.quicken.com/en/articles/5518419-earning-achievements-in-quicken-simplifi : Achievements (cannot be disabled)
- `S-SF3` [A] https://support.simplifi.quicken.com/en/articles/4637231-using-watchlists-on-the-mobile-app : Watchlists
- `S-SF4` [A] https://support.simplifi.quicken.com/en/articles/3357180-getting-to-know-your-dashboard : Dashboard tiles and Customize
- `S-SF5` [D] https://apps.apple.com/us/app/quicken-intelligent-money/id1449777194?see-all=reviews : App Store reviews
- `S-PG1` [C] https://www.thepennyhoarder.com/budgeting/pocketguard-review/ : PocketGuard review
- `S-PG2` [C] https://pocketguard.com/news/insights-is-getting-rebuilt-starting-with-trends/ : Insights posts (HTTP 403 to the reader; known via a summary; second post .../insights-phase-2-a-clearer-picture-of-your-money/)
- `S-PG3` [D] https://apps.apple.com/us/app/pocketguard-budget-app/id949414211?see-all=reviews : App Store reviews
- `S-RM1` [A] https://help.rocketmoney.com/en/articles/934402-how-do-i-cancel-a-subscription : Cancel flow
- `S-RM2` [D] https://apps.apple.com/us/app/rocket-money-bills-budgets/id1130616675?see-all=reviews : App Store reviews
- `S-RM3` [A] https://www.rocketmoney.com/learn/personal-finance/is-there-an-app-that-can-tell-me-when-my-subscription-price-increased : Price-rise alert thresholds (vendor page)
- `S-MT1` [C] https://www.nerdwallet.com/article/finance/mint-app-closing-what-it-means-how-to-pick-a-new-budget-service : Mint legacy

**Manual and mobile trackers**
- `S-SP1` [A] https://www.spendee.com/ : Spendee site
- `S-SP2` [B] https://www.spendee.com/app/en/budgets.webp : Budget orb image
- `S-SP3` [B] https://www.spendee.com/app/en/home.webp : Home render
- `S-SP4` [A] https://www.spendee.com/blog/whats-new-spendee-6-0-12/ : 6.0.12 notes
- `S-SP5` [D] https://apps.apple.com/us/app/expense-budget-app-spendee/id635861140?see-all=reviews&platform=iphone : App Store reviews (v6 backlash)
- `S-WA1` [A] https://support.budgetbakers.com/hc/en-us/articles/7076953735314-Setup-Budgets : Budget states and predicted expenses
- `S-WA2` [A] https://support.budgetbakers.com/hc/en-us/articles/7150077480850-Add-or-Modify-Dashboard-Cards-Widgets : Dashboard cards
- `S-WA3` [B] https://play-lh.googleusercontent.com/1rORfTJhgViuBjS7-N9Qpm_GufsdzkPIRaYJy0ycNuro522cJICH3migIpskHBbgs6w67MFR-VJXdot1Y0fluMo=h520 : "Most frequent" row
- `S-WA4` [A] https://support.budgetbakers.com/hc/en-us/articles/7002928224786-Clone-Transactions : Clone transactions
- `S-TO1` [A] https://toshl.com/blog/how-to-track-expenses-incomes-and-transfers-ios/ : Entry flow, drag the +
- `S-TO2` [A] https://toshl.com/blog/a-new-toshl-for-ios-app-is-afoot-beta-testers-wanted/ : Liquid Glass beta
- `S-TO3` [B] https://play-lh.googleusercontent.com/CSaRdyi317XcdRBr2AH9iKKl5n-qn7e-kczq4ps4uJtpxaHyncTDdZznrfdUyWodF3qxbiLBXTrMUSRkbe1r6w=h600 : Monthly overview (2017 sample data)
- `S-MM1` [B] https://play-lh.googleusercontent.com/dlJhah0lWR8cArzZ6OlUwQNq0WbOo6QJ9QiOSec5Fq_czyXlkYn1Gv0hDDzoYo-jZ2av8DDoS4jTOLbpzo9oew=h600 : Calendar cells (2020-era art); home https://play-lh.googleusercontent.com/a7NsKwnQFWsESmKWeVdBneG5BzpVQ3q4CaMQanvkdqNNubnQQ2AIDBlsfQr4i8dUAwI7gsRIxcCa3v8xdSRUGzY=h600
- `S-MM2` [A] https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_US : Play listing; Android 4.12 widgets note https://help.realbyteapps.com/hc/en-us/articles/58999568169497-Android-4-12-x-Update-June-2026
- `S-MM3` [D] https://apps.apple.com/us/app/money-manager-expense-budget/id560481810?see-all=reviews&platform=iphone : App Store reviews
- `S-MF1` [C] https://screensdesign.com/showcase/monefy-money-tracker : Monefy flow frames (e.g. https://media.screensdesign.com/avs-pp-640/3be26cb976b142cca182ab17f79346a2.webp)
- `S-MF2` [A] https://www.monefy.com/guide/learn-how-to-budget : "under 10 seconds" (vendor)

**Open-source and indie**
- `S-CA1` [A] https://cashewapp.web.app/faq.html : Cashew FAQ (home customisation, add flow, pinned entries)
- `S-CA3` [A] https://raw.githubusercontent.com/jameskokoska/Cashew/main/budget/lib/widgets/pieChart.dart : Donut code
- `S-CA4` [A] https://raw.githubusercontent.com/jameskokoska/Cashew/main/budget/lib/widgets/countNumber.dart : Count-up code
- `S-CA5` [A] https://github.com/jameskokoska/Cashew/blob/main/budget/lib/widgets/budgetContainer.dart : Budget bar code
- `S-CA6` [A] https://raw.githubusercontent.com/jameskokoska/Cashew/main/budget/lib/pages/addTransactionPage.dart : Add page, title suggestion; haptics in selectAmount.dart
- `S-CA7` [B] https://cashewapp.web.app/assets/screenshots/home-promotion-light.jpg : Home screenshot
- `S-CA8` [B] https://cashewapp.web.app/assets/screenshots/budget-promotion-light.jpg : Budget screenshot
- `S-CA9` [A] https://raw.githubusercontent.com/jameskokoska/Cashew/main/budget/assets/translations/generated/en.json : Strings; onboarding page https://github.com/jameskokoska/Cashew/tree/main/budget/lib/pages
- `S-CA10` [D] https://apps.apple.com/my/app/cashew-expense-budget-tracker/id6463662930?see-all=reviews : App Store reviews
- `S-CA11` [C] https://tech.yahoo.com/apps/articles/found-best-open-source-expense-203813649.html : Android Police via Yahoo (2025-07-04)
- `S-CA12` [C] https://codewithandrea.com/videos/code-review-cashew-app/ : Code review video (2025-06-09)
- `S-BC1` [A] https://www.bluecoinsapp.com/changelog/ : Changelog (also /news/)
- `S-BC2` [B] https://play-lh.googleusercontent.com/Gl4NvIeLO33E2NIoRVg1YHw1gDUgFOiNl1YsL7M7guvXeaPlKA6m6s3N3jVyHQvlPgKanho2SPjL2CuA9Tzwnw=w1052-h592 : Main and budget screens
- `S-BC3` [A] https://play.google.com/store/apps/details?id=com.rammigsoftware.bluecoins&hl=en_US : Play listing and reviews
- `S-IV1` [A] https://github.com/Ivy-Apps/ivy-wallet : Repo; Play https://play.google.com/store/apps/details?id=com.ivy.wallet&hl=en_US
- `S-IV2` [B] https://user-images.githubusercontent.com/5564499/189540998-4d6cdcd3-ab4d-40f7-85d4-c82fe8a017d1.png : Home screenshot
- `S-FO1` [A] https://fold.money/ : Fold site copy
- `S-FO2` [C] https://60fps.design/shots/fold-cash-flow-graph-interaction : Cash Flow scrub description (frames not seen)
- `S-FO3` [C] https://60fps.design/shots/fold-slide-to-connect-account-interaction : Slide to connect
- `S-FO4` [C] https://60fps.design/apps/fold : Fold shot list (titles only)
- `S-FO5` [D] https://apps.apple.com/in/app/fold-expense-tracker-budget/id1626994334?see-all=reviews : App Store IN reviews
- `S-FO-PRIV` [A] https://fold.money/blog/privacy-by-design : Fold privacy post
- `S-DI1` [A] https://apps.apple.com/us/app/dime-budget-expense-tracker/id1635280255 : Dime listing, version notes, reviews
- `S-WF1` [A] https://github.com/dreautall/waterfly-iii : Waterfly III (Firefly III client); Financisto https://github.com/dsolonenko/financisto

**Shared, envelope, couples, business, small apps**
- `S-SW1` [A] https://blog.splitwise.com/2021/04/05/splitwise-for-android-v5-0/ : Android 5.0 design rationale
- `S-SW2` [A] https://blog.splitwise.com/2026/05/18/dark-mode-announcement/ : Dark mode toggle
- `S-SW3` [B] https://play-lh.googleusercontent.com/hGTdx0Nk41UaCgJvgZm5JIr011sdl_wb3gd-wT9TZyAHi4Jliz-c-RXa3AB75K3oR3jlns9dW8zOpA0jWJXqAA=w1052-h592 : Home screenshot
- `S-SW4` [B] https://tetras-ltd.com/images/blog/splitwise-pro-interstitial-2026-08.webp : Pro interstitial with countdown
- `S-SW5` [D] https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews&platform=iphone : App Store reviews
- `S-GB1` [A] https://play.google.com/store/apps/details?id=com.dayspringtech.envelopes&hl=en_US : Goodbudget Play listing
- `S-GB2` [A] https://goodbudget.com/blog/2026/04/stay-ahead-of-the-curve-with-running-balances-on-iphone/ : Running balances (image https://goodbudget.com/wp-content/uploads/2026/03/19115612/ios_running_balances_larger-1024x755.png)
- `S-GB3` [B] https://play-lh.googleusercontent.com/Qm18VYIbNcAxXy7Vq6xGQ-JMX0HaplbTQl0vcDeFqzt9EydQwAQJu0u4nG-czA2KpMjvea_kWz9tqUAM0pLcjw=w1052-h592 : Envelope list with tick
- `S-GB4` [B] https://play-lh.googleusercontent.com/Iof4Lz7yo0m7ZzQrCmRU83Lb9nEJloG6_9whIsq35D60uFWpX2DcuTAYKZYhdpnGYrywDOWrpNlEgs7iRKtq=w1052-h592 : Add transaction form
- `S-GB5` [C] https://spendalyst.com/blog/goodbudget-review : "decade ago" (search snippet, page not opened)
- `S-EL` [C] https://www.elitedaily.com/lifestyle/budgeting-apps-for-couples-reviews : Couples apps review (2024-07-21)
- `S-BU1` [A] https://apps.apple.com/us/app/buddy-budget-planner-app/id936422955 : Buddy listing and version history
- `S-BU2` [C] https://screensdesign.com/showcase/buddy-budget-planner-app : Buddy flow showcase
- `S-BU3` [B] https://media.screensdesign.com/avs-pp-640/89765b848b864767bfb0d54577b8d573.webp : Onboarding frame (other frames on the showcase page)
- `S-BU4` [C] https://www.elitedaily.com/lifestyle/budgeting-apps-for-couples-reviews : Daily-budget notification description (same page as S-EL)
- `S-BU5` [D] https://apps.apple.com/us/app/buddy-budget-planner-app/id936422955?see-all=reviews&platform=iphone : App Store reviews
- `S-EM1` [A] https://apps.apple.com/us/app/emma-budget-planner-tracker/id1270062373 : Emma listing
- `S-EM2` [B] https://play-lh.googleusercontent.com/fIf_7vraOv7QGr5fSGNRTQhJSzZsYZCldke7LJIOwaa2qBLcpjt9YlScbowHpUXJvucVcuwvZUrpQGE9R190=w1052-h592 : Budgeting screen
- `S-EM3` [B] https://play-lh.googleusercontent.com/hs7nQC69riSQC7b3fZeaBtLmgrulYOHO5jWX7OxJ1GjBH_NPQv6KN6QOAMJqKNjUjE1dF0Mys0aNHMNNV8RZbYw=w1052-h592 : Subscription card; net-worth chart https://play-lh.googleusercontent.com/dSfJO_hmbrtUsRhdzwXux9qe8tBrEFurWXKvwAQLICg37r1cy_4pGp9OMw1M3cmGiYpO9ZfyjhFUQClsT39BfA=w1052-h592
- `S-EM4` [C] https://moneytothemasses.com/banking/emma-review-is-it-the-best-budgeting-app : Emma review (2026-10-14)
- `S-MC1` [A] https://apps.apple.com/fr/app/mooncard-notes-de-frais/id1589208393 : Mooncard listing, version notes, reviews (French; translations mine)
- `S-MC2` [B] https://play-lh.googleusercontent.com/ox58IDRi1sfwIfzjJ3Y-3NEUAzDjJT0-v4_bLKi2QppCCLTXADDJAoBJ1_0dIJVowHIS5q1RM79NFPRtKDbCHw=w1052-h592 : Home screenshot
- `S-FI1` [A] https://ratayfina.com/en/ : Rata y Fina ("Fina") site
- `S-FI2` [A] https://apps.apple.com/us/app/rata-y-fina-expense-tracker/id6787686922 : Listing (choose your hero, tone switch)
- `S-FI3` [B] https://ratayfina.com/img/en-inicio.webp : Home image (file name as cited in the worker note; directory inferred from the sibling images)
- `S-FI4` [B] https://ratayfina.com/img/en-apuntar.webp : NL entry and confirm sheet

**Neobanks**
- `S-MZ1` [A] https://monzo.com/blog/the-new-and-improved-home-screen : Home redesign, Edit layout
- `S-MZ2` [A] https://monzo.com/blog/how-we-measured-change-aversion-with-our-new-home-screen : Change aversion measurement
- `S-MZ3` [A] https://monzo.com/tone-of-voice : Tone of voice dial
- `S-MZ4` [C] https://www.theguardian.com/money/2026/mar/07/monzo-customer-language-year-in-monzo-review : Year in Monzo wording
- `S-MZ5` [D] https://community.monzo.com/t/option-to-show-category-icon-instead-of-retailer-logo-in-transactions/108650 : Logo vs category icon thread (forum)
- `S-MZ6` [A] https://monzo.com/help/budgeting-overdrafts-savings/trends-spending-and-balance-web : Trends and Left to spend
- `S-MZ7` [A] https://community.monzo.com/t/introducing-graphs-on-trends/125166 : Graphs on Trends (2021)
- `S-MZ8` [D] https://community.monzo.com/t/retiring-the-summary-feature/186087 : Summary retired (forum, Nov 2025)
- `S-MZ9` [A] https://apps.apple.com/gb/app/monzo-bank-mobile-banking/id1052238659 : App Store GB listing and reviews
- `S-MZ-BR` [A] https://monzo.com/blog/weve-had-a-little-makeover : Brand makeover: coral used intentionally
- `S-MZ-LAB` [A] https://community.monzo.com/t/monzo-labs-hide-left-to-spend/84378 : Labs "Hide Left to Spend"
- `S-MZ-CONF` [D] https://community.monzo.com/t/early-payment-screen-i-would-like-to-turn-it-off/111715 : Confetti on early payment (forum)
- `S-RV1` [A] https://help.revolut.com/en-US/help/accounts/budget-and-analytics/how-can-i-see-my-spending-and-income-analytics/ : Analytics help
- `S-RV2` [C] https://medium.com/design-bootcamp/one-big-number-how-fintech-apps-are-rebuilding-the-home-screen-around-your-balance-41b2639768ff : Home screens built around one number (2026-10-14)
- `S-RV3` [A] https://help.revolut.com/en-US/help/profile-and-plan/security-and-personal-data/why-is-my-balance-blurry/ : Hide balances
- `S-RV4` [D] https://apps.apple.com/gb/app/id932493382?see-all=reviews : App Store GB reviews
- `S-N261` [A] https://n26.com/en-at/blog/new-in-n26-more-control : Statistics, Wrap-Up, widget (2022, older than 2024)
- `S-N262` [D] https://apps.apple.com/de/app/id956857223?see-all=reviews : App Store DE reviews
- `S-ST1` [A] https://www.starlingbank.com/features/spending-insights : Spending Insights
- `S-ST2` [B] https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/ee/2b/ef/ee2bef94-5ee9-6b95-47ad-444e9407647c/1.jpg/392x696bb.jpg : Store screenshots (home, category bars)
- `S-ST3` [D] https://apps.apple.com/gb/app/id956806430?see-all=reviews : App Store GB reviews
- `S-WI1` [A] https://wise.com/gb/blog/cheap-easy-and-insightful-android-say-hello-to-insights : Insights (2020, older than 2024)
- `S-WI2` [A] https://wise.com/gb/blog/your-shortcut-for-adding-new-recipients : "Snap it. Send it." (2025-12-16)
- `S-WI3` [D] https://apps.apple.com/gb/app/id612261027?see-all=reviews : App Store GB reviews
- `S-UP1` [A] https://up.com.au/blog/the-evolutionary-design-of-up/ : Design post (2020, older than 2024): onboarding, pull-to-save, pools
- `S-UP2` [A] https://up.com.au/blog/saver-insights : Saver Insights (2026-10-21)
- `S-UP3` [A] https://up.com.au/blog/custom-spending-insights : Custom insights (2022)
- `S-UP4` [B] https://is1-ssl.mzstatic.com/image/thumb/PurpleSource116/v4/cd/56/1d/cd561da7-7b6e-9317-30f4-88aaafcc87e7/aeab177d-7c0c-476b-8b91-d9a465b03f91_FRAME_ONE.png/392x696bb.png : Activity feed
- `S-UP5` [D] https://apps.apple.com/au/app/id1350717115?see-all=reviews : App Store AU reviews
- `S-UP-REL` [A] https://up.com.au/blog/up-1-16-0-release-notes : Widgets and lock-screen widgets; also https://up.com.au/blog/shipped-in-the-night (locked savers)
- `S-BQ1` [A] https://www.bunq.com/benefits/budgeting : Budgeting (help page unreadable; 80/95/100% from a search snippet)
- `S-BQ2` [D] https://apps.apple.com/nl/app/id1021178150?see-all=reviews : App Store NL reviews
- `S-CH1` [D] https://apps.apple.com/us/app/id836215269?see-all=reviews : Chime App Store reviews
- `S-CL1` [C] https://tme.net/blog/cleo-budget-assistant-app/ : Cleo roast mode opt-in (search snippet)

**Platform spend views and Indian UPI apps**
- `S-AC1` [A] https://support.apple.com/en-us/102329 : Apple Card spending summary help
- `S-AC2` [B] https://cdsassets.apple.com/live/7WUAS350/images/apple-pay/ios-27-iphone-17-pro-wallet-apple-card-balance-monthly-spending.png : Monthly spending screen
- `S-AC3` [B] https://www.apple.com/newsroom/images/product/apple-card/standard/Apple-Card-weekly-spending-screen-03252019_inline.jpg.large.jpg : Weekly spending screen (2019)
- `S-AC4` [C] https://9to5mac.com/2026/06/22/apple-wallet-in-ios-27-adds-new-feature-for-tracking-your-money/ : Wallet Insights
- `S-AC5` [C] https://9to5mac.com/2026/09/15/apple-card-has-three-new-and-improved-features-in-ios-27/ : Recurring and other iOS 27 changes
- `S-AC6` [A] https://www.apple.com/apple-card/features/ : Features page and privacy lines
- `S-AC7` [C] https://lodefinance.com/journal/apple-wallet-insights-ios-27 : "It reports; it doesn't plan"
- `S-GP1` [A] https://support.google.com/pay/india/answer/17034014?hl=en : Ask Google Pay
- `S-PT1` [A] https://paytm.com/blog/payments/paytm-introduces-automatic-spend-categorization-and-monthly-spend-summary-for-smarter-expense-tracking/ : Paytm Spend Summary
- `S-PP1` [C] https://moneytools.in/blog/how-to-see-monthly-expenses-in-phonepe : PhonePe lacks monthly totals (third-party, mild bias)
- `S-CR1` [A] https://web.archive.org/web/20231003015127/https://cred.club/design : CRED design page (Wayback; live URL 404)
- `S-CR2` [A] https://github.com/CRED-CLUB/neopop-flutter : NeoPop Flutter library (defaults and README); also https://github.com/CRED-CLUB/neopop-android and https://github.com/CRED-CLUB/neopop-ios
- `S-CR3` [C] https://uxplanet.org/thoughts-on-creds-ui-revamp-apr-2022-6d2b4dcfcfc6 : CRED revamp critique (2022, older than 2024)
- `S-CR4` [D] https://apps.apple.com/in/app/cred-credit-cards-bills-upi/id1428580080?see-all=reviews : App Store IN reviews

**Awards, streaks, exemplars, charts, typography**
- `S-AD24` [A] https://developer.apple.com/design/awards/2024/ : Apple Design Awards 2024 (Gentler Streak; Copilot finalist)
- `S-AD25` [A] https://developer.apple.com/design/awards/2025/ : Apple Design Awards 2025 (Denim, Opal); 2026 winners via https://www.apple.com/newsroom/2026/06/apple-reveals-winners-of-the-2026-apple-design-awards/
- `S-APPLEPAUSE` [A] https://support.apple.com/en-in/guide/iphone/iph9a08e004e/ios : Pause Activity rings (up to 90 days)
- `S-DUO` [A] https://blog.duolingo.com/how-streaks-keep-duolingo-learners-committed-to-their-language-goals/ : Duolingo streak experiments (2017)
- `S-GUARD` [C] https://www.theguardian.com/lifeandstyle/article/2024/sep/08/streak-daily-ritual-can-enrich-your-life-or-become-unhealthy-obsession : Streak anxiety (2024-09-08)
- `S-SMASH` [C] https://www.smashingmagazine.com/2026/02/designing-streak-system-ux-psychology/ : Streak system UX (2026-02-18)
- `S-MOBBINS` [C] https://mobbin.com/blog/streaks-are-about-losing-a-study-of-859-streak-designs : 859 streak designs (2026)
- `S-TS1` [A] https://toss.tech/article/8-writing-principles-of-toss : Toss 8 writing principles (Korean; translation mine)
- `S-TS4` [A] https://developers-apps-in-toss.toss.im/landing-page/landing-page-en/design/consumer-ux-guide : Apps in Toss consumer UX guide; principles https://toss.im/tossfeed/article/tossproductprinciples ; design system https://toss.tech/article/toss-design-system
- `S-TS-FONT` [A] https://en.sandoll.co.kr/Story/?bmode=view&idx=19492476 : Toss Product Sans story (fixed-width numerals)
- `S-WISE-SANS` [A] https://medium.com/transferwise-design/wise-sans-refined-expressive-by-default-51b019b700af : Wise Sans
- `S-CASH` [C] https://www.creativebloq.com/design/branding/cash-apps-new-brand-guidelines-make-style-guides-fun : Cash Sans
- `S-RHF` [C] https://the-brandidentity.com/project/collins-robinhood-combine-make-investing-accessible-affordable-engaging : Robinhood typefaces
- `S-CRED-ID` [C] https://resources.indiefolio.com/how-the-design-team-at-cred-is-pushing-boundaries/ : CRED type and numbers
- `S-RHT` [A] https://github.com/robinhood/ticker : Robinhood Ticker (odometer)
- `S-RHT2` [C] https://goodux.appcues.com/blog/robinhood-haptic-feature-announcement : Robinhood haptic reveal
- `S-M3E` [A] https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/ : Material 3 Expressive launch
- `S-M3API` [A] https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme : MotionScheme reference
- `S-HIGCH` [A] https://developer.apple.com/design/human-interface-guidelines/charts : HIG Charts (also .../charting-data)
- `S-WCAG` [A] https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html : WCAG 2.2 1.4.11 (use of colour: .../use-of-color.html)
- `S-OKI` [A] https://jfly.uni-koeln.de/color/ : Okabe-Ito colour-blind-safe design
- `S-NYU` [A] https://siegal.bio.nyu.edu/color-palette/ : Okabe-Ito hex values
- `S-TOL` [A] https://sronpersonalpages.nl/~pault/ : Paul Tol palettes
- `S-DW` [C] https://www.datawrapper.de/blog/improved-stacked-column-charts : Direct labels
- `S-DAR` [C] https://news.darden.virginia.edu/2024/08/22/heres-how-to-build-a-better-personal-budget : Budget study, 21.9% (abstract only)
- `S-TF1` [C] https://thefintechtimes.com/research-finds-budgeting-apps-increase-spending-by-up-to-a-third/ : Think Forward / Arizona study (press coverage; paper not read)
- `S-INDIA` [C] https://assets.nextleap.app/submissions/NL_googlepay-91c2bf66-272d-46f7-af26-945ba8c14747.pdf : NextLeap GPay study, via docs/research/ui/INDIA_APPS.md lesson 8 (small n)

**Onboarding, entry, trust, accessibility**
- `S-NN1` [A] https://www.nngroup.com/articles/mobile-tutorials/ : NN/g tutorials study (also https://www.nngroup.com/articles/mobile-app-onboarding/ and https://www.nngroup.com/articles/empty-state-interface-design/)
- `S-MOB1` [C] https://mobbin.com/blog/the-long-onboarding-a-study-of-1-460-flows : 1,460 onboarding flows
- `S-HIGON` [A] https://developer.apple.com/design/human-interface-guidelines/onboarding : HIG Onboarding (privacy: .../privacy)
- `S-GIN` [A] https://services.google.com/fh/files/misc/app-website-localization-indian-users.pdf : Google/Kantar playbook for Indian users
- `S-SMN` [C] https://medium.com/smartnews-inc/optimizing-push-frequency-for-new-users-5db5e04d76df : SmartNews push-frequency engineering blog
- `S-ANDQS` [A] https://developer.android.com/develop/ui/views/quicksettings-tiles : Quick Settings tiles; requestAddTileService https://developer.android.com/reference/android/app/StatusBarManager
- `S-WKC` [A] https://developer.apple.com/documentation/widgetkit/creating-controls-to-perform-actions-across-the-system : Controls and Lock Screen widget families
- `S-NOTIF` [A] https://developer.android.com/develop/ui/views/notifications/build-notification : Direct reply, lock-screen visibility; Apple https://developer.apple.com/documentation/usernotifications/untextinputnotificationaction
- `S-ANDSPEECH` [A] https://developer.android.com/reference/android/speech/SpeechRecognizer : On-device speech recognizer (API 31)
- `S-IOSSPEECH` [A] https://developer.apple.com/documentation/speech/sfspeechrecognitionrequest/requiresondevicerecognition : iOS on-device recognition
- `S-FM` [A] https://developer.apple.com/documentation/foundationmodels : Apple Foundation Models
- `S-GEMINI` [A] https://developers.google.com/ml-kit/genai : ML Kit GenAI (Gemini Nano): device list, foreground only
- `S-MK1` [A] https://merakharcha.in/ : Mera Kharcha: voice entry, needs Premium and internet
- `S-NB1` [A] https://apps.apple.com/us/app/nobuy-no-spend-day-tracker/id6760716822 : NoBuy end-of-day widget (snippet)
- `S-ZERO` [C] https://apps.apple.com/us/app/zero-offline-expense-tracker/id6759560225 : Offline tracker listing wording
- `S-APPLEPR` [A] https://support.apple.com/en-us/102188 : App Privacy Report
- `S-SIG` [A] https://support.signal.org/hc/en-us/articles/10074659364122-Backups-and-Device-Transfers-on-Signal : Signal backups
- `S-SIGNAL` [C] https://freedom.press/digisec/blog/signals-new-secure-backup-feature/ : Signal secure backup
- `S-PLAYDISC` [A] https://support.google.com/googleplay/android-developer/answer/10144311 : Play prominent disclosure policy
- `S-DPDP` [C] https://www.seclore.com/fundamentals/dpdp-rules-2025-compliance-guide/ : DPDP Rules 2025 (not legal advice)
- `S-AND15` [A] https://developer.android.com/about/versions/15/behavior-changes-all : Android 15 changes: content sensitivity (search citation, page not opened)
- `S-AND16` [A] https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability : Android 16 orientation/resizability (search citation)
- `S-UIMODE` [A] https://developer.android.com/reference/kotlin/android/app/UiModeManager : UiModeManager.getContrast (search citation)
- `S-ITMS` [C] https://blog.xojo.com/2016/09/06/ipad-apps-and-itms-90474-error/ : iPad orientations and ITMS-90474 (2016; verify against current rules)
- `S-PLAID` [A] https://plaid.com/docs/api/products/enrich : Plaid Enrich logo_url (needs network)
- `S-SIMPLE` [A] https://github.com/simple-icons/simple-icons/blob/develop/DISCLAIMER.md : Simple Icons disclaimer
- `S-DMLP` [C] https://www.dmlp.org/legal-guide/using-trademarks-others : Trademark use guide
- `S-OFL` [A] https://raw.githubusercontent.com/rsms/inter/master/LICENSE.txt : Inter licence (OFL-1.1); Plus Jakarta Sans https://github.com/tokotype/PlusJakartaSans/blob/master/OFL.txt ; Manrope https://github.com/google/fonts/blob/main/ofl/manrope/OFL.txt ; DM Sans https://github.com/googlefonts/dm-fonts/blob/main/Sans/OFL.txt ; Noto Sans Devanagari https://github.com/notofonts/devanagari/blob/main/OFL.txt

**Compose Multiplatform and platform haptics/motion**
- `S-CP-CL` [A] https://github.com/JetBrains/compose-multiplatform/blob/master/CHANGELOG.md : CMP changelog (1.12.1, 1.12.2, 1.13 notes)
- `S-CMPSRC` [A] https://github.com/JetBrains/compose-multiplatform-core/tree/v1.12.1 : CMP source at the tag of our version (haptic types, MotionScheme, material3 tokens)
- `S-CMPRES` [A] https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html : Resources and fonts
- `S-CMPA11Y` [A] https://kotlinlang.org/docs/multiplatform/compose-ios-accessibility.html : iOS accessibility in CMP
- `S-CMPCOMPARE` [A] https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html : Targets (no watchOS)
- `S-KNCH` [A] https://github.com/JetBrains/kotlin/blob/master/kotlin-native/platformLibs/src/platform/ios/CoreHaptics.def : CoreHaptics binding ships with Kotlin/Native
- `S-ANDHAP` [A] https://developer.android.com/develop/ui/views/haptics/haptics-principles : Android haptic principles (custom effects: .../custom-haptic-effects)
- `S-HIGHAP` [A] https://developer.apple.com/design/human-interface-guidelines/playing-haptics : HIG Playing haptics
- `S-HIGMOT` [A] https://developer.apple.com/design/human-interface-guidelines/motion : HIG Motion
- `S-ANDANIM` [A] https://developer.android.com/develop/ui/compose/animation/customize : Compose animation: springs and interruptions

**Revolut image**
- `S-RV5` [B] https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/7e/b9/74/7eb9744e-01ba-5426-3f28-6f6c7f653645/Screen_2_1206x2622.jpg/320x480bb.jpg : Revolut store screenshot (home, balance and actions)

---

## Appendix B. Unverified items, each with a 5-minute check (the lead can run these without a design decision)

1. **Resolved material3 version:** `./gradlew :shared:dependencies --configuration commonMainImplementationDependenciesMetadata | grep -i material3` should show `1.12.0-alpha03`.
2. **`tnum` on iOS:** put `Text("1,23,456.78", style = TextStyle(fontFeatureSettings = "tnum"))` over `"1,11,111.11"` in an iPhone preview, with the system font and with a bundled font that has `tnum`; compare digit widths.
3. **iOS Dynamic Type live:** simulator, Settings > Accessibility > Display & Text Size > Larger Text > Larger Accessibility Sizes, drag to AX5 with the app open; the Home headline and a list row must grow and not clip; then toggle Bold Text (also with a bundled face).
4. **Does a bundled font land in our static XCFramework?** Add one font, build the iOS app, check that it renders (CMP embeds resources in XCFrameworks since 1.8.2 [A]).
5. **Interactive iOS swipe-back for the hand-rolled `ScreenHost`:** add `NavigationBackHandler`, print `navState.transitionState`, drag from the left edge; progress events mean yes, only on release means binary.
6. **Backdrop blur on iOS:** record content with `rememberGraphicsLayer()`, draw twice, apply `BlurEffect(24f, 24f)` to the second; look for the blurred copy. Also `MeshGradientPainter` and skiko `RuntimeEffect` compiled in `iosMain`.
7. **Does `NavigationBackHandler` need an extra Gradle artifact** (`navigationevent-compose`)? Try the import.
8. **`120+45` in the number pad:** type it (expected: not evaluated; grep found no evaluator).
9. **On-device speech:** iOS `SFSpeechRecognizer(locale: hi-IN).supportsOnDeviceRecognition` on a real iPhone; Android `SpeechRecognizer.isOnDeviceRecognitionAvailable` on a low-end test phone; a compile test of `import platform.Speech.SFSpeechRecognizer` in `iosMain`.
10. **A Control Center button running `AddExpenseIntent` with a required String parameter:** does it prompt? If not, use an `OpenIntent`.
11. **`GraphicsLayer.toImageBitmap()` on iOS in CMP 1.12.1** (needed for any "share a recap image").
12. **Android built-in intent for "add expense"** in the Assistant BII list.
13. **Locked-phone view of the weekly recap notification** (expected: amounts visible; the code applies the private version only to bills).
14. **iPad:** validate the archive for orientation rules (`UISupportedInterfaceOrientations` has 3 of 4 and there is no `UIRequiresFullScreen`), and test a Bluetooth keyboard with Cmd shortcuts reaching `onKeyEvent`.
15. **iOS 26 widget previews** in tinted and clear modes for the Today widget; check how the purple capsule renders.
16. **Emoji tofu:** a newest-emoji category icon on an Android 8 emulator.
17. **Android 15 `setContentSensitivity`:** one line in `MainActivity`; verify the screen-recording redaction on an API 35 device.
18. **iOS app switcher snapshot** when the app lock is on: confirm it is blank.
19. **The first-run screenshot** the researchers described (0.00 card + Welcome + dense privacy paragraph) is `~/Documents/personal/expenses_builds/ui_research/audit/home_empty_welcome_w393f10l.png`, taken by another worker; re-look before redesigning.

**What I did not verify myself:** all competitor facts (I relied on the researchers' reads and spot-checked the file for internal consistency), the CMP source facts (taken from the researcher's reading of the tag), and any behaviour on a real device. I did re-run the palette-contrast and the Motion arithmetic, and I re-read `Notifier.kt`, `HomeStyle.kt`, `AppRoot.kt` and grep results for `tnum`, `drawWithCache` and `TextMeasurer`.
