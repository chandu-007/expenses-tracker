package app.expenses.core.domain

/** How long ago something was, in words, for lines such as "Last text received 3 days ago". */
object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun ago(thenMillis: Long, nowMillis: Long): String {
        val d = nowMillis - thenMillis
        return when {
            d < MINUTE -> "just now"
            d < 2 * MINUTE -> "a minute ago"
            d < HOUR -> "${d / MINUTE} minutes ago"
            d < 2 * HOUR -> "an hour ago"
            d < DAY -> "${d / HOUR} hours ago"
            d < 2 * DAY -> "yesterday"
            else -> "${d / DAY} days ago"
        }
    }

    /** Whether [thenMillis] is within the last [days] days of [nowMillis]. */
    fun within(thenMillis: Long, nowMillis: Long, days: Int): Boolean = nowMillis - thenMillis <= days * DAY
}
