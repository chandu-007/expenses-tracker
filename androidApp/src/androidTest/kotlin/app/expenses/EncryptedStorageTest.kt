package app.expenses

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.expenses.core.storage.DatabaseKeyUnavailableException
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.db.ExpensesDb
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-encrypted.db"
    private var driver: SqlDriver? = null
    private val keyFile get() = File(context.noBackupFilesDir, "db_key.bin")

    @Before fun clean() = EncryptedDriver.reset(context, name)

    @After fun close() {
        runCatching { driver?.close() }
        EncryptedDriver.reset(context, name)
    }

    private fun open(): ExpensesDb {
        driver = EncryptedDriver.create(context, name)
        return ExpensesDb(driver!!)
    }

    private fun ExpensesDb.add(id: String, merchant: String?, source: String = "manual", ref: String? = null) =
        schemaQueries.insertEntry(id, "EXPENSE", 1, "bank", null, 100, "INR", "food", merchant, null, source, ref)

    private fun SqlDriver.string(sql: String): String? = executeQuery(null, sql, { c ->
        c.next()
        QueryResult.Value(c.getString(0))
    }, 0).value

    private fun closeDriver() {
        driver!!.close()
        driver = null
    }

    @Test
    fun databaseFileOnDiskIsNotPlainSqlite() {
        open().add("a", "Swiggy")
        closeDriver()
        val header = ByteArray(16)
        context.getDatabasePath(name).inputStream().use { assertEquals(16, it.read(header)) }
        assertNotEquals("SQLite format 3\u0000", String(header, Charsets.ISO_8859_1))
    }

    @Test
    fun sqlcipherIsActiveAndAWrongKeyCannotReadTheDatabase() {
        open().add("a", "Swiggy")
        assertTrue(driver!!.string("PRAGMA cipher_version").orEmpty().isNotEmpty())
        closeDriver()
        val wrong = AndroidSqliteDriver(ExpensesDb.Schema, context, name, SupportOpenHelperFactory(ByteArray(32) { 7 }))
        try {
            assertFailsWith<Exception> { ExpensesDb(wrong).schemaQueries.entryCount().executeAsOne() }
        } finally {
            runCatching { wrong.close() }
        }
    }

    @Test
    fun dataSurvivesReopenWithTheStoredKey() {
        open().add("a", "Swiggy")
        closeDriver()
        assertEquals(1L, open().schemaQueries.entryCount().executeAsOne())
    }

    @Test
    fun bundledSqliteSupportsUpsertsFts5AndTrigram() {
        val db = open()
        val version = driver!!.string("select sqlite_version()")!!
        val (major, minor) = version.split('.').map { it.toInt() }
        assertTrue(major > 3 || minor >= 38, "SQLCipher's SQLite is $version; the framework SQLite must never be used")
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
        assertTrue(runCatching { db.add("b", "y", "sms", "UPI1") }.isFailure)
        assertEquals(1L, db.schemaQueries.entryCount().executeAsOne())
    }

    @Test
    fun aMissingKeyFileWithAnExistingDatabaseIsReportedNotReplaced() {
        open().add("a", "Swiggy")
        closeDriver()
        assertTrue(keyFile.delete())
        assertFailsWith<DatabaseKeyUnavailableException> { EncryptedDriver.create(context, name) }
        assertFalse(keyFile.exists(), "must not create a new key next to an existing database")
    }

    @Test
    fun aLostKeystoreKeyIsReportedNotACrash() {
        open().add("a", "Swiggy")
        closeDriver()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("expenses_db_wrap_key")
        assertFailsWith<DatabaseKeyUnavailableException> { EncryptedDriver.create(context, name) }
    }

    @Test
    fun aCorruptKeyFileIsReported() {
        open().add("a", "Swiggy")
        closeDriver()
        keyFile.writeBytes(ByteArray(10) { 1 })
        assertFailsWith<DatabaseKeyUnavailableException> { EncryptedDriver.create(context, name) }
    }

    @Test
    fun resetLetsTheUserStartFreshAfterKeyLoss() {
        open().add("a", "Swiggy")
        closeDriver()
        assertTrue(keyFile.delete())
        EncryptedDriver.reset(context, name)
        assertEquals(0L, open().schemaQueries.entryCount().executeAsOne())
    }
}
