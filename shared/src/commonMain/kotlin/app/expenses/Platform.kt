package app.expenses

import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.domain.Notice
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.ByteArraySink
import app.expenses.core.storage.ByteArraySource
import app.expenses.core.storage.ByteSink
import app.expenses.core.storage.ByteSource
import app.expenses.core.storage.FileVault
import app.expenses.core.storage.SealPrimitives
import app.expenses.core.storage.SecretStore
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
 *   files         readBytes(uri), writeBytes(uri, bytes), openRead(uri), openWrite(uri) (the same as streams), displayName(uri), mimeType(uri), folderDisplayName(uri)
 *   attachments   attachmentFiles: AttachmentFileStore
 *   backup        backupCipher: BackupCipher
 *   auto-export   runAutoExport(container, folderUri, nowMillis, force), secretStore (the key of passphrase-protected automatic copies)
 *   capture       captureChanges: Flow<Unit>, isSmsPermissionGranted(), supportsShortcutsCapture, readClipboardText(), openExternalUrl(url)
 *   about         appVersionText, osVersionText, deviceModelText (for the feedback email; empty by default)
 *   signing       signingExpiresAtMillis (iPhone test builds: when the free-signed install stops opening; null elsewhere)
 *   notification  notificationsAllowed(), showNotices(notices), notificationTimingNote
 *   app lock      authenticate(title, subtitle, onResult)
 *   statements    decodeStatementText(bytes), looksLikeXlsx(bytes), readXlsx(bytes)
 *                 looksLikePdf / looksLikeImage (shared), readPdfText(bytes, password), recognizeText(bytes) (unsupported unless a platform overrides)
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

    /** The most data (entries and settings, not photos) a backup file may hold on this platform: it is read and built in memory, several times over. */
    val maxBackupPayloadBytes: Int get() = BackupFile.MAX_PAYLOAD_BYTES

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

    /**
     * The file as a stream, for what can be large (a backup that carries photos). Throws [IllegalArgumentException] with a message for a person when it cannot
     * be opened. Closing the source releases the file. The default reads it all at once; a platform overrides it to read in pieces.
     */
    fun openRead(uri: String): ByteSource = ByteArraySource(readBytes(uri))

    /**
     * Replaces the content at [uri] with what is written to the sink; closing it completes the file (on iPhone it then offers the file to the share sheet,
     * as [writeBytes] does). Throws [IllegalArgumentException] with a message for a person when the location cannot be opened for writing. The default holds
     * it all and writes at the end; a platform overrides it to write in pieces.
     */
    fun openWrite(uri: String): ByteSink = object : ByteSink {
        private val out = ByteArraySink()
        override fun write(buf: ByteArray, off: Int, len: Int) = out.write(buf, off, len)
        override fun close() = writeBytes(uri, out.toByteArray())
    }

    /**
     * Best-effort removal of a file this app began writing at [uri] and could not finish (a backup that failed half way), so a half file is not left looking like
     * a backup. Never throws; does nothing where the platform cannot.
     */
    fun discard(uri: String) {}

    fun displayName(uri: String): String?
    fun mimeType(uri: String): String?

    /** The short name of the folder an automatic export was pointed at, or null if it cannot be derived. */
    fun folderDisplayName(uri: String): String?

    val attachmentFiles: AttachmentFileStore
    val backupCipher: BackupCipher

    /** Runs an automatic export now if due (or [force]d). Never throws; the outcome is recorded in the settings for the screen. No-op where [supportsAutoExport] is false. */
    suspend fun runAutoExport(container: AppContainer, folderUri: String?, nowMillis: Long, force: Boolean)

    /**
     * Where the key of passphrase-protected automatic copies is kept: the Android Keystore, the iPhone Keychain (readable after the first unlock, this device only).
     * Null where a platform has none; protection cannot then be turned on, and one that is on writes nothing.
     */
    val secretStore: SecretStore? get() = null

    /** Deletes the Excel files an unprotected run left in the automatic-copy folder, after the person pressed "Delete them" ([AutoExportRunner.deleteOldExcel]). Never throws; the outcome is recorded for the screen. */
    suspend fun deleteOldAutoExcel(container: AppContainer, folderUri: String?) {}

    // ---- capture and notifications ----

    /** Emits whenever capture changed the inbox, so an open screen can refresh. Never emits where capture is unsupported. */
    val captureChanges: Flow<Unit>
    fun isSmsPermissionGranted(): Boolean

    /**
     * True where the OS gives no access to texts but the person can feed bank messages in by hand or by an automation they set up (iPhone: a Shortcuts
     * automation running the "Capture bank message" action, or pasting a text). The capture screens then show that setup and the paste box instead of the
     * Android switches. False by default; Android does not override it, so none of its screens change.
     */
    val supportsShortcutsCapture: Boolean get() = false

    /** The text on the clipboard, or null when there is none (or this platform does not offer it). Only called when the person pressed a paste button. */
    fun readClipboardText(): String? = null

    /** Puts [text] on the clipboard. False where that is not offered. */
    fun copyText(text: String): Boolean = false

    /** Opens [url] in the app that handles it (iPhone: `shortcuts://`; both: a `mailto:` address opens the mail app with the message filled in). False when nothing could be opened. */
    fun openExternalUrl(url: String): Boolean = false

    /** The app's own version, for the feedback email ("0.6.0"). Empty where a platform does not say. */
    val appVersionText: String get() = ""

    /** The system and its version, for the feedback email ("Android 15", "iOS 19.1"). */
    val osVersionText: String get() = ""

    /** The kind of device, for the feedback email ("Google Pixel 8", "iPhone"). */
    val deviceModelText: String get() = ""

    /** When a build signed with a free Apple ID stops opening, in epoch milliseconds; null when it does not expire or cannot be told (Android, simulator, App Store). */
    val signingExpiresAtMillis: Long? get() = null

    /** Whether the system will show our notifications at all. */
    fun notificationsAllowed(): Boolean

    /** Shows each notice that can be shown and returns those that were. Empty where notifications are unsupported. */
    fun showNotices(notices: List<Notice>): List<Notice>

    /** When not null, what the Notifications screen says about when a check runs, instead of Android's "about once an hour" text. Null on Android. */
    val notificationTimingNote: String? get() = null

    // ---- app lock ----

    /** Shows the system prompt. [onResult] is called once: success, a message, and whether the person backed out. */
    fun authenticate(title: String, subtitle: String?, onResult: (success: Boolean, message: String?, cancelled: Boolean) -> Unit)

    // ---- statement import ----

    /** Statement bytes to text: UTF-8 (with or without BOM), UTF-16 with BOM, else Windows-1252. */
    fun decodeStatementText(bytes: ByteArray): String
    fun looksLikeXlsx(bytes: ByteArray): Boolean

    /** The rows of the first sheet of an .xlsx workbook. Throws [IllegalArgumentException] with a reason for a person when it cannot be read. */
    fun readXlsx(bytes: ByteArray): List<List<String>>

    /** Whether [bytes] start like a PDF (the "%PDF-" mark, which may follow a little junk). */
    fun looksLikePdf(bytes: ByteArray): Boolean = hasPdfMark(bytes)

    /** Whether [bytes] are a JPEG, PNG or WebP picture. */
    fun looksLikeImage(bytes: ByteArray): Boolean = app.expenses.core.domain.ImageSniff.looksLikeImage(bytes)

    /**
     * The text of a PDF, read on the device. [password] is what the person typed for a protected file (null the first time). Where this platform cannot read
     * PDFs, [PdfText.Unreadable] says so. Never throws for a bad file, never keeps or logs the password.
     */
    fun readPdfText(bytes: ByteArray, password: String?): PdfText = PdfText.Unreadable("This phone cannot read PDF statements yet. Download the statement as CSV or Excel from your bank instead.")

    /** The text recognised in a picture of a statement, line by line, on the device. Throws [IllegalArgumentException] with a reason for a person when it cannot. */
    fun recognizeText(bytes: ByteArray): String = throw IllegalArgumentException("This phone cannot read statements from pictures yet. Download the statement as CSV or Excel from your bank instead.")
}

