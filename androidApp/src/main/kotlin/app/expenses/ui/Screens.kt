package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import app.expenses.UndoUi
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi
import app.expenses.RowUi
import app.expenses.TemplateUi
import app.expenses.UiState
import app.expenses.core.domain.Currency
import app.expenses.DueUi
import app.expenses.core.domain.Money
import app.expenses.core.domain.parseAmount

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryListScreen(
    state: UiState.Ready,
    onAdd: () -> Unit,
    onLoadMore: () -> Unit,
    onMessageShown: () -> Unit,
    onTemplate: (TemplateUi) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onUndo: (UndoUi) -> Unit,
    onUndoDismissed: () -> Unit,
    onOpen: (RowUi) -> Unit,
    onDelete: (RowUi) -> Unit,
    onManageCategories: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenData: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSecurity: () -> Unit,
    onDismissWelcome: () -> Unit,
    onSnoozeBackupNudge: () -> Unit,
    onPostDue: (DueUi, Money?) -> Unit,
    onSkipDue: (DueUi) -> Unit,
) {
    var askingFor by remember { mutableStateOf<DueUi?>(null) }
    askingFor?.let { d -> AmountDialog("Amount for ${d.name}", onConfirm = { onPostDue(d, it); askingFor = null }, onDismiss = { askingFor = null }) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var removing by remember { mutableStateOf<TemplateUi?>(null) }
    LaunchedEffect(listState, state.rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.rows.size - 10) onLoadMore() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    LaunchedEffect(state.undo) {
        val undo = state.undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(undo.text, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onUndo(undo)
        onUndoDismissed()
    }
    removing?.let { t ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove \"${t.name}\"?") },
            text = { Text("The quick entry is removed. Expenses already recorded with it stay.") },
            confirmButton = { TextButton(onClick = { onDeleteTemplate(t.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd, modifier = Modifier.testTag("add")) { Text("+", style = MaterialTheme.typography.headlineMedium) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Spent ${state.budget.periodName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = onOpenSearch, modifier = Modifier.testTag("open_search")) { Text("Search") }
                    TextButton(onClick = onOpenReports, modifier = Modifier.testTag("open_reports")) { Text("Reports") }
                    var more by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { more = true }, modifier = Modifier.testTag("more_menu")) { Text("More") }
                        // The less frequent places: kept in a menu so the header never has to squeeze or scroll.
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text("Categories") }, onClick = { more = false; onManageCategories() }, modifier = Modifier.testTag("manage_categories"))
                            DropdownMenuItem(text = { Text("Repeating expenses") }, onClick = { more = false; onOpenRecurring() }, modifier = Modifier.testTag("open_recurring"))
                            DropdownMenuItem(text = { Text("Notifications") }, onClick = { more = false; onOpenNotifications() }, modifier = Modifier.testTag("open_notifications"))
                            DropdownMenuItem(text = { Text("App lock") }, onClick = { more = false; onOpenSecurity() }, modifier = Modifier.testTag("open_security"))
                            DropdownMenuItem(text = { Text("Your data: export and backup") }, onClick = { more = false; onOpenData() }, modifier = Modifier.testTag("open_data"))
                        }
                    }
                }
                Text(state.periodSpend, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("month_spend"))
            }
            BudgetSummary(state.budget, onOpenBudget)
            if (state.showBackupNudge) BackupNudgeCard(onBackUp = onOpenData, onLater = onSnoozeBackupNudge)
            DueSection(state.due, onPost = { d -> if (d.amount == null) askingFor = d else onPostDue(d, null) }, onSkip = onSkipDue, onOpenAll = onOpenRecurring)
            Spacer(Modifier.height(8.dp))
            if (state.templates.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.testTag("templates"),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.templates, key = { it.id }) { t ->
                        AssistChip(
                            onClick = { onTemplate(t) },
                            label = { Text(if (t.amountText != null) "${t.name} ${t.amountText}" else "${t.name} …") },
                            modifier = Modifier.testTag("template_${t.name}").combinedClickable(onClick = { onTemplate(t) }, onLongClick = { removing = t }),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            HorizontalDivider()
            if (state.rows.isEmpty()) {
                if (state.showWelcome) {
                    WelcomeCard(onAdd = onAdd, onImportOrRestore = onOpenData, onSkip = onDismissWelcome)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No expenses yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.testTag("list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.rows, key = { it.seq }, contentType = { "row" }) { SwipeableRow(it, onOpen, onDelete) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(row: RowUi, onOpen: (RowUi) -> Unit, onDelete: (RowUi) -> Unit) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(row); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        modifier = Modifier.testTag("row_${row.title}"),
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onOpen(row) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(if (row.inflow) "+${row.amount}" else row.amount, style = MaterialTheme.typography.bodyLarge, color = if (row.inflow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** What the form starts with. For a new expense these are the remembered defaults; for an edit, the stored values. */
data class FormStart(val amountText: String, val category: String, val accountId: String, val note: String, val occurredAt: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(
    title: String,
    start: FormStart,
    accounts: List<AccountUi>,
    categories: List<CategoryUi>,
    onCreateCategory: (name: String, parentId: String?, onCreated: (String) -> Unit) -> Unit,
    showTemplateOption: Boolean,
    onSave: (amount: Money, category: String, note: String, accountId: String, saveAsTemplate: Boolean, occurredAt: Long) -> Unit,
    onBack: () -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(start.amountText) }
    var category by rememberSaveable { mutableStateOf(start.category) }
    var note by rememberSaveable { mutableStateOf(start.note) }
    var accountId by rememberSaveable { mutableStateOf(start.accountId) }
    var occurredAt by rememberSaveable { mutableStateOf(start.occurredAt) }
    var saveAsTemplate by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    val amount = parseAmount(amountText, Currency.INR)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (start.amountText.isEmpty()) focus.requestFocus() }
    val zone = ZoneId.systemDefault()
    val dateLabel = DateTimeFormatter.ofPattern("EEE, d MMM yyyy").withZone(zone).format(Instant.ofEpochMilli(occurredAt))

    if (creatingCategory) {
        CategoryNameDialog(
            title = "New category", categories = categories, allowParent = false,
            onConfirm = { name, parent -> onCreateCategory(name, parent) { id -> category = id }; creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { utc ->
                        val picked = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
                        occurredAt = picked.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("cancel")) { Text("Cancel") } }) },
        // Pinned above the keyboard so Save is always reachable, however small the screen.
        bottomBar = {
            Button(
                onClick = { amount?.let { onSave(it, category, note, accountId, saveAsTemplate, occurredAt) } },
                enabled = amount != null && amount.minorUnits > 0,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("save"),
            ) { Text("Save") }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amountText = v },
                label = { Text("Amount (₹)") },
                isError = amountText.isNotEmpty() && amount == null,
                supportingText = { if (amountText.isNotEmpty() && amount == null) Text("Enter an amount with at most 2 decimals") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("amount"),
            )
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.testTag("date")) { Text(dateLabel) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Hidden categories are not offered, except one already chosen (an entry being edited keeps its category).
                categories.filter { !it.hidden || it.id == category }.forEach { c ->
                    FilterChip(
                        selected = c.id == category, onClick = { category = c.id },
                        label = { Text(displayName(categories, c)) },
                        leadingIcon = c.colorArgb?.let { col -> { ColorDot(col) } },
                        modifier = Modifier.testTag("category_chip_${c.id}"),
                    )
                }
                AssistChip(onClick = { creatingCategory = true }, label = { Text("+ New") }, modifier = Modifier.testTag("new_category_chip"))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { a -> FilterChip(selected = a.id == accountId, onClick = { accountId = a.id }, label = { Text(a.name) }) }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note or merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("note"))
            if (showTemplateOption) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it }, modifier = Modifier.testTag("save_template"))
                    Text("Save as a quick entry")
                }
            }
        }
    }
}

/** Explains why an entry cannot be edited here yet, instead of offering a form that would lose information. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyEntryScreen(title: String, reason: String, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title, maxLines = 1) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("cancel")) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Can't edit this entry here", style = MaterialTheme.typography.titleMedium)
            Text(reason, modifier = Modifier.testTag("read_only_reason"))
        }
    }
}

@Composable
fun OpeningScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun KeyLostScreen(message: String, onStartFresh: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Your data can't be opened", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "The key that protects the saved data on this phone is gone, which can happen after a restore or a security reset. " +
                "The data can't be recovered from this phone. ($message)",
        )
        Spacer(Modifier.height(24.dp))
        if (!confirm) {
            Button(onClick = { confirm = true }) { Text("Start fresh…") }
        } else {
            Text("This permanently deletes the unreadable data and starts an empty ledger.", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { confirm = false }) { Text("Keep it") }
                Button(onClick = onStartFresh) { Text("Delete and start fresh") }
            }
        }
    }
}
