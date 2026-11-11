package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.PayeeAliasUi

/** What the "Same shop as..." screens need: the aliases the person made, how to make one (names as the person reads them; answer null or why not) and how to remove one (by its key). */
class AliasControls(val aliases: List<PayeeAliasUi>, val onJoin: (from: String, to: String, done: (String?) -> Unit) -> Unit, val onRemove: (String) -> Unit)

/**
 * "[fromName] is the same shop as...": pick another name or type it, and the two count as one shop from then on (what the app learned, what earlier entries say, a search, the subscription finder
 * and the Merchant report); no entry is rewritten, and removing the alias puts everything back. [fromName] null asks for both names (the button on the learned list that is not tied to a row).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SameShopDialog(fromName: String?, suggestions: List<String>, onJoin: (from: String, to: String, done: (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var from by rememberSaveable { mutableStateOf(fromName.orEmpty()) }
    var to by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        modifier = Modifier.padding(bottom = dialogKeyboardRoom()),
        onDismissRequest = onClose,
        title = { Text(if (fromName != null) "$fromName is the same shop as…" else "Count two names as one shop") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Both names then count as one for what the app learns, for search, for subscriptions and for the Merchant report. Your entries are not changed, and you can undo it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fromName == null) OutlinedTextField(from, { from = it; problem = null }, label = { Text("This name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("same_shop_from"))
                OutlinedTextField(to, { to = it; problem = null }, label = { Text(if (fromName == null) "Same shop as" else "Name of the other shop") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("same_shop_to"))
                if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEachIndexed { i, s -> AssistChip(onClick = { to = s; problem = null }, label = { Text(s) }, modifier = Modifier.testTag("same_shop_suggest_$i")) }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("same_shop_problem")) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(from.trim(), to.trim()) { p -> if (p == null) onClose() else problem = p } }, enabled = from.isNotBlank() && to.isNotBlank(), modifier = Modifier.testTag("same_shop_ok")) { Text("Count as one shop") }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("same_shop_cancel")) { Text("Cancel") } },
    )
}

/** One alias in the list under "What the app has learned": "Swiggy Instamart counts as Swiggy" and Remove. */
@Composable
internal fun AliasRow(a: PayeeAliasUi, onRemove: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("alias_row_${a.from}"), verticalAlignment = Alignment.CenterVertically) {
        Text("${a.fromLabel} counts as ${a.toLabel}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag("alias_text_${a.from}"))
        TextButton(onClick = { onRemove(a.from) }, modifier = Modifier.testTag("alias_remove_${a.from}")) { Text("Remove") }
    }
}
