package app.expenses.core.domain

/** One small, one-time hint shown at the top of a screen the first time it is opened. [id] is also what is remembered as seen. */
data class Tip(val id: String, val screen: String, val title: String, val text: String)

/**
 * The one-time tips. Each is shown once, at the top of its screen, and never again once it was dismissed or its screen was left. The set of ids already seen is kept as one comma-separated line.
 * Texts are short, calm and specific; none carries an amount.
 */
object Tips {
    val ALL: List<Tip> = listOf(
        Tip("reports", "reports", "Reading the bars", "Touch and slide across the bars to read each one."),
        Tip("budget", "budget", "Budget settings", "The pencil sets the period and carry over."),
        Tip("search", "search", "Narrowing a search", "Filters narrow by category, account, dates or amount."),
        Tip("categories", "categories", "Moving categories", "Hold the handle to drag a category to a new place."),
        Tip("calendar", "calendar", "Reading the calendar", "Tap a day to see what was spent on it."),
        Tip("goals", "goals", "Saving towards a goal", "Add money to a goal by hand, or let it follow a savings account."),
        Tip("home_layout", "home_layout", "Arranging home", "Hold the handle to drag a block, or use the arrows."),
    )

    /** The settings key holding the ids already seen, as one comma-separated line. */
    const val SETTING = "tips_seen"

    val IDS: Set<String> = ALL.map { it.id }.toSet()

    fun forScreen(screen: String): Tip? = ALL.firstOrNull { it.screen == screen }

    /** The tip of [screen] if it has not been seen, else null. */
    fun unseen(seen: Set<String>, screen: String): Tip? = forScreen(screen)?.takeIf { it.id !in seen }

    fun encode(seen: Set<String>): String = seen.filter { it.isNotEmpty() }.sorted().joinToString(",")

    /** The set kept by [encode]; nothing, blanks or stray commas give the ids that are there. */
    fun decode(raw: String?): Set<String> = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
