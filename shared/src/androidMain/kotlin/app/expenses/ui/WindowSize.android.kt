package app.expenses.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@Composable
internal actual fun platformWindowSizeDp(): DpSize? {
    val c = LocalConfiguration.current
    return DpSize(c.screenWidthDp.dp, c.screenHeightDp.dp)
}
