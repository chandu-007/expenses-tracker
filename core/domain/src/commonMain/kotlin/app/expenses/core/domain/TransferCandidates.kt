package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType

/**
 * One live expense or income as [TransferCandidates] reads it. [outgoing] is true for an expense. [at] is when it happened, [arrivedAt] when the text it was read from arrived (null for an
 * entry typed by hand). [originText] is the original words of a captured entry. Amounts are minor units of the ledger's currency.
 */
data class CandidateEntry(
    val id: String, val outgoing: Boolean, val accountId: String, val at: Long, val arrivedAt: Long?, val amountMinor: Long,
    val title: String?, val note: String?, val originText: String?,
)

data class CandidateAccount(val id: String, val name: String, val type: AccountType)

enum class CandidateGroup { CASH_WITHDRAWALS, OWN_ACCOUNTS, SELF_TRANSFERS }

/**
 * One suggestion. For [CandidateGroup.OWN_ACCOUNTS] [entry] is the expense and [partner] the income it pairs with (one transfer replaces both); for the other groups [entry] stands alone.
 * [reason] says in words why it is suggested. Nothing here changes anything: converting is the person's tap.
 */
data class TransferCandidate(val group: CandidateGroup, val entry: CandidateEntry, val partner: CandidateEntry?, val reason: String)

/** What was found, each group newest first. */
data class TransferCandidateList(val cashWithdrawals: List<TransferCandidate>, val ownAccounts: List<TransferCandidate>, val selfTransfers: List<TransferCandidate>) {
    val total: Int get() = cashWithdrawals.size + ownAccounts.size + selfTransfers.size
    val isEmpty: Boolean get() = total == 0
}

/**
 * Finds, among the expenses and income already in the ledger, the ones that are really money moving between the person's own accounts, so they can be turned into transfers (with Undo)
 * instead of one by one. Precision over recall: a candidate is something the words or the other half of the move say, never a guess from an amount alone.
 *
 *  - **Cash withdrawals**: an expense paid from a bank account, wallet or credit card whose title, note or original words say an ATM cash withdrawal ([TransferWording.readsAsAtmWithdrawal];
 *    the same list the capture side uses). A withdrawal on a credit card (a cash advance) is included: the money moves from the card to the person's cash, and spending it later is recorded as
 *    spending, so leaving it an expense would count it twice. Entries paid from the Cash account itself are not.
 *  - **Moves between your own accounts**: an expense out of one bank account or wallet and an income into another, of exactly the same amount, that the rules of the live pairing of two bank texts
 *    ([TransferPairs.basis]: "transfer"-type wording on either half and half an hour, or a text naming the other account and a day) put together. One transfer replaces both.
 *  - **Self transfers**: one entry alone whose words say it is to or from the person themselves ([TransferWording.saysSelfTransfer]: "self transfer", "to self", "own account", "own name"...), and
 *    which no pair or withdrawal already took.
 * An entry the person said is "not a transfer" ([dismissed]) and one that cannot change its type ([refused]: shared, foreign, refunded, a bill's payment, a repeating item, "expect this back")
 * is never suggested, and is also never allowed to take the place of another in a pair.
 */
object TransferCandidates {
    private val refundWords = Regex("""(?i)\b(?:refund(?:ed)?|reversal|reversed|revoked|cashback)\b""")

    /** The account types money can be taken out of by an ATM. */
    private val ATM_SOURCES = setOf(AccountType.BANK, AccountType.WALLET, AccountType.CREDIT_CARD)

    /** The account types a move between own accounts is looked for in: what the live pairing uses. */
    private val PAIR_ACCOUNTS = setOf(AccountType.BANK, AccountType.WALLET)

    /** The account types a self transfer may be from or to. */
    private val SELF_ACCOUNTS = setOf(AccountType.BANK, AccountType.WALLET, AccountType.CASH)

    /** What the finder reads of an entry's words: title, note and original text, separated so a phrase cannot run across them. */
    private fun words(e: CandidateEntry): String = listOfNotNull(e.title, e.note, e.originText).joinToString("\n")

