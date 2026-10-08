package app.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.BackupNudge
import app.expenses.core.domain.Csv
import app.expenses.core.domain.LastBackup
import app.expenses.core.domain.Flow
import app.expenses.core.domain.ParsedStatement
import app.expenses.core.domain.ReportMath
import app.expenses.core.domain.StatementDetector
import app.expenses.core.domain.StatementMapping
import app.expenses.core.domain.StatementParser
import app.expenses.core.domain.parseAmount
import app.expenses.core.storage.SearchQuery
import app.expenses.core.storage.searchFiltered
import kotlinx.datetime.plus
import app.expenses.core.storage.DrillTarget
import app.expenses.core.storage.ReportGroup
import kotlinx.datetime.minus
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.Template
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.AccountRules
import app.expenses.core.domain.Appearance
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BaseCurrencyRules
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.CardText
import app.expenses.core.domain.CurrencyChoices
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.AttachmentRules
import app.expenses.core.domain.ImportProfiles
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.storage.SavedSearch
import app.expenses.core.storage.resolveCategory
import app.expenses.core.domain.HomeLayout
import app.expenses.core.domain.HomeSection
import app.expenses.core.storage.DrillLine
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.compact
import app.expenses.core.domain.BillOnDay
import app.expenses.core.domain.CalendarMath
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.OwnExport
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.storage.BackupData
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.BudgetStatus
import app.expenses.core.storage.CategoryStore
import app.expenses.core.storage.bulkLoad
import app.expenses.core.storage.db.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class RowUi(val seq: Long, val id: String, val title: String, val subtitle: String, val amount: String,
    /** Money coming in (income, a refund): shown with a plus sign so it is not mistaken for spending. */
    val inflow: Boolean,
)
data class AccountUi(val id: String, val name: String)

/**
 * An account as the accounts screen needs it. [openingText] is what the form shows: for a card or loan it is the amount owed, as a
 * positive number. [usage] is how many entries and saved items refer to it (zero means it can be deleted).
 */
data class AccountRowUi(
    val id: String, val name: String, val type: String, val typeLabel: String, val isLiability: Boolean, val archived: Boolean,
    val balanceText: String, val balanceMinor: Long, val owed: Boolean, val openingText: String, val usage: Long,
    /** Repeating items and quick entries that post to it: while there are any, it cannot be archived. */
    val savedItems: Long = 0,
    /** Billing dates of a credit card, when set. */
    val card: CardRowUi? = null,
)

/** A card's billing dates and where its latest statement stands, as the accounts screen shows them. */
data class CardRowUi(val statementDay: Int, val dueDay: Int, val minDuePercent: Int, val remindDaysBefore: Int, val termsText: String, val statusText: String)

val ACCOUNT_TYPE_LABELS = linkedMapOf("BANK" to "Bank", "CASH" to "Cash", "WALLET" to "Wallet", "CREDIT_CARD" to "Credit card", "LOAN" to "Loan")

/** A category as the screens need it. [usage] is how many items refer to it (needed to decide whether it can be deleted). */
data class CategoryUi(val id: String, val name: String, val parentId: String?, val colorArgb: Long?, val hidden: Boolean, val usage: Long)

/** An attachment as the editor lists it. */
data class AttachmentUi(val id: String, val name: String, val sizeText: String, val mime: String)

/** Automatic export to the user's own folder, as the Data screen shows it. [status] is the last outcome. */
data class AutoExportUi(val frequency: AutoExportFrequency, val folderName: String?, val lastText: String?, val status: String?, val statusOk: Boolean)

/** A saved quick entry as the UI needs it. [amountText] is null when the template asks for the amount. */
data class TemplateUi(val id: String, val name: String, val amountText: String?, val category: String, val accountId: String, val note: String)

/**
 * One budget as the screens show it. A null [limit] means no limit is set (the line is still listed so one can be added).
 * [percent] is the share of the limit used, whole percent, and can pass 100.
 */
data class BudgetLineUi(
    val categoryId: String?,
    val name: String,
    val limit: String?,
    /** The limit as plain digits for the edit field, or empty. */
    val limitPlain: String,
    val spent: String,
    /** "₹2,000.00 left" or "₹150.00 over"; empty without a limit. */
    val note: String,
    val percent: Int,
    val state: BudgetState,
)

/** Budget state for the current period. [perDay] is null without an overall limit. */
data class BudgetUi(
    val period: BudgetPeriod,
    /** "1 Oct – 31 Oct". */
    val range: String,
    /** What the period is called in headings: "this month", "this cycle" or "this week". */
    val periodName: String,
    val overall: BudgetLineUi,
    val categories: List<BudgetLineUi>,
    val perDay: String?,
    /** Bills still to come this period, or null when there are none (or no limit). */
    val upcoming: String?,
    /** True when the bills still to come use up everything left under the limit. */
    val billsUseItAll: Boolean,
    val daysLeft: Int,
    /** How many category limits are near or over. */
    val categoriesNeedingAttention: Int,
)

/** A repeating expense waiting to be posted or skipped. [amount] is null when the amount differs each time and must be asked for. */
data class DueUi(val itemId: String, val epochDay: Long, val name: String, val amount: String?, val whenText: String, val overdue: Boolean)

/** A repeating expense as the editor needs it. [amountPlain] is empty when the amount is asked each time. */
data class RecurringUi(
    val id: String,
    val name: String,
    val amountPlain: String,
    val categoryId: String,
    val accountId: String,
    val merchant: String,
    val schedule: Schedule,
    val auto: Boolean,
    val kind: RecurringKind,
    val toAccountId: String,
    /** "₹15,000.00 · every month · next 1 Nov". */
    val summary: String,
)

/** Shown as "Added ₹20 · Undo" after an entry is recorded in one step. */
/** [restore] is true for a deletion: undoing it brings the entry back instead of removing one. */
data class UndoUi(
    val entryId: String, val text: String, val restore: Boolean = false, val recurringId: String? = null, val recurringDay: Long = 0,
    /** For a bulk edit: the earlier versions to put back; for a bulk delete: the ids to bring back, in order. */
    val bulkDrafts: List<EntryDraft> = emptyList(), val bulkRestore: List<String> = emptyList(),
)

/** One transaction found in a statement, as shown in the preview. */
data class ImportRowUi(val text: String, val amount: String, val spending: Boolean)

/** What an import did, for the screen. */
data class ImportResultUi(val imported: Int, val alreadyThere: Int, val spending: String, val income: String)

/** State of the statement import. [mapping] is null until a file is chosen. Nothing is saved until the user confirms. */
data class ImportUi(
    val active: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val columnNames: List<String> = emptyList(),
    val mapping: StatementMapping? = null,
    val accountId: String = "",
    val dateAmbiguous: Boolean = false,
    val damaged: Boolean = false,
    val rowCount: Int = 0,
    val preview: List<ImportRowUi> = emptyList(),
    val txnCount: Int = 0,
    val spending: String = "",
    val income: String = "",
    val problems: List<String> = emptyList(),
    val problemCount: Int = 0,
    val ignoredRows: Int = 0,
    val mappingProblem: String? = null,
    /** Set when the layout was not guessed but remembered from an earlier file with the same column titles. */
    val profileNote: String? = null,
    val result: ImportResultUi? = null,
)

/** A search kept under a name, ready to apply. */
data class SavedSearchUi(val id: String, val name: String, val input: SearchInput)

enum class SearchKind { ANY, EXPENSE, INCOME }
enum class SearchRange { ANY, LAST_30_DAYS, THIS_PERIOD, THIS_YEAR }

/** What the user has typed and chosen on the search screen. Amounts are rupees as typed ("250", "99.50"); empty means no limit. */
data class SearchInput(
    val text: String = "",
    val minRupees: String = "",
    val maxRupees: String = "",
    val categoryId: String? = null,
    val kind: SearchKind = SearchKind.ANY,
    val range: SearchRange = SearchRange.ANY,
    val tag: String? = null,
)

/** [problem] explains input that cannot be searched; [hasConditions] is false until something is typed or chosen. */
data class SearchUi(
    val results: List<RowUi> = emptyList(),
    val searching: Boolean = false,
    val hasConditions: Boolean = false,
    val truncated: Boolean = false,
    val problem: String? = null,
    /** What the last bulk change from here did, with a way to take it back. */
    val notice: String? = null,
    val undo: UndoUi? = null,
)

/** One day cell of the calendar. [spent] is a short label (empty when nothing was spent). */
data class CalendarCellUi(val day: Int, val epochDay: Long, val spent: String, val spentMinor: Long, val bills: Int, val level: Int, val isToday: Boolean)

/** What the calendar shows: the month grid, its total, and the picked day's entries and bills. */
data class CalendarUi(
    val label: String, val offset: Int, val blanks: Int, val cells: List<CalendarCellUi>, val total: String,
    val selectedEpochDay: Long?, val dayTitle: String, val dayTotal: String, val dayLines: List<DrillLine>, val dayBills: List<String>,
)

enum class ReportMode { PERIOD, YEAR, FINANCIAL_YEAR, CUSTOM }

/** A row of a report grouped by account, tag or merchant. [key] is what drilling in needs (null: entries with no tag). */
data class GroupRowUi(val key: String?, val name: String, val amount: String, val share: Int, val negative: Boolean)

data class ReportRowUi(val categoryId: String?, val name: String, val amount: String, val share: Int, val colorArgb: Long?, val negative: Boolean, val children: List<ReportRowUi> = emptyList())

/** One bar of the trend. [fraction] is its height relative to the tallest bar (0 to 1). Tapping it opens the entries of that period. */
data class TrendBarUi(val label: String, val amount: String, val fraction: Float, val isSelected: Boolean, val startDay: Long, val endDay: Long)

data class DrillLineUi(val entryId: String, val date: String, val title: String, val category: String, val amount: String, val negative: Boolean)

/** The entries behind a figure. [total] is the sum of [lines], shown so the list visibly adds up to what was tapped. */
data class DrillUi(val title: String, val subtitle: String, val total: String, val lines: List<DrillLineUi>, val truncated: Boolean)

