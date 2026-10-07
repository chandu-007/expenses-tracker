package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()
    private val day = 86_400_000L

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        scenario?.close()
        resetApp()
        files.forEach { it.delete() }
    }

    private fun launch() { scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitAnyText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun present(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) = runBlocking { container.write { it.templates.putSetting(key, value) } }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!

    private fun seed(n: Int) = runBlocking {
        container.write { ok -> repeat(n) { i -> ok.store.save(Expense(EntryId("e$i"), System.currentTimeMillis() - i * 1_000L, AccountId("cash"), Money(100L + i, Currency.INR)), EntryDetails(merchant = "Shop $i")) } }
    }

    // ---- the first run ----

    @Test
    fun aFreshInstallOffersToAddTheFirstExpenseAndTheFirstExpenseTakesThreeSteps() {
        launch()
        waitFor("welcome")
        waitAnyText("uninstalling the app deletes it") // the honest sentence about where the data lives
        val started = System.nanoTime()
        compose.onNodeWithTag("welcome_add").performClick()          // step 1
        waitFor("amount")
        compose.onNodeWithTag("amount").performTextInput("250")      // step 2 (the amount field already has focus)
        compose.onNodeWithTag("save").performClick()                 // step 3
        compose.waitUntil(10_000) { entries() == 1L }
        val machineMs = (System.nanoTime() - started) / 1_000_000
        android.util.Log.w("ONBOARD", "first expense saved after 3 steps in $machineMs ms of machine time (a person adds their own time)")
        waitGone("welcome")
        assertTrue(!present("welcome"), "the welcome goes away by itself with the first entry")
    }

    @Test
    fun skippingHidesItForGoodEvenAfterARestartAndTheListSaysHowToAdd() {
        launch()
        waitFor("welcome")
        compose.onNodeWithTag("welcome_skip").performClick()
        waitGone("welcome")
        waitAnyText("No expenses yet")
        assertEquals("1", setting("welcome_dismissed"))
        launch()
        waitAnyText("No expenses yet")
        assertTrue(!present("welcome"))
    }

    @Test
    fun theImportOrRestoreButtonOpensTheDataScreenWhereBothLive() {
        launch()
        waitFor("welcome")
        compose.onNodeWithTag("welcome_import").performClick()
        waitFor("import_statement"); waitFor("open_backup")
    }

    @Test
    fun deletingEveryEntryDoesNotBringTheWelcomeBackBecauseTheyHaveUsedTheApp() {
        seed(2)
        runBlocking { container.write { ok -> ok.store.delete(EntryId("e0")); ok.store.delete(EntryId("e1")) } }
        launch()
        waitAnyText("No expenses yet")
        assertTrue(!present("welcome"))
    }

    @Test
    fun theReminderCountsOnlyEntriesThatStillExist() {
        seed(12)
        runBlocking { container.write { ok -> repeat(5) { i -> ok.store.delete(EntryId("e$i")) } } } // 7 left
        launch()
        assertTrue(!present("backup_nudge"), "twelve were added but only seven remain")
    }

    @Test
    fun anExistingLedgerNeverShowsTheWelcome() {
        seed(3)
        launch()
        assertTrue(!present("welcome"))
        assertEquals(null, setting("welcome_dismissed"), "nothing was recorded either")
    }

    // ---- the backup reminder ----

    @Test
    fun theReminderAppearsWhenThereIsSomethingToLoseAndNotBefore() {
        seed(9)
        launch()
        assertTrue(!present("backup_nudge"), "nine entries is not yet enough")
        runBlocking { container.write { ok -> ok.store.save(Expense(EntryId("tenth"), System.currentTimeMillis(), AccountId("cash"), Money(500, Currency.INR)), EntryDetails(merchant = "Tenth")) } }
        launch()
        waitFor("backup_nudge")
    }

    @Test
    fun laterQuietensItForTwoWeeksAndItReturnsAfterThat() {
        seed(12)
        launch(); waitFor("backup_nudge")
        compose.onNodeWithTag("nudge_later").performClick()
        waitGone("backup_nudge")
        launch(); assertTrue(!present("backup_nudge"), "still quiet after a restart")
        put("backup_nudge_snoozed_at", (System.currentTimeMillis() - 15 * day).toString()) // 15 days ago
        launch(); waitFor("backup_nudge")
    }

    @Test
    fun backUpNowOpensTheDataScreenAndARealBackupSilencesTheReminder() {
        seed(12)
        launch(); waitFor("backup_nudge")
        compose.onNodeWithTag("nudge_backup").performClick()
        waitFor("create_backup")
        val uri = Uri.fromFile(File(context.cacheDir, "nudge.expbak").also { files += it })
        runBlocking { DataActions(context, container).backup(uri, null) }
        assertTrue(setting("backup_last_at") != null && setting("backup_last_count") == "12", "the backup was recorded with its entry count")
        launch(); assertTrue(!present("backup_nudge"), "a backup was just made")
    }

    @Test
    fun aBackupThatFailsDoesNotCountAndIsNotRecorded() {
        seed(12)
        val bad = Uri.fromFile(File("/nonexistent-directory/x.expbak"))
        kotlin.runCatching { runBlocking { DataActions(context, container).backup(bad, null) } }.also { assertTrue(it.isFailure, "writing to nowhere fails") }
        assertEquals(null, setting("backup_last_at"), "a failed backup is not remembered")
        launch(); waitFor("backup_nudge")
    }

    @Test
    fun anOldBackupWithManyNewEntriesBringsItBackAndAFreshOneDoesNot() {
        seed(30)
        put("backup_last_at", (System.currentTimeMillis() - 40 * day).toString()); put("backup_last_count", "5")
        launch(); waitFor("backup_nudge")
        put("backup_last_at", (System.currentTimeMillis() - 2 * day).toString())
        runBlocking { container.write { ok -> ok.templates.putSetting("backup_nudge_snoozed_at", "0") } } // an expired snooze, to prove the age is what matters
        launch(); assertTrue(!present("backup_nudge"), "many new entries but the backup is two days old")
    }
}