    /**
     * [entries]: entries whose words may say something (the store narrows by a coarse text match; a precise reading is made here). [pairs]: expense and income of the same amount close in time on
     * different accounts, as (expense, income). [namedEnd]: the id of the person's own account that the original words of an entry name as the other end of the payment, or null. Entries are
     * never trusted beyond their own words. [otherHalfLive]: the other half of the move (the money-in of a withdrawal that was reversed, the credit of a self transfer on the other account) is already
     * in the ledger, so converting this entry alone would count the money twice: it is not offered. [absorbable]: the income of a pair can be deleted without leaving anything behind (it has no
     * attachment).
     */
    fun find(
        entries: List<CandidateEntry>, pairs: List<Pair<CandidateEntry, CandidateEntry>>, accounts: List<CandidateAccount>, dismissed: Set<String>, refused: (String) -> Boolean,
        namedEnd: (CandidateEntry) -> String? = { null },
        otherHalfLive: (CandidateEntry) -> Boolean = { false }, absorbable: (CandidateEntry) -> Boolean = { true },
    ): TransferCandidateList {
        val type = accounts.associate { it.id to it.type }
        val refusedCache = HashMap<String, Boolean>()
        fun cannot(id: String) = refusedCache.getOrPut(id) { refused(id) }

        // moves between own accounts first: an entry that has its other half is not also suggested alone
        val sides = HashMap<String, TransferSide>(); val byId = HashMap<String, CandidateEntry>()
        fun side(e: CandidateEntry): TransferSide? {
            if (e.id in sides) return sides[e.id]
            val own = e.accountId.takeIf { type[it] in PAIR_ACCOUNTS } ?: return null
            return TransferSide(e.id, e.outgoing, e.amountMinor, e.arrivedAt ?: e.at, own, namedEnd(e), TransferWording.says(words(e))).also { sides[e.id] = it; byId[e.id] = e }
        }
        val edges = ArrayList<Pair<CandidateEntry, CandidateEntry>>()
        for ((x, i) in pairs) {
            if (!x.outgoing || i.outgoing || x.id in dismissed || i.id in dismissed) continue
            // thin evidence is not enough to delete a real income: either half reading as an ATM withdrawal is not a move, and both halves must say transfer or both name the other account
            if (TransferWording.readsAsAtmWithdrawal(x.title, x.note, x.originText) || TransferWording.readsAsAtmWithdrawal(i.title, i.note, i.originText)) continue
            val both = TransferWording.says(words(x)) && TransferWording.says(words(i)) || namedEnd(x) == i.accountId && namedEnd(i) == x.accountId
            if (!both || !absorbable(i)) continue
            val a = side(x) ?: continue; val b = side(i) ?: continue
            if (TransferPairs.basis(a, b) == null) continue
            if (cannot(x.id) || cannot(i.id)) continue
            edges += x to i
        }
        val usable = edges.flatMap { listOf(it.first.id, it.second.id) }.toSet()
        val chosen = TransferPairs.pair(usable.map { sides.getValue(it) })
        val own = chosen.map { p ->
            val x = byId.getValue(p.out.id); val i = byId.getValue(p.into.id)
            val gap = kotlin.math.abs(p.out.at - p.into.at) / 60_000
            val why = when (p.basis) {
                PairBasis.NAMED_ACCOUNT -> "A text names the other account: the same amount left one and reached the other, $gap min apart"
                PairBasis.AMOUNT_AND_WORDS -> "The same amount left one account and reached the other $gap min apart, and the words say transfer"
            }
            TransferCandidate(CandidateGroup.OWN_ACCOUNTS, x, i, why)
        }.sortedWith(newestFirst)
        val paired = chosen.flatMapTo(HashSet()) { listOf(it.out.id, it.into.id) }

        val cash = ArrayList<TransferCandidate>(); val self = ArrayList<TransferCandidate>()
        for (e in entries) {
            if (e.id in dismissed || e.id in paired || e.amountMinor <= 0) continue
            val kind = type[e.accountId] ?: continue
            val text = words(e)
            if (refundWords.containsMatchIn(text)) continue
            when {
                e.outgoing && kind in ATM_SOURCES && TransferWording.readsAsAtmWithdrawal(e.title, e.note, e.originText, bareWithdrawnOk = kind != AccountType.WALLET) ->
                    if (!cannot(e.id) && !otherHalfLive(e)) cash += TransferCandidate(CandidateGroup.CASH_WITHDRAWALS, e, null, if (kind == AccountType.CREDIT_CARD) "Reads as a cash advance on a card" else "Reads as an ATM cash withdrawal")
                kind in SELF_ACCOUNTS && TransferWording.saysSelfTransfer(text) && !cannot(e.id) && !otherHalfLive(e) ->
                    self += TransferCandidate(CandidateGroup.SELF_TRANSFERS, e, null, "The words say it is " + if (e.outgoing) "to yourself" else "from yourself")
            }
        }
        return TransferCandidateList(cash.sortedWith(newestFirst), own, self.sortedWith(newestFirst))
    }

    private val newestFirst = compareByDescending<TransferCandidate> { it.entry.at }.thenBy { it.entry.id }
}
