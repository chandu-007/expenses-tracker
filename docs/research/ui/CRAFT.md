# UI craft research: what to add to a motion layer that already exists (deltas, not a re-proposal)

Scope: Expenses (Kotlin Multiplatform, Compose Multiplatform 1.12.1, Material 3 via `compose.material3`, Kotlin 2.4.20, lifecycle 2.11.0, Glance 1.2.0 on Android; iPhone-only, iOS 17+, shared UI). Everything is on-device, no INTERNET permission, no new libraries, no new permissions. This document is research only: no product code was changed, nothing was built or run, no emulator or Xcode was used.

It does **not** repeat `docs/research/*.md` (global-budgeting, india, manual-trackers, reviews, COMPETITOR_ANALYSIS, OUR_FEATURES, shared, tech), `docs/MARKET_RESEARCH.md`, `docs/HOME_OPTIONS.md` or `docs/PERFORMANCE.md`. It builds on them and on `docs/PHASE2_PROGRESS.md` (Slices 47, 48, 52, 67, 88, 103, 111, 112).

Evidence labels used on every claim:
- **[R]** read in this repo, this session (file named). The code is ground truth.
- **[A]** official documentation or primary source read this session (URL given).
- **[B]** source code of a library or a primary open-source repo read this session.
- **[C]** secondary source (blog, search summary, case study): useful, not authoritative.
- **[I]** my inference or calculation. Not verified on a device. Treat as a hypothesis with a stated way to check it.

---

## 0. The short version

The app is further along than most competitors on *structure* (one motion vocabulary, one setting for speed, Reduce-motion honoured, a container transform, a rolling total, a scrubbable line, a wallet stack, a glass tab bar). What is missing is mostly **finish**: the small things people feel without naming them.

1. Numbers do not use tabular figures anywhere in the app (no `tnum` in the repo [R]), so every amount can jitter sideways while it rolls and right-aligned amounts do not line up digit for digit.
2. The default motion speed ("Relaxed", 1.8x) makes the springs slower than Material's slowest spatial spring and puts the tweens in Material's Medium-to-ExtraLong range (QUICK 216, STANDARD 396, EMPHASIZED 576, GLIDE 828 ms), including the Add form, the most frequent flow in the app [R][B][I computed in 3.1]. It is a deliberate owner choice (Slice 67); the proposal is to protect feedback and the Add form, not to reverse it.
3. Haptics go through three different code paths (the `Feel` enum, direct `LocalHapticFeedback` calls in capture screens, `pressScale`), `Undo` is mapped to the "reject/failure" pattern, "limit crossed" to the "long-press" pattern, there is no in-app off switch, and the iPhone has no "warning" pattern [R][A].
4. There is no interactive (finger-following) back gesture inside the app and no measurement of fluidity on an iPhone at all [R]. All numbers in `PERFORMANCE.md` are from an Android emulator.
5. The same category has three different colours (name-hash avatar tint, chart palette, optional user colour) and 23 default categories collide into 8 avatar tints [R].

Ranked list of twenty upgrades is in section 2. Detail by area in section 3 onward. Design-token deltas in section 5. Implementation order in section 6. Risks in section 7. What I could not verify in section 8. Sources in section 9.

---

## 1. Already in the app (inventory, from the code)

All paths under `shared/src/commonMain/kotlin/app/expenses/` unless stated. "Measured" means a number is written in a repo doc; everything else was only read.

| What | Where | What it does today | Measured / verified |
|---|---|---|---|
| Motion vocabulary | `ui/Motion.kt` | `Motion.scale` (default 1.8) times base 120/220/320/460 ms = QUICK 216, STANDARD 396, EMPHASIZED 576, GLIDE 828 ms. `Motion.spring(damping, stiffness)` divides stiffness by scale squared. `EaseOut` = (0.05, 0.7, 0.1, 1) and `EaseIn` = (0.3, 0, 0.8, 0.15), which are exactly Material 3's emphasized-decelerate and emphasized-accelerate curves [B: `MotionTokens.kt`]. `Motion.tween(reduce, ...)` becomes `tween(0)` under Reduce motion | `MotionUiTest` (17 device tests in Slice 67, emulator API 26/34). No phone |
| Motion setting | `ui/AppearanceScreen.kt`, `AppRoot.kt:307-309` | Appearance > Motion: Off / Quick (1x) / Relaxed (1.8x, default) / Slow (2.8x). `LocalReduceMotion` = setting OR the phone's animations-off (Android `ANIMATOR_DURATION_SCALE == 0`, iOS `UIAccessibilityIsReduceMotionEnabled`), both read once per composition (`remember`) | Test `reduceMotionIsASwitch...`, `theSpeedChoiceIsRemembered...` |
| Screen transitions | `ui/ScreenHost.kt` | One keyed `AnimatedContent` inside a `SharedTransitionLayout`. Tab to tab: crossfade (STANDARD). Add form: slides up from the bottom with a 0.94 scale-back of Home (GLIDE). Edit form: container transform from the tapped row. Back to Home: scale 1.04 to 1 with fade. Everything else: scale 0.94 to 1 plus fade. A leaving screen is hidden from accessibility and its Back handler stands down (`LocalLeavingScreen`) | `MotionUiTest.theAddFormRisesIn...`, `aScreenThatIsAnimatingOut...` |
| Container transform | `ui/Motion.kt` `Modifier.sharedContainer(key)`; used in `ui/Screens.kt:~539` per Home row (`"entry:${row.id}"`) | `sharedBounds`, `ResizeMode.RemeasureToBounds`, tween EMPHASIZED. It is a `composed {}` modifier on **every** Home row | `PERFORMANCE.md` suggestion 2 already flags the per-row cost; not measured |
| Glass tab bar | `ui/HomeStyle.kt` `HomeBar` | Home, Search, Add, Reports, More. A tinted translucent pill (gradient, rim, 2 dp highlight) glides under the current tab on a spring (0.72 damping, 260 stiffness over scale squared) and stretches up to 30% while travelling. `TabMemory` makes it start where it last rested. Surface is the theme surface at 90% alpha plus a hairline: **not** blur, and the bar sits **below** the content, not over it. Labels shrink with `TextAutoSize`; font scale capped at 1.3 | Slice 67 text: "not true liquid glass". `MotionUiTest.theBarShowsEveryTab...` |
| Press feedback | `ui/HomeStyle.kt` `Modifier.pressScale` | Scale 0.95 on a spring (0.6, 600), ripple, optional haptic. Used by tabs and tiles | Not measured |
| Haptics | `ui/Motion.kt` enum `Feel {SAVE, UNDO, LIMIT, PAID, TICK}`; `androidMain/.../PlatformHooks.android.kt`; `iosMain/.../PlatformHooks.ios.kt` | Android through `View.performHapticFeedback` (no VIBRATE permission): SAVE/PAID = CONFIRM (API 30+, else VIRTUAL_KEY), UNDO = REJECT (30+, else KEYBOARD_TAP), LIMIT = LONG_PRESS, TICK = CLOCK_TICK. iOS through Compose's `LocalHapticFeedback`: Confirm, Reject, LongPress, SegmentTick. Used by: save, undo bar, bill paid, limit crossed, 14-day scrub, `MessageHost`. Separately, `CaptureScreens.kt` calls `LocalHapticFeedback` (Confirm) directly 8 times and `HomeBar`/tiles pass `SegmentTick` to `pressScale` | Never felt on a phone (Slice 52, 67 say so) |
| Rolling total | `ui/HomeVisuals.kt` `RollingAmount` | Animates a `Float` from the old to the new minor-unit value (tween EMPHASIZED), re-formats the **whole** string each frame, no roll on first composition, none under Reduce motion or Hide amounts. Semantics always carry the final text. Used on Home's month figure (`Screens.kt:~400`) | `MotionUiTest.theMonthTotalReadsTheNewFigureAtOnce...` |
| 14-day line | `ui/HomeVisuals.kt` `HomeSpark` | Smooth cubic line plus 0.32-alpha area gradient, draws in once from the left (saved with `rememberSaveable` so it does not replay on scroll), dot on today, drag shows a guide, dot and a pill with that day's amount, a `TICK` haptic each time the day changes. Hidden when there is nothing to draw. Content description is only "Spending over the last 14 days" | `MotionUiTest.draggingAFingerAlongTheLine...` |
| Budget bar | `ui/HomeVisuals.kt` `AnimatedBudgetBar`, `LimitCrossedFeel` | Fills to the new value and shifts OK/near/over colour (primary / amber `0xFFE08A00` / error). Fires `Feel.LIMIT` when `over` flips to true after first composition | Unit and device tests for the state, none for feel |
| Reports charts | `ui/ReportCharts.kt` | `BreakdownRing`: top 5 slices + "Others", 22 dp butt-cap strokes with a 2 degree gap, 700 ms sweep-in, tap a slice or legend row to pull it out 8 dp and dim the rest, legend shows names and %, content description lists every slice. `TrendChart`: bars built from `Box`es (not Canvas), 500 ms tween (**not** scaled by Motion), tap a bar to open that period | `PERFORMANCE.md`: nothing specific |
| Calendar | `ui/CalendarScreen.kt` | Month grid; each cell is tinted `primary * level * 0.18` (a heat shade), selection colour animates | Device tests |
| Wallet stack | `ui/AccountTiles.kt` `WalletStack` | Cards overlap showing a 48 dp edge; tap spreads them on a spring (0.78, 300 over scale squared); "Stack them" and Back collapse. Shadow 8 dp on all but the first | `WalletStack` test |
| Lists | `ui/Screens.kt` `SwipeableRow`, `ui/EntryListStyle.kt`, `ui/HomeStyle.kt` `DayHeader` | Day-grouped rounded cards (22 dp corners); stable keys (`seq`) and `contentType`; `animateItem()`; swipe from the right to delete (plain red "Delete" text, no threshold haptic), Undo bar; long-press starts selection; a just-added row glows for 1100 ms (that `tween(1100)` is **not** scaled by Motion and is not skipped under Reduce motion [R]) | Emulator scroll with 100,000 entries: 0.3 to 0.6% janky frames, p99 12 to 14 ms (Slice 47, 67) |
| Surfaces | `ui/HomeStyle.kt` `heroBackground`, `HeroGlow`, `ui/AccountTiles.kt` | Hero: accent gradient (accent to 28% toward black or white), corner radial light, 1 dp rim. Two blurred lights (`Modifier.blur`) on Android 12+ and on iOS (`supportsBlur`). Everything else is opaque tonal | Slice 48: blur seen on an Android 17 image |
| Theme | `core/domain/.../Appearance.kt`, `AppRoot.kt:955 colorSchemeFor` | Six accents (violet, blue, teal, green, orange, red); `primaryFor` lightens the accent in dark mode until it reads 4.5:1 on `0x141218`; container colours are mixes. Baseline `lightColorScheme()` / `darkColorScheme()` underneath. No dynamic colour in the app (widgets do use it) [R: `WidgetUi.kt:55`]. No custom fonts, no `Typography(...)` override, no `tnum` | `AppearanceTest` |
| Money text | `core/domain/.../MoneyFormat.kt`, `WholeAmounts.kt`, `AmountWords.kt` | Indian grouping; `Money.compact()` gives ₹1.2K, ₹1.2L, ₹3.4Cr (cut, never rounded up); whole-rupee drawing with exact text kept for screen readers; amount in words on the Add form | Host tests |
| Empty and first run | `ui/EmptyStates.kt`, `FirstRun.kt`, `FirstWeekCard.kt`, `TourSpots.kt`, `GuidedSetup.kt` | Seven Canvas line-art pictures that fade and grow in; welcome card; first-week checklist; spotlight tour; guided setup. `HOME_OPTIONS.md` item 12: first-run order left unchanged on purpose, "Start here" checklist not built | Emulator and simulator |
| Home modules | `core/domain/.../HomeLayout.kt`, `ui/HomeLayoutScreen.kt` | Four movable, hideable sections (budget, bills, balances, quick entries); reorder with up/down text buttons | Host tests |
| Large screens | `core/domain/.../WindowLayout.kt`, `ui/WidePane.kt`, `AppRoot.kt` (`aboveHinge`, `MAX_CONTENT_WIDTH` 720 dp) | Two panes at 600 dp wide and 480 dp high; fold awareness through `androidx.window`; Ctrl+N / Ctrl+F; two-pane Search. No navigation rail (`grep NavigationRail` finds nothing) | Slices 103, 111, emulator |
| Widgets | `androidApp/.../widget/*`, `iosApp/Widget/*.swift`, `QuickAddTileService` | Five Glance widgets (Material You colours), a Quick Settings tile, one static launcher shortcut. iPhone: Today (small) and Quick add (medium); `supportedFamilies` is only systemSmall and systemMedium; App Intents "Add expense", "Capture bank message" exist in `Intents.swift` | Emulator, simulator |
| Platform setup that matters for feel | `AndroidManifest.xml:24`, `iosApp/project.yml:43,53`, `MainViewController.kt:38` | `android:enableOnBackInvokedCallback="true"` (system back-to-home preview works), targetSdk 36; iOS `CADisableMinimumFrameDurationOnPhone: true` (required for 120 Hz in CMP [A]), `TARGETED_DEVICE_FAMILY: "1"` (iPhone only) | n/a |

