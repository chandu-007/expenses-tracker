package app.expenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.expenses.ui.PermissionRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSInteger
import platform.darwin.NSObject

/**
 * The root view controller of the iPhone app: the shared Compose UI ([ExpensesRoot]) on this phone's [IosPlatform]. The Swift host calls this once
 * from its UIViewControllerRepresentable. The platform, database container, view model and lock live for the whole process (a view controller can be
 * recreated by SwiftUI; the data and the lock must not restart with it).
 *
 * Launch arguments for the UI tests: `-reset-data` wipes the ledger (as "start fresh" does, which also counts the guide as seen); `-dismiss-welcome` hides the welcome card.
 */
fun MainViewController(host: IosHostServices): UIViewController {
    val app = IosApp.get(host)
    return ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
        // The screens do not deal with the keyboard themselves (on Android the window resizes for it); here the whole UI rises above it.
        Box(Modifier.fillMaxSize().imePadding()) { ExpensesRoot(app.viewModel, app.rootHost) }
    }
}

internal class IosApp private constructor(host: IosHostServices) {
    private val core = IosCore.get(host)
    val platform = core.platform
    private val lock = IosAppLock()
    val container = core.container
    val viewModel = LedgerViewModel(container, platform)
    val rootHost: RootHost = IosRootHost(platform, lock)

    companion object {
        private var instance: IosApp? = null
        fun get(host: IosHostServices): IosApp = instance ?: try {
            IosApp(host).also { instance = it }
        } catch (e: Throwable) {
            NSLog("Expenses: start-up failed: %@", e.stackTraceToString())
            throw e
        }
    }
}

private class IosRootHost(override val platform: Platform, override val lock: AppLockPort) : RootHost {
    override val addRequests: StateFlow<Int> = MutableStateFlow(0)
    override val billsRequests: StateFlow<Int> = IosNotifications.billsRequests

    override fun openAttachment(uri: String, mime: String) {
        val url = NSURL.URLWithString(uri) ?: return
        onMain {
            val top = topViewController() ?: return@onMain
            val preview = QLPreviewController()
            val source = PreviewSource(url)
            activePreview = source
            preview.dataSource = source
            top.presentViewController(preview, animated = true, completion = null)
        }
    }

    override fun onDarkChanged(dark: Boolean) {}
    override fun onSecureChanged(secure: Boolean) {}

    /** The first screen with data is up. A cold start with the lock on asks for Face ID now (the app was already active, so no notification will). */
    override fun onReady() = lock.requestUnlock()

    @Composable
    override fun rememberSmsPermission(onResult: (Boolean) -> Unit): SmsPermission = NoSms

    override fun notificationAccessGranted() = false
    override fun openNotificationAccessSettings() {}

    override fun privacyRows(): List<PermissionRow> = listOf(
        PermissionRow("Face ID", IosAuthentication.availability() == app.expenses.ui.LockAvailability.READY, "Only to unlock the app when you turn on the app lock. The phone checks your face or passcode; the app never sees it."),
    )

    override val canUseInternet get() = false
}

private object NoSms : SmsPermission {
    override val granted get() = false
    override fun request() {}
}

private class PreviewItem(private val url: NSURL) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL = url
    override fun previewItemTitle(): String? = url.lastPathComponent
}

/** QuickLook holds its data source weakly, so the one being shown is kept here. */
private class PreviewSource(private val url: NSURL) : NSObject(), QLPreviewControllerDataSourceProtocol {
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): NSInteger = 1
    override fun previewController(controller: QLPreviewController, previewItemAtIndex: NSInteger): QLPreviewItemProtocol = PreviewItem(url)
}

private var activePreview: Any? = null
