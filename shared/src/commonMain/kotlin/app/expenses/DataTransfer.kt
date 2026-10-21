package app.expenses

import app.expenses.core.storage.BackupCounts
import app.expenses.core.storage.BackupData
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.BackupHeader
import app.expenses.core.storage.RestoreReport
import app.expenses.core.storage.Sha256
import kotlinx.datetime.TimeZone

/**
 * Export, backup and restore through files the user picked with the system file picker. A file that was written is read back and
 * compared before it is reported as saved, so "Backup saved" never means "probably saved". The Android app's `DataActions` wraps this.
 */
class DataTransfer(private val platform: Platform, private val container: AppContainer, private val now: () -> Long = ::nowMillis) {
    /** A backup file that was opened: either it needs a passphrase first, or it has been checked and previewed. */
    sealed interface Opened {
        class NeedsPassphrase(val bytes: ByteArray) : Opened
        class Ready(val header: BackupHeader, val data: BackupData, val report: RestoreReport) : Opened
    }

    companion object {
        const val SETTING_BACKUP_AT = "backup_last_at"
        const val SETTING_BACKUP_COUNT = "backup_last_count"
    }

    class Saved(val bytes: Int, val counts: BackupCounts, val encrypted: Boolean)

    /** Writes every entry as a CSV. Returns the number of entries. */
    suspend fun exportCsv(uri: String): Int {
        val bytes = container.write { it.csv.bytes(TimeZone.currentSystemDefault()) } ?: throw BackupException("The data is not available.")
        writeAndVerify(uri, bytes)
        return bytes.decodeToString().lines().count { it.isNotEmpty() } - 1
    }

    /** Writes every entry as an Excel workbook. Returns the number of entries. */
    suspend fun exportXlsx(uri: String): Int {
        val (bytes, count) = container.write { ok -> ok.csv.xlsx(TimeZone.currentSystemDefault()) to ok.db.schemaQueries.entryCount().executeAsOne() } ?: throw BackupException("The data is not available.")
        writeAndVerify(uri, bytes)
        return count.toInt()
    }

    private suspend fun backupContents(passphrase: String?): Triple<ByteArray, BackupCounts, Boolean> {
        val (plain, counts) = container.write { ok ->
            val data = ok.backup.export()
            BackupFile.encode(data, now()) to BackupCounts(data.accounts.size, data.categories.size, data.entries.size, data.templates.size, data.budgets.size, data.recurring.size)
        } ?: throw BackupException("The data is not available.")
        val cipher = platform.backupCipher
        return Triple(if (passphrase != null) cipher.encrypt(plain, passphrase) else plain, counts, passphrase != null)
    }

    /**
     * A full backup as bytes, for handing to another app (the share sheet: Drive, email, a chat), checked the way a restore would. It is not recorded as
     * a backup made: there is no telling whether the person finished sending it.
     */
    suspend fun backupForSharing(passphrase: String?): Pair<ByteArray, Saved> {
        val (bytes, counts, encrypted) = backupContents(passphrase)
        BackupFile.decode(if (passphrase != null) platform.backupCipher.decrypt(bytes, passphrase) else bytes)
        return bytes to Saved(bytes.size, counts, encrypted)
    }

    /** Writes a full backup, encrypted when [passphrase] is not null. */
    suspend fun backup(uri: String, passphrase: String?): Saved {
        val (bytes, counts, _) = backupContents(passphrase)
        val cipher = platform.backupCipher
        writeAndVerify(uri, bytes)
        // Check the file the way a restore would, so a backup that could not be restored is found out now.
        val back = if (passphrase != null) cipher.decrypt(read(uri), passphrase) else read(uri)
        BackupFile.decode(back)
        // Remember that it worked, so the reminder to make a backup stops. Only now: a backup that failed above must not count.
        container.write { ok ->
            ok.templates.putSetting(SETTING_BACKUP_AT, now().toString())
            ok.templates.putSetting(SETTING_BACKUP_COUNT, counts.entries.toString())
        }
        return Saved(bytes.size, counts, passphrase != null)
    }

    /** Opens a backup file and checks it. Nothing on the device changes. */
    suspend fun open(uri: String, passphrase: String?): Opened = openBytes(read(uri), passphrase)

    suspend fun openBytes(bytes: ByteArray, passphrase: String?): Opened {
        val cipher = platform.backupCipher
        val plain = if (cipher.isEncrypted(bytes)) {
            if (passphrase == null) return Opened.NeedsPassphrase(bytes)
            cipher.decrypt(bytes, passphrase)
        } else bytes
        val (header, data) = BackupFile.decode(plain)
        val report = container.write { it.backup.preview(data) } ?: throw BackupException("The data is not available.")
        return Opened.Ready(header, data, report)
    }

    /** Merges the opened backup into this device. Everything already here is left as it is. */
    suspend fun restore(data: BackupData): RestoreReport =
        container.write { ok -> ok.backup.restore(data).also { app.expenses.core.domain.BaseCurrency.current = ok.currency.current() } } ?: throw BackupException("The data is not available.")

    private fun read(uri: String): ByteArray =
        try { platform.readBytes(uri) } catch (e: IllegalArgumentException) { throw BackupException(e.message ?: "That file could not be opened.") }

    private fun writeAndVerify(uri: String, bytes: ByteArray) {
        try { platform.writeBytes(uri, bytes) } catch (e: IllegalArgumentException) { throw BackupException(e.message ?: "That location could not be written to.") }
        val back = read(uri)
        if (back.size != bytes.size || Sha256.hex(back) != Sha256.hex(bytes)) throw BackupException("The file was not saved correctly. Try another location.")
    }
}
