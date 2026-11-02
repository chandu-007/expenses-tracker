package app.expenses

import app.expenses.core.domain.AccountMatch
import app.expenses.core.domain.BankNames
import app.expenses.core.parsers.BankRules
import app.expenses.core.parsers.SenderKind
import app.expenses.core.storage.CaptureAccounts

/**
 * Which of the person's accounts a bank text is about, for the screens that show waiting texts: the digits the text prints, the bank that sent it and what the person chose before for that bank
 * and those digits, put together by [AccountMatch] (the rules and their order are documented there). Built once per refresh of the list, because it reads the accounts that carry digits and
 * the remembered choices from the database; each [resolve] then costs one settings read.
 *
 * [liveAccountIds] are the accounts a text may be put on (not deleted, not archived): a remembered choice that points at another account is not used.
 */
internal class AccountResolver(private val ok: AppContainer.Opened.Ok, private val liveAccountIds: Set<String>) {
    private val candidates = ok.looks.candidates()

    /**
     * The matching result for a text from [issuer] that prints [digits] (null: none printed). [preferCard]: the text says "card", so a card is the answer when exactly one carries the digits.
     * [bankKnown] false when the digits are not the sender's own account (the other end of a transfer): the bank they belong to is not said, so no account is ruled out by its name.
     */
    fun resolve(issuer: String, digits: String?, preferCard: Boolean = false, bankKnown: Boolean = true): AccountMatch.Result {
        val remembered = ok.templates.setting(CaptureAccounts.key(issuer, digits))?.takeIf { it in liveAccountIds }
        return AccountMatch.resolve(digits, issuer, candidates, if (bankKnown) banks else null, remembered, preferCard)
    }

    /** The one account the text points at, or null when none or more than one could be meant. */
    fun accountFor(issuer: String, digits: String?, preferCard: Boolean = false, bankKnown: Boolean = true): String? =
        (resolve(issuer, digits, preferCard, bankKnown) as? AccountMatch.Result.Found)?.accountId

    private companion object {
        /** What the account names say about banks: the display names of the banks and card issuers the app reads (not the payment apps and bill senders, which are no bank). */
        val banks: BankNames by lazy { BankNames(BankRules.ALL.filter { it.kind == SenderKind.BANK || it.kind == SenderKind.CARD }.map { it.issuer }.distinct()) }
    }
}

/** What a card in To review says when the account for a waiting text is not known, and what a new account for it would start as. */
internal object AccountAsk {
    /** [text] is why the question is asked; [choices] are the only accounts offered (several carry the digits), empty when every account is. */
    class Ask(val text: String, val choices: List<String>)

    /** Null when no question is needed: the account is known, or the person has too few accounts for there to be a choice. [names]: id to name of the accounts the text may go to. */
    fun of(match: AccountMatch.Result, issuer: String, digits: String?, names: Map<String, String>): Ask? {
        if (names.size < 2) return null
        return when (match) {
            is AccountMatch.Result.Found -> null
            is AccountMatch.Result.Several -> {
                val shown = match.accountIds.mapNotNull { names[it] }
                val list = if (shown.size <= 1) shown.joinToString() else shown.dropLast(1).joinToString(", ") + " and " + shown.last()
                Ask("$list all end in $digits, so I cannot tell which this is.", match.accountIds.filter { it in names })
            }
            AccountMatch.Result.None ->
                if (digits != null) Ask("I have not seen $issuer ending $digits before.", emptyList()) else Ask("$issuer texts do not say which account they are about.", emptyList())
        }
    }

    /** The name a new account for this text starts with ("ICICI Bank 123"): the sender's name and the digits it prints. */
    fun newName(issuer: String, digits: String?): String = if (digits != null) "$issuer $digits" else issuer

    /** A credit card only when the text says so; every other text is a bank account (a card's type cannot be changed once it has entries, so the person picks it when it is not obvious). */
    fun newType(body: String): String = if (body.contains("credit card", ignoreCase = true)) "CREDIT_CARD" else "BANK"
}
