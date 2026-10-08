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

/** A folder the user chose, as far as automatic export cares. [write] returns the name the file really got (the system may add " (1)"), or null. */
interface ExportFolder {
    fun names(): List<String>
    fun write(name: String, mime: String, bytes: ByteArray): String?
    fun read(name: String): ByteArray?
    fun delete(name: String): Boolean
}

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
    const val KEEP = 7
    const val BACKUP_PREFIX = "expenses-backup"
    const val XLSX_PREFIX = "expenses"
    const val S_FOLDER = "auto_export_folder"
    const val S_FREQ = "auto_export_freq"
    const val S_LAST_AT = "auto_export_last_at"
    const val S_STATUS = "auto_export_status"

    data class Result(val ran: Boolean, val ok: Boolean, val text: String)

    private fun dayOf(now: Long) = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Runs the export if it is due (or [force]d). Never throws: the outcome is recorded for the screen and returned. */
    suspend fun run(container: AppContainer, folder: ExportFolder?, now: Long, force: Boolean = false): Result {
        val settings = container.write { ok -> Triple(ok.templates.setting(S_FREQ), ok.templates.setting(S_LAST_AT)?.toLongOrNull(), ok.templates.setting(S_FOLDER)) } ?: return Result(false, false, "The data is not available.")
        val freq = AutoExportFrequency.entries.firstOrNull { it.name == settings.first } ?: AutoExportFrequency.OFF
        if (!force && !AutoExportRules.isDue(freq, settings.second, now)) return Result(false, true, "")
        if (settings.third == null || folder == null) return record(container, now, Result(true, false, "Choose a folder first."), false)
        return try {
            val day = dayOf(now)
            val (backup, xlsx) = container.write { ok -> BackupFile.encode(ok.backup.export(), now) to ok.csv.xlsx(TimeZone.currentSystemDefault()) } ?: throw IllegalStateException("The data is not available.")
            val files = listOf(Triple("$BACKUP_PREFIX-$day.expbak", "application/octet-stream", backup), Triple("$XLSX_PREFIX-$day.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
            for ((name, mime, bytes) in files) {
                // A file of the same name from earlier today is replaced, so running twice in a day leaves one file, not two.
                if (name in folder.names()) folder.delete(name)
                val written = folder.write(name, mime, bytes) ?: throw IllegalStateException("The folder could not be written to.")
                val back = folder.read(written)
                if (back == null || back.size != bytes.size || Sha256.hex(back) != Sha256.hex(bytes)) throw IllegalStateException("A file was not saved correctly.")
            }
            val names = folder.names()
            AutoExportRules.filesToDelete(names, BACKUP_PREFIX, "expbak", KEEP).forEach { folder.delete(it) }
            AutoExportRules.filesToDelete(names, XLSX_PREFIX, "xlsx", KEEP).forEach { folder.delete(it) }
            record(container, now, Result(true, true, "Saved $day: a backup and an Excel file."), true)
        } catch (e: Exception) {
            record(container, now, Result(true, false, "Could not save: ${e.message ?: e::class.java.simpleName}"), false)
        }
    }

    private suspend fun record(container: AppContainer, now: Long, r: Result, success: Boolean): Result {
        container.write { ok ->
            ok.templates.putSetting(S_STATUS, (if (r.ok) "ok|" else "fail|") + r.text)
            if (success) ok.templates.putSetting(S_LAST_AT, now.toString())
        }
        return r
    }

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
