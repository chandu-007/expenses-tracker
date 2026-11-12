package app.expenses

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.expenses.ui.PermissionRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import app.expenses.ui.BackHandler
import app.expenses.ui.BudgetScreen
import app.expenses.ui.LoansScreen
import app.expenses.ui.BillsScreen
import app.expenses.ui.AliasControls
import app.expenses.ui.GuideScreen
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.Appearance
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.Density
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.ui.aboveHinge
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

/** Text another app shared to this one (Android's Share, "Expenses") for "Check a message": [n] counts shares, so the same words shared twice are two. Kept in memory only. */
data class SharedText(val n: Int, val text: String)

private val NoSharedTexts: StateFlow<SharedText?> = kotlinx.coroutines.flow.MutableStateFlow(null)

/** A request to pay a credit card: open the add form as a transfer to [accountId]. [n] counts requests, so the same card asked twice is two requests. */
data class PayCardRequest(val n: Int, val accountId: String)

private val NoPayCardRequests: StateFlow<PayCardRequest?> = kotlinx.coroutines.flow.MutableStateFlow(null)

/** What [ExpensesRoot] needs from the host (Android activity, iOS view controller) and cannot get any other way. */
interface RootHost {
    val platform: Platform

    /** Counts requests to open the add screen (launcher shortcut, Quick Settings tile, future iOS shortcuts). */
    val addRequests: StateFlow<Int>

    /** Counts requests to open the bills page (a bill notification). */
    val billsRequests: StateFlow<Int>

    /** The latest "Pay now" tap on a card notice (a new [PayCardRequest.n] each time), or null. A host without notification buttons keeps the default. */
    val payCardRequests: StateFlow<PayCardRequest?> get() = NoPayCardRequests

    /** The latest key shortcut from a hardware keyboard (a new [ShortcutRequest.n] each time), or null. A host without hardware shortcuts keeps the default. */
    val shortcutRequests: StateFlow<ShortcutRequest?> get() = NoShortcutRequests

    /** True where a file can be handed to another app through the system share sheet (Android). */
    val canShareFiles: Boolean get() = false

    /** Offers [bytes] as a file called [name] to the apps the person picks (Google Drive, email, a chat). */
    fun shareFile(name: String, mime: String, bytes: ByteArray) {}
    val lock: AppLockPort

    /** Opens an attachment (a URI from [LedgerViewModel.attachmentUri]) in another app. */
    fun openAttachment(uri: String, mime: String)

    /** The theme in use changed (Android sets the system-bar icon colours from it). */
    fun onDarkChanged(dark: Boolean)

    /** The app lock is on (hide the app from the app switcher and screenshots where the system allows). */
    fun onSecureChanged(secure: Boolean)

    /** The first screen with data is composed. */
    fun onReady()

    /** SMS permission state and request; [onResult] gets the answer of a request. Returns a no-op, never-granted control where there is no SMS capture. */
    @Composable
    fun rememberSmsPermission(onResult: (Boolean) -> Unit): SmsPermission

    /** Whether the person let the app read other apps' notifications. */
    fun notificationAccessGranted(): Boolean
    fun openNotificationAccessSettings()

    /** The privacy screen: what the app is allowed to do and whether it may use the internet. */
    fun privacyRows(): List<PermissionRow>
    val canUseInternet: Boolean

    /** What the capture checkup reads from the phone itself: reminders permission, battery setting, whether the notification listener is bound, the maker. Cheap; read again each time the app comes back. */
    fun phoneStatus(): PhoneStatus = PhoneStatus()

    /** Opens the system page of this app's own settings (where a permission that Android no longer asks for is changed). */
    fun openAppSettings() {}

    /** Opens the system's battery page for this app (App info, Battery). */
    fun openBatterySettings() {}

    /** Asks Android to bind the notification listener again (it drops the link now and then). */
    fun reconnectNotificationListener() {}

    /** Text shared to the app from another app, to be shown in "Check a message" (checked and forgotten: never saved). A host with no share target keeps the default. */
    val sharedTexts: StateFlow<SharedText?> get() = NoSharedTexts

    /** The shared text has been taken by the screen: the host forgets it (it must not come back after the app lock, nor stay in memory while the person does other things). */
    fun sharedTextTaken() {}
}

interface SmsPermission {
    val granted: Boolean
    fun request()
}

/**
 * Where the screen's collectors resume when a flow changes. The real main dispatcher, so a state change made on the database thread wakes the screen on the
 * main thread. Left to the default, the Compose test harness resumes the collector on the writing thread and recomposes there, which killed test runs with
 * "Only the original thread that created a view hierarchy can touch its views". On a phone the default is the main thread anyway, so nothing changes there.
 */
internal val MAIN: CoroutineDispatcher = Dispatchers.Main.immediate

/** The last non-null value of something a screen needs while it is still being drawn on its way out. */
/** What a hardware-keyboard shortcut asks for: Ctrl+N adds an expense, Ctrl+F opens Search. */
enum class Shortcut { ADD, SEARCH }

class ShortcutRequest(val n: Int, val shortcut: Shortcut)

private val NoShortcutRequests = MutableStateFlow<ShortcutRequest?>(null)

private class Held<T : Any>(var value: T?)

/** The draft last typed in the open entry, with the identity of the entry it belongs to. Not state: nothing observes it, it is only read when an editor is created. */
private class TypedDraft {
    private var identity: List<Any?>? = null
    private var draft: EntryDraft? = null
    fun of(identity: List<Any?>): EntryDraft? = if (this.identity == identity) draft else null
    fun keep(identity: List<Any?>, draft: EntryDraft) { this.identity = identity; this.draft = draft }
    fun forget() { identity = null; draft = null }
}

