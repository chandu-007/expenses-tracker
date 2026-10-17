package app.expenses

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.expenses.core.domain.AttachmentRules
import app.expenses.core.domain.Notice
import app.expenses.core.storage.Sha256
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UniformTypeIdentifiers.UTType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The iPhone implementation of [Platform].
 *
 * The database is SQLite in Application Support, under iOS Data Protection (readable after the first unlock; no separate key of the app's own,
 * so [Platform.openDriver] never reports a lost key). Files come from the Files picker as file URLs (opened files are copied to the temporary
 * folder first, so they read without a security scope; a scoped URL is still opened and closed properly). A file the person wants to *save* is
 * written to a staging folder and then handed to the share sheet ([IosExport]).
 *
 * Not on iPhone, and said so by the capability flags: SMS and notification capture, the widget, notifications, automatic export to a folder.
 * [host] supplies the Swift-only crypto behind passphrase-protected backups.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
class IosPlatform(host: IosHostServices) : Platform {
    override val supportsAppLock get() = true
    override val supportsBiometricLock get() = true
    override val supportsNotifications get() = false
    override val supportsAutoExport get() = false
    override val supportsCaptureSources get() = false
    override val supportsSmsCapture get() = false
    override val supportsNotificationCapture get() = false
    override val supportsWidget get() = false
    override val seedingEnabled get() = false
    override val autoExportKeep get() = AutoExportKeys.KEEP
    override val backupMinPassphrase get() = IosBackupCipher.MIN_PASSPHRASE

    override fun trace(name: String) {}

    private val files = NSFileManager.defaultManager

    private fun ensureDir(path: String) {
        files.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication), error = null)
    }

    /** Application Support/expenses: not shown in the Files app. */
    private val supportDir: String by lazy {
        val base = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: error("No Application Support folder")
        "$base/expenses".also(::ensureDir)
    }
    private val databaseName = "expenses.db"

    override fun openDriver(): SqlDriver = NativeSqliteDriver(ExpensesDb.Schema, databaseName, onConfiguration = { it.copy(extendedConfig = it.extendedConfig.copy(basePath = supportDir)) })

    override fun resetDatabase() {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) files.removeItemAtPath("$supportDir/$databaseName$suffix", error = null)
    }

    override fun nodeId(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        return defaults.stringForKey("node_id") ?: Uuid.random().toString().take(8).also { defaults.setObject(it, forKey = "node_id") }
    }

    private fun urlOf(uri: String): NSURL = NSURL.URLWithString(uri)?.takeIf { it.scheme != null } ?: NSURL.fileURLWithPath(uri)

    override fun readBytes(uri: String): ByteArray {
        val url = urlOf(uri)
        val started = url.startAccessingSecurityScopedResource()
        try {
            return NSData.dataWithContentsOfURL(url)?.toByteArray() ?: throw IllegalArgumentException("That file could not be opened.")
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    override fun writeBytes(uri: String, bytes: ByteArray) {
        val url = urlOf(uri)
        val started = url.startAccessingSecurityScopedResource()
        try {
            if (!bytes.toNSData().writeToURL(url, atomically = true)) throw IllegalArgumentException("That location could not be written to.")
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
        IosExport.offer(url)
    }

    override fun displayName(uri: String): String? = urlOf(uri).lastPathComponent
    override fun mimeType(uri: String): String? = urlOf(uri).pathExtension?.takeIf { it.isNotEmpty() }?.let { UTType.typeWithFilenameExtension(it)?.preferredMIMEType }
    override fun folderDisplayName(uri: String): String? = urlOf(uri).lastPathComponent

    override val attachmentFiles: AttachmentFileStore = object : AttachmentFileStore {
        private val dir: String by lazy { "$supportDir/attachments".also(::ensureDir) }

        override fun copyIn(id: String, source: String): AttachmentCopy {
            val bytes = readBytes(source)
            AttachmentRules.checkAdd(0, bytes.size.toLong())?.let { throw IllegalArgumentException(it) }
            if (!bytes.toNSData().writeToURL(NSURL.fileURLWithPath("$dir/$id"), atomically = true)) throw IllegalArgumentException("That file could not be read: it could not be stored.")
            return AttachmentCopy(bytes.size.toLong(), Sha256.hex(bytes))
        }

        override fun delete(id: String) { files.removeItemAtPath("$dir/$id", error = null) }
        override fun shareUri(id: String): String = NSURL.fileURLWithPath("$dir/$id").absoluteString.orEmpty()
        override fun sweep(recorded: Set<String>): Int =
            files.contentsOfDirectoryAtPath(dir, error = null).orEmpty().filterIsInstance<String>().filter { it !in recorded }.count { files.removeItemAtPath("$dir/$it", error = null) }
    }

    override val backupCipher: BackupCipher = IosBackupCipher(host)

    override suspend fun runAutoExport(container: AppContainer, folderUri: String?, nowMillis: Long, force: Boolean) {}

    override val captureChanges: Flow<Unit> = emptyFlow()
    override fun isSmsPermissionGranted(): Boolean = false
    override fun notificationsAllowed(): Boolean = false
    override fun showNotices(notices: List<Notice>): List<Notice> = emptyList()

    override fun authenticate(title: String, subtitle: String?, onResult: (success: Boolean, message: String?, cancelled: Boolean) -> Unit) = IosAuthentication.authenticate(title, onResult)

    override fun decodeStatementText(bytes: ByteArray): String = IosStatementText.decode(bytes)
    override fun looksLikeXlsx(bytes: ByteArray): Boolean = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    override fun readXlsx(bytes: ByteArray): List<List<String>> = IosXlsx.read(bytes)
}
