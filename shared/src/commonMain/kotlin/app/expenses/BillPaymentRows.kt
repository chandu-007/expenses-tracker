package app.expenses

import app.expenses.core.domain.AccountMatch
import app.expenses.core.domain.BankNames
import app.expenses.core.domain.BillPaymentMatch
import app.expenses.core.domain.OpenBill
import app.expenses.core.parsers.BillKind
import app.expenses.core.parsers.BillPaymentNotice
import app.expenses.core.parsers.BillPaymentSide
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.InboxItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * What a card in To review says about a bill payment text ("Axis Bank credit card bill payment of Rs 12,345.67 was successful"): which open bill it pays, which account the money left, which card
 * account it went to, and whether the same payment is already in the ledger. Reads the ledger only, records nothing: marking the bill paid is the person's tap ([LedgerViewModel.confirmBillPayment]).
 *
 * The paying bank is the one that SENT the text (its account is the text's own account, found like any waiting text's: the digits, what the person chose before, or the question "Which account is
 * this?"); the card is the one the WORDS name, which may be another bank's. A card's own text ("Payment of Rs X received towards your card") is the other leg of the same payment: the text's own
 * account is the card, and the account the money left is asked.
 */
internal object BillPaymentRows {
    private val tail = Regex("""(?i)\s+(?:bank|card|credit card)$""")

    /** "Axis Bank" is "Axis", "SBI Card" is "SBI": how a card's bank is said in "Mark the Axis card bill as paid?". */
    fun shortName(bank: String): String = bank.replace(tail, "").replace(tail, "").trim().ifEmpty { bank }

    /** The question the card asks. */
    fun question(n: BillPaymentNotice): String =
        if (n.kind == BillKind.CREDIT_CARD && n.cardBank != null) "Mark the ${shortName(n.cardBank!!)} card bill as paid?" else "Mark this bill as paid?"

    /** The words an entry made from the payment is filed under when no bill is marked. */
    fun entryTitle(n: BillPaymentNotice, bill: app.expenses.core.storage.Bill?): String = bill?.title ?: when {
        n.kind == BillKind.CREDIT_CARD -> (n.cardBank?.let { shortName(it) + " " } ?: "") + "card bill payment"
        else -> (n.biller?.let { "$it " } ?: "") + n.kind.label.let { if (it == BillKind.OTHER.label) "bill" else it } + " payment"
    }

    /** The account of the card the payment went to: the one carrying the digits, the text's own account when the card sent it, the one card of that bank, or the only card. Empty when the app tracks none for it. */
    private fun cardAccount(i: InboxItem, n: BillPaymentNotice, cards: List<app.expenses.core.storage.db.Account>, ownAccount: app.expenses.core.storage.db.Account?, resolver: AccountResolver): String {
        if (cards.isEmpty()) return ""
        if (n.cardLast4 != null) resolver.accountFor(n.cardBank ?: i.issuer, n.cardLast4, preferCard = true, bankKnown = n.cardBank != null)?.takeIf { id -> cards.any { it.id == id } }?.let { return it }
        if (n.side == BillPaymentSide.RECEIVED && ownAccount?.type == "CREDIT_CARD") return ownAccount.id
        if (n.cardBank != null) cards.filter { AccountResolver.banks.sameBank(n.cardBank, it.name) == true }.singleOrNull()?.let { return it.id }
        return if (n.cardBank == null && n.cardLast4 == null && cards.size == 1) cards.single().id else ""
    }

    /**
     * Everything the card shows. [accounts] are the live accounts; [ownMatch] what the text's own account resolves to and [goesTo] the account the card starts with; [money] and [day] format an amount and a date the way the rest of the screen does.
     */
    fun of(
        ok: AppContainer.Opened.Ok, i: InboxItem, n: BillPaymentNotice, accounts: List<app.expenses.core.storage.db.Account>, resolver: AccountResolver, ownMatch: AccountMatch.Result,
        goesTo: String, zone: TimeZone, today: LocalDate, money: (Long) -> String, day: (LocalDate) -> String,
    ): BillPayUi {
        val received = n.side == BillPaymentSide.RECEIVED
        val cards = accounts.filter { it.type == "CREDIT_CARD" }
        val movable = accounts.filter { it.type != "CREDIT_CARD" && it.type != "LOAN" && it.currency == i.currency }
        val ownId = (ownMatch as? AccountMatch.Result.Found)?.accountId
        val ownAccount = accounts.firstOrNull { it.id == ownId }
        val cardId = cardAccount(i, n, cards, ownAccount, resolver)
        val paidOn = i.txnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateOfMillis(i.receivedAt, zone)

        // the account the money left: for the bank's own text the text's account (its digits, what the person chose before, or the account the card starts with); for the card's text the only bank account there is
        val several = (ownMatch as? AccountMatch.Result.Several)?.accountIds.orEmpty().filter { id -> movable.any { it.id == id } }
        val payFrom = when {
            !received -> goesTo.takeIf { id -> movable.any { it.id == id } }.orEmpty()
            movable.size == 1 -> movable.single().id
            else -> ""
        }
        val payChoices = if (!received && several.isNotEmpty()) several else movable.map { it.id }

        // the open bills this may pay
        fun parse(d: String?) = d?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val open = ArrayList<OpenBill>()
        for (b in ok.bills.list(BillStatus.FOUND, 100) + ok.bills.list(BillStatus.TRACKED, 100)) {
            val label = BillKind.entries.firstOrNull { it.name == b.kind }?.label
            val biller = if (b.kind == BillKind.CREDIT_CARD.name || label == null) null else b.title.removeSuffix(" $label").takeIf { it != b.title && it.isNotBlank() }
            open += OpenBill(b.id, b.kind, b.issuer, b.accountLast4, biller, b.title, b.amountMinor, b.minDueMinor, b.currency, parse(b.dueDate), dateOfMillis(b.receivedAt, zone))
        }
        // the card's digits: the ones the words print, else those of the card account they resolved to (so a bill of another card of the same bank is not offered)
        val cardDigits = n.cardLast4 ?: cardId.takeIf { it.isNotEmpty() }?.let { id -> ok.looks.all().firstOrNull { it.accountId == id }?.last4 }
        // a card whose statement is worked out from its billing dates (no text behind it) is a bill too, unless a tracked bill from a text already stands for it
        if (n.kind == BillKind.CREDIT_CARD && cardId.isNotEmpty()) {
            val account = accounts.firstOrNull { it.id == cardId }
            val st = if (account != null && ok.cards.terms(cardId) != null) ok.cards.standing(cardId, today, zone)?.takeIf { it.remaining > 0 } else null
            if (account != null && st != null && open.none { o -> o.kind == BillKind.CREDIT_CARD.name && o.dueDate != null && kotlin.math.abs(o.dueDate!!.toEpochDays() - st.cycle.dueDate.toEpochDays()) <= 2 }) {
                val id = "card:$cardId"
                open += OpenBill(id, BillKind.CREDIT_CARD.name, n.cardBank ?: account.name, cardDigits, null, account.name + " statement", st.remaining, st.minimumDue.takeIf { it > 0 && it < st.remaining }, i.currency, st.cycle.dueDate, st.cycle.dueDate.minusDays(30))
            }
        }
        val hits = BillPaymentMatch.candidates(n.kind.name, n.cardBank, cardDigits, n.biller, i.amountMinor, i.currency, paidOn, open)
        val matches = hits.take(3).map { h ->
            BillMatchUi(
                h.bill.id, h.bill.title, money(h.bill.amountMinor), h.bill.dueDate?.let { "due ${day(it)}" } ?: "no due date",
                when (h.fit) { BillPaymentMatch.Fit.TOTAL -> "total"; BillPaymentMatch.Fit.MINIMUM -> "minimum"; BillPaymentMatch.Fit.PART -> "part"; BillPaymentMatch.Fit.FULL -> "full" },
            )
        }

        // the same payment, already in the ledger: an entry of this amount near the day (a payment the person recorded, a transfer to the card, the bank's text before the card's), or a bill already marked paid for it
        val from = paidOn.minusDays(3).startOfDayMillis(zone); val until = paidOn.plusDays(4).startOfDayMillis(zone)
        val at = i.receivedAt
        val entry = ok.db.schemaQueries.entriesNearBillPayment(i.amountMinor, i.currency, from, until).executeAsList()
            .sortedWith(compareBy({ if (cardId.isNotEmpty() && it.counter_account_id == cardId) 0 else 1 }, { kotlin.math.abs(it.occurred_at - at) }, { it.id })).firstOrNull()
        val paidBill = ok.bills.list(BillStatus.PAID, 20).firstOrNull { b ->
            b.currency == i.currency && b.paidAt != null && kotlin.math.abs(b.paidAt!! - at) <= 4 * 86_400_000L &&
                BillPaymentMatch.candidates(n.kind.name, n.cardBank, cardDigits, n.biller, i.amountMinor, i.currency, dateOfMillis(b.paidAt!!, zone), listOf(OpenBill(b.id, b.kind, b.issuer, b.accountLast4, null, b.title, b.amountMinor, b.minDueMinor, b.currency, parse(b.dueDate), dateOfMillis(b.receivedAt, zone)))).isNotEmpty()
        }
        val sameEntry = entry?.id ?: paidBill?.entryId.orEmpty()
        val sameNote = when {
            paidBill != null -> "You already marked ${paidBill.title} as paid on ${day(dateOfMillis(paidBill.paidAt!!, zone))}: this text is that payment."
            entry != null -> "Already in your ledger: ${entry.merchant ?: "an entry"} on ${day(dateOfMillis(entry.occurred_at, zone))}. This text looks like that payment."
            else -> ""
        }
        return BillPayUi(
            question(n), "Bill payment", matches, matches.firstOrNull()?.billId.orEmpty(), payFrom, payChoices, cardId, cards.map { it.id },
            sameEntryId = sameEntry, sameNote = sameNote, title = entryTitle(n, null), received = received, isCard = n.kind == BillKind.CREDIT_CARD,
        )
    }
}
