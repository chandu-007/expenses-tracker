package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N-18: "already spoken for". Invented bills only. */
class SpokenForTest {
    private val end = LocalDate(2026, 12, 1)
    private val money = { m: Long -> Money(m, Currency.INR).formatted() }

    private fun due(name: String, rupees: Long, day: Int, month: Int = 11, paid: Boolean = false) = SpokenFor.Due(name, rupees * 100, LocalDate(2026, month, day), paid)
    private fun unknown(name: String, day: Int, month: Int = 11) = SpokenFor.Due(name, null, LocalDate(2026, month, day))

    @Test fun noneDueIsNoLine() {
        assertNull(SpokenFor.compute(500_000, emptyList(), end))
        assertNull(SpokenFor.compute(500_000, listOf(due("Rent", 18_000, 1, month = 12)), end), "due on the first day of the next period is not this period's")
    }

    @Test fun someDueWithRoomLeft() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Mobile", 600, 28), due("Netflix", 500, 25), due("Power", 1_000, 26)), end)!!
        assertEquals(3, r.count)
        assertEquals(210_000L, r.totalMinor)
        assertEquals(listOf("Netflix", "Power", "Mobile"), r.items.map { it.name }, "in date order")
        assertFalse(r.coversAll)
        assertEquals("₹2,100.00 of what's left is already spoken for by 3 bills due", SpokenFor.line(r, money))
    }

    @Test fun oneBillSaysBill() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Rent", 1_800, 30)), end)!!
        assertEquals("₹1,800.00 of what's left is already spoken for by 1 bill due", SpokenFor.line(r, money))
    }

    @Test fun oneBillThatTakesAllOfItIsSaidInTheSingular() {
        val r = SpokenFor.compute(150_000, listOf(due("Rent", 2_000, 26)), end)!!
        assertTrue(r.coversAll)
        assertEquals("All of what's left is spoken for: 1 bill due comes to ₹2,000.00", SpokenFor.line(r, money))
    }

    @Test fun allOfWhatIsLeftIsDue() {
        val r = SpokenFor.compute(150_000, listOf(due("Power", 1_000, 26), due("Mobile", 600, 28)), end)!!
        assertTrue(r.coversAll)
        assertEquals("All of what's left is spoken for: 2 bills due come to ₹1,600.00", SpokenFor.line(r, money))
        val exact = SpokenFor.compute(160_000, listOf(due("Power", 1_000, 26), due("Mobile", 600, 28)), end)!!
        assertTrue(exact.coversAll, "exactly what is left is all of it")
        assertTrue(SpokenFor.compute(0, listOf(due("Power", 1_000, 26)), end)!!.coversAll, "nothing left is not 'over'")
    }

    @Test fun overBudget() {
        val r = SpokenFor.compute(-50_000, listOf(due("Power", 1_000, 26)), end)!!
        assertTrue(r.overBudget)
        assertFalse(r.coversAll)
        assertEquals("1 bill still due (₹1,000.00) will add to what is over", SpokenFor.line(r, money))
    }

    @Test fun paidBillsAreLeftOut() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Power", 1_000, 26, paid = true), due("Mobile", 600, 28)), end)!!
        assertEquals(listOf("Mobile"), r.items.map { it.name })
        assertEquals(60_000L, r.totalMinor)
        assertNull(SpokenFor.compute(1_000_000, listOf(due("Power", 1_000, 26, paid = true)), end), "all paid: no line")
    }

    @Test fun thePeriodEndIsExclusiveAndAnUnpaidBillFromBeforeThePeriodStillCounts() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Last day", 100, 30), due("Next period", 200, 1, month = 12), due("Overdue", 300, 3), due("Last period", 400, 31, month = 10)), end)!!
        assertEquals(listOf("Last period", "Overdue", "Last day"), r.items.map { it.name }, "an overdue unpaid bill is still to be paid from what is left, however long ago it fell due")
        assertEquals(80_000L, r.totalMinor)
        assertEquals(5, SpokenFor.compute(1_000_000, listOf(due("Ancient", 100, 2, month = 3)) + List(4) { due("B$it", 10, 5) }, end)!!.count, "there is no lower bound")
    }

    @Test fun aBillWithNoAmountIsCountedAndListedButAddsNothingToTheTotal() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Power", 1_000, 26), due("Mobile", 448, 28), unknown("Water", 27)), end)!!
        assertEquals(2, r.count)
        assertEquals(144_800L, r.totalMinor)
        assertEquals(listOf("Water"), r.noAmount.map { it.name })
        assertEquals(listOf("Power", "Water", "Mobile"), r.listed.map { it.name }, "listed in date order, with or without an amount")
        assertEquals("₹1,448.00 of what's left is already spoken for by 2 bills due, plus 1 with no amount yet", SpokenFor.line(r, money))
        assertFalse(r.coversAll)
    }

    @Test fun noAmountIsSaidAfterEveryVariantOfTheLine() {
        val over = SpokenFor.compute(-50_000, listOf(due("Power", 1_000, 26), unknown("Water", 27), unknown("Gas", 28)), end)!!
        assertEquals("1 bill still due (₹1,000.00) will add to what is over, plus 2 with no amount yet", SpokenFor.line(over, money))
        val all = SpokenFor.compute(50_000, listOf(due("Power", 1_000, 26), unknown("Water", 27)), end)!!
        assertEquals("All of what's left is spoken for: 1 bill due comes to ₹1,000.00, plus 1 with no amount yet", SpokenFor.line(all, money))
    }

    @Test fun onlyBillsWithNoAmountMakeALineThatSaysSo() {
        val one = SpokenFor.compute(1_000_000, listOf(unknown("Water", 27)), end)!!
        assertEquals(0, one.count); assertEquals(0L, one.totalMinor); assertFalse(one.coversAll, "nothing counted, so nothing is 'all'")
        assertEquals("1 bill due has no amount yet", SpokenFor.line(one, money))
        val two = SpokenFor.compute(0, listOf(unknown("Water", 27), unknown("Gas", 28)), end)!!
        assertFalse(two.coversAll)
        assertEquals("2 bills due have no amount yet", SpokenFor.line(two, money))
        assertNull(SpokenFor.compute(1_000_000, listOf(unknown("Next", 1, month = 12)), end), "not due before the period ends: no line")
    }

    @Test fun theCardBillsNoteIsAddedToWhicheverLine() {
        val r = SpokenFor.compute(1_000_000, listOf(due("Power", 1_000, 26), unknown("Water", 27)), end)!!
        assertEquals("₹1,000.00 of what's left is already spoken for by 1 bill due, plus 1 with no amount yet (card bills not included)", SpokenFor.line(r, money, cardBillsNote = true))
        val only = SpokenFor.compute(1_000_000, listOf(unknown("Water", 27)), end)!!
        assertEquals("1 bill due has no amount yet (card bills not included)", SpokenFor.line(only, money, cardBillsNote = true))
        assertEquals("₹1,000.00 of what's left is already spoken for by 1 bill due, plus 1 with no amount yet", SpokenFor.line(r, money), "no note unless asked for")
    }

    @Test fun aWeeklyPeriod() {
        val r = SpokenFor.compute(200_000, listOf(due("Milk", 420, 7), due("Paper", 90, 9)), LocalDate(2026, 11, 9))!!
        assertEquals(listOf("Milk"), r.items.map { it.name })
    }
}
