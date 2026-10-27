# Accessibility findings (automated, API 26 emulator)

Found by `androidApp/src/androidTest/kotlin/app/expenses/AccessibilityUiTest.kt`, which runs Google's Accessibility Test Framework (ATF 4.1.1, the "latest" check preset:
SpeakableTextPresent, TouchTargetSize, TextContrast, ImageContrast, RedundantDescription, DuplicateSpeakableText, TextSize, EditableContentDesc, TraversalOrder and the rest) over
nine walks: Home with entries, a budget and the getting-started card (folded and open); the Add form (empty, with the number pad, with an amount); Search (empty and with text typed);
To review with one item; Accounts (stacked and spread out) and the new-account dialog; Accounts with a credit card and the credit card dialog; the bill items dialog; More.
The first run only found things; the fixes that followed are listed under Status.

## What ran, and one thing that did not work as advertised
- Dependency: `androidx.compose.ui:ui-test-junit4-accessibility` 1.12.1 (the version of Compose BOM 2026.09.00; added as `androidTestImplementation` only, plus Guava 31.0.1-android
  `androidTestImplementation`, the version ATF already brings at run time, because the test names one of its types). It resolved online without trouble; offline resolution was not tried.
- **Compose's own `compose.enableAccessibilityChecks()` does not see the Compose screens on this device.** It runs (51 invocations in one run of the class), but it examines only the Android
  view tree (DecorView, ComposeView, AndroidComposeView, dialog windows, the ripple host views): all 4,099 results were `NOT_RUN`, ATF's own reasons being "not important for
  accessibility" and "not visible" for the Compose host view. No Compose semantics node was checked, and a deliberately bad control (a 20 dp clickable with no label)
  was not reported. Tried and measured on the API 26 emulator with Compose 1.12.1; whether it behaves the same on a newer Android was NOT tried (the only emulator image here is API 26,
  and the disk had about 6 GB free, too little to add another). It is left switched on in the test, so that a newer Compose or device that makes it see the nodes starts to be enforced.
- So the test also runs ATF itself on the accessibility NODE tree of every window of the app, read through `UiAutomation` (the tree TalkBack reads, with every Compose node in it),
  with a screenshot for the contrast checks. That is what produced the findings below. A calibration test (`theChecksReallyRunAndReportAKnownBadControl`) fails if that scan stops
  reporting an unlabeled 20 dp clickable, so a library update that silently turned the checks off cannot go unnoticed.
