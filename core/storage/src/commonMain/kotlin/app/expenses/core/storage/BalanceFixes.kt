package app.expenses.core.storage

import app.expenses.core.domain.BalanceFix
import app.expenses.core.domain.BalanceFixEntry
import app.expenses.core.domain.BalanceFixLog
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.db.ExpensesDb

/**
 * "Fix this balance": the person says what an account really holds (the bank app, the card statement) and the difference moves the account's OPENING balance, nothing else.
 * No entry is made, so no category, budget, report, subscription or Transfer-candidate figure can change; every balance of the account, at every date, moves by the same
 * amount, which is also what the statement and limit figures of a card are worked out from. The change goes through [LedgerStore.saveAccount], so it is logged for sync like any
 * other edit of the account (`opening_minor`). The fix is remembered as one line in a setting ([BalanceFixLog]) so the account can show it and a backup can carry it.
 *
 * Not thread-safe: use through the process-wide write lock.
 */
class BalanceFixes(private val db: ExpensesDb, private val store: LedgerStore) {
    private val q get() = db.schemaQueries

    /** What [fix] did, or why it did nothing (the reason is written for the person). */
    sealed interface Result {
        data class Fixed(val accountId: String, val name: String, val type: AccountType, val beforeMinor: Long, val afterMinor: Long, val atMillis: Long) : Result {
            val deltaMinor: Long get() = afterMinor - beforeMinor
        }
        data class Refused(val message: String) : Result
    }

    /**
     * Makes [accountId]'s balance [stated] (a signed balance in the account's currency: negative is owed on a card or loan). The balance the app shows is read here, inside the
     * transaction, so a change made since the person looked is not missed. Refuses an account that is gone, a currency other than the account's, a balance already right, and amounts
     * too large to hold. All or nothing.
     */
    fun fix(accountId: String, stated: Long, currency: Currency, atMillis: Long): Result = db.transactionWithResult {
        val row = q.accountById(accountId).executeAsOneOrNull()
        if (row == null || row.deleted != 0L) return@transactionWithResult Result.Refused(BalanceFix.NO_ACCOUNT)
        if (row.currency != currency.code) return@transactionWithResult Result.Refused("This account is in ${row.currency}, and the ledger's currency is ${currency.code}. Only the ledger's currency can be used here.")
        val current = store.balances()[AccountId(accountId)]?.minorUnits ?: 0L
        val plan = BalanceFix.plan(current, stated) ?: return@transactionWithResult Result.Refused(BalanceFix.TOO_LARGE)
        if (plan.matches) return@transactionWithResult Result.Refused(BalanceFix.ALREADY_MATCHES)
        val opening = BalanceFix.newOpening(row.opening_minor, plan.deltaMinor) ?: return@transactionWithResult Result.Refused(BalanceFix.TOO_LARGE)
        val type = AccountType.valueOf(row.type)
        store.saveAccount(Account(AccountId(row.id), row.name, type, currency, opening, row.archived == 1L))
        writeHistory(accountId, BalanceFixLog.add(history(accountId), BalanceFixEntry(atMillis, current, stated)))
        dismissHint(accountId)
        Result.Fixed(row.id, row.name, type, current, stated, atMillis)
    }

    /**
     * Takes the fix made at [atMillis] back: the opening balance moves by [deltaMinor] the other way (a fix is a difference, so later edits of the account are not lost) and its line leaves
     * the history. False when the account is gone or the move would pass the limit; nothing is changed then.
     */
    fun undo(accountId: String, deltaMinor: Long, atMillis: Long, currency: Currency): Boolean = db.transactionWithResult {
        val row = q.accountById(accountId).executeAsOneOrNull()
        if (row == null || row.deleted != 0L || row.currency != currency.code) return@transactionWithResult false
        // a fix is undone once: its history line is the proof that it is still in force (a second call would move the opening balance again)
        if (history(accountId).none { it.atMillis == atMillis && it.deltaMinor == deltaMinor }) return@transactionWithResult false
        val opening = BalanceFix.newOpening(row.opening_minor, -deltaMinor) ?: return@transactionWithResult false
        store.saveAccount(Account(AccountId(row.id), row.name, AccountType.valueOf(row.type), currency, opening, row.archived == 1L))
        writeHistory(accountId, BalanceFixLog.remove(history(accountId), atMillis))
        true
    }

    /** The fixes of one account, oldest first. */
    fun history(accountId: String): List<BalanceFixEntry> = BalanceFixLog.parse(q.settingValue(BalanceFixLog.key(accountId)).executeAsOneOrNull())

    /** The fixes of every account that has any, by account id (one query), oldest first. */
    fun allHistory(): Map<String, List<BalanceFixEntry>> =
        q.settingsWithPrefix(BalanceFixLog.PREFIX).executeAsList().associate { it.key.removePrefix(BalanceFixLog.PREFIX) to BalanceFixLog.parse(it.value_) }.filterValues { it.isNotEmpty() }

    private fun writeHistory(accountId: String, entries: List<BalanceFixEntry>) {
        if (entries.isEmpty()) q.removeSetting(BalanceFixLog.key(accountId)) else q.putSetting(BalanceFixLog.key(accountId), BalanceFixLog.format(entries))
    }

    // ---- the hint "Looks like some earlier spending is missing" ----

    /** The accounts whose hint was dismissed (or whose balance was fixed), by id, in one query. A setting of this phone only: it is not in a backup. */
    fun dismissedHints(): Set<String> = q.settingsWithPrefix(HINT_PREFIX).executeAsList().filter { it.value_ == "1" }.mapTo(HashSet()) { it.key.removePrefix(HINT_PREFIX) }

    fun dismissHint(accountId: String) = q.putSetting(HINT_PREFIX + accountId, "1")

    companion object {
        const val HINT_PREFIX = "fix_hint_off:"
    }
}
