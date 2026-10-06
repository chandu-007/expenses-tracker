package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateStoreTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val store = TemplateStore(db, HlcClock("n1"), { 1_000L })
    private val inr = Currency.INR

    private fun chai(id: String = "chai", amount: Long? = 2_000) =
        Template(id, "Chai", amount?.let { Money(it, inr) }, CategoryId("Food"), AccountId("cash"), "Tea stall")

    @Test
    fun templatesRoundTripWithAndWithoutAFixedAmount() {
        val fixed = chai()
        val variable = Template("fuel", "Petrol", null, CategoryId("Transport"), AccountId("bank"))
        store.save(fixed)
        store.save(variable)
        assertEquals(setOf(fixed, variable), store.list().toSet())
    }

    @Test
    fun theMostUsedTemplateComesFirstThenAlphabetical() {
        store.save(chai("a")); store.save(Template("b", "Auto", Money(5_000, inr), CategoryId("Transport"), AccountId("cash")))
        store.save(Template("c", "Bus", Money(1_500, inr), CategoryId("Transport"), AccountId("cash")))
        assertEquals(listOf("Auto", "Bus", "Chai"), store.list().map { it.name }) // no use yet: by name
        repeat(3) { store.recordUse("c") }
        store.recordUse("a")
        assertEquals(listOf("Bus", "Chai", "Auto"), store.list().map { it.name })
    }

    @Test
    fun editingChangesOnlyThatTemplateAndLogsOnlyTheChangedFields() {
        store.save(chai())
        val created = db.schemaQueries.opsSince("", 1_000).executeAsList().size
        store.save(chai(amount = 2_500))
        val edits = db.schemaQueries.opsSince("", 1_000).executeAsList().drop(created)
        assertEquals(listOf("amount_minor" to "2500"), edits.map { it.field_ to it.value_ })
        store.save(chai(amount = 2_500)) // no change, no operations
        assertEquals(created + 1, db.schemaQueries.opsSince("", 1_000).executeAsList().size)
        assertEquals(Money(2_500, inr), store.list().single().amount)
    }

    @Test
    fun deletedTemplatesDisappearAndCannotBeEditedBack() {
        store.save(chai())
        store.delete("chai")
        assertTrue(store.list().isEmpty())
        assertEquals("1", db.schemaQueries.latestOp("template", "chai", "deleted").executeAsOne().value_)
        assertFailsWith<IllegalArgumentException> { store.save(chai()) }
        store.delete("chai") // deleting twice is harmless
        store.delete("never-existed")
    }

    @Test
    fun invalidTemplatesAreRejectedByTheDomainType() {
        assertFailsWith<IllegalArgumentException> { Template("x", "  ", null, CategoryId("c"), AccountId("a")) }
        assertFailsWith<IllegalArgumentException> { Template("x", "Zero", Money(0, inr), CategoryId("c"), AccountId("a")) }
        assertFailsWith<IllegalArgumentException> { Template("", "NoId", null, CategoryId("c"), AccountId("a")) }
    }

    @Test
    fun foreignCurrencyTemplatesKeepTheirDecimalPlaces() {
        val jpy = Currency("JPY", 0)
        store.save(Template("ramen", "Ramen", Money(900, jpy), CategoryId("Food"), AccountId("yen")))
        assertEquals(Money(900, jpy), store.list().single().amount)
    }

    @Test
    fun settingsAreStoredAndOverwritten() {
        assertNull(store.setting("last_category"))
        store.putSetting("last_category", "Food")
        store.putSetting("last_category", "Transport")
        assertEquals("Transport", store.setting("last_category"))
        assertTrue(db.schemaQueries.opsSince("", 1_000).executeAsList().none { it.entity == "setting" }, "settings stay on this device")
    }
}
