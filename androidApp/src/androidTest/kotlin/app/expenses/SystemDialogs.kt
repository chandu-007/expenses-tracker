package app.expenses

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The Google keyboard of the Android 8 emulator image crashes ("Resources$NotFoundException: Resource ID #0x7f040106", inside its own process, while it loads its keys) the first time it is brought up in
 * an unfolded-tablet size, which is what the tests that set the display (`wm size`) do. Its "Application Error" dialog then takes the window focus for five minutes: the app's window has no focus, so the
 * soft keyboard cannot show for it (`WidePaneUiTest`'s keyboard test), `KeyboardShortcutsUiTest` never sees its key presses reach the app, and `AccessibilityUiTest` reports the dialog's own button.
 * The tests that change the display call [dismissAppErrorDialog] when they finish, and the keyboard test calls it between its tries. It does nothing when the app's window has the focus, and never on a
 * phone where the keyboard does not crash.
 */
object SystemDialogs {
    private fun shell(cmd: String): String {
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        return ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }.decodeToString()
    }

    /** True when the window that has the focus is an "Application Error" dialog (of any app). */
    fun appErrorDialogIsUp(): Boolean = shell("dumpsys window").lineSequence().any { it.contains("mCurrentFocus=") && it.contains("Application Error") }

    /** Closes the "Application Error" dialog with Back (it cancels like "Close app"); returns whether there was one. */
    fun dismissAppErrorDialog(): Boolean {
        if (!appErrorDialogIsUp()) return false
        shell("input keyevent KEYCODE_BACK")
        Thread.sleep(700)
        return true
    }
}
