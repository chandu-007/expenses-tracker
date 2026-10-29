package app.expenses.core.domain

/** One expense with a merchant: the day (an epoch-day number) and the amount in minor units. [name] is how the merchant is written on the entry. */
data class SubscriptionSample(val name: String, val day: Int, val amountMinor: Long)

/**
 * A charge that keeps coming: [cadenceDays] apart (about 28, 30, 84, 91, 182 or 365), of about [amountMinor] (the latest charge), last on [lastDay], expected again on [nextDay].
 * [previousAmountMinor] is what it cost before its latest change of price, only when the new price has been charged at least twice in a row after a price that had also held for two charges
 * (a one-off discount or an odd charge says nothing); [lastName] is how the merchant was written on the latest charge.
 *
 * [merchantKey] is the merchant as one name and [key] this row ("netflix|30"; a merchant billing several prices has one row for each: "apple services|30|7500"). A row is hidden by
 * [ignoredBy] when the person said "not one" for this merchant and cadence, whatever the price has since become.
 */
data class Subscription(
    val key: String, val name: String, val amountMinor: Long, val cadenceDays: Int, val count: Int, val lastDay: Int, val nextDay: Int,
    val previousAmountMinor: Long? = null, val lastName: String = name, val merchantKey: String = key.substringBefore('|'), val priceKey: Long? = null,
) {
    /** Billed by the calendar, not by counting days: a monthly charge is paid 12 times a year, a quarterly one 4, a half-yearly one 2, a yearly one once; a charge every 28 or 84 days is paid 365 / gap times. */
    val yearlyMinor: Long get() = when (cadenceDays) { 30 -> amountMinor * 12; 91 -> amountMinor * 4; 182 -> amountMinor * 2; 365 -> amountMinor; else -> amountMinor * 365 / cadenceDays }
    val monthlyMinor: Long get() = yearlyMinor / 12
    val priceWentUp: Boolean get() = previousAmountMinor != null && amountMinor > previousAmountMinor
    val priceWentDown: Boolean get() = previousAmountMinor != null && amountMinor < previousAmountMinor

    /**
     * Whether [ignored] (the keys of rows marked "not one") hides this row: the merchant on its own (what an older version stored), the merchant and cadence ("netflix|30": a price change or a
     * shift of the typical price does not bring it back), or, for one price of a merchant that bills several, that cadence and a price within a quarter of this one.
     */
    fun ignoredBy(ignored: Set<String>): Boolean = ignored.any { k ->
        val parts = k.split('|')
        when {
            k == merchantKey -> true
            parts[0] != merchantKey || parts.getOrNull(1)?.toIntOrNull() != cadenceDays -> false
            parts.size < 3 -> true
            else -> { val p = parts[2].toLongOrNull(); val mine = priceKey ?: amountMinor; p != null && kotlin.math.abs(p - mine) * 100 <= mine * 25 }
        }
    }
}

