package app.expenses

import app.expenses.core.domain.AccountLooks
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.TransferPair
import app.expenses.core.domain.TransferPairs
import app.expenses.core.domain.TransferSide
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.parsers.GenericAlertParser
import app.expenses.core.parsers.ParseResult
import app.expenses.core.parsers.TransferClues
import app.expenses.core.storage.CaptureAccounts
import app.expenses.core.storage.EarlierEntry
import app.expenses.core.storage.InboxItem

/**
 * Finds, among the texts waiting in To review, the ones that are the two halves of one move of money between the person's own accounts, the ones whose other half the person already added as
 * an expense or income, and the lone ones that might be a transfer. It reads only what is already on the device and records nothing: adding anything is the person's tap
 * ([LedgerViewModel.confirmTransferPair], [LedgerViewModel.makeTransfer]).
 *
 * Both accounts of a pair have to be known from the digits the texts carry: the account that has those last four digits set (More > Accounts), else the one remembered for that bank and
 * digits from an earlier Add. A person with fewer than two accounts to move money between (a bank account or wallet; a card has its own card-payment handling) gets nothing, and costs nothing.
 */
class TransferScan private constructor(
    val accountNames: Map<String, String>,
    /** Pairs of two waiting texts. */
    val pairs: List<TransferPair>,
    /** For a waiting text whose other half was already added: the pair (the entry's side is the accepted one) and that entry. */
    val offers: Map<String, Pair<TransferPair, EarlierEntry>>,
    /** Every other eligible waiting text. */
    val lone: Map<String, Lone>,
    /** Whether some account to move money between has no digits set, so telling the person to set them would help. */
    val digitsWouldHelp: Boolean,
) {
    /** A waiting text with no counterpart: [side] as read, [counter] whether it names an account that is not the person's, and [mightBeTo] the name of the account it might be a transfer to or from. */
    class Lone(val side: TransferSide, val namesOther: Boolean, val mightBeTo: String?)

    companion object {
        private const val APART_PREFIX = "pair_apart:"
        /** How far around a waiting text the entries it might pair with are looked for; the pairing's own windows decide, from the time each text arrived. */
        const val EARLIER_QUERY_MS = 36 * 3_600_000L
        private const val MAX_LOOKUPS = 60

        fun apartKey(outId: String, intoId: String) = APART_PREFIX + TransferPairs.key(outId, intoId)

        /** [eligible]: the ids of the waiting items that may take part (plain payments that are not flagged as repeats). [accounts]: the person's live accounts. Null when there is nothing to look for. */
        fun of(ok: AppContainer.Opened.Ok, items: List<InboxItem>, eligible: Set<String>, accounts: List<app.expenses.core.storage.db.Account>): TransferScan? {
            val movable = accounts.filter { it.type == "BANK" || it.type == "WALLET" }
            val base = BaseCurrency.current.code
            if (movable.size < 2 || eligible.isEmpty()) return null
            val names = movable.associate { it.id to it.name }
            val looks = ok.looks.all().filter { it.last4 != null }
            val looksByDigits = looks.groupBy { it.last4!! }
            val typeOf = accounts.associate { it.id to AccountType.valueOf(it.type) }
            val cache = HashMap<String, String?>()
            /** The movable account these digits (as one bank prints them) stand for: the account carrying them, else the one remembered for this bank and digits. */
            fun accountFor(issuer: String, digits: String?): String? {
                if (digits == null) return null
                return cache.getOrPut("$issuer|$digits") {
                    (AccountLooks.pick(looksByDigits[digits].orEmpty().mapNotNull { l -> typeOf[l.accountId]?.let { l.accountId to it } }, preferCard = false) ?: ok.templates.setting(CaptureAccounts.key(issuer, digits)))?.takeIf { it in names }
                }
            }
            val byId = items.associateBy { it.id }
            val sides = LinkedHashMap<String, TransferSide>()
            val namesOther = HashMap<String, Boolean>()
            for (i in items) {
                if (i.id !in eligible || i.currency != base) continue
                val body = i.body.orEmpty()
                val counter = TransferClues.counterLast4(body, i.accountLast4)
                val own = accountFor(i.issuer, i.accountLast4)
                val names2 = accountFor(i.issuer, counter)?.takeIf { it != own }
                namesOther[i.id] = counter != null && names2 == null
                sides[i.id] = TransferSide(i.id, i.direction == "DEBIT", i.amountMinor, i.receivedAt, own, names2, TransferClues.hasTransferWords(body))
            }
            if (sides.isEmpty()) return null
            val apart = ok.templates.settingsWithPrefix(APART_PREFIX).mapTo(HashSet()) { it.first.removePrefix(APART_PREFIX) }
            val together = TransferPairs.pair(sides.values.toList(), apart)
            val paired = together.flatMapTo(HashSet()) { listOf(it.out.id, it.into.id) }

            // what is left may be the other half of something already added from an earlier text
            val earlierOf = HashMap<String, EarlierEntry>()
            val entrySides = LinkedHashMap<String, TransferSide>()
            val leftovers = sides.values.filter { it.id !in paired }
            // one indexed lookup for each of the newest waiting texts whose account is known, however long the backlog
            for (s in leftovers.filter { it.accountId != null }.take(MAX_LOOKUPS)) {
                val item = byId.getValue(s.id)
                for (e in ok.transfers.earlier(item.amountMinor, item.currency, item.receivedAt, EARLIER_QUERY_MS)) {
                    val key = "entry:${e.entryId}"
                    if (key in entrySides || e.accountId !in names) continue
                    earlierOf[key] = e
                    val parsed = e.originText?.let { t -> (GenericAlertParser.parse(t) as? ParseResult.Parsed)?.alert }
                    val own = accountFor(item.issuer, parsed?.accountLast4)
                    entrySides[key] = TransferSide(key, e.outgoing, item.amountMinor, e.receivedAt ?: e.occurredAt, e.accountId, accountFor(item.issuer, parsed?.counterLast4)?.takeIf { it != e.accountId && it != own }, e.originText?.let(TransferClues::hasTransferWords) == true, accepted = true)
                }
            }
            val offers = HashMap<String, Pair<TransferPair, EarlierEntry>>()
            if (entrySides.isNotEmpty()) for (p in TransferPairs.pair(leftovers + entrySides.values, apart).filter { it.out.accepted || it.into.accepted }) {
                val pending = if (p.out.accepted) p.into else p.out
                offers[pending.id] = p to earlierOf.getValue(if (p.out.accepted) p.out.id else p.into.id)
            }
            val lone = LinkedHashMap<String, Lone>()
            for (s in sides.values) {
                if (s.id in paired || s.id in offers) continue
                val others = s.accountId?.let { names - it } ?: emptyMap()
                lone[s.id] = Lone(s, namesOther[s.id] == true, TransferPairs.mightBeTransferTo(s.namesAccountId, s.hinted, others))
            }
            return TransferScan(names, together, offers, lone, movable.any { a -> looks.none { it.accountId == a.id } })
        }
    }
}
