package app.expenses.core.storage

import app.expenses.core.domain.CaptureRule
import app.expenses.core.domain.PayeeKey
import app.expenses.core.storage.db.ExpensesDb

/**
 * One thing the app learned: payees that read as [key] go to [categoryId]; [uses] is how many times in a row the person chose it. A key that starts with [PayeeKey.INCOME_PREFIX] is a PAYER whose
 * money in goes to an income category ([income]); every other key is a payee of money out, as before.
 */
data class Learned(val key: String, val categoryId: String, val uses: Int, /** How the payee was written when it was learned ("Swiggy"), for the list; the key when unknown. */ val label: String = key) {
    val income: Boolean get() = PayeeKey.isIncomeKey(key)
}

/** A capture rule exactly as stored (its place in the order included), to put it back when its removal is undone. */
data class RuleSnapshot(val id: String, val position: Long, val pattern: String, val categoryId: String)

/** A learned choice exactly as stored (the count in a row and the label of the list included), to put it back when forgetting it is undone. */
data class LearnedSnapshot(val key: String, val categoryId: String, val uses: Int, val label: String?)

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

    /** The rule [id] as stored, or null. Read before [remove] so Undo can restore it. */
    fun snapshotRule(id: String): RuleSnapshot? = q.allCaptureRules().executeAsList().firstOrNull { it.id == id }?.let { RuleSnapshot(it.id, it.position, it.pattern, it.category_id) }

    /**
     * Puts a removed rule back where it was in the order. Refuses (and changes nothing) when its category is gone or another rule now has the same pattern: the person made that rule since, and
     * it stays. Ties in position (a rule was moved or added in between) are settled with the restored rule first, and the order is renumbered 1..n.
     */
    fun restoreRule(s: RuleSnapshot) {
        db.transaction {
            require(q.categoryById(s.categoryId).executeAsOneOrNull()?.deleted == 0L) { "That category no longer exists." }
            val clash = q.allCaptureRules().executeAsList().firstOrNull { it.id != s.id && it.pattern.lowercase() == s.pattern.lowercase() }
            require(clash == null) { "You already have a rule for \"${clash!!.pattern}\"." }
            q.upsertCaptureRule(s.id, s.position, s.pattern, s.categoryId)
            val all = q.allCaptureRules().executeAsList().sortedWith(compareBy({ it.position }, { if (it.id == s.id) 0 else 1 }, { it.id }))
            all.forEachIndexed { n, r -> if (r.position != n + 1L) q.upsertCaptureRule(r.id, n + 1L, r.pattern, r.category_id) }
        }
    }

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
    fun learn(payee: String?, categoryId: String, label: String? = null, income: Boolean = false) {
        val key = (if (income) PayeeKey.incomeOf(payee) else PayeeKey.of(payee)) ?: return
        if (categoryId.isEmpty()) return
        db.transaction {
            (label ?: payee)?.trim()?.takeIf { it.isNotEmpty() && '@' !in it }?.let { l -> if (q.settingValue(LABEL_PREFIX + key).executeAsOneOrNull() != l) q.putSetting(LABEL_PREFIX + key, l) }
            // an income choice has no older key to carry over: it was never stored before income was learned
            val old = if (income) null else PayeeKey.legacy(payee)?.takeIf { it != key }?.let { l -> q.allMerchantMap().executeAsList().firstOrNull { it.key == l } }
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

    fun forget(payee: String, income: Boolean = false) {
        if (income) { PayeeKey.incomeOf(payee)?.let { forgetKey(it) }; return }
        PayeeKey.of(payee)?.let { forgetKey(it) }
        PayeeKey.legacy(payee)?.let { forgetKey(it) }
    }

    /** Forgets what was learned under [key] (a row of [learnedRows]). */
    fun forgetKey(key: String) { q.forgetMerchant(key); q.removeSetting(LABEL_PREFIX + key) }

    /** The learned choice under [key] exactly as stored, or null. Read before [forgetKey] or [setLearned] so Undo can restore it. */
    fun snapshotLearned(key: String): LearnedSnapshot? =
        q.allMerchantMap().executeAsList().firstOrNull { it.key == key }?.let { LearnedSnapshot(it.key, it.category_id, it.uses.toInt(), q.settingValue(LABEL_PREFIX + key).executeAsOneOrNull()) }

    /**
     * Puts a learned choice back as it was: its category, the count in a row and its label. Whatever is stored under the key now (a choice made since) is replaced, as Undo of what the person just
     * did. Refuses when its category is gone.
     */
    fun restoreLearned(s: LearnedSnapshot) {
        db.transaction {
            require(q.categoryById(s.categoryId).executeAsOneOrNull()?.deleted == 0L) { "That category no longer exists." }
            q.forgetMerchant(s.key)
            q.learnMerchant(s.key, s.categoryId)
            if (s.uses != 1) q.setMerchantUses(s.uses.toLong(), s.key)
            if (s.label != null) q.putSetting(LABEL_PREFIX + s.key, s.label) else q.removeSetting(LABEL_PREFIX + s.key)
        }
    }

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