/**
 * Finds charges that repeat like a subscription: the same merchant, about the same amount, at a steady gap (every 28 days, monthly, every 12 weeks, every three or six months, yearly), and
 * still going. Utility bills vary in amount and groceries vary in gap, so neither qualifies. It guesses; the person can mark a guess as not a subscription.
 *
 * **Precision first.** A wrong row inflates "a year" at the top, and "Remind me" makes a repeating item from it, so the finder asks for more evidence than it once did:
 *  - at least **three charges** for a 28-, 30-, 84-, 91- or 182-day pattern for a whole merchant, **four** for each price of a merchant that bills several (three for a known recurring service);
 *  - the gaps must keep the pattern: three in four inside the window, and a skipped cycle (a gap of twice the usual) at most once;
 *  - exactly **two charges** only for a yearly pattern (350 to 380 days apart), and then only when the merchant is a known recurring service ([DefaultRules.isRecurringService]: a streaming,
 *    mobile, insurance, loan, investment or rent service of the merchant table), or both charges are the same price (within 2%), at least 200 rupees, with no other charge of that merchant between them;
 *  - a merchant charged **more than six times within 30 days** is never a subscription, unless it is a known recurring service (a delivery app is not one);
 *  - cash withdrawals (ATM) are never one: "ATM cash" is one name for every withdrawal;
 *  - a **price change** is said only when the new price was charged twice in a row after a price that had also held for two charges.
 *
 * What it tolerates, because real histories are not tidy: a **skipped** cycle (a gap of twice the usual); a **double charge** (two charges within a week count as one); a **price change** (one
 * step up or down, however large, and a gentle drift of up to 15%, which is what a dollar subscription billed in rupees does); the merchant **written differently** ("APPLE.COM/BILL",
 * "Apple Services", "NETFLIX.COM", "GOOGLE *YouTubePremium"), which the merchant table ([DefaultRules.displayNameFor]) reads as one clean name; and one merchant billing **several subscriptions of
 * different prices** (Apple's iCloud, Music and App Store all show as one merchant), which are told apart by their amount.
 *
 * **Monthly or every 28 days?** By the calendar a monthly charge is 28 days apart only from February to March, so gaps of 28 to 34 days are one monthly pattern (a series of three charges that crosses
 * February is monthly). A charge every 28 days (a prepaid plan) is told by its day of the month: with at least four charges, every gap 27 to 29 days (never possible for three months in a row)
 * and a day of the month that drifts by two days or more, it is a 28-day pattern; a charge that falls on the same day of the month is monthly however its gaps fall.
 */
object SubscriptionFinder {
    private data class Cadence(val days: Int, val window: IntRange)

    private val monthly = Cadence(30, 27..34)
    private val every28 = Cadence(28, 27..29)
    /** The other gaps that make a cadence; the windows do not overlap those of the monthly pattern. */
    private val others = listOf(Cadence(84, 80..86), Cadence(91, 87..96), Cadence(182, 170..195), Cadence(365, 350..380))
    private const val AMOUNT_TOLERANCE_PERCENT = 15
    private const val ENOUGH_ALIKE_PERCENT = 70
    private const val CHANGE_PERCENT = 5
    private const val SAME_CHARGE_DAYS = 7
    private const val SAME_PRICE_PERCENT = 10
    private const val MOST_CHARGES_IN_30_DAYS = 6
    private const val MIN_ONE_OFF_YEARLY_MINOR = 20_000L

    /** The merchant as one name: the clean name from the merchant table when the text is a known service ("APPLE.COM/BILL" and "Apple Services" are both "apple services"), else the text in lower case with its spaces tidied. */
    fun keyOf(name: String): String = (DefaultRules.displayNameFor(name) ?: name).trim().lowercase().replace(Regex("\\s+"), " ")

    /** Cash taken out at an ATM is one merchant to the finder ("ATM cash", "ATM-CASH") and so would look like a bill: it is left out. */
    private fun isCashWithdrawal(name: String): Boolean = name.trim().lowercase().let { it == "cash" || it == "cash withdrawal" || Regex("^atm\\b").containsMatchIn(it) }

    fun find(samples: List<SubscriptionSample>, today: Int): List<Subscription> =
        samples.filter { it.name.isNotBlank() && it.amountMinor > 0 && !isCashWithdrawal(it.name) }.groupBy { keyOf(it.name) }.flatMap { (key, all) -> judgeMerchant(key, all, today) }.sortedByDescending { it.yearlyMinor }

    /**
     * The merchant as a whole, or each of its prices on its own when it bills several things at once. Two prices that are charged during the same months (Apple's iCloud at 75 and Music at 219
     * both show as "APPLE.COM/BILL") are two subscriptions; one price that follows another (a rise from 75 to 99) is one subscription whose price changed.
     */
    private fun judgeMerchant(key: String, all: List<SubscriptionSample>, today: Int): List<Subscription> {
        val known = DefaultRules.isRecurringService(all.maxBy { it.day }.name)
        if (!known && mostIn30Days(all) > MOST_CHARGES_IN_30_DAYS) return emptyList()
        val prices = byPrice(all).filter { it.size >= 2 }
        if (prices.size >= 2 && interleaved(prices)) {
            val name = display(all)
            return prices.mapNotNull { cluster ->
                val typical = cluster.map { it.amountMinor }.sorted()[cluster.size / 2]
                judge(key, "$name (${plain(typical)})", all, cluster, today, known, typical)
            }
        }
        return listOfNotNull(judge(key, display(all), all, all, today, known, null))
    }

