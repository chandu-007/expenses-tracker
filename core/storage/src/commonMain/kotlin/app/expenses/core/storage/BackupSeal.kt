package app.expenses.core.storage

/**
 * What a platform supplies for passphrase protection: only the primitives, so the file layout below is written once, in common code, and cannot
 * differ between Android and iPhone. (The older single-block layout, `EXPENC1`, is still each platform's own and is still written for a backup that
 * has no files in it.)
 */
interface SealPrimitives {
    /** PBKDF2-HMAC-SHA256 of the passphrase's UTF-8 bytes, 32 bytes long. */
    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray

    /** AES-256-GCM with a 12-byte [nonce] and [aad] authenticated: the ciphertext followed by the 16-byte tag. */
    fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray

    /** The inverse of [seal], or null when the tag does not match (wrong key, changed or damaged data). */
    fun unseal(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray?

    fun randomBytes(count: Int): ByteArray
}

/**
 * The passphrase layout for a backup that carries files (`EXPENC2`), made to be written and read in pieces so a large backup never sits in memory:
 *
 *     "EXPENC2\n"  iterations (4 bytes, big-endian)  salt (16)  chunkBytes (4, big-endian)      = the 32-byte header
 *     then chunks, each AES-256-GCM(key, nonce, aad, plaintext) as ciphertext followed by its 16-byte tag
 *
 * The key is PBKDF2-HMAC-SHA256 of the passphrase with the salt. Every chunk but the last holds exactly `chunkBytes` of the plain file; the last holds
 * 0 to `chunkBytes` (0 only for an empty file). Chunk number i (from 0) uses the nonce 4 zero bytes then i as 8 bytes big-endian, and authenticates the
 * header followed by one byte, 1 for the last chunk and 0 for any other. So chunks cannot be reordered, dropped from the end or taken from another
 * file without the tag failing, and a changed iteration count, salt or chunk size fails at once. The plain file inside is an ordinary backup file.
 */
object BackupSeal {
    private val MAGIC = "EXPENC2\n".encodeToByteArray()
    const val ITERATIONS = 600_000
    const val CHUNK_BYTES = 64 * 1024
    const val MIN_PASSPHRASE = 8
    private const val SALT_BYTES = 16
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 8 + 4 + SALT_BYTES + 4
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 5_000_000
    private const val MIN_ACCEPTED_CHUNK = 4 * 1024
    private const val MAX_ACCEPTED_CHUNK = 1024 * 1024

    private val LEGACY_MAGIC = "EXPENC1\n".encodeToByteArray()

    /** Whether a file that starts with [start] is sealed the older way (one block: every backup without files), which each platform's own cipher opens. */
    fun isLegacySealed(start: ByteArray): Boolean = start.size >= LEGACY_MAGIC.size && LEGACY_MAGIC.indices.all { start[it] == LEGACY_MAGIC[it] }

    /** Whether a file that starts with [start] is sealed this way. */
    fun isSealed(start: ByteArray): Boolean = start.size >= MAGIC.size && MAGIC.indices.all { start[it] == MAGIC[it] }

    /** Writes the header to [inner] and returns a sink that seals what is written to it. Closing it writes the last chunk and closes [inner]. */
    fun sink(inner: ByteSink, passphrase: String, primitives: SealPrimitives, iterations: Int = ITERATIONS, chunkBytes: Int = CHUNK_BYTES, salt: ByteArray = primitives.randomBytes(SALT_BYTES)): SealedSink {
        require(passphrase.length >= MIN_PASSPHRASE) { "The passphrase must be at least $MIN_PASSPHRASE characters" }
        require(chunkBytes in MIN_ACCEPTED_CHUNK..MAX_ACCEPTED_CHUNK && salt.size == SALT_BYTES)
        val header = MAGIC + intBytes(iterations) + salt + intBytes(chunkBytes)
        val key = primitives.deriveKey(passphrase, salt, iterations)
        inner.write(header, 0, header.size)
        return SealedSink(inner, primitives, key, header, chunkBytes)
    }

