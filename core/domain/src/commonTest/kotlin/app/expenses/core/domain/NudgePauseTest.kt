package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgePauseTest {
    private val utc = TimeZone.UTC
    private val noon18Nov = startOfDay(LocalDate(2026, 11, 18), utc) + 12 * 3_600_000L

    @Test
    fun noSettingOrAPastTimeIsNotPaused() {
        assertFalse(NudgePause.isPaused(null, noon18Nov))
        assertFalse(NudgePause.isPaused(0L, noon18Nov))
        assertFalse(NudgePause.isPaused(noon18Nov, noon18Nov))
        assertTrue(NudgePause.isPaused(noon18Nov + 1, noon18Nov))
        assertTrue(NudgePause.isPaused(NudgePause.FOREVER, noon18Nov))
    }

    @Test
    fun theLengthsAreAWeekAMonthAndUntilTurnedBackOn() {
        assertEquals(startOfDay(LocalDate(2026, 11, 25), utc), NudgePause.until(NudgePause.Choice.WEEK, noon18Nov, utc))
        assertEquals(startOfDay(LocalDate(2026, 12, 18), utc), NudgePause.until(NudgePause.Choice.MONTH, noon18Nov, utc))
        assertEquals(Long.MAX_VALUE, NudgePause.until(NudgePause.Choice.FOREVER, noon18Nov, utc))
    }

    @Test
    fun theStatusLineSaysUntilWhenOrJustPaused() {
        val week = NudgePause.until(NudgePause.Choice.WEEK, noon18Nov, utc)
        assertEquals("Paused until 25 Nov", NudgePause.status(week, noon18Nov, utc))
        assertEquals("Paused", NudgePause.status(NudgePause.FOREVER, noon18Nov, utc))
        assertNull(NudgePause.status(week, week, utc), "over at the moment it ends")
        assertNull(NudgePause.status(null, noon18Nov, utc))
    }

    @Test
    fun aDueBillIsNeverPausedAndTheOtherCardsAre() {
        assertFalse(HomeNudge.BILLS_DUE in NudgePause.HOME_PAUSED)
        assertEquals(HomeNudge.entries.toSet() - HomeNudge.BILLS_DUE, NudgePause.HOME_PAUSED)
        val shown = HomeNudges.visible(HomeNudge.entries.associateWith { 2 }.filterKeys { it !in NudgePause.HOME_PAUSED }, emptyMap(), noon18Nov)
        assertEquals(listOf(HomeNudge.BILLS_DUE), shown)
    }

    @Test
    fun onlyPlainSuggestionsAmongTheNoticesWait() {
        val paused = NoticeKind.entries.filter { NudgePause.pausesNotice(it) }.toSet()
        assertEquals(setOf(NoticeKind.BILL_FOUND, NoticeKind.BUDGET_PACE, NoticeKind.BUDGET_WRAPUP, NoticeKind.CARD_CLEARED), paused)
        for (kept in listOf(NoticeKind.BILL_DUE, NoticeKind.CARD_DUE, NoticeKind.BILL_TRACKED, NoticeKind.BUDGET_OVER, NoticeKind.BUDGET_NEAR, NoticeKind.WEEKLY_RECAP, NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT)) {
            assertFalse(NudgePause.pausesNotice(kept), "$kept is a commitment or something the person asked for")
        }
    }
}
