package app.expenses.ui

import app.expenses.core.domain.BaseCurrency
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import app.expenses.ACCOUNT_TYPE_LABELS
import app.expenses.AccountRowUi
import app.expenses.core.domain.CardNetwork
import app.expenses.core.domain.CardTier
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
    onSave: (id: String?, name: String, type: String, opening: String, network: String?, tier: String?, last4: String, done: (String?) -> Unit) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCard: (id: String, statementDay: String, dueDay: String, minPercent: String, remindDays: String, done: (String?) -> Unit) -> Unit,
    onClearCard: (String) -> Unit,
    onSetCreditLimit: (id: String, limit: String) -> Unit = { _, _ -> },
    /** "See its transactions": Home's list narrowed to this account. */
    onShowEntries: ((String) -> Unit)? = null,
    /** The balance-based safe to spend, once it is worked out (a moment after the page opens), and the switch for showing it on Home. */
    safeToSpend: app.expenses.SafeToSpendUi? = null,
    onSafeToSpendOnHome: (Boolean) -> Unit = {},
) {
    var editing by remember { mutableStateOf<AccountRowUi?>(null) }
    var safeOpen by rememberSaveable { mutableStateOf(false) }
    if (safeOpen && safeToSpend != null) SafeToSpendDialog(safeToSpend, onClose = { safeOpen = false }, onShowOnHome = onSafeToSpendOnHome)
    var creating by rememberSaveable { mutableStateOf(false) }
    val active = rows.filter { !it.archived }
    val archived = rows.filter { it.archived }
    Scaffold(
        topBar = { AreaTopBar("Accounts", Area.MONEY, Icons.Outlined.AccountBalance, onBack, "back") },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("add_account").semantics { contentDescription = "Add an account" }) { Text("+") } }, // a screen reader says "Add an account", not "plus"
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("accounts_list")) {
            item {
                val net = active.sumOf { it.balanceMinor }
                // With the safe-to-spend line below it the header is kept short (no help sentence, less room under it): the cards must still start high enough on a phone
                // for the third and fourth of them to be in view when they are spread (AccountTilesUiTest reads where each one is).
                Column(Modifier.padding(16.dp, if (safeToSpend != null) 8.dp else 16.dp, 16.dp, if (safeToSpend != null) 0.dp else 16.dp)) {
                    Text("Net worth", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money(net, BaseCurrency.current).formatted(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("net_worth"))
                    if (safeToSpend == null) Text("Balances start from the opening balance you set, then follow your entries.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (safeToSpend != null) SafeToSpendLine(safeToSpend, "safe_to_spend_line", onClick = { safeOpen = true }, compact = true)
                HorizontalDivider()
            }
            // each account is a card; one that is archived stays a plain line below
            if (active.isNotEmpty()) item(key = "wallet") { WalletStack(active, onOpen = { editing = it }, modifier = Modifier.animateItem()) }
            if (archived.isNotEmpty()) {
                item { Text("Archived", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)) }
                items(archived, key = { it.id }) { AccountRow(it, Modifier.animateItem()) { editing = it } }
            }
        }
    }
    if (creating) AccountDialog(null, rows, { creating = false }, onSave, onArchive, onDelete, onSaveCard, onClearCard, onSetCreditLimit)
    editing?.let { e -> AccountDialog(rows.firstOrNull { it.id == e.id } ?: e, rows, { editing = null }, onSave, onArchive, onDelete, onSaveCard, onClearCard, onSetCreditLimit, onShowEntries) }
}

@Composable
private fun AccountRow(a: AccountRowUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Column(modifier) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 12.dp).testTag("account_${a.name}"), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge)
            Text(a.typeLabel + if (a.owed) " · owed" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            a.card?.let { c ->
                Text(c.termsText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.statusText.isNotEmpty()) Text(c.statusText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("card_status_${a.name}"))
            }
        }
        Text(a.balanceText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("balance_${a.name}"))
    }
    HorizontalDivider()
  }
}

