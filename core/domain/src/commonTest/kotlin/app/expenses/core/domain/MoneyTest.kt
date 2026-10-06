package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    private val inr = Currency.INR
    private val small = Arb.long(-1_000_000_000_000L, 1_000_000_000_000L)

    @Test
    fun addingTheNegationGivesZero() = runTest {
        checkAll(small) { a ->
            val m = Money(a, inr)
            assertTrue((m + -m).isZero)
        }
    }

    @Test
    fun additionIsCommutativeAndAssociative() = runTest {
        checkAll(small, small, small) { a, b, c ->
            val x = Money(a, inr)
            val y = Money(b, inr)
            val z = Money(c, inr)
            assertEquals(x + y, y + x)
            assertEquals((x + y) + z, x + (y + z))
        }
    }

    @Test
    fun subtractIsInverseOfAdd() = runTest {
        checkAll(small, small) { a, b ->
            val x = Money(a, inr)
            val y = Money(b, inr)
            assertEquals(x, (x + y) - y)
        }
    }

    @Test
    fun mixingCurrenciesIsRejected() {
        assertFailsWith<IllegalArgumentException> { Money(1, Currency.INR) + Money(1, Currency.USD) }
        assertFailsWith<IllegalArgumentException> { Money(1, Currency.INR).compareTo(Money(1, Currency.USD)) }
    }

    @Test
    fun overflowThrowsInsteadOfWrapping() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, inr) + Money(1, inr) }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE, inr) - Money(1, inr) }
        assertFailsWith<ArithmeticException> { -Money(Long.MIN_VALUE, inr) }
    }

    @Test
    fun invalidCurrencyIsRejected() {
        assertFailsWith<IllegalArgumentException> { Currency("inr", 2) }
        assertFailsWith<IllegalArgumentException> { Currency("INR", 9) }
    }
}
