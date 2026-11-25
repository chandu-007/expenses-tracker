package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignTokensTest {
    @Test
    fun theFourteenRadiiOfTheAppBecomeFourSteps() {
        val before = listOf(2, 3, 6, 8, 10, 12, 14, 16, 18, 20, 21, 22, 24, 28) // what the code drew before UI S1 (the audit's count: 14)
        assertEquals(14, before.size)
        val after = before.map { DesignTokens.radiusFor(it) }.toSet()
        assertEquals(setOf(8, 12, 20, 24), after, "the design's shapes (calm and clear): tiles and inputs 12, rows and cards 20, hero cards 24")
        assertEquals(DesignTokens.RADII_DP.toSet(), after)
    }

    @Test
    fun theNamedChangesAreAsDecided() {
        assertEquals(20, DesignTokens.radiusFor(22), "entry cards 22 to 20"); assertEquals(24, DesignTokens.radiusFor(24), "hero cards 24")
        assertEquals(12, DesignTokens.radiusFor(12), "tiles and inputs 12"); assertEquals(12, DesignTokens.radiusFor(16), "inputs 16 to 12")
        assertEquals(24, DesignTokens.radiusFor(28), "sheets 28 to 24"); assertEquals(8, DesignTokens.radiusFor(8))
    }

    @Test
    fun theSpacingIsOnAFourPointGrid() {
        assertTrue(DesignTokens.SPACING_DP.all { it % 4 == 0 })
        assertEquals(DesignTokens.SPACING_DP.sorted(), DesignTokens.SPACING_DP)
    }

    @Test
    fun anAmountSplitsIntoSignCurrencyWholePartAndPaise() {
        assertEquals(MoneyParts("", "₹", "1,85,000", ".00"), MoneyParts.split("₹1,85,000.00"))
        assertEquals(MoneyParts("-", "₹", "649", ".50"), MoneyParts.split("-₹649.50"), "the minus stays full size: it is information")
        assertEquals(MoneyParts("", "₹", "31,822", ""), MoneyParts.split("₹31,822"), "a whole-rupee headline has no paise")
        assertEquals(MoneyParts("+", "₹", "1,200", ".00"), MoneyParts.split("+₹1,200.00"))
        assertEquals(MoneyParts("", "USD ", "1,234,567", ".89"), MoneyParts.split("USD 1,234,567.89"))
        assertEquals(MoneyParts("", "", "₹ ••••", ""), MoneyParts.split("₹ ••••"), "a hidden amount is left as it is")
        assertEquals(MoneyParts("", "", "", ""), MoneyParts.split(""))
        for (t in listOf("₹1,85,000.00", "-₹649.50", "₹31,822", "₹ ••••", "", "USD 1,234,567.89")) MoneyParts.split(t).let { assertEquals(t, it.sign + it.symbol + it.body + it.paise, "nothing is lost") }
    }
}
