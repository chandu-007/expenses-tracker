package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsualAmountTest {
    private fun c(id: String, label: String, minor: Long, currency: String = "INR") = SimilarCandidate(id, label, "Food", minor, 1_000L, currency)

    @Test
    fun theUsualAmountIsTheMiddleOneOfAtLeastThreeEarlierExpensesToTheSamePayee() {
        val earlier = listOf(c("a", "Starbucks", 30_000), c("b", "Starbucks Coffee", 36_000), c("c", "starbucks", 42_000), c("d", "Cafe Noir", 99_900))
        assertEquals(36_000L, UsualAmount.usual("Starbucks", null, "INR", earlier))
        val four = listOf(c("a", "Starbucks", 30_000), c("b", "Starbucks", 36_000), c("e", "Starbucks", 30_000), c("f", "Starbucks", 36_000))
        assertEquals(33_000L, UsualAmount.usual("Starbucks", null, "INR", four), "an even count takes the middle of the two middle ones")
    }

    @Test
    fun fewerThanThreeOrAnotherCurrencyOrTheEntryItselfAreNotAUsualAmount() {
        val two = listOf(c("a", "Starbucks", 30_000), c("b", "Starbucks", 36_000))
        assertNull(UsualAmount.usual("Starbucks", null, "INR", two))
        assertNull(UsualAmount.usual("Starbucks", null, "INR", two + c("z", "Starbucks", 9_900, "USD")), "a dollar entry is not an INR one")
        assertNull(UsualAmount.usual("Starbucks", "c", "INR", two + c("c", "Starbucks", 42_000)), "the entry being looked at does not count towards its own usual")
        assertNull(UsualAmount.usual("Starbucks", null, "INR", emptyList()))
    }

    @Test
    fun aDifferenceIsSaidOnlyWhenItIsAQuarterOfTheUsualAndAtLeastFiveUnits() {
        assertEquals(12_000L, UsualAmount.difference(48_000, 36_000, 2), "₹120 above ₹360: a third")
        assertEquals(-12_000L, UsualAmount.difference(24_000, 36_000, 2), "₹120 below")
        assertNull(UsualAmount.difference(38_000, 36_000, 2), "₹20 above ₹360 is not worth a line")
        assertNull(UsualAmount.difference(1_300, 1_000, 2), "30% of ₹10 but under 5 units")
        assertEquals(500L, UsualAmount.difference(1_500, 1_000, 0), "a currency with no decimals: 500 above 1,000")
        assertNull(UsualAmount.difference(48_000, 0, 2))
    }
}
