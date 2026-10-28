package app.expenses.core.domain

/** A category the person already has, as the importer needs to know it. */
data class CategoryRef(val id: String, val name: String, val parentId: String?, val forExpense: Boolean, val forIncome: Boolean)

/**
 * Turns the category names of a file from another app into the person's own categories. A name is matched to a category they already have, ignoring case and
 * spaces at the ends; nothing is created by this. What is left over is offered as new categories, and only the person's tick creates them.
 */
object ImportCategories {
    private fun key(s: String) = s.trim().lowercase()

    /** A category from a file that the person has none of: [name] as the file wrote it, and whether it was on money in ([income]). */
    data class NewCategory(val name: String, val income: Boolean)

    /** The name a file's category stands for: the category, or when it has none the sub-category. Null when the row has neither. */
    fun nameOf(category: String?, subcategory: String?): String? = category?.trim()?.takeIf { it.isNotEmpty() } ?: subcategory?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The id of the existing category the row means, or null. A sub-category is looked for first, under a category of the file's category name (so "Food >
     * Groceries" finds Groceries under Food), then among the person's categories under any name; then the file's category name itself. A category that is not for
     * the row's kind (an income category for spending) is not offered.
     */
    fun match(category: String?, subcategory: String?, income: Boolean, existing: List<CategoryRef>): String? {
        val usable = existing.filter { if (income) it.forIncome else it.forExpense }
        val sub = subcategory?.trim()?.takeIf { it.isNotEmpty() }
        val top = category?.trim()?.takeIf { it.isNotEmpty() }
        if (sub != null) {
            val parents = if (top == null) emptySet() else usable.filter { key(it.name) == key(top) }.map { it.id }.toSet()
            usable.firstOrNull { key(it.name) == key(sub) && it.parentId != null && it.parentId in parents }?.let { return it.id }
            usable.firstOrNull { key(it.name) == key(sub) }?.let { return it.id }
        }
        if (top != null) {
            usable.firstOrNull { key(it.name) == key(top) && it.parentId == null }?.let { return it.id }
            usable.firstOrNull { key(it.name) == key(top) }?.let { return it.id }
        }
        return null
    }

    /** The different categories [txns] have that [existing] has no match for, in the order they appear. */
    fun unmatched(txns: List<StatementTxn>, existing: List<CategoryRef>): List<NewCategory> {
        val seen = LinkedHashMap<Pair<String, Boolean>, NewCategory>()
        for (t in txns) {
            val name = nameOf(t.category, t.subcategory) ?: continue
            if (match(t.category, t.subcategory, t.flow == Flow.INCOME, existing) != null) continue
            seen.getOrPut(key(name) to (t.flow == Flow.INCOME)) { NewCategory(name.trim(), t.flow == Flow.INCOME) }
        }
        return seen.values.toList()
    }
}
