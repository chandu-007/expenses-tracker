package app.expenses.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import platform.Foundation.NSURL
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

@Composable
actual fun rememberFeel(): (Feel) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { feel ->
            haptic.performHapticFeedback(
                when (feel) {
                    Feel.SAVE, Feel.PAID -> HapticFeedbackType.Confirm
                    Feel.UNDO -> HapticFeedbackType.Reject
                    Feel.LIMIT -> HapticFeedbackType.LongPress
                    Feel.TICK -> HapticFeedbackType.SegmentTick
                },
            )
        }
    }
}

@Composable
actual fun systemAnimationsOff(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }

actual val supportsBlur: Boolean = true

@Composable
actual fun rememberKeyboardOpen(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

/** Receives the picker's answer once. UIKit holds a delegate weakly, so [PickerHolder] keeps it alive until it has answered. */
private class PickerDelegate(private val onPicked: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) = finish((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.absoluteString)
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = finish(null)
    private fun finish(uri: String?) { PickerHolder.active = null; onPicked(uri) }
}

private object PickerHolder { var active: PickerDelegate? = null }

private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

private fun present(picker: UIDocumentPickerViewController, onPicked: (String?) -> Unit) {
    val top = topViewController()
    if (top == null) { onPicked(null); return }
    val delegate = PickerDelegate(onPicked)
    PickerHolder.active = delegate
    picker.delegate = delegate
    top.presentViewController(picker, animated = true, completion = null)
}

/**
 * Open a file or a folder with the Files app picker. Opened files are copied into the app's temporary folder first, so the URL is readable without
 * a security scope. Saving a new file ([createDocument]) is not wired up on iPhone yet: iOS only exports a file that already exists, so the caller's
 * "pick a place, then write" order does not fit; it reports "nothing picked".
 */
private object IosFilePickers : FilePickers {
    override fun openDocument(mimeTypes: List<String>, onPicked: (String?) -> Unit) {
        val types = mimeTypes.mapNotNull { m -> if (m == "*/*") UTTypeItem else UTType.typeWithMIMEType(m) }.ifEmpty { listOf(UTTypeItem) }
        present(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true), onPicked)
    }

    override fun createDocument(suggestedName: String, mime: String, onPicked: (String?) -> Unit) = onPicked(null)

    override fun openFolder(onPicked: (String?) -> Unit) {
        present(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false), onPicked)
    }
}

@Composable
actual fun rememberFilePickers(): FilePickers = IosFilePickers

/** Notifications are not available on iPhone yet ([app.expenses.Platform.supportsNotifications] is false), so the screen never offers this. */
@Composable
actual fun rememberNotificationPermissionRequest(onDone: () -> Unit): () -> Unit = { onDone() }

/** The app lock is not available on iPhone yet, so the screen never offers this. */
@Composable
actual fun rememberOpenSecuritySettings(): () -> Unit = {}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.compose.ui.backhandler.BackHandler(enabled, onBack)

@Composable
actual fun GuideDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, scrimColor = Color.Transparent)) { content() }
}
