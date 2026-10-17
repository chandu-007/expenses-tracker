package app.expenses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import app.expenses.ui.BackHandler
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
import androidx.glance.appwidget.updateAll
import app.expenses.ui.BudgetScreen
import app.expenses.ui.LoansScreen
import app.expenses.ui.BillsScreen
import app.expenses.ui.GuideScreen
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.Appearance
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.Density
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.ui.AccountsScreen
import app.expenses.ui.CalendarScreen
import app.expenses.ui.AppearanceScreen
import app.expenses.ui.CurrencyScreen
import app.expenses.ui.InboxScreen
import app.expenses.ui.CaptureHealthScreen
import app.expenses.ui.PrivacyScreen
import app.expenses.ui.CaptureRulesScreen
import app.expenses.ui.CaptureSettingsScreen
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

    /** The view model takes the shared container and platform, so the default factory must be the app's. */
    override val defaultViewModelProviderFactory: androidx.lifecycle.ViewModelProvider.Factory
        get() = (application as ExpensesApp).viewModelFactory

    /** Counts requests to open the add screen from the launcher shortcut or the Quick Settings tile. */
    private val addRequests = MutableStateFlow(0)
    private val billsRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("onCreate: begin")
        super.onCreate(savedInstanceState)
        (application as ExpensesApp).platform.activity = this
        if (savedInstanceState == null && intent.action == ACTION_ADD_EXPENSE) addRequests.value = 1
        if (savedInstanceState == null && intent.action == ACTION_OPEN_BILLS) billsRequests.value = 1
        if (savedInstanceState == null && (intent.action == ACTION_ADD_EXPENSE || intent.action == ACTION_OPEN_BILLS)) viewModel.deferGuide()
        // Measurement builds only: `am start -n app.expenses/.MainActivity --ei seed_rows 100000`
        intent.getIntExtra("seed_rows", 0).takeIf { it > 0 && BuildConfig.SEEDING_ENABLED && savedInstanceState == null }?.let { viewModel.seed(it, full = intent.getBooleanExtra("seed_full", false)) }
        setContent {
            val themeState by viewModel.state.collectAsStateWithLifecycle(context = MAIN)
            val look = (themeState as? UiState.Ready)?.appearance ?: Appearance()
            val dark = Appearances.isDark(look.mode, isSystemInDarkTheme())
            // On Android 15 and later the app draws under the system bars, which keep light icons unless told otherwise: white clock and battery on a pale screen.
            val hostView = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect {
                // posted to the view's own thread: a side effect normally runs on the main thread, but under the test harness's clock it has run on a worker, and touching the window from there throws
                hostView.post {
                    val bars = androidx.core.view.WindowCompat.getInsetsController(window, hostView)
                    bars.isAppearanceLightStatusBars = !dark; bars.isAppearanceLightNavigationBars = !dark
                }
            }
            MaterialTheme(colorScheme = colorSchemeFor(look, dark)) {
              androidx.compose.runtime.CompositionLocalProvider(app.expenses.ui.LocalCompact provides (look.density == Density.COMPACT), app.expenses.ui.LocalPlatform provides (application as ExpensesApp).platform) {
                Surface {
                    val state by viewModel.state.collectAsStateWithLifecycle(context = MAIN)
                    LaunchedEffect(Unit) { StartupTrace.mark("first composition") }
                    var adding by rememberSaveable { mutableStateOf(false) }
                    var managingCategories by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(managingCategories) { viewModel.setCategoriesScreenOpen(managingCategories) }
                    var managingAccounts by rememberSaveable { mutableStateOf(false) }
                    var customisingHome by rememberSaveable { mutableStateOf(false) }
                    var appearing by rememberSaveable { mutableStateOf(false) }
                    var currencyOpen by rememberSaveable { mutableStateOf(false) }
                    var inboxOpen by rememberSaveable { mutableStateOf(false) }
                    var captureOpen by rememberSaveable { mutableStateOf(false) }
                    var rulesOpen by rememberSaveable { mutableStateOf(false) }
                    var healthOpen by rememberSaveable { mutableStateOf(false) }
                    var privacyOpen by rememberSaveable { mutableStateOf(false) }
                    val health by viewModel.captureHealth.collectAsStateWithLifecycle(context = MAIN)
                    val captureRules by viewModel.captureRules.collectAsStateWithLifecycle(context = MAIN)
                    val inboxRows by viewModel.inbox.collectAsStateWithLifecycle(context = MAIN)
                    val captureSettings by viewModel.captureSettings.collectAsStateWithLifecycle(context = MAIN)
                    var smsPermission by androidx.compose.runtime.remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
                    val smsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
                        smsPermission = granted
                        viewModel.setSmsCapture(granted)
                    }
                    val currencyBlocker by viewModel.currencyBlocker.collectAsStateWithLifecycle(context = MAIN)
                    LaunchedEffect(managingAccounts) { viewModel.setAccountsScreenOpen(managingAccounts) }
                    var budgeting by rememberSaveable { mutableStateOf(false) }
                    var loansOpen by rememberSaveable { mutableStateOf(false) }
                    var billsOpen by rememberSaveable { mutableStateOf(false) }
                    val billsUi by viewModel.bills.collectAsStateWithLifecycle(context = MAIN)
                    val billAsks by billsRequests.collectAsState()
                    var handledBillAsks by rememberSaveable { mutableIntStateOf(0) }
                    LaunchedEffect(billAsks) { if (billAsks > handledBillAsks) { handledBillAsks = billAsks; billsOpen = true; viewModel.watchBills(true) } }
                    // "personId|gave|amount" when the add form is opened from the lent-and-borrowed pages
                    var prefillLoan by rememberSaveable { mutableStateOf<String?>(null) }
                    val loans by viewModel.loans.collectAsStateWithLifecycle(context = MAIN)
                    val people by viewModel.people.collectAsStateWithLifecycle(context = MAIN)
                    var repeating by rememberSaveable { mutableStateOf(false) }
                    var alerts by rememberSaveable { mutableStateOf(false) }
                    var yourData by rememberSaveable { mutableStateOf(false) }
                    var reporting by rememberSaveable { mutableStateOf(false) }
                    var calendaring by rememberSaveable { mutableStateOf(false) }
                    var searching by rememberSaveable { mutableStateOf(false) }
                    var securing by rememberSaveable { mutableStateOf(false) }
                    val lock = (application as ExpensesApp).lock
                    val locked by lock.locked.collectAsStateWithLifecycle(context = MAIN)
                    val lockSettings by lock.settings.collectAsStateWithLifecycle(context = MAIN)
                    val lockMessage by lock.message.collectAsStateWithLifecycle(context = MAIN)
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

                    val editing by viewModel.editor.collectAsStateWithLifecycle(context = MAIN)
                    val history by viewModel.history.collectAsStateWithLifecycle(context = MAIN)
                    val attachmentList by viewModel.attachments.collectAsStateWithLifecycle(context = MAIN)
                    val attachmentProblem by viewModel.attachmentProblem.collectAsStateWithLifecycle(context = MAIN)

                    if (locked) LockedScreen(lockMessage) { lock.requestUnlock(this@MainActivity) } else when (val s = state) {
                        UiState.Opening -> OpeningScreen()
                        is UiState.KeyLost -> KeyLostScreen(s.message, viewModel::startFresh)
                        is UiState.Ready -> androidx.compose.runtime.CompositionLocalProvider(
                            app.expenses.ui.LocalHideAmounts provides s.hideAmounts,
                            app.expenses.ui.LocalReduceMotion provides (s.reduceMotion || app.expenses.ui.systemAnimationsOff()),
                        ) {
                        androidx.compose.runtime.SideEffect { app.expenses.ui.Motion.scale = app.expenses.ui.Motion.scaleOf(s.motionSpeed) }
                        // the tour: the app opens each part for real, and a layer on top explains it
                        val guide by viewModel.guide.collectAsStateWithLifecycle(context = MAIN)
                        var tourIndex by rememberSaveable { mutableIntStateOf(0) }
                        // back to the home screen, with nothing of the tour left open
                        val closeEverything = {
                            adding = false; searching = false; reporting = false; calendaring = false; inboxOpen = false; captureOpen = false; rulesOpen = false; healthOpen = false
                            billsOpen = false; managingAccounts = false; managingCategories = false; budgeting = false; repeating = false; loansOpen = false; alerts = false
                            privacyOpen = false; securing = false; yourData = false; appearing = false; customisingHome = false; currencyOpen = false
                            viewModel.watchBills(false); viewModel.watchLoans(false); viewModel.closeSearch(); viewModel.closeCalendar(); viewModel.closeReports(); viewModel.cancelRestore()
                            viewModel.closeEditor(); viewModel.closeHistory()
                        }
                        val tourStep = guide?.steps?.getOrNull(tourIndex.coerceAtLeast(0))
                        androidx.compose.runtime.LaunchedEffect(guide?.steps, tourIndex) {
                            val t = tourStep ?: return@LaunchedEffect
                            closeEverything()
                            when (t.target) {
                                null, app.expenses.core.domain.GuideTarget.MORE -> {}
                                app.expenses.core.domain.GuideTarget.ADD -> { prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true }
                                app.expenses.core.domain.GuideTarget.SEARCH -> searching = true
                                app.expenses.core.domain.GuideTarget.REPORTS -> { reporting = true; viewModel.openReports() }
                                app.expenses.core.domain.GuideTarget.INBOX -> { inboxOpen = true; viewModel.loadInbox() }
                                app.expenses.core.domain.GuideTarget.CAPTURE -> { captureOpen = true; viewModel.loadCaptureSettings() }
                                app.expenses.core.domain.GuideTarget.BILLS -> { billsOpen = true; viewModel.watchBills(true) }
                                app.expenses.core.domain.GuideTarget.ACCOUNTS -> managingAccounts = true
                                app.expenses.core.domain.GuideTarget.CATEGORIES -> managingCategories = true
                                app.expenses.core.domain.GuideTarget.BUDGET -> budgeting = true
                                app.expenses.core.domain.GuideTarget.RECURRING -> repeating = true
                                app.expenses.core.domain.GuideTarget.LOANS -> { loansOpen = true; viewModel.watchLoans(true) }
                                app.expenses.core.domain.GuideTarget.CALENDAR -> { calendaring = true; viewModel.openCalendar() }
                                app.expenses.core.domain.GuideTarget.NOTIFICATIONS -> alerts = true
                                app.expenses.core.domain.GuideTarget.PRIVACY -> privacyOpen = true
                                app.expenses.core.domain.GuideTarget.DATA -> yourData = true
                            }
                        }
                        guide?.let { g ->
                            GuideScreen(
                                g, index = tourIndex, onIndex = { tourIndex = it },
                                onShowAll = { tourIndex = 0; viewModel.showWholeGuide() },
                                onTryIt = { viewModel.finishGuide(false); tourIndex = 0 }, // ends the tour and stays on the screen being shown
                                onFinish = { never -> viewModel.finishGuide(never); tourIndex = 0; closeEverything() },
                            )
                        }
                        val similarOffer by viewModel.similar.collectAsStateWithLifecycle(context = MAIN)
                        val stoppedSimilar by viewModel.stoppedSimilar.collectAsStateWithLifecycle(context = MAIN)
                        androidx.compose.runtime.LaunchedEffect(managingCategories) { if (managingCategories) viewModel.loadStoppedSimilar() }
                        similarOffer?.let { offer -> if (editing == null && !adding) app.expenses.ui.SimilarDialog(offer, onApply = viewModel::applySimilar, onDismiss = { stop -> viewModel.dismissSimilar(stop) }) }
                        val screenKey = listOf(
                            history != null, editing is Editable.Form, editing is Editable.ReadOnly, inboxOpen, healthOpen, privacyOpen, rulesOpen, captureOpen, currencyOpen, appearing,
                            customisingHome, managingAccounts, managingCategories, securing, searching, calendaring, reporting, yourData, alerts, repeating, budgeting, adding, loansOpen, billsOpen,
                        ).indexOf(true) // -1 is home; the screens keep their place in this list as their key
                        // a screen that is leaving is still drawn for a moment, after the state it was showing is gone: it keeps its last value
                        val heldHistory = androidx.compose.runtime.remember { Held(history) }; if (history != null) heldHistory.value = history
                        val heldEditing = androidx.compose.runtime.remember { Held(editing) }; if (editing != null) heldEditing.value = editing
                        // the bar along the bottom is shown by Home, Search and Reports alike; its glass indicator glides between them as one bar
                        var wantMore by rememberSaveable { mutableStateOf(false) }
                        val goTab: (String) -> Unit = { t ->
                            if (searching && t != "Search") { searching = false; viewModel.closeSearch() }
                            if (reporting && t != "Reports") { reporting = false; viewModel.closeReports() }
                            when {
                                t == "Search" && !searching -> searching = true
                                t == "Reports" && !reporting -> { reporting = true; viewModel.openReports() }
                                t == "More" -> wantMore = true
                            }
                        }
                        val tabBar: @androidx.compose.runtime.Composable (String) -> Unit = { sel ->
                            // with the keyboard up the bar would float above it over nothing: it returns when the keyboard goes
                            if (!app.expenses.ui.rememberKeyboardOpen()) {
                            app.expenses.ui.HomeBar(sel, onHome = { goTab("Home") }, onSearch = { goTab("Search") }, onReports = { goTab("Reports") }, onMore = { goTab("More") }, moreBadge = s.showBackupNudge)
                            }
                        }
                        app.expenses.ui.ScreenHost(screenKey) { k -> when {
                            k == 0 -> {
                                val h = history ?: heldHistory.value!!
                                BackHandler { viewModel.closeHistory() }
                                HistoryScreen(h, onBack = viewModel::closeHistory, onRestore = { viewModel.restoreVersion(h.entryId, it) })
                            }
                            k == 1 -> {
                                val e = (editing as? Editable.Form) ?: (heldEditing.value as Editable.Form)
                                BackHandler { viewModel.closeEditor() }
                                // Keyed, so opening a refund from an expense's editor starts a new form instead of reusing the expense's typed state.
                                androidx.compose.runtime.key(e.draft.id, e.draft.kind, e.draft.refundsEntry) {
                                EntryFormScreen(
                                    title = e.title,
                                    start = e.draft.let { d -> d.copy(accountId = d.accountId.ifEmpty { s.lastAccountId }) },
                                    kinds = listOf(e.draft.kind),
                                    accounts = s.allAccounts.filter { a -> a.id == e.draft.accountId || a.id == e.draft.toAccountId || s.accounts.any { it.id == a.id } }, // an archived account stays pickable for an entry that uses it
                                    categories = s.categories,
                                    onCreateCategory = { name, parent, kind, done -> viewModel.createCategory(name, parent, null, kind, onCreated = done) },
                                    showTemplateOption = false,
                                    allTags = s.tags,
                                    attachments = attachmentList, attachmentProblem = attachmentProblem, onAttach = { uri -> e.draft.id?.let { viewModel.addAttachment(it, uri) } },
                                    onOpenAttachment = { a -> openAttachment(viewModel.attachmentUri(a.id), a.mime) },
                                    onRemoveAttachment = { a -> e.draft.id?.let { viewModel.removeAttachment(it, a.id) } },
                                    people = people, onCreatePerson = { name, done -> viewModel.createPerson(name, done) },
                                    onSave = { d, _ -> viewModel.saveEntry(d, false) },
                                    onBack = viewModel::closeEditor,
                                    refundOf = e.refundOf,
                                    onHistory = e.draft.id?.let { id -> { viewModel.openHistory(id) } },
                                    onRefund = if (e.canRefund) ({ e.draft.id?.let(viewModel::startRefund) ?: Unit }) else null,
                                )
                                }
                            }
                            k == 2 -> {
                                val e = (editing as? Editable.ReadOnly) ?: (heldEditing.value as Editable.ReadOnly)
                                BackHandler { viewModel.closeEditor() }
                                ReadOnlyEntryScreen(e.title, e.reason, viewModel::closeEditor)
                            }
                            k == 3 -> {
                                BackHandler { inboxOpen = false }
                                InboxScreen(inboxRows, s.accounts, s.categories, onConfirm = { id, a, c, t, f, to, r, done -> viewModel.confirmCapture(id, a, c, t, f, to, r, done) }, onDismiss = viewModel::dismissCapture, onFlip = viewModel::flipCaptureDirection, onName = viewModel::namePayee, onBack = { inboxOpen = false })
                            }
                            k == 4 -> {
                                BackHandler { healthOpen = false }
                                CaptureHealthScreen(health, onAddSender = { viewModel.addSenderHeader(it, smsPermission) }, onBack = { healthOpen = false })
                            }
                            k == 5 -> {
                                BackHandler { privacyOpen = false }
                                PrivacyScreen(privacyRows(), PermissionNotes.canUseInternet(this), onBack = { privacyOpen = false })
                            }
                            k == 6 -> {
                                BackHandler { rulesOpen = false }
                                CaptureRulesScreen(captureRules, s.categories, onAdd = viewModel::addCaptureRule, onRemove = viewModel::removeCaptureRule, onMove = viewModel::moveCaptureRule, onTest = viewModel::testCaptureRule, onBack = { rulesOpen = false })
                            }
                            k == 7 -> {
                                BackHandler { captureOpen = false }
                                CaptureSettingsScreen(
                                    captureSettings, smsPermission, NotificationCaptureService.accessGranted(this@MainActivity),
                                    onToggle = { on -> if (!on) viewModel.setSmsCapture(false) else if (smsPermission) viewModel.setSmsCapture(true) else smsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS) },
                                    onToggleNotif = { on -> viewModel.setNotificationCapture(on); if (on && !NotificationCaptureService.accessGranted(this@MainActivity)) startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    onOpenAccessSettings = { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    onHeaders = viewModel::setExtraHeaders, onOpenRules = { rulesOpen = true; viewModel.loadCaptureRules() }, onOpenHealth = { healthOpen = true; viewModel.loadCaptureHealth(smsPermission) }, onBack = { captureOpen = false },
                                )
                            }
                            k == 8 -> {
                                BackHandler { currencyOpen = false }
                                CurrencyScreen(s.baseCurrency, currencyBlocker, onChoose = viewModel::setBaseCurrency, onBack = { currencyOpen = false })
                            }
                            k == 9 -> {
                                BackHandler { appearing = false }
                                AppearanceScreen(s.appearance, onChange = viewModel::setAppearance, onBack = { appearing = false }, motionSpeed = s.motionSpeed, onMotionSpeed = viewModel::setMotionSpeed)
                            }
                            k == 10 -> {
                                BackHandler { customisingHome = false }
                                HomeLayoutScreen(s.homeLayout, onChange = viewModel::setHomeLayout, onBack = { customisingHome = false })
                            }
                            k == 11 -> {
                                BackHandler { managingAccounts = false }
                                AccountsScreen(
                                    rows = s.accountRows, onBack = { managingAccounts = false },
                                    onSave = viewModel::saveAccount, onArchive = viewModel::setAccountArchived, onDelete = viewModel::deleteAccount,
                                    onSaveCard = viewModel::saveCardTerms, onClearCard = viewModel::clearCardTerms,
                                )
                            }
                            k == 12 -> {
                                BackHandler { managingCategories = false }
                                CategoriesScreen(
                                    categories = s.categories,
                                    onBack = { managingCategories = false },
                                    onCreate = { name, parent, color, kind -> viewModel.createCategory(name, parent, color, kind) },
                                    onUpdate = viewModel::updateCategory,
                                    onSetKind = viewModel::setCategoryKind,
                                    onMove = viewModel::moveCategory,
                                    onMerge = viewModel::mergeCategory,
                                    onDelete = viewModel::deleteCategory,
                                    onAddSuggested = viewModel::addSuggestedCategories,
                                    stopped = stoppedSimilar, onAskAgain = viewModel::askAgain,
                                )
                            }
                            k == 13 -> {
                                BackHandler { securing = false; lock.dismissMessage() }
                                SecurityScreen(
                                    settings = lockSettings, availability = { app.expenses.ui.LockAvailability.valueOf(lock.availability().name) }, message = lockMessage,
                                    onEnable = { lock.enable(this@MainActivity, it) }, onDisable = { lock.disable(this@MainActivity) },
                                    onTimeout = { lock.setTimeout(this@MainActivity, it) },
                                    onBack = { securing = false; lock.dismissMessage() },
                                )
                            }
                            k == 14 -> {
                                val found by viewModel.search.collectAsStateWithLifecycle(context = MAIN)
                                BackHandler { searching = false; viewModel.closeSearch() }
                                SearchScreen(
                                    tabBar = { tabBar("Search") },
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
                            k == 15 -> {
                                val cal by viewModel.calendar.collectAsStateWithLifecycle(context = MAIN)
                                BackHandler { calendaring = false; viewModel.closeCalendar() }
                                CalendarScreen(
                                    state = cal, onBack = { calendaring = false; viewModel.closeCalendar() },
                                    onShift = viewModel::shiftCalendar, onSelect = viewModel::selectCalendarDay, onOpenEntry = viewModel::openEditor,
                                )
                            }
                            k == 16 -> {
                                val report by viewModel.report.collectAsStateWithLifecycle(context = MAIN)
                                // Back leaves the entry list first, then the report.
                                BackHandler { if (report?.drill != null) viewModel.closeDrill() else { reporting = false; viewModel.closeReports() } }
                                ReportsScreen(
                                    tabBar = { tabBar("Reports") },
                                    report = report,
                                    onBack = { reporting = false; viewModel.closeReports() },
                                    onMode = viewModel::setReportMode, onStep = viewModel::stepReport,
                                    onDrill = { target, start, end, label -> viewModel.drill(target, start, end, label) },
                                    onGroup = viewModel::setReportGroup, onRange = viewModel::setReportRange,
                                    onCloseDrill = viewModel::closeDrill,
                                    onOpenEntry = viewModel::openEditor,
                                )
                            }
                            k == 17 -> {
                                val dataState by viewModel.data.collectAsStateWithLifecycle(context = MAIN)
                                val statement by viewModel.statement.collectAsStateWithLifecycle(context = MAIN)
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
                            k == 18 -> {
                                BackHandler { alerts = false }
                                NotificationSettingsScreen(prefs = s.notifications, onChange = viewModel::updateNotificationPrefs, onSendTest = viewModel::sendTestNotification, onBack = { alerts = false })
                            }
                            k == 19 -> {
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
                            k == 20 -> {
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
                            k == 21 -> {
                                BackHandler { adding = false }
                                EntryFormScreen(
                                    title = "Add",
                                    start = prefillLoan?.split('|')?.let { p -> EntryDraft(null, DraftKind.LOAN, p[2], "", emptyList(), s.lastAccountId, "", "", System.currentTimeMillis(), personId = p[0], gave = p[1] == "1") } ?: EntryDraft(null, DraftKind.EXPENSE, "", prefillCategory ?: s.lastCategory, emptyList(), prefillAccount ?: s.lastAccountId, "", prefillNote, System.currentTimeMillis()),
                                    kinds = listOf(DraftKind.EXPENSE, DraftKind.INCOME, DraftKind.TRANSFER, DraftKind.LOAN),
                                    accounts = s.accounts,
                                    categories = s.categories,
                                    onCreateCategory = { name, parent, kind, done -> viewModel.createCategory(name, parent, null, kind, onCreated = done) },
                                    showTemplateOption = true,
                                    lastExpenseCategory = s.lastCategory, lastIncomeCategory = s.lastIncomeCategory,
                                    allTags = s.tags,
                                    people = people, onCreatePerson = { name, done -> viewModel.createPerson(name, done) },
                                    onSave = { d, asTemplate -> viewModel.saveEntry(d, asTemplate); adding = false; prefillLoan = null },
                                    onBack = { adding = false; prefillLoan = null },
                                )
                            }
                            k == 23 -> {
                                BackHandler { billsOpen = false; viewModel.watchBills(false) }
                                BillsScreen(
                                    ui = billsUi, accounts = s.accounts, onBack = { billsOpen = false; viewModel.watchBills(false) },
                                    onAdd = viewModel::trackBill, onSkip = viewModel::dismissBill, onSetDue = viewModel::setBillDue, onUnpay = viewModel::unpayBill, onPay = viewModel::payBill,
                                )
                            }
                            k == 22 -> {
                                BackHandler { if (loans.selected != null) viewModel.selectLoanPerson(null) else { loansOpen = false; viewModel.watchLoans(false) } }
                                LoansScreen(
                                    ui = loans,
                                    onBack = { loansOpen = false; viewModel.watchLoans(false) },
                                    onSelect = viewModel::selectLoanPerson,
                                    onNew = { person, gave, amount -> prefillLoan = listOf(person.orEmpty(), if (gave) "1" else "0", if (amount > 0) app.expenses.core.domain.Money(amount, BaseCurrency.current).toPlainString() else "").joinToString("|"); adding = true },
                                    onOpenEntry = { viewModel.openEditor(it) },
                                    onRemove = viewModel::removePerson,
                                )
                            }
                            else -> EntryListScreen(
                                state = s,
                                onAdd = { StartupTrace.mark("add: tapped"); prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true },
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
                                onOpenLoans = { loansOpen = true; viewModel.watchLoans(true) },
                                onOpenBills = { billsOpen = true; viewModel.watchBills(true) },
                                onOpenGuide = viewModel::openGuide,
                                tourMore = if (guide != null) tourStep?.target == app.expenses.core.domain.GuideTarget.MORE else if (wantMore) true else null,
                                onMoreShown = { wantMore = false },
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
                                onOpenInbox = { inboxOpen = true; viewModel.loadInbox() },
                                onOpenCapture = { captureOpen = true; viewModel.loadCaptureSettings() },
                                onOpenPrivacy = { privacyOpen = true },
                            )
                        }
                        } }
                    }
                }
              }
            }
        }
    }

    override fun onDestroy() {
        val platform = (application as ExpensesApp).platform
        if (platform.activity === this) platform.activity = null
        super.onDestroy()
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
        // the widget shows this month's spending: bring it up to date when the person leaves the app
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { app.expenses.widget.QuickAddWidget().updateAll(appContext) } }
        val app = application as ExpensesApp
        app.appScope.launch { runCatching { app.container.maintain() } }
    }

    private fun privacyRows() = PermissionNotes.rows(this)

    private fun colorSchemeFor(look: Appearance, dark: Boolean): androidx.compose.material3.ColorScheme {
        fun c(rgb: Int) = androidx.compose.ui.graphics.Color(0xFF000000.toInt() or rgb)
        val primary = Appearances.primaryFor(look.accent, dark)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val container = Appearances.containerFor(primary, dark)
        return base.copy(primary = c(primary), onPrimary = c(Appearances.onColor(primary)), primaryContainer = c(container), onPrimaryContainer = c(Appearances.onColor(container)), secondary = c(primary), secondaryContainer = c(container), onSecondaryContainer = c(Appearances.onColor(container)))
    }

    private fun openAttachment(uri: String, mime: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent.createChooser(view, "Open with")) } catch (e: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(this, "No app on this phone can open that file.", android.widget.Toast.LENGTH_LONG).show() }
    }

    /** The activity is `singleTop`, so a shortcut or tile tap while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ADD_EXPENSE) addRequests.value += 1
        if (intent.action == ACTION_OPEN_BILLS) billsRequests.value += 1
        if (intent.action == ACTION_ADD_EXPENSE || intent.action == ACTION_OPEN_BILLS) viewModel.deferGuide()
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "app.expenses.action.ADD_EXPENSE"
        /** A bill notification: open the bills page. */
        const val ACTION_OPEN_BILLS = "app.expenses.action.OPEN_BILLS"
    }
}

/**
 * Where the screen's collectors resume when a flow changes. The real main dispatcher, so a state change made on the database thread wakes the screen on the
 * main thread. Left to the default, the Compose test harness resumes the collector on the writing thread and recomposes there, which killed test runs with
 * "Only the original thread that created a view hierarchy can touch its views". On a phone the default is the main thread anyway, so nothing changes there.
 */
private val MAIN = kotlinx.coroutines.Dispatchers.Main.immediate

/** The last non-null value of something a screen needs while it is still being drawn on its way out. */
private class Held<T : Any>(var value: T?)
