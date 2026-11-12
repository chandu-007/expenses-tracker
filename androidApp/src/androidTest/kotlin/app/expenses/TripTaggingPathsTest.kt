package app.expenses

import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BillItem
import app.expenses.core.domain.BillEntries
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.Trip
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.storage.BackupFile
import app.expenses.widget.QuickAddAction
import app.expenses.widget.TEMPLATE_ID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Travel mode, every way an expense is created on a real container (no screen): the manual add (the form's draft), the draft with the chip taken off, an expense dated earlier, a bill's items each as its own entry, a
 * quick add and the widget's chip tap, a refund of a tagged expense, a type change ("Make transfer" in bulk), a bill paid, a repeating item posted, a restore. The ones that are not spending on a trip, and the ones that only
 * carry tags, must NOT tag. Invented data. The To review paths (Add, Add all) are in [TripModeUiTest]; the statement import, the type change and the restore are also host-tested (`TripStoreTest`, `TripBackupTest`).
 */
@RunWith(AndroidJUnit4::class)
class TripTaggingPathsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private val zone = ZoneId.systemDefault()
    private val glanceId = AppWidgetId(4242)

    @Before fun fresh() { runBlocking { container.startFresh() }; BaseCurrency.current = inr }
    @After fun cleanUp() { runBlocking { container.startFresh() } }

    private fun epochDay(offset: Long) = LocalDate.now().plusDays(offset).toEpochDay()
    private fun at(offset: Long, hour: Int = 12) = LocalDate.now().plusDays(offset).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun tagsOf(id: String) = db { it.store.tagsOf(EntryId(id)) }
    private fun goa() = db { it.trips.save(Trip("goa", "Goa", "Trip: Goa", epochDay(-3), epochDay(3))) }
    private fun draft(at: Long, amount: String = "250", note: String = "Lunch", tags: List<String> = emptyList(), noTripTag: Boolean = false, kind: DraftKind = DraftKind.EXPENSE) =
        EntryDraft(null, kind, amount, "Food", emptyList(), "cash", "", note, at, tags = tags, noTripTag = noTripTag)

    @Test
    fun aManualAddIsTaggedByItsOwnDateNotByWhenItWasTyped() {
        goa()
        val inside = db { EntryActions(it).save(draft(at(-2))) }       // typed now, dated two days ago: inside
        val today = db { EntryActions(it).save(draft(at(0), note = "Today")) }
        val outside = db { EntryActions(it).save(draft(at(-20), note = "Old")) } // dated before the trip
        val after = db { EntryActions(it).save(draft(at(4), note = "Later")) }   // dated after it
        val mixed = db { EntryActions(it).save(draft(at(0), note = "Mixed", tags = listOf("food run", "TRIP: goa"))) }
        assertEquals(listOf("Trip: Goa"), tagsOf(inside)); assertEquals(listOf("Trip: Goa"), tagsOf(today))
        assertTrue(tagsOf(outside).isEmpty() && tagsOf(after).isEmpty())
        assertEquals(listOf("food run", "Trip: Goa"), tagsOf(mixed), "the tags typed stay, and the trip's tag is not added twice in other capitals")
    }

    @Test
    fun theChipTakenOffInTheFormMeansNoTagAndOnlyAnExpenseIsEverTagged() {
        goa()
        assertTrue(tagsOf(db { EntryActions(it).save(draft(at(0), noTripTag = true)) }).isEmpty())
        assertTrue(tagsOf(db { EntryActions(it).save(draft(at(0), kind = DraftKind.INCOME).copy(category = "income:other")) }).isEmpty(), "income is not spending")
        val transfer = EntryDraft(null, DraftKind.TRANSFER, "100", "", emptyList(), "cash", "bank", "Move", at(0))
        assertTrue(tagsOf(db { EntryActions(it).save(transfer) }).isEmpty(), "a transfer is not spending")
    }

    @Test
    fun aBillsItemsAddedEachAsItsOwnEntryAreTaggedUnlessTheChipWasTakenOff() {
        goa()
        val items = listOf(BillItem("Thali", 25_000, 1), BillItem("Beer", 18_000, 1))
        fun addAll(base: EntryDraft) = db { ok -> ok.store.atomically { BillEntries.drafts(base, items, 0, inr).map { EntryActions(ok).save(it) } } }
        val tagged = addAll(draft(at(0), amount = "430", note = "Bill"))
        assertTrue(tagged.all { tagsOf(it) == listOf("Trip: Goa") }, "each item, by the bill's date")
        val off = addAll(draft(at(0), amount = "430", note = "Bill", noTripTag = true))
        assertTrue(off.all { tagsOf(it).isEmpty() })
    }

    @Test
    fun aQuickAddAndTheWidgetsChipAreTaggedByTheirDate() {
        goa()
        db { it.templates.save(Template("chai", "Chai", Money(2_000, inr), CategoryId("Food"), AccountId("cash"), "Tea stall")) }
        val viaActions = db { EntryActions(it).quickAdd("chai")!!.entryId }
        assertEquals(listOf("Trip: Goa"), tagsOf(viaActions))
        runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "chai")) }
        val chips = db { it.db.schemaQueries.recentEntries(10).executeAsList().filter { r -> r.id != viaActions } }
        assertEquals(1, chips.size); assertEquals(listOf("Trip: Goa"), tagsOf(chips.single().id), "the widget chip is a new expense made now, inside the trip")
        val old = db { EntryActions(it, nowMillis = { at(-30) }).quickAdd("chai", occurredAt = at(-30))!!.entryId }
        assertTrue(tagsOf(old).isEmpty(), "a tap queued from a day outside the trip (the iPhone widget replays its taps with their own time) is not tagged")
        db { it.trips.save(it.trips.get("goa")!!.copy(enabled = false)) }
        runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "chai")) }
        val off = db { it.db.schemaQueries.recentEntries(10).executeAsList().first { r -> r.id !in chips.map { c -> c.id } + viaActions + old } }
        assertTrue(tagsOf(off.id).isEmpty(), "a trip that is off tags nothing")
    }

    @Test
    fun aRefundOfATaggedTripExpenseIsTaggedAndOneOfAnUntaggedExpenseIsNot() {
        goa()
        val hotel = db { EntryActions(it).save(draft(at(-1), amount = "5000", note = "Hotel")) }
        val shirt = db { EntryActions(it).save(draft(at(-1), amount = "900", note = "Shirt", noTripTag = true)) }
        val refundOfHotel = db { ok -> EntryActions(ok).save(EntryDraft(null, DraftKind.REFUND, "1000", "", emptyList(), "cash", "", "Hotel refund", at(10), hotel, 500_000)) }
        val refundOfShirt = db { ok -> EntryActions(ok).save(EntryDraft(null, DraftKind.REFUND, "100", "", emptyList(), "cash", "", "Shirt refund", at(0), shirt, 90_000)) }
        assertEquals(listOf("Trip: Goa"), tagsOf(refundOfHotel), "its own date is a week after the trip: it goes with the purchase it reverses")
        assertTrue(tagsOf(refundOfShirt).isEmpty())
    }

    @Test
    fun makingTaggedAndUntaggedExpensesTransfersInBulkNeverTagsAndKeepsTheTagsAsTheyWere() {
        goa()
        val tagged = db { EntryActions(it).save(draft(at(0), note = "Tagged one")) }
        val removed = db { EntryActions(it).save(draft(at(0), note = "Untagged one", noTripTag = true)) }
        val result = db { EntryActions(it).bulkMakeTransfer(listOf(tagged, removed), "bank") }
        assertEquals(2, result.changed)
        val transfers = db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList().filter { it.kind == "TRANSFER" } }
        assertEquals(2, transfers.size)
        val byTitle = transfers.associate { it.merchant to tagsOf(it.id) }
        assertEquals(listOf("Trip: Goa"), byTitle["Tagged one"], "tags are carried, as in every type change")
        assertTrue(byTitle["Untagged one"]!!.isEmpty(), "and nothing is added")
        db { ok -> result.undoKinds.asReversed().forEach { ok.kindChanges.undo(it) } }
        assertEquals(listOf("Trip: Goa"), tagsOf(tagged)); assertTrue(tagsOf(removed).isEmpty())
    }

    @Test
    fun aRepeatingItemPostedAndABackupRestoredInsideTheTripAreNotTagged() {
        goa()
        val today = kotlinx.datetime.LocalDate.fromEpochDays(epochDay(0).toInt())
        db { ok -> ok.recurring.save(app.expenses.core.domain.RecurringItem("gym", "Gym", Money(50_000, inr), CategoryId("Health"), AccountId("cash"), "Gym", app.expenses.core.domain.Schedule(app.expenses.core.domain.RepeatUnit.DAY, 1, today))) }
        val posted = db { ok -> ok.recurring.post("gym", today, kotlinx.datetime.TimeZone.currentSystemDefault()) }!!
        assertTrue(tagsOf(posted).isEmpty(), "a repeating item is not spending on a trip")
        val made = db { EntryActions(it).save(draft(at(0), note = "Made", noTripTag = true)) }
        val data = db { ok -> BackupFile.decode(BackupFile.encode(ok.backup.export(), 1L)).second }
        assertEquals(1, data.trips.size, "the trip is in the backup")
        // a phone with nothing on it gets the trip and the entries back; the entry that had no tag is not tagged by the restore although the trip that is on covers its day
        runBlocking { container.startFresh() }; BaseCurrency.current = inr
        val report = db { ok -> ok.backup.restore(data) }
        assertEquals(1, report.tripsAdded)
        assertEquals(1, db { it.trips.count() })
        assertTrue(tagsOf(made).isEmpty() && tagsOf(posted).isEmpty())
        assertEquals(listOf("Trip: Goa"), db { ok -> ok.trips.all().map { it.tag } })
    }
}
