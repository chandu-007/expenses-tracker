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
import app.expenses.core.storage.ByteSource
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
    override fun openWrite(name: String, mime: String): FolderWriter? {
        val uri = DocumentsContract.createDocument(cr, child(treeId), mime, name) ?: return null
        val out = cr.openOutputStream(uri, "wt") ?: return null
        val requested = name
        return object : FolderWriter {
            override var name: String = requested
                private set
            private val sink = OutputStreamSink(out.buffered(64 * 1024))
            override fun write(buf: ByteArray, off: Int, len: Int) = sink.write(buf, off, len)
            override fun close() {
                sink.close()
                this.name = entries().firstOrNull { child(it.first) == uri }?.second ?: this.name
            }
        }
    }

    private fun flagsOf(documentId: String): Int =
        cr.query(child(documentId), arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } ?: 0

    /**
     * Whether this provider renames documents (`FLAG_SUPPORTS_RENAME`), asked of a document in the folder, or of a small one created and removed again when the
     * folder is empty. Where it does not, a copy is written under its real name from the start (no temporary copy, so nothing is uploaded twice).
     */
    override val canRename: Boolean by lazy {
        try {
            val existing = entries().firstOrNull { !it.second.endsWith(".part") && !it.second.endsWith(".old") }
            if (existing != null) flagsOf(existing.first) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
            else {
                val probe = DocumentsContract.createDocument(cr, child(treeId), "application/octet-stream", "expenses-probe.tmp")
                if (probe == null) false else try {
                    cr.query(probe, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c -> c.moveToFirst() && c.getInt(0) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0 } ?: false
                } finally {
                    runCatching { DocumentsContract.deleteDocument(cr, probe) }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Renames through the document provider and returns the name the document ended up with, which a provider may change (" (1)"); null when it cannot rename. */
    override fun rename(name: String, newName: String): String? = try {
        val doc = entries().firstOrNull { it.second == name }
        if (doc == null || flagsOf(doc.first) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME == 0) null
        else {
            val moved = DocumentsContract.renameDocument(cr, child(doc.first), newName)
            // the provider may have given the document another name than asked for (" (1)"): the caller compares
            if (moved == null) null else {
                val id = DocumentsContract.getDocumentId(moved)
                entries().firstOrNull { it.first == id }?.second ?: newName
            }
        }
    } catch (e: Exception) {
        null
    }

    override fun openRead(name: String): ByteSource? =
        entries().firstOrNull { it.second == name }?.let { e -> cr.openInputStream(child(e.first))?.let { InputStreamSource(it.buffered(64 * 1024)) } }

    override fun read(name: String): ByteArray? = entries().firstOrNull { it.second == name }?.let { cr.openInputStream(child(it.first))?.use { s -> s.readBytes() } }
    override fun delete(name: String): Boolean = entries().firstOrNull { it.second == name }?.let { runCatching { DocumentsContract.deleteDocument(cr, child(it.first)) }.getOrDefault(false) } ?: false
}

/**
 * Writes a backup file and an Excel workbook into a folder the user chose, on a schedule, and keeps only the newest few of each. The files
 * are plain unless the person turned on "Protect automatic copies with a passphrase": the key made from it lives in the Android Keystore
 * ([KeystoreSecretStore]) and a run seals the backup with it (see [AutoExportRunner]). Each file is
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
