package app.expenses.core.storage

/** Where the bytes of attachments are kept (the app's private folder) as far as a backup and a restore care. The platform wraps its file store in this. */
interface FileVault {
    /** The size in bytes of the stored file, or -1 when there is none. */
    fun size(id: String): Long

    /** The stored file's bytes, or null when there is none. */
    fun open(id: String): ByteSource?

    /** A new (or replaced) file for [id]; closing the sink completes it. */
    fun create(id: String): ByteSink

    fun delete(id: String)
}

/**
 * What to seal a backup with: a passphrase (the key is derived from it with a fresh salt: `EXPENC2`), or a key made from a passphrase earlier and kept ([withKey], for
 * automatic copies that cannot ask: `EXPENC3`), and the primitives to do it with.
 */
class SealWith private constructor(private val passphrase: String?, val primitives: SealPrimitives, private val kept: KeptKey?) {
    constructor(passphrase: String, primitives: SealPrimitives) : this(passphrase, primitives, null)

    internal fun sink(inner: ByteSink): BackupSeal.SealedSink = if (kept != null) BackupSeal.keyedSink(inner, kept, primitives) else BackupSeal.sink(inner, passphrase!!, primitives)

    /**
     * The key a reader is given for the header of the file just written. A kept key is only used when the header names its salt and rounds (they are what lets the
     * passphrase alone open the file); [written] is the key the sink made for an `EXPENC2` file, so that one is not derived a second time.
     */
    internal fun readerKey(salt: ByteArray, iterations: Int, written: ByteArray?): ByteArray {
        if (kept != null) {
            if (iterations != kept.iterations || !salt.contentEquals(kept.salt)) throw BackupException("The copy was not sealed with the key that opens it. Nothing was kept.")
            return kept.key
        }
        return written ?: primitives.deriveKey(passphrase!!, salt, iterations)
    }

    companion object {
        /** Seals with [kept], the key a passphrase gave earlier ([AutoCopyProtection.enable]); the file is `EXPENC3` and opens with that passphrase. */
        fun withKey(kept: KeptKey, primitives: SealPrimitives) = SealWith(null, primitives, kept)
    }
}

/** A backup that was written: its size and SHA-256 as stored (after any sealing), and what [BackupFile.write] reported. [key] is the sealing key it was written with, if it was sealed. */
class SavedBackup(val bytes: Long, val sha256: String, val written: BackupWritten, internal val key: ByteArray? = null)

/** What came of putting a backup's files into the file store. [stored] are the ones that arrived whole and matched their checksum. */
class FilesRestored(val stored: Set<String>, val damaged: Int, val missing: Int, val notStored: Int, val notWanted: Int)

/**
 * The steps of writing a backup with files, checking what was written, and putting a backup's files back, on top of [BackupFile] and [BackupSeal]. They are
 * here, not in the app's screens' glue, so they are tested on the host with in-memory files. Nothing here holds more than a few buffers of a file.
 */
object BackupTransfer {
    /**
     * Writes [data] and [files] to [out] (and closes it), sealed when [seal] is not null. The backup is version 2 with the files after the
     * payload. [data] must list no attachments of its own (the list is made from [files]).
     */
    fun write(data: BackupData, files: List<BackupFileSource>, createdAtMillis: Long, seal: SealWith?, out: ByteSink): SavedBackup {
        val stored = HashingSink(out)
        val sealed = seal?.sink(stored)
        val written = BackupFile.write(data, files, createdAtMillis, sealed ?: stored)
        return SavedBackup(stored.count, stored.hex(), written, sealed?.key)
    }

    /**
     * Reads the backup just written back from [source] the way a restore would (opening it, checking the payload, reading and checking every file) and
     * compares it with what was written, so a backup that could not be restored is found out now. Throws [BackupException] if anything differs.
     */
    fun check(source: ByteSource, seal: SealWith?, saved: SavedBackup) {
        val raw = HashingSource(source)
        var bad = 0
        try {
            val plain = if (seal != null) BackupSeal.source(raw, seal.primitives) { salt, iterations -> seal.readerKey(salt, iterations, saved.key) } else raw
            val reader = BackupFile.open(plain)
            // a file that was already wrong on this phone when it was copied is written as it is, and is expected not to match
            reader.readFiles({ DiscardSink }) { a, r -> if (r != FileResult.OK && a.id !in saved.written.mismatched) bad++ }
            if (bad > 0) throw BackupException("The backup was written, but $bad of its photos and files did not read back correctly. Try another location.")
            val hex = raw.finishHex()
            if (raw.count != saved.bytes || hex != saved.sha256) throw BackupException("The file was not saved correctly. Try another location.")
        } finally {
            try { raw.close() } catch (_: Exception) { }
        }
    }

    /**
     * Puts the files of an opened backup into [vault]: those whose ids (as this phone keeps them, [BackupAttachment.localId]) are in [wanted], each checked against
     * its listed checksum as it is written; one that does not match, or is cut off, or cannot be stored is removed again and counted, and the rest carry on.
     * Only the first valid record with an id is stored (a repeat of an id is counted as not wanted and can never touch the file kept for the first), and the
     * file goes to the record that passed [BackupAttachment.isRestorable], the same one [BackupStore.restore] records. Never throws for damage.
     */
    fun storeFiles(reader: BackupReader, wanted: Set<String>, vault: FileVault): FilesRestored {
        val stored = LinkedHashSet<String>()
        val owner = HashMap<String, BackupAttachment>()
        val couldNotCreate = HashSet<String>()
        var damaged = 0; var missing = 0; var notStored = 0; var notWanted = 0
        reader.readFiles(
            want = { a ->
                val id = a.localId()
                if (id !in wanted || !a.isRestorable() || id in owner) null
                else { owner[id] = a; try { vault.create(id) } catch (e: Exception) { couldNotCreate += id; null } }
            },
            done = { a, r ->
                val id = a.localId()
                when {
                    owner[id] !== a -> notWanted++
                    r == FileResult.OK -> stored += id
                    r == FileResult.DAMAGED -> { damaged++; vault.delete(id) }
                    r == FileResult.MISSING -> { missing++; vault.delete(id) }
                    else -> { notStored++; if (id !in couldNotCreate) vault.delete(id) }
                }
            },
        )
        return FilesRestored(stored, damaged, missing, notStored, notWanted)
    }

    /**
     * After a restore: removes the files in [stored] that have no attachment record, and only those. [recorded] is which of them have one now (from
     * [BackupStore.recordedAttachments]), whoever wrote it: a file a concurrent restore recorded is never removed, whatever this restore's own transaction did.
     */
    fun discardUnrecorded(stored: Set<String>, recorded: Set<String>, vault: FileVault) {
        for (id in stored) if (id !in recorded) vault.delete(id)
    }

    /**
     * The photos and files of [data] that can be opened and read now. A backup is made from these: one that cannot be opened (deleted, no permission, a bad
     * block at its start) is left out of the list instead of failing the backup, and counted. Returns the data without them and how many were left out.
     * A file that fails later, in the middle, is handled by [BackupFile.write].
     */
    fun leaveOutUnreadable(data: BackupData, vault: FileVault): Pair<BackupData, Int> {
        if (data.attachments.isEmpty()) return data to 0
        val probe = ByteArray(1)
        val readable = data.attachments.filter { a ->
            try {
                val source = vault.open(a.id)
                if (source == null) false else try { source.read(probe, 0, 1) >= 0 } finally { source.close() }
            } catch (e: Exception) {
                false
            }
        }
        return data.copy(attachments = readable) to (data.attachments.size - readable.size)
    }
}
