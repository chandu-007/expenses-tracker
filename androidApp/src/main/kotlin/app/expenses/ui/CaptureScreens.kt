package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CaptureSettingsUi
import app.expenses.CategoryUi
import app.expenses.InboxRowUi

/** Transactions found in texts, waiting to be added to the ledger or skipped. Nothing here counts anywhere until it is added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    rows: List<InboxRowUi>, accounts: List<AccountUi>, categories: List<CategoryUi>,
    onConfirm: (id: String, accountId: String, categoryId: String, done: (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit, onBack: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("To review") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("inbox_back")) { Text("Back") } }) }) { pad ->
        if (rows.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp).fillMaxSize()) {
                Text("Nothing to review.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_empty"))
                Text("Bank texts that look like transactions will appear here for you to add or skip. Nothing is added to your ledger by itself.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("inbox_list")) {
            items(rows, key = { it.id }) { InboxCard(it, accounts, categories, onConfirm, onDismiss); HorizontalDivider() }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(
    r: InboxRowUi, accounts: List<AccountUi>, categories: List<CategoryUi>,
    onConfirm: (String, String, String, (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit,
) {
    var account by rememberSaveable(r.id) { mutableStateOf(r.accountId) }
    var category by rememberSaveable(r.id) { mutableStateOf(r.categoryId) }
    var changing by rememberSaveable(r.id) { mutableStateOf(false) }
    var problem by remember(r.id) { mutableStateOf<String?>(null) }
    val credit = r.direction == "CREDIT"
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("inbox_${r.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(r.payee ?: r.issuer, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text((if (credit) "+" else "") + r.amountText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_amount_${r.id}"))
        }
        Text("${if (credit) "Money in" else "Money out"} · ${r.dateText} · ${r.issuer}" + (r.last4?.let { " · ending $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(r.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
        Text("Read automatically: please check it before adding.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val accountName = accounts.firstOrNull { it.id == account }?.name ?: "choose an account"
        val categoryName = categories.firstOrNull { it.id == category }?.name
        Text("Goes to $accountName" + (if (!credit) " · " + (categoryName ?: "no category") else ""), style = MaterialTheme.typography.bodyMedium)
        if (changing) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == account, onClick = { account = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_account_${r.id}_${a.id}")) } }
            if (!credit) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { !it.hidden }.take(24).forEach { c -> FilterChip(selected = c.id == category, onClick = { category = c.id }, label = { Text(c.name) }, modifier = Modifier.testTag("inbox_category_${r.id}_${c.id}")) }
            }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_problem_${r.id}")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onConfirm(r.id, account, category) { p -> problem = p } }, enabled = account.isNotEmpty(), modifier = Modifier.testTag("inbox_add_${r.id}")) { Text("Add") }
            OutlinedButton(onClick = { changing = !changing }, modifier = Modifier.testTag("inbox_change_${r.id}")) { Text(if (changing) "Done" else "Change") }
            TextButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_skip_${r.id}")) { Text("Skip") }
        }
    }
}

/** Turning SMS capture on or off, and what it does and does not do, before Android asks for the permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(
    settings: CaptureSettingsUi, permissionGranted: Boolean, onToggle: (Boolean) -> Unit, onHeaders: (String) -> Unit, onBack: () -> Unit,
) {
    var headers by rememberSaveable(settings.extraHeaders) { mutableStateOf(settings.extraHeaders) }
    Scaffold(topBar = { TopAppBar(title = { Text("Capture from texts") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("capture_back")) { Text("Back") } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Read bank texts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = settings.smsOn && permissionGranted, onCheckedChange = onToggle, modifier = Modifier.testTag("capture_sms_switch"))
            }
            Text("What this does", style = MaterialTheme.typography.labelMedium)
            Text("When a text arrives from a bank or card sender, this app reads it on your phone and, if it is a payment or a credit, puts it in your To review list. You decide whether to add it.", style = MaterialTheme.typography.bodyMedium)
            Text("What it does not do", style = MaterialTheme.typography.labelMedium)
            Text("Texts from people, one-time passwords and anything from a sender that is not a bank are ignored without being read. Nothing is sent anywhere: the reading happens on this phone. A text is kept only until you add or skip it; after that only the amount, date and reference stay.", style = MaterialTheme.typography.bodyMedium)
            if (settings.smsOn && !permissionGranted) Text("Android has not allowed this app to receive texts. Turn the switch on again to be asked.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_permission_missing"))
            Text("Your bank's sender name", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(headers, { headers = it }, label = { Text("Extra sender names (one per line)") }, modifier = Modifier.fillMaxWidth().testTag("capture_headers"))
            Text("Only senders on the built-in list of banks are read. If yours is missing, add the name that appears after the first dash, for example HDFCBK from AX-HDFCBK-S.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onHeaders(headers) }, modifier = Modifier.testTag("capture_headers_save")) { Text("Save names") }
        }
    }
}
