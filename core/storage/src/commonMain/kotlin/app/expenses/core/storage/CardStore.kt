package app.expenses.core.storage

import app.expenses.core.domain.BillingCycle
import app.expenses.core.domain.CardBilling
import app.expenses.core.domain.CardStanding
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/** Credit card billing terms and what is still to pay on the latest statement. Not thread-safe: use through the process-wide write lock. */
class CardStore(private val db: ExpensesDb, private val clock: HlcClock, private val nowMillis: () -> Long) {
    private val q get() = db.schemaQueries

    fun terms(accountId: String): CardTerms? = q.cardTermsOf(accountId).executeAsOneOrNull()?.let(::of)

    fun all(): List<CardTerms> = q.allCardTerms().executeAsList().map(::of)

    /** The credit limit the person entered for a card, in minor units, or null. Kept on this device only (a setting, not part of the synced billing terms). */
    fun creditLimit(accountId: String): Long? = q.settingValue("card_limit:$accountId").executeAsOneOrNull()?.toLongOrNull()?.takeIf { it > 0 }

    /** Sets the credit limit; null or zero removes it. */
    fun setCreditLimit(accountId: String, minor: Long?) { q.putSetting("card_limit:$accountId", if (minor != null && minor > 0) minor.toString() else "") }

    /** Saves the terms of a credit card. Refuses an account that is not a live credit card. */
    fun save(terms: CardTerms) {
        db.transaction {
            val account = q.accountById(terms.accountId).executeAsOneOrNull()
            require(account != null && account.deleted == 0L && account.type == "CREDIT_CARD") { "Billing dates can only be set on a credit card." }
            val before = q.cardTermsOf(terms.accountId).executeAsOneOrNull()
            q.upsertCardTerms(terms.accountId, terms.statementDay.toLong(), terms.dueDay.toLong(), terms.minDuePercent.toLong(), terms.remindDaysBefore.toLong())
            fun log(field: String, old: Long?, new: Int) { if (before == null || old != new.toLong()) q.insertOp(clock.now(nowMillis()).encode(), "card_terms", terms.accountId, field, new.toString()) }
            log("statement_day", before?.statement_day, terms.statementDay); log("due_day", before?.due_day, terms.dueDay)
            log("min_due_percent", before?.min_due_percent, terms.minDuePercent); log("remind_days", before?.remind_days, terms.remindDaysBefore)
        }
    }

    fun clear(accountId: String) {
        db.transaction {
            if (q.cardTermsOf(accountId).executeAsOneOrNull() != null) {
                q.deleteCardTerms(accountId)
                q.insertOp(clock.now(nowMillis()).encode(), "card_terms", accountId, "deleted", "1")
            }
        }
    }

    /** The statement cut most recently as of [today], what was owed on it, and how much of that is still unpaid. Null when the card has no terms. */
    fun standing(accountId: String, today: LocalDate, zone: TimeZone): CardStanding? {
        val terms = terms(accountId) ?: return null
        val cycle: BillingCycle = CardBilling.cycleOn(today, terms)
        // The statement covers the whole of its day, so the cut is the start of the next day.
        val cutMillis = cycle.statementDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        val owed = -(q.accountBalanceBefore(cutMillis, accountId).executeAsOneOrNull()?.let { (it as Any?) as? Number }?.toLong() ?: 0L)
        val paid = ((q.accountInflowSince(accountId, cutMillis).executeAsOne() as Any?) as? Number)?.toLong() ?: 0L
        return CardBilling.standing(cycle, owed, paid, terms)
    }

    private fun of(r: app.expenses.core.storage.db.Card_terms) = CardTerms(r.account_id, r.statement_day.toInt(), r.due_day.toInt(), r.min_due_percent.toInt(), r.remind_days.toInt())
}
