package app.expenses.core.domain.ledger

/**
 * One signed slice of spending in one category (null = uncategorized), in minor units.
 * [mine] is what the user consumed; [cashOut] is what left the user's accounts. They differ for shared
 * expenses (cash out is the full amount) and friend-paid items (no cash out).
 */
data class SpendLine(val category: CategoryId?, val mine: Long, val cashOut: Long)

/**
 * The single definition of how entries turn into spending. [Ledger] sums these lines in memory and the
 * storage layer persists them for SQL reports, so the two can never disagree.
 * Entries that are not spending (income, transfers, settlements) have no lines.
 */
object SpendLines {
    private fun keysOf(splits: List<CategorySplit>): List<CategoryId?> =
        if (splits.isEmpty()) listOf(null) else splits.map { it.category }

    private fun weightsOf(splits: List<CategorySplit>): List<Long> =
        if (splits.isEmpty()) listOf(1L) else splits.map { it.amount.minorUnits }

    fun of(expense: Expense): List<SpendLine> {
        val keys = keysOf(expense.splits)
        val weights = weightsOf(expense.splits)
        val mine = allocate(expense.myShare.minorUnits, weights)
        val cash = allocate(expense.amount.minorUnits, weights)
        return keys.indices.map { SpendLine(keys[it], mine[it], cash[it]) }
    }

    fun of(friendPaid: FriendPaid): List<SpendLine> {
        val keys = keysOf(friendPaid.splits)
        val mine = allocate(friendPaid.myShare.minorUnits, weightsOf(friendPaid.splits))
        return keys.indices.map { SpendLine(keys[it], mine[it], 0L) }
    }

    /**
     * Negative lines for every refund of [original]. Refunds are applied in (time, id) order, each one spread
     * over the amounts still unrefunded in every category, so no category can be refunded for more than it was
     * charged and the result does not depend on the order refunds were recorded. Lines of an earlier refund
     * never change when a later one is added, but inserting a back-dated refund can change later ones, so
     * callers must recompute all refunds of an expense together.
     */
    fun ofRefunds(original: Expense, refunds: List<Refund>): Map<EntryId, List<SpendLine>> {
        val keys = keysOf(original.splits)
        val remaining = if (original.splits.isEmpty()) {
            longArrayOf(original.amount.minorUnits)
        } else {
            LongArray(original.splits.size) { original.splits[it].amount.minorUnits }
        }
        val result = LinkedHashMap<EntryId, List<SpendLine>>()
        for (refund in refunds.sortedWith(compareBy<Refund> { it.occurredAt }.thenBy { it.id.value })) {
            val open = remaining.indices.filter { remaining[it] > 0 }
            val parts = allocate(refund.amount.minorUnits, open.map { remaining[it] })
            val lines = ArrayList<SpendLine>()
            for ((slot, i) in open.withIndex()) {
                remaining[i] -= parts[slot]
                lines.add(SpendLine(keys[i], -parts[slot], -parts[slot]))
            }
            result[refund.id] = lines
        }
        return result
    }
}
