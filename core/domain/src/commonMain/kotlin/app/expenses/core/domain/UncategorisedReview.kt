package app.expenses.core.domain

/**
 * The calm card on Home that says how many expenses have no category and opens a one-at-a-time review. Uncategorised is the safe default for a captured payment the app is not sure of; this is the
 * way out. It shows only when there are at least [MIN_COUNT] of them and the person has not said "Not now" in the last [SNOOZE_DAYS] days. The count comes from one capped, indexed query
 * (`uncategorisedExpenseCount`) worked out with Home's other slow figures a moment after the last write, never on the save path or when Home opens.
 */
object UncategorisedNudge {
    const val MIN_COUNT = 3
    const val SNOOZE_DAYS = 7
    /** The count stops here: beyond it the card says "500+" (the query reads at most this many rows). */
    const val CAP = 500

    private const val DAY_MS = 86_400_000L

    /** Whether the card shows: [count] expenses (capped) and the time of the last "Not now" ([snoozedAt], null when never) before [now]. A snooze in the future (a clock set back) counts as over. */
    fun shouldShow(count: Int, snoozedAt: Long?, now: Long): Boolean =
        count >= MIN_COUNT && (snoozedAt == null || snoozedAt > now || now - snoozedAt >= SNOOZE_DAYS * DAY_MS)

    /** "12 expenses have no category" ("500+ expenses have no category" at the cap). */
    fun text(count: Int): String = (if (count >= CAP) "$CAP+" else count.toString()) + (if (count == 1) " expense has" else " expenses have") + " no category"
}

/** One uncategorised expense as the review shows it: who ([label]), how much, when, and where it came from ([source], "sms" for a captured one). */
data class ReviewItem(val id: String, val label: String, val amountMinor: Long, val occurredAt: Long, val source: String?)

/**
 * The state of one pass through the uncategorised expenses, one at a time: what is left, what was filed, what was skipped. Immutable; every step returns the next state. A skipped item stays
 * as it was (still uncategorised) and is not offered again in this pass. Only the last choice can be taken back ([undone]).
 */
data class ReviewSession(val queue: List<ReviewItem>, val index: Int = 0, val filed: Int = 0, val skipped: Int = 0) {
    val current: ReviewItem? get() = queue.getOrNull(index)
    /** Items not yet answered, the current one included. */
    val left: Int get() = (queue.size - index).coerceAtLeast(0)
    val done: Boolean get() = index >= queue.size

    fun withFiled(): ReviewSession = if (done) this else copy(index = index + 1, filed = filed + 1)
    fun withSkipped(): ReviewSession = if (done) this else copy(index = index + 1, skipped = skipped + 1)

    /** The state before the last choice was made (the item comes back as the current one). */
    fun undone(): ReviewSession = if (index == 0 || filed == 0) this else copy(index = index - 1, filed = filed - 1)

    companion object {
        /**
         * The order to go through [items] in: payees with the most waiting first, so that one answer (which the app learns, and then suggests for the rest) helps the most, the entries of one
         * payee together and the newest first within it; payees with equally many go newest first. [keyOf] gives the key of a label (see [PayeeKey]); a label with no key stands alone.
         */
        fun order(items: List<ReviewItem>, keyOf: (String) -> String?): List<ReviewItem> {
            val groups = LinkedHashMap<String, MutableList<ReviewItem>>()
            items.forEachIndexed { i, it -> groups.getOrPut(keyOf(it.label) ?: "#$i") { ArrayList(2) } += it }
            return groups.values.map { g -> g.sortedByDescending { it.occurredAt } }
                .sortedWith(compareByDescending<List<ReviewItem>> { it.size }.thenByDescending { it.first().occurredAt })
                .flatten()
        }
    }
}
