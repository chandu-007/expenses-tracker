package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N-18: "already spoken for". Invented bills only. */
class SpokenForTest {
    private val start = LocalDate(2026, 11, 1)
    private val end = LocalDate(2026, 12, 1)
    private val money = { m: Long -> Money(m, Currency.INR).formatted() }

    private fun due(name: String, rupees: Long, day: Int, month: Int = 11, paid: Boolean = false) = SpokenFor.Due(name, rupees * 100, LocalDate(2026, month, day), paid)

    @Test fun noneDueIsNoLine() {
        assertNull(SpokenFor.compute(500_000, emptyList(), start, end))
        assertNull(SpokenFor.compute(500_000, listOf(due("Rent", 18_000, 1, month = 12)), start, end), "due on the first day of the next period is not this period's")
    }

    @Test fun someDueWithRoomLeft() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Mobile", 600, 28), due("Netflix", 500, 25), due("Power", 1_000, 26)), start, end)!!
        assertEquals(3, r.count)
        assertEquals(210_000L, r.totalMinor)
        assertEquals(listOf("Netflix", "Power", "Mobile"), r.items.map { it.name }, "in date order")
        assertFalse(r.coversAll)
        assertEquals("₹2,100.00 of what's left is already spoken for by 3 bills due", SpokenFor.line(r, money))
    }

    @Test fun oneBillSaysBill() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Rent", 1_800, 30)), start, end)!!
        assertEquals("₹1,800.00 of what's left is already spoken for by 1 bill due", SpokenFor.line(r, money))
    }

    @Test fun oneBillThatTakesAllOfItIsSaidInTheSingular() {
        val r = SpokenFor.compute(150_000, listOf(due("Rent", 2_000, 26)), start, end)!!
        assertTrue(r.coversAll)
        assertEquals("All of what's left is spoken for: 1 bill due comes to ₹2,000.00", SpokenFor.line(r, money))
    }

    @Test fun allOfWhatIsLeftIsDue() {
        val r = SpokenFor.compute(150_000, listOf(due("Power", 1_000, 26), due("Mobile", 600, 28)), start, end)!!
        assertTrue(r.coversAll)
        assertEquals("All of what's left is spoken for: 2 bills due come to ₹1,600.00", SpokenFor.line(r, money))
        val exact = SpokenFor.compute(160_000, listOf(due("Power", 1_000, 26), due("Mobile", 600, 28)), start, end)!!
        assertTrue(exact.coversAll, "exactly what is left is all of it")
        assertTrue(SpokenFor.compute(0, listOf(due("Power", 1_000, 26)), start, end)!!.coversAll, "nothing left is not 'over'")
    }

    @Test fun overBudget() {
        val r = SpokenFor.compute(-50_000, listOf(due("Power", 1_000, 26)), start, end)!!
        assertTrue(r.overBudget)
        assertFalse(r.coversAll)
        assertEquals("1 bill still due (₹1,000.00) will add to what is over", SpokenFor.line(r, money))
    }

    @Test fun paidBillsAreLeftOut() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Power", 1_000, 26, paid = true), due("Mobile", 600, 28)), start, end)!!
        assertEquals(listOf("Mobile"), r.items.map { it.name })
        assertEquals(60_000L, r.totalMinor)
        assertNull(SpokenFor.compute(1_000_000, listOf(due("Power", 1_000, 26, paid = true)), start, end), "all paid: no line")
    }

    @Test fun thePeriodEndIsExclusiveAndAnUnpaidBillEarlierInThePeriodCounts() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Last day", 100, 30), due("Next period", 200, 1, month = 12), due("Overdue", 300, 3), due("Last period", 400, 31, month = 10)), start, end)!!
        assertEquals(listOf("Overdue", "Last day"), r.items.map { it.name })
        assertEquals(40_000L, r.totalMinor)
    }

    @Test fun aBillWithoutAnAmountIsNotCounted() {
        assertNull(SpokenFor.compute(1_000_000, listOf(due("Unknown", 0, 20)), start, end))
    }

    @Test fun aWeeklyPeriod() {
        val r = SpokenFor.compute(200_000, listOf(due("Milk", 420, 7), due("Paper", 90, 9)), LocalDate(2026, 11, 2), LocalDate(2026, 11, 9))!!
        assertEquals(listOf("Milk"), r.items.map { it.name })
    }
}
