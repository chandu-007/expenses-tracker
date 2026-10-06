package app.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.DatabaseKeyUnavailableException
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.LedgerStore
import app.expenses.core.storage.bulkLoad
import app.expenses.core.storage.db.Entry
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class RowUi(val seq: Long, val id: String, val title: String, val subtitle: String, val amount: String, val outflow: Boolean)
data class AccountUi(val id: String, val name: String)

sealed interface UiState {
    data object Opening : UiState

    /** The database exists but its key is gone; the user must choose to start fresh or restore. */
    data class KeyLost(val message: String) : UiState

    data class Ready(
        val rows: List<RowUi>,
        val monthSpend: String,
        val accounts: List<AccountUi>,
        val endReached: Boolean,
        val message: String? = null,
    ) : UiState
}

val DEFAULT_CATEGORIES = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")

private const val PAGE = 50L

/**
 * Owns the encrypted database for the app's lifetime. Opening it, reading and saving all happen off the main
 * thread; the UI only observes [state]. A new screen of rows is requested by [loadMore] using keyset paging.
 */
class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val inr = Currency.INR
    private val _state = MutableStateFlow<UiState>(UiState.Opening)
    val state: StateFlow<UiState> = _state

    private lateinit var driver: SqlDriver
    private lateinit var db: ExpensesDb
    private lateinit var store: LedgerStore
    private var accountNames = emptyMap<String, String>()
    private var currencyDigits = emptyMap<String, Int>()
    private var loaded = mutableListOf<Entry>()
    private var endReached = false

    /** Completes when the database is open (or the key is lost). Work that needs the store waits for it. */
    private val opening = viewModelScope.launch(Dispatchers.IO) { open() }

    private fun open() {
        try {
            driver = EncryptedDriver.create(getApplication())
            db = ExpensesDb(driver)
            store = LedgerStore(db, HlcClock(nodeId()), System::currentTimeMillis)
            ensureDefaultAccounts()
            refresh()
        } catch (e: DatabaseKeyUnavailableException) {
            _state.value = UiState.KeyLost(e.message ?: "The database key is unavailable")
        }
    }

    /** Last resort after key loss, only on the user's explicit choice: deletes all data. */
    fun startFresh() {
        viewModelScope.launch(Dispatchers.IO) {
            opening.join()
            if (::driver.isInitialized) runCatching { driver.close() }
            EncryptedDriver.reset(getApplication())
            _state.value = UiState.Opening
            open()
        }
    }

    fun addExpense(amount: Money, category: String, note: String, accountId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val expense = Expense(
                    id = EntryId(UUID.randomUUID().toString()),
                    occurredAt = System.currentTimeMillis(),
                    account = AccountId(accountId),
                    amount = amount,
                    splits = listOf(CategorySplit(CategoryId(category), amount)),
                )
                store.save(expense, EntryDetails(merchant = note.trim().ifEmpty { null }))
                refresh()
            } catch (e: IllegalArgumentException) {
                _state.update { (it as? UiState.Ready)?.copy(message = e.message) ?: it }
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? UiState.Ready ?: return
        if (endReached) return
        viewModelScope.launch(Dispatchers.IO) {
            val last = loaded.lastOrNull() ?: return@launch
            val page = db.schemaQueries.entriesBefore(last.occurred_at, last.seq, PAGE).executeAsList()
            if (page.size < PAGE) endReached = true
            if (page.isEmpty()) return@launch
            loaded.addAll(page)
            _state.value = current.copy(rows = current.rows + page.map(::toRow), endReached = endReached)
        }
    }

    fun dismissMessage() {
        _state.update { (it as? UiState.Ready)?.copy(message = null) ?: it }
    }

    /** Debug and benchmark builds only: fills the database with realistic rows to measure speed at scale. */
    fun seed(rows: Int) {
        check(BuildConfig.SEEDING_ENABLED) { "Seeding is not available in this build" }
        viewModelScope.launch(Dispatchers.IO) {
            opening.join()
            if (!::store.isInitialized) return@launch
            val q = db.schemaQueries
            val step = 3_650L * 86_400_000L / rows
            val start = System.currentTimeMillis() - 3_650L * 86_400_000L
            val cats = DEFAULT_CATEGORIES
            db.bulkLoad(driver) {
                for (i in 0 until rows) {
                    val at = start + i * step
                    val amount = 100L + (i * 37L) % 90_000L
                    q.insertEntry("seed$i", "EXPENSE", at, "cash", null, amount, "INR", null, "Merchant ${i % 400}", "seed note $i", "seed", null)
                    q.insertSpendLine("seed$i", 0, at, "INR", cats[i % cats.size], amount, amount)
                }
            }
            refresh()
        }
    }

    // ---- internals ----

    private fun ensureDefaultAccounts() {
        if (db.schemaQueries.selectAccounts().executeAsList().isNotEmpty()) return
        store.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
    }

    private fun refresh() {
        val accounts = db.schemaQueries.selectAccounts().executeAsList()
        accountNames = accounts.associate { it.id to it.name }
        currencyDigits = db.schemaQueries.selectCurrencies().executeAsList().associate { it.code to it.digits.toInt() }
        loaded = db.schemaQueries.recentEntries(PAGE).executeAsList().toMutableList()
        endReached = loaded.size < PAGE
        val zone = ZoneId.systemDefault()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = store.spendByCategory(inr, from = monthStart).values.fold(Money.zero(inr)) { a, m -> a + m }
        _state.value = UiState.Ready(loaded.map(::toRow), spent.formatted(), accounts.map { AccountUi(it.id, it.name) }, endReached)
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

    /** A random id for this install, used only to make sync-log timestamps unique per device. */
    private fun nodeId(): String {
        val f = File(getApplication<Application>().noBackupFilesDir, "node_id")
        return if (f.exists()) f.readText() else UUID.randomUUID().toString().take(8).also { f.writeText(it) }
    }

    override fun onCleared() {
        if (::driver.isInitialized) runCatching { driver.close() }
    }
}
