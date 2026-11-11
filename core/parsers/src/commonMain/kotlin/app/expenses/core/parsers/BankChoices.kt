package app.expenses.core.parsers

/**
 * The banks and card issuers a person can pick in the guided setup, with the sender names each one texts from, all read from [BankRules] (nothing is listed twice and nothing here names a
 * bank). Payment apps and billers are not banks and are not offered. Picking a bank adds nothing to the list of trusted senders: every bank here is read already. The pick is only what the
 * checkup uses to say "no text from ICICI Bank seen yet".
 */
object BankChoices {
    /** One bank: its display name and every header root it texts from (HDFC Bank: HDFCBK and HDFCBN). */
    data class Choice(val issuer: String, val senderNames: List<String>, val isCard: Boolean)

    /** One entry per issuer, in the order of [BankRules.ALL] (the common banks come first). */
    val all: List<Choice> by lazy {
        BankRules.ALL.filter { it.kind == SenderKind.BANK || it.kind == SenderKind.CARD }.groupBy { it.issuer }
            .map { (issuer, rules) -> Choice(issuer, rules.map { it.root }, rules.all { it.kind == SenderKind.CARD }) }
    }

    /** How many are shown before "More banks". */
    const val POPULAR = 8

    val issuers: List<String> by lazy { all.map { it.issuer } }

    /** The sender names of [issuer] (every root whose display name is it), or empty for a name that is not on the list. */
    fun senderNamesOf(issuer: String): List<String> = all.firstOrNull { it.issuer == issuer }?.senderNames.orEmpty()

    private val names: app.expenses.core.domain.BankNames by lazy { app.expenses.core.domain.BankNames(issuers) }

    /**
     * The bank an account's NAME says it belongs to ("HDFC Savings" is HDFC Bank's, "SBI Card Elite" is SBI Card's, "Salary" says nothing: null). Only the name is read, as everywhere else the app
     * matches an account to a bank ([app.expenses.core.domain.BankNames]). When two issuers fit, the one whose whole name is in the account's name wins, then the longer name.
     */
    fun issuerOfAccountName(accountName: String): String? {
        val lower = accountName.lowercase()
        return issuers.filter { names.sameBank(it, accountName) == true }.maxWithOrNull(compareBy<String> { lower.contains(it.lowercase()) }.thenBy { it.length })
    }

    /** The bank a header root belongs to, or null. */
    fun issuerOfRoot(root: String): String? = SenderRules.BUILT_IN[root.trim().uppercase()]?.takeIf { it in issuers }
}
