package app.expenses.core.storage

import app.expenses.core.storage.db.ExpensesDb

private const val MAX_MERGE_CHAIN = 32

/**
 * The category an id currently stands for. A category that was merged into another resolves to that one, following the chain
 * if the target was itself merged later. An id that was never merged (including unknown ids and plainly deleted categories)
 * resolves to itself. A chain longer than [MAX_MERGE_CHAIN] can only come from corrupt data and is refused.
 */
fun ExpensesDb.resolveCategory(id: String): String {
    var current = id
    repeat(MAX_MERGE_CHAIN) {
        current = schemaQueries.mergedInto(current).executeAsOneOrNull() ?: return current
    }
    throw IllegalStateException("Category merge chain from $id does not end; the data is corrupt")
}
