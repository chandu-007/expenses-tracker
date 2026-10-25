package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.FeedbackMail
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Send feedback. The row is shown in the Help section and the message it would open is built from the phone's own version, system and model. Tapping the row is not
 * done here: it would leave the app for whatever mail app the device has. Written to run on a device; not run when written.
 */
@RunWith(AndroidJUnit4::class)
class FeedbackUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theHelpSectionOffersSendFeedback() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(10_000) { has("add") }
        compose.openMore()
        compose.waitUntil(10_000) { has("open_feedback") }
        runCatching { compose.onNodeWithTag("open_feedback", useUnmergedTree = true).performScrollTo() }
        compose.onNodeWithTag("open_feedback", useUnmergedTree = true).assertExists()
        assertTrue(has("open_guide"), "next to the guide, in Help")
    }

    @Test
    fun theMessageNamesTheVersionTheSystemAndTheModelAndNothingElse() {
        val platform = AndroidPlatform(context)
        assertEquals(BuildConfig.VERSION_NAME, platform.appVersionText)
        assertTrue(platform.osVersionText.startsWith("Android "), platform.osVersionText)
        assertTrue(platform.deviceModelText.isNotBlank())
        val url = FeedbackMail.build(platform.appVersionText, platform.osVersionText, platform.deviceModelText)
        assertTrue(url.startsWith("mailto:nelluri.c@gmail.com?subject=Expenses%20feedback%20%28version%20${BuildConfig.VERSION_NAME}%2C%20Android%29"), url)
        assertTrue(url.contains("Describe%20what%20happened%3A"), url)
    }
}
