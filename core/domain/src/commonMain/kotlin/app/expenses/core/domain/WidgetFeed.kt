package app.expenses.core.domain

import kotlinx.datetime.LocalDate

/**
 * Where the overall limit stands, as the widgets draw it (UI S8, backlog U-23): the [word] beside the bar ("A little ahead"), the [level] that colours it, and [todayPermille], how far through the
 * period today is (0 to 1000), the marker on the bar. The bar's fill itself is [WidgetBudget.permille]. Every part of it follows from the figures, so it exists only where the figures may be shown.
 */
data class WidgetPace(val level: PaceLevel, val word: String, val todayPermille: Int)

/**
 * The "Allow widgets" switch (UI S8, GLOBAL I31). On (the default): the widgets, the iPhone's Lock Screen widgets and Android's launcher shortcuts work as they always did. Off: none of them shows
 * anything from the ledger (no figure, no quick entry, no name), the eye is gone, a quick-entry tap that was already on the screen is refused, and only Add (which opens the app, behind its lock) stays.
 */
object WidgetAccess {
    const val SETTING = "widgets_allowed"

    /** Off only when the person turned it off: a missing setting (every install before UI S8) is on, so an update changes nothing on a placed widget. */
    fun allowed(value: String?): Boolean = value != "0"

    fun value(allowed: Boolean): String = if (allowed) "1" else "0"
}

/** One quick entry as a launcher shortcut: [short] for the icon's label (10 characters at most, as Android asks), [long] for the long-press list (25 at most). */
data class ShortcutLabel(val templateId: String, val short: String, val long: String)

/** Android's dynamic launcher shortcuts for the saved quick entries (UI S8, backlog N-20): long-press the app's icon, tap "Add Chai ₹20", and it is recorded. */
object QuickShortcuts {
    /** Launchers show four or five shortcuts in all; the static "Add expense" is one of them. */
    const val MAX = 3
    const val SHORT_MAX = 10
    const val LONG_MAX = 25

    /**
     * The shortcuts to offer: the first [MAX] quick entries that have an amount, in the order the app lists them. None while widgets are not allowed. The amount is left out while Hide amounts or
     * the App lock is on (anyone holding the unlocked phone can long-press the icon). An amount is never cut: when the words do not fit, the name is shortened instead.
     */
    fun labels(templates: List<Template>, allowed: Boolean, hide: Boolean, locked: Boolean): List<ShortcutLabel> {
        if (!allowed) return emptyList()
        return templates.filter { it.amount != null }.take(MAX).map { t ->
            val name = t.name.trim()
            val amount = if (hide || locked) null else t.amount!!.formatted().removeSuffix(".00")
            ShortcutLabel(t.id, cut(name, SHORT_MAX), long(name, amount))
        }
    }

    private fun cut(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"

    private fun long(name: String, amount: String?): String {
        if (amount == null) return if ("Add $name".length <= LONG_MAX) "Add $name" else cut(name, LONG_MAX)
        for (s in listOf("Add $name $amount", "$name $amount")) if (s.length <= LONG_MAX) return s
        val room = LONG_MAX - amount.length - 1
        return if (room >= 2) cut(name, room) + " " + amount else cut(name, LONG_MAX)
    }
}

/**
 * Everything the widgets of the iPhone are given (UI S8): written by the app into the App Group's preferences, because a widget cannot open the database. Strings are empty where there is nothing to
 * show. Privacy, decided here and nowhere else: while widgets are not [allowed] nothing at all is given (no figure, no quick entry); while Hide amounts or the App lock is on ([canToggle] false)
 * every "real" field holds the masked text and the pace fields are empty (a bar's length is a figure too); otherwise the real figures are given and the widget's eye decides whether they are drawn.
 */
data class WidgetFeed(
    val allowed: Boolean,
    val canToggle: Boolean,
    /** Spent today, "₹1,240.00". */
    val todaySpent: String,
    val todaySpentMasked: String,
    /** "₹1,565.00 a day keeps you on pace", or empty without a limit with something to spare. */
    val todayPerDay: String,
    val todayPerDayMasked: String,
    /** Spent this calendar month. */
    val monthSpent: String,
    val monthSpentMasked: String,
    /** "₹28K left of ₹60K" (or "₹1.2K over ₹60K"), empty without a limit. */
    val budgetLine: String,
    /** The Lock Screen's round gauge: "₹28K" left, or "Over" when the limit is passed; empty without a limit. */
    val budgetLeftShort: String,
    /** "On pace", "A little ahead", ...; empty without a limit. */
    val paceWord: String,
    /** The [PaceLevel]'s name, for the colour of the word; empty without a limit. */
    val paceLevel: String,
    /** Share of the limit spent, 0 to 1000; -1 without a limit. */
    val paceUsedPermille: Int,
    /** How far through the period today is, 0 to 1000; -1 without a limit. */
    val paceTodayPermille: Int,
    /** The exact words a screen reader says for the pace bar; empty without a limit. */
    val paceSpoken: String,
    /** The quick entries, "id<TAB>name<TAB>amount", at most six; the amount masked while Hide amounts or the App lock is on. */
    val buttons: List<String>,
)

object WidgetFeeds {
    const val MAX_BUTTONS = 6

