package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MilestonesTest {
    private val day = 86_400_000L
    private val today = 20_000L
    private val now = today * day + 12 * 3_600_000L

    private fun facts(count: Long = 1, days: Int? = 0, backedUp: Boolean = false, period: MilestonePeriod? = null) = MilestoneFacts(count, days, backedUp, period)
    private fun next(facts: MilestoneFacts, seen: List<MilestoneSeen> = emptyList(), on: Boolean = true, pausedUntil: Long? = null, at: Long = today) =
        Milestones.next(on, pausedUntil, if (at == today) now else at * day, seen, at, facts)

    @Test
    fun theFirstEntryIsNotedOnceOnANewLedger() {
        assertEquals(Milestones.FIRST_ENTRY, next(facts())?.id)
        assertNull(next(facts(), seen = listOf(MilestoneSeen(Milestones.FIRST_ENTRY, today - 20))), "once ever")
        assertNull(next(facts(count = 40)), "a ledger that already had history has no first-entry moment")
        assertNull(next(facts(days = null)), "an old ledger has no first-entry moment")
        assertNull(next(facts(count = 0)), "nothing to note without an entry")
    }

    @Test
    fun offUntilTheOptInIsOn() {
        assertNull(next(facts(), on = false))
    }

    @Test
    fun neverWhilePausedAndAgainWhenThePauseEnds() {
        assertNull(next(facts(), pausedUntil = NudgePause.FOREVER))
        assertNull(next(facts(), pausedUntil = now + 1))
        assertNotNull(next(facts(), pausedUntil = now), "a pause that ended at this very moment is over")
        assertNotNull(next(facts(), pausedUntil = 0L))
    }

    @Test
    fun theFirstWeekComesAfterSevenDaysAndOnlyWithSomeEntries() {
        assertNotEquals(Milestones.FIRST_WEEK, next(facts(count = 2, days = 8))?.id, "two entries are not a week of use")
        assertEquals(Milestones.FIRST_WEEK, next(facts(count = 5, days = 8))?.id)
        assertNotEquals(Milestones.FIRST_WEEK, next(facts(count = 5, days = 6))?.id, "not yet a week")
        assertNull(next(facts(count = 5, days = 90)), "long after, it is stale and not offered")
    }

    @Test
    fun aHundredEntriesAndAFirstBackup() {
        assertEquals(Milestones.ENTRIES_100, next(facts(count = 100, days = null))?.id)
        assertNull(next(facts(count = 99, days = null)))
        assertEquals(Milestones.FIRST_BACKUP, next(facts(count = 120, days = null, backedUp = true))?.id, "the backup is offered before the hundred: the more recent thing first")
        assertEquals(Milestones.ENTRIES_100, next(facts(count = 120, days = null, backedUp = true), seen = listOf(MilestoneSeen(Milestones.FIRST_BACKUP, today - 15)))?.id)
    }

    @Test
    fun aMonthUnderBudgetIsSaidOncePerMonthAndNeedsABudget() {
        val kept = MilestonePeriod(19_900L, "October", overallBudgetKept = true, quietDays = 0)
        val m = next(facts(count = 50, days = null, period = kept))!!
        assertEquals("under_budget:19900", m.id)
        assertEquals("October ended under budget.", m.text)
        assertNull(next(facts(count = 50, days = null, period = MilestonePeriod(19_900L, "October", overallBudgetKept = false, quietDays = 0))), "no budget, or over it: nothing to say")
        assertNull(next(facts(count = 99, days = null, period = kept), seen = listOf(MilestoneSeen("under_budget:19900", today - 20))), "once per month")
        val nextMonth = kept.copy(startEpochDay = 19_930L, monthName = "November")
        assertEquals("November ended under budget.", next(facts(count = 99, days = null, period = nextMonth), seen = listOf(MilestoneSeen("under_budget:19900", today - 40)))?.text)
    }

    @Test
    fun aQuietMonthNeedsSeveralNoSpendDaysAndSaysNoNumber() {
        val quiet = MilestonePeriod(19_900L, "October", overallBudgetKept = false, quietDays = Milestones.QUIET_DAYS_MIN)
        val m = next(facts(count = 50, days = null, period = quiet))!!
        assertEquals("quiet_days:19900", m.id)
        assertEquals("October had several days with no spending.", m.text)
        assertNull(next(facts(count = 50, days = null, period = quiet.copy(quietDays = Milestones.QUIET_DAYS_MIN - 1))))
    }

    @Test
    fun atLeastFourteenDaysBetweenTwo() {
        val seen = listOf(MilestoneSeen("x", today - 13))
        assertNull(next(facts(count = 100, days = null), seen = seen))
        assertNotNull(next(facts(count = 100, days = null), seen = listOf(MilestoneSeen("x", today - 14))))
    }

    @Test
    fun atMostSixInAYear() {
        // six, each 15 days apart, the newest 15 days ago and the oldest 90: the cap is reached although the gap rule is met
        val six = (0 until 6).map { MilestoneSeen("m$it", today - 15 - 15L * it) }
        assertFalse(Milestones.mayShow(true, null, now, six, today))
        assertNull(next(facts(count = 100, days = null), seen = six))
        // 275 days on, the oldest is 365 days old and has left the window: five count, so one more may come
        val later = today + 275
        assertTrue(later - six.minOf { it.epochDay } >= Milestones.WINDOW_DAYS)
        assertTrue(Milestones.mayShow(true, null, later * day, six, later))
        assertEquals(5, six.count { later - it.epochDay < Milestones.WINDOW_DAYS })
    }

    @Test
    fun theTextsHoldNoAmountNoCountOfDaysAndNoStreakWords() {
        // first_entry (a new ledger) and entries_100 cannot both hold, so two sets of facts cover every kind
        val all = Milestones.candidates(facts(count = 150, days = 10, backedUp = true, period = MilestonePeriod(19_900L, "October", true, 9))) + Milestones.candidates(facts(count = 2, days = 1))
        assertEquals(setOf(Milestones.UNDER_BUDGET, Milestones.QUIET_DAYS, Milestones.FIRST_BACKUP, Milestones.ENTRIES_100, Milestones.FIRST_WEEK, Milestones.FIRST_ENTRY), all.map { it.id.substringBefore(':') }.toSet(), "every kind is offered when it holds")
        for (m in all) {
            assertEquals(m.text, AmountMask.mask(m.text), "no amount in: ${m.text}")
            assertFalse(m.text.contains("streak", ignoreCase = true), m.text)
            assertFalse(m.text.contains("!"), m.text)
            assertFalse(m.text.any { it.isDigit() }, "no figure at all in: ${m.text}")
        }
    }

    @Test
    fun acknowledgingKeepsOnceEverAndTheSettingRoundTrips() {
        val once = Milestones.acknowledge(emptyList(), "under_budget:19900", today)
        assertEquals(once, Milestones.acknowledge(once, "under_budget:19900", today + 3), "twice changes nothing")
        val two = Milestones.acknowledge(once, Milestones.FIRST_BACKUP, today + 20)
        assertEquals("under_budget:19900@20000,first_backup@20020", Milestones.encode(two))
        assertEquals(two, Milestones.decode(Milestones.encode(two)))
        assertEquals(emptyList(), Milestones.decode(null))
        assertEquals(listOf(MilestoneSeen("a", 5)), Milestones.decode("a@5,broken,b@x,@7"), "a damaged part is left out")
    }

    @Test
    fun daysOfUseReadsTheClockSetting() {
        assertEquals(3, Milestones.daysOfUse((now - 3 * day - 5).toString(), now))
        assertNull(Milestones.daysOfUse("old", now))
        assertNull(Milestones.daysOfUse(null, now))
        assertEquals(0, Milestones.daysOfUse((now + day).toString(), now), "a clock gone backwards is day 0")
    }
}
