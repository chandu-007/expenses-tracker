package app.expenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
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
}
