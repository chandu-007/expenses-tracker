package app.expenses.core.storage

import app.expenses.core.domain.Category
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.CategoryRules
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
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

    /** A subcategory always has its parent's kind; a top-level category has the [kind] asked for. */
    fun create(name: String, parentId: String? = null, colorArgb: Long? = null, kind: CategoryKind = CategoryKind.EXPENSE): Category = db.transactionWithResult {
        val all = list()
        val kind = parentId?.let { p -> all.firstOrNull { it.id == p }?.kind } ?: kind
        CategoryRules.checkName(all, name, parentId)?.let { throw IllegalArgumentException(it) }
        CategoryRules.checkParent(all, id = "", newParentId = parentId)?.let { throw IllegalArgumentException(it) }
        val id = newId()
        val clean = CategoryRules.normalizeName(name)
        val position = (q.maxCategoryPosition(parentId).executeAsOne() + 1).toInt()
        q.insertCategory(id, clean, parentId, colorArgb, position.toLong(), kind.name)
        log(id, null, mapOf("name" to clean, "parent_id" to parentId, "color" to colorArgb?.toString(), "position" to position.toString(), "hidden" to "0", "kind" to kind.name))
        Category(id, clean, parentId, colorArgb, position, hidden = false, kind = kind)
    }

    fun rename(id: String, name: String) = change(id) { all, c ->
        CategoryRules.checkName(all, name, c.parentId, excludeId = id)?.let { throw IllegalArgumentException(it) }
        c.copy(name = CategoryRules.normalizeName(name))
    }

    fun setColor(id: String, colorArgb: Long?) = change(id) { _, c -> c.copy(colorArgb = colorArgb) }

    fun setHidden(id: String, hidden: Boolean) = change(id) { _, c -> c.copy(hidden = hidden) }

    /**
     * Changes what a category is offered for, and its subcategories with it. Refused while something of the kind being dropped still uses it
     * (an expense category with expenses cannot become an income category); the person merges or moves those entries first.
     */
    fun setKind(id: String, kind: CategoryKind) {
        db.transaction {
            val all = list()
            val me = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("That category does not exist")
            if (me.parentId != null) throw IllegalArgumentException("A subcategory follows its parent: change the kind of ${all.first { it.id == me.parentId }.name}")
            for (c in listOf(me) + all.filter { it.parentId == id }) {
                if (!kind.forExpense && q.categoryExpenseUse(c.id).executeAsOne() > 0) throw IllegalArgumentException("${c.name} is used by expenses, so it can't be only for income")
                if (!kind.forIncome && q.categoryIncomeUse(c.id).executeAsOne() > 0) throw IllegalArgumentException("${c.name} is used by income, so it can't be only for expenses")
            }
            for (c in listOf(me) + all.filter { it.parentId == id }) if (c.kind != kind) write(c, c.copy(kind = kind))
        }
    }

    /** Moves a category under a top-level parent, or to the top level when [parentId] is null. It goes to the end of its new level. */
    fun setParent(id: String, parentId: String?) = change(id) { all, c ->
        if (c.parentId == parentId) return@change c
        CategoryRules.checkParent(all, id, parentId)?.let { throw IllegalArgumentException(it) }
        CategoryRules.checkName(all, c.name, parentId, excludeId = id)?.let { throw IllegalArgumentException(it) }
        val parentKind = parentId?.let { p -> all.first { it.id == p }.kind } ?: c.kind
        if (parentKind != c.kind) {
            if (!parentKind.forExpense && q.categoryExpenseUse(id).executeAsOne() > 0) throw IllegalArgumentException("${c.name} is used by expenses, so it can't go under an income category")
            if (!parentKind.forIncome && q.categoryIncomeUse(id).executeAsOne() > 0) throw IllegalArgumentException("${c.name} is used by income, so it can't go under an expense category")
        }
        c.copy(parentId = parentId, position = (q.maxCategoryPosition(parentId).executeAsOne() + 1).toInt(), kind = parentKind)
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
            q.remapRecurring(intoId, fromId)
            // A limit moves with the category unless the target already has its own, which then wins.
            q.budgetByScope(fromId).executeAsOneOrNull()?.let { src ->
                val keep = q.budgetByScope(intoId).executeAsOneOrNull() == null
                writeBudget(db, clock, nowMillis, fromId, null)
                if (keep) writeBudget(db, clock, nowMillis, intoId, Money(src.limit_minor, Currency(src.currency, currencyDigits(src.currency))))
            }
            q.markMerged(intoId, fromId)
            q.insertOp(clock.now(nowMillis()).encode(), "category", fromId, "merged_into", intoId)
            q.insertOp(clock.now(nowMillis()).encode(), "category", fromId, "deleted", "1")
        }
    }

    /** Deletes a category nothing refers to. Otherwise merge it into another one. */
    fun delete(id: String) {
        db.transaction {
            CategoryRules.checkDelete(list(), id, usage(id))?.let { throw IllegalArgumentException(it) }
            writeBudget(db, clock, nowMillis, id, null) // a limit on a category that is going away goes with it
            q.softDeleteCategory(id)
            q.insertOp(clock.now(nowMillis()).encode(), "category", id, "deleted", "1")
        }
    }

    // ---- internals ----

    private fun currencyDigits(code: String): Int = q.selectCurrencies().executeAsList().firstOrNull { it.code == code }?.digits?.toInt() ?: 2

    private fun change(id: String, transform: (List<Category>, Category) -> Category) {
        db.transaction {
            val all = list()
            val before = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("That category does not exist")
            val after = transform(all, before)
            if (after != before) write(before, after)
        }
    }

    private fun write(before: Category, after: Category) {
        q.updateCategory(after.name, after.parentId, after.colorArgb, after.position.toLong(), if (after.hidden) 1 else 0, after.kind.name, before.id)
        log(before.id, fieldsOf(before), fieldsOf(after))
    }

    private fun fieldsOf(c: Category): Map<String, String?> = linkedMapOf(
        "name" to c.name, "parent_id" to c.parentId, "color" to c.colorArgb?.toString(), "position" to c.position.toString(), "hidden" to if (c.hidden) "1" else "0", "kind" to c.kind.name,
    )

    private fun log(id: String, before: Map<String, String?>?, after: Map<String, String?>) {
        for ((field, value) in after) {
            val changed = if (before == null) value != null else before[field] != value
            if (changed) q.insertOp(clock.now(nowMillis()).encode(), "category", id, field, value)
        }
    }

    private fun toCategory(r: app.expenses.core.storage.db.Category) =
        Category(r.id, r.name, r.parent_id, r.color, r.position.toInt(), r.hidden == 1L, CategoryKind.of(r.kind))

    private fun newId(): String = Random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /**
     * The first time, creates the eight default categories. Their ids equal their names, so entries recorded before categories
     * could be managed keep pointing at the right one. Seeding is not written to the sync log: every device creates the same rows.
     */
    private fun seedDefaults() {
        if (q.categoryCount().executeAsOne() == 0L) db.transaction {
            DEFAULTS.forEachIndexed { i, name -> q.insertCategory(name, name, null, null, i.toLong(), CategoryKind.EXPENSE.name) }
        }
        seedIncomeDefaults()
    }

    /**
     * Once, adds the income categories (an upgrade has none, and a new install gets them with the expense ones). Their ids are fixed, so every device
     * creates the same rows and nothing is logged; the marker stops them coming back after the person deletes or renames them.
     */
    private fun seedIncomeDefaults() {
        if (q.settingValue(INCOME_SEEDED).executeAsOneOrNull() != null) return
        db.transaction {
            INCOME_DEFAULTS.forEachIndexed { i, name ->
                val id = "income:" + name.lowercase().filter { it.isLetterOrDigit() }
                if (q.categoryById(id).executeAsOneOrNull() == null) q.insertCategory(id, name, null, null, (100 + i).toLong(), CategoryKind.INCOME.name)
            }
            q.putSetting(INCOME_SEEDED, "1")
        }
    }

    companion object {
        val DEFAULTS = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")
        val INCOME_DEFAULTS = listOf("Salary", "Interest", "Gifts received", "Refunds and cashback", "Other income")
        private const val INCOME_SEEDED = "income_categories_seeded"
    }
}
