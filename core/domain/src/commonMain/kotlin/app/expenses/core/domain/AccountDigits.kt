package app.expenses.core.domain

/**
 * The digits a bank prints for an account or a card in its texts ("A/c XX1234", "Card XX3025", "Acct XX123"): the LAST FOUR for most banks and for cards, but only the
 * LAST THREE for some bank accounts (ICICI Bank writes "Acct XX123"). A value is always three or four digits, kept exactly as printed, never padded or cut to a fixed
 * length; its length is part of what it says. Stored on an account (`account_look.last4`, the name is older than three-digit support), carried by a captured text
 * (`inbox.account_last4`), and compared here.
 *
 * Two values name the same account when they are equal, or when one is the end of the other (a text that shows "123" for the account the person entered as "4123"):
 * [compatible]. Only those three- and four-digit shapes count; anything else is no digits at all.
 */
object AccountDigits {
    const val SHORTEST = 3
    const val LONGEST = 4

    /** What the account form says when the digits are not three or four digits. */
    const val INVALID_MESSAGE = "Type 3 or 4 digits, like 821 or 4821, as your bank's texts show them, or leave it empty."

    /** Whether [s] is three or four digits and nothing else. */
    fun isValid(s: String?): Boolean = s != null && s.length in SHORTEST..LONGEST && s.all { it in '0'..'9' }

    /** Blank means none. Otherwise exactly three or four digits, as typed; anything else fails with [INVALID_MESSAGE]. */
    fun parse(raw: String): Result<String?> {
        val t = raw.trim()
        if (t.isEmpty()) return Result.success(null)
        return if (isValid(t)) Result.success(t) else Result.failure(IllegalArgumentException(INVALID_MESSAGE))
    }

    /**
     * The digits a run of digits stands for when a bank prints the end of an account number: a run of exactly three is a bank's three-digit mask and is kept whole, a longer run
     * (a card or an account number printed in full or with only its head masked) is read as its last four, and a shorter run is not digits of an account at all.
     */
    fun fromRun(run: String): String? {
        if (run.isEmpty() || !run.all { it in '0'..'9' }) return null
        return when {
            run.length == SHORTEST -> run
            run.length >= LONGEST -> run.takeLast(LONGEST)
            else -> null
        }
    }

    /** Whether two values could be the end of one account number: equal, or one is the end of the other. Not valid digits never match anything. */
    fun compatible(a: String?, b: String?): Boolean {
        if (!isValid(a) || !isValid(b)) return false
        return a!!.endsWith(b!!) || b.endsWith(a)
    }

    /** Whether two digit values the app holds could name one account: equal as stored (whatever they are), or [compatible]. Null names nothing. */
    fun same(a: String?, b: String?): Boolean = a != null && b != null && (a == b || compatible(a, b))

    private val MENTION = Regex("""(?i)(?<![0-9])(?:([x*]+[\s-]*|(?:a/?c|acct|account|ending|ends)\s*(?:no\.?|in|with)?[\s:.\-]*[x*]*))?([0-9]{3,4})(?![0-9])""")

    /**
     * Whether [text] (a statement line, a narration) names the account that carries [digits]: a run of three or four digits that is [compatible] with them. Four digits that equal four digits stand
     * alone (as they always did); anything that involves a three-digit value has to be introduced by a mask or an account word ("XX123", "A/c 4123", "ending 123"), because a bare three-digit
     * number in a narration is as likely a branch or a reference as an account.
     */
    fun mentionedIn(text: String, digits: String): Boolean = MENTION.findAll(text).any { m ->
        val run = m.groupValues[2]
        compatible(run, digits) && ((run.length == LONGEST && digits.length == LONGEST) || m.groupValues[1].isNotEmpty())
    }
}
