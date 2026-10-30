package app.expenses.core.parsers

import app.expenses.core.domain.TransferWording

/**
 * What a transaction text is about, beyond money in or out. It decides whether the ledger should record an expense or income, or move money
 * between the person's own accounts, or reverse an earlier payment (FR-LED-03). Read from the words only, and only while a text is still
 * waiting for review.
 */
enum class MessageKind {
    PAYMENT,

    /** A payment made to a credit card (or the card's receipt of it): money moving between the person's own accounts. */
    CARD_PAYMENT,

    /** Cash taken out at an ATM: money moving from the account into the person's cash. */
    ATM_WITHDRAWAL,

    /** Money back for an earlier payment: a refund, or a payment reversed after it failed. */
    REFUND,
}

object KindRules {
    private val cardPayment = Regex(
        """(?i)\b(?:re)?payment\s+of\b[^\n]{0,70}\b(?:received|towards)\b[^\n]{0,70}\bcard\b|\brepayment\b[^\n]{0,70}\bcredit\s+card\b|\bcredit\s+card\s+bill\b|\bcard\s+bill\b[^\n]{0,40}\bpaid\b|\btowards\s+(?:your\s+)?[\w\s]{0,30}credit\s+card\b|\bcc\s+(?:bill|payment)\b""",
    )
    private val refundWords = Regex("""(?i)\brefund(?:ed)?\b|\breversal\b|\breversed\b|\brevoked\b""")

    fun of(body: String, direction: Direction): MessageKind {
        val text = body.replace(Regex("\\s+"), " ")
        return when {
            direction == Direction.CREDIT && refundWords.containsMatchIn(text) -> MessageKind.REFUND
            cardPayment.containsMatchIn(text) -> MessageKind.CARD_PAYMENT
            direction == Direction.DEBIT && TransferWording.saysAtmWithdrawal(text) && !refundWords.containsMatchIn(text) -> MessageKind.ATM_WITHDRAWAL
            else -> MessageKind.PAYMENT
        }
    }
}
