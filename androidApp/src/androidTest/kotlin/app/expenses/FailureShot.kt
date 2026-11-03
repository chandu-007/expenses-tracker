package app.expenses

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Screenshots for failures that cannot be reproduced on demand. Two ways, because a JUnit rule hears of a failure only after the test's @After has run (the activity is gone by then, and the
 * picture shows the launcher):
 *  - [around] wraps one wait: when it times out, the screen as it is NOW is saved, then the failure goes on;
 *  - the rule itself saves one more picture when the test has failed, for failures that are not waits.
 * Pictures are /sdcard/Download/failshot_<name>.png; pull them with `adb pull /sdcard/Download`.
 */
class FailureShot : TestWatcher() {
    override fun failed(e: Throwable?, description: Description) {
        capture("${description.className.substringAfterLast('.')}_${description.methodName}_after")
    }

    companion object {
        fun capture(name: String) {
            val file = "failshot_$name.png".replace(Regex("[^A-Za-z0-9_.]"), "_")
            runCatching {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$file").close()
                Thread.sleep(1500)
            }
        }

        /** Runs [wait]; when it fails, saves the screen under [name] first. */
        fun <T> around(name: String, wait: () -> T): T = try { wait() } catch (t: Throwable) { capture(name); throw t }
    }
}
