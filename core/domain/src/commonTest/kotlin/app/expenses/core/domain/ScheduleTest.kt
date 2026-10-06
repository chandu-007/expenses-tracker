package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private fun dates(s: Schedule, from: LocalDate, until: LocalDate) = s.occurrences(from, until).map { it.date }

    @Test
    fun aMonthlyItemOnThe31stClampsWithoutDriftingAndComesBack() {
        val s = Schedule(RepeatUnit.MONTH, 1, d(2027, 1, 31))
        assertEquals(listOf(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31), d(2027, 4, 30), d(2027, 5, 31)), dates(s, d(2027, 1, 1), d(2027, 6, 1)))
        assertEquals(d(2028, 2, 29), Schedule(RepeatUnit.MONTH, 1, d(2028, 1, 31)).dateAt(1), "leap year")
    }

    @Test
    fun yearlyOn29FebFallsOn28FebInOtherYears() {
        val s = Schedule(RepeatUnit.YEAR, 1, d(2028, 2, 29))
        assertEquals(listOf(d(2028, 2, 29), d(2029, 2, 28), d(2030, 2, 28), d(2031, 2, 28), d(2032, 2, 29)), (0..4).map { s.dateAt(it)!! })
    }

    @Test
    fun everyOtherWeekAndEveryThreeMonthsAndEveryTenDays() {
        assertEquals(listOf(d(2026, 10, 5), d(2026, 10, 19), d(2026, 11, 2)), dates(Schedule(RepeatUnit.WEEK, 2, d(2026, 10, 5)), d(2026, 10, 1), d(2026, 11, 10)))
        assertEquals(listOf(d(2026, 10, 15), d(2027, 1, 15), d(2027, 4, 15)), dates(Schedule(RepeatUnit.MONTH, 3, d(2026, 10, 15)), d(2026, 1, 1), d(2027, 5, 1)))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 11), d(2026, 10, 21), d(2026, 10, 31)), dates(Schedule(RepeatUnit.DAY, 10, d(2026, 10, 1)), d(2026, 10, 1), d(2026, 11, 1)))
    }

    @Test
    fun endConditionsStopTheScheduleExactly() {
        val count = Schedule(RepeatUnit.MONTH, 1, d(2026, 10, 1), ScheduleEnd.AfterCount(3))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 11, 1), d(2026, 12, 1)), dates(count, d(2026, 1, 1), d(2030, 1, 1)))
        assertNull(count.dateAt(3))
        val onDate = Schedule(RepeatUnit.MONTH, 1, d(2026, 10, 1), ScheduleEnd.OnOrBefore(d(2026, 12, 1)))
        assertEquals(3, dates(onDate, d(2026, 1, 1), d(2030, 1, 1)).size, "the end date itself is included")
        assertEquals(2, dates(Schedule(RepeatUnit.MONTH, 1, d(2026, 10, 1), ScheduleEnd.OnOrBefore(d(2026, 11, 30))), d(2026, 1, 1), d(2030, 1, 1)).size)
        assertNull(onDate.nextOnOrAfter(d(2026, 12, 2)))
    }

    @Test
    fun theWindowIsHalfOpenAndNextOnOrAfterIncludesToday() {
        val s = Schedule(RepeatUnit.MONTH, 1, d(2026, 10, 5))
        assertEquals(listOf(d(2026, 10, 5)), dates(s, d(2026, 10, 5), d(2026, 11, 5)), "from is included, until is not")
        assertEquals(Occurrence(0, d(2026, 10, 5)), s.nextOnOrAfter(d(2026, 10, 5)))
        assertEquals(Occurrence(1, d(2026, 11, 5)), s.nextOnOrAfter(d(2026, 10, 6)))
        assertEquals(Occurrence(0, d(2026, 10, 5)), s.nextOnOrAfter(d(2020, 1, 1)), "before the start")
    }

    @Test
    fun fastSkippingAgreesWithWalkingFromTheStartForEveryUnit() = runTest {
        for (unit in RepeatUnit.entries) checkAll(Arb.int(1, 12), Arb.long(0, 4_000), Arb.long(0, 4_000), Arb.long(1, 900)) { every, startOffset, fromOffset, span ->
            val base = d(2020, 1, 1)
            val start = base.plus(startOffset, DateTimeUnit.DAY)
            val from = base.plus(fromOffset, DateTimeUnit.DAY)
            val until = from.plus(span, DateTimeUnit.DAY)
            val s = Schedule(unit, every, start)
            val walked = generateSequence(0) { it + 1 }.map { Occurrence(it, s.dateAt(it)!!) }.takeWhile { it.date < until }.filter { it.date >= from }.toList()
            assertEquals(walked, s.occurrences(from, until), "$unit every $every from $start")
            assertTrue(s.occurrences(from, until).zipWithNext().all { (a, b) -> a.date < b.date })
        }
    }

    @Test
    fun invalidSchedulesAreRejected() {
        assertFailsWith<IllegalArgumentException> { Schedule(RepeatUnit.DAY, 0, d(2026, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { Schedule(RepeatUnit.DAY, 366, d(2026, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { Schedule(RepeatUnit.DAY, 1, d(2026, 1, 2), ScheduleEnd.OnOrBefore(d(2026, 1, 1))) }
        assertFailsWith<IllegalArgumentException> { ScheduleEnd.AfterCount(0) }
    }
}
