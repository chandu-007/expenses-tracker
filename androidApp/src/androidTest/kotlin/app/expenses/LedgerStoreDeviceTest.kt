package app.expenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Ledger
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.SpendBasis
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.LedgerStore
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.storage.search
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The ledger store on the real Android runtime: SQLCipher, upserts, FTS triggers, spend lines, sync log. */
@RunWith(AndroidJUnit4::class)
class LedgerStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "ledger-store-test.db"
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)

    @Before fun clean() = EncryptedDriver.reset(context, name)
    @After fun cleanUp() = EncryptedDriver.reset(context, name)

    @Test
    fun entriesSaveReloadAndReportOnTheDeviceDatabase() {
        val driver = EncryptedDriver.create(context, name)
        try {
            val db = ExpensesDb(driver)
            val store = LedgerStore(db, HlcClock("device"), { 1_000L })
            val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)
            val card = Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, inr)
            store.saveAccount(bank)
            store.saveAccount(card)
            val food = CategoryId("food")
            val entries = listOf(
                Income(EntryId("i"), 1, bank.id, m(100_000)),
                Expense(EntryId("x"), 2, card.id, m(4_000), listOf(CategorySplit(food, m(4_000))), listOf(Share(null, m(1_000)), Share(PersonId("asha"), m(3_000)))),
                Expense(EntryId("y"), 3, bank.id, m(900), listOf(CategorySplit(food, m(900)))),
                Refund(EntryId("r"), 4, bank.id, m(300), EntryId("y")),
                Transfer(EntryId("t"), 5, bank.id, card.id, m(4_000)),
            )
            entries.forEach { store.save(it, EntryDetails(merchant = "Swiggy ${it.id.value}")) }

            val reference = Ledger.EMPTY.addAccount(bank).addAccount(card).addAll(entries)
            val loaded = store.loadLedger()
            assertEquals(reference.balances(), loaded.balances())
            assertEquals(reference.personBalance(PersonId("asha"), inr), loaded.personBalance(PersonId("asha"), inr))
            for (basis in SpendBasis.entries) {
                assertEquals(reference.spendByCategory(inr, basis), store.spendByCategory(inr, basis), "$basis")
            }
            assertEquals(5, db.search("swiggy").size)
            assertFailsWith<IllegalArgumentException> { store.save(Refund(EntryId("r2"), 6, bank.id, m(601), EntryId("y"))) }
            assertEquals(5, store.loadLedger().entries.size)
        } finally {
            driver.close()
        }
    }
}
