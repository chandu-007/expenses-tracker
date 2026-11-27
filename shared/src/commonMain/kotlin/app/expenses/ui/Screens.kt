package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.size
import app.expenses.core.domain.BudgetState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.outlined.Close
import app.expenses.core.domain.HomeNudge
import app.expenses.core.domain.HomeNudges
import app.expenses.core.domain.NudgePause
import app.expenses.core.domain.DayEntry
import app.expenses.core.domain.wholeAmounts
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import app.expenses.core.domain.DayTotals
import kotlinx.coroutines.launch
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
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
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.formatted
import androidx.compose.ui.graphics.Color
import app.expenses.core.domain.FirstWeekStep
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
    /** Tapping an account on Home's strip: show only its entries (null: all). */
    onFilterAccount: (String?) -> Unit = {},
    /** While Home shows one account: limit it to a month, or all dates. */
    onSetHomePeriod: (app.expenses.HomePeriod) -> Unit = {},
    /** Opens Search limited to this account. */
    onSearchAccount: (String) -> Unit = {},
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenData: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSubscriptions: () -> Unit = {},
    onOpenTransferCandidates: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    /** Travel mode: the trips page (More), and the summary of the trip that is on (Home's card). */
    onOpenTrips: () -> Unit = {},
    onOpenTrip: (String) -> Unit = {},
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
    /** The switch in the safe-to-spend dialog: show that line under the budget on Home. */
    onSafeToSpendOnHome: (Boolean) -> Unit = {},
    onOpenAppearance: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenVerify: () -> Unit,
    /** The guided capture setup (Home's card and More), hiding that card for good, the capture checkup, and "Bring in earlier spending". */
    onOpenSetup: () -> Unit = {},
    onHideSetup: () -> Unit = {},
    onOpenCheckup: () -> Unit = {},
    onOpenEarlier: () -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenSecurity: () -> Unit,
    onDismissWelcome: () -> Unit,
    /** The welcome card's "Show me around" (the five-stop tour), the Example month's "Hide example" and a tap on one of its expense rows (it opens Add filled in). */
    onStartTour: () -> Unit = {},
    onHideExample: () -> Unit = {},
    onExampleRow: (app.expenses.core.domain.ExampleRow) -> Unit = {},
    onSnoozeBackupNudge: () -> Unit,
    /** UI S6: "Thanks" on the milestone card (the id of the milestone shown). */
    onMilestoneThanks: (String) -> Unit = {},
    /** "Review" on the Home card that says how many expenses have no category, and its "Not now". */
    onOpenCategorise: () -> Unit = {},
    onSnoozeCategorise: () -> Unit = {},
    /** "Not now" on the Home cards that can be put off for a day: which one, and how many things it was about (see [HomeNudges]). */
    onSnoozeNudge: (HomeNudge, Int) -> Unit = { _, _ -> },
    onDismissFirstWeek: () -> Unit = {},
    onPostDue: (DueUi, Money?) -> Unit,
    onSkipDue: (DueUi) -> Unit,
    onBulkCategory: (List<String>, String) -> Unit,
    onBulkAccount: (List<String>, String) -> Unit,
    onBulkDelete: (List<String>) -> Unit,
    onBulkAddTag: (List<String>, String) -> Unit,
    onBulkRemoveTag: (List<String>, String) -> Unit,
    onBulkTransfer: ((List<String>, String) -> Unit)? = null,
) {
    // asked before anything that returns early (the More page): a call after that return shifted the screen's state and broke Back from a place opened in More
    // the graph and the small lines go in a two-pane list pane: it is narrow and the entries should start on its first screen
    val wide = rememberIsWide()
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptyList() }
    var askingFor by remember { mutableStateOf<DueUi?>(null) }
    askingFor?.let { d -> AmountDialog("Amount for ${d.name}", onConfirm = { onPostDue(d, it); askingFor = null }, onDismiss = { askingFor = null }) }
    var safeOpen by rememberSaveable { mutableStateOf(false) }
    if (safeOpen && state.safeToSpend != null) SafeToSpendDialog(state.safeToSpend, onClose = { safeOpen = false }, onShowOnHome = onSafeToSpendOnHome)
    val listState = rememberLazyListState()
    val feel = rememberFeel()
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // the Undo bar of an entry just saved would sit over the foot of the More page: opening another page lets it go
    androidx.compose.runtime.LaunchedEffect(moreOpen) { if (moreOpen) snackbar.currentSnackbarData?.dismiss() }
    var removing by remember { mutableStateOf<TemplateUi?>(null) }
    // the rows grouped under a heading for each day; the heading is an item of its own so scrolling stays lazy
    val listItems = remember(state.rows) { buildList<Any> { var last = Long.MIN_VALUE; for (r in state.rows) { if (r.day != last) { add(r.day); last = r.day }; add(r) } } }
    // the account Home is narrowed to, if any
    val filtered = state.accountRows.firstOrNull { it.id == state.homeAccountId }
    // the first-run screen: a brand-new install shows the welcome, and the month card, "Set a budget" and the safe-to-spend tiles above it say nothing yet (they come back after Skip or the first entry)
    val firstRun = state.showWelcome && state.rows.isEmpty() && filtered == null
    // the page's own items before the entries: the top block, and while an account is chosen its bar
    val lead = if (filtered != null) 2 else 1
    LaunchedEffect(listState, listItems, lead) {
        // the position counts those leading items and the day headings too: take the leading items off, then count the entries up to it.
        // More is loaded when one of the last ten entries is on screen
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (state.rows.isNotEmpty() && listItems.take((last - lead + 1).coerceAtLeast(0)).count { it is RowUi } >= state.rows.size - 10) onLoadMore() }
    }
    // choosing an account brings its bar (and the entries below it) to the top of the page; leaving the account view goes back to the cards
    // Only a real change of the account does it: the account last scrolled for is kept (and saved with the screen, so a rotation or coming back from Search or an
    // entry keeps the scroll position), seeded with the account shown now.
    var scrolledFor by rememberSaveable { mutableStateOf(state.homeAccountId) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(state.homeAccountId) {
        val now = state.homeAccountId
        val was = scrolledFor
        if (now == was) return@LaunchedEffect
        scrolledFor = now
        if (now != null) { androidx.compose.runtime.withFrameNanos { }; if (reduceMotion) listState.scrollToItem(1) else listState.animateScrollToItem(1) }
        else if (was != null) { if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
    }
    // an entry just added glows (see SwipeableRow): bring it into view if the page's top block, now part of the scrolling page, leaves it below the fold
    val freshId = state.undo?.takeIf { it.reveal && !it.restore }?.entryId
    val latestItems by androidx.compose.runtime.rememberUpdatedState(listItems)
    val latestLead by androidx.compose.runtime.rememberUpdatedState(lead)
    LaunchedEffect(freshId) {
        if (freshId == null) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { } // the new row is laid out
        val at = latestItems.indexOfFirst { it is RowUi && it.id == freshId }
        if (at < 0) return@LaunchedEffect
        val index = at + latestLead
        val shown = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val bottom = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
        if (shown == null) { if (reduceMotion) listState.scrollToItem(index, scrollOffset = -(bottom / 2)) else listState.animateScrollToItem(index, scrollOffset = -(bottom / 2)) }
        else if (shown.offset + shown.size > bottom) { if (reduceMotion) listState.scrollBy((shown.offset + shown.size - bottom).toFloat()) else listState.animateScrollBy((shown.offset + shown.size - bottom).toFloat()) }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(if (state.hideAmounts) AmountMask.mask(it) else it); onMessageShown() }
    }
    // UI S6: while Pause nudges is on, Home's suggestions, the backup reminder and the milestone card wait (a due bill does not: NudgePause)
    val nudgesPaused = NudgePause.isPaused(state.nudgesPausedUntil, app.expenses.nowMillis())
    LaunchedEffect(state.undo) {
        val undo = state.undo ?: return@LaunchedEffect
        // an entry just added is answered with a drawn tick beside the words (UI S6); the other bars (a deleted entry, a changed type) are as before
        val saved = undo.reveal && !undo.restore
        val result = snackbar.showSnackbar(StatusVisuals(if (state.hideAmounts) AmountMask.mask(undo.text) else undo.text, "Undo", SnackbarDuration.Long, tick = saved))
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
        snackbarHost = { SnackbarHost(snackbar) { data -> StatusSnackbar(data) } },
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
                onMakeTransfer = onBulkTransfer?.let { go -> { to -> go(selected, to); selected = emptyList() } },
            )
            else HomeBar(
                selected = if (moreOpen) "More" else "Home",
                onHome = { onMoreChange(false) }, onSearch = { onMoreChange(false); onOpenSearch() }, onReports = { onMoreChange(false); onOpenReports() },
                onMore = { onMoreChange(!moreOpen) }, moreBadge = state.showBackupNudge && !nudgesPaused,
                // the Add button sits in the middle of the bar (it used to float over the list); the More page has it too
                onAdd = { onMoreChange(false); onAdd() }, addTag = "add",
                // UI S8 (N-20): a long press on Add offers the first four saved quick entries with an amount, recorded at once like a tap on them on Home
                quickEntries = remember(state.templates) { state.templates.mapNotNull { t -> t.amountText?.let { BarQuickEntry(t.id, t.name, it) } }.take(4) },
                onQuickEntry = { id -> state.templates.firstOrNull { it.id == id }?.let(onTemplate) },
            )
        },
    ) { padding ->
        if (moreOpen) {
            // a page of its own (not a pop-up over the list): everything the app can do, grouped. Opening a place keeps this page underneath, so Back comes back to it
            val platform = LocalPlatform.current
            var feedbackFailed by remember { mutableStateOf(false) }
            val money = Area.MONEY; val teal = Area.CAPTURE; val purple = Area.LOOK; val green = Area.PRIVACY; val amber = Area.HELP
            val reads = platform.supportsSmsCapture || platform.supportsShortcutsCapture
            val activeAccounts = state.accountRows.filter { !it.archived }
            val net = Money(activeAccounts.sumOf { it.balanceMinor }, BaseCurrency.current).formatted()
            val visible = state.categories.filter { !it.hidden }
            // the cards at the top of More: the nudges Home has, in the same order, whether or not Home put its card off (the first two; every one has its row below)
            val counts = homeNudgeCounts(state)
            val nudges = buildList {
                counts[HomeNudge.BILLS_DUE]?.let { n -> add(MoreNudge("open_bills", Icons.Outlined.Receipt, "Bills", "$n due this week", n, Signal.watch, Signal.watch, onOpenBills)) }
                counts[HomeNudge.INBOX]?.let { n -> add(MoreNudge("open_inbox", Icons.Outlined.Inbox, "To review", "$n waiting for you", n, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary, onOpenInbox)) }
                if (state.billPeek.newCount > 0 && counts[HomeNudge.BILLS_DUE] == null) add(MoreNudge("open_bills", Icons.Outlined.Receipt, "Bills", "${state.billPeek.newCount} new", state.billPeek.newCount, Signal.watch, Signal.watch, onOpenBills))
                counts[HomeNudge.WAITING]?.let { n -> add(MoreNudge("open_loans", Icons.Outlined.SwapHoriz, "Money to get back", "$n ${if (n == 1) "expense" else "expenses"} waiting", n, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary, onOpenLoans)) }
                counts[HomeNudge.CATEGORISE]?.let { n -> add(MoreNudge("open_categorise", Icons.Outlined.Label, "No category", "$n ${if (n == 1) "expense" else "expenses"}", n, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary, onOpenCategorise)) }
            }
            MorePage(
                Modifier.padding(padding), nudges,
                reminder = if (state.showBackupNudge && !nudgesPaused) ({ BackupNudgeCard(onBackUp = onOpenData, onLater = onSnoozeBackupNudge) }) else null,
                sections = listOf(
                    MoreSection("Inbox and bills", teal.top, teal.bottom, listOf(
                        MoreRow("open_inbox", Icons.Outlined.Inbox, "To review", if (state.inboxPending > 0) "${state.inboxPending} waiting for you" else "Nothing waiting", onClick = onOpenInbox),
                        MoreRow("open_bills", Icons.Outlined.Receipt, "Bills", if (state.billPeek.newCount > 0) "${state.billPeek.newCount} new" else "Card and utility bills found in your messages", onClick = onOpenBills),
                    )),
                    MoreSection("Money", money.top, money.bottom, listOf(
                        MoreRow("open_budget", Icons.Outlined.AccountBalanceWallet, "Budget", state.budget.overall.limit?.let { "${state.budget.overall.note} of $it" } ?: "Set a limit for ${state.budget.periodName}", onClick = onOpenBudget),
                        MoreRow("manage_accounts", Icons.Outlined.AccountBalance, "Accounts", "${activeAccounts.size} ${if (activeAccounts.size == 1) "account" else "accounts"} \u00B7 net $net", onClick = onManageAccounts),
                        MoreRow("manage_categories", Icons.Outlined.Label, "Categories", "${visible.count { it.kind.forExpense }} for expenses \u00B7 ${visible.count { it.kind.forIncome }} for income", onClick = onManageCategories),
                        MoreRow("open_recurring", Icons.Outlined.Repeat, "Repeating expenses", "Rent, subscriptions, EMIs", onClick = onOpenRecurring),
                        MoreRow("open_calendar", Icons.Outlined.CalendarMonth, "Calendar", "Spending day by day", onClick = onOpenCalendar),
                        MoreRow("open_goals", Icons.Outlined.Flag, "Savings goals", "Save up for a trip or a laptop", onClick = onOpenGoals),
                        MoreRow("open_trips", Icons.Outlined.Flight, "Travel mode", state.tripCard?.let { "${it.title} \u00B7 ${it.dayText}" } ?: "Tag everything you spend on a trip", onClick = onOpenTrips),
                        MoreRow("open_subscriptions", Icons.Outlined.Autorenew, "Subscriptions", "Charges that keep coming, and what they cost a year", onClick = onOpenSubscriptions),
                        MoreRow("open_earlier_spending", Icons.Outlined.History, "Bring in earlier spending", "Started after you had already spent? Import a statement, add old entries, set balances", onClick = onOpenEarlier),
                        MoreRow("open_transfer_candidates", Icons.Outlined.CompareArrows, "Transfer candidates", "Cash withdrawals and moves between your accounts that are counted as spending", onClick = onOpenTransferCandidates),
                        MoreRow("open_loans", Icons.Outlined.SwapHoriz, "Lent and borrowed", if (state.waiting.isEmpty) "Who owes whom" else "Who owes whom \u00B7 ${state.waiting.count} ${if (state.waiting.count == 1) "expense" else "expenses"} to get back", onClick = onOpenLoans),
                        MoreRow("open_categorise", Icons.Outlined.Label, "Fill in missing categories", if (state.categoriseNudge > 0) "${state.categoriseNudge} ${if (state.categoriseNudge == 1) "expense has" else "expenses have"} no category" else "Go through expenses that have no category, one at a time", onClick = onOpenCategorise),
                    )),
                    MoreSection("Capture", teal.top, teal.bottom, listOf(
                        MoreRow("open_capture", Icons.Outlined.Sms, if (reads) "Capture from texts" else "Capture from payment apps", if (reads) "Bank texts and payment alerts, added only when you approve" else "Google Pay, PhonePe, Paytm and BHIM alerts, and bank emails if you turn that on, added only when you approve", onClick = onOpenCapture),
                        MoreRow("more_capture_checkup", Icons.Outlined.TaskAlt, "Why isn't capture working?", "Checks every step and says what to fix", onClick = onOpenCheckup),
                        MoreRow("more_capture_setup", Icons.Outlined.Checklist, "Set up reading bank texts", "Four short steps: permission, banks, accounts", onClick = onOpenSetup),
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
                        MoreRow("more_verify", Icons.Outlined.VerifiedUser, "Verify it yourself", "What this app cannot do, and how to check", onClick = onOpenVerify),
                        MoreRow("open_data", Icons.Outlined.Backup, "Your data: export and backup", "Excel, CSV, backup and restore", onClick = onOpenData),
                    )),
                    MoreSection("Help", amber.top, amber.bottom, listOf(
                        MoreRow("open_guide", Icons.Outlined.Info, "How to use this app", "A two-minute tour of the real screens", onClick = onOpenGuide),
                        MoreRow("open_feedback", Icons.Outlined.Feedback, "Send feedback", if (feedbackFailed) FEEDBACK_NO_MAIL_APP else "Opens your mail app with the version and phone model filled in. You send it", onClick = { feedbackFailed = !sendFeedback(platform) }),
                    )),
                ),
            )
            return@Scaffold
        }
        LimitCrossedFeel(state.budget.overall.limit != null && state.budget.overall.state == BudgetState.OVER)
        val large = isLargeText()
        // the money spent on each complete day, for the day headings: worked out from the rows already here (the last day loaded waits for the next page), no query
        val dayTotals = remember(state.rows, state.endReached, state.baseCurrency) {
            DayTotals.of(state.rows.map { DayEntry(it.day, it.kind, it.minor, it.currency) }, state.baseCurrency.code, state.endReached)
        }
        // The whole page is one list: the month card, the banners, the cards and the account bar are its first item and scroll away with the entries, so a small
        // screen is not left with a few rows under a fixed top. The empty states are items of the same list.
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).testTag("list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "home_top", contentType = "top") {
                Column(Modifier.fillMaxWidth().testTag("home_top")) {
                    // Slice 133: ONE white card: what is left to spend, its status, the pace bar, how this period stands against the last, and the two figures that say what can be spent (HomeHero). No graph.
                    // It reads what Home already holds; the Budget block of Customise home decides whether its budget parts show
                    // the header: the day, the month as the title, and the eye that hides or shows every amount
                    if (!firstRun) HomeHeader(hidden = state.hideAmounts, onToggle = onToggleHideAmounts)
                    val sections = state.homeLayout.visible()
                    // the hero is the Budget block of Customise home: first by default, then right at the top; moved lower it moves with the block; with the block hidden the top is the amount spent alone
                    val heroFirst = sections.firstOrNull() == HomeSection.BUDGET || HomeSection.BUDGET !in sections
                    if (!firstRun && heroFirst) HomeHero(state, showBudget = HomeSection.BUDGET in sections, onOpenBudget = onOpenBudget, onOpenSafe = { safeOpen = true }, large = large)
                    // Travel mode: while a trip is on, one compact line about it right under the month card. Information, not a nudge: it is never put off, is not in HomeNudges and does not count in "Also waiting" (docs/HOME_NUDGES.md)
                    state.tripCard?.let { TripHomeCard(it) { onOpenTrip(it.id) } }
                    // the capture heartbeat: when the last bank text was seen; amber after a quiet stretch; one tap opens "Why isn't capture working?". Information, not a nudge (docs/HOME_NUDGES.md)
                    state.captureHeartbeat?.let { CaptureHeartbeatChip(it, onOpenCheckup) }
                    // One card at a time, the first of HomeNudges' order that is not put off; what else is waiting is one line under it (see docs/HOME_NUDGES.md)
                    val bills = state.billPeek
                    val nudgeCounts = homeNudgeCounts(state).filterKeys { !nudgesPaused || it !in NudgePause.HOME_PAUSED }
                    val shownNudges = HomeNudges.visible(nudgeCounts, state.nudgeSnoozes, app.expenses.nowMillis())
                    val card = HomeNudges.card(shownNudges)
                    fun notNow(n: HomeNudge) = { onSnoozeNudge(n, nudgeCounts[n] ?: 0) }
                    // every nudge has the same look and ONE close button (NudgeCard); what the close does is the nudge's own: a day for most, a week for "No category", for good for "Getting started"
                    when (card) {
                        HomeNudge.BILLS_DUE, HomeNudge.BILLS_NEW -> BillsBanner(bills, onOpenBills, notNow(card))
                        HomeNudge.INBOX -> InboxBanner(state, onOpenInbox, notNow(card))
                        HomeNudge.WAITING -> WaitingBanner(state.waiting, onOpenLoans, notNow(card))
                        HomeNudge.CATEGORISE -> CategoriseNudgeCard(state.categoriseNudge, onReview = onOpenCategorise, onNotNow = onSnoozeCategorise)
                        // the close puts it off for a day; the way to hide it for good is offered once, in the bar that says so (the setup stays in More, Capture)
                        HomeNudge.SETUP -> state.setupCard?.let { SetupNudgeCard(it, onOpen = onOpenSetup, onNotNow = {
                            onSnoozeNudge(HomeNudge.SETUP, 1)
                            scope.launch { if (snackbar.showSnackbar("Hidden for today", actionLabel = "Hide for good", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onHideSetup() }
                        }) }
                        HomeNudge.FIRST_WEEK -> FirstWeekCard(state.firstWeek, onCheckup = if (state.firstWeek.any { it.step == FirstWeekStep.CAPTURE && !it.done }) onOpenCheckup else null, onEarlier = onOpenEarlier, onOpen = { step -> when (step) { FirstWeekStep.ACCOUNT_DIGITS -> onManageAccounts(); FirstWeekStep.BUDGET -> onOpenBudget(); FirstWeekStep.CAPTURE -> onOpenCapture(); FirstWeekStep.BACKUP -> onOpenData(); FirstWeekStep.ENTRY -> onAdd() } }, onDismiss = onDismissFirstWeek)
                        null -> {}
                    }
                    // "Also waiting: To review (2) · No category (4)": what else waits, each as a link to its own place, with its count (the nudges' order and snooze rules decide what is here, not this line)
                    HomeNudges.alsoWaitingLinks(shownNudges, nudgeCounts).takeIf { it.isNotEmpty() }?.let { links ->
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(horizontal = 16.dp).semantics(mergeDescendants = true) { }.testTag("home_more_nudges"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Also waiting:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically))
                            links.forEachIndexed { i, link ->
                                val go: () -> Unit = when (link.nudge) {
                                    HomeNudge.BILLS_DUE, HomeNudge.BILLS_NEW -> onOpenBills
                                    HomeNudge.INBOX -> onOpenInbox
                                    HomeNudge.WAITING -> onOpenLoans
                                    HomeNudge.CATEGORISE -> onOpenCategorise
                                    HomeNudge.SETUP -> onOpenSetup
                                    HomeNudge.FIRST_WEEK -> { { onMoreChange(true) } }
                                }
                                Text(
                                    link.text + if (i < links.lastIndex) " ·" else "",
                                    style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clip(Radius.XS).clickable(onClickLabel = "Open ${link.text}", role = androidx.compose.ui.semantics.Role.Button, onClick = go).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).testTag("home_waiting_${link.nudge.key}"),
                                )
                            }
                        }
                    }
                    // UI S6 (D2): at most one calm milestone, only when the person opted in and nudges are not paused; one line, a tick, "Thanks". Not a nudge: it asks for nothing and is not in "Also waiting"
                    state.milestone?.takeIf { state.milestonesOn && !nudgesPaused }?.let { m -> MilestoneCard(m.text, onThanks = { onMilestoneThanks(m.id) }) }
                    sections.forEach { section ->
                        when (section) {
                            HomeSection.BUDGET -> if (!heroFirst && !firstRun) HomeHero(state, showBudget = true, onOpenBudget = onOpenBudget, onOpenSafe = { safeOpen = true }, large = large)
                            HomeSection.BILLS -> DueSection(state.due, onPost = { d -> if (d.amount == null) askingFor = d else onPostDue(d, null) }, onSkip = onSkipDue, onOpenAll = onOpenRecurring)
                            // on a brand-new install the two empty tiles say nothing and would push the welcome card's Skip off the screen: they wait for the first entry or for Skip
                            HomeSection.BALANCES -> if (!(state.rows.isEmpty() && state.showWelcome)) BalancesStrip(state.accountRows.filter { !it.archived }, selectedId = state.homeAccountId, onSelect = { id -> onFilterAccount(if (id == state.homeAccountId) null else id) }, onOpenAccounts = onManageAccounts)
                            HomeSection.QUICK_ENTRIES -> {
                                if (state.templates.isNotEmpty()) {
                                    LazyRow(
                                        modifier = Modifier.testTag("templates"),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(state.templates, key = { it.id }) { t ->
                                            LongPressChip(
                                                label = if (t.amountText != null) "${t.name} · ${t.amountText.wholeAmounts()}" else "${t.name} …", leading = androidx.compose.material.icons.Icons.Outlined.Add,
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
                }
            }
            // the account bar: its own item, so choosing an account can bring it to the top of the page with the entries right below
            // sticky: "Showing only X", Show all and the period chips stay in view while the account's entries scroll under them
            if (filtered != null) stickyHeader(key = "home_filter", contentType = "filter") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Showing only ${filtered.name}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).testTag("account_filter_note"))
                        TextButton(onClick = onManageAccounts, modifier = Modifier.testTag("account_filter_manage")) { Text("Edit account") }
                        TextButton(onClick = { onFilterAccount(null) }, modifier = Modifier.testTag("account_filter_clear")) { Text("Show all") }
                    }
                    // narrow the account's list by date; searching inside it is one tap away
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SegmentedChoice(
                            listOf(Segment(app.expenses.HomePeriod.THIS_MONTH, "This month", "account_period_this"), Segment(app.expenses.HomePeriod.LAST_MONTH, "Last month", "account_period_last"), Segment(app.expenses.HomePeriod.ALL, "All", "account_period_all")),
                            state.homePeriod, onSetHomePeriod, Modifier.weight(1f), crowded = WhenCrowded.SCROLL,
                        )
                        TextButton(onClick = { onSearchAccount(filtered.id) }, modifier = Modifier.testTag("account_search")) { Text("Search") }
                    }
                }
            }
            if (state.rows.isEmpty()) {
                item(key = "home_empty", contentType = "empty") {
                    if (filtered != null) {
                        Box(Modifier.fillParentMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (state.homePeriod == app.expenses.HomePeriod.ALL) "Nothing in ${filtered.name} yet." else "Nothing in ${filtered.name} in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("account_filter_empty"))
                                if (state.homePeriod == app.expenses.HomePeriod.ALL) androidx.compose.material3.OutlinedButton(onClick = onAdd, modifier = Modifier.testTag("account_filter_empty_add")) { Text("Add an expense") }
                                else androidx.compose.material3.OutlinedButton(onClick = { onSetHomePeriod(app.expenses.HomePeriod.ALL) }, modifier = Modifier.testTag("account_filter_empty_all")) { Text("Show all time") }
                            }
                        }
                    } else if (state.showWelcome) {
                        Column(Modifier.fillMaxWidth()) {
                            WelcomeCard(onAdd = onAdd, onImportOrRestore = onOpenData, onSkip = onDismissWelcome, onTour = onStartTour)
                            // below the buttons, so Skip is on the first screen; only a drawing: it is never stored (see ExampleMonthBlock)
                            if (state.showExample) ExampleMonthBlock(state.baseCurrency, onRow = onExampleRow, onHide = onHideExample)
                        }
                    } else {
                        Box(Modifier.fillParentMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                EmptyArt(EmptyKind.HOME, Modifier.size(140.dp))
                                Text("No expenses yet. Tap Add.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(listItems, key = { _, it -> if (it is RowUi) it.seq else "day$it" }, contentType = { _, it -> if (it is RowUi) "row" else "day" }) { index, item ->
                    if (item is RowUi) SwipeableRow(
                        item, pos = CardPos.of(first = listItems.getOrNull(index - 1) !is RowUi, last = listItems.getOrNull(index + 1) !is RowUi), onOpen, onDelete, selecting = selected.isNotEmpty(), isSelected = item.id in selected,
                        onLongPress = { if (selected.isEmpty()) feel(Feel.LONG_PRESS); selected = selected.toggled(item.id) }, onToggle = { selected = selected.toggled(item.id) },
                        modifier = Modifier.animateItem(), fresh = state.undo?.let { u -> !u.restore && u.entryId == item.id } == true,
                    ) else (item as Long).let { day -> DayHeader(day, dayTotals[day]?.let { signedAmount(Money(it, state.baseCurrency).formatted(), Flow3.OUT) }) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(row: RowUi, pos: CardPos, onOpen: (RowUi) -> Unit, onDelete: (RowUi) -> Unit, selecting: Boolean, isSelected: Boolean, onLongPress: () -> Unit, onToggle: () -> Unit, fresh: Boolean = false, modifier: Modifier = Modifier) {
    val tappedEntry = LocalTappedEntry.current
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(row); true } else false
        },
    )
    // a tick the moment the swipe passes the point where letting go would delete, and another when it comes back
    val feelSwipe = rememberFeel()
    var pastThreshold by remember { mutableStateOf(false) }
    LaunchedEffect(dismiss.targetValue) {
        val past = dismiss.targetValue == SwipeToDismissBoxValue.EndToStart
        if (past != pastThreshold) { if (!(past && dismiss.currentValue == SwipeToDismissBoxValue.EndToStart)) feelSwipe(if (past) Feel.THRESHOLD else Feel.THRESHOLD_OFF); pastThreshold = past }
    }
    // a row that has just been added glows in the accent colour and settles over a second, so the eye finds it
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(fresh) { if (fresh) { flash.snapTo(1f); flash.animateTo(0f, Motion.tween(reduceMotion, Motion.FLASH, androidx.compose.animation.core.LinearEasing)) } }
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selecting,
        backgroundContent = {
            // swipe v2 (UI S3): the background goes from the card's colour to the error container as the finger travels, and the icon and its word grow from 80% to full size; all read while drawing, so
            // the row is not recomposed on every frame of the drag. A resting row does not compose it at all (the card covers it): only the flip to "being dragged" does, which keeps the list light on a fling
            val dragging by remember { derivedStateOf { dismiss.dismissDirection != SwipeToDismissBoxValue.Settled } }
            if (dragging) {
                val surface = MaterialTheme.colorScheme.surfaceVariant
                val danger = MaterialTheme.colorScheme.errorContainer
                fun travelled() = if (dismiss.dismissDirection == SwipeToDismissBoxValue.Settled) 0f else dismiss.progress.coerceIn(0f, 1f)
                Box(Modifier.fillMaxSize().drawBehind { drawRect(androidx.compose.ui.graphics.lerp(surface, danger, travelled())) }.padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                    Row(Modifier.graphicsLayer { val k = 0.8f + 0.2f * travelled(); scaleX = k; scaleY = k; alpha = 0.4f + 0.6f * travelled() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                        androidx.compose.material3.Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        },
        modifier = modifier.entryCardClip(pos).tappedContainer(row.id).testTag("row_${row.title}"),
    ) {
        val large = isLargeText()
        val flow = flowOf(row.kind, row.inflow)
        // Home's headings say the day: the row says the account and the category (a row with neither keeps its date)
        val detail = row.detail.ifEmpty { row.subtitle }
        val amount: @Composable () -> Unit = {
            SpokenAmount(row.amount, flow) { MoneyText(signedAmount(row.amount, flow), style = NumberStyle.Row, color = amountColor(flow), modifier = Modifier.testTag("amount_${row.title}")) }
        }
        Row(
            Modifier.fillMaxWidth().entryCardFill(pos, isSelected)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = flash.value * 0.16f))
                // UI S9 (U-24): right-click selects, as a long press does; the keyboard's focus shows as a ring
                .then(if (LocalLargeWindow.current) Modifier.focusRing(Radius.S).secondaryClick(onLongPress) else Modifier)
                .combinedClickable(onClick = { if (selecting) onToggle() else { tappedEntry?.id = row.id; onOpen(row) } }, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = rowPadding(12.dp)).semantics {
                    selected = isSelected
                    // the swipe's action for a screen reader, which cannot swipe (the long press and the bulk bar are the other ways)
                    customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Delete") { onDelete(row); true })
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // choosing: the tile crossfades into the checkbox (120 ms; at once under Reduce motion)
            val tileSize = if (large) 32.dp else 40.dp
            androidx.compose.animation.Crossfade(selecting, Modifier.padding(end = if (large) 10.dp else 14.dp), animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(120), label = "tileOrCheck") { choosing ->
                Box(Modifier.size(tileSize), contentAlignment = Alignment.Center) {
                    if (choosing) Checkbox(checked = isSelected, onCheckedChange = null) else {
                        EntryTile(row.title, row.category, row.kind, size = tileSize)
                        // from a text or an import and still without a category: a small dot (its words, "not reviewed", are in its description)
                        if (row.unreviewed) UnreviewedDot(row.id, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
            if (!large) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                amount()
            } else {
                // at large text the amount goes under the words, so the title is not squeezed into a few letters: the title and the detail take two lines each
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    amount()
                }
            }
        }
    }
}

/** Explains why an entry cannot be edited here yet, instead of offering a form that would lose information. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyEntryScreen(title: String, reason: String, onBack: () -> Unit, origin: app.expenses.OriginUi? = null) {
    Scaffold(topBar = { ScreenHeader(title, onBack, "cancel") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Can't edit this entry here", style = MaterialTheme.typography.titleMedium)
            Text(reason, modifier = Modifier.testTag("read_only_reason"))
            if (origin != null) OriginSection(origin)
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
                "The data can't be recovered from this phone.",
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
 * What is waiting in To review, at a glance: how many and how much goes out and comes in. One slim card (see [NudgeCard]); the whole card opens the list, where the items are.
 */
@Composable
private fun InboxBanner(state: UiState.Ready, onOpen: () -> Unit, onNotNow: () -> Unit) {
    val peek = state.inboxPeek
    NudgeCard(
        tag = "inbox_banner", title = "${state.inboxPending} to review", titleTag = "inbox_banner_title",
        line = if (peek.outText.isNotEmpty()) "${peek.outText} out · ${peek.inText} in, from bank texts" else null, lineTag = null,
        actionLabel = "Review", actionTag = "inbox_banner_open", onAction = onOpen,
        dismissTag = "nudge_notnow_inbox", dismissLabel = "Not now: hide for today", onDismiss = onNotNow, wholeCardOpens = true,
    )
}

/** Money expected back for expenses, at a glance: how many are waiting and what they add up to. The whole card opens the list (on the Lent and borrowed page). */
@Composable
private fun WaitingBanner(waiting: app.expenses.WaitingUi, onOpen: () -> Unit, onNotNow: () -> Unit) {
    NudgeCard(
        tag = "waiting_banner", title = if (waiting.count == 1) "1 expense waiting to come back" else "${waiting.count} expenses waiting to come back", titleTag = "waiting_banner_title",
        line = if (waiting.totalText.isNotEmpty()) "${waiting.totalText} to get back" else "Money you expect back", lineTag = "waiting_banner_total",
        actionLabel = "Open", actionTag = "waiting_banner_open", onAction = onOpen,
        dismissTag = "nudge_notnow_waiting", dismissLabel = "Not now: hide for today", onDismiss = onNotNow, wholeCardOpens = true,
    )
}

/** Bills at a glance: how many are new or due, and what they come to. One slim card; the whole card opens the bills page, where each bill is. */
@Composable
private fun BillsBanner(peek: app.expenses.BillPeekUi, onOpen: () -> Unit, onNotNow: () -> Unit) {
    val overdue = peek.rows.any { it.overdue }
    NudgeCard(
        tag = "bills_banner",
        title = if (peek.newCount > 0) (if (peek.newCount == 1) "1 new bill found" else "${peek.newCount} new bills found") else if (peek.dueThisWeek > 0) (if (peek.dueThisWeek == 1) "1 bill due this week" else "${peek.dueThisWeek} bills due this week") else "Bills coming up",
        titleTag = "bills_banner_title",
        line = if (peek.newCount > 0) "Found in bank texts. Add them to be reminded." else if (peek.dueThisWeek > 0) listOfNotNull(peek.weekTotal.takeIf { it.isNotEmpty() }?.let { "$it in all" }, "Overdue ones are included".takeIf { overdue }).joinToString(". ").ifEmpty { null } else "Due in the next two weeks",
        lineTag = "bills_banner_line",
        actionLabel = if (peek.newCount > 0) "Add" else "Open", actionTag = "bills_banner_open", onAction = onOpen,
        dismissTag = "nudge_notnow_bills", dismissLabel = "Not now: hide for today", onDismiss = onNotNow, wholeCardOpens = true,
    )
}

/** What each Home nudge is about now, as a number: the nudges that are not here have nothing to say (see [HomeNudges.visible]). Used by Home's card and by the More page's cards. */
internal fun homeNudgeCounts(state: UiState.Ready): Map<HomeNudge, Int> = buildMap {
    val bills = state.billPeek
    if (!bills.isEmpty) { if (bills.dueThisWeek > 0) put(HomeNudge.BILLS_DUE, bills.dueThisWeek) else put(HomeNudge.BILLS_NEW, maxOf(bills.newCount, bills.rows.size, 1)) }
    if (state.inboxPending > 0) put(HomeNudge.INBOX, state.inboxPending)
    if (!state.waiting.isEmpty) put(HomeNudge.WAITING, state.waiting.count)
    if (state.categoriseNudge > 0) put(HomeNudge.CATEGORISE, state.categoriseNudge)
    if (state.setupCard != null) put(HomeNudge.SETUP, 1)
    if (state.firstWeek.isNotEmpty()) put(HomeNudge.FIRST_WEEK, state.firstWeek.size)
}
