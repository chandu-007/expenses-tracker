package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * A trip (Travel mode): a name, the ONE tag its expenses get, the first and last day (both included, calendar days counted since 1970-01-01 as the Search dates are, so a trip means the same days
 * in whatever zone the phone is in), whether it is on, and an optional daily budget in minor units of the ledger's currency. While a trip is on, a new expense dated inside it is tagged with [tag] when it is
 * created (see [TripRules.tripAt]); nothing is converted: the ledger stays in its one currency.
 */
data class Trip(
    val id: String,
    val name: String,
    val tag: String,
    val startDay: Long,
    val endDay: Long,
    val enabled: Boolean = true,
    val dailyBudgetMinor: Long? = null,
) {
    /** How many days the trip lasts, the first and the last included. */
    val length: Int get() = (endDay - startDay + 1).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun covers(day: Long): Boolean = day in startDay..endDay
}

enum class TripPhase { UPCOMING, ACTIVE, PAST }

/** Why a trip as typed cannot be saved: the words for the person and the field they belong to. */
data class TripProblem(val field: TripField, val message: String)

enum class TripField { NAME, TAG, START, END, BUDGET, DATES }

sealed interface TripResult {
    data class Ok(val trip: Trip) : TripResult
    data class Problem(val problem: TripProblem) : TripResult
}

object TripRules {
    const val MAX_NAME = 40
    const val MAX_DAYS = 366

    /** A restore takes at most this many trips from a file; a person has a handful. */
    const val MAX_TRIPS = 200

    /** Daily budget limit in minor units; times [MAX_DAYS] it stays far below 2^53, where the balance queries stop being exact. */
    const val MAX_BUDGET_MINOR = 100_000_000_000L

    /** 2000-01-01 and 2099-12-31 as epoch days: a day outside them is a typing slip or a damaged file, not a trip. */
    const val FIRST_DAY = 10_957L
    const val LAST_DAY = 47_481L

    /** The tag suggested for a trip called [name] ("Trip: Goa"), cut to what a tag may hold; null when the name is empty. */
    fun suggestedTag(name: String): String? = cleanName(name)?.let { TagRules.normalize("Trip: $it") }

    /** A name as it is kept: runs of spaces made one, nothing at the ends, no line breaks; null when nothing is left. */
    fun cleanName(text: String): String? = text.replace(Regex("\\s+"), " ").trim().ifEmpty { null }

    /** Where [day] falls compared with [trip]. */
    fun phase(trip: Trip, today: Long): TripPhase = when {
        today < trip.startDay -> TripPhase.UPCOMING
        today > trip.endDay -> TripPhase.PAST
        else -> TripPhase.ACTIVE
    }

    /** The trip that is on today, if there is one: enabled and [today] inside its days. */
    fun activeOn(trips: List<Trip>, today: Long): Trip? = trips.firstOrNull { it.enabled && it.covers(today) }

    /** "Day 3 of 7": the number of [today] in the trip (1 for the first day), or null outside it. */
    fun dayNumber(trip: Trip, today: Long): Int? = if (trip.covers(today)) (today - trip.startDay + 1).toInt() else null

    /** The first instant of the trip's first day and the first instant after its last day, in [zone]. */
    fun fromMillis(trip: Trip, zone: TimeZone): Long = SearchDates.fromMillis(trip.startDay, zone)
    fun untilMillis(trip: Trip, zone: TimeZone): Long = SearchDates.untilMillis(trip.endDay, zone)

    /** Whether an entry made at [occurredAt] falls on one of the trip's days, by the calendar of [zone]: 00:00 of the first day is inside, 23:59:59.999 of the last day is inside, 00:00 of the day after is not. */
    fun covers(trip: Trip, occurredAt: Long, zone: TimeZone): Boolean = occurredAt >= fromMillis(trip, zone) && occurredAt < untilMillis(trip, zone)

    /** The trip that tags a new expense made at [occurredAt]: the first one that is ON and covers it. Trips never overlap (see [overlapping]), so there is at most one. */
    fun tripAt(trips: List<Trip>, occurredAt: Long, zone: TimeZone): Trip? = trips.firstOrNull { it.enabled && covers(it, occurredAt, zone) }

    /** An enabled trip in [others] (not [trip] itself) that shares a day with [trip]; null when [trip] is off or nothing overlaps. */
    fun overlapping(trip: Trip, others: List<Trip>): Trip? =
        if (!trip.enabled) null else others.firstOrNull { it.id != trip.id && it.enabled && it.startDay <= trip.endDay && trip.startDay <= it.endDay }

    /** Another trip (on or off, past or coming) that already has [trip]'s tag, compared as the tag system compares tags: tidied and ignoring capitals. A tag belongs to one trip only, so a trip's figures are exactly what that tag lists. */
    fun tagTaken(trip: Trip, others: List<Trip>): Trip? {
        val mine = TagRules.normalize(trip.tag)?.lowercase() ?: return null
        return others.firstOrNull { it.id != trip.id && TagRules.normalize(it.tag)?.lowercase() == mine }
    }

