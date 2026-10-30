package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Transfer candidates read from a real database: invented entries only; the 20,000-entry timing is printed and bounded. */
class TransferCandidateStoreTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val clock = HlcClock("a")
    private var now = 5_000L
    private val ledger = LedgerStore(db, clock) { now++ }
    private val origins = OriginStore(db)
    private val expects = ExpectStore(db)
    private val attachments = AttachmentStore(db, clock) { now++ }
    private val changes = KindChanges(db, ledger, origins, expects, attachments)
    private val templates = TemplateStore(db, clock) { now++ }
    private val store = TransferCandidateStore(db, changes, templates)
    private val inr = Currency.INR
    private val minute = 60_000L
    private val day = 24 * 60 * minute
    private val t0 = 1_700_000_000_000L

    init {
        ledger.saveAccount(Account(AccountId("bank"), "Main bank", AccountType.BANK, inr))
        ledger.saveAccount(Account(AccountId("save"), "Savings", AccountType.BANK, inr))
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, inr))
    }

    private fun m(v: Long) = Money(v, inr)
    private fun expense(id: String, at: Long, account: String = "bank", amount: Long = 500_000) = Expense(EntryId(id), at, AccountId(account), m(amount), listOf(CategorySplit(CategoryId("Other"), m(amount))))
    private fun income(id: String, at: Long, account: String = "save", amount: Long = 500_000) = Income(EntryId(id), at, AccountId(account), m(amount), CategoryId("income:other"))
    private fun scan() = store.scan("INR")

    @Test
    fun aRealLedgerGivesTheThreeGroupsAndLeavesOutWhatCannotChangeItsType() {
        ledger.save(expense("atm1", t0), EntryDetails(merchant = "ATM cash", source = "sms", sourceRef = "R1"))
        origins.put("atm1", OriginKind.SMS, "AX-BANK", "Rs.5,000.00 withdrawn at ATM 12345 on 05-10-26", t0)
        ledger.save(expense("atm2", t0 + day, account = "card", amount = 300_000), EntryDetails(merchant = "Bank", note = "ATM WDL"))
        ledger.save(expense("atmos", t0), EntryDetails(merchant = "ATMOS cafe"))
        // refused for their own reasons
        ledger.save(expense("expecting", t0 + 2 * day), EntryDetails(merchant = "ATM cash")); expects.set("expecting", "Ravi", null, 500_000)
        ledger.save(Expense(EntryId("shared"), t0 + 3 * day, AccountId("bank"), m(500_000), listOf(CategorySplit(CategoryId("Other"), m(500_000))), listOf(Share(null, m(250_000)), Share(PersonId("p1"), m(250_000)))), EntryDetails(merchant = "ATM cash"))
        // a move of money
        ledger.save(expense("x", t0 + 4 * day, amount = 1_200_000), EntryDetails(merchant = "Funds transfer to savings"))
        ledger.save(income("i", t0 + 4 * day + 3 * minute, amount = 1_200_000), EntryDetails(merchant = "Transfer received"))
        // a self transfer
        ledger.save(expense("self", t0 + 5 * day, amount = 77_700), EntryDetails(merchant = "Self transfer"))
        // already a transfer
        ledger.save(Transfer(EntryId("done"), t0 + 6 * day, AccountId("bank"), AccountId("cash"), m(100_000)), EntryDetails(merchant = "ATM cash"))

        val found = scan()
        assertEquals(setOf("atm1", "atm2"), found.cashWithdrawals.map { it.entry.id }.toSet())
        assertEquals(listOf("x" to "i"), found.ownAccounts.map { it.entry.id to it.partner!!.id })
        assertEquals(listOf("self"), found.selfTransfers.map { it.entry.id })
        assertEquals(4, found.total)
    }

    @Test
    fun notATransferIsRememberedPerEntryAndBroughtBackTogether() {
        ledger.save(expense("a", t0), EntryDetails(merchant = "ATM cash")); ledger.save(expense("b", t0 + day), EntryDetails(merchant = "ATM cash"))
        assertEquals(2, scan().total)
        store.dismiss(listOf("a"))
        assertEquals(listOf("b"), scan().cashWithdrawals.map { it.entry.id })
        assertEquals(setOf("a"), store.dismissed())
        // a new store over the same database (a reopened app) still knows
        assertEquals(listOf("b"), TransferCandidateStore(db, changes, templates).scan("INR").cashWithdrawals.map { it.entry.id })
        assertEquals(1, store.clearDismissed()); assertEquals(2, scan().total); assertEquals(emptySet(), store.dismissed())
    }

    @Test
    fun anEntryChangedToATransferLeavesTheListAndUndoBringsItBack() {
        ledger.save(expense("a", t0, amount = 200_000), EntryDetails(merchant = "ATM cash"))
        assertEquals(1, scan().total)
        val c = changes.change("a", Transfer(EntryId("t"), t0, AccountId("bank"), AccountId("cash"), m(200_000)), "ATM cash", emptyList())
        assertEquals(0, scan().total)
        changes.undo(c)
        assertEquals(listOf("a"), scan().cashWithdrawals.map { it.entry.id })
    }

    @Test
    fun otherCurrenciesAreNotRead() {
        ledger.save(expense("a", t0), EntryDetails(merchant = "ATM cash"))
        assertEquals(0, store.scan("USD").total)
    }

    @Test
    fun twentyThousandEntriesAreReadInWellUnderASecond() {
        val rnd = Random(7)
        val merchants = listOf("Swiggy", "Zomato", "Amazon", "Uber", "Netflix", "Big Bazaar", "Ravi Kumar", "Anita Rao", "Pharmacy", "Metro", "Petrol pump", "Rent", "Cafe Coffee", "BookMyShow")
        val falseFriends = listOf("ATMOS cafe", "Batman store", "Treatment centre", "Cash and Carry", "Cashify", "Self Help Group", "Money Transfer to RAVI KUMAR", "ATM Bakery")
        var truePositives = 0
        db.bulkLoad(driver) {
            var at = t0
            for (n in 0 until 20_000) {
                at += rnd.nextLong(5 * minute, 90 * minute)
                val kind = n % 25
                when {
                    kind == 0 -> { ledger.save(income("i$n", at, account = if (n % 2 == 0) "bank" else "save", amount = rnd.nextLong(100, 400) * 100), EntryDetails(merchant = "Salary ${n % 7}")) }
                    kind == 1 && n % 4 == 1 -> { // real ATM withdrawals, half of them with their bank text kept
                        ledger.save(expense("e$n", at, amount = rnd.nextLong(5, 40) * 50_000 / 10), EntryDetails(merchant = "ATM cash", source = "sms", sourceRef = "R$n"))
                        if (n % 8 == 1) origins.put("e$n", OriginKind.SMS, "AX-BANK", "Rs.${n}00.00 withdrawn at ATM $n on 05-10-26 from A/c XX1234", at)
                        truePositives++
                    }
                    kind == 2 -> ledger.save(expense("e$n", at, amount = rnd.nextLong(50, 500) * 100), EntryDetails(merchant = falseFriends[n % falseFriends.size]))
                    else -> {
                        ledger.save(expense("e$n", at, account = if (n % 3 == 0) "card" else "bank", amount = rnd.nextLong(20, 3_000) * 100), EntryDetails(merchant = merchants[n % merchants.size], note = if (n % 5 == 0) "lunch with the team at the office" else null, source = "sms", sourceRef = "R$n"))
                        if (n % 3 == 0) origins.put("e$n", OriginKind.SMS, "AX-BANK", "Rs.${n}.00 spent on your card ending 1234 at ${merchants[n % merchants.size]} on 05-10-26. Not you? Call 18002586161 Avl limit Rs 100000", at)
                    }
                }
            }
            // thirty real moves between the person's own accounts, minutes apart
            for (k in 0 until 30) {
                val when_ = t0 + (k + 1L) * 20 * day
                ledger.save(expense("mx$k", when_, amount = 1_234_500L + k), EntryDetails(merchant = "Funds transfer to savings"))
                ledger.save(income("mi$k", when_ + 2 * minute, amount = 1_234_500L + k), EntryDetails(merchant = "Transfer received"))
                truePositives += 2
            }
            for (k in 0 until 10) ledger.save(expense("self$k", t0 + k * day + 7L, amount = 88_800L + k), EntryDetails(merchant = "Self transfer"))
        }
        assertEquals(20_000 + 60 + 10, db.schemaQueries.entryCount().executeAsOne().toInt())
        // a first read warms the database and the code; the second is the one a person waits for on a later visit. Both are bounded.
        val cold = TimeSource.Monotonic.markNow(); val first = scan(); val coldMs = cold.elapsedNow().inWholeMilliseconds
        val warm = TimeSource.Monotonic.markNow(); val again = scan(); val warmMs = warm.elapsedNow().inWholeMilliseconds
        println("TRANSFER CANDIDATES on 20,070 entries: first read $coldMs ms, second $warmMs ms; ${first.cashWithdrawals.size} withdrawals, ${first.ownAccounts.size} moves, ${first.selfTransfers.size} self")
        assertEquals(first, again)
        assertEquals(30, first.ownAccounts.size); assertEquals(10, first.selfTransfers.size)
        assertEquals(truePositives - 60, first.cashWithdrawals.size, "every real withdrawal and no false friend")
        assertTrue(first.cashWithdrawals.none { it.entry.title != "ATM cash" })
        assertTrue(coldMs < 1_000, "first read took $coldMs ms")
        assertTrue(warmMs < 1_000, "second read took $warmMs ms")
    }

    @Test
    fun aLedgerWithOnlyAFewDifferentAmountsIsStillReadInBoundedTime() {
        // the worst case for the pairing query: almost every income has several expenses of exactly its amount within a day and a half
        val rnd = Random(11)
        db.bulkLoad(driver) {
            var at = t0
            for (n in 0 until 20_000) {
                at += rnd.nextLong(5 * minute, 60 * minute)
                val amount = (1 + n % 5) * 10_000L
                if (n % 5 == 0) ledger.save(income("i$n", at, amount = amount), EntryDetails(merchant = "Credit")) else ledger.save(expense("e$n", at, amount = amount), EntryDetails(merchant = "Shop ${n % 9}"))
            }
        }
        val started = TimeSource.Monotonic.markNow(); val found = scan(); val ms = started.elapsedNow().inWholeMilliseconds
        println("TRANSFER CANDIDATES worst case (5 amounts) on 20,000 entries: $ms ms, ${found.total} found")
        assertEquals(0, found.total, "no wording, so nothing is offered however many amounts match")
        assertTrue(ms < 2_000, "took $ms ms")
    }

    @Test
    fun aWithdrawalWhoseMoneyCameBackAsAReversalIsNotOfferedButAnUntouchedOneIs() {
        ledger.save(expense("atm", t0, amount = 200_000), EntryDetails(merchant = "ATM cash", source = "sms", sourceRef = "R1"))
        ledger.save(income("rev", t0 + 60 * minute, account = "bank", amount = 200_000), EntryDetails(merchant = "Reversal", source = "sms", sourceRef = "R1/reversal"))
        ledger.save(expense("ok", t0 + 5 * day, amount = 200_000), EntryDetails(merchant = "ATM cash", source = "sms", sourceRef = "R2"))
        assertEquals(listOf("ok"), scan().cashWithdrawals.map { it.entry.id })
        assertTrue(store.otherHalfLive("atm")); assertTrue(!store.otherHalfLive("ok"))
        // a deleted reversal no longer counts
        ledger.delete(EntryId("rev")); assertEquals(setOf("atm", "ok"), scan().cashWithdrawals.map { it.entry.id }.toSet())
    }

    @Test
    fun aSelfTransferWhoseCreditOnTheOtherAccountIsAlreadyInTheLedgerIsNotOffered() {
        ledger.save(expense("self", t0, amount = 450_000), EntryDetails(merchant = "Self transfer"))
        assertEquals(listOf("self"), scan().selfTransfers.map { it.entry.id })
        // the credit was captured too late to be paired with it (more than 30 minutes), but it is there: converting the expense alone would count it twice
        ledger.save(income("credit", t0 + 2 * 60 * minute, account = "save", amount = 450_000), EntryDetails(merchant = "Credit"))
        assertEquals(emptyList(), scan().selfTransfers.map { it.entry.id }); assertTrue(store.otherHalfLive("self")); assertTrue(store.otherHalfLive("credit"))
    }

    @Test
    fun anIncomeOnTheSameAccountOrOfAnotherAmountOrMoreThanThirtySixHoursAwayIsNotTheOtherHalf() {
        ledger.save(expense("self", t0, amount = 450_000), EntryDetails(merchant = "Self transfer"))
        ledger.save(income("sameAccount", t0 + minute, account = "bank", amount = 450_000), EntryDetails(merchant = "Refund"))
        ledger.save(income("otherAmount", t0 + minute, account = "save", amount = 450_001), EntryDetails(merchant = "Credit"))
        ledger.save(income("late", t0 + 37 * 60 * minute, account = "save", amount = 450_000), EntryDetails(merchant = "Credit"))
        assertTrue(!store.otherHalfLive("self")); assertEquals(listOf("self"), scan().selfTransfers.map { it.entry.id })
    }

    @Test
    fun aPairWhoseIncomeHasAnAttachmentIsNotOffered() {
        ledger.save(expense("x", t0, amount = 900_000), EntryDetails(merchant = "Funds transfer to savings"))
        ledger.save(income("i", t0 + 2 * minute, amount = 900_000), EntryDetails(merchant = "Transfer received"))
        assertEquals(1, scan().ownAccounts.size)
        attachments.add("photo", "i", "slip.jpg", "image/jpeg", 100, "h")
        assertEquals(0, scan().ownAccounts.size)
    }
}