Not present at all [R]: tabular figures, odometer digit rolling, a skeleton, interactive predictive back, an in-app haptics switch, category icons (only the first letter), category colour consistency, a donut with rounded caps, chart accessibility beyond a description, true-black dark theme, high-contrast or Reduce-Transparency handling, Lock Screen widgets, Control Center control, dynamic launcher shortcuts, drag to reorder.

---

## 2. Gaps versus best in class, then the ranked top 20

### 2.1 Gaps (what a person feels, not what an engineer sees)

| # | Gap | Evidence | Reference apps that do it |
|---|---|---|---|
| G1 | Numbers are proportional-width; digits shift while rolling; rupee sign and paise are the same size as the figure | no `tnum` in repo [R] | Robinhood's Ticker rolls per digit with stable widths [B: github.com/robinhood/ticker]; Cash App and Apple Card use a big figure with a small currency sign [C, not verified here] |
| G2 | Default motion sits in the long Material tiers, and feedback-sized motion is scaled too | `Motion.kt` [R], tokens [B] | M3 puts taps at 50 to 200 ms and large transitions at 450 to 600 ms; fast spatial spring settles in about 0.24 s [B][I] |
| G3 | Haptic meaning is inconsistent and cannot be switched off | `PlatformHooks.*`, `CaptureScreens.kt` [R] | Apple HIG: use the documented meaning, be consistent, make haptics optional [A: playing-haptics] |
| G4 | No finger-following back gesture; iOS edge swipe is commit-only [I] | `PlatformBackHandler` is the plain `BackHandler` on both platforms [R] | Android 14+ predictive back and iOS interactive pop [A] |
| G5 | Swipe row has no icon, no threshold feedback, one action | `SwipeableRow` [R] | iOS Mail, Gmail, Monzo-style row actions [C] |
| G6 | Category identity is not stable or distinct | `CategoryAvatar` hashes the name into 8 pale tints; `reportColor` uses a different palette; `Category.colorArgb` optional [R] | Monzo, Revolut, Copilot use a fixed colour plus a glyph per category [C] |
| G7 | Charts are three separate implementations with three interaction models | `HomeSpark` scrubs, `BreakdownRing` taps, `TrendChart` taps and is not Canvas [R] | Robinhood scrub-everywhere; Apple Health charts: one selection model [C] |
| G8 | No instant first frame or skeleton; a spinner on cold open | `OpeningScreen` is a `CircularProgressIndicator` [R] | M3 skeleton loader pattern [A: m3.material.io transition patterns] |
| G9 | Glass is a pill on an opaque bar, no scroll-edge treatment, no Reduce-Transparency path | `HomeBar` [R] | iOS 26 tab bars float over content with scroll-edge blur; HIG says to use it sparingly and only on the navigation layer [A: HIG Materials] |
| G10 | No iPhone fluidity measurement; Android numbers are emulator-only | `PERFORMANCE.md` [R] | Apple's own Hitches tooling and MetricKit [A: URLs in section 14] |
| G11 | iPhone glanceable surface is thin: no Lock Screen families, no Control Center control | `iosApp/Widget` [R] | Widgets and controls docs [A] |
| G12 | Chart accessibility is a one-line description; Home line has no values for a screen reader | `HomeSpark` [R] | WCAG 1.1.1 and 1.4.1 [A] |
| G13 | Reduce motion means "instant" everywhere; the system setting is read once, not observed | `Motion.tween`, `PlatformHooks.*` [R] | Apple suggests replacing large movement with a fade [C/I] |
| G14 | Tablet or fold: the bar stays at the bottom, stretched | no rail [R] | M3 adaptive navigation: bar on compact, rail from 600 dp [A: developer.android.com build-adaptive-navigation] |

### 2.2 Ranked top 20 (impact first, then effort)

Effort: S = under 2 days, M = 3 to 6 days, L = more than a week. Impact is on the feeling of the app for one person using it daily. Parity says whether Android and iPhone get the same result from `commonMain`.

| Rank | Upgrade | Area | Effort | Impact | Parity |
|---|---|---|---|---|---|
| 1 | Tabular figures everywhere money is drawn, plus a "hero figure" style (small ₹ and paise, big digits, baseline aligned) | 4 | S | High | Same code; verify iOS glyph width (3.2) |
| 2 | Refine `Motion.kt`: an unscaled feedback tier (90 ms), M3 spatial/effects spring pairs, Add form capped at about 350 ms, Reduce motion becomes a short crossfade, system setting observed live | 1 | S to M | High | Same |
| 3 | One haptic surface: fold every direct call into `Feel`, fix meanings (Undo, Limit), add `THRESHOLD`, `STEP`, `WARN`, an in-app Haptics switch, iPhone `UINotificationFeedbackGenerator` through `expect/actual` | 2 | S to M | High | Android and iOS differ in vocabulary; table in 3.2 |
| 4 | Swipe row v2: icon and label that scale in, colour that follows the drag, haptic at the threshold, optional leading action | 6 | S | High | Same |
| 5 | Odometer digit roll in `RollingAmount` with no per-frame recomposition; roll Budget left, Safe to spend, tile balances and Reports "Spent" only when they change | 1, 4 | M | High | Same |
| 6 | One category identity: stable colour per category (not name hash), vector glyph in a tonal tile, merchant monogram fallback, one colour-blind-checked chart palette that works in dark | 3, 5 | M | High | Same |
| 7 | Interactive predictive back in `ScreenHost` (progress-driven scale and shift), iOS edge swipe through the same handler | 1 | M | High | Android 14+ and iOS; API check in 3.1 |
| 8 | Measure fluidity: debug-only frame-time logger on Android (`FrameMetrics`), `CADisplayLink` logger on iPhone, and a first iPhone baseline on the owner's phone | 12 | S to M | High (it unlocks every other item) | Different code, same report |
| 9 | One `ChartScrubber` and chart signature: round caps on the ring, Canvas bars with scrub, goal/pace line, shared haptic and semantics | 3 | M | High | Same |
| 10 | Budget bar as a bullet graph: a "pace" tick for where you should be today; ring variant for the "left today" tile | 3 | S | Medium to High | Same |
| 11 | Defer shared-container cost: attach `sharedBounds` only to rows that are visible or only to the tapped row | 12 | S | Medium (jank safety) | Same |
| 12 | Chart accessibility: spoken summaries, step-through actions, "show as table" | 11 | S | Medium (High for access) | Same; VoiceOver behaviour unverified |
| 13 | Instant first frame: draw the Home shell (month card frame, bar) at once; skeletons only where a wait can exceed a second | 12 | S to M | Medium | Same |
| 14 | Success micro-moments: drawn check for Save and Paid, "no-spend day" marks, "all reviewed" finish; no streak counter | 6, 7 | S | Medium | Same |
| 15 | Glass v2: scroll-edge scrim, bar floats over content with bottom inset, Reduce-Transparency and high-contrast fallback to opaque; real backdrop blur only behind a measured gate | 5 | M | Medium | iPhone benefits most; Android 12+ for blur |
| 16 | iPhone Lock Screen widgets (circular, rectangular, inline) and a Control Center "Add expense" control | 9 | M | High on iPhone | Swift only, not shared |
| 17 | Drag to reorder Home modules; two new modules from `HOME_OPTIONS.md` option C ("Where it went", "Coming up"), off by default | 8 | M | Medium | Same |
| 18 | Dynamic colour as a seventh accent on Android 12+ ("Match my wallpaper"); true-black OLED dark theme as a theme mode | 5, 11 | S | Medium | Android-only for dynamic colour |
| 19 | Navigation rail from 600 dp, hover and right-click on mouse, drag and drop of an entry | 10 | M | Medium (tablets, ChromeOS, DeX) | Android first |
| 20 | Microcopy pass (short, active, one idea per line) and dynamic launcher shortcuts for the quick entries | 7, 9 | S | Medium | Android shortcuts only |

Next five if time allows: stacked area for category over time (M to L, needs a by-day-by-category aggregate [I]), a hero transition from the Home month card into Reports (S to M), Wear (not feasible under the rules, 3.9), Android 16 Live Updates (not appropriate, 3.9), a custom numeric font subset (M, adds a resource artifact; see risks).

---

## 3. Detailed notes by area

### 3.1 Motion system

