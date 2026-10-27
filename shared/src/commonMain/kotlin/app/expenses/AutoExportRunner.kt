package app.expenses

import app.expenses.core.domain.AttachmentRules
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.domain.AutoExportRules
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.BackupFileSource
import app.expenses.core.storage.BackupTransfer
import app.expenses.core.storage.SavedBackup
import app.expenses.core.storage.ByteArraySink
import app.expenses.core.storage.ByteArraySource
import app.expenses.core.storage.ByteSink
import app.expenses.core.storage.ByteSource
import app.expenses.core.storage.Sha256
import app.expenses.core.storage.SingleFlight
import app.expenses.core.storage.asWriteProblem
import app.expenses.core.storage.runOrElse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A folder the user chose, as far as automatic export cares. [write] returns the name the file really got (the system may add " (1)"), or null. */
interface ExportFolder {
    fun names(): List<String>
    fun write(name: String, mime: String, bytes: ByteArray): String?
    fun read(name: String): ByteArray?
    fun delete(name: String): Boolean

    /**
     * The same as [write] as a stream, for a backup that carries photos and can be large; closing it completes the file and [FolderWriter.name] is then what
     * it was called. Null when the file cannot be created. The default holds everything and writes at the end: a real folder overrides it.
     */
    fun openWrite(name: String, mime: String): FolderWriter? {
        val requested = name
        return object : FolderWriter {
            private val out = ByteArraySink()
            override var name: String = requested
                private set
            override fun write(buf: ByteArray, off: Int, len: Int) = out.write(buf, off, len)
            override fun close() { this.name = this@ExportFolder.write(requested, mime, out.toByteArray()) ?: throw IllegalStateException("The folder could not be written to.") }
            override fun abort() {} // nothing was written to the folder yet
        }
    }

    /** The same as [read] as a stream; null when there is no such file. The default reads it all: a real folder overrides it. */
    fun openRead(name: String): ByteSource? = read(name)?.let { ByteArraySource(it) }

    /**
     * Whether [rename] works in this folder. Where it does, a copy is written under a temporary name and takes its real name only after it has been read back
     * and compared, so a run that is cut off never leaves a half file that looks like a good copy.
     */
    val canRename: Boolean get() = false

    /** Gives the file [name] the name [newName] (no file of that name exists) and returns the name it has now, or null when that could not be done. */
    fun rename(name: String, newName: String): String? = null
}

/** A file being written into a folder: after [close], [name] is the name it really got (the system may add " (1)"). */
interface FolderWriter : ByteSink {
    val name: String
}

/** What a run did: whether it ran at all, whether it worked, and the sentence for the screen. */
data class AutoExportResult(val ran: Boolean, val ok: Boolean, val text: String)

/**
 * Writes a backup file and an Excel workbook into a folder the user chose, on a schedule, and keeps only the newest few of each. The files
 * are not encrypted (a passphrase cannot be stored, and one asked for every time would not be automatic); the screen says so. Each file is
 * read back and compared before the run counts, so a half-written file is reported, not recorded as a success. Shared by Android (a document
 * provider folder) and iPhone (a folder from the Files app).
 *
 * One run at a time in the whole app (the periodic worker, a foreground check and the "Save a copy now" button can all start one): a second is told at once
 * that a copy is already being saved, and [running] says when one is in flight. A copy is written under a temporary name where the folder can rename, and replaces
 * the file of the same day only after it has been read back and compared (see [place]).
 */
object AutoExportRunner {
    private const val S_FOLDER = AutoExportKeys.FOLDER
    private const val S_FREQ = AutoExportKeys.FREQ
    private const val S_LAST_AT = AutoExportKeys.LAST_AT
    private const val S_STATUS = AutoExportKeys.STATUS

    /** The ending of a copy that is still being written; it matches no real copy, so it is never counted, kept or pruned as one. */
    private const val PART = ".part"

    /** The ending of the copy of the day that a new copy is replacing, while it is replaced. */
    private const val OLD = ".old"
    private val leftover = Regex("^" + Regex.escape(AutoExportKeys.XLSX_PREFIX) + "(-backup)?-\\d{4}-\\d{2}-\\d{2}\\.(expbak|xlsx)(" + Regex.escape(PART) + "|" + Regex.escape(OLD) + ")$")

    private val flight = SingleFlight()
    private val _running = MutableStateFlow(false)

