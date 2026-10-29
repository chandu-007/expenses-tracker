package app.expenses.core.storage

import app.expenses.core.domain.CardPayments
import app.expenses.core.domain.TransferWording
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Flow
import app.expenses.core.domain.Money
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * What an import did. "Already there" rows were left exactly as they are. [transfers] of the [imported] entries are transfers between the person's own accounts
 * (a card's bill paid from the bank), together [transferMinor]: they are in neither [spendingMinor] nor [incomeMinor].
 */
data class ImportResult(val imported: Int, val alreadyThere: Int, val spendingMinor: Long, val incomeMinor: Long, val matchedExisting: Int = 0, val transfers: Int = 0, val transferMinor: Long = 0)

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
 * A row from another app's file may name its own account ([StatementTxn.accountId]), which then stands in for [account] for that row (in its fingerprint and its
 * twin check too), and its category ([StatementTxn.categoryId]), note and labels, which the entry keeps. None of them is part of the fingerprint.
 * A row with a [StatementTxn.counterAccount] (the person said it is a card's bill being paid) is saved as a **transfer** between the two accounts instead, with
 * a fingerprint of its own that leaves the other account out (so choosing another card on a second import adds nothing). It counts as already there when the
 * same line was imported by an earlier version as an expense or income. It is **not added again** when a transfer between those two accounts for the same
 * amount is already in the app within three days, whatever its source: a "Mark paid", a captured text, or the other side's statement (the card's credit for
 * a bank's debit), and, as for any row, when a text or hand-typed expense or income of the same amount is there within a day.
 * An ordinary row (not said to be a transfer) is also **not added again** when it is one side of a transfer between the person's own accounts that is already in the app from
 * another source: a debit row of the source account, or a credit row of the destination account, of the same amount within a day. That is how the two statements of a transfer
 * captured from the bank's two texts add nothing on top of it; the transfer answers once for each side.
 * A row that carries its [StatementTxn.sourceText] (the line as it was in the file) keeps it as the entry's origin (kind STATEMENT).
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

        for (other in txns.mapNotNull { it.counterAccount }.toSet()) {
            val o = q.accountById(other).executeAsOneOrNull()
            require(o != null && o.deleted == 0L) { "The account a payment was to be moved to or from does not exist" }
            require(txns.none { it.counterAccount == other && (it.accountId ?: account.value) == other }) { "A transfer needs two different accounts" }
            require(o.currency == currency.code) { "${o.name} is in ${o.currency}, not ${currency.code}, so a payment cannot be moved to or from it here" }
        }
        // the accounts the rows name for themselves: each must be there, in the currency of the import
        val accountTypes = HashMap<String, String>().also { it[account.value] = acct.type }
        for (id in txns.mapNotNull { it.accountId }.toSet()) {
            val a = q.accountById(id).executeAsOneOrNull()
            require(a != null && a.deleted == 0L) { "An account named in the file does not exist" }
            require(a.currency == currency.code) { "${a.name} is in ${a.currency}, not ${currency.code}" }
            accountTypes[id] = a.type
        }
        for (id in txns.mapNotNull { it.categoryId }.toSet()) {
            val c = q.categoryById(id).executeAsOneOrNull()
            require(c != null && c.deleted == 0L) { "A category chosen for the file does not exist" }
        }

        val seen = HashMap<String, Int>()
        val ordered = txns.withIndex().map { (i, t) ->
            val text = normalise(t.description)
            val key = "${(t.accountId ?: account.value)}|${t.date.toEpochDays()}|${t.flow}|${t.minor}|$text"
            val n = (seen[key] ?: 0).also { seen[key] = it + 1 }
            Row(t, "$key|$n", text, n, i)
        }.sortedWith(compareBy({ it.txn.date }, { it.index }))

        var imported = 0; var there = 0; var spend = 0L; var income = 0L; var matched = 0; var transfers = 0; var transferMinor = 0L
        val taken = HashSet<String>()
        for ((t, fingerprint, text, n, _) in ordered) {
            val counter = t.counterAccount
            val rowAccount = t.accountId?.let(::AccountId) ?: account
            val cardSide = accountTypes[rowAccount.value] == "CREDIT_CARD"
            // a line as a transfer has a fingerprint of its own: named a transfer, and without the other account (so a second import choosing another card adds nothing)
            fun transferHash() = Sha256.hex("${rowAccount.value}|${t.date.toEpochDays()}|TRANSFER_${t.flow}|${t.minor}|$text|$n".encodeToByteArray())
            val plainHash = Sha256.hex(fingerprint.encodeToByteArray())
            val hash = if (counter != null) transferHash() else plainHash
            val id = "csv:" + hash.take(ID_HEX)
            if (q.entryById(id).executeAsOneOrNull() != null) { there++; continue }
            // the line as an earlier version (or a choice to leave it as it stands) made it still counts; and the other way round, a line that looks like a card
            // payment, once imported as a transfer, must not come back as spending on a later import that leaves it as it stands
            val otherId = if (counter != null) "csv:" + plainHash.take(ID_HEX) else if (looksLikeCardPayment(t, cardSide)) "csv:" + transferHash().take(ID_HEX) else null
            if (otherId != null && q.entryById(otherId).executeAsOneOrNull() != null) { there++; continue }
            val dayStart = t.date.atStartOfDayIn(zone).toEpochMilliseconds()
            val kind = if (t.flow == Flow.SPENDING) "EXPENSE" else "INCOME"
            // the same line of the same file, imported by an earlier version under another description: already there (each existing entry answers for one row)
            val sameLine = t.sourceText?.takeIf { it.isNotBlank() }?.let { text ->
                q.statementLineAlreadyThere(rowAccount.value, kind, t.minor, dayStart, dayStart + DAY_MILLIS, text.take(OriginStore.MAX_CHARS)).executeAsList().firstOrNull { it !in taken }
            }
            if (sameLine != null) { taken += sameLine; there++; continue }
            val transferTwin = if (counter == null) null else {
                val (from, to) = if (t.flow == Flow.SPENDING) rowAccount.value to counter else counter to rowAccount.value
                q.transfersLikeStatementRow(from, to, t.minor, dayStart - TRANSFER_WINDOW_DAYS * DAY_MILLIS, dayStart + (TRANSFER_WINDOW_DAYS + 1) * DAY_MILLIS).executeAsList().firstOrNull { it !in taken }
            }
            if (transferTwin != null) { taken += transferTwin; matched++; continue }
            val twin = q.entriesLikeStatementRow(rowAccount.value, kind, t.minor, dayStart - DAY_MILLIS, dayStart + 2 * DAY_MILLIS).executeAsList().firstOrNull { it !in taken }
            if (twin != null) { taken += twin; matched++; continue }
            // an ordinary line that is one side of a transfer between the person's own accounts already in the app (captured from the bank's two texts, say): not an expense or income on top
            // of it. One transfer answers once for each side, so the other account's statement can still match it. A same-amount line is the transfer's side only when it reads like one: its
            // words say transfer (TPT, FT, IMPS, NEFT, RTGS, "transfer", "sweep", own account), or it names the other account's last four digits, or this same file holds the other side's row.
            // Otherwise it is a real payment that happens to be the same amount (a Zomato order on the day of a 5,000 transfer) and is imported as it is.
            if (counter == null) {
                val outgoing = t.flow == Flow.SPENDING
                val from = dayStart - DAY_MILLIS; val until = dayStart + 2 * DAY_MILLIS
                val side = if (outgoing) "out" else "in"
                val candidates = if (outgoing) q.transfersOutOfLikeStatementRow(rowAccount.value, t.minor, from, until).executeAsList().map { it.id to it.other }
                    else q.transfersIntoLikeStatementRow(rowAccount.value, t.minor, from, until).executeAsList().map { it.id to it.other }
                val words = t.description + " " + t.sourceText.orEmpty()
                val transfer = candidates.firstOrNull { (id, other) -> "$id|$side" !in taken && (readsLikeTransfer(words, other) || fileHasOtherSide(txns, t, account.value, other)) }
                if (transfer != null) { taken += "${transfer.first}|$side"; matched++; continue }
            }
            val at = dayStart + NOON_MILLIS
            val money = Money(t.minor, currency)
            // labels only when the file had some: null leaves an entry's labels alone, which for a new entry is none
            val details = EntryDetails(merchant = t.description.ifBlank { null }, note = t.note, source = SOURCE, sourceRef = hash, tags = t.tags.takeIf { it.isNotEmpty() })
            val category = t.categoryId?.let(::CategoryId)
            when {
                counter != null -> {
                    val (from, to) = if (t.flow == Flow.SPENDING) rowAccount to AccountId(counter) else AccountId(counter) to rowAccount
                    ledger.save(Transfer(EntryId(id), at, from, to, money), details); transfers++; transferMinor += t.minor
                }
                t.flow == Flow.SPENDING -> { ledger.save(Expense(EntryId(id), at, rowAccount, money, splits = listOfNotNull(category?.let { CategorySplit(it, money) })), details); spend += t.minor }
                else -> { ledger.save(Income(EntryId(id), at, rowAccount, money, category), details); income += t.minor }
            }
            t.sourceText?.takeIf { it.isNotBlank() }?.let { q.putOrigin(id, OriginKind.STATEMENT.name, null, it.take(OriginStore.MAX_CHARS), null) }
            taken += id // a later row of this same file must not be taken for it
            imported++
        }
        ImportResult(imported, there, spend, income, matched, transfers, transferMinor)
    }

    /** Whether a statement line's words say it is a move between the person's own accounts: transfer wording, or the last four digits of the other account ([other], when it has some set). */
    private fun readsLikeTransfer(words: String, other: String?): Boolean {
        if (TransferWording.saysOrRail(words)) return true
        val last4 = other?.let { q.accountLookOf(it).executeAsOneOrNull()?.last4 } ?: return false
        var at = words.indexOf(last4)
        while (at >= 0) {
            if ((at == 0 || !words[at - 1].isDigit()) && (at + 4 >= words.length || !words[at + 4].isDigit())) return true
            at = words.indexOf(last4, at + 1)
        }
        return false
    }

    /** Whether the file has the row that is the other side of [t]: on the [other] account, the opposite direction, the same amount, within a day. */
    private fun fileHasOtherSide(txns: List<StatementTxn>, t: StatementTxn, account: String, other: String?): Boolean =
        other != null && txns.any { u -> u !== t && (u.accountId ?: account) == other && u.flow != t.flow && u.minor == t.minor && u.counterAccount == null && kotlin.math.abs(u.date.toEpochDays() - t.date.toEpochDays()) <= 1 }

    private fun looksLikeCardPayment(t: StatementTxn, cardSide: Boolean) =
        if (cardSide) t.flow == Flow.INCOME && CardPayments.looksLikePaymentReceived(t.description) else t.flow == Flow.SPENDING && CardPayments.looksLikePaymentToCard(t.description)

    /** A transaction with its fingerprint as spending or income, its description tidied, and how many identical lines came before it. */
    private data class Row(val txn: StatementTxn, val fingerprint: String, val text: String, val n: Int, val index: Int)

    private fun normalise(description: String) = description.lowercase().replace(Regex("\\s+"), " ").trim()

    companion object {
        const val SOURCE = "csv"
        private const val ID_HEX = 32
        private const val NOON_MILLIS = 12 * 3_600_000L
        private const val DAY_MILLIS = 24 * 3_600_000L
        /** A bank's debit and the card's credit for one payment, or a Mark paid made a day or two later, are days apart. */
        private const val TRANSFER_WINDOW_DAYS = 3
    }
}
