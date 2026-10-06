# Phase 1 progress: core ledger

Started 2026-10-06. Scope and exit criteria are in `ROADMAP.md`; requirement IDs are in `PRODUCT_CONTEXT.md`.

## Slice 1: fast entry (FR-CAP-01, FR-CAP-02 partly, FR-CAP-03)
**Built and verified on Android 8.0 (API 26) emulator:**
- **Remembered defaults.** The add screen starts with the category and account used last (stored in a device-local `setting` table, never synced). Device test: add with Transport and Bank, reopen, both are preselected.
- **Quick entries (templates).** "Save as a quick entry" on the add screen stores the entry; it then appears as a chip above the list. One tap records the expense; an **Undo** snackbar (about 10 s) takes it back (the entry is soft-deleted and its spend lines removed). Long-press a chip to remove it, with a confirmation. Most-used chips come first. Template edits are written to the sync log field by field; use counts and settings stay on the device. 7 storage tests plus a device test of tap, total update, undo.
- **Save is always reachable.** The form scrolls and Save sits in a bottom bar above the keyboard. (A new checkbox had pushed Save below the keyboard on short screens; found while diagnosing failing tests, which turned out to have a different cause, below.)
- **Launcher shortcut and Quick Settings tile.** Both open the add screen directly, with no list in between. Verified on the system: the static shortcut `add_expense` is registered with the shortcut service with the right intent; the tile appears in the Quick Settings panel and tapping it opens the add screen; firing the shortcut intent while the app is open shows the add screen (`singleTop`, `onNewIntent`). No data is accepted from the intent, only "open the add screen".
- **One database owner.** `AppContainer` now owns the encrypted database for the whole process (the screen, tile, and later widget and SMS receiver share it), opened once off the main thread. Writes in the view model are serialised.

**Lesson:** three device tests failed after the refactor, and my first theory (layout) was wrong. The real cause was that the process-wide container kept an open connection to a database file the test had deleted. The container now has `close()` and tests close it before deleting files.

**Test totals:** host/JVM tests all pass; 23 device tests pass (3 new quick-entry tests, 7 template store tests on the host).

**Not done in this slice (so FR-CAP-02 and FR-CAP-03 are not complete):**
- Home-screen widget (Glance).
- The "template without an amount" path (fills category, account, note and asks for the amount) exists in the app, but the add screen cannot create such a template yet, so it is untested on a device.
- No template edit screen; templates are created from the add screen and can only be removed.
- The one-time prompt that offers to add the tile (`requestAddTileService`, Android 13 and up) is not built; today users must add the tile by hand.
- No measurement yet of taps or seconds per entry. By count: add screen is FAB, amount, Save (2 taps plus typing); a quick entry is 1 tap.

**Verification record.** Commit `1bbdf9c` was made while `check` was red (Android lint rejected the pre-API-34 tile call); commit `1e53438` fixes that. After it, the whole chain was re-run from deleted result files (`check`, release build, device tests): **125 host/JVM tests (both targets counted) and 23 device tests on Android 8.0, all passing, build successful.** The counts above are from that run. From now on, verification and commit are chained so a failing step blocks the commit.

## Slice 2: home-screen widget (FR-CAP-02, Android part)
**Built:** a Glance widget, "Quick add", with one button per saved quick entry that has a fixed amount (up to 4, most used first) and a "+" button that opens the add screen. Tapping a button records the expense with no app launch and shows a toast. A line "Added Chai ₹20.00 · Undo" follows for **2 minutes**; after that the Undo button refuses (the app checks the entry's age, so a stale button can never delete something the user has accepted) and the line disappears at the next redraw. The widget redraws when a tap happens; the app also asks it to redraw so new quick entries appear. If the database key is lost the widget says to open the app.

**Shared logic.** The "record an expense, remember defaults, undo" logic now lives in one class (`EntryActions`) used by the screens and the widget. The write lock moved from the view model into `AppContainer.write`, so every writer in the process is serialised, as the store requires.

**Verified:**
- 9 widget tests. Widget content (buttons, "+", undo line, empty and locked messages) with Glance's test library; the undo window including a backwards clock; the tap callback against the real encrypted database (expense recorded with the right amount, note, and category total; Undo removes it and its totals); an Undo older than the window is refused and the entry stays; a missing quick entry changes nothing.
- **System side:** the system lists the provider (`getInstalledProvidersForPackage`). On **Android 17 (API 37)** the widget was bound through `AppWidgetManager` with the shell's permission, rendered with `update()`, and reported by the system; all 9 pass there. On Android 8 (API 26) 8 pass and the binding test is skipped: borrowing the permission needs Android 10 (API 29), and the `appwidget` shell command that could grant it does not exist on that image ("No shell command implementation"). **Widget binding and rendering through the system is therefore verified on newer Android only.**
- Totals on API 26: 32 device tests, 0 failures, 1 skipped.

**Not verified:** the widget has not been placed on a real launcher and looked at or tapped by hand (no launcher automation here); visual layout at different widget sizes and in dark mode is unchecked; only the first 4 quick entries show.

**Gaps this exposes:**
- **No way to delete or edit an entry in the app yet.** The undo window (2 minutes on the widget; about 10 seconds in the app) is the only way to take back a mistaken one-tap entry. Entry edit and delete come next.
- Quick entries without a fixed amount do not appear on the widget.
- Widget redraws only on a tap or when the app changes templates; there is no periodic refresh (not needed today).