    /** True while a copy is being written: the screen shows it and keeps the button off. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private fun dayOf(now: Long) = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** Runs the export if it is due (or [force]d). Never throws: the outcome is recorded for the screen and returned. A run that finds another in flight returns at once without recording anything. */
    suspend fun run(container: AppContainer, folder: ExportFolder?, now: Long, force: Boolean = false): AutoExportResult =
        flight.runOrElse({ AutoExportResult(false, true, "A copy is already being saved.") }) { runOnce(container, folder, now, force) }

    private suspend fun runOnce(container: AppContainer, folder: ExportFolder?, now: Long, force: Boolean): AutoExportResult {
        val settings = container.write { ok -> Triple(ok.templates.setting(S_FREQ), ok.templates.setting(S_LAST_AT)?.toLongOrNull(), ok.templates.setting(S_FOLDER)) } ?: return AutoExportResult(false, false, "The data is not available.")
        val freq = AutoExportFrequency.entries.firstOrNull { it.name == settings.first } ?: AutoExportFrequency.OFF
        if (!force && !AutoExportRules.isDue(freq, settings.second, now)) return AutoExportResult(false, true, "")
        if (settings.third == null || folder == null) return record(container, now, AutoExportResult(true, false, "Choose a folder first."), false)
        _running.value = true
        try {
            return try {
                val day = dayOf(now)
                // one snapshot for both files; the photos and files the backup carries are read afterwards, outside the lock
                val (planned, xlsx) = container.write { ok -> backupPlan(ok, container.attachmentFiles, BackupKeys.AUTO_FILES_LIMIT_BYTES) to ok.csv.xlsx(TimeZone.currentSystemDefault()) } ?: throw IllegalStateException("The data is not available.")
                val plan = planned.screened(container.attachmentFiles)
                val backupName = "${AutoExportKeys.BACKUP_PREFIX}-$day.expbak"
                val xlsxName = "${AutoExportKeys.XLSX_PREFIX}-$day.xlsx"
                var files = plan.files
                if (plan.data.attachments.isEmpty()) {
                    val bytes = BackupFile.encode(plan.data, now)
                    place(folder, backupName, { n -> writeBytes(folder, n, "application/octet-stream", bytes) }, { w -> verifyBytes(folder, w, bytes) })
                } else {
                    var saved: SavedBackup? = null
                    place(folder, backupName, { n -> writeBackupWithFiles(folder, n, plan, container, now).also { saved = it.second }.first }, { w -> BackupTransfer.check(folder.openRead(w) ?: throw IllegalStateException("A file was not saved correctly."), null, saved!!) })
                    files = plan.files.after(plan.data, saved!!.written)
                }
                place(folder, xlsxName, { n -> writeBytes(folder, n, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx) }, { w -> verifyBytes(folder, w, xlsx) })
                // what a run that was cut off left behind (.part, .old: not copies) goes only now that this run has made good ones, never before
                folder.names().filter { leftover.matches(it) }.forEach { runCatching { folder.delete(it) } }
                val names = folder.names()
                AutoExportRules.filesToDelete(names, AutoExportKeys.BACKUP_PREFIX, "expbak", AutoExportKeys.KEEP).forEach { folder.delete(it) }
                AutoExportRules.filesToDelete(names, AutoExportKeys.XLSX_PREFIX, "xlsx", AutoExportKeys.KEEP).forEach { folder.delete(it) }
                val notes = files.notes()
                val about = when {
                    files.included > 0 -> " (with ${fileCountText(files.included)}, ${AttachmentRules.sizeText(files.includedBytes)}${notes.joinToString("") { "; $it" }})"
                    notes.isNotEmpty() -> " (${notes.joinToString("; ")})"
                    else -> ""
                }
                record(container, now, AutoExportResult(true, true, "Saved $day: a backup$about and an Excel file."), true)
            } catch (e: Exception) {
                record(container, now, AutoExportResult(true, false, "Could not save: ${e.asWriteProblem().let { it.message ?: it::class.simpleName }}"), false)
            }
        } finally {
            _running.value = false
        }
    }