    /** The most charges of one merchant in any 30 days. */
    private fun mostIn30Days(all: List<SubscriptionSample>): Int {
        val d = all.map { it.day }.sorted()
        var best = 0; var lo = 0
        for (hi in d.indices) { while (d[hi] - d[lo] >= 30) lo++; best = maxOf(best, hi - lo + 1) }
        return best
    }

    /** Whether some two of the price groups are charged over the same stretch of time (each spans at least a third of the shorter one's days in common), which one price following another is not. */
    private fun interleaved(groups: List<List<SubscriptionSample>>): Boolean {
        for (i in groups.indices) for (j in i + 1 until groups.size) {
            val a = groups[i].minOf { it.day }..groups[i].maxOf { it.day }; val b = groups[j].minOf { it.day }..groups[j].maxOf { it.day }
            val common = minOf(a.last, b.last) - maxOf(a.first, b.first)
            val shorter = minOf(a.last - a.first, b.last - b.first)
            if (common > 0 && common * 3 >= shorter) return true
        }
        return false
    }

    /** The name to show: the clean name of a known service, else the merchant as the latest charge wrote it. */
    private fun display(all: List<SubscriptionSample>): String {
        val latest = all.maxBy { it.day }.name.trim()
        return DefaultRules.displayNameFor(latest) ?: latest
    }

    private fun plain(minor: Long): String = if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

    /** Charges grouped by price: sorted by amount, a new group starts when a charge costs more than [SAME_PRICE_PERCENT] over the cheapest of the group so far. */
    private fun byPrice(all: List<SubscriptionSample>): List<List<SubscriptionSample>> {
        val out = ArrayList<MutableList<SubscriptionSample>>()
        for (s in all.sortedBy { it.amountMinor }) {
            val g = out.lastOrNull()
            if (g != null && s.amountMinor * 100 <= g.first().amountMinor * (100 + SAME_PRICE_PERCENT)) g += s else out += mutableListOf(s)
        }
        return out
    }

    /** [all] is every charge of the merchant, [group] the ones being judged (all of them, or one price of them); [price] is that price when it is one of several. */
    private fun judge(merchantKey: String, name: String, all: List<SubscriptionSample>, group: List<SubscriptionSample>, today: Int, known: Boolean, price: Long?): Subscription? {
        // one charge a day, and two charges within a week are one (a double charge, or a retry after a failure)
        val days = ArrayList<SubscriptionSample>()
        for (s in group.sortedWith(compareBy({ it.day }, { it.amountMinor }))) if (days.isEmpty() || s.day - days.last().day > SAME_CHARGE_DAYS) days += s
        if (days.size < 2) return null
        val cadence = cadenceOf(days) ?: return null
        // one price of several needs four charges at an unknown merchant: three same-priced orders a month apart happen by chance at a shop that is used often
        if (price != null && !known && days.size < 4 && cadence.days != 365) return null
        if (days.size < 3) {
            // two charges are a pattern only for a yearly one, and only with more than the chance of two orders at the same price
            if (cadence.days != 365) return null
            val a = days[0]; val b = days[1]
            val samePrice = kotlin.math.abs(a.amountMinor - b.amountMinor) * 100 <= a.amountMinor * 2
            val alone = price == null && all.none { it.day > a.day + SAME_CHARGE_DAYS && it.day < b.day - SAME_CHARGE_DAYS }
            if (!(known || (samePrice && a.amountMinor >= MIN_ONE_OFF_YEARLY_MINOR && alone))) return null
        }
        val gaps = days.zipWithNext { a, b -> b.day - a.day }
        // the gaps keep the pattern: at least three in four are in the window, and a gap of twice the usual (a skipped cycle) is allowed once, not as a way of fitting random gaps
        val inWindow = gaps.count { it in cadence.window }
        val skipped = gaps.count { it in 2 * cadence.window.first..2 * cadence.window.last }
        if (skipped > 1 || inWindow * 100 / gaps.size < 75) return null
        val amounts = days.map { it.amountMinor }
        if (!amountsFit(amounts, cadence.days)) return null
        val last = days.last()
        if (today - last.day > cadence.days + cadence.days / 2 + 5) return null // it stopped
        val next = last.day + gaps.filter { it in cadence.window }.sorted().let { if (it.isEmpty()) cadence.days else it[it.size / 2] }
        val key = "$merchantKey|${cadence.days}" + (price?.let { "|$it" } ?: "")
        return Subscription(key, name, last.amountMinor, cadence.days, days.size, last.day, next, priceNote(amounts), last.name.trim(), merchantKey, price)
    }

