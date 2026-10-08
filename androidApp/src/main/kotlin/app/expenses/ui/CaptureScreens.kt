package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    onConfirm: (id: String, accountId: String, categoryId: String, treatment: String, fromId: String, toId: String, refundOf: String, done: (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit, onBack: () -> Unit,
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
    onConfirm: (String, String, String, String, String, String, String, (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit,
) {
    var asPayment by rememberSaveable(r.id) { mutableStateOf(false) }
    var fromAcct by rememberSaveable(r.id) { mutableStateOf(r.fromAccountId) }
    var toAcct by rememberSaveable(r.id) { mutableStateOf(r.toAccountId) }
    val paired = r.treatment != "PAYMENT" && !asPayment
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
        if (paired && r.treatment == "TRANSFER") {
            val nameOf = { id: String -> accounts.firstOrNull { it.id == id }?.name ?: "choose" }
            Text("Recorded as a ${r.treatmentLabel}: from ${nameOf(fromAcct)} to ${nameOf(toAcct)}. It moves money between your own accounts, so it is not spending.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_pair_${r.id}"))
            if (changing) {
                Text("From", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == fromAcct, onClick = { fromAcct = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_from_${r.id}_${a.id}")) } }
                Text("To", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == toAcct, onClick = { toAcct = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_to_${r.id}_${a.id}")) } }
            }
        } else if (paired && r.treatment == "REFUND") {
            Text("Recorded as a ${r.treatmentLabel}, so the original expense is reduced instead of this counting as income.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_pair_${r.id}"))
        }
        val accountName = accounts.firstOrNull { it.id == account }?.name ?: "choose an account"
        val categoryName = categories.firstOrNull { it.id == category }?.name
        val why = if (category == r.categoryId && r.categoryNote.isNotEmpty()) " (${r.categoryNote})" else ""
        if (!paired) Text("Goes to $accountName" + (if (!credit) " · " + (categoryName ?: "no category") + why else ""), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_goes_${r.id}"))
        if (changing && !paired) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == account, onClick = { account = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_account_${r.id}_${a.id}")) } }
            if (!credit) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { !it.hidden }.take(24).forEach { c -> FilterChip(selected = c.id == category, onClick = { category = c.id }, label = { Text(c.name) }, modifier = Modifier.testTag("inbox_category_${r.id}_${c.id}")) }
            }
        }
        r.duplicateNote?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_duplicate_${r.id}")) }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_problem_${r.id}")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.duplicateNote != null) OutlinedButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_same_${r.id}")) { Text("Same one: skip") }
            Button(
                onClick = { onConfirm(r.id, account, category, if (paired) r.treatment else "PAYMENT", fromAcct, toAcct, r.refundOfEntry) { p -> problem = p } }, enabled = account.isNotEmpty(),
                modifier = Modifier.testTag("inbox_add_${r.id}"),
            ) { Text(if (r.duplicateNote != null) "Add anyway" else if (paired) "Add as ${r.treatmentLabel}" else "Add") }
            OutlinedButton(onClick = { changing = !changing }, modifier = Modifier.testTag("inbox_change_${r.id}")) { Text(if (changing) "Done" else "Change") }
            if (r.treatment != "PAYMENT") TextButton(onClick = { asPayment = !asPayment }, modifier = Modifier.testTag("inbox_plain_${r.id}")) { Text(if (asPayment) "Use ${r.treatmentLabel}" else if (credit) "Add as income instead" else "Add as expense instead") }
            TextButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_skip_${r.id}")) { Text("Skip") }
        }
    }
}

/** Turning SMS capture on or off, and what it does and does not do, before Android asks for the permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(
    settings: CaptureSettingsUi, permissionGranted: Boolean, onToggle: (Boolean) -> Unit, onHeaders: (String) -> Unit, onOpenRules: () -> Unit, onOpenHealth: () -> Unit, onBack: () -> Unit,
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
            HorizontalDivider()
            OutlinedButton(onClick = onOpenRules, modifier = Modifier.testTag("open_rules")) { Text("Category rules") }
            OutlinedButton(onClick = onOpenHealth, modifier = Modifier.testTag("open_health")) { Text("How capture is doing") }
        }
    }
}

/** The person's own rules: "when the payee or text mentions X, use this category", tried top to bottom, tested against past entries before saving. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CaptureRulesScreen(
    rules: List<app.expenses.CaptureRuleUi>, categories: List<CategoryUi>,
    onAdd: (pattern: String, categoryId: String, done: (String?) -> Unit) -> Unit, onRemove: (String) -> Unit, onMove: (String, Boolean) -> Unit,
    onTest: (String, (Long) -> Unit) -> Unit, onBack: () -> Unit,
) {
    var pattern by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var tested by remember { mutableStateOf<Long?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Category rules") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("rules_back")) { Text("Back") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("rules_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("When a captured payment mentions these words, it gets this category. Rules are tried from the top; the first match wins, and a rule beats what the app learned from your earlier choices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(rules, key = { _, r -> r.id }) { i, r ->
                Row(Modifier.fillMaxWidth().testTag("rule_${r.pattern}"), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("\"${r.pattern}\"", style = MaterialTheme.typography.bodyLarge); Text("→ ${r.categoryName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { onMove(r.id, true) }, enabled = i > 0, modifier = Modifier.testTag("rule_up_${r.pattern}")) { Text("Up") }
                    TextButton(onClick = { onMove(r.id, false) }, enabled = i < rules.lastIndex, modifier = Modifier.testTag("rule_down_${r.pattern}")) { Text("Down") }
                    TextButton(onClick = { onRemove(r.id) }, modifier = Modifier.testTag("rule_remove_${r.pattern}")) { Text("Remove") }
                }
            }
            item { HorizontalDivider() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New rule", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(pattern, { pattern = it; tested = null; problem = null }, label = { Text("Payee or text contains") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rule_pattern"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.filter { !it.hidden }.take(24).forEach { c -> FilterChip(selected = c.id == category, onClick = { category = c.id; problem = null }, label = { Text(c.name) }, modifier = Modifier.testTag("rule_category_${c.id}")) }
                    }
                    tested?.let { Text(if (it == 0L) "No past entry mentions this." else "It would have matched $it of your past entries.", modifier = Modifier.testTag("rule_tested")) }
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("rule_problem")) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onTest(pattern) { tested = it } }, enabled = pattern.isNotBlank(), modifier = Modifier.testTag("rule_test")) { Text("Test on my history") }
                        Button(onClick = { onAdd(pattern, category) { p -> if (p == null) { pattern = ""; tested = null } else problem = p } }, enabled = pattern.isNotBlank() && category.isNotEmpty(), modifier = Modifier.testTag("rule_add")) { Text("Add rule") }
                    }
                }
            }
        }
    }
}