@Composable
private fun AccountDialog(
    existing: AccountRowUi?,
    all: List<AccountRowUi>,
    onClose: () -> Unit,
    onSave: (String?, String, String, String, String?, String?, String, (String?) -> Unit) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCard: (String, String, String, String, String, (String?) -> Unit) -> Unit,
    onClearCard: (String) -> Unit,
    onSetCreditLimit: (id: String, limit: String) -> Unit = { _, _ -> },
    onShowEntries: ((String) -> Unit)? = null,
) {
    var cardOpen by remember { mutableStateOf(false) }
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: "BANK") }
    var opening by rememberSaveable(existing?.id) { mutableStateOf(existing?.openingText ?: "") }
    var network by rememberSaveable(existing?.id) { mutableStateOf(existing?.network) }
    var tier by rememberSaveable(existing?.id) { mutableStateOf(existing?.tier) }
    var last4 by rememberSaveable(existing?.id) { mutableStateOf(existing?.last4 ?: "") }
    var problem by remember { mutableStateOf<String?>(null) }
    val liability = type == "CREDIT_CARD" || type == "LOAN"
    val onlyActive = existing != null && !existing.archived && all.count { !it.archived } == 1
    if (cardOpen && existing != null) CardTermsDialog(existing, onSaveCard, onClearCard, onSetCreditLimit) { cardOpen = false }
    AlertDialog(
        modifier = Modifier.padding(bottom = dialogKeyboardRoom()),
        onDismissRequest = onClose,
        title = { Text(if (existing == null) "New account" else "Edit account") },
        text = {
            // The dialog always leaves room for the keyboard below it (it comes up by itself when the dialog opens), so its content scrolls above the keyboard and Save and Cancel stay in view.
            // The room is fixed, so nothing moves when the keyboard comes: resizing the window for it made taps miss.
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
                if (type == "CREDIT_CARD") {
                    if (existing != null && existing.type == "CREDIT_CARD") {
                        TextButton(onClick = { cardOpen = true }, modifier = Modifier.testTag("card_dates")) { Text(if (existing.card == null) "Set billing dates and reminders" else "Billing dates: ${existing.card.termsText}") }
                    } else Text("Save the card first, then add its billing dates to get due-date reminders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (existing != null && existing.hasCardSummary) existing.card?.let { c ->
                        Text("Card summary", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("card_summary_title"))
                        CardSummaryLines(c, Triple("card_cycle_spend", "card_limit_use", "card_due_line"), MaterialTheme.colorScheme.onSurface, barColor = limitBarColors(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                
                    Text("Card network", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardNetwork.entries.forEach { n -> FilterChip(selected = network == n.name, onClick = { network = if (network == n.name) null else n.name }, label = { Text(n.label) }, modifier = Modifier.testTag("network_${n.name}")) }
                    }
                    Text("Card tier", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardTier.entries.forEach { t -> FilterChip(selected = tier == t.name, onClick = { tier = if (tier == t.name) null else t.name }, label = { Text(t.label) }, modifier = Modifier.testTag("tier_${t.name}")) }
                    }
                    Text("The tile takes the look of its network and tier. Premium is for Signature, Infinite, World and similar cards.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                }
                OutlinedTextField(
                    last4, { v ->
                        // a paste such as "XX7710" or "**** 7710" keeps its digits (the last four); a fifth key typed after four digits is ignored
                        val digits = v.filter { it.isDigit() }
                        last4 = if (digits.length > 4 && v.length - last4.length <= 1) last4 else digits.takeLast(4)
                        problem = null
                    }, label = { Text("Last 4 digits (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("account_last4"),
                )
                Text("Of the card or the account number, as your bank's texts show it. It lets a text or a bill find this account by itself.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("account_problem")) }
                if (existing != null) {
                    Spacer(Modifier.height(4.dp))
                    if (onShowEntries != null && !existing.archived) TextButton(onClick = { onShowEntries(existing.id); onClose() }, modifier = Modifier.testTag("account_show_entries")) { Text("See its transactions on Home") }
                    if (existing.archived) TextButton(onClick = { onArchive(existing.id, false); onClose() }, modifier = Modifier.testTag("account_unarchive")) { Text("Restore to the pickers") }
                    else TextButton(onClick = { onArchive(existing.id, true); onClose() }, enabled = !onlyActive && existing.savedItems == 0L, modifier = Modifier.testTag("account_archive")) { Text(if (onlyActive) "Archive (keep one active account)" else "Archive: hide it, keep its history") }
                    if (!existing.archived && existing.savedItems > 0L) Text("${existing.savedItems} repeating items or quick entries post to it. Change them first, then archive it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("account_saved_note"))
                    if (existing.usage == 0L) TextButton(onClick = { onDelete(existing.id); onClose() }, enabled = !onlyActive, modifier = Modifier.testTag("account_delete")) { Text("Delete") }
                    else Text("It has ${existing.usage} entries or saved items, so it can be archived but not deleted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(existing?.id, name, type, opening, network, tier, last4) { p -> if (p == null) onClose() else problem = p } }, modifier = Modifier.testTag("account_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** The billing dates of a credit card. Days are 1 to 28 so every month has them. */
@Composable
private fun CardTermsDialog(a: AccountRowUi, onSave: (String, String, String, String, String, (String?) -> Unit) -> Unit, onClear: (String) -> Unit, onSetLimit: (String, String) -> Unit, onClose: () -> Unit) {
    var limit by rememberSaveable(a.id) { mutableStateOf(a.card?.creditLimit.orEmpty()) }
    var statement by rememberSaveable(a.id) { mutableStateOf(a.card?.statementDay?.toString() ?: "") }
    var due by rememberSaveable(a.id) { mutableStateOf(a.card?.dueDay?.toString() ?: "") }
    var percent by rememberSaveable(a.id) { mutableStateOf(a.card?.minDuePercent?.toString() ?: "5") }
    var remind by rememberSaveable(a.id) { mutableStateOf(a.card?.remindDaysBefore?.toString() ?: "3") }
    var problem by remember { mutableStateOf<String?>(null) }
    @Composable fun field(value: String, label: String, tag: String, set: (String) -> Unit) =
        OutlinedTextField(value, { v -> if (v.all { it.isDigit() } && v.length <= 3) { set(v); problem = null } }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag(tag))
    AlertDialog(
        modifier = Modifier.padding(bottom = dialogKeyboardRoom()),
        onDismissRequest = onClose,
        title = { Text("${a.name}: billing dates") },
        text = {
            // As in Edit account: room is left for the keyboard, so Save and Cancel stay above it.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                field(statement, "Statement day (1 to 28)", "card_statement_day") { statement = it }
                field(due, "Payment due day (1 to 28)", "card_due_day") { due = it }
                field(percent, "Minimum due, percent of the statement", "card_min_percent") { percent = it }
                field(remind, "Remind this many days before (0 to 10)", "card_remind_days") { remind = it }
                OutlinedTextField(limit, { v -> if (v.all { it.isDigit() || it == '.' } && v.length <= 12) limit = v }, label = { Text("Credit limit, optional") }, supportingText = { Text("For a notice at 80% and 100% of it") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("card_limit"))
                Text("Both days are on your card statement. The minimum is only an estimate; your statement has the exact figure. You get one reminder ahead and one on the due day, and none once the statement is paid.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("card_problem")) }
                if (a.card != null) TextButton(onClick = { onClear(a.id); onClose() }, modifier = Modifier.testTag("card_clear")) { Text("Remove billing dates") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(a.id, statement, due, percent, remind) { p -> if (p == null) { onSetLimit(a.id, limit); onClose() } else problem = p } }, modifier = Modifier.testTag("card_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
