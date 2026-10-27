package app.expenses

import app.expenses.core.domain.AttachmentRules
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.storage.BackupCounts
import app.expenses.core.storage.AttachmentPlan
import app.expenses.core.storage.BackupData
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.BackupFileSource
import app.expenses.core.storage.BackupHeader
import app.expenses.core.storage.BackupReader
import app.expenses.core.storage.BackupSeal
import app.expenses.core.storage.BackupTransfer
import app.expenses.core.storage.BackupWritten
import app.expenses.core.storage.SavedBackup
import app.expenses.core.storage.ByteArraySink
import app.expenses.core.storage.ByteArraySource
import app.expenses.core.storage.ByteSink
import app.expenses.core.storage.ByteSource
import app.expenses.core.storage.FileVault
import app.expenses.core.storage.FilesRestored
import app.expenses.core.storage.PushbackSource
import app.expenses.core.storage.RestoreReport
import app.expenses.core.storage.SealWith
import app.expenses.core.storage.Sha256
import app.expenses.core.storage.SingleFlight
import app.expenses.core.storage.asWriteProblem
import app.expenses.core.storage.runOrElse
import app.expenses.core.storage.readAll
import app.expenses.core.storage.readFully
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException

/**
 * Export, backup and restore through files the user picked with the system file picker. A file that was written is read back and
 * compared before it is reported as saved, so "Backup saved" never means "probably saved". The Android app's `DataActions` wraps this.
 */
class DataTransfer(private val platform: Platform, private val container: AppContainer, private val now: () -> Long = ::nowMillis) {
    /**
     * A backup file that was opened: either it needs a passphrase first (it is chosen again with the passphrase, by [uri]), or it has been checked and previewed.
     * [Ready.reopen] opens the same file again for its photos and files, which are only read when the restore is confirmed (null when it has none).
     */
    sealed interface Opened {
        class NeedsPassphrase(val uri: String) : Opened
        class Ready(val header: BackupHeader, val data: BackupData, val report: RestoreReport, val reopen: (() -> BackupReader)? = null) : Opened
    }

    /** What a restore did: the usual [report]; [files] (null when the backup had none) for what happened to the photos and files that were stored; [skipped] says which were left alone and why. */
    class Restored(val report: RestoreReport, val files: FilesRestored?, val skipped: AttachmentPlan = AttachmentPlan())

    companion object {
        const val SETTING_BACKUP_AT = "backup_last_at"
        const val SETTING_BACKUP_COUNT = "backup_last_count"

        /** One restore at a time in the whole app: a second one would store the same files again while the first records them. */
        private val restoreFlight = SingleFlight()
    }

    /**
     * What a backup did about photos and files: [included] are in it ([includedBytes] together); [onPhone] are on record for entries. The rest are left out, for
     * the reason [note] gives (the switch is off, or they are over the limit for this kind of copy), or because they are [missing] from this phone or changed
     * since they were attached, or could not be read ([unreadable]). [damagedOnPhone] were already damaged on this phone: they are in the backup as found and cannot be restored from it.
     */
    data class FilesSaved(
        val included: Int = 0, val includedBytes: Long = 0, val onPhone: Int = 0, val note: String? = null,
        val missing: Int = 0, val unreadable: Int = 0, val damagedOnPhone: Int = 0,
    ) {
        /** What to tell the person, one clause each (no capital, no full stop); empty when nothing was left out and nothing is wrong. */
        fun notes(): List<String> = listOfNotNull(
            note,
            missing.takeIf { it > 0 }?.let { "${fileCountText(it)} missing from this phone or changed since ${if (it == 1) "it was" else "they were"} attached, left out" },
            unreadable.takeIf { it > 0 }?.let { "${fileCountText(it)} could not be read and ${if (it == 1) "was" else "were"} left out; the rest of the backup is complete" },
            damagedOnPhone.takeIf { it > 0 }?.let { "${fileCountText(it)} on this phone ${if (it == 1) "was" else "were"} already damaged: ${if (it == 1) "it is" else "they are"} in the backup as found and cannot be restored from it" },
        )

        /** The numbers after a backup was written: files that could not be read while it was written are no longer counted as included. */
        fun after(data: BackupData, written: BackupWritten): FilesSaved {
            val good = data.attachments.filter { it.id !in written.unreadable }
            return copy(included = good.size, includedBytes = good.sumOf { it.sizeBytes }, unreadable = unreadable + written.unreadable.size, damagedOnPhone = written.mismatched.size - written.unreadable.size)
        }
    }

    class Saved(val bytes: Long, val counts: BackupCounts, val encrypted: Boolean, val files: FilesSaved = FilesSaved())

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