    private fun tagText(other: Trip) = "The tag \"${other.tag}\" already belongs to your trip \"${other.name}\" (${SearchDates.summary(other.startDay, other.endDay)}). A tag can belong to one trip only. Choose another tag."

    private fun overlapText(other: Trip) = "These dates overlap your trip \"${other.name}\" (${SearchDates.summary(other.startDay, other.endDay)}), which is on. Change the dates, or turn that trip off."

    /**
     * The trip as typed in the form. [others] are the trips already kept (a trip being edited may be among them: it is not compared with itself). The reasons are plain sentences. [budgetText] empty means no
     * daily budget; [currency] reads it.
     */
    fun build(id: String, nameText: String, tagText: String, startDay: Long?, endDay: Long?, budgetText: String, enabled: Boolean, others: List<Trip>, currency: Currency): TripResult {
        fun no(field: TripField, message: String) = TripResult.Problem(TripProblem(field, message))
        val name = cleanName(nameText) ?: return no(TripField.NAME, "Give the trip a name, for example Goa.")
        if (name.length > MAX_NAME) return no(TripField.NAME, "A trip name can have up to $MAX_NAME characters.")
        val tag = TagRules.normalize(tagText) ?: return no(TripField.TAG, "Type the tag to put on this trip's expenses, for example Trip: Goa.")
        if (tagText.replace(',', ' ').trim().trimStart('#').replace(Regex("\\s+"), " ").trim().length > TagRules.MAX_LENGTH) return no(TripField.TAG, "A tag can have up to ${TagRules.MAX_LENGTH} characters. Make it shorter.")
        if (startDay == null) return no(TripField.START, "Choose the first day of the trip.")
        if (endDay == null) return no(TripField.END, "Choose the last day of the trip.")
        if (endDay < startDay) return no(TripField.DATES, "The last day is before the first day. Change one of them.")
        if (startDay < FIRST_DAY || endDay > LAST_DAY) return no(TripField.DATES, "Choose days between 2000 and 2099.")
        if (endDay - startDay + 1 > MAX_DAYS) return no(TripField.DATES, "A trip can last up to $MAX_DAYS days. For a longer stay, make two trips.")
        val budget: Long? = if (budgetText.isBlank()) null else {
            val money = parseAmount(budgetText, currency) ?: return no(TripField.BUDGET, "Type the daily budget as an amount, or leave it empty.")
            if (money.minorUnits <= 0L) return no(TripField.BUDGET, "The daily budget must be more than zero, or leave it empty.")
            if (money.minorUnits > MAX_BUDGET_MINOR) return no(TripField.BUDGET, "That daily budget is too large.")
            money.minorUnits
        }
        val trip = Trip(id, name, tag, startDay, endDay, enabled, budget)
        tagTaken(trip, others)?.let { return no(TripField.TAG, tagText(it)) }
        overlapping(trip, others)?.let { return no(TripField.DATES, overlapText(it)) }
        return TripResult.Ok(trip)
    }

    /** What is wrong with a trip that is already built (the store checks this again, so nothing odd is ever kept), or null. */
    fun problemOf(trip: Trip, others: List<Trip>): String? {
        if (trip.id.isBlank()) return "A trip needs an id."
        val cleaned = cleanName(trip.name)
        if (cleaned == null || cleaned != trip.name || cleaned.length > MAX_NAME) return "Give the trip a name of up to $MAX_NAME characters."
        if (TagRules.normalize(trip.tag) != trip.tag) return "The tag of a trip is a tag of up to ${TagRules.MAX_LENGTH} characters."
        if (trip.endDay < trip.startDay) return "The last day is before the first day. Change one of them."
        if (trip.startDay < FIRST_DAY || trip.endDay > LAST_DAY) return "Choose days between 2000 and 2099."
        if (trip.endDay - trip.startDay + 1 > MAX_DAYS) return "A trip can last up to $MAX_DAYS days. For a longer stay, make two trips."
        if (trip.dailyBudgetMinor != null && (trip.dailyBudgetMinor <= 0L || trip.dailyBudgetMinor > MAX_BUDGET_MINOR)) return "The daily budget must be more than zero, or leave it empty."
        tagTaken(trip, others)?.let { return tagText(it) }
        return overlapping(trip, others)?.let(::overlapText)
    }

    /** A trip read from a file made by someone else: made fit (name and tag tidied, a budget outside the limits dropped), or null when it cannot be a trip at all (no id, days outside 2000 to 2099, last day before the first, longer than [MAX_DAYS]). */
    fun fromFile(id: String, name: String, tag: String, startDay: Long, endDay: Long, enabled: Boolean, dailyBudgetMinor: Long?): Trip? {
        val n = cleanName(name)?.take(MAX_NAME)?.trim()?.ifEmpty { null } ?: return null
        val t = TagRules.normalize(tag) ?: return null
        if (id.isBlank() || id.length > 64 || endDay < startDay || startDay < FIRST_DAY || endDay > LAST_DAY || endDay - startDay + 1 > MAX_DAYS) return null
        return Trip(id, n, t, startDay, endDay, enabled, dailyBudgetMinor?.takeIf { it in 1..MAX_BUDGET_MINOR })
    }

