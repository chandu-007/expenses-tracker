package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.UndoUi
import app.expenses.core.domain.AmountMask
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What the bar says now: [text], and an optional [action] (the Undo of a repeating item), which takes its own touches and nothing else's. */
private class Bar(val text: String, val actionLabel: String? = null, val onAction: () -> Unit = {})

/**
 * Wraps the screens ([content]) and says the view model's one-line [message] (what an action just did: "Added to your bills", "Notifications are blocked for this app") while a screen other than Home is
 * open. Home says it itself in its own bar; every other screen had nowhere to show it, so the sentence waited for Home and was seen late or never. [onShown] clears it, as Home does.
 *
 * The bar is a row **under** the screen, not over it: while it is up the screen is given the room that is left (and the navigation-bar inset moves to the bar), so it covers no
 * button, text or tab bar, and takes no touch outside its own Undo button. (A Material snackbar drawn over the screen swallowed every touch on its area; a bar drawn over it covered
 * Mark paid and Remove at a large font.) It goes when Home takes over, which says things in its own bar.
 */
@Composable
fun MessageBarHost(
    message: String?, homeShowing: Boolean, hideAmounts: Boolean, onShown: () -> Unit,
    /** An Undo to offer here instead of Home (see the caller for which screens), its action and the bar going away. */
    undo: UndoUi? = null, onUndo: (UndoUi) -> Unit = {}, onUndoDismissed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var said by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var bar by remember { mutableStateOf<Bar?>(null) }
    var acted by remember { mutableStateOf(false) }
    // taken off the view model's state at once (as Home does), and drawn from a copy: clearing the state must not end the bar that is showing it
    LaunchedEffect(message, homeShowing) {
        if (homeShowing || message == null) return@LaunchedEffect
        said = ((said?.first ?: 0) + 1) to message
        onShown()
    }
    LaunchedEffect(said) {
        val (_, text) = said ?: return@LaunchedEffect
        bar = Bar(if (hideAmounts) AmountMask.mask(text) else text)
        delay(MESSAGE_MILLIS)
        bar = null
    }
    val feel = rememberFeel()
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        acted = false
        bar = Bar(if (hideAmounts) AmountMask.mask(u.text) else u.text, "Undo") { acted = true }
        withTimeoutOrNull(UNDO_MILLIS) { snapshotFlow { acted }.first { it } }
        if (acted) { feel(Feel.UNDO); onUndo(u) }
        bar = null
        onUndoDismissed()
    }
    // back on Home (which says things in its own bar) the bar of the screen just left goes with it
    LaunchedEffect(homeShowing) { if (homeShowing) bar = null }
    val b = bar
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().then(if (b != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) { content() }
        if (b != null) Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(8.dp)).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }.testTag("message_host"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(b.text, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 6.dp).testTag("message_text"))
            if (b.actionLabel != null) TextButton(onClick = b.onAction, modifier = Modifier.testTag("message_action")) { Text(b.actionLabel, color = MaterialTheme.colorScheme.inversePrimary) }
        }
    }
}

private const val MESSAGE_MILLIS = 4_000L
private const val UNDO_MILLIS = 10_000L
