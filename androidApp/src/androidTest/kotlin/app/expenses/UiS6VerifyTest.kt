package app.expenses

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.PermissionClaims
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
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI slice S6 (N-11, N-23): the "Verify it yourself" page, the backup status line at the top of Your data, and the tick beside "Backup saved". */
@RunWith(AndroidJUnit4::class)
class UiS6VerifyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val made = mutableListOf<File>()

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); shell("settings put system font_scale 1.0"); resetApp(); made.forEach { it.delete() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }

    /** Every piece of text under the node with [tag] (a card is a column of texts, so its own text is empty). */
    private fun texts(tag: String): List<String> {
        waitFor(tag)
        fun walk(x: SemanticsNode): List<String> = (x.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + x.children.flatMap { walk(it) }
        return walk(node(tag).fetchSemanticsNode())
    }
    private fun textOf(tag: String) = texts(tag).joinToString("")

    private fun openVerify() { launch(); compose.openMore(); tap("more_verify"); waitFor("verify_screen") }
    private fun openData() { launch(); compose.openMore(); tap("open_data"); waitFor("open_backup") }
    private fun saveOneEntry(id: String) = runBlocking { container.write { ok -> ok.store.save(Expense(EntryId(id), 1_000L, AccountId("cash"), Money(10_000L, Currency.INR)), EntryDetails(merchant = "Cafe $id")) } }
    private fun waitLine(accept: (String) -> Boolean) = compose.waitUntil(15_000) { has("backup_status_line") && accept(textOf("backup_status_line")) }

    @Test
    fun moreHasAVerifyRowThatOpensThePageAndBackReturnsToMoreAndFromThePrivacyPageBackReturnsThere() {
        launch(); compose.openMore(); tap("more_verify"); waitFor("verify_screen")
        for (tag in listOf("verify_not_have", "verify_ask", "verify_airplane", "verify_free", "verify_fraud")) assertTrue(has(tag), "$tag is on the page")
        tap("verify_back"); waitFor("more_page"); assertFalse(has("verify_screen"))
        // from the privacy page: the page opens, and Back returns to the privacy page it was opened from
        tap("open_privacy"); waitFor("privacy_internet"); tap("privacy_verify"); waitFor("verify_screen")
        tap("verify_back"); waitFor("privacy_internet"); assertFalse(has("verify_screen"))
        tap("privacy_back"); waitFor("more_page")
    }

    @Test
    fun theListOfPermissionsWeDoNotHaveIsNotEmptyAndNamesNothingTheManifestDeclares() {
        // the real package info, read here by the test itself, not through the app's own helper
        val real = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().filter { !it.startsWith(context.packageName + ".") }
        assertEquals(real, AndroidPlatform(context).declaredPermissions(), "the platform reads the same list the system shows")
        openVerify()
        waitFor("verify_not_have")
        val shown = PermissionClaims.SENSITIVE.filter { has("verify_not_${it.id}") }
        assertTrue(shown.isNotEmpty(), "at least one kind of access is listed as not asked for")
        for (c in shown) assertTrue(c.permissions.none { it in real }, "${c.label} is listed as not asked for, but the manifest declares ${c.permissions.filter { it in real }}")
        // and the other way round: a kind the manifest declares is never listed as one we do not have
        for (c in PermissionClaims.SENSITIVE.filter { c -> c.permissions.any { it in real } }) assertFalse(has("verify_not_${c.id}"), "${c.label} is declared, so it must not be listed")
        assertEquals(android.Manifest.permission.INTERNET !in real, has("verify_not_internet"), "Internet is listed exactly when the manifest lacks it")
        assertEquals(PermissionClaims.of(real).notRequested.size, shown.size)
        assertEquals(PermissionClaims.summary(PermissionClaims.of(real)), textOf("verify_summary"))
        // what it does ask for is listed with its reason (the same rows as the privacy page)
        assertTrue(texts("verify_ask").isNotEmpty())
    }

    @Test
    fun theAirplaneModeStepsTheFreeToUseLineAndTheFraudLineArePresentAndTheWordsAreCalm() {
        openVerify()
        val steps = texts("verify_airplane")
        assertTrue(steps.any { it.startsWith("1.") && it.contains("airplane mode") } && steps.any { it.startsWith("2.") } && steps.any { it.startsWith("3.") }, "three steps: $steps")
        assertEquals(listOf("Free to use", "Expenses is free. There are no ads, no subscriptions and no in-app purchases, and you do not need an account."), texts("verify_free"))
        // the app is free: nothing on the page speaks of paying for it
        for (pay in listOf("paid for", "paid features", "premium", "pro version", "price")) assertFalse(textOf("verify_free").contains(pay, ignoreCase = true), pay)
        assertTrue(texts("verify_fraud").any { it == "We never ask for your OTP, PIN or password. Neither does your bank by text or call." })
        val all = listOf("verify_not_have", "verify_ask", "verify_airplane", "verify_free", "verify_fraud").joinToString(" ") { textOf(it) }
        assertFalse(all.contains("!"), "no exclamation marks")
        for (scare in listOf("danger", "hack", "attack", "scam alert", "warning", "threat")) assertFalse(all.contains(scare, ignoreCase = true), scare)
    }

    @Test
    fun atTwiceTheFontSizeEverySectionIsThereWholeAndNoWiderThanTheScreen() {
        shell("settings put system font_scale 2.0")
        openVerify()
        val root = node("verify_screen").fetchSemanticsNode().size
        for (tag in listOf("verify_not_have", "verify_ask", "verify_airplane", "verify_free", "verify_fraud")) {
            runCatching { node(tag).performScrollTo() }
            val size = node(tag).fetchSemanticsNode().size
            assertTrue(size.width <= root.width && size.height > 0, "$tag fits the width at 200%: ${size.width} of ${root.width}")
        }
        tap("verify_back"); waitFor("more_page")
    }

    @Test
    fun theBackupStatusLineSaysNoBackupYetThenWhenTheLastOneWasMadeAndHowManyEntriesWereAddedSince() {
        openData()
        waitFor("backup_status_line")
        assertEquals("No backup yet", textOf("backup_status_line"))
        saveOneEntry("e0")
        val file = File(context.cacheDir, "s6-status.expbak").also { made += it }
        viewModel().createBackup(Uri.fromFile(file).toString(), null)
        // the confirmation has its tick, and the line now says when
        waitFor("backup_done_check")
        compose.waitUntil(15_000) { has("data_status") && textOf("data_status").startsWith("Backup saved and checked") }
        waitLine { it.startsWith("Last backup: just now") }
        assertEquals("Last backup: just now \u00B7 nothing new since", textOf("backup_status_line"))
        saveOneEntry("e1"); saveOneEntry("e2")
        viewModel().loadBackupFiles()
        waitLine { it.endsWith("2 entries added since") }
        assertEquals("Last backup: just now \u00B7 2 entries added since", textOf("backup_status_line"))
    }

    @Test
    fun noTickIsShownBeforeABackupIsMadeAndTheLineHasNoAmountsOrWarningColourWords() {
        openData()
        waitFor("backup_status_line")
        assertFalse(has("backup_done_check"), "no tick before a backup")
        viewModel().setHideAmounts(true)
        val line = textOf("backup_status_line")
        assertFalse(line.contains("\u20B9") || line.contains("\u2022"), line)
    }
}
