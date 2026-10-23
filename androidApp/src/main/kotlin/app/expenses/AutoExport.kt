package app.expenses

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.domain.AutoExportRules
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.Sha256
import kotlinx.datetime.TimeZone
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** The user's chosen folder through the system's document provider (Drive, local storage, ...). The permission was kept when it was chosen. */
class SafFolder(private val context: Context, private val tree: Uri) : ExportFolder {
    private val cr get() = context.contentResolver
    private val treeId get() = DocumentsContract.getTreeDocumentId(tree)
    private fun child(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun entries(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out += c.getString(0) to c.getString(1)
        } ?: throw IllegalStateException("The folder cannot be read. It may have been removed, or the permission was withdrawn.")
        return out
    }

    override fun names() = entries().map { it.second }
    override fun write(name: String, mime: String, bytes: ByteArray): String? {
        val uri = DocumentsContract.createDocument(cr, child(treeId), mime, name) ?: return null
        cr.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: return null
        return entries().firstOrNull { child(it.first) == uri }?.second ?: name
    }
    override fun read(name: String): ByteArray? = entries().firstOrNull { it.second == name }?.let { cr.openInputStream(child(it.first))?.use { s -> s.readBytes() } }
    override fun delete(name: String): Boolean = entries().firstOrNull { it.second == name }?.let { runCatching { DocumentsContract.deleteDocument(cr, child(it.first)) }.getOrDefault(false) } ?: false
}

/**
 * Writes a backup file and an Excel workbook into a folder the user chose, on a schedule, and keeps only the newest few of each. The files
 * are not encrypted (a passphrase cannot be stored, and one asked for every time would not be automatic); the screen says so. Each file is
 * read back and compared before the run counts, so a half-written file is reported, not recorded as a success.
 */
object AutoExport {
    const val KEEP = AutoExportKeys.KEEP
    const val BACKUP_PREFIX = AutoExportKeys.BACKUP_PREFIX
    const val XLSX_PREFIX = AutoExportKeys.XLSX_PREFIX
    const val S_FOLDER = AutoExportKeys.FOLDER
    const val S_FREQ = AutoExportKeys.FREQ
    const val S_LAST_AT = AutoExportKeys.LAST_AT
    const val S_STATUS = AutoExportKeys.STATUS

    data class Result(val ran: Boolean, val ok: Boolean, val text: String)

    /** Runs the export if it is due (or [force]d). Never throws: the outcome is recorded for the screen and returned. The work is [AutoExportRunner], shared with iPhone. */
    suspend fun run(container: AppContainer, folder: ExportFolder?, now: Long, force: Boolean = false): Result =
        AutoExportRunner.run(container, folder, now, force).let { Result(it.ran, it.ok, it.text) }

    fun folderOf(context: Context, uriText: String?): ExportFolder? = uriText?.let { SafFolder(context, Uri.parse(it)) }
}

/** Checks twice a day whether an automatic export is due. Cheap when nothing is due. */
class AutoExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ExpensesApp).container
        val uri = container.write { it.templates.setting(AutoExport.S_FOLDER) }
        AutoExport.run(container, AutoExport.folderOf(applicationContext, uri), System.currentTimeMillis())
        return Result.success() // a failure is recorded on screen; retrying in a loop would only repeat it
    }

    companion object {
        private const val NAME = "auto-export"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoExportWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
