package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SnoozeTest {
    @Test fun theSecondButtonIsEveningUntilThreeInTheAfternoonThenThreeHours() {
        assertEquals(SnoozeMode.EVENING, Snooze.laterMode(9 * 60)); assertEquals(SnoozeMode.EVENING, Snooze.laterMode(14 * 60 + 59))
        assertEquals(SnoozeMode.HOURS3, Snooze.laterMode(15 * 60)); assertEquals(SnoozeMode.HOURS3, Snooze.laterMode(22 * 60))
    }

    @Test fun eachModeSaysHowLongToWait() {
        assertEquals(180, Snooze.delayMinutes(SnoozeMode.HOURS3, 10 * 60))
        assertEquals(8 * 60, Snooze.delayMinutes(SnoozeMode.EVENING, 10 * 60), "10:00 to 18:00")
        assertEquals(180, Snooze.delayMinutes(SnoozeMode.EVENING, 17 * 60 + 45), "too close to six: three hours instead")
        assertEquals(23 * 60, Snooze.delayMinutes(SnoozeMode.TOMORROW, 10 * 60), "10:00 to 09:00 the next day")
        assertEquals(9 * 60, Snooze.delayMinutes(SnoozeMode.TOMORROW, 0), "just after midnight")
        assertEquals(24 * 60 + 9 * 60 - 23 * 60 - 59, Snooze.delayMinutes(SnoozeMode.TOMORROW, 23 * 60 + 59))
    }
}
