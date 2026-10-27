package app.expenses.core.storage

import kotlinx.serialization.json.Json

/** What happened to one file of a backup when it was read. */
enum class FileResult {
    /** Read in full and its checksum matched. */
    OK,

    /** Not wanted by the caller: its bytes were read past and thrown away. */
    SKIPPED,

    /** Read in full, but the bytes do not match the checksum the backup lists for it. */
    DAMAGED,

    /** The backup ended (or could not be read on) before this file was complete. */
    MISSING,

    /** The caller's place for it could not be written to (no space left, say). */
    NOT_STORED,
}

/** One file to put in a backup: what it is, and a way to read its bytes (opened when its turn comes, closed after it). */
class BackupFileSource(val meta: BackupAttachment, val open: () -> ByteSource)

/**
 * What [BackupFile.write] wrote. [mismatched] are files whose bytes on this phone no longer match their own recorded checksum, or that could not be read
 * while the backup was written ([unreadable], a subset): each is in the list with the bytes it had (zeros for what could not be read) and is skipped on restore.
 */
class BackupWritten(val bytes: Long, val files: Int, val mismatched: Set<String>, val unreadable: Set<String> = emptySet())

/** Throws away what it is given. For reading a file only to check it. */
object DiscardSink : ByteSink {
    override fun write(buf: ByteArray, off: Int, len: Int) {}
    override fun close() {}
}

/**
 * A backup file that was opened and checked up to its payload: [header] and [data] are ready, and the bytes of its files (if it has any) are still to be
 * read from [rest], in order, by [readFiles]. Close it when done.
 */
class BackupReader internal constructor(val header: BackupHeader, val data: BackupData, private val rest: ByteSource) {
    /**
     * Reads the files listed in [BackupData.attachments], in the order they are stored. For each one [want] says where to put its bytes (a sink, closed
     * after the file) or null to read past it; [done] hears how it went. A file that does not match its checksum, or that is cut off, is reported and
     * reading goes on with the next: damage to one file never stops the rest. Does not throw (an unreadable source ends the files that were still to come).
     */
    fun readFiles(want: (BackupAttachment) -> ByteSink?, done: (BackupAttachment, FileResult) -> Unit) {
        (rest as? DamageTolerantSource)?.skipDamage = true
        val buf = ByteArray(COPY_BUFFER)
        var ended = false
        for (a in data.attachments) {
            if (ended) { done(a, FileResult.MISSING); continue }
            val sink = try { want(a) } catch (e: Exception) { null }
            val digest = Sha256.Digest()
            var remaining = a.sizeBytes
            var storeFailed = false
            while (remaining > 0) {
                val n = try { rest.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt()) } catch (e: Exception) { -1 }
                if (n < 0) { ended = true; break }
                if (sink != null && !storeFailed) {
                    digest.update(buf, 0, n)
                    try { sink.write(buf, 0, n) } catch (e: Exception) { storeFailed = true }
                }
                remaining -= n
            }
            if (sink != null) try { sink.close() } catch (e: Exception) { storeFailed = true }
            done(
                a,
                when {
                    ended || remaining > 0 -> FileResult.MISSING
                    sink == null -> FileResult.SKIPPED
                    storeFailed -> FileResult.NOT_STORED
                    digest.finishHex() == a.sha256 -> FileResult.OK
                    else -> FileResult.DAMAGED
                },
            )
        }
    }

    fun close() = try { rest.close() } catch (e: Exception) { }
}

object BackupFile {
    const val FORMAT = "expenses-backup"

    /** The newest version this app reads. A backup without files is written as version 1, one with files as version 2. */
    const val VERSION = 2

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The first line of a backup is about 150 bytes; this much is looked at before a file is called "not a backup". */
    private const val MAX_HEADER_LINE = 4096

    /**
     * The most payload (everything but the photos and files) a backup may have, by default: about 548 bytes per entry in a populated test ledger, so about
     * 245,000 entries, far more than the 100,000 the app is built and measured for. The payload is held in memory: opening needs about 6 times its size in heap
     * (the growing buffer, its copy, the text, the parsed objects) and writing about 8 times (the objects, the text, its bytes, the hash input, the file head),
     * so a phone with little memory lowers it: [maxPayloadBytes].
     */
    const val MAX_PAYLOAD_BYTES = 128 * 1024 * 1024

