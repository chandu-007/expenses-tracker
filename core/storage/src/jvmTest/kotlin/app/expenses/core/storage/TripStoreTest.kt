package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Flow
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.Trip
import app.expenses.core.domain.TripRules
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.Entry
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Travel mode in the store: the trips, the one hook that tags a NEW expense by its own date, every way an entry is created, the counted "tag earlier entries" with its Undo, and the trip's figures. Invented data only. */
class TripStoreTest {
    private val ist = TimeZone.of("Asia/Kolkata")
    private val inr = Currency.INR
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val clock = HlcClock("n")
    private var now = 1_000L
    private val ledger = LedgerStore(db, clock) { now }.also { it.zone = { ist } }
    private val trips get() = ledger.trips
    private val tagging = TripTagging(ledger)
    private val q get() = db.schemaQueries
    private fun m(v: Long) = Money(v, inr)

    private fun day(y: Int, mo: Int, d: Int) = LocalDate(y, mo, d).toEpochDays().toLong()
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, min: Int = 0, sec: Int = 0, nano: Int = 0) = LocalDateTime(y, mo, d, h, min, sec, nano).toInstant(ist).toEpochMilliseconds()
    private val goa = Trip("goa", "Goa", "Trip: Goa", day(2026, 11, 3), day(2026, 11, 9))

    init {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
    }

    private fun expense(id: String, at: Long, amt: Long = 10_000, cat: String = "Food") = Expense(EntryId(id), at, AccountId("cash"), m(amt), listOf(CategorySplit(CategoryId(cat), m(amt))))
    private fun tags(id: String) = ledger.tagsOf(EntryId(id))
    private fun create(e: Entry, vararg tag: String, trip: Boolean = true, explicit: Boolean = tag.isNotEmpty()) = ledger.save(e, EntryDetails(merchant = e.id.value, tags = if (explicit) tag.toList() else null, tripTag = trip))
    private fun ops(row: String) = q.opsOfRow("trip", row).executeAsList()

    // ---- the trips ----

    @Test
    fun tripsAreKeptNewestFirstAndEveryFieldChangeIsLoggedOnceAndANoChangeSaveLogsNothing() {
        val later = Trip("later", "Kerala", "Trip: Kerala", day(2026, 12, 20), day(2026, 12, 27), true, 250_000)
        trips.save(goa); trips.save(later)
        assertEquals(listOf("later", "goa"), trips.all().map { it.id })
        assertEquals(later, trips.get("later")); assertEquals(2, trips.count())
        assertEquals(6, ops("goa").size, "name, tag, both days, enabled and budget are logged for a new trip (an empty budget as an empty value)")
        trips.save(goa); assertEquals(6, ops("goa").size, "saving the same trip again changes nothing")
        trips.save(goa.copy(endDay = day(2026, 11, 12))); assertEquals(7, ops("goa").size)
        assertEquals(day(2026, 11, 12).toString(), ops("goa").last().value_)
        trips.delete("goa"); assertNull(trips.get("goa")); assertEquals("deleted", ops("goa").last().field_)
        trips.delete("goa"); trips.delete("nothing-here"); assertEquals(8, ops("goa").size, "deleting what is not there is a no-op")
    }

    @Test
    fun anOverlapIsRefusedWithTheOtherTripsNameAndNothingIsChanged() {
        trips.save(goa)
        val e = assertFailsWith<IllegalArgumentException> { trips.save(Trip("mum", "Mumbai", "Trip: Mumbai", day(2026, 11, 9), day(2026, 11, 12))) }
        assertEquals("These dates overlap your trip \"Goa\" (3 Nov – 9 Nov 2026), which is on. Change the dates, or turn that trip off.", e.message)
        assertEquals(listOf("goa"), trips.all().map { it.id })
        // the edit of a trip's own dates is not an overlap with itself
        trips.save(goa.copy(startDay = day(2026, 11, 1), endDay = day(2026, 11, 20)))
        assertEquals(day(2026, 11, 20), trips.get("goa")!!.endDay)
        // a trip that is off may sit on the days; switching it on is refused while the other is on, and allowed once the other is off
        trips.save(Trip("off", "Maybe", "Trip: Maybe", day(2026, 11, 5), day(2026, 11, 6), enabled = false))
        assertFailsWith<IllegalArgumentException> { trips.save(trips.get("off")!!.copy(enabled = true)) }
        assertFalse(trips.get("off")!!.enabled)
        trips.save(trips.get("goa")!!.copy(enabled = false))
        trips.save(trips.get("off")!!.copy(enabled = true))
        assertTrue(trips.get("off")!!.enabled)
        assertFailsWith<IllegalArgumentException> { trips.save(trips.get("goa")!!.copy(enabled = true)) }
    }

    @Test
    fun theStoreChecksTheRulesAgainWhateverTheCallerDid() {
        for (bad in listOf(goa.copy(id = ""), goa.copy(name = ""), goa.copy(name = " x"), goa.copy(tag = "a,b"), goa.copy(endDay = goa.startDay - 1), goa.copy(startDay = 5), goa.copy(endDay = goa.startDay + 366), goa.copy(dailyBudgetMinor = 0), goa.copy(dailyBudgetMinor = -1))) {
            assertFailsWith<IllegalArgumentException>("$bad") { trips.save(bad) }
        }
        assertEquals(0, trips.count())
    }

    @Test
    fun aPersonKeepsAtMostTwoHundredTrips() {
        val start = day(2026, 1, 1)
        for (i in 0 until TripRules.MAX_TRIPS) trips.save(Trip("t$i", "Trip $i", "tag $i", start + i * 2, start + i * 2 + 1))
        val e = assertFailsWith<IllegalArgumentException> { trips.save(Trip("extra", "Extra", "extra", start + 1_000, start + 1_001)) }
        assertEquals("You can keep up to 200 trips. Delete an old one first.", e.message)
        trips.save(trips.get("t5")!!.copy(name = "Renamed")) // an edit is not a new trip
        assertEquals(TripRules.MAX_TRIPS, trips.count())
    }

    // ---- the one hook: a NEW expense is tagged by its own date ----

    @Test
    fun anExpenseMadeInsideATripIsTaggedAtCreationAndTheEdgesOfTheDaysAreExactInIndia() {
        trips.save(goa)
        val cases = listOf(
            "before" to (at(2026, 11, 3, 0, 0) - 1), "first00" to at(2026, 11, 3, 0, 0), "mid" to at(2026, 11, 6, 13, 30),
            "last2359" to at(2026, 11, 9, 23, 59, 59, 999_000_000), "after00" to at(2026, 11, 10, 0, 0),
        )
        for ((id, t) in cases) create(expense(id, t))
        assertEquals(listOf("before", "after00"), cases.map { it.first }.filter { tags(it).isEmpty() }, "the day before the first and the day after the last are not tagged")
        for (id in listOf("first00", "mid", "last2359")) assertEquals(listOf("Trip: Goa"), tags(id), id)
    }

    @Test
    fun theTagIsTheSpellingAlreadyInUseAndItIsMergedWithTheTagsTyped() {
        trips.save(goa)
        create(expense("old", at(2026, 5, 1)), "trip: goa") // an earlier use with other capitals
        create(expense("a", at(2026, 11, 4)), "Food run", "TRIP: GOA", "80C")
        assertEquals(listOf("80C", "Food run", "trip: goa"), tags("a"), "typed twice in other capitals: one tag, in the spelling the ledger already uses, and the typed ones stay")
        create(expense("b", at(2026, 11, 4)), explicit = false)
        assertEquals(listOf("trip: goa"), tags("b"))
        create(expense("c", at(2026, 11, 4)), explicit = true) // an empty list typed: only the trip tag
        assertEquals(listOf("trip: goa"), tags("c"))
    }

    @Test
    fun anEntryThatAlreadyHoldsTenTagsKeepsThemAndTheTripTagIsTheOneLeftOut() {
        trips.save(goa)
        val ten = (1..10).map { "tag$it" }
        create(expense("full", at(2026, 11, 4)), *ten.toTypedArray())
        assertEquals(ten.sorted(), tags("full").sortedBy { it }.sorted()); assertEquals(10, tags("full").size)
        assertFalse(tags("full").any { it.startsWith("Trip") })
        create(expense("nine", at(2026, 11, 4)), *ten.take(9).toTypedArray())
        assertEquals(10, tags("nine").size); assertTrue("Trip: Goa" in tags("nine"))
    }

    @Test
    fun nothingIsTaggedWhenTheTripIsOffWhenTheCallerDoesNotAskAndForAnEntryThatIsNotNew() {
        trips.save(goa.copy(enabled = false))
        create(expense("off", at(2026, 11, 4)))
        assertTrue(tags("off").isEmpty(), "a trip that is off tags nothing")
        trips.save(goa)
        create(expense("quiet", at(2026, 11, 4)), trip = false)
        ledger.save(expense("default", at(2026, 11, 4)), EntryDetails(merchant = "x"))
        assertTrue(tags("quiet").isEmpty() && tags("default").isEmpty(), "tagging is something a path asks for; the default is not to")
        create(expense("typed", at(2026, 11, 4)), "mine", trip = false)
        assertEquals(listOf("mine"), tags("typed"))
    }

    @Test
    fun onlyAnExpenseIsTaggedNotIncomeATransferALoanOrAFriendsPayment() {
        trips.save(goa)
        val t = at(2026, 11, 5)
        create(Income(EntryId("pay"), t, AccountId("bank"), m(5_000), CategoryId("income:other")))
        create(Transfer(EntryId("move"), t, AccountId("bank"), AccountId("cash"), m(5_000)))
        create(Settlement(EntryId("loan"), t, PersonId("asha"), AccountId("bank"), m(5_000), SettlementDirection.RECEIVED_FROM_PERSON))
        create(FriendPaid(EntryId("friend"), t, PersonId("raj"), m(5_000), listOf(CategorySplit(CategoryId("Food"), m(5_000)))))
        create(expense("lunch", t))
        for (id in listOf("pay", "move", "loan", "friend")) assertTrue(tags(id).isEmpty(), id)
        assertEquals(listOf("Trip: Goa"), tags("lunch"))
    }

    // ---- editing never adds or re-adds ----

    @Test
    fun aTagTheTripPutOnAndThePersonTookOffStaysOffThroughEveryLaterEdit() {
        trips.save(goa)
        create(expense("lunch", at(2026, 11, 5)))
        assertEquals(listOf("Trip: Goa"), tags("lunch"))
        ledger.save(expense("lunch", at(2026, 11, 5)), EntryDetails(merchant = "lunch", tags = emptyList()))
        assertTrue(tags("lunch").isEmpty(), "taken off")
        // the note, the amount and the date change; even a caller that wrongly asks for the trip tag cannot put it back, because the entry is not new
        ledger.save(expense("lunch", at(2026, 11, 5), amt = 25_000), EntryDetails(merchant = "lunch", note = "dinner", tags = null, tripTag = true))
        ledger.save(expense("lunch", at(2026, 11, 7, 9, 0), amt = 25_000), EntryDetails(merchant = "lunch", note = "dinner", tags = listOf(), tripTag = true))
        ledger.save(expense("lunch", at(2026, 12, 1), amt = 25_000), EntryDetails(merchant = "lunch", tags = null, tripTag = true))
        assertTrue(tags("lunch").isEmpty())
        // and an entry made before the trip existed is not tagged by editing it into the trip's days
        create(expense("early", at(2026, 10, 1)), trip = true)
        ledger.save(expense("early", at(2026, 11, 5)), EntryDetails(merchant = "early", tripTag = true))
        assertTrue(tags("early").isEmpty())
    }

    // ---- a refund goes with the expense it reverses ----

    private fun refund(id: String, of: String, minor: Long, at: Long) = Refund(EntryId(id), at, AccountId("cash"), m(minor), EntryId(of))

    @Test
    fun aRefundOfATaggedTripExpenseGetsTheTripTagWhateverItsOwnDateAndTheTripsStateAndTheTotalNetsOut() {
        trips.save(goa)
        create(expense("hotel", at(2026, 11, 4), amt = 500_000))
        trips.save(goa.copy(enabled = false)) // the trip is over and off when the money comes back
        create(refund("back", "hotel", 120_000, at(2026, 11, 28)))
        assertEquals(listOf("Trip: Goa"), tags("back"), "its own day is far outside the trip: it counts with the purchase it reverses")
        trips.save(goa)
        val s = trips.lines(goa, inr)
        assertEquals(500_000L, s.lines.sumOf { it.mineMinor }, "the refund of 28 November is outside the trip's days")
        // a refund inside the days nets the trip total out
        create(refund("early", "hotel", 80_000, at(2026, 11, 8)))
        assertEquals(listOf("Trip: Goa"), tags("early"))
        assertEquals(420_000L to 2, trips.totals(goa, inr), "spending 5,000.00 less the refund of 800.00, on two entries (the hotel and the refund)")
        assertEquals(420_000L, trips.lines(goa, inr).lines.sumOf { it.mineMinor })
    }

    @Test
    fun aRefundIsNotTaggedWhenItsExpenseIsNotTaggedOrNoTripCoveredIt() {
        create(expense("before", at(2026, 11, 4)), trip = false) // made before the trip was entered: untagged
        trips.save(goa)
        create(refund("r1", "before", 1_000, at(2026, 11, 5)))
        assertTrue(tags("r1").isEmpty(), "the expense is not tagged, so neither is its refund")
        create(expense("bought", at(2026, 11, 4)))
        ledger.save(expense("bought", at(2026, 11, 4)), EntryDetails(merchant = "bought", tags = emptyList())) // the person took the tag off
        create(refund("r2", "bought", 1_000, at(2026, 11, 5)))
        assertTrue(tags("r2").isEmpty(), "the person took the tag off the expense: the refund stays with them")
        create(expense("other", at(2026, 11, 6)), "Trip: Goa", trip = false)
        trips.delete("goa")
        create(refund("r3", "other", 1_000, at(2026, 11, 7)))
        assertTrue(tags("r3").isEmpty(), "with the trip deleted nothing says it was a trip tag (the reports still count the refund with its expense)")
        create(expense("later", at(2027, 1, 5)), "Trip: Goa", trip = false)
        trips.save(goa)
        create(refund("r4", "later", 1_000, at(2027, 1, 6)))
        assertTrue(tags("r4").isEmpty(), "the expense lies outside the trip's days, so the tag it carries is just a tag")
    }

    // ---- every way an entry is created ----

    @Test
    fun aStatementImportTagsTheExpensesInsideTheTripByTheirOwnDateAndOnlyTheseAndAgainChangesNothing() {
        trips.save(goa)
        val importer = StatementImporter(db, ledger)
        val rows = listOf(
            StatementTxn(1, LocalDate(2026, 11, 2), Flow.SPENDING, 11_100, "BEFORE"), StatementTxn(2, LocalDate(2026, 11, 3), Flow.SPENDING, 22_200, "FIRST DAY"),
            StatementTxn(3, LocalDate(2026, 11, 9), Flow.SPENDING, 33_300, "LAST DAY"), StatementTxn(4, LocalDate(2026, 11, 10), Flow.SPENDING, 44_400, "AFTER"),
            StatementTxn(5, LocalDate(2026, 11, 5), Flow.INCOME, 55_500, "REFUND CREDIT"),
        )
        importer.import(rows, AccountId("bank"), inr, ist)
        val byName = q.liveEntriesInInsertionOrder().executeAsList().associate { it.merchant to it.id }
        for ((name, tagged) in listOf("BEFORE" to false, "FIRST DAY" to true, "LAST DAY" to true, "AFTER" to false, "REFUND CREDIT" to false)) assertEquals(if (tagged) listOf("Trip: Goa") else emptyList(), tags(byName.getValue(name)), name)
        val before = q.allEntryTags().executeAsList().size
        val again = importer.import(rows, AccountId("bank"), inr, ist)
        assertEquals(0, again.imported, "the same file twice adds nothing"); assertEquals(before, q.allEntryTags().executeAsList().size)
    }

    @Test
    fun changingTheTypeNeverTagsAndCarriesTheTagsAsTheyWere() {
        trips.save(goa)
        val changes = KindChanges(db, ledger, OriginStore(db), ExpectStore(db), AttachmentStore(db, clock) { now })
        create(expense("tagged", at(2026, 11, 5)))
        create(expense("plain", at(2026, 11, 5)), trip = false)
        create(Income(EntryId("in"), at(2026, 11, 5), AccountId("bank"), m(9_000), CategoryId("income:other")), "mine")
        val toTransfer = changes.change("tagged", Transfer(EntryId("t1"), at(2026, 11, 5), AccountId("cash"), AccountId("bank"), m(10_000)), "x", tags("tagged"))
        assertEquals(listOf("Trip: Goa"), tags("t1"), "an expense made a transfer carries its tags, as every type change always has")
        val plainToTransfer = changes.change("plain", Transfer(EntryId("t2"), at(2026, 11, 5), AccountId("cash"), AccountId("bank"), m(10_000)), "x", emptyList())
        assertTrue(tags("t2").isEmpty(), "an untagged expense made a transfer is not tagged")
        val incomeToExpense = changes.change("in", expense("e2", at(2026, 11, 5)), "x", listOf("mine"))
        assertEquals(listOf("mine"), tags("e2"), "money in made an expense inside the trip is NOT tagged: it is a change, not a new entry")
        changes.undo(incomeToExpense); changes.undo(plainToTransfer); changes.undo(toTransfer)
        assertEquals(listOf("Trip: Goa"), tags("tagged")); assertTrue(tags("plain").isEmpty()); assertEquals(listOf("mine"), tags("in"))
    }

    @Test
    fun aRepeatingItemPostedInsideTheTripIsNotTagged() {
        trips.save(goa)
        val recurring = RecurringStore(db, clock, { now }, ledger)
        recurring.save(RecurringItem("rent", "Gym", m(100_000), CategoryId("Bills"), AccountId("cash"), "Gym", Schedule(RepeatUnit.DAY, 1, LocalDate(2026, 11, 4))))
        val posted = recurring.post("rent", LocalDate(2026, 11, 4), ist)!!
        assertNotNull(q.entryById(posted).executeAsOneOrNull()); assertTrue(tags(posted).isEmpty())
    }

    @Test
    fun aRestoreNeverAddsTheTripTagToTheEntriesItBringsBack() {
        val a = Device("a"); a.ledger.zone = { ist }
        a.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        a.ledger.trips.save(goa)
        a.ledger.save(expense("in-a", at(2026, 11, 5)), EntryDetails(merchant = "a", tripTag = true)) // tagged
        a.ledger.save(expense("untagged-a", at(2026, 11, 6)), EntryDetails(merchant = "b", tags = emptyList())) // typed empty
        val b = Device("b"); b.ledger.zone = { ist }
        b.ledger.trips.save(goa.copy(id = "other-id")) // the phone being restored into has its own trip covering the same days
        b.backup.restore(BackupFile.decode(BackupFile.encode(a.backup.export(), 1L)).second)
        assertEquals(listOf("Trip: Goa"), b.ledger.tagsOf(EntryId("in-a")))
        assertTrue(b.ledger.tagsOf(EntryId("untagged-a")).isEmpty(), "an entry that had no tag when it was backed up is not tagged by the restore")
    }

    // ---- "Tag N earlier entries" ----

    private fun seedWeek() {
        create(expense("e1", at(2026, 11, 3, 8)), trip = false)
        create(expense("e2", at(2026, 11, 5, 9)), "Food run", trip = false)
        create(expense("e3", at(2026, 11, 9, 23, 59, 59, 999_000_000)), trip = false)
        create(expense("tagged", at(2026, 11, 4)), "Trip: Goa", trip = false) // already has it
        create(expense("outside", at(2026, 11, 10)), trip = false)
        create(expense("gone", at(2026, 11, 6)), trip = false); ledger.delete(EntryId("gone"))
        create(Income(EntryId("in"), at(2026, 11, 6), AccountId("bank"), m(5_000), CategoryId("income:other")), trip = false)
        create(Transfer(EntryId("tr"), at(2026, 11, 6), AccountId("bank"), AccountId("cash"), m(5_000)), trip = false)
        create(refund("rf", "e1", 1_000, at(2026, 11, 7)), trip = false)
    }

    @Test
    fun theOfferCountsOnlyLiveExpensesInTheTripsDaysThatLackItsTagAndTagsExactlyThose() {
        seedWeek()
        val offer = tagging.offer(goa)
        assertEquals(listOf("e1", "e2", "e3"), offer.ids, "oldest first; not the tagged one, the deleted one, the income, the transfer, the refund or the day after")
        assertEquals(3, offer.count)
        val done = tagging.apply(offer)
        assertEquals(listOf("e1", "e2", "e3"), done.tagged); assertEquals(0, done.full)
        assertEquals(listOf("Trip: Goa"), tags("e1")); assertEquals(listOf("Food run", "Trip: Goa"), tags("e2")); assertEquals(listOf("Trip: Goa"), tags("e3"))
        assertTrue(tags("outside").isEmpty() && tags("in").isEmpty() && tags("tr").isEmpty() && tags("rf").isEmpty())
        assertEquals(0, tagging.offer(goa).count, "a second look finds nothing")
        assertEquals(0, tagging.apply(offer).tagged.size, "and applying the same offer again tags nobody twice")
    }

    @Test
    fun theTagIsComparedIgnoringCapitalsWhenLookingForWhatLacksIt() {
        create(expense("has", at(2026, 11, 4)), "Trip: Goa", trip = false)
        create(expense("lacks", at(2026, 11, 4)), "Other", trip = false)
        val shouting = goa.copy(tag = "TRIP: GOA") // typed in other capitals than the entries carry
        val offer = tagging.offer(shouting)
        assertEquals(listOf("lacks"), offer.ids)
        tagging.apply(offer)
        assertEquals(listOf("Other", "Trip: Goa"), tags("lacks"), "added in the spelling the ledger already uses")
    }

    @Test
    fun undoTakesTheTagOffExactlyTheEntriesItWasPutOnAndLeavesTheRest() {
        seedWeek()
        val before = listOf("e1", "e2", "e3", "tagged", "outside").associateWith { tags(it) }
        val done = tagging.apply(tagging.offer(goa))
        // the person took the tag off e3 meanwhile and added another to e2: Undo leaves what they did
        ledger.removeTag(EntryId("e3"), "Trip: Goa")
        ledger.addTag(EntryId("e2"), "later")
        assertEquals(2, tagging.undo(done), "e1 and e2 lost it; e3 did not have it any more")
        assertEquals(before["e1"], tags("e1")); assertEquals(listOf("Food run", "later"), tags("e2")); assertEquals(before["e3"], tags("e3"))
        assertEquals(before["tagged"], tags("tagged"), "an entry that had the tag before was never touched")
        assertEquals(0, tagging.undo(done), "a second Undo does nothing")
        assertEquals(listOf("e1", "e3", "e2").toSet(), tagging.offer(goa).ids.toSet(), "and the offer is back")
    }

    @Test
    fun anEntryWithTenTagsIsLeftOutAndCounted() {
        val ten = (1..10).map { "tag$it" }
        create(expense("full", at(2026, 11, 4)), *ten.toTypedArray(), trip = false)
        create(expense("room", at(2026, 11, 4)), trip = false)
        val done = tagging.apply(tagging.offer(goa))
        assertEquals(listOf("room"), done.tagged); assertEquals(1, done.full)
        assertEquals(10, tags("full").size)
    }

    @Test
    fun editedDatesOfferOnlyTheDaysTheTripGainedSoATagThePersonRemovedInsideTheOldDaysIsNotOffered() {
        trips.save(goa)
        create(expense("inside", at(2026, 11, 5)))
        ledger.removeTag(EntryId("inside"), "Trip: Goa") // the person said no
        create(expense("extra", at(2026, 11, 11)), trip = false)
        val longer = goa.copy(endDay = day(2026, 11, 12))
        val offer = tagging.offer(longer, TripRules.newlyCovered(goa, longer))
        assertEquals(listOf("extra"), offer.ids)
        assertEquals(setOf("inside", "extra"), tagging.offer(longer).ids.toSet(), "the whole-trip action on the summary page offers both: it says N and the person decides")
    }

    @Test
    fun theChangeIsInTheEntrysHistoryLikeAnyTagChange() {
        create(expense("e1", at(2026, 11, 3)), trip = false)
        tagging.apply(tagging.offer(goa))
        val last = ledger.history(EntryId("e1")).last()
        assertTrue(last.changes.any { it.field == "tags" && it.before == null && it.after == "Trip: Goa" }, "the edit lists the new tag: ${last.changes}")
    }

    @Test
    fun deletingATripLeavesTheTagsAndTheFiguresOfAnotherTripWithTheSameTagStillSeeThem() {
        trips.save(goa)
        create(expense("lunch", at(2026, 11, 5)))
        trips.delete("goa")
        assertEquals(listOf("Trip: Goa"), tags("lunch"), "the tag is an ordinary tag now")
        assertEquals(listOf("Trip: Goa" to 1L), ledger.tagsInUse())
        assertEquals(1, q.entriesWithTag("Trip: Goa", 10).executeAsList().size, "and Search finds it")
        trips.save(goa.copy(id = "goa-again"))
        assertEquals(1, trips.totals(trips.get("goa-again")!!, inr).second)
    }

    // ---- the figures ----

    @Test
    fun theTripsFiguresAreTheTaggedLinesInsideItsDaysInItsCurrencyAndNotTheDeletedOnes() {
        trips.save(goa)
        create(expense("a", at(2026, 11, 3, 0, 0), amt = 10_000, cat = "Food"))
        create(expense("b", at(2026, 11, 9, 23, 59, 59, 999_000_000), amt = 20_000, cat = "Stay"))
        create(expense("c", at(2026, 11, 10, 0, 0), amt = 99_000), "Trip: Goa", trip = false) // tagged but a day too late
        create(expense("d", at(2026, 11, 5), amt = 5_000), "other", trip = false) // inside but another tag
        create(expense("gone", at(2026, 11, 5), amt = 7_000)); ledger.delete(EntryId("gone"))
        val lines = trips.lines(goa, inr)
        assertFalse(lines.capped)
        assertEquals(setOf("a", "b"), lines.lines.map { it.entryId }.toSet())
        assertEquals(30_000L to 2, trips.totals(goa, inr))
        assertEquals(0L to 0, trips.totals(goa, Currency.USD), "another currency is not added in")
        assertEquals(30_000L, ledger.spendByCategory(inr, from = TripRules.fromMillis(goa, ist), until = TripRules.untilMillis(goa, ist)).values.sumOf { it.minorUnits } - 5_000L, "agrees with the report's spend lines, less the other-tag expense")
    }

    @Test
    fun theTagReportAndTheTripAgreeOnWhatWasSpentWithTheTagAsTheirSharedRule() {
        trips.save(goa)
        create(expense("a", at(2026, 11, 4), amt = 30_000))
        create(refund("r", "a", 5_000, at(2026, 11, 5)))
        val report = ReportStore(db, ledger).groups(ReportGroup.TAG, app.expenses.core.domain.DatePeriod(LocalDate(2026, 11, 3), LocalDate(2026, 11, 10)), ist, inr)
        assertEquals(25_000L, report.single { it.name == "Trip: Goa" }.total.minorUnits)
        assertEquals(25_000L, trips.totals(goa, inr).first)
    }

    @Test
    fun theHookLooksAtOnlyTheFewEnabledTripsAndTheirDaysNotAtEntries() {
        // no trips at all: the answer is "none" without reading an entry
        assertNull(trips.tripAt(at(2026, 11, 5)))
        trips.save(goa); trips.save(Trip("kerala", "Kerala", "Trip: Kerala", day(2026, 12, 20), day(2026, 12, 27)))
        assertEquals("goa", trips.tripAt(at(2026, 11, 5))?.id); assertEquals("kerala", trips.tripAt(at(2026, 12, 21))?.id)
        assertNull(trips.tripAt(at(2026, 12, 1)))
        assertEquals(listOf("goa"), trips.tripsCovering(at(2026, 11, 5)).map { it.id })
    }

    @Test
    fun aTripInAnotherZoneFollowsThePhonesCalendarWhereverThePhoneIs() {
        trips.save(Trip("one", "One day", "Trip: One", day(2026, 11, 3), day(2026, 11, 3)))
        val instant = LocalDateTime(2026, 11, 3, 23, 30).toInstant(ist).toEpochMilliseconds()
        ledger.zone = { TimeZone.of("Asia/Tokyo") }
        assertNull(trips.tripAt(instant), "03:00 on 4 November in Tokyo")
        ledger.zone = { TimeZone.of("Asia/Kolkata") }
        assertNotNull(trips.tripAt(instant))
        ledger.zone = { TimeZone.UTC }
        assertNotNull(trips.tripAt(instant), "18:00 UTC on 3 November")
    }
}
