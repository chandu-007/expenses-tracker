package app.expenses

import android.content.Intent
import android.net.Uri
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
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AttachmentsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val made = mutableListOf<File>()
    private val files = AttachmentFiles(context)

    private fun resetApp() { runBlocking { container.startFresh() }; File(context.filesDir, "attachments").deleteRecursively() }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp(); made.forEach { it.delete() } }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun vm(): LedgerViewModel { var v: LedgerViewModel? = null; scenario!!.onActivity { v = ViewModelProvider(it)[LedgerViewModel::class.java] }; return v!! }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun source(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(context.cacheDir, name).also { it.writeBytes(bytes); made += it })
    private fun seed() = db { ok -> ok.store.save(Expense(EntryId("e"), System.currentTimeMillis(), AccountId("cash"), Money(10_000, Currency.INR)), EntryDetails(merchant = "Cafe")) }
    private fun recorded() = db { it.attachments.list("e") }
    private fun onDisk() = File(context.filesDir, "attachments").list().orEmpty().toSet()

    @Test
    fun aFileIsCopiedInRecordedShownInTheEditorReadableThroughTheProviderAndRemovedWithItsFile() {
        seed(); launch(); tap("row_Cafe"); waitFor("attachment_add")
        val bytes = ByteArray(5_000) { (it * 7).toByte() }
        vm().addAttachment("e", source("bill.pdf", bytes).toString())
        waitFor("attachment_bill.pdf")
        val a = recorded().single()
        assertEquals("bill.pdf", a.name); assertEquals(5_000L, a.sizeBytes)
        assertEquals(bytes.toList(), files.file(a.id).readBytes().toList(), "the stored copy is byte for byte the original")
        assertEquals(app.expenses.core.storage.Sha256.hex(bytes), a.sha256)
        assertEquals(bytes.toList(), context.contentResolver.openInputStream(files.shareUri(a.id))!!.use { it.readBytes() }.toList(), "a viewer reads it through the provider")
        assertEquals(setOf(a.id), onDisk())
        tap("attachment_delete_bill.pdf"); tap("attachment_remove_ok"); waitGone("attachment_bill.pdf")
        assertEquals(emptyList(), recorded()); assertEquals(emptySet(), onDisk(), "the file is deleted too")
    }

    @Test
    fun anOversizedOrEmptyFileIsRefusedWithAMessageAndLeavesNoFileOrRecord() {
        seed(); launch(); tap("row_Cafe"); waitFor("attachment_add")
        vm().addAttachment("e", source("huge.bin", ByteArray(15 * 1024 * 1024 + 1)).toString())
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("attachment_problem") and hasText("over 15 MB", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        vm().addAttachment("e", source("empty.txt", ByteArray(0)).toString())
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("attachment_problem") and hasText("That file is empty", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(emptyList(), recorded()); assertEquals(emptySet(), onDisk())
    }

    @Test
    fun theEleventhFileIsRefusedAndItsCopyIsRemoved() {
        seed(); launch(); tap("row_Cafe"); waitFor("attachment_add")
        repeat(10) { i -> vm().addAttachment("e", source("f$i.txt", byteArrayOf(1, 2, 3)).toString()); compose.waitUntil(10_000) { recorded().size == i + 1 } }
        vm().addAttachment("e", source("one-too-many.txt", byteArrayOf(9)).toString())
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("attachment_problem") and hasText("up to 10 attachments", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(10, recorded().size); assertEquals(10, onDisk().size, "no stray copy of the refused file")
    }

    @Test
    fun aNewEntrySaysToSaveFirstAndTheSweepRemovesOnlyFilesWithNoRecord() {
        launch(); tap("add"); waitFor("attachments_hint")
        assertTrue(!has("attachment_add"))
        seed()
        val keep = "keep"; val orphan = "orphan"
        db { ok -> ok.attachments.add(keep, "e", "k.txt", "text/plain", 1, "h") }
        File(context.filesDir, "attachments").mkdirs(); files.file(keep).writeText("k"); files.file(orphan).writeText("o")
        assertEquals(1, db { ok -> files.sweep(ok.attachments.allIds()) })
        assertEquals(setOf(keep), onDisk())
    }

    @Test
    fun aSourceThatNeverEndsIsCutOffAtTheLimitWithItsPartialCopyDeleted() {
        val endless = object : java.io.InputStream() { var served = 0L; override fun read() = 1.also { served++ }; override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; java.util.Arrays.fill(b, off, off + len, 1); return len } }
        assertTrue(assertFailsWith<IllegalArgumentException> { files.copyStream("endless", endless) }.message!!.contains("15 MB"))
        assertTrue(endless.served < 16L * 1024 * 1024 + 64 * 1024, "stopped reading right after the limit: ${endless.served}")
        assertEquals(false, files.file("endless").exists(), "no partial copy is left")
    }

    @Test
    fun anUnreadableSourceFailsCleanlyAndLeavesNoFile() {
        val broken = object : java.io.InputStream() { override fun read() = throw java.io.IOException("disk gone") }
        assertTrue(assertFailsWith<IllegalArgumentException> { files.copyStream("broken", broken) }.message!!.contains("disk gone"))
        assertEquals(false, files.file("broken").exists())
    }
}
