@file:OptIn(ExperimentalForeignApi::class)

package app.expenses

import app.expenses.core.storage.ByteSink
import app.expenses.core.storage.ByteSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.fileHandleForWritingToURL
import platform.posix.memcpy

/**
 * Files as the shared code's streams, read and written in pieces through NSFileHandle, so a backup that carries photos is never held in memory.
 * [onClose] runs once when the stream is closed (to end a security-scoped access, or to offer a finished export to the share sheet).
 */
internal class FileHandleSource(private val handle: NSFileHandle, override val length: Long, private val onClose: () -> Unit = {}) : ByteSource {
    private var closed = false

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val data = handle.readDataUpToLength(len.convert(), error.ptr) ?: throw IllegalStateException(error.value?.localizedDescription ?: "The file could not be read.")
            val n = data.length.toInt()
            if (n == 0) -1 else {
                buf.usePinned { memcpy(it.addressOf(off), data.bytes, data.length) }
                n
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try { handle.closeAndReturnError(null) } finally { onClose() }
    }
}

internal class FileHandleSink(private val handle: NSFileHandle, private val onClose: () -> Unit = {}) : ByteSink {
    private var closed = false

    override fun write(buf: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        val data = buf.usePinned { NSData.create(bytes = it.addressOf(off), length = len.convert()) }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!handle.writeData(data, error.ptr)) {
                // POSIX error 28 is "no space left on device": said in words the backup code recognises and turns into a plain sentence
                throw IllegalStateException(if (error.value?.code == 28L) "No space left on device" else error.value?.localizedDescription ?: "The file could not be written.")
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try { handle.closeAndReturnError(null) } finally { onClose() }
    }
}

/** A file to read in pieces, or null when it cannot be opened. [onClose] runs when the source is closed (or at once if the file could not be opened). */
internal fun openFileForReading(url: NSURL, onClose: () -> Unit = {}): FileHandleSource? {
    val handle = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        NSFileHandle.fileHandleForReadingFromURL(url, error.ptr)
    }
    if (handle == null) { onClose(); return null }
    val size = (NSFileManager.defaultManager.attributesOfItemAtPath(url.path.orEmpty(), error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: -1L
    return FileHandleSource(handle, size, onClose)
}

/** A new, empty file at [url] (an existing one is replaced) to write in pieces, or null when it cannot be created. */
internal fun openFileForWriting(url: NSURL, onClose: () -> Unit = {}): FileHandleSink? {
    if (!NSFileManager.defaultManager.createFileAtPath(url.path.orEmpty(), contents = null, attributes = null)) { onClose(); return null }
    val handle = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        NSFileHandle.fileHandleForWritingToURL(url, error.ptr)
    }
    if (handle == null) { onClose(); return null }
    return FileHandleSink(handle, onClose)
}
