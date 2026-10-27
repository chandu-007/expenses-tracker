package app.expenses.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun ResizeDialogForKeyboard() {
    val view = LocalView.current
    val window = ((view as? DialogWindowProvider) ?: (view.parent as? DialogWindowProvider))?.window
    if (window != null) SideEffect { window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
}
