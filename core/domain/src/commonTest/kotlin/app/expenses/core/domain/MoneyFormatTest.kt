package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyFormatTest {
    private val inr = Currency.INR

    @Test
    fun rupeesUseIndianGrouping() {
        assertEquals("₹0.00", Money(0, inr).formatted())
        assertEquals("₹0.05", Money(5, inr).formatted())
        assertEquals("₹999.99", Money(99_999, inr).formatted())
        assertEquals("₹1,000.00", Money(100_000, inr).formatted())
        assertEquals("₹12,34,567.89", Money(123_456_789, inr).formatted())
        assertEquals("₹1,00,00,000.00", Money(1_000_000_000, inr).formatted())
        assertEquals("-₹1,234.50", Money(-123_450, inr).formatted())
    }

    @Test
    fun otherCurrenciesUseThousandsGroupingAndTheirOwnDecimalPlaces() {
        assertEquals("USD 1,234,567.89", Money(123_456_789, Currency.USD).formatted())
        assertEquals("JPY 1,200", Money(1_200, Currency("JPY", 0)).formatted())
        assertEquals("KWD 7.500", Money(7_500, Currency("KWD", 3)).formatted())
        assertEquals("-USD 0.01", Money(-1, Currency.USD).formatted())
    }

    @Test
    fun theMostNegativeAmountDoesNotOverflow() {
        assertEquals("-₹92,23,37,20,36,85,477.5808", Money(Long.MIN_VALUE, Currency("INR", 4)).formatted())
    }

    @Test
    fun formattingThenParsingGivesTheSameAmount() = runTest {
        checkAll(Arb.long(0, 999_999_999_999_999L)) { minor ->
            val text = Money(minor, inr).formatted().removePrefix("₹")
            assertEquals(Money(minor, inr), parseAmount(text, inr), text)
        }
    }

    @Test
    fun typedAmountsAreParsedStrictly() {
        assertEquals(Money(12_345, inr), parseAmount("123.45", inr))
        assertEquals(Money(12_350, inr), parseAmount("123.5", inr))
        assertEquals(Money(5_000, inr), parseAmount("50", inr))
        assertEquals(Money(50, inr), parseAmount(".5", inr))
        assertEquals(Money(123_456_700, inr), parseAmount("12,34,567", inr))
        assertNull(parseAmount("", inr))
        assertNull(parseAmount(".", inr))
        assertNull(parseAmount("-5", inr))
        assertNull(parseAmount("1.234", inr)) // more decimals than paise
        assertNull(parseAmount("1.2.3", inr))
        assertNull(parseAmount("12abc", inr))
        assertNull(parseAmount("9".repeat(16), inr))
        assertEquals(Money(1_200, Currency("JPY", 0)), parseAmount("1200", Currency("JPY", 0)))
        assertNull(parseAmount("1200.5", Currency("JPY", 0)))
    }
}
