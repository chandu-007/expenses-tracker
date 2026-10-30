package app.expenses

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
import app.expenses.core.storage.AttachmentStore
import app.expenses.core.storage.BaseCurrencyStore
import app.expenses.core.storage.CardStore
import app.expenses.core.storage.CaptureRuleStore
import app.expenses.core.storage.InboxStore
import app.expenses.core.storage.OriginStore
import app.expenses.core.storage.AccountLookStore
import app.expenses.core.storage.BillStore
import app.expenses.core.storage.PeopleStore
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.storage.SavedSearchStore
import app.expenses.core.storage.SchemaUpgrade
import app.expenses.core.storage.FtsTuning
import app.expenses.core.storage.LedgerStore
import app.expenses.core.storage.TemplateStore
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * The one place that owns the encrypted database for the whole app process. The screen, the Quick Settings tile, a
 * widget or a background receiver all go through this, so there is never a second connection to the same file.
 * Opening happens once, off the main thread.
 */
class AppContainer(private val platform: Platform) {
    init { app.expenses.core.storage.BackupFile.maxPayloadBytes = platform.maxBackupPayloadBytes }

    /** Where the bytes of attachments are kept: what a backup reads its photos and files from. */
    val attachmentFiles: AttachmentFileStore get() = platform.attachmentFiles

    /** The primitives that seal backups with passphrases, for the streaming layout. */
    val backupPrimitives get() = platform.backupCipher.primitives

    /** Where the key of passphrase-protected automatic copies is kept; null where the platform has no secure storage. */
    val secretStore get() = platform.secretStore

    sealed interface Opened {
        class Ok(val driver: SqlDriver, val db: ExpensesDb, val store: LedgerStore, val templates: TemplateStore, val categories: CategoryStore, val budgets: BudgetStore, val recurring: RecurringStore, val notifications: NotificationService, val backup: BackupStore, val csv: CsvExport, val reports: ReportStore, val statements: StatementImporter, val savedSearches: SavedSearchStore, val attachments: AttachmentStore, val currency: BaseCurrencyStore, val cards: CardStore, val inbox: InboxStore, val captureRules: CaptureRuleStore, val people: PeopleStore, val bills: BillStore, val looks: AccountLookStore, val origins: OriginStore) : Opened {
            /** What the person expects back for expenses ("I expect this back"): a side table, so it needs only the database. */
            val expects: app.expenses.core.storage.ExpectStore = app.expenses.core.storage.ExpectStore(db)

            /** "This is a transfer between my own accounts": two waiting texts become one transfer, or a waiting text replaces the earlier expense or income it is the other half of. */
            val transfers: app.expenses.core.storage.CaptureTransfers = app.expenses.core.storage.CaptureTransfers(db, store, inbox, origins)

            /** "Change the type" of an expense, income or transfer: the entry is replaced by one of the new type, with an exact Undo. */
            val kindChanges: app.expenses.core.storage.KindChanges = app.expenses.core.storage.KindChanges(db, store, origins, expects, attachments)
        }

        /** The database exists but its key is gone. Nothing is created or deleted until the user chooses. */
        class KeyLost(val message: String) : Opened
    }

    private val mutex = Mutex()
    @Volatile private var opened: Opened? = null

    suspend fun open(): Opened = mutex.withLock { opened ?: withContext(ioDispatcher) { doOpen() }.also { opened = it } }

    private val writeLock = Mutex()

    /**
     * Merges full-text index segments in short steps while the app is idle (called when the screen goes to the
     * background). Each step takes the write lock for at most about 70 ms, so a widget tap in between is never held up
     * for long, and the work stops after [budgetMillis] or when nothing is left. Returns the steps performed.
     */
    suspend fun maintain(budgetMillis: Long = 2_000): Int {
        val started = TimeSource.Monotonic.markNow()
        var steps = 0
        while (started.elapsedNow().inWholeMilliseconds < budgetMillis) {
            // the database is resolved after the write lock is held, so a close in between cannot hand this a shut driver
            val more = writeLock.withLock { val ok = open() as? Opened.Ok ?: return 0; withContext(ioDispatcher) { FtsTuning.mergeStep(ok.driver) } }
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
        // Lock order everywhere: the write lock first, then the open mutex. The database is resolved only once the write lock is held, so [close] and
        // [startFresh] (which hold it too) can never shut the driver between this block getting it and running.
        return writeLock.withLock {
            val ok = open() as? Opened.Ok ?: return@withLock null
            withContext(ioDispatcher) { block(ok) }
        }
    }

    /** Closes the database and forgets it, so the next [open] opens it again from disk. Used when tearing the app down in tests. */
    suspend fun close() = writeLock.withLock {
        mutex.withLock {
            withContext(ioDispatcher) { (opened as? Opened.Ok)?.let { runCatching { it.driver.close() } } }
            opened = null
        }
    }

    /** Deletes everything and starts an empty ledger. Only after the user has confirmed on screen. */
    suspend fun startFresh(): Opened = writeLock.withLock {
        mutex.withLock {
            withContext(ioDispatcher) {
                (opened as? Opened.Ok)?.let { runCatching { it.driver.close() } }
                platform.resetDatabase()
                // someone who wipes their ledger already knows the app: the guide is for a first install and for updates, not for starting over
                doOpen().also { opened = it; (it as? Opened.Ok)?.templates?.putSetting(GuideSettings.SEEN, app.expenses.core.domain.Guide.VERSION.toString()) }
            }
        }
    }

    private fun doOpen(): Opened = try {
        platform.trace("open: begin")
        val driver = platform.openDriver()
        SchemaUpgrade.ensureLatest(driver) // a database made by an earlier build gets the columns and tables added since
        val db = ExpensesDb(driver)
        FtsTuning.apply(driver)
        if (platform.seedingEnabled) db.schemaQueries.entryCount().executeAsOne() // forces the real open so the mark below is honest
        platform.trace("open: database opened")
        val clock = HlcClock(platform.nodeId())
        val store = LedgerStore(db, clock, ::nowMillis)
        val templates = TemplateStore(db, clock, ::nowMillis)
        val currency = BaseCurrencyStore(db, clock, ::nowMillis, templates, store)
        BaseCurrency.current = currency.current() // before anything formats or parses an amount
        if (db.schemaQueries.selectAccounts().executeAsList().isEmpty()) {
            store.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, BaseCurrency.current))
            store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, BaseCurrency.current))
        }
        platform.trace("open: store ready")
        val recurring = RecurringStore(db, clock, ::nowMillis, store)
        val cardStore = CardStore(db, clock, ::nowMillis)
        val budgets = BudgetStore(db, clock, ::nowMillis, store, recurring)
        val billStore = BillStore(db)
        Opened.Ok(driver, db, store, templates, CategoryStore(db, clock, ::nowMillis), budgets, recurring, NotificationService(db, recurring, budgets, cardStore, ReportStore(db, store), billStore), BackupStore(db, clock, ::nowMillis, store, templates, recurring), CsvExport(db), ReportStore(db, store), StatementImporter(db, store), SavedSearchStore(db, clock, ::nowMillis), AttachmentStore(db, clock, ::nowMillis), currency, cardStore, InboxStore(db), CaptureRuleStore(db), PeopleStore(db), billStore, AccountLookStore(db, clock, ::nowMillis), OriginStore(db))
    } catch (e: DatabaseKeyUnavailableException) {
        Opened.KeyLost(e.message ?: "The database key is unavailable")
    }
}
