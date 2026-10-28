package app.expenses.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Call inside a dialog's content: makes the dialog's window shrink above the on-screen keyboard instead of being covered by it. Without this a dialog that has a text field and
 * buttons (Save, Cancel) lost the buttons behind the keyboard while the field had focus: the window was not resized, so the dialog's own scrolling had nothing to scroll.
 * Android: the dialog window's soft input mode becomes "adjust resize". iPhone: nothing to do (the root view already makes room for the keyboard).
 *
 * Note for dialogs that Android gives the keyboard to by itself (the first text field takes focus when the dialog opens): it comes up a second or more after the dialog appears, and the
 * dialog then moves and shrinks to make room. A test that taps a button inside such a dialog in that moment can miss it (see the Edit account dialog in AccountsScreen.kt).
 */
@Composable
expect fun ResizeDialogForKeyboard()

/**
 * Room to keep free at the bottom of a dialog that has text fields and buttons (Save, Cancel): pass it as `Modifier.padding(bottom = ...)` on the dialog. The dialog then ends above where
 * the keyboard will be, its content scrolls inside, and the buttons are never behind the keyboard. The amount is fixed, not measured: nothing in the dialog moves or resizes when the keyboard
 * comes or goes (a window that resized when the keyboard came a second after the dialog opened made taps miss, see [ResizeDialogForKeyboard]). Android: a keyboard's height, capped for a short
 * window. iPhone: 0 (the root view already makes room for the keyboard).
 *
 * Tried and rejected: giving the room only while `WindowInsets.isImeVisible`. A dialog's window is not resized, so inside it the keyboard is never reported as visible (a screenshot with the
 * keyboard up showed Save and Cancel still hidden), and the keyboard's height read from the activity would move the dialog when the keyboard comes, as the resizing did.
 */
@Composable
expect fun dialogKeyboardRoom(): Dp
