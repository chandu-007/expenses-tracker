package app.expenses.core.parsers

/** Why a text was not taken as a transaction. Each one is counted by capture health, never stored with its body. */
enum class IgnoreReason { PERSONAL_SENDER, NOT_ALLOWLISTED, OTP, NOT_A_TRANSACTION, DECLINED }

sealed interface CaptureOutcome {
    data class Ignored(val reason: IgnoreReason, val headerRoot: String?) : CaptureOutcome

    /** A transaction alert from an allowlisted sender. [issuer] is the display name, [headerRoot] what matched. */
    data class Transaction(val issuer: String, val headerRoot: String, val alert: ParsedAlert) : CaptureOutcome
}

/**
 * Decides, from the sender and the body alone, whether a text becomes a pending transaction. The order matters and is part of the privacy
 * promise: the sender is checked **before the body is read**, so a text from a person or an unknown header is never parsed at all.
 */
object CaptureClassifier {
    fun classify(sender: String, body: String, extraRoots: Set<String> = emptySet()): CaptureOutcome {
        val root = SenderRules.headerRoot(sender) ?: return CaptureOutcome.Ignored(IgnoreReason.PERSONAL_SENDER, null)
        val issuer = SenderRules.BUILT_IN[root] ?: if (root in extraRoots.map { it.uppercase() }) root else return CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, root)
        return when (val r = GenericAlertParser.parse(body, root)) {
            is ParseResult.NotATransaction -> CaptureOutcome.Ignored(if (r.reason == "otp") IgnoreReason.OTP else IgnoreReason.NOT_A_TRANSACTION, root)
            is ParseResult.Parsed -> if (r.alert.direction == Direction.DECLINED) CaptureOutcome.Ignored(IgnoreReason.DECLINED, root) else CaptureOutcome.Transaction(issuer, root, r.alert)
        }
    }
}
