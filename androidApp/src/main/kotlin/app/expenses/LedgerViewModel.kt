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
import app.expenses.core.domain.ReportMath
import app.expenses.core.storage.DrillTarget
import kotlinx.datetime.minus
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.Template
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.AccountId
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class RowUi(val seq: Long, val id: String, val title: String, val subtitle: String, val amount: String, val outflow: Boolean)
data class AccountUi(val id: String, val name: String)

/** A category as the screens need it. [usage] is how many items refer to it (needed to decide whether it can be deleted). */
data class CategoryUi(val id: String, val name: String, val parentId: String?, val colorArgb: Long?, val hidden: Boolean, val usage: Long)

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
    /** "₹15,000.00 · every month · next 1 Nov". */
    val summary: String,
)

/** Shown as "Added ₹20 · Undo" after an entry is recorded in one step. */
/** [restore] is true for a deletion: undoing it brings the entry back instead of removing one. */
data class UndoUi(val entryId: String, val text: String, val restore: Boolean = false, val recurringId: String? = null, val recurringDay: Long = 0)

enum class ReportMode { PERIOD, YEAR }

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
        val notifications: NotificationPrefs,
        val recurring: List<RecurringUi>,
        val accounts: List<AccountUi>,
        val templates: List<TemplateUi>,
        val categories: List<CategoryUi>,
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
private const val LAST_CATEGORY = "last_category"
private const val LAST_ACCOUNT = "last_account"

/**
 * Screen state for the ledger. The database lives in [AppContainer]; every read and write here runs off the main
 * thread, and writes are serialised so rapid taps cannot overlap. New rows are requested with keyset paging.
 */
