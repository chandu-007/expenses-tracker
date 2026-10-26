@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.expenses

import app.expenses.ui.LockAvailability
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_time
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue

/** The window the person is looking at (foreground scene's key window). */
internal fun keyWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val scene = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: scenes.firstOrNull()
    return scene?.windows?.filterIsInstance<UIWindow>()?.let { w -> w.firstOrNull { it.isKeyWindow() } ?: w.firstOrNull() }
}

/** The view controller on top of everything, where a picker or sheet is presented from. */
internal fun topViewController(): UIViewController? {
    var vc = keyWindow()?.rootViewController
    while (true) {
        // a controller that is on its way out is not a place to present from: something presented on it goes down with it, and later presents then fail
        val next = vc?.presentedViewController?.takeUnless { it.isBeingDismissed() } ?: break
        vc = next
    }
    return vc
}

internal fun onMain(block: () -> Unit) { dispatch_async(dispatch_get_main_queue(), block) }

/** Runs [block] on the main thread after [seconds]. */
internal fun afterMain(seconds: Double, block: () -> Unit) {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (seconds * 1_000_000_000).toLong()), dispatch_get_main_queue(), block)
}

/**
 * Runs [block] once [controller] is no longer on screen (it has no presenting controller), checking every tenth of a second for at most [maxSeconds], then runs it
 * anyway. What the block starts (a new screen over the app) must not be presented while the controller that asked for it is still leaving, on a slow phone or
 * with Reduce Motion alike.
 */
internal fun whenGone(controller: platform.UIKit.UIViewController, maxSeconds: Double = 3.0, block: () -> Unit) {
    var waited = 0.0
    fun check() {
        if ((controller.presentingViewController == null && !controller.isBeingDismissed()) || waited >= maxSeconds) { block(); return }
        waited += 0.1
        afterMain(0.1) { check() }
    }
    afterMain(0.05) { check() }
}

/**
 * Saving a file on iPhone: iOS shares or exports a file that already exists, so the "pick a place, then write" order of the shared screens becomes
 * "name a staging file, write it, then offer it" ([stagingFile] then [offer], called by [IosPlatform.writeBytes]). The share sheet has Save to Files,
 * AirDrop, Mail and so on. The staging copy is removed when the sheet closes.
 */
internal object IosExport {
    private val root get() = NSTemporaryDirectory().trimEnd('/') + "/export"

    /** A new, empty location called [name] (its folder is unique, so the name stays as the person will see it). */
    fun stagingFile(name: String): String {
        val dir = "$root/${NSUUID().UUIDString}"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return NSURL.fileURLWithPath("$dir/${name.substringAfterLast('/')}").absoluteString.orEmpty()
    }

    /** Shows the share sheet for [url] when it is a staging file; does nothing for any other location. */
    fun offer(url: NSURL) {
        val path = url.path ?: return
        if (!path.contains("/export/")) return
        onMain {
            val top = topViewController() ?: return@onMain
            val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            sheet.completionWithItemsHandler = { _, _, _, _ ->
                NSFileManager.defaultManager.removeItemAtPath(path.substringBeforeLast('/'), error = null)
            }
            top.presentViewController(sheet, animated = true, completion = null)
        }
    }
}

/** Face ID, Touch ID or the device passcode through LocalAuthentication. */
@OptIn(ExperimentalForeignApi::class)
internal object IosAuthentication {
    fun availability(): LockAvailability = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error.ptr)) LockAvailability.READY
        else if (error.value?.code == LAErrorPasscodeNotSet) LockAvailability.NO_SCREEN_LOCK
        else LockAvailability.UNAVAILABLE
    }

    fun authenticate(reason: String, onResult: (success: Boolean, message: String?, cancelled: Boolean) -> Unit) {
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, error ->
            val code = error?.code
            val cancelled = code == LAErrorUserCancel || code == LAErrorSystemCancel || code == LAErrorAppCancel
            val message = if (success) null else error?.localizedDescription ?: "That did not work."
            onMain { onResult(success, message, cancelled) }
        }
    }
}