    /** The days that [trip] covers and [before] did not, as inclusive ranges: all of [trip] when [before] is null or was off, the added days at either end when its dates grew. Used so a changed trip offers to tag only what is newly inside it. */
    fun newlyCovered(before: Trip?, trip: Trip): List<LongRange> {
        if (!trip.enabled) return emptyList()
        if (before == null || !before.enabled) return listOf(trip.startDay..trip.endDay)
        val out = ArrayList<LongRange>(2)
        if (trip.startDay < before.startDay) out += trip.startDay..minOf(trip.endDay, before.startDay - 1)
        if (trip.endDay > before.endDay) out += maxOf(trip.startDay, before.endDay + 1)..trip.endDay
        return out
    }
}

/** One line of money spent on a trip: when, the category it went to (null: none) and your part of it, as the reports count it (refunds are negative). */
data class TripLine(val entryId: String, val occurredAt: Long, val categoryId: String?, val mineMinor: Long)

/** One bar of the per-day chart: [startDay] to [endDay] (a day, or a week for a long trip), the amount, and where it lies in time. */
data class TripBar(val startDay: Long, val endDay: Long, val minor: Long, val isToday: Boolean, val isFuture: Boolean)

/** A daily budget against what was spent: [allowedMinor] is the budget times the days so far; [differenceMinor] is positive when under it. */
data class TripBudgetStatus(val dailyMinor: Long, val daysCounted: Int, val allowedMinor: Long, val spentMinor: Long) {
    val differenceMinor: Long get() = allowedMinor - spentMinor
}

data class TripSummary(
    val trip: Trip,
    val phase: TripPhase,
    val totalMinor: Long,
    val entryCount: Int,
    /** Days counted so far: 0 before the trip, the day number during it, all of them after. */
    val daysElapsed: Int,
    val daysTotal: Int,
    /** Spent per elapsed day, rounded to the nearest minor unit; null before the first day. */
    val averagePerDayMinor: Long?,
    val bars: List<TripBar>,
    /** Net spending by category id (null: no category), without zero rows. */
    val byCategory: Map<String?, Long>,
    val budget: TripBudgetStatus?,
    /** What carries the trip's tag but is dated outside its days (a booking paid earlier, a refund that came later, older entries): in [totalMinor] and [entryCount], not in the bars, the average or the budget. */
    val outsideMinor: Long,
    val outsideCount: Int,
    /** True when there were more lines than were read: the figures are then of the newest part. */
    val capped: Boolean,
)

object TripSummaryMath {
    /** Trips up to this many days get a bar for each day; longer ones a bar for each week. */
    const val DAILY_BARS_UP_TO = 31

    fun of(trip: Trip, lines: List<TripLine>, zone: TimeZone, today: Long, capped: Boolean = false): TripSummary {
        val phase = TripRules.phase(trip, today)
        val daysTotal = trip.length
        val daysElapsed = when (phase) { TripPhase.UPCOMING -> 0; TripPhase.ACTIVE -> (today - trip.startDay + 1).toInt(); TripPhase.PAST -> daysTotal }
        var total = 0L; var inside = 0L; var outside = 0L
        val perDay = LongArray(daysTotal)
        val byCategory = LinkedHashMap<String?, Long>()
        val entries = HashSet<String>(); val outsideEntries = HashSet<String>()
        for (l in lines) {
            total += l.mineMinor
            entries += l.entryId
            byCategory[l.categoryId] = (byCategory[l.categoryId] ?: 0L) + l.mineMinor
            val day = dateOf(l.occurredAt, zone).toEpochDays().toLong()
            if (day < trip.startDay || day > trip.endDay) { outside += l.mineMinor; outsideEntries += l.entryId }
            else { inside += l.mineMinor; perDay[(day - trip.startDay).toInt()] += l.mineMinor }
        }
        val bars = ArrayList<TripBar>()
        val step = if (daysTotal <= DAILY_BARS_UP_TO) 1 else 7
        var i = 0
        while (i < daysTotal) {
            val last = minOf(i + step, daysTotal) - 1
            var sum = 0L
            for (k in i..last) sum += perDay[k]
            val a = trip.startDay + i; val b = trip.startDay + last
            bars += TripBar(a, b, sum, isToday = today in a..b, isFuture = a > today)
            i += step
        }
        val average = if (daysElapsed == 0) null else if (inside >= 0) (inside + daysElapsed / 2) / daysElapsed else -((-inside + daysElapsed / 2) / daysElapsed)
        val budget = trip.dailyBudgetMinor?.takeIf { daysElapsed > 0 }?.let { TripBudgetStatus(it, daysElapsed, it * daysElapsed, inside) }
        return TripSummary(trip, phase, total, entries.size, daysElapsed, daysTotal, average, bars, byCategory.filterValues { it != 0L }, budget, outside, outsideEntries.size, capped)
    }

    /** The label of a bar: the day of the month for a day, "3 Nov" for a week's first day. */
    fun barLabel(bar: TripBar): String {
        val a = LocalDate.fromEpochDays(bar.startDay.toInt())
        return if (bar.startDay == bar.endDay) a.day.toString() else "${a.day} ${MONTHS[a.month.ordinal]}"
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
