package app.expenses

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.Sha256
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.storage.readAll
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL

/**
 * True only while the process is running under XCTest (the test runner sets `XCTestConfigurationFilePath`). The debug entries in this file and in
 * [IosAutoExportFilesDebug] write and remove real files and data, and they ship in the framework, so outside a test run they do nothing.
 */
internal fun runningUnderXCTest(): Boolean = NSProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != null

private const val ONLY_IN_TESTS = "refused: this is a test entry and does nothing outside a test run"

/** The app's platform with a throw-away in-memory database, so a test can restore and back up without touching the person's data; files go through the real attachment store. */
private class ScratchPlatform(base: IosPlatform) : Platform by base {
    override fun openDriver(): SqlDriver = NativeSqliteDriver(ExpensesDb.Schema, "scratch-backup-test.db", onConfiguration = { it.copy(inMemory = true) })
}

/**
 * Entry points for the Swift unit tests of backups that carry photos and files: the whole path the app uses (the Files-style streams, the sealed layout on
 * CryptoKit, the attachment store), run against a scratch database. The sample files are the ones tools/backup_interop/Interop.java makes and verifies:
 * file k (1 to 4) has byte i = (i * 31 + k * 7) mod 256. Answers are plain sentences, because a Kotlin exception cannot be caught from Swift.
 */
object IosBackupDebug {
    /** How fast the portable SHA-256 (the part of a large backup's time that is the app's own code) runs here: 32 MB hashed, as a sentence. */
    fun sha256Speed(): String {
        val block = ByteArray(64 * 1024) { it.toByte() }
        val digest = Sha256.Digest()
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        repeat(512) { digest.update(block, 0, block.size) }
        digest.finishHex()
        val ms = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(1)
        return "32 MB in $ms ms = ${32_000 / ms} MB/s"
    }

    private val sizes = intArrayOf(70_000, 65_536, 1, 3_000)
    private fun pattern(k: Int, size: Int) = ByteArray(size) { (it * 31 + k * 7).toByte() }
    private fun uriOf(path: String) = NSURL.fileURLWithPath(path).absoluteString.orEmpty()

    /** Opens the backup at [path] (sealed when [passphrase] is not null), restores it into a scratch database, reads every restored file back and compares it with the sample pattern. */
    suspend fun restoreThroughTheApp(host: IosHostServices, path: String, passphrase: String?): String {
        if (!runningUnderXCTest()) return ONLY_IN_TESTS
        val platform = ScratchPlatform(IosCore.get(host).platform)
        val container = AppContainer(platform)
        val transfer = DataTransfer(platform, container)
        val vault = platform.attachmentFiles
        var ids = emptyList<String>()
        return try {
            when (val opened = transfer.open(uriOf(path), passphrase)) {
                is DataTransfer.Opened.NeedsPassphrase -> "needs a passphrase"
                is DataTransfer.Opened.Ready -> {
                    ids = opened.data.attachments.map { it.id }
                    val restored = transfer.restore(opened)
                    var matched = 0
                    opened.data.attachments.forEachIndexed { i, a ->
                        val source = vault.open(a.id) ?: return@forEachIndexed
                        val bytes = try { source.readAll() } finally { source.close() }
                        if (bytes.contentEquals(pattern(i + 1, a.sizeBytes.toInt())) && Sha256.hex(bytes) == a.sha256) matched++
                    }
                    val f = restored.files
                    "restored: entries=${restored.report.added.entries} files=${restored.report.attached.size} matched=$matched damaged=${f?.damaged ?: 0} missing=${f?.missing ?: 0}"
                }
            }
        } catch (e: BackupException) {
            "refused: ${e.message}"
        } catch (e: Throwable) {
            "failed: ${e::class.simpleName}: ${e.message}"
        } finally {
            ids.forEach(vault::delete)
            runCatching { container.close() }
        }
    }

    /**
     * Makes a backup with four sample files through [DataTransfer.backup] (a scratch database, the real attachment store, the Files-style stream, the sealed layout
     * when [passphrase] is not null) and writes it to [path], read back and checked as the app does. [path] is then read by the Java reference.
     */
    suspend fun backupThroughTheApp(host: IosHostServices, path: String, passphrase: String?): String {
        if (!runningUnderXCTest()) return ONLY_IN_TESTS
        val platform = ScratchPlatform(IosCore.get(host).platform)
        val container = AppContainer(platform)
        val vault = platform.attachmentFiles
        val ids = (1..sizes.size).map { "att-$it" }
        return try {
            container.write { ok ->
                ok.store.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR))
                for (e in 1..2) ok.store.save(Expense(EntryId("e$e"), e.toLong(), AccountId("cash"), Money(500L + e, Currency.INR)), EntryDetails(merchant = "Sample $e"))
            }
            for (k in 1..sizes.size) {
                val bytes = pattern(k, sizes[k - 1])
                vault.create(ids[k - 1]).also { it.write(bytes, 0, bytes.size) }.close()
                container.write { ok -> ok.attachments.add(ids[k - 1], if (k <= 2) "e1" else "e2", "file$k.bin", "application/octet-stream", bytes.size.toLong(), Sha256.hex(bytes)) }
            }
            val saved = DataTransfer(platform, container).backup(uriOf(path), passphrase)
            "ok: ${saved.bytes} bytes, ${saved.files.included} files, ${saved.counts.entries} entries"
        } catch (e: Throwable) {
            "failed: ${e::class.simpleName}: ${e.message}"
        } finally {
            ids.forEach(vault::delete)
            runCatching { container.close() }
        }
    }
}
