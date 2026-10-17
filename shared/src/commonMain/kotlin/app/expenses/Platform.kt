package app.expenses

import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.domain.Notice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/*
 * What the shared code needs from the operating system. One implementation per platform:
 * androidApp/.../AndroidPlatform.kt and shared/src/iosMain/.../IosPlatform.kt. A URI is always a String here; the platform turns it into a
 * content Uri / file URL. Anything a platform cannot do reports it through a capability flag (never crashes the UI).
 *
 * Members (keep this list current):
 *   capabilities  supportsAppLock, supportsBiometricLock, supportsNotifications, supportsAutoExport, supportsCaptureSources,
 *                 supportsSmsCapture, supportsNotificationCapture, supportsWidget, seedingEnabled
 *   numbers       autoExportKeep, backupMinPassphrase
 *   diagnostics   trace(name)
 *   database      openDriver(), resetDatabase(), nodeId()
 *   files         readBytes(uri), writeBytes(uri, bytes), displayName(uri), mimeType(uri), folderDisplayName(uri)
 *   attachments   attachmentFiles: AttachmentFileStore
 *   backup        backupCipher: BackupCipher
 *   auto-export   runAutoExport(container, folderUri, nowMillis, force)
 *   capture       captureChanges: Flow<Unit>, isSmsPermissionGranted()
 *   notification  notificationsAllowed(), showNotices(notices)
 *   app lock      authenticate(title, subtitle, onResult)
 *   statements    decodeStatementText(bytes), looksLikeXlsx(bytes), readXlsx(bytes)
 * Top-level: nowMillis(), ioDispatcher (expect), rethrowUncaught(e) (expect), see Formatting.kt for dates.
 */
interface Platform {
    // ---- capabilities ----
    val supportsAppLock: Boolean
    val supportsBiometricLock: Boolean
    val supportsNotifications: Boolean
    val supportsAutoExport: Boolean

    /** Reading SMS or other apps' notifications to fill the capture inbox. False on iPhone. */
    val supportsCaptureSources: Boolean
    val supportsSmsCapture: Boolean
    val supportsNotificationCapture: Boolean
    val supportsWidget: Boolean

    /** Measurement builds only: seeds big ledgers and logs startup marks. */
    val seedingEnabled: Boolean

    /** How many automatic export files of each kind are kept. */
    val autoExportKeep: Int
    val backupMinPassphrase: Int

    fun trace(name: String)

    // ---- database ----

    /** Opens (or creates) the app database. Throws [app.expenses.core.storage.DatabaseKeyUnavailableException] when it exists but its key is gone. */
    fun openDriver(): SqlDriver

    /** Deletes the database and its key. Only after the user confirmed. */
    fun resetDatabase()

    /** A random id for this install, stable across launches, used to make sync-log timestamps unique per device. */
    fun nodeId(): String

    // ---- files the user picked ----

    /** Throws [IllegalArgumentException] with a message for a person when the file cannot be opened at all; other I/O failures propagate as they are. */
    fun readBytes(uri: String): ByteArray

    /** Replaces the content at [uri]. Throws [IllegalArgumentException] with a message for a person when the location cannot be opened for writing; other I/O failures propagate. */
    fun writeBytes(uri: String, bytes: ByteArray)
    fun displayName(uri: String): String?
    fun mimeType(uri: String): String?

    /** The short name of the folder an automatic export was pointed at, or null if it cannot be derived. */
    fun folderDisplayName(uri: String): String?

    val attachmentFiles: AttachmentFileStore
    val backupCipher: BackupCipher

    /** Runs an automatic export now if due (or [force]d). Never throws; the outcome is recorded in the settings for the screen. No-op where [supportsAutoExport] is false. */
    suspend fun runAutoExport(container: AppContainer, folderUri: String?, nowMillis: Long, force: Boolean)

    // ---- capture and notifications ----

    /** Emits whenever capture changed the inbox, so an open screen can refresh. Never emits where capture is unsupported. */
    val captureChanges: Flow<Unit>
    fun isSmsPermissionGranted(): Boolean

    /** Whether the system will show our notifications at all. */
    fun notificationsAllowed(): Boolean

    /** Shows each notice that can be shown and returns those that were. Empty where notifications are unsupported. */
    fun showNotices(notices: List<Notice>): List<Notice>

    // ---- app lock ----

    /** Shows the system prompt. [onResult] is called once: success, a message, and whether the person backed out. */
    fun authenticate(title: String, subtitle: String?, onResult: (success: Boolean, message: String?, cancelled: Boolean) -> Unit)

    // ---- statement import ----

    /** Statement bytes to text: UTF-8 (with or without BOM), UTF-16 with BOM, else Windows-1252. */
    fun decodeStatementText(bytes: ByteArray): String
    fun looksLikeXlsx(bytes: ByteArray): Boolean

    /** The rows of the first sheet of an .xlsx workbook. Throws [IllegalArgumentException] with a reason for a person when it cannot be read. */
    fun readXlsx(bytes: ByteArray): List<List<String>>
}

/** What copying an attachment in produced. */
class AttachmentCopy(val sizeBytes: Long, val sha256: String)

/**
 * The bytes of attachments, in the app's private storage under a random name; the database says what each is.
 */
interface AttachmentFileStore {
    /** Copies [source] in as [id]. Throws [IllegalArgumentException] with a reason when it is empty or over the limit; nothing is left behind then. */
    fun copyIn(id: String, source: String): AttachmentCopy
    fun delete(id: String)

    /** An address another app may read for a moment (the viewer the user picks), never the file path itself. */
    fun shareUri(id: String): String

    /** Removes files that no record points to. Returns how many. */
    fun sweep(recorded: Set<String>): Int
}

/** Optional passphrase protection for a backup file. The Android implementation is [BackupCrypto]'s AES-256-GCM with PBKDF2; the file layout is fixed so any platform can read it. */
interface BackupCipher {
    val minPassphrase: Int
    fun isEncrypted(bytes: ByteArray): Boolean

    /** Throws [IllegalArgumentException] when the passphrase is shorter than [minPassphrase]. */
    fun encrypt(plain: ByteArray, passphrase: String): ByteArray

    /** The original bytes, or a [app.expenses.core.storage.BackupException] saying the passphrase is wrong or the file is damaged. */
    fun decrypt(bytes: ByteArray, passphrase: String): ByteArray
}

/** Milliseconds since 1970 UTC. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Where database and file work runs. */
internal expect val ioDispatcher: CoroutineDispatcher

/** A failure in background work nobody awaits: reaches the platform's uncaught-exception path, as it would on the thread itself. */
internal expect fun rethrowUncaught(e: Throwable)
