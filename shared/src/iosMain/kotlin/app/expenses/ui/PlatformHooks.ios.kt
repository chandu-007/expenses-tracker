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
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToURL
import platform.Foundation.NSUUID
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
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

private class CameraDelegate(private val onPicked: (String?) -> Unit) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.let { UIImageJPEGRepresentation(it, 0.85) }
        var uri: String? = null
        if (data != null) {
            val url = NSURL.fileURLWithPath(NSTemporaryDirectory().trimEnd('/') + "/photo-" + NSUUID().UUIDString + ".jpg")
            if (data.writeToURL(url, atomically = true)) uri = url.absoluteString
        }
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(uri)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(null)
    }

    private fun finish(uri: String?) { CameraHolder.active = null; onPicked(uri) }
}

private object CameraHolder { var active: CameraDelegate? = null }

private fun present(picker: UIDocumentPickerViewController, onPicked: (String?) -> Unit) {
    val top = app.expenses.topViewController()
    if (top == null) { onPicked(null); return }
    val delegate = PickerDelegate(onPicked)
    PickerHolder.active = delegate
    picker.delegate = delegate
    top.presentViewController(picker, animated = true, completion = null)
}

/**
 * Open a file or a folder with the Files app picker. Opened files are copied into the app's temporary folder first, so the URL is readable without
 * a security scope. Saving a new file ([createDocument]) answers at once with a staging file; writing to it (Platform.writeBytes) opens the share sheet
 * (Save to Files, AirDrop, Mail...), see [app.expenses.IosExport].
 */
private object IosFilePickers : FilePickers {
    override fun openDocument(mimeTypes: List<String>, onPicked: (String?) -> Unit) {
        val types = mimeTypes.mapNotNull { m -> if (m == "*/*") UTTypeItem else UTType.typeWithMIMEType(m) }.ifEmpty { listOf(UTTypeItem) }
        present(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true), onPicked)
    }

    override fun createDocument(suggestedName: String, mime: String, onPicked: (String?) -> Unit) = onPicked(app.expenses.IosExport.stagingFile(suggestedName))

    override val canTakePhoto: Boolean get() = UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    /** The system camera (iOS asks for permission the first time, with the reason in Info.plist). The picture is saved as a JPEG in the temporary folder and its address handed back. */
    override fun takePhoto(onPicked: (String?) -> Unit) {
        val top = app.expenses.topViewController()
        if (top == null || !canTakePhoto) { onPicked(null); return }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        val delegate = CameraDelegate(onPicked)
        CameraHolder.active = delegate
        picker.delegate = delegate
        top.presentViewController(picker, animated = true, completion = null)
    }

    override fun openFolder(onPicked: (String?) -> Unit) {
        present(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false), onPicked)
    }
}

@Composable
actual fun rememberFilePickers(): FilePickers = IosFilePickers

/** The system's permission dialog (once); after a refusal this app's notification page in Settings, the only place the choice can change. */
@Composable
actual fun rememberNotificationPermissionRequest(onDone: () -> Unit): () -> Unit {
    val done = androidx.compose.runtime.rememberUpdatedState(onDone)
    return { app.expenses.IosNotifications.requestPermission { done.value() } }
}

/** iOS has no page for the passcode that an app may open; the closest is this app's own page in Settings. */
@Composable
actual fun rememberOpenSecuritySettings(): () -> Unit = {
    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.compose.ui.backhandler.BackHandler(enabled, onBack)

@Composable
actual fun GuideDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, usePlatformInsets = false, scrimColor = Color.Transparent)) { content() }
}
