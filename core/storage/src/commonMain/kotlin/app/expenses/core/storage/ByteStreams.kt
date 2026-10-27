package app.expenses.core.storage

/**
 * Bytes that are read in order and never all held at once: what lets a backup that carries photos be written, read back and restored in a small
 * amount of memory. Common code has no input stream of its own, so each platform wraps the file API it has (an InputStream, an NSFileHandle).
 */
interface ByteSource {
    /** Reads up to [len] bytes into [buf] at [off] and returns how many (at least one), or -1 at the end. */
    fun read(buf: ByteArray, off: Int, len: Int): Int

    fun close()

    /** The whole length in bytes when it is known, else -1. Only used to describe a file that is not a backup. */
    val length: Long get() = -1L
}

/** Bytes that are written in order. [close] finishes the output (a sealed stream writes its last chunk) and releases it. */
interface ByteSink {
    fun write(buf: ByteArray, off: Int, len: Int)

    fun close()

    /** Gives up after a failure: releases the output without completing it (a sealed stream writes no last chunk; a file made to be shared is not offered). */
    fun abort() = close()
}

/** A source that can carry on past damage: see [BackupSeal]. Set by the backup reader once the part that must be intact has been read. */
interface DamageTolerantSource : ByteSource {
    var skipDamage: Boolean
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    private var at = 0
    override val length: Long get() = bytes.size.toLong()

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (at >= bytes.size) return -1
        val n = minOf(len, bytes.size - at)
        bytes.copyInto(buf, off, at, at + n)
        at += n
        return n
    }

    override fun close() {}
}

/** Collects what is written. [capacity] is the expected size, so the common case does not grow by copying. */
class ByteArraySink(capacity: Int = 1024) : ByteSink {
    private var data = ByteArray(maxOf(capacity, 16))
    var size = 0
        private set

    override fun write(buf: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        if (size + len > data.size) data = data.copyOf(maxOf(data.size + (data.size shr 1), size + len))
        buf.copyInto(data, size, off, off + len)
        size += len
    }

    override fun close() {}

    fun toByteArray(): ByteArray = data.copyOf(size)
}

/** Counts and hashes what passes through to [inner], so a written file can be compared with a read-back without holding it. */
class HashingSink(private val inner: ByteSink) : ByteSink {
    private val digest = Sha256.Digest()
    var count = 0L
        private set

    override fun write(buf: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        digest.update(buf, off, len)
        count += len
        inner.write(buf, off, len)
    }

    override fun close() = inner.close()

    override fun abort() = inner.abort()

    /** The SHA-256 of everything written so far. Call once, after the last write. */
    fun hex(): String = digest.finishHex()
}

/** Reads until [len] bytes arrived or the source ended; returns how many arrived (less than [len] only at the end). */
fun ByteSource.readFully(buf: ByteArray, off: Int = 0, len: Int = buf.size - off): Int {
    var got = 0
    while (got < len) {
        val n = read(buf, off + got, len - got)
        if (n < 0) break
        got += n
    }
    return got
}

/** All the remaining bytes. Only for what is known to be small (a backup without files, a header line). */
fun ByteSource.readAll(): ByteArray {
    val out = ByteArraySink(maxOf(length.coerceAtMost(Int.MAX_VALUE.toLong() - 16).toInt(), 1024))
    val buf = ByteArray(COPY_BUFFER)
    while (true) {
        val n = read(buf, 0, buf.size)
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** Reads and throws away what remains, returning how many bytes that was. */
fun ByteSource.drain(buf: ByteArray = ByteArray(COPY_BUFFER)): Long {
    var total = 0L
    while (true) {
        val n = read(buf, 0, buf.size)
        if (n < 0) return total
        total += n
    }
}

/** Size of the buffer used to move bytes: large enough to be quick, small enough to be nothing next to a phone's memory. */
const val COPY_BUFFER = 64 * 1024

/** Hands back [prefix] (bytes that were read to look at the start of a file) and then carries on with [inner]. */
class PushbackSource(private val prefix: ByteArray, private val inner: ByteSource) : ByteSource {
    private var at = 0
    override val length: Long get() = inner.length

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (at < prefix.size) {
            val n = minOf(len, prefix.size - at)
            prefix.copyInto(buf, off, at, at + n)
            at += n
            return n
        }
        return inner.read(buf, off, len)
    }

    override fun close() = inner.close()
}

/** Counts and hashes what is read from [inner]. */
class HashingSource(private val inner: ByteSource) : ByteSource {
    private val digest = Sha256.Digest()
    var count = 0L
        private set

    override val length: Long get() = inner.length

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        val n = inner.read(buf, off, len)
        if (n > 0) { digest.update(buf, off, n); count += n }
        return n
    }

    override fun close() = inner.close()

    /** Reads what is left (to count and hash all of it) and returns the SHA-256 of everything that was read. Call once. */
    fun finishHex(): String { drain(); return digest.finishHex() }
}

/**
 * A failure to write, said plainly for a person: the errors for a full disk come in different words on each system (ENOSPC, "No space left on device", "there
 * isn't enough space"), so they are recognised by their words, anywhere in the chain of causes, and become one clear [BackupException]. Anything else is returned as it is.
 */
fun Throwable.asWriteProblem(): Throwable {
    var e: Throwable? = this
    var depth = 0
    while (e != null && depth < 6) {
        val m = e.message?.lowercase().orEmpty()
        if ("enospc" in m || "no space left" in m || "not enough space" in m || "enough space" in m || "disk full" in m || "edquot" in m) {
            return BackupException("There is not enough space to save the backup. Free some space on this phone, or choose another place to save it.")
        }
        e = e.cause
        depth++
    }
    return this
}
