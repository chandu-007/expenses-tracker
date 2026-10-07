package app.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import app.expenses.ACCOUNT_TYPE_LABELS
import app.expenses.AccountRowUi
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted

/**
 * Where the money is kept: banks, cash, wallets, cards and loans, each with its balance. A card or loan shows what is owed. An account
 * that has entries cannot be deleted, only archived (hidden from pickers, history and balance kept); one with none can be deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    rows: List<AccountRowUi>,
    onBack: () -> Unit,
    onSave: (id: String?, name: String, type: String, opening: String, done: (String?) -> Unit) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<AccountRowUi?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val active = rows.filter { !it.archived }
    val archived = rows.filter { it.archived }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Accounts") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("back")) { Text("Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("add_account")) { Text("+") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("accounts_list")) {
            item {
                val net = active.sumOf { it.balanceMinor }
                Column(Modifier.padding(16.dp)) {
                    Text("Net worth", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money(net, Currency.INR).formatted(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("net_worth"))
                    Text("Balances start from the opening balance you set, then follow your entries.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
            items(active, key = { it.id }) { AccountRow(it) { editing = it } }
            if (archived.isNotEmpty()) {
                item { Text("Archived", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)) }
                items(archived, key = { it.id }) { AccountRow(it) { editing = it } }
            }
        }
    }
    if (creating) AccountDialog(null, rows, { creating = false }, onSave, onArchive, onDelete)
    editing?.let { e -> AccountDialog(rows.firstOrNull { it.id == e.id } ?: e, rows, { editing = null }, onSave, onArchive, onDelete) }
}

@Composable
private fun AccountRow(a: AccountRowUi, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 12.dp).testTag("account_${a.name}"), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge)
            Text(a.typeLabel + if (a.owed) " · owed" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(a.balanceText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("balance_${a.name}"))
    }
    HorizontalDivider()
}

@Composable
private fun AccountDialog(
    existing: AccountRowUi?,
    all: List<AccountRowUi>,
    onClose: () -> Unit,
    onSave: (String?, String, String, String, (String?) -> Unit) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: "BANK") }
    var opening by rememberSaveable(existing?.id) { mutableStateOf(existing?.openingText ?: "") }
    var problem by remember { mutableStateOf<String?>(null) }
    val liability = type == "CREDIT_CARD" || type == "LOAN"
    val onlyActive = existing != null && !existing.archived && all.count { !it.archived } == 1
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (existing == null) "New account" else "Edit account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it; problem = null }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("account_name"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ACCOUNT_TYPE_LABELS.forEach { (key, label) -> FilterChip(selected = type == key, onClick = { type = key; problem = null }, label = { Text(label) }, modifier = Modifier.testTag("type_$key")) }
                }
                OutlinedTextField(
                    opening, { opening = it; problem = null },
                    label = { Text(if (liability) "Amount owed today (optional)" else "Balance today (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("account_opening"),
                )
                Text(
                    if (liability) "What you owe before your first entry here. Leave empty if nothing." else "What it holds before your first entry here. Later entries add to or take from this.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("account_problem")) }
                if (existing != null) {
                    Spacer(Modifier.height(4.dp))
                    if (existing.archived) TextButton(onClick = { onArchive(existing.id, false); onClose() }, modifier = Modifier.testTag("account_unarchive")) { Text("Restore to the pickers") }
                    else TextButton(onClick = { onArchive(existing.id, true); onClose() }, enabled = !onlyActive, modifier = Modifier.testTag("account_archive")) { Text(if (onlyActive) "Archive (keep one active account)" else "Archive: hide it, keep its history") }
                    if (existing.usage == 0L) TextButton(onClick = { onDelete(existing.id); onClose() }, enabled = !onlyActive, modifier = Modifier.testTag("account_delete")) { Text("Delete") }
                    else Text("It has ${existing.usage} entries or saved items, so it can be archived but not deleted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(existing?.id, name, type, opening) { p -> if (p == null) onClose() else problem = p } }, modifier = Modifier.testTag("account_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
