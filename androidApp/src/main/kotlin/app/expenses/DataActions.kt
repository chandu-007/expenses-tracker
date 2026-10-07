package app.expenses

import android.content.Context
import android.net.Uri
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
 * compared before it is reported as saved, so "Backup saved" never means "probably saved".
 */
class DataActions(private val context: Context, private val container: AppContainer, private val now: () -> Long = System::currentTimeMillis) {
    /** A backup file that was opened: either it needs a passphrase first, or it has been checked and previewed. */
    sealed interface Opened {
        class NeedsPassphrase(val bytes: ByteArray) : Opened
        class Ready(val header: BackupHeader, val data: BackupData, val report: RestoreReport) : Opened
    }

    class Saved(val bytes: Int, val counts: BackupCounts, val encrypted: Boolean)

    /** Writes every entry as a CSV. Returns the number of entries. */
    suspend fun exportCsv(uri: Uri): Int {
        val bytes = container.write { it.csv.bytes(TimeZone.currentSystemDefault()) } ?: throw BackupException("The data is not available.")
        writeAndVerify(uri, bytes)
        return bytes.decodeToString().lines().count { it.isNotEmpty() } - 1
    }

    /** Writes a full backup, encrypted when [passphrase] is not null. */
    suspend fun backup(uri: Uri, passphrase: String?): Saved {
        val (plain, counts) = container.write { ok ->
            val data = ok.backup.export()
            BackupFile.encode(data, now()) to BackupCounts(data.accounts.size, data.categories.size, data.entries.size, data.templates.size, data.budgets.size, data.recurring.size)
        } ?: throw BackupException("The data is not available.")
        val bytes = if (passphrase != null) BackupCrypto.encrypt(plain, passphrase) else plain
        writeAndVerify(uri, bytes)
        // Check the file the way a restore would, so a backup that could not be restored is found out now.
        val back = if (passphrase != null) BackupCrypto.decrypt(read(uri), passphrase) else read(uri)
        BackupFile.decode(back)
        return Saved(bytes.size, counts, passphrase != null)
    }

    /** Opens a backup file and checks it. Nothing on the device changes. */
    suspend fun open(uri: Uri, passphrase: String?): Opened = openBytes(read(uri), passphrase)

    suspend fun openBytes(bytes: ByteArray, passphrase: String?): Opened {
        val plain = if (BackupCrypto.isEncrypted(bytes)) {
            if (passphrase == null) return Opened.NeedsPassphrase(bytes)
            BackupCrypto.decrypt(bytes, passphrase)
        } else bytes
        val (header, data) = BackupFile.decode(plain)
        val report = container.write { it.backup.preview(data) } ?: throw BackupException("The data is not available.")
        return Opened.Ready(header, data, report)
    }

    /** Merges the opened backup into this device. Everything already here is left as it is. */
    suspend fun restore(data: BackupData): RestoreReport =
        container.write { it.backup.restore(data) } ?: throw BackupException("The data is not available.")

    private fun read(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BackupException("That file could not be opened.")

    private fun writeAndVerify(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: throw BackupException("That location could not be written to.")
        val back = read(uri)
        if (back.size != bytes.size || Sha256.hex(back) != Sha256.hex(bytes)) throw BackupException("The file was not saved correctly. Try another location.")
    }
}
