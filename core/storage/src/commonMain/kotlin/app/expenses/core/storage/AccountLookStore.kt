package app.expenses.core.storage

import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.AccountLooks
import app.expenses.core.domain.CardNetwork
import app.expenses.core.domain.CardTier
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb

/** The network, tier and last four digits of accounts. Not thread-safe: use through the process-wide write lock. */
class AccountLookStore(private val db: ExpensesDb, private val clock: HlcClock, private val nowMillis: () -> Long) {
    private val q get() = db.schemaQueries

    fun get(accountId: String): AccountLook? = q.accountLookOf(accountId).executeAsOneOrNull()?.let(::of)

    fun all(): List<AccountLook> = q.allAccountLooks().executeAsList().map(::of)

    /** Saves the look of a live account; a look with nothing set removes the row. Digits other than four are refused. */
    fun save(look: AccountLook) {
        db.transaction {
            val account = q.accountById(look.accountId).executeAsOneOrNull()
            require(account != null && account.deleted == 0L) { "That account no longer exists." }
            AccountLooks.parseLast4(look.last4.orEmpty()).getOrElse { throw IllegalArgumentException(it.message) }
            val before = q.accountLookOf(look.accountId).executeAsOneOrNull()
            if (look.isEmpty) {
                if (before != null) { q.deleteAccountLook(look.accountId); q.insertOp(clock.now(nowMillis()).encode(), "account_look", look.accountId, "deleted", "1") }
                return@transaction
            }
            q.upsertAccountLook(look.accountId, look.network?.name, look.tier?.name, look.last4)
            fun log(field: String, old: String?, new: String?) { if (before == null || old != new) q.insertOp(clock.now(nowMillis()).encode(), "account_look", look.accountId, field, new ?: "") }
            log("network", before?.network, look.network?.name); log("tier", before?.tier, look.tier?.name); log("last4", before?.last4, look.last4)
        }
    }

    /** The account these last four digits point at, or null when none or more than one could be meant (see [AccountLooks.pick]). */
    fun accountFor(last4: String?, preferCard: Boolean): String? {
        if (last4 == null) return null
        return AccountLooks.pick(q.accountsWithLast4(last4).executeAsList().map { it.id to AccountType.valueOf(it.type) }, preferCard)
    }

    private fun of(r: app.expenses.core.storage.db.Account_look) = AccountLook(
        r.account_id, r.network?.let { n -> CardNetwork.entries.firstOrNull { it.name == n } }, r.tier?.let { t -> CardTier.entries.firstOrNull { it.name == t } }, r.last4,
    )
}
