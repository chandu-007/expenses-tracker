package app.expenses.core.storage

import app.expenses.core.domain.Category
import app.expenses.core.domain.CategoryRules
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random

/**
 * The category tree. Every rule lives in [CategoryRules]; this class applies the checks and the change in one transaction,
 * so a refused change leaves nothing behind. Changes are written to the sync log field by field, like entries.
 *
 * Merging re-points existing entries, splits, spend lines, income and quick entries at the target category in one step. It is
 * logged as a single `merged_into` operation on the removed category rather than as one operation per entry, so a device
 * replaying the log must apply that re-mapping itself (sync is not built yet; this is the contract it will need).
 *
 * The removed category stays as a tombstone pointing at its target ([resolveCategory]), and [LedgerStore] and [TemplateStore] redirect
 * any entry or quick entry saved with the old id, so a late edit can never leave something in a removed category.
 *
 * Not thread-safe: call through the process-wide write lock.
 */
class CategoryStore(
    private val db: ExpensesDb,
    private val clock: HlcClock,
    private val nowMillis: () -> Long,
) {
    private val q get() = db.schemaQueries

    init {
        seedDefaults()
    }

    /** All live categories in display order: top level by position, each followed by its subcategories. */
    fun list(): List<Category> = CategoryRules.inDisplayOrder(q.liveCategories().executeAsList().map(::toCategory))

    /** Number of things referring to the category (splits, income, quick entries), including deleted entries. */
    fun usage(id: String): Long = q.categoryUsage(id).executeAsOne()

    fun create(name: String, parentId: String? = null, colorArgb: Long? = null): Category = db.transactionWithResult {
        val all = list()
        CategoryRules.checkName(all, name, parentId)?.let { throw IllegalArgumentException(it) }
        CategoryRules.checkParent(all, id = "", newParentId = parentId)?.let { throw IllegalArgumentException(it) }
        val id = newId()
        val clean = CategoryRules.normalizeName(name)
        val position = (q.maxCategoryPosition(parentId).executeAsOne() + 1).toInt()
        q.insertCategory(id, clean, parentId, colorArgb, position.toLong())
        log(id, null, mapOf("name" to clean, "parent_id" to parentId, "color" to colorArgb?.toString(), "position" to position.toString(), "hidden" to "0"))
        Category(id, clean, parentId, colorArgb, position, hidden = false)
    }

    fun rename(id: String, name: String) = change(id) { all, c ->
        CategoryRules.checkName(all, name, c.parentId, excludeId = id)?.let { throw IllegalArgumentException(it) }
        c.copy(name = CategoryRules.normalizeName(name))
    }

    fun setColor(id: String, colorArgb: Long?) = change(id) { _, c -> c.copy(colorArgb = colorArgb) }

    fun setHidden(id: String, hidden: Boolean) = change(id) { _, c -> c.copy(hidden = hidden) }

    /** Moves a category under a top-level parent, or to the top level when [parentId] is null. It goes to the end of its new level. */
    fun setParent(id: String, parentId: String?) = change(id) { all, c ->
        if (c.parentId == parentId) return@change c
        CategoryRules.checkParent(all, id, parentId)?.let { throw IllegalArgumentException(it) }
        CategoryRules.checkName(all, c.name, parentId, excludeId = id)?.let { throw IllegalArgumentException(it) }
        c.copy(parentId = parentId, position = (q.maxCategoryPosition(parentId).executeAsOne() + 1).toInt())
    }

    /** Swaps places with the neighbour above ([direction] -1) or below (+1) at the same level. Does nothing at the ends. */
    fun move(id: String, direction: Int) {
        db.transaction {
            val all = list()
            val other = CategoryRules.neighbour(all, id, direction) ?: return@transaction
            val me = all.first { it.id == id }
            // Positions can tie (they are only compared within a level), so renumber the whole level to make the swap well defined.
            val level = all.filter { it.parentId == me.parentId }.map { it.id }.toMutableList()
            val a = level.indexOf(id); val b = level.indexOf(other.id)
            level[a] = other.id; level[b] = id
            level.forEachIndexed { i, cid ->
                val c = all.first { it.id == cid }
                if (c.position != i) write(c, c.copy(position = i))
            }
        }
    }

    /**
     * Moves everything in [fromId] into [intoId] and removes [fromId]. All the re-mapping happens in one transaction, so it either
     * fully happens or not at all. The category totals are unchanged except that the two categories become one.
     */
    fun merge(fromId: String, intoId: String) {
        db.transaction {
            val all = list()
            CategoryRules.checkMerge(all, fromId, intoId)?.let { throw IllegalArgumentException(it) }
            q.remapSplits(intoId, fromId)
            q.remapSpendLines(intoId, fromId)
            q.remapIncome(intoId, fromId)
            q.remapTemplates(intoId, fromId)
            q.markMerged(intoId, fromId)
            q.insertOp(clock.now(nowMillis()).encode(), "category", fromId, "merged_into", intoId)
            q.insertOp(clock.now(nowMillis()).encode(), "category", fromId, "deleted", "1")
        }
    }

    /** Deletes a category nothing refers to. Otherwise merge it into another one. */
    fun delete(id: String) {
        db.transaction {
            CategoryRules.checkDelete(list(), id, usage(id))?.let { throw IllegalArgumentException(it) }
            q.softDeleteCategory(id)
            q.insertOp(clock.now(nowMillis()).encode(), "category", id, "deleted", "1")
        }
    }

    // ---- internals ----

    private fun change(id: String, transform: (List<Category>, Category) -> Category) {
        db.transaction {
            val all = list()
            val before = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("That category does not exist")
            val after = transform(all, before)
            if (after != before) write(before, after)
        }
    }

    private fun write(before: Category, after: Category) {
        q.updateCategory(after.name, after.parentId, after.colorArgb, after.position.toLong(), if (after.hidden) 1 else 0, before.id)
        log(before.id, fieldsOf(before), fieldsOf(after))
    }

    private fun fieldsOf(c: Category): Map<String, String?> = linkedMapOf(
        "name" to c.name, "parent_id" to c.parentId, "color" to c.colorArgb?.toString(), "position" to c.position.toString(), "hidden" to if (c.hidden) "1" else "0",
    )

    private fun log(id: String, before: Map<String, String?>?, after: Map<String, String?>) {
        for ((field, value) in after) {
            val changed = if (before == null) value != null else before[field] != value
            if (changed) q.insertOp(clock.now(nowMillis()).encode(), "category", id, field, value)
        }
    }

    private fun toCategory(r: app.expenses.core.storage.db.Category) =
        Category(r.id, r.name, r.parent_id, r.color, r.position.toInt(), r.hidden == 1L)

    private fun newId(): String = Random.nextBytes(8).joinToString("") { "%02x".format(it) }

    /**
     * The first time, creates the eight default categories. Their ids equal their names, so entries recorded before categories
     * could be managed keep pointing at the right one. Seeding is not written to the sync log: every device creates the same rows.
     */
    private fun seedDefaults() {
        if (q.categoryCount().executeAsOne() > 0) return
        db.transaction {
            DEFAULTS.forEachIndexed { i, name -> q.insertCategory(name, name, null, null, i.toLong()) }
        }
    }

    companion object {
        val DEFAULTS = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")
    }
}
