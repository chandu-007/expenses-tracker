package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.CurrencyChoices
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.CategoryId
import kotlinx.datetime.LocalDate
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseCurrencyStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val ledger = LedgerStore(db, clock) { 1L }
    private val templates = TemplateStore(db, clock) { 1L }
    private val store = BaseCurrencyStore(db, clock, { 2L }, templates, ledger)
    private val usd = Currency.USD

    init {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR))
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR))
    }

    private fun currencies() = db.schemaQueries.allAccounts().executeAsList().associate { it.id to it.currency }

    @Test
    fun anEmptyLedgerStartsInRupeesAndChangesEveryAccountTogetherAndRemembersIt() {
        assertEquals(Currency.INR, store.current())
        assertNull(store.change(usd))
        assertEquals(usd, store.current())
        assertEquals(mapOf("cash" to "USD", "bank" to "USD"), currencies())
        ledger.save(Expense(EntryId("e"), 1, AccountId("cash"), Money(1250, usd)))
        assertEquals("-USD 12.50", ledger.balances()[AccountId("cash")]!!.formatted())
    }

    @Test
    fun theLedgerCanReadItsAccountsAndBalancesInTheNewCurrencyStraightAfterTheChange() {
        assertNull(store.change(Currency("EUR", 2)))
        assertEquals(setOf("EUR"), ledger.balances().values.map { it.currency.code }.toSet())
    }

    @Test
    fun aRepeatingItemOrAQuickEntryBlocksTheChange() {
        val categories = CategoryStore(db, clock, { 1L })
        val recurring = RecurringStore(db, clock, { 1L }, ledger)
        recurring.save(RecurringItem("r", "Rent", Money(100, Currency.INR), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 8, 1))))
        assertTrue(assertNotNull(store.change(usd)).contains("repeating"))
        recurring.delete("r")
        templates.save(Template("t", "Pills", Money(100, Currency.INR), CategoryId("Bills"), AccountId("bank")))
        assertTrue(assertNotNull(store.change(usd)).contains("quick entries"))
        templates.delete("t")
        assertNull(store.change(usd))
    }

    @Test
    fun theChangeIsLoggedForSyncPerAccountAndNotLoggedWhenNothingChanges() {
        assertNull(store.change(usd))
        val ops = db.schemaQueries.opsOfRow("account", "cash").executeAsList().filter { it.field_ == "currency" }
        assertEquals("USD", ops.last().value_)
        val n = db.schemaQueries.opsOfRow("account", "cash").executeAsList().size
        assertNull(store.change(usd))
        assertEquals(n, db.schemaQueries.opsOfRow("account", "cash").executeAsList().size)
    }

    @Test
    fun anyExistingAmountBlocksTheChangeAndLeavesEverythingAsItWas() {
        ledger.save(Expense(EntryId("e"), 1, AccountId("cash"), Money(100, Currency.INR)))
        assertTrue(assertNotNull(store.change(usd)).contains("before the first entry"))
        assertEquals(Currency.INR, store.current()); assertEquals(mapOf("cash" to "INR", "bank" to "INR"), currencies())
        ledger.delete(EntryId("e"))
        assertNotNull(store.change(usd), "a deleted entry still counts: its history carries the old currency")
    }

    @Test
    fun budgetsQuickEntriesRepeatingItemsAndOpeningBalancesEachBlock() {
        db.schemaQueries.upsertBudget("ALL", 5000, "INR")
        assertTrue(assertNotNull(store.change(usd)).contains("budgets"))
        db.schemaQueries.deleteBudget("ALL")
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR, 5000))
        assertTrue(assertNotNull(store.change(usd)).contains("opening balances"))
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR, 0))
        assertNull(store.change(usd))
    }

    @Test
    fun aCurrencyThatIsNotOfferedIsRejectedAndYenKeepsNoDecimals() {
        assertTrue(runCatching { store.change(Currency("XYZ", 2)) }.isFailure)
        assertEquals(0, CurrencyChoices.byCode("JPY").minorUnitDigits); assertEquals(3, CurrencyChoices.byCode("KWD").minorUnitDigits)
        assertEquals(Currency.INR, CurrencyChoices.byCode("garbage")); assertEquals(Currency.INR, CurrencyChoices.byCode(null))
        assertNull(store.change(CurrencyChoices.byCode("JPY")))
        assertEquals("JPY", currencies()["cash"])
    }
}
