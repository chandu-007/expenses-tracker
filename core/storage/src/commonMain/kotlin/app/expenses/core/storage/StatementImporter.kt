package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Flow
import app.expenses.core.domain.Money
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** What an import did. "Already there" rows were left exactly as they are. */
data class ImportResult(val imported: Int, val alreadyThere: Int, val spendingMinor: Long, val incomeMinor: Long, val matchedExisting: Int = 0)

/**
 * Saves the transactions read from a bank statement. Each becomes an expense (money out) or income (money in) on [account], dated at noon
 * on its day, with the statement's description as its merchant so it can be searched, and left uncategorised for the user to sort.
 *
 * **Importing is idempotent.** An entry's id is a fingerprint of its account, day, direction, amount and description, plus how many
 * identical rows came before it in the file (two identical coffees on one day are two entries, but always the same two). So importing
 * the same file again, or an export that overlaps an earlier one, adds only what is new. An id that already exists, whether live or
 * deleted, is skipped: an import never overwrites an edit and never brings back something the user deleted.
 *
 * A row that matches an entry from **another source** (a captured text or notification, or one typed by hand) on the same account, direction and
 * amount within a day of the row's date is **not added again** and is counted in [ImportResult.matchedExisting] so the person is told. Each existing entry
 * answers for at most one row, so two real same-amount purchases on one day are not both swallowed by one entry.
 * Everything is saved in one transaction, oldest first (which also keeps the search index fast), so a failure saves nothing.
 *
 * Not thread-safe: call through the process-wide write lock.
 */
class StatementImporter(private val db: ExpensesDb, private val ledger: LedgerStore) {
    private val q get() = db.schemaQueries

    fun import(txns: List<StatementTxn>, account: AccountId, currency: Currency, zone: TimeZone): ImportResult = db.transactionWithResult {
        val acct = q.accountById(account.value).executeAsOneOrNull()
        require(acct != null && acct.deleted == 0L) { "That account does not exist" }
        require(acct.currency == currency.code) { "That account is in ${acct.currency}, not ${currency.code}" }

        val seen = HashMap<String, Int>()
        val ordered = txns.withIndex().map { (i, t) ->
            val key = "${account.value}|${t.date.toEpochDays()}|${t.flow}|${t.minor}|${normalise(t.description)}"
            val n = (seen[key] ?: 0).also { seen[key] = it + 1 }
            Triple(t, "$key|$n", i)
        }.sortedWith(compareBy({ it.first.date }, { it.third }))

        var imported = 0; var there = 0; var spend = 0L; var income = 0L; var matched = 0
        val taken = HashSet<String>()
        for ((t, fingerprint, _) in ordered) {
            val hash = Sha256.hex(fingerprint.encodeToByteArray())
            val id = "csv:" + hash.take(ID_HEX)
            if (q.entryById(id).executeAsOneOrNull() != null) { there++; continue }
            val dayStart = t.date.atStartOfDayIn(zone).toEpochMilliseconds()
            val kind = if (t.flow == Flow.SPENDING) "EXPENSE" else "INCOME"
            val twin = q.entriesLikeStatementRow(account.value, kind, t.minor, dayStart - DAY_MILLIS, dayStart + 2 * DAY_MILLIS).executeAsList().firstOrNull { it !in taken }
            if (twin != null) { taken += twin; matched++; continue }
            val at = dayStart + NOON_MILLIS
            val money = Money(t.minor, currency)
            val details = EntryDetails(merchant = t.description.ifBlank { null }, source = SOURCE, sourceRef = hash)
            when (t.flow) {
                Flow.SPENDING -> { ledger.save(Expense(EntryId(id), at, account, money), details); spend += t.minor }
                Flow.INCOME -> { ledger.save(Income(EntryId(id), at, account, money), details); income += t.minor }
            }
            imported++
        }
        ImportResult(imported, there, spend, income, matched)
    }

    private fun normalise(description: String) = description.lowercase().replace(Regex("\\s+"), " ").trim()

    companion object {
        const val SOURCE = "csv"
        private const val ID_HEX = 32
        private const val NOON_MILLIS = 12 * 3_600_000L
        private const val DAY_MILLIS = 24 * 3_600_000L
    }
}
