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
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
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
    onOpenLoans: () -> Unit,
    onOpenBills: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    tourMore: Boolean? = null,
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
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    // while the tour is running it says whether the More page is open; otherwise (null) the person decides
    androidx.compose.runtime.LaunchedEffect(tourMore) { tourMore?.let { moreOpen = it } }
    BackHandler(enabled = moreOpen && selected.isEmpty()) { moreOpen = false }
    val closeMore = { moreOpen = false }
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
                selected = if (moreOpen) "More" else "Home",
                onHome = { moreOpen = false }, onSearch = { moreOpen = false; onOpenSearch() }, onReports = { moreOpen = false; onOpenReports() },
                onMore = { moreOpen = !moreOpen },
            )
        },
        floatingActionButton = {
            if (!moreOpen) {
            // shrinks to a round button while the list is scrolled, and opens up again at the top
            val expanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
            androidx.compose.material3.ExtendedFloatingActionButton(onClick = onAdd, expanded = expanded, icon = { Text("+", style = MaterialTheme.typography.headlineMedium) }, text = { Text("Add") }, modifier = Modifier.testTag("add"))
            }
        },
    ) { padding ->
        if (moreOpen) {
            // a page of its own (not a pop-up over the list): everything the app can do, grouped, in as many columns as the screen is wide enough for
            MorePage(Modifier.padding(padding)) {
                                MenuGroup("Inbox") {
                                    MenuTile(Icons.Outlined.Inbox, "To review", "open_inbox", badge = state.inboxPending.takeIf { it > 0 }?.toString()) { closeMore(); onOpenInbox() }
                                    MenuTile(Icons.Outlined.Sms, "Capture from texts", "open_capture") { closeMore(); onOpenCapture() }
                                }
                                MenuGroup("Money") {
                                    MenuTile(Icons.Outlined.AccountBalance, "Accounts", "manage_accounts") { closeMore(); onManageAccounts() }
                                    MenuTile(Icons.Outlined.Label, "Categories", "manage_categories") { closeMore(); onManageCategories() }
                                    MenuTile(Icons.Outlined.Repeat, "Repeating expenses", "open_recurring") { closeMore(); onOpenRecurring() }
                                    MenuTile(Icons.Outlined.CalendarMonth, "Calendar", "open_calendar") { closeMore(); onOpenCalendar() }
                                    MenuTile(Icons.Outlined.SwapHoriz, "Lent and borrowed", "open_loans") { closeMore(); onOpenLoans() }
                                    MenuTile(Icons.Outlined.Receipt, "Bills", "open_bills", badge = state.billPeek.newCount.takeIf { it > 0 }?.toString()) { closeMore(); onOpenBills() }
                                }
                                MenuGroup("Make it yours") {
                                    MenuTile(Icons.Outlined.Palette, "Appearance", "open_appearance") { closeMore(); onOpenAppearance() }
                                    MenuTile(Icons.Outlined.Dashboard, "Customise home", "open_home_layout") { closeMore(); onCustomiseHome() }
                                    MenuTile(Icons.Outlined.CurrencyRupee, "Currency", "open_currency") { closeMore(); onOpenCurrency() }
                                    MenuTile(Icons.Outlined.Notifications, "Notifications", "open_notifications") { closeMore(); onOpenNotifications() }
                                }
                                MenuGroup("Privacy and data") {
                                    MenuTile(if (state.hideAmounts) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (state.hideAmounts) "Show amounts" else "Hide amounts", "toggle_privacy") { closeMore(); onToggleHideAmounts() }
                                    MenuTile(Icons.Outlined.Lock, "App lock", "open_security") { closeMore(); onOpenSecurity() }
                                    MenuTile(Icons.Outlined.PrivacyTip, "Privacy: what this app can see", "open_privacy") { closeMore(); onOpenPrivacy() }
                                    MenuTile(Icons.Outlined.Backup, "Your data: export and backup", "open_data") { closeMore(); onOpenData() }
                                }
                                MenuGroup("Help") {
                                    MenuTile(Icons.Outlined.Info, "How to use this app", "open_guide") { closeMore(); onOpenGuide() }
                                }
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            val onHero = MaterialTheme.colorScheme.onPrimary
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).tourSpot("hero").heroBackground()) {
                if (android.os.Build.VERSION.SDK_INT >= 31) HeroGlow(onHero) // Android 12 and later: two softly blurred lights behind the figures
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp)) {
                    Text("Spent ${state.budget.periodName}", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f), maxLines = 1)
                Text(state.periodSpend, style = MaterialTheme.typography.displaySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = onHero, modifier = Modifier.padding(top = 4.dp).testTag("month_spend"))
                }
            }
            if (!state.billPeek.isEmpty) BillsBanner(state.billPeek, onOpenBills)
            if (state.inboxPending > 0) InboxBanner(state, onOpenInbox)
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
                            modifier = Modifier.animateItem(), fresh = state.undo?.let { u -> !u.restore && u.entryId == item.id } == true,
                        ) else DayHeader(item as Long)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(row: RowUi, onOpen: (RowUi) -> Unit, onDelete: (RowUi) -> Unit, selecting: Boolean, isSelected: Boolean, onLongPress: () -> Unit, onToggle: () -> Unit, fresh: Boolean = false, modifier: Modifier = Modifier) {
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(row); true } else false
        },
    )
    // a row that has just been added glows in the accent colour and settles over a second, so the eye finds it
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(fresh) { if (fresh) { flash.snapTo(1f); flash.animateTo(0f, androidx.compose.animation.core.tween(1100)) } }
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = flash.value * 0.16f))
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

/**
 * What is waiting in To review, at a glance: how many, how much goes out and comes in, the first few by name, and one button. The whole card opens the list.
 */
@Composable
private fun InboxBanner(state: UiState.Ready, onOpen: () -> Unit) {
    val peek = state.inboxPeek
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onOpen).testTag("inbox_banner"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${state.inboxPending} to review", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.testTag("inbox_banner_title"))
                    if (peek.outText.isNotEmpty()) Text("${peek.outText} out · ${peek.inText} in, found in your texts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Review", color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiary).padding(horizontal = 16.dp, vertical = 8.dp))
            }
            peek.rows.forEach { r ->
                val flow = if (r.credit) Flow3.IN else Flow3.OUT
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(r.title, r.title, Modifier.padding(end = 10.dp), size = 28.dp)
                    Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(signedAmount(r.amountText, flow), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = amountColor(flow), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (peek.more > 0) Text("and ${peek.more} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Bills at a glance: how many are new, and the first few with when they are due. The whole card opens the bills page. */
@Composable
private fun BillsBanner(peek: app.expenses.BillPeekUi, onOpen: () -> Unit) {
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onOpen).testTag("bills_banner"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (peek.newCount > 0) (if (peek.newCount == 1) "1 new bill found" else "${peek.newCount} new bills found") else "Bills coming up", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.testTag("bills_banner_title"))
                    Text(if (peek.newCount > 0) "Found in your messages. Add them to be reminded." else "Due in the next two weeks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (peek.newCount > 0) "Add" else "Open", color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondary).padding(horizontal = 16.dp, vertical = 8.dp))
            }
            peek.rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("bills_banner_row_${r.id}")) {
                    CategoryAvatar(r.title, r.title, Modifier.padding(end = 10.dp), size = 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (r.dueIn.isNotEmpty()) Text("Due ${r.dueIn}", style = MaterialTheme.typography.bodySmall, color = if (r.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(r.amountText, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (peek.more > 0) Text("and ${peek.more} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