    /**
     * Reads the header of a sealed file from [inner] and returns the plain bytes. [keyFor] gets the salt and iteration count and returns the key (derived
     * from the passphrase, or one kept from opening the file before). Throws [BackupException] for a header that is not ours; a wrong passphrase shows
     * up on the first read, as "the passphrase is wrong, or the file is damaged".
     */
    fun source(inner: ByteSource, primitives: SealPrimitives, keyFor: (salt: ByteArray, iterations: Int) -> ByteArray): DamageTolerantSource {
        val header = ByteArray(HEADER_BYTES)
        if (inner.readFully(header) < HEADER_BYTES || !isSealed(header)) throw BackupException("This is not a backup file from this app.")
        val iterations = int(header, 8)
        val chunkBytes = int(header, 8 + 4 + SALT_BYTES)
        if (iterations !in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS || chunkBytes !in MIN_ACCEPTED_CHUNK..MAX_ACCEPTED_CHUNK) throw BackupException("This backup file is damaged.")
        val salt = header.copyOfRange(12, 12 + SALT_BYTES)
        return SealedSource(inner, primitives, keyFor(salt, iterations), header, chunkBytes)
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun int(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun nonce(index: Long): ByteArray {
        val n = ByteArray(12)
        for (i in 0 until 8) n[11 - i] = (index ushr (8 * i)).toByte()
        return n
    }

    private fun aad(header: ByteArray, last: Boolean) = header + byteArrayOf(if (last) 1 else 0)

    /** The sink [sink] returns. [key] is what the passphrase derived (kept so the file just written can be checked without deriving it a second time: key derivation is deliberately slow). */
    class SealedSink internal constructor(private val inner: ByteSink, private val primitives: SealPrimitives, internal val key: ByteArray, private val header: ByteArray, private val chunkBytes: Int) : ByteSink {
        private val buf = ByteArray(chunkBytes)
        private var filled = 0
        private var index = 0L
        private var closed = false

        override fun write(buf: ByteArray, off: Int, len: Int) {
            check(!closed) { "closed" }
            var i = off
            val end = off + len
            while (i < end) {
                // a full chunk is sealed only when more arrives, so the last chunk is known to be the last
                if (filled == chunkBytes) flush(last = false)
                val n = minOf(chunkBytes - filled, end - i)
                buf.copyInto(this.buf, filled, i, i + n)
                filled += n; i += n
            }
        }

        private fun flush(last: Boolean) {
            val sealed = primitives.seal(key, nonce(index), aad(header, last), if (filled == chunkBytes) buf else buf.copyOf(filled))
            inner.write(sealed, 0, sealed.size)
            index++
            filled = 0
        }

        override fun close() {
            if (closed) return
            closed = true
            // if the last chunk cannot be written (no space left), the stream underneath is still released, not left open
            try { flush(last = true) } catch (e: Throwable) { try { inner.abort() } catch (_: Exception) { }; throw e }
            inner.close()
        }

        override fun abort() {
            if (closed) return
            closed = true
            inner.abort()
        }
    }

    private class SealedSource(private val inner: ByteSource, private val primitives: SealPrimitives, private val key: ByteArray, private val header: ByteArray, private val chunkBytes: Int) : DamageTolerantSource {
        override var skipDamage = false
        private val sealedSize = chunkBytes + TAG_BYTES
        private var current = ByteArray(0)
        private var at = 0
        private var index = 0L
        private var ahead: ByteArray? = null
        private var started = false
        private var finished = false

        private fun readSealed(): ByteArray? {
            val b = ByteArray(sealedSize)
            val n = inner.readFully(b)
            return when (n) { 0 -> null; sealedSize -> b; else -> b.copyOf(n) }
        }

        /** Opens the next chunk into [current]; false at the end. */
        private fun next(): Boolean {
            if (finished) return false
            if (!started) { started = true; ahead = readSealed() }
            val sealed = ahead
            if (sealed == null) {
                finished = true
                if (!skipDamage) throw BackupException(if (index == 0L) "This backup file is damaged." else "This backup file is incomplete: it was cut short.")
                return false
            }
            // a short chunk can only be the last one; a full one is the last only when nothing follows it
            ahead = if (sealed.size == sealedSize) readSealed() else null
            val last = ahead == null
            var plain = if (sealed.size >= TAG_BYTES) primitives.unseal(key, nonce(index), aad(header, last), sealed) else null
            if (plain == null && last && sealed.size >= TAG_BYTES) {
                // it opens as an ordinary chunk: the file was cut off after it, so what it holds is genuine but the end is missing
                primitives.unseal(key, nonce(index), aad(header, false), sealed)?.let {
                    if (!skipDamage) throw BackupException("This backup file is incomplete: it was cut short.")
                    current = it; at = 0; index++; finished = true
                    return it.isNotEmpty()
                }
            }
            if (plain == null) {
                if (index == 0L) throw BackupException("The passphrase is wrong, or the file is damaged.")
                if (!skipDamage) throw BackupException("This backup file is damaged: part of it could not be read.")
                plain = ByteArray(maxOf(sealed.size - TAG_BYTES, 0)) // what was here cannot be trusted: stands in as zeros, so the files that lie across it fail their own checksum
            }
            current = plain; at = 0; index++
            if (last) finished = true
            return true
        }

        override fun read(buf: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (at >= current.size) { if (!next()) return -1 }
            val n = minOf(len, current.size - at)
            current.copyInto(buf, off, at, at + n)
            at += n
            return n
        }

        override fun close() = inner.close()
    }
}
