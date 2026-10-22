package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.GoalRowUi
import app.expenses.GoalsUi

/** Things to save up for: each with how far along it is and, with a date, what to put aside each month. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(
    state: GoalsUi?, onBack: () -> Unit,
    onSave: (id: String?, name: String, target: String, months: String, accountId: String?, done: (String?) -> Unit) -> Unit,
    onAdjust: (id: String, amount: String, add: Boolean, done: (String?) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // a goal's id, or "" for a new one
    var adjusting by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = state?.rows.orEmpty()
    editing?.let { id -> GoalDialog(rows.firstOrNull { it.id == id }, state, onSave, { editing = null }) }
    adjusting?.let { id -> rows.firstOrNull { it.id == id }?.let { AdjustDialog(it, onAdjust) { adjusting = null } } }
    deleting?.let { id -> rows.firstOrNull { it.id == id }?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Remove \"${g.name}\"?") }, text = { Text("Only the goal goes. No entries or accounts are touched.") },
            confirmButton = { TextButton(onClick = { onDelete(g.id); deleting = null }, modifier = Modifier.testTag("goal_delete_ok")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    } }
    Scaffold(topBar = { AreaTopBar("Savings goals", Area.MONEY, Icons.Outlined.Flag, onBack, "goals_back") }) { padding ->
        if (state == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("goals_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rows.isEmpty()) Text("Nothing to save for yet. A goal is a target, like a trip or a laptop. Put money towards it by hand, or let it follow a savings account.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("goals_empty"))
                    Button(onClick = { editing = "" }, modifier = Modifier.testTag("goal_new")) { Text("New goal") }
                }
            }
            items(rows, key = { it.id }) { g ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("goal_${g.name}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${g.percent}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("goal_percent_${g.name}"))
                    }
                    LinearProgressIndicator(progress = { g.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${g.savedText} of ${g.targetText} · ${g.leftText}" + if (g.dueText.isNotEmpty()) " · ${g.dueText}" else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("goal_line_${g.name}"))
                    if (g.monthlyText.isNotEmpty()) Text(g.monthlyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("goal_monthly_${g.name}"))
                    if (g.accountName.isNotEmpty()) Text("Follows ${g.accountName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (g.accountId.isEmpty()) TextButton(onClick = { adjusting = g.id }, modifier = Modifier.testTag("goal_add_${g.name}")) { Text("Add or take out") }
                        TextButton(onClick = { editing = g.id }, modifier = Modifier.testTag("goal_edit_${g.name}")) { Text("Edit") }
                        TextButton(onClick = { deleting = g.id }, modifier = Modifier.testTag("goal_remove_${g.name}")) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    goal: GoalRowUi?, state: GoalsUi?,
    onSave: (String?, String, String, String, String?, (String?) -> Unit) -> Unit, onClose: () -> Unit,
) {
    var name by rememberSaveable(goal?.id) { mutableStateOf(goal?.name.orEmpty()) }
    var target by rememberSaveable(goal?.id) { mutableStateOf(goal?.targetPlain.orEmpty()) }
    var months by rememberSaveable(goal?.id) { mutableStateOf(goal?.monthsPlain.orEmpty()) }
    var account by rememberSaveable(goal?.id) { mutableStateOf(goal?.accountId.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(if (goal == null) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; problem = null }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("goal_name"))
                OutlinedTextField(target, { v -> if (v.all { it.isDigit() || it == '.' }) { target = v; problem = null } }, label = { Text("How much") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("goal_target"))
                OutlinedTextField(months, { v -> if (v.all { it.isDigit() } && v.length <= 3) { months = v; problem = null } }, label = { Text("Reach it in (months), optional") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("goal_months"))
                val accounts = state?.accounts.orEmpty()
                if (accounts.isNotEmpty()) {
                    Text("Follow an account (optional)", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { a -> FilterChip(selected = account == a.id, onClick = { account = if (account == a.id) "" else a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("goal_account_${a.name}")) }
                    }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("goal_problem")) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(goal?.id, name, target, months, account.ifEmpty { null }) { p -> if (p == null) onClose() else problem = p } }, modifier = Modifier.testTag("goal_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun AdjustDialog(goal: GoalRowUi, onAdjust: (String, String, Boolean, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var amount by rememberSaveable(goal.id) { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text(goal.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A note of what you have set aside. It does not move money between accounts.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(amount, { v -> if (v.all { it.isDigit() || it == '.' }) { amount = v; problem = null } }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("goal_amount"))
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("goal_problem")) }
            }
        },
        confirmButton = { TextButton(onClick = { onAdjust(goal.id, amount, true) { p -> if (p == null) onClose() else problem = p } }, modifier = Modifier.testTag("goal_put")) { Text("Put in") } },
        dismissButton = { Row { TextButton(onClick = { onAdjust(goal.id, amount, false) { p -> if (p == null) onClose() else problem = p } }, modifier = Modifier.testTag("goal_take")) { Text("Take out") }; TextButton(onClick = onClose) { Text("Cancel") } } },
    )
}
