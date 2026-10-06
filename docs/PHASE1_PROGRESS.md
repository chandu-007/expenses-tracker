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

## Slice 3: edit, delete, dates (completes the entry-correction gap)
**Built:**
- **Tap a row to edit** amount, category, account, note and date, on the same form as Add (one form, two uses; the "save as quick entry" option appears only when adding). The field starts with the stored amount as plain digits (`1234567.50`), and saving reopens the list at the same scroll depth (the list no longer jumps back to the top after a change).
- **Swipe a row left to delete**, with an **Undo** that restores it (its totals come back too). The earlier Undo behaviour for new entries is unchanged.
- **A date button on the form** (date picker), so past expenses can be added and any entry moved to another day. The time of day is kept. Moving an entry to another month takes it out of this month's total.
- **Only simple expenses are editable.** Entries that are shared with others, split across categories, have a foreign-currency amount, have refunds, are not rupees, or are not expenses open a read-only screen that says why, so an edit can never silently drop information.
- **Editing keeps where an entry came from.** Source and source reference (the SMS or bank reference) are preserved, so editing a captured expense cannot make it be captured again as a duplicate. Tested with an SMS-sourced entry.

**Verified (Android 8.0 emulator):** 7 new screen and action tests (edit and save changes the total and category; cancel changes nothing; swipe-delete then Undo; an expense with refunds is read-only with the reason; moving to another month; SMS source kept; deleted entries are not editable and restore works), plus `toPlainString`/date helper tests (property test that plain amounts round-trip; a daylight-saving gap).

**Found and fixed along the way:**
1. **The database was in the default rollback-journal mode.** Measured on the emulator with SQLCipher: a save took a median of 1.9 ms (p95 2.9 ms) on an empty database, versus **0.32 ms (p95 0.50 ms) in write-ahead-log mode**. WAL is now on, with a test that pins the mode and shows a reader is not blocked by an open write. This also lets the screen read while the widget (or later an SMS receiver) writes. After enabling it, the gate's save p95 on the 100k-row database is **4.7-8.5 ms over 4 runs (limit 12.5 ms)**, median 0.36-0.41 ms.
2. **The performance gate's cleanup ran only if the speed assertion passed**, so one failure made a second test (dataset size) fail too. It now cleans up in a `finally`. That second failure was a consequence, not a separate bug.
3. An older quick-entry test broke because the new date button pushed its checkbox below the keyboard; the test now scrolls to it (the form scrolls for real users).

**Open and unexplained:** on the 100k-row gate database, **one save in 60 takes about 100 ms (98-116 ms in 6 runs), and it is always the same one, save number 11.** It is not the first save and it was not fixed by checkpointing after a bulk load (tried and reverted). The budget (p95) passes, saves run off the main thread, and the spike is below perceptual annoyance, so I am not blocking on it, but the cause is unknown. Candidate causes not yet tested: an SQLite page-cache spill, a log merge triggered by the connection pool, or emulator I/O.

**Not done:** editing of split/shared/foreign/refunded entries; editing income or transfers (there is no screen to create them yet either); a bulk-select mode; the date picker is not covered by a screen test (only the date logic is).

**Totals:** 133 host/JVM tests (both targets counted), 40 device tests on Android 8.0 (1 skipped: widget binding, Android 10+), full chain run from deleted result files, build green.
