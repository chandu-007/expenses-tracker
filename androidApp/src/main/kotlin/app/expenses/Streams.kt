package app.expenses

import app.expenses.core.storage.ByteSink
import app.expenses.core.storage.ByteSource
import java.io.InputStream
import java.io.OutputStream

/** A java.io input stream as the shared code's [ByteSource]. [length] is the size when the system said it, else -1. */
class InputStreamSource(private val input: InputStream, override val length: Long = -1L) : ByteSource {
    override fun read(buf: ByteArray, off: Int, len: Int): Int = if (len == 0) 0 else input.read(buf, off, len)
    override fun close() = input.close()
}

/** A java.io output stream as the shared code's [ByteSink]; closing flushes and closes it. */
class OutputStreamSink(private val output: OutputStream) : ByteSink {
    override fun write(buf: ByteArray, off: Int, len: Int) = output.write(buf, off, len)
    /** Closes the stream even when the last flush fails (no space left), so the file is never left open. */
    override fun close() { try { output.flush() } finally { output.close() } }

    override fun abort() { try { output.close() } catch (_: Exception) { } }
}
