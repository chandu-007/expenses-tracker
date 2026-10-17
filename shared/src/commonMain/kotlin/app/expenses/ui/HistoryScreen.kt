package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.HistoryUi

/**
 * Everything that happened to one entry, newest first: when it was created, what each edit changed (old value to new), when it was
 * deleted and brought back. An edit can be undone, which puts the earlier version back as a new edit, so nothing is lost either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: HistoryUi, onBack: () -> Unit, onRestore: (Int) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("History: ${history.title}", maxLines = 1) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("history_back")) { Text("Back") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("history_list")) {
            items(history.revisions, key = { it.index }) { r ->
                Column(Modifier.fillMaxWidth().padding(16.dp).testTag("revision_${r.index}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${r.what} · ${r.whenText}", style = MaterialTheme.typography.titleSmall)
                    r.lines.forEach { l ->
                        Text(
                            if (l.before == null) "${l.label}: ${l.after ?: "none"}" else "${l.label}: ${l.before} → ${l.after ?: "none"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (r.canRestore) OutlinedButton(onClick = { onRestore(r.index) }, modifier = Modifier.testTag("restore_${r.index}")) { Text("Undo this edit") }
                }
                HorizontalDivider()
            }
        }
    }
}
