package app.expenses

import app.expenses.core.domain.Money
import app.expenses.core.domain.SearchDates
import app.expenses.core.domain.Trip
import app.expenses.core.domain.TripPhase
import app.expenses.core.domain.TripProblem
import app.expenses.core.domain.TripRules
import app.expenses.core.domain.TripSummaryMath
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.Currency

/** A trip that is on, as the add form needs it: which days it covers and the tag a new expense on them gets ("Tagged for Goa trip"). Small and rebuilt with every refresh. */
data class TripWindowUi(val id: String, val name: String, val tag: String, val startDay: Long, val endDay: Long)

/** Home's card while a trip is on: "Trip: Goa · day 3 of 7 · ₹4,500 spent". Informational, not a nudge (docs/HOME_NUDGES.md). */
data class TripCardUi(
    val id: String, val title: String, val dayText: String, val spentText: String, val spentMinor: Long,
    /** Day [dayNumber] of [length]: the card's progress bar when the trip has no budget. */
    val dayNumber: Int = 0, val length: Int = 0,
    /** Percent of the whole trip budget (the daily budget times the days) spent so far, and that budget as text; null / empty without a daily budget. Arithmetic on the total the card already has. */
    val budgetPercent: Int? = null, val budgetText: String = "",
) {
    /** The one line the card shows, and the words a screen reader reads. */
    val line: String get() = "$title · $dayText · $spentText"
}

/** One trip in the list. [phase] is where it lies in time; [statusText] says it in words ("Day 3 of 7", "Starts in 5 days", "Finished · 7 days"), with "· Off" when it is not tagging. */
data class TripRowUi(
    val id: String, val name: String, val tag: String, val startDay: Long, val endDay: Long, val enabled: Boolean,
    val budgetPlain: String, val datesText: String, val statusText: String, val phase: TripPhase,
)

data class TripsUi(val rows: List<TripRowUi>)

/** One bar of the per-day chart; [description] is what a screen reader says for it ("5 Nov: ₹1,500"). */
data class TripBarUi(val label: String, val amountText: String, val fraction: Float, val isToday: Boolean, val isFuture: Boolean, val description: String)

data class TripCategoryUi(val name: String, val amountText: String, val share: Int, val colorArgb: Long?, val negative: Boolean)

/** The summary of one trip: what was spent on its tagged entries inside its days, by day and by category, against the daily budget when there is one. */
data class TripSummaryUi(
    val id: String, val name: String, val tag: String, val datesText: String, val statusText: String, val enabled: Boolean,
    val startDay: Long, val endDay: Long,
    val totalText: String, val totalMinor: Long, val entryCount: Int, val daysText: String, val averageText: String?,
    val bars: List<TripBarUi>, val categories: List<TripCategoryUi>,
    /** "Daily budget ₹1,000 · ₹3,000 for the 3 days so far", and how that compares ("₹800 under"); null without a budget or before the trip. */
    val budgetLine: String?, val budgetStatus: String?, val budgetOver: Boolean,
    /** "Outside the trip days ₹900.00 · 2 entries": tagged for the trip but dated before or after its days; null when there is none. */
    val outsideText: String?,
    /** Expenses on its days that do not carry the tag: what "Tag N earlier entries" would tag. */
    val untaggedCount: Int,
    val canEndToday: Boolean,
    /** More lines than the page reads: the figures are of the newest part. */
    val capped: Boolean,
)

/** "Tag N earlier entries" waiting for the person's answer: asked after a trip is saved, for the days it gained. [days] null means all of the trip's days. */
data class TripOfferUi(val tripId: String, val tripName: String, val tag: String, val count: Int, val days: List<LongRange>?)

/** What saving a trip came to: a [problem] in words, or [tagUsedBy] (the tag is already on that many entries, which would count in the trip: nothing is saved until the person confirms), or the saved trip with the [offer] to tag earlier entries (null when none lie in its days). */
data class TripSaveOutcome(val problem: TripProblem?, val offer: TripOfferUi?, val tagUsedBy: Long? = null)

internal object TripUiBuilder {
    private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

    fun statusText(trip: Trip, today: Long): String {
        val off = if (trip.enabled) "" else " · Off"
        return when (TripRules.phase(trip, today)) {
            TripPhase.ACTIVE -> "Day ${today - trip.startDay + 1} of ${trip.length}$off"
            TripPhase.UPCOMING -> (trip.startDay - today).let { d -> if (d == 1L) "Starts tomorrow" else "Starts in $d days" } + off
            TripPhase.PAST -> "Finished · ${plural(trip.length, "day", "days")}$off"
        }
    }

