package app.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.Template
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
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

    // ---- notifications ----

    fun setNotificationPrefs(p: NotificationPrefs) = withStore { ok ->
        ok.notifications.setPrefs(p)
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
