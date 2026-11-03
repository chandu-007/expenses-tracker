package app.expenses.core.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** How this month's spending compares with last month's up to the same day: [arrow] is "↓" (less), "↑" (more) or "→" (the same), [percent] the text beside it ("12%"), [spoken] what a screen reader says. */
data class WidgetDelta(val direction: DeltaDirection, val arrow: String, val percent: String, val spoken: String)

enum class DeltaDirection { LESS, MORE, SAME }

/** The overall monthly budget as one slim bar: [permille] of the limit used (0 to 1000, never more), [over] when the limit is passed, [text] like "₹5K left of ₹20K". */
data class WidgetBudget(val permille: Int, val over: Boolean, val text: String)

/** One of the top categories: its [name], [amountText] (short: ₹12.3K) and its share of the month's spending in [permille] (0 to 1000). */
data class WidgetCategoryBar(val name: String, val amountText: String, val permille: Int)

/** One recent entry: [title], signed [amountText], [whenText] ("Today", "Yesterday", "3 Nov"), [income] for a coloured plus. */
data class WidgetRecent(val title: String, val amountText: String, val whenText: String, val income: Boolean)

/** A recent entry as the database has it, before it is worded for the widget. [kind] is the entry's kind (EXPENSE, INCOME, ...). */
data class RecentInput(val kind: String, val title: String?, val categoryName: String?, val amount: Money, val at: Long)

/** The detail the bigger widgets add under the month's total. Every figure in it is a real amount, so it exists only when the total itself may be shown ([WidgetSummary.insights]). */
data class WidgetInsights(val delta: WidgetDelta?, val budget: WidgetBudget?, val categories: List<WidgetCategoryBar>, val recent: List<WidgetRecent>) {
    companion object { val NONE = WidgetInsights(null, null, emptyList(), emptyList()) }
}

/** What there is to show, for [WidgetSummary.plan]: how many chips, categories and recent entries exist, whether there is a comparison and a budget, and whether the figures may be shown. */
data class WidgetHave(val chips: Int, val categories: Int, val recent: Int, val delta: Boolean, val budget: Boolean, val undo: Boolean, val revealed: Boolean)

/** How a widget of a given size is laid out. */
enum class WidgetTier {
    /** About 2 x 1: the figure, the eye and a round Add button. */
    TINY,
    /** About 2 x 2: label, figure, comparison, Add. */
    SQUARE,
    /** About 4 x 1: figure, one or two chips, Add, in one row. */
    WIDE,
    /** Anything wider than 2 cells and taller than 1: a header row, then as many sections as the height holds. */
    CARD,
}

/**
 * What a widget draws at one size: the [tier], and for the [WidgetTier.CARD] which sections fit, in the order they are given up when space runs out (the last goes first):
 * undo line, chips, budget bar, top categories, recent entries (the comparison is drawn in the header's label row and takes no height). Counts are how many rows of each.
 */
data class WidgetPlan(
    val tier: WidgetTier, val chips: Int, val showUndo: Boolean, val showDelta: Boolean, val showBudget: Boolean, val categories: Int, val recent: Int, val showHint: Boolean, val wideAdd: Boolean, val chipsInHeader: Boolean,
    /** A quiet line saying the figures are hidden, where a revealed widget would show them (CARD only, with room for it). */
    val hiddenNote: Boolean = false,
)

/**
 * The words and shapes of the home-screen widgets, as plain data so they can be tested without a phone. The Android widget only draws what is here.
 * **Privacy:** the month's total is masked until the person shows it with the eye, and never shown while the app's Hide amounts or the App lock is on; the comparison, the budget, the categories
 * and the recent entries (with their shops) are held to the same rule, which is why [insights] returns nothing unless [revealed] is true.
 */
object WidgetSummary {
    /** Tallest value shown for a change, so a month that started from almost nothing does not print thousands of percent. */
    private const val MAX_PERCENT = 999

