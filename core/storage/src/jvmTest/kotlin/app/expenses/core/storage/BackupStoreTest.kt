package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** One in-memory device: every store the backup touches. */
class Device(node: String) {
    val inr = Currency.INR
    val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    val clock = HlcClock(node)
    val categories = CategoryStore(db, clock) { 5_000L }
    val ledger = LedgerStore(db, clock) { 5_000L }
    val templates = TemplateStore(db, clock) { 5_000L }
    val recurring = RecurringStore(db, clock, { 5_000L }, ledger)
    val budgets = BudgetStore(db, clock, { 5_000L }, ledger, recurring)
    val notifications = NotificationService(db, recurring, budgets, CardStore(db, clock) { 1L }, ReportStore(db, ledger))
    val backup = BackupStore(db, clock, { 5_000L }, ledger, templates, recurring)
    fun m(v: Long) = Money(v, inr)

    /** A ledger with one of everything. */
    fun populate() {
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(AccountId("oldcard"), "Old card", AccountType.CREDIT_CARD, inr, openingMinor = -50_000, archived = true))
        val pets = categories.create("Pets")
        categories.create("Vet", parentId = pets.id, colorArgb = 0xFF00AA00)
        categories.merge("Groceries", "Food") // leaves a tombstone
        ledger.save(Expense(EntryId("coffee"), 1_000, AccountId("cash"), m(12_000), listOf(CategorySplit(CategoryId("Food"), m(12_000)))), EntryDetails(merchant = "Cafe, \"The\" Best", note = "line1\nline2", tags = listOf("Trip", "80C")))
        ledger.save(Expense(EntryId("dinner"), 2_000, AccountId("bank"), m(300_000), listOf(CategorySplit(CategoryId("Food"), m(300_000))), listOf(Share(null, m(100_000)), Share(PersonId("asha"), m(200_000)))))
        ledger.save(Expense(EntryId("split"), 3_000, AccountId("bank"), m(90_000), listOf(CategorySplit(CategoryId("Food"), m(50_000)), CategorySplit(CategoryId(pets.id), m(40_000)))))
        ledger.save(Expense(EntryId("hotel"), 4_000, AccountId("bank"), m(830_000), listOf(CategorySplit(CategoryId("Fun"), m(830_000))), original = Money(10_000, Currency.USD)))
        ledger.save(Refund(EntryId("refund"), 5_000, AccountId("bank"), m(10_000), EntryId("coffee")))
        ledger.save(Income(EntryId("salary"), 6_000, AccountId("bank"), m(5_000_000), CategoryId("Other")))
        ledger.save(Transfer(EntryId("atm"), 7_000, AccountId("bank"), AccountId("cash"), m(200_000)))
        ledger.save(FriendPaid(EntryId("friend"), 8_000, PersonId("raj"), m(45_000), listOf(CategorySplit(CategoryId("Fun"), m(45_000)))))
        ledger.save(Settlement(EntryId("settle"), 9_000, PersonId("asha"), AccountId("bank"), m(200_000), SettlementDirection.RECEIVED_FROM_PERSON))
        templates.save(Template("t1", "Chai", m(2_000), CategoryId("Food"), AccountId("cash"), "Stall"))
        templates.save(Template("t2", "Auto", null, CategoryId("Transport"), AccountId("cash"), null))
        budgets.setLimit(null, m(5_000_000)); budgets.setLimit(pets.id, m(100_000)); budgets.setPeriod(BudgetPeriod.Monthly(25))
        recurring.save(RecurringItem("rent", "Rent", m(1_500_000), CategoryId("Bills"), AccountId("bank"), "Landlord", Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 8, 31), ScheduleEnd.AfterCount(12)), auto = true))
        recurring.save(RecurringItem("power", "Power", null, CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.WEEK, 2, LocalDate(2026, 9, 1), ScheduleEnd.OnOrBefore(LocalDate(2027, 1, 1)))))
        recurring.save(RecurringItem("sip", "SIP", m(100_000), null, AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 9, 5)), kind = RecurringKind.TRANSFER, toAccount = AccountId("cash")))
        recurring.save(RecurringItem("pay", "Pay", m(5_000_000), CategoryId("Other"), AccountId("bank"), "Acme", Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 9, 1)), auto = true, kind = RecurringKind.INCOME))
        recurring.post("rent", LocalDate(2026, 8, 31), TimeZone.UTC)
        recurring.skip("power", LocalDate(2026, 9, 1))
        SavedSearchStore(db, clock) { 5_000L }.save(SavedSearch("ss", "Big food", text = "swiggy", minRupees = "500", categoryId = "Food", kind = "EXPENSE", range = "THIS_YEAR", tag = "Trip"))
        templates.putSetting("hide_amounts", "1")
        templates.putSetting("home_layout", "QUICK_ENTRIES,BUDGET,BILLS,BALANCES;BILLS")
        notifications.setPrefs(NotificationPrefs(bills = false, quietStartMinute = 23 * 60 + 30, billHour = 18))
    }

    fun entries() = db.schemaQueries.entryCount().executeAsOne()
}

