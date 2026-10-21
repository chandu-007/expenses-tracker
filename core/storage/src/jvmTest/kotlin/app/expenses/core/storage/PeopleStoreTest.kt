package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PeopleStoreTest {
    private val d = Device("a")
    private val people = PeopleStore(d.db)
    private fun m(v: Long, c: Currency = Currency.INR) = Money(v, c)
    private fun gave(id: String, p: Person, minor: Long, at: Long) = d.ledger.save(Settlement(EntryId(id), at, PersonId(p.id), AccountId("bank"), m(minor), SettlementDirection.PAID_TO_PERSON))
    private fun got(id: String, p: Person, minor: Long, at: Long) = d.ledger.save(Settlement(EntryId(id), at, PersonId(p.id), AccountId("bank"), m(minor), SettlementDirection.RECEIVED_FROM_PERSON))
    private fun owed(p: Person) = people.balances("INR").first { it.person.id == p.id }.minor

    init { d.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR)) }

    @Test
    fun aNameMakesOnePersonWhateverTheCapitalisationOrSpacingAndBadNamesAreRefused() {
        val a = people.create("  Chandu   Rao ")
        assertEquals("Chandu Rao", a.name)
        assertEquals(a, people.create("chandu rao"), "the same person again is not a second person")
        assertEquals(listOf(a), people.list())
        assertFailsWith<IllegalArgumentException> { people.create("   ") }
        assertFailsWith<IllegalArgumentException> { people.create("x".repeat(PeopleStore.MAX_NAME + 1)) }
        assertEquals(PeopleStore.MAX_NAME, people.create("y".repeat(PeopleStore.MAX_NAME)).name.length)
    }

    @Test
    fun whatAPersonOwesIsWhatWasGivenLessWhatWasGotBackIncludingPartPayments() {
        val c = people.create("Chandu")
        gave("l1", c, 500_000, 1_000)
        assertEquals(500_000, owed(c), "lent 5,000: Chandu owes 5,000")
        got("p1", c, 150_000, 2_000)
        assertEquals(350_000, owed(c), "a part payment of 1,500 leaves 3,500")
        got("p2", c, 350_000, 3_000)
        assertEquals(0, owed(c), "paid in full: settled up")
        got("b1", c, 20_000, 4_000)
        assertEquals(-20_000, owed(c), "money got beyond that is borrowed: now you owe 200")
    }

    @Test
    fun balancesAreKeptPerPersonAndPerCurrencyAndIgnoreDeletedEntries() {
        val a = people.create("Asha"); val b = people.create("Bala"); val z = people.create("Zoya"); val y = people.create("Yash")
        gave("a1", a, 100_000, 1_000); gave("b1", b, 900_000, 2_000); got("a2", a, 30_000, 3_000); got("y1", y, 950_000, 3_500)
        d.ledger.saveAccount(Account(AccountId("usd"), "Dollar", AccountType.BANK, Currency.USD))
        d.ledger.save(Settlement(EntryId("usd"), 4_000, PersonId(a.id), AccountId("usd"), m(5_000, Currency.USD), SettlementDirection.PAID_TO_PERSON))
        gave("gone", z, 77_000, 5_000); d.ledger.delete(EntryId("gone"))
        val rows = people.balances("INR")
        assertEquals(listOf("Yash", "Bala", "Asha", "Zoya"), rows.map { it.person.name }, "largest balance first whichever way it points, the settled-up after")
        assertEquals(listOf(-950_000L, 900_000L, 70_000L, 0L), rows.map { it.minor })
        assertEquals(listOf(1, 1, 2, 0), rows.map { it.entries })
        assertEquals(listOf(3_500L, 2_000L, 3_000L, 0L), rows.map { it.lastAt })
        assertEquals(5_000, people.balances("USD").first { it.person.name == "Asha" }.minor, "another currency is its own balance")
    }

    @Test
    fun aPersonWithEntriesStaysOnTheListAndAnUnusedOneCanBeRemoved() {
        val a = people.create("Asha"); val b = people.create("Bala")
        gave("a1", a, 100, 1_000)
        assertTrue(assertFailsWith<IllegalArgumentException> { people.remove(a.id) }.message!!.contains("stay on the list"))
        people.remove(b.id)
        assertEquals(listOf(a), people.list())
        assertEquals(null, people.get(b.id))
        assertEquals("bala", people.create("bala").name, "a removed name can be used again, as typed now")
    }

    @Test
    fun peopleAndTheirEntriesSurviveABackupAndARestoreOnAnotherDevice() {
        val c = people.create("Chandu"); val gone = people.create("Old friend"); people.remove(gone.id)
        gave("l1", c, 500_000, 1_000); got("p1", c, 100_000, 2_000)
        val (_, data) = BackupFile.decode(BackupFile.encode(d.backup.export(), 1L))
        val other = Device("b"); val otherPeople = PeopleStore(other.db)
        other.backup.restore(data)
        assertEquals(listOf(c), otherPeople.list(), "the name came with the backup, so the entries are not nameless")
        assertEquals(400_000, otherPeople.balances("INR").single().minor)
        other.backup.restore(data) // twice changes nothing
        assertEquals(1, otherPeople.list().size)
    }

    // ---- a bill shared with friends: one expense, your share is spending, theirs is owed to you ----

    private fun sharedDinner(id: String, with: List<Person>, total: Long, at: Long) {
        val (each, mine) = app.expenses.core.domain.BillSplit.shares(total, with.size)!!
        d.ledger.save(app.expenses.core.domain.ledger.Expense(EntryId(id), at, AccountId("bank"), m(total), listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("Food"), m(total))),
            listOf(app.expenses.core.domain.ledger.Share(null, m(mine))) + with.map { app.expenses.core.domain.ledger.Share(PersonId(it.id), m(each)) }))
    }

    @Test
    fun aSharedBillIsOwedByEachFriendForTheirShareAndOnlyYourShareIsSpentAndTheAccountPaysAll() {
        val a = people.create("Asha"); val r = people.create("Ravi")
        sharedDinner("dinner", listOf(a, r), 90_001, 1_000)
        assertEquals(30_000L, owed(a)); assertEquals(30_000L, owed(r))
        assertEquals(30_001L, d.ledger.spendByCategory(Currency.INR)[app.expenses.core.domain.ledger.CategoryId("Food")]?.minorUnits, "your share, with the odd paisa")
        assertEquals(-90_001L, d.ledger.balances()[AccountId("bank")]!!.minorUnits, "the account paid the whole bill")
        assertEquals(1, people.balances("INR").first { it.person.id == a.id }.entries)
        assertFailsWith<IllegalArgumentException> { people.remove(a.id) }
        // paying their share back settles it, as for money lent
        got("back", a, 30_000, 2_000)
        assertEquals(0L, owed(a)); assertEquals(30_000L, owed(r))
        // deleting the bill takes the shares with it
        d.ledger.delete(EntryId("dinner"))
        assertEquals(-30_000L, owed(a), "they paid you back for a bill that is gone: you owe them that"); assertEquals(0L, owed(r))
    }

    @Test
    fun aSharedBillAddsToWhatWasLentAndOtherCurrenciesAreNotMixedIn() {
        val a = people.create("Asha")
        gave("loan", a, 10_000, 500)
        sharedDinner("d1", listOf(a), 20_000, 1_000)
        assertEquals(20_000L, owed(a), "10,000 lent and 10,000 share")
        assertEquals(0L, people.balances("USD").first { it.person.id == a.id }.minor)
    }

    @Test
    fun theSharesOfABillTravelInABackupAndComeBackOnAnotherDevice() {
        val a = people.create("Asha"); val r = people.create("Ravi")
        sharedDinner("dinner", listOf(a, r), 90_000, 1_000)
        val data = d.backup.export()
        val other = Device("b")
        other.backup.restore(data)
        val otherPeople = PeopleStore(other.db)
        assertEquals(listOf(30_000L, 30_000L), otherPeople.balances("INR").filter { it.person.name in setOf("Asha", "Ravi") }.map { it.minor })
        assertEquals(30_000L, other.ledger.spendByCategory(Currency.INR)[app.expenses.core.domain.ledger.CategoryId("Food")]?.minorUnits, "only your share is spending on the other device too")
        assertEquals(3, other.db.schemaQueries.allShares().executeAsList().size)
    }
}

