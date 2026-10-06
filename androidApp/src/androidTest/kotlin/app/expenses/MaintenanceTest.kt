package app.expenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.search
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Index maintenance through the app's own container, on the encrypted database. */
@RunWith(AndroidJUnit4::class)
class MaintenanceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()
    @After fun cleanUp() = resetApp()

    @Test
    fun maintainingMergesTheIndexWithoutChangingAnySearchAnswer() {
        val queries = listOf("swiggy", "zomato 4", "mazon", "nomatch")
        runBlocking {
            container.write { ok ->
                repeat(300) { i ->
                    ok.db.schemaQueries.insertEntry("e$i", "EXPENSE", i.toLong(), "cash", null, 100, "INR", null, listOf("swiggy", "zomato", "amazon")[i % 3] + " ${i % 50}", null, "manual", null)
                }
            }
        }
        val before = runBlocking { container.write { ok -> queries.map { q -> ok.db.search(q, 1000).map { it.id } } } }!!
        val steps = runBlocking { container.maintain(budgetMillis = 5_000) }
        assertTrue(steps > 0, "300 separate commits leave segments to merge")
        assertEquals(0, runBlocking { container.maintain(budgetMillis = 5_000) }, "a second pass has nothing left to do")
        val after = runBlocking { container.write { ok -> queries.map { q -> ok.db.search(q, 1000).map { it.id } } } }!!
        assertEquals(before, after)
        assertTrue(before.any { it.isNotEmpty() })
    }

    @Test
    fun theTuningIsInPlaceAfterTheAppOpensTheDatabase() {
        val config: Map<String, Long>? = runBlocking {
            container.write { ok ->
                ok.driver.executeQuery(null, "SELECT k, v FROM entry_fts_config WHERE k IN ('automerge','crisismerge') ORDER BY k", { c ->
                    val m = LinkedHashMap<String, Long>(); while (c.next().value) m[c.getString(0)!!] = c.getLong(1)!!
                    app.cash.sqldelight.db.QueryResult.Value(m)
                }, 0).value
            }
        }
        assertEquals(mapOf<String, Long>("automerge" to 0L, "crisismerge" to 1000L), config)
    }
}
