package app.expenses

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.expenses.ui.PermissionRow

/**
 * What each permission in the app's manifest is for, in plain words, for the privacy screen. Every permission the built app declares must be
 * described here: a test fails when one is not, so the screen cannot quietly leave one out.
 */
object PermissionNotes {
    val NOTES: Map<String, Pair<String, String>> = mapOf(
        Manifest.permission.RECEIVE_SMS to ("Read bank texts" to "Lets the app see an incoming text so it can find a bank transaction in it. Texts from people and one-time passwords are ignored without being read, and nothing is sent anywhere. Only used if you turn on capture."),
        Manifest.permission.POST_NOTIFICATIONS to ("Notifications" to "Lets the app remind you of bills, card due dates and budgets."),
        Manifest.permission.USE_BIOMETRIC to ("Fingerprint or face unlock" to "Lets the app lock use your phone's fingerprint or face check. The app never sees your fingerprint."),
        "android.permission.USE_FINGERPRINT" to ("Fingerprint (older phones)" to "The same lock check on older Android versions."),
        Manifest.permission.WAKE_LOCK to ("Stay awake briefly" to "Lets the scheduled export and reminders finish their short work while the screen is off."),
        Manifest.permission.ACCESS_NETWORK_STATE to ("See whether there is a connection" to "Added by a system library for scheduling. It cannot send anything; the app has no internet permission."),
        Manifest.permission.RECEIVE_BOOT_COMPLETED to ("Start after restart" to "Lets reminders and the scheduled export start again after the phone restarts."),
        Manifest.permission.FOREGROUND_SERVICE to ("Run a short task" to "Lets a system library run a short background task, such as an export, to the end."),
    )

    /** The permissions the installed app declares (not its own private one), each with its state now and its description. */
    fun declared(context: Context): List<String> =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().filter { !it.startsWith(context.packageName + ".") }

    fun canUseInternet(context: Context) = declared(context).contains(Manifest.permission.INTERNET)

    /** Notification access is not a manifest permission but a setting the person turns on for an app; shown with the others because it is as sensitive. */
    fun notificationAccessRow(context: Context) = PermissionRow(
        "Notification access", NotificationCaptureService.accessGranted(context),
        "Lets the app see notifications. It reads only Google Pay, PhonePe, Paytm and BHIM, only if you turn on payment app capture, and ignores every other app's without reading it. Nothing is sent anywhere.",
    )

    fun rows(context: Context): List<PermissionRow> = declared(context).map { perm ->
        val (name, why) = NOTES[perm] ?: (perm.substringAfterLast('.') to "No description has been written for this permission.")
        PermissionRow(name, ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED, why)
    } + notificationAccessRow(context)
}
