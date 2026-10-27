package app.expenses

import android.content.Context
import android.net.Uri
import app.expenses.core.storage.BackupData
import app.expenses.core.storage.BackupHeader
import app.expenses.core.storage.BackupReader
import app.expenses.core.storage.RestoreReport

/**
 * Export, backup and restore with a content [Uri]: the Android face of the shared [DataTransfer], which holds the logic.
 */
class DataActions(context: Context, container: AppContainer, now: () -> Long = System::currentTimeMillis) {
    private val transfer = DataTransfer(AndroidPlatform(context), container, now)

    /** A backup file that was opened: either it needs a passphrase first, or it has been checked and previewed. */
    sealed interface Opened {
        class NeedsPassphrase(val uri: String) : Opened
        class Ready(val header: BackupHeader, val data: BackupData, val report: RestoreReport, val reopen: (() -> BackupReader)? = null) : Opened
    }

    companion object {
        const val SETTING_BACKUP_AT = DataTransfer.SETTING_BACKUP_AT
        const val SETTING_BACKUP_COUNT = DataTransfer.SETTING_BACKUP_COUNT
    }

    suspend fun exportCsv(uri: Uri): Int = transfer.exportCsv(uri.toString())
    suspend fun exportXlsx(uri: Uri): Int = transfer.exportXlsx(uri.toString())
    suspend fun exportFinancialYear(uri: Uri, yearsBack: Int): String = transfer.exportFinancialYear(uri.toString(), yearsBack)
    suspend fun backup(uri: Uri, passphrase: String?): DataTransfer.Saved = transfer.backup(uri.toString(), passphrase)
    suspend fun open(uri: Uri, passphrase: String?): Opened = transfer.open(uri.toString(), passphrase).let(::wrap)
    suspend fun restore(data: BackupData): RestoreReport = transfer.restore(data)

    /** Restores an opened backup with its photos and files. */
    suspend fun restore(opened: Opened.Ready): DataTransfer.Restored = transfer.restore(DataTransfer.Opened.Ready(opened.header, opened.data, opened.report, opened.reopen))

    private fun wrap(o: DataTransfer.Opened): Opened = when (o) {
        is DataTransfer.Opened.NeedsPassphrase -> Opened.NeedsPassphrase(o.uri)
        is DataTransfer.Opened.Ready -> Opened.Ready(o.header, o.data, o.report, o.reopen)
    }
}