class BackupStoreTest {
    private fun roundTrip(source: Device): BackupData = BackupFile.decode(BackupFile.encode(source.backup.export(), 1L)).second

    @Test
    fun aBackupOfEverythingRestoresToAnEmptyDeviceExactlyAndTheTotalsAgree() {
        val a = Device("a"); a.populate()
        val b = Device("b")
        val report = b.backup.restore(roundTrip(a))
        assertEquals(a.backup.export(), b.backup.export(), "exporting the restored device gives the same backup")
        assertEquals(10, report.added.entries)
        assertEquals(a.ledger.spendByCategory(a.inr), b.ledger.spendByCategory(b.inr), "SQL totals")
        assertEquals(a.ledger.loadLedger().spendByCategory(a.inr), b.ledger.loadLedger().spendByCategory(b.inr), "in-memory ledger")
        assertEquals(b.ledger.spendByCategory(b.inr), b.ledger.loadLedger().spendByCategory(b.inr), "and they agree with each other")
        assertEquals(a.ledger.balances(), b.ledger.balances(), "every account balance, opening balances included")
        assertEquals(a.notifications.prefs(), b.notifications.prefs())
        assertEquals(a.budgets.period(), b.budgets.period())
        assertEquals(a.recurring.due(LocalDate(2026, 10, 1)), b.recurring.due(LocalDate(2026, 10, 1)), "handled occurrences stay handled")
    }

    @Test
    fun restoringTwiceOrOverTheSameDeviceDuplicatesNothing() {
        val a = Device("a"); a.populate()
        val before = a.backup.export()
        val report = a.backup.restore(roundTrip(a))
        assertEquals(BackupCounts(), report.added)
        assertEquals(before, a.backup.export())
        val b = Device("b")
        b.backup.restore(roundTrip(a))
        val second = b.backup.restore(roundTrip(a))
        assertEquals(BackupCounts(), second.added, "the second restore adds nothing")
        assertEquals(a.backup.export(), b.backup.export())
    }

    @Test
    fun editsAndDeletionsMadeSinceTheBackupAreNeverUndone() {
        val a = Device("a"); a.populate()
        val backup = roundTrip(a)
        a.ledger.save(Expense(EntryId("coffee"), 1_000, AccountId("cash"), a.m(15_000), listOf(CategorySplit(CategoryId("Food"), a.m(15_000)))), EntryDetails(merchant = "Edited"))
        a.ledger.delete(EntryId("friend"))
        val report = a.backup.restore(backup)
        assertEquals(BackupCounts(), report.added)
        assertEquals(15_000L, a.db.schemaQueries.entryById("coffee").executeAsOne().amount_minor, "the later edit is kept")
        assertEquals(1L, a.db.schemaQueries.entryById("friend").executeAsOne().deleted, "a deleted entry is not brought back")
    }

