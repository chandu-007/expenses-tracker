package app.expenses.core.domain

/**
 * Wording that says money is moving between a person's own accounts, in one place for everything that reads it (the pairing of two bank texts, the statement import).
 *
 * [says] is the strict reading: "transfer", "own account", "self", TPT (HDFC's third-party transfer code), "FT-", "sweep". It is what lets two texts of the same amount be paired by amount and time alone,
 * so it must not be satisfied by the payment rails: IMPS, NEFT and RTGS carry salaries, rent and every payment to another person, and 21% of the sample corpus mentions one.
 * [saysOrRail] adds the rails, for a statement line that is judged next to a transfer the app already has (the same amount on the same account within a day).
 */
object TransferWording {
    private val strict = Regex(
        """(?i)\btransfer\b|\bfunds?\s+transfer\b|\b(?:own|my|self)\s+(?:a/?c|account)\b|\bself\b|\bTPT\b|\bFT\s*-|\bsweep\b""",
    )
    private val rails = Regex("""(?i)\b(?:imps|neft|rtgs|trf)\b""")

    fun says(text: String): Boolean = strict.containsMatchIn(text)

    /**
     * Cash taken out at an ATM, as a bank's text words it: "cash withdrawal", "withdrawn", "ATM WDL", "ATM withdrawal", "ATM cash", "WDL at ATM", "cash wdl". One list for the capture
     * side ([app.expenses.core.parsers.KindRules] suggests the transfer to cash) and for the entries already added ([TransferCandidates]). A bare "ATM" inside a name does not count
     * ("ATMOS cafe", "Batman store", "ATM Bakery"): the word has to be followed by one of the withdrawal words.
     */
    private val atmStrong = Regex("""(?i)\bcash\s+withdraw\w*|\batm\s*[-/]?\s*(?:wdl|withdrawal|cash)\b|\bwdl\s+at\s+atm\b|\bcash\s+wdl\b""")

    /** The bare word "withdrawn": a bank text's "Rs.5,000 withdrawn", but also what a wallet says of money moved to a bank account, so the finder counts it for bank and card accounts only. */
    private val bareWithdrawn = Regex("""(?i)\bwithdrawn\b""")

    fun saysAtmWithdrawal(text: String): Boolean = atmStrong.containsMatchIn(text) || bareWithdrawn.containsMatchIn(text)

    /** The codes a bank statement's narration starts a withdrawal with ("NWD-<card>-...", "AWL-..."): only for statement-style words, a text never uses them. */
    private val atmCodes = Regex("""(?i)(?:^|[\s|/])(?:nwd|awl)\s*-""")

    /** A title or note that is nothing but the name of a cash withdrawal: "ATM", "ATM cash", "Cash withdrawal", "ATM WDL", "Cash". Typed by hand or cleaned from a narration ("ATM cash"). */
    private val atmTitle = Regex("""(?i)^\s*(?:atm|cash|atm\s*[-/]?\s*cash|cash\s+withdrawals?|atm\s*[-/]?\s*(?:wdl|withdrawals?)|cash\s+wdl)\s*$""")

    /** What a bank charges around a withdrawal ("ATM WDL CHGS", "charges for cash withdrawal at other bank ATM", "GST"): money spent, not money moved. */
    private val charges = Regex("""(?i)\b(?:charges?|chgs?|fees?|gst)\b""")

    /** A wallet's own words for money moved out of it ("withdrawn from your Paytm Wallet to bank account"): a move between accounts, but not cash. */
    private val walletWithdrawal = Regex("""(?i)\bwallet\b[^\n]{0,80}\b(?:bank|a/?c|account)\b|\bwithdraw\w*\b[^\n]{0,60}\bwallet\b""")

    /** The boilerplate many card texts end with ("cash withdrawal limit reset", "limit for cash withdrawal"): it names the word without a withdrawal having happened. */
    private val limitBoilerplate = Regex("""(?i)\b(?:cash\s+)?withdraw\w*\s+limits?\b|\blimits?\s+(?:for|of|on)\s+(?:cash\s+)?withdraw\w*""")

    /**
     * Whether [title] (an entry's merchant or note) names a cash withdrawal, or [text] (the words an entry was read from) says one: the finder's reading of an entry already in the ledger.
     * Words about charges or fees, a wallet's own withdrawal and limit boilerplate are not a withdrawal; [bareWithdrawnOk] is false for a wallet account, where the bare "withdrawn" is the wallet's
     * word for moving money to a bank.
     */
    fun readsAsAtmWithdrawal(title: String?, note: String?, text: String?, bareWithdrawnOk: Boolean = true): Boolean {
        val all = listOfNotNull(title, note, text)
        if (all.any { charges.containsMatchIn(it) || walletWithdrawal.containsMatchIn(it) }) return false
        fun says(s: String): Boolean { val t = limitBoilerplate.replace(s, " "); return atmStrong.containsMatchIn(t) || bareWithdrawnOk && bareWithdrawn.containsMatchIn(t) || atmCodes.containsMatchIn(t) }
        return listOfNotNull(title, note).any { atmTitle.matches(it) || says(it) } || text?.let(::says) == true
    }

    /**
     * Wording that says the other end of a payment is the person themselves, with nothing left to guess: "self transfer", "to self", "own account", "own A/c", "own name", "own VPA".
     * Stricter than [says] (which also takes "transfer" alone, and a bare "self"): used where one entry on its own is judged. "to self" must end there: "Paid to Self Drive Cars" is a shop.
     */
    private val selfWords = Regex(
        """(?i)\bself[\s-]*(?:transfer|trf|a/?c|account|upi)\b|\b(?:to|from)\s+self\b(?!\s+[a-z])|\bown\s+(?:a/?c|account|name|vpa|upi(?:\s+id)?)\b|\bmy\s+(?:vpa|upi(?:\s+id)?)\b|\bown\s+(?:bank\s+)?account\b""",
    )

    fun saysSelfTransfer(text: String): Boolean = selfWords.containsMatchIn(text)

    fun saysOrRail(text: String): Boolean = says(text) || rails.containsMatchIn(text)
}