**Principle.** Motion explains cause and effect; feedback is near-instant; large movement is physical and can be interrupted. Apple: "Add motion purposefully", "avoid adding motion to UI interactions that occur frequently", "let people cancel motion" [A: https://developer.apple.com/design/human-interface-guidelines/motion]. NN/g: 0.1 s is the limit for something to feel instantaneous, 1 s keeps the flow of thought [A: https://www.nngroup.com/articles/response-times-3-important-limits/].

**What exists** is in section 1. The curves already equal Material's emphasized pair, so the vocabulary is sound. The refinements are about *tiers* and *defaults*.

**Delta 1: three tiers, one of them unscaled (refine `Motion.kt`).**
The Material 3 token values, read from source: durations Short1..4 = 50, 100, 150, 200 ms; Medium1..4 = 250, 300, 350, 400; Long1..4 = 450, 500, 550, 600; ExtraLong1..4 = 700 to 1000 [B: `androidx/compose/material3/tokens/MotionTokens.kt`]. Springs (damping, stiffness): default spatial 0.8 / 380, fast spatial 0.6 / 800, slow spatial 0.8 / 200; effects springs (colour, alpha) use damping 1.0 and stiffness 1600 / 3800 / 800 [B: `ExpressiveMotionTokens.kt`]. The M3 rule is that *spatial* springs may overshoot and *effects* springs never do [A: https://m3.material.io/styles/motion/overview/how-it-works].

Settling time of an under-damped spring is about 4 / (damping x sqrt(stiffness)) seconds (mass 1, 2% band) [I, standard second-order result]. Computed:

| Spring | Stiffness after scale | Settles in about |
|---|---|---|
| M3 fast spatial (0.6, 800) | 800 | 0.24 s |
| M3 default spatial (0.8, 380) | 380 | 0.26 s |
| M3 slow spatial (0.8, 200) | 200 | 0.35 s |
| `Motion.spring(0.8, 400)` at Relaxed 1.8x | 123 | 0.45 s |
| Wallet stack spring (0.78, 300) at 1.8x | 93 | 0.53 s |
| Tab glass spring (0.72, 260) at 1.8x | 80 | 0.62 s |

So "Relaxed" is slower than M3's *slowest* spatial spring, and the default tweens are STANDARD 396 ms (Medium4), EMPHASIZED 576 ms (Long4), GLIDE 828 ms (ExtraLong2). That is a deliberate design choice (the owner asked for the Apple TV focus feel, Slice 67), so the proposal is not to reverse it but to make it cheaper where it hurts:

```kotlin
// Motion.kt (refine)
const val FEEDBACK = 90                       // press, tick, ripple, colour flash: NEVER multiplied by scale
fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)          // colour/alpha: no overshoot, fixed
fun <T> spatial(fast: Boolean = false) =                                     // movement: scaled like today
    if (fast) spring<T>(0.6f, 800f / (scale * scale)) else spring<T>(0.8f, 380f / (scale * scale))
val ADD_FORM_GLIDE get() = minOf(GLIDE, 400)  // the most frequent flow in the app: Medium4 at most
```
Where it is used: `pressScale` already has its own un-scaled spring (0.6 / 600), which is right; move it to `Motion.FEEDBACK` semantics so there is one place. Add form: `ScreenHost` `to == ADD_FORM` and `from == ADD_FORM` use `ADD_FORM_GLIDE` and let the amount field take focus at once (already 16 to 18 ms [R: Slice 47 text]) so the keyboard rises *with* the sheet. Nothing else changes for people who like Relaxed.

Hard-coded numbers that ignore the setting [R]: `TrendChart` bar `tween(500)`, `SwipeableRow` flash `tween(1100)` (also plays under Reduce motion), `BreakdownRing` sweep (partly scaled, floor 500 ms). Route them through `Motion` (the flash through `Motion.tween(reduce, ...)`).

**Delta 2: Reduce motion.**
Today it means every transition is `tween(0)`. Apple's guidance is to make motion optional and to *supplement* with other feedback [A: HIG Motion]; the common practice is to replace large travel with a short crossfade so the change is still understood [C/I, not re-verified]. Proposal: under Reduce motion, `ScreenHost` uses `fadeIn(tween(FEEDBACK + 30)) togetherWith fadeOut(tween(FEEDBACK))` for screen changes (no translation or scale), charts draw fully formed, rolling digits swap with one crossfade, springs become tweens. WCAG 2.3.3 "Animation from interactions" is Level AAA [A: https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html], so this is craft, not compliance. Also observe changes while the app runs (Android: `ContentObserver` on `ANIMATOR_DURATION_SCALE`; iOS: `UIAccessibilityReduceMotionStatusDidChangeNotification`) instead of one `remember` [R: `PlatformHooks.*`]. Effort S.

**Delta 3: list item placement.** `animateItem()` is used with defaults on Home rows [R]. Pass Motion-scaled specs (`placementSpec = Motion.spatial()`, `fadeInSpec = Motion.effects()`), and do not animate on first load or when a page of older entries is appended (Compose only animates items that were already composed; appended pages are fine [I]). Keep `key = seq` and `contentType` as they are. Effort S.

**Delta 4: hero and shared-element transitions beyond the entry row.** Container transform from row to editor exists. Extensions with the same `sharedContainer` API: Home month card to the Reports summary (same `heroBackground`, tab change so it must also survive the tab crossfade), category tile to its drill-down, account tile to its sheet. Rules from Google: put modifiers you do **not** want shared before `sharedBounds` in the chain; prefer `sharedBounds` over `sharedElement` for `Text` that changes colour or weight [A: https://developer.android.com/develop/ui/compose/animation/shared-elements]. Cost: during the transition the gradient and the two blurred `HeroGlow` lights would be re-drawn every frame; hide `HeroGlow` while a shared transition is running (`SharedTransitionScope.isTransitionActive`). Effort S to M. Reference: Apple Card and Wallet open a card from its stack position [C].

**Delta 5: shared-container cost on every Home row.** `sharedContainer` is `composed {}` and reads three composition locals per row [R]. Google's current performance guidance recommends attaching `sharedBounds` only when an item is visible (`Modifier.onVisibilityChanged`), because modifier instantiation and state observation otherwise run for pre-fetched items that may never be shown [A: https://developer.android.com/agents/skills/jetpack-compose/jetpack-compose-performance/references/animation]. Two ways: (a) the visibility gate from that page; (b) set a `lastTapped` key at the moment of tap and apply `sharedBounds` only to that row. (b) is cheaper but needs the state to be set one frame before the screen key changes (the tap handler can set it, then change the key in the next frame). Also convert the modifier from `composed {}` to a `Modifier.Node` as Google recommends for modifiers that own an `Animatable` [A, same page]. Measure on the 100,000-entry scroll first (`PERFORMANCE.md` method). Effort S. Risk medium: it touches `MotionUiTest`.

**Delta 6: number roll (see 3.4).**

**Delta 7: pull and overscroll.** Do **not** add pull-to-refresh: nothing is fetched, and a fake refresh teaches people that the app is out of date when it is not. Android 12+ stretch overscroll comes with Compose; iOS rubber-banding is Compose's own [I: not checked in this release]. Use the pull gesture for nothing.

**Delta 8: predictive back, honestly.**
Facts: `enableOnBackInvokedCallback` is on and targetSdk is 36 [R], so Android's system animations (back-to-home, cross-activity) already work. Inside the app, `BackHandler` is `androidx.activity.compose.BackHandler` on Android and `androidx.compose.ui.backhandler.BackHandler` on iOS [R], which only gives a commit, not progress. Compose Multiplatform 1.10 deprecated `PredictiveBackHandler` and introduced `NavigationBackHandler` with `NavigationEventState.transitionState` (progress) built on the Navigation Event library, also on iOS [A: https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html]; the 1.11 and 1.12 notes list `navigationevent-compose` 1.1.0 to 1.1.1 [A: https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html]. Pattern: in `ScreenHost`, for a screen that sits on top of Home, drive `graphicsLayer { scaleX = 1 - 0.08 * p; translationX = ... }` from the progress inside the draw phase, commit on `onBackCompleted`, animate back on `onBackCancelled`. Android design numbers: full-screen surface transitions fade the entering content from a 35% progress threshold [A: https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back]. **Check before starting:** whether `navigationevent-compose` is already on the classpath through `compose.ui` (it is a dependency of CMP's back handler since 1.10 [I]); run `./gradlew :shared:dependencies` and look. If it would be a new coordinate, the owner's no-new-library rule applies and this item waits for a decision. Effort M. Risk: `LocalLeavingScreen` and the back-handler stand-down logic must keep working while a gesture is in progress.

**Performance and battery for the whole motion layer.**
- Read animated state only in draw or layout: `graphicsLayer { alpha = a.value }`, `Modifier.offset { IntOffset(...) }`, `drawWithCache`/`Canvas { ... }` lambdas. This is the first rule in Google's animation performance page [A, URL above]. Current offenders [R]: `RollingAmount` reads `roll.value` in the composable body (recomposes `Text` every frame for 576 ms), `TrendChart` reads `h` in composition (`(96 * h).dp`, one recomposition per bar per frame), `AccountTile`/`WalletStack` read `open.value` in composition to compute dp offsets (`with(density) { ... .toDp() }`; use `Modifier.offset { }` and a `layout` height instead).
- Use `Modifier.preferredFrameRate(FrameRateCategory.High)` on iOS for the few surfaces that animate continuously (rolling digits, scrub) if ProMotion hitches show up; CMP 1.9 added it and `CADisableMinimumFrameDurationOnPhone` is already set [A: https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html]. Do not set it on idle screens.
- Concurrent rendering on iOS is on by default from CMP 1.11 and CMP 1.12 reports improved lazy-list scrolling on iOS [A: whats-new-compose-111 and -112 pages].
- No `infiniteRepeatable` anywhere except a skeleton; infinite animations keep the GPU awake.

**Accessibility.** Reduce motion as above; keep `LocalLeavingScreen` semantics hiding; never carry meaning by motion alone (the rolling total already carries final text in semantics).

---

### 3.2 Haptic language

**Principle.** Apple: use system patterns according to their documented meaning, be consistent so each pattern teaches a cause and effect, complement other feedback, avoid overuse, prefer short haptics, **make haptics optional** [A: https://developer.apple.com/design/human-interface-guidelines/playing-haptics]. Android: "less is more", prefer action-oriented `HapticFeedbackConstants` over raw vibration effects, and "given the choice of buzzy haptics or no haptics, choose no haptics" [A: https://developer.android.com/develop/ui/views/haptics/haptics-principles].

**Hard constraint that decides the Android design.** Raw `Vibrator`/`VibrationEffect` needs the VIBRATE permission [I: standard Android behaviour, not re-read here], which the owner does not want to add. `View.performHapticFeedback` needs none, and it is already what the app uses. So the Android vocabulary is limited to the `HapticFeedbackConstants` set. API levels, from the reference page [A: https://developer.android.com/reference/android/view/HapticFeedbackConstants]: CONFIRM 30, REJECT 30, GESTURE_END 30, SEGMENT_TICK 34, SEGMENT_FREQUENT_TICK 34, GESTURE_THRESHOLD_ACTIVATE 34, TOGGLE_ON 34, CLOCK_TICK 21, LONG_PRESS 3, VIRTUAL_KEY 3. The app's minSdk is 26, so every pattern below needs an older fallback.

**Findings in the code [R]:**
- `Feel.UNDO` maps to REJECT / Compose `Reject`. REJECT is documented as "rejection or failure of a user interaction". Undo is a successful reversal, not a failure. A person who feels a failure buzz after tapping Undo is told the opposite of what happened.
- `Feel.LIMIT` maps to LONG_PRESS ("a long press ... resulting in an action"). It is the pattern people know as "a menu opened".
- `CaptureScreens.kt` calls `LocalHapticFeedback.performHapticFeedback(Confirm)` directly in 8 places, bypassing `Feel`, so any future in-app switch or retune would miss them.
- `pressScale` passes `SegmentTick` (Android API 34 and up) for tab and tile presses. Segment ticks mean "switching between choices", which suits tabs, but on API 26 to 33 what Compose does for it is [I: unverified]; check `HapticFeedbackType.SegmentTick` fallback.
- iPhone: `Feel` is mapped through Compose's `HapticFeedbackType`. CMP issue CMP-10050 reported `SegmentTick` and `SegmentFrequentTick` producing nothing on iOS in 1.11.0-alpha04; it is marked Fixed for 1.11.0-beta03 [B: https://youtrack.jetbrains.com/issue/CMP-10050 via REST]. The app is on 1.12.1 so the scrub tick should work, but nobody has felt it [R: Slice 67 "not felt on a phone"]. Compose does not expose iOS's notification-style haptics (success / warning / error). They are available in `iosMain` through `platform.UIKit.UINotificationFeedbackGenerator` without a library (the Zarzara KMP write-up shows the four-line actual [C: https://zarzara.app/blog/cross-platform-haptics-kotlin-multiplatform]; its Android half uses `Vibrator`, which this app should not copy).

**Proposed haptic language (one table, one `Feel` enum, a Haptics switch).**

| Moment | `Feel` | Android constant (fallback) | iPhone | Notes |
|---|---|---|---|---|
| Tab or tile press | `PRESS` (new, replaces the SegmentTick argument) | SEGMENT_TICK 34 (CLOCK_TICK) | selection feedback | Very light; fires on touch-down only |
| Save an expense, bill paid, a goal reached, "all reviewed" | `SAVE` / `PAID` | CONFIRM 30 (VIRTUAL_KEY) | notification `success` | Once per action. Same pattern for every success: that is the point |
| Undo tapped | `UNDO` -> soft neutral | GESTURE_END 30 (KEYBOARD_TAP) | impact `soft` | Not REJECT |
| Swipe passes its commit threshold | `THRESHOLD` (new) | GESTURE_THRESHOLD_ACTIVATE 34 (CLOCK_TICK) | impact `light` | Fires on crossing, again on crossing back |
| Scrub across a chart | `STEP` (replaces TICK) | SEGMENT_FREQUENT_TICK 34 (CLOCK_TICK) | selection feedback | At most one tick per 40 ms [I] and none while the finger is still |
| A budget goes over **because of an entry you just made** | `WARN` (replaces LIMIT) | REJECT 30 (LONG_PRESS) | notification `warning` via interop | Never on screen open, never for "near" (80%). Existing `LimitCrossedFeel` already avoids first composition |
| Delete committed by swipe | none | none | none | The row leaving and the Undo bar are the feedback |
| Long press starts selection | `LONG_PRESS` | LONG_PRESS | impact `medium` | Matches the platform meaning |
| Number-pad key | `KEY` | VIRTUAL_KEY | selection feedback | Only if the optional pad is on; the system keyboard has its own |
| Errors in a form field | none | none | none | Colour and text carry it; never buzz while typing |

Never: scrolling, typing, list items appearing, rolling digits, background updates, widgets. Do not stack a haptic on a ripple *and* a spring *and* a transition: one sensory cue per event beyond the visual.

**Compose techniques.** Keep `expect fun rememberFeel(): (Feel) -> Unit` (it already exists, `PlatformHooks.kt:12`). Add one `LocalHapticsEnabled` composition local fed by a new Appearance setting "Haptics: On / Off" (stored beside `reduce_motion`; default On), checked inside `rememberFeel` so nothing else needs to know. Replace every direct `LocalHapticFeedback` call and the `pressScale(haptic = ...)` parameter with `Feel`. On iOS, create the `UIImpactFeedbackGenerator`/`UINotificationFeedbackGenerator` once and call `prepare()` on touch-down for the swipe and scrub cases to remove first-tap latency [A: Apple UIFeedbackGenerator docs, https://developer.apple.com/documentation/UIKit/UIFeedbackGenerator; `prepare()` behaviour is from memory, [I]].

**Performance.** Haptics cost nothing in frame time; cost is in annoyance. Throttle scrub ticks; do not call from a draw or layout lambda; call on the main thread from a gesture callback.

**Accessibility.** Haptics never replace text or colour: HIG Motion says supplement visual feedback with haptics or audio, not the reverse [A]. Android's system "touch feedback" setting is respected by `performHapticFeedback` unless `FLAG_IGNORE_VIEW_SETTING` is passed [I: standard behaviour; do not pass the flag].

**Effort.** S to M. The risky part is not code but taste: the first pass must be felt on the owner's iPhone and on one low-end Android phone with a weak motor, then retuned. Android devices differ widely in actuator quality [C: Zarzara post].

---

### 3.3 Charts

**Principle.** Tufte's sparkline: a word-sized, data-dense graphic with no axes or gridlines, readable next to text [A: https://www.edwardtufte.com/notebook/sparkline-theory-and-practice-edward-tufte/]. Few's bullet graph: a bar for the measure, a tick for the target, and qualitative ranges, in the space of a progress bar [A: https://www.perceptualedge.com/articles/misc/Bullet_Graph_Design_Spec.pdf]. Donuts are fine for a part-to-whole question with at most about five slices and a total in the centre; bars are better for comparing and ranking [C: https://academy.datawrapper.de/article/25-how-to-create-a-donut-chart and the 2025 study "Are pie charts evil?" https://journals.sagepub.com/doi/10.1177/14738716241259432, which found pies and donuts as accurate as bars for part-whole judgement].

**What exists is already on the right side of these rules:** one line without axes, a donut capped at five slices plus "Others" with a total in the middle and a legend that repeats the percentages (so colour is not the only signal, WCAG 1.4.1 [A: https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html]), rounded bars, a heat-shaded calendar.

**A signature chart style (the deltas).** One look for every chart so the app is recognisable in a screenshot:
1. Stroke 2.5 dp, round caps and joins (the line has them; the donut has `StrokeCap.Butt`).
2. One accent hue per chart plus neutrals; category colour only where the chart is *about* categories.
3. No gridlines, no axis box; direct labels (min, max, last) at 11 to 12 sp in `onSurfaceVariant`.
4. "Today" is always the filled dot; past is the accent at 95%, forecast or pace is dashed.
5. One selection model everywhere (see `ChartScrubber` below).
6. All labels in tabular figures (3.4).

**Components, with exact Compose techniques.**

- **Sparkline (exists; refine).** Build the `Path` once with `Modifier.drawWithCache { ... }` keyed on the data, instead of in the `Canvas` lambda (it is rebuilt on every draw while the reveal runs and while scrubbing [R]). Keep `reveal` and `fade` read inside the draw lambda (already so [R]). Add the min and max day as 2 tiny dots, and the pill value in tabular figures. Cost: 14 points; trivial.

- **`ChartScrubber` (new, shared).** A `Modifier` that takes `count`, `indexAt(x)`, `onIndex(i)` and handles `detectHorizontalDragGestures` plus tap, emits `Feel.STEP` on index change (throttled), exposes semantics (below), and draws guide, dot and pill through a small `ScrubState`. `HomeSpark` already contains all of it inline (`HomeVisuals.kt` lines for `pointerInput`, `shownIndex`, `fade`); the work is extracting it, then using it in Reports bars, the area chart and the calendar row. Effort M.

- **Bars with scrubbing (replace `TrendChart` Boxes).** One `Canvas` (or `drawWithCache`) drawing `n` rounded-top rectangles via `drawRoundRect`, heights from one `Animatable` progress read in draw, selected bar in the accent and others at 30%, an optional dashed horizontal line for the budget or the average (a Few-style target [A]). Tap opens the period (as now), drag scrubs. This also removes one recomposition per bar per frame [R]. Effort M.

- **Donut with rounded caps.** `drawArc(..., style = Stroke(width, cap = StrokeCap.Round))` makes each end bulge by half the stroke width; shorten each sweep by `2 * (strokeWidth / 2) / radius` radians in degrees plus the visual gap, otherwise neighbours overlap. Keep the sweep-in, pull-out and dimming. Keep top 5 plus "Others". For more than five categories show the legend as a ranked list of mini horizontal bars (bars rank better than angles [C]) rather than adding slices. Effort S.

- **Stacked area for category over time.** One `Path` per series using the same monotone cubic as the sparkline, stacked in order of total (largest at the bottom), at most 5 series plus "Others", `drawPath` with a vertical alpha gradient per series, scrub shows the day's split in a pill. Data: needs spend by day by category for the period; today the Reports code aggregates by category and by period [I: not read in detail]; a new grouped query is one more aggregate on the report pass. Do not start this before the cheaper chart work. Effort M to L.

- **Calendar heatmap (exists; refine).** Each cell is tinted `primary * level * 0.18` [R]. Make the ramp perceptual (4 steps, evenly spaced in luminance), add a 4-swatch legend, keep the day number and short amount in the cell so colour is never the only carrier (WCAG 1.4.1 [A]); mark no-spend days with a small ring (see 3.6). Effort S.

- **Budget ring and bullet bar.** The linear bar stays in lists. Add (a) a *pace tick* on the bar: the fraction of the period that has passed, so "62% spent, 48% of the month gone" is visible without reading (the app already has pace alerts in `Notifications.kt` `BUDGET_*` [R]); (b) a ring variant (270 to 360 degree arc, round caps, state colour, amount in the middle) for the "Budget left" tile and the widget, because a ring is a compact single number. Over-budget: the arc keeps going past 100% in the error colour with a second lap hairline, never a bigger number alone. Effort S.

**Colour system for categories (the biggest lever).**
Today [R]: `CategoryAvatar` picks one of 8 pale tints by `name.lowercase().hashCode() mod 8` (it never reads `Category.colorArgb`); the chart palette is a different list of 8 saturated colours (`ReportCharts.kt:52`: indigo, teal, orange, pink, green, light blue, brown, purple) and `reportColor(own, index)` uses the person's chosen colour only there; `Category.colorArgb` is optional. Slice 46 said "the colour chosen in Categories is not used here yet" for the home tile, and the avatar code still ignores it. With 23 default expense categories into 8 tints, collisions are certain, and renaming a category changes its colour.

Proposal:
1. **One resolver** `categoryHue(category): CategoryHue` in `core/domain`-adjacent UI code: `colorArgb` if the person set one, else a stable slot from the category's id order in a fixed 12-slot list (never from the name). Same resolver for avatar, chart slice, legend dot, calendar tag, widget bars.
2. **Tonal pairs, precomputed.** For each slot store four constants: `fillLight`, `onFillLight`, `fillDark`, `onFillDark`. Light: a mid tone (about tone 40) with white label, or a pale container (about tone 90) with a dark glyph; dark: tone 80 fill with a dark label, or a tone 30 container with a light glyph. Use the pale-container version for avatars and the saturated fill for chart marks. Precomputing 48 constants avoids a runtime colour library and runtime cost.
3. **Chart palette start point** (check, do not trust): the Okabe-Ito set, designed to stay distinguishable under the main colour-vision deficiencies: orange `#E69F00`, sky blue `#56B4E9`, bluish green `#009E73`, yellow `#F0E442`, blue `#0072B2`, vermillion `#D55E00`, reddish purple `#CC79A7` [A: original page https://jfly.uni-koeln.de/color/; hex list [C]]. Lighten about 15% for dark mode. It has 8 colours, matching the 5 + Others cap of the donut with room. The current palette has teal, green and light blue within a similar lightness band, a classic confusion trio for red-green deficiency [I: not simulated].
4. **A test that settles it without a library.** A JVM unit test in `core/domain` applying the Machado deuteranopia, protanopia and tritanopia 3x3 matrices to each palette colour (about 20 lines), converting to CIELAB and asserting a minimum distance between every pair of the top 6 slots (start at 12 to 15 delta-E and tune) [I]. This is the cheapest honest verification and runs in CI.
5. **Redundancy**, because no 12-hue set is fully safe: direct percentage labels, ordered legend, a gap between slices (exists), and the category glyph (3.5).
6. Non-text contrast: marks that must be understood need 3:1 against their background (WCAG 1.4.11 [A: https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html]); the `0.18` calendar tint cannot meet it alone, which is why the number stays in the cell.

**Performance.** Charts are small data (14 to 366 points). Costs to avoid: rebuilding `Path` per frame, per-bar recomposition, shadows, and `Modifier.blur` on anything that animates. Use `drawWithCache`, one `Animatable` per chart, and `graphicsLayer` for any translate or alpha. Battery: nothing animates when idle.

**Accessibility.** See 3.11: each chart gets a spoken summary and step actions, and Reports already offers the same data as rows underneath (drill-down list), which is the table alternative.

**Effort.** Ring caps S; calendar S; budget pace S; sparkline cache S; `ChartScrubber` M; bars M; area M to L; colour system M.

---

### 3.4 Typography and number display

**Principle.** In a money app the figure is the interface. Digits must not move when they change; the currency sign is a label, not part of the number; short forms must be honest.

**Findings [R].** No `TextStyle` sets `fontFeatureSettings`; no `Typography(...)` override; no custom font (system Roboto on Android, San Francisco on iPhone through Skia's system font mapping [I]). `FontFamily.Monospace` is used for card digits only (`AccountTiles.kt:110`). Hero style is `displaySmall` bold. The big month figure is capped at 54 sp at large text (`CappedFontScale`). The Indian formats exist: grouping, `compact()` (₹1.2L, ₹3.4Cr), whole rupees on headlines, amount in words.

**Deltas.**
1. **Tabular figures.** `TextStyle(fontFeatureSettings = "tnum")` on every style that shows a figure: row amounts, day totals, tile balances, hero, chart labels, pills. Roboto and the Apple system font both ship a `tnum` feature [C: https://dev.to/alanwest/tabular-numbers-in-css-font-variant-numeric-vs-monospace-hacks-25cn]; whether Compose on iOS honours it with San Francisco through Skia is **not verified** (a practitioner repo says exactly that [C: https://github.com/Carlsmeister/strive/issues/30]). Verification, 10 minutes: a common `@Test` or a debug screen measuring the width of `"111,111"` and `"888,888"` with `TextMeasurer` on each platform; equal widths mean it works. Without it the fallback is `FontFamily.Monospace`-like alignment for amounts only, which looks wrong, so if iOS fails the feature, the answer is a bundled numeric font (section 7).
   Define two extra styles in one place: `Number.Hero` (displaySmall, SemiBold, `tnum`, letter spacing -0.5 sp), `Number.Row` (bodyLarge, SemiBold, `tnum`), `Number.Label` (labelMedium, `tnum`). Pass them into `RollingAmount`, `DayHeader`, `SwipeableRow`.
2. **Hero with a small currency sign.** In an `AnnotatedString`: `₹` at 55% of the digit size, digits at full size, and `.40` at 55%, all on the same baseline (`SpanStyle(fontSize = ...)`; avoid `BaselineShift.Superscript` for the rupee sign, it looks like a footnote). The rupee glyph U+20B9 exists in Roboto and San Francisco [I]. Home already shows whole rupees on headlines (`wholeAmounts`), so paise appear only where the exact figure matters (rows, entry, reports). Effort S.
3. **Odometer digit roll (replace the Float count-up).** Today `RollingAmount` interpolates the number and re-formats the full string each frame [R]. The Robinhood Ticker approach: each digit scrolls vertically to its new value, choosing the shorter direction (up or down) so the least elements are passed, with a horizontal slide and fade for digits that appear or disappear [B/C: https://github.com/robinhood/ticker and https://betterprogramming.pub/scrollcounter-an-ios-solution-to-the-robinhood-number-animation-bbcbd8c90355]. Compose version: split the formatted string into characters; for each character whose value changed, a tiny `DigitColumn` draws the old and the new glyph in a clipped `Box` with `graphicsLayer { translationY = progress * height }` read only in the layer lambda (no recomposition per frame), progress from one shared `Animatable` with `Motion.spatial()`; unchanged characters are plain `Text`. Needs `tnum` (item 1) or columns jitter. Keep every current rule: no roll on first composition, none under Reduce motion or Hide amounts, semantics carry the final text. At most about 6 changing columns at a time; if more than 4 digits change (a big jump) fall back to a crossfade [I]. Effort M. Reference: Robinhood [B], Apple's own `contentTransition(.numericText())` in SwiftUI [C, not read].
   Where to use it: Home month figure (exists as count-up), Budget left, Safe to spend, account tile balances, Reports "Spent", the Add form's amount in words. Only roll on *change*, never on open, so cold start stays calm.
4. **Lakh and crore.** `compact()` is correct and conservative (cut, not rounded). Two checks [not verified]: (a) what TalkBack and VoiceOver say for "₹1.2L" (set `contentDescription` to the words from `AmountWords`-style output: "1.2 lakh rupees"); (b) mixed "K" with "L" is common in India but "₹1.2K" next to "₹1.2L" needs the same tabular width, so use `tnum` there too.
5. **Type scale.** Keep Material's scale (the app has no override), but pin four roles: Hero (displaySmall, 36 sp, SemiBold), Row amount (bodyLarge SemiBold), Day heading (labelLarge), Caption (labelSmall for chart labels). Large-text behaviour already exists (`LARGE_TEXT_SCALE = 1.5`, `CappedFontScale`); keep it. Android 14 scales fonts non-linearly up to 200% [C/I]; the hero cap matters more there.
6. **Custom numeric font (optional, later).** A digits-only subset (digits, ₹, comma, point, minus, plus, space; about 10 to 20 KB) would make numbers pixel-identical on both platforms and solve the `tnum` doubt. It needs the Compose resources mechanism (a `composeResources/font` folder and `Font(Res.font.x)`); whether that adds a new Gradle artifact beyond what the plugin already pulls is **not verified**; the project has no `composeResources` folder today [R]. Licence (OFL) must be checked. Defer until the `tnum` test result is known.

**Performance.** Text measurement is the cost; digit columns draw two glyphs each; keep `maxLines = 1`, no autosize on animated numbers.

**Accessibility.** WCAG 1.4.4 resize text to 200% [A: https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html]; `HOME_OPTIONS.md` found tile and row breakage at 200% and Slice 112 fixed most; re-test with the new hero style. Never convey change direction only by the roll (the "+158%" chip has words).

---

### 3.5 Shape, elevation, surface, blur, gradient; icons

**Principle.** Surfaces should say what is content and what is chrome. Apple's iOS 26 rule is explicit: Liquid Glass is a functional layer for controls and navigation that floats above content; **do not use it in the content layer**; use it sparingly; the *regular* variant blurs and adjusts luminosity for legibility; scroll-edge effects blur and fade content under bars; the appearance changes with Reduce Transparency and Increase Contrast [A: https://developer.apple.com/design/human-interface-guidelines/materials].

**What exists is directionally right:** opaque tonal cards for content, a translucent pill only in the bar, a gradient hero. The gap is the bar itself.

**Deltas.**
1. **Scroll-edge scrim (S).** Draw a 24 to 32 dp vertical gradient from `background` at 0% to `background` at 100% alpha just above the bar and just below the top app area. It gives the "content slides under a layer" look with no blur and no per-frame cost (drawn once). It is the cheap half of Apple's scroll-edge effect.
2. **Floating bar (M).** Let the list draw to the bottom edge and pad its content by the bar height plus the navigation-bar inset (`contentPadding` of the `LazyColumn`), with the bar as an overlay. Needs every screen that uses `HomeBar` (Home, Search, Reports, More) to add the padding; keep the keyboard rule (`rememberKeyboardOpen`) and the 56 dp targets.
3. **Real backdrop blur, only if measured (M to L).** Compose has `Modifier.blur`, which blurs the node's own content, not what is behind it. A backdrop effect can be built without a library with `rememberGraphicsLayer()`: record the content into a `GraphicsLayer`, draw it normally, then draw the same layer again (offset, scaled, with a blur `RenderEffect`/`Modifier.blur`) behind the bar, clipped to the bar's bounds [A: https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer; the technique itself [C/I]; libraries that do it exist (Cloudy, KMPLiquidGlass, Haze) and are not allowed]. Cost: content is drawn twice and blurred every frame it scrolls; on Android below 12 `Modifier.blur` does nothing (the app already has `supportsBlur`), on iOS it runs on Skia. Gate: build it behind a debug flag, then compare janky-frame percentage on the 100,000-entry scroll on a low-end phone and on the iPhone with the frame logger from 3.12. If it costs more than about 1 percentage point of janky frames, do not ship it.
4. **Reduce Transparency and high contrast (S).** Add `expect fun systemReduceTransparency(): Boolean` (iOS `UIAccessibilityIsReduceTransparencyEnabled`; Android has no equivalent, so false or the high-contrast text setting) and make `HomeBar` fully opaque with a visible top border when true. HIG says Liquid Glass itself reacts to these settings [A].
5. **Do not copy refraction.** True Liquid Glass (specular refraction, morphing) is a native system effect. Compose has no refraction primitive; a shader (AGSL on Android 13+, SkSL on iOS in CMP 1.11+ where `Shader` is now a Compose wrapper [A: whats-new-compose-111]) is possible but fragile across two renderers and costly on low-end phones. Best effort per effort: keep the lit pill, add the scrim, add the Reduce-Transparency path.
6. **Material You dynamic colour vs brand accent (S).** `dynamicLightColorScheme/dynamicDarkColorScheme(context)` are Android-only (androidMain). Add an `expect fun dynamicSchemeOrNull(dark: Boolean): ColorScheme?` (null on iOS and on Android below 12) and a seventh choice "Match my wallpaper" in the accent row; the six fixed accents remain the default and the only choice on iPhone. The widgets already use Material You colours [R], so the app and its widgets would agree for people who choose it. Contrast: dynamic schemes are tonal and safe, but run `AccessibilityUiTest` over them [R: ATF runs there]. Reference: Material 3 dynamic colour [A: https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic-color].
7. **Elevation.** Use tonal elevation for chrome (the bar uses `tonalElevation = 3.dp` [R]) and shadows only for objects the person is holding or that overlap (wallet-stack cards 8 dp, a dragged row). Shadows are cheap on Android's hardware layers and cost more on Skia-drawn iOS when many are on screen [I]; keep the stack to the 8 dp shadow it has and never put shadows on list rows.
8. **Gradients.** Keep the one hero gradient and the corner light; the audit measured contrast 2.9 to 6.1:1 on its small text [R: `HOME_OPTIONS.md` finding 14]; fix by darkening the gradient's bright corner for text zones or by putting small text on a `onHero.copy(alpha = .18)` pill (the compare chip already does).

**Icons and merchants.**
- Today the category tile is the first letter (the owner kept it: Q11) and first letters collide (Food and Fun are "F") [R: `HOME_OPTIONS.md` finding 9].
- **Vector over emoji.** Emoji render with Apple Color Emoji on iPhone and Noto Color Emoji on Android, cannot be tinted, differ in shape between platforms, and newer emoji show as boxes on older Android: Slice 48 already found that unicode glyphs were "not reliable on Android 8" [R]. `compose.materialIconsExtended` is already a dependency [R: `shared/build.gradle.kts:37`], so a set of about 40 category glyphs (restaurant, shopping bag, bus, bolt, medical, movie, home, school, flight, gas station, savings, receipt) costs no new library; R8 keeps only the icons referenced on Android [I]; on iOS check the klib size impact once (not measured).
- **Tile = tonal container + glyph**, colours from the category resolver (3.3). The person's chosen colour (`colorArgb`) finally has a visible effect.
- **Merchants, logo-less.** No logos (no internet, no licensing). A *monogram*: the first letter or two of the clean merchant name (`MerchantTable` gives clean names for 250+ known ones [R]) on a tonal tile coloured by a stable hash of the **payee key** (not the category), with a 14 dp category glyph badge at the bottom right. Rows then show: category glyph for categorised rows without a merchant, monogram with badge for merchant rows. Reference: Cash App and Monzo show merchant logos [C]; Copilot-style apps use emoji [C]; the logo-less monogram approach is what every offline tracker without a logo service ends up with.
- Effort: glyph set and tile M, merchant monogram S.

**Corner radii and the rest: see tokens, section 5.**

---

### 3.6 Microinteractions

1. **Swipe actions (S, rank 4).** `SwipeableRow` shows a flat red area with "Delete" [R]. Proposed, using `SwipeToDismissBoxState`:
   - Background: `Icon(delete)` + label that scale from 0.8 to 1 and fade in as `state.progress` goes from 0 to the threshold; colour from `surfaceVariant` to `errorContainer` interpolated with progress (read in `graphicsLayer`/`drawBehind`, not composition).
   - At the threshold: `Feel.THRESHOLD` once (observe `state.targetValue` with `snapshotFlow`), and again when the finger moves back.
   - Optional leading swipe (start to end) for "Category" (opens the picker) because categorising is the second most common edit in this app's own flow (To review, Categorise [R]); keep it off by default or require a longer travel to avoid accidents.
   - No confirm dialog: the Undo bar is the safety net [R: `UNDO_AUDIT.md`].
   - Reference: iOS Mail and Gmail row actions [C].
2. **Long-press (exists as selection).** Add `Feel.LONG_PRESS` on entering selection mode; the row lifts 2 dp and the avatar becomes a checkbox with a 120 ms crossfade. Effort S.
3. **Undo.** Most screens use a custom one-line bar (`MessageHost.kt`, written after a Material snackbar swallowed touches [R]) and `UndoBar.kt` still wraps a Material `SnackbarHost` for screens with a Scaffold; give both the same spring, lifetime and haptic. Craft: slide up on a spring that is `Motion.spatial(fast = true)`, 10 s lifetime (same as the widget's `UNDO_WINDOW_MILLIS` [R]), `Feel.UNDO` soft on tap, no countdown ring, announce text to screen readers (`liveRegion = Polite`). One undo at a time, a new action replaces the bar (already the contract in `UndoBar.kt` [R]).
4. **Pull-to-refresh: do not add** (3.1 delta 7).
5. **Checkmark and success states (S, rank 14).** `MotionUiTest.payingABillShowsAPaidCheck` exists, so a "paid" check exists [R]. Make it one shared `SuccessCheck`: a `Path` for a tick drawn with `PathMeasure.getSegment(0, length * progress)` over 280 ms ease-out (draw-phase only), with `Feel.SAVE`; use it on Save, Paid, "All reviewed", "Backup done". Under Reduce motion it appears complete. Then the screen closes; the row glow (exists) and the rolling total (exists) carry the rest. Do not add confetti: a money app should feel calm and accurate; Monzo's own rule is that humour is "a delicate seasoning" and belongs mostly in marketing, with the occasional surprise in the app [A: https://monzo.com/tone-of-voice].
6. **Skeleton and loading.** Material's skeleton loader pattern (a subtle pulse from the top left to the bottom right, content fades in over it) is meant for after-launch waits [A: https://m3.material.io/styles/motion/transitions/transition-patterns]. In this app almost nothing waits on a network; the waits are cold start and a first report pass. Use the instant-shell approach (3.12) and keep any skeleton to two places: the first-ever Reports computation on a huge ledger and the import preview. Never loop a pulse forever (battery).
7. **Press feedback.** Exists (`pressScale`). Add it to chips, quick entries and bar buttons consistently, with one spring in `Motion` (3.1).
8. **Ethical delight instead of streaks (S).** Streaks work through loss aversion, and the better-documented criticism is anxiety and "performative" use; Duolingo appears on a database of dark patterns for pushy reminders [C: https://www.ludaxis.io/blog/gamification-in-apps-duolingo-case-study-2026, https://screenwiseapp.com/guides/duolingo-streaks-and-anxiety-in-kids]. A tracker's honest behaviour is to be accurate, not to be habit-forming. Rules: no daily streak counter, no "you will lose" copy, no notification about a streak, anything opt-in, nothing that makes a person feel bad for a day they did not log. Positive alternatives with no loss state: a small ring on **no-spend days** in the calendar and "6 no-spend days this month" in the summary; a quiet "All reviewed" moment when To review reaches zero; "Under budget for the third month" as an observation in Reports, not a badge.

---

### 3.7 Empty states, first run, onboarding

**Principle.** Material: an empty state is a non-interactive picture plus a tagline; prefer starter or educational content to a blank screen; onboarding should connect to the first-run experience [A: https://m1.material.io/patterns/empty-states.html, https://m2.material.io/design/communication/onboarding.html, via search; older Material versions].

**What exists is good:** seven Canvas pictures, welcome card, first-week card, a tour, guided capture setup [R]. `HOME_OPTIONS.md` left two things on purpose: the 0.00 month card above the Welcome card, and the "Start here" checklist [R].

**Deltas.**
1. **Ghost data, not zeros.** On an empty ledger, draw the month card with a faint, dashed example line and the words "Your last 14 days will draw here". Starter content in Material's sense, no fake numbers in the ledger (Cashew and others use sample data; here it would pollute totals and backups). Effort S.
2. **First action in one frame.** The first-run Home leads with Add and one sentence; the three-step "Start here" (first expense, budget, read bank texts) becomes a slim card that collapses as steps complete (checks drawn with `SuccessCheck`). It is the `HOME_OPTIONS.md` option B empty state; effort S to M, and the risky part is the many tests that use the first-run path [R].
3. **Motion in first run:** pictures already fade and grow once; keep them to once per install, not per visit, and skip under Reduce motion (done [R]).
4. **Tone** (3.13): one idea per line, a verb on the button.
5. **Permission moments** (bank texts, notification access): show the system dialog only after the app has said, on one screen, what is read and that nothing leaves the phone (already how guided setup works [R]); craft = a locked-padlock or phone-with-check picture, never a stock shield.

---

### 3.8 Personalised home and dashboard modules

**What exists.** `HomeLayout` has four sections, each movable and hideable, with unknown future names added at the end so updates never hide a block [R]. Reorder is up/down text buttons.

**Deltas.**
1. **Drag to reorder (M).** `pointerInput(Unit) { detectDragGesturesAfterLongPress(...) }` on a handle, `Modifier.offset { IntOffset(0, dragY) }` for the dragged item (draw/layout phase), `animateItem()` on the others, `Feel.LONG_PRESS` on pickup and `Feel.STEP` each time it crosses a neighbour. Keep the up/down buttons as the accessible alternative (WCAG 2.5.7 "Dragging movements" requires a non-drag alternative [A: https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements.html, standard WCAG 2.2 criterion, URL not fetched here]). The setting format does not change.
2. **Modules to add as off-by-default sections** (from `HOME_OPTIONS.md` option C, where the data is already computed [R]): "Where it went" (top 3 categories as ranked mini bars, tap opens the drill-down), "Coming up" (next bills and dues, with days), "To review" count (a number, not a nudge). Add them to `HomeSection`; `HomeLayout.decode` already appends unknown names so older backups keep working [R].
3. **Quick-entry chips** become the home of haptic and press craft (`Feel.PRESS`, check morph on add, "Undo" in the bar).
4. **Hide amounts** (exists) must hide bars and the new modules' shapes (decision 6 in `HOME_OPTIONS.md`).
5. **Do not add a recommendation feed.** On-device, no network, no data to make it honest; keep insights to the one sentence under the month figure.

Effort M overall; M for drag, S for each module.

---

### 3.9 Widgets, Live Activities, Control Center, Lock Screen, Quick Settings, Wear

**Android (feasible with no internet, no new libs):**
- Existing: five Glance widgets, Quick Settings tile, one static launcher shortcut [R].
- **Dynamic launcher shortcuts for quick entries (S).** `ShortcutManagerCompat.setDynamicShortcuts` is in `androidx.core`, already present (`core-ktx` [R]). Publish up to 4 of the person's quick entries ("Chai ₹20") as long-press shortcuts that add the entry directly (reuse the widget chip action). Android guidance says dynamic shortcuts should reflect the user's likely next action [I, not re-read]. No permission needed.
- **Widget craft with Glance 1.2.0.** Glance renders `RemoteViews` underneath, so there are no springs or gradients beyond what `RemoteViews` supports; craft is layout and information, not motion. Make sure every size shows one clear number, tabular figures are not available in `RemoteViews` text (check for `android:fontFeatureSettings` in the XML attribute set: it exists on `TextView` but Glance does not expose it [I: unverified]).
- **Android 16 Live Updates: not appropriate.** `ProgressStyle` with promoted-ongoing is for journeys such as rides, delivery and navigation, needs `POST_PROMOTED_NOTIFICATIONS` and no custom layouts [C: https://android-developers.googleblog.com/2025/01/first-beta-android16.html and secondary posts]; a new permission and a poor fit. Skip.
- **Wear OS: not feasible under the rules.** Tiles need `androidx.wear.tiles` and `androidx.wear.protolayout` and a separate Wear module and APK [C: https://developer.android.com/training/wearables/tiles/get_started]. What the watch already gets for free: bridged phone notifications with their actions (Mark paid, Later today [R]). Make those action labels short.

**iPhone (Swift only, no server, no push):**
- Existing: Today (small) and Quick add (medium) widgets and App Intents "Add expense" and "Capture bank message" [R].
- **Lock Screen widgets (M, rank 16).** Add `accessoryCircular` (a gauge: budget left as a ring), `accessoryRectangular` (today's spending and a-day figure), `accessoryInline` ("₹1,240 today") to `supportedFamilies`. Mark every figure privacy-sensitive so it redacts on a locked device, and honour the app's Hide amounts flag from the shared app-group data, exactly as the Android widgets do [C: https://developer.apple.com/documentation/widgetkit/creating-lock-screen-widgets-and-watch-complications].
- **Control Center / Lock Screen / Action button control (S to M).** iOS 18's `ControlWidget` (button or toggle) backed by an App Intent; the existing "Add expense" intent is the obvious action; it needs `@available(iOS 18, *)` because the deployment target is 17 [A: https://developer.apple.com/documentation/widgetkit/creating-controls-to-perform-actions-across-the-system]. Free signing and widget extension rules: the extension already exists and ships on free signing [R], so no new entitlement is expected [I; verify with the owner's profile].
- **Live Activities / Dynamic Island: low value.** A Live Activity can start locally from the foreground app with no push, but the lifetime is hours (about 8 in the Dynamic Island and 12 on the Lock Screen [C: infinum.com/blog/live-activities-in-ios-apps and the Apple HIG overview https://developer.apple.com/design/human-interface-guidelines/live-activities]), meant for time-bound events (delivery, match, workout). A multi-day trip (Travel mode) does not fit; a bill due "today" is already a notification. Recommendation: do not build. If something is wanted, a *scanning a statement* progress activity is the one honest fit [I].
- Parity honesty: none of this is shared code, none can be tested on the simulator reliably for the lock screen privacy redaction (needs a device [I]).

---

### 3.10 Large screens, foldables, tablets, desktop

**What exists** is strong (section 1): two panes at 600 x 480 dp, fold awareness, max content width, keyboard shortcuts, two-pane Search.

**Deltas.**
1. **Navigation rail from 600 dp (M).** M3 adaptive navigation: bottom bar on compact windows, rail on medium and expanded [A: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation]. `HomeBar` already takes the same actions; render a vertical rail variant (glass pill glides vertically under the current item) when the window is two-pane. Today the bar stretches to the pane width [R]. Add the Add button at the top of the rail.
2. **Pointer craft (S).** `hoverable` + a 6% `onSurface` overlay on rows, buttons and bars when `isHovered`; right-click on a row opens the same actions as long-press (`Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary))` is experimental in `foundation` [I]); cursor `PointerIcon.Hand` on clickable cards. Matters for ChromeOS and DeX, where the manifest says the app installs [R: `PERFORMANCE.md` mentions Chromebooks].
3. **Drag and drop (M).** Dragging an entry out as text/CSV or into another pane (`PERFORMANCE.md` suggestion 4 already names it [R]). The Compose drag-and-drop modifiers are experimental; Android-first.
4. **Charts on wide panes:** the detail pane can show the 14-day line at 120 dp instead of 56 and the scrubber value inline instead of a pill.
5. **iPad:** the target is `TARGETED_DEVICE_FAMILY: "1"` (iPhone only) [R]; enabling iPad is a product decision, not craft.
6. **Foldable posture:** `androidx.window` is declared [R]; tabletop posture could move the Add form's number pad to the lower half, S, only worth it with a real fold to test on.

Effort M for rail, S for hover, M for drag.

---

### 3.11 Dark mode, OLED, high contrast, large text, Reduce motion, chart accessibility

**What exists [R]:** Dark scheme from Material baseline with the accent lightened to 4.5:1 on `0x141218`; amount colours chosen per theme to reach 4.5:1 (`amountColor`); large-text mode at 150%; capped hero and bar fonts; `AccessibilityUiTest` with Google's Accessibility Test Framework on 9 screens (**light theme, default font size, API 26 only**); dark mode, large text, TalkBack on hardware and VoiceOver "NOT checked" [R: `ACCESSIBILITY_FINDINGS.md`, `OUR_FEATURES.md`].

**Deltas.**
1. **True-black option (S).** A fourth theme choice "Dark (black)": `background = 0xFF000000`, `surface = 0xFF0B0B0F`, cards at `surfaceVariant` 0xFF16161C. On OLED a black pixel emits no light, so battery saving depends on content and brightness [I; Google has stated figures at Android Dev Summit 2019 that I did not re-read]. Keep the current dark as default. `Appearances.DARK_SURFACE` is a single constant today [R]; the contrast function `primaryFor` takes it as input so it generalises.
2. **High contrast (S).** `expect fun systemHighContrast(): Boolean` (Android: the "high contrast text" secure setting [I]; iOS: `UIAccessibilityDarkerSystemColorsEnabled`). When on: stronger outlines (`outline` instead of `outlineVariant`), no glass alpha, 1.5 dp rims, chart marks get a 1 dp contrasting stroke.
3. **Dark and large text under test.** Extend `AccessibilityUiTest` to dark and to font scale 2.0 (Android runs to 200% on Android 14, and `HOME_OPTIONS.md` already measured those configurations by screenshot [R]); this is the missing evidence rather than a feature.
4. **Reduce motion** (3.1 delta 2).
5. **Chart accessibility (S, rank 12).**
   - Every chart gets a spoken summary built from the data, not a label: "Spending over the last 14 days: ₹18,420 in total, highest on Saturday at ₹4,100, today ₹320". `HomeSpark` says only "Spending over the last 14 days" today [R]. Compose `contentDescription` plus `stateDescription` for the focused point.
   - Step-through: semantics `customActions` ("Next day", "Previous day") or `progressBarRangeInfo` + `setProgress` so a TalkBack or VoiceOver user can move a point with swipe-up/down and hear "Tuesday, ₹210".
   - "Show as table" toggle under each chart in Reports: the drill-down rows already are that table [R], so the toggle can simply scroll to them.
   - Never colour alone: legend percentages and labels (exist for the donut [R]).
   - iPhone: VoiceOver's audio graphs use `AXChartDescriptor` on a native view; Compose does not expose it [I]; the custom actions above are the reachable substitute. Which of these CMP maps to `UIAccessibility` correctly in 1.12.1 is **not verified**; test on the owner's phone with VoiceOver on.
6. **Target size.** WCAG 2.2 "Target size (minimum)" is 24 css px at AA [A: https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html]; the app already reaches 48 dp everywhere [R: `HOME_OPTIONS.md` "what works"].

---

### 3.12 Perceived performance, and how to measure fluidity without new libraries

**What the repo already does well [R]:** stable `key` and `contentType` for list items; `animateItem()`; whole-list state held outside screens (`SearchFields`); optimistic by nature (local write about 7 ms p95 on an emulator, `RefreshBudgetTest` budgets 50 ms); single-flight loads; WAL; lazy paging with a trigger counted in entries not list positions; DataSignature re-read on resume. Cold start 280 to 300 ms and data composed about 700 ms on the emulator on a loaded host [R: `PERFORMANCE.md`].

**Perceived-performance deltas.**
1. **Instant first frame (S to M, rank 13).** `OpeningScreen` is a spinner [R]. Draw the real Home chrome (top area, month-card frame in the accent gradient, bar) immediately and let the numbers fade in; keep the last month total in a `settings` row so even the first frame can show a real figure (the person sees their number at once) [I: needs the figure persisted; the app already persists settings]. NN/g: under 0.1 s feels instant, under 1 s keeps the flow [A]. The audit says data composed takes 0.2 to 0.7 s [R], which is exactly the zone where a spinner is worse than a frame.
2. **Stable keys and content types everywhere** (done on Home; check Search, Reports drill-down, Bills, Categories lists [I: not audited]).
3. **Avoid recomposition storms:** the three offenders in 3.1 (`RollingAmount`, `TrendChart`, `WalletStack`), and the per-row `composed {}` modifier. Compiler: Kotlin 2.4.20 has strong skipping on by default (introduced as default in Kotlin 2.0.20 [I]); inspect Compose compiler reports for unstable classes carried by `RowUi`, `ReportUi` (flag `-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=...`, no library).
4. **Prefetch:** pre-warm the Add form's category chips and recent payees when Home is visible (`LedgerViewModel` already loads them); the Add form focus is 16 to 18 ms [R], so only the sheet's own animation is left to optimise (3.1).
5. **Defer work out of the animation window:** do not start a report aggregate while a screen transition runs; start it on `isTransitionActive == false` [I].
6. **Baseline profile:** Slice 111 made one and could not show a gain on a loaded Mac with an emulator, so it was not shipped [R]. The repeat on a real phone, as `PERFORMANCE.md` describes, is the right next step, because Compose first-use jank is a real-device phenomenon [I]. Not in this document's top 20 because it is a measurement, not a craft.

**Measuring fluidity (no new library):**

*Android.*
- Today's method: `dumpsys gfxinfo` janky-frame percentage and p50/p99 on an emulator with 100,000 entries [R: Slice 47, 67; `tools/measure_perf.py`]. Emulator GPU is software, so this is a best case.
- **In-app frame logger (debug builds only).** `Window.addOnFrameMetricsAvailableListener(listener, handler)` (platform API 24+, no library) gives per-frame durations: `TOTAL_DURATION`, `DRAW_DURATION`, `LAYOUT_MEASURE_DURATION`, `DEADLINE` (API 31+) [A: https://developer.android.com/reference/android/view/Window#addOnFrameMetricsAvailableListener(android.view.Window.OnFrameMetricsAvailableListener,%20android.os.Handler)]. Aggregate into p50, p90, p99, count over 16.7 ms and over 8.3 ms (120 Hz), and over 700 ms (frozen) [A: frozen frames definition, https://developer.android.com/topic/performance/vitals/render]; write one line to `Log` on each screen change and on a debug overlay. `androidx.metrics:metrics-performance` (JankStats) would do the same but is a new library, so it is out.
- Perfetto with FrameTimeline on Android 12+ for the exact cause of a slow frame; `adb` only, no app change [A: same vitals page].
- Compose-specific: Layout Inspector recomposition counts (Android Studio) on Home while the total rolls; compiler reports.
- Real data for free: if the app is on Play, Play Console's Android vitals reports slow and frozen frames for opted-in users with no code in the app [A: vitals/render page; whether this app's Play build is large enough to get data is [I]].
- Test on a low-end phone (2 GB, a Mali-G52-class GPU) with "Profile HWUI rendering" if one is to hand; the emulator will never show overdraw cost.

*iPhone.*
- **Xcode Instruments > Animation Hitches** template, run from Xcode on the owner's phone (a "hitch" is a frame that misses its slot; the doc "Understanding hitches in your app" [A: https://developer.apple.com/documentation/xcode/understanding-hitches-in-your-app, page content not readable here]). No code change.
- **In-app logger (debug).** A `CADisplayLink` from `platform.QuartzCore` in `iosMain` recording `targetTimestamp - timestamp` vs the previous frame; same p50/p90/p99 report as Android. No library. [I: standard CoreAnimation API; not built here.]
- **MetricKit** (`MXMetricManager`, `MXAnimationMetric` with scroll hitch time ratio) delivers daily on-device payloads without any server [A: https://developer.apple.com/documentation/metrickit/mxanimationmetric, page content not readable here; the scroll hitch ratio field is from memory, [I]]. It is first-party Apple, no third-party library, and needs no network.
- Set the 120 Hz opt-in check: `CADisableMinimumFrameDurationOnPhone` is already true [R]; without it iPhone Pro devices cap at 60 Hz for third-party apps [I].
- **There is no iPhone number for anything in `PERFORMANCE.md`**: cold start, scroll, add form. First deliverable of rank 8 is a table of the same measures on the owner's phone.

*Budgets to adopt* [I, tune on data]: touch response under 100 ms; screen transition frames under 16.7 ms p90 and under 8.3 ms p90 on 120 Hz devices for the 3 hero motions (tab glide, Add form, rolling digits); janky-frame ratio under 2% on the 100,000-entry scroll on a low-end phone, as today's emulator figure is 0.3 to 0.6%.

---

### 3.13 Microcopy and tone

**Principle.** Monzo's writing principles: "straightforward kindness, everyday magic, warm wit"; first question "what does my reader care about most?"; active voice; plain words (assistance to help, ensure to make sure); humour sparingly in-app [A: https://monzo.com/tone-of-voice]. Mailchimp's voice and tone guide: the same voice, tone shifts with the reader's mood, and a person dealing with money problems is not in a playful mood [A: https://styleguide.mailchimp.com/voice-and-tone/].

**Observation [R]:** the app's copy is accurate and long (many sentences explain limits in full). That is right for a settings page and heavy for a toast, a nudge or a button.

**Rules to add to the doc set:**
1. Toasts and Undo lines: at most 7 words and a number. "Added. Chai ₹20" not "The expense has been recorded in your ledger".
2. Buttons: a verb ("Add", "Pay now"), never "Submit", "OK".
3. Errors: say what to do first ("Type an amount to save"), then why.
4. One calm exclamation per month maximum; none in anything about debt, overdue or limits.
5. Numbers in copy use the same format as the figure beside them (₹1,698, not "about 1.7k").
6. Indian reader: lakh/crore wording in words ("1.2 lakh"), Indian date order, "bank text" not "SMS message" on iPhone where it is a Shortcut.
7. Empty-state taglines: what appears here and what makes it appear, 10 words.
8. Never moralise ("You overspent!"); report ("₹1,200 over Food").

Effort S (a copy audit of nudges, toasts and empty states).

---

## 4. Per-area summary card (principle, references, techniques, 60 to 120 fps, accessibility, effort)

| Area | Principle | 2 to 3 references | Exact Compose techniques | 60 to 120 fps and battery | Reduce-motion and a11y | Effort |
|---|---|---|---|---|---|---|
| 1 Motion | Feedback instant, spatial motion physical | M3 motion tokens [B], Apple Motion HIG [A], Android predictive back [A] | `Motion.FEEDBACK`, `spatial()`/`effects()`, `graphicsLayer {}`, `Modifier.offset {}`, `sharedBounds`, `NavigationBackHandler` progress | Draw-phase reads; no infinite animations | Crossfade substitute, live observation | S to M |
| 2 Haptics | One meaning per pattern, optional, rare | Apple HIG [A], Android haptics principles [A] | `Feel` enum, `performHapticFeedback` constants, iOS `UINotificationFeedbackGenerator` via `expect/actual`, `LocalHapticsEnabled` | No frame cost | Switch; never the only cue | S to M |
| 3 Charts | Few marks, direct labels, one selection model | Tufte sparklines [A], Few bullet graph [A], Datawrapper [C] | `drawWithCache`, `StrokeCap.Round`, `Path`, `ChartScrubber`, Machado CVD test | Cache `Path`, one `Animatable` | Summaries, step actions, table | S to L |
| 4 Type and numbers | Digits do not move | Robinhood Ticker [B], Monzo [A] | `fontFeatureSettings = "tnum"`, `AnnotatedString`, per-digit `graphicsLayer` | No recomposition per frame | WCAG 1.4.4, words for lakh | S to M |
| 5 Surfaces and icons | Glass only on navigation; vector over emoji | Apple Materials [A], M3 dynamic colour [A] | scrim gradient, `rememberGraphicsLayer` (gated), `dynamicColorScheme` actual | Blur gated by measurement | Reduce Transparency fallback | S to L |
| 6 Microinteractions | Acknowledge, then confirm; no confetti | Monzo [A], Mail row actions [C] | `SwipeToDismissBoxState.progress`, `PathMeasure` check, `snapshotFlow` | Draw-phase only | Complete state under Reduce motion | S |
| 7 Empty and first run | Starter content, one first action | Material empty states [A] | `drawWithCache` ghost line, checklist card | One-shot animations | Skippable, semantics hidden for art | S to M |
| 8 Home modules | User controls order; never hide new blocks | Revolut widgets [C], option C [R] | `detectDragGesturesAfterLongPress`, `animateItem` | Offset in layout phase | Up/down buttons kept (WCAG 2.5.7) | M |
| 9 Widgets and glance | Show one number, respect privacy | WidgetKit docs [A/C], Glance [R] | Swift accessory families, `ControlWidget`, `ShortcutManagerCompat` | Timelines not polling | Redaction while locked | M |
| 10 Large screens | Rail from 600 dp, pointer parity | M3 adaptive [A] | `NavigationRail`-style `HomeBar`, `hoverable` | Same | Keyboard exists | M |
| 11 Dark, contrast, text | Test the combinations, add black | HIG Materials [A], WCAG [A] | `ColorScheme` black variant, high-contrast `expect` | OLED power [I] | Dark and 200% tests | S |
| 12 Perceived performance | Show the frame first; measure on the phone | NN/g [A], Android vitals [A], Hitches [A] | `FrameMetrics`, `CADisplayLink`, MetricKit | Budgets in 3.12 | n/a | S to M |

---

## 5. Design tokens: proposed deltas to the existing theme (not a rewrite)

Existing facts to anchor on [R]: baseline Material 3 `lightColorScheme/darkColorScheme` with six accents; primary lightened to 4.5:1 in dark; corners found in use: 28 / 24 / 22 / 20 / 18 / 14 / 8 (`HOME_OPTIONS.md` finding 15); gaps 12 / 12 / 0 / 8 / 4; card outer margin 16 dp, day heading start 20 dp; touch targets 48 dp or more; `Motion` durations base 120 / 220 / 320 / 460 times scale 1.8.

**Spacing (4 dp grid, name the steps already in use):** `space.xs 4`, `s 8`, `m 12`, `l 16` (screen and card margin, as now), `xl 20` (day heading indent), `xxl 24`, `section 32`. Card to card 12 (already the dominant gap). Nothing new in layout; the token names exist so new code stops inventing 10 and 14.

**Radii (collapse 7 values to 5):** `radius.xs 8` (small bars, chips' inner), `s 14` (tiles inside cards, the account tile strip), `m 20` (entry cards, nudges, bar pill, budget cards: replaces 18, 20, 22, 24), `l 28` (hero, sheets, wallet cards at 22 can stay 22 only if the owner likes the look: otherwise 20 or 28), `full` (pills, chips, avatars at 34% of size stay). Entry card 22 to 20 and nudge 24 to 20 are the visible changes and fix audit finding 8 and 15.

**Elevation and surfaces:** `elev.0` flat content card (tint `surfaceVariant * 0.42` over `surface`, as now); `elev.1` chrome (`tonalElevation 3 dp`, as the bar); `elev.2` held or overlapping objects (shadow 8 dp: wallet stack cards, a dragged row); `elev.glass` the bar pill (the lit gradient, as now). Never shadow list rows.

**Durations and easings (delta to `Motion.kt`):**
- `FEEDBACK 90 ms` (new, never scaled): press, tick, colour flash.
- `QUICK 120 x scale` (as now).
- `STANDARD 220 x scale` (as now).
- `EMPHASIZED 320 x scale` (as now).
- `GLIDE 460 x scale` (as now) and `ADD_FORM_GLIDE = min(GLIDE, 400)` (new).
- Easings: `EaseOut` and `EaseIn` unchanged (equal to Material's emphasized decelerate and accelerate [B]); add `EaseStandard = CubicBezierEasing(0.2, 0, 0, 1)` for in-place changes (equal to Material's standard [B]).
- Springs: `spatial()` default (0.8, 380), `spatial(fast = true)` (0.6, 800), `spatialSlow` (0.8, 200), all stiffness divided by scale squared as today; `effects()` (1.0, 1600) unscaled [B]. Existing call sites map as: tab glass (0.72, 260) -> `spatial()` with its overshoot kept by passing 0.72; wallet (0.78, 300) -> `spatial()`; `pressScale` (0.6, 600) -> `spatial(fast = true)` unscaled.
- Default speed: leave "Relaxed 1.8x" as the owner's choice; surface "Quick" in the first-run tour as the option for people who find the app slow [I]. Do not change the default without a real-phone look.

**Type scale (keep Material; pin roles):** `Number.Hero` displaySmall 36 sp SemiBold `tnum` -0.5 sp tracking, capped at 54 sp at large text (exists); `Number.Row` bodyLarge SemiBold `tnum`; `Number.Label` labelMedium `tnum`; `Heading.Day` labelLarge on `onSurfaceVariant` (exists); `Caption.Chart` labelSmall 11 sp. Currency sign and paise at 55% of the digit size in the hero. No custom font until the `tnum` test is run.

**Colour roles (additions only):**
- `category.<slot>.{fill,onFill,container,onContainer}` x light/dark, 12 slots, precomputed constants (3.3, 3.5).
- `chart.palette[0..7]`: Okabe-Ito-based, +15% lightness in dark (3.3).
- `state.ok / near / over` for budgets: today `primary`, `0xFFE08A00`, `error`. Make `near` a named role with a dark-mode variant (amber at 4.5:1 on `0x141218`); keep the words ("near", "over") next to every colour.
- `amount.out / in` (exists in `amountColor`: light `B3261E`/`17744A`, dark `FF8A80`/`6FD3A0`): keep; add them to the colour-vision test so out and in stay distinguishable (red and green are the canonical confusion pair; the sign and the "+"/"-" already carry it [R: `signedAmount`]).
- `surface.black` variant for true-black dark (3.11).
- `glass.rim / top / bottom` alphas (0.35 / 0.26 / 0.10 of primary, as in `HomeBar`) as named values so the Reduce-Transparency path can switch them off in one place.

**Haptic roles:** `Feel { PRESS, SAVE, PAID, UNDO, THRESHOLD, STEP, WARN, LONG_PRESS, KEY }` with the table in 3.2.

---

## 6. Implementation order I would suggest (so each step can be felt and measured)

1. Rank 8 first (frame logger and the first iPhone baseline). Without it, items 2, 5, 9, 11 and 15 cannot be judged on a phone.
2. Rank 1 (tabular figures) with the 10-minute width test on both platforms.
3. Rank 2 and 3 together (Motion tiers and one haptic surface), then a felt pass on the owner's iPhone and a low-end Android.
4. Rank 4 and 14 (swipe and success moments): small, visible.
5. Rank 5 (odometer roll), rank 11 (shared-container cost), 6 (category identity).
6. Rank 9, 10, 12 (charts).
7. Rank 7 (predictive back) after checking the dependency question.
8. Larger: 15, 16, 17, 19.

---

## 7. Risks

| Risk | Where | Why | Mitigation |
|---|---|---|---|
| Jank from per-frame recomposition | `RollingAmount`, `TrendChart`, `WalletStack` (existing) and anything new that reads animated state in composition | Google's guidance: reads in composition recompose on every tick [A] | Draw/layout-phase reads; the frame logger |
| Jank from the shared container on every row | `Screens.kt` rows | per-row `composed {}` plus state [R][A] | Visible-only or tapped-row-only attach; scroll test on 100,000 rows |
| Backdrop blur cost | Glass v2 | content drawn twice and blurred per scrolled frame [I] | Debug flag, measure on a low-end phone and the iPhone; ship only under the 1 point jank budget; Android below 12 falls back |
| Battery | blur, infinite animations, widgets | GPU and wake-ups | No infinite loops except one-off skeletons; widgets already update hourly [R]; Lock Screen widgets use timelines, not polling |
| Haptic fatigue | scrub, press | overuse turns people to turn haptics off [A: Android haptics principles] | Throttle, one table, the switch; felt review |
| Haptic inconsistency across phones | Android actuators vary | [C] | Constants only, no custom waveforms; fallback to no haptic over buzzy |
| iOS parity of `tnum` and accessibility mapping | numbers, charts | unverified in CMP 1.12.1 [C] | Width test; VoiceOver test; fallback to a bundled digits font |
| iOS parity of haptics | `Feel` | CMP maps `HapticFeedbackType` internally; SegmentTick regression existed in 1.11.0-alpha04 [B] | `expect/actual` with direct UIKit generators for the ones that matter |
| Predictive back dependency | `NavigationBackHandler` | may be a new Gradle coordinate [I] | `:shared:dependencies` check; ask the owner if new |
| Dark and 200% text untested | everything new | ATF runs only in light, default size [R] | Extend `AccessibilityUiTest` to dark and font scale 2.0 |
| Over-delight | confetti, streaks, long motion | HIG: purposeful, optional, not on frequent actions [A]; streak criticism [C] | No streak counters, no confetti, Reduce motion crossfade |
| Test churn | changing tags or timing of motion | `MotionUiTest` and 100+ tests use `add`, `home_trip_card`, `month_spend` tags [R: `HOME_OPTIONS.md` section 6] | Keep tags; add new tests, do not weaken old ones |
| Resources artifact for a font | optional custom numeric font | may add a Compose resources artifact [I] | Only if `tnum` fails on iOS; owner decision |
| Free-signing limits on iPhone widgets/controls | Lock Screen widgets, controls | new extension capabilities under a free profile [I] | Try one on the owner's phone before building the rest |

---

## 8. What I could not verify (said plainly)

- Nothing was built, run, felt or measured. No device, emulator or simulator was used. Every "feel" claim (haptic mapping, spring settle times, glass legibility) is reading plus arithmetic. The settle times in 3.1 are my calculation from the standard second-order formula, not from a trace.
- Whether `fontFeatureSettings = "tnum"` is honoured by San Francisco through Compose on iOS in 1.12.1: only a practitioner note that says "not verified" [C].
- What Compose Multiplatform's iOS actual does for each `HapticFeedbackType` (which UIKit generator and style). I read the issue tracker (CMP-10050) and Apple's docs, not the Kotlin/Native source (the file path I guessed returned 404).
- Whether `navigationevent-compose` is already on this project's classpath; whether `NavigationBackHandler` gives interactive progress on iOS in this version in practice.
- Whether CMP maps Compose semantics (`customActions`, `stateDescription`, `progressBarRangeInfo`) to VoiceOver correctly in 1.12.1.
- Anything on Apple's pages that render with JavaScript only (the Hitches and MetricKit pages): I confirmed the URLs exist and relied on the page titles and memory for their content.
- `ExpressiveMotionTokens` and `MotionTokens` values come from `androidx-main` source; the app's Material 3 artifact (`compose.material3` through CMP 1.12.1) is based on a Jetpack Material 3 1.5.0 alpha [A: CMP 1.12.2 dependency table]; the exact tokens in that build were not checked.
- Whether R8 shrinks `materialIconsExtended` to the used icons on Android and what the iOS klib costs: not measured.
- Competitor details (Apple Card, Cash App, Revolut, Wealthfront, CRED, Monzo UI behaviour) come from search summaries and public design write-ups [C]; I did not install or record those apps. The only first-party sources for them here are Monzo's tone-of-voice page [A] and CRED's open NeoPOP repos (`github.com/CRED-CLUB/neopop-flutter`, which shows haptics on tap down and up in its button) [B]. No Wealthfront or Apple Card primary source was found, so no claim rests on them.
- Android 14 non-linear font scaling up to 200%, Android dynamic shortcut limits, `VIBRATE` permission need for `Vibrator`: standard knowledge, not re-read.
- Live Activity duration limits (about 8 h Dynamic Island, 12 h Lock Screen) are from a secondary source [C].
- I did not read the whole of `LedgerViewModel` or `ReportStore`, so the cost of a by-day-by-category aggregate is an estimate.
- OLED battery saving magnitude, and the claim that shadows cost more on Skia than on Android hardware layers: [I].
- Low-end Android behaviour: no data in the repo at all (all numbers are from API 26 and API 34 emulators on a loaded Mac) [R].

---

## 9. Sources

Official and primary ([A]/[B]):
- Apple HIG Motion: https://developer.apple.com/design/human-interface-guidelines/motion
- Apple HIG Playing haptics: https://developer.apple.com/design/human-interface-guidelines/playing-haptics
- Apple HIG Materials (Liquid Glass): https://developer.apple.com/design/human-interface-guidelines/materials
- Apple HIG Live Activities: https://developer.apple.com/design/human-interface-guidelines/live-activities
- Apple UIFeedbackGenerator: https://developer.apple.com/documentation/UIKit/UIFeedbackGenerator
- Apple lock screen widgets, controls, ActivityKit: https://developer.apple.com/documentation/widgetkit/creating-lock-screen-widgets-and-watch-complications , https://developer.apple.com/documentation/widgetkit/creating-controls-to-perform-actions-across-the-system , https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities
- Android HapticFeedbackConstants: https://developer.android.com/reference/android/view/HapticFeedbackConstants
- Android haptics principles: https://developer.android.com/develop/ui/views/haptics/haptics-principles
- Compose animation performance (defer reads, shared transitions): https://developer.android.com/agents/skills/jetpack-compose/jetpack-compose-performance/references/animation
- Compose shared elements: https://developer.android.com/develop/ui/compose/animation/shared-elements
- Android predictive back design: https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back
- Android render vitals and FrameMetrics: https://developer.android.com/topic/performance/vitals/render
- Adaptive navigation: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Material 3 motion and tokens: https://m3.material.io/styles/motion/overview/how-it-works , source `androidx/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/MotionTokens.kt` and `ExpressiveMotionTokens.kt` (androidx-main)
- Material transition patterns (skeleton): https://m3.material.io/styles/motion/transitions/transition-patterns
- Compose Multiplatform 1.10, 1.11, 1.12 release notes: https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html , https://kotlinlang.org/docs/multiplatform/whats-new-compose-111.html , https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html , 1.9: https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html
- CMP issue on iOS segment tick haptics: https://youtrack.jetbrains.com/issue/CMP-10050
- WCAG 2.2: https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html , .../use-of-color.html , .../non-text-contrast.html , .../target-size-minimum.html , .../resize-text.html
- NN/g response times: https://www.nngroup.com/articles/response-times-3-important-limits/
- Tufte sparklines: https://www.edwardtufte.com/notebook/sparkline-theory-and-practice-edward-tufte/
- Few, bullet graph spec: https://www.perceptualedge.com/articles/misc/Bullet_Graph_Design_Spec.pdf
- Okabe and Ito colour page: https://jfly.uni-koeln.de/color/
- Monzo tone of voice: https://monzo.com/tone-of-voice ; Mailchimp voice and tone: https://styleguide.mailchimp.com/voice-and-tone/
- Robinhood Ticker: https://github.com/robinhood/ticker ; CRED NeoPOP: https://github.com/CRED-CLUB/neopop-flutter

Secondary ([C]): Datawrapper donut guide https://academy.datawrapper.de/article/25-how-to-create-a-donut-chart ; pie versus bar study https://journals.sagepub.com/doi/10.1177/14738716241259432 ; Zarzara haptics write-up https://zarzara.app/blog/cross-platform-haptics-kotlin-multiplatform ; ScrollCounter write-up https://betterprogramming.pub/scrollcounter-an-ios-solution-to-the-robinhood-number-animation-bbcbd8c90355 ; tabular figures note https://dev.to/alanwest/tabular-numbers-in-css-font-variant-numeric-vs-monospace-hacks-25cn ; Revolut analytics help https://help.revolut.com/en-US/help/accounts/budget-and-analytics/what-is-the-analytics-dashboard/ ; Duolingo streak criticism https://www.ludaxis.io/blog/gamification-in-apps-duolingo-case-study-2026 , https://screenwiseapp.com/guides/duolingo-streaks-and-anxiety-in-kids ; Android 16 Live Updates https://android-developers.googleblog.com/2025/01/first-beta-android16.html ; Wear tiles https://developer.android.com/training/wearables/tiles/get_started ; Live Activities overview https://infinum.com/blog/live-activities-in-ios-apps/ .