class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val inr = Currency.INR
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

    private data class ReportRequest(val mode: ReportMode, val offset: Int, val drillTarget: DrillTarget? = null, val drillStart: Long? = null, val drillEnd: Long? = null)
    private var reportRequest: ReportRequest? = null
    private val _report = MutableStateFlow<ReportUi?>(null)
    val report: StateFlow<ReportUi?> = _report

    private var accountNames = emptyMap<String, String>()
    private var currencyDigits = emptyMap<String, Int>()
    private var categoryNames = emptyMap<String, String>()
    private var loadedCategory = emptyMap<String, String>()
    private var loaded = mutableListOf<Entry>()
    private var endReached = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            when (val o = container.open()) {
                is AppContainer.Opened.Ok -> { refresh(o); StartupTrace.mark("open: first page loaded") }
                is AppContainer.Opened.KeyLost -> _state.value = UiState.KeyLost(o.message)
            }
        }
    }

    /** Last resort after key loss, only on the user's explicit choice: deletes all data. */
    fun startFresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UiState.Opening
            when (val o = container.startFresh()) {
                is AppContainer.Opened.Ok -> refresh(o)
                is AppContainer.Opened.KeyLost -> _state.value = UiState.KeyLost(o.message)
            }
        }
    }

    /** Records an expense from the add screen. Remembers its category and account as the next defaults. */
    fun addExpense(amount: Money, category: String, note: String, accountId: String, saveAsTemplate: Boolean, occurredAt: Long) = withStore { ok ->
        val id = EntryActions(ok).recordExpense(amount, category, note.trim().ifEmpty { null }, accountId, occurredAt)
        if (saveAsTemplate) {
            val name = note.trim().ifEmpty { category }
            ok.templates.save(Template(UUID.randomUUID().toString(), name, amount, CategoryId(category), AccountId(accountId), note.trim().ifEmpty { null }))
        }
        refresh(ok, undo = UndoUi(id, "Added ${amount.formatted()}"))
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
        if (u.recurringId != null) ok.recurring.undo(u.recurringId, kotlinx.datetime.LocalDate.fromEpochDays(u.recurringDay))
        else if (u.restore) EntryActions(ok).restore(u.entryId) else EntryActions(ok).undo(u.entryId)
        refresh(ok)
    }

    /** Opens the editor for an entry: the simple form, or a read-only explanation. */
    fun openEditor(entryId: String) = withStore { ok ->
        _editor.value = EntryActions(ok).editable(entryId)
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun saveEdit(entryId: String, amount: Money, category: String, note: String, accountId: String, occurredAt: Long) = withStore { ok ->
        EntryActions(ok).edit(entryId, amount, category, note.trim().ifEmpty { null }, accountId, occurredAt)
        _editor.value = null
        refresh(ok)
    }

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
    fun saveRecurring(id: String?, name: String, amount: Money?, category: String, account: String, merchant: String, schedule: Schedule, auto: Boolean) = withStore { ok ->
        ok.recurring.save(RecurringItem(id ?: UUID.randomUUID().toString(), name.trim(), amount, CategoryId(category), AccountId(account), merchant.trim().ifEmpty { null }, schedule, auto && amount != null))
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

    // ---- reports ----

    fun openReports() = reportAction { ReportRequest(ReportMode.PERIOD, 0) }
    fun setReportMode(mode: ReportMode) = reportAction { ReportRequest(mode, 0) }
    fun stepReport(delta: Int) = reportAction { r -> r.copy(offset = minOf(0, r.offset + delta), drillTarget = null, drillStart = null, drillEnd = null) }

    /** Opens the entries behind a figure: a category, the whole period, uncategorised, or income. [startDay] and [endDay] pick another period (a trend bar). */
    fun drill(target: DrillTarget, startDay: Long? = null, endDay: Long? = null) = reportAction { r -> r.copy(drillTarget = target, drillStart = startDay, drillEnd = endDay) }

    /** Leaves the entry list and returns to the report. */
    fun closeDrill() = reportAction { r -> r.copy(drillTarget = null, drillStart = null, drillEnd = null) }

    fun closeReports() {
        reportRequest = null
        _report.value = null
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
        fun periodAt(offset: Int) = if (r.mode == ReportMode.PERIOD) ReportMath.periodAt(budgetPeriod, today, offset) else ReportMath.yearAt(today, offset)
        val period = periodAt(r.offset)
        val previous = periodAt(r.offset - 1)
        fun javaDate(d: kotlinx.datetime.LocalDate) = LocalDate.ofEpochDay(d.toEpochDays())
        val rangeFmt = DateTimeFormatter.ofPattern("d MMM")
        val title = if (r.mode == ReportMode.YEAR) period.start.year.toString()
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
            val what = if (r.mode == ReportMode.YEAR) "the year before" else "the period before"
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
            }
            val range = if (dp == period) title else rangeFmt.format(javaDate(dp.start)) + " – " + rangeFmt.format(javaDate(dp.endExclusive).minusDays(1))
            val total = Money(lines.sumOf { it.amount.minorUnits }, inr)
            DrillUi(
                what, range, total.formatted(),
                lines.map { DrillLineUi(it.entryId, dateFmt.format(Instant.ofEpochMilli(it.occurredAt)), it.title, it.categoryName.orEmpty(), it.amount.formatted(), it.amount.minorUnits < 0) },
                truncated = lines.size.toLong() == limit,
            )
        }
        return ReportUi(r.mode, title, r.offset < 0, spent.formatted(), income.formatted(), net.formatted(), net.minorUnits >= 0, comparison, reports.breakdown(period, zone, inr).map(::row), trend, drill)
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

    private fun refresh(ok: AppContainer.Opened.Ok, undo: UndoUi? = null) {
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
        loadedCategory = firstCategories(ok, loaded)
        val visible = cats.filter { !it.hidden }
        val lastCategory = ok.templates.setting(LAST_CATEGORY)?.takeIf { id -> visible.any { it.id == id } } ?: visible.firstOrNull()?.id ?: ""
        val lastAccount = ok.templates.setting(LAST_ACCOUNT)?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: "cash"
        _state.value = UiState.Ready(
            rows = loaded.map(::toRow),
            periodSpend = spent.formatted(),
            budget = budgetUi(ok, status, cats),
            due = dueUi(ok, today),
            notifications = ok.notifications.prefs(),
            hiddenOlder = ok.recurring.hiddenOlder(kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth)),
            recurring = recurringUi(ok, today),
            accounts = accounts.map { AccountUi(it.id, it.name) },
            categories = cats.map { CategoryUi(it.id, it.name, it.parentId, it.colorArgb, it.hidden, ok.categories.usage(it.id)) },
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
                i.id, i.name, i.amount?.toPlainString().orEmpty(), i.category.value, i.account.value, i.merchant.orEmpty(), s, i.auto,
                listOfNotNull(i.amount?.formatted() ?: "amount each time", cadence, next, if (i.auto) "posts by itself" else null).joinToString(" · "),
            )
        }
    }

    /** entry id -> id of its main category, for the given entries. */
    private fun firstCategories(ok: AppContainer.Opened.Ok, entries: List<Entry>): Map<String, String> =
        if (entries.isEmpty()) emptyMap()
        else ok.db.schemaQueries.firstSplits(entries.map { it.id }).executeAsList().associate { it.entry_id to it.category_id }

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

    private fun toRow(e: Entry): RowUi = RowUi(
        seq = e.seq,
        id = e.id,
        title = e.merchant ?: e.note ?: e.kind.lowercase().replaceFirstChar { it.uppercase() },
        subtitle = dateFormat.format(Instant.ofEpochMilli(e.occurred_at)) +
            (e.account_id?.let { accountNames[it] }?.let { " · $it" } ?: "") +
            ((loadedCategory[e.id] ?: e.category_id)?.let { categoryNames[it] }?.let { " · $it" } ?: ""),
        amount = Money(e.amount_minor, Currency(e.currency, currencyDigits[e.currency] ?: 2)).formatted(),
        outflow = e.kind == "EXPENSE" || e.kind == "FRIEND_PAID",
    )
}
