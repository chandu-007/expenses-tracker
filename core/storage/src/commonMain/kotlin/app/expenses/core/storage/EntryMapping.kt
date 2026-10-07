package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.ledger.Entry as DomainEntry
import app.expenses.core.storage.db.Entry as EntryRow

/** Free-text details the ledger itself does not need but the app stores and searches. */
data class EntryDetails(
    val merchant: String? = null,
    val note: String? = null,
    val source: String = "manual",
    val sourceRef: String? = null,
    /** The entry's tags. Null leaves its tags as they are (an import or restore that knows nothing about tags must not wipe them); empty clears them. */
    val tags: List<String>? = null,
)

/** Column values for one entry row, plus its split and share lists. */
internal data class EntryColumns(
    val id: String,
    val kind: String,
    val occurredAt: Long,
    val accountId: String?,
    val counterAccountId: String?,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    val personId: String?,
    val toAmountMinor: Long?,
    val toCurrency: String?,
    val direction: String?,
    val refundsEntry: String?,
    val originalAmountMinor: Long?,
    val originalCurrency: String?,
    val splits: List<CategorySplit>,
    val shares: List<Share>,
)

internal object EntryMapping {
    fun columns(e: DomainEntry): EntryColumns = when (e) {
        is Expense -> EntryColumns(
            e.id.value, "EXPENSE", e.occurredAt, e.account.value, null, e.amount.minorUnits, e.amount.currency.code, null,
            null, null, null, null, null, e.original?.minorUnits, e.original?.currency?.code, e.splits, e.shares,
        )
        is Income -> EntryColumns(
            e.id.value, "INCOME", e.occurredAt, e.account.value, null, e.amount.minorUnits, e.amount.currency.code, e.category?.value,
            null, null, null, null, null, null, null, emptyList(), emptyList(),
        )
        is Transfer -> EntryColumns(
            e.id.value, "TRANSFER", e.occurredAt, e.from.value, e.to.value, e.amount.minorUnits, e.amount.currency.code, null,
            null, e.toAmount.minorUnits, e.toAmount.currency.code, null, null, null, null, emptyList(), emptyList(),
        )
        is Refund -> EntryColumns(
            e.id.value, "REFUND", e.occurredAt, e.account.value, null, e.amount.minorUnits, e.amount.currency.code, null,
            null, null, null, null, e.refundsEntry.value, null, null, emptyList(), emptyList(),
        )
        is FriendPaid -> EntryColumns(
            e.id.value, "FRIEND_PAID", e.occurredAt, null, null, e.myShare.minorUnits, e.myShare.currency.code, null,
            e.payer.value, null, null, null, null, null, null, e.splits, emptyList(),
        )
        is Settlement -> EntryColumns(
            e.id.value, "SETTLEMENT", e.occurredAt, e.account.value, null, e.amount.minorUnits, e.amount.currency.code, null,
            e.person.value, null, null, e.direction.name, null, null, null, emptyList(), emptyList(),
        )
    }

    /** Every currency an entry mentions, so its decimal places can be recorded. */
    fun currencies(e: DomainEntry): List<Currency> = when (e) {
        is Expense -> listOfNotNull(e.amount.currency, e.original?.currency)
        is Income -> listOf(e.amount.currency)
        is Transfer -> listOf(e.amount.currency, e.toAmount.currency)
        is Refund -> listOf(e.amount.currency)
        is FriendPaid -> listOf(e.myShare.currency)
        is Settlement -> listOf(e.amount.currency)
    }

    fun toDomain(row: EntryRow, splits: List<CategorySplit>, shares: List<Share>, currency: (String) -> Currency): DomainEntry {
        fun money(minor: Long, code: String) = Money(minor, currency(code))
        val id = EntryId(row.id)
        val amount = money(row.amount_minor, row.currency)
        return when (row.kind) {
            "EXPENSE" -> Expense(
                id, row.occurred_at, AccountId(row.account_id!!), amount, splits, shares,
                original = row.original_amount_minor?.let { money(it, row.original_currency!!) },
            )
            "INCOME" -> Income(id, row.occurred_at, AccountId(row.account_id!!), amount, row.category_id?.let(::CategoryId))
            "TRANSFER" -> Transfer(
                id, row.occurred_at, AccountId(row.account_id!!), AccountId(row.counter_account_id!!), amount,
                money(row.to_amount_minor!!, row.to_currency!!),
            )
            "REFUND" -> Refund(id, row.occurred_at, AccountId(row.account_id!!), amount, EntryId(row.refunds_entry!!))
            "FRIEND_PAID" -> FriendPaid(id, row.occurred_at, PersonId(row.person_id!!), amount, splits)
            "SETTLEMENT" -> Settlement(
                id, row.occurred_at, PersonId(row.person_id!!), AccountId(row.account_id!!), amount,
                SettlementDirection.valueOf(row.direction!!),
            )
            else -> error("Unknown entry kind '${row.kind}' for ${row.id}")
        }
    }

    /** Field-level view of a stored entry, used for the sync log: only changed fields are logged. */
    fun fields(row: EntryRow, splits: List<CategorySplit>, shares: List<Share>): Map<String, String?> = linkedMapOf(
        "kind" to row.kind,
        "occurred_at" to row.occurred_at.toString(),
        "account_id" to row.account_id,
        "counter_account_id" to row.counter_account_id,
        "amount_minor" to row.amount_minor.toString(),
        "currency" to row.currency,
        "category_id" to row.category_id,
        "person_id" to row.person_id,
        "to_amount_minor" to row.to_amount_minor?.toString(),
        "to_currency" to row.to_currency,
        "direction" to row.direction,
        "refunds_entry" to row.refunds_entry,
        "original_amount_minor" to row.original_amount_minor?.toString(),
        "original_currency" to row.original_currency,
        "merchant" to row.merchant,
        "note" to row.note,
        "source" to row.source,
        "source_ref" to row.source_ref,
        "splits" to splits.joinToString(",") { esc(it.category.value) + ":" + it.amount.minorUnits },
        "shares" to shares.joinToString(",") { esc(it.person?.value ?: "") + ":" + it.amount.minorUnits },
    )

    private fun esc(s: String) = s.replace("%", "%25").replace(",", "%2C").replace(":", "%3A")
}
