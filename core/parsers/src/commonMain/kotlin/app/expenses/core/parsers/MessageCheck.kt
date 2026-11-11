package app.expenses.core.parsers

import kotlinx.datetime.LocalDate

/**
 * "Check a message": how the app WOULD read one text the person pastes or shares into the checkup screen, without keeping anything. It is a pure function of the words, the optional sender name and the
 * sender names the person added: it calls the same [PasteRules.classify] that real intake uses (so the answer cannot differ from what would really happen), and it has no way to save, count or send:
 * there is no store, no clock and no callback anywhere in it. The words are only ever read from what the person pasted; nothing here looks at the phone's messages.
 *
 * What it returns keeps the account digits and the payee as the parser found them, because the caller needs them to find the person's account and category; the person is shown only
 * [maskedDigits] and never the reference or any other number of the text.
 */
object MessageCheck {
    enum class Verdict {
        /** It would go to To review as a payment or a credit. */
        TRANSACTION,

        /** It would go to Bills. */
        BILL,

        /** It would be ignored; [Reading.ignoreReason] says why. */
        IGNORED,
    }

    /** What the sender name the person typed (or did not type) stands for. */
    enum class SenderStatus {
        /** No sender typed: a pasted text carries none, so its words have to name a bank ([PasteRules]). */
        NOT_GIVEN,

        /** On the built-in list of banks, cards and billers. */
        ON_LIST,

        /** Added by the person under Bank sender names. */
        ADDED_BY_YOU,

        /** A name of letters (a header) that is neither: its texts are skipped unread. */
        NOT_ON_LIST,

        /** A phone number or something that is not a registered header: a person, never read. */
        NOT_A_HEADER,
    }

    /** Everything the app would take from the text. Fields that do not apply are null. */
    data class Reading(
        val verdict: Verdict,
        val ignoreReason: IgnoreReason?,
        val sender: SenderStatus,
        /** The header root of the typed sender ("HDFCBK" for "AX-HDFCBK-S"), or null. */
        val senderRoot: String?,
        /** The bank the app took the text to be from (its display name), or null when none. */
        val issuer: String?,
        /** True when the bank was found from the words, because no sender was typed. */
        val bankFromWords: Boolean,
        /** True: money out; false: money in; null: not a payment. */
        val moneyOut: Boolean?,
        val amountMinor: Long?,
        val currency: String?,
        /** The payee as the text names it, raw (an address, a shop); the caller cleans it. */
        val rawPayee: String?,
        /** The digits the text prints for its own account or card (three or four), or null. */
        val accountDigits: String?,
        val hasReference: Boolean,
        val date: LocalDate?,
        /** Read by a rule written for this bank's wording, not the generic reader: more certain. */
        val bankRule: Boolean,
        /** A confirmation that a bill was paid (read as a payment, not a bill). */
        val billPayment: Boolean,
        val billKind: BillKind?,
        val billBiller: String?,
        val billDue: LocalDate?,
        /** For a skipped sender name: what the text would have been read as if the person added the name. Null otherwise. */
        val ifAdded: Reading? = null,
    )

    /**
     * Reads [body] as the app would. [sender] is the name the text came from as the person typed it, or null/blank for none. [extraRoots] are the sender names the person added;
     * [today] gives a due date written without a year its year.
     */
    fun read(sender: String?, body: String, extraRoots: Set<String>, today: LocalDate): Reading {
        val named = sender?.trim().orEmpty()
        val root = if (named.isEmpty()) null else SenderRules.headerRoot(named)
        val extra = extraRoots.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        val status = when {
            named.isEmpty() -> SenderStatus.NOT_GIVEN
            root == null -> SenderStatus.NOT_A_HEADER
            root in SenderRules.BUILT_IN -> SenderStatus.ON_LIST
            root in extra -> SenderStatus.ADDED_BY_YOU
            else -> SenderStatus.NOT_ON_LIST
        }
        val outcome = PasteRules.classify(named, body, extra, today) ?: CaptureOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION, null)
        val base = of(outcome, status, root, body, fromWords = named.isEmpty() && outcome !is CaptureOutcome.Ignored)
        // a sender that is not on any list is skipped without its words being read; what it WOULD be if it were trusted is worked out apart, for the offer to add it
        if (status == SenderStatus.NOT_ON_LIST && root != null) {
            val wouldBe = PasteRules.classify(named, body, extra + root, today)
            if (wouldBe != null && wouldBe !is CaptureOutcome.Ignored) return base.copy(ifAdded = of(wouldBe, SenderStatus.ADDED_BY_YOU, root, body, fromWords = false))
        }
        return base
    }

    private fun of(o: CaptureOutcome, status: SenderStatus, root: String?, body: String, fromWords: Boolean): Reading = when (o) {
        is CaptureOutcome.Ignored -> Reading(
            Verdict.IGNORED, o.reason, status, root, null, false, null, null, null, null, null, false, null, false, false, null, null, null,
        )
        is CaptureOutcome.Transaction -> {
            val a = o.alert
            Reading(
                Verdict.TRANSACTION, null, status, root, o.issuer.takeIf { it != PasteRules.UNKNOWN_ISSUER }, fromWords, a.direction == Direction.DEBIT || a.direction == Direction.DECLINED,
                a.amountMinor, a.currency, PayeeGuess.of(body), a.accountLast4, !a.reference.isNullOrBlank(), a.date, a.confidence == Confidence.BANK_RULE,
                BillPayments.read(body) != null, null, null, null,
            )
        }
        is CaptureOutcome.Bill -> {
            val n = o.notice
            Reading(
                Verdict.BILL, null, status, root, o.issuer.takeIf { it != PasteRules.UNKNOWN_ISSUER }, fromWords, null, n.amountMinor, n.currency, null, n.accountLast4, !n.reference.isNullOrBlank(),
                null, false, false, n.kind, n.biller, n.dueDate,
            )
        }
    }

    /** The digits a text prints for an account, as the screen shows them: all but the last two hidden ("••34" for 1234 and "•34" for 034). Null for none. */
    fun maskedDigits(digits: String?): String? {
        val d = digits?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: return null
        return if (d.length <= 2) "•".repeat(d.length) else "•".repeat(d.length - 2) + d.takeLast(2)
    }

    /** A plain sentence for why a text would be ignored. */
    fun ignoredBecause(reason: IgnoreReason, sender: SenderStatus): String = when (reason) {
        IgnoreReason.PERSONAL_SENDER -> "The sender looks like a phone number or a person, so the app would ignore it. Texts from people are never read."
        IgnoreReason.NOT_ALLOWLISTED -> "The sender name is not on the list of banks, so the app would skip this text without reading it."
        IgnoreReason.OTP -> "It looks like a one-time password, so the app would ignore it."
        IgnoreReason.DECLINED -> "It says a payment was declined or failed, so nothing was spent and the app would ignore it."
        IgnoreReason.NOT_A_TRANSACTION ->
            if (sender == SenderStatus.NOT_GIVEN) "With no sender name, the words have to name a bank or look like a bank alert (an amount, money moving and an account or card). These do not, so the app would ignore it."
            else "It does not say that money moved (or is due), so the app would ignore it."
    }
}
