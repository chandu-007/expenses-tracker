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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The Android haptic constant for [feel] on a phone with API level [sdk]: what each pattern is documented to mean, so a person learns what happened without looking (UI S7). A save and a paid bill are a
 * confirm; Undo is a soft, neutral end-of-gesture tap (it reverses something, it is not a failure); a budget limit crossed is the one warning (reject, the firmest); a press on a tab or tile is a very light
 * segment tick and a step along a chart a frequent one; a swipe crossing its commit point has its own pair on Android 14; a press and hold is the platform's long press; a key of the number pad is a virtual key.
 * Before the API level a constant exists at, the nearest older one is used. Only `View.performHapticFeedback` constants: no vibrator, so no permission.
 */
fun hapticConstant(feel: Feel, sdk: Int): Int = when (feel) {
    Feel.PRESS -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    Feel.SAVE, Feel.PAID -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    Feel.UNDO -> if (sdk >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.KEYBOARD_TAP
    Feel.WARN -> if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    Feel.THRESHOLD -> if (sdk >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
    Feel.THRESHOLD_OFF -> if (sdk >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE else HapticFeedbackConstants.CLOCK_TICK
    Feel.STEP -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    Feel.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
    Feel.KEY -> HapticFeedbackConstants.VIRTUAL_KEY
}

@Composable
actual fun rememberPlatformFeel(): (Feel) -> Unit {
    val view = LocalView.current
    return remember(view) { { feel -> view.performHapticFeedback(hapticConstant(feel, Build.VERSION.SDK_INT)) } }
}

@Composable
actual fun systemAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

actual val supportsBlur: Boolean = Build.VERSION.SDK_INT >= 31

@Composable
actual fun rememberLowRamDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice }.getOrDefault(false)
    }
}

private fun isEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("sdk_gphone") ||
        Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk")

private fun Context.activityOrNull(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}

@Composable
actual fun SlowFrameWatch(onSlow: () -> Unit) {
    val view = LocalView.current
    val latest by rememberUpdatedState(onSlow)
    DisposableEffect(view) {
        val window = view.context.activityOrNull()?.window
        if (window == null || isEmulator()) return@DisposableEffect onDispose { }
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val health = app.expenses.core.domain.FrameHealth()
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (health.add(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000.0)) main.post { latest() }
        }
        runCatching { window.addOnFrameMetricsAvailableListener(listener, main) }
        onDispose { runCatching { window.removeOnFrameMetricsAvailableListener(listener) } }
    }
}

@Composable
actual fun systemReduceTransparency(): Boolean {
    val context = LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(context, config) {
        runCatching {
            val level = if (Build.VERSION.SDK_INT >= 34) (context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager).contrast else 0f
            level > 0f || Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
    }
}

/**
 * Two signs, either one is enough: the visible part of the window shrinking by more than a sixth of its height (the window is resized for the keyboard, which is what the API 26
 * emulator did and the insets API reported nothing for), or the keyboard's own inset being visible (the API 34 emulator: a screenshot showed the tab bar floating above the keyboard
 * over nothing, the first sign never having fired there).
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
            open = (full > 0 && (full - rect.bottom) > full / 6) ||
                androidx.core.view.ViewCompat.getRootWindowInsets(view)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout() // a keyboard that is already up when the screen appears (the layout may not change again) counts at once
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
    // saved with the screen: if Android destroys it while the camera app is in front, the target file is still known when the photo comes back
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var recovered by rememberSaveable { mutableStateOf<String?>(null) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = photoPath?.let { java.io.File(it) }; photoPath = null
        val uri = if (ok && file != null && file.length() > 0) androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString() else null // some camera apps report success and leave an empty file
        val callback = pending; pending = null
        if (callback != null) callback(uri) else if (uri != null) recovered = uri
    }
    val cameraAvailable = remember(context) { Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null }
    return remember(open, create, folder, photo, cameraAvailable, recovered) {
        object : FilePickers {
            override val canTakePhoto: Boolean get() = cameraAvailable
            override val recoveredPhoto: String? get() = recovered
            override fun consumeRecoveredPhoto() { recovered = null }
            override fun takePhoto(onPicked: (String?) -> Unit) {
                val dir = java.io.File(context.cacheDir, "camera").also { it.mkdirs() }
                dir.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }.forEach { it.delete() } // yesterday's leftovers
                val file = java.io.File.createTempFile("bill", ".jpg", dir)
                photoPath = file.absolutePath; pending = onPicked
                try { photo.launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)) } catch (e: Exception) { photoPath = null; pending = null; file.delete(); onPicked(null) }
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
actual fun PlatformPredictiveBackHandler(enabled: Boolean, onProgress: (progress: Float, fromLeft: Boolean) -> Unit, onCancel: () -> Unit, onBack: () -> Unit) {
    val progress by rememberUpdatedState(onProgress)
    val cancelled by rememberUpdatedState(onCancel)
    val back by rememberUpdatedState(onBack)
    androidx.activity.compose.PredictiveBackHandler(enabled) { events: kotlinx.coroutines.flow.Flow<androidx.activity.BackEventCompat> ->
        try {
            events.collect { progress(it.progress, it.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT) }
            back()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            cancelled()
            throw e
        }
    }
}

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
