package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Density
import app.expenses.core.domain.Money
import app.expenses.core.domain.ThemeMode
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AppearanceUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    @Suppress("UNCHECKED_CAST")
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) } as T
    private fun saved() = Appearances.decode(db { it.templates.setting("appearance") })
    private fun height(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value }
    private fun open() { compose.openMore(); tap("open_appearance"); waitFor("theme_DARK") }

    @Test
    fun theChoicesAreSavedKeptAfterARestartAndShownAsSelectedWhenTheScreenReopens() {
        launch(); open()
        tap("theme_DARK"); tap("accent_green"); tap("density_COMPACT")
        compose.waitUntil(10_000) { saved().mode == ThemeMode.DARK && saved().accent.key == "green" && saved().density == Density.COMPACT }
        launch(); open()
        assertEquals(Triple(ThemeMode.DARK, "green", Density.COMPACT), saved().let { Triple(it.mode, it.accent.key, it.density) })
        compose.onNodeWithTag("theme_DARK", useUnmergedTree = true).assertIsSelected()
    }

    @Test
    fun compactDensityMakesTheListRowsShorterAndComfortableRestoresThem() {
        db { ok -> ok.store.save(Expense(EntryId("e"), System.currentTimeMillis(), AccountId("cash"), Money(500, Currency.INR)), EntryDetails(merchant = "Cafe")) }
        launch(); waitFor("row_Cafe")
        val comfortable = height("row_Cafe")
        open(); tap("density_COMPACT"); compose.waitUntil(10_000) { saved().density == Density.COMPACT }; tap("appearance_back"); compose.closeMore(); waitFor("row_Cafe")
        val compact = height("row_Cafe")
        assertTrue(compact < comfortable - 4, "compact $compact dp should be shorter than comfortable $comfortable dp")
        open(); tap("density_COMFORTABLE"); compose.waitUntil(10_000) { saved().density == Density.COMFORTABLE }; tap("appearance_back"); compose.closeMore(); waitFor("row_Cafe")
        assertEquals(comfortable, height("row_Cafe"), 0.5f)
    }

    @Test
    fun theLookIsPartOfABackupSoARestoreOnAnotherPhoneKeepsIt() {
        launch(); open(); tap("accent_teal"); compose.waitUntil(10_000) { saved().accent.key == "teal" }
        val data = db { it.backup.export() }
        assertEquals("teal", Appearances.decode(data.settings["appearance"]).accent.key)
    }
}


