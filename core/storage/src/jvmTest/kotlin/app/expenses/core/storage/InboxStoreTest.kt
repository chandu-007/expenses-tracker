package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboxStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val store = InboxStore(db)

    private fun item(id: String, at: Long = 1_000, body: String? = "Rs.100 debited") =
        InboxItem(id, "SMS", "AX-HDFCBK-S", "HDFC Bank", at, "DEBIT", 10_000, "INR", "2026-10-05", "R$id", "1234", true, "GENERIC", body, InboxStatus.PENDING, null)

    @Test
    fun anItemIsAddedPendingAndTheSameTextAgainIsNotAddedWhateverItsIdOrState() {
        assertTrue(store.add(item("a"), "fp1"))
        assertFalse(store.add(item("b"), "fp1"), "same fingerprint: the same text delivered twice")
        assertEquals(1, store.pendingCount()); assertEquals(listOf("a"), store.list().map { it.id })
        assertTrue(store.confirm("a", "entry1"))
        assertFalse(store.add(item("c"), "fp1"), "still not added once it is confirmed")
        store.dismiss("a")
        assertTrue(store.add(item("d"), "fp2"))
    }

    @Test
    fun theNewestComesFirstAndTheLimitAppliesToThatOrder() {
        store.add(item("old", 1_000), "1"); store.add(item("new", 3_000), "2"); store.add(item("mid", 2_000), "3")
        assertEquals(listOf("new", "mid", "old"), store.list().map { it.id })
        assertEquals(listOf("new", "mid"), store.list(limit = 2).map { it.id })
    }

    @Test
    fun confirmingForgetsTheTextAndKeepsTheEntryAndAResolvedItemCannotBeResolvedAgain() {
        store.add(item("a"), "1")
        assertEquals("Rs.100 debited", store.get("a")!!.body)
        assertTrue(store.confirm("a", "entry1"))
        val done = store.get("a")!!
        assertEquals(InboxStatus.CONFIRMED, done.status); assertEquals("entry1", done.entryId); assertNull(done.body, "the words of the text are gone")
        assertFalse(store.dismiss("a")); assertFalse(store.confirm("a", "entry2"))
        assertEquals("entry1", store.get("a")!!.entryId, "the second attempt changed nothing")
        assertEquals(0, store.pendingCount()); assertEquals(listOf("a"), store.list(InboxStatus.CONFIRMED).map { it.id })
    }

    @Test
    fun flippingTurnsAPendingItemBothWaysAndLeavesAResolvedOneAlone() {
        store.add(item("a"), "1"); store.add(item("b"), "2")
        store.flipDirection("a")
        assertEquals("CREDIT", store.get("a")!!.direction); assertEquals("DEBIT", store.get("b")!!.direction, "only the one asked for")
        store.flipDirection("a")
        assertEquals("DEBIT", store.get("a")!!.direction, "flipping twice is the original")
        store.confirm("b", "e1"); store.flipDirection("b")
        assertEquals("DEBIT", store.get("b")!!.direction, "an item already added is not rewritten")
        store.flipDirection("missing") // no such item: nothing happens
    }

    @Test
    fun dismissingKeepsNoTextAndUnknownIdsAreRefused() {
        store.add(item("a"), "1"); assertTrue(store.dismiss("a")); assertNull(store.get("a")!!.body)
        assertFalse(store.dismiss("nope")); assertFalse(store.confirm("nope", "e"))
    }

    @Test
    fun reopeningBringsAResolvedItemBackAsPendingWithoutItsEntry() {
        store.add(item("a"), "1"); store.confirm("a", "entry1"); store.reopen("a")
        val back = store.get("a")!!; assertEquals(InboxStatus.PENDING, back.status); assertNull(back.entryId); assertEquals(1, store.pendingCount())
        store.reopen("a"); assertEquals(InboxStatus.PENDING, store.get("a")!!.status)
    }

    @Test
    fun captureEventsRecordNoTextAndOldOnesArePruned() {
        val day = 86_400_000L
        store.recordEvent(1 * day, "HDFCBK", CaptureEventOutcome.CAPTURED)
        store.recordEvent(100 * day, "HDFCBK", CaptureEventOutcome.OTP) // 99 days later: the first is older than 90 days and goes
        store.recordEvent(100 * day, null, CaptureEventOutcome.PERSONAL_SENDER)
        val rows = db.schemaQueries.captureEventCounts(0).executeAsList().map { Triple(it.sender_root, it.outcome, it.n) }.sortedBy { it.second }
        assertEquals(listOf(Triple("HDFCBK", "OTP", 1L), Triple("", "PERSONAL_SENDER", 1L)).sortedBy { it.second }, rows)
    }
}
