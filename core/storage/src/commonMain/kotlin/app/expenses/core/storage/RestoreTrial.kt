package app.expenses.core.storage

import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.domain.sync.NodeId
import app.expenses.core.storage.db.ExpensesDb

/**
 * N-23, "Test a backup": proves that a backup file would restore, without restoring it. The file is opened and read to its end (every photo and file checked against
 * its checksum, none stored), then its data is restored into a SCRATCH database the caller made for this and deletes afterwards, and what that database then holds
 * is counted. The app's own database, its settings, its node id and its key are never touched: [restoreInto] is given only the scratch database, refuses any database
 * that already holds an account or an entry, and stamps what it writes with a clock of its own (a random node id that is never stored).
 */
object RestoreTrial {
    /** What opening a file gave: it needs a passphrase, or it was read whole ([files] photos and files listed, [filesBad] of them damaged or missing). */
    sealed interface Opened {
        data object NeedsPassphrase : Opened
        class Ready(val data: BackupData, val files: Int, val filesBad: Int) : Opened
    }

    /** What the scratch database held after the restore: live entries and accounts, and when the newest entry happened (null when there are none). */
    data class Counts(val entries: Long, val accounts: Int, val lastEntryAt: Long?)

    /**
     * Reads the backup in [source] (closed here) to its end: plain, sealed (`EXPENC2` / `EXPENC3`, opened with [passphrase] through [primitives]) or the oldest sealed
     * kind (`EXPENC1`, opened by [legacyDecrypt]). Without a passphrase a sealed file gives [Opened.NeedsPassphrase]. Throws [BackupException] with words for a person
     * when it is not a backup, the passphrase is wrong, or it is damaged.
     */
    fun open(source: ByteSource, passphrase: String?, primitives: SealPrimitives, legacyDecrypt: (bytes: ByteArray, passphrase: String) -> ByteArray): Opened {
        val head = ByteArray(8)
        val rest = try { PushbackSource(head.copyOf(source.readFully(head)), source) } catch (e: Exception) { source.close(); throw e }
        try {
            if (BackupSeal.isLegacySealed(head)) {
                if (passphrase == null) return Opened.NeedsPassphrase
                return Opened.Ready(BackupFile.decode(legacyDecrypt(rest.readAll(), passphrase)).second, 0, 0)
            }
            val plain = if (BackupSeal.isSealed(head)) {
                if (passphrase == null) return Opened.NeedsPassphrase
                BackupSeal.source(rest, primitives) { salt, iterations -> primitives.deriveKey(passphrase, salt, iterations) }
            } else rest
            val reader = BackupFile.open(plain)
            var files = 0
            var bad = 0
            reader.readFiles({ DiscardSink }) { _, result -> files++; if (result != FileResult.OK) bad++ }
            return Opened.Ready(reader.data, files, bad)
        } finally {
            try { rest.close() } catch (_: Exception) { }
        }
    }

    /**
     * Restores [data] into [scratch], a new, empty database made for the test (its schema already created), and counts what it then holds. Throws
     * [IllegalStateException] for a database that already has an account or has ever had an entry: that is not a scratch database, and nothing is written to it.
     * Any other exception is the restore failing, as it would have failed for real (nothing of the person's is involved either way).
     */
    fun restoreInto(scratch: ExpensesDb, data: BackupData, nowMillis: () -> Long): Counts {
        val q = scratch.schemaQueries
        check(!q.entryCountEver().executeAsOne() && q.selectAccounts().executeAsList().isEmpty()) { "A test restore goes only into a new, empty database" }
        val clock = HlcClock(NodeId.generate())
        val ledger = LedgerStore(scratch, clock, nowMillis)
        val templates = TemplateStore(scratch, clock, nowMillis)
        val recurring = RecurringStore(scratch, clock, nowMillis, ledger)
        BackupStore(scratch, clock, nowMillis, ledger, templates, recurring).restore(data)
        return Counts(q.entryCount().executeAsOne(), q.selectAccounts().executeAsList().size, q.recentEntries(1).executeAsList().firstOrNull()?.occurred_at)
    }

    /** A count with grouping, the Indian way ("1,240", "1,00,000"). */
    fun count(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,${s.takeLast(3)}"
    }

    /**
     * The sentence the person reads: "Opens fine: 1,240 entries, 6 accounts, last entry 21 Nov." and, when the file carries photos and files, how they read back.
     * [day] formats the time of the newest entry.
     */
    fun summary(c: Counts, files: Int, filesBad: Int, day: (Long) -> String): String = buildString {
        append("Opens fine: ").append(count(c.entries)).append(if (c.entries == 1L) " entry, " else " entries, ")
        append(c.accounts).append(if (c.accounts == 1) " account" else " accounts")
        c.lastEntryAt?.let { append(", last entry ").append(day(it)) }
        append('.')
        if (files > 0) {
            val noun = if (files == 1) "photo or file" else "photos and files"
            if (filesBad == 0) append(" ${count(files.toLong())} $noun read back whole.")
            else append(" ${count(filesBad.toLong())} of ${count(files.toLong())} $noun are damaged or missing; the entries are fine.")
        }
    }
}
