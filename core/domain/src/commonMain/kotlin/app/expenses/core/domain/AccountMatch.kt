package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType

/**
 * Which bank a word in an account's name points at, from the display names of the BANKS and card issuers the app reads texts from ("ICICI Bank", "HDFC Bank", "SBI", "Kotak Mahindra Bank"; not the
 * payment apps or bill senders, which are no bank). An account called "ICICI Savings" is evidently ICICI's; one called "Salary" says nothing. Used only to keep a text of one bank away from an
 * account that is plainly another bank's, never to prove that an account is a bank's.
 */
class BankNames(private val banks: Collection<String>) {
    private val known: List<Set<String>> = banks.map { wordsOf(it) }.filter { it.isNotEmpty() }.distinct()

    /**
     * Whether the account called [accountName] belongs to the bank [issuer]: true when the name has a distinctive word of that bank, false when it has one of another known
     * bank (and none of this one's), null when the name names no bank, or [issuer] is no bank on the list (a payment app, a biller, a name the person added).
     */
    fun sameBank(issuer: String?, accountName: String): Boolean? {
        if (issuer == null || issuer !in banks) return null
        val mine = wordsOf(issuer)
        if (mine.isEmpty()) return null
        val name = wordsIn(accountName)
        if (name.any { it in mine }) return true
        return if (known.any { other -> other.none { it in mine } && other.any { it in name } }) false else null
    }

    private companion object {
        /** Words of a bank's name that every bank has, or that are ordinary words: they say nothing about which bank. */
        val GENERIC = setOf("bank", "banks", "card", "cards", "payments", "payment", "small", "finance", "limited", "ltd", "of", "india", "indian", "first", "national", "the", "and", "pay", "yes", "union", "standard")

        fun wordsIn(text: String): Set<String> = text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()

        fun wordsOf(issuer: String): Set<String> = wordsIn(issuer).filter { it.length >= 3 && it !in GENERIC }.toSet()
    }
}

/**
 * Which account a bank text is about, from the digits it prints, what the person has told the app, and the bank it came from. The one place that decides it, so a text in To review,
 * the pairing of two texts as one transfer and a bill all read the digits the same way.
 *
 * An account FITS when the digits it carries are the same as the text's or one ends the other ("123" and "4123": [AccountDigits.compatible]) and its name does not plainly belong to a different
 * bank than the text's ([banks]; an account whose name names no bank fits, the person did not say). Then, in order:
 *  1. **Exactly the same digits, and nothing else fits.** That account (a card, when [preferCard] says the text is about a card and exactly one card carries them). This is every four-digit match,
 *     which is all there was before three-digit digits existed.
 *  2. **What the person chose before** for this bank and these digits ([remembered]): they said so, and it settles digits two accounts share and digits that merely end the same.
 *  3. **Exactly one account fits** (digits that only end the same, "123" for an account saved as "4123", included).
 *  4. Otherwise nothing is guessed: [Result.Several] names the accounts that fit, so the person is asked; [Result.None] when none does.
 */
object AccountMatch {
    /** An account as the matcher needs it: [digits] is what the person entered (three or four digits), null when none. */
    data class Candidate(val id: String, val name: String, val type: AccountType, val digits: String?)

    /** How an account was found. */
    enum class Basis { DIGITS, REMEMBERED, ENDS_WITH }

    sealed interface Result {
        /** Exactly one account. */
        data class Found(val accountId: String, val basis: Basis) : Result

        /** Two or more accounts could be meant, and nothing tells them apart: ask. */
        data class Several(val accountIds: List<String>) : Result

        /** No account carries these digits (or the text printed none). */
        data object None : Result
    }

    /**
     * [digits] are what the text prints (null when it prints none), [issuer] the bank that sent it (its display name, null when no bank), [accounts] the person's live accounts, [banks] how to
     * tell an account's bank from its name (null: not used), [remembered] the id of the account chosen for this bank and digits that still exists.
     */
    fun resolve(digits: String?, issuer: String?, accounts: List<Candidate>, banks: BankNames? = null, remembered: String? = null, preferCard: Boolean = false): Result {
        if (!AccountDigits.isValid(digits)) return remembered?.let { Result.Found(it, Basis.REMEMBERED) } ?: Result.None
        val fits = accounts.filter { AccountDigits.compatible(it.digits, digits) && banks?.sameBank(issuer, it.name) != false }
        val same = fits.filter { it.digits == digits }
        if (fits.size == same.size) pick(same, preferCard)?.let { return Result.Found(it, Basis.DIGITS) }
        if (remembered != null) return Result.Found(remembered, Basis.REMEMBERED)
        if (fits.isEmpty()) return Result.None
        pick(fits, preferCard)?.let { id -> return Result.Found(id, if (same.any { it.id == id }) Basis.DIGITS else Basis.ENDS_WITH) }
        return Result.Several(fits.map { it.id })
    }

    /** One candidate is the answer. With several, a card (when [preferCard]) if exactly one is a card; anything else is ambiguous and gives null. */
    private fun pick(candidates: List<Candidate>, preferCard: Boolean): String? {
        if (candidates.size == 1) return candidates.single().id
        if (preferCard) candidates.filter { it.type == AccountType.CREDIT_CARD }.takeIf { it.size == 1 }?.let { return it.single().id }
        return null
    }
}
