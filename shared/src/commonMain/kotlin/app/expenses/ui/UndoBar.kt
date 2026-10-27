package app.expenses.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.expenses.ScreenUndo

/**
 * The state of a snackbar that offers [offer] with an "Undo" button, the way Home's list does for a deleted entry. Put it in the screen's
 * Scaffold (`snackbarHost = { SnackbarHost(host) }`). Pressing Undo calls [onUndo]; whichever way the bar goes (Undo, timeout, swiped away) [onGone]
 * follows, so the screen's owner can let go of what it kept for the Undo. A newer offer replaces the bar of the older one without calling [onGone] for it.
 */
@Composable
fun rememberUndoSnackbar(offer: ScreenUndo?, onUndo: (String) -> Unit, onGone: (String) -> Unit): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val feel = rememberFeel()
    LaunchedEffect(offer) {
        val o = offer ?: return@LaunchedEffect
        val result = host.showSnackbar(o.text, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) { feel(Feel.UNDO); onUndo(o.id) }
        onGone(o.id)
    }
    return host
}