    /** Writes the summary of a financial year as an Excel workbook; [yearsBack] 0 is the current year. Returns its label, like "FY 2025-26". */
    suspend fun exportFinancialYear(uri: String, yearsBack: Int): String {
        val zone = TimeZone.currentSystemDefault()
        val (bytes, label) = container.write { ok ->
            val fy = app.expenses.core.storage.FinancialYearExport(ok.reports)
            val period = fy.yearOf(todayLocal(), yearsBack)
            fy.xlsx(period, zone, BaseCurrency.current) to fy.label(period)
        } ?: throw BackupException("The data is not available.")
        writeAndVerify(uri, bytes)
        return label
    }

    /** Refused before anything is written, as the older sealing always did. */
    private fun requirePassphrase(passphrase: String?) {
        if (passphrase != null) require(passphrase.length >= platform.backupMinPassphrase) { "The passphrase must be at least ${platform.backupMinPassphrase} characters" }
    }

    private suspend fun plan(limit: Long?): BackupPlan =
        (container.write { ok -> backupPlan(ok, platform.attachmentFiles, limit) } ?: throw BackupException("The data is not available.")).screened(platform.attachmentFiles)

    private fun sources(data: BackupData, vault: FileVault) = data.attachments.map { a ->
        BackupFileSource(a) { vault.open(a.id) ?: throw BackupException("A photo or file changed while the backup was being made. Try again.") }
    }

    private fun <T> asBackupException(what: String, block: () -> T): T =
        try { block() } catch (e: IllegalArgumentException) { throw BackupException(e.message ?: what) }

    /** A backup without photos and files as bytes (exactly the file earlier versions wrote), sealed the older way when there is a passphrase. */
    private fun plainBytes(plan: BackupPlan, passphrase: String?): ByteArray {
        val plain = BackupFile.encode(plan.data, now())
        return if (passphrase != null) platform.backupCipher.encrypt(plain, passphrase) else plain
    }

    /**
     * A full backup as bytes, for handing to another app (the share sheet: Drive, email, a chat), checked the way a restore would. It is not recorded as
     * a backup made: there is no telling whether the person finished sending it. The file is built in memory for the share sheet, so photos and files
     * go in only up to [BackupKeys.SHARE_FILES_LIMIT_BYTES]; over that they are left out and the result says so.
     */
    suspend fun backupForSharing(passphrase: String?): Pair<ByteArray, Saved> {
        requirePassphrase(passphrase)
        val plan = plan(BackupKeys.SHARE_FILES_LIMIT_BYTES)
        val cipher = platform.backupCipher
        if (plan.data.attachments.isEmpty()) {
            val bytes = plainBytes(plan, passphrase)
            BackupFile.decode(if (passphrase != null) cipher.decrypt(bytes, passphrase) else bytes)
            return bytes to Saved(bytes.size.toLong(), plan.counts, passphrase != null, plan.files)
        }
        val seal = passphrase?.let { SealWith(it, cipher.primitives) }
        val bytes: ByteArray
        val saved: SavedBackup
        try {
            val out = ByteArraySink((plan.files.includedBytes + 64 * 1024).coerceAtMost(Int.MAX_VALUE.toLong() / 2).toInt())
            saved = BackupTransfer.write(plan.data, sources(plan.data, platform.attachmentFiles), now(), seal, out)
            bytes = out.toByteArray()
        } catch (e: OutOfMemoryError) {
            // this path holds the photos in memory for the share sheet: it is the files that are too much
            throw BackupException("This backup is too large to send from this phone. Turn off \"Include photos and files\", or use Save backup to a file instead.")
        }
        BackupTransfer.check(ByteArraySource(bytes), seal, saved)
        return bytes to Saved(bytes.size.toLong(), plan.counts, passphrase != null, plan.files.after(plan.data, saved.written))
    }

