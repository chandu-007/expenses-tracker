package app.expenses.core.storage

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream

/**
 * Small files that must survive a power cut whole. A plain write is only in memory for some seconds after it returns: the API 34 emulator, killed 3 to 10 s after
 * the app's first launch, came back with `db_key.bin` and `node_id` both there and EMPTY (ext4), so the app said its data's key was gone and then stopped at start-up.
 * Here the bytes go to `<name>.tmp`, which is synced to the disk, then renamed over the target, and the folder is synced so the rename is on the disk as well:
 * after a power cut the target is either as it was before or the whole new file.
 */
object DurableFiles {
    fun replace(target: File, bytes: ByteArray) {
        val dir = target.absoluteFile.parentFile ?: error("No folder for ${target.name}")
        val tmp = File(dir, "${target.name}.tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        check(tmp.renameTo(target)) { "Could not store ${target.name}" }
        // the file is written and in place; a folder that cannot be synced only loses the guarantee for the rename, so that is not a failure of the write
        try {
            val fd = Os.open(dir.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
        } catch (_: ErrnoException) { }
    }
}