    /**
     * Puts one file in the folder under [finalName]: [write] writes it under the name it is given and returns the name it really got, [verify] reads it back and
     * throws if it is not right. Where the folder can rename, the new file is written as `name.part`, verified, and only then does it replace the file of the
     * same name (the old one is deleted, the new one renamed): a failed or cut-off run never costs the good copy already there and never leaves a half file
     * with a real copy's name. Where it cannot, the order is the old one (the old file removed, the new written under its own name), and a failed one is removed again.
     */
    private fun place(folder: ExportFolder, finalName: String, write: (name: String) -> String, verify: (written: String) -> Unit) {
        // Without rename: the old order (the same-day file removed, the new one written under its own name), and a failed partial is removed again.
        if (!folder.canRename) {
            if (finalName in folder.names()) folder.delete(finalName)
            var written: String? = null
            try {
                written = write(finalName)
                verify(written)
            } catch (e: Exception) {
                runCatching { folder.delete(written ?: finalName) }
                throw e
            }
            return
        }
        // With rename: write and verify under a temporary name first. The verified temporary copy is kept until the real name has been given to a verified copy.
        val temp = finalName + PART
        if (temp in folder.names()) runCatching { folder.delete(temp) }
        var current: String? = null
        try {
            current = write(temp)
            verify(current)
        } catch (e: Exception) {
            runCatching { folder.delete(current ?: temp) }
            throw e
        }
        var verified: String = current
        // the same-day copy is moved aside, not deleted, so it is still there if anything below fails
        val aside = finalName + OLD
        var movedAside = false
        if (finalName in folder.names()) {
            if (aside in folder.names()) runCatching { folder.delete(aside) }
            movedAside = folder.rename(finalName, aside) == aside
            if (!movedAside) runCatching { folder.delete(finalName) } // this folder cannot move it: the verified copy above is what keeps the day safe
        }
        val renamed = folder.rename(verified, finalName)
        if (renamed == finalName) {
            if (movedAside) runCatching { folder.delete(aside) }
            return
        }
        // The rename failed, or gave the copy another name (outside the rotation pattern): the copy stays where it is, verified, until a copy under the real name is verified.
        if (renamed != null) verified = renamed
        if (finalName in folder.names()) runCatching { folder.delete(finalName) }
        var fallback: String? = null
        try {
            fallback = write(finalName)
            verify(fallback)
        } catch (e: Exception) {
            runCatching { folder.delete(fallback ?: finalName) }
            if (movedAside) runCatching { folder.rename(aside, finalName) } // the earlier copy of the day is put back; the verified new one is left beside it
            throw e
        }
        runCatching { folder.delete(verified) }
        if (movedAside) runCatching { folder.delete(aside) }
    }

    /** A temporary copy is created as a plain binary file whatever it holds: a document provider may add the extension of the type it is given to the name. */
    private fun writeBytes(folder: ExportFolder, name: String, mime: String, bytes: ByteArray): String =
        folder.write(name, if (name.endsWith(PART)) "application/octet-stream" else mime, bytes) ?: throw IllegalStateException("The folder could not be written to.")

    /** Reads a small file back and compares it with what was meant to be written. */
    private fun verifyBytes(folder: ExportFolder, written: String, bytes: ByteArray) {
        val back = folder.read(written)
        if (back == null || back.size != bytes.size || Sha256.hex(back) != Sha256.hex(bytes)) throw IllegalStateException("A file was not saved correctly.")
    }

    /** Writes the backup in pieces (it carries photos and files, so it can be large). Returns the name it got and what was written; [place] reads it back the way a restore would. */
    private fun writeBackupWithFiles(folder: ExportFolder, name: String, plan: BackupPlan, container: AppContainer, now: Long): Pair<String, SavedBackup> {
        val vault = container.attachmentFiles
        val sink = folder.openWrite(name, "application/octet-stream") ?: throw IllegalStateException("The folder could not be written to.")
        val saved = BackupTransfer.write(plan.data, plan.data.attachments.map { a -> BackupFileSource(a) { vault.open(a.id) ?: throw BackupException("A photo or file is gone.") } }, now, null, sink)
        return sink.name to saved
    }

    private suspend fun record(container: AppContainer, now: Long, r: AutoExportResult, success: Boolean): AutoExportResult {
        container.write { ok ->
            ok.templates.putSetting(S_STATUS, (if (r.ok) "ok|" else "fail|") + r.text)
            if (success) ok.templates.putSetting(S_LAST_AT, now.toString())
        }
        return r
    }
}
