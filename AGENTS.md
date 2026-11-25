# Working in this repository

Expense tracker: Kotlin Multiplatform with a shared Compose UI (`shared/src/commonMain/kotlin/app/expenses/ui`), Android app (`androidApp`), iPhone app (`iosApp`, widgets in Swift), domain, storage and parsers in `core/`. Product context: `docs/PRODUCT_CONTEXT.md`. Work log: `docs/PHASE2_PROGRESS.md`.

## Current work: the redesign (releases 0.17 to 0.20)

- The spec is `docs/REDESIGN.md`. Read it before touching any screen: decisions D1 to D6, the rules in section 1, the screen tables and the release plan in section 8.
- Exact values (colours, sizes, radii, gaps, copy) are in `docs/redesign/screens/<Board>.dc.html`, in the inline styles and text; board data is in `renderVals()` at the bottom of each file. Pictures of every board: `../expenses_builds/redesign_final/png/`. Build in Compose with the app's own components; never port the HTML structure.
- Work on a branch per release (`redesign-0.17`, …), never directly on `main`.
- One slice at a time, in the order of `docs/REDESIGN.md` section 8. Stop after each slice and show the owner the before and after screenshots before starting the next.

## Rules that are not negotiable

1. Keep every existing `testTag`. Tests find screens by tag (`add` alone is used in about 100 test files). A new control gets a new tag; a removed control's tests are moved, not deleted.
2. Colours come from the theme and the tokens in `ui/Tokens.kt` (and `DesignTokens` in core). Never add a new `Color(0x…)` in a screen, a new radius or a new gap.
3. Touch targets 48 dp minimum. Check every changed screen at 393 dp and 360 dp, light and dark, 100%, 150% and 200% text, and with Hide amounts on. Nothing clipped, nothing wider than the window.
4. Home and the nudges keep the rules in `docs/HOME_NUDGES.md` and the Slice 112 decisions in `docs/HOME_OPTIONS.md` unless `docs/REDESIGN.md` changes them on purpose.
5. Nothing may add a query to Home open or the entry list. For a change to Home or the list, run the timing A/B described in `docs/PERFORMANCE.md`.
6. The app sends nothing anywhere and asks for no new permission. Fonts are bundled, never downloaded.

## Checks before a slice is done

1. `./gradlew check` passes.
2. The UI tests of every screen the slice touched pass on the emulator (API 34; API 26 for keyboard and dialog work), set up with `tools/emulator_setup.sh`, one emulator at a time.
3. Before and after screenshots of each changed screen in the states of rule 3, saved outside the repository under `../expenses_builds/redesign_build/<slice>/`.
4. The slice is logged in `docs/PHASE2_PROGRESS.md` in the same form as Slices 107 to 128: what was built, what ran, what was not verified, owner decisions.
