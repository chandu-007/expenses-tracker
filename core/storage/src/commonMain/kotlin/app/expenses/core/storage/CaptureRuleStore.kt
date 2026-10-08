package app.expenses.core.storage

import app.expenses.core.domain.CaptureRule
import app.expenses.core.domain.CategorySuggest
import app.expenses.core.storage.db.ExpensesDb

/** The person's capture rules (ordered) and the payee-to-category choices learned from what they confirm. Not thread-safe: use through the write lock. */
class CaptureRuleStore(private val db: ExpensesDb) {
    private val q get() = db.schemaQueries

    fun rules(): List<CaptureRule> = q.allCaptureRules().executeAsList().map { CaptureRule(it.id, it.pattern, it.category_id) }

    /** Adds a rule at the end of the order. Refuses an empty pattern and a pattern an existing rule already has. */
    fun add(id: String, pattern: String, categoryId: String) {
        val clean = pattern.trim().replace(Regex("\\s+"), " ")
        val rule = CaptureRule(id, clean, categoryId)
        db.transaction {
            val clash = rules().firstOrNull { it.id != id && it.pattern.lowercase() == clean.lowercase() }
            require(clash == null) { "You already have a rule for \"${clash!!.pattern}\"." }
            require(q.categoryById(categoryId).executeAsOneOrNull()?.deleted == 0L) { "That category no longer exists." }
            val existing = q.allCaptureRules().executeAsList().firstOrNull { it.id == id }
            q.upsertCaptureRule(id, existing?.position ?: (q.maxRulePosition().executeAsOne() + 1), rule.pattern, categoryId)
        }
    }

    fun remove(id: String) { q.deleteCaptureRule(id) }

    /** Moves a rule one place earlier (up) or later in the order. Does nothing at the ends. */
    fun move(id: String, up: Boolean) {
        db.transaction {
            val all = q.allCaptureRules().executeAsList()
            val i = all.indexOfFirst { it.id == id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in all.indices) return@transaction
            // positions are re-numbered 1..n so two rules never share one
            val order = all.map { it.id }.toMutableList().also { it[i] = all[j].id; it[j] = all[i].id }
            order.forEachIndexed { n, rid -> val r = all.first { it.id == rid }; q.upsertCaptureRule(r.id, n + 1L, r.pattern, r.category_id) }
        }
    }

    /** What was chosen for each payee key before. */
    fun learned(): Map<String, String> = q.allMerchantMap().executeAsList().associate { it.key to it.category_id }

    /** Remembers that this payee goes to [categoryId]. A later different choice replaces it: corrections stick. Payees without a usable key are not remembered. */
    fun learn(payee: String?, categoryId: String) {
        val key = payee?.let(CategorySuggest::merchantKey)?.takeIf { it.length >= 2 } ?: return
        if (categoryId.isNotEmpty()) q.learnMerchant(key, categoryId)
    }

    fun forget(payee: String) { q.forgetMerchant(CategorySuggest.merchantKey(payee)) }

    /** How many past expenses and incomes mention every word of [pattern] in their payee or note: what the rule would have caught. */
    fun matchesInHistory(pattern: String): Long {
        val words = CaptureRule.words(pattern)
        if (words.isEmpty()) return 0
        // one word is counted in SQL; with several, the entries are checked in memory against all of them (rules have few words)
        if (words.size == 1) return q.entriesMentioning(escape(words.first())).executeAsOne()
        val rule = CaptureRule("t", pattern, "x")
        return q.recentEntries(HISTORY_SCAN) /* live entries only */.executeAsList().count { it.kind in setOf("EXPENSE", "INCOME") && rule.matches(listOfNotNull(it.merchant, it.note).joinToString(" ")) }.toLong()
    }

    private fun escape(w: String) = w.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object { const val HISTORY_SCAN = 20_000L }
}
