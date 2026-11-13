package app.expenses

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.wholeAmounts
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertTrue

/**
 * What a screen reader is given for Home's whole-rupee headline figures (Slice 112): the figures are DRAWN in whole units ("₹1,549"), but the accessibility node a service such as TalkBack reads
 * (`AccessibilityNodeInfo`, taken from the window through UiAutomation, not from Compose's test tree) carries the exact amount ("₹1,549.10"). The test lists every node of Home that has a rupee
 * amount, writes each to the log under `A11yExact` (how it reads), requires the exact text on the month figure, Budget left, Safe to spend, its a-day line and each account tile, and fails when any node
 * says one of these figures ONLY in whole units. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class ExactAmountA11yUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shot = FailureShot()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR

    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = FailureShot.around("ExactA11y_waitFor_$tag") { compose.waitUntil(20_000) { has(tag) } }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun at(offset: Long) = LocalDate.now().plusDays(offset).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun readerNodes(): List<String> {
        val ua = instrumentation.uiAutomation
        ua.serviceInfo = ua.serviceInfo.also { it.flags = it.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val said = listOf(n.text?.toString().orEmpty(), n.contentDescription?.toString().orEmpty()).filter { it.isNotEmpty() }.joinToString(" | ")
            if (said.isNotEmpty()) out += said
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (out.none { it.contains("₹") } && System.currentTimeMillis() < deadline) {
            out.clear()
            walk(ua.rootInActiveWindow)
            if (out.none { it.contains("₹") }) ua.windows.forEach { w -> walk(w.root) }
            if (out.none { it.contains("₹") }) Thread.sleep(500)
        }
        return out
    }

    private fun entry(id: String, offset: Long, merchant: String, minor: Long, category: String, account: String) = db { ok ->
        ok.store.save(Expense(EntryId(id), at(offset), AccountId(account), Money(minor, inr), listOf(CategorySplit(CategoryId(category), Money(minor, inr)))), EntryDetails(merchant = merchant))
    }

    @Test
    fun theWholeRupeeHeadlineFiguresAreReadAsTheirExactAmounts() {
        db { ok ->
            ok.budgets.setLimit(null, Money(6_000_000, inr))
            ok.templates.putSetting("safe_to_spend_on_home", "1")
        }
        entry("a", 0, "BookMyShow", 41_110, "Fun", "bank"); entry("b", 0, "Netflix", 70_150, "Bills", "bank"); entry("c", -1, "Starbucks", 43_650, "Food", "cash")
        db { ok -> ok.store.save(Income(EntryId("sal"), at(-2), AccountId("bank"), Money(18_500_000, inr), CategoryId("Other")), EntryDetails(merchant = "Employer")) }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add"); waitFor("month_spend"); waitFor("budget_summary"); waitFor("home_safe_to_spend_line"); waitFor("balance_chip_Bank")
        Thread.sleep(3_000)
        val nodes = readerNodes()
        nodes.filter { it.contains("₹") }.forEach { android.util.Log.i("A11yExact", "reads: $it") }

        val exact = Regex("""₹[\d,]+\.\d\d""")
        fun readBy(vararg parts: String) = nodes.firstOrNull { n -> parts.all { n.contains(it) } }
        val month = readBy("₹1,549.10"); assertTrue(month != null, "the month figure is read as its exact amount; nodes: $nodes")
        val budget = readBy("left of ₹60,000.00"); assertTrue(budget != null && exact.containsMatchIn(budget), "Budget left is read exactly: $budget")
        val safe = readBy("safe to spend"); assertTrue(safe != null && exact.containsMatchIn(safe), "Safe to spend is read exactly: $safe")
        val perDayBudget = readBy("a day from your budget"); assertTrue(perDayBudget != null && exact.containsMatchIn(perDayBudget), "the budget's a-day line is read exactly: $perDayBudget")
        val perDaySafe = readBy("from your balances"); assertTrue(perDaySafe != null && exact.containsMatchIn(perDaySafe), "the balances' a-day line is read exactly: $perDaySafe")
        val bank = readBy("Bank"); assertTrue(bank != null && exact.containsMatchIn(bank), "the Bank tile is read with its exact balance: $bank")

        // no node says one of these figures only in whole units
        val exactFigures = nodes.flatMap { exact.findAll(it).map { m -> m.value }.toList() }.toSet()
        val roundedOnly = exactFigures.map { it.wholeAmounts() }.filter { !it.contains('.') }.toSet()
        val offenders = nodes.filter { n -> roundedOnly.any { r -> Regex(Regex.escape(r) + """(?![\d,.])""").containsMatchIn(n) } && !exact.containsMatchIn(n) }
        assertTrue(offenders.isEmpty(), "a node reads a headline figure in whole units only: $offenders")
    }
}