    /** The limit in force: [MAX_PAYLOAD_BYTES] unless the platform set a lower one for its memory (see `Platform.maxBackupPayloadBytes`). */
    @kotlin.concurrent.Volatile
    var maxPayloadBytes: Int = MAX_PAYLOAD_BYTES

    /** A backup without files, as bytes (version 1: exactly what earlier versions of the app wrote). */
    fun encode(data: BackupData, createdAtMillis: Long): ByteArray {
        require(data.attachments.isEmpty()) { "A backup with files is written with BackupFile.write" }
        try {
            val payload = json.encodeToString(BackupData.serializer(), data).encodeToByteArray()
            checkPayloadSize(payload.size)
            val header = json.encodeToString(BackupHeader.serializer(), BackupHeader(FORMAT, 1, createdAtMillis, payload.size, Sha256.hex(payload)))
            return header.encodeToByteArray() + byteArrayOf('\n'.code.toByte()) + payload
        } catch (e: OutOfMemoryError) {
            throw BackupException(NO_MEMORY_TO_WRITE)
        }
    }

    /**
     * Writes a backup to [out] and closes it: [data] (its attachments list is replaced by the one made from [files]) and then the bytes of each file,
     * one after the other, copied in pieces so nothing large is held. With no [files] it is a version 1 file, exactly as [encode] makes it.
     * A file that cannot be opened or read now, or is shorter than listed, does not fail the backup: the rest of its place is written as zeros (so the files after
     * it stay where the list says), it is reported in [BackupWritten.unreadable], and a restore finds it damaged and skips it. A failure to write to [out] does fail it.
     */
    fun write(data: BackupData, files: List<BackupFileSource>, createdAtMillis: Long, out: ByteSink): BackupWritten {
        val mismatched = HashSet<String>()
        val unreadable = HashSet<String>()
        val buf = ByteArray(COPY_BUFFER)
        var total = 0L
        try {
            // inside the try: a payload that is too large, or that the phone has no memory for, aborts the output that is already open
            val payload = try {
                json.encodeToString(BackupData.serializer(), data.copy(attachments = files.map { it.meta })).encodeToByteArray()
            } catch (e: OutOfMemoryError) {
                throw BackupException(NO_MEMORY_TO_WRITE)
            }
            checkPayloadSize(payload.size)
            val head = try {
                val header = json.encodeToString(BackupHeader.serializer(), BackupHeader(FORMAT, if (files.isEmpty()) 1 else 2, createdAtMillis, payload.size, Sha256.hex(payload)))
                header.encodeToByteArray() + byteArrayOf('\n'.code.toByte())
            } catch (e: OutOfMemoryError) {
                throw BackupException(NO_MEMORY_TO_WRITE)
            }
            total = head.size.toLong() + payload.size
            out.write(head, 0, head.size)
            out.write(payload, 0, payload.size)
            for (f in files) {
                val digest = Sha256.Digest()
                var remaining = f.meta.sizeBytes
                val source = try { f.open() } catch (e: Exception) { null }
                var failed = source == null
                try {
                    while (remaining > 0 && source != null && !failed) {
                        val n = try { source.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt()) } catch (e: Exception) { -1 }
                        if (n < 0) { failed = true; break }
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        remaining -= n
                    }
                } finally {
                    try { source?.close() } catch (_: Exception) { }
                }
                if (failed) {
                    buf.fill(0)
                    while (remaining > 0) { val n = minOf(buf.size.toLong(), remaining).toInt(); out.write(buf, 0, n); remaining -= n }
                    unreadable += f.meta.id; mismatched += f.meta.id
                } else if (digest.finishHex() != f.meta.sha256) mismatched += f.meta.id
                total += f.meta.sizeBytes
            }
            out.close()
        } catch (e: Throwable) {
            try { out.abort() } catch (_: Exception) { }
            throw e
        }
        return BackupWritten(total, files.size, mismatched, unreadable)
    }

    /** Checks the file and returns its contents, or throws [BackupException] saying what is wrong. Nothing is written anywhere. Files that follow the payload are not read. */
    fun decode(raw: ByteArray): Pair<BackupHeader, BackupData> = open(ByteArraySource(raw)).let { it.header to it.data }

    /**
     * Reads the start of a backup from [source] (which must be a plain backup file: a sealed one is opened with [BackupSeal] first) and checks it: the
     * header, the payload length and checksum, and that the payload reads. Throws [BackupException] saying what is wrong. The files that follow the payload
     * of a version 2 file are read with [BackupReader.readFiles]; the source is closed when the reader is, or here if the file is refused.
     */
    fun open(source: ByteSource): BackupReader {
        try {
            return read(source)
        } catch (e: OutOfMemoryError) {
            try { source.close() } catch (_: Exception) { }
            throw BackupException("This backup is too large to open on this phone. Nothing was changed.")
        } catch (e: Throwable) {
            try { source.close() } catch (_: Exception) { }
            throw e
        }
    }

    private fun read(source: ByteSource): BackupReader {
        val input = Lookahead(source)
        val first = input.first8()
        fun about() = describe(first, source.length)
        // a copy that went through an email, a chat or a cloud drive can arrive with a byte-order mark or blank space in front, or a newline behind
        input.skipLeadingNoise()
        val c0 = input.peek(0)
        if (c0 < 0) throw BackupException("That file is empty, so it is not a backup. A download that did not finish can leave an empty file: save the backup again and choose it once more.")
        if (c0 == 'P'.code && input.peek(1) == 'K'.code) throw BackupException("That is a zip or Excel file, not a backup. A backup file ends in .expbak. ${about()}")
        if (c0 == '<'.code) throw BackupException("That is a web page, not a backup: the download may have been blocked or asked you to sign in. ${about()}")
        val line = input.line(MAX_HEADER_LINE) ?: throw BackupException("This is not a backup file from this app. ${about()}")
        val header = try {
            json.decodeFromString(BackupHeader.serializer(), line.decodeToString().trim())
        } catch (e: Exception) {
            throw BackupException("This is not a backup file from this app. ${about()}")
        }
        if (header.format != FORMAT) throw BackupException("This is not a backup file from this app (it says it is \"${header.format.take(40)}\"). ${about()}")
        if (header.version > VERSION) throw BackupException("This backup was made by a newer version of the app. Update the app to restore it.")
        if (header.version < 1 || header.payloadBytes < 0) throw BackupException("This backup file is not valid.")
        if (header.payloadBytes > maxPayloadBytes) throw BackupException("This backup file is not valid: its data section claims to be over ${maxPayloadBytes / 1024 / 1024} MB, more than this phone can open. It may be damaged.")

        // the payload: read only as far as the file actually goes, so a wrong length in the header cannot ask for memory the file does not have
        val payloadOut = ByteArraySink(minOf(header.payloadBytes, 1 shl 20))
        val buf = ByteArray(COPY_BUFFER)
        var got = 0L
        while (got < header.payloadBytes) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), header.payloadBytes - got).toInt())
            if (n < 0) break
            payloadOut.write(buf, 0, n)
            got += n
        }
        if (got < header.payloadBytes) throw BackupException("This backup file is incomplete or damaged (it should hold ${header.payloadBytes} bytes of data but holds $got).")
        if (header.version == 1) {
            // blank space after the data (a trailing newline added on the way) is not part of it; anything else means the length is wrong
            var extra = 0L
            var onlyBlank = true
            while (true) {
                val n = input.read(buf, 0, buf.size)
                if (n < 0) break
                extra += n
                for (i in 0 until n) if (buf[i] != '\n'.code.toByte() && buf[i] != '\r'.code.toByte() && buf[i] != ' '.code.toByte()) onlyBlank = false
            }
            if (!onlyBlank) throw BackupException("This backup file is incomplete or damaged (it should hold ${header.payloadBytes} bytes of data but holds ${got + extra}).")
        }
        val payload = payloadOut.toByteArray()
        if (Sha256.hex(payload) != header.sha256) throw BackupException("This backup file is damaged: its contents do not match its checksum. Nothing was changed.")
        val data = try {
            json.decodeFromString(BackupData.serializer(), payload.decodeToString())
        } catch (e: Exception) {
            throw BackupException("This backup file could not be read (${e.message?.lineSequence()?.firstOrNull() ?: "unknown error"}).")
        }
        // version 1 has no file bytes after the payload, so whatever its list says there is nothing to read
        return if (header.version >= 2) BackupReader(header, data, input) else BackupReader(header, data.copy(attachments = emptyList()), EmptySource.also { input.close() })
    }

    private fun checkPayloadSize(bytes: Int) {
        if (bytes > maxPayloadBytes) throw BackupException("There is too much data to put in one backup file on this phone (its entries alone take over ${maxPayloadBytes / 1024 / 1024} MB).")
    }

    /** The data part of a backup (entries, settings: not the photos, which are copied in pieces) could not be built in the memory this phone gives the app. */
    const val NO_MEMORY_TO_WRITE = "There is not enough memory on this phone to make a backup of this much data. Nothing was saved."


    /** What to tell about a file that is not a backup, without showing any of its words: its size (when it is known) and the first bytes in hex. */
    private fun describe(first: ByteArray, length: Long): String {
        val hex = first.take(8).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return if (length >= 0) "(The file has $length bytes and begins $hex.)" else "(The file begins $hex.)"
    }

    private object EmptySource : ByteSource {
        override fun read(buf: ByteArray, off: Int, len: Int) = -1
        override fun close() {}
    }

    /** A small buffer in front of the source, for looking at the first bytes of a file before deciding what it is. */
    private class Lookahead(private val inner: ByteSource) : DamageTolerantSource {
        override var skipDamage: Boolean
            get() = (inner as? DamageTolerantSource)?.skipDamage ?: false
            set(value) { (inner as? DamageTolerantSource)?.skipDamage = value }

        private var buf = ByteArray(8192)
        private var pos = 0
        private var lim = 0
        private var eof = false
        private var firstBytes = ByteArray(0)

        /** Makes at least [n] bytes available from the current position, if the file has them. */
        private fun ensure(n: Int): Boolean {
            while (lim - pos < n && !eof) {
                if (pos > 0 && lim == buf.size) { buf.copyInto(buf, 0, pos, lim); lim -= pos; pos = 0 }
                if (lim == buf.size) buf = buf.copyOf(buf.size * 2)
                val r = inner.read(buf, lim, buf.size - lim)
                if (r < 0) eof = true else lim += r
            }
            return lim - pos >= n
        }

        /** The first eight bytes of the file as it arrived, before any blank space in front is skipped. */
        fun first8(): ByteArray { ensure(8); firstBytes = buf.copyOfRange(pos, minOf(lim, pos + 8)); return firstBytes }

        fun peek(i: Int): Int = if (ensure(i + 1)) buf[pos + i].toInt() and 0xFF else -1

        fun skipLeadingNoise() {
            if (ensure(3) && buf[pos] == 0xEF.toByte() && buf[pos + 1] == 0xBB.toByte() && buf[pos + 2] == 0xBF.toByte()) pos += 3
            while (true) {
                val c = peek(0)
                if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) pos++ else break
            }
        }

        /** The bytes up to the next newline (which is consumed), or null if there is none within [max] bytes. */
        fun line(max: Int): ByteArray? {
            var i = 0
            while (i < max) {
                val c = peek(i)
                if (c < 0) return null
                if (c == '\n'.code) { val out = buf.copyOfRange(pos, pos + i); pos += i + 1; return out }
                i++
            }
            return null
        }

        override fun read(buf: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos == lim) {
                if (eof) return -1
                val r = inner.read(buf, off, len)
                if (r < 0) eof = true
                return r
            }
            val n = minOf(len, lim - pos)
            this.buf.copyInto(buf, off, pos, pos + n)
            pos += n
            return n
        }

        override fun close() = inner.close()
    }
}
