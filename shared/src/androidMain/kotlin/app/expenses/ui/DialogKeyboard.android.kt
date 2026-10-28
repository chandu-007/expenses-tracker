package app.expenses.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun ResizeDialogForKeyboard() {
    val view = LocalView.current
    val window = ((view as? DialogWindowProvider) ?: (view.parent as? DialogWindowProvider))?.window
    if (window != null) SideEffect { window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
}

@Composable
actual fun dialogKeyboardRoom(): Dp {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // A phone's keyboard is about 250 to 280 dp tall whatever the screen. Never more than 45% of the window: a short window (landscape, split screen) has no room to give.
    return minOf(280.dp, windowHeight * 0.45f)
}
