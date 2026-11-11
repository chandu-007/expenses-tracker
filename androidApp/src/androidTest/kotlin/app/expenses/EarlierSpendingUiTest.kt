package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.ui.WideLayoutTestHook
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Bring in earlier spending" (More, and Home's Getting started card): the three ways to catch up, in the order that is recommended, with no new importer: the statement importer of Your data, a fast form
 * that keeps its date and category, and the Accounts page for balances. Invented amounts and a made-up statement.
 */
@RunWith(AndroidJUnit4::class)
class EarlierSpendingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() { androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get(); CheckupTestHook.statement = null; resetApp() }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        CheckupTestHook.statement = null; WideLayoutTestHook.widthDp = null; WideLayoutTestHook.heightDp = null
        shell("settings put system font_scale 1.0")
        resetApp(); files.forEach { it.delete() }
    }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String {
        waitFor(tag)
        // a tag on a Text is read as it is; a tag on a row or a button that merges its texts is read from the merged tree
        fun read(unmerged: Boolean) = runCatching { compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString("") { it.text } else "" } }.getOrDefault("")
        return read(true).ifEmpty { read(false) }
    }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performTextInput(text) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun openPage() { launch(); compose.openMore(); tap("open_earlier_spending"); waitFor("earlier_spending_title") }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList() } }!!
    private fun firstExpenseCategory() = runBlocking { container.write { ok -> ok.categories.list().first { it.kind.forExpense && !it.hidden && it.parentId == null }.id } }!!
    private fun file(name: String, content: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { it.writeText(content); files += it })

    private val lastMonthEnd: LocalDate get() = LocalDate.now().withDayOfMonth(1).minusDays(1)
    private fun dayOf(occurredAt: Long) = java.time.Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

    @Test
    fun thePageGivesThreeWaysInOrderExplainsWhyOldTextsCannotBeReadAndIsReachableFromMoreAndFromGettingStarted() {
        openPage()
        assertEquals("Spent money before you started using the app?", textOf("earlier_spending_title"))
        assertTrue(textOf("earlier_spending_order").startsWith("Best order: 1,"))
        for (tag in listOf("earlier_spending_way_statement", "earlier_spending_way_manual", "earlier_spending_way_balances")) waitFor(tag)
        val tops = listOf("statement", "manual", "balances").map { compose.onNodeWithTag("earlier_spending_way_$it", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y }
        assertTrue(tops[0] < tops[1] && tops[1] < tops[2], "statement, then by hand, then balances: $tops")
        assertTrue(textOf("earlier_spending_no_scan").contains("never looks through the messages already on your phone"), textOf("earlier_spending_no_scan"))
        // no new permission or reader: the page says what it cannot do, and offers no scan
        assertFalse(has("earlier_spending_scan_texts"))
        compose.onNodeWithTag("earlier_spending_back").performClick(); waitFor("more_page")
        // from Home's Getting started card
        runBlocking { container.write { ok ->
            ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("e"), System.currentTimeMillis(), app.expenses.core.domain.ledger.AccountId("cash"), app.expenses.core.domain.Money(10_000, app.expenses.core.domain.Currency.INR), listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("Food"), app.expenses.core.domain.Money(10_000, app.expenses.core.domain.Currency.INR)))))
            ok.templates.putSetting("first_week_dismissed", "0"); ok.templates.putSetting("first_week_started", (System.currentTimeMillis() - 86_400_000L).toString())
        } }
        launch(); waitFor("first_week_card"); tap("first_week_toggle"); tap("first_week_earlier"); waitFor("earlier_spending_title")
    }

    @Test
    fun theFastFormStartsOnLastMonthEndKeepsDateAndCategoryAndAddsOneExpenseForEachAmount() {
        openPage()
        assertEquals("Date: " + lastMonthEnd.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)), textOf("earlier_spending_date"))
        val category = firstExpenseCategory()
        tap("earlier_spending_category_$category")
        type("earlier_spending_amount", "250"); tap("earlier_spending_add")
        compose.waitUntil(10_000) { entries().size == 1 }
        val first = entries().single()
        assertEquals(25_000L, first.amount_minor); assertEquals(lastMonthEnd, dayOf(first.occurred_at), "on the day shown")
        waitFor("earlier_spending_added_count"); assertEquals("1 expense added in this visit", textOf("earlier_spending_added_count"))
        assertTrue(textOf("earlier_spending_added_0").startsWith("250 on ${lastMonthEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"), textOf("earlier_spending_added_0"))
        // the amount is empty again; the date and the category stayed, so the next one is only an amount
        compose.waitUntil(10_000) { compose.onNodeWithTag("earlier_spending_amount", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.isNullOrEmpty() }
        assertEquals("Date: " + lastMonthEnd.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)), textOf("earlier_spending_date"))
        type("earlier_spending_amount", "90.50"); tap("earlier_spending_add")
        compose.waitUntil(10_000) { entries().size == 2 }
        val all = entries()
        assertEquals(setOf(25_000L, 9_050L), all.map { it.amount_minor }.toSet())
        assertTrue(all.all { dayOf(it.occurred_at) == lastMonthEnd })
        assertEquals(1, all.map { it.account_id }.toSet().size)
        waitFor("earlier_spending_added_1"); assertEquals("2 expenses added in this visit", textOf("earlier_spending_added_count"))
        // the date picker opens and OK keeps the date
        tap("earlier_spending_date"); waitFor("earlier_spending_date_ok"); tap("earlier_spending_date_ok"); gone("earlier_spending_date_ok")
        assertEquals(2, entries().size, "choosing a date adds nothing")
    }

    @Test
    fun anAmountThatIsNotANumberIsRefusedInPlainWordsAndNothingIsSaved() {
        openPage()
        type("earlier_spending_amount", "abc"); tap("earlier_spending_add")
        waitFor("earlier_spending_problem"); assertTrue(textOf("earlier_spending_problem").isNotBlank())
        assertEquals(0, entries().size); assertFalse(has("earlier_spending_added_count"))
        compose.onNodeWithTag("earlier_spending_amount", useUnmergedTree = true).performTextClearance(); type("earlier_spending_amount", "40"); tap("earlier_spending_add")
        compose.waitUntil(10_000) { entries().size == 1 }
    }

    @Test
    fun theStatementButtonOpensTheImporterOfYourDataAndClosingItComesBackHere() {
        val csv = """
            Account Statement,,,,,,
            Customer ID: 12345,,,,,,

            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/10/26,UPI-SWIGGY-9876,000123,01/10/26,"1,250.50",,"49,750.00"
            02/10/26,SALARY OCT,000124,02/10/26,,"50,000.00","99,750.00"
            03/10/26,"CAFE, INDIRANAGAR",000125,03/10/26,300.00,,"99,450.00"
            Closing balance,,,,,,"99,450.00"
        """.trimIndent()
        CheckupTestHook.statement = file("earlier.csv", csv).toString()
        openPage()
        tap("earlier_spending_import")
        waitFor("import_confirm"); waitFor("import_summary")
        assertEquals(0, entries().size, "nothing is saved until the person confirms")
        tap("import_confirm"); compose.waitUntil(10_000) { entries().size == 3 }
        tap("import_close")
        waitFor("earlier_spending_title"); assertFalse(has("import_statement"), "the way back is to this page, not to Your data")
    }

    @Test
    fun theBalancesButtonOpensAccountsAndBackComesBack() {
        openPage()
        tap("earlier_spending_balances"); waitFor("accounts_list")
        tap("back"); waitFor("earlier_spending_title")
    }

    @Test
    fun atTwiceTheFontSizeAndInATwoPaneWindowTheWholeFormStillWorks() {
        shell("settings put system font_scale 2.0"); Thread.sleep(2_000)
        openPage()
        for (tag in listOf("earlier_spending_import", "earlier_spending_date", "earlier_spending_amount", "earlier_spending_add", "earlier_spending_balances")) waitFor(tag)
        type("earlier_spending_amount", "75"); tap("earlier_spending_add"); compose.waitUntil(10_000) { entries().size == 1 }
        WideLayoutTestHook.widthDp = 1000; WideLayoutTestHook.heightDp = 800
        openPage()
        type("earlier_spending_amount", "10"); tap("earlier_spending_add"); compose.waitUntil(10_000) { entries().size == 2 }
        val page = compose.onNodeWithTag("earlier_spending", useUnmergedTree = true).fetchSemanticsNode().size
        assertTrue(page.width > 0)
    }
}
