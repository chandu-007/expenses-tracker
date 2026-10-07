package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountMaskTest {
    private fun m(s: String) = AmountMask.mask(s)

    @Test
    fun everyKindOfAmountTheAppShowsIsHiddenKeepingItsSignAndCurrency() {
        assertEquals("₹••••", m("₹1,23,456.78")); assertEquals("-₹••••", m("-₹950")); assertEquals("+₹••••", m("+₹2.5K")); assertEquals("₹••••", m("₹3.4Cr")); assertEquals("₹••••", m("₹1.2L"))
        assertEquals("USD ••••", m("USD 1,234.56")); assertEquals("JPY ••••", m("JPY 1.2K")); assertEquals("₹••••", m("₹0.00"))
    }

    @Test
    fun amountsInsideSentencesAreHiddenAndTheRestOfTheSentenceIsKept() {
        assertEquals("₹•••• left of ₹••••", m("₹3,000.00 left of ₹5,000.00"))
        assertEquals("Rent ₹•••• · every month · next 1 Nov", m("Rent ₹15,000.00 · every month · next 1 Nov"))
        assertEquals("Up to ₹•••• can still be refunded", m("Up to ₹3,000.00 can still be refunded"))
        assertEquals("Bank  -₹••••", m("Bank  -₹20.00")); assertEquals("Amex  -₹•••• owed", m("Amex  -₹2,500.00 owed"))
        assertEquals("12% more than the period before", m("12% more than the period before"))
    }

    @Test
    fun datesCountsPercentagesAndLabelsAreNeverTouched() {
        for (s in listOf("7 Oct 2026", "2026-10-07", "3 selected", "55%", "Amount (₹)", "1 Apr – 31 Mar", "Spent this month", "FY 2026–27", "20 entries", "Day 25", "₹", "USDA 12", "ABC12")) assertEquals(s, m(s), s)
    }

    @Test
    fun maskingIsIdempotentAndEmptyTextIsEmpty() {
        assertEquals("", m("")); assertEquals(m("₹1.00"), m(m("₹1.00")))
    }
}
