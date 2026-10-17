package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.SimilarUi

/**
 * "These earlier entries look like the one you just filed: file them the same way?" Everything is the person's decision: move them all, go through them one
 * at a time (move or skip each), or leave them. Nothing changes until they say so, and the move is one change that Undo takes back.
 */
@Composable
fun SimilarDialog(offer: SimilarUi, onApply: (List<String>) -> Unit, onDismiss: (stopAsking: Boolean) -> Unit) {
    var reviewing by remember(offer) { mutableStateOf(false) }
    var index by remember(offer) { mutableIntStateOf(0) }
    val chosen = remember(offer) { mutableStateListOf<String>() }
    val n = offer.rows.size
    if (!reviewing) {
        val where = offer.rows.groupingBy { it.fromCategory }.eachCount().entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }
        AlertDialog(
            onDismissRequest = { onDismiss(false) },
            title = { Text("Same as \"${offer.label}\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${if (n == 1) "1 earlier entry looks" else "$n earlier entries look"} like this one and ${if (n == 1) "is" else "are"} filed elsewhere ($where). File ${if (n == 1) "it" else "them"} under ${offer.categoryName} too?", modifier = Modifier.testTag("similar_text"))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onDismiss(false) }, modifier = Modifier.testTag("similar_not_now")) { Text("Not now") }
                        TextButton(onClick = { onDismiss(true) }, modifier = Modifier.testTag("similar_stop")) { Text("Stop asking about this") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onApply(offer.rows.map { it.id }) }, modifier = Modifier.testTag("similar_all")) { Text(if (n == 1) "Move it" else "Move all $n") } },
            dismissButton = { if (n > 1) TextButton(onClick = { reviewing = true }, modifier = Modifier.testTag("similar_review")) { Text("Review one by one") } },
            modifier = Modifier.testTag("similar_dialog"),
        )
    } else {
        val row = offer.rows[index]
        fun next(move: Boolean) {
            if (move) chosen += row.id
            if (index + 1 >= n) onApply(chosen.toList()) else index++
        }
        AlertDialog(
            onDismissRequest = { onDismiss(false) },
            title = { Text("${index + 1} of $n", modifier = Modifier.testTag("similar_progress")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("similar_row_title"))
                    Text("${row.dateText} · ${row.amountText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Now in ${row.fromCategory}. Move to ${offer.categoryName}?")
                    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            val rest = offer.rows.drop(index).map { it.id }
                            onApply(chosen.toList() + rest)
                        }, modifier = Modifier.testTag("similar_rest")) { Text("Move all the rest") }
                        TextButton(onClick = { onApply(chosen.toList()) }, modifier = Modifier.testTag("similar_done")) { Text("Done (${chosen.size} chosen)") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { next(true) }, modifier = Modifier.testTag("similar_yes")) { Text("Move") } },
            dismissButton = { TextButton(onClick = { next(false) }, modifier = Modifier.testTag("similar_skip")) { Text("Skip") } },
            modifier = Modifier.testTag("similar_dialog"),
        )
    }
}
