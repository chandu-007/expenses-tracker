package app.expenses.core.parsers

import app.expenses.core.domain.AccountDigits

/**
 * The digits of the account or card a bank text names, as the bank prints them: four for most banks and for cards ("Card XX3025", "A/c XX1234"), THREE for some bank accounts
 * ("ICICI Bank Acct XX123 debited ..."). The value keeps the length the text shows ([AccountDigits]); nothing is padded, and a three-digit mask is never read as four digits or dropped.
 *
 * Shapes read, in this order (the first that is found wins): an account word with its digits ("A/c XX1234", "Acct XX123", "A/c *123", "Account XXX123", "ac no. ending 4321", "Account 1234");
 * a card word with four digits ("Card XX3025", "card no. 3025"); "ending 123" or "ending in 3025"; a card word with a masked three ("Card XX123"); and last, a bare mask ("x123", "XX123", "**4821")
 * of one to five mask characters. A long mask ("XXXXXXXXXX1234") is the other account of a transfer ([TransferClues]), not the text's own, so it is never read here. A longer run of digits
 * is read as its last four ([AccountDigits.fromRun]).
 */
object AccountMasks {
    private val account = Regex("""(?i)(?:a/?c|acct|account)\s*(?:no\.?)?\s*(?:ending(?:\s+(?:in|with))?)?\s*[:*]?\s*[x*]*(\d{3,6})(?:\b|-)""")
    private val card = Regex("""(?i)(?:card\s*(?:no\.?)?|ending(?:\s+in)?)\s*[:*]?\s*[x*]*(\d{4})\b""")
    private val ending = Regex("""(?i)\bending\s*(?:in|with)?\s*[:*]?\s*[x*]*(\d{3,4})(?!\d)""")
    private val maskedCardThree = Regex("""(?i)\bcard\s*(?:no\.?)?\s*[:*]?\s*[x*]+(\d{3})(?!\d)""")
    private val bare = Regex("""(?i)(?<![a-z0-9*])[x*]{1,5}(\d{3,4})(?!\d)""")

    /** The digits the text prints for its own account or card, or null when it prints none. */
    fun read(text: String): String? {
        for (re in listOf(account, card, ending, maskedCardThree, bare)) {
            val run = re.find(text)?.groupValues?.get(1) ?: continue
            AccountDigits.fromRun(run)?.let { return it }
        }
        return null
    }
}
