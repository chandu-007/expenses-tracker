package app.expenses.core.domain

/**
 * One half of a possible move of money between two of the person's own accounts, as far as matching goes: a captured text still waiting in To review, or an entry
 * already added from an earlier text ([accepted]). The bank sends one text for each account, so a transfer shows up as money out of one and money into another.
 *
 * @property outgoing money left [accountId] (a debit); false is money in.
 * @property at when the text arrived, epoch milliseconds (an accepted entry: when its text arrived, else when it happened).
 * @property accountId the person's account this half belongs to, or null when the text's digits do not point at one of the person's accounts.
 * @property namesAccountId the person's account the text names as the other end ("XXXXXXXXXX1234-TPT-..." in a credit, "To A/c xxxxxxxxxx5678" in an IMPS debit), or null.
 * @property hinted the text says "transfer", "own account", "self", TPT, "FT-" or "sweep" ([TransferWording.says]; computed by the caller from the words of the text). The payment rails IMPS, NEFT and RTGS do not count.
 */
data class TransferSide(
    val id: String, val outgoing: Boolean, val amountMinor: Long, val at: Long, val accountId: String?, val namesAccountId: String? = null, val hinted: Boolean = false,
    val accepted: Boolean = false,
)

/** Why two halves were put together: [NAMED_ACCOUNT] when a text names the other account's digits, [AMOUNT_AND_WORDS] when only the amount, the time and "transfer" wording say so. */
enum class PairBasis { NAMED_ACCOUNT, AMOUNT_AND_WORDS }

data class TransferPair(val out: TransferSide, val into: TransferSide, val basis: PairBasis)

object TransferPairs {
    /** Banks send both texts within seconds to minutes: with only the amount and the wording to go by, half an hour is the most that is trusted. */
    const val WINDOW_MS = 30 * 60_000L

    /** When a text names the other account's digits, the pair is much more certain, so a delayed text (an offline phone, a slow bank) may still be matched within a day. */
    const val NAMED_WINDOW_MS = 24 * 3_600_000L

    /**
     * Whether [a] and [b] are the two halves of one transfer, and on what basis. They must go opposite ways for the same amount (exact minor units) on two *different*
     * accounts that are both known, and at most one of them may be an entry that is already in the ledger. With one text naming the other's account digits the window is a day;
     * otherwise some "transfer" wording on either half and half an hour.
     */
    fun basis(a: TransferSide, b: TransferSide): PairBasis? {
        if (a.outgoing == b.outgoing || a.amountMinor != b.amountMinor || a.amountMinor <= 0) return null
        if (a.accepted && b.accepted) return null
        val out = if (a.outgoing) a else b
        val into = if (a.outgoing) b else a
        val from = out.accountId ?: return null
        val to = into.accountId ?: return null
        if (from == to) return null
        val gap = kotlin.math.abs(out.at - into.at)
        if ((into.namesAccountId == from || out.namesAccountId == to) && gap <= NAMED_WINDOW_MS) return PairBasis.NAMED_ACCOUNT
        // a text that names some *other* account than the one the other half belongs to is not describing this transfer
        if (into.namesAccountId != null && into.namesAccountId != from) return null
        if (out.namesAccountId != null && out.namesAccountId != to) return null
        if ((out.hinted || into.hinted) && gap <= WINDOW_MS) return PairBasis.AMOUNT_AND_WORDS
        return null
    }

    /** Up to this many waiting halves on each side are matched optimally (a search over every assignment); more fall back to the greedy order. A person has a handful of transfers waiting at once. */
    private const val EXACT_LIMIT = 8

