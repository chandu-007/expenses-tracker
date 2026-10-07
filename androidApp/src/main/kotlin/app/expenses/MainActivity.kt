package app.expenses

import android.content.Intent
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
import app.expenses.ui.CategoriesScreen
import app.expenses.ui.DataScreen
import app.expenses.ui.ExpenseFormScreen
import app.expenses.ui.FormStart
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
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { StartupTrace.mark("first composition") }
                    var adding by rememberSaveable { mutableStateOf(false) }
                    var managingCategories by rememberSaveable { mutableStateOf(false) }
                    var budgeting by rememberSaveable { mutableStateOf(false) }
                    var repeating by rememberSaveable { mutableStateOf(false) }
                    var alerts by rememberSaveable { mutableStateOf(false) }
                    var yourData by rememberSaveable { mutableStateOf(false) }
                    var reporting by rememberSaveable { mutableStateOf(false) }
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

                    if (locked) LockedScreen(lockMessage) { lock.requestUnlock(this@MainActivity) } else when (val s = state) {
                        UiState.Opening -> OpeningScreen()
                        is UiState.KeyLost -> KeyLostScreen(s.message, viewModel::startFresh)
                        is UiState.Ready -> when {
                            editing is Editable.Simple -> {
                                val e = editing as Editable.Simple
                                BackHandler { viewModel.closeEditor() }
                                ExpenseFormScreen(
                                    title = "Edit expense",
                                    start = FormStart(e.amountText, e.category, e.accountId.ifEmpty { s.lastAccountId }, e.note, e.occurredAt),
                                    accounts = s.accounts,
                                    categories = s.categories,
                                    onCreateCategory = { name, parent, done -> viewModel.createCategory(name, parent, null, done) },
                                    showTemplateOption = false,
                                    onSave = { amount, category, note, account, _, at -> viewModel.saveEdit(e.id, amount, category, note, account, at) },
                                    onBack = viewModel::closeEditor,
                                )
                            }
                            editing is Editable.ReadOnly -> {
                                val e = editing as Editable.ReadOnly
                                BackHandler { viewModel.closeEditor() }
                                ReadOnlyEntryScreen(e.title, e.reason, viewModel::closeEditor)
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
                                    onDrill = { target, start, end -> viewModel.drill(target, start, end) },
                                    onCloseDrill = viewModel::closeDrill,
                                    onOpenEntry = viewModel::openEditor,
                                )
                            }
                            yourData -> {
                                val dataState by viewModel.data.collectAsStateWithLifecycle()
                                BackHandler { yourData = false; viewModel.cancelRestore(); viewModel.dismissDataStatus() }
                                DataScreen(
                                    state = dataState,
                                    onBack = { yourData = false; viewModel.cancelRestore(); viewModel.dismissDataStatus() },
                                    onExportCsv = viewModel::exportCsv, onBackup = viewModel::createBackup, onOpenBackup = viewModel::openBackup,
                                    onUnlock = viewModel::unlockBackup, onRestore = viewModel::confirmRestore, onCancelRestore = viewModel::cancelRestore,
                                )
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
                                )
                            }
                            adding -> {
                                BackHandler { adding = false }
                                ExpenseFormScreen(
                                    title = "Add expense",
                                    start = FormStart("", prefillCategory ?: s.lastCategory, prefillAccount ?: s.lastAccountId, prefillNote, System.currentTimeMillis()),
                                    accounts = s.accounts,
                                    categories = s.categories,
                                    onCreateCategory = { name, parent, done -> viewModel.createCategory(name, parent, null, done) },
                                    showTemplateOption = true,
                                    onSave = { amount, category, note, account, asTemplate, at ->
                                        viewModel.addExpense(amount, category, note, account, asTemplate, at)
                                        adding = false
                                    },
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
                                onOpenBudget = { budgeting = true },
                                onOpenRecurring = { repeating = true },
                                onOpenNotifications = { alerts = true },
                                onOpenData = { yourData = true },
                                onOpenReports = { reporting = true; viewModel.openReports() },
                                onOpenSearch = { searching = true },
                                onOpenSecurity = { securing = true },
                                onPostDue = { d, amount -> viewModel.postDue(d.itemId, d.epochDay, amount) },
                                onSkipDue = { d -> viewModel.skipDue(d.itemId, d.epochDay) },
                            )
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

    /** The activity is `singleTop`, so a shortcut or tile tap while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ADD_EXPENSE) addRequests.value += 1
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "app.expenses.action.ADD_EXPENSE"
    }
}
