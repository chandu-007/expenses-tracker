package app.expenses.core.domain

/** An account of the person's as the importer needs it: a credit card is the other end of a card payment. [last4] is the digits they entered, if any. */
data class ImportAccount(val id: String, val name: String, val isCard: Boolean = false, val last4: String? = null)

/**
 * Everything the preview and the import need to decide about the transactions read from a file, in one place, from the person's accounts and categories:
 *  - a file from another app names accounts and categories: they are matched to the person's own by name (case does not matter), the account to the one chosen
 *    for the import when it matches none, the category to none (or to "Other", see [plan]);
 *  - a bank statement's card payments are found and treated as the person chose (see [CardPayments]).
 * Nothing here touches storage; creating the categories the person ticked is the caller's, with [Plan.newCategories].
 */
object ImportPlanner {
    /**
     * [txns] are the transactions to import (null while the person still has to choose what to do with card payments); [shown] are the ones the preview counts
     * (without the card payments awaiting a choice). [found], [others] and [pick] describe the card-payment group the screen offers (null found: none).
     */
    class Plan(
        val txns: List<StatementTxn>?, val shown: List<StatementTxn>,
        val found: CardPayments.Found?, val others: List<CardRef>, val pick: CardPayments.Pick,
        val skippedCardPayments: Int, val transfers: Int,
        val newCategories: List<ImportCategories.NewCategory>, val withCategory: Int, val onOwnAccount: Int,
    )

    /**
     * [from] a file of another app ([fromOtherApp]) names categories and accounts; a bank statement does not, and has card payments. [pick] is the person's choice
     * for the card payments and the side ([CardPayments.Side]) it was made for (ignored when the account changed to the other kind). [createCategories] is the
     * person's tick to create the categories they have none of; without it those rows go to a category called "Other" the person already has, else to none.
     */
    fun plan(
        parsed: List<StatementTxn>, into: ImportAccount, accounts: List<ImportAccount>, categories: List<CategoryRef>, fromOtherApp: Boolean,
        pick: Pair<CardPayments.Side, CardPayments.Pick>? = null, createCategories: Boolean = false,
    ): Plan {
        var txns = parsed
        var withCategory = 0; var onOwnAccount = 0
        var newCategories = emptyList<ImportCategories.NewCategory>()
        if (fromOtherApp) {
            newCategories = ImportCategories.unmatched(parsed, categories)
            val byName = accounts.groupBy { it.name.trim().lowercase() }
            fun other(income: Boolean) = categories.firstOrNull { it.parentId == null && it.name.trim().equals("Other", ignoreCase = true) && (if (income) it.forIncome else it.forExpense) }?.id
            txns = parsed.map { t ->
                val ownAccount = t.accountName?.trim()?.lowercase()?.let { byName[it] }?.singleOrNull()?.id
                val named = ImportCategories.nameOf(t.category, t.subcategory)
                val matched = if (named == null) null else ImportCategories.match(t.category, t.subcategory, t.flow == Flow.INCOME, categories)
                val category = matched ?: if (named != null && !createCategories) other(t.flow == Flow.INCOME) else null
                if (category != null) withCategory++
                if (ownAccount != null && ownAccount != into.id) onOwnAccount++
                t.copy(accountId = ownAccount, categoryId = category)
            }
            return Plan(txns, txns, null, emptyList(), CardPayments.Pick(null), 0, 0, newCategories, withCategory, onOwnAccount)
        }
        val side = if (into.isCard) CardPayments.Side.CARD else CardPayments.Side.BANK
        val others = accounts.filter { it.id != into.id && it.isCard == (side == CardPayments.Side.BANK) }.map { CardRef(it.id, it.name, it.last4) }
        val found = CardPayments.find(txns, side, others)
        val chosen = pick?.takeIf { it.first == side }?.second?.let { p -> p.copy(account = p.account?.takeIf { id -> others.any { it.id == id } }) }
        val effective = if (found == null) CardPayments.Pick(null) else chosen ?: CardPayments.defaultPick(found, others)
        val applied = CardPayments.apply(txns, found, effective, others)
        val inGroup = found?.indexes?.toSet().orEmpty()
        val shown = applied?.txns ?: txns.filterIndexed { i, _ -> i !in inGroup }
        return Plan(applied?.txns, shown, found, others, effective, applied?.skipped ?: 0, applied?.transfers ?: 0, emptyList(), 0, 0)
    }
}