    /** This month so far against last month up to the same day. Null when last month had nothing to compare with. */
    fun delta(spent: Money, lastMonthSameDays: Money): WidgetDelta? {
        if (lastMonthSameDays.minorUnits <= 0) return null
        val diff = spent.minorUnits - lastMonthSameDays.minorUnits
        val last = lastMonthSameDays.minorUnits
        val pct = ((if (diff < 0) -diff else diff) * 100 + last / 2) / last
        if (pct == 0L) return WidgetDelta(DeltaDirection.SAME, "→", "same", "About the same as last month so far")
        val text = if (pct > MAX_PERCENT) "$MAX_PERCENT%+" else "$pct%"
        return if (diff < 0) WidgetDelta(DeltaDirection.LESS, "↓", text, "$text less than last month so far")
        else WidgetDelta(DeltaDirection.MORE, "↑", text, "$text more than last month so far")
    }

    /** The overall budget as a bar. Null without a limit. [spent] counts against it for the budget's own period. */
    fun budget(limit: Money?, spent: Money): WidgetBudget? {
        if (limit == null || limit.minorUnits <= 0) return null
        val used = spent.minorUnits.coerceAtLeast(0)
        val permille = if (used >= limit.minorUnits) 1000 else (used * 1000 / limit.minorUnits).toInt()
        val over = used > limit.minorUnits
        val text = if (over) "${Money(used - limit.minorUnits, limit.currency).compact()} over ${limit.compact()}" else "${Money(limit.minorUnits - used, limit.currency).compact()} left of ${limit.compact()}"
        return WidgetBudget(permille, over, text)
    }

    /**
     * The biggest categories of the month, subcategories counted under their parent, each with its share of everything spent. [parentOf] maps a subcategory id to its parent's;
     * [names] maps a top-level id to its name; spending with no category is "Uncategorised". At most [max], biggest first.
     */
    fun topCategories(spend: Map<String?, Money>, parentOf: Map<String, String?>, names: Map<String, String>, max: Int): List<WidgetCategoryBar> {
        if (max <= 0) return emptyList()
        val totals = LinkedHashMap<String?, Long>()
        for ((id, m) in spend) {
            if (m.minorUnits <= 0) continue
            val top = id?.let { parentOf[it] ?: it }
            totals[top] = (totals[top] ?: 0L) + m.minorUnits
        }
        val all = totals.values.sum()
        if (all <= 0) return emptyList()
        val currency = spend.values.first().currency
        return totals.entries.sortedByDescending { it.value }.take(max).map { (id, minor) ->
            WidgetCategoryBar(id?.let { names[it] } ?: "Uncategorised", Money(minor, currency).compact(), (minor * 1000 / all).toInt().coerceIn(0, 1000))
        }
    }

    /** The newest expenses and income, worded: a shop or note (else the category), the amount with its sign, and when. Transfers and the like are left out. At most [max]. */
    fun recent(rows: List<RecentInput>, nowMillis: Long, zone: TimeZone, max: Int): List<WidgetRecent> {
        if (max <= 0) return emptyList()
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
        return rows.asSequence().filter { it.kind == "EXPENSE" || it.kind == "INCOME" }.take(max).map { r ->
            val day = Instant.fromEpochMilliseconds(r.at).toLocalDateTime(zone).date
            val income = r.kind == "INCOME"
            WidgetRecent(
                title = r.title?.takeIf { it.isNotBlank() } ?: r.categoryName?.takeIf { it.isNotBlank() } ?: if (income) "Income" else "Expense",
                amountText = (if (income) "+" else "-") + Money(kotlin.math.abs(r.amount.minorUnits), r.amount.currency).formatted(),
                whenText = dayWord(day, today), income = income,
            )
        }.toList()
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    internal fun dayWord(day: LocalDate, today: LocalDate): String = when ((today.toEpochDays() - day.toEpochDays()).toLong()) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> "${day.day} ${MONTHS[day.month.ordinal]}"
    }

    /** Everything the big widgets add under the total, or [WidgetInsights.NONE] unless the total may be shown ([revealed]). */
    fun insights(revealed: Boolean, delta: WidgetDelta?, budget: WidgetBudget?, categories: List<WidgetCategoryBar>, recent: List<WidgetRecent>): WidgetInsights =
        if (revealed) WidgetInsights(delta, budget, categories, recent) else WidgetInsights.NONE