    /** The cadence the charges keep, from the middle gap, with the monthly pattern told from the 28-day one by the day of the month. Null when the gaps fit none. */
    private fun cadenceOf(days: List<SubscriptionSample>): Cadence? {
        val gaps = days.zipWithNext { a, b -> b.day - a.day }
        val median = gaps.sorted()[gaps.size / 2]
        if (median in monthly.window) {
            if (gaps.size >= 3 && gaps.all { it in every28.window } && driftsOverMonths(days)) return every28
            return monthly
        }
        return others.firstOrNull { median in it.window }
    }

    /** Whether the day of the month of the charges moves (by two days or more): a monthly charge stays on its day, a 28-day one slides back a few days each month. */
    private fun driftsOverMonths(days: List<SubscriptionSample>): Boolean {
        val d = days.map { kotlinx.datetime.LocalDate.fromEpochDays(it.day).day }
        val first = d.first()
        return d.any { val x = kotlin.math.abs(it - first); minOf(x, 30 - x) >= 2 }
    }

    /** Whether the amounts are one price (within [AMOUNT_TOLERANCE_PERCENT] of the usual), or one price and then another (a change of price, however big, the later one charged twice, or once for a cadence of half a year or more). */
    private fun amountsFit(amounts: List<Long>, cadence: Int): Boolean {
        val sorted = amounts.sorted()
        val typical = sorted[sorted.size / 2]
        val alike = amounts.count { kotlin.math.abs(it - typical) * 100 <= typical * AMOUNT_TOLERANCE_PERCENT }
        if (alike * 100 / amounts.size >= ENOUGH_ALIKE_PERCENT) return true
        val needed = if (cadence >= 182) 1 else 2
        for (k in 2..amounts.size - needed) if (holds(amounts.subList(0, k)) && holds(amounts.subList(k, amounts.size))) return true
        return false
    }

    /**
     * What it cost before the latest change of price, or null. A change is a step of [CHANGE_PERCENT] or more between neighbouring charges; it is said only when the latest price was charged at least
     * twice in a row and the price before it at least twice too, so one odd charge or a one-month discount (99, 99, 79, 99, 99) changes nothing and a drifting dollar price (steps under 5%) says nothing.
     */
    private fun priceNote(amounts: List<Long>): Long? {
        val runs = ArrayList<MutableList<Long>>()
        for (a in amounts) {
            val r = runs.lastOrNull()
            if (r != null && kotlin.math.abs(a - r.last()) * 100 < r.last() * CHANGE_PERCENT) r += a else runs += mutableListOf(a)
        }
        if (runs.size < 2) return null
        val now = runs.last(); val before = runs[runs.size - 2]
        return if (now.size >= 2 && before.size >= 2) before.last() else null
    }

    private fun holds(run: List<Long>): Boolean {
        val typical = run.sorted()[run.size / 2]
        return run.count { kotlin.math.abs(it - typical) * 100 <= typical * AMOUNT_TOLERANCE_PERCENT } * 100 / run.size >= 80
    }
}
