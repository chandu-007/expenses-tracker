package app.expenses.core.storage

import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb

/** A search kept under a name. [kind] is ANY, EXPENSE or INCOME and [range] is ANY, LAST_30_DAYS, THIS_PERIOD or THIS_YEAR (the screen's own names). */
data class SavedSearch(
    val id: String, val name: String, val text: String = "", val minRupees: String = "", val maxRupees: String = "",
    val categoryId: String? = null, val kind: String = "ANY", val range: String = "ANY", val tag: String? = null,
) {
    /** A search with nothing in it would match everything, which is not worth keeping. */
    val hasConditions: Boolean get() = text.isNotBlank() || minRupees.isNotBlank() || maxRupees.isNotBlank() || categoryId != null || kind != "ANY" || range != "ANY" || tag != null
}

/** Names of saved searches: 1 to [MAX_NAME] characters, each different ignoring case and spacing. */
class SavedSearchStore(private val db: ExpensesDb, private val clock: HlcClock, private val nowMillis: () -> Long) {
    private val q get() = db.schemaQueries

    fun list(): List<SavedSearch> = q.liveSavedSearches().executeAsList().map(::of)

    /** Saves (or, with an existing id, replaces) a search. Throws [IllegalArgumentException] with a reason for the user when it cannot be kept. */
    fun save(search: SavedSearch) {
        val name = search.name.trim().replace(Regex("\\s+"), " ")
        require(name.isNotEmpty()) { "Give the search a name." }
        require(name.length <= MAX_NAME) { "Keep the name to $MAX_NAME characters." }
        require(search.hasConditions) { "Type something to search for, or choose a filter, before saving." }
        db.transaction {
            val clash = list().firstOrNull { it.id != search.id && it.name.trim().replace(Regex("\\s+"), " ").equals(name, ignoreCase = true) }
            require(clash == null) { "You already have a saved search called \"${clash!!.name}\"." }
            val before = q.savedSearchById(search.id).executeAsOneOrNull()
            q.upsertSavedSearch(search.id, name, search.text.trim(), search.minRupees.trim(), search.maxRupees.trim(), search.categoryId, search.kind, search.range, search.tag)
            val after = q.savedSearchById(search.id).executeAsOne()
            fun log(field: String, old: String?, new: String?) { if (before == null || old != new) q.insertOp(clock.now(nowMillis()).encode(), "saved_search", search.id, field, new) }
            log("name", before?.name, after.name); log("text", before?.text, after.text); log("min_rupees", before?.min_rupees, after.min_rupees); log("max_rupees", before?.max_rupees, after.max_rupees)
            log("category_id", before?.category_id, after.category_id); log("kind", before?.kind, after.kind); log("range_kind", before?.range_kind, after.range_kind); log("tag", before?.tag, after.tag)
        }
    }

    fun delete(id: String) {
        db.transaction {
            if (q.savedSearchById(id).executeAsOneOrNull()?.deleted == 0L) {
                q.softDeleteSavedSearch(id)
                q.insertOp(clock.now(nowMillis()).encode(), "saved_search", id, "deleted", "1")
            }
        }
    }

    private fun of(r: app.expenses.core.storage.db.Saved_search) = SavedSearch(r.id, r.name, r.text, r.min_rupees, r.max_rupees, r.category_id, r.kind, r.range_kind, r.tag)

    companion object { const val MAX_NAME = 40 }
}
