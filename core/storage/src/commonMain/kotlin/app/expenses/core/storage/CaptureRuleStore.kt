package app.expenses.core.storage

import app.expenses.core.domain.CaptureRule
import app.expenses.core.domain.PayeeKey
import app.expenses.core.storage.db.ExpensesDb

/** One thing the app learned: payees that read as [key] go to [categoryId]; [uses] is how many times in a row the person chose it. */
data class Learned(val key: String, val categoryId: String, val uses: Int, /** How the payee was written when it was learned ("Swiggy"), for the list; the key when unknown. */ val label: String = key)

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

    /** How many times in a row the person chose the same category for each payee key (a different choice starts again at 1). */
    fun learnedUses(): Map<String, Int> = q.allMerchantMap().executeAsList().associate { it.key to it.uses.toInt() }

    /** Everything remembered, for the list the person can look at and change: the key, the category, and how many times in a row they chose it. */
    fun learnedRows(): List<Learned> {
        val labels = q.settingsWithPrefix(LABEL_PREFIX).executeAsList().associate { it.key.removePrefix(LABEL_PREFIX) to it.value_ }
        return q.allMerchantMap().executeAsList().map { Learned(it.key, it.category_id, it.uses.toInt(), labels[it.key]?.takeIf { l -> l.isNotBlank() } ?: it.key) }
    }

    /**
     * Remembers that this payee goes to [categoryId], under [PayeeKey.of] (so "SWIGGY BANGALORE KA IN", "swiggy@icici" and "Swiggy" are one payee). A later different choice replaces it:
     * corrections stick. A choice remembered before that key existed (stored under the payee's letters and digits as read) is carried over and its count kept when it agrees. Payees without a
     * usable key are not remembered.
     */
    fun learn(payee: String?, categoryId: String, label: String? = null) {
        val key = PayeeKey.of(payee) ?: return
        if (categoryId.isEmpty()) return
        db.transaction {
            (label ?: payee)?.trim()?.takeIf { it.isNotEmpty() && '@' !in it }?.let { l -> if (q.settingValue(LABEL_PREFIX + key).executeAsOneOrNull() != l) q.putSetting(LABEL_PREFIX + key, l) }
            val old = PayeeKey.legacy(payee)?.takeIf { it != key }?.let { l -> q.allMerchantMap().executeAsList().firstOrNull { it.key == l } }
            q.learnMerchant(key, categoryId)
            if (old != null) {
                if (old.category_id == categoryId) repeat(old.uses.toInt()) { q.learnMerchant(key, categoryId) }
                q.forgetMerchant(old.key)
            }
        }
    }

    /** The person's own choice of a category for what the app learned under [key]: the choice stands as if they had made it once. */
    fun setLearned(key: String, categoryId: String) {
        require(key.isNotEmpty() && q.categoryById(categoryId).executeAsOneOrNull()?.deleted == 0L) { "That category no longer exists." }
        db.transaction { q.forgetMerchant(key); q.learnMerchant(key, categoryId) }
    }

    fun forget(payee: String) {
        PayeeKey.of(payee)?.let { forgetKey(it) }
        PayeeKey.legacy(payee)?.let { forgetKey(it) }
    }

    /** Forgets what was learned under [key] (a row of [learnedRows]). */
    fun forgetKey(key: String) { q.forgetMerchant(key); q.removeSetting(LABEL_PREFIX + key) }

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

    companion object { const val HISTORY_SCAN = 20_000L; private const val LABEL_PREFIX = "learned_label:" }
}