    /** "₹1,565.00 a day keeps you on pace": what spending at most this much each day until the end of the period keeps. Null without a positive amount. */
    fun perDayLine(perDay: Money?): String? = perDay?.takeIf { it.minorUnits > 0 }?.let { "${it.formatted()} a day keeps you on pace" }

    /**
     * Where [spent] (in the limit's own period) stands against [limit], [today] being in [period]. [upcoming] is what repeating bills still to come will take (see [PaceMath.level]). Null without a limit.
     */
    fun pace(spent: Money, limit: Money?, period: DatePeriod, today: LocalDate, upcoming: Money?): WidgetPace? {
        if (limit == null || limit.minorUnits <= 0) return null
        val daysIn = PaceMath.daysIn(period, today)
        val level = PaceMath.level(spent.minorUnits, limit.minorUnits, PaceMath.elapsed(period, today), daysIn, period.days, upcoming?.minorUnits ?: 0L)
        val over = spent.minorUnits - limit.minorUnits
        val word = PaceMath.word(level, if (over > 0) Money(over, limit.currency).formatted().removeSuffix(".00") else "")
        return WidgetPace(level, word, (daysIn * 1000L / period.days).toInt().coerceIn(0, 1000))
    }

    /** What a screen reader says for a pace bar: the exact amounts (never the short "₹28K"), the word, and how far through the period today is. */
    fun spoken(spent: Money, limit: Money, pace: WidgetPace?): String {
        val left = limit.minorUnits - spent.minorUnits.coerceAtLeast(0)
        val where = if (left >= 0) "${Money(left, limit.currency).formatted()} left of ${limit.formatted()}" else "${Money(-left, limit.currency).formatted()} over ${limit.formatted()}"
        return if (pace == null) where else "$where. ${pace.word}. Today is ${pace.todayPermille / 10} percent of the way through the period."
    }

    /**
     * The feed. [spentToday] and [month] are spending today and this calendar month; [perDay] what the budget leaves for each remaining day; [limit] and [budgetSpent] the overall limit and what
     * counts against it in its own period; [pace] from [pace]; [templates] the saved quick entries in the app's order.
     */
    fun of(
        allowed: Boolean, hide: Boolean, locked: Boolean,
        spentToday: Money, month: Money, perDay: Money?, limit: Money?, budgetSpent: Money, pace: WidgetPace?, templates: List<Template>,
    ): WidgetFeed {
        if (!allowed) return WidgetFeed(false, false, "", "", "", "", "", "", "", "", "", "", -1, -1, "", emptyList())
        val canToggle = !hide && !locked
        fun real(text: String) = if (canToggle) text else AmountMask.mask(text)
        val perDayText = perDayLine(perDay)
        val budget = WidgetSummary.budget(limit, budgetSpent, pace).takeIf { canToggle }
        val buttons = templates.filter { it.amount != null }.take(MAX_BUTTONS).map { t ->
            val amount = t.amount!!.formatted()
            listOf(t.id, t.name, if (canToggle) amount else AmountMask.mask(amount)).joinToString("\t")
        }
        val leftShort = if (budget == null || limit == null) "" else if (budget.over) "Over" else Money(limit.minorUnits - budgetSpent.minorUnits.coerceAtLeast(0), limit.currency).compact()
        return WidgetFeed(
            allowed = true, canToggle = canToggle,
            todaySpent = real(spentToday.formatted()), todaySpentMasked = AmountMask.mask(spentToday.formatted()),
            todayPerDay = perDayText?.let(::real).orEmpty(), todayPerDayMasked = perDayText?.let(AmountMask::mask).orEmpty(),
            monthSpent = real(month.formatted()), monthSpentMasked = AmountMask.mask(month.formatted()),
            budgetLine = budget?.text.orEmpty(), budgetLeftShort = leftShort,
            paceWord = budget?.pace?.word.orEmpty(), paceLevel = budget?.pace?.level?.name.orEmpty(),
            paceUsedPermille = budget?.permille ?: -1, paceTodayPermille = budget?.pace?.todayPermille ?: -1, paceSpoken = budget?.spoken.orEmpty(),
            buttons = buttons,
        )
    }
}
