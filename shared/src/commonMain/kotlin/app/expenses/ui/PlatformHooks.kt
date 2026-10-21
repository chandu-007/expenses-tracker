package app.expenses.ui

import androidx.compose.runtime.Composable

/*
 * Small per-platform hooks the screens use. Actuals: shared/src/androidMain and shared/src/iosMain, package app.expenses.ui.
 * A URI is a String everywhere in shared code (Android: a content Uri's toString(); iOS: a file URL's absoluteString).
 */

/** A function that performs a [Feel]: a crisp confirm on saving, a soft reject on Undo, a firmer one when a budget limit is crossed, a double tap for paid. */
@Composable
expect fun rememberFeel(): (Feel) -> Unit

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

/** The dialog the guided tour draws in: full screen, with no dimming of its own (the tour paints its own scrim). */
@Composable
expect fun GuideDialog(onDismiss: () -> Unit, content: @Composable () -> Unit)