    @Test
    fun restoringIntoADeviceWithOtherDataMergesAndTheTotalsAddUp() {
        val a = Device("a"); a.populate()
        val b = Device("b")
        b.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, b.inr))
        b.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, b.inr))
        b.ledger.save(Expense(EntryId("mine"), 500, AccountId("cash"), b.m(7_000), listOf(CategorySplit(CategoryId("Food"), b.m(7_000)))))
        val own = b.ledger.spendByCategory(b.inr).getValue(CategoryId("Food"))
        b.backup.restore(roundTrip(a))
        assertEquals(11L, b.entries())
        assertEquals(own + a.ledger.spendByCategory(a.inr).getValue(CategoryId("Food")), b.ledger.spendByCategory(b.inr).getValue(CategoryId("Food")))
        assertEquals(b.ledger.spendByCategory(b.inr), b.ledger.loadLedger().spendByCategory(b.inr))
    }

    @Test
    fun aRestoreThatBreaksARuleChangesNothing() {
        val a = Device("a"); a.populate()
        val data = a.backup.export()
        val broken = data.copy(entries = data.entries + BackupEntry("ghost", "EXPENSE", 99, accountId = "no-such-account", amountMinor = 100, currency = "INR"))
        val b = Device("b")
        assertFailsWith<IllegalArgumentException> { b.backup.restore(broken) }
        assertEquals(0L, b.entries())
        assertTrue(b.db.schemaQueries.selectAccounts().executeAsList().isEmpty(), "the accounts that came before the bad entry were rolled back too")
        assertEquals(13, b.categories.list().size, "only the seeded defaults (eight for expenses, five for income)")
        b.backup.restore(data) // and a good restore still works afterwards
        assertEquals(10L, b.entries())
    }

    @Test
    fun previewReportsWhatARestoreWouldDoWithoutDoingIt() {
        val a = Device("a"); a.populate()
        val b = Device("b")
        val data = roundTrip(a)
        val preview = b.backup.preview(data)
        assertEquals(0L, b.entries())
        assertEquals(preview, b.backup.restore(data))
        assertEquals(BackupCounts(), b.backup.preview(data).added)
        assertEquals(10, b.backup.preview(data).alreadyHere.entries)
    }

    @Test
    fun aMergedCategoryStillRedirectsAfterARestore() {
        val a = Device("a"); a.populate()
        val b = Device("b"); b.backup.restore(roundTrip(a))
        b.ledger.saveAccount(Account(AccountId("x"), "X", AccountType.CASH, b.inr))
        b.ledger.save(Expense(EntryId("late"), 1, AccountId("x"), b.m(100), listOf(CategorySplit(CategoryId("Groceries"), b.m(100)))))
        assertEquals(a.m(100), b.ledger.spendByCategory(b.inr).getValue(CategoryId("Food")) - a.ledger.spendByCategory(a.inr).getValue(CategoryId("Food")), "an edit with the old merged id lands in Food")
    }

    // ---- the file ----

    private val sample: BackupData = Device("a").also { it.populate() }.backup.export()

    @Test
    fun aDamagedOrCutOffFileIsRefusedWithAReasonBeforeAnythingIsRead() {
        val good = BackupFile.encode(sample, 1L)
        val flipped = good.copyOf().also { it[good.size - 20] = (it[good.size - 20] + 1).toByte() }
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(flipped) }.message!!.contains("damaged"))
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(good.copyOf(good.size - 50)) }.message!!.contains("incomplete"))
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode("hello".encodeToByteArray()) }.message!!.contains("not a backup"))
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(ByteArray(0)) }.message!!.contains("not a backup"))
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode("{\"a\":1}\n{}".encodeToByteArray()) }.message!!.contains("not a backup"))
        assertEquals(sample, BackupFile.decode(good).second)
    }

    private fun withHeader(version: Int, payload: String, format: String = BackupFile.FORMAT): ByteArray {
        val p = payload.encodeToByteArray()
        return ("""{"format":"$format","version":$version,"createdAt":1,"payloadBytes":${p.size},"sha256":"${Sha256.hex(p)}"}""" + "\n").encodeToByteArray() + p
    }

    @Test
    fun aNewerVersionIsRefusedAndUnknownFieldsFromALaterMinorChangeAreIgnored() {
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(withHeader(2, "{}")) }.message!!.contains("newer version"))
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(withHeader(1, "{}", format = "something-else")) }.message!!.contains("not a backup"))
        assertEquals(1, BackupFile.decode(withHeader(1, """{"accounts":[{"id":"a","name":"A","type":"CASH","currency":"INR","icon":"wallet"}],"futureThing":[1,2,3]}""")).second.accounts.size)
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(withHeader(1, """{"accounts":"nope"}""")) }.message!!.contains("could not be read"))
    }

    @Test
    fun aRenamedOrHiddenDefaultComesBackButOneUsedOnThisDeviceIsLeaveAlone() {
        val a = Device("a")
        a.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, a.inr))
        a.categories.rename("Fun", "Entertainment"); a.categories.setHidden("Health", true); a.categories.setColor("Bills", 0xFF112233)
        val b = Device("b")
        b.backup.restore(roundTrip(a))
        assertEquals(a.categories.list(), b.categories.list(), "a new device gets the renamed, hidden and coloured defaults")
        // a device where "Transport" already has an entry keeps its own version
        val c = Device("c")
        c.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, c.inr))
        c.ledger.save(Expense(EntryId("t"), 1, AccountId("cash"), c.m(100), listOf(CategorySplit(CategoryId("Transport"), c.m(100)))))
        val src = Device("s"); src.categories.rename("Transport", "Travel")
        c.backup.restore(roundTrip(src))
        assertEquals("Transport", c.categories.list().first { it.id == "Transport" }.name)
    }

    // ---- a file that has been through email, a chat or a cloud drive ----

    private fun sampleFile(): ByteArray { val a = Device("a"); a.populate(); return BackupFile.encode(a.backup.export(), 1L) }

    @Test
    fun aByteOrderMarkBlankSpaceInFrontOrANewlineBehindDoesNotMakeABackupUnreadable() {
        val good = sampleFile()
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val expected = BackupFile.decode(good).second
        assertEquals(expected, BackupFile.decode(bom + good).second, "byte-order mark")
        assertEquals(expected, BackupFile.decode("\r\n  ".encodeToByteArray() + good).second, "blank space in front")
        assertEquals(expected, BackupFile.decode(good + "\r\n".encodeToByteArray()).second, "newline behind")
        // but data added behind that is not blank space is still refused
        assertFailsWith<BackupException> { BackupFile.decode(good + "x".encodeToByteArray()) }
    }

    @Test
    fun aFileThatIsNotABackupSaysWhatItLooksLikeAndGivesItsSizeAndFirstBytesButNoneOfItsWords() {
        fun message(bytes: ByteArray) = assertFailsWith<BackupException> { BackupFile.decode(bytes) }.message.orEmpty()
        assertTrue(message(ByteArray(0)).contains("empty"))
        assertTrue(message("PK\u0003\u0004zip".encodeToByteArray()).contains("zip or Excel"))
        assertTrue(message("<html>Sign in</html>".encodeToByteArray()).contains("web page"))
        val other = message("hello private words\nsecond line".encodeToByteArray())
        assertTrue(other.contains("not a backup file from this app") && other.contains("31 bytes") && other.contains("68 65 6c 6c 6f"), other)
        assertTrue(!other.contains("private"), "the words of the file are never repeated")
        val wrong = message(withHeader(1, "{}", format = "other-app"))
        assertTrue(wrong.contains("other-app"), wrong)
    }
}

