package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DuplicatesTest {
    private fun e(id: String, day: Long, ref: String? = null) = DupEntry(id, 100, day, ref, id)

    @Test
    fun theSameReferenceIsTheSameTransactionWhateverTheDate() {
        val m = Duplicates.best(100, "R1", listOf(e("far", 90, "r1"), e("near", 100)))!!
        assertEquals("far", m.entry.id); assertEquals(DupStrength.SAME_REFERENCE, m.strength)
    }

    @Test
    fun withoutAReferenceTheNearestDayWithinOneDayIsLikelyAndTiesAreStable() {
        assertEquals(DupStrength.LIKELY, Duplicates.best(100, null, listOf(e("a", 101)))!!.strength)
        assertEquals("b", Duplicates.best(100, null, listOf(e("a", 99), e("b", 100)))!!.entry.id, "the exact day beats a neighbour")
        assertEquals("a", Duplicates.best(100, null, listOf(e("b", 99), e("a", 101)))!!.entry.id, "equal distance: the same answer every time")
        assertNull(Duplicates.best(100, null, listOf(e("a", 102), e("b", 98))), "two days apart is a different payment")
        assertNull(Duplicates.best(100, null, emptyList()))
    }

    @Test
    fun aDifferentReferenceMeansADifferentPaymentButABlankOneIsIgnored() {
        assertNull(Duplicates.best(100, "R9", listOf(e("a", 100, "R1"))), "same amount, same day, but the bank gave two different numbers: two payments")
        assertEquals("b", Duplicates.best(100, "R9", listOf(e("a", 100, "R1"), e("b", 99, "r9")))!!.entry.id, "the same number still wins")
        assertEquals(DupStrength.LIKELY, Duplicates.best(100, "R9", listOf(e("a", 100, null)))!!.strength, "an entry with no number can still be the same payment")
        assertEquals(DupStrength.LIKELY, Duplicates.best(100, "  ", listOf(e("a", 100, "  ")))!!.strength)
        assertEquals(DupStrength.LIKELY, Duplicates.best(100, "R9", listOf(e("a", 100, "  ")))!!.strength, "a blank number on the entry is no number")
    }

    private fun p(id: String, at: Long, ref: String? = null, amount: Long = 500, dir: String = "DEBIT", sender: String = id, fromApp: Boolean = false) = PendingKey(id, dir, amount, at, ref, sender, fromApp)

    @Test
    fun aSecondTextForTheSamePaymentPointsAtTheFirstWithinTenMinutes() {
        val min = 60_000L
        assertEquals(mapOf("b" to "a"), Duplicates.repeatsOfPending(listOf(p("b", 5 * min), p("a", 0))))
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0), p("b", 11 * min))), "eleven minutes apart: a different payment")
        assertEquals(mapOf("b" to "a"), Duplicates.repeatsOfPending(listOf(p("a", 0), p("b", 10 * min))), "the limit itself counts")
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0), p("b", min, amount = 501))))
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0), p("b", min, dir = "CREDIT"))))
    }

    @Test
    fun twoDifferentReferencesAreTwoPaymentsButAMissingOneDoesNotSeparateThem() {
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0, "R1"), p("b", 1000, "R2"))))
        assertEquals(mapOf("b" to "a"), Duplicates.repeatsOfPending(listOf(p("a", 0, "R1"), p("b", 1000, "r1"))))
        assertEquals(mapOf("b" to "a"), Duplicates.repeatsOfPending(listOf(p("a", 0, "R1"), p("b", 1000, null))))
        // three texts: each points at the nearest earlier one, so the chain is visible
        assertEquals(mapOf("b" to "a", "c" to "b"), Duplicates.repeatsOfPending(listOf(p("a", 0), p("b", 1000), p("c", 2000))))
    }

    @Test
    fun twoPaymentsOfTheSameAmountFromOneSenderAreTwoPaymentsUnlessTheyShareAReference() {
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0, sender = "HDFCBK"), p("b", 20_000, sender = "HDFCBK"))), "100 and 100 a few seconds apart, no references")
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0, "R1", sender = "HDFCBK"), p("b", 20_000, null, sender = "HDFCBK"))), "one has no reference: still two payments")
        assertEquals(mapOf("b" to "a"), Duplicates.repeatsOfPending(listOf(p("a", 0, "R1", sender = "HDFCBK"), p("b", 20_000, "r1", sender = "HDFCBK"))), "the same number from the same sender is a repeat")
        assertEquals(mapOf("b" to "a"), Duplicates.repeatsOfPending(listOf(p("a", 0, sender = "HDFCBK"), p("b", 20_000, sender = "PHONEPE"))), "a bank alert and an app alert are the same payment")
    }

    @Test
    fun anAppPostingTheSameNotificationTwiceIsARepeatEvenFromTheSameSender() {
        assertEquals(mapOf("b" to "a"), Duplicates.repeatsOfPending(listOf(p("a", 0, sender = "PHONEPE", fromApp = true), p("b", 20_000, sender = "PHONEPE", fromApp = true))))
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0, "R1", sender = "PHONEPE", fromApp = true), p("b", 20_000, "R2", sender = "PHONEPE", fromApp = true))), "two different references are still two payments")
        assertEquals(emptyMap(), Duplicates.repeatsOfPending(listOf(p("a", 0, sender = "PHONEPE", fromApp = true), p("b", 20_000, sender = "PHONEPE", fromApp = false))), "a text and a notification from one sender name: the same-sender rule stands")
    }
}
