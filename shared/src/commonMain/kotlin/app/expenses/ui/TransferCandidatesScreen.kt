package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.TransferCandidateRowUi
import app.expenses.TransferCandidatesUi
import app.expenses.TransferGroupUi

/**
 * Entries already in the ledger that look like money moving between the person's own accounts (cash taken out at an ATM, a move from one account to another, a transfer to oneself), found for
 * the person to turn into transfers. Nothing changes until "Make transfers" is pressed for the ticked rows of a group, and that is one step with one Undo. "Not a transfer" keeps a suggestion
 * from coming back. Rows start ticked only for cash withdrawals, and only when there is exactly one Cash account to move the money to.
 *
 * [onMake] gets the group key, the ticked rows' ids and the account to move them to (blank for a move between two accounts, which names both). [onNotOne] gets the group key and the row ids.
 */
@Composable
fun TransferCandidatesScreen(
    state: TransferCandidatesUi?, onBack: () -> Unit, onMake: (String, List<String>, String) -> Unit, onNotOne: (String, List<String>) -> Unit, onShowDismissed: () -> Unit,
    onCreateCash: () -> Unit, onUndo: (String) -> Unit, onUndoGone: (String) -> Unit,
) {
    // "key|id" for a ticked row, the keys of the groups whose ticks the person has touched (until then a group's own preselection stands), and "key|accountId" for a chosen destination:
    // plain strings survive a rotation
    var ticked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var touched by rememberSaveable { mutableStateOf(setOf<String>()) }
    var chosen by rememberSaveable { mutableStateOf(listOf<String>()) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val snackbar = rememberUndoSnackbar(state?.undo, onUndo, onUndoGone)
    fun selection(g: TransferGroupUi): List<String> {
        val ids = g.rows.map { it.id }
        return if (g.key in touched) ids.filter { "${g.key}|$it" in ticked } else if (g.preselect) ids else emptyList()
    }
    fun destination(g: TransferGroupUi): String? = chosen.firstOrNull { it.startsWith("${g.key}|") }?.substringAfter('|')?.takeIf { id -> g.destinations.any { it.id == id } } ?: g.suggested
    fun setSelection(g: TransferGroupUi, ids: Set<String>) { ticked = ticked.filterNot { it.startsWith("${g.key}|") }.toSet() + ids.map { "${g.key}|$it" }; touched = touched + g.key }

    Scaffold(
        topBar = { AreaTopBar("Transfer candidates", Area.MONEY, Icons.Outlined.CompareArrows, onBack, "transfer_candidates_back") },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { padding ->
        if (state == null) return@Scaffold
        if (creating) AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Create a Cash account?") },
            text = { Text("You have no Cash account yet. This adds one named Cash with nothing in it, so withdrawals have somewhere to go. You can rename or remove it under Accounts.") },
            confirmButton = { TextButton(onClick = { creating = false; onCreateCash() }, modifier = Modifier.testTag("transfer_candidates_create_cash_confirm")) { Text("Create Cash") } },
            dismissButton = { TextButton(onClick = { creating = false }, modifier = Modifier.testTag("transfer_candidates_create_cash_cancel")) { Text("Not now") } },
        )
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("transfer_candidates_list")) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Entries that look like money moving between your own accounts. Nothing changes until you press Make transfers, and Undo puts everything back.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).testTag("transfer_candidates_notice")) }
                    if (state.loading) Text("Looking through your entries…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).testTag("transfer_candidates_loading"))
                    else if (state.groups.isEmpty()) {
                        Text("Nothing found.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).testTag("transfer_candidates_empty"))
                        Text("No cash withdrawals, moves between your own accounts or transfers to yourself were found in your entries. This page looks again every time you open it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
            for (g in state.groups) {
                val picked = selection(g)
                val dest = destination(g)
                val destName = g.destinations.firstOrNull { it.id == dest }?.name
                item(key = "g_${g.key}") {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).testTag("transfer_candidates_group_${g.key}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${g.title} (${g.rows.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("transfer_candidates_title_${g.key}"))
                        Text(g.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(g.rows, key = { "r_${g.key}_${it.id}" }) { r ->
                    CandidateRow(r, g.key, r.id in picked, destName) { on -> setSelection(g, if (on) picked.toSet() + r.id else picked.toSet() - r.id) }
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onNotOne(g.key, listOf(r.id)) }, modifier = Modifier.testTag("transfer_candidates_not_${r.id}")) { Text("Not a transfer") }
                    }
                    HorizontalDivider()
                }
                item(key = "f_${g.key}") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (g.destinations.isNotEmpty()) {
                            var menu by rememberSaveable(g.key) { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (g.key == "cash") "Move the money to" else "Other account", style = MaterialTheme.typography.bodyMedium)
                                androidx.compose.foundation.layout.Box {
                                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.testTag("transfer_candidates_dest_${g.key}")) { Text(destName ?: "Choose an account") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        g.destinations.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { menu = false; chosen = chosen.filterNot { it.startsWith("${g.key}|") } + "${g.key}|${a.id}" }, modifier = Modifier.testTag("transfer_candidates_to_${g.key}_${a.name}")) }
                                    }
                                }
                            }
                            if (g.canCreateCash) TextButton(onClick = { creating = true }, modifier = Modifier.testTag("transfer_candidates_create_cash")) { Text("Create a Cash account") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            val all = picked.size == g.rows.size
                            OutlinedButton(onClick = { setSelection(g, if (all) emptySet() else g.rows.map { it.id }.toSet()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("transfer_candidates_all_${g.key}")) { Text(if (all) "Clear all" else "Select all") }
                            Button(
                                onClick = { onMake(g.key, picked, dest.orEmpty()) }, enabled = picked.isNotEmpty() && (g.key == "own" || dest != null),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("transfer_candidates_make_${g.key}"),
                            ) { Text(if (picked.isEmpty()) "Make transfers" else "Make ${picked.size} ${if (picked.size == 1) "transfer" else "transfers"}") }
                            OutlinedButton(onClick = { onNotOne(g.key, picked) }, enabled = picked.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp).testTag("transfer_candidates_not_selected_${g.key}")) { Text("Not a transfer") }
                        }
                    }
                    HorizontalDivider()
                }
            }
            if (state.dismissed > 0) item(key = "dismissed") {
                TextButton(onClick = onShowDismissed, modifier = Modifier.padding(8.dp).testTag("transfer_candidates_show_dismissed")) {
                    Text("Show the ${state.dismissed} you marked as not ${if (state.dismissed == 1) "a transfer" else "transfers"}")
                }
            }
        }
    }
}

/** One suggestion: a tick box row (the whole row toggles, as one control for a screen reader), with where the money moves from and to. */
@Composable
private fun CandidateRow(r: TransferCandidateRowUi, group: String, on: Boolean, destName: String?, onToggle: (Boolean) -> Unit) {
    val other = if (group == "own") r.toName else destName ?: "…"
    val route = if (r.outgoing) "${r.fromName} → $other" else "$other → ${r.fromName}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = on, role = Role.Checkbox, onValueChange = onToggle).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp).testTag("transfer_candidates_row_${r.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp).testTag("transfer_candidates_check_${r.id}"))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.testTag("transfer_candidates_title_${r.id}"))
            Text("${r.dateText} · $route", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("transfer_candidates_route_${r.id}"))
            Text(r.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(r.amountText, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).testTag("transfer_candidates_amount_${r.id}"))
    }
}
