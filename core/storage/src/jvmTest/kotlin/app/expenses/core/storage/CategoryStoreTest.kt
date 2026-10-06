package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val clock = HlcClock("n1")
    private val categories = CategoryStore(db, clock, { 1_000L })
    private val ledger = LedgerStore(db, clock, { 1_000L })
    private val templates = TemplateStore(db, clock, { 1_000L })
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)

    init { ledger.saveAccount(bank) }

    private fun opsFor(id: String) = db.schemaQueries.opsSince("", 100_000).executeAsList().filter { it.entity == "category" && it.row_id == id }
    private fun names() = categories.list().map { it.name }
    private fun expense(id: String, amount: Long, vararg cats: String, at: Long = 1) {
        val parts = cats.toList()
        val each = amount / parts.size
        val splits = parts.mapIndexed { i, c -> CategorySplit(CategoryId(c), m(if (i == parts.lastIndex) amount - each * (parts.size - 1) else each)) }
        ledger.save(Expense(EntryId(id), at, bank.id, m(amount), splits))
    }

    // ---- defaults ----

    @Test
    fun theEightDefaultsAreCreatedOnceWithTheirNamesAsIds() {
        assertEquals(CategoryStore.DEFAULTS, names())
        assertTrue(categories.list().all { it.id == it.name && it.parentId == null && !it.hidden })
        CategoryStore(db, clock, { 2_000L }) // a second start must not add them again
        assertEquals(CategoryStore.DEFAULTS, names())
        assertTrue(db.schemaQueries.opsSince("", 1_000).executeAsList().none { it.entity == "category" }, "seeding is the same on every device and is not logged")
    }

    // ---- create, rename, colour, hide ----

    @Test
    fun createdCategoriesGoAtTheEndOfTheirLevelAndAreLogged() {
        val pets = categories.create("  Pets  ", colorArgb = 0xFF00AA00)
        assertEquals("Pets", pets.name)
        assertEquals("Pets", categories.list().last().name)
        val vet = categories.create("Vet", parentId = pets.id)
        assertEquals(pets.id, categories.list().first { it.id == vet.id }.parentId)
        // a top-level category has no parent, and like entries a new row logs only the fields that have a value
        assertEquals(setOf("name", "color", "position", "hidden"), opsFor(pets.id).map { it.field_ }.toSet())
        assertTrue(opsFor(vet.id).any { it.field_ == "parent_id" && it.value_ == pets.id })
    }

    @Test
    fun invalidNamesAndParentsAreRefusedAndLeaveNothingBehind() {
        val before = categories.list()
        val opsBefore = db.schemaQueries.opsSince("", 100_000).executeAsList().size
        assertFailsWith<IllegalArgumentException> { categories.create("food") } // clashes with Food ignoring case
        assertFailsWith<IllegalArgumentException> { categories.create("   ") }
        assertFailsWith<IllegalArgumentException> { categories.create("Sub", parentId = "ghost") }
        val vet = categories.create("Vet", parentId = "Food")
        assertFailsWith<IllegalArgumentException> { categories.create("Deep", parentId = vet.id) } // only one level deep
        categories.delete(vet.id)
        assertEquals(before, categories.list())
        assertTrue(db.schemaQueries.opsSince("", 100_000).executeAsList().size > opsBefore) // the create and delete of Vet were logged
    }

    @Test
    fun renamingKeepsTheIdSoEntriesFollow() {
        expense("e1", 1_000, "Food")
        categories.rename("Food", "  Eating   out ")
        assertEquals("Eating out", categories.list().first { it.id == "Food" }.name)
        assertEquals(m(1_000), ledger.spendByCategory(inr)[CategoryId("Food")])
        assertEquals(listOf("name" to "Eating out"), opsFor("Food").map { it.field_ to it.value_ })
        categories.rename("Food", "eating OUT") // only the case changes: allowed, because the category does not clash with itself
        assertEquals("eating OUT", categories.list().first { it.id == "Food" }.name)
    }

    @Test
    fun colourAndHiddenChangeOnlyThatFieldAndHiddenCategoriesKeepTheirEntries() {
        expense("e1", 500, "Fun")
        categories.setColor("Fun", 0xFF112233)
        categories.setHidden("Fun", true)
        val fun1 = categories.list().first { it.id == "Fun" }
        assertEquals(0xFF112233, fun1.colorArgb)
        assertTrue(fun1.hidden)
        assertEquals(setOf("color", "hidden"), opsFor("Fun").map { it.field_ }.toSet())
        assertEquals(m(500), ledger.spendByCategory(inr)[CategoryId("Fun")])
        categories.setHidden("Fun", true) // no change, no operation
        assertEquals(2, opsFor("Fun").size)
    }

    // ---- hierarchy and order ----

    @Test
    fun movingUnderAParentChecksTheRulesAndGoesToTheEndOfThatLevel() {
        val cafe = categories.create("Cafe")
        categories.setParent(cafe.id, "Food")
        assertEquals("Food", categories.list().first { it.id == cafe.id }.parentId)
        assertFailsWith<IllegalArgumentException> { categories.setParent("Food", "Bills") } // Food has a subcategory now
        assertFailsWith<IllegalArgumentException> { categories.setParent("Bills", "Bills") }
        assertFailsWith<IllegalArgumentException> { categories.setParent(cafe.id, cafe.id) }
        val dup = categories.create("Cafe") // top-level again, same name as the one under Food
        assertFailsWith<IllegalArgumentException> { categories.setParent(dup.id, "Food") } // would clash within Food
        assertFailsWith<IllegalArgumentException> { categories.setParent(cafe.id, null) } // a top-level "Cafe" already exists
        categories.rename(dup.id, "Cafe two")
        categories.setParent(cafe.id, null)
        assertNull(categories.list().first { it.id == cafe.id }.parentId)
    }

    @Test
    fun reorderingSwapsWithTheNeighbourAndDoesNothingAtTheEnds() {
        categories.move("Groceries", -1)
        assertEquals(listOf("Groceries", "Food", "Transport"), names().take(3))
        categories.move("Groceries", -1) // already first
        assertEquals("Groceries", names().first())
        categories.move("Other", 1) // already last
        assertEquals("Other", names().last())
        categories.move("Groceries", 1)
        assertEquals(listOf("Food", "Groceries", "Transport"), names().take(3))
        assertEquals(CategoryStore.DEFAULTS.size, categories.list().map { it.position }.toSet().size, "positions are distinct after moves")
    }

    // ---- delete and merge ----

    @Test
    fun deletingIsAllowedOnlyWhileNothingUsesTheCategory() {
        val pets = categories.create("Pets")
        categories.delete(pets.id)
        assertTrue(categories.list().none { it.id == pets.id })
        expense("e1", 700, "Health")
        assertFailsWith<IllegalArgumentException> { categories.delete("Health") }
        ledger.delete(EntryId("e1"))
        assertFailsWith<IllegalArgumentException> { categories.delete("Health") } // a deleted entry can be restored, so it still counts
        assertEquals(1, categories.usage("Health"))
        templates.save(Template("t", "Pills", m(100), CategoryId("Bills"), bank.id))
        assertFailsWith<IllegalArgumentException> { categories.delete("Bills") }
        templates.delete("t")
        categories.delete("Bills")
        assertTrue(categories.list().none { it.id == "Bills" })
    }

    @Test
    fun mergingReMapsEverythingAndTheTotalsAreTheSumOfTheTwo() {
        expense("e1", 1_000, "Food")
        expense("e2", 2_500, "Groceries")
        expense("e3", 3_000, "Food", "Groceries") // split across both
        expense("e4", 400, "Fun")
        ledger.save(Income(EntryId("i1"), 5, bank.id, m(9_000), CategoryId("Food")))
        templates.save(Template("t", "Veg", m(100), CategoryId("Groceries"), bank.id))
        val before = ledger.spendByCategory(inr)
        categories.merge("Groceries", "Food")

        val expected = mapOf<CategoryId?, Money>(CategoryId("Food") to before.getValue(CategoryId("Food")) + before.getValue(CategoryId("Groceries")), CategoryId("Fun") to m(400))
        assertEquals(expected, ledger.spendByCategory(inr), "SQL totals")
        assertEquals(expected, ledger.loadLedger().spendByCategory(inr), "the reloaded ledger agrees with the SQL totals")
        assertEquals(CategoryId("Food"), templates.list().single().category)
        assertEquals("Food", db.schemaQueries.entryById("i1").executeAsOne().category_id)
        assertTrue(categories.list().none { it.id == "Groceries" })
        assertEquals(0, categories.usage("Groceries"))
        assertEquals(listOf("merged_into" to "Food", "deleted" to "1"), opsFor("Groceries").map { it.field_ to it.value_ })
    }

    @Test
    fun aRefusedMergeChangesNothing() {
        expense("e1", 1_000, "Food")
        val sub = categories.create("Cafe", parentId = "Food")
        val before = ledger.spendByCategory(inr)
        val opsBefore = db.schemaQueries.opsSince("", 100_000).executeAsList().size
        assertFailsWith<IllegalArgumentException> { categories.merge("Food", "Fun") } // Food has a subcategory
        assertFailsWith<IllegalArgumentException> { categories.merge("Food", "Food") }
        assertFailsWith<IllegalArgumentException> { categories.merge("Food", "ghost") }
        assertEquals(before, ledger.spendByCategory(inr))
        assertEquals(opsBefore, db.schemaQueries.opsSince("", 100_000).executeAsList().size)
        categories.merge(sub.id, "Food") // a subcategory can merge into its parent
        assertTrue(categories.list().none { it.id == sub.id })
    }

    @Test
    fun anEditSavedWithAMergedCategoryIdLandsInTheTargetCategory() {
        categories.merge("Groceries", "Food")
        // a stale edit, as if from a device that had not seen the merge
        expense("late", 700, "Groceries")
        ledger.save(Income(EntryId("inc"), 2, bank.id, m(5_000), CategoryId("Groceries")))
        templates.save(Template("t", "Veg", m(100), CategoryId("Groceries"), bank.id))
        assertEquals(mapOf<CategoryId?, Money>(CategoryId("Food") to m(700)), ledger.spendByCategory(inr), "SQL totals")
        assertEquals(mapOf<CategoryId?, Money>(CategoryId("Food") to m(700)), ledger.loadLedger().spendByCategory(inr), "the reloaded ledger agrees")
        assertEquals("Food", db.schemaQueries.entryById("inc").executeAsOne().category_id)
        assertEquals(CategoryId("Food"), templates.list().single().category)
        assertEquals(0, categories.usage("Groceries"), "nothing may refer to the removed category")
        // a split expense whose parts both resolve to the same target stays consistent
        ledger.save(Expense(EntryId("two"), 3, bank.id, m(900), listOf(CategorySplit(CategoryId("Groceries"), m(400)), CategorySplit(CategoryId("Food"), m(500)))))
        assertEquals(m(1_600), ledger.spendByCategory(inr).getValue(CategoryId("Food")))
        assertEquals(ledger.spendByCategory(inr), ledger.loadLedger().spendByCategory(inr))
    }

    @Test
    fun aMergeChainIsFollowedToItsEnd() {
        categories.merge("Groceries", "Food")
        categories.merge("Food", "Other")
        assertEquals("Other", db.resolveCategory("Groceries"))
        assertEquals("Other", db.resolveCategory("Food"))
        assertEquals("Other", db.resolveCategory("Other"))
        assertEquals("Unknown", db.resolveCategory("Unknown"), "ids that were never merged resolve to themselves")
        expense("late", 300, "Groceries")
        assertEquals(mapOf<CategoryId?, Money>(CategoryId("Other") to m(300)), ledger.spendByCategory(inr))
    }

    @Test
    fun aCategoryThatWasOnlyDeletedIsNotRedirected() {
        val pets = categories.create("Pets")
        categories.delete(pets.id)
        assertEquals(pets.id, db.resolveCategory(pets.id))
    }

    @Test
    fun aMergedCategoryDoesNotComeBackAndItsNameIsFreeToReuse() {
        categories.merge("Groceries", "Food")
        assertTrue(categories.list().none { it.name == "Groceries" })
        val again = categories.create("Groceries")
        assertTrue(again.id != "Groceries")
        assertEquals(m(0).minorUnits, categories.usage(again.id))
    }
}