/** What reading a PDF gave. */
sealed interface PdfText {
    /** The text, lines in reading order. [recognised] is true when the pages were pictures (a scanned statement) and the text was recognised, so it may hold mistakes. */
    data class Text(val text: String, val recognised: Boolean) : PdfText

    /** The file is protected and no password was given. */
    data object PasswordNeeded : PdfText

    /** A password was given and it did not open the file. */
    data object WrongPassword : PdfText

    /** [reason] is for a person to read. */
    data class Unreadable(val reason: String) : PdfText
}

private fun hasPdfMark(bytes: ByteArray): Boolean {
    val mark = "%PDF-"
    val limit = minOf(bytes.size, 1024) - mark.length
    for (start in 0..limit) if (mark.indices.all { bytes[start + it] == mark[it].code.toByte() }) return true
    return false
}

/** What copying an attachment in produced. */
class AttachmentCopy(val sizeBytes: Long, val sha256: String)

/**
 * The bytes of attachments, in the app's private storage under a random name; the database says what each is. As a [FileVault] it is what a backup reads
 * the files from and a restore puts them back into ([FileVault.create] replaces a file of that id; the file is whole once its sink is closed).
 */
interface AttachmentFileStore : FileVault {
    /** Copies [source] in as [id]. Throws [IllegalArgumentException] with a reason when it is empty or over the limit; nothing is left behind then. */
    fun copyIn(id: String, source: String): AttachmentCopy
    override fun delete(id: String)

    /** An address another app may read for a moment (the viewer the user picks), never the file path itself. */
    fun shareUri(id: String): String

    /** Removes files that no record points to. Returns how many. */
    fun sweep(recorded: Set<String>): Int
}

/** Optional passphrase protection for a backup file. The Android implementation is [BackupCrypto]'s AES-256-GCM with PBKDF2; the file layout is fixed so any platform can read it. */
interface BackupCipher {
    val minPassphrase: Int

    /** The AES-GCM, PBKDF2 and random primitives the streaming layout for backups with files ([BackupSeal]) is written on. */
    val primitives: SealPrimitives

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
