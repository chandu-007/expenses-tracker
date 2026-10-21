package app.expenses.core.domain

/** One expense with a merchant: the day (an epoch-day number) and the amount in minor units. [name] is how the merchant is written on the entry. */
data class SubscriptionSample(val name: String, val day: Int, val amountMinor: Long)

/** A charge that keeps coming: [cadenceDays] apart (about 30, 91 or 365), of about [amountMinor], last on [lastDay], expected again on [nextDay]. */
data class Subscription(val key: String, val name: String, val amountMinor: Long, val cadenceDays: Int, val count: Int, val lastDay: Int, val nextDay: Int) {
    /** Billed by the calendar, not by counting days: a monthly charge is paid 12 times a year, a quarterly one 4 times, a yearly one once. */
    private val timesAYear: Int get() = when (cadenceDays) { 30 -> 12; 91 -> 4; else -> 1 }
    val yearlyMinor: Long get() = amountMinor * timesAYear
    val monthlyMinor: Long get() = yearlyMinor / 12
}

/**
 * Finds charges that repeat like a subscription: the same merchant, about the same amount, at a steady gap (monthly, every three months, yearly), and
 * still going. Utility bills vary in amount and groceries vary in gap, so neither qualifies. It guesses; the person can mark a guess as not a subscription.
 */
object SubscriptionFinder {
    private val cadences = listOf(30 to 27..34, 91 to 85..96, 365 to 350..380)
    private const val AMOUNT_TOLERANCE_PERCENT = 15
    private const val ENOUGH_ALIKE_PERCENT = 70

    fun keyOf(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")

    fun find(samples: List<SubscriptionSample>, today: Int): List<Subscription> =
        samples.filter { it.name.isNotBlank() && it.amountMinor > 0 }.groupBy { keyOf(it.name) }.mapNotNull { (key, all) -> judge(key, all, today) }.sortedByDescending { it.yearlyMinor }

    private fun judge(key: String, all: List<SubscriptionSample>, today: Int): Subscription? {
        val days = all.sortedBy { it.day }.distinctBy { it.day }
        if (days.size < 2) return null
        val gaps = days.zipWithNext { a, b -> b.day - a.day }
        val median = gaps.sorted()[gaps.size / 2]
        val (cadence, window) = cadences.firstOrNull { median in it.second } ?: return null
        if (days.size < (if (cadence == 365) 2 else 3)) return null
        if (gaps.count { it in window } * 100 / gaps.size < 75) return null
        val amounts = days.map { it.amountMinor }.sorted()
        val typical = amounts[amounts.size / 2]
        if (amounts.count { kotlin.math.abs(it - typical) * 100 <= typical * AMOUNT_TOLERANCE_PERCENT } * 100 / amounts.size < ENOUGH_ALIKE_PERCENT) return null
        val last = days.last()
        if (today - last.day > cadence + cadence / 2 + 5) return null // it stopped
        return Subscription(key, days.last().name.trim(), last.amountMinor, cadence, days.size, last.day, last.day + cadence)
    }
}
