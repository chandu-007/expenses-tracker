package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.Entry
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.domain.ledger.Transfer

enum class DraftKind(val label: String) { EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer"), REFUND("Refund"), LOAN("Lent or borrowed") }

/** One part of a split expense, as typed. */
data class SplitDraft(val category: String, val amountText: String)

/**
 * What the entry form holds, as typed, before it is a ledger entry. Every screen and every kind goes through [EntryDrafts.build], so
 * the rules for what may be saved live in one place and the form can show the exact reason when it cannot.
 *
 * - Expense: one [category] (empty = uncategorised), or, when [splits] has two or more parts, those parts, which must add up to the amount.
 * - Income: optional [category].
 * - Transfer: from [accountId] to [toAccountId]; covers a card bill payment, an ATM withdrawal (bank to cash) and moving savings.
 * - Lent or borrowed: money that went to ([gave]) or came from a person ([personId]), through [accountId]. It is not spending or income: it moves the account and what the person owes.
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
    val personId: String = "",
    /** For [DraftKind.LOAN]: true when the money went to the person (lent, or paid back), false when it came from them (borrowed, or paid to you). */
    val gave: Boolean = true,
    /** For a new expense: the people the bill is shared equally with (their ids). The whole amount leaves the account; only your share is spending, and each of them owes theirs. */
    val splitWith: List<String> = emptyList(),
    /** For a new expense paid in another currency: its code, what was paid in it and the rate chosen (units of the ledger's currency for one of it). The amount that left the account is worked out from these. */
    val foreignCode: String = "",
    val foreignAmount: String = "",
    val foreignRate: String = "",
    /** With [splitWith]: what each of them owes, typed (same order). Empty means the bill is shared equally. Your part is what is left. */
    val splitAmounts: List<String> = emptyList(),
    /** A friend who paid the whole bill (their id). The amount is then your share of it; no account moves, and you owe them that. */
    val paidBy: String = "",
)

/** Sharing one bill equally between you and some friends. */
object BillSplit {
    /** What each friend owes and what is left to you (the odd paise stay with you), for a bill of [totalMinor] shared with [friends] people. Null when it cannot be shared. */
    fun shares(totalMinor: Long, friends: Int): Pair<Long, Long>? {
        if (friends < 1 || totalMinor < friends + 1) return null
        val each = totalMinor / (friends + 1)
        return each to totalMinor - each * friends
    }

    /** What is left to you when the friends owe the amounts [owed] out of [totalMinor]. Null when they owe nothing, or leave you nothing. */
    fun mineAfter(totalMinor: Long, owed: List<Long>): Long? {
        if (owed.isEmpty() || owed.any { it <= 0 }) return null
        val left = totalMinor - owed.sum()
        return left.takeIf { it > 0 }
    }
}

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
        // paid in another currency: what left the account is what was paid times the rate; what was paid is kept beside it
        val foreign = if (d.kind == DraftKind.EXPENSE && d.foreignCode.isNotEmpty() && d.id == null)
            ForeignAmount.convert(d.foreignAmount, d.foreignRate, CurrencyChoices.byCode(d.foreignCode), currency)
                ?: return problem("Enter what you paid in ${d.foreignCode} and the rate you worked out: how many ${currency.code} one ${d.foreignCode} is.") else null
        val amount = foreign?.first ?: parseAmount(d.amountText, currency)
        if (amount == null || amount.minorUnits <= 0) return problem(if (d.amountText.isBlank()) "Enter an amount." else "Enter an amount like 250 or 99.50.")
        if (d.accountId.isEmpty()) return problem("Choose an account.")
        return when (d.kind) {
            DraftKind.EXPENSE -> expense(d, id, amount, currency, foreign?.second)
            DraftKind.INCOME -> DraftResult.Ok(Income(id, d.occurredAt, AccountId(d.accountId), amount, d.category.ifEmpty { null }?.let(::CategoryId)))
            DraftKind.TRANSFER -> when {
                d.toAccountId.isEmpty() -> problem("Choose the account the money goes to.")
                d.toAccountId == d.accountId -> problem("Choose two different accounts.")
                else -> DraftResult.Ok(Transfer(id, d.occurredAt, AccountId(d.accountId), AccountId(d.toAccountId), amount))
            }
            DraftKind.LOAN -> if (d.personId.isEmpty()) problem(if (d.gave) "Choose who you gave the money to." else "Choose who the money came from.")
                else DraftResult.Ok(Settlement(id, d.occurredAt, PersonId(d.personId), AccountId(d.accountId), amount, if (d.gave) SettlementDirection.PAID_TO_PERSON else SettlementDirection.RECEIVED_FROM_PERSON))
            DraftKind.REFUND -> when {
                d.refundsEntry == null -> problem("A refund needs the expense it is for.")
                amount.minorUnits > d.refundableMinor -> problem(
                    if (d.refundableMinor <= 0) "This expense has been refunded in full." else "At most ${Money(d.refundableMinor, currency).formatted()} can still be refunded.",
                )
                else -> DraftResult.Ok(Refund(id, d.occurredAt, AccountId(d.accountId), amount, EntryId(d.refundsEntry)))
            }
        }
    }

    private fun expense(d: EntryDraft, id: EntryId, amount: Money, currency: Currency, original: Money? = null): DraftResult {
        if (!isSplit(d)) {
            val parts = if (d.category.isEmpty()) emptyList() else listOf(CategorySplit(CategoryId(d.category), amount))
            if (d.paidBy.isNotEmpty() && d.id == null) return DraftResult.Ok(FriendPaid(id, d.occurredAt, PersonId(d.paidBy), amount, if (d.category.isEmpty()) emptyList() else listOf(CategorySplit(CategoryId(d.category), amount))))
            val friends = d.splitWith.distinct()
            if (friends.isEmpty() || d.id != null) return DraftResult.Ok(Expense(id, d.occurredAt, AccountId(d.accountId), amount, parts, original = original))
            if (d.splitAmounts.isNotEmpty()) {
                // each friend's part was typed: yours is what is left
                val owed = friends.indices.map { i -> parseAmount(d.splitAmounts.getOrElse(i) { "" }, currency)?.minorUnits ?: return DraftResult.Problem("Enter what friend ${i + 1} owes.") }
                val mine = BillSplit.mineAfter(amount.minorUnits, owed) ?: return DraftResult.Problem("What your friends owe has to be less than the whole bill, so a part is left for you.")
                return DraftResult.Ok(Expense(id, d.occurredAt, AccountId(d.accountId), amount, parts, listOf(Share(null, Money(mine, currency))) + friends.indices.map { Share(PersonId(friends[it]), Money(owed[it], currency)) }, original))
            }
            val (each, mine) = BillSplit.shares(amount.minorUnits, friends.size) ?: return DraftResult.Problem("That amount is too small to share between ${friends.size + 1} people.")
            return DraftResult.Ok(Expense(id, d.occurredAt, AccountId(d.accountId), amount, parts, listOf(Share(null, Money(mine, currency))) + friends.map { Share(PersonId(it), Money(each, currency)) }, original))
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
        return DraftResult.Ok(Expense(id, d.occurredAt, AccountId(d.accountId), amount, parts, original = original))
    }
}
