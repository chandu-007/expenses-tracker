package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DateMathTest {
    private val india = TimeZone.of("Asia/Kolkata")
    private val newYork = TimeZone.of("America/New_York")

    @Test
    fun movingToADateKeepsTheTimeOfDayAndLandsOnThatDate() = runTest {
        checkAll(Arb.long(0L, 4_000_000_000_000L), Arb.long(0L, 4_000_000_000_000L)) { at, other ->
            val target = dateOf(other, india)
            val moved = withDate(at, target, india)
            assertEquals(target, dateOf(moved, india))
            // same wall-clock time of day in the same zone (India has no daylight saving)
            assertEquals((at + 19_800_000L).mod(86_400_000L), (moved + 19_800_000L).mod(86_400_000L))
        }
    }

    @Test
    fun movingToTheSameDateChangesNothing() {
        val at = 1_790_000_000_000L
        assertEquals(at, withDate(at, dateOf(at, india), india))
    }

    @Test
    fun aTimeThatDoesNotExistOnTheNewDateMovesForward() {
        // 2026-03-08 02:30 does not exist in New York (clocks jump from 02:00 to 03:00)
        val at = withDate(1_790_000_000_000L, LocalDate(2026, 2, 1), newYork)
        val moved = withDate(at, LocalDate(2026, 3, 8), newYork)
        assertEquals(LocalDate(2026, 3, 8), dateOf(moved, newYork))
    }

    @Test
    fun plainAmountsRoundTripAndMatchTheEditField() = runTest {
        checkAll(Arb.long(0L, 999_999_999_999_999L)) { minor ->
            val m = Money(minor, Currency.INR)
            assertEquals(m, parseAmount(m.toPlainString(), Currency.INR))
        }
        assertEquals("1234567.50", Money(123_456_750, Currency.INR).toPlainString())
        assertEquals("0.05", Money(5, Currency.INR).toPlainString())
        assertEquals("1200", Money(1_200, Currency("JPY", 0)).toPlainString())
        assertEquals("-12.30", Money(-1_230, Currency.USD).toPlainString())
    }
}
