package app.expenses.ui

import androidx.compose.runtime.Composable

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
