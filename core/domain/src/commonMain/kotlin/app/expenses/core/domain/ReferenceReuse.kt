package app.expenses.core.domain

/**
 * What a bank reference that is already on an entry means when a second text carries it: the same transaction told again (a text, then the notification, then the email: refused as
 * "already in your ledger"), or the payment being undone (a refund or reversal text carries the reference of the payment it undoes: it is a new entry under "<reference>/reversal").
 *
 * The entry's own kind is not enough to tell: the person may have changed its type (an expense that was really income, a transfer), and then the kind no longer says which way the *first
 * text* pointed. [originalOutgoing] is that direction, read from the original words the entry keeps (null when it has none or they cannot be read). The rule:
 *  - an entry that is a transfer is the same transaction, whichever way the text points;
 *  - an entry whose kind no longer matches the direction of its original words was changed by the person (or the text was read the wrong way round and turned): any text with the
 *    reference is the same transaction. Counting it as a reversal would record the transaction twice, and doing so silently is the worse mistake; the person can still add a real
 *    refund by hand;
 *  - otherwise a text pointing the other way is a reversal, and one pointing the same way is the same transaction.
 * With no readable original words the entry's kind stands for its direction (the rule before types could be changed).
 */
object ReferenceReuse {
    enum class Verdict { SAME_TRANSACTION, REVERSAL }

    /** The kinds of entry that are money coming in. */
    private fun isIncoming(kind: String) = kind == "INCOME" || kind == "REFUND"

    /**
     * [existingKind] is the entry's kind now ("EXPENSE", "INCOME", "REFUND", "TRANSFER"); [originalOutgoing] true when its original text said money went out, false when money came in,
     * null when unknown; [textOutgoing] is the direction of the text carrying the reference.
     */
    fun verdict(existingKind: String, originalOutgoing: Boolean?, textOutgoing: Boolean): Verdict {
        if (existingKind == "TRANSFER") return Verdict.SAME_TRANSACTION
        val entryOutgoing = !isIncoming(existingKind)
        if (originalOutgoing != null && originalOutgoing != entryOutgoing) return Verdict.SAME_TRANSACTION
        return if (entryOutgoing != textOutgoing) Verdict.REVERSAL else Verdict.SAME_TRANSACTION
    }
}
