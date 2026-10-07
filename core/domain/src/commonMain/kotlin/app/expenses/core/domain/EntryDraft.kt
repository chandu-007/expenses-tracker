package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.Entry
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Transfer

enum class DraftKind(val label: String) { EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer"), REFUND("Refund") }

/** One part of a split expense, as typed. */
data class SplitDraft(val category: String, val amountText: String)

/**
 * What the entry form holds, as typed, before it is a ledger entry. Every screen and every kind goes through [EntryDrafts.build], so
 * the rules for what may be saved live in one place and the form can show the exact reason when it cannot.
 *
 * - Expense: one [category] (empty = uncategorised), or, when [splits] has two or more parts, those parts, which must add up to the amount.
 * - Income: optional [category].
 * - Transfer: from [accountId] to [toAccountId]; covers a card bill payment, an ATM withdrawal (bank to cash) and moving savings.
 * - Refund: of [refundsEntry], at most [refundableMinor] (what is still refundable, counting the entry being edited as not yet refunded).
 */
data class EntryDraft(
    val id: String?,
    val kind: DraftKind,
    val amountText: String = "",
    val category: String = "",
    val splits: List<SplitDraft> = emptyList(),
    val accountId: String = "",
    val toAccountId: String = "",
    val note: String = "",
    val occurredAt: Long = 0L,
    val refundsEntry: String? = null,
    val refundableMinor: Long = 0L,
    val tags: List<String> = emptyList(),
)

sealed interface DraftResult {
    data class Ok(val entry: Entry) : DraftResult
    data class Problem(val message: String) : DraftResult
}

object EntryDrafts {
    /** Two or more parts make a split; a single part is just a category. */
    fun isSplit(d: EntryDraft) = d.kind == DraftKind.EXPENSE && d.splits.size >= 2

    /** What is left to assign in a split, in minor units (negative when over-assigned); null when amounts do not parse. */
    fun leftToAssign(d: EntryDraft, currency: Currency): Long? {
        val total = parseAmount(d.amountText, currency)?.minorUnits ?: return null
        var sum = 0L
        for (s in d.splits) { if (s.amountText.isBlank()) continue; sum += parseAmount(s.amountText, currency)?.minorUnits ?: return null }
        return total - sum
    }

    fun build(d: EntryDraft, id: EntryId, currency: Currency): DraftResult {
        fun problem(m: String) = DraftResult.Problem(m)
        val amount = parseAmount(d.amountText, currency)
        if (amount == null || amount.minorUnits <= 0) return problem(if (d.amountText.isBlank()) "Enter an amount." else "Enter an amount like 250 or 99.50.")
        if (d.accountId.isEmpty()) return problem("Choose an account.")
        return when (d.kind) {
            DraftKind.EXPENSE -> expense(d, id, amount, currency)
            DraftKind.INCOME -> DraftResult.Ok(Income(id, d.occurredAt, AccountId(d.accountId), amount, d.category.ifEmpty { null }?.let(::CategoryId)))
            DraftKind.TRANSFER -> when {
                d.toAccountId.isEmpty() -> problem("Choose the account the money goes to.")
                d.toAccountId == d.accountId -> problem("Choose two different accounts.")
                else -> DraftResult.Ok(Transfer(id, d.occurredAt, AccountId(d.accountId), AccountId(d.toAccountId), amount))
            }
            DraftKind.REFUND -> when {
                d.refundsEntry == null -> problem("A refund needs the expense it is for.")
                amount.minorUnits > d.refundableMinor -> problem(
                    if (d.refundableMinor <= 0) "This expense has been refunded in full." else "At most ${Money(d.refundableMinor, currency).formatted()} can still be refunded.",
                )
                else -> DraftResult.Ok(Refund(id, d.occurredAt, AccountId(d.accountId), amount, EntryId(d.refundsEntry)))
            }
        }
    }

    private fun expense(d: EntryDraft, id: EntryId, amount: Money, currency: Currency): DraftResult {
        if (!isSplit(d)) {
            val parts = if (d.category.isEmpty()) emptyList() else listOf(CategorySplit(CategoryId(d.category), amount))
            return DraftResult.Ok(Expense(id, d.occurredAt, AccountId(d.accountId), amount, parts))
        }
        val parts = ArrayList<CategorySplit>()
        for ((i, s) in d.splits.withIndex()) {
            if (s.category.isEmpty()) return DraftResult.Problem("Choose a category for part ${i + 1}.")
            val m = parseAmount(s.amountText, currency)
            if (m == null || m.minorUnits <= 0) return DraftResult.Problem("Enter an amount for part ${i + 1}.")
            parts += CategorySplit(CategoryId(s.category), m)
        }
        if (parts.map { it.category }.toSet().size != parts.size) return DraftResult.Problem("Use each category once; add the amounts together instead.")
        val sum = parts.sumOf { it.amount.minorUnits }
        if (sum != amount.minorUnits) {
            val diff = amount.minorUnits - sum
            return DraftResult.Problem(
                if (diff > 0) "${Money(diff, currency).formatted()} is not assigned to any category yet." else "The parts add up to ${Money(-diff, currency).formatted()} more than the amount.",
            )
        }
        return DraftResult.Ok(Expense(id, d.occurredAt, AccountId(d.accountId), amount, parts))
    }
}
