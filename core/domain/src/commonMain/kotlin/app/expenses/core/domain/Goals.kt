package app.expenses.core.domain

/**
 * Something to save up for. [savedMinor] is what has been put aside by hand; when [accountId] is set the goal instead follows that account's balance
 * (a savings account kept for it). [dueDay] is the epoch day to reach it by, or null.
 */
data class Goal(val id: String, val name: String, val targetMinor: Long, val savedMinor: Long, val dueDay: Int?, val accountId: String?) {
    init { require(targetMinor > 0) { "A goal needs a target above zero" } }
}

object GoalMath {
    /** Whole percent reached, 0 to 100 (more than reached still reads 100). */
    fun percent(saved: Long, target: Long): Int = if (target <= 0 || saved <= 0) 0 else (saved * 100 / target).coerceAtMost(100L).toInt()

    fun remaining(saved: Long, target: Long): Long = (target - saved).coerceAtLeast(0)

    /** What to put aside each month to get there by [dueDay], rounded up; null with no date, nothing left, or a date already past (then there is no month left to spread it over). */
    fun monthly(saved: Long, target: Long, dueDay: Int?, today: Int): Long? {
        val left = remaining(saved, target)
        if (dueDay == null || left == 0L || dueDay <= today) return null
        val months = ((dueDay - today) + 29) / 30 // whole months, a part month counts as one
        return (left + months - 1) / months
    }
}

/** Goals kept as one small text setting: a line each, fields separated by a tab. Names cannot hold tabs or line breaks, so there is nothing to escape. */
object GoalCodec {
    fun encode(goals: List<Goal>): String = goals.joinToString("\n") { listOf(it.id, clean(it.name), it.targetMinor, it.savedMinor, it.dueDay ?: "", it.accountId.orEmpty()).joinToString("\t") }

    fun decode(text: String?): List<Goal> = text.orEmpty().split('\n').mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 6) return@mapNotNull null
        runCatching { Goal(f[0], f[1], f[2].toLong(), f[3].toLong(), f[4].toIntOrNull(), f[5].ifEmpty { null }) }.getOrNull()
    }

    fun clean(name: String) = name.filter { it != '\t' && it != '\n' && it != '\r' }.trim().take(60)
}
