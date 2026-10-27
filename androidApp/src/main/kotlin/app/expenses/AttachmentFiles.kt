package app.expenses

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.expenses.core.domain.AttachmentRules
import app.expenses.core.storage.Sha256
import java.io.File

/**
 * The bytes of attachments, in the app's private storage (nothing else can read them) under a random name; the database says what each is.
 * A file is copied in with a size cap that is checked while copying, so a huge or endless source cannot fill the phone.
 */
class AttachmentFiles(private val context: Context) {
    class Copied(val sizeBytes: Long, val sha256: String)

    private val dir get() = File(context.filesDir, "attachments").also { it.mkdirs() }
    fun file(id: String) = File(dir, id)

    /** Copies [source] in as [id]. Throws [IllegalArgumentException] with a reason when it is empty or over the limit; nothing is left behind then. */
    fun copyIn(id: String, source: Uri): Copied {
        val input = context.contentResolver.openInputStream(source) ?: throw IllegalArgumentException("That file could not be opened.")
        return copyStream(id, input)
    }

    /** The copy itself: reads at most one byte past the limit, then stops. Separate so it can be tested with a source that never ends. */
    internal fun copyStream(id: String, input: java.io.InputStream): Copied {
        val target = file(id)
        try {
            var total = 0L
            input.use { i -> target.outputStream().use { o ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    total += n
                    if (total > AttachmentRules.MAX_BYTES) throw IllegalArgumentException(AttachmentRules.checkAdd(0, total)!!)
                    o.write(buf, 0, n)
                }
            } }
            AttachmentRules.checkAdd(0, total)?.let { throw IllegalArgumentException(it) }
            return Copied(total, Sha256.hex(target.readBytes()))
        } catch (e: Exception) {
            target.delete()
            throw if (e is IllegalArgumentException) e else IllegalArgumentException("That file could not be read: ${e.message ?: e::class.java.simpleName}")
        }
    }

    fun delete(id: String) { file(id).delete() }

    /** The size of the stored file, or -1 when there is none. */
    fun size(id: String): Long = file(id).let { if (it.isFile) it.length() else -1L }

    /** The stored file to read in pieces, or null when there is none. */
    fun open(id: String): InputStreamSource? = file(id).takeIf { it.isFile }?.let { InputStreamSource(it.inputStream().buffered(64 * 1024), it.length()) }

    /** A new (or replaced) file for [id], written in pieces; the caller removes it again if what was written turns out wrong. */
    fun create(id: String): OutputStreamSink = OutputStreamSink(file(id).outputStream().buffered(64 * 1024))

    /** A content address another app may read for a moment (the viewer the user picks), never the file path itself. */
    fun shareUri(id: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file(id))

    /** Removes files that no record points to (a copy that was interrupted before its record was written). Returns how many. */
    fun sweep(recorded: Set<String>): Int = dir.listFiles().orEmpty().filter { it.name !in recorded }.count { it.delete() }
}
