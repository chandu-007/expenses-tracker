package app.expenses.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun rememberFeel(): (Feel) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { feel ->
            val constant = when (feel) {
                Feel.SAVE, Feel.PAID -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                Feel.UNDO -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.KEYBOARD_TAP
                Feel.LIMIT -> HapticFeedbackConstants.LONG_PRESS
                Feel.TICK -> HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(constant)
        }
    }
}

@Composable
actual fun systemAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

actual val supportsBlur: Boolean = Build.VERSION.SDK_INT >= 31

/**
 * The window is resized for the keyboard rather than padded, so the insets API reports nothing on every phone and emulator this app supports;
 * the visible part of the window shrinking by more than a sixth of its height is the reliable sign.
 */
@Composable
actual fun rememberKeyboardOpen(): Boolean {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val rect = android.graphics.Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            view.getWindowVisibleDisplayFrame(rect)
            val full = view.rootView.height
            open = full > 0 && (full - rect.bottom) > full / 6
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return open
}

/** [ActivityResultContracts.CreateDocument] with the type chosen per call instead of per contract. */
private class CreateTypedDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

@Composable
actual fun rememberFilePickers(): FilePickers {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending?.invoke(uri?.toString()); pending = null }
    val create = rememberLauncherForActivityResult(CreateTypedDocument()) { uri -> pending?.invoke(uri?.toString()); pending = null }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            // Keep the permission: the scheduled export runs later, when no screen is open.
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        pending?.invoke(uri?.toString()); pending = null
    }
    // the photo is written to a file of ours in the cache by the camera app (through our own file provider), so no camera permission is needed
    var photoFile by remember { mutableStateOf<java.io.File?>(null) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = photoFile; photoFile = null
        val uri = if (ok && file != null && file.length() > 0) androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file) else null
        pending?.invoke(uri?.toString()); pending = null
    }
    val cameraAvailable = remember(context) { Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null }
    return remember(open, create, folder, photo, cameraAvailable) {
        object : FilePickers {
            override val canTakePhoto: Boolean get() = cameraAvailable
            override fun takePhoto(onPicked: (String?) -> Unit) {
                val dir = java.io.File(context.cacheDir, "camera").also { it.mkdirs() }
                dir.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }.forEach { it.delete() } // yesterday's leftovers
                val file = java.io.File.createTempFile("bill", ".jpg", dir)
                photoFile = file; pending = onPicked
                try { photo.launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)) } catch (e: Exception) { photoFile = null; pending = null; file.delete(); onPicked(null) }
            }
            override fun openDocument(mimeTypes: List<String>, onPicked: (String?) -> Unit) { pending = onPicked; open.launch(mimeTypes.toTypedArray()) }
            override fun createDocument(suggestedName: String, mime: String, onPicked: (String?) -> Unit) { pending = onPicked; create.launch(suggestedName to mime) }
            override fun openFolder(onPicked: (String?) -> Unit) { pending = onPicked; folder.launch(null) }
        }
    }
}

@Composable
actual fun rememberNotificationPermissionRequest(onDone: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { done() }
    return {
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}

@Composable
actual fun rememberOpenSecuritySettings(): () -> Unit {
    val context = LocalContext.current
    return { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

@Composable
actual fun GuideDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        // the dialog window dims what is behind it by itself; the tour does its own dimming, with a bright window around the control it points at
        val view = LocalView.current
        ((view as? DialogWindowProvider) ?: (view.parent as? DialogWindowProvider))?.window?.let { w ->
            SideEffect { w.setDimAmount(0f); w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        }
        content()
    }
}
