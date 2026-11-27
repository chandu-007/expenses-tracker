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

    /** Category tiles shown in the entry form (Slice 135: the board's 4 by 2 grid, seven tiles and "All N"): the most used first, then the person's own order. */
    const val TILES = 7

    /** [visible] is (id, how many entries use it) in the order the person arranged them. The first [limit] by use (ties keep that order), plus the chosen one when it lies beyond them. */
    fun tiles(visible: List<Pair<String, Long>>, selected: String?, limit: Int = TILES): CategoryPicks {
        val ranked = visible.withIndex().sortedWith(compareByDescending<IndexedValue<Pair<String, Long>>> { it.value.second }.thenBy { it.index }).map { it.value.first }
        // the grid is 4 by 2 with "All N" in the last place: seven tiles, the chosen one taking the seventh when it lies beyond them (never hidden, never an ninth tile)
        val shown = if (selected != null && selected in ranked && selected !in ranked.take(limit)) ranked.take(limit - 1) + selected else ranked.take(limit)
        return CategoryPicks(shown, ranked.size - shown.size)
    }

    /** The categories whose names contain every word typed, ignoring case; all of them when nothing is typed. */
    fun search(names: List<Pair<String, String>>, typed: String): List<String> {
        val words = typed.lowercase().split(' ').filter { it.isNotEmpty() }
        return names.filter { (_, name) -> words.all { name.lowercase().contains(it) } }.map { it.first }
    }
}