@Composable
fun ExpensesRoot(viewModel: LedgerViewModel, host: RootHost) {
    val platform = host.platform
    val themeState by viewModel.state.collectAsStateWithLifecycle(context = MAIN)
    val look = (themeState as? UiState.Ready)?.appearance ?: Appearance()
    val dark = Appearances.isDark(look.mode, isSystemInDarkTheme())
    SideEffect { host.onDarkChanged(dark) }
    MaterialTheme(colorScheme = colorSchemeFor(look, dark)) {
        androidx.compose.runtime.CompositionLocalProvider(app.expenses.ui.LocalCompact provides (look.density == Density.COMPACT), app.expenses.ui.LocalPlatform provides platform) {
            // the keyboard takes room from the screen instead of covering it (from Android 15 the window is no longer resized for it)
            // on a wide window (a tablet, an unfolded foldable) the app keeps a comfortable width in the middle instead of stretching every line across it
            // from WindowLayout.TWO_PANE (window at least 600 dp wide and 480 dp high): Home and the open entry side by side (see WidePane.kt); below it nothing differs from a phone
            val wide = app.expenses.ui.rememberIsWide()
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            Surface(Modifier.widthIn(max = if (wide) app.expenses.ui.WIDE_PANES_MAX_WIDTH else app.expenses.ui.MAX_CONTENT_WIDTH).aboveHinge().imePadding()) {
            val state by viewModel.state.collectAsStateWithLifecycle(context = MAIN)
            LaunchedEffect(Unit) { platform.trace("first composition") }
            var adding by rememberSaveable { mutableStateOf(false) }
            // "fromAccount|toAccount|amount|note" when the add form is opened by Pay now on a card notice
            var prefillTransfer by rememberSaveable { mutableStateOf<String?>(null) }
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
            // "Why isn't capture working?", the guided setup (with the step it is on) and "Bring in earlier spending"
            var checkupOpen by rememberSaveable { mutableStateOf(false) }
            var setupOpen by rememberSaveable { mutableStateOf(false) }
            var setupStep by rememberSaveable { mutableStateOf(app.expenses.core.domain.SetupStep.PERMISSION.name) }
            var earlierOpen by rememberSaveable { mutableStateOf(false) }
            // the statement picked from "Bring in earlier spending" closes Your data again when the import is over
            var importFromEarlier by rememberSaveable { mutableStateOf(false) }
            // the one-at-a-time review of expenses with no category, opened from the card on Home
            var categorising by rememberSaveable { mutableStateOf(false) }
            val categoriseState by viewModel.categorise.collectAsStateWithLifecycle(context = MAIN)
            var privacyOpen by rememberSaveable { mutableStateOf(false) }
            val health by viewModel.captureHealth.collectAsStateWithLifecycle(context = MAIN)
            val captureRules by viewModel.captureRules.collectAsStateWithLifecycle(context = MAIN)
            val learnedPayees by viewModel.learnedPayees.collectAsStateWithLifecycle(context = MAIN)
            val learnedUndo by viewModel.learnedUndo.collectAsStateWithLifecycle(context = MAIN)
            val payeeAliases by viewModel.payeeAliases.collectAsStateWithLifecycle(context = MAIN)
            val inboxRows by viewModel.inbox.collectAsStateWithLifecycle(context = MAIN)
            val captureSettings by viewModel.captureSettings.collectAsStateWithLifecycle(context = MAIN)
            val skippedSenders by viewModel.skippedSenders.collectAsStateWithLifecycle(context = MAIN)
            val sms = host.rememberSmsPermission { granted -> viewModel.setSmsCapture(granted); if (!granted) viewModel.say("Android did not allow it, so bank texts are not being read.") }
            val smsPermission = sms.granted
            val notifAccess = app.expenses.ui.rememberResumeState { host.notificationAccessGranted() }
            // what the checkup and the setup say about the permission and the notification access: the phone's answer (a test may stand in for it with [CheckupTestHook]; the app never sets it)
            val smsGranted = CheckupTestHook.smsPermission ?: smsPermission
            val accessGranted = CheckupTestHook.notifAccess ?: notifAccess.value
            val currencyBlocker by viewModel.currencyBlocker.collectAsStateWithLifecycle(context = MAIN)
            LaunchedEffect(managingAccounts) { viewModel.setAccountsScreenOpen(managingAccounts) }
            var budgeting by rememberSaveable { mutableStateOf(false) }
            var loansOpen by rememberSaveable { mutableStateOf(false) }
            var billsOpen by rememberSaveable { mutableStateOf(false) }
            val billsUi by viewModel.bills.collectAsStateWithLifecycle(context = MAIN)
            val billUndo by viewModel.billUndo.collectAsStateWithLifecycle(context = MAIN)
            val waitingUndo by viewModel.waitingUndo.collectAsStateWithLifecycle(context = MAIN)
            val inboxUndo by viewModel.inboxUndo.collectAsStateWithLifecycle(context = MAIN)
            val billAsks by host.billsRequests.collectAsState()
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
            var subscribing by rememberSaveable { mutableStateOf(false) }
            var transferring by rememberSaveable { mutableStateOf(false) }
            var goaling by rememberSaveable { mutableStateOf(false) }
            var searching by rememberSaveable { mutableStateOf(false) }
            // the account Search opens inside (from Home's account bar); null searches every account
            var searchAccount by rememberSaveable { mutableStateOf<String?>(null) }
            // a tag and days Search opens with (a trip's "See all entries"); null for every other way in
            var searchTag by rememberSaveable { mutableStateOf<String?>(null) }
            var searchFrom by rememberSaveable { mutableStateOf<Long?>(null) }
            var searchTo by rememberSaveable { mutableStateOf<Long?>(null) }
            // Travel mode: the trips page (from More) and the summary of one trip (from the page, or from Home's card)
            var tripsOpen by rememberSaveable { mutableStateOf(false) }
            var tripSummaryOf by rememberSaveable { mutableStateOf<String?>(null) }
            var securing by rememberSaveable { mutableStateOf(false) }
            val lock = host.lock
            val locked by lock.locked.collectAsStateWithLifecycle(context = MAIN)
            val lockSettings by lock.settings.collectAsStateWithLifecycle(context = MAIN)
            val lockMessage by lock.message.collectAsStateWithLifecycle(context = MAIN)
            // While the lock is on, the app is hidden from the recent-apps view and from screenshots.
            LaunchedEffect(lockSettings.enabled) { host.onSecureChanged(lockSettings.enabled) }
            var prefillCategory by rememberSaveable { mutableStateOf<String?>(null) }
            var prefillAccount by rememberSaveable { mutableStateOf<String?>(null) }
            var prefillNote by rememberSaveable { mutableStateOf("") }
            // Lets `am start` and tracing measure cold start up to the first screen with data, not just the spinner.
            LaunchedEffect(state is UiState.Ready) { if (state is UiState.Ready) { platform.trace("ready composed"); host.onReady() } }

            val requests by host.addRequests.collectAsState()
            var handledRequests by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(requests) {
                if (requests > handledRequests) { handledRequests = requests; prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true }
            }
            val payRequest by host.payCardRequests.collectAsState()
            var handledPay by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(payRequest) {
                val r = payRequest ?: return@LaunchedEffect
                if (r.n > handledPay) { handledPay = r.n; viewModel.payCardDraft(r.accountId) { draft -> if (draft != null) { prefillTransfer = draft; prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true } } }
            }

            val editing by viewModel.editor.collectAsStateWithLifecycle(context = MAIN)
            val history by viewModel.history.collectAsStateWithLifecycle(context = MAIN)
            val attachmentList by viewModel.attachments.collectAsStateWithLifecycle(context = MAIN)
            val attachmentProblem by viewModel.attachmentProblem.collectAsStateWithLifecycle(context = MAIN)
            val editorProblem by viewModel.editorProblem.collectAsStateWithLifecycle(context = MAIN)
            val stagedList by viewModel.stagedAttachments.collectAsStateWithLifecycle(context = MAIN)
            val foreignRates by viewModel.foreignRates.collectAsStateWithLifecycle(context = MAIN)
            // closing the add form without saving deletes the files chosen for it (a save takes them first)
            LaunchedEffect(adding) { if (!adding) viewModel.discardStaged() }

            if (locked) LockedScreen(lockMessage) { lock.requestUnlock() } else when (val s = state) {
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
                    adding = false; searching = false; reporting = false; calendaring = false; subscribing = false; transferring = false; goaling = false; inboxOpen = false; captureOpen = false; rulesOpen = false; healthOpen = false
                    billsOpen = false; managingAccounts = false; managingCategories = false; budgeting = false; repeating = false; loansOpen = false; alerts = false
                    checkupOpen = false; setupOpen = false; earlierOpen = false; importFromEarlier = false
                    privacyOpen = false; securing = false; yourData = false; appearing = false; customisingHome = false; currencyOpen = false; categorising = false; viewModel.closeCategorise()
                    viewModel.watchBills(false); viewModel.watchLoans(false); viewModel.closeSearch(); viewModel.closeCalendar(); viewModel.closeSubscriptions(); viewModel.closeTransferCandidates(); viewModel.closeGoals(); viewModel.closeReports(); viewModel.cancelRestore()
                    viewModel.closeEditor(); viewModel.closeHistory()
                    tripsOpen = false; tripSummaryOf = null; viewModel.closeTrips(); viewModel.closeTripSummary()
                }
                // the guided setup opens at the step the person left it on (the first step when it was never begun or is finished)
                val openSetup = {
                    viewModel.openSetup { st ->
                        setupStep = (if (st.status == app.expenses.core.domain.SetupStatus.IN_PROGRESS || st.status == app.expenses.core.domain.SetupStatus.OFFERED) st.step else app.expenses.core.domain.SetupStep.PERMISSION).name
                        setupOpen = true
                    }
                }
                // text shared from another app opens "Check a message" with it, checked at once
                val shared by host.sharedTexts.collectAsState()
                // NOT saveable: the activity's share counter starts at 0 in a new activity or process, so a restored count would swallow the first shares there
                var handledShared by remember { mutableIntStateOf(0) }
                var sharedWords by remember { mutableStateOf("") }
                // the shared words are forgotten as soon as the page is left: they must not come back the next time it is opened
                LaunchedEffect(checkupOpen) { if (!checkupOpen) sharedWords = "" }
                LaunchedEffect(shared) {
                    val r = shared ?: return@LaunchedEffect
                    if (r.n <= handledShared) return@LaunchedEffect
                    handledShared = r.n; sharedWords = r.text
                    host.sharedTextTaken()
                    closeEverything(); checkupOpen = true
                    // the screen checks them as it appears (see below): a check started here would be cleared by the screen it replaces
                }
                // the end of the first tour offers the setup, once
                val setupRequested by viewModel.setupRequests.collectAsStateWithLifecycle(context = MAIN)
                var handledSetupRequests by rememberSaveable { mutableIntStateOf(0) }
                LaunchedEffect(setupRequested) { if (setupRequested > handledSetupRequests) { handledSetupRequests = setupRequested; openSetup() } }
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
                        app.expenses.core.domain.GuideTarget.SUBSCRIPTIONS -> { subscribing = true; viewModel.openSubscriptions() }
                        app.expenses.core.domain.GuideTarget.GOALS -> { goaling = true; viewModel.openGoals() }
                    }
                }
                guide?.let { g ->
                    GuideScreen(
                        g, index = tourIndex, onIndex = { tourIndex = it },
                        onShowAll = { tourIndex = 0; viewModel.showWholeGuide() },
                        onTryIt = { viewModel.finishGuide(false); tourIndex = 0 }, // ends the tour and stays on the screen being shown
                        onFinish = { never -> viewModel.finishGuide(never, completed = tourIndex >= g.steps.lastIndex); tourIndex = 0; closeEverything() },
                    )
                }
                val similarOffer by viewModel.similar.collectAsStateWithLifecycle(context = MAIN)
                val stoppedSimilar by viewModel.stoppedSimilar.collectAsStateWithLifecycle(context = MAIN)
                androidx.compose.runtime.LaunchedEffect(managingCategories) { if (managingCategories) viewModel.loadStoppedSimilar() }
                similarOffer?.let { offer -> if (editing == null && !adding) app.expenses.ui.SimilarDialog(offer, onApply = viewModel::applySimilar, onDismiss = { stop -> viewModel.dismissSimilar(stop) }) }
                // the entry editor: a full-screen overlay on a phone (keys 1 and 2), the right-hand pane beside Home on a wide window
                // What is typed in the open entry is kept here as it changes, so when the window turns wide or narrow under an open entry (a foldable unfolded or folded), the editor that appears in
                // its new place (the pane beside Home, or the full screen) starts from it instead of from the saved entry. Forgotten when the editor closes.
                val typedDraft = androidx.compose.runtime.remember { TypedDraft() }
                androidx.compose.runtime.LaunchedEffect(editing == null) { if (editing == null) typedDraft.forget() }
                val editorScreen: @androidx.compose.runtime.Composable (Editable) -> Unit = { e -> when (e) {
                    is Editable.Form -> {
                        BackHandler { viewModel.closeEditor() }
                        // Keyed, so opening a refund from an expense's editor starts a new form instead of reusing the expense's typed state.
                        androidx.compose.runtime.key(e.draft.id, e.draft.kind, e.draft.refundsEntry) {
                        val identity = listOf(e.draft.id, e.draft.kind, e.draft.refundsEntry)
                        EntryFormScreen(
                            restored = typedDraft.of(identity), onDraft = { d -> typedDraft.keep(identity, d) },
                            title = e.title,
                            start = e.draft.let { d -> d.copy(accountId = d.accountId.ifEmpty { s.lastAccountId }) },
                            kinds = if (e.canChangeKind) listOf(DraftKind.EXPENSE, DraftKind.INCOME, DraftKind.TRANSFER) else listOf(e.draft.kind),
                            accounts = s.allAccounts.filter { a -> a.id == e.draft.accountId || a.id == e.draft.toAccountId || s.accounts.any { it.id == a.id } }, // an archived account stays pickable for an entry that uses it
                            categories = s.categories,
                            onCreateCategory = { name, parent, kind, done -> viewModel.createCategory(name, parent, null, kind, onCreated = done) },
                            showTemplateOption = false,
                            allTags = s.tags,
                            attachments = attachmentList, attachmentProblem = attachmentProblem, onAttach = { uri -> e.draft.id?.let { viewModel.addAttachment(it, uri) } },
                            onOpenAttachment = { a -> host.openAttachment(viewModel.attachmentUri(a.id), a.mime) },
                            onRemoveAttachment = { a -> e.draft.id?.let { viewModel.removeAttachment(it, a.id) } },
                            people = people, onCreatePerson = { name, done -> viewModel.createPerson(name, done) },
                            onSave = { d, _ -> if (e.canChangeKind && d.kind != e.draft.kind) viewModel.saveChangingKind(d) else viewModel.saveEntry(d, false) },
                            onBack = viewModel::closeEditor,
                            refundOf = e.refundOf,
                            onHistory = e.draft.id?.let { id -> { viewModel.openHistory(id) } },
                            kindChangeProblem = editorProblem,
                            onRefund = if (e.canRefund) ({ e.draft.id?.let(viewModel::startRefund) ?: Unit }) else null,
                            origin = e.origin,
                            suggestedCategory = e.suggestion,
                        )
                        }
                    }
                    is Editable.ReadOnly -> {
                        BackHandler { viewModel.closeEditor() }
                        ReadOnlyEntryScreen(e.title, e.reason, viewModel::closeEditor, e.origin)
                    }
                } }
                // the screens keep their place in this list as their key (-1 is home); places 1 and 2 are the editor, decided below
                val screens = listOf(
                    history != null, false, false, inboxOpen, healthOpen, privacyOpen, rulesOpen, captureOpen, currencyOpen, appearing,
                    customisingHome, managingAccounts, managingCategories, securing, searching, calendaring, reporting, yourData, alerts, repeating, budgeting, adding, loansOpen, billsOpen, subscribing, goaling, transferring, categorising,
                    checkupOpen, setupOpen, earlierOpen, tripSummaryOf != null, tripsOpen,
                )
                // The entry editor is the pane beside the list on a wide window, but only while Home or Search (the two screens with a list to put it beside) is what is showing: opened from the calendar,
                // a report or another screen it is a full screen over that screen on a wide window too, and Back returns to that screen.
                val searchAlone = searching && screens.count { it } == 1
                val editorFull = !wide || (screens.any { it } && !searchAlone)
                val screenKey = when {
                    history != null -> 0
                    editing is Editable.Form && editorFull -> 1
                    editing is Editable.ReadOnly && editorFull -> 2
                    else -> screens.indexOf(true)
                }
                // a screen that is leaving is still drawn for a moment, after the state it was showing is gone: it keeps its last value
                // what is typed and chosen on Search, kept here (a new, clean one for each visit): the page goes away and comes back when the window changes size under an open entry
                val searchFields = rememberSaveable(searching, searchAccount, searchTag, searchFrom, searchTo, saver = app.expenses.ui.SearchFields.Saver) { app.expenses.ui.SearchFields(searchAccount, searchTag, searchFrom, searchTo) }
                var searchFocus by rememberSaveable { mutableIntStateOf(0) }
                val heldHistory = androidx.compose.runtime.remember { Held(history) }; if (history != null) heldHistory.value = history
                val heldEditing = androidx.compose.runtime.remember { Held(editing) }; if (editing != null) heldEditing.value = editing
                // the bar along the bottom is shown by Home, Search and Reports alike; its glass indicator glides between them as one bar
                var wantMore by rememberSaveable { mutableStateOf(false) }
                // the More page is open: kept here, above the screens, so Back from a screen opened from More comes back to More
                var moreOpen by rememberSaveable { mutableStateOf(false) }
                val goTab: (String) -> Unit = { t ->
                    if (searching && t != "Search") { searching = false; searchAccount = null; searchTag = null; searchFrom = null; searchTo = null; viewModel.closeSearch() }
                    if (reporting && t != "Reports") { reporting = false; viewModel.closeReports() }
                    when {
                        t == "Search" && !searching -> { platform.trace("search: tapped"); searchAccount = null; searchTag = null; searchFrom = null; searchTo = null; searching = true }
                        t == "Reports" && !reporting -> { reporting = true; viewModel.openReports() }
                        t == "More" -> wantMore = true
                    }
                }
                // a shortcut from a hardware keyboard acts only where the bar along the bottom is (Home, Search, Reports), with no tour and no add screen up: elsewhere it would open a screen that stays hidden behind the one showing
                val shortcut by host.shortcutRequests.collectAsState()
                var handledShortcut by rememberSaveable { mutableIntStateOf(0) }
                androidx.compose.runtime.LaunchedEffect(shortcut) {
                    val r = shortcut ?: return@LaunchedEffect
                    if (r.n <= handledShortcut) return@LaunchedEffect
                    handledShortcut = r.n
                    if (guide != null || adding || screenKey !in listOf(app.expenses.ui.ScreenKeys.HOME, 14, 16)) return@LaunchedEffect
                    when (r.shortcut) {
                        // the add screen is drawn over Home only: from Search or Reports the page is left first (what was typed there is not kept), or the add screen would wait unseen behind it
                        Shortcut.ADD -> {
                            if (searching) { searching = false; searchAccount = null; viewModel.closeSearch() }
                            if (reporting) { reporting = false; viewModel.closeReports() }
                            prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true
                        }
                        Shortcut.SEARCH -> if (searching) searchFocus++ else goTab("Search")
                    }
                }
                val tabBar: @androidx.compose.runtime.Composable (String) -> Unit = { sel ->
                    // with the keyboard up the bar would float above it over nothing: it returns when the keyboard goes
                    if (!app.expenses.ui.rememberKeyboardOpen()) {
                    app.expenses.ui.HomeBar(sel, onHome = { goTab("Home") }, onSearch = { goTab("Search") }, onReports = { goTab("Reports") }, onMore = { goTab("More") }, moreBadge = s.showBackupNudge)
                    }
                }
                // a message set while a screen other than Home is open (Added to your bills, Notifications are blocked...) would wait for Home: a bar under the screen says it (the screen is
                // given the room, so the bar covers nothing and takes no touch)
                app.expenses.ui.MessageBarHost(
                    s.message, homeShowing = screenKey == app.expenses.ui.ScreenKeys.HOME, hideAmounts = s.hideAmounts, onShown = viewModel::dismissMessage,
                    // Post and Skip on a repeating item offer an Undo; Bills (23) and Repeating expenses (19) are where they are pressed, and have no bar of their own; so is Accounts (11), where Archive offers one
                    undo = s.undo?.takeIf { screenKey == 11 || screenKey == 19 || screenKey == 23 }, onUndo = viewModel::undo, onUndoDismissed = viewModel::dismissUndo,
                ) {
                app.expenses.ui.ScreenHost(screenKey) { k -> app.expenses.ui.WideFrame(wide, k, editing, editorScreen, moreOpen = moreOpen) { when {
                    k == 0 -> {
                        val h = history ?: heldHistory.value!!
                        BackHandler { viewModel.closeHistory() }
                        HistoryScreen(h, onBack = viewModel::closeHistory, onRestore = { viewModel.restoreVersion(h.entryId, it) })
                    }
                    // k 1 and 2 are drawn only while the editor is a full screen: a one still leaving when the window turns wide draws nothing, the pane beside Home has the editor then
                    k == 1 -> if (editorFull) editorScreen((editing as? Editable.Form) ?: (heldEditing.value as Editable.Form))
                    k == 2 -> if (editorFull) editorScreen((editing as? Editable.ReadOnly) ?: (heldEditing.value as Editable.ReadOnly))
                    k == 3 -> {
                        BackHandler { inboxOpen = false }
                        InboxScreen(inboxRows, s.accounts, s.categories, onConfirm = { id, a, c, t, f, to, r, done -> viewModel.confirmCapture(id, a, c, t, f, to, r, done) }, onDismiss = viewModel::dismissCapture, onFlip = viewModel::flipCaptureDirection, onName = viewModel::namePayee, onBack = { inboxOpen = false }, onPaste = if (platform.supportsShortcutsCapture) viewModel::pasteMessage else null, undo = inboxUndo, onUndo = viewModel::undoDismissCapture, onUndoGone = viewModel::inboxUndoGone, onConfirmPair = viewModel::confirmTransferPair, onApart = viewModel::keepApart, onConvert = viewModel::makeTransfer, onChooseAccount = viewModel::chooseCaptureAccount, onCreateAccount = viewModel::createCaptureAccount, onBillPaid = viewModel::confirmBillPayment, onJoin = viewModel::joinCapture, onSetUpCapture = { inboxOpen = false; captureOpen = true; viewModel.loadCaptureSettings() }, onOpenCheckup = { inboxOpen = false; checkupOpen = true }, onSameShop = { from, to, done -> viewModel.joinShops(from, to, true, done) })
                    }
                    k == 4 -> {
                        BackHandler { healthOpen = false }
                        // the report depends on the text permission: work it out again when the permission changes while the screen is open (the first load is the tap that opened it)
                        val firstHealth = remember { booleanArrayOf(true) }
                        LaunchedEffect(smsPermission) { if (firstHealth[0]) firstHealth[0] = false else viewModel.loadCaptureHealth(smsPermission) }
                        CaptureHealthScreen(health, onAddSender = { viewModel.addSenderHeader(it, smsPermission) }, onBack = { healthOpen = false })
                    }
                    k == 5 -> {
                        BackHandler { privacyOpen = false }
                        // read again whenever the app returns from the system's settings, so a permission given there shows at once
                        val privacy = app.expenses.ui.rememberResumeState { host.privacyRows() }
                        PrivacyScreen(privacy.value, host.canUseInternet, onBack = { privacyOpen = false })
                    }
                    k == 6 -> {
                        BackHandler { rulesOpen = false }
                        CaptureRulesScreen(
                            captureRules, s.categories, onAdd = viewModel::addCaptureRule, onRemove = viewModel::removeCaptureRule, onMove = viewModel::moveCaptureRule, onTest = viewModel::testCaptureRule, onBack = { rulesOpen = false },
                            learned = learnedPayees, onForgetLearned = viewModel::forgetLearned, onChangeLearned = { key, cat, done -> viewModel.changeLearned(key, cat, done) }, onFileLearned = viewModel::fileEarlierLike,
                            learnedUndo = learnedUndo, onUndoLearned = viewModel::undoLearnedFiling, onUndoLearnedGone = viewModel::learnedUndoGone,
                            shops = AliasControls(payeeAliases, { from, to, done -> viewModel.joinShops(from, to, false, done) }, viewModel::removeAlias),
                        )
                    }
                    k == 7 -> {
                        BackHandler { captureOpen = false }
                        CaptureSettingsScreen(
                            captureSettings, smsPermission, notifAccess.value,
                            onToggle = { on -> if (!on) viewModel.setSmsCapture(false) else if (smsPermission) viewModel.setSmsCapture(true) else sms.request() },
                            onToggleNotif = { on -> viewModel.setNotificationCapture(on); if (on && !notifAccess.value) host.openNotificationAccessSettings() },
                            onToggleEmail = { on -> viewModel.setEmailCapture(on); if (on && !notifAccess.value) host.openNotificationAccessSettings() },
                            onOpenAccessSettings = { host.openNotificationAccessSettings() },
                            onHeaders = viewModel::setExtraHeaders, onOpenRules = { rulesOpen = true; viewModel.loadCaptureRules() }, onOpenHealth = { healthOpen = true; viewModel.loadCaptureHealth(smsPermission) }, onBack = { captureOpen = false },
                            onTestMessage = { text, done -> viewModel.pasteMessage(text) { r -> done(r); viewModel.loadCaptureSettings() } },
                            skipped = skippedSenders, onTrustSkipped = viewModel::trustSkippedSender,
                            onOpenCheckup = { captureOpen = false; checkupOpen = true }, onOpenSetup = { captureOpen = false; openSetup() },
                        )
                    }
                    k == 28 -> {
                        BackHandler { checkupOpen = false }
                        // read again whenever the app comes back from Android's settings, so a permission given there shows at once
                        val phone = app.expenses.ui.rememberResumeState { host.phoneStatus() }
                        val phoneNow = CheckupTestHook.phone ?: phone.value
                        val checkup by viewModel.checkup.checkup.collectAsStateWithLifecycle(context = MAIN)
                        val checked by viewModel.checkup.result.collectAsStateWithLifecycle(context = MAIN)
                        val reload = { viewModel.checkup.load(phoneNow, smsGranted, accessGranted) }
                        // the listener binds a moment after access is given, and Android says nothing when it does: the phone's status is read again every two seconds while this page is open (a power-manager call; a changed value only reloads)
                        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2_000); phone.refresh() } }
                        LaunchedEffect(phoneNow, smsGranted, accessGranted, captureSettings.smsOn, captureSettings.notifOn, captureSettings.emailOn, captureSettings.extraHeaders) { reload() }
                        var disclosing by rememberSaveable { mutableStateOf(false) }
                        if (disclosing) app.expenses.ui.SmsDisclosure(onAgree = { disclosing = false; sms.request() }, onDecline = { disclosing = false })
                        val askReminders = app.expenses.ui.rememberNotificationPermissionRequest { phone.refresh(); reload() }
                        val later = androidx.compose.runtime.rememberCoroutineScope()
                        // keyed on the share: a second text shared while the screen is open starts the screen again with that text (it reads its text box once)
                        androidx.compose.runtime.key(handledShared) { app.expenses.ui.CaptureCheckupScreen(
                            checkup, checked, initialText = sharedWords,
                            onAct = { act, arg -> when (act) {
                                app.expenses.core.domain.CheckAct.ALLOW_TEXTS -> disclosing = true
                                app.expenses.core.domain.CheckAct.TURN_ON_TEXTS -> viewModel.setSmsCapture(true)
                                app.expenses.core.domain.CheckAct.OPEN_APP_SETTINGS -> host.openAppSettings()
                                app.expenses.core.domain.CheckAct.OPEN_NOTIFICATION_ACCESS -> host.openNotificationAccessSettings()
                                app.expenses.core.domain.CheckAct.RECONNECT_LISTENER -> { host.reconnectNotificationListener(); later.launch { kotlinx.coroutines.delay(1_500); phone.refresh(); reload() } }
                                app.expenses.core.domain.CheckAct.ALLOW_REMINDERS -> askReminders()
                                app.expenses.core.domain.CheckAct.OPEN_BATTERY -> host.openBatterySettings()
                                app.expenses.core.domain.CheckAct.OPEN_SENDERS, app.expenses.core.domain.CheckAct.OPEN_CAPTURE -> { captureOpen = true; viewModel.loadCaptureSettings() }
                                app.expenses.core.domain.CheckAct.OPEN_ACCOUNTS -> managingAccounts = true
                                app.expenses.core.domain.CheckAct.TRUST_SENDER -> viewModel.trustSkippedSender(arg)
                                app.expenses.core.domain.CheckAct.OPEN_SHORTCUTS -> platform.openExternalUrl("shortcuts://")
                                app.expenses.core.domain.CheckAct.TURN_ON_PAYMENT_APPS -> { viewModel.setNotificationCapture(true); if (!accessGranted) host.openNotificationAccessSettings() }
                                app.expenses.core.domain.CheckAct.OPEN_SETUP -> { checkupOpen = false; openSetup() }
                            } },
                            onTick = viewModel.checkup::tick, onCheck = viewModel.checkup::check, onClearResult = viewModel.checkup::clearResult, onBack = { checkupOpen = false },
                        ) }
                    }
                    k == 29 -> {
                        val step = app.expenses.core.domain.SetupStep.entries.firstOrNull { it.name == setupStep } ?: app.expenses.core.domain.SetupStep.PERMISSION
                        BackHandler { viewModel.setupLeave(step); setupOpen = false }
                        val setupUi by viewModel.setup.collectAsStateWithLifecycle(context = MAIN)
                        LaunchedEffect(Unit) { viewModel.loadCaptureSettings(); viewModel.loadSetup() }
                        app.expenses.ui.GuidedSetupScreen(
                            step, setupUi,
                            app.expenses.ui.SetupFacts(viewModel.checkup.way(), smsGranted, captureSettings.smsOn, captureSettings.notifOn, accessGranted, captureSettings.lastReceivedFresh, captureSettings.extraHeaders),
                            s.accountRows,
                            app.expenses.ui.SetupActions(
                                onStep = { to -> setupStep = to.name; viewModel.setupMoveTo(to) },
                                onLeave = { st -> viewModel.setupLeave(st); setupOpen = false },
                                onAllowTexts = { if (smsGranted) viewModel.setSmsCapture(true) else sms.request() },
                                onTurnOnPaymentApps = { viewModel.setNotificationCapture(true); if (!accessGranted) host.openNotificationAccessSettings() },
                                onOpenShortcuts = { platform.openExternalUrl("shortcuts://") },
                                onOpenCaptureSteps = { captureOpen = true; viewModel.loadCaptureSettings() },
                                onChooseBank = viewModel::setupChooseBank, onHeaders = viewModel::setExtraHeaders,
                                onSaveDigits = { row, digits, done -> viewModel.saveAccount(row.id, row.name, row.type, row.openingText, row.network, row.tier, digits, done) },
                                onOpenAccounts = { managingAccounts = true },
                                onOpenCheckup = { setupOpen = false; checkupOpen = true },
                                onDone = { setupOpen = false },
                            ),
                        )
                    }
                    k == 31 -> {
                        val summary by viewModel.tripSummary.collectAsStateWithLifecycle(context = MAIN)
                        val tripUndo by viewModel.tripUndo.collectAsStateWithLifecycle(context = MAIN)
                        val closeSummary = { tripSummaryOf = null; viewModel.closeTripSummary() }
                        // the page is rebuilt from the store, also after the app's process was recreated with the page open
                        LaunchedEffect(tripSummaryOf) { tripSummaryOf?.let { viewModel.openTripSummary(it) } }
                        BackHandler { closeSummary() }
                        app.expenses.ui.TripSummaryScreen(
                            summary, tripUndo, onBack = closeSummary,
                            // Search opens with the trip's tag already chosen (all dates: the same entries as the page's total) (Back returns to this page: it is still open underneath)
                            onSeeAll = { tag -> searchAccount = null; searchTag = tag; searchFrom = null; searchTo = null; searching = true },
                            onTagEarlier = { id -> viewModel.tagEarlier(id, null) }, onEndToday = viewModel::endTripToday,
                            onUndo = viewModel::undoTripTagging, onUndoGone = viewModel::tripUndoGone,
                        )
                    }
                    k == 32 -> {
                        val tripList by viewModel.trips.collectAsStateWithLifecycle(context = MAIN)
                        val tripOffer by viewModel.tripOffer.collectAsStateWithLifecycle(context = MAIN)
                        val tripUndo by viewModel.tripUndo.collectAsStateWithLifecycle(context = MAIN)
                        val closeTrips = { tripsOpen = false; viewModel.closeTrips() }
                        LaunchedEffect(Unit) { if (tripList == null) viewModel.openTrips() }
                        BackHandler { closeTrips() }
                        app.expenses.ui.TripsScreen(
                            tripList, tags = s.tags, offer = tripOffer, undo = tripUndo, onBack = closeTrips,
                            onSave = viewModel::saveTrip, onDelete = viewModel::deleteTrip,
                            onOpen = { id -> tripSummaryOf = id; viewModel.openTripSummary(id) },
                            onAcceptOffer = { o -> viewModel.tagEarlier(o.tripId, o.days) }, onDismissOffer = viewModel::dismissTripOffer,
                            onUndo = viewModel::undoTripTagging, onUndoGone = viewModel::tripUndoGone,
                        )
                    }
                    k == 30 -> {
                        BackHandler { earlierOpen = false }
                        val pickers = app.expenses.ui.rememberFilePickers()
                        app.expenses.ui.EarlierSpendingScreen(
                            accounts = s.accounts, categories = s.categories,
                            // the statement importer is the one in Your data: the picked file goes to it, and its page opens over this one until the import is closed
                            onImportStatement = {
                                val start = { uri: String -> viewModel.startImport(uri); importFromEarlier = true; yourData = true }
                                val fixed = CheckupTestHook.statement
                                if (fixed != null) start(fixed) else pickers.openDocument(listOf("*/*")) { uri -> if (uri != null) start(uri) }
                            },
                            onOpenAccounts = { managingAccounts = true },
                            onAdd = viewModel::addEarlierSpending, onBack = { earlierOpen = false },
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
                            onSaveCard = viewModel::saveCardTerms, onClearCard = viewModel::clearCardTerms, onSetCreditLimit = viewModel::setCreditLimit,
                            onShowEntries = { id -> viewModel.showAccountOnHome(id); managingAccounts = false; moreOpen = false },
                            safeToSpend = s.safeToSpend, onSafeToSpendOnHome = viewModel::setSafeToSpendOnHome,
                            onFixBalance = viewModel::fixBalance, onDismissFixHint = viewModel::dismissFixHint,
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
                            settings = lockSettings, availability = lock::availability, message = lockMessage,
                            onEnable = { lock.enable(it) }, onDisable = { lock.disable() },
                            onTimeout = { lock.setTimeout(it) },
                            onBack = { securing = false; lock.dismissMessage() },
                        )
                    }
                    k == 14 -> {
                        val found by viewModel.search.collectAsStateWithLifecycle(context = MAIN)
                        BackHandler { searching = false; searchAccount = null; searchTag = null; searchFrom = null; searchTo = null; viewModel.closeSearch() }
                        SearchScreen(
                            tabBar = { tabBar("Search") },
                            state = found, categories = s.categories,
                            onChange = viewModel::search,
                            onBack = { searching = false; searchAccount = null; searchTag = null; searchFrom = null; searchTo = null; viewModel.closeSearch() },
                            initialAccountId = searchAccount, initialTag = searchTag, initialFromDay = searchFrom, initialToDay = searchTo, fields = searchFields, focusTick = searchFocus,
                            onOpen = { viewModel.openEditor(it.id) },
                            accounts = s.accounts,
                            onBulkCategory = viewModel::bulkSetCategory, onBulkAccount = viewModel::bulkSetAccount, onBulkDelete = viewModel::bulkDelete, onBulkTransfer = viewModel::bulkMakeTransfers,
                            tags = s.tags, onBulkAddTag = viewModel::bulkAddTag, onBulkRemoveTag = viewModel::bulkRemoveTag,
                            onUndo = viewModel::undo,
                            saved = s.savedSearches, onSaveSearch = viewModel::saveSearch, onDeleteSaved = viewModel::deleteSavedSearch,
                            recent = s.recentSearches, onRemember = viewModel::rememberSearch, onForgetRecent = viewModel::forgetSearch, onClearRecent = viewModel::clearRecentSearches,
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
                    k == 24 -> {
                        val subs by viewModel.subscriptions.collectAsStateWithLifecycle(context = MAIN)
                        BackHandler { subscribing = false; viewModel.closeSubscriptions() }
                        app.expenses.ui.SubscriptionsScreen(subs, onBack = { subscribing = false; viewModel.closeSubscriptions() }, onNotOne = viewModel::ignoreSubscription, onShowIgnored = viewModel::showIgnoredSubscriptions, onRepeat = viewModel::repeatSubscription)
                    }
                    k == 27 -> {
                        BackHandler { categorising = false; viewModel.closeCategorise() }
                        app.expenses.ui.CategoriseScreen(
                            categoriseState, s.categories, onChoose = viewModel::categoriseChoose, onSkip = viewModel::categoriseSkip, onBack = { categorising = false; viewModel.closeCategorise() },
                            onUndo = viewModel::undoCategorise, onUndoGone = viewModel::categoriseUndoGone,
                        )
                    }
                    k == 26 -> {
                        val found by viewModel.transferCandidates.collectAsStateWithLifecycle(context = MAIN)
                        BackHandler { transferring = false; viewModel.closeTransferCandidates() }
                        app.expenses.ui.TransferCandidatesScreen(
                            found, onBack = { transferring = false; viewModel.closeTransferCandidates() }, onMake = viewModel::makeCandidateTransfers, onNotOne = viewModel::dismissTransferCandidates,
                            onShowDismissed = viewModel::showDismissedTransferCandidates, onCreateCash = viewModel::createCashAccountForCandidates, onUndo = viewModel::undoCandidateTransfers, onUndoGone = viewModel::candidateTransfersUndoGone,
                        )
                    }
                    k == 25 -> {
                        val goalState by viewModel.goals.collectAsStateWithLifecycle(context = MAIN)
                        BackHandler { goaling = false; viewModel.closeGoals() }
                        app.expenses.ui.GoalsScreen(goalState, onBack = { goaling = false; viewModel.closeGoals() }, onSave = viewModel::saveGoal, onAdjust = viewModel::adjustGoal, onDelete = viewModel::deleteGoal)
                    }
                    k == 16 -> {
                        val report by viewModel.report.collectAsStateWithLifecycle(context = MAIN)
                        val reportBuilds by viewModel.reportBuilds.collectAsStateWithLifecycle(context = MAIN)
                        // Back leaves the entry list first, then the report.
                        BackHandler { if (report?.drill != null) viewModel.closeDrill() else { reporting = false; viewModel.closeReports() } }
                        ReportsScreen(
                            tabBar = { tabBar("Reports") },
                            report = report, working = reportBuilds > 0,
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
                        val autoRunning by viewModel.autoExportRunning.collectAsStateWithLifecycle(context = MAIN)
                        val statement by viewModel.statement.collectAsStateWithLifecycle(context = MAIN)
                        // an import started from "Bring in earlier spending" returns there when it is closed (it may not have become active yet when this page opens: it is waited for)
                        var sawImport by remember { mutableStateOf(false) }
                        LaunchedEffect(statement.active) { if (statement.active) sawImport = true else if (sawImport && importFromEarlier) { importFromEarlier = false; yourData = false } }
                        if (statement.active) {
                            BackHandler { viewModel.closeImport() }
                            ImportScreen(
                                state = statement, accounts = s.accounts,
                                onAccount = viewModel::changeImportAccount, onMapping = viewModel::changeImportMapping,
                                onImport = viewModel::confirmImport, onClose = viewModel::closeImport, onPassword = viewModel::submitImportPassword,
                                onCardPayments = viewModel::chooseCardPayments, onCreateCategories = viewModel::setCreateImportCategories, onPlainFile = viewModel::readImportAsPlainFile,
                            )
                        } else {
                            BackHandler { importFromEarlier = false; yourData = false; viewModel.cancelRestore(); viewModel.dismissDataStatus() }
                            DataScreen(
                                state = dataState,
                                onBack = { importFromEarlier = false; yourData = false; viewModel.cancelRestore(); viewModel.dismissDataStatus() },
                                onExportCsv = viewModel::exportCsv, onExportXlsx = viewModel::exportXlsx, onExportFinancialYear = viewModel::exportFinancialYear,
                                autoExport = s.autoExport, onAutoFolder = viewModel::setAutoExportFolder, onAutoFrequency = viewModel::setAutoExportFrequency, onAutoNow = viewModel::exportNow, onAutoProtect = viewModel::setAutoProtect, onDeleteOldExcel = viewModel::deleteOldAutoExcel, onBackup = viewModel::createBackup, onOpenBackup = viewModel::openBackup, onImportStatement = viewModel::startImport,
                                onUnlock = viewModel::unlockBackup, onRestore = viewModel::confirmRestore, onCancelRestore = viewModel::cancelRestore,
                                onShown = viewModel::loadBackupFiles, onIncludeFiles = viewModel::setIncludeBackupFiles, autoRunning = autoRunning,
                                canShareBackup = host.canShareFiles, onShareBackup = { name, pass -> viewModel.shareBackup(name, pass) { n, b -> host.shareFile(n, "application/octet-stream", b) } },
                            )
                        }
                    }
                    k == 18 -> {
                        BackHandler { alerts = false }
                        NotificationSettingsScreen(prefs = s.notifications, onChange = viewModel::updateNotificationPrefs, onSendTest = viewModel::sendTestNotification, onBack = { alerts = false })
                    }
                    k == 19 -> {
                        BackHandler { repeating = false }
                
                        RecurringScreen(
                            due = s.due, hiddenOlder = s.hiddenOlder, items = s.recurring, categories = s.categories, accounts = s.accounts,
                            today = todayLocal(),
                            onBack = { repeating = false },
                            onPost = { d, amount -> viewModel.postDue(d.itemId, d.epochDay, amount) },
                            onSkip = { d -> viewModel.skipDue(d.itemId, d.epochDay) },
                            onSave = viewModel::saveRecurring,
                            onDelete = viewModel::deleteRecurring,
                            sinkingFunds = s.sinkingFunds, onSinkingFunds = viewModel::setSinkingFunds,
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
                            onRollover = viewModel::setBudgetRollover,
                        )
                    }
                    k == 21 -> {
                        BackHandler { adding = false }
                        EntryFormScreen(
                            title = "Add",
                            attachments = stagedList, attachmentProblem = attachmentProblem, onAttach = viewModel::stageAttachment,
                            onOpenAttachment = { a -> host.openAttachment(viewModel.attachmentUri(a.id), a.mime) },
                            onRemoveAttachment = { a -> viewModel.unstageAttachment(a.id) },
                            start = prefillTransfer?.split('|')?.let { p -> EntryDraft(null, DraftKind.TRANSFER, p[2], "", emptyList(), p[0], p[1], p.getOrElse(3) { "" }, nowMillis()) } ?: prefillLoan?.split('|')?.let { p -> EntryDraft(null, DraftKind.LOAN, p[2], "", emptyList(), s.lastAccountId, "", "", nowMillis(), personId = p[0], gave = p[1] == "1") } ?: EntryDraft(null, DraftKind.EXPENSE, "", prefillCategory ?: s.lastCategory, emptyList(), prefillAccount ?: s.lastAccountId, "", prefillNote, nowMillis()),
                            kinds = listOf(DraftKind.EXPENSE, DraftKind.INCOME, DraftKind.TRANSFER, DraftKind.LOAN),
                            accounts = s.accounts,
                            categories = s.categories,
                            onCreateCategory = { name, parent, kind, done -> viewModel.createCategory(name, parent, null, kind, onCreated = done) },
                            showTemplateOption = true,
                            lastExpenseCategory = s.lastCategory, lastIncomeCategory = s.lastIncomeCategory,
                            allTags = s.tags,
                            people = people, onCreatePerson = { name, done -> viewModel.createPerson(name, done) },
                            lastRates = foreignRates,
                            tripWindows = s.tripWindows,
                            onSave = { d, asTemplate -> viewModel.saveEntry(d, asTemplate); adding = false; prefillLoan = null; prefillTransfer = null },
                            onSaveEach = { drafts, photo -> viewModel.saveEntries(drafts, photo); adding = false; prefillLoan = null; prefillTransfer = null },
                            onBack = { adding = false; prefillLoan = null; prefillTransfer = null },
                        )
                    }
                    k == 23 -> {
                        BackHandler { billsOpen = false; viewModel.watchBills(false) }
                        BillsScreen(
                            ui = billsUi, accounts = s.accounts, onBack = { billsOpen = false; viewModel.watchBills(false) },
                            onAdd = viewModel::trackBill, onSkip = viewModel::dismissBill, onSetDue = viewModel::setBillDue, onUnpay = viewModel::unpayBill, onPay = viewModel::payBill,
                            onPostRepeat = viewModel::postDue, onSkipRepeat = viewModel::skipDue,
                            undo = billUndo, onUndo = viewModel::undoDismissBill, onUndoGone = viewModel::billUndoGone, onRepair = viewModel::repairBillPaymentTexts,
                            onSetUpCapture = { billsOpen = false; viewModel.watchBills(false); captureOpen = true; viewModel.loadCaptureSettings() },
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
                            accounts = s.accounts,
                            onGotItBack = viewModel::gotItBack, onNotExpecting = viewModel::notExpectingBack,
                            undo = waitingUndo, onUndo = viewModel::undoWaiting, onUndoGone = viewModel::waitingUndoGone,
                        )
                    }
                    else -> EntryListScreen(
                        state = s,
                        onAdd = { platform.trace("add: tapped"); prefillCategory = null; prefillAccount = null; prefillNote = ""; adding = true },
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
                        onFilterAccount = viewModel::showAccountOnHome,
                        onOpenBudget = { budgeting = true },
                        onOpenRecurring = { repeating = true },
                        onOpenNotifications = { alerts = true },
                        onOpenData = { yourData = true },
                        onOpenReports = { reporting = true; viewModel.openReports() },
                        onOpenCalendar = { calendaring = true; viewModel.openCalendar() },
                        onOpenSubscriptions = { subscribing = true; viewModel.openSubscriptions() },
                        onOpenTransferCandidates = { transferring = true; viewModel.openTransferCandidates() },
                        onOpenGoals = { goaling = true; viewModel.openGoals() },
                        onOpenTrips = { tripsOpen = true; viewModel.openTrips() },
                        onOpenTrip = { id -> tripSummaryOf = id; viewModel.openTripSummary(id) },
                        onOpenLoans = { loansOpen = true; viewModel.watchLoans(true) },
                        onOpenBills = { billsOpen = true; viewModel.watchBills(true) },
                        onOpenGuide = viewModel::openGuide,
                        tourMore = if (guide != null) tourStep?.target == app.expenses.core.domain.GuideTarget.MORE else if (wantMore) true else null,
                        onMoreShown = { wantMore = false },
                        moreOpen = moreOpen, onMoreChange = { moreOpen = it },
                        onCustomiseHome = { customisingHome = true },
                        onOpenAppearance = { appearing = true },
                        onOpenSearch = { searchAccount = null; searchTag = null; searchFrom = null; searchTo = null; searching = true },
                        onSearchAccount = { id -> searchAccount = id; searchTag = null; searchFrom = null; searchTo = null; searching = true },
                        onSetHomePeriod = viewModel::setHomePeriod,
                        onOpenSecurity = { securing = true },
                        onDismissWelcome = viewModel::dismissWelcome,
                        onSnoozeBackupNudge = viewModel::snoozeBackupNudge,
                        onOpenCategorise = { categorising = true; viewModel.openCategorise() },
                        onSnoozeCategorise = viewModel::snoozeCategoriseNudge,
                        onSnoozeNudge = viewModel::snoozeNudge,
                        onDismissFirstWeek = viewModel::dismissFirstWeek,
                        onPostDue = { d, amount -> viewModel.postDue(d.itemId, d.epochDay, amount) },
                        onSkipDue = { d -> viewModel.skipDue(d.itemId, d.epochDay) },
                        onBulkCategory = viewModel::bulkSetCategory,
                        onBulkAccount = viewModel::bulkSetAccount,
                        onBulkDelete = viewModel::bulkDelete,
                        onBulkAddTag = viewModel::bulkAddTag,
                        onBulkRemoveTag = viewModel::bulkRemoveTag,
                        onBulkTransfer = viewModel::bulkMakeTransfers,
                        onToggleHideAmounts = { viewModel.setHideAmounts(!s.hideAmounts) },
                        onSafeToSpendOnHome = viewModel::setSafeToSpendOnHome,
                        onOpenCurrency = { currencyOpen = true; viewModel.loadCurrencyBlocker() },
                        onOpenInbox = { inboxOpen = true; viewModel.loadInbox() },
                        onOpenCapture = { captureOpen = true; viewModel.loadCaptureSettings() },
                        onOpenPrivacy = { privacyOpen = true },
                        onOpenSetup = { openSetup() }, onHideSetup = viewModel::hideSetupCard,
                        onOpenCheckup = { checkupOpen = true }, onOpenEarlier = { earlierOpen = true },
                    )
                } }
                }
                }
                }
            }
            }
            }
        }
    }
}

private fun colorSchemeFor(look: Appearance, dark: Boolean): androidx.compose.material3.ColorScheme {
    fun c(rgb: Int) = androidx.compose.ui.graphics.Color(0xFF000000.toInt() or rgb)
    val primary = Appearances.primaryFor(look.accent, dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val container = Appearances.containerFor(primary, dark)
    return base.copy(primary = c(primary), onPrimary = c(Appearances.onColor(primary)), primaryContainer = c(container), onPrimaryContainer = c(Appearances.onColor(container)), secondary = c(primary), secondaryContainer = c(container), onSecondaryContainer = c(Appearances.onColor(container)))
}
