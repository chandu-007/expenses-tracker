package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.EncryptedDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Runs first (the name sorts first) so that the one slow launch that follows installing a new build is spent here, with its own limit and its
 * time reported, instead of randomly failing whichever test happens to launch the app first. On an emulator, the first launch right after a new
 * build is installed has taken more than 10 s (the wait every other test uses) about one run in six, against about 100 ms for every launch after
 * it; the cause is not known. Every other test keeps its 10 s wait.
 */
@RunWith(AndroidJUnit4::class)
class AaaFirstLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    @Test
    fun theFirstLaunchAfterANewBuildShowsTheHomeScreenWithinAMinuteAndItsTimeIsReported() {
        val start = System.nanoTime()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(60_000) { compose.onAllNodes(hasTestTag("add"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val ms = (System.nanoTime() - start) / 1_000_000
        android.util.Log.w("FirstLaunch", "first launch of this run: $ms ms to the home screen")
        assertTrue(ms < 60_000)
    }
}
