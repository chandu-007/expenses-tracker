package app.expenses.core.domain.ledger

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money

/**
 * In-memory ledger that enforces the money rules in PRODUCT_CONTEXT.md section 4.
 * It is immutable: [addAccount], [add] and [addAll] return a new ledger, or throw
 * [IllegalArgumentException] if an entry breaks a rule, so an invalid state can never exist.
 *
 * This is the reference model used by tests and by on-device validation. Large-scale queries
 * in the app run as indexed SQL in the storage layer and must agree with this model.
 *
 * Known limitation: refunds of shared expenses (expenses with [Expense.shares]) are rejected
 * until the product defines how a refund is split between participants.
 */
class Ledger private constructor(
    val accounts: Map<AccountId, Account>,
    val entries: List<Entry>,
    private val byId: Map<EntryId, Entry>,
    private val refunded: Map<EntryId, Long>,
) {

    fun addAccount(account: Account): Ledger {
        require(account.id !in accounts) { "Account already exists: ${account.id.value}" }
        return Ledger(accounts + (account.id to account), entries, byId, refunded)
    }

    fun add(entry: Entry): Ledger = addAll(listOf(entry))

    /** Validates and appends entries in order. Atomic: if any entry is invalid nothing is added. */
    fun addAll(newEntries: List<Entry>): Ledger {
        val list = ArrayList(entries)
        val map = HashMap(byId)
        val refundedMap = HashMap(refunded)
        for (e in newEntries) {
            require(e.id !in map) { "Duplicate entry id: ${e.id.value}" }
            validate(e, map, refundedMap)
            list.add(e)
            map[e.id] = e
        }
        return Ledger(accounts, list, map, refundedMap)
    }

    fun entry(id: EntryId): Entry? = byId[id]

    /** Total already refunded against an expense, in minor units. */
    fun refundedMinorUnits(expense: EntryId): Long = refunded[expense] ?: 0L

    /** Signed balance of every account. Liability accounts are negative when money is owed. */
    fun balances(): Map<AccountId, Money> {
        val totals = HashMap<AccountId, Long>()
        for ((id, account) in accounts) totals[id] = account.openingMinor
        fun move(account: AccountId, delta: Long) {
            totals[account] = (totals[account] ?: 0L) + delta
        }
        for (e in entries) {
            when (e) {
                is Expense -> move(e.account, -e.amount.minorUnits)
                is Income -> move(e.account, e.amount.minorUnits)
                is Transfer -> {
                    move(e.from, -e.amount.minorUnits)
                    move(e.to, e.toAmount.minorUnits)
                }
                is Refund -> move(e.account, e.amount.minorUnits)
                is Settlement -> when (e.direction) {
                    SettlementDirection.RECEIVED_FROM_PERSON -> move(e.account, e.amount.minorUnits)
                    SettlementDirection.PAID_TO_PERSON -> move(e.account, -e.amount.minorUnits)
                }
                is FriendPaid -> Unit
            }
        }
        return totals.mapValues { (id, v) -> Money(v, accounts.getValue(id).currency) }
    }

    fun balance(account: AccountId): Money {
        require(account in accounts) { "Unknown account: ${account.value}" }
        return balances().getValue(account)
    }

    /** Sum of all account balances in [currency]. Transfers never change it. */
    fun netWorth(currency: Currency): Money =
        balances().values.filter { it.currency == currency }.fold(Money.zero(currency)) { acc, m -> acc + m }

    /** Positive means [person] owes the user; negative means the user owes [person]. */
    fun personBalance(person: PersonId, currency: Currency): Money {
        var total = 0L
        for (e in entries) {
            when (e) {
                is Expense -> if (e.amount.currency == currency) {
                    e.shares.filter { it.person == person }.forEach { total += it.amount.minorUnits }
                }
                is FriendPaid -> if (e.payer == person && e.myShare.currency == currency) total -= e.myShare.minorUnits
                is Settlement -> if (e.person == person && e.amount.currency == currency) {
                    total += when (e.direction) {
                        SettlementDirection.RECEIVED_FROM_PERSON -> -e.amount.minorUnits
                        SettlementDirection.PAID_TO_PERSON -> e.amount.minorUnits
                    }
                }
                else -> Unit
            }
        }
        return Money(total, currency)
    }

    /**
     * Net spending per category in [currency] for entries with `from <= occurredAt < until`.
     * Refunds reduce the categories of the original expense (see [SpendLines.ofRefunds]).
     * Transfers, income and settlements never count. A null key is "uncategorized".
     */
    fun spendByCategory(
        currency: Currency,
        basis: SpendBasis = SpendBasis.MY_SHARE,
        from: Long = Long.MIN_VALUE,
        until: Long = Long.MAX_VALUE,
    ): Map<CategoryId?, Money> {
        val totals = LinkedHashMap<CategoryId?, Long>()
        fun add(lines: List<SpendLine>) {
            for (l in lines) {
                val v = if (basis == SpendBasis.MY_SHARE) l.mine else l.cashOut
                totals[l.category] = (totals[l.category] ?: 0L) + v
            }
        }
        val refundLines = refundLines()
        for (e in entries) {
            if (e.occurredAt < from || e.occurredAt >= until) continue
            when (e) {
                is Expense -> if (e.amount.currency == currency) add(SpendLines.of(e))
                is Refund -> {
                    val original = byId.getValue(e.refundsEntry) as Expense
                    if (original.amount.currency == currency) add(refundLines.getValue(e.id))
                }
                is FriendPaid -> if (e.myShare.currency == currency) add(SpendLines.of(e))
                else -> Unit
            }
        }
        return totals.filterValues { it != 0L }.mapValues { Money(it.value, currency) }
    }

    fun totalSpend(
        currency: Currency,
        basis: SpendBasis = SpendBasis.MY_SHARE,
        from: Long = Long.MIN_VALUE,
        until: Long = Long.MAX_VALUE,
    ): Money = spendByCategory(currency, basis, from, until).values.fold(Money.zero(currency)) { acc, m -> acc + m }

    private fun refundLines(): Map<EntryId, List<SpendLine>> {
        val result = HashMap<EntryId, List<SpendLine>>()
        for ((expenseId, refunds) in entries.filterIsInstance<Refund>().groupBy { it.refundsEntry }) {
            result.putAll(SpendLines.ofRefunds(byId.getValue(expenseId) as Expense, refunds))
        }
        return result
    }

    private fun accountOf(id: AccountId): Account =
        accounts[id] ?: throw IllegalArgumentException("Unknown account: ${id.value}")

    private fun requireMoneyOn(account: AccountId, amount: Money, what: String) {
        val a = accountOf(account)
        require(amount.currency == a.currency) {
            "$what is in ${amount.currency.code} but account ${a.name} is ${a.currency.code}"
        }
    }

    private fun requireSumEquals(total: Money, parts: List<Money>, what: String) {
        require(parts.all { it.currency == total.currency }) { "$what must use ${total.currency.code}" }
        val sum = parts.fold(Money.zero(total.currency)) { acc, m -> acc + m }
        require(sum == total) { "$what must add up to the total (got ${sum.minorUnits}, expected ${total.minorUnits})" }
    }

    private fun validate(e: Entry, byId: Map<EntryId, Entry>, refunded: MutableMap<EntryId, Long>) {
        when (e) {
            is Expense -> {
                requireMoneyOn(e.account, e.amount, "Expense")
                if (e.splits.isNotEmpty()) requireSumEquals(e.amount, e.splits.map { it.amount }, "Category splits")
                if (e.shares.isNotEmpty()) requireSumEquals(e.amount, e.shares.map { it.amount }, "Shares")
            }
            is Income -> requireMoneyOn(e.account, e.amount, "Income")
            is Transfer -> {
                requireMoneyOn(e.from, e.amount, "Transfer source amount")
                requireMoneyOn(e.to, e.toAmount, "Transfer destination amount")
                if (accountOf(e.from).currency == accountOf(e.to).currency) {
                    require(e.amount == e.toAmount) { "Same-currency transfer must have equal amounts" }
                }
            }
            is Refund -> {
                val original = byId[e.refundsEntry] as? Expense
                    ?: throw IllegalArgumentException("Refund must reference an existing expense")
                require(original.shares.isEmpty()) { "Refunds of shared expenses are not supported yet" }
                require(e.amount.currency == original.amount.currency) { "Refund currency must match the expense" }
                requireMoneyOn(e.account, e.amount, "Refund")
                val already = refunded[original.id] ?: 0L
                val total = already + e.amount.minorUnits
                require(total <= original.amount.minorUnits) {
                    "Refunds (${total}) would exceed the original expense (${original.amount.minorUnits})"
                }
                refunded[original.id] = total
            }
            is FriendPaid -> {
                if (e.splits.isNotEmpty()) requireSumEquals(e.myShare, e.splits.map { it.amount }, "Category splits")
            }
            is Settlement -> requireMoneyOn(e.account, e.amount, "Settlement")
        }
    }

    companion object {
        val EMPTY = Ledger(emptyMap(), emptyList(), emptyMap(), emptyMap())
    }
}
