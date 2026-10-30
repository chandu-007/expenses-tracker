package app.expenses.core.parsers

/**
 * Which way money went in the original words an entry keeps ("Where this came from"), read again with the capture rules. Used to tell a text that tells the same transaction again from a
 * refund or reversal of it when the entry's type has since been changed by the person ([app.expenses.core.domain.ReferenceReuse]). The words are read here, on the device, and nothing is stored.
 */
object OriginDirection {
    /**
     * True when the words say money went out, false when it came in, null when they cannot be read (no amount or direction, a declined payment, a bill, an OTP).
     * [sender] is the name the words came from ("AX-HDFCBK-S", "GPAY"); the bank's own rules are applied when it names one. An email ([email]) is read as the capture reads one: without the
     * safety notices that close many bank emails, and with no bank header (the bank is found from the sender's name, which is not kept).
     */
    fun outgoing(sender: String?, text: String, email: Boolean): Boolean? {
        val root = if (email) null else sender?.let(SenderRules::headerRoot)
        val body = if (email) CaptureClassifier.withoutSafetyNotice(text) else text
        val alert = (GenericAlertParser.parse(body, root) as? ParseResult.Parsed)?.alert ?: return null
        return when (alert.direction) {
            Direction.DEBIT -> true
            Direction.CREDIT -> false
            Direction.DECLINED -> null
        }
    }
}