    fun rows(trips: List<Trip>, today: Long, currency: Currency): List<TripRowUi> = trips.map { t ->
        TripRowUi(t.id, t.name, t.tag, t.startDay, t.endDay, t.enabled, t.dailyBudgetMinor?.let { Money(it, currency).toPlainString() }.orEmpty(), SearchDates.summary(t.startDay, t.endDay).orEmpty(), statusText(t, today), TripRules.phase(t, today))
    }

    /** The card for the trip that is on today, or null. [spent] is what its tagged entries on its days add up to. */
    fun card(trip: Trip, today: Long, spentMinor: Long, currency: Currency): TripCardUi {
        val title = if (trip.name.startsWith("trip", ignoreCase = true)) trip.name else "Trip: ${trip.name}"
        return TripCardUi(
            trip.id, title, "day ${today - trip.startDay + 1} of ${trip.length}", Money(spentMinor, currency).formatted() + " spent", spentMinor,
            dayNumber = (today - trip.startDay + 1).toInt(), length = trip.length,
            budgetPercent = TripRules.budgetPercent(trip, spentMinor), budgetText = TripRules.totalBudgetMinor(trip)?.let { Money(it, currency).formatted() }.orEmpty(),
        )
    }

    /** How the top of the category list reads: at most [LIMIT] rows, the rest added into "Everything else". */
    private const val LIMIT = 6

    fun summary(trip: Trip, s: app.expenses.core.domain.TripSummary, today: Long, currency: Currency, rows: List<app.expenses.core.domain.CategoryRow>, colors: Map<String, Long?>, untagged: Int): TripSummaryUi {
        fun money(minor: Long) = Money(minor, currency).formatted()
        val tallest = s.bars.maxOfOrNull { it.minor }?.takeIf { it > 0 } ?: 1L
        val bars = s.bars.map { b ->
            val range = SearchDates.summary(b.startDay, b.endDay).orEmpty()
            TripBarUi(TripSummaryMath.barLabel(b), money(b.minor), (b.minor.coerceAtLeast(0).toFloat() / tallest), b.isToday, b.isFuture, "$range: ${money(b.minor)}")
        }
        val shown = rows.take(LIMIT).map { r -> TripCategoryUi(r.name, r.total.formatted(), r.share, r.categoryId?.let { colors[it] }, r.total.minorUnits < 0) }
        val rest = rows.drop(LIMIT)
        val cats = if (rest.isEmpty()) shown else shown + TripCategoryUi("Everything else", Money(rest.sumOf { it.total.minorUnits }, currency).formatted(), rest.sumOf { it.share }, null, false)
        val budget = s.budget
        val budgetLine = budget?.let { "Daily budget ${money(it.dailyMinor)} · ${money(it.allowedMinor)} for ${plural(it.daysCounted, "day", "days")}${if (s.phase == TripPhase.ACTIVE) " so far" else ""}" }
        val status = budget?.let { if (it.differenceMinor >= 0) "${money(it.differenceMinor)} under" else "${money(-it.differenceMinor)} over" }
        val daysText = if (s.phase == TripPhase.ACTIVE) "Day ${s.daysElapsed} of ${s.daysTotal}" else plural(s.daysTotal, "day", "days")
        return TripSummaryUi(
            trip.id, trip.name, trip.tag, SearchDates.summary(trip.startDay, trip.endDay).orEmpty(), statusText(trip, today), trip.enabled, trip.startDay, trip.endDay,
            money(s.totalMinor), s.totalMinor, s.entryCount, daysText, s.averagePerDayMinor?.let { money(it) },
            bars, cats, budgetLine, status, budget != null && budget.differenceMinor < 0,
            if (s.outsideCount > 0 || s.outsideMinor != 0L) "Outside the trip days ${money(s.outsideMinor)} · ${plural(s.outsideCount, "entry", "entries")}" else null, untagged, s.phase == TripPhase.ACTIVE && trip.endDay > today, s.capped,
        )
    }
}

/** What a restore says about the trips it added: nothing when none, else a sentence to append to the restore message. */
fun tripsRestoredText(added: Int): String = if (added <= 0) "" else if (added == 1) " 1 trip was added." else " $added trips were added."
