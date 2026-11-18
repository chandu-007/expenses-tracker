package app.expenses

import android.content.Context
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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The install's node id file (`no_backup/node_id`). An empty one, which a phone that loses power right after the first launch can leave (the file made, its bytes never
 * on the disk), stopped the app at start-up on every launch: `HlcClock` rejected the empty id. Seen on the API 34 emulator after it was killed right after a test run.
 */
@RunWith(AndroidJUnit4::class)
class NodeIdFileTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as ExpensesApp
    private val file get() = File(context.noBackupFilesDir, "node_id")
    private val tmp get() = File(context.noBackupFilesDir, "node_id.tmp")
    private var before: ByteArray? = null

    @Before fun keep() { before = file.takeIf { it.exists() }?.readBytes() }
    @After fun restore() { before?.let { file.writeBytes(it) } ?: file.delete(); runBlocking { app.container.startFresh() } }

    private val hexId = Regex("[0-9a-f]{8}")

    @Test
    fun anEmptyNodeIdFileGetsANewIdAndTheLedgerOpensAndStampsWithIt() {
        file.writeBytes(ByteArray(0))
        val opened = runBlocking { app.container.startFresh() } // before the fix: IllegalArgumentException "node must be non-empty" from HlcClock
        assertIs<AppContainer.Opened.Ok>(opened)
        val id = file.readText()
        assertTrue(hexId.matches(id), "a new id is stored: '$id'")
        assertEquals(id, app.platform.nodeId(), "and read back")
        assertFalse(tmp.exists(), "no temporary file is left")
        val stamps = runBlocking {
            app.container.write { ok ->
                ok.store.save(Expense(EntryId("node-1"), 1_000L, AccountId("cash"), Money(10_000L, Currency.INR)), EntryDetails(merchant = "Cafe"))
                ok.db.schemaQueries.opsOfRow("entry", "node-1").executeAsList().map { it.hlc }
            }
        }.orEmpty()
        assertTrue(stamps.isNotEmpty() && stamps.all { it.endsWith("-$id") }, "the entry's changes carry the new id: $stamps")
    }

    @Test
    fun garbageInTheFileIsReplacedButAUsableIdIsKeptAsItIs() {
        file.writeBytes(ByteArray(8)) // zero bytes: what some file systems leave after a power cut
        val made = app.platform.nodeId()
        assertTrue(hexId.matches(made) && file.readText() == made, "replaced: '$made'")

        file.writeText("a1b2c3d4\n") // a usable id with a stray newline
        assertEquals("a1b2c3d4", app.platform.nodeId())
        assertEquals("a1b2c3d4\n", file.readText(), "a usable id is never rewritten")
        assertIs<AppContainer.Opened.Ok>(runBlocking { app.container.startFresh() })
        assertEquals("a1b2c3d4\n", file.readText(), "starting fresh keeps the id")
    }

    @Test
    fun manyFirstCallsAtOnceAllGetTheOneIdThatIsStored() {
        repeat(20) { round ->
            file.delete()
            val n = 8
            val barrier = CyclicBarrier(n)
            val ids = arrayOfNulls<String>(n)
            // a platform each, as the app makes more than one (the container's and the data actions' own)
            (0 until n).map { i -> thread { val p = AndroidPlatform(context); barrier.await(); ids[i] = p.nodeId() } }.forEach { it.join() }
            assertEquals(1, ids.toSet().size, "round $round: one id for every caller: ${ids.toList()}")
            assertEquals(ids[0], file.readText(), "round $round: the id that is stored")
            assertTrue(hexId.matches(ids[0]!!))
        }
    }
}
