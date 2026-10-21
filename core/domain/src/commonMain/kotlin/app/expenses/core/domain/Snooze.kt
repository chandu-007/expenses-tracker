package app.expenses.core.domain

/** The ways a bill notification can be put off: its buttons ("This evening" or "In 3 hours", and "Tomorrow morning"). */
enum class SnoozeMode { HOURS3, EVENING, TOMORROW }

object Snooze {
    const val EVENING_MINUTE = 18 * 60
    const val MORNING_MINUTE = 9 * 60

    /** Before three in the afternoon the second button says "This evening" (six o'clock); later it is "In 3 hours", since evening is too close. */
    fun laterMode(minuteOfDay: Int): SnoozeMode = if (minuteOfDay < 15 * 60) SnoozeMode.EVENING else SnoozeMode.HOURS3

    fun label(mode: SnoozeMode) = when (mode) { SnoozeMode.HOURS3 -> "In 3 hours"; SnoozeMode.EVENING -> "This evening"; SnoozeMode.TOMORROW -> "Tomorrow" }

    /** Minutes from [minuteOfDay] until the notice comes back: 3 hours, 18:00 today, or 09:00 (this morning if it is not yet nine, else tomorrow). Always positive. */
    fun delayMinutes(mode: SnoozeMode, minuteOfDay: Int): Int = when (mode) {
        SnoozeMode.HOURS3 -> 180
        SnoozeMode.EVENING -> if (minuteOfDay < EVENING_MINUTE - 30) EVENING_MINUTE - minuteOfDay else 180
        SnoozeMode.TOMORROW -> if (minuteOfDay < MORNING_MINUTE) MORNING_MINUTE - minuteOfDay else 24 * 60 - minuteOfDay + MORNING_MINUTE // after midnight, "tomorrow" means this morning
    }
}
