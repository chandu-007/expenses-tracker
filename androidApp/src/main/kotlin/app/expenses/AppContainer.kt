package app.expenses

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.BackupStore
import app.expenses.core.storage.BudgetStore
import app.expenses.core.storage.CsvExport
import app.expenses.core.storage.CategoryStore
import app.expenses.core.storage.NotificationService
import app.expenses.core.storage.RecurringStore
import app.expenses.core.storage.ReportStore
import app.expenses.core.storage.StatementImporter
import app.expenses.core.storage.DatabaseKeyUnavailableException
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.AttachmentStore
import app.expenses.core.storage.BaseCurrencyStore
import app.expenses.core.storage.CardStore
import app.expenses.core.storage.CaptureRuleStore
import app.expenses.core.storage.InboxStore
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.storage.SavedSearchStore
import app.expenses.core.storage.SchemaUpgrade
import app.expenses.core.storage.FtsTuning
import app.expenses.core.storage.LedgerStore
import app.expenses.core.storage.TemplateStore
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The one place that owns the encrypted database for the whole app process. The screen, the Quick Settings tile, a
 * widget or a background receiver all go through this, so there is never a second connection to the same file.
 * Opening happens once, off the main thread.
 */
class AppContainer(private val context: Context) {
    sealed interface Opened {
        class Ok(val driver: SqlDriver, val db: ExpensesDb, val store: LedgerStore, val templates: TemplateStore, val categories: CategoryStore, val budgets: BudgetStore, val recurring: RecurringStore, val notifications: NotificationService, val backup: BackupStore, val csv: CsvExport, val reports: ReportStore, val statements: StatementImporter, val savedSearches: SavedSearchStore, val attachments: AttachmentStore, val currency: BaseCurrencyStore, val cards: CardStore, val inbox: InboxStore, val captureRules: CaptureRuleStore) : Opened

        /** The database exists but its key is gone. Nothing is created or deleted until the user chooses. */
        class KeyLost(val message: String) : Opened
    }

    private val mutex = Mutex()
    @Volatile private var opened: Opened? = null

    suspend fun open(): Opened = mutex.withLock { opened ?: withContext(Dispatchers.IO) { doOpen() }.also { opened = it } }

    private val writeLock = Mutex()

    /**
     * Merges full-text index segments in short steps while the app is idle (called when the screen goes to the
     * background). Each step takes the write lock for at most about 70 ms, so a widget tap in between is never held up
     * for long, and the work stops after [budgetMillis] or when nothing is left. Returns the steps performed.
     */
    suspend fun maintain(budgetMillis: Long = 2_000): Int {
        val ok = open() as? Opened.Ok ?: return 0
        val deadline = System.nanoTime() + budgetMillis * 1_000_000
        var steps = 0
        while (System.nanoTime() < deadline) {
            val more = writeLock.withLock { withContext(Dispatchers.IO) { FtsTuning.mergeStep(ok.driver) } }
            if (!more) break
            steps++
        }
        return steps
    }

    /**
     * Runs [block] on the open store, off the main thread, one at a time across the whole process. Everything that
     * writes (screens, the widget, later a background receiver) must go through here, because [LedgerStore] is not
     * thread-safe. Returns null if the database key is lost.
     */
    suspend fun <T> write(block: (Opened.Ok) -> T): T? {
        val ok = open() as? Opened.Ok ?: return null
        return writeLock.withLock { withContext(Dispatchers.IO) { block(ok) } }
    }

    /** Closes the database and forgets it, so the next [open] opens it again from disk. Used when tearing the app down in tests. */
    suspend fun close() = mutex.withLock {
        withContext(Dispatchers.IO) { (opened as? Opened.Ok)?.let { runCatching { it.driver.close() } } }
        opened = null
    }

    /** Deletes everything and starts an empty ledger. Only after the user has confirmed on screen. */
    suspend fun startFresh(): Opened = mutex.withLock {
        withContext(Dispatchers.IO) {
            (opened as? Opened.Ok)?.let { runCatching { it.driver.close() } }
            EncryptedDriver.reset(context)
            doOpen().also { opened = it }
        }
    }

    private fun doOpen(): Opened = try {
        StartupTrace.mark("open: begin")
        val driver = EncryptedDriver.create(context)
        SchemaUpgrade.ensureLatest(driver) // a database made by an earlier build gets the columns and tables added since
        val db = ExpensesDb(driver)
        FtsTuning.apply(driver)
        if (BuildConfig.SEEDING_ENABLED) db.schemaQueries.entryCount().executeAsOne() // forces the real open so the mark below is honest
        StartupTrace.mark("open: database opened")
        val clock = HlcClock(nodeId())
        val store = LedgerStore(db, clock, System::currentTimeMillis)
        val templates = TemplateStore(db, clock, System::currentTimeMillis)
        val currency = BaseCurrencyStore(db, clock, System::currentTimeMillis, templates, store)
        BaseCurrency.current = currency.current() // before anything formats or parses an amount
        if (db.schemaQueries.selectAccounts().executeAsList().isEmpty()) {
            store.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, BaseCurrency.current))
            store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, BaseCurrency.current))
        }
        StartupTrace.mark("open: store ready")
        val recurring = RecurringStore(db, clock, System::currentTimeMillis, store)
        val cardStore = CardStore(db, clock, System::currentTimeMillis)
        val budgets = BudgetStore(db, clock, System::currentTimeMillis, store, recurring)
        Opened.Ok(driver, db, store, templates, CategoryStore(db, clock, System::currentTimeMillis), budgets, recurring, NotificationService(db, recurring, budgets, cardStore, ReportStore(db, store)), BackupStore(db, clock, System::currentTimeMillis, store, templates, recurring), CsvExport(db), ReportStore(db, store), StatementImporter(db, store), SavedSearchStore(db, clock, System::currentTimeMillis), AttachmentStore(db, clock, System::currentTimeMillis), currency, cardStore, InboxStore(db), CaptureRuleStore(db))
    } catch (e: DatabaseKeyUnavailableException) {
        Opened.KeyLost(e.message ?: "The database key is unavailable")
    }

    /** A random id for this install, used only to make sync-log timestamps unique per device. */
    private fun nodeId(): String {
        val f = File(context.noBackupFilesDir, "node_id")
        return if (f.exists()) f.readText() else UUID.randomUUID().toString().take(8).also { f.writeText(it) }
    }
}
