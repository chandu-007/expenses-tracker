package app.expenses.core.domain.ledger

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import kotlin.jvm.JvmInline

@JvmInline value class AccountId(val value: String)
@JvmInline value class CategoryId(val value: String)
@JvmInline value class PersonId(val value: String)
@JvmInline value class EntryId(val value: String)

/**
 * Liability accounts (credit cards, loans) carry a negative balance when money is owed,
 * so net worth is always the plain sum of balances.
 */
enum class AccountType(val isLiability: Boolean) {
    BANK(false),
    CASH(false),
    WALLET(false),
    CREDIT_CARD(true),
    LOAN(true),
}

/**
 * [openingMinor] is what the account held before the first entry in the app (negative for money owed on a card or loan). [archived]
 * hides it from pickers; its history stays and still counts, because entries refer to it.
 */
data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val openingMinor: Long = 0L,
    val archived: Boolean = false,
)

/** One slice of an expense assigned to a category. A null category elsewhere means "uncategorized". */
data class CategorySplit(val category: CategoryId, val amount: Money) {
    init {
        require(amount.minorUnits > 0) { "Split amount must be positive" }
    }
}

/** Who consumed part of a shared expense. A null [person] is the user. */
data class Share(val person: PersonId?, val amount: Money) {
    init {
        require(amount.minorUnits > 0) { "Share amount must be positive" }
    }
}

sealed interface Entry {
    val id: EntryId

    /** Epoch milliseconds when the money actually moved. */
    val occurredAt: Long
}

/**
 * Money leaves [account]. [amount] is the full amount paid even when shared.
 * [shares] empty means the user consumed everything. Otherwise shares must sum to [amount]
 * and shares held by other people become receivables, not spending.
 * [original] keeps the foreign-currency amount for display; it is never recomputed.
 */
data class Expense(
    override val id: EntryId,
    override val occurredAt: Long,
    val account: AccountId,
    val amount: Money,
    val splits: List<CategorySplit> = emptyList(),
    val shares: List<Share> = emptyList(),
    val original: Money? = null,
) : Entry {
    init {
        require(amount.minorUnits > 0) { "Expense amount must be positive" }
    }

    /** The portion the user consumed. */
    val myShare: Money
        get() = if (shares.isEmpty()) amount else amount - sharesHeldByOthers()

    fun sharesHeldByOthers(): Money =
        shares.filter { it.person != null }.fold(Money.zero(amount.currency)) { acc, s -> acc + s.amount }
}

data class Income(
    override val id: EntryId,
    override val occurredAt: Long,
    val account: AccountId,
    val amount: Money,
    val category: CategoryId? = null,
) : Entry {
    init {
        require(amount.minorUnits > 0) { "Income amount must be positive" }
    }
}

/**
 * Movement between two of the user's own accounts. Never income or expense.
 * Covers card bill payments, ATM withdrawals (bank to cash), wallet top-ups and savings moves.
 * [toAmount] differs from [amount] only for cross-currency transfers.
 */
data class Transfer(
    override val id: EntryId,
    override val occurredAt: Long,
    val from: AccountId,
    val to: AccountId,
    val amount: Money,
    val toAmount: Money = amount,
) : Entry {
    init {
        require(from != to) { "Transfer needs two different accounts" }
        require(amount.minorUnits > 0 && toAmount.minorUnits > 0) { "Transfer amounts must be positive" }
    }
}

/** Money coming back for an earlier [Expense]. Net spend drops; it is not income. */
data class Refund(
    override val id: EntryId,
    override val occurredAt: Long,
    val account: AccountId,
    val amount: Money,
    val refundsEntry: EntryId,
) : Entry {
    init {
        require(amount.minorUnits > 0) { "Refund amount must be positive" }
    }
}

/**
 * A friend paid for something the user consumed. Creates the user's consumption and a payable
 * to [payer], with no account movement.
 */
data class FriendPaid(
    override val id: EntryId,
    override val occurredAt: Long,
    val payer: PersonId,
    val myShare: Money,
    val splits: List<CategorySplit> = emptyList(),
) : Entry {
    init {
        require(myShare.minorUnits > 0) { "Share must be positive" }
    }
}

enum class SettlementDirection { RECEIVED_FROM_PERSON, PAID_TO_PERSON }

/** Settling up with a person. Moves an account balance and the person balance, never spending. */
data class Settlement(
    override val id: EntryId,
    override val occurredAt: Long,
    val person: PersonId,
    val account: AccountId,
    val amount: Money,
    val direction: SettlementDirection,
) : Entry {
    init {
        require(amount.minorUnits > 0) { "Settlement amount must be positive" }
    }
}

enum class SpendBasis {
    /** What the user consumed. Shared expenses count only the user's share. */
    MY_SHARE,

    /** What left the user's accounts. Shared expenses count in full; friend-paid items are excluded. */
    CASH_OUT,
}
