package app.expenses

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.DatabaseKeyUnavailableException
import app.expenses.core.storage.EncryptedDriver
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
        class Ok(val driver: SqlDriver, val db: ExpensesDb, val store: LedgerStore, val templates: TemplateStore) : Opened

        /** The database exists but its key is gone. Nothing is created or deleted until the user chooses. */
        class KeyLost(val message: String) : Opened
    }

    private val mutex = Mutex()
    @Volatile private var opened: Opened? = null

    suspend fun open(): Opened = mutex.withLock { opened ?: withContext(Dispatchers.IO) { doOpen() }.also { opened = it } }

    private val writeLock = Mutex()

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
        val db = ExpensesDb(driver)
        if (BuildConfig.SEEDING_ENABLED) db.schemaQueries.entryCount().executeAsOne() // forces the real open so the mark below is honest
        StartupTrace.mark("open: database opened")
        val clock = HlcClock(nodeId())
        val store = LedgerStore(db, clock, System::currentTimeMillis)
        if (db.schemaQueries.selectAccounts().executeAsList().isEmpty()) {
            store.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR))
            store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR))
        }
        StartupTrace.mark("open: store ready")
        Opened.Ok(driver, db, store, TemplateStore(db, clock, System::currentTimeMillis))
    } catch (e: DatabaseKeyUnavailableException) {
        Opened.KeyLost(e.message ?: "The database key is unavailable")
    }

    /** A random id for this install, used only to make sync-log timestamps unique per device. */
    private fun nodeId(): String {
        val f = File(context.noBackupFilesDir, "node_id")
        return if (f.exists()) f.readText() else UUID.randomUUID().toString().take(8).also { f.writeText(it) }
    }
}
