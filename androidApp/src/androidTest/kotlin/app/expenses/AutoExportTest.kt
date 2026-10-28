package app.expenses

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.AutoCopyProtection
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A folder on the app's own storage standing in for the system's document provider; [failWrites] and [corrupt] simulate trouble. */
private class DirFolder(val dir: File, var failWrites: Boolean = false, var corrupt: Boolean = false) : ExportFolder {
    override fun names() = dir.list().orEmpty().toList()
    override fun write(name: String, mime: String, bytes: ByteArray): String? {
        if (failWrites) return null
        var n = name; var i = 0
        while (File(dir, n).exists()) { i++; n = name.substringBeforeLast('.') + " ($i)." + name.substringAfterLast('.') }
        File(dir, n).writeBytes(if (corrupt) bytes.copyOf(bytes.size - 1) else bytes); return n
    }
    override fun read(name: String) = File(dir, name).takeIf { it.exists() }?.readBytes()
    override fun delete(name: String) = File(dir, name).delete()
}

@RunWith(AndroidJUnit4::class)
class AutoExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val dir = File(context.cacheDir, "auto-export-test")
    private val day = 86_400_000L
    private val t0 = 1_900_000_000_000L

    @Before fun fresh() { runBlocking { container.startFresh() }; dir.deleteRecursively(); dir.mkdirs() }
    @After fun cleanUp() { runBlocking { container.startFresh() }; dir.deleteRecursively() }

    private fun setup(freq: AutoExportFrequency, folder: String? = "content://tree/x") = runBlocking {
        container.write { ok ->
            ok.store.save(Expense(EntryId("e"), t0, AccountId("cash"), Money(12_300, Currency.INR)), EntryDetails(merchant = "Cafe"))
            ok.templates.putSetting(AutoExport.S_FREQ, freq.name); folder?.let { ok.templates.putSetting(AutoExport.S_FOLDER, it) }
        }
    }
    private fun setting(k: String) = runBlocking { container.write { it.templates.setting(k) } }
    private fun run(f: ExportFolder?, now: Long, force: Boolean = false) = runBlocking { AutoExport.run(container, f, now, force) }

    @Test
    fun aDueRunWritesAVerifiedRestorableBackupAndAWorkbookAndRecordsWhenItWorked() {
        setup(AutoExportFrequency.WEEKLY); val f = DirFolder(dir)
        val r = run(f, t0)
        assertTrue(r.ran && r.ok, r.text)
        assertEquals(2, f.names().size)
        val backup = File(dir, f.names().single { it.endsWith(".expbak") }).readBytes()
        assertEquals(1, BackupFile.decode(backup).second.entries.size, "the backup decodes, checksum and all")
        assertTrue(File(dir, f.names().single { it.endsWith(".xlsx") }).readBytes().take(2) == listOf('P'.code.toByte(), 'K'.code.toByte()), "the workbook is a zip")
        assertEquals(t0.toString(), setting(AutoExport.S_LAST_AT)); assertTrue(setting(AutoExport.S_STATUS)!!.startsWith("ok|"))
    }

    @Test
    fun nothingHappensWhenOffOrNotYetDueAndItRunsAgainOnceTheIntervalHasPassed() {
        setup(AutoExportFrequency.OFF); val f = DirFolder(dir)
        assertEquals(false, run(f, t0).ran); assertEquals(0, f.names().size)
        runBlocking { container.write { it.templates.putSetting(AutoExport.S_FREQ, AutoExportFrequency.WEEKLY.name) } }
        assertTrue(run(f, t0).ran)
        assertEquals(false, run(f, t0 + 3 * day).ran, "three days into a weekly schedule")
        assertTrue(run(f, t0 + 7 * day).ran)
        assertEquals(4, f.names().size)
    }

    @Test
    fun runningTwiceOnTheSameDayLeavesOneCopyOfEachNotTwo() {
        setup(AutoExportFrequency.DAILY); val f = DirFolder(dir)
        run(f, t0); run(f, t0 + 1_000, force = true)
        assertEquals(2, f.names().size, f.names().toString())
    }

    @Test
    fun onlyTheNewestSevenOfEachKindAreKeptAndTheUsersOwnFilesAreNeverTouched() {
        setup(AutoExportFrequency.DAILY); val f = DirFolder(dir)
        File(dir, "my-tax-notes.txt").writeText("keep me"); File(dir, "expenses-backup-final.expbak").writeText("keep me too")
        repeat(10) { run(f, t0 + it * day) }
        assertEquals(7, f.names().count { it.startsWith("expenses-backup-2") && it.endsWith(".expbak") })
        assertEquals(7, f.names().count { it.startsWith("expenses-2") && it.endsWith(".xlsx") })
        assertTrue(File(dir, "my-tax-notes.txt").exists() && File(dir, "expenses-backup-final.expbak").exists())
    }

    @Test
    fun aFailedOrCorruptWriteIsReportedKeepsTheOldCopiesAndDoesNotCountAsDone() {
        setup(AutoExportFrequency.DAILY); val f = DirFolder(dir)
        assertTrue(run(f, t0).ok); val before = f.names().sorted()
        f.corrupt = true
        val bad = run(f, t0 + day)
        assertTrue(bad.ran && !bad.ok && bad.text.contains("not saved correctly"), bad.text)
        assertEquals(t0.toString(), setting(AutoExport.S_LAST_AT), "the failed run is not recorded as the last success")
        assertTrue(setting(AutoExport.S_STATUS)!!.startsWith("fail|"))
        assertTrue(f.names().containsAll(before), "the earlier good copies are still there")
        f.corrupt = false; f.failWrites = true
        assertTrue(!run(f, t0 + 2 * day).ok)
        f.failWrites = false
        assertTrue(run(f, t0 + 3 * day).ok, "and it tries again next time")
    }

    @Test
    fun withoutAChosenFolderItSaysSoInsteadOfDoingNothingSilently() {
        setup(AutoExportFrequency.WEEKLY, folder = null)
        val r = run(null, t0)
        assertTrue(r.ran && !r.ok && r.text.contains("Choose a folder"))
        assertNotNull(setting(AutoExport.S_STATUS))
    }

    // ---- protecting the copies with a passphrase ----

    private val platform get() = (context.applicationContext as ExpensesApp).platform
    private val pass = "correct horse battery"
    private fun protect(passphrase: String = pass) = runBlocking { AutoCopyControls.turnOn(platform, container, passphrase) }
    private fun backupFile(f: DirFolder, which: Int = 0) = File(dir, f.names().filter { it.endsWith(".expbak") }.sorted()[which])
    private fun open(file: File, passphrase: String?) = runBlocking { DataActions(context, container).open(Uri.fromFile(file), passphrase) }

    @Test
    fun aProtectedCopyIsSealedOpensWithJustThePassphraseThroughOpenBackupAndShowsNothingWithout() {
        setup(AutoExportFrequency.WEEKLY); val f = DirFolder(dir)
        protect()
        assertEquals("1", setting(AutoExportKeys.PROTECT))
        val stored = platform.secretStore!!.get(AutoCopyProtection.SECRET)!!
        assertTrue(String(stored, Charsets.ISO_8859_1).indexOf(pass) < 0 && stored.size == 57, "what is kept is the 57-byte key record, not the passphrase")
        val r = run(f, t0, force = true)
        assertTrue(r.ran && r.ok, r.text); assertTrue(r.text.contains("protected backup"), r.text)
        assertEquals(1, f.names().size, "a protected run makes no Excel file: ${f.names()}")
        val file = backupFile(f)
        val bytes = file.readBytes()
        assertEquals("EXPENC3\n", String(bytes.copyOf(8), Charsets.ISO_8859_1))
        assertTrue(!String(bytes, Charsets.ISO_8859_1).contains("Cafe") && !String(bytes, Charsets.ISO_8859_1).contains("expenses-backup"), "neither the entries nor the format line are readable")
        // restored the way "Choose a backup file" does it: it asks for the passphrase, a wrong one is refused, the right one opens it, with nothing from the key kept on the phone
        assertTrue(open(file, null) is DataActions.Opened.NeedsPassphrase)
        assertTrue(assertFailsWith<BackupException> { open(file, "wrong passphrase!") }.message!!.contains("passphrase is wrong"))
        val ready = open(file, pass) as DataActions.Opened.Ready
        assertEquals(1, ready.data.entries.size); assertEquals("Cafe", ready.data.entries.single().merchant)
        // the header holds the salt and rounds of the kept key, which is what lets the passphrase alone do it
        val kept = AutoCopyProtection.load(platform.secretStore)!!
        assertEquals(kept.salt.toList(), bytes.copyOfRange(12, 28).toList())
        assertEquals(kept.iterations, java.nio.ByteBuffer.wrap(bytes, 8, 4).int)
        assertEquals(t0.toString(), setting(AutoExport.S_LAST_AT)); assertTrue(setting(AutoExport.S_STATUS)!!.startsWith("ok|"))
    }

    @Test
    fun turningProtectionOffWritesPlainCopiesAgainAndLeavesTheSealedOnesSealed() {
        setup(AutoExportFrequency.DAILY); val f = DirFolder(dir)
        File(dir, "expenses-2000-01-01.xlsx").writeText("an Excel file from before")
        protect()
        val sealed = run(f, t0)
        assertTrue(sealed.ok && sealed.text.contains("An Excel file from before is still in the folder"), sealed.text)
        assertTrue(File(dir, "expenses-2000-01-01.xlsx").exists(), "a protected run does not remove the Excel files that were there")
        runBlocking { AutoCopyControls.turnOff(platform, container) }
        assertEquals("0", setting(AutoExportKeys.PROTECT)); assertEquals(null, platform.secretStore!!.get(AutoCopyProtection.SECRET), "the key is forgotten")
        val plain = run(f, t0 + day)
        assertTrue(plain.ok && plain.text.contains("and an Excel file"), plain.text)
        assertEquals(setOf("EXPENC3\n", "{\"format\""), (0..1).map { String(backupFile(f, it).readBytes().copyOf(if (it == 0) 8 else 9), Charsets.ISO_8859_1) }.toSet(), "one sealed copy, one plain")
        assertTrue(open(backupFile(f, 0), pass) is DataActions.Opened.Ready, "the copy made while it was on still opens with its passphrase")
    }

    @Test
    fun aKeyThatCannotBeReadWritesNothingPrunesNothingAndSaysSoNeverAPlainCopy() {
        setup(AutoExportFrequency.DAILY); val f = DirFolder(dir)
        protect()
        // eight copies from earlier days, so that any run that did succeed would have to prune
        repeat(8) { File(dir, "expenses-backup-2001-01-0${it + 1}.expbak").writeText("older copy $it"); File(dir, "expenses-2001-01-0${it + 1}.xlsx").writeText("older sheet $it") }
        val before = f.names().sorted(); val lastBefore = setting(AutoExport.S_LAST_AT)
        // the key is gone: the Keystore key that wraps it is deleted, as a lock-screen change or a restore to a new phone can do
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("expenses_secrets_wrap_key")
        val r = run(f, t0, force = true)
        assertTrue(r.ran && !r.ok && r.text == AutoExportKeys.KEY_NOT_FOUND, r.text)
        assertEquals(before, f.names().sorted(), "nothing was written and nothing was removed")
        assertEquals(lastBefore, setting(AutoExport.S_LAST_AT), "not counted as a success")
        assertEquals("fail|" + AutoExportKeys.KEY_NOT_FOUND, setting(AutoExport.S_STATUS))
        // the same with the blob gone
        runBlocking { AutoCopyControls.turnOn(platform, container, pass) }
        File(context.noBackupFilesDir, "secrets").deleteRecursively()
        val r2 = run(f, t0 + day)
        assertTrue(!r2.ok && r2.text == AutoExportKeys.KEY_NOT_FOUND, r2.text)
        assertEquals(before, f.names().sorted())
        // setting the passphrase again puts it right, and the failure notice goes
        protect()
        assertEquals("", (setting(AutoExport.S_STATUS) ?: "").substringAfter('|'), "the notice about the old key is cleared")
        assertTrue(run(f, t0 + 2 * day).ok)
        assertEquals(7, f.names().count { it.startsWith("expenses-backup-") && it.endsWith(".expbak") }, "and now the run prunes as usual")
    }

    @Test
    fun changingThePassphraseMakesANewSaltAndKeyNextCopiesUseItAndOldCopiesKeepTheOldPassphrase() {
        setup(AutoExportFrequency.DAILY); val f = DirFolder(dir)
        protect()
        val oldKey = AutoCopyProtection.load(platform.secretStore)!!
        assertTrue(run(f, t0).ok)
        val newPass = "another good passphrase"
        protect(newPass)
        val newKey = AutoCopyProtection.load(platform.secretStore)!!
        assertNotEquals(oldKey.salt.toList(), newKey.salt.toList()); assertNotEquals(oldKey.key.toList(), newKey.key.toList())
        assertTrue(run(f, t0 + day).ok)
        val old = backupFile(f, 0); val fresh = backupFile(f, 1)
        assertNotEquals(old.readBytes().copyOfRange(12, 28).toList(), fresh.readBytes().copyOfRange(12, 28).toList())
        assertTrue(open(old, pass) is DataActions.Opened.Ready); assertFailsWith<BackupException> { open(old, newPass) }
        assertTrue(open(fresh, newPass) is DataActions.Opened.Ready); assertFailsWith<BackupException> { open(fresh, pass) }
    }

    @Test
    fun aShortPassphraseIsRefusedAndChangesNothing() {
        setup(AutoExportFrequency.DAILY)
        assertFailsWith<BackupException> { protect("short") }
        assertEquals(null, setting(AutoExportKeys.PROTECT)); assertEquals(null, platform.secretStore!!.get(AutoCopyProtection.SECRET))
    }

    @Test
    fun theOldExcelFilesAreDeletedOnlyWhenAskedAndOnlyTheAppsOwnNamesAndOnlyWhileProtected() {
        setup(AutoExportFrequency.DAILY); val f = DirFolder(dir)
        for (n in listOf("expenses-2000-01-01.xlsx", "expenses-2000-01-02 (1).xlsx", "my-notes.xlsx", "expenses-final.xlsx", "expenses-backup-2000-01-01.expbak")) File(dir, n).writeText("x")
        protect()
        val r = run(f, t0, force = true)
        assertTrue(r.ok && r.text.contains("2 Excel files from before are still in the folder"), r.text)
        assertEquals("2", setting(AutoExportKeys.OLD_EXCEL), "the screen is told how many")
        assertTrue(File(dir, "expenses-2000-01-01.xlsx").exists(), "a run never deletes them itself")
        val d = runBlocking { AutoExportRunner.deleteOldExcel(container, f) }
        assertTrue(d.ran && d.ok && d.text == "Deleted 2 Excel files from before protection.", d.text)
        assertEquals(listOf("my-notes.xlsx", "expenses-final.xlsx").sorted(), f.names().filter { it.endsWith(".xlsx") }.sorted(), "the person's own files stay")
        assertTrue(f.names().contains("expenses-backup-2000-01-01.expbak"))
        assertEquals("0", setting(AutoExportKeys.OLD_EXCEL)); assertEquals("ok|Deleted 2 Excel files from before protection.", setting(AutoExport.S_STATUS))
        // with protection off nothing is deleted at all
        runBlocking { AutoCopyControls.turnOff(platform, container) }
        File(dir, "expenses-2000-01-03.xlsx").writeText("x")
        val off = runBlocking { AutoExportRunner.deleteOldExcel(container, f) }
        assertTrue(!off.ran && File(dir, "expenses-2000-01-03.xlsx").exists(), off.text)
    }
}
