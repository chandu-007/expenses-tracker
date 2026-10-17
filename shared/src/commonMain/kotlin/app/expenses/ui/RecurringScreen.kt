package app.expenses.ui

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.symbolOrCode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi
import app.expenses.DueUi
import app.expenses.RecurringUi
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.parseAmount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** How many due items the list shows before pointing at the full screen. */
private const val LIST_DUE_LIMIT = 3

/** Repeating expenses waiting on the list screen: post or skip each with one tap. */
@Composable
fun DueSection(due: List<DueUi>, onPost: (DueUi) -> Unit, onSkip: (DueUi) -> Unit, onOpenAll: () -> Unit) {
    if (due.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("due_section"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        due.take(LIST_DUE_LIMIT).forEach { d -> DueCard(d, onPost, onSkip) }
        if (due.size > LIST_DUE_LIMIT) {
            TextButton(onClick = onOpenAll, modifier = Modifier.testTag("due_more")) { Text("${due.size - LIST_DUE_LIMIT} more waiting") }
        }
    }
}

@Composable
fun DueCard(d: DueUi, onPost: (DueUi) -> Unit, onSkip: (DueUi) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("due_card_${d.itemId}_${d.epochDay}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${d.name} · ${d.amount ?: "amount?"}", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(d.whenText, style = MaterialTheme.typography.bodySmall, color = if (d.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { onSkip(d) }, modifier = Modifier.testTag("due_skip_${d.itemId}_${d.epochDay}")) { Text("Skip") }
        TextButton(onClick = { onPost(d) }, modifier = Modifier.testTag("due_post_${d.itemId}_${d.epochDay}")) { Text("Post") }
    }
}

/** Asks for one amount, for bills that differ each time. Short, so it stays above the keyboard. */
@Composable
fun AmountDialog(title: String, onConfirm: (Money) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val amount = parseAmount(text, BaseCurrency.current)?.takeIf { it.minorUnits > 0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) text = v }, label = { Text("Amount (${BaseCurrency.current.symbolOrCode()})") }, singleLine = true,
                isError = text.isNotEmpty() && amount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { amount?.let(onConfirm) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("due_amount"),
            )
        },
        confirmButton = { TextButton(onClick = { amount?.let(onConfirm) }, enabled = amount != null, modifier = Modifier.testTag("due_amount_ok")) { Text("Post") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    due: List<DueUi>,
    hiddenOlder: Int,
    items: List<RecurringUi>,
    categories: List<CategoryUi>,
    accounts: List<AccountUi>,
    today: LocalDate,
    onBack: () -> Unit,
    onPost: (DueUi, Money?) -> Unit,
    onSkip: (DueUi) -> Unit,
    onSave: (id: String?, name: String, amount: Money?, category: String, account: String, merchant: String, schedule: Schedule, auto: Boolean, kind: RecurringKind, toAccount: String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // an item id, or NEW
    var asking by remember { mutableStateOf<DueUi?>(null) }
    asking?.let { d -> AmountDialog("Amount for ${d.name}", onConfirm = { onPost(d, it); asking = null }, onDismiss = { asking = null }) }

    val edited = items.firstOrNull { it.id == editing }
    if (editing == NEW || edited != null) {
        RecurringEditor(
            start = edited, categories = categories, accounts = accounts, today = today,
            onSave = { name, amount, cat, acct, merchant, schedule, auto, kind, to -> onSave(edited?.id, name, amount, cat, acct, merchant, schedule, auto, kind, to); editing = null },
            onDelete = { edited?.let { onDelete(it.id) }; editing = null },
            onBack = { editing = null },
        )
        return
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Repeating expenses") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("recurring_back")) { Text("Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { editing = NEW }, modifier = Modifier.testTag("new_recurring")) { Text("+", style = MaterialTheme.typography.headlineMedium) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (due.isNotEmpty()) {
                item { Text("Waiting", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }
                items(due, key = { "due_${it.itemId}_${it.epochDay}" }) { d ->
                    Column(Modifier.animateItem().padding(horizontal = 16.dp)) {
                        DueCard(d, onPost = { if (it.amount == null) asking = it else onPost(it, null) }, onSkip = onSkip)
                    }
                }
                item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            }
            if (hiddenOlder > 0) {
                item {
                    Text(
                        "$hiddenOlder older ${if (hiddenOlder == 1) "occurrence is" else "occurrences are"} not shown (more than 60 days ago, never posted or skipped).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("hidden_older"),
                    )
                }
            }
            item { Text("All repeating expenses", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }
            if (items.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyArt(EmptyKind.GENERIC, Modifier.padding(top = 8.dp))
                        Text("Nothing repeats yet. Tap + to add rent, a subscription or a bill.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("recurring_empty"))
                    }
                }
            }
            itemsIndexed(items, key = { _, it -> it.id }) { index, i ->
                val pos = CardPos.of(index == 0, index == items.lastIndex)
                Column(Modifier.animateItem().entryCardClip(pos)) {
                Column(Modifier.fillMaxWidth().entryCardFill(pos).clickable { editing = i.id }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("rec_row_${i.name}")) {
                    Text(i.name, style = MaterialTheme.typography.bodyLarge)
                    Text(i.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
            }
        }
    }
}

private const val NEW = "\u0000new"

private val UNIT_LABELS = listOf(RepeatUnit.DAY to "Day", RepeatUnit.WEEK to "Week", RepeatUnit.MONTH to "Month", RepeatUnit.YEAR to "Year")

private fun LocalDate.label(): String = fullLabel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works in UTC midnights.
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDays() * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { onPick(LocalDate.fromEpochDays(it.floorDiv(MILLIS_PER_DAY))) }; onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = picker) }
}

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
private fun Stepper(value: Int, range: IntRange, tag: String, label: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(value - 1) }, enabled = value > range.first, modifier = Modifier.testTag("${tag}_down").semantics { contentDescription = "$label, less" }) { Text("−") }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
        TextButton(onClick = { onChange(value + 1) }, enabled = value < range.last, modifier = Modifier.testTag("${tag}_up").semantics { contentDescription = "$label, more" }) { Text("+") }
    }
}

