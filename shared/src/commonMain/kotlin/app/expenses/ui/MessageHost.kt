package app.expenses.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.AmountMask

/**
 * Says the view model's one-line [message] (what an action just did: "Added to your bills", "Notifications are blocked for this app") while a screen other than Home is
 * open. Home says it itself in its own bar; every other screen had nowhere to show it, so the sentence waited for Home and was seen late or never. [onShown] clears it, as Home does.
 */
@Composable
fun MessageOnOtherScreens(
    message: String?, homeShowing: Boolean, hideAmounts: Boolean, onShown: () -> Unit,
    /** An Undo to offer here instead of Home (see the caller for which screens), its action and the bar going away. */
    undo: app.expenses.UndoUi? = null, onUndo: (app.expenses.UndoUi) -> Unit = {}, onUndoDismissed: () -> Unit = {},
) {
    val host = remember { SnackbarHostState() }
    // taken off the view model's state at once (as Home does), and drawn from a copy: clearing the state must not end the bar that is showing it
    var said by remember { androidx.compose.runtime.mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(message, homeShowing) {
        if (homeShowing || message == null) return@LaunchedEffect
        said = ((said?.first ?: 0) + 1) to message
        onShown()
    }
    // back on Home (which says things in its own bar) the bar of the screen just left goes with it
    LaunchedEffect(homeShowing) { if (homeShowing) host.currentSnackbarData?.dismiss() }
    LaunchedEffect(said) {
        val (_, text) = said ?: return@LaunchedEffect
        host.showSnackbar(if (hideAmounts) AmountMask.mask(text) else text, duration = SnackbarDuration.Short)
    }
    val feel = rememberFeel()
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        val result = host.showSnackbar(if (hideAmounts) AmountMask.mask(u.text) else u.text, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) { feel(Feel.UNDO); onUndo(u) }
        onUndoDismissed()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(host, Modifier.navigationBarsPadding().padding(bottom = 72.dp).testTag("message_host")) // above a bottom button row, not over it
    }
}
