@file:OptIn(ExperimentalForeignApi::class)

package app.expenses

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.URLByAppendingPathComponent
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL

/**
 * Automatic copies into a folder from the Files app (iCloud Drive, On My iPhone, another provider), as on Android: a backup and an Excel file on a schedule, the newest few kept.
 * The folder is remembered as a security-scoped bookmark made when it was chosen, since iOS lets an app into a folder it was given only through one. The run itself is
 * [AutoExportRunner]. iOS decides when a background refresh runs, so a due export is also done whenever the app comes forward.
 */
internal object IosAutoExport {
    private val defaults get() = NSUserDefaults.standardUserDefaults
    private fun key(uri: String) = "export_bookmark:$uri"

    /** Called with the folder the person picked, while the system still lets the app into it: keeps a bookmark so later runs can open it. */
    fun remember(url: NSURL) {
        val started = url.startAccessingSecurityScopedResource()
        try {
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val data = url.bookmarkDataWithOptions(0uL, null, null, error.ptr)
                if (data != null) defaults.setObject(data, forKey = key(url.absoluteString.orEmpty()))
            }
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun resolve(uri: String): NSURL? {
        val data = defaults.dataForKey(key(uri))
        if (data != null) {
            memScoped {
                val stale = alloc<BooleanVar>()
                val error = alloc<ObjCObjectVar<NSError?>>()
                NSURL.URLByResolvingBookmarkData(data, 0uL, null, stale.ptr, error.ptr)?.let { return it }
            }
        }
        return NSURL.URLWithString(uri)
    }

    suspend fun run(container: AppContainer, folderUri: String?, now: Long, force: Boolean) {
        val url = folderUri?.let(::resolve)
        AutoExportRunner.run(container, url?.let(::Folder), now, force)
    }

    /** The scheduled run: does nothing unless a folder was chosen and the schedule says it is due. */
    suspend fun runIfDue(container: AppContainer) {
        val uri = container.write { it.templates.setting(AutoExportKeys.FOLDER) } ?: return
        run(container, uri, nowMillis(), force = false)
    }

    private class Folder(private val dir: NSURL) : ExportFolder {
        private val files get() = NSFileManager.defaultManager

        private fun <T> scoped(block: () -> T): T {
            val started = dir.startAccessingSecurityScopedResource()
            try { return block() } finally { if (started) dir.stopAccessingSecurityScopedResource() }
        }

        private fun file(name: String): NSURL = dir.URLByAppendingPathComponent(name)!!

        override fun names(): List<String> = scoped {
            files.contentsOfDirectoryAtPath(dir.path.orEmpty(), error = null)?.filterIsInstance<String>()
                ?: throw IllegalStateException("The folder cannot be read. It may have been removed, or the permission was withdrawn.")
        }

        override fun write(name: String, mime: String, bytes: ByteArray): String? = scoped { if (bytes.toNSData().writeToURL(file(name), atomically = true)) name else null }
        override fun read(name: String): ByteArray? = scoped { NSData.dataWithContentsOfURL(file(name))?.toByteArray() }
        override fun delete(name: String): Boolean = scoped { files.removeItemAtURL(file(name), error = null) }
    }
}

/** Test entry for the Swift unit tests: a copy into [folderPath] now, answering with the sentence the screen would show. */
object IosAutoExportDebug {
    suspend fun exportInto(host: IosHostServices, folderPath: String, now: Long): String {
        val core = IosCore.get(host)
        val uri = NSURL.fileURLWithPath(folderPath).absoluteString.orEmpty()
        core.container.write { it.templates.putSetting(AutoExportKeys.FOLDER, uri) }
        IosAutoExport.run(core.container, uri, now, force = true)
        return core.container.write { it.templates.setting(AutoExportKeys.STATUS) }.orEmpty()
    }
}
