package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SavedSearchStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val store = SavedSearchStore(db, HlcClock("n")) { 1L }
    private fun s(id: String, name: String, text: String = "uber") = SavedSearch(id, name, text, "100", "", "Transport", "EXPENSE", "LAST_30_DAYS", "Work")

    @Test
    fun aSearchIsKeptWithEveryConditionAndListedByName() {
        store.save(s("b", "Cabs")); store.save(s("a", "airport", "airport"))
        assertEquals(listOf("airport", "Cabs"), store.list().map { it.name }, "alphabetical, ignoring case")
        assertEquals(s("b", "Cabs"), store.list().last(), "every condition survives")
    }

    @Test
    fun namesAreTidiedAndUniqueIgnoringCaseAndEditingKeepsTheSameId() {
        store.save(s("a", "  Cab   rides "))
        assertEquals("Cab rides", store.list().single().name)
        assertEquals("You already have a saved search called \"Cab rides\".", assertFailsWith<IllegalArgumentException> { store.save(s("b", "cab RIDES")) }.message)
        store.save(s("a", "cab rides", "ola")) // the same search renamed in another case and changed: fine
        assertEquals(1, store.list().size); assertEquals("ola", store.list().single().text)
        assertEquals("Give the search a name.", assertFailsWith<IllegalArgumentException> { store.save(s("c", "  ")) }.message)
        assertTrue(assertFailsWith<IllegalArgumentException> { store.save(s("c", "x".repeat(41))) }.message!!.contains("40"))
    }

    @Test
    fun aSearchWithNothingInItIsRefusedAndDeletingRemovesItAndIsLogged() {
        assertEquals("Type something to search for, or choose a filter, before saving.", assertFailsWith<IllegalArgumentException> { store.save(SavedSearch("e", "Empty")) }.message)
        store.save(s("a", "Cabs")); store.delete("a"); store.delete("a")
        assertEquals(emptyList(), store.list())
        val ops = db.schemaQueries.opsSince("", 1000).executeAsList().filter { it.entity == "saved_search" }.map { it.field_ to it.value_ }
        assertTrue("name" to "Cabs" in ops && "deleted" to "1" in ops, "$ops")
        store.save(s("n", "Cabs")) // the name is free again after a delete
    }

    @Test
    fun aBackupCarriesSavedSearchesAndRestoringTwiceAddsNothing() {
        store.save(s("a", "Cabs")); store.save(SavedSearch("b", "Big", minRupees = "5000"))
        val clock = HlcClock("n"); val ledger = LedgerStore(db, clock) { 1L }
        val backup = BackupStore(db, clock, { 1L }, ledger, TemplateStore(db, clock) { 1L }, RecurringStore(db, clock, { 1L }, ledger))
        val data = BackupFile.decode(BackupFile.encode(backup.export(), 1L)).second
        assertEquals(2, data.savedSearches.size)
        val fresh = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
        val fl = LedgerStore(fresh, clock) { 1L }
        val fb = BackupStore(fresh, clock, { 1L }, fl, TemplateStore(fresh, clock) { 1L }, RecurringStore(fresh, clock, { 1L }, fl))
        fb.restore(data); fb.restore(data)
        assertEquals(store.list(), SavedSearchStore(fresh, clock) { 1L }.list())
    }
}