    // ---- layout: how much of all this fits ----

    /** Vertical room, in dp, that each part of the card takes (see the widget's drawing code, which uses the same numbers). */
    object Room {
        /** Every control is at least this many dp high and wide; the header and the rows of controls are exactly this high. */
        const val TARGET = 48
        /** The card's top and bottom inset: small, because the 48 dp controls carry their own margin around the 40 dp shapes you see. */
        const val PAD = 4; const val HEADER = TARGET; const val UNDO = TARGET + 6; const val CHIPS = TARGET + 6; const val HINT = 22; const val BUDGET = 30
        const val SECTION_TITLE = 20; const val CATEGORY_ROW = 22; const val RECENT_ROW = 24
        const val MIN_ROWS = 2; const val MAX_CATEGORIES = 4; const val MAX_RECENT = 4
        /** Narrower than this and the Add pill is only a round "+", so the figure has room. */
        const val WIDE_ADD_FROM = 300
    }

    /**
     * Decides what a widget of [widthDp] x [heightDp] draws. Tiers: under 180 dp wide is TINY below 100 dp high and SQUARE from there; 180 dp and wider is WIDE below 100 dp high and
     * CARD from there. A CARD gives the space under its header to the sections in this order, each only when it fits whole: undo line, chips, budget bar, top categories (2 to 3 rows, 4 when
     * there are no recent entries), recent entries (2 to 4 rows). Sections with nothing to show (see [have]) take no room. Figures-only sections need [WidgetHave.revealed].
     */
    fun plan(widthDp: Int, heightDp: Int, have: WidgetHave): WidgetPlan {
        val wideAdd = widthDp >= Room.WIDE_ADD_FROM
        if (widthDp < 180) {
            return if (heightDp < 100) WidgetPlan(WidgetTier.TINY, 0, false, false, false, 0, 0, false, false, false)
            else WidgetPlan(WidgetTier.SQUARE, 0, false, have.delta && have.revealed, false, 0, 0, false, false, false)
        }
        if (heightDp < 100) return WidgetPlan(WidgetTier.WIDE, minOf(if (widthDp >= 380) 2 else if (widthDp >= 300) 1 else 0, have.chips), false, false, false, 0, 0, false, wideAdd, true)
        var left = heightDp - 2 * Room.PAD - Room.HEADER
        fun take(need: Int): Boolean = if (left >= need) { left -= need; true } else false
        /** How many of [available] rows of a titled section fit in what is left, taking their room; none when fewer than two (or all, when there are fewer) would fit. */
        fun rows(have: Int, per: Int, max: Int): Int {
            if (have == 0) return 0
            val n = minOf(have, max, (left - Room.SECTION_TITLE) / per)
            if (n < minOf(have, Room.MIN_ROWS) || n <= 0) return 0
            left -= Room.SECTION_TITLE + n * per
            return n
        }
        val undo = have.undo && take(Room.UNDO)
        val chips = if (have.chips > 0 && take(Room.CHIPS)) minOf(have.chips, if (widthDp >= 300) 3 else 2) else 0
        val hint = have.chips == 0 && take(Room.HINT)
        val delta = have.revealed && have.delta && widthDp >= Room.WIDE_ADD_FROM // the comparison sits in the header's label row (it costs no height), which a narrow widget has no room for
        val budget = have.revealed && have.budget && take(Room.BUDGET)
        // with recent entries to show as well, the categories keep to three rows so that both fit
        val cats = if (have.revealed) rows(have.categories, Room.CATEGORY_ROW, if (have.recent > 0) 3 else Room.MAX_CATEGORIES) else 0
        val recent = if (have.revealed) rows(have.recent, Room.RECENT_ROW, Room.MAX_RECENT) else 0
        val note = !have.revealed && take(Room.HINT) && heightDp >= 170
        return WidgetPlan(WidgetTier.CARD, chips, undo, delta, budget, cats, recent, hint, wideAdd, false, note)
    }
}
