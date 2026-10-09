package app.expenses

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.storage.bulkLoad
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread
import kotlin.test.assertTrue

/**
 * Loading more rows while the list is being rebuilt by a save used to throw ConcurrentModificationException on a background thread, which
 * ends the app's process. This hammers both at once; the process surviving to the final assertion is the pass.
 */
@RunWith(AndroidJUnit4::class)
class PagingRaceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    @Test
    fun loadingMoreRowsWhileSavesRebuildTheListNeverCrashesTheApp() {
        runBlocking {
            container.write { ok ->
                ok.db.bulkLoad(ok.driver) {
                    for (i in 0 until 4_000) ok.db.schemaQueries.insertEntry("e$i", "EXPENSE", 1_300_000_000_000L + i * 60_000L, "bank", null, 100, "INR", null, "Shop", null, "manual", null)
                }
            }
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        var vm: LedgerViewModel? = null
        scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }
        val model = vm!!
        Thread.sleep(3_000)
        val pagers = thread { repeat(400) { model.loadMore(); Thread.sleep(1) } }
        val savers = thread { repeat(150) { i -> model.saveEntry(EntryDraft(null, DraftKind.EXPENSE, "5${i % 90}", "", emptyList(), "bank", "", "race $i", System.currentTimeMillis()), false); Thread.sleep(3) } }
        pagers.join(); savers.join()
        Thread.sleep(3_000)
        assertTrue(true, "the process is still here")
    }
}
