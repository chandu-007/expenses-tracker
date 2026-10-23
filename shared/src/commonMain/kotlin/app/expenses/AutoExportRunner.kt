package app.expenses

import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.domain.AutoExportRules
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.Sha256
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A folder the user chose, as far as automatic export cares. [write] returns the name the file really got (the system may add " (1)"), or null. */
interface ExportFolder {
    fun names(): List<String>
    fun write(name: String, mime: String, bytes: ByteArray): String?
    fun read(name: String): ByteArray?
    fun delete(name: String): Boolean
}

/** What a run did: whether it ran at all, whether it worked, and the sentence for the screen. */
data class AutoExportResult(val ran: Boolean, val ok: Boolean, val text: String)

/**
 * Writes a backup file and an Excel workbook into a folder the user chose, on a schedule, and keeps only the newest few of each. The files
 * are not encrypted (a passphrase cannot be stored, and one asked for every time would not be automatic); the screen says so. Each file is
 * read back and compared before the run counts, so a half-written file is reported, not recorded as a success. Shared by Android (a document
 * provider folder) and iPhone (a folder from the Files app).
 */
object AutoExportRunner {
    private const val S_FOLDER = AutoExportKeys.FOLDER
    private const val S_FREQ = AutoExportKeys.FREQ
    private const val S_LAST_AT = AutoExportKeys.LAST_AT
    private const val S_STATUS = AutoExportKeys.STATUS

    private fun dayOf(now: Long) = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** Runs the export if it is due (or [force]d). Never throws: the outcome is recorded for the screen and returned. */
    suspend fun run(container: AppContainer, folder: ExportFolder?, now: Long, force: Boolean = false): AutoExportResult {
        val settings = container.write { ok -> Triple(ok.templates.setting(S_FREQ), ok.templates.setting(S_LAST_AT)?.toLongOrNull(), ok.templates.setting(S_FOLDER)) } ?: return AutoExportResult(false, false, "The data is not available.")
        val freq = AutoExportFrequency.entries.firstOrNull { it.name == settings.first } ?: AutoExportFrequency.OFF
        if (!force && !AutoExportRules.isDue(freq, settings.second, now)) return AutoExportResult(false, true, "")
        if (settings.third == null || folder == null) return record(container, now, AutoExportResult(true, false, "Choose a folder first."), false)
        return try {
            val day = dayOf(now)
            val (backup, xlsx) = container.write { ok -> BackupFile.encode(ok.backup.export(), now) to ok.csv.xlsx(TimeZone.currentSystemDefault()) } ?: throw IllegalStateException("The data is not available.")
            val files = listOf(
                Triple("${AutoExportKeys.BACKUP_PREFIX}-$day.expbak", "application/octet-stream", backup),
                Triple("${AutoExportKeys.XLSX_PREFIX}-$day.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx),
            )
            for ((name, mime, bytes) in files) {
                // A file of the same name from earlier today is replaced, so running twice in a day leaves one file, not two.
                if (name in folder.names()) folder.delete(name)
                val written = folder.write(name, mime, bytes) ?: throw IllegalStateException("The folder could not be written to.")
                val back = folder.read(written)
                if (back == null || back.size != bytes.size || Sha256.hex(back) != Sha256.hex(bytes)) throw IllegalStateException("A file was not saved correctly.")
            }
            val names = folder.names()
            AutoExportRules.filesToDelete(names, AutoExportKeys.BACKUP_PREFIX, "expbak", AutoExportKeys.KEEP).forEach { folder.delete(it) }
            AutoExportRules.filesToDelete(names, AutoExportKeys.XLSX_PREFIX, "xlsx", AutoExportKeys.KEEP).forEach { folder.delete(it) }
            record(container, now, AutoExportResult(true, true, "Saved $day: a backup and an Excel file."), true)
        } catch (e: Exception) {
            record(container, now, AutoExportResult(true, false, "Could not save: ${e.message ?: e::class.simpleName}"), false)
        }
    }

    private suspend fun record(container: AppContainer, now: Long, r: AutoExportResult, success: Boolean): AutoExportResult {
        container.write { ok ->
            ok.templates.putSetting(S_STATUS, (if (r.ok) "ok|" else "fail|") + r.text)
            if (success) ok.templates.putSetting(S_LAST_AT, now.toString())
        }
        return r
    }
}