    /**
     * The halves put together, each used at most once, so that as many pairs as possible are made and, among those, the best ones: two pending texts are preferred to a pending text and an entry,
     * a named account to wording alone, and the closer in time to the farther. Two transfers of one amount at one time therefore pair off one for one even when the closest-in-time pairing would
     * leave two valid halves alone (O1 at 0 min, O2 at 10, I1 at 9, I2 at 40: O1 with I1 and O2 with I2, not O2 with I1). The answer is the same whatever order the halves are given in
     * (ties by id). [apart] holds the pairs the person said are separate, as ids joined by [key]; they are never put together again.
     */
    fun pair(sides: List<TransferSide>, apart: Set<String> = emptySet()): List<TransferPair> {
        val outs = sides.filter { it.outgoing }.sortedBy { it.id }
        val ins = sides.filter { !it.outgoing }.sortedBy { it.id }
        // cost[i][j]: null when the two are not a pair, else how good it is (lower is better): kind of pair first, then the time between them
        val cost = Array(outs.size) { i -> Array<Long?>(ins.size) { j ->
            val o = outs[i]; val n = ins[j]
            if (key(o.id, n.id) in apart) null
            else basis(o, n)?.let { b -> ((if (o.accepted || n.accepted) 1 else 0) * 2L + b.ordinal) * RANK + kotlin.math.abs(o.at - n.at) }
        } }
        val chosen: List<Pair<Int, Int>> = if (outs.size <= EXACT_LIMIT && ins.size <= EXACT_LIMIT) bestAssignment(cost, outs.size, ins.size) else greedy(cost, outs, ins)
        return chosen.map { (i, j) -> TransferPair(outs[i], ins[j], basis(outs[i], ins[j])!!) }
    }

    private const val RANK = 1_000_000_000_000L

    /** The assignment with the most pairs, then the least total cost, found by trying each outgoing half against every free incoming one (at most 8 and 8). */
    private fun bestAssignment(cost: Array<Array<Long?>>, n: Int, m: Int): List<Pair<Int, Int>> {
        class Best(val pairs: Int, val total: Long, val picks: List<Pair<Int, Int>>)
        val memo = HashMap<Long, Best>()
        fun search(i: Int, used: Int): Best {
            if (i == n) return Best(0, 0, emptyList())
            val k = i.toLong() shl 16 or used.toLong()
            memo[k]?.let { return it }
            var best = search(i + 1, used) // this outgoing half stays alone
            for (j in 0 until m) {
                val c = cost[i][j] ?: continue
                if (used and (1 shl j) != 0) continue
                val rest = search(i + 1, used or (1 shl j))
                val candidate = Best(rest.pairs + 1, rest.total + c, listOf(i to j) + rest.picks)
                if (candidate.pairs > best.pairs || (candidate.pairs == best.pairs && candidate.total < best.total)) best = candidate
            }
            memo[k] = best
            return best
        }
        return search(0, 0).picks
    }

    private fun greedy(cost: Array<Array<Long?>>, outs: List<TransferSide>, ins: List<TransferSide>): List<Pair<Int, Int>> {
        val all = ArrayList<Triple<Int, Int, Long>>()
        for (i in outs.indices) for (j in ins.indices) cost[i][j]?.let { all += Triple(i, j, it) }
        all.sortWith(compareBy({ it.third }, { outs[it.first].id }, { ins[it.second].id }))
        val usedOut = HashSet<Int>(); val usedIn = HashSet<Int>(); val out = ArrayList<Pair<Int, Int>>()
        for ((i, j, _) in all) if (usedOut.add(i)) { if (usedIn.add(j)) out += i to j else usedOut.remove(i) }
        return out
    }

    /** The key under which a "These are separate" answer is remembered: the two ids in a fixed order. */
    fun key(a: String, b: String): String = if (a <= b) "$a|$b" else "$b|$a"

    /**
     * What to say on an item whose other half has not arrived (yet): which of the person's accounts it might be a transfer to or from, or null. Only when the text names the
     * digits of another account of the person's ([namesAccountId]), or its wording says "transfer" ([hinted]) and the person has exactly one other account it could be
     * ([others]: the person's other accounts that money can be moved to or from, id to name; the account the text is about is not in it).
     */
    fun mightBeTransferTo(namesAccountId: String?, hinted: Boolean, others: Map<String, String>): String? {
        if (namesAccountId != null) return others[namesAccountId]
        return if (hinted && others.size == 1) others.values.single() else null
    }
}