data class ReportUi(
    val mode: ReportMode,
    val title: String,
    val canGoNext: Boolean,
    val spent: String,
    val income: String,
    val net: String,
    val netPositive: Boolean,
    /** "12% more than the period before" or null when there is nothing to compare with. */
    val comparison: String?,
    val rows: List<ReportRowUi>,
    val trend: List<TrendBarUi>,
    val drill: DrillUi?,
    val group: ReportGroup = ReportGroup.CATEGORY,
    val groupRows: List<GroupRowUi> = emptyList(),
    /** The custom range as days since 1970-01-01, the end exclusive; set in the custom mode. */
    val customStartDay: Long = 0, val customEndDay: Long = 0,
)

/** A backup file that was opened and checked, waiting for the user to confirm the restore. */
data class PendingRestoreUi(val madeAt: String, val entriesInFile: Int, val newEntries: Int, val alreadyHereEntries: Int, val otherNewItems: Int)

/** State of the Data screen. [status] is the outcome of the last action; [needsPassphrase] asks for the passphrase of an encrypted backup. */
data class DataUi(
    val busy: Boolean = false,
    val status: String? = null,
    val statusIsError: Boolean = false,
    val needsPassphrase: Boolean = false,
    val pending: PendingRestoreUi? = null,
)

sealed interface UiState {
    data object Opening : UiState

    /** The database exists but its key is gone; the user must choose to start fresh or restore. */
    data class KeyLost(val message: String) : UiState

    data class Ready(
        val rows: List<RowUi>,
        /** Spending in the current budget period (the calendar month unless the user chose otherwise). */
        val periodSpend: String,
        val budget: BudgetUi,
        val due: List<DueUi>,
        /** Occurrences older than the due window that nobody dealt with; mentioned so they do not vanish silently. */
        val hiddenOlder: Int,
        /** True on a fresh install where nothing has ever been recorded, until the user adds something or skips it. */
        val showWelcome: Boolean,
        /** True when it is time to remind the user to make a backup. */
        val showBackupNudge: Boolean,
        val notifications: NotificationPrefs,
        val recurring: List<RecurringUi>,
        val accounts: List<AccountUi>,
        /** Every account for the accounts screen, archived ones included, with balances. */
        val accountRows: List<AccountRowUi>,
        /** Everything an old entry might refer to (archived accounts included), for showing and editing it. */
        val allAccounts: List<AccountUi>,
        val templates: List<TemplateUi>,
        val homeLayout: HomeLayout,
        val appearance: Appearance,
        val baseCurrency: Currency,
        val autoExport: AutoExportUi,
        /** "Hide amounts" is on: every amount on screen is hidden. */
        val hideAmounts: Boolean,
        val savedSearches: List<SavedSearchUi>,
        val categories: List<CategoryUi>,
        /** Tags in use, most used first, to offer when tagging. */
        val tags: List<String>,
        /** What the add screen starts with: the category and account used last. */
        val lastCategory: String,
        val lastAccountId: String,
        val endReached: Boolean,
        val message: String? = null,
        val undo: UndoUi? = null,
    ) : UiState
}

/** The ids of the default categories, used only to give seeded test data realistic categories. */
val DEFAULT_CATEGORIES = CategoryStore.DEFAULTS

private const val PAGE = 50L
private const val SEARCH_LIMIT = 100L
private const val TAG_SUGGESTIONS = 40L
private const val PROFILE_SEARCH_ROWS = 15
private const val BALANCES_DEBOUNCE_MS = 1_500L
private const val SETTING_HOME_LAYOUT = "home_layout"
private const val SETTING_HIDE_AMOUNTS = "hide_amounts"
private const val SETTING_APPEARANCE = "appearance"
private const val SETTING_WELCOME_DISMISSED = "welcome_dismissed"
private const val SETTING_NUDGE_SNOOZED_AT = "backup_nudge_snoozed_at"
private const val MAX_STATEMENT_BYTES = 20_000_000
private const val MAX_STATEMENT_ROWS = 200_000
private const val PREVIEW_ROWS = 6
private const val PROBLEMS_SHOWN = 20
private const val SEARCH_DEBOUNCE_MS = 200L
private const val LAST_CATEGORY = "last_category"
private const val LAST_ACCOUNT = "last_account"

/**
 * Screen state for the ledger. The database lives in [AppContainer]; every read and write here runs off the main
 * thread, and writes are serialised so rapid taps cannot overlap. New rows are requested with keyset paging.
 */
