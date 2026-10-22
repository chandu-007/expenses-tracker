package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForeignAmountTest {
    private val inr = Currency.INR
    private val usd = Currency("USD", 2)
    private val jpy = Currency("JPY", 0)

    @Test fun anAmountInAnotherCurrencyBecomesWhatLeftYourAccountAtYourRateAndKeepsWhatWasPaid() {
        val (base, paid) = ForeignAmount.convert("25", "83.20", usd, inr)!!
        assertEquals(Money(208_000, inr), base); assertEquals(Money(2_500, usd), paid)
        assertEquals(Money(280_000, inr), ForeignAmount.convert("5000", "0.56", jpy, inr)!!.first, "yen has no decimals")
        assertEquals(Money(6_000, usd), ForeignAmount.convert("5000", "0.012", inr, usd)!!.first, "5,000 rupees at 0.012 is 60 dollars: it works the other way too")
    }

    @Test fun halvesRoundUpAndTinyOrBrokenInputIsRefused() {
        assertEquals(Money(1, inr), ForeignAmount.convert("0.01", "0.5", usd, inr)!!.first, "half a paisa rounds up to one")
        assertEquals(Money(2, inr), ForeignAmount.convert("0.03", "0.5", usd, inr)!!.first, "1.5 paise rounds to 2")
        assertNull(ForeignAmount.convert("0.01", "0.4", usd, inr), "0.4 of a paisa is nothing")
        for (bad in listOf("", "abc", "0", "-5", "1.234")) assertNull(ForeignAmount.convert(bad, "83", usd, inr), "amount '$bad'")
        for (bad in listOf("", "abc", "0", "-1", "1.2345678", "2000000")) assertNull(ForeignAmount.convert("10", bad, usd, inr), "rate '$bad'")
    }

    @Test fun theRateIsReadWithCommasAndSpacesIgnored() {
        assertEquals(83.2, ForeignAmount.parseRate(" 83.20 "))
        assertEquals(1234.5, ForeignAmount.parseRate("1,234.5"))
        assertNull(ForeignAmount.parseRate("1.2.3"))
    }
}
