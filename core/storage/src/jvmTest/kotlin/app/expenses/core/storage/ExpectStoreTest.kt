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
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "I expect this back": the side table, how refunds shrink it, and what a backup does with it. */
class ExpectStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)

    private class Rig {
        val d = Device("a")
        val expects = ExpectStore(d.db)
        init {
            d.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, d.inr))
            d.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, d.inr))
        }
        fun expense(id: String, minor: Long, at: Long = 1_000, merchant: String = "Dinner") =
            d.ledger.save(Expense(EntryId(id), at, AccountId("bank"), d.m(minor), listOf(CategorySplit(CategoryId("Food"), d.m(minor)))), EntryDetails(merchant = merchant))
        fun refund(id: String, of: String, minor: Long, account: String = "bank") = d.ledger.save(Refund(EntryId(id), 2_000, AccountId(account), d.m(minor), EntryId(of)))
    }

    @Test
    fun anExpectationIsKeptWithWhoFromAndHowMuchAndListedWhileWaiting() {
        val r = Rig()
        r.expense("dinner", 100_000)
        r.d.db.schemaQueries.insertPersonRaw("asha", "Asha", 0)
        r.expects.set("dinner", "  the office  ", "asha", 60_000)
        assertEquals(Expectation("dinner", "the office", "asha", 60_000, false), r.expects.get("dinner"))
        val w = r.expects.waiting().single()
        assertEquals("Dinner", w.title); assertEquals(60_000, w.expectedMinor); assertEquals(60_000, w.remainingMinor); assertEquals(0, w.gotMinor); assertEquals("bank", w.accountId); assertEquals("asha", w.personId)
        assertFailsWith<IllegalArgumentException> { r.expects.set("dinner", null, null, 0) }
        r.expects.set("dinner", " ", "", 100_000) // blank text and id are stored as none, and setting again replaces
        assertEquals(Expectation("dinner", null, null, 100_000, false), r.expects.get("dinner"))
    }

    @Test
    fun aRefundShrinksWhatIsWaitingAndTheLastOneClosesIt() {
        val r = Rig()
        r.expense("dinner", 100_000)
        r.expects.set("dinner", "Asha", null, 80_000)
        r.refund("back1", "dinner", 30_000, account = "cash")
        assertEquals(50_000, r.expects.waiting().single().remainingMinor, "a partial refund leaves the rest waiting")
        assertEquals(30_000, r.expects.waiting().single().gotMinor)
        r.refund("back2", "dinner", 50_000)
        assertTrue(r.expects.waiting().isEmpty(), "the expected amount is back: nothing is waiting")
        assertEquals(false, r.expects.get("dinner")!!.closed, "closing is worked out from the refunds, not stored twice")
        // the refund is deleted again (Undo): it is waiting again
        r.d.ledger.delete(EntryId("back2"))
        assertEquals(50_000, r.expects.waiting().single().remainingMinor)
    }

    @Test
    fun theExpenseStillCountsAsSpendingUntilARefundReducesIt() {
        val r = Rig()
        r.expense("dinner", 100_000)
        r.expects.set("dinner", null, null, 100_000)
        assertEquals(m(100_000), r.d.ledger.spendByCategory(inr).getValue(CategoryId("Food")), "expecting it back changes no report")
        r.refund("back", "dinner", 100_000)
        assertNull(r.d.ledger.spendByCategory(inr)[CategoryId("Food")], "the refund takes it off, as any refund does")
    }

    @Test
    fun notExpectingItAnyMoreClosesItWithoutMoneyAndCanBeTakenBack() {
        val r = Rig()
        r.expense("dinner", 100_000)
        r.expects.set("dinner", null, null, 100_000)
        assertTrue(r.expects.close("dinner"))
        assertTrue(r.expects.waiting().isEmpty())
        assertFalse(r.expects.close("dinner"), "closing twice is a no-op")
        assertFalse(r.expects.close("nothing"))
        r.expects.reopen("dinner")
        assertEquals(1, r.expects.waiting().size)
        assertEquals(m(100_000), r.d.ledger.spendByCategory(inr).getValue(CategoryId("Food")))
    }

    @Test
    fun setReopensAClosedOneAndClearForgetsIt() {
        val r = Rig()
        r.expense("dinner", 100_000)
        r.expects.set("dinner", null, null, 100_000); r.expects.close("dinner")
        r.expects.set("dinner", "Raj", null, 40_000)
        assertEquals(40_000, r.expects.waiting().single().remainingMinor)
        r.expects.clear("dinner")
        assertNull(r.expects.get("dinner")); assertTrue(r.expects.waiting().isEmpty())
    }

    @Test
    fun aDeletedExpenseLeavesTheListAndComesBackWithTheExpense() {
        val r = Rig()
        r.expense("dinner", 100_000)
        r.expects.set("dinner", null, null, 100_000)
        r.d.ledger.delete(EntryId("dinner"))
        assertTrue(r.expects.waiting().isEmpty()); assertTrue(r.expects.all().isEmpty(), "a backup carries none for a deleted expense")
        r.d.ledger.restore(EntryId("dinner"))
        assertEquals(1, r.expects.waiting().size)
    }

    @Test
    fun anExpenseEditedDownCapsWhatIsExpectedAndTheListIsNewestFirst() {
        val r = Rig()
        r.expense("old", 100_000, at = 1_000, merchant = "Old")
        r.expense("new", 50_000, at = 9_000, merchant = "New")
        r.expects.set("old", null, null, 100_000); r.expects.set("new", null, null, 50_000)
        assertEquals(listOf("New", "Old"), r.expects.waiting().map { it.title })
        r.expense("old", 60_000, at = 1_000, merchant = "Old") // edited down: the expectation was for 100,000
        assertEquals(60_000, r.expects.waiting().first { it.entryId == "old" }.remainingMinor)
    }

    // ---- backup ----

    private fun withExpectations(): Rig {
        val r = Rig()
        r.expense("dinner", 100_000); r.expense("taxi", 25_000, merchant = "Taxi"); r.expense("lunch", 40_000, merchant = "Lunch")
        r.d.db.schemaQueries.insertPersonRaw("asha", "Asha", 0)
        r.expects.set("dinner", null, "asha", 80_000)
        r.expects.set("taxi", "the office", null, 25_000); r.expects.close("taxi")
        return r
    }

    @Test
    fun aBackupCarriesExpectationsAndRestoresThemOnAnEmptyDevice() {
        val a = withExpectations()
        val data = BackupFile.decode(BackupFile.encode(a.d.backup.export(), 1L)).second
        assertEquals(listOf(BackupExpect("dinner", null, "asha", 80_000, false), BackupExpect("taxi", "the office", null, 25_000, true)), data.expects)
        val b = Device("b"); val bx = ExpectStore(b.db)
        b.backup.restore(data)
        assertEquals(a.d.backup.export(), b.backup.export(), "the restored device exports the same backup, expectations included")
        assertEquals(listOf("dinner"), bx.waiting().map { it.entryId })
        assertEquals("Asha", b.db.schemaQueries.personById("asha").executeAsOne().name)
        assertTrue(bx.get("taxi")!!.closed, "a closed one stays closed")
        // twice adds nothing, and what the person did on this device since is never overridden
        bx.close("dinner")
        b.backup.restore(data)
        assertTrue(bx.get("dinner")!!.closed)
    }

    @Test
    fun anExpectationIsNotRestoredOverAnExpenseAlreadyHereWhereThePersonClearedIt() {
        val a = withExpectations()
        val data = a.d.backup.export()
        a.expects.clear("dinner")
        a.d.backup.restore(data)
        assertNull(a.expects.get("dinner"), "the expense was already on this device: its decision stands")
    }

    @Test
    fun aPersonThatIsNotThereIsDroppedButTheFreeTextStays() {
        val a = Device("a"); ExpectStore(a.db)
        val data = withExpectations().d.backup.export().let { it.copy(people = emptyList(), expects = listOf(BackupExpect("dinner", "Asha (typed)", "ghost", 80_000))) }
        a.backup.restore(data)
        assertEquals(Expectation("dinner", "Asha (typed)", null, 80_000, false), ExpectStore(a.db).get("dinner"))
    }

    @Test
    fun aBackupWithoutExpectationsIsExactlyAsItWasBeforeTheKeyExisted() {
        val d = Device("a"); d.populate()
        val text = BackupFile.encode(d.backup.export(), 1L).decodeToString()
        assertFalse(text.contains("\"expects\""), "the key is left out when there is none")
        assertEquals(emptyList(), d.backup.export().expects)
    }

    @Test
    fun anOldBackupWithNoExpectsKeyRestoresAndANewOneWithTheKeyIsReadByOlderReadersThatIgnoreIt() {
        // an old file: no "expects"
        val old = """{"accounts":[{"id":"a","name":"A","type":"CASH","currency":"INR"}],"currencies":[{"code":"INR","digits":2}],"entries":[{"id":"e","kind":"EXPENSE","occurredAt":5,"accountId":"a","amountMinor":100,"currency":"INR"}]}"""
        val p = old.encodeToByteArray()
        val file = ("""{"format":"expenses-backup","version":1,"createdAt":1,"payloadBytes":${p.size},"sha256":"${Sha256.hex(p)}"}""" + "\n").encodeToByteArray() + p
        val b = Device("b")
        b.backup.restore(BackupFile.decode(file).second)
        assertEquals(1L, b.entries())
        assertTrue(ExpectStore(b.db).all().isEmpty())
        // a new file read as an older reader would: it knows nothing about "expects" and ignores the key
        val newer = BackupFile.encode(withExpectations().d.backup.export(), 1L).decodeToString()
        assertTrue(newer.contains("\"expects\":["))
        val lenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        @kotlinx.serialization.Serializable class OldReader(val accounts: List<BackupAccount> = emptyList(), val entries: List<BackupEntry> = emptyList())
        assertEquals(2, lenient.decodeFromString(OldReader.serializer(), newer.substringAfter('\n')).accounts.size)
    }

    @Test
    fun aSharedExpenseIsNotAnExpectation() {
        // the ledger keeps refunds of shared expenses out, so there is nothing for an expectation to be paid back by
        val r = Rig()
        r.d.db.schemaQueries.insertPersonRaw("asha", "Asha", 0)
        r.d.ledger.save(Expense(EntryId("shared"), 1, AccountId("bank"), m(100_000), emptyList(), listOf(Share(null, m(50_000)), Share(PersonId("asha"), m(50_000)))))
        assertFailsWith<IllegalArgumentException> { r.refund("back", "shared", 50_000) }
    }

    @Test
    fun theSettingsThatTravelWithABackupIncludeTheTwoNewSwitches() {
        val a = Device("a")
        a.db.schemaQueries.putSetting("safe_to_spend_on_home", "0"); a.db.schemaQueries.putSetting("sinking_funds_on", "1")
        val b = Device("b")
        b.backup.restore(a.backup.export())
        assertEquals("0", b.db.schemaQueries.settingValue("safe_to_spend_on_home").executeAsOneOrNull())
        assertEquals("1", b.db.schemaQueries.settingValue("sinking_funds_on").executeAsOneOrNull())
    }

    @Test
    fun anOlderDatabaseWithoutTheTableGetsItFromTheUpgradeAndKeepsItsEntries() {
        val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
        d.execute(null, "DROP TABLE entry_expect", 0)
        val db = ExpensesDb(d)
        assertFailsWith<Exception> { ExpectStore(db).waiting() }
        SchemaUpgrade.ensureLatest(d); SchemaUpgrade.ensureLatest(d)
        val ledger = LedgerStore(db, HlcClock("n")) { 1L }
        ledger.saveAccount(Account(AccountId("a"), "A", AccountType.BANK, Currency.INR))
        ledger.save(Expense(EntryId("e"), 1, AccountId("a"), Money(100, Currency.INR)))
        ExpectStore(db).set("e", "Raj", null, 100)
        assertEquals(1, ExpectStore(db).waiting().size)
    }
    @Test
    fun theWaitingListNeverWalksTheEntriesItRunsOnEveryRefreshOfHome() {
        // the table of expectations is the outer loop and each expense is found by its id: with 20,000 entries and nothing expected it must still be (nearly) free
        val r = Rig()
        r.d.db.transaction { for (i in 0 until 20_000) r.d.db.schemaQueries.insertEntry("e$i", "EXPENSE", 1_000L + i, "bank", null, 100L, "INR", "Food", "Shop $i", null, "manual", null) }
        val plan = ArrayList<String>()
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
        driver.executeQuery(null, "EXPLAIN QUERY PLAN SELECT x.entry_id FROM entry_expect x CROSS JOIN entry e ON e.id = x.entry_id WHERE x.closed = 0 AND e.deleted = 0 AND e.kind = 'EXPENSE'", { c ->
            while (c.next().value) plan += c.getString(3).orEmpty()
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
        assertTrue(plan.any { it.startsWith("SCAN x") }, plan.toString())
        assertTrue(plan.none { it.startsWith("SCAN e") }, plan.toString())
        val started = System.nanoTime()
        repeat(50) { assertTrue(r.expects.waiting().isEmpty()) }
        val ms = (System.nanoTime() - started) / 50 / 1e6
        assertTrue(ms < 5.0, "waiting() took $ms ms with 20,000 entries and nothing expected")
    }
}
