package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstWeekTest {
    private val day = 86_400_000L
    private val start = 1_000_000_000_000L
    private fun inputs(
        now: Long = start + day, started: Long? = start, dismissed: Boolean = false, digits: Boolean = false, budget: Boolean = false,
        capture: FirstWeekCapture = FirstWeekCapture.SWITCH, captureOn: Boolean = false, backup: Boolean = false, entry: Boolean = true,
    ) = FirstWeekInputs(started, now, dismissed, digits, budget, capture, captureOn, backup, entry)

    @Test
    fun fiveStepsInOrderEachTickedByWhatIsTrue() {
        val items = FirstWeek.items(inputs(digits = true, budget = false, captureOn = false, backup = true, entry = true))
        assertEquals(listOf(FirstWeekStep.ACCOUNT_DIGITS, FirstWeekStep.BUDGET, FirstWeekStep.CAPTURE, FirstWeekStep.BACKUP, FirstWeekStep.ENTRY), items.map { it.step })
        assertEquals(listOf(true, false, false, true, true), items.map { it.done })
    }

    @Test
    fun nothingBeforeTheFirstEntryAndNothingOnceTheWeekHasPassed() {
        assertEquals(emptyList(), FirstWeek.items(inputs(started = null, entry = false)))
        assertEquals(5, FirstWeek.items(inputs(now = start + 14 * day - 1)).size, "the last moment of day 14")
        assertEquals(emptyList(), FirstWeek.items(inputs(now = start + 14 * day)))
        assertEquals(emptyList(), FirstWeek.items(inputs(now = start + 400 * day)))
        assertEquals(5, FirstWeek.items(inputs(now = start - 5 * day)).size, "a clock that went backwards still shows it")
    }

    @Test
    fun closedOrCompleteHidesTheCard() {
        assertEquals(emptyList(), FirstWeek.items(inputs(dismissed = true)))
        assertEquals(emptyList(), FirstWeek.items(inputs(digits = true, budget = true, captureOn = true, backup = true, entry = true)))
        assertTrue(FirstWeek.items(inputs(digits = true, budget = true, captureOn = true, backup = true, entry = false)).isNotEmpty(), "one step left")
    }

    @Test
    fun theCaptureStepFollowsThePlatform() {
        assertEquals("Turn on capture", FirstWeek.items(inputs()).single { it.step == FirstWeekStep.CAPTURE }.title)
        assertEquals("Set up capture and send a test", FirstWeek.items(inputs(capture = FirstWeekCapture.SHORTCUT)).single { it.step == FirstWeekStep.CAPTURE }.title)
        val none = FirstWeek.items(inputs(capture = FirstWeekCapture.NONE))
        assertEquals(4, none.size); assertTrue(none.none { it.step == FirstWeekStep.CAPTURE })
        assertEquals(emptyList(), FirstWeek.items(inputs(capture = FirstWeekCapture.NONE, digits = true, budget = true, backup = true)), "complete without the step that does not exist")
    }
}
