package app.expenses

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import app.expenses.ui.BillItemsDialog
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Google's Accessibility Test Framework (ATF) over the screens of the app: controls without a label, touch targets under 48 dp, text with too little contrast, controls a
 * screen reader skips or announces twice.
 *
 * TWO ways in, because on this device only one of them sees Compose:
 *  1. Compose's own `enableAccessibilityChecks()` (ui-test-junit4-accessibility) on the test rule, which runs ATF before every click and text input and on
 *     [tryPerformAccessibilityChecks]. Measured on the API 26 emulator with Compose 1.12.1 and ATF 4.1.1: it runs, but it examines only the Android VIEW tree (DecorView, ComposeView,
 *     AndroidComposeView, dialog windows) and none of the Compose semantics nodes: every result is NOT_RUN, and a deliberately bad control (see [theChecksReallyRunAndReportAKnownBadControl])
 *     is not reported. It stays switched on, so that it does its part where it can and so that a newer Compose or device that makes it see the nodes starts to be enforced.
 *  2. [scan]: the same ATF checks (the latest preset: SpeakableTextPresent, TouchTargetSize, TextContrast, ImageContrast, RedundantDescription, TextSize, EditableContentDesc,
 *     ...) run on the accessibility node tree of every window of the app, read through UiAutomation. That is the tree TalkBack reads, with every Compose node in it, and a screenshot
 *     for the contrast checks. This is what finds the app's findings.
 *
 * Two modes. Normally (and in the full suite) an ERROR from either way fails the test, except the findings listed in [known]: each is written up with a proposed fix in
 * docs/ACCESSIBILITY_FINDINGS.md, so the test guards against NEW findings while those are being fixed (remove a line from [known] when its fix lands). With the instrumentation
 * argument `a11yCollect=true` nothing fails and nothing is suppressed: every ERROR and WARNING is written to `<external files dir of the app>/a11y_findings.tsv` and the log (tag A11Y):
 *   adb shell am instrument -w -e class app.expenses.AccessibilityUiTest -e a11yCollect true app.expenses.test/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/data/app.expenses/files/a11y_findings.tsv
 *
 * The screen name on a finding is the step the test was in; the element text identifies it.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<*>? = null
    private val collect = InstrumentationRegistry.getArguments().getString("a11yCollect") == "true"
    private val inr = Currency.INR
    private val day = 86_400_000L

    /** One finding as the framework reported it. */
    data class Finding(val screen: String, val check: String, val type: String, val element: String, val message: String)

    /** A finding that is known and written up in docs/ACCESSIBILITY_FINDINGS.md. [check] is the framework check's class name, [element] the element's text or description (null: any); a finding matches when the element's description CONTAINS it. */
    private class Known(val check: String, val element: String?)

    @Before fun fresh() {
        runBlocking { container.startFresh() }
        compose.enableAccessibilityChecks() // way 1 (see above); an ERROR it reports fails the test
    }

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        runBlocking { container.startFresh() }
        if (collect) report()
    }

    // ---- way 2: ATF on the node tree --------------------------------------------------------------------------------------------------------------------

    private var screen = "?"

    /** The element's own text or description; for a clickable row or card that has none, the texts inside it (the node tree does not merge them), so that a finding can be found on screen. */
    private fun describe(e: ViewHierarchyElement?): String {
        if (e == null) return "(no element)"
        fun own(x: ViewHierarchyElement) = x.text?.toString()?.takeIf { it.isNotBlank() } ?: x.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        own(e)?.let { return it }
        val inside = mutableListOf<String>()
        fun walk(x: ViewHierarchyElement) {
            for (i in 0 until x.childViewCount) { if (inside.size >= 3) return; val c = x.getChildView(i); own(c)?.let { inside += it }; walk(c) }
        }
        walk(e)
        return if (inside.isEmpty()) "(${e.className})" else "(${e.className} containing: ${inside.joinToString(" / ")})"
    }

    private fun isKnown(check: String, element: String) = known.any { it.check == check && (it.element == null || it.element in element) }

    /**
     * Runs ATF's latest hierarchy checks on the accessibility node tree of every window of this app (the screen, and a dialog or menu over it), with a screenshot for the contrast
     * checks. Returns the ERRORs that are not known; in guard mode the caller fails on them.
     */
    private fun scan(name: String): List<Finding> {
        screen = name
        compose.waitForIdle()
        val ui = instrumentation.uiAutomation
        // let dialogs finish fading in and the keyboard finish sliding up: the contrast checks read a screenshot, and text under a half-faded dialog or a moving keyboard reads as washed out
        runCatching { ui.waitForIdle(800, 6_000) }
        compose.waitForIdle()
        val windows = ui.windows.filter { it.root?.packageName == context.packageName }
        val shot = ui.takeScreenshot()
        if (collect && shot != null) context.getExternalFilesDir("a11y_shots")?.let { dir -> File(dir, name.replace(Regex("[^A-Za-z0-9]+"), "_") + ".png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) } } // to look at what a finding is about
        val builder = when {
            windows.isNotEmpty() -> AccessibilityHierarchyAndroid.newBuilder(windows, context)
            else -> AccessibilityHierarchyAndroid.newBuilder(ui.rootInActiveWindow ?: error("no accessibility window of ${context.packageName} to check"), context) // the active window only
        }
        val hierarchy = builder.setObtainCharacterLocations(true).build()
        val params = Parameters().also { if (shot != null) it.putScreenCapture(BitmapImage(shot)) }
        val unexpected = mutableListOf<Finding>()
        for (c in AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)) {
            for (r in c.runCheckOnHierarchy(hierarchy, null, params)) {
                calls.incrementAndGet()
                val check = c.javaClass.simpleName
                types.merge("$check:${r.type.name}", 1, Int::plus)
                if (r.type != AccessibilityCheckResultType.ERROR && r.type != AccessibilityCheckResultType.WARNING) continue
                val b = r.element?.boundsInScreen?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "-"
                val what = describe(r.element)
                val f = Finding(screen, check, r.type.name, "$what [${r.element?.className}, $b]", r.getMessage(java.util.Locale.US).toString().replace('\n', ' ').replace('\t', ' '))
                if (found.putIfAbsent("$screen|$check|${f.type}|$what", f) == null) android.util.Log.w("A11Y", "${f.screen}\t${f.check}\t${f.type}\t${f.element}\t${f.message}")
                if (r.type == AccessibilityCheckResultType.ERROR) { errorsSeen.incrementAndGet(); if (!isKnown(check, what)) unexpected += f }
            }
        }
        return unexpected.distinctBy { it.check to it.element }
    }

    private fun report() {
        val dir = context.getExternalFilesDir(null) ?: return
        File(dir, "a11y_findings.tsv").writeText("# checks run: ${calls.get()}; results by check and type: ${types.toSortedMap()}\n" + found.values.filter { !it.screen.startsWith("calibration") }.joinToString("\n") { listOf(it.screen, it.check, it.type, it.element, it.message).joinToString("\t") } + "\n")
    }

    // ---- the app's side ----------------------------------------------------------------------------------------------------------------------------------

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)); waitFor("add") }

    /** Checks the screen now, both ways. [tag] names a node that is on screen (Compose's checks are called through it). */
    private fun check(name: String, tag: String) {
        waitFor(tag)
        screen = name
        compose.waitForIdle()
        compose.onNodeWithTag(tag, useUnmergedTree = true).tryPerformAccessibilityChecks()
        val unexpected = scan(name)
        if (!collect) assertTrue(unexpected.isEmpty(), "accessibility findings that are not known (see docs/ACCESSIBILITY_FINDINGS.md, and add them to AccessibilityUiTest.known only with a write-up):\n" + unexpected.joinToString("\n") { "  ${it.screen}: ${it.check}: ${it.element}: ${it.message}" })
    }

    private fun entry(i: Int, merchant: String, rupees: Long, category: String, account: String) {
        runBlocking {
            container.write { ok ->
                ok.store.save(
                    Expense(EntryId("a11y$i"), System.currentTimeMillis() - i * 3_600_000L, AccountId(account), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))),
                    EntryDetails(merchant = merchant),
                )
            }
        }
    }

    private fun seedHome() {
        entry(0, "Tea stall", 45, "Food", "cash"); entry(1, "Metro card", 500, "Transport", "bank"); entry(2, "Electricity bill", 1_840, "Bills", "bank"); entry(3, "Groceries", 1_260, "Food", "cash")
        runBlocking {
            container.write { ok ->
                ok.budgets.setLimit(null, Money(500_000, inr))
                ok.templates.putSetting("first_week_started", (System.currentTimeMillis() - day).toString()) // inside the first week: the getting-started card shows
            }
        }
    }

    // ---- the screens --------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun homeWithEntriesABudgetAndTheGettingStartedCard() {
        seedHome(); launch()
        waitFor("first_week_card"); waitFor("budget_summary")
        check("Home (folded getting-started card)", "add")
        tap("first_week_toggle"); waitFor("first_week_item_BUDGET")
        check("Home (getting-started card open)", "add")
    }

    @Test
    fun theAddForm() {
        seedHome(); launch()
        tap("add"); waitFor("save")
        check("Add form", "amount")
        tap("amount_pad_toggle"); waitFor("number_pad")
        check("Add form with the number pad", "amount")
        compose.onNodeWithTag("amount", useUnmergedTree = true).performTextInput("123")
        tap("amount_pad_toggle")
        check("Add form with an amount", "save")
    }

    @Test
    fun search() {
        seedHome(); launch()
        tap("open_search"); waitFor("search_text")
        check("Search", "search_text")
        compose.onNodeWithTag("search_text", useUnmergedTree = true).performTextInput("tea")
        check("Search with text typed", "search_text")
    }

    @Test
    fun toReviewWithAnItem() {
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161", 5_000) }
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        check("To review", "inbox_back")
    }

    @Test
    fun accountsAndTheAccountDialog() {
        seedHome(); launch()
        compose.openMore(); tap("manage_accounts"); waitFor("accounts_list"); waitFor("accounts_stack")
        check("Accounts (stacked)", "accounts_stack")
        tap("accounts_stack"); waitFor("accounts_stack_close")
        check("Accounts (spread out)", "accounts_stack_close")
        tap("add_account"); waitFor("account_name")
        check("Account dialog (new account)", "account_name")
    }

    @Test
    fun aCreditCardDialog() {
        var s = java.time.LocalDate.now().minusDays(2)
        while (s.dayOfMonth > 28) s = s.minusDays(1)
        val terms = CardTerms("amex", statementDay = s.dayOfMonth, dueDay = (s.dayOfMonth + 9) % 28 + 1)
        runBlocking {
            container.write { ok ->
                ok.store.saveAccount(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, inr))
                ok.store.save(Expense(EntryId("old"), System.currentTimeMillis() - 30 * day, AccountId("amex"), Money(40_000, inr)), EntryDetails(merchant = "Phone"))
                ok.store.save(Expense(EntryId("new"), System.currentTimeMillis() - day, AccountId("amex"), Money(25_000, inr)), EntryDetails(merchant = "Shoes"))
                ok.cards.save(terms); ok.cards.setCreditLimit("amex", 400_000)
            }
        }
        launch(); compose.openMore(); tap("manage_accounts"); waitFor("accounts_stack"); tap("accounts_stack"); waitFor("accounts_stack_close")
        check("Accounts with a credit card", "accounts_stack_close")
        tap("account_Amex"); waitFor("card_summary_title")
        check("Credit card dialog", "card_summary_title")
    }

    @Test
    fun theBillItemsDialog() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        this.scenario = scenario
        val bill = "BLUE HERON PROVISIONS\nLemon Rice Pack 80.00\nAlmond Butter 250g 325.00\nGreen Tea Box 140.00\nSub Total 545.00\nGST 5% 27.25\nTOTAL 572.25"
        scenario.onActivity { activity ->
            activity.setContent { MaterialTheme { BillItemsDialog(bill, willSetAmount = true, photoUri = "content://photo/1", onClose = {}, onApply = {}, currency = inr, canAddEach = true, onCopyText = { true }) } }
        }
        waitFor("bill_items_list")
        check("Bill items dialog", "bill_items_apply")
    }

    /** Proof that the scan sees Compose nodes at all: a control that is plainly wrong (20 dp, no label) must be reported. If a library or Android update silently stopped the checks from seeing the nodes, this fails. */
    @Test
    fun theChecksReallyRunAndReportAKnownBadControl() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        this.scenario = scenario
        scenario.onActivity { activity ->
            activity.setContent { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(20.dp).background(Color.Red).clickable { }.testTag("bad_control")) }
        }
        waitFor("bad_control")
        runCatching { compose.onNodeWithTag("bad_control", useUnmergedTree = true).tryPerformAccessibilityChecks() } // way 1: reports nothing for it on this device (see the class comment)
        val errors = scan("calibration")
        assertTrue(errors.any { it.check == "SpeakableTextPresentCheck" }, "the scan did not report an unlabeled clickable: it does not see the Compose nodes. Reported: $errors")
        assertTrue(errors.any { it.check == "TouchTargetSizeCheck" }, "the scan did not report a 20 dp touch target. Reported: $errors")
        if (!collect) { // and an ERROR that is not in [known] fails the check, which is what makes the other tests a guard
            val failure = runCatching { check("calibration", "bad_control") }.exceptionOrNull()
            assertTrue(failure is AssertionError, "check() did not fail on an unknown ERROR: $failure")
        }
    }

    @Test
    fun transferCandidates() {
        runBlocking {
            container.write { ok ->
                ok.store.save(Expense(EntryId("atm"), System.currentTimeMillis() - 86_400_000L, AccountId("bank"), Money(300_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(300_000, Currency.INR)))), EntryDetails(merchant = "ATM cash"))
                ok.store.save(Expense(EntryId("self"), System.currentTimeMillis() - 2 * 86_400_000L, AccountId("bank"), Money(450_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(450_000, Currency.INR)))), EntryDetails(merchant = "Self transfer"))
            }
        }
        launch(); compose.openMore(); tap("open_transfer_candidates"); waitFor("transfer_candidates_row_atm"); waitFor("transfer_candidates_row_self")
        check("Transfer candidates", "transfer_candidates_list")
        tap("transfer_candidates_row_self"); tap("transfer_candidates_dest_self"); waitFor("transfer_candidates_to_self_Cash")
        check("Transfer candidates with a destination menu open", "transfer_candidates_to_self_Cash")
    }

    @Test
    fun more() {
        seedHome(); launch(); compose.openMore()
        check("More", "more_page")
    }

    companion object {
        /** Every finding seen in this process, by screen, check, type and element (the same finding is reported again on every action). */
        private val found = java.util.concurrent.ConcurrentHashMap<String, Finding>()
        /** How many times the framework ran its checks in this process, and how many results of each type it gave (to see that it ran at all). */
        private val calls = java.util.concurrent.atomic.AtomicInteger()
        private val types = java.util.concurrent.ConcurrentHashMap<String, Int>()
        private val errorsSeen = java.util.concurrent.atomic.AtomicInteger()

        /**
         * The ERRORs that are known and written up, with the proposed fix, in docs/ACCESSIBILITY_FINDINGS.md. They are the only ones suppressed; any other ERROR fails the test.
         * Empty now: the four touch-target findings it once listed (Search "Select all", the bill dialog's radio rows, the stacked account card edge, the getting-started rows) are fixed.
         * (WARNINGs never fail the test; the ones seen are triaged in the same document.)
         */
        private val known: List<Known> = emptyList()
    }
}
