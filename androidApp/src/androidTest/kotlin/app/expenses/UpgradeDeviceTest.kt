package app.expenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.EncryptedDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An install made by an earlier build (accounts without opening balances, no tags table) must open, keep its data and work. */
@RunWith(AndroidJUnit4::class)
class UpgradeDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() = resetApp()

    @Test
    fun anEncryptedDatabaseFromBeforeTheNewColumnsOpensKeepsItsAccountsAndGainsTheNewShape() {
        runBlocking { container.write { it.db.schemaQueries.selectAccounts().executeAsList().size } } // creates the encrypted database with the default accounts
        runBlocking { container.close() }
        EncryptedDriver.create(context).let { d ->
            d.execute(null, "ALTER TABLE account DROP COLUMN opening_minor", 0)
            d.execute(null, "ALTER TABLE account DROP COLUMN archived", 0)
            d.execute(null, "DROP TABLE entry_tag", 0)
            d.close()
        }
        val names = runBlocking { container.write { ok -> ok.db.schemaQueries.selectAccounts().executeAsList().map { it.name } } }!!
        assertEquals(listOf("Bank", "Cash"), names.sorted(), "the accounts that were there are still there")
        val ok = runBlocking { container.write { ok -> ok.store.balances().size to ok.store.tagsInUse().size } }!!
        assertTrue(ok.first == 2 && ok.second == 0, "balances (which need the new columns) and tags work")
    }
}
