package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N-23 (feat-016): "Test a backup" in Your data restores a chosen backup into a scratch database in the cache folder, says what it held, and deletes it. The live database,
 * its settings, node_id and db_key are never touched. The file is handed to the view model as the picker would (the system picker is not driven). Invented data only.
 */
@RunWith(AndroidJUnit4::class)
class UiFeat016BackupTestTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val actions get() = DataActions(context, container)
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun reset() = runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } }
    @Before fun fresh() = reset()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); reset(); files.forEach { it.delete() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) }
    private fun waitText(tag: String, text: String, ms: Long = 60_000) = compose.waitUntil(ms) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun file(name: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { files += it })
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.openMore()
        waitFor("open_data")
        compose.onNodeWithTag("open_data").also { runCatching { it.performScrollTo() } }.performClick()
        waitFor("test_backup")
    }

    private fun populate() = runBlocking {
        container.write { ok ->
            repeat(5) { i ->
                ok.store.save(Expense(EntryId("e$i"), System.currentTimeMillis() - i * 1_000, AccountId("cash"), Money(10_000L * (i + 1), inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000L * (i + 1), inr)))), EntryDetails(merchant = "Shop $i"))
            }
        }
    }

    private fun entryCount() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!
    private fun settings() = runBlocking { container.write { it.db.schemaQueries.settingsWithPrefix("").executeAsList().map { s -> s.key to s.value_ } } }!!
    private fun appFile(name: String) = File(context.noBackupFilesDir, name).takeIf { it.exists() }?.readBytes()
    private fun scratchLeft() = File(context.cacheDir, "restore-test").listFiles().orEmpty().map { it.name }

    @Test
    fun aGoodBackupOpensFineAndTheLiveDataSettingsNodeIdAndKeyAreUntouched() {
        populate()
        val uri = file("feat016.expbak")
        runBlocking { actions.backup(uri, null) }
        launch()
        val before = settings(); val node = appFile("node_id"); val key = appFile("db_key.bin")
        viewModel().testBackup(uri.toString())
        waitText("test_backup_result", "Opens fine: 5 entries, 2 accounts, last entry ")
        assertEquals(5L, entryCount(), "nothing was added to this phone: the test restored into the scratch database")
        assertEquals(before, settings(), "no setting changed")
        assertContentEquals(node, appFile("node_id")); assertContentEquals(key, appFile("db_key.bin"))
        assertTrue(scratchLeft().isEmpty(), "the scratch database is deleted: ${scratchLeft()}")
    }

    @Test
    fun aProtectedBackupAsksForItsPassphraseAndAWrongOneIsSaidPlainly() {
        populate()
        val uri = file("feat016-secret.expbak")
        runBlocking { actions.backup(uri, "correct horse battery") }
        launch()
        viewModel().testBackup(uri.toString())
        waitFor("test_passphrase")
        compose.onNodeWithTag("test_passphrase").performTextInput("wrong one!")
        compose.onNodeWithTag("test_unlock").performClick()
        waitText("test_backup_result", "passphrase is wrong")
        viewModel().testBackup(uri.toString())
        waitFor("test_passphrase")
        compose.onNodeWithTag("test_passphrase").performTextInput("correct horse battery")
        compose.onNodeWithTag("test_unlock").performClick()
        waitText("test_backup_result", "Opens fine: 5 entries")
        assertEquals(5L, entryCount())
        assertTrue(scratchLeft().isEmpty())
    }

    @Test
    fun aFileThatIsNotABackupIsSaidPlainlyAndNothingChanges() {
        populate()
        val uri = file("feat016-junk.expbak")
        File(uri.path!!).writeBytes(Random(5).nextBytes(4_000))
        launch()
        val before = settings()
        viewModel().testBackup(uri.toString())
        waitText("test_backup_result", "did not pass")
        assertEquals(5L, entryCount()); assertEquals(before, settings())
        assertTrue(scratchLeft().isEmpty())
    }

    @Test
    fun theButtonIsBesideTheBackupStatusLine() {
        launch()
        waitFor("backup_status_line")
        val status = compose.onNodeWithTag("backup_status_line", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("test_backup", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(button.top >= status.bottom - 1f && button.top - status.bottom < 200f, "right under the status line: $status / $button")
    }
}