    /** Writes a full backup, encrypted when [passphrase] is not null, with the photos and files if the switch is on. The file is read back and checked before this returns. */
    suspend fun backup(uri: String, passphrase: String?): Saved {
        requirePassphrase(passphrase)
        val plan = plan(null)
        val cipher = platform.backupCipher
        val size: Long
        var files = plan.files
        // Once writing to [uri] has begun, a failure leaves a partial file there: it is removed again if the platform can (it was made for this backup), and a
        // full disk is said plainly. A location that could not be opened at all is left alone.
        var begun = false
        try {
            if (plan.data.attachments.isEmpty()) {
                val bytes = plainBytes(plan, passphrase)
                try { platform.writeBytes(uri, bytes) } catch (e: IllegalArgumentException) { throw BackupException(e.message ?: "That location could not be written to.") } catch (e: Exception) { begun = true; throw e }
                begun = true
                val back = read(uri)
                if (back.size != bytes.size || Sha256.hex(back) != Sha256.hex(bytes)) throw BackupException("The file was not saved correctly. Try another location.")
                // Check the file the way a restore would, so a backup that could not be restored is found out now.
                BackupFile.decode(if (passphrase != null) cipher.decrypt(back, passphrase) else back)
                size = bytes.size.toLong()
            } else {
                val seal = passphrase?.let { SealWith(it, cipher.primitives) }
                val sink = asBackupException("That location could not be written to.") { platform.openWrite(uri) }
                begun = true
                val saved = BackupTransfer.write(plan.data, sources(plan.data, platform.attachmentFiles), now(), seal, sink)
                BackupTransfer.check(asBackupException("That file could not be opened.") { platform.openRead(uri) }, seal, saved)
                size = saved.bytes
                files = plan.files.after(plan.data, saved.written)
            }
        } catch (e: Throwable) {
            if (begun) try { platform.discard(uri) } catch (_: Throwable) { }
            throw e.asWriteProblem()
        }
        // Remember that it worked, so the reminder to make a backup stops. Only now: a backup that failed above must not count.
        container.write { ok ->
            ok.templates.putSetting(SETTING_BACKUP_AT, now().toString())
            ok.templates.putSetting(SETTING_BACKUP_COUNT, plan.counts.entries.toString())
        }
        return Saved(size, plan.counts, passphrase != null, files)
    }

    /** Opens a backup file and checks it. Nothing on the device changes. */
    suspend fun open(uri: String, passphrase: String?): Opened {
        val cipher = platform.backupCipher
        val source = asBackupException("That file could not be opened.") { platform.openRead(uri) }
        val head = try { ByteArray(8).let { it.copyOf(source.readFully(it)) } } catch (e: Exception) { source.close(); throw e }
        val rest = PushbackSource(head, source)
        try {
            if (BackupSeal.isLegacySealed(head)) {
                if (passphrase == null) { rest.close(); return Opened.NeedsPassphrase(uri) }
                val (header, data) = BackupFile.decode(cipher.decrypt(rest.readAll().also { rest.close() }, passphrase))
                return Opened.Ready(header, data, previewOf(data))
            }
            if (BackupSeal.isSealed(head)) {
                if (passphrase == null) { rest.close(); return Opened.NeedsPassphrase(uri) }
                val primitives = cipher.primitives
                var key: ByteArray? = null
                val reader = BackupFile.open(BackupSeal.source(rest, primitives) { salt, iterations -> primitives.deriveKey(passphrase, salt, iterations).also { key = it } })
                // the key is kept (not the passphrase) so the files can be read again without deriving it a second time
                val kept = key!!
                return ready(reader) { BackupFile.open(BackupSeal.source(platform.openRead(uri), primitives) { _, _ -> kept }) }
            }
            return ready(BackupFile.open(rest)) { BackupFile.open(platform.openRead(uri)) }
        } catch (e: Throwable) {
            try { rest.close() } catch (_: Exception) { }
            throw e
        }
    }

    private suspend fun previewOf(data: BackupData): RestoreReport = container.write { it.backup.preview(data) } ?: throw BackupException("The data is not available.")

    private suspend fun ready(reader: BackupReader, reopen: () -> BackupReader): Opened.Ready {
        val header = reader.header
        val data = reader.data
        reader.close() // the photos and files are read, from the start, when the restore is confirmed
        return Opened.Ready(header, data, previewOf(data), if (data.attachments.isEmpty()) null else reopen)
    }

    /** Merges the opened backup's entries and settings (not its photos and files: see [restore] with the opened backup) into this device. Everything already here is left as it is. */
    suspend fun restore(data: BackupData): RestoreReport =
        container.write { ok -> ok.backup.restore(data).also { app.expenses.core.domain.BaseCurrency.current = ok.currency.current() } } ?: throw BackupException("The data is not available.")

    /**
     * Merges an opened backup into this device, photos and files included. The files are put in the app's file store first (each checked against its own
     * checksum; a damaged or cut-off one is dropped and counted, never stopping the rest), then everything is recorded in one transaction. If that fails,
     * the files are removed again, so the store and the database never disagree. A backup restored twice, or over the same phone, adds nothing the second time.
     * Only one restore runs at a time (a second call is refused at once): two would store the same files twice, and the later one would remove what the first recorded.
     */
    suspend fun restore(ready: Opened.Ready): Restored =
        restoreFlight.runOrElse({ throw BackupException("A restore is already running. Wait for it to finish.") }) { restoreWithFiles(ready) }

