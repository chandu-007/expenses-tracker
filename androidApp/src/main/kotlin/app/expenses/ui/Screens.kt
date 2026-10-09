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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import app.expenses.core.domain.AmountMask
import app.expenses.core.domain.HomeSection
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
    onManageAccounts: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenData: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenCalendar: () -> Unit,
    onCustomiseHome: () -> Unit,
    onToggleHideAmounts: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSecurity: () -> Unit,
    onDismissWelcome: () -> Unit,
    onSnoozeBackupNudge: () -> Unit,
    onPostDue: (DueUi, Money?) -> Unit,
    onSkipDue: (DueUi) -> Unit,
    onBulkCategory: (List<String>, String) -> Unit,
    onBulkAccount: (List<String>, String) -> Unit,
    onBulkDelete: (List<String>) -> Unit,
    onBulkAddTag: (List<String>, String) -> Unit,
    onBulkRemoveTag: (List<String>, String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptyList() }
    var askingFor by remember { mutableStateOf<DueUi?>(null) }
    askingFor?.let { d -> AmountDialog("Amount for ${d.name}", onConfirm = { onPostDue(d, it); askingFor = null }, onDismiss = { askingFor = null }) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var removing by remember { mutableStateOf<TemplateUi?>(null) }
    // the rows grouped under a heading for each day; the heading is an item of its own so scrolling stays lazy
    val listItems = remember(state.rows) { buildList<Any> { var last = Long.MIN_VALUE; for (r in state.rows) { if (r.day != last) { add(r.day); last = r.day }; add(r) } } }
    LaunchedEffect(listState, listItems) {
        // the position counts the day headings too, so count the entries up to it: more is loaded when one of the last ten entries is on screen
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (listItems.take(last + 1).count { it is RowUi } >= state.rows.size - 10) onLoadMore() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(if (state.hideAmounts) AmountMask.mask(it) else it); onMessageShown() }
    }
    LaunchedEffect(state.undo) {
        val undo = state.undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(if (state.hideAmounts) AmountMask.mask(undo.text) else undo.text, actionLabel = "Undo", duration = SnackbarDuration.Long)
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
        bottomBar = {
            if (selected.isNotEmpty()) BulkBar(
                selected.size, state.categories, state.accounts,
                onCategory = { c -> onBulkCategory(selected, c); selected = emptyList() },
                onAccount = { a -> onBulkAccount(selected, a); selected = emptyList() },
                onAddTag = { t -> onBulkAddTag(selected, t); selected = emptyList() },
                onRemoveTag = { t -> onBulkRemoveTag(selected, t); selected = emptyList() },
                tags = state.tags,
                onDelete = { onBulkDelete(selected); selected = emptyList() },
                onCancel = { selected = emptyList() },
            )
            else HomeBar(
                onSearch = onOpenSearch, onReports = onOpenReports,
                more = { open, dismiss ->
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        DropdownMenu(
                            expanded = open, onDismissRequest = dismiss, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp,
                        ) {
                            // two tiles to a row, so all fourteen places fit on a phone screen without scrolling
                            Column(Modifier.width(332.dp).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                MenuGroup("Inbox") {
                                    MenuTile("📥", "To review", "open_inbox", badge = state.inboxPending.takeIf { it > 0 }?.toString()) { dismiss(); onOpenInbox() }
                                    MenuTile("📱", "Capture from texts", "open_capture") { dismiss(); onOpenCapture() }
                                }
                                MenuGroup("Money") {
                                    MenuTile("🏦", "Accounts", "manage_accounts") { dismiss(); onManageAccounts() }
                                    MenuTile("🏷", "Categories", "manage_categories") { dismiss(); onManageCategories() }
                                    MenuTile("🔁", "Repeating expenses", "open_recurring") { dismiss(); onOpenRecurring() }
                                    MenuTile("📅", "Calendar", "open_calendar") { dismiss(); onOpenCalendar() }
                                }
                                MenuGroup("Make it yours") {
                                    MenuTile("🎨", "Appearance", "open_appearance") { dismiss(); onOpenAppearance() }
                                    MenuTile("🏠", "Customise home", "open_home_layout") { dismiss(); onCustomiseHome() }
                                    MenuTile("💱", "Currency", "open_currency") { dismiss(); onOpenCurrency() }
                                    MenuTile("🔔", "Notifications", "open_notifications") { dismiss(); onOpenNotifications() }
                                }
                                MenuGroup("Privacy and data") {
                                    MenuTile("👁", if (state.hideAmounts) "Show amounts" else "Hide amounts", "toggle_privacy") { dismiss(); onToggleHideAmounts() }
                                    MenuTile("🔒", "App lock", "open_security") { dismiss(); onOpenSecurity() }
                                    MenuTile("🔍", "Privacy: what this app can see", "open_privacy") { dismiss(); onOpenPrivacy() }
                                    MenuTile("💾", "Your data: export and backup", "open_data") { dismiss(); onOpenData() }
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // shrinks to a round button while the list is scrolled, and opens up again at the top
            val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
            androidx.compose.material3.ExtendedFloatingActionButton(onClick = onAdd, expanded = expanded, icon = { Text("+", style = MaterialTheme.typography.headlineMedium) }, text = { Text("Add") }, modifier = Modifier.testTag("add"))
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val onHero = MaterialTheme.colorScheme.onPrimary
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heroBackground()) {
                if (android.os.Build.VERSION.SDK_INT >= 31) HeroGlow(onHero) // Android 12 and later: two softly blurred lights behind the figures
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp)) {
                    Text("Spent ${state.budget.periodName}", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f), maxLines = 1)
                Text(state.periodSpend, style = MaterialTheme.typography.displaySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = onHero, modifier = Modifier.padding(top = 4.dp).testTag("month_spend"))
                }
            }
            if (state.inboxPending > 0) androidx.compose.material3.Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onOpenInbox).testTag("inbox_banner"),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)),
            ) {
                Text("${state.inboxPending} to review: transactions found in your texts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
            }
            if (state.showBackupNudge) BackupNudgeCard(onBackUp = onOpenData, onLater = onSnoozeBackupNudge)
            state.homeLayout.visible().forEach { section ->
                when (section) {
                    HomeSection.BUDGET -> BudgetSummary(state.budget, onOpenBudget)
                    HomeSection.BILLS -> DueSection(state.due, onPost = { d -> if (d.amount == null) askingFor = d else onPostDue(d, null) }, onSkip = onSkipDue, onOpenAll = onOpenRecurring)
                    HomeSection.BALANCES -> BalancesStrip(state.accountRows.filter { !it.archived }, onManageAccounts)
                    HomeSection.QUICK_ENTRIES -> {
                if (state.templates.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.testTag("templates"),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.templates, key = { it.id }) { t ->
                            LongPressChip(
                                label = if (t.amountText != null) "${t.name} ${t.amountText}" else "${t.name} …",
                                onClick = { onTemplate(t) }, onLongClick = { removing = t },
                                modifier = Modifier.testTag("template_${t.name}"),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
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
                    items(listItems, key = { if (it is RowUi) it.seq else "day$it" }, contentType = { if (it is RowUi) "row" else "day" }) { item ->
                        if (item is RowUi) SwipeableRow(
                            item, onOpen, onDelete, selecting = selected.isNotEmpty(), isSelected = item.id in selected,
                            onLongPress = { selected = selected.toggled(item.id) }, onToggle = { selected = selected.toggled(item.id) },
                            modifier = Modifier.animateItem(),
                        ) else DayHeader(item as Long)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(row: RowUi, onOpen: (RowUi) -> Unit, onDelete: (RowUi) -> Unit, selecting: Boolean, isSelected: Boolean, onLongPress: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(row); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selecting,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        modifier = modifier.testTag("row_${row.title}"),
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = { if (selecting) onToggle() else onOpen(row) }, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = rowPadding(12.dp)).semantics { selected = isSelected },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp)) else CategoryAvatar(row.category, row.title, Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val flow = flowOf(row.kind, row.inflow)
            Text(signedAmount(row.amount, flow), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = amountColor(flow), modifier = Modifier.testTag("amount_${row.title}"))
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
