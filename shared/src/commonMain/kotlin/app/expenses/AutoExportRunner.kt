package app.expenses

import app.expenses.core.domain.AttachmentRules
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.domain.AutoExportRules
import app.expenses.core.storage.AutoCopyProtection
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.BackupFileSource
import app.expenses.core.storage.BackupTransfer
import app.expenses.core.storage.SavedBackup
import app.expenses.core.storage.SealWith
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
 * are plain unless the person turned on "Protect automatic copies with a passphrase" ([AutoExportKeys.PROTECT]): a run cannot ask for the passphrase, so it
 * seals the backup (`EXPENC3`, see docs/BACKUP_FORMAT.md) under the key that was made from it and kept in the phone's secure storage ([AutoCopyProtection]).
 * With protection on, a run whose key cannot be read writes NOTHING (never a plain copy instead) and removes nothing, and says so; no Excel file is made while it
 * is on, since it cannot be locked and would show everything. Each file is
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
    private const val S_PROTECT = AutoExportKeys.PROTECT

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

    private class Settings(val freq: String?, val lastAt: Long?, val folder: String?, val protect: Boolean)

    private suspend fun runOnce(container: AppContainer, folder: ExportFolder?, now: Long, force: Boolean): AutoExportResult {
        val settings = container.write { ok -> ok.templates.let { t -> Settings(t.setting(S_FREQ), t.setting(S_LAST_AT)?.toLongOrNull(), t.setting(S_FOLDER), t.setting(S_PROTECT) == "1") } } ?: return AutoExportResult(false, false, "The data is not available.")
        val freq = AutoExportFrequency.entries.firstOrNull { it.name == settings.freq } ?: AutoExportFrequency.OFF
        if (!force && !AutoExportRules.isDue(freq, settings.lastAt, now)) return AutoExportResult(false, true, "")
        if (settings.folder == null || folder == null) return record(container, now, AutoExportResult(true, false, "Choose a folder first."), false)
        // Protection on: the key is read now, or this run writes nothing and removes nothing (never a plain copy instead) and the screen says so. Not recorded as a success.
        val sealWith: SealWith? = when (val how = AutoCopyProtection.plan(settings.protect, container.secretStore, container.backupPrimitives)) {
            is AutoCopyProtection.Plan.Plain -> null
            is AutoCopyProtection.Plan.Sealed -> how.seal
            is AutoCopyProtection.Plan.CannotProtect -> return record(container, now, AutoExportResult(true, false, keyProblemText(how.why)), false)
        }
        _running.value = true
        try {
            return try {
                val day = dayOf(now)
                // one snapshot for both files (no workbook while protected: it cannot be locked); the photos and files the backup carries are read afterwards, outside the lock
                val (planned, xlsx) = container.write { ok -> backupPlan(ok, container.attachmentFiles, BackupKeys.AUTO_FILES_LIMIT_BYTES) to (if (sealWith == null) ok.csv.xlsx(TimeZone.currentSystemDefault()) else null) } ?: throw IllegalStateException("The data is not available.")
                val plan = planned.screened(container.attachmentFiles)
                val backupName = "${AutoExportKeys.BACKUP_PREFIX}-$day.expbak"
                val xlsxName = "${AutoExportKeys.XLSX_PREFIX}-$day.xlsx"
                var files = plan.files
                if (sealWith == null && plan.data.attachments.isEmpty()) {
                    val bytes = BackupFile.encode(plan.data, now)
                    place(folder, backupName, { n -> writeBytes(folder, n, "application/octet-stream", bytes) }, { w -> verifyBytes(folder, w, bytes) })
                } else {
                    // written in pieces: a backup with photos can be large, and a sealed copy (with or without them) is always written this way
                    var saved: SavedBackup? = null
                    place(folder, backupName, { n -> writeBackupWithFiles(folder, n, plan, container, now, sealWith).also { saved = it.second }.first }, { w -> BackupTransfer.check(folder.openRead(w) ?: throw IllegalStateException("A file was not saved correctly."), sealWith, saved!!) })
                    if (plan.data.attachments.isNotEmpty()) files = plan.files.after(plan.data, saved!!.written)
                }
                if (xlsx != null) place(folder, xlsxName, { n -> writeBytes(folder, n, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx) }, { w -> verifyBytes(folder, w, xlsx) })
                // what a run that was cut off left behind (.part, .old: not copies) goes only now that this run has made good ones, never before
                folder.names().filter { leftover.matches(it) }.forEach { runCatching { folder.delete(it) } }
                val names = folder.names()
                AutoExportRules.filesToDelete(names, AutoExportKeys.BACKUP_PREFIX, "expbak", AutoExportKeys.KEEP).forEach { folder.delete(it) }
                if (xlsx != null) AutoExportRules.filesToDelete(names, AutoExportKeys.XLSX_PREFIX, "xlsx", AutoExportKeys.KEEP).forEach { folder.delete(it) }
                val notes = files.notes()
                val about = when {
                    files.included > 0 -> " (with ${fileCountText(files.included)}, ${AttachmentRules.sizeText(files.includedBytes)}${notes.joinToString("") { "; $it" }})"
                    notes.isNotEmpty() -> " (${notes.joinToString("; ")})"
                    else -> ""
                }
                val oldExcel = if (sealWith == null) 0 else AutoExportRules.countOurs(names, AutoExportKeys.XLSX_PREFIX, "xlsx")
                record(container, now, AutoExportResult(true, true, if (sealWith == null) "Saved $day: a backup$about and an Excel file." else "Saved $day: a protected backup$about. No Excel file is made while copies are protected." + earlierExcel(oldExcel)), true, oldExcel)
            } catch (e: Exception) {
                record(container, now, AutoExportResult(true, false, "Could not save: ${e.asWriteProblem().let { it.message ?: it::class.simpleName }}"), false)
            }
        } finally {
            _running.value = false
        }
    }

    /** What the status says for each reason a protected run cannot use its key. */
    fun keyProblemText(why: AutoCopyProtection.Why): String = when (why) {
        AutoCopyProtection.Why.UNAVAILABLE -> AutoExportKeys.KEY_UNAVAILABLE
        AutoCopyProtection.Why.CORRUPT -> AutoExportKeys.KEY_CORRUPT
        AutoCopyProtection.Why.NOT_FOUND, AutoCopyProtection.Why.NO_STORE -> AutoExportKeys.KEY_NOT_FOUND
    }

    /** What to say when Excel files from before protection was turned on are still in the folder: they are not protected, and a run does not remove them. */
    private fun earlierExcel(count: Int): String = when (count) {
        0 -> ""
        1 -> " An Excel file from before is still in the folder and is not protected: delete it if it should not be there."
        else -> " $count Excel files from before are still in the folder and are not protected: delete them if they should not be there."
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

    /** Writes the backup in pieces (it carries photos and files, so it can be large), sealed when [seal] is not null. Returns the name it got and what was written; [place] reads it back the way a restore would. */
    private fun writeBackupWithFiles(folder: ExportFolder, name: String, plan: BackupPlan, container: AppContainer, now: Long, seal: SealWith?): Pair<String, SavedBackup> {
        val vault = container.attachmentFiles
        val sink = folder.openWrite(name, "application/octet-stream") ?: throw IllegalStateException("The folder could not be written to.")
        val saved = BackupTransfer.write(plan.data, plan.data.attachments.map { a -> BackupFileSource(a) { vault.open(a.id) ?: throw BackupException("A photo or file is gone.") } }, now, seal, sink)
        return sink.name to saved
    }

    /**
     * Deletes the Excel files an earlier, unprotected run left in the folder (only names `expenses-YYYY-MM-DD.xlsx`, with the system's " (1)"; never the person's own
     * files), and only while protection is on. Called when the person pressed "Delete them"; nothing is deleted any other way. Never throws; the outcome is the status line.
     */
    suspend fun deleteOldExcel(container: AppContainer, folder: ExportFolder?): AutoExportResult =
        flight.runOrElse({ AutoExportResult(false, true, "A copy is being saved. Try again in a moment.") }) {
            val protect = container.write { it.templates.setting(S_PROTECT) == "1" } ?: return@runOrElse AutoExportResult(false, false, "The data is not available.")
            if (!protect || folder == null) return@runOrElse AutoExportResult(false, false, "Nothing was deleted.")
            val r = try {
                val old = AutoExportRules.ourNames(folder.names(), AutoExportKeys.XLSX_PREFIX, "xlsx")
                val gone = old.count { folder.delete(it) }
                val left = old.size - gone
                AutoExportResult(true, left == 0, (if (gone == 1) "Deleted 1 Excel file from before protection." else "Deleted $gone Excel files from before protection.") + if (left > 0) " $left could not be deleted." else "")
            } catch (e: Exception) {
                AutoExportResult(true, false, "Could not delete the Excel files: ${e.asWriteProblem().let { it.message ?: it::class.simpleName }}")
            }
            val left = try { AutoExportRules.countOurs(folder.names(), AutoExportKeys.XLSX_PREFIX, "xlsx") } catch (e: Exception) { 0 }
            container.write { ok ->
                ok.templates.putSetting(S_STATUS, (if (r.ok) "ok|" else "fail|") + r.text)
                ok.templates.putSetting(AutoExportKeys.OLD_EXCEL, left.toString())
            }
            r
        }

    private suspend fun record(container: AppContainer, now: Long, r: AutoExportResult, success: Boolean, oldExcel: Int? = null): AutoExportResult {
        container.write { ok ->
            if (oldExcel != null) ok.templates.putSetting(AutoExportKeys.OLD_EXCEL, oldExcel.toString())
            ok.templates.putSetting(S_STATUS, (if (r.ok) "ok|" else "fail|") + r.text)
            if (success) ok.templates.putSetting(S_LAST_AT, now.toString())
        }
        return r
    }
}
