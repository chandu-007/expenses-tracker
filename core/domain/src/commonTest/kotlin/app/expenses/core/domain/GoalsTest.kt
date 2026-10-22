package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GoalsTest {
    @Test fun percentIsWholeAndCappedAndNeverNegative() {
        assertEquals(25, GoalMath.percent(25_000, 100_000)); assertEquals(0, GoalMath.percent(0, 100_000)); assertEquals(0, GoalMath.percent(-5, 100_000))
        assertEquals(100, GoalMath.percent(150_000, 100_000), "more than the target still reads 100"); assertEquals(33, GoalMath.percent(1, 3))
        assertEquals(75_000, GoalMath.remaining(25_000, 100_000)); assertEquals(0, GoalMath.remaining(150_000, 100_000))
    }

    @Test fun monthlyAmountSpreadsWhatIsLeftOverTheMonthsAndRoundsUp() {
        assertEquals(25_000L, GoalMath.monthly(0, 100_000, 120, 0), "120 days is four months")
        assertEquals(25_000L, GoalMath.monthly(0, 100_000, 100, 0), "100 days: a part month counts as one, so four")
        assertEquals(33_334L, GoalMath.monthly(0, 100_001, 90, 0), "three months, rounded up")
        assertEquals(100_000L, GoalMath.monthly(0, 100_000, 10, 0), "ten days is one month")
        assertNull(GoalMath.monthly(0, 100_000, null, 0), "no date"); assertNull(GoalMath.monthly(100_000, 100_000, 90, 0), "nothing left"); assertNull(GoalMath.monthly(0, 100_000, 50, 60), "date past")
    }

    @Test fun goalsRoundTripThroughTheirTextAndAnythingBrokenIsSkipped() {
        val goals = listOf(Goal("a", "Goa trip", 5_000_000, 120_000, 20_500, null), Goal("b", "Laptop", 90_000_00, 0, null, "savings"))
        assertEquals(goals, GoalCodec.decode(GoalCodec.encode(goals)))
        assertEquals(listOf(goals[0]), GoalCodec.decode(GoalCodec.encode(goals).lines().first() + "\nnot a goal\nx\ty\tz"))
        assertEquals(emptyList(), GoalCodec.decode(null))
        assertEquals("Tabname", GoalCodec.clean("Tab\tname\n"), "tabs and line breaks would break the text, so they are dropped")
        assertFailsWith<IllegalArgumentException> { Goal("c", "x", 0, 0, null, null) }
    }
}