class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val inr get() = BaseCurrency.current
    private val container = (app as ExpensesApp).container
    private val _state = MutableStateFlow<UiState>(UiState.Opening)
    val state: StateFlow<UiState> = _state

    private val _editor = MutableStateFlow<Editable?>(null)

    /** The entry being edited, or null. */
    val editor: StateFlow<Editable?> = _editor

    private val _data = MutableStateFlow(DataUi())
    val data: StateFlow<DataUi> = _data
    private var openedBytes: ByteArray? = null
    private var openedBackup: BackupData? = null
    private val actions get() = DataActions(getApplication(), container)

    private val _import = MutableStateFlow(ImportUi())
    val statement: StateFlow<ImportUi> = _import
    private var importTable: app.expenses.core.domain.CsvTable? = null
    private var importParsed: ParsedStatement? = null

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search
    private var searchInput: SearchInput? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    private data class ReportRequest(
        val mode: ReportMode, val offset: Int, val drillTarget: DrillTarget? = null, val drillStart: Long? = null, val drillEnd: Long? = null,
        val group: ReportGroup = ReportGroup.CATEGORY, val customStart: Long? = null, val customEnd: Long? = null, val drillLabel: String? = null,
    )
    private var reportRequest: ReportRequest? = null
    private val _report = MutableStateFlow<ReportUi?>(null)
    val report: StateFlow<ReportUi?> = _report

    private var accountNames = emptyMap<String, String>()
    private var currencyDigits = emptyMap<String, Int>()
    private var categoryNames = emptyMap<String, String>()
    private var loadedCategory = emptyMap<String, String>()
    private var loaded = mutableListOf<Entry>()
    private var endReached = false

    /** Last resort after key loss, only on the user's explicit choice: deletes all data. */
    fun startFresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UiState.Opening
            when (val o = container.startFresh()) {
                is AppContainer.Opened.Ok -> container.write { ok -> refresh(ok) }
                is AppContainer.Opened.KeyLost -> _state.value = UiState.KeyLost(o.message)
            }
        }
    }

    /**
     * Saves the add or edit form: a new entry when [EntryDraft.id] is null, otherwise an edit. A new expense can also become a quick entry.
     * On a rule it breaks, the reason is shown and the form is left as it was (the editor stays open).
     */
    fun saveEntry(draft: EntryDraft, saveAsTemplate: Boolean) = withStore { ok ->
        val startedAt = System.nanoTime()
        val id = EntryActions(ok).save(draft)
        val amount = parseAmount(draft.amountText, inr)!!
        if (saveAsTemplate && draft.kind == DraftKind.EXPENSE && draft.id == null && !EntryDrafts.isSplit(draft)) {
            val name = draft.note.trim().ifEmpty { draft.category }
            ok.templates.save(Template(UUID.randomUUID().toString(), name, amount, CategoryId(draft.category), AccountId(draft.accountId), draft.note.trim().ifEmpty { null }))
        }
        _editor.value = null
        val verb = when (draft.kind) { DraftKind.EXPENSE -> "expense"; DraftKind.INCOME -> "income"; DraftKind.TRANSFER -> "transfer"; DraftKind.REFUND -> "refund" }
        refreshQuickly(ok, undo = if (draft.id == null) UndoUi(id, "Added $verb ${amount.formatted()}") else null)
        lastSaveMillis = (System.nanoTime() - startedAt) / 1e6
    }

    /** Opens the refund form for an expense. */
    fun startRefund(expenseId: String) = withStore { ok ->
        _editor.value = EntryActions(ok).startRefund(expenseId)
    }

    /** One tap: records the template's expense right away. Templates without an amount are handled by the add screen. */
    fun quickAdd(templateId: String) = withStore { ok ->
        val added = EntryActions(ok).quickAdd(templateId) ?: return@withStore
        refresh(ok, undo = UndoUi(added.entryId, added.text))
    }

    fun deleteTemplate(templateId: String) = withStore { ok ->
        ok.templates.delete(templateId)
        refresh(ok)
    }

    /** Takes back an entry that was just added. */
    fun undo(u: UndoUi) = withStore { ok ->
        if (u.bulkDrafts.isNotEmpty()) ok.store.atomically { u.bulkDrafts.forEach { EntryActions(ok).save(it) } }
        else if (u.bulkRestore.isNotEmpty()) ok.store.atomically { u.bulkRestore.forEach { EntryActions(ok).restore(it) } }
        else if (u.recurringId != null) ok.recurring.undo(u.recurringId, kotlinx.datetime.LocalDate.fromEpochDays(u.recurringDay))
        else if (u.restore) EntryActions(ok).restore(u.entryId) else EntryActions(ok).undo(u.entryId)
        refresh(ok)
        _search.update { it.copy(notice = null, undo = null) }
    }

    /** Opens the editor for an entry: the simple form, or a read-only explanation. */
    fun openEditor(entryId: String) = withStore { ok ->
        _editor.value = EntryActions(ok).editable(entryId)
        _attachmentProblem.value = null
        _attachments.value = attachmentsOf(ok, entryId)
    }

    // ---- attachments ----

    private val attachmentFiles by lazy { AttachmentFiles(getApplication()) }
    private val _attachments = MutableStateFlow<List<AttachmentUi>>(emptyList())

    private val _attachmentProblem = MutableStateFlow<String?>(null)

    /** Why the last attachment was refused, shown in the editor itself (the list's message bar is hidden behind it). */
    val attachmentProblem: StateFlow<String?> get() = _attachmentProblem

    /** The attachments of the entry being edited. */
    val attachments: StateFlow<List<AttachmentUi>> get() = _attachments

    private fun attachmentsOf(ok: AppContainer.Opened.Ok, entryId: String) = ok.attachments.list(entryId).map { AttachmentUi(it.id, it.name, AttachmentRules.sizeText(it.sizeBytes), it.mime) }

    /** Copies a picked photo or file in and attaches it to [entryId]. A refusal is shown as a message and leaves nothing behind. */
    fun addAttachment(entryId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            _attachmentProblem.value = null
            try {
                val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
                val name = getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                val copied = attachmentFiles.copyIn(id, uri)
                container.write { ok ->
                    ok.attachments.add(id, entryId, name ?: uri.lastPathSegment ?: "attachment", mime, copied.sizeBytes, copied.sha256)
                    _attachments.value = attachmentsOf(ok, entryId)
                }
            } catch (e: Exception) { // any failure: the copy is removed and the reason shown, never an app crash from a background coroutine
                attachmentFiles.delete(id)
                _attachmentProblem.value = if (e is IllegalArgumentException) e.message else "Could not attach that file: ${e.message ?: e::class.java.simpleName}"
            }
        }
    }

    fun removeAttachment(entryId: String, id: String) = withStore { ok ->
        ok.attachments.delete(id)
        attachmentFiles.delete(id)
        _attachments.value = attachmentsOf(ok, entryId)
    }

    /** The address to hand to a viewer app for one attachment. */
    fun attachmentUri(id: String): Uri = attachmentFiles.shareUri(id)

    fun closeEditor() {
        _editor.value = null
        _attachments.value = emptyList()
        _history.value = null
    }

    /** The history of the entry being edited, or null when not shown. */
    val history: StateFlow<HistoryUi?> get() = _history
    private val _history = MutableStateFlow<HistoryUi?>(null)

    fun openHistory(entryId: String) = withStore { ok -> _history.value = EntryActions(ok).history(entryId) }
    fun closeHistory() { _history.value = null }

    /** Puts back the version before an edit, then returns to the list. */
    fun restoreVersion(entryId: String, index: Int) = withStore { ok ->
        EntryActions(ok).restoreVersion(entryId, index)
        _history.value = null
        _editor.value = null
        refresh(ok, undo = null)
    }

    // ---- bulk edit ----

    /** Applies a bulk change and reports it: a snackbar on the list and a notice on the search screen, both with Undo. */
    private fun bulk(what: String, run: (EntryActions) -> EntryActions.BulkResult) = withStore { ok ->
        val r = run(EntryActions(ok))
        val undo = if (r.undoDrafts.isNotEmpty() || r.undoRestore.isNotEmpty()) UndoUi("", r.text(what), bulkDrafts = r.undoDrafts, bulkRestore = r.undoRestore) else null
        refresh(ok, undo = undo)
        _search.update { it.copy(notice = r.text(what), undo = undo) }
    }

    fun bulkSetCategory(ids: List<String>, category: String) = bulk("Changed the category of") { it.bulkSetCategory(ids, category) }
    fun bulkSetAccount(ids: List<String>, account: String) = bulk("Moved") { it.bulkSetAccount(ids, account) }
    fun bulkAddTag(ids: List<String>, tag: String) = bulk("Tagged") { it.bulkAddTag(ids, tag) }
    fun bulkRemoveTag(ids: List<String>, tag: String) = bulk("Removed the tag from") { it.bulkRemoveTag(ids, tag) }
    fun bulkDelete(ids: List<String>) = bulk("Deleted") { it.bulkDelete(ids) }

    /** Deletes an entry and offers to bring it back. */
    fun deleteEntry(row: RowUi) = withStore { ok ->
        EntryActions(ok).delete(row.id)
        refresh(ok, undo = UndoUi(row.id, "Deleted ${row.title} ${row.amount}", restore = true))
    }

    fun loadMore() {
        val current = _state.value as? UiState.Ready ?: return
        if (endReached) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = container.open() as? AppContainer.Opened.Ok ?: return@launch
            val last = loaded.lastOrNull() ?: return@launch
            val page = ok.db.schemaQueries.entriesBefore(last.occurred_at, last.seq, PAGE).executeAsList()
            if (page.size < PAGE) endReached = true
            if (page.isEmpty()) return@launch
            loaded.addAll(page)
            loadedCategory = loadedCategory + firstCategories(ok, page)
            _state.update { s -> (s as? UiState.Ready)?.copy(rows = s.rows + page.map(::toRow), endReached = endReached) ?: current }
        }
    }

    // ---- categories ----

    /** Creates a category and, once saved, tells the caller its id (used to select it in the add form). */
    fun createCategory(name: String, parentId: String?, colorArgb: Long?, onCreated: (String) -> Unit = {}) = withStore { ok ->
        val created = ok.categories.create(name, parentId, colorArgb)
        refresh(ok)
        viewModelScope.launch(Dispatchers.Main) { onCreated(created.id) }
    }

    /** Applies all the edits of the category dialog as one change: either every part is saved or none. */
    fun updateCategory(id: String, name: String, colorArgb: Long?, hidden: Boolean, parentId: String?) = withStore { ok ->
        ok.db.transaction {
            ok.categories.rename(id, name)
            ok.categories.setColor(id, colorArgb)
            ok.categories.setHidden(id, hidden)
            ok.categories.setParent(id, parentId)
        }
        refresh(ok)
    }

    fun moveCategory(id: String, direction: Int) = withStore { ok ->
        ok.categories.move(id, direction)
        refresh(ok)
    }

    fun mergeCategory(fromId: String, intoId: String) = withStore { ok ->
        ok.categories.merge(fromId, intoId)
        refresh(ok)
    }

    fun deleteCategory(id: String) = withStore { ok ->
        ok.categories.delete(id)
        refresh(ok)
    }

    // ---- accounts ----

    // Balances read the whole ledger (about 25 ms on a 100,000-entry ledger even with covering indexes), so they are not part of the
    // save path: they are worked out only while something shows them, a moment after the last write, and the screen updates when ready.
    private var accountRowsCache: List<AccountRowUi> = emptyList()
    private var categoryUsageCache: Map<String, Long> = emptyMap()
    private var categoriesOpen = false
    private var nudgeCache = false
    private var figuresReady = false
    private var accountsOpen = false
    private var balancesVisible = false
    private var balancesJob: kotlinx.coroutines.Job? = null

    fun setAccountsScreenOpen(open: Boolean) { accountsOpen = open; if (open) scheduleSlowFigures() }
    fun setCategoriesScreenOpen(open: Boolean) { categoriesOpen = open; if (open) scheduleSlowFigures() }

    /**
     * The two figures that read the whole ledger, the account balances and the live entry count behind the backup reminder, are not part of a
     * write: they are worked out a moment after the last one, and the screen updates when they are ready. Balances are skipped while nothing
     * shows them. (On a 100,000-entry ledger the count alone took 27 ms and the balances 25 ms, against a 50 ms save budget.)
     */
    private fun scheduleSlowFigures() {
        balancesJob?.cancel()
        val wait = if (accountsOpen || categoriesOpen || !figuresReady) 0L else BALANCES_DEBOUNCE_MS
        balancesJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(wait)
            val figures = container.write { ok ->
                val live = ok.db.schemaQueries.entryCount().executeAsOne()
                val nudge = BackupNudge.shouldShow(
                    live,
                    ok.templates.setting(DataActions.SETTING_BACKUP_AT)?.toLongOrNull()?.let { at -> LastBackup(at, ok.templates.setting(DataActions.SETTING_BACKUP_COUNT)?.toLongOrNull() ?: 0L) },
                    ok.templates.setting(SETTING_NUDGE_SNOOZED_AT)?.toLongOrNull(), System.currentTimeMillis(),
                )
                val rows = if (accountsOpen || balancesVisible) accountRows(ok, ok.db.schemaQueries.selectAccounts().executeAsList()) else accountRowsCache
                val usage = if (categoriesOpen) ok.categories.list().associate { it.id to ok.categories.usage(it.id) } else categoryUsageCache
                Triple(nudge, rows, usage)
            } ?: return@launch
            nudgeCache = figures.first; accountRowsCache = figures.second; categoryUsageCache = figures.third; figuresReady = true
            _state.update { st ->
                (st as? UiState.Ready)?.copy(accountRows = figures.second, showBackupNudge = figures.first, categories = st.categories.map { c -> c.copy(usage = figures.third[c.id] ?: c.usage) }) ?: st
            }
        }
    }

    private fun accountRows(ok: AppContainer.Opened.Ok, rows: List<app.expenses.core.storage.db.Account>): List<AccountRowUi> {
        val balances = ok.store.balances()
        val today = java.time.LocalDate.now().let { kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth) }
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        return rows.map { a ->
            val type = AccountType.valueOf(a.type)
            val bal = balances[AccountId(a.id)] ?: Money.zero(inr)
            val shownOpening = if (type.isLiability) -a.opening_minor else a.opening_minor
            AccountRowUi(
                a.id, a.name, a.type, ACCOUNT_TYPE_LABELS.getValue(a.type), type.isLiability, a.archived == 1L,
                bal.formatted(), bal.minorUnits, AccountRules.isOwed(Account(AccountId(a.id), a.name, type, inr), bal.minorUnits),
                if (shownOpening == 0L) "" else Money(shownOpening, inr).toPlainString(), ok.store.accountUsage(AccountId(a.id)),
                ok.db.schemaQueries.accountSavedItemUsage(a.id).executeAsOne(),
                if (type != AccountType.CREDIT_CARD) null else ok.cards.terms(a.id)?.let { t ->
                    CardRowUi(t.statementDay, t.dueDay, t.minDuePercent, t.remindDaysBefore, CardText.terms(t), ok.cards.standing(a.id, today, zone)?.let { CardText.status(it, today) { m -> Money(m, inr).formatted() } }.orEmpty())
                },
            )
        }.sortedWith(compareBy({ it.archived }, { it.name.lowercase() }))
    }

    /**
     * Creates ([id] null) or edits an account. [openingText] is the amount held, or for a card or loan the amount owed. [onDone] gets null
     * on success, otherwise the reason it was refused (shown in the form, which stays open).
     */
    fun saveAccount(id: String?, name: String, type: String, openingText: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val problem = try {
                container.write { ok ->
                    val all = ok.db.schemaQueries.selectAccounts().executeAsList()
                    val clean = AccountRules.normalizeName(name)
                    AccountRules.checkName(all.map { Account(AccountId(it.id), it.name, AccountType.valueOf(it.type), inr) }, clean, id)?.let { return@write it }
                    val kind = AccountType.valueOf(type)
                    val typed = openingText.trim()
                    val opening = if (typed.isEmpty()) 0L else (parseAmount(typed, inr)?.minorUnits ?: return@write "Enter the amount as a number, like 25000 or 1250.50")
                    if (id != null && all.first { it.id == id }.type.let { AccountType.valueOf(it).isLiability } != kind.isLiability &&
                        ok.store.accountUsage(AccountId(id)) > 0) return@write "A card or loan cannot become a bank, cash or wallet account once it has entries, or the other way round."
                    val existing = id?.let { ok.db.schemaQueries.accountById(it).executeAsOneOrNull() }
                    ok.store.saveAccount(Account(AccountId(id ?: UUID.randomUUID().toString()), clean, kind, inr, if (kind.isLiability) -opening else opening, existing?.archived == 1L))
                    if (id != null && kind != AccountType.CREDIT_CARD) ok.cards.clear(id) // billing dates belong to a credit card only
                    refresh(ok)
                    null
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    /** Sets or, with blank days, removes a credit card's billing dates. [onDone] gets null on success, otherwise the reason (shown in the dialog). */
    fun saveCardTerms(accountId: String, statementDay: String, dueDay: String, minPercent: String, remindDays: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val problem = try {
                container.write { ok ->
                    val s = statementDay.trim().toIntOrNull(); val d = dueDay.trim().toIntOrNull()
                    val p = minPercent.trim().ifEmpty { "5" }.toIntOrNull(); val r = remindDays.trim().ifEmpty { "3" }.toIntOrNull()
                    when {
                        s == null || s !in 1..28 -> "The statement day is the day of the month your statement is made, 1 to 28."
                        d == null || d !in 1..28 -> "The due day is the day of the month payment is due, 1 to 28."
                        p == null || p !in 0..100 -> "The minimum due percentage is 0 to 100 (most cards: 5)."
                        r == null || r !in 0..10 -> "Reminders can start 0 to 10 days before the due date."
                        else -> { ok.cards.save(CardTerms(accountId, s, d, p, r)); refresh(ok); null }
                    }
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    fun clearCardTerms(accountId: String) = withStore { ok -> ok.cards.clear(accountId); refresh(ok) }

    fun setAccountArchived(id: String, archived: Boolean) = withStore { ok ->
        val row = ok.db.schemaQueries.accountById(id).executeAsOneOrNull() ?: return@withStore
        require(!archived || ok.db.schemaQueries.accountSavedItemUsage(id).executeAsOne() == 0L) { "Repeating items or quick entries still use this account. Change them first, then archive it." }
        require(!archived || ok.db.schemaQueries.selectAccounts().executeAsList().count { it.archived == 0L && it.id != id } > 0) { "Keep at least one active account." }
        ok.store.saveAccount(Account(AccountId(id), row.name, AccountType.valueOf(row.type), inr, row.opening_minor, archived))
        refresh(ok)
    }

    fun deleteAccount(id: String) = withStore { ok ->
        ok.store.deleteAccount(AccountId(id))
        refresh(ok)
    }

    // ---- saved searches ----

    /** Keeps the current search under [name]. [onDone] gets null on success or the reason it was refused (shown in the dialog, which stays open). */
    fun saveSearch(name: String, input: SearchInput, onDone: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val problem = try {
                container.write { ok ->
                    ok.savedSearches.save(SavedSearch(UUID.randomUUID().toString(), name, input.text, input.minRupees, input.maxRupees, input.categoryId, input.kind.name, input.range.name, input.tag))
                    refresh(ok)
                }
                null
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    fun deleteSavedSearch(id: String) = withStore { ok -> ok.savedSearches.delete(id); refresh(ok) }

    // ---- automatic export ----

    private fun autoExportUi(ok: AppContainer.Opened.Ok): AutoExportUi {
        val t = ok.templates
        val freq = AutoExportFrequency.entries.firstOrNull { it.name == t.setting(AutoExport.S_FREQ) } ?: AutoExportFrequency.OFF
        val folder = t.setting(AutoExport.S_FOLDER)?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':')?.ifEmpty { null } ?: "the chosen folder" }
        val last = t.setting(AutoExport.S_LAST_AT)?.toLongOrNull()?.let { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) }
        val status = t.setting(AutoExport.S_STATUS)
        return AutoExportUi(freq, folder, last, status?.substringAfter('|')?.ifEmpty { null }, status?.startsWith("ok|") ?: true)
    }

    fun setAutoExportFolder(uri: Uri) = withStore { ok ->
        ok.templates.putSetting(AutoExport.S_FOLDER, uri.toString())
        if (ok.templates.setting(AutoExport.S_FREQ) == null || ok.templates.setting(AutoExport.S_FREQ) == AutoExportFrequency.OFF.name) ok.templates.putSetting(AutoExport.S_FREQ, AutoExportFrequency.WEEKLY.name)
        refresh(ok)
    }

    fun setAutoExportFrequency(f: AutoExportFrequency) = withStore { ok -> ok.templates.putSetting(AutoExport.S_FREQ, f.name); refresh(ok) }

    /** Runs an export now, whatever the schedule says, and shows how it went. */
    fun exportNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = container.write { it.templates.setting(AutoExport.S_FOLDER) }
            AutoExport.run(container, AutoExport.folderOf(getApplication(), uri), System.currentTimeMillis(), force = true)
            container.write { refresh(it) }
        }
    }

    // ---- currency ----

    private val _currencyBlocker = MutableStateFlow<String?>(null)

    /** Why the currency cannot be changed right now, or null when it can. Loaded when the currency screen opens. */
    val currencyBlocker: StateFlow<String?> get() = _currencyBlocker

    fun loadCurrencyBlocker() = withStore { ok -> _currencyBlocker.value = BaseCurrencyRules.blocker(ok.currency.facts()) }

    fun setBaseCurrency(code: String) = withStore { ok ->
        val refusal = ok.currency.change(CurrencyChoices.byCode(code))
        BaseCurrency.current = ok.currency.current()
        _currencyBlocker.value = refusal ?: BaseCurrencyRules.blocker(ok.currency.facts())
        refresh(ok)
    }

    // ---- appearance ----

    fun setAppearance(a: Appearance) = withStore { ok -> ok.templates.putSetting(SETTING_APPEARANCE, a.encode()); refresh(ok) }

    // ---- privacy ----

    fun setHideAmounts(hide: Boolean) = withStore { ok ->
        ok.templates.putSetting(SETTING_HIDE_AMOUNTS, if (hide) "1" else "0")
        refresh(ok)
    }

    // ---- home layout ----

    /** Saves which home blocks are shown and in what order; takes effect at once. */
    fun setHomeLayout(layout: HomeLayout) = withStore { ok ->
        ok.templates.putSetting(SETTING_HOME_LAYOUT, layout.encode())
        refresh(ok)
    }

    // ---- first run and reminders ----

    /** "Skip" on the first-run card: it is not shown again. */
    fun dismissWelcome() = withStore { ok ->
        ok.templates.putSetting(SETTING_WELCOME_DISMISSED, "1")
        refresh(ok)
    }

    /** "Later" on the backup reminder: quiet for [BackupNudge.SNOOZE_DAYS] days. */
    fun snoozeBackupNudge() = withStore { ok ->
        ok.templates.putSetting(SETTING_NUDGE_SNOOZED_AT, System.currentTimeMillis().toString())
        refresh(ok)
    }

    // ---- repeating expenses ----

    /** Posts a due occurrence. [amount] is required only for items whose amount differs each time. */
    fun postDue(itemId: String, epochDay: Long, amount: Money?) = withStore { ok ->
        val day = kotlinx.datetime.LocalDate.fromEpochDays(epochDay)
        val item = ok.recurring.list().firstOrNull { it.id == itemId }
        val entryId = ok.recurring.post(itemId, day, kotlinx.datetime.TimeZone.currentSystemDefault(), amount)
        refresh(ok, undo = entryId?.let { UndoUi(it, "Posted ${item?.name.orEmpty()} ${(amount ?: item?.amount)?.formatted().orEmpty()}".trim(), recurringId = itemId, recurringDay = epochDay) })
    }

    fun skipDue(itemId: String, epochDay: Long) = withStore { ok ->
        val item = ok.recurring.list().firstOrNull { it.id == itemId }
        ok.recurring.skip(itemId, kotlinx.datetime.LocalDate.fromEpochDays(epochDay))
        refresh(ok, undo = UndoUi("", "Skipped ${item?.name.orEmpty()}", recurringId = itemId, recurringDay = epochDay))
    }

    /** Creates ([id] null) or changes a repeating expense. Changes apply to occurrences not yet posted; posted ones stay as they are. */
    fun saveRecurring(id: String?, name: String, amount: Money?, category: String, account: String, merchant: String, schedule: Schedule, auto: Boolean, kind: RecurringKind = RecurringKind.EXPENSE, toAccount: String = "") = withStore { ok ->
        ok.recurring.save(RecurringItem(
            id ?: UUID.randomUUID().toString(), name.trim(), amount, category.takeIf { kind != RecurringKind.TRANSFER }?.let(::CategoryId), AccountId(account),
            merchant.trim().ifEmpty { null }, schedule, auto && amount != null, kind, toAccount.takeIf { kind == RecurringKind.TRANSFER }?.let(::AccountId),
        ))
        refresh(ok)
    }

    fun deleteRecurring(id: String) = withStore { ok ->
        ok.recurring.delete(id)
        refresh(ok)
    }

    /** Posts occurrences of items set to post by themselves. Called when the app comes to the front. */
    fun postAutomatic() = withStore { ok ->
        val today = LocalDate.now()
        val done = ok.recurring.autoPost(kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth), kotlinx.datetime.TimeZone.currentSystemDefault())
        if (done.isNotEmpty()) {
            refresh(ok)
            _state.update { (it as? UiState.Ready)?.copy(message = if (done.size == 1) "Posted ${done[0].first.item.name}" else "Posted ${done.size} repeating expenses") ?: it }
        }
    }

    // ---- statement import ----

    /** Reads a statement file the user picked and guesses its layout. Nothing is saved. */
    fun startImport(uri: Uri) {
        _import.value = ImportUi(active = true, busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("That file could not be opened.")
                require(bytes.size <= MAX_STATEMENT_BYTES) { "That file is too large to import (over ${MAX_STATEMENT_BYTES / 1_000_000} MB). Is it a bank statement?" }
                val table = if (XlsxReader.looksLikeZip(bytes)) app.expenses.core.domain.CsvTable(XlsxReader.read(bytes), ',', false) else Csv.parse(StatementText.decode(bytes))
                require(!OwnExport.matches(table.rows)) { OwnExport.MESSAGE }
                require(table.rows.size >= 2) { "That file has no rows to import. A statement CSV has a header row and at least one transaction." }
                require(table.rows.size <= MAX_STATEMENT_ROWS) { "That file has too many rows to import (over $MAX_STATEMENT_ROWS)." }
                importTable = table
                val accounts = container.write { it.db.schemaQueries.selectAccounts().executeAsList().filter { a -> a.archived == 0L } }.orEmpty()
                val account = (_state.value as? UiState.Ready)?.lastAccountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id.orEmpty()
                val guessed = StatementDetector.detect(table.rows)
                // The header may not be where the detector thinks (it cannot place unusual titles), so every one of the first rows is tried.
                val width = table.rows.maxOf { r -> r.size }
                val saved = container.write { ok -> table.rows.take(PROFILE_SEARCH_ROWS).firstNotNullOfOrNull { row -> ImportProfiles.signature(row)?.let { sig -> ImportProfiles.decode(ok.templates.setting(sig), width) } } }
                recomputeImport(saved ?: guessed, account, profileNote = if (saved != null) "Read the way you set it up for the last file with these column titles. Change anything below if it is wrong." else null)
            } catch (e: IllegalArgumentException) {
                _import.value = ImportUi(active = true, error = e.message)
            } catch (e: Exception) {
                _import.value = ImportUi(active = true, error = "That file could not be read: ${e.message ?: e::class.java.simpleName}")
            }
        }
    }

    fun changeImportMapping(change: (StatementMapping) -> StatementMapping) {
        val m = _import.value.mapping ?: return
        viewModelScope.launch(Dispatchers.IO) { recomputeImport(change(m), _import.value.accountId) }
    }

    fun changeImportAccount(id: String) {
        _import.update { it.copy(accountId = id) }
    }

    private fun recomputeImport(mapping: StatementMapping, accountId: String, profileNote: String? = null) {
        val table = importTable ?: return
        val names = StatementDetector.columnNames(table.rows, mapping.headerRows)
        val missing = mapping.missing()
        val parsed = if (missing == null) StatementParser.parse(table.rows, mapping, inr.minorUnitDigits) { table.lines[it] } else null
        importParsed = parsed
        val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
        fun sum(flow: Flow) = Money(parsed?.txns?.filter { it.flow == flow }?.sumOf { it.minor } ?: 0L, inr)
        _import.value = ImportUi(
            active = true, columnNames = names, mapping = mapping, accountId = accountId,
            dateAmbiguous = StatementDetector.dateIsAmbiguous(table.rows, mapping), damaged = table.damaged, rowCount = table.rows.size - mapping.headerRows,
            preview = parsed?.txns?.take(PREVIEW_ROWS)?.map {
                ImportRowUi(dateFmt.format(LocalDate.ofEpochDay(it.date.toEpochDays())) + (if (it.description.isNotEmpty()) " · " + it.description else ""), Money(it.minor, inr).formatted(), it.flow == Flow.SPENDING)
            }.orEmpty(),
            txnCount = parsed?.txns?.size ?: 0, spending = sum(Flow.SPENDING).formatted(), income = sum(Flow.INCOME).formatted(),
            problems = parsed?.problems?.take(PROBLEMS_SHOWN)?.map { "Line ${it.row}: ${it.reason}" }.orEmpty(), problemCount = parsed?.problems?.size ?: 0,
            ignoredRows = parsed?.ignoredRows ?: 0, mappingProblem = missing, profileNote = profileNote,
        )
    }

    /** Saves what the preview showed. Already-imported rows are skipped, so doing it twice is harmless. */
    fun confirmImport() {
        val parsed = importParsed ?: return
        val account = _import.value.accountId
        if (account.isEmpty() || parsed.txns.isEmpty()) return
        _import.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.write { ok ->
                    val r = ok.statements.import(parsed.txns, AccountId(account), inr, kotlinx.datetime.TimeZone.currentSystemDefault())
                    // Remember how this bank's file was read, now that it is known to have worked, for the next one with the same column titles.
                    val mapping = _import.value.mapping
                    val signature = mapping?.takeIf { it.headerRows > 0 }?.let { m -> importTable?.rows?.getOrNull(m.headerRows - 1)?.let(ImportProfiles::signature) }
                    if (mapping != null && signature != null) ok.templates.putSettingIfChanged(signature, ImportProfiles.encode(mapping))
                    refresh(ok)
                    _import.update { it.copy(busy = false, result = ImportResultUi(r.imported, r.alreadyThere, Money(r.spendingMinor, inr).formatted(), Money(r.incomeMinor, inr).formatted())) }
                }
            } catch (e: Exception) {
                _import.update { it.copy(busy = false, error = "Nothing was imported: ${e.message ?: e::class.java.simpleName}") }
            }
        }
    }

    fun closeImport() {
        importTable = null
        importParsed = null
        _import.value = ImportUi()
    }

    // ---- search ----

    /** Searches as the user types: waits a moment for more typing, then looks. Checks the amounts first and says what is wrong. */
    fun search(input: SearchInput) {
        searchInput = input
        searchJob?.cancel()
        val min = input.minRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) ?: return problem("Enter amounts as numbers, like 250 or 99.50") }
        val max = input.maxRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) ?: return problem("Enter amounts as numbers, like 250 or 99.50") }
        if (min != null && max != null && min.minorUnits > max.minorUnits) return problem("The smallest amount is above the largest")
        val conditions = input.text.isNotBlank() || min != null || max != null || input.categoryId != null || input.kind != SearchKind.ANY || input.range != SearchRange.ANY || input.tag != null
        if (!conditions) { _search.value = SearchUi(); return }
        _search.update { it.copy(searching = true, hasConditions = true, problem = null) }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            container.write { ok -> _search.value = runSearch(ok, input, min, max) }
        }
    }

    private fun problem(message: String) { _search.value = SearchUi(hasConditions = true, problem = message) }

    fun closeSearch() {
        searchJob?.cancel()
        searchInput = null
        _search.value = SearchUi()
    }

    private fun runSearch(ok: AppContainer.Opened.Ok, input: SearchInput, min: Money?, max: Money?): SearchUi {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = LocalDate.now()
        val today = kotlinx.datetime.LocalDate(t.year, t.monthValue, t.dayOfMonth)
        val period: app.expenses.core.domain.DatePeriod? = when (input.range) {
            SearchRange.ANY -> null
            SearchRange.LAST_30_DAYS -> app.expenses.core.domain.DatePeriod(today.minus(29, kotlinx.datetime.DateTimeUnit.DAY), today.plus(1, kotlinx.datetime.DateTimeUnit.DAY))
            SearchRange.THIS_PERIOD -> ReportMath.periodAt(ok.budgets.period(), today, 0)
            SearchRange.THIS_YEAR -> ReportMath.yearAt(today, 0)
        }
        val cats = ok.categories.list()
        // A chosen category also covers its subcategories.
        val categoryIds = input.categoryId?.let { id -> listOf(id) + cats.filter { it.parentId == id }.map { it.id } }
        val query = SearchQuery(
            text = input.text, minMinor = min?.minorUnits, maxMinor = max?.minorUnits, currency = if (min != null || max != null) inr.code else null,
            tag = input.tag, categoryIds = categoryIds, kind = when (input.kind) { SearchKind.ANY -> null; SearchKind.EXPENSE -> "EXPENSE"; SearchKind.INCOME -> "INCOME" },
            fromMillis = period?.startMillis(zone) ?: Long.MIN_VALUE, untilMillis = period?.endMillis(zone) ?: Long.MAX_VALUE,
        )
        val found = ok.db.searchFiltered(query, SEARCH_LIMIT + 1)
        val shown = found.take(SEARCH_LIMIT.toInt())
        accountNames = ok.db.schemaQueries.selectAccounts().executeAsList().associate { it.id to it.name }
        val firsts = firstCategories(ok, shown)
        return SearchUi(shown.map { toRow(it, firsts) }, searching = false, hasConditions = true, truncated = found.size > SEARCH_LIMIT)
    }

    // ---- reports ----

    fun openReports() = reportAction { ReportRequest(ReportMode.PERIOD, 0) }
    fun setReportMode(mode: ReportMode) = reportAction { r -> r.copy(mode = mode, offset = 0, drillTarget = null, drillStart = null, drillEnd = null) }
    fun setReportGroup(group: ReportGroup) = reportAction { r -> r.copy(group = group, drillTarget = null, drillStart = null, drillEnd = null) }
    fun stepReport(delta: Int) = reportAction { r -> if (r.mode == ReportMode.CUSTOM) r else r.copy(offset = minOf(0, r.offset + delta), drillTarget = null, drillStart = null, drillEnd = null) }

    /** A report over any dates: [startDay] to [endDay] inclusive, as days since 1970-01-01. An end before the start is swapped. */
    fun setReportRange(startDay: Long, endDay: Long) = reportAction { r ->
        val a = minOf(startDay, endDay); val b = maxOf(startDay, endDay)
        r.copy(mode = ReportMode.CUSTOM, customStart = a, customEnd = b + 1, drillTarget = null, drillStart = null, drillEnd = null)
    }

    /** Opens the entries behind a figure: a category, the whole period, uncategorised, or income. [startDay] and [endDay] pick another period (a trend bar). */
    fun drill(target: DrillTarget, startDay: Long? = null, endDay: Long? = null, label: String? = null) = reportAction { r -> r.copy(drillTarget = target, drillStart = startDay, drillEnd = endDay, drillLabel = label) }

    /** Leaves the entry list and returns to the report. */
    fun closeDrill() = reportAction { r -> r.copy(drillTarget = null, drillStart = null, drillEnd = null) }

    fun closeReports() {
        reportRequest = null
        _report.value = null
    }

    // ---- calendar ----

    private data class CalendarRequest(val offset: Int, val selectedEpochDay: Long?)
    private var calendarRequest: CalendarRequest? = null
    private val _calendar = MutableStateFlow<CalendarUi?>(null)
    val calendar: StateFlow<CalendarUi?> get() = _calendar

    private fun calendarAction(next: (CalendarRequest) -> CalendarRequest) {
        calendarRequest = next(calendarRequest ?: CalendarRequest(0, null))
        viewModelScope.launch(Dispatchers.IO) { container.write { ok -> calendarRequest?.let { _calendar.value = buildCalendar(ok, it) } } }
    }

    fun openCalendar() = calendarAction { CalendarRequest(0, null) }
    fun closeCalendar() { calendarRequest = null; _calendar.value = null }
    fun shiftCalendar(delta: Int) = calendarAction { CalendarRequest(it.offset + delta, null) }
    fun selectCalendarDay(epochDay: Long?) = calendarAction { it.copy(selectedEpochDay = if (it.selectedEpochDay == epochDay) null else epochDay) }

    private fun buildCalendar(ok: AppContainer.Opened.Ok, r: CalendarRequest): CalendarUi {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = LocalDate.now()
        val today = kotlinx.datetime.LocalDate(t.year, t.monthValue, t.dayOfMonth)
        val (year, month) = CalendarMath.monthAt(today, r.offset)
        val first = kotlinx.datetime.LocalDate(year, month, 1)
        val period = DatePeriod(first, first.plus(1, kotlinx.datetime.DateTimeUnit.MONTH))
        val due = ok.recurring.unhandled(period.start, period.endExclusive).filter { it.item.kind == RecurringKind.EXPENSE }
        val m = CalendarMath.month(year, month, zone, ok.reports.spendPoints(period, zone, inr), due.map { BillOnDay(it.date, it.item.amount?.minorUnits) }, today)
        val selected = r.selectedEpochDay?.let { kotlinx.datetime.LocalDate.fromEpochDays(it) }?.takeIf { it in period }
        val lines = selected?.let { ok.reports.drill(DatePeriod(it, it.plus(1, kotlinx.datetime.DateTimeUnit.DAY)), zone, inr, DrillTarget.All, 200) }.orEmpty()
        val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM")
        return CalendarUi(
            label = DateTimeFormatter.ofPattern("MMMM yyyy").format(LocalDate.of(year, month, 1)), offset = r.offset, blanks = m.leadingBlanks,
            cells = m.days.map { CalendarCellUi(it.date.dayOfMonth, it.date.toEpochDays(), if (it.spentMinor == 0L) "" else Money(it.spentMinor, inr).compact(), it.spentMinor, it.bills, it.level, it.isToday) },
            total = Money(m.totalSpentMinor, inr).formatted(),
            selectedEpochDay = selected?.toEpochDays(),
            dayTitle = selected?.let { dayFmt.format(LocalDate.ofEpochDay(it.toEpochDays())) }.orEmpty(),
            dayTotal = selected?.let { Money(m.days[it.dayOfMonth - 1].spentMinor, inr).formatted() }.orEmpty(),
            dayLines = lines,
            dayBills = due.filter { it.date == selected }.map { b -> b.item.name + (b.item.amount?.let { " ${it.formatted()}" } ?: " (amount each time)") },
        )
    }

    private fun reportAction(next: (ReportRequest) -> ReportRequest) {
        val current = reportRequest ?: ReportRequest(ReportMode.PERIOD, 0)
        reportRequest = next(current)
        viewModelScope.launch(Dispatchers.IO) { container.write { ok -> reportRequest?.let { _report.value = buildReport(ok, it) } } }
    }

    private fun buildReport(ok: AppContainer.Opened.Ok, r: ReportRequest): ReportUi {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = LocalDate.now()
        val today = kotlinx.datetime.LocalDate(t.year, t.monthValue, t.dayOfMonth)
        val budgetPeriod = ok.budgets.period()
        val customStart = kotlinx.datetime.LocalDate.fromEpochDays((r.customStart ?: (today.toEpochDays() - 29L)).toInt())
        val customEnd = kotlinx.datetime.LocalDate.fromEpochDays((r.customEnd ?: (today.toEpochDays() + 1L)).toInt())
        fun periodAt(offset: Int) = when (r.mode) {
            ReportMode.PERIOD -> ReportMath.periodAt(budgetPeriod, today, offset)
            ReportMode.YEAR -> ReportMath.yearAt(today, offset)
            ReportMode.FINANCIAL_YEAR -> BudgetPeriod.Yearly().containing(today.plus(offset, kotlinx.datetime.DateTimeUnit.YEAR))
            ReportMode.CUSTOM -> app.expenses.core.domain.DatePeriod(customStart, customEnd)
        }
        val period = periodAt(r.offset)
        // The same length of time just before (for a custom range); the year or period before otherwise.
        val previous = if (r.mode == ReportMode.CUSTOM) app.expenses.core.domain.DatePeriod(customStart.minus(period.days, kotlinx.datetime.DateTimeUnit.DAY), customStart) else periodAt(r.offset - 1)
        fun javaDate(d: kotlinx.datetime.LocalDate) = LocalDate.ofEpochDay(d.toEpochDays())
        val rangeFmt = DateTimeFormatter.ofPattern("d MMM")
        val title = if (r.mode == ReportMode.YEAR) period.start.year.toString()
        else if (r.mode == ReportMode.FINANCIAL_YEAR) "FY ${period.start.year}–${(period.start.year + 1) % 100}"
        else if (r.mode == ReportMode.CUSTOM) DateTimeFormatter.ofPattern("d MMM yyyy").let { f -> f.format(javaDate(period.start)) + " – " + f.format(javaDate(period.endExclusive).minusDays(1)) }
        else rangeFmt.format(javaDate(period.start)) + " – " + rangeFmt.format(javaDate(period.endExclusive).minusDays(1)) + if (period.start.year != period.endExclusive.minus(1, kotlinx.datetime.DateTimeUnit.DAY).year) " ${period.endExclusive.year}" else ""

        val reports = ok.reports
        val spent = reports.spent(period, zone, inr)
        val income = reports.income(period, zone, inr)
        val net = income - spent
        val colors = ok.categories.list().associate { it.id to it.colorArgb }
        fun row(c: app.expenses.core.domain.CategoryRow): ReportRowUi = ReportRowUi(c.categoryId, c.name, c.total.formatted(), c.share, c.categoryId?.let { colors[it] }, c.total.minorUnits < 0, c.children.map(::row))

        // Trend: the six periods ending at this one, or the twelve months of the year.
        val bars: List<Triple<String, kotlinx.datetime.LocalDate, app.expenses.core.domain.DatePeriod>> = if (r.mode == ReportMode.YEAR) {
            ReportMath.monthsOf(period.start.year).map { Triple(DateTimeFormatter.ofPattern("MMM").format(javaDate(it.start)), it.start, it) }
        } else if (r.mode == ReportMode.FINANCIAL_YEAR || r.mode == ReportMode.CUSTOM) {
            ReportMath.monthBars(period).map { Triple(DateTimeFormatter.ofPattern("MMM").format(javaDate(it.start)), it.start, it) }
        } else {
            (5 downTo 0).map { k -> periodAt(r.offset - k).let { p -> Triple(DateTimeFormatter.ofPattern(if (p.days >= 28) "MMM" else "d MMM").format(javaDate(p.start)), p.start, p) } }
        }
        val totals = reports.trend(bars.map { it.third }, zone, inr)
        val tallest = totals.maxOfOrNull { it.minorUnits }?.takeIf { it > 0 } ?: 1L
        val trend = bars.mapIndexed { i, (label, _, p) ->
            TrendBarUi(label, totals[i].formatted(), (totals[i].minorUnits.coerceAtLeast(0).toFloat() / tallest), p == period, p.start.toEpochDays(), p.endExclusive.toEpochDays())
        }

        val change = ReportMath.percentChange(spent, reports.spent(previous, zone, inr))
        val comparison = change?.let {
            val what = when (r.mode) { ReportMode.YEAR, ReportMode.FINANCIAL_YEAR -> "the year before"; ReportMode.CUSTOM -> "the same length of time before"; else -> "the period before" }
            when { it > 0 -> "$it% more than $what"; it < 0 -> "${-it}% less than $what"; else -> "The same as $what" }
        }

        val drill = r.drillTarget?.let { target ->
            val dp = if (r.drillStart != null && r.drillEnd != null) app.expenses.core.domain.DatePeriod(kotlinx.datetime.LocalDate.fromEpochDays(r.drillStart.toInt()), kotlinx.datetime.LocalDate.fromEpochDays(r.drillEnd.toInt())) else period
            val limit = 500L
            val lines = reports.drill(dp, zone, inr, target, limit)
            val dateFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())
            val what = when (target) {
                DrillTarget.All -> "All spending"
                DrillTarget.Income -> "Income"
                DrillTarget.Uncategorised -> ReportMath.UNCATEGORISED
                is DrillTarget.InCategory -> categoryNames[target.id] ?: target.id
                is DrillTarget.InAccount -> r.drillLabel ?: accountNames[target.id] ?: "Account"
                is DrillTarget.WithTag -> "Tag: ${r.drillLabel ?: target.tag}"
                DrillTarget.Untagged -> "No tag"
                is DrillTarget.AtMerchant -> r.drillLabel ?: target.key.ifEmpty { "No merchant" }
            }
            val range = if (dp == period) title else rangeFmt.format(javaDate(dp.start)) + " – " + rangeFmt.format(javaDate(dp.endExclusive).minusDays(1))
            val total = Money(lines.sumOf { it.amount.minorUnits }, inr)
            DrillUi(
                what, range, total.formatted(),
                lines.map { DrillLineUi(it.entryId, dateFmt.format(Instant.ofEpochMilli(it.occurredAt)), it.title, it.categoryName.orEmpty(), it.amount.formatted(), it.amount.minorUnits < 0) },
                truncated = lines.size.toLong() == limit,
            )
        }
        val groupRows = if (r.group == ReportGroup.CATEGORY) emptyList() else reports.groups(r.group, period, zone, inr).map { GroupRowUi(it.key, it.name, it.total.formatted(), it.share, it.total.minorUnits < 0) }
        return ReportUi(
            r.mode, title, r.mode != ReportMode.CUSTOM && r.offset < 0, spent.formatted(), income.formatted(), net.formatted(), net.minorUnits >= 0, comparison,
            reports.breakdown(period, zone, inr).map(::row), trend, drill, r.group, groupRows, period.start.toEpochDays().toLong(), period.endExclusive.toEpochDays().toLong(),
        )
    }

    // ---- export, backup and restore ----

    /** Runs a file action off the main thread, showing "working" and then its outcome. A [BackupException] message is written for the user. */
    private fun dataAction(block: suspend () -> String) {
        _data.update { it.copy(busy = true, status = null, statusIsError = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val message = block()
                _data.update { it.copy(busy = false, status = message, statusIsError = false) }
            } catch (e: BackupException) {
                _data.update { it.copy(busy = false, status = e.message, statusIsError = true) }
            } catch (e: Exception) {
                _data.update { it.copy(busy = false, status = "That did not work: ${e.message ?: e::class.java.simpleName}", statusIsError = true) }
            }
        }
    }

    fun exportXlsx(uri: Uri) = dataAction { "Exported ${actions.exportXlsx(uri)} entries as an Excel workbook." }
    fun exportCsv(uri: Uri) = dataAction { "Exported ${actions.exportCsv(uri)} entries as a spreadsheet file." }

    fun createBackup(uri: Uri, passphrase: String?) = dataAction {
        val saved = actions.backup(uri, passphrase)
        "Backup saved and checked: ${saved.counts.entries} entries, ${saved.counts.categories} categories" + if (saved.encrypted) ", protected by your passphrase." else ". It is not encrypted; keep it somewhere private."
    }

    /** Opens a backup file. Nothing changes until [confirmRestore]. */
    fun openBackup(uri: Uri) = dataAction { showOpened(actions.open(uri, null)) }

    fun unlockBackup(passphrase: String) = dataAction {
        val bytes = openedBytes ?: throw BackupException("Choose the backup file again.")
        showOpened(actions.openBytes(bytes, passphrase))
    }

    private fun showOpened(o: DataActions.Opened): String {
        when (o) {
            is DataActions.Opened.NeedsPassphrase -> {
                openedBytes = o.bytes
                _data.update { it.copy(needsPassphrase = true, pending = null) }
                return "This backup is protected by a passphrase."
            }
            is DataActions.Opened.Ready -> {
                openedBytes = null
                openedBackup = o.data
                val made = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(o.header.createdAt))
                val r = o.report
                _data.update {
                    it.copy(
                        needsPassphrase = false,
                        pending = PendingRestoreUi(made, o.data.entries.size, r.added.entries, r.alreadyHere.entries, r.added.accounts + r.added.categories + r.added.templates + r.added.budgets + r.added.recurring),
                    )
                }
                return "Backup checked: it is complete and not damaged."
            }
        }
    }

    fun confirmRestore() {
        val data = openedBackup ?: return
        dataAction {
            val report = actions.restore(data)
            openedBackup = null
            _data.update { it.copy(pending = null) }
            container.write { ok -> refresh(ok) }
            "Restored: ${report.added.entries} entries added, ${report.alreadyHere.entries} were already here and were left as they are."
        }
    }

    fun cancelRestore() {
        openedBackup = null; openedBytes = null
        _data.update { it.copy(pending = null, needsPassphrase = false) }
    }

    fun dismissDataStatus() = _data.update { it.copy(status = null) }

    // ---- notifications ----

    /**
     * Changes the notification choices relative to what is *stored*, inside the write. Basing a change on the value on screen would lose
     * taps: two quick taps on "+" both read the same old time and write the same new one.
     */
    fun updateNotificationPrefs(change: (NotificationPrefs) -> NotificationPrefs) = withStore { ok ->
        ok.notifications.setPrefs(change(ok.notifications.prefs()))
        refresh(ok)
    }

    /** Shows one notice immediately, so the user can see that permission and channels work. Not recorded; it is not an event. */
    fun sendTestNotification() {
        val shown = Notifier.show(getApplication(), listOf(Notice("test:${System.nanoTime()}", NoticeKind.BILL_DUE, "Test notification", "If you can read this, reminders and alerts will reach you.")))
        if (shown.isEmpty()) _state.update { (it as? UiState.Ready)?.copy(message = "Notifications are blocked for this app") ?: it }
    }

    // ---- budgets ----

    /** Sets a limit for a category, or for everything when [categoryId] is null. */
    fun setBudget(categoryId: String?, amount: Money) = withStore { ok ->
        ok.budgets.setLimit(categoryId, amount)
        refresh(ok)
    }

    /** Lengthens or shortens a custom period by [delta] days, relative to what is stored (quick taps all count). */
    fun shiftCustomDays(delta: Int) = withStore { ok ->
        val current = ok.budgets.period() as? BudgetPeriod.EveryDays ?: return@withStore
        ok.budgets.setPeriod(BudgetPeriod.EveryDays((current.days + delta).coerceIn(1, BudgetPeriod.EveryDays.MAX_DAYS), current.anchor))
        refresh(ok)
    }

    fun clearBudget(categoryId: String?) = withStore { ok ->
        ok.budgets.clearLimit(categoryId)
        refresh(ok)
    }

    fun setBudgetPeriod(period: BudgetPeriod) = withStore { ok ->
        ok.budgets.setPeriod(period)
        refresh(ok)
    }

    /** Moves a monthly period's start day by [delta], relative to the stored day (see [updateNotificationPrefs] for why), staying within 1 to 28. */
    fun shiftBudgetStartDay(delta: Int) = withStore { ok ->
        val current = ok.budgets.period() as? BudgetPeriod.Monthly ?: return@withStore
        ok.budgets.setPeriod(BudgetPeriod.Monthly((current.startDay + delta).coerceIn(1, BudgetPeriod.Monthly.MAX_START_DAY)))
        refresh(ok)
    }

    fun dismissMessage() {
        _state.update { (it as? UiState.Ready)?.copy(message = null) ?: it }
    }

    fun dismissUndo() {
        _state.update { (it as? UiState.Ready)?.copy(undo = null) ?: it }
    }

    /** Debug and benchmark builds only: fills the database with realistic rows to measure speed at scale. */
    fun seed(rows: Int) {
        check(BuildConfig.SEEDING_ENABLED) { "Seeding is not available in this build" }
        withStore { ok ->
            val q = ok.db.schemaQueries
            val step = 3_650L * 86_400_000L / rows
            val start = System.currentTimeMillis() - 3_650L * 86_400_000L
            ok.db.bulkLoad(ok.driver) {
                for (i in 0 until rows) {
                    val at = start + i * step
                    val amount = 100L + (i * 37L) % 90_000L
                    q.insertEntry("seed$i", "EXPENSE", at, "cash", null, amount, "INR", null, "Merchant ${i % 400}", "seed note $i", "seed", null)
                    q.insertSpendLine("seed$i", 0, at, "INR", DEFAULT_CATEGORIES[i % DEFAULT_CATEGORIES.size], amount, amount)
                }
            }
            refresh(ok)
        }
    }

    // ---- internals ----

    /** Runs [block] off the main thread through the container's process-wide write lock, once the database is open. */
    private fun withStore(block: (AppContainer.Opened.Ok) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.write(block)
            } catch (e: IllegalArgumentException) {
                _state.update { (it as? UiState.Ready)?.copy(message = e.message) ?: it }
            }
        }
    }

    /** How long the last save took until the new entry and the period total were on screen (the write plus the quick update), in milliseconds. Read by the timing gate. */
    @Volatile var lastSaveMillis: Double = 0.0
        internal set

    /** How long the rebuild that follows a save took after the new entry was already on screen, in milliseconds. Read by the timing gate. */
    @Volatile var lastSettleMillis: Double = 0.0
        internal set

    // Formatted list rows by the stored row they came from: an unchanged entry is not formatted again after every save, and an edited one is a
    // different key, so it is. Cleared by every full rebuild.
    private val rowCache = HashMap<Entry, RowUi>()
    private fun rowsOf(entries: List<Entry>): List<RowUi> {
        if (rowCache.size > 2_000) rowCache.clear()
        return entries.map { e -> rowCache.getOrPut(e) { toRow(e) } }
    }

    /**
     * After a save: put on screen what the person is waiting to see (the new entry in the list and this period's total) at once, then rebuild
     * everything else right behind it. On a 100,000-entry ledger the full rebuild is about 10 ms, which would otherwise sit inside the 50 ms
     * save budget. Everything else is still correct within a few milliseconds; nothing is skipped.
     */
    private fun refreshQuickly(ok: AppContainer.Opened.Ok, undo: UndoUi?) {
        val current = _state.value as? UiState.Ready
        if (current == null) { refresh(ok, undo); return }
        val q = ok.db.schemaQueries
        val want = maxOf(loaded.size.toLong(), PAGE)
        loaded = q.recentEntries(want).executeAsList().toMutableList()
        endReached = loaded.size < want
        loadedCategory = firstCategories(ok, loaded)
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = LocalDate.now()
        val period = ok.budgets.period().containing(kotlinx.datetime.LocalDate(t.year, t.monthValue, t.dayOfMonth))
        val spent = ok.store.spendByCategory(inr, from = period.startMillis(zone), until = period.endMillis(zone)).values.fold(Money.zero(inr)) { a, m -> a + m }
        _state.value = current.copy(rows = rowsOf(loaded), periodSpend = spent.formatted(), endReached = endReached, undo = undo)
        val quickDone = System.nanoTime()
        viewModelScope.launch(Dispatchers.IO) {
            container.write { ok2 -> refresh(ok2, undo = (_state.value as? UiState.Ready)?.undo) }
            lastSettleMillis = (System.nanoTime() - quickDone) / 1e6
        }
    }

    private fun refresh(ok: AppContainer.Opened.Ok, undo: UndoUi? = null) {
        rowCache.clear() // names (accounts, categories) may have changed since the rows were formatted
        val q = ok.db.schemaQueries
        val accounts = q.selectAccounts().executeAsList()
        accountNames = accounts.associate { it.id to it.name }
        currencyDigits = q.selectCurrencies().executeAsList().associate { it.code to it.digits.toInt() }
        // Reload as many rows as are already shown, so a change does not throw the user back to the top of a long list.
        val want = maxOf(loaded.size.toLong(), PAGE)
        loaded = q.recentEntries(want).executeAsList().toMutableList()
        endReached = loaded.size < want
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val status = ok.budgets.status(kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth), kotlinx.datetime.TimeZone.currentSystemDefault(), inr)
        val spent = ok.store.spendByCategory(inr, from = status.period.startMillis(kotlinx.datetime.TimeZone.currentSystemDefault()), until = status.period.endMillis(kotlinx.datetime.TimeZone.currentSystemDefault()))
            .values.fold(Money.zero(inr)) { a, m -> a + m }
        val cats = ok.categories.list()
        categoryNames = cats.associate { it.id to it.name }
        reportRequest?.let { _report.value = buildReport(ok, it) } // keep an open report in step with edits made from its entry list
        calendarRequest?.let { _calendar.value = buildCalendar(ok, it) }
        searchInput?.let { i -> // and an open search, so an entry edited from the results is shown as it now is
            val min = i.minRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) }
            val max = i.maxRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) }
            if (_search.value.hasConditions && _search.value.problem == null) _search.value = runSearch(ok, i, min, max)
        }
        loadedCategory = firstCategories(ok, loaded)
        val visible = cats.filter { !it.hidden }
        val lastCategory = ok.templates.setting(LAST_CATEGORY)?.takeIf { id -> visible.any { it.id == id } } ?: visible.firstOrNull()?.id ?: ""
        val active = accounts.filter { it.archived == 0L }
        val lastAccount = ok.templates.setting(LAST_ACCOUNT)?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id ?: "cash"
        val layout = HomeLayout.decode(ok.templates.setting(SETTING_HOME_LAYOUT))
        balancesVisible = HomeSection.BALANCES in layout.visible()
        scheduleSlowFigures()
        _state.value = UiState.Ready(
            rows = rowsOf(loaded),
            periodSpend = spent.formatted(),
            budget = budgetUi(ok, status, cats),
            due = dueUi(ok, today),
            notifications = ok.notifications.prefs(),
            // 'ever' counts deleted entries too: someone who added something and deleted it is not new to the app.
            showWelcome = !ok.db.schemaQueries.entryCountEver().executeAsOne() && ok.templates.setting(SETTING_WELCOME_DISMISSED) != "1",
            showBackupNudge = nudgeCache,
            hiddenOlder = ok.recurring.hiddenOlder(kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth)),
            recurring = recurringUi(ok, today),
            accounts = active.map { AccountUi(it.id, it.name) },
            allAccounts = accounts.map { AccountUi(it.id, it.name) },
            accountRows = accountRowsCache,
            savedSearches = ok.savedSearches.list().map { SavedSearchUi(it.id, it.name, SearchInput(it.text, it.minRupees, it.maxRupees, it.categoryId?.let { c -> ok.db.resolveCategory(c) }, SearchKind.valueOf(it.kind), SearchRange.valueOf(it.range), it.tag)) },
            appearance = Appearances.decode(ok.templates.setting(SETTING_APPEARANCE)),
            baseCurrency = BaseCurrency.current,
            hideAmounts = ok.templates.setting(SETTING_HIDE_AMOUNTS) == "1",
            homeLayout = HomeLayout.decode(ok.templates.setting(SETTING_HOME_LAYOUT)),
            autoExport = autoExportUi(ok),
            tags = ok.store.tagsInUse(TAG_SUGGESTIONS).map { it.first },
            // Usage (which decides whether Delete is offered) is worked out in the background only while the categories screen is open.
            categories = cats.map { CategoryUi(it.id, it.name, it.parentId, it.colorArgb, it.hidden, categoryUsageCache[it.id] ?: 0L) },
            templates = ok.templates.list().map { TemplateUi(it.id, it.name, it.amount?.formatted(), it.category.value, it.account.value, it.merchant ?: "") },
            lastCategory = lastCategory,
            lastAccountId = lastAccount,
            endReached = endReached,
            undo = undo,
        )
    }

    private fun budgetUi(ok: AppContainer.Opened.Ok, status: BudgetStatus, cats: List<app.expenses.core.domain.Category>): BudgetUi {
        val byScope = status.progress.associateBy { it.categoryId }
        val names = cats.associate { it.id to it.name }
        fun line(categoryId: String?, name: String): BudgetLineUi {
            val p = byScope[categoryId]
            return BudgetLineUi(
                categoryId, name,
                limit = p?.limit?.formatted(), limitPlain = p?.limit?.toPlainString().orEmpty(),
                spent = (p?.spent ?: Money.zero(inr)).formatted(),
                note = p?.let { if (it.remaining.minorUnits >= 0) "${it.remaining.formatted()} left" else "${Money(-it.remaining.minorUnits, inr).formatted()} over" }.orEmpty(),
                percent = p?.usedPercent ?: 0,
                state = p?.state ?: BudgetState.OK,
            )
        }
        val lines = cats.filter { !it.hidden || byScope.containsKey(it.id) }
            .map { c -> line(c.id, c.parentId?.let { pid -> names[pid]?.let { "$it › ${c.name}" } } ?: c.name) }
        val period = ok.budgets.period()
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        fun java(d: kotlinx.datetime.LocalDate) = LocalDate.of(d.year, d.month.ordinal + 1, d.day)
        return BudgetUi(
            period = period,
            range = fmt.format(java(status.period.start)) + " – " + fmt.format(java(status.period.endExclusive).minusDays(1)),
            periodName = when (period) {
                is BudgetPeriod.Weekly -> "this week"
                is BudgetPeriod.Monthly -> if (period.startDay == 1) "this month" else "this cycle"
                is BudgetPeriod.BiWeekly -> "this fortnight"
                BudgetPeriod.SemiMonthly -> "this half-month"
                is BudgetPeriod.EveryDays -> "this period"
                is BudgetPeriod.Yearly -> if (period.startMonth == 4) "this financial year" else if (period.startMonth == 1) "this year" else "this year of the budget"
            },
            overall = line(null, "Everything"),
            categories = lines,
            perDay = status.safeToSpend?.takeIf { byScope[null]?.state != BudgetState.OVER }?.perDay?.formatted(),
            upcoming = status.safeToSpend?.upcoming?.takeIf { it.minorUnits > 0 }?.formatted(),
            billsUseItAll = status.safeToSpend?.let { it.available.minorUnits <= 0 && it.remaining.minorUnits > 0 } ?: false,
            daysLeft = status.safeToSpend?.daysLeft ?: 0,
            categoriesNeedingAttention = status.progress.count { it.categoryId != null && it.state != BudgetState.OK },
        )
    }

    private fun dueUi(ok: AppContainer.Opened.Ok, today: LocalDate): List<DueUi> {
        val now = kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth)
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        return ok.recurring.due(now).map { d ->
            val late = (now.toEpochDays() - d.date.toEpochDays()).toInt()
            DueUi(
                d.item.id, d.date.toEpochDays(), d.item.name, d.item.amount?.formatted(),
                when { late <= 0 -> "Due today"; late == 1 -> "Due yesterday"; else -> "Due ${fmt.format(LocalDate.ofEpochDay(d.date.toEpochDays()))} · $late days ago" },
                overdue = late > 0,
            )
        }
    }

    private fun recurringUi(ok: AppContainer.Opened.Ok, today: LocalDate): List<RecurringUi> {
        val now = kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth)
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
        return ok.recurring.list().map { i ->
            val s = i.schedule
            val cadence = if (s.every == 1) "every ${s.unit.name.lowercase()}" else "every ${s.every} ${s.unit.name.lowercase()}s"
            val next = s.nextOnOrAfter(now)?.let { "next ${fmt.format(LocalDate.ofEpochDay(it.date.toEpochDays()))}" } ?: "ended"
            RecurringUi(
                i.id, i.name, i.amount?.toPlainString().orEmpty(), i.category?.value.orEmpty(), i.account.value, i.merchant.orEmpty(), s, i.auto, i.kind, i.toAccount?.value.orEmpty(),
                listOfNotNull(i.amount?.formatted() ?: "amount each time", cadence, next, if (i.auto) "posts by itself" else null).joinToString(" · "),
            )
        }
    }

    /** entry id -> id of its main category, for the given entries. */
    private fun firstCategories(ok: AppContainer.Opened.Ok, entries: List<Entry>): Map<String, String> =
        if (entries.isEmpty()) emptyMap()
        else ok.db.schemaQueries.firstSplits(entries.map { it.id }).executeAsList().associate { it.entry_id to it.category_id }

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

    private fun toRow(e: Entry, firstCategory: Map<String, String> = loadedCategory): RowUi = RowUi(
        seq = e.seq,
        id = e.id,
        title = e.merchant ?: e.note ?: e.kind.lowercase().replaceFirstChar { it.uppercase() },
        subtitle = dateFormat.format(Instant.ofEpochMilli(e.occurred_at)) +
            (e.account_id?.let { accountNames[it] }?.let { " · $it" } ?: "") +
            ((firstCategory[e.id] ?: e.category_id)?.let { categoryNames[it] }?.let { " · $it" } ?: ""),
        amount = Money(e.amount_minor, Currency(e.currency, currencyDigits[e.currency] ?: 2)).formatted(),
        inflow = e.kind == "INCOME" || e.kind == "REFUND",
    )

    // Last on purpose. This starts work on another thread that reads the properties above and below it; declared earlier in the class it
    // could run before they were initialised (a date formatter below it was still null on about 3% of starts, and the load died silently,
    // leaving the opening screen up forever).
    init {
        viewModelScope.launch(Dispatchers.IO) {
            when (val o = container.open()) {
                // Through the write lock like every other refresh: a load that read the data before an automatic post but published after the
                // post's own refresh would overwrite the screen with stale totals.
                is AppContainer.Opened.Ok -> {
                    container.write { ok -> refresh(ok) }; StartupTrace.mark("open: first page loaded")
                    // Once at start, before anything can be adding a file: copies that were interrupted before their record was written.
                    container.write { ok -> attachmentFiles.sweep(ok.attachments.allIds()) }
                }
                is AppContainer.Opened.KeyLost -> _state.value = UiState.KeyLost(o.message)
            }
        }
    }
}
