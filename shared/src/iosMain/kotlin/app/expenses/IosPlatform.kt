package app.expenses

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.expenses.core.domain.Notice
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.Sha256
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val NOT_ON_IPHONE = "not available on iPhone"

/**
 * The iPhone implementation of [Platform]. The database is a plain (not yet encrypted) SQLite file in Application Support; files are read
 * and written through file URLs. SMS and notification capture, the widget, app lock, notifications, automatic export, passphrase-protected
 * backups and .xlsx import are not available yet and report so through the capability flags or a clear error.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
class IosPlatform : Platform {
    override val supportsAppLock get() = false
    override val supportsBiometricLock get() = false
    override val supportsNotifications get() = false
    override val supportsAutoExport get() = false
    override val supportsCaptureSources get() = false
    override val supportsSmsCapture get() = false
    override val supportsNotificationCapture get() = false
    override val supportsWidget get() = false
    override val seedingEnabled get() = false
    override val autoExportKeep get() = AutoExportKeys.KEEP
    override val backupMinPassphrase get() = 8

    override fun trace(name: String) {}

    private val files = NSFileManager.defaultManager

    /** Application Support/expenses, created on first use. Never backed up selectively and never shown in the Files app. */
    private val supportDir: String by lazy {
        val base = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: error("No Application Support folder")
        "$base/expenses".also { files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null) }
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
    }

    override fun displayName(uri: String): String? = urlOf(uri).lastPathComponent
    override fun mimeType(uri: String): String? = null
    override fun folderDisplayName(uri: String): String? = urlOf(uri).lastPathComponent

    override val attachmentFiles: AttachmentFileStore = object : AttachmentFileStore {
        private val dir: String by lazy { "$supportDir/attachments".also { files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null) } }

        override fun copyIn(id: String, source: String): AttachmentCopy {
            val bytes = readBytes(source)
            app.expenses.core.domain.AttachmentRules.checkAdd(0, bytes.size.toLong())?.let { throw IllegalArgumentException(it) }
            if (!bytes.toNSData().writeToURL(NSURL.fileURLWithPath("$dir/$id"), atomically = true)) throw IllegalArgumentException("That file could not be read: it could not be stored.")
            return AttachmentCopy(bytes.size.toLong(), Sha256.hex(bytes))
        }

        override fun delete(id: String) { files.removeItemAtPath("$dir/$id", error = null) }
        override fun shareUri(id: String): String = NSURL.fileURLWithPath("$dir/$id").absoluteString.orEmpty()
        override fun sweep(recorded: Set<String>): Int =
            (files.contentsOfDirectoryAtPath(dir, error = null).orEmpty().filterIsInstance<String>()).filter { it !in recorded }.count { files.removeItemAtPath("$dir/$it", error = null) }
    }

    /** Passphrase-protected backups are not available on iPhone yet; an unprotected backup works, and a protected one made on Android is recognised and refused with a clear message. */
    override val backupCipher: BackupCipher = object : BackupCipher {
        private val magic = "EXPENC1\n".encodeToByteArray()
        override val minPassphrase get() = 8
        override fun isEncrypted(bytes: ByteArray) = bytes.size > magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)
        override fun encrypt(plain: ByteArray, passphrase: String): ByteArray = throw UnsupportedOperationException("Passphrase-protected backups are $NOT_ON_IPHONE yet")
        override fun decrypt(bytes: ByteArray, passphrase: String): ByteArray = throw BackupException("Passphrase-protected backups cannot be opened on iPhone yet.")
    }

    override suspend fun runAutoExport(container: AppContainer, folderUri: String?, nowMillis: Long, force: Boolean) {}

    override val captureChanges: Flow<Unit> = emptyFlow()
    override fun isSmsPermissionGranted(): Boolean = false
    override fun notificationsAllowed(): Boolean = false
    override fun showNotices(notices: List<Notice>): List<Notice> = emptyList()

    override fun authenticate(title: String, subtitle: String?, onResult: (success: Boolean, message: String?, cancelled: Boolean) -> Unit) {
        onResult(false, "App lock is $NOT_ON_IPHONE yet.", false)
    }

    override fun decodeStatementText(bytes: ByteArray): String {
        fun utf16(from: Int, bigEndian: Boolean): String = buildString {
            var i = from
            while (i + 1 < bytes.size) {
                val a = bytes[i].toInt() and 0xFF; val b = bytes[i + 1].toInt() and 0xFF
                append((if (bigEndian) (a shl 8) or b else (b shl 8) or a).toChar())
                i += 2
            }
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return utf16(2, bigEndian = false)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return utf16(2, bigEndian = true)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return try {
            bytes.decodeToString(start, bytes.size, throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            buildString { for (i in start until bytes.size) append(windows1252(bytes[i].toInt() and 0xFF)) }
        }
    }

    override fun looksLikeXlsx(bytes: ByteArray): Boolean = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    override fun readXlsx(bytes: ByteArray): List<List<String>> = throw IllegalArgumentException("Excel workbooks (.xlsx) cannot be imported on iPhone yet. Export the statement as a CSV file instead.")
}

private fun windows1252(b: Int): Char = if (b in 0x80..0x9F) WINDOWS_1252_HIGH[b - 0x80] else b.toChar()

/** Code points of bytes 0x80..0x9F in Windows-1252 (the five undefined bytes map to themselves, as the Windows decoder does). */
private val WINDOWS_1252_HIGH = charArrayOf(
    '\u20AC', '\u0081', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021', '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', '\u008D', '\u017D', '\u008F',
    '\u0090', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014', '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\u009D', '\u017E', '\u0178',
)

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
