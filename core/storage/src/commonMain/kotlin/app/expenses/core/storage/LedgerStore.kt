package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Ledger
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.SpendBasis
import app.expenses.core.domain.ledger.SpendLine
import app.expenses.core.domain.ledger.SpendLines
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.domain.ledger.Entry as DomainEntry
import app.expenses.core.storage.db.Entry as EntryRow

/**
 * Saves ledger entries to the database and reads them back.
 *
 * Every write runs in one transaction and is validated by the same [Ledger] rules the app uses in memory,
 * so the database can never hold an entry the ledger would reject. Alongside each entry it keeps the
 * per-category spend lines (from [SpendLines]) that SQL reports read, and it appends one sync-log
 * operation per changed field, stamped by [clock].
 *
 * Not thread-safe: use one instance from one writer at a time.
 */
class LedgerStore(
    private val db: ExpensesDb,
    private val clock: HlcClock,
    private val nowMillis: () -> Long,
) {
    private val q get() = db.schemaQueries
    private val digits = HashMap<String, Int>().apply { q.selectCurrencies().executeAsList().forEach { put(it.code, it.digits.toInt()) } }

    // ---- accounts ----

    fun saveAccount(account: Account) {
        db.transaction {
            require(account.id.value.isNotEmpty()) { "Account id must not be empty" }
            ensureCurrency(account.currency)
            val before = q.accountById(account.id.value).executeAsOneOrNull()
            require(before == null || before.currency == account.currency.code) { "An account's currency cannot change" }
            q.insertAccount(account.id.value, account.name, account.type.name, account.currency.code)
            val beforeFields = before?.let { mapOf("name" to it.name, "type" to it.type, "currency" to it.currency) }
            logChanges("account", account.id.value, beforeFields, mapOf("name" to account.name, "type" to account.type.name, "currency" to account.currency.code))
        }
    }

    // ---- entries ----

    /** Adds or edits an entry. Throws [IllegalArgumentException], leaving the database unchanged, if it breaks a ledger rule. */
    fun save(requested: DomainEntry, details: EntryDetails = EntryDetails()) {
        db.transaction {
            val entry = redirectMergedCategories(requested)
            EntryMapping.currencies(entry).forEach(::ensureCurrency)
            val cols = EntryMapping.columns(entry)
            val before = q.entryById(cols.id).executeAsOneOrNull()
            require(before == null || before.deleted == 0L) { "Entry ${cols.id} is deleted; restore it before editing" }
            require(before == null || before.kind == cols.kind) { "An entry's kind cannot change" }
            validate(entry)
            val beforeFields = before?.let { EntryMapping.fields(it, splitsOf(it), sharesOf(it)) }

            q.upsertEntryFull(
                cols.id, cols.kind, cols.occurredAt, cols.accountId, cols.counterAccountId, cols.amountMinor, cols.currency,
                cols.categoryId, cols.personId, cols.toAmountMinor, cols.toCurrency, cols.direction, cols.refundsEntry,
                cols.originalAmountMinor, cols.originalCurrency, details.merchant, details.note, details.source, details.sourceRef,
            )
            q.deleteSplits(cols.id)
            cols.splits.forEachIndexed { i, s -> q.insertSplit(cols.id, i.toLong(), s.category.value, s.amount.minorUnits) }
            q.deleteShares(cols.id)
            cols.shares.forEachIndexed { i, s -> q.insertShare(cols.id, i.toLong(), s.person?.value, s.amount.minorUnits) }

            val row = q.entryById(cols.id).executeAsOne()
            logChanges("entry", cols.id, beforeFields, EntryMapping.fields(row, splitsOf(row), sharesOf(row)))
            rebuildSpendLines(entry)
        }
    }

    /** Soft-deletes an entry. An expense that still has refunds cannot be deleted; delete its refunds first. */
    fun delete(id: EntryId) {
        db.transaction {
            val row = q.entryById(id.value).executeAsOneOrNull() ?: throw IllegalArgumentException("Unknown entry: ${id.value}")
            if (row.deleted == 1L) return@transaction
            require(row.kind != "EXPENSE" || !q.hasLiveRefunds(id.value).executeAsOne()) {
                "Entry ${id.value} has refunds; delete them first"
            }
            q.softDeleteEntry(id.value)
            q.deleteSpendLines(id.value)
            logChanges("entry", id.value, mapOf("deleted" to "0"), mapOf("deleted" to "1"))
            if (row.kind == "REFUND") rebuildRefundLines(originalOf(row))
        }
    }

    /** Brings back a deleted entry, if it still obeys the ledger rules (for a refund: the expense is live and has room). */
    fun restore(id: EntryId) {
        db.transaction {
            val row = q.entryById(id.value).executeAsOneOrNull() ?: throw IllegalArgumentException("Unknown entry: ${id.value}")
            if (row.deleted == 0L) return@transaction
            val entry = domain(row)
            validate(entry)
            q.restoreEntry(id.value)
            logChanges("entry", id.value, mapOf("deleted" to "1"), mapOf("deleted" to "0"))
            rebuildSpendLines(entry)
        }
    }

    // ---- reading ----

    /** Rebuilds the whole in-memory [Ledger] from live rows, in the order they were first saved. */
    fun loadLedger(): Ledger {
        var ledger = Ledger.EMPTY
        for (a in q.selectAccounts().executeAsList()) ledger = ledger.addAccount(accountOf(a))
        val splits = q.allSplits().executeAsList().groupBy { it.entry_id }
        val shares = q.allShares().executeAsList().groupBy { it.entry_id }
        val entries = q.liveEntriesInInsertionOrder().executeAsList().map { row ->
            val cur = currency(row.currency)
            EntryMapping.toDomain(
                row,
                splits[row.id].orEmpty().map { CategorySplit(CategoryId(it.category_id), Money(it.amount_minor, cur)) },
                shares[row.id].orEmpty().map { Share(it.person_id?.let(::PersonId), Money(it.amount_minor, cur)) },
                ::currency,
            )
        }
        return ledger.addAll(entries)
    }

    /** Category totals from the stored spend lines. Matches [Ledger.spendByCategory]: zero totals are omitted. */
    fun spendByCategory(
        currency: Currency,
        basis: SpendBasis = SpendBasis.MY_SHARE,
        from: Long = Long.MIN_VALUE,
        until: Long = Long.MAX_VALUE,
    ): Map<CategoryId?, Money> =
        q.spendByCategory(currency.code, from, until).executeAsList()
            .associate { it.category_id?.let(::CategoryId) to ((if (basis == SpendBasis.MY_SHARE) it.mine else it.cash_out) ?: 0L) }
            .filterValues { it != 0L }
            .mapValues { Money(it.value, currency) }

    // ---- internals ----

    /** Points any category that was merged into another at its target, so nothing is ever stored against a removed category. */
    private fun redirectMergedCategories(entry: DomainEntry): DomainEntry {
        fun fix(c: CategoryId) = CategoryId(db.resolveCategory(c.value))
        fun fix(splits: List<CategorySplit>) = splits.map { CategorySplit(fix(it.category), it.amount) }
        return when (entry) {
            is Expense -> entry.copy(splits = fix(entry.splits))
            is FriendPaid -> entry.copy(splits = fix(entry.splits))
            is Income -> entry.copy(category = entry.category?.let(::fix))
            else -> entry
        }
    }

    private fun ensureCurrency(c: Currency) {
        val known = digits[c.code]
        if (known == null) {
            q.insertCurrency(c.code, c.minorUnitDigits.toLong())
            digits[c.code] = c.minorUnitDigits
        } else {
            require(known == c.minorUnitDigits) { "${c.code} was stored with $known decimal places, not ${c.minorUnitDigits}" }
        }
    }

    private fun currency(code: String) = Currency(code, digits[code] ?: error("Unknown currency $code"))

    private fun accountOf(a: app.expenses.core.storage.db.Account) =
        Account(AccountId(a.id), a.name, AccountType.valueOf(a.type), currency(a.currency))

    private fun splitsOf(row: EntryRow): List<CategorySplit> =
        q.splitsOf(row.id).executeAsList().map { CategorySplit(CategoryId(it.category_id), Money(it.amount_minor, currency(row.currency))) }

    private fun sharesOf(row: EntryRow): List<Share> =
        q.sharesOf(row.id).executeAsList().map { Share(it.person_id?.let(::PersonId), Money(it.amount_minor, currency(row.currency))) }

    private fun domain(row: EntryRow): DomainEntry = EntryMapping.toDomain(row, splitsOf(row), sharesOf(row), ::currency)

    private fun originalOf(refundRow: EntryRow): Expense {
        val orig = q.entryById(refundRow.refunds_entry!!).executeAsOneOrNull()
        require(orig != null && orig.deleted == 0L && orig.kind == "EXPENSE") { "Refund must reference an existing expense" }
        return domain(orig) as Expense
    }

    /** Runs the entry through [Ledger] with just the context it depends on (accounts, and refunds of the same expense). */
    private fun validate(entry: DomainEntry) {
        var ledger = Ledger.EMPTY
        for (a in q.selectAccounts().executeAsList()) ledger = ledger.addAccount(accountOf(a))
        when (entry) {
            is Refund -> {
                val original = originalOf(synthetic(entry))
                ledger = ledger.add(original)
                for (r in q.refundsOf(original.id.value).executeAsList()) if (r.id != entry.id.value) ledger = ledger.add(domain(r))
            }
            is Expense -> {
                ledger = ledger.add(entry) // validates the edit against refunds that already exist
                for (r in q.refundsOf(entry.id.value).executeAsList()) ledger = ledger.add(domain(r))
                return
            }
            else -> Unit
        }
        ledger.add(entry)
    }

    /** A refund row that only carries the id of its expense, so [originalOf] can be reused before the refund is stored. */
    private fun synthetic(refund: Refund): EntryRow = EntryRow(
        seq = 0, id = refund.id.value, kind = "REFUND", occurred_at = refund.occurredAt, account_id = refund.account.value,
        counter_account_id = null, amount_minor = refund.amount.minorUnits, currency = refund.amount.currency.code,
        category_id = null, person_id = null, to_amount_minor = null, to_currency = null, direction = null,
        refunds_entry = refund.refundsEntry.value, original_amount_minor = null, original_currency = null,
        merchant = null, note = null, source = "manual", source_ref = null, deleted = 0,
    )

    private fun rebuildSpendLines(entry: DomainEntry) {
        when (entry) {
            is Expense -> {
                writeLines(entry.id.value, entry.occurredAt, entry.amount.currency, SpendLines.of(entry))
                rebuildRefundLines(entry)
            }
            is FriendPaid -> writeLines(entry.id.value, entry.occurredAt, entry.myShare.currency, SpendLines.of(entry))
            is Refund -> rebuildRefundLines(originalOf(synthetic(entry)))
            else -> Unit
        }
    }

    /** Refunds of one expense share its categories, so they are always recomputed together. */
    private fun rebuildRefundLines(expense: Expense) {
        val refunds = q.refundsOf(expense.id.value).executeAsList().map { domain(it) as Refund }
        val lines = SpendLines.ofRefunds(expense, refunds)
        for (r in refunds) writeLines(r.id.value, r.occurredAt, expense.amount.currency, lines.getValue(r.id))
    }

    private fun writeLines(entryId: String, occurredAt: Long, currency: Currency, lines: List<SpendLine>) {
        q.deleteSpendLines(entryId)
        lines.filter { it.mine != 0L || it.cashOut != 0L }.forEachIndexed { i, l ->
            q.insertSpendLine(entryId, i.toLong(), occurredAt, currency.code, l.category?.value, l.mine, l.cashOut)
        }
    }

    /** One sync-log operation per field whose value changed (all non-null fields for a new row). */
    private fun logChanges(entity: String, rowId: String, before: Map<String, String?>?, after: Map<String, String?>) {
        for ((field, value) in after) {
            val changed = if (before == null) value != null else before[field] != value
            if (changed) q.insertOp(clock.now(nowMillis()).encode(), entity, rowId, field, value)
        }
    }
}
