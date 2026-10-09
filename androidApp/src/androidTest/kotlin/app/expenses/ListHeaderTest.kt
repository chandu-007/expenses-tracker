package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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

/** The header of the list is where every other screen is reached from, so everything in it must be visible and tappable. */
@RunWith(AndroidJUnit4::class)
class ListHeaderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
    }

    @Test
    fun everyActionIsOnScreenAndTheTitleKeepsItsWidth() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("add")).fetchSemanticsNodes().isNotEmpty() }
        for (tag in listOf("open_search", "open_reports", "more_menu", "add")) compose.onNodeWithTag(tag).assertIsDisplayed()
        val title = compose.onNode(hasText("Spent this", substring = true)).getUnclippedBoundsInRoot()
        assertTrue(title.width > 120.dp, "the title is ${title.width} wide; it was squeezed to a single letter when the header overflowed")
        assertTrue(title.height < 40.dp, "the title wrapped onto ${title.height} of height")
        compose.onNodeWithTag("more_menu").performClick()
        for (tag in listOf("manage_categories", "open_recurring", "open_notifications", "open_security", "open_data")) {
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        compose.onNodeWithText("Your data: export and backup").assertIsDisplayed()
    }
}
