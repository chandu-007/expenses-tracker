package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.ImportUi
import app.expenses.core.domain.AmountLayout
import app.expenses.core.domain.DateOrder
import app.expenses.core.domain.SignConvention
import app.expenses.core.domain.StatementMapping

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
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Import a statement") }, navigationIcon = { TextButton(onClick = onClose, modifier = Modifier.testTag("import_close")) { Text(if (state.result != null) "Done" else "Cancel") } }) },
        bottomBar = {
            if (state.result == null && state.mapping != null) {
                Button(onClick = onImport, enabled = !state.busy && state.txnCount > 0 && state.accountId.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("import_confirm")) {
                    Text(if (state.txnCount == 1) "Import 1 transaction" else "Import ${state.txnCount} transactions")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.testTag("import_busy"))
            state.profileNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("import_profile_note")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_error")) }
            state.result?.let { r ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("import_result")) {
                    Text("Imported ${r.imported} ${if (r.imported == 1) "transaction" else "transactions"}", style = MaterialTheme.typography.titleLarge)
                    Text("Spending ${r.spending}, income ${r.income}.")
                    if (r.matchedExisting > 0) Text("${r.matchedExisting} matched entries you already have (from texts or typed by hand: same account, amount and direction within a day) and were not added again.", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("import_matched"))
                    if (r.alreadyThere > 0) Text("${r.alreadyThere} were already in the app from an earlier import and were left as they are.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("They have no category yet. Open Reports or the list to sort them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            val m = state.mapping ?: return@Column
            if (state.damaged) Text("This file looks cut off or damaged (a quoted cell is never closed). Check it before importing.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_damaged"))

            Text("Into which account?", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> FilterChip(selected = a.id == state.accountId, onClick = { onAccount(a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("import_account_${a.id}")) }
            }
            HorizontalDivider()

            state.mappingProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_mapping_problem")) }
            if (state.mappingProblem == null) {
                Text("What I understood", style = MaterialTheme.typography.titleMedium)
                Text("${state.txnCount} ${if (state.txnCount == 1) "transaction" else "transactions"}: spending ${state.spending}, income ${state.income}", modifier = Modifier.testTag("import_summary"))
                if (state.repairedRows > 0) Text("${state.repairedRows} ${if (state.repairedRows == 1) "row" else "rows"} had commas inside the description (the file does not quote them) and were put back together.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("import_repaired"))
                if (state.ignoredRows > 0) Text("${state.ignoredRows} ${if (state.ignoredRows == 1) "row" else "rows"} skipped (balances, totals or rows with no amount).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("import_ignored"))
                state.preview.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().testTag("preview_$i")) {
                        Text(r.text, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text((if (r.spending) "−" else "+") + r.amount, color = if (r.spending) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
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

            Text("How to read this file", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rows to skip at the top", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onMapping { it.copy(headerRows = (it.headerRows - 1).coerceAtLeast(0)) } }, enabled = m.headerRows > 0, modifier = Modifier.testTag("header_down").semantics { contentDescription = "Skip one row fewer" }) { Text("−") }
                Text(m.headerRows.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("header_rows"))
                TextButton(onClick = { onMapping { it.copy(headerRows = (it.headerRows + 1).coerceAtMost(MAX_SKIPPED_ROWS)) } }, modifier = Modifier.testTag("header_up").semantics { contentDescription = "Skip one row more" }) { Text("+") }
            }
            ColumnPicker("Date column", "pick_date", state.columnNames, m.dateColumn, allowNone = false) { c -> onMapping { it.copy(dateColumn = c) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DateOrder.DMY to "Day/Month/Year", DateOrder.MDY to "Month/Day/Year", DateOrder.YMD to "Year-Month-Day").forEach { (o, label) ->
                    FilterChip(selected = m.dateOrder == o, onClick = { onMapping { it.copy(dateOrder = o) } }, label = { Text(label) }, modifier = Modifier.testTag("order_${o.name}"))
                }
            }
            if (state.dateAmbiguous) Text("These dates could be day first or month first. Check the preview below.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("import_ambiguous"))
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
