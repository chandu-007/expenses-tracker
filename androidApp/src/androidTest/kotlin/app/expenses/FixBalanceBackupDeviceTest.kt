package app.expenses

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.BalanceFixEntry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.BackupFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A fixed balance in the real backup files on the real encrypted database: plain (version 1), sealed without files (`EXPENC1`, written by the phone's own cipher) and sealed with files
 * (`EXPENC2`), restored into a freshly started app, whose Cash and Bank are the two accounts the app makes by itself. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class FixBalanceBackupDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val actions get() = DataActions(context, container)
    private val inr = Currency.INR
    private val made = mutableListOf<File>()
    private val attachmentDir = File(context.filesDir, "attachments")

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }; attachmentDir.deleteRecursively() }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { resetApp(); made.forEach { it.delete() } }

    private fun target(name: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { made += it })
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun balances() = db { ok -> ok.store.balances().mapKeys { it.key.value }.mapValues { it.value.minorUnits } }

    /** The default Bank (25,000, fixed to 24,100.50), a card that owes 3,000 by its entries fixed to 5,500, and cash fixed to 850: every kind of change. */
    private fun seedFixed() = db { ok ->
        ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 25_000_00))
        ok.store.saveAccount(Account(AccountId("card1"), "Axis Card", AccountType.CREDIT_CARD, inr))
        ok.store.save(Expense(EntryId("c1"), 1_000L, AccountId("card1"), Money(3_000_00, inr)))
        ok.balanceFixes.fix("bank", 24_100_50, inr, 1_700_000_000_000)
        ok.balanceFixes.fix("card1", -5_500_00, inr, 1_700_000_100_000)
        ok.balanceFixes.fix("cash", 850_00, inr, 1_700_000_200_000)
    }

    private fun expectedBalances() = mapOf("bank" to 24_100_50L, "card1" to -5_500_00L, "cash" to 850_00L)
    private fun lines() = db { ok -> ok.balanceFixes.allHistory() }
    private val expectedLines = mapOf(
        "bank" to listOf(BalanceFixEntry(1_700_000_000_000, 25_000_00, 24_100_50)),
        "card1" to listOf(BalanceFixEntry(1_700_000_100_000, -3_000_00, -5_500_00)),
        "cash" to listOf(BalanceFixEntry(1_700_000_200_000, 0, 850_00)),
    )

    private fun backupAndRestore(name: String, passphrase: String?) {
        val uri = target(name)
        val saved = runBlocking { actions.backup(uri, passphrase) }
        assertEquals(passphrase != null, saved.encrypted)
        assertEquals(expectedBalances(), balances().filterKeys { it in expectedBalances() })
        resetApp()
        assertEquals(0L, balances().getValue("bank"), "a freshly started app has an empty Bank")
        val ready = runBlocking { actions.open(uri, passphrase) } as DataActions.Opened.Ready
        runBlocking { actions.restore(ready) }
        assertEquals(expectedBalances(), balances().filterKeys { it in expectedBalances() }, "every fixed balance came back, the default Bank's too")
        assertEquals(expectedLines, lines(), "and the lines of the fixes")
    }

    @Test
    fun aPlainBackupRestoresTheFixedBalancesAndTheirLinesIntoAFreshApp() {
        seedFixed(); backupAndRestore("fixed-plain.expbak", null)
        val raw = File(target("fixed-plain.expbak").path!!).readBytes()
        assertEquals(1, BackupFile.decode(raw).first.version)
        assertTrue(BackupFile.decode(raw).second.balanceFixes.size == 3)
    }

    @Test
    fun aBackupSealedWithoutFilesIsStillTheOldEXPENC1AndCarriesTheFixes() {
        seedFixed()
        db { it.templates.putSetting(BackupKeys.INCLUDE_FILES, "0") }
        val uri = target("fixed-sealed1.expbak")
        runBlocking { actions.backup(uri, "correct horse") }
        assertEquals("EXPENC1\n", File(uri.path!!).readBytes().copyOf(8).decodeToString(), "sealed exactly as before, so every older version of the app opens it")
        resetApp()
        runBlocking { actions.restore(runBlocking { actions.open(uri, "correct horse") } as DataActions.Opened.Ready) }
        assertEquals(expectedBalances(), balances().filterKeys { it in expectedBalances() })
        assertEquals(expectedLines, lines())
    }

    @Test
    fun aBackupSealedWithFilesIsEXPENC2AndCarriesTheFixes() {
        seedFixed()
        val bytes = ByteArray(5_000) { (it * 31).toByte() }
        val src = Uri.fromFile(File(context.cacheDir, "fixed-src.bin").also { it.writeBytes(bytes); made += it })
        val copied = (AndroidPlatform(context)).attachmentFiles.copyIn("att-x", src.toString())
        db { ok -> ok.attachments.add("att-x", "c1", "bill.jpg", "image/jpeg", copied.sizeBytes, copied.sha256) }
        val uri = target("fixed-sealed2.expbak")
        runBlocking { actions.backup(uri, "correct horse") }
        assertEquals("EXPENC2\n", File(uri.path!!).readBytes().copyOf(8).decodeToString())
        resetApp()
        runBlocking { actions.restore(runBlocking { actions.open(uri, "correct horse") } as DataActions.Opened.Ready) }
        assertEquals(expectedBalances(), balances().filterKeys { it in expectedBalances() })
        assertEquals(expectedLines, lines())
    }

    @Test
    fun aBackupOfALedgerWithNoFixesIsTheFileItAlwaysWasAndItsDefaultBankKeepsItsOpeningBalance() {
        db { ok -> ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 12_000_00)) }
        val uri = target("no-fixes.expbak")
        runBlocking { actions.backup(uri, null) }
        val raw = File(uri.path!!).readBytes()
        assertTrue("balanceFixes" !in raw.decodeToString(), "no key when there is nothing to say")
        resetApp()
        runBlocking { actions.restore(runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready) }
        assertEquals(12_000_00L, balances().getValue("bank"), "the opening balance of the default Bank comes back on a fresh app")
        assertEquals(emptyMap(), lines())
    }
}
