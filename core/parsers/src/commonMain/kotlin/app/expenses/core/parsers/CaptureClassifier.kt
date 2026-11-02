package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Why a text was not taken as a transaction. Each one is counted by capture health, never stored with its body. */
enum class IgnoreReason { PERSONAL_SENDER, NOT_ALLOWLISTED, OTP, NOT_A_TRANSACTION, DECLINED }

sealed interface CaptureOutcome {
    data class Ignored(val reason: IgnoreReason, val headerRoot: String?) : CaptureOutcome

    /** A transaction alert from an allowlisted sender. [issuer] is the display name, [headerRoot] what matched. */
    data class Transaction(val issuer: String, val headerRoot: String, val alert: ParsedAlert) : CaptureOutcome

    /** Something to pay (a card statement, an electricity bill, an EMI falling due), not a payment that happened. [issuer] is who sent it. */
    data class Bill(val issuer: String, val headerRoot: String, val notice: BillNotice) : CaptureOutcome
}

/**
 * Decides, from the sender and the body alone, whether a text becomes a pending transaction. The order matters and is part of the privacy
 * promise: the sender is checked **before the body is read**, so a text from a person or an unknown header is never parsed at all.
 */
object CaptureClassifier {
    /** [today] resolves a due date written without a year; it is the day the text arrived. */
    fun classify(sender: String, body: String, extraRoots: Set<String> = emptySet(), today: LocalDate = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date): CaptureOutcome {
        val root = SenderRules.headerRoot(sender) ?: return CaptureOutcome.Ignored(IgnoreReason.PERSONAL_SENDER, null)
        val issuer = SenderRules.BUILT_IN[root] ?: if (root in extraRoots.map { it.uppercase() }) root else return CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, root)
        val parsed = GenericAlertParser.parse(body, root)
        // THE RULE. A text that asks for money (BillParser: due / payable / pay by / bill generated / statement ready / EMI or premium falling due, with an amount,
        // and not worded as a receipt) is a bill, UNLESS it also states that money actually moved ([MoneyMoved]): a past-tense movement word (debited, credited,
        // spent, withdrawn, deducted, refunded, transferred, paid) outside a promise ("will be debited") or an "ignore if already paid" line, or a UPI reference
        // number, or HDFC's "Txn ... Card ... At" layout. Then the debit or credit wins, because losing a real payment is worse than showing a bill as a transaction
        // ("Rs.500 debited ... Your bill of Rs.1200 is due on 25-10" stays a transaction). Receipts ("payment of Rs X received", "thank you for your payment") are never
        // bills (BillParser), so they keep going to the transaction rules. A text with no ask is judged by the transaction rules alone.
        // What went wrong before 0.7.1: any "paid" made GenericAlertParser read the text as a debit (Jio's "Kindly ignore, if already paid"), and a parsed alert skipped the
        // bill rules altogether, so a bill reminder became a debit in To review.
        val bill = BillParser.parse(body, root, today)
        if (bill != null && !(parsed is ParseResult.Parsed && MoneyMoved.stated(body))) return CaptureOutcome.Bill(issuer, root, bill)
        return when (val r = parsed) {
            is ParseResult.NotATransaction -> CaptureOutcome.Ignored(if (r.reason == "otp") IgnoreReason.OTP else IgnoreReason.NOT_A_TRANSACTION, root)
            is ParseResult.Parsed -> if (r.alert.direction == Direction.DECLINED) CaptureOutcome.Ignored(IgnoreReason.DECLINED, root) else CaptureOutcome.Transaction(issuer, root, r.alert)
        }
    }

    /**
     * The email counterpart of [classify]. An email app's notification has no SMS header, so the bank is found from the email's sender name(s) ([senderNames], tried in
     * order: the display name first) with [SenderRules.emailIssuer], and only then is the body read, by the very same rules as a text from that bank's header: a
     * transaction, a bill, an OTP or neither. An email from nobody on the list is [IgnoreReason.NOT_ALLOWLISTED] with no root, and its words are never parsed.
     */
    fun classifyEmail(senderNames: List<String>, body: String, extraRoots: Set<String> = emptySet(), today: LocalDate = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date): CaptureOutcome {
        val root = senderNames.firstNotNullOfOrNull { SenderRules.emailIssuer(it, extraRoots) } ?: return CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, null)
        return classify(root, withoutSafetyNotice(body), extraRoots, today)
    }

    /** "Never share your OTP, password or card details with anyone" closes many bank emails; read as it stands, the word OTP would make a real alert look like a one-time password. */
    private val safetyNotice = Regex("""(?i)\b(?:do\s*n[o']?t|don't|never|not\s+to)\s+share\b[^.!?\n]*[.!?]?|\bnever\s+asks?\s+(?:you\s+)?for\b[^.!?\n]*[.!?]?""")

    /** An email's words with those safety sentences taken out; the words kept on the pending item are the original ones. */
    internal fun withoutSafetyNotice(body: String): String = safetyNotice.replace(body, " ")
}

/** Whether a text says that money moved, as opposed to asking for it. Used by [CaptureClassifier] to let a real debit or credit win over a bill's wording. */
internal object MoneyMoved {
    /** A promise or a plan, not an event: "will be debited", "to be auto-debited", "scheduled debit". */
    private val promise = Regex("""(?i)\b(?:will|shall|would|to)\s+be\s+(?:auto[- ]?)?(?:debited|deducted|charged|paid|credited|transferred)\b|\bscheduled\s+(?:to\s+be\s+)?debit\w*""")
    /** "sent" is not here: "your bill has been sent to your email" is a bill; a sent payment carries a UPI reference or a bank's wording. */
    private val words = Regex("""(?i)\b(?:debited|credited|spent|withdrawn|deducted|refunded|reversed|transferred|paid|purchase)\b""")
    private val upiRef = Regex("""(?i)\bupi\s*(?:ref(?:erence)?\.?\s*(?:no\.?|number)?)?\s*[:\-]?\s*\d{9,18}\b|upi/[a-z0-9]+/\d{9,18}""")
    private val cardTxn = Regex("""(?is)^\s*txn\b.*\bcard\b.*\bat\b""")

    fun stated(body: String): Boolean {
        val text = PaidDisclaimer.strip(body.replace(Regex("\\s+"), " ").trim())
        return words.containsMatchIn(promise.replace(text, " ")) || upiRef.containsMatchIn(text) || cardTxn.containsMatchIn(text)
    }
}
