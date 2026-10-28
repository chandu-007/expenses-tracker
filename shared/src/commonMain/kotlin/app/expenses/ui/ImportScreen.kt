package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CardGroupUi
import app.expenses.ImportUi
import app.expenses.core.domain.AmountLayout
import app.expenses.core.domain.DateOrder
import app.expenses.core.domain.SignConvention
import app.expenses.core.domain.StatementMapping

@Composable
private fun DateOrderChips(m: StatementMapping, ambiguous: Boolean, onMapping: ((StatementMapping) -> StatementMapping) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DateOrder.DMY to "Day/Month/Year", DateOrder.MDY to "Month/Day/Year", DateOrder.YMD to "Year-Month-Day").forEach { (o, label) ->
            FilterChip(selected = m.dateOrder == o, onClick = { onMapping { it.copy(dateOrder = o) } }, label = { Text(label) }, modifier = Modifier.testTag("order_${o.name}"))
        }
    }
    if (ambiguous) Text("These dates could be day first or month first. Check the preview below.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("import_ambiguous"))
}

/**
 * The lines that look like paying a credit card's bill, set apart with what to do with them. The person's choice is a chip; with none made the import stays off,
 * because guessing the card (or counting the payment as spending) would be wrong in a way that is hard to see later.
 */
@Composable
private fun CardPaymentsGroup(g: CardGroupUi, onChoose: (String, String) -> Unit) {
    val bank = g.side == "BANK"
    Column(Modifier.fillMaxWidth().testTag("import_cardpay"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(if (bank) "Looks like a card payment" else "Looks like payments of this card's bill", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("import_cardpay_title"))
        Text("${g.count} ${if (g.count == 1) "line" else "lines"}, together ${g.total}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("import_cardpay_count"))
        // a few lines say what kind they are; "Show all" lists every one, so the person can see which lines the choice below covers
        var showAll by rememberSaveable { mutableStateOf(false) }
        val listed = if (showAll) g.rows else g.rows.take(CARD_ROWS_SHOWN)
        listed.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().testTag("import_cardpay_row_$i")) {
                Text(r.text, Modifier.weight(1f), maxLines = if (showAll) 2 else 1, style = MaterialTheme.typography.bodyMedium)
                Text(r.amount, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (g.count > CARD_ROWS_SHOWN) TextButton(onClick = { showAll = !showAll }, modifier = Modifier.testTag("import_cardpay_show_all")) { Text(if (showAll) "Show fewer" else "Show all ${g.count}") }
        if (showAll && g.count > g.rows.size) Text("…and ${g.count - g.rows.size} more.", style = MaterialTheme.typography.bodySmall)
        Text(
            if (bank) "Paying a card's bill moves money from your account to the card. The card's purchases are the spending, so counting the payment as well would count it twice."
            else "A payment received is you paying this card's bill from your bank, not income. Your bank statement shows the same payment as money out.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            g.accounts.forEach { a ->
                FilterChip(
                    selected = g.choice == "TRANSFER" && g.account == a.id, onClick = { onChoose("TRANSFER", a.id) },
                    label = { Text(if (bank) "Record as a transfer to ${a.name}" else "Record as a transfer from ${a.name}") }, modifier = Modifier.testTag("import_cardpay_to_${a.id}"),
                )
            }
            FilterChip(selected = g.choice == "AS_IS", onClick = { onChoose("AS_IS", "") }, label = { Text(if (bank) "Record as spending" else "Record as income") }, modifier = Modifier.testTag("import_cardpay_as_is"))
            FilterChip(selected = g.choice == "SKIP", onClick = { onChoose("SKIP", "") }, label = { Text("Skip") }, modifier = Modifier.testTag("import_cardpay_skip"))
        }
        if (g.eachNamed) Text("Each one goes to the card its own line names.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("import_cardpay_each"))
        if (!g.resolved) Text("Choose what to do with these before importing.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_cardpay_needed"))
    }
}

/** Picks one column of the file by its header text. [allowNone] adds a "None" choice for optional columns. */
@Composable
private fun ColumnPicker(label: String, tag: String, names: List<String>, selected: Int?, allowNone: Boolean, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) { Text(selected?.let { names.getOrNull(it) } ?: "Choose…", maxLines = 1) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowNone) DropdownMenuItem(text = { Text("None") }, onClick = { open = false; onPick(null) }, modifier = Modifier.testTag("${tag}_none"))
                names.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n, maxLines = 1) }, onClick = { open = false; onPick(i) }, modifier = Modifier.testTag("${tag}_$i")) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    state: ImportUi,
    accounts: List<AccountUi>,
    onAccount: (String) -> Unit,
    onMapping: ((StatementMapping) -> StatementMapping) -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
    onPassword: (String) -> Unit,
    onCardPayments: (String, String) -> Unit = { _, _ -> },
    onCreateCategories: (Boolean) -> Unit = {},
    onPlainFile: () -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Import a statement") }, navigationIcon = { TextButton(onClick = onClose, modifier = Modifier.testTag("import_close")) { Text(if (state.result != null) "Done" else "Cancel") } }) },
        bottomBar = {
            if (state.result == null && state.mapping != null) {
                Button(onClick = onImport, enabled = !state.busy && state.txnCount > 0 && state.accountId.isNotEmpty() && state.cardGroup?.resolved != false, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("import_confirm")) {
                    Text(if (state.txnCount == 1) "Import 1 transaction" else "Import ${state.txnCount} transactions")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.testTag("import_busy"))
                // reading a PDF or recognising a picture takes a while, and happens on this phone
                if (state.readFrom != null) Text("Reading the ${state.readFrom} on this phone. Nothing leaves it.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("import_busy_note"))
            }
            state.profileNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("import_profile_note")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_error")) }
            state.result?.let { r ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("import_result")) {
                    Text("Imported ${r.imported} ${if (r.imported == 1) "transaction" else "transactions"}", style = MaterialTheme.typography.titleLarge)
                    Text("Spending ${r.spending}, income ${r.income}.")
                    if (r.transfers > 0) Text("${r.transfers} ${if (r.transfers == 1) "was" else "were"} recorded as ${if (r.transfers == 1) "a transfer" else "transfers"} between your own accounts (${r.transferAmount}), not as spending or income.", modifier = Modifier.testTag("import_result_transfers"))
                    if (r.matchedExisting > 0) Text("${r.matchedExisting} matched entries you already have (from texts or typed by hand: same account, amount and direction within a day) and were not added again.", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("import_matched"))
                    if (r.alreadyThere > 0) Text("${r.alreadyThere} were already in the app from an earlier import and were left as they are.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("They have no category yet. Open Reports or the list to sort them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            if (state.needsPassword) {
                // the password is typed here, handed over once and cleared; it is not kept in the screen state or anywhere else
                var password by remember { mutableStateOf("") }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("import_password_box")) {
                    Text("This PDF is protected with a password", style = MaterialTheme.typography.titleMedium)
                    Text("Banks often use a mix of your name, birth date or the last digits of the card or account; the statement email usually says which. It is used once, here on this phone, to open the file. It is not saved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("PDF password") }, singleLine = true, isError = state.passwordError != null,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { state.passwordError?.let { Text(it, modifier = Modifier.testTag("import_password_error")) } },
                        modifier = Modifier.fillMaxWidth().testTag("import_password"),
                    )
                    Button(onClick = { val typed = password; password = ""; onPassword(typed) }, enabled = !state.busy && password.isNotEmpty(), modifier = Modifier.testTag("import_password_go")) { Text("Open the PDF") }
                }
                return@Column
            }
            val m = state.mapping ?: return@Column
            state.readFrom?.let {
                Text("Read from a $it; check amounts before importing. ${state.readNote.orEmpty()}".trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("import_read_note"))
            }
            state.totalsNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_totals_warning")) }
            if (state.damaged) Text("This file looks cut off or damaged (a quoted cell is never closed). Check it before importing.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_damaged"))

            state.preset?.let { app ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("import_preset")) {
                    Text("Looks like a $app export", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("import_preset_title"))
                    state.presetNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("import_preset_note")) }
                    state.presetLeftOut?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("import_preset_left_out")) }
                    TextButton(onClick = onPlainFile, modifier = Modifier.testTag("import_preset_off")) { Text("Read it as a plain statement instead") }
                }
            }
            Text(if (state.preset != null) "Into which account? (for rows that name none of yours)" else "Into which account?", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> FilterChip(selected = a.id == state.accountId, onClick = { onAccount(a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("import_account_${a.id}")) }
            }
            HorizontalDivider()

            state.mappingProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_mapping_problem")) }
            if (state.mappingProblem == null) {
                Text("What I understood", style = MaterialTheme.typography.titleMedium)
                Text("${state.txnCount} ${if (state.txnCount == 1) "transaction" else "transactions"}: spending ${state.spending}, income ${state.income}", modifier = Modifier.testTag("import_summary"))
                if (state.repairedRows > 0) Text("${state.repairedRows} ${if (state.repairedRows == 1) "row" else "rows"} had commas inside the description (the file does not quote them) and were put back together.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("import_repaired"))
                if (state.cardPayments > 0) Text("Skipped ${state.cardPayments} ${if (state.cardPayments == 1) "payment" else "payments"} to the card: paying the card's bill is not income, and the bank account it came from shows it as money out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("import_card_payments"))
                if (state.ignoredRows > 0) Text("${state.ignoredRows} ${if (state.ignoredRows == 1) "row" else "rows"} skipped (balances, totals or rows with no amount).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("import_ignored"))
                if (state.transfers > 0) Text("${state.transfers} of these ${if (state.transfers == 1) "is a transfer" else "are transfers"} between your own accounts, not spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("import_transfers"))
                state.preview.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().testTag("preview_$i")) {
                        Text(r.text, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text(if (r.transfer) "⇄ " + r.amount else (if (r.spending) "−" else "+") + r.amount, color = if (r.spending || r.transfer) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.cardGroup?.let { g -> CardPaymentsGroup(g, onCardPayments) }
                state.categories?.let { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("import_categories")) {
                        Text("${c.withCategory} ${if (c.withCategory == 1) "row found" else "rows found"} a category of yours." + if (c.onOwnAccount > 0) " ${c.onOwnAccount} ${if (c.onOwnAccount == 1) "goes" else "go"} to an account of yours named in the file." else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("import_categories_matched"))
                        if (c.newNames.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = c.create, onCheckedChange = onCreateCategories, modifier = Modifier.testTag("import_create_categories"))
                                Text("Create these ${c.newNames.size} new ${if (c.newNames.size == 1) "category" else "categories"}: ${c.newNames.take(NEW_CATEGORY_NAMES_SHOWN).joinToString(", ")}${if (c.newNames.size > NEW_CATEGORY_NAMES_SHOWN) " and ${c.newNames.size - NEW_CATEGORY_NAMES_SHOWN} more" else ""}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("import_new_categories"))
                            }
                            if (!c.create) Text("Without the tick nothing is created: those rows go to \"Other\" if you have it, else they have no category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (state.problemCount > 0) {
                    Text("${state.problemCount} ${if (state.problemCount == 1) "row" else "rows"} could not be read and will NOT be imported:", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_problem_count"))
                    state.problems.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (state.problemCount > state.problems.size) Text("…and ${state.problemCount - state.problems.size} more.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Imported transactions have no category. Importing the same file again, or one that overlaps it, adds only what is new.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            if (state.preset != null) {
                // a recognised file needs no column choices, only the date order when the file does not say it
                Text("How to read the dates", style = MaterialTheme.typography.titleMedium)
                DateOrderChips(m, state.dateAmbiguous, onMapping)
                return@Column
            }
            Text("How to read this file", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rows to skip at the top", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onMapping { it.copy(headerRows = (it.headerRows - 1).coerceAtLeast(0)) } }, enabled = m.headerRows > 0, modifier = Modifier.testTag("header_down").semantics { contentDescription = "Skip one row fewer" }) { Text("−") }
                Text(m.headerRows.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("header_rows"))
                TextButton(onClick = { onMapping { it.copy(headerRows = (it.headerRows + 1).coerceAtMost(MAX_SKIPPED_ROWS)) } }, modifier = Modifier.testTag("header_up").semantics { contentDescription = "Skip one row more" }) { Text("+") }
            }
            ColumnPicker("Date column", "pick_date", state.columnNames, m.dateColumn, allowNone = false) { c -> onMapping { it.copy(dateColumn = c) } }
            DateOrderChips(m, state.dateAmbiguous, onMapping)
            ColumnPicker("Description column", "pick_description", state.columnNames, m.descriptionColumn, allowNone = true) { c -> onMapping { it.copy(descriptionColumn = c) } }

            Text("How does it show money in and out?", style = MaterialTheme.typography.labelMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    AmountLayout.SEPARATE_DEBIT_CREDIT to "Separate debit and credit columns",
                    AmountLayout.SIGNED_AMOUNT to "One amount column",
                    AmountLayout.AMOUNT_WITH_TYPE to "An amount column and a Dr/Cr column",
                ).forEach { (l, label) -> FilterChip(selected = m.layout == l, onClick = { onMapping { it.copy(layout = l) } }, label = { Text(label) }, modifier = Modifier.testTag("layout_${l.name}")) }
            }
            when (m.layout) {
                AmountLayout.SEPARATE_DEBIT_CREDIT -> {
                    ColumnPicker("Debit (money out)", "pick_debit", state.columnNames, m.debitColumn, allowNone = false) { c -> onMapping { it.copy(debitColumn = c) } }
                    ColumnPicker("Credit (money in)", "pick_credit", state.columnNames, m.creditColumn, allowNone = false) { c -> onMapping { it.copy(creditColumn = c) } }
                }
                AmountLayout.SIGNED_AMOUNT -> {
                    ColumnPicker("Amount", "pick_amount", state.columnNames, m.amountColumn, allowNone = false) { c -> onMapping { it.copy(amountColumn = c) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = m.sign == SignConvention.NEGATIVE_IS_SPENDING, onClick = { onMapping { it.copy(sign = SignConvention.NEGATIVE_IS_SPENDING) } }, label = { Text("Minus is spending") }, modifier = Modifier.testTag("sign_negative"))
                        FilterChip(selected = m.sign == SignConvention.POSITIVE_IS_SPENDING, onClick = { onMapping { it.copy(sign = SignConvention.POSITIVE_IS_SPENDING) } }, label = { Text("Plus is spending") }, modifier = Modifier.testTag("sign_positive"))
                    }
                }
                AmountLayout.AMOUNT_WITH_TYPE -> {
                    ColumnPicker("Amount", "pick_amount", state.columnNames, m.amountColumn, allowNone = false) { c -> onMapping { it.copy(amountColumn = c) } }
                    ColumnPicker("Dr/Cr", "pick_type", state.columnNames, m.typeColumn, allowNone = false) { c -> onMapping { it.copy(typeColumn = c) } }
                }
            }
            HorizontalDivider()

        }
    }
}

private const val MAX_SKIPPED_ROWS = 50
private const val NEW_CATEGORY_NAMES_SHOWN = 8
private const val CARD_ROWS_SHOWN = 3
