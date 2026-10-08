package app.expenses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.expenses.ui.BudgetScreen
import app.expenses.core.domain.Appearance
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.Density
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.ui.AccountsScreen
import app.expenses.ui.CalendarScreen
import app.expenses.ui.AppearanceScreen
import app.expenses.ui.CurrencyScreen
import app.expenses.ui.CategoriesScreen
import app.expenses.ui.HomeLayoutScreen
import app.expenses.ui.DataScreen
import app.expenses.ui.ImportScreen
import app.expenses.ui.EntryFormScreen
import app.expenses.ui.HistoryScreen
import app.expenses.ui.ReadOnlyEntryScreen
import app.expenses.ui.EntryListScreen
import app.expenses.ui.KeyLostScreen
import app.expenses.ui.NotificationSettingsScreen
import app.expenses.ui.LockedScreen
import app.expenses.ui.OpeningScreen
import app.expenses.ui.SecurityScreen
import app.expenses.ui.ReportsScreen
import app.expenses.ui.SearchScreen
import app.expenses.ui.RecurringScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: LedgerViewModel by viewModels()

    /** Counts requests to open the add screen from the launcher shortcut or the Quick Settings tile. */
    private val addRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("onCreate: begin")
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.action == ACTION_ADD_EXPENSE) addRequests.value = 1
        // Measurement builds only: `am start -n app.expenses/.MainActivity --ei seed_rows 100000`
        intent.getIntExtra("seed_rows", 0).takeIf { it > 0 && BuildConfig.SEEDING_ENABLED && savedInstanceState == null }?.let(viewModel::seed)
        setContent {
            val themeState by viewModel.state.collectAsStateWithLifecycle()
            val look = (themeState as? UiState.Ready)?.appearance ?: Appearance()
            val dark = Appearances.isDark(look.mode, isSystemInDarkTheme())
            MaterialTheme(colorScheme = colorSchemeFor(look, dark)) {
              androidx.compose.runtime.CompositionLocalProvider(app.expenses.ui.LocalCompact provides (look.density == Density.COMPACT)) {
                Surface {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { StartupTrace.mark("first composition") }
                    var adding by rememberSaveable { mutableStateOf(false) }
                    var managingCategories by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(managingCategories) { viewModel.setCategoriesScreenOpen(managingCategories) }
                    var managingAccounts by rememberSaveable { mutableStateOf(false) }
                    var customisingHome by rememberSaveable { mutableStateOf(false) }
                    var appearing by rememberSaveable { mutableStateOf(false) }
                    var currencyOpen by rememberSaveable { mutableStateOf(false) }
                    val currencyBlocker by viewModel.currencyBlocker.collectAsStateWithLifecycle()
                    LaunchedEffect(managingAccounts) { viewModel.setAccountsScreenOpen(managingAccounts) }
                    var budgeting by rememberSaveable { mutableStateOf(false) }
                    var repeating by rememberSaveable { mutableStateOf(false) }
                    var alerts by rememberSaveable { mutableStateOf(false) }
                    var yourData by rememberSaveable { mutableStateOf(false) }
                    var reporting by rememberSaveable { mutableStateOf(false) }
                    var calendaring by rememberSaveable { mutableStateOf(false) }
                    var searching by rememberSaveable { mutableStateOf(false) }
                    var securing by rememberSaveable { mutableStateOf(false) }
                    val lock = (application as ExpensesApp).lock
                    val locked by lock.locked.collectAsStateWithLifecycle()
                    val lockSettings by lock.settings.collectAsStateWithLifecycle()
                    val lockMessage by lock.message.collectAsStateWithLifecycle()
                    // While the lock is on, the app is hidden from the recent-apps view and from screenshots.
                    LaunchedEffect(lockSettings.enabled) {
                        if (lockSettings.enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    var prefillCategory by rememberSaveable { mutableStateOf<String?>(null) }
                    var prefillAccount by rememberSaveable { mutableStateOf<String?>(null) }
                    var prefillNote by rememberSaveable { mutableStateOf("") }
                    // Lets `am start` and tracing measure cold start up to the first screen with data, not just the spinner.
                    LaunchedEffect(state is UiState.Ready) { if (state is UiState.Ready) { StartupTrace.mark("ready composed"); reportFullyDrawn() } }

                    val requests by addRequests.collectAsState()
                    var handledRequests by rememberSaveable { mutableIntStateOf(0) }
                    LaunchedEffect(requests) {
                        if (requests > handledRequests) { handledRequests = requests; prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true }
                    }

                    val editing by viewModel.editor.collectAsStateWithLifecycle()
                    val history by viewModel.history.collectAsStateWithLifecycle()
                    val attachmentList by viewModel.attachments.collectAsStateWithLifecycle()
                    val attachmentProblem by viewModel.attachmentProblem.collectAsStateWithLifecycle()

                    if (locked) LockedScreen(lockMessage) { lock.requestUnlock(this@MainActivity) } else when (val s = state) {
                        UiState.Opening -> OpeningScreen()
                        is UiState.KeyLost -> KeyLostScreen(s.message, viewModel::startFresh)
                        is UiState.Ready -> androidx.compose.runtime.CompositionLocalProvider(app.expenses.ui.LocalHideAmounts provides s.hideAmounts) { when {
                            history != null -> {
                                val h = history!!
                                BackHandler { viewModel.closeHistory() }
                                HistoryScreen(h, onBack = viewModel::closeHistory, onRestore = { viewModel.restoreVersion(h.entryId, it) })
                            }
                            editing is Editable.Form -> {
                                val e = editing as Editable.Form
                                BackHandler { viewModel.closeEditor() }
                                // Keyed, so opening a refund from an expense's editor starts a new form instead of reusing the expense's typed state.
                                androidx.compose.runtime.key(e.draft.id, e.draft.kind, e.draft.refundsEntry) {
                                EntryFormScreen(
                                    title = e.title,
                                    start = e.draft.let { d -> d.copy(accountId = d.accountId.ifEmpty { s.lastAccountId }) },
                                    kinds = listOf(e.draft.kind),
                                    accounts = s.allAccounts.filter { a -> a.id == e.draft.accountId || a.id == e.draft.toAccountId || s.accounts.any { it.id == a.id } }, // an archived account stays pickable for an entry that uses it
                                    categories = s.categories,
                                    onCreateCategory = { name, parent, done -> viewModel.createCategory(name, parent, null, done) },
                                    showTemplateOption = false,
                                    allTags = s.tags,
                                    attachments = attachmentList, attachmentProblem = attachmentProblem, onAttach = { uri -> e.draft.id?.let { viewModel.addAttachment(it, uri) } },
                                    onOpenAttachment = { a -> openAttachment(viewModel.attachmentUri(a.id), a.mime) },
                                    onRemoveAttachment = { a -> e.draft.id?.let { viewModel.removeAttachment(it, a.id) } },
                                    onSave = { d, _ -> viewModel.saveEntry(d, false) },
                                    onBack = viewModel::closeEditor,
                                    refundOf = e.refundOf,
                                    onHistory = e.draft.id?.let { id -> { viewModel.openHistory(id) } },
                                    onRefund = if (e.canRefund) ({ e.draft.id?.let(viewModel::startRefund) ?: Unit }) else null,
                                )
                                }
                            }
                            editing is Editable.ReadOnly -> {
                                val e = editing as Editable.ReadOnly
                                BackHandler { viewModel.closeEditor() }
                                ReadOnlyEntryScreen(e.title, e.reason, viewModel::closeEditor)
                            }
                            currencyOpen -> {
                                BackHandler { currencyOpen = false }
                                CurrencyScreen(s.baseCurrency, currencyBlocker, onChoose = viewModel::setBaseCurrency, onBack = { currencyOpen = false })
                            }
                            appearing -> {
                                BackHandler { appearing = false }
                                AppearanceScreen(s.appearance, onChange = viewModel::setAppearance, onBack = { appearing = false })
                            }
                            customisingHome -> {
                                BackHandler { customisingHome = false }
                                HomeLayoutScreen(s.homeLayout, onChange = viewModel::setHomeLayout, onBack = { customisingHome = false })
                            }
                            managingAccounts -> {
                                BackHandler { managingAccounts = false }
                                AccountsScreen(
                                    rows = s.accountRows, onBack = { managingAccounts = false },
                                    onSave = viewModel::saveAccount, onArchive = viewModel::setAccountArchived, onDelete = viewModel::deleteAccount,
                                )
                            }
                            managingCategories -> {
                                BackHandler { managingCategories = false }
                                CategoriesScreen(
                                    categories = s.categories,
                                    onBack = { managingCategories = false },
                                    onCreate = { name, parent, color -> viewModel.createCategory(name, parent, color) },
                                    onUpdate = viewModel::updateCategory,
                                    onMove = viewModel::moveCategory,
                                    onMerge = viewModel::mergeCategory,
                                    onDelete = viewModel::deleteCategory,
                                )
                            }
                            securing -> {
                                BackHandler { securing = false; lock.dismissMessage() }
                                SecurityScreen(
                                    settings = lockSettings, availability = lock::availability, message = lockMessage,
                                    onEnable = { lock.enable(this@MainActivity, it) }, onDisable = { lock.disable(this@MainActivity) },
                                    onTimeout = { lock.setTimeout(this@MainActivity, it) },
                                    onBack = { securing = false; lock.dismissMessage() },
                                )
                            }
                            searching -> {
                                val found by viewModel.search.collectAsStateWithLifecycle()
                                BackHandler { searching = false; viewModel.closeSearch() }
                                SearchScreen(
                                    state = found, categories = s.categories,
                                    onChange = viewModel::search,
                                    onBack = { searching = false; viewModel.closeSearch() },
                                    onOpen = { viewModel.openEditor(it.id) },
                                    accounts = s.accounts,
                                    onBulkCategory = viewModel::bulkSetCategory, onBulkAccount = viewModel::bulkSetAccount, onBulkDelete = viewModel::bulkDelete,
                                    tags = s.tags, onBulkAddTag = viewModel::bulkAddTag, onBulkRemoveTag = viewModel::bulkRemoveTag,
                                    onUndo = viewModel::undo,
                                    saved = s.savedSearches, onSaveSearch = viewModel::saveSearch, onDeleteSaved = viewModel::deleteSavedSearch,
                                )
                            }
                            calendaring -> {
                                val cal by viewModel.calendar.collectAsStateWithLifecycle()
                                BackHandler { calendaring = false; viewModel.closeCalendar() }
                                CalendarScreen(
                                    state = cal, onBack = { calendaring = false; viewModel.closeCalendar() },
                                    onShift = viewModel::shiftCalendar, onSelect = viewModel::selectCalendarDay, onOpenEntry = viewModel::openEditor,
                                )
                            }
                            reporting -> {
                                val report by viewModel.report.collectAsStateWithLifecycle()
                                // Back leaves the entry list first, then the report.
                                BackHandler { if (report?.drill != null) viewModel.closeDrill() else { reporting = false; viewModel.closeReports() } }
                                ReportsScreen(
                                    report = report,
                                    onBack = { reporting = false; viewModel.closeReports() },
                                    onMode = viewModel::setReportMode, onStep = viewModel::stepReport,
                                    onDrill = { target, start, end, label -> viewModel.drill(target, start, end, label) },
                                    onGroup = viewModel::setReportGroup, onRange = viewModel::setReportRange,
                                    onCloseDrill = viewModel::closeDrill,
                                    onOpenEntry = viewModel::openEditor,
                                )
                            }
                            yourData -> {
                                val dataState by viewModel.data.collectAsStateWithLifecycle()
                                val statement by viewModel.statement.collectAsStateWithLifecycle()
                                if (statement.active) {
                                    BackHandler { viewModel.closeImport() }
                                    ImportScreen(
                                        state = statement, accounts = s.accounts,
                                        onAccount = viewModel::changeImportAccount, onMapping = viewModel::changeImportMapping,
                                        onImport = viewModel::confirmImport, onClose = viewModel::closeImport,
                                    )
                                } else {
                                    BackHandler { yourData = false; viewModel.cancelRestore(); viewModel.dismissDataStatus() }
                                    DataScreen(
                                        state = dataState,
                                        onBack = { yourData = false; viewModel.cancelRestore(); viewModel.dismissDataStatus() },
                                        onExportCsv = viewModel::exportCsv, onExportXlsx = viewModel::exportXlsx,
                                        autoExport = s.autoExport, onAutoFolder = viewModel::setAutoExportFolder, onAutoFrequency = viewModel::setAutoExportFrequency, onAutoNow = viewModel::exportNow, onBackup = viewModel::createBackup, onOpenBackup = viewModel::openBackup, onImportStatement = viewModel::startImport,
                                        onUnlock = viewModel::unlockBackup, onRestore = viewModel::confirmRestore, onCancelRestore = viewModel::cancelRestore,
                                    )
                                }
                            }
                            alerts -> {
                                BackHandler { alerts = false }
                                NotificationSettingsScreen(prefs = s.notifications, onChange = viewModel::updateNotificationPrefs, onSendTest = viewModel::sendTestNotification, onBack = { alerts = false })
                            }
                            repeating -> {
                                BackHandler { repeating = false }
                                val now = java.time.LocalDate.now()
                                RecurringScreen(
                                    due = s.due, hiddenOlder = s.hiddenOlder, items = s.recurring, categories = s.categories, accounts = s.accounts,
                                    today = kotlinx.datetime.LocalDate(now.year, now.monthValue, now.dayOfMonth),
                                    onBack = { repeating = false },
                                    onPost = { d, amount -> viewModel.postDue(d.itemId, d.epochDay, amount) },
                                    onSkip = { d -> viewModel.skipDue(d.itemId, d.epochDay) },
                                    onSave = viewModel::saveRecurring,
                                    onDelete = viewModel::deleteRecurring,
                                )
                            }
                            budgeting -> {
                                BackHandler { budgeting = false }
                                BudgetScreen(
                                    budget = s.budget,
                                    onBack = { budgeting = false },
                                    onSetLimit = viewModel::setBudget,
                                    onClearLimit = viewModel::clearBudget,
                                    onSetPeriod = viewModel::setBudgetPeriod,
                                    onShiftStartDay = viewModel::shiftBudgetStartDay,
                                    onShiftCustomDays = viewModel::shiftCustomDays,
                                )
                            }
                            adding -> {
                                BackHandler { adding = false }
                                EntryFormScreen(
                                    title = "Add",
                                    start = EntryDraft(null, DraftKind.EXPENSE, "", prefillCategory ?: s.lastCategory, emptyList(), prefillAccount ?: s.lastAccountId, "", prefillNote, System.currentTimeMillis()),
                                    kinds = listOf(DraftKind.EXPENSE, DraftKind.INCOME, DraftKind.TRANSFER),
                                    accounts = s.accounts,
                                    categories = s.categories,
                                    onCreateCategory = { name, parent, done -> viewModel.createCategory(name, parent, null, done) },
                                    showTemplateOption = true,
                                    allTags = s.tags,
                                    onSave = { d, asTemplate -> viewModel.saveEntry(d, asTemplate); adding = false },
                                    onBack = { adding = false },
                                )
                            }
                            else -> EntryListScreen(
                                state = s,
                                onAdd = { prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true },
                                onLoadMore = viewModel::loadMore,
                                onMessageShown = viewModel::dismissMessage,
                                onTemplate = { t ->
                                    if (t.amountText != null) viewModel.quickAdd(t.id)
                                    else { prefillCategory = t.category; prefillAccount = t.accountId; prefillNote = t.note.ifEmpty { t.name }; adding = true }
                                },
                                onDeleteTemplate = viewModel::deleteTemplate,
                                onUndo = viewModel::undo,
                                onUndoDismissed = viewModel::dismissUndo,
                                onOpen = { viewModel.openEditor(it.id) },
                                onDelete = viewModel::deleteEntry,
                                onManageCategories = { managingCategories = true },
                                onManageAccounts = { managingAccounts = true },
                                onOpenBudget = { budgeting = true },
                                onOpenRecurring = { repeating = true },
                                onOpenNotifications = { alerts = true },
                                onOpenData = { yourData = true },
                                onOpenReports = { reporting = true; viewModel.openReports() },
                                onOpenCalendar = { calendaring = true; viewModel.openCalendar() },
                                onCustomiseHome = { customisingHome = true },
                                onOpenAppearance = { appearing = true },
                                onOpenSearch = { searching = true },
                                onOpenSecurity = { securing = true },
                                onDismissWelcome = viewModel::dismissWelcome,
                                onSnoozeBackupNudge = viewModel::snoozeBackupNudge,
                                onPostDue = { d, amount -> viewModel.postDue(d.itemId, d.epochDay, amount) },
                                onSkipDue = { d -> viewModel.skipDue(d.itemId, d.epochDay) },
                                onBulkCategory = viewModel::bulkSetCategory,
                                onBulkAccount = viewModel::bulkSetAccount,
                                onBulkDelete = viewModel::bulkDelete,
                                onBulkAddTag = viewModel::bulkAddTag,
                                onBulkRemoveTag = viewModel::bulkRemoveTag,
                                onToggleHideAmounts = { viewModel.setHideAmounts(!s.hideAmounts) },
                                onOpenCurrency = { currencyOpen = true; viewModel.loadCurrencyBlocker() },
                            )
                        }
                        }
                    }
                }
              }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as ExpensesApp).lock.onStart(this)
        viewModel.postAutomatic()
    }

    /** When the screen leaves the foreground the user is not waiting on anything: a good moment to tidy the search index. */
    override fun onStop() {
        super.onStop()
        (application as ExpensesApp).lock.onStop(this)
        val app = application as ExpensesApp
        app.appScope.launch { runCatching { app.container.maintain() } }
    }

    private fun colorSchemeFor(look: Appearance, dark: Boolean): androidx.compose.material3.ColorScheme {
        fun c(rgb: Int) = androidx.compose.ui.graphics.Color(0xFF000000.toInt() or rgb)
        val primary = Appearances.primaryFor(look.accent, dark)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val container = Appearances.containerFor(primary, dark)
        return base.copy(primary = c(primary), onPrimary = c(Appearances.onColor(primary)), primaryContainer = c(container), onPrimaryContainer = c(Appearances.onColor(container)), secondary = c(primary), secondaryContainer = c(container), onSecondaryContainer = c(Appearances.onColor(container)))
    }

    private fun openAttachment(uri: Uri, mime: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent.createChooser(view, "Open with")) } catch (e: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(this, "No app on this phone can open that file.", android.widget.Toast.LENGTH_LONG).show() }
    }

    /** The activity is `singleTop`, so a shortcut or tile tap while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ADD_EXPENSE) addRequests.value += 1
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "app.expenses.action.ADD_EXPENSE"
    }
}
