package app.expenses

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.db.ExpensesDb
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-encrypted.db"
    private var driver: SqlDriver? = null

    @Before fun clean() {
        context.deleteDatabase(name)
    }

    @After fun close() {
        driver?.close()
        context.deleteDatabase(name)
    }

    private fun open(): ExpensesDb {
        driver = EncryptedDriver.create(context, name)
        return ExpensesDb(driver!!)
    }

    private fun ExpensesDb.add(id: String, merchant: String?, source: String = "manual", ref: String? = null) =
        schemaQueries.insertEntry(id, "EXPENSE", 1, "bank", null, 100, "INR", "food", merchant, null, source, ref)

    @Test
    fun databaseFileOnDiskIsNotPlainSqlite() {
        val db = open()
        db.add("a", "Swiggy")
        driver!!.close()
        driver = null
        val header = context.getDatabasePath(name).inputStream().use { it.readNBytes(16) }
        assertNotEquals("SQLite format 3\u0000", String(header, Charsets.ISO_8859_1))
    }

    @Test
    fun dataSurvivesReopenWithTheStoredKey() {
        open().add("a", "Swiggy")
        driver!!.close()
        val db = open()
        assertEquals(1L, db.schemaQueries.entryCount().executeAsOne())
    }

    @Test
    fun bundledSqliteSupportsUpsertsFts5AndTrigram() {
        val db = open()
        val version = driver!!.executeQuery(null, "select sqlite_version()", { c ->
            c.next()
            app.cash.sqldelight.db.QueryResult.Value(c.getString(0))
        }, 0).value
        val (major, minor) = version!!.split('.').map { it.toInt() }
        assertTrue(major > 3 || minor >= 38, "SQLCipher's SQLite is $version")
        db.add("a", "Swiggy Instamart")
        db.add("a", "Zomato") // upsert by id
        assertEquals(1L, db.schemaQueries.entryCount().executeAsOne())
        assertEquals(listOf("a"), db.schemaQueries.searchEntries("omat", 10).executeAsList().map { it.id })
        assertTrue(db.schemaQueries.searchEntries("swiggy", 10).executeAsList().isEmpty())
    }

    @Test
    fun aClashOnSourceReferenceKeepsTheOriginalRow() {
        val db = open()
        db.add("a", "x", "sms", "UPI1")
        runCatching { db.add("b", "y", "sms", "UPI1") }.also { assertTrue(it.isFailure) }
        assertEquals(1L, db.schemaQueries.entryCount().executeAsOne())
    }
}
