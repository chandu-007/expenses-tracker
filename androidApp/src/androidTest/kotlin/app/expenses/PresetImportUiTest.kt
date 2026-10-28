package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A file exported by another app is recognised by its columns and imported with its categories and accounts. The files are INVENTED, in the documented layouts. */
@RunWith(AndroidJUnit4::class)
class PresetImportUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp(); files.forEach { it.delete() } }

    private fun file(name: String, content: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { it.writeBytes(content.encodeToByteArray()); files += it })
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitAnyText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
    private fun entryCount() = entries().size.toLong()
    private fun categoryNames() = runBlocking { container.write { it.categories.list().map { c -> c.name } } }!!
    private fun categoryOf(e: app.expenses.core.storage.db.Entry) = runBlocking { container.write { ok -> e.category_id ?: ok.db.schemaQueries.splitsOf(e.id).executeAsList().firstOrNull()?.category_id } }

    private fun openImport(name: String, csv: String) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); waitFor("open_data"); compose.onNodeWithTag("open_data").performScrollTo().performClick(); waitFor("import_statement")
        viewModel().startImport(file(name, csv).toString()); waitFor("import_preset")
    }

    private val moneyManager = """
        Date,Account,Category,Subcategory,Note,Amount,Income/Expense,Description
        10/05/2026,Bank,Food,Eating out,Lunch with team,450.00,Expense,office
        10/06/2026,Bank,Salary,,October pay,85000.00,Income,
        10/07/2026,Bank,Cash,,ATM,2000.00,Transfer-Out,
        10/08/2026,Cash,Pet care,,Dog food,600.00,Expense,
        10/09/2026,Cash,Pet care,,Vet,1500.00,Expense,
        10/10/2026,Elsewhere,Fun,,Cinema,300.00,Expense,
    """.trimIndent()

    @Test
    fun aMoneyManagerFileIsRecognisedAndImportedWithItsAccountsAndMatchedCategoriesAndNothingIsCreatedUnlessTicked() {
        openImport("mm.csv", moneyManager)
        waitText("import_preset_title", "Looks like a Money Manager export")
        waitText("import_preset_left_out", "1 transfer between your own accounts")
        waitText("import_summary", "5 transactions: spending ₹2,850.00, income ₹85,000.00")
        assertTrue(!has("pick_date"), "no column choices for a recognised file")
        waitFor("import_new_categories"); waitText("import_new_categories", "Pet care")
        waitText("import_categories_matched", "rows found a category of yours")
        compose.onNodeWithTag("import_create_categories").performScrollTo().assertIsOff()
        assertEquals(0L, entryCount(), "nothing is saved until the person confirms")
    }

    @Test
    fun withoutTheTickTheUnmatchedCategoryGoesToOtherAndNothingIsCreated() {
        val before = categoryNames()
        openImport("mm.csv", moneyManager)
        waitFor("import_confirm"); compose.onNodeWithTag("import_confirm").performClick(); waitFor("import_result")
        assertEquals(before, categoryNames(), "nothing created")
        val rows = entries().associateBy { it.merchant }
        assertEquals(listOf("Lunch with team", "October pay", "Dog food", "Vet", "Cinema"), entries().map { it.merchant })
        assertEquals("Food", categoryOf(rows.getValue("Lunch with team")))
        assertEquals("income:salary", categoryOf(rows.getValue("October pay")))
        assertEquals("Other", categoryOf(rows.getValue("Dog food")), "no category of that name: Other")
        assertEquals("bank", rows.getValue("Lunch with team").account_id); assertEquals("cash", rows.getValue("Dog food").account_id)
        assertEquals("bank", rows.getValue("Cinema").account_id, "an account the person does not have: the one chosen")
        assertEquals("office", rows.getValue("Lunch with team").note)
        assertEquals(setOf("csv"), entries().map { it.source }.toSet())
        // importing the same file again adds nothing
        compose.onNodeWithTag("import_close").performClick(); waitFor("import_statement")
        viewModel().startImport(file("mm2.csv", moneyManager).toString()); waitFor("import_preset")
        compose.onNodeWithTag("import_confirm").performClick(); waitAnyText("Imported 0 transactions"); waitAnyText("5 were already in the app")
        assertEquals(5, entries().size)
    }

    @Test
    fun withTheTickTheNewCategoryIsCreatedAndUsed() {
        openImport("mm.csv", moneyManager)
        waitFor("import_create_categories"); tap("import_create_categories")
        compose.waitUntil(10_000) { !compose.onAllNodes(hasText("Without the tick nothing is created", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("import_confirm").performClick(); waitFor("import_result")
        assertTrue("Pet care" in categoryNames())
        val rows = entries().associateBy { it.merchant }
        val pet = runBlocking { container.write { it.categories.list().first { c -> c.name == "Pet care" }.id } }
        assertEquals(pet, categoryOf(rows.getValue("Dog food"))); assertEquals(pet, categoryOf(rows.getValue("Vet")))
    }

    @Test
    fun theFileCanBeReadAsAPlainStatementInstead() {
        openImport("mm.csv", moneyManager)
        tap("import_preset_off")
        waitGone("import_preset"); waitFor("pick_date")
    }

    @Test
    fun aYnabRegisterShowsItsNameAndReadsOutflowAndInflow() {
        openImport("ynab.csv", """
            "Account","Flag","Date","Payee","Category Group/Category","Category Group","Category","Memo","Outflow","Inflow","Cleared"
            "Bank","","10/25/2026","Corner Grocer","Everyday: Groceries","Everyday","Groceries","weekly shop","₹1,200.50","₹0.00","Cleared"
            "Bank","","10/26/2026","Acme Payroll","Inflow: Ready to Assign","Inflow","Ready to Assign","","₹0.00","₹50,000.00","Cleared"
        """.trimIndent())
        waitText("import_preset_title", "Looks like a YNAB export")
        waitText("import_summary", "2 transactions: spending ₹1,200.50, income ₹50,000.00")
        compose.onNodeWithTag("import_confirm").performClick(); waitFor("import_result")
        assertEquals("Groceries", categoryOf(entries().first()))
    }

    @Test
    fun aSplitwiseFileSaysItsAmountsAreTheWholeCost() {
        openImport("splitwise.csv", "Date,Description,Category,Cost,Currency,Asha,Ravi\n2026-10-05,Dinner,Dining out,1200.00,INR,800.00,-800.00\n")
        waitText("import_preset_title", "Looks like a Splitwise export")
        waitText("import_preset_note", "whole cost")
    }

    @Test
    fun anOrdinaryBankFileIsNotTakenForAnotherApp() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); waitFor("open_data"); compose.onNodeWithTag("open_data").performScrollTo().performClick(); waitFor("import_statement")
        viewModel().startImport(file("b.csv", "Date,Narration,Withdrawal Amt.,Deposit Amt.\n01/10/26,SHOP,250.00,\n").toString()); waitFor("import_account_bank")
        waitFor("pick_date")
        assertTrue(!has("import_preset"))
    }

    @Test
    fun anUnrecognisedTypeIsLeftOutAndSaidSoNeverReadAsSpending() {
        openImport("mm2.csv", """
            Date,Account,Category,Subcategory,Note,Amount,Income/Expense,Description
            10/05/2026,Bank,Food,,Lunch,450.00,Ausgabe,
            10/06/2026,Bank,Food,,Dinner,300.00,Expense,
        """.trimIndent())
        waitText("import_preset_left_out", "1 row whose Income/Expense/Transfer value was not recognised")
        waitText("import_summary", "1 transaction: spending ₹300.00")
    }

    @Test
    fun aSplitwiseSettleUpIsLeftOut() {
        openImport("sw2.csv", "Date,Description,Category,Cost,Currency,Asha,Ravi\n2026-10-05,Dinner,Dining out,1200.00,INR,800.00,-800.00\n2026-10-06,Payment,Payment,500.00,INR,-500.00,500.00\n")
        waitText("import_preset_left_out", "1 settle-up payment between friends")
        waitText("import_summary", "1 transaction: spending ₹1,200.00")
    }
}
