package app.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.bulkLoad
import app.expenses.core.storage.db.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class RowUi(val seq: Long, val id: String, val title: String, val subtitle: String, val amount: String, val outflow: Boolean)
data class AccountUi(val id: String, val name: String)

/** A saved quick entry as the UI needs it. [amountText] is null when the template asks for the amount. */
data class TemplateUi(val id: String, val name: String, val amountText: String?, val category: String, val accountId: String, val note: String)

/** Shown as "Added ₹20 · Undo" after an entry is recorded in one step. */
data class UndoUi(val entryId: String, val text: String)

sealed interface UiState {
    data object Opening : UiState

    /** The database exists but its key is gone; the user must choose to start fresh or restore. */
    data class KeyLost(val message: String) : UiState

    data class Ready(
        val rows: List<RowUi>,
        val monthSpend: String,
        val accounts: List<AccountUi>,
        val templates: List<TemplateUi>,
        /** What the add screen starts with: the category and account used last. */
        val lastCategory: String,
        val lastAccountId: String,
        val endReached: Boolean,
        val message: String? = null,
        val undo: UndoUi? = null,
    ) : UiState
}

val DEFAULT_CATEGORIES = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")

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

    private val writeLock = Mutex()
    private var accountNames = emptyMap<String, String>()
    private var currencyDigits = emptyMap<String, Int>()
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
    fun addExpense(amount: Money, category: String, note: String, accountId: String, saveAsTemplate: Boolean) = withStore { ok ->
        val id = recordExpense(ok, amount, category, note.trim().ifEmpty { null }, accountId)
        if (saveAsTemplate) {
            val name = note.trim().ifEmpty { category }
            ok.templates.save(Template(UUID.randomUUID().toString(), name, amount, CategoryId(category), AccountId(accountId), note.trim().ifEmpty { null }))
        }
        refresh(ok, undo = UndoUi(id, "Added ${amount.formatted()}"))
    }

    /** One tap: records the template's expense right away. Templates without an amount are handled by the add screen. */
    fun quickAdd(templateId: String) = withStore { ok ->
        val t = ok.templates.list().firstOrNull { it.id == templateId } ?: return@withStore
        val amount = t.amount ?: return@withStore
        val id = recordExpense(ok, amount, t.category.value, t.merchant ?: t.name, t.account.value)
        ok.templates.recordUse(templateId)
        refresh(ok, undo = UndoUi(id, "Added ${t.name} ${amount.formatted()}"))
    }

    fun deleteTemplate(templateId: String) = withStore { ok ->
        ok.templates.delete(templateId)
        refresh(ok)
    }

    /** Takes back an entry that was just added. */
    fun undo(entryId: String) = withStore { ok ->
        ok.store.delete(EntryId(entryId))
        refresh(ok)
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
            _state.update { s -> (s as? UiState.Ready)?.copy(rows = s.rows + page.map(::toRow), endReached = endReached) ?: current }
        }
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

    /** Runs [block] off the main thread, one write at a time, once the database is open. */
    private fun withStore(block: (AppContainer.Opened.Ok) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = container.open() as? AppContainer.Opened.Ok ?: return@launch
            writeLock.withLock {
                try {
                    block(ok)
                } catch (e: IllegalArgumentException) {
                    _state.update { (it as? UiState.Ready)?.copy(message = e.message) ?: it }
                }
            }
        }
    }

    private fun recordExpense(ok: AppContainer.Opened.Ok, amount: Money, category: String, note: String?, accountId: String): String {
        val id = UUID.randomUUID().toString()
        ok.store.save(
            Expense(EntryId(id), System.currentTimeMillis(), AccountId(accountId), amount, listOf(CategorySplit(CategoryId(category), amount))),
            EntryDetails(merchant = note),
        )
        ok.templates.putSetting(LAST_CATEGORY, category)
        ok.templates.putSetting(LAST_ACCOUNT, accountId)
        return id
    }

    private fun refresh(ok: AppContainer.Opened.Ok, undo: UndoUi? = null) {
        val q = ok.db.schemaQueries
        val accounts = q.selectAccounts().executeAsList()
        accountNames = accounts.associate { it.id to it.name }
        currencyDigits = q.selectCurrencies().executeAsList().associate { it.code to it.digits.toInt() }
        loaded = q.recentEntries(PAGE).executeAsList().toMutableList()
        endReached = loaded.size < PAGE
        val zone = ZoneId.systemDefault()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = ok.store.spendByCategory(inr, from = monthStart).values.fold(Money.zero(inr)) { a, m -> a + m }
        val lastCategory = ok.templates.setting(LAST_CATEGORY)?.takeIf { it.isNotEmpty() } ?: DEFAULT_CATEGORIES.first()
        val lastAccount = ok.templates.setting(LAST_ACCOUNT)?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: "cash"
        _state.value = UiState.Ready(
            rows = loaded.map(::toRow),
            monthSpend = spent.formatted(),
            accounts = accounts.map { AccountUi(it.id, it.name) },
            templates = ok.templates.list().map { TemplateUi(it.id, it.name, it.amount?.formatted(), it.category.value, it.account.value, it.merchant ?: "") },
            lastCategory = lastCategory,
            lastAccountId = lastAccount,
            endReached = endReached,
            undo = undo,
        )
    }

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

    private fun toRow(e: Entry): RowUi = RowUi(
        seq = e.seq,
        id = e.id,
        title = e.merchant ?: e.note ?: e.kind.lowercase().replaceFirstChar { it.uppercase() },
        subtitle = dateFormat.format(Instant.ofEpochMilli(e.occurred_at)) + (e.account_id?.let { accountNames[it] }?.let { " · $it" } ?: ""),
        amount = Money(e.amount_minor, Currency(e.currency, currencyDigits[e.currency] ?: 2)).formatted(),
        outflow = e.kind == "EXPENSE" || e.kind == "FRIEND_PAID",
    )
}