    private suspend fun restoreWithFiles(ready: Opened.Ready): Restored {
        val data = ready.data
        val reopen = ready.reopen
        if (reopen == null || data.attachments.isEmpty()) return Restored(restore(data), null)
        val vault = platform.attachmentFiles
        val plan = container.write { it.backup.attachmentsPlan(data) } ?: throw BackupException("The data is not available.")
        var files: FilesRestored? = null
        if (plan.wanted.isNotEmpty()) {
            val reader = try { reopen() } catch (e: IllegalArgumentException) { throw BackupException(e.message ?: "That file could not be opened.") }
            try {
                if (reader.header.sha256 != ready.header.sha256) throw BackupException("The backup file changed after it was checked. Choose it again.")
                files = BackupTransfer.storeFiles(reader, plan.wanted, vault)
            } finally {
                reader.close()
            }
        }
        val stored = files?.stored.orEmpty()
        try {
            val (report, recorded) = container.write { ok ->
                ok.backup.restore(data, stored).also { app.expenses.core.domain.BaseCurrency.current = ok.currency.current() } to ok.backup.recordedAttachments(stored)
            } ?: throw BackupException("The data is not available.")
            // what this restore stored but did not record is removed, unless somebody recorded it
            BackupTransfer.discardUnrecorded(stored, recorded, vault)
            return Restored(report, files ?: FilesRestored(emptySet(), 0, 0, 0, 0), plan)
        } catch (e: CancellationException) {
            // The transaction may already have committed (cancellation is seen when the write returns): the files are not touched. If it had not, the files with no record are removed by the sweep when the app next starts.
            throw e
        } catch (e: Throwable) {
            val recorded = try { container.write { it.backup.recordedAttachments(stored) } } catch (_: Throwable) { null }
            if (recorded != null) BackupTransfer.discardUnrecorded(stored, recorded, vault)
            throw e
        }
    }

    private fun read(uri: String): ByteArray =
        try { platform.readBytes(uri) } catch (e: IllegalArgumentException) { throw BackupException(e.message ?: "That file could not be opened.") }

    private fun writeAndVerify(uri: String, bytes: ByteArray) {
        try { platform.writeBytes(uri, bytes) } catch (e: IllegalArgumentException) { throw BackupException(e.message ?: "That location could not be written to.") }
        val back = read(uri)
        if (back.size != bytes.size || Sha256.hex(back) != Sha256.hex(bytes)) throw BackupException("The file was not saved correctly. Try another location.")
    }
}

/** What a backup is made of: [data] (with its photos and files listed when they are included), [counts] for the message, and what was done about files. */
internal class BackupPlan(val data: BackupData, val counts: BackupCounts, val files: DataTransfer.FilesSaved)

/**
 * Decides, inside the write lock, what a backup carries. Photos and files go in when the switch is on and they are within [limit] (null: no limit); one
 * that is missing from this phone is left out of the list instead of failing the backup. [DataTransfer.FilesSaved.note] says why files were left out.
 */
internal fun backupPlan(ok: AppContainer.Opened.Ok, vault: FileVault, limit: Long?): BackupPlan {
    val totals = ok.backup.attachmentTotals()
    val on = ok.templates.setting(BackupKeys.INCLUDE_FILES) != "0"
    val tooBig = on && limit != null && totals.bytes > limit
    val data = ok.backup.export(if (on && !tooBig && totals.count > 0) vault::size else null)
    val note = when {
        totals.count == 0 -> null
        !on -> "photos and files are not in it: you switched them off"
        tooBig -> "photos and files (${AttachmentRules.sizeText(totals.bytes)}) are over the ${AttachmentRules.sizeText(limit!!)} limit here, so they are not in it"
        else -> null
    }
    val missing = if (on && !tooBig) totals.count - data.attachments.size else 0
    return BackupPlan(
        data, BackupCounts(data.accounts.size, data.categories.size, data.entries.size, data.templates.size, data.budgets.size, data.recurring.size),
        DataTransfer.FilesSaved(data.attachments.size, data.attachments.sumOf { it.sizeBytes }, totals.count, note, missing),
    )
}

/**
 * The plan without the photos and files that cannot be opened now (deleted, no permission, a bad block): they are left out of the list and counted, so one bad file
 * never keeps the entries from being backed up. Reads the start of each file, outside the write lock.
 */
internal fun BackupPlan.screened(vault: FileVault): BackupPlan {
    if (data.attachments.isEmpty()) return this
    val (kept, left) = BackupTransfer.leaveOutUnreadable(data, vault)
    if (left == 0) return this
    return BackupPlan(kept, counts, files.copy(included = kept.attachments.size, includedBytes = kept.attachments.sumOf { it.sizeBytes }, unreadable = left))
}

/** "1 photo or file" or "3 photos and files". */
internal fun fileCountText(n: Int): String = if (n == 1) "1 photo or file" else "$n photos and files"
