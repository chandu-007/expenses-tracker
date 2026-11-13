package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class WholeAmountsTest {
    private fun w(s: String) = WholeAmounts.round(s)

    @Test fun roundsHalfUpOnTheDigitAfterThePoint() {
        assertEquals("₹31,822", w("₹31,822.40"))
        assertEquals("₹31,823", w("₹31,822.50"))
        assertEquals("₹31,823", w("₹31,822.99"))
        assertEquals("₹10", w("₹10.499"))
        assertEquals("₹11", w("₹10.5"))
        assertEquals("₹28,178", w("₹28,178.00"))
    }

    @Test fun carriesIntoTheNextGroupAndKeepsIndianGrouping() {
        assertEquals("₹1,00,000", w("₹99,999.50"))
        assertEquals("₹1,000", w("₹999.50"))
        assertEquals("₹12,34,568", w("₹12,34,567.89"))
        assertEquals("₹1,44,062", w("₹1,44,061.85"))
    }

    @Test fun negativeAndSignedAmountsRoundOnTheirSize() {
        assertEquals("-₹4,686", w("-₹4,685.50"))
        assertEquals("-₹4,685", w("-₹4,685.49"))
        assertEquals("+₹1,000", w("+₹999.60"))
    }

    @Test fun anAmountThatWouldReadZeroKeepsItsPaise() {
        assertEquals("₹0.40", w("₹0.40"))
        assertEquals("-₹0.10", w("-₹0.10"))
        assertEquals("₹1", w("₹0.50"))
        assertEquals("₹0", w("₹0.00"), "a real zero is zero")
    }

    @Test fun otherCurrenciesUseThousandsGroupingAndTheirCode() {
        assertEquals("USD 1,235", w("USD 1,234.56"))
        assertEquals("USD 1,000", w("USD 999.50"))
    }

    @Test fun textAroundAndUnrelatedFiguresAreLeftAlone() {
        assertEquals("₹28,178 left of ₹60,000", w("₹28,178.00 left of ₹60,000.00"))
        assertEquals("about ₹1,565 a day from your budget", w("about ₹1,565.44 a day from your budget"))
        assertEquals("₹1.2K and ₹3.4Cr and ₹9.5L", w("₹1.2K and ₹3.4Cr and ₹9.5L"))
        assertEquals("4 expenses, 30 Nov, +158%", w("4 expenses, 30 Nov, +158%"))
        assertEquals("₹950", w("₹950"))
    }

    @Test fun theAmountIsNeverRoundedAcrossHalfAWholeUnitAway() {
        // the rounded figure is within half a unit of the exact one, for every paisa of a few hundred rupees
        for (paise in 0 until 30_000) {
            val exact = "₹${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"
            val shown = w(exact)
            if (shown == exact) { assertEquals(true, paise in 1..99, exact); continue }
            val back = shown.removePrefix("₹").replace(",", "").toLong() * 100
            assertEquals(true, kotlin.math.abs(back - paise) <= 50, "$exact shown as $shown")
        }
    }
}
