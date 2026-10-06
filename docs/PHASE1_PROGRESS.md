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