/** The full-screen editor, with Save pinned above the keyboard like the expense form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditor(
    start: RecurringUi?,
    categories: List<CategoryUi>,
    accounts: List<AccountUi>,
    today: LocalDate,
    onSave: (name: String, amount: Money?, category: String, account: String, merchant: String, schedule: Schedule, auto: Boolean, kind: RecurringKind, toAccount: String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val key = start?.id ?: NEW
    var name by rememberSaveable(key) { mutableStateOf(start?.name.orEmpty()) }
    var amountText by rememberSaveable(key) { mutableStateOf(start?.amountPlain.orEmpty()) }
    var category by rememberSaveable(key) { mutableStateOf(start?.categoryId ?: categoryFor(categories, income = false, current = "", last = "")) }
    var account by rememberSaveable(key) { mutableStateOf(start?.accountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var kind by rememberSaveable(key) { mutableStateOf(start?.kind ?: RecurringKind.EXPENSE) }
    var toAccount by rememberSaveable(key) { mutableStateOf(start?.toAccountId ?: accounts.getOrNull(1)?.id.orEmpty()) }
    var unit by rememberSaveable(key) { mutableStateOf(start?.schedule?.unit ?: RepeatUnit.MONTH) }
    var every by rememberSaveable(key) { mutableStateOf(start?.schedule?.every ?: 1) }
    var startDay by rememberSaveable(key) { mutableStateOf((start?.schedule?.start ?: today).toEpochDays()) }
    var endKind by rememberSaveable(key) { mutableStateOf(when (start?.schedule?.end) { is ScheduleEnd.OnOrBefore -> "date"; is ScheduleEnd.AfterCount -> "count"; else -> "never" }) }
    var endDay by rememberSaveable(key) { mutableStateOf(((start?.schedule?.end as? ScheduleEnd.OnOrBefore)?.date ?: LocalDate.fromEpochDays((start?.schedule?.start ?: today).toEpochDays() + 365)).toEpochDays()) }
    var endCount by rememberSaveable(key) { mutableStateOf((start?.schedule?.end as? ScheduleEnd.AfterCount)?.count ?: 12) }
    var auto by rememberSaveable(key) { mutableStateOf(start?.auto ?: false) }
    var picking by rememberSaveable(key) { mutableStateOf<String?>(null) } // "start" or "end"
    var confirmingDelete by rememberSaveable(key) { mutableStateOf(false) }

    val amount = if (amountText.isBlank()) null else parseAmount(amountText, BaseCurrency.current)?.takeIf { it.minorUnits > 0 }
    val amountInvalid = amountText.isNotBlank() && amount == null
    val startDate = LocalDate.fromEpochDays(startDay)
    val endDate = LocalDate.fromEpochDays(endDay)
    val endProblem = if (endKind == "date" && endDate < startDate) "The end date is before the start date" else null
    val canSave = name.isNotBlank() && (kind == RecurringKind.TRANSFER || category.isNotEmpty()) && account.isNotEmpty() && !amountInvalid && endProblem == null &&
        (kind != RecurringKind.TRANSFER || (toAccount.isNotEmpty() && toAccount != account))

    picking?.let { which ->
        DatePickDialog(
            initial = if (which == "start") startDate else endDate,
            onPick = { if (which == "start") startDay = it.toEpochDays() else endDay = it.toEpochDays() },
            onDismiss = { picking = null },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete \"${start?.name}\"?") },
            text = { Text("It stops repeating. Expenses already posted stay.") },
            confirmButton = { TextButton(onClick = onDelete, modifier = Modifier.testTag("rec_delete_confirm")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Keep") } },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text((if (start == null) "New repeating " else "Edit repeating ") + kind.name.lowercase(), maxLines = 1) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("rec_cancel")) { Text("Cancel") } }) },
        bottomBar = {
            Button(
                onClick = {
                    val end = when (endKind) { "date" -> ScheduleEnd.OnOrBefore(endDate); "count" -> ScheduleEnd.AfterCount(endCount); else -> ScheduleEnd.Never }
                    onSave(name.trim(), amount, category, account, start?.merchant.orEmpty(), Schedule(unit, every, startDate, end), auto && amount != null, kind, toAccount)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("rec_save"),
            ) { Text("Save") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurringKind.entries.forEach { k -> FilterChip(
                    selected = kind == k,
                    // an expense and an income have their own categories: one that does not serve the new kind is swapped for one that does
                    onClick = { if (k != kind) { kind = k; if (k != RecurringKind.TRANSFER) category = categoryFor(categories, income = k == RecurringKind.INCOME, current = category, last = "") } },
                    label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) }, modifier = Modifier.testTag("rec_kind_${k.name}")) }
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rec_name"))
            OutlinedTextField(
                value = amountText, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amountText = v },
                label = { Text("Amount (${BaseCurrency.current.symbolOrCode()})") }, singleLine = true, isError = amountInvalid,
                supportingText = { Text(if (amountInvalid) "Enter an amount above zero, with at most 2 decimals" else "Leave empty if it differs each time; you'll be asked when it's due") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("rec_amount"),
            )
            if (kind != RecurringKind.TRANSFER) Text("Category", style = MaterialTheme.typography.labelMedium)
            if (kind != RecurringKind.TRANSFER) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                offeredCategories(categories, income = kind == RecurringKind.INCOME, keep = category).forEach { c ->
                    FilterChip(selected = c.id == category, onClick = { category = c.id }, label = { Text(displayName(categories, c)) }, modifier = Modifier.testTag("rec_cat_${c.id}"))
                }
            }
            Text(when (kind) { RecurringKind.TRANSFER -> "From"; RecurringKind.INCOME -> "Received in"; RecurringKind.EXPENSE -> "Account" }, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> FilterChip(selected = a.id == account, onClick = { account = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("rec_acct_${a.id}")) }
            }
            if (kind == RecurringKind.TRANSFER) {
                Text("To", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a -> FilterChip(selected = a.id == toAccount, onClick = { toAccount = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("rec_to_${a.id}")) }
                }
                if (toAccount == account) Text("Choose two different accounts.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("rec_problem"))
            }
            HorizontalDivider()
            Text("Repeats", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every")
                Stepper(every, 1..Schedule.MAX_EVERY, "rec_every", "Repeat interval") { every = it }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UNIT_LABELS.forEach { (u, label) -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(if (every == 1) label else label + "s") }, modifier = Modifier.testTag("rec_unit_${u.name}")) }
            }
            OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.testTag("rec_start")) { Text("Starts ${startDate.label()}") }
            Text("Ends", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = endKind == "never", onClick = { endKind = "never" }, label = { Text("Never") }, modifier = Modifier.testTag("rec_end_never"))
                FilterChip(selected = endKind == "date", onClick = { endKind = "date" }, label = { Text("On a date") }, modifier = Modifier.testTag("rec_end_date"))
                FilterChip(selected = endKind == "count", onClick = { endKind = "count" }, label = { Text("After a number of times") }, modifier = Modifier.testTag("rec_end_count"))
            }
            if (endKind == "date") OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.testTag("rec_end_picker")) { Text(endDate.label()) }
            if (endKind == "count") Row(verticalAlignment = Alignment.CenterVertically) { Stepper(endCount, 1..1_000, "rec_end_times", "Number of times") { endCount = it }; Text("times") }
            endProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("rec_end_problem")) }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = auto && amount != null, onCheckedChange = { auto = it }, enabled = amount != null, modifier = Modifier.testTag("rec_auto"))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Post by itself")
                    Text(
                        if (amount == null) "Needs a fixed amount" else "Posted on its due date when you open the app; you can undo it. Otherwise it waits for your OK.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (start != null) {
                HorizontalDivider()
                OutlinedButton(onClick = { confirmingDelete = true }, modifier = Modifier.testTag("rec_delete")) { Text("Delete") }
            }
        }
    }
}
