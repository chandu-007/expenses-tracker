package app.expenses.core.domain

/**
 * A spending category. [id] never changes, so renaming a category does not touch any entry. [parentId] is null for a
 * top-level category; categories nest one level only (a category, its subcategories). [colorArgb] is optional.
 * [hidden] categories are not offered when adding an expense but keep all their existing entries.
 */
data class Category(
    val id: String,
    val name: String,
    val parentId: String?,
    val colorArgb: Long?,
    val position: Int,
    val hidden: Boolean,
)

/** The rules for changing the category tree. Each check returns an error message for the user, or null if it is fine. */
object CategoryRules {
    const val MAX_NAME_LENGTH = 40

    /** Trims and collapses runs of spaces, so "Eating   out " and "Eating out" are the same name. */
    fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /**
     * A name must be non-empty, at most [MAX_NAME_LENGTH] characters, and unique (ignoring case) among its siblings:
     * top-level names among top-level categories, subcategory names within the same parent. The same name may appear under
     * different parents ("Other" under both Food and Travel). [excludeId] is the category being renamed or moved.
     */
    fun checkName(all: List<Category>, rawName: String, parentId: String?, excludeId: String? = null): String? {
        val name = normalizeName(rawName)
        if (name.isEmpty()) return "Give the category a name"
        if (name.length > MAX_NAME_LENGTH) return "Use at most $MAX_NAME_LENGTH characters"
        val clash = all.any { it.id != excludeId && it.parentId == parentId && it.name.equals(name, ignoreCase = true) }
        return if (clash) "There is already a category called \"$name\" here" else null
    }

    /** A parent must exist, be top-level, and not be the category itself; and a category with subcategories cannot become one. */
    fun checkParent(all: List<Category>, id: String, newParentId: String?): String? {
        if (newParentId == null) return null
        if (newParentId == id) return "A category cannot be inside itself"
        val parent = all.firstOrNull { it.id == newParentId } ?: return "That parent category does not exist"
        if (parent.parentId != null) return "Subcategories can only go one level deep"
        if (all.any { it.parentId == id }) return "Move or merge this category's subcategories first"
        return null
    }

    /** Merging moves everything from one category into another and removes the first. */
    fun checkMerge(all: List<Category>, fromId: String, intoId: String): String? {
        if (fromId == intoId) return "Choose a different category to merge into"
        if (all.none { it.id == fromId } || all.none { it.id == intoId }) return "That category does not exist"
        if (all.any { it.parentId == fromId }) return "Move or merge this category's subcategories first"
        return null
    }

    /** A category can be deleted only while nothing refers to it and it has no subcategories. */
    fun checkDelete(all: List<Category>, id: String, usage: Long): String? = when {
        all.none { it.id == id } -> "That category does not exist"
        all.any { it.parentId == id } -> "Move or merge this category's subcategories first"
        usage > 0 -> "This category is used by $usage ${if (usage == 1L) "item" else "items"}. Merge it into another category instead"
        else -> null
    }

    private val levelOrder = compareBy<Category>({ it.position }, { it.name.lowercase() }, { it.id })

    /** Tree order for display: each top-level category followed by its subcategories, each level by position then name. */
    fun inDisplayOrder(all: List<Category>): List<Category> {
        val children = all.filter { it.parentId != null }.groupBy { it.parentId }
        val topIds = all.filter { it.parentId == null }.map { it.id }.toSet()
        val ordered = all.filter { it.parentId == null }.sortedWith(levelOrder).flatMap { top -> listOf(top) + children[top.id].orEmpty().sortedWith(levelOrder) }
        // A subcategory whose parent is missing cannot be placed under it; keep it visible at the end rather than lose it.
        val orphans = all.filter { it.parentId != null && it.parentId !in topIds }.sortedWith(levelOrder)
        return ordered + orphans
    }

    /** The id that follows [id] when swapping places with a neighbour at the same level, or null at the end of the list. */
    fun neighbour(all: List<Category>, id: String, direction: Int): Category? {
        require(direction == -1 || direction == 1) { "direction must be -1 or 1" }
        val me = all.firstOrNull { it.id == id } ?: return null
        val siblings = all.filter { it.parentId == me.parentId }.sortedWith(levelOrder)
        val at = siblings.indexOfFirst { it.id == id }
        return siblings.getOrNull(at + direction)
    }
}