- Light theme only, default font size, a 1080x2340 px screen, soft keyboard shown where a field has focus. Dark mode, large text, other languages and TalkBack on hardware were not checked.
- The bill items dialog is shown the way `BillItemsUiTest` shows it (default Material 3 colours, not the app's own palettes: `colorSchemeFor` is private to `AppRoot`), so its contrast is not the app's.
  Its touch-target and label results are the app's.

## Status
All four ERRORs, the one real WARNING and the borderline contrast WARNING are fixed (see below), and so are the "+" button's missing name and, for New account, the keyboard covering Save and Cancel (Edit account and the billing-dates dialog are not: see observation 1). `AccessibilityUiTest.known` is now EMPTY: any ERROR fails the test.
Evidence: AccessibilityUiTest in guard mode, 9 tests OK, and in collect mode (nothing suppressed) the ERRORs the scan reports are only the calibration control's. The fixes were checked only on the
API 26 emulator, with ATF's numbers, not with TalkBack and not on a phone.

## ERRORs: 4 elements, all TouchTargetSizeCheck: FIXED
ATF wants 48 dp in both directions. All four were rows or buttons wide enough and a few dp short in height.

| # | Screen | Element | Was | Fix made |
|---|--------|---------|-----|----------|
| 1 | Search, with results | "Select all N" / "Clear selection" button (`search_select_all`) | 42 dp measured (the code asked for 32 dp) | `SearchScreen.kt`: the fixed `height(32.dp)` is gone, so the button is the Material minimum of 48 dp, and the count row lost its 2 dp top and bottom padding, so the row is 48 dp high instead of 36 (+12 dp on a screen of about 850 dp, and under the keyboard the list has 12 dp less room). Tag unchanged |
| 2 | Bill items dialog | The two radio rows "One entry for the whole bill" / "Each item as its own entry" (`bill_items_mode_one`, `bill_items_mode_each`) | 40 dp | `BillItemsUi.kt`: `heightIn(min = 48.dp)` and `selectable(role = Role.RadioButton)` instead of `clickable`, so a screen reader also says "radio button, selected". Tags unchanged |
| 3 | Accounts, cards stacked | The visible edge of a card behind the front one | 46 dp | `AccountTiles.kt`: `peek` 46 dp to 48 dp (a stack of N cards is 2 dp taller per extra card) |
| 4 | Home, getting-started card open | A finished step's row (`first_week_item_*`) | 44 dp (unfinished rows have a second line and were taller) | `FirstWeekCard.kt`: `heightIn(min = 48.dp)` on the row. Tag unchanged |

(The ERRORs the framework reports in a collect run are the calibration control's own, `SpeakableTextPresentCheck` and `TouchTargetSizeCheck`, which is deliberately bad; they are not counted.)

## WARNINGs (never fail the test)
First run: 11 reported. After the fixes: 6 remain, all false positives.
| Screen | Element | Check | Status |
|--------|---------|-------|--------|
| Bill items dialog | The three "Edit" buttons (`bill_item_edit_N`) | DuplicateSpeakableText ("Edit" identical on 3 buttons) | **FIXED.** `BillItemsUi.kt`: each button keeps the visible text "Edit" and its tag, and has the content description "Edit <item name>". The warning is gone from the collect run |
| Accounts, spread out | The small type label on the green Cash card ("Cash"), ratio 4.23 (needs 4.5) | TextContrast | **FIXED.** `AccountTiles.kt`: the label's white is 0.9 opaque instead of 0.8 (the colour constants are local: white on the 0xFF2E7D32 to 0xFF1B4D1F gradient). By WCAG's formula (my calculation, not a measurement) 0.9 white on the middle green is about 5.8:1; ATF no longer reports it in the collect run. The darker status lines on the card were already 0.9 |
| Add form (empty, and with the number pad) | "Save" (ratio 1.86) | TextContrast | **Left: false positive.** The button is disabled until there is an amount; WCAG exempts inactive controls from the contrast rule. With an amount typed it is not flagged |
| Account dialog (new account) | "Save" (1.08), "Cancel" (1.38) | TextContrast | **Gone with the keyboard fix** (the keyboard covered them; see observation 1). False positives for contrast |
| Credit card dialog (= Edit account) | "Cancel" (1.37), "Card network" (1.08) | TextContrast | **Left: false positives**, the keyboard covers them (observation 1: Edit account is not resized). "Save" in that dialog is no longer reported |
| Home (folded card) | "-₹1,840.00" (1.03), a 14 px sliver | TextContrast | **Left: false positive.** The list row is cut off by the bottom bar; ATF says "only partially visible within a scrollable container" |
| Home (card open) | "Cash -₹1,305.00" (4.33), at the very bottom of the screen | TextContrast | **Gone in the last run; was a false positive.** Only the top few pixels of the Cash tile showed above the bottom bar; the foreground colour ATF estimated was the card's own green. It depends on how far the list is scrolled, so it can come back |
| Search | "Money that came in" (1.04), a 6 px sliver at the keyboard's edge | TextContrast | **Left: false positive.** Cut off by the keyboard |

## What ATF did not flag but the screenshots show
1. **Account dialogs: Save and Cancel are under the on-screen keyboard.** FIXED for "New account" only; NOT fixed for "Edit account" and the billing-dates dialog (reasons below).
   Measured on the API 26 emulator with the first field focused and the keyboard up: the dialog window was not resized, so its buttons sat behind the keyboard (New account: Save at y 1521 to 1576 px
   with the keyboard starting at about y 1520; credit card y 2047; billing dates y 1664) and swiping inside the dialog did nothing, because the content fitted the unshrunk dialog. A person could
   reach Save only by closing the keyboard first (its Back or tick key).
   `Modifier.imePadding()` on the dialog (tried) changed nothing there: a dialog window does not get the keyboard inset on API 26. What works is the window's soft input mode "adjust resize":
   a new `ResizeDialogForKeyboard()` (`ui/DialogKeyboard.kt`, with an Android actual that sets it on the dialog's window and an iPhone actual that does nothing), called inside the New account
   dialog in `AccountsScreen.kt`. Measured afterwards: the dialog shrinks above the keyboard, its content scrolls, Save and Cancel stay in view (New account for a credit
   card: Save at y 1364; keyboard from about y 1640).
   **The billing-dates dialog had the call too and lost it in the 0.7.0 integration:** with it, `AlertsUiTest.aCreditLimitIsEnteredOnTheCardKeptShownAgainAndRemovedWithItsBillingDates` (taps "Remove billing dates" in that dialog) missed its tap in 5 of 12 runs alone on the API 26 emulator, 0 of 12 on the 0.6.2 commit (a00b138) and 0 of 12 after the call was removed. So, like Edit account, the keyboard covers that dialog's Save and Cancel while a field has focus (billing dates were y 1664 under a keyboard from about y 1640 before the call; the Back or tick key closes it).
   **Edit account is left as it was**, on purpose. Android raises the keyboard there by itself (the Name field takes focus) a second or more after the dialog opens, and with the resize the dialog then
   moves and shrinks while a person, or a test, is already tapping a button in it. Measured with `AccountsUiTest` (+ one `AccountFilterUiTest` test), 6 runs: with the resize on Edit account, taps were
   missed in 3 runs of 6 (one failure each: Archive or Delete not taking effect), 0 of 4 runs without it, and 0 of 6 with the final change; two earlier 134-test runs with the resize on Edit account also failed on such taps (Archive, Delete, "See its transactions on Home"). A cleaner
   fix needs a product decision: stop Edit account from raising the keyboard by itself (the first tap on Name would raise it), then the resize is safe there too. A window flag could not do it
   (`SOFT_INPUT_STATE_ALWAYS_HIDDEN` tried: Compose asks for the keyboard explicitly, so the flag has no effect). Until then, in Edit account the keyboard covers Save, Cancel, Archive and Delete
   while the Name field has focus; the keyboard's Back or tick key closes it. This is why "Credit card dialog" (which is Edit account for a card) still shows 2 TextContrast WARNINGs (Cancel, "Card network"):
   ATF measured the keyboard's grey behind them. Other dialogs with text fields (the limit dialog, the bill line editor, tag and category dialogs and so on) were NOT checked and may have the same
   problem; `ResizeDialogForKeyboard()` is the one-line fix for each, with the same caveat for any dialog that gets the keyboard by itself. The iPhone actual was not compiled here.
2. **The "+" button on Accounts had no real name.** FIXED. `AccountsScreen.kt`: the button has the content description "Add an account" (it still shows "+", and keeps `add_account`).

## Not covered
Screens not walked (Reports, Calendar, Bills, Goals, Loans, Subscriptions, every Settings screen, the Lock screens, the first-run tour and welcome card), every dialog other than those named,
dark mode, large font and display size, landscape and the two-pane layout, languages other than English, and TalkBack itself (`docs/REAL_DEVICE_CHECKLIST.md` lists what to do by hand).
