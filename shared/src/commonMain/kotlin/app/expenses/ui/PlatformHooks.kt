package app.expenses.ui

import androidx.compose.runtime.Composable

/*
 * Small per-platform hooks the screens use. Actuals: shared/src/androidMain and shared/src/iosMain, package app.expenses.ui.
 * A URI is a String everywhere in shared code (Android: a content Uri's toString(); iOS: a file URL's absoluteString).
 */

/** The platform's pattern for each [Feel] (Android: `View.performHapticFeedback`, no permission; iPhone: UIKit's feedback generators). Screens use [rememberFeel], which adds the Haptics switch and the scrub throttle. */
@Composable
expect fun rememberPlatformFeel(): (Feel) -> Unit

/** Whether the phone itself has animations turned off (Android: animator scale 0; iPhone: Reduce Motion). */
@Composable
expect fun systemAnimationsOff(): Boolean

/** True where a blurred light behind the hero figures is cheap (Android 12 and later; always on iPhone). */
expect val supportsBlur: Boolean

/** Whether the on-screen keyboard is showing. */
@Composable
expect fun rememberKeyboardOpen(): Boolean

/** The platform's file dialogs. Each [onPicked] gets the chosen URI, or null when the person backed out. */
interface FilePickers {
    /** Pick one existing file. [mimeTypes] may hold the wildcard type for anything. */
    fun openDocument(mimeTypes: List<String>, onPicked: (String?) -> Unit)

    /** Choose where a new file called [suggestedName] of type [mime] is saved; the caller then writes to the returned URI. */
    fun createDocument(suggestedName: String, mime: String, onPicked: (String?) -> Unit)

    /** Choose a folder the app may keep writing into later. */
    fun openFolder(onPicked: (String?) -> Unit)

    /** True where the phone can take a photo for the app (a camera app is installed). Always false by default. */
    val canTakePhoto: Boolean get() = false

    /** Takes a photo with the phone's camera app and returns its address, or null when cancelled or unavailable. Needs no camera permission of its own. */
    fun takePhoto(onPicked: (String?) -> Unit) { onPicked(null) }

    /** A photo that was taken but whose callback was lost because Android recreated the screen while the camera was open; handed over once, then cleared. */
    val recoveredPhoto: String? get() = null
    fun consumeRecoveredPhoto() {}
}

@Composable
expect fun rememberFilePickers(): FilePickers

/** Returns a function that asks for permission to show notifications (or opens the app's notification settings where a prompt is not possible); [onDone] runs after. */
@Composable
expect fun rememberNotificationPermissionRequest(onDone: () -> Unit): () -> Unit

/** Returns a function that opens the phone's screen-lock / security settings page. */
@Composable
expect fun rememberOpenSecuritySettings(): () -> Unit

/** The system Back gesture (Android Back button; iPhone edge swipe where the host forwards it). Does nothing while [enabled] is false. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * The system Back gesture with its progress (UI S7, U-20): like [PlatformBackHandler], but while a finger-following back gesture runs [onProgress] gets how far it has gone (0 to 1) and whether it began at the left edge,
 * [onCancel] runs if the person lets go without going back, and [onBack] when the back is committed. A Back that is all at once (the Back button, the three-button bar) is just [onBack]. Uses only what Compose and
 * the platform already have (Android: the activity library's predictive back handler; iPhone: Compose's back handler): no new library.
 */
@Composable
expect fun PlatformPredictiveBackHandler(enabled: Boolean, onProgress: (progress: Float, fromLeft: Boolean) -> Unit, onCancel: () -> Unit, onBack: () -> Unit)

/** The dialog the guided tour draws in: full screen, with no dimming of its own (the tour paints its own scrim). */
@Composable
expect fun GuideDialog(onDismiss: () -> Unit, content: @Composable () -> Unit)
