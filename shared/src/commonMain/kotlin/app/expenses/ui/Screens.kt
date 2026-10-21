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
import androidx.compose.foundation.layout.size
import app.expenses.core.domain.BudgetState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.formatted
import androidx.compose.ui.graphics.Color
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
    onMoreShown: () -> Unit = {},
    /** Whether the More page is showing. It lives above the screens, so a screen opened from More returns to More on Back. */
    moreOpen: Boolean = false,
    onMoreChange: (Boolean) -> Unit = {},
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
    val feel = rememberFeel()
    val snackbar = remember { SnackbarHostState() }
    // the Undo bar of an entry just saved would sit over the foot of the More page: opening another page lets it go
    androidx.compose.runtime.LaunchedEffect(moreOpen) { if (moreOpen) snackbar.currentSnackbarData?.dismiss() }
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
        if (result == SnackbarResult.ActionPerformed) { feel(Feel.UNDO); onUndo(undo) }
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
    // while the tour is running it says whether the More page is open; otherwise (null) the person decides
    androidx.compose.runtime.LaunchedEffect(tourMore) { tourMore?.let { onMoreChange(it); if (it) onMoreShown() } }
    BackHandler(enabled = moreOpen && selected.isEmpty()) { onMoreChange(false) }
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
                selection = bulkSelectionOf(state.rows, selected),
            )
            else HomeBar(
                selected = if (moreOpen) "More" else "Home",
                onHome = { onMoreChange(false) }, onSearch = { onMoreChange(false); onOpenSearch() }, onReports = { onMoreChange(false); onOpenReports() },
                onMore = { onMoreChange(!moreOpen) }, moreBadge = state.showBackupNudge,
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
            // a page of its own (not a pop-up over the list): everything the app can do, grouped. Opening a place keeps this page underneath, so Back comes back to it
            val platform = LocalPlatform.current
            val money = Area.MONEY; val teal = Area.CAPTURE; val purple = Area.LOOK; val green = Area.PRIVACY; val amber = Area.HELP
            val reads = platform.supportsSmsCapture || platform.supportsShortcutsCapture
            val activeAccounts = state.accountRows.filter { !it.archived }
            val net = Money(activeAccounts.sumOf { it.balanceMinor }, BaseCurrency.current).formatted()
            val visible = state.categories.filter { !it.hidden }
            val nudges = buildList {
                if (state.inboxPending > 0) add(MoreNudge("open_inbox", Icons.Outlined.Inbox, "To review", "${state.inboxPending} waiting for you", state.inboxPending, Color(0xFF6750A4), Color(0xFF4F378B), onOpenInbox))
                if (state.billPeek.newCount > 0) add(MoreNudge("open_bills", Icons.Outlined.Receipt, "Bills", "${state.billPeek.newCount} new", state.billPeek.newCount, Color(0xFF00897B), Color(0xFF004D40), onOpenBills))
            }
            MorePage(
                Modifier.padding(padding), nudges,
                reminder = if (state.showBackupNudge) ({ BackupNudgeCard(onBackUp = onOpenData, onLater = onSnoozeBackupNudge) }) else null,
                sections = listOf(
                    MoreSection("Inbox and bills", teal.top, teal.bottom, listOf(
                        MoreRow("open_inbox", Icons.Outlined.Inbox, "To review", if (state.inboxPending > 0) "${state.inboxPending} waiting for you" else "Nothing waiting", onClick = onOpenInbox),
                        MoreRow("open_bills", Icons.Outlined.Receipt, "Bills", if (state.billPeek.newCount > 0) "${state.billPeek.newCount} new" else "Card and utility bills found in your messages", onClick = onOpenBills),
                    )),
                    MoreSection("Money", money.top, money.bottom, listOf(
                        MoreRow("manage_accounts", Icons.Outlined.AccountBalance, "Accounts", "${activeAccounts.size} ${if (activeAccounts.size == 1) "account" else "accounts"} \u00B7 net $net", onClick = onManageAccounts),
                        MoreRow("manage_categories", Icons.Outlined.Label, "Categories", "${visible.count { it.kind.forExpense }} for expenses \u00B7 ${visible.count { it.kind.forIncome }} for income", onClick = onManageCategories),
                        MoreRow("open_recurring", Icons.Outlined.Repeat, "Repeating expenses", "Rent, subscriptions, EMIs", onClick = onOpenRecurring),
                        MoreRow("open_calendar", Icons.Outlined.CalendarMonth, "Calendar", "Spending day by day", onClick = onOpenCalendar),
                        MoreRow("open_loans", Icons.Outlined.SwapHoriz, "Lent and borrowed", "Who owes whom", onClick = onOpenLoans),
                    )),
                    MoreSection("Capture", teal.top, teal.bottom, listOf(
                        MoreRow("open_capture", Icons.Outlined.Sms, if (reads) "Capture from texts" else "Capture from payment apps", if (reads) "Bank texts and payment alerts, added only when you approve" else "Google Pay, PhonePe, Paytm and BHIM alerts, added only when you approve", onClick = onOpenCapture),
                        MoreRow("open_notifications", Icons.Outlined.Notifications, "Notifications", "Bills, budgets and the weekly recap", onClick = onOpenNotifications),
                    )),
                    MoreSection("Make it yours", purple.top, purple.bottom, listOf(
                        MoreRow("open_appearance", Icons.Outlined.Palette, "Appearance", "Theme, density and motion speed", onClick = onOpenAppearance),
                        MoreRow("open_home_layout", Icons.Outlined.Dashboard, "Customise home", "Choose and reorder the blocks", onClick = onCustomiseHome),
                        MoreRow("open_currency", Icons.Outlined.CurrencyRupee, "Currency", BaseCurrency.current.code, onClick = onOpenCurrency),
                    )),
                    MoreSection("Privacy and data", green.top, green.bottom, listOf(
                        MoreRow("toggle_privacy", if (state.hideAmounts) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (state.hideAmounts) "Show amounts" else "Hide amounts", "Replace every figure with dots", switchOn = state.hideAmounts, onClick = onToggleHideAmounts),
                        MoreRow("open_security", Icons.Outlined.Lock, "App lock", "Fingerprint, face or your phone's PIN", onClick = onOpenSecurity),
                        MoreRow("open_privacy", Icons.Outlined.PrivacyTip, "Privacy: what this app can see", "Permissions in plain words", onClick = onOpenPrivacy),
                        MoreRow("open_data", Icons.Outlined.Backup, "Your data: export and backup", "Excel, CSV, backup and restore", onClick = onOpenData),
                    )),
                    MoreSection("Help", amber.top, amber.bottom, listOf(
                        MoreRow("open_guide", Icons.Outlined.Info, "How to use this app", "A two-minute tour of the real screens", onClick = onOpenGuide),
                    )),
                ),
            )
            return@Scaffold
        }
        LimitCrossedFeel(state.budget.overall.limit != null && state.budget.overall.state == BudgetState.OVER)
        val fold = remember { HeaderFold() }
        val foldPx = with(androidx.compose.ui.platform.LocalDensity.current) { 190.dp.toPx() }
        Column(Modifier.fillMaxSize().padding(padding).nestedScroll(remember(foldPx) { fold.connection(foldPx) })) {
            val onHero = MaterialTheme.colorScheme.onPrimary
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).tourSpot("hero").heroBackground()) {
                if (supportsBlur) HeroGlow(onHero) // Android 12 and later (always on iPhone): two softly blurred lights behind the figures
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp)) {
                    Text("Spent ${state.budget.periodName}", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f), maxLines = 1)
                    RollingAmount(
                        state.periodSpend, state.periodSpendMinor,
                        modifier = Modifier.padding(top = 4.dp).graphicsLayer { val k = 1f - 0.30f * fold.fraction; scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }.testTag("month_spend"),
                        color = onHero, style = MaterialTheme.typography.displaySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    if (state.spark.compare.isNotEmpty()) Text(state.spark.compare, style = MaterialTheme.typography.bodySmall, color = onHero.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp).foldAway(fold).testTag("month_compare"))
                    if (!state.spark.isEmpty) HomeSpark(state.spark, onHero, Modifier.padding(top = 12.dp).foldAway(fold))
                }
            }
            Column(Modifier.foldAway(fold)) {
            if (!state.billPeek.isEmpty) BillsBanner(state.billPeek, onOpenBills)
            if (state.inboxPending > 0) InboxBanner(state, onOpenInbox)
            state.homeLayout.visible().forEach { section ->
                when (section) {
                    HomeSection.BUDGET -> BudgetSummary(state.budget, onOpenBudget)
                    HomeSection.BILLS -> DueSection(state.due, onPost = { d -> if (d.amount == null) askingFor = d else onPostDue(d, null) }, onSkip = onSkipDue, onOpenAll = onOpenRecurring)
                    // on a brand-new install the two empty tiles say nothing and would push the welcome card's Skip off the screen: they wait for the first entry or for Skip
                    HomeSection.BALANCES -> if (!(state.rows.isEmpty() && state.showWelcome)) BalancesStrip(state.accountRows.filter { !it.archived }, onManageAccounts)
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
            }
            Spacer(Modifier.height(4.dp))
            if (state.rows.isEmpty()) {
                if (state.showWelcome) {
                    WelcomeCard(onAdd = onAdd, onImportOrRestore = onOpenData, onSkip = onDismissWelcome)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            EmptyArt(EmptyKind.HOME, Modifier.size(140.dp))
                            Text("No expenses yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.testTag("list"), contentPadding = PaddingValues(bottom = 88.dp)) {
                    itemsIndexed(listItems, key = { _, it -> if (it is RowUi) it.seq else "day$it" }, contentType = { _, it -> if (it is RowUi) "row" else "day" }) { index, item ->
                        if (item is RowUi) SwipeableRow(
                            item, pos = CardPos.of(first = listItems.getOrNull(index - 1) !is RowUi, last = listItems.getOrNull(index + 1) !is RowUi), onOpen, onDelete, selecting = selected.isNotEmpty(), isSelected = item.id in selected,
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
private fun SwipeableRow(row: RowUi, pos: CardPos, onOpen: (RowUi) -> Unit, onDelete: (RowUi) -> Unit, selecting: Boolean, isSelected: Boolean, onLongPress: () -> Unit, onToggle: () -> Unit, fresh: Boolean = false, modifier: Modifier = Modifier) {
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
        modifier = modifier.entryCardClip(pos).sharedContainer("entry:${row.id}").testTag("row_${row.title}"),
    ) {
        Row(
            Modifier.fillMaxWidth().entryCardFill(pos, isSelected)
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
                    Text(if (peek.newCount > 0) (if (peek.newCount == 1) "1 new bill found" else "${peek.newCount} new bills found") else if (peek.dueThisWeek > 0) (if (peek.dueThisWeek == 1) "1 bill due this week" else "${peek.dueThisWeek} bills due this week") else "Bills coming up", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.testTag("bills_banner_title"))
                    Text(if (peek.newCount > 0) "Found in your messages. Add them to be reminded." else if (peek.dueThisWeek > 0) "Anything overdue is included" else "Due in the next two weeks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
