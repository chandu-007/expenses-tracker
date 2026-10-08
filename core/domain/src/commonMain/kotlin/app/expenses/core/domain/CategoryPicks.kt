package app.expenses.core.domain

/** Which categories the entry form shows as chips, and how many are left for the "More" list. */
data class CategoryPicks(val shown: List<String>, val more: Int)

object CategoryChoices {
    /** Chips shown at once. A form with 500 chips took 45 ms longer to open than one with 8, and nobody scans 500 chips. */
    const val CHIPS = 24

    /**
     * The first [limit] of [visible] (ids in the order the person arranged them), plus the chosen one when it lies beyond them, so the
     * selection is never hidden. [CategoryPicks.more] counts the visible categories not shown as chips.
     */
    fun pick(visible: List<String>, selected: String?, limit: Int = CHIPS): CategoryPicks {
        val first = visible.take(limit)
        val shown = if (selected != null && selected in visible && selected !in first) first + selected else first
        return CategoryPicks(shown, visible.size - shown.size)
    }

    /** The categories whose names contain every word typed, ignoring case; all of them when nothing is typed. */
    fun search(names: List<Pair<String, String>>, typed: String): List<String> {
        val words = typed.lowercase().split(' ').filter { it.isNotEmpty() }
        return names.filter { (_, name) -> words.all { name.lowercase().contains(it) } }.map { it.first }
    }
}
