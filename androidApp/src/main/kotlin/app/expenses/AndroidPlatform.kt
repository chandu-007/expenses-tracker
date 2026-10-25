package app.expenses

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.domain.Notice
import app.expenses.core.storage.EncryptedDriver
import java.io.File
import java.lang.ref.WeakReference
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the shared code needs from Android, backed by the classes that were already here. */
@OptIn(ExperimentalUuidApi::class)
class AndroidPlatform(context: Context) : Platform {
    private val context: Context = context.applicationContext ?: context

    /** The activity that shows the system authentication prompt. Set by [MainActivity] while it exists. */
    var activity: FragmentActivity?
        get() = activityRef?.get()
        set(value) { activityRef = value?.let(::WeakReference) }
    private var activityRef: WeakReference<FragmentActivity>? = null

    private val authenticator: Authenticator by lazy { BiometricAuthenticator() }
    private val pdfReader by lazy { PdfStatementReader(this.context) }

    override val supportsAppLock get() = true
    override val supportsBiometricLock get() = true
    override val supportsNotifications get() = true
    override val supportsAutoExport get() = true
    override val supportsCaptureSources get() = true
    override val supportsSmsCapture get() = BuildConfig.SMS_CAPTURE
    override val supportsNotificationCapture get() = true
    override val supportsWidget get() = true
    override val seedingEnabled get() = BuildConfig.SEEDING_ENABLED
    override val autoExportKeep get() = AutoExport.KEEP
    override val backupMinPassphrase get() = BackupCrypto.MIN_PASSPHRASE

    override fun trace(name: String) = StartupTrace.mark(name)

    override fun openDriver(): SqlDriver = EncryptedDriver.create(context)
    override fun resetDatabase() = EncryptedDriver.reset(context)

    override fun nodeId(): String {
        val f = File(context.noBackupFilesDir, "node_id")
        return if (f.exists()) f.readText() else Uuid.random().toString().take(8).also { f.writeText(it) }
    }

    override fun readBytes(uri: String): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } ?: throw IllegalArgumentException("That file could not be opened.")

    override fun writeBytes(uri: String, bytes: ByteArray) {
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(bytes); it.flush() } ?: throw IllegalArgumentException("That location could not be written to.")
    }

    override fun displayName(uri: String): String? =
        context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    override fun mimeType(uri: String): String? = context.contentResolver.getType(Uri.parse(uri))

    override fun folderDisplayName(uri: String): String? = Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.ifEmpty { null }

    override val attachmentFiles: AttachmentFileStore = object : AttachmentFileStore {
        private val files = AttachmentFiles(this@AndroidPlatform.context)
        override fun copyIn(id: String, source: String): AttachmentCopy = files.copyIn(id, Uri.parse(source)).let { AttachmentCopy(it.sizeBytes, it.sha256) }
        override fun delete(id: String) = files.delete(id)
        override fun shareUri(id: String): String = files.shareUri(id).toString()
        override fun sweep(recorded: Set<String>): Int = files.sweep(recorded)
    }

    override val backupCipher: BackupCipher = object : BackupCipher {
        override val minPassphrase get() = BackupCrypto.MIN_PASSPHRASE
        override fun isEncrypted(bytes: ByteArray) = BackupCrypto.isEncrypted(bytes)
        override fun encrypt(plain: ByteArray, passphrase: String) = BackupCrypto.encrypt(plain, passphrase)
        override fun decrypt(bytes: ByteArray, passphrase: String) = BackupCrypto.decrypt(bytes, passphrase)
    }

    override suspend fun runAutoExport(container: AppContainer, folderUri: String?, nowMillis: Long, force: Boolean) {
        AutoExport.run(container, AutoExport.folderOf(context, folderUri), nowMillis, force)
    }

    override val captureChanges: Flow<Unit> = Capture.changes.map { }

    override fun isSmsPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** A `mailto:` address opens the mail app with the message filled in (nothing is sent until the person sends it); anything else is viewed by whichever app handles it. */
    override fun openExternalUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val intent = Intent(if (uri.scheme == "mailto") Intent.ACTION_SENDTO else Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
    }

    override val appVersionText: String get() = BuildConfig.VERSION_NAME
    override val osVersionText: String get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    override val deviceModelText: String get() = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun notificationsAllowed(): Boolean = Notifier.canShow(context)
    override fun showNotices(notices: List<Notice>): List<Notice> = Notifier.show(context, notices)

    override fun authenticate(title: String, subtitle: String?, onResult: (success: Boolean, message: String?, cancelled: Boolean) -> Unit) {
        val host = activity
        if (host == null) { onResult(false, "The screen is not ready for the lock prompt.", false); return }
        authenticator.authenticate(host, title, subtitle) { r ->
            when (r) {
                Authenticator.Result.Success -> onResult(true, null, false)
                is Authenticator.Result.Failed -> onResult(false, r.message, r.cancelled)
            }
        }
    }

    override fun decodeStatementText(bytes: ByteArray): String = StatementText.decode(bytes)
    override fun looksLikeXlsx(bytes: ByteArray): Boolean = XlsxReader.looksLikeZip(bytes)
    override fun readXlsx(bytes: ByteArray): List<List<String>> = XlsxReader.read(bytes)
    override fun readPdfText(bytes: ByteArray, password: String?): PdfText = pdfReader.read(bytes, password)
    override fun recognizeText(bytes: ByteArray): String = OcrStatementReader().use { it.recognize(bytes) }
}
