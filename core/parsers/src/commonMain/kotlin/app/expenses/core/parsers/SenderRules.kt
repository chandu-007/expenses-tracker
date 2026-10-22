package app.expenses.core.parsers

/**
 * Who sent a text, as far as capture cares. Indian transactional SMS come from alphanumeric headers registered with TRAI: an operator and
 * circle prefix of two letters, a dash, the header root, and often a dash and a one-letter category suffix: `AX-HDFCBK-S`, `VM-SBIINB`,
 * `JD-ICICIB-T`. The prefix and suffix change from one text to the next, the **root** does not, so the allowlist is matched on the root.
 * A personal number (digits, with or without +91) has no header and is never captured.
 */
object SenderRules {
    private val header = Regex("""^([A-Za-z0-9]{2})-([A-Za-z0-9]{3,12}?)(?:-([A-Za-z]))?$""")

    /** The upper-case header root of an alphanumeric sender, or null for a phone number or anything that is not a registered header. */
    fun headerRoot(sender: String): String? {
        val s = sender.trim()
        if (s.isEmpty() || s.removePrefix("+").all { it.isDigit() || it == ' ' || it == '-' }) return null
        val m = header.matchEntire(s)
        val root = if (m != null) m.groupValues[2] else s.takeIf { it.length in 3..12 && it.all { c -> c.isLetterOrDigit() } && it.any { c -> c.isLetter() } }
        return root?.uppercase()
    }

    /**
     * Header roots of banks, card issuers and payment apps that send transaction alerts, with the name shown to the user. **Provisional:**
     * written from public knowledge of common headers, not checked against live traffic; the labelled corpus from consenting testers is what
     * confirms or corrects it (FR-CAP-13 ships corrections over the air). A person can add a header the list misses.
     */
    val BUILT_IN: Map<String, String> = mapOf(
        "HDFCBK" to "HDFC Bank", "HDFCBN" to "HDFC Bank", "ICICIB" to "ICICI Bank", "SBIINB" to "SBI", "SBIPSG" to "SBI", "SBICRD" to "SBI Card", "SBIUPI" to "SBI",
        "AXISBK" to "Axis Bank", "KOTAKB" to "Kotak Mahindra Bank", "IDFCFB" to "IDFC FIRST Bank", "INDUSB" to "IndusInd Bank", "YESBNK" to "Yes Bank",
        "PNBSMS" to "Punjab National Bank", "BOBSMS" to "Bank of Baroda", "BOIIND" to "Bank of India", "CANBNK" to "Canara Bank", "UNIONB" to "Union Bank of India",
        "IDBIBK" to "IDBI Bank", "FEDBNK" to "Federal Bank", "RBLBNK" to "RBL Bank", "AUBANK" to "AU Small Finance Bank", "BANDHN" to "Bandhan Bank",
        "SCBANK" to "Standard Chartered", "HSBCIN" to "HSBC", "AMEXIN" to "American Express", "CITIBK" to "Citibank", "INDBNK" to "Indian Bank",
        // Payment apps, for notification capture (FR-CAP-06): not text headers, but the same list decides which sources are read.
        "GPAY" to "Google Pay", "PHONEPE" to "PhonePe", "PAYTM" to "Paytm", "BHIM" to "BHIM",
        // Utilities, telecom and insurers that send bills. **Even more provisional than the banks above:** a handful written from memory of common headers, not checked against live
        // traffic. Electricity, water and gas boards each use their own header; capture health lists the headers it saw and ignored, and a person can add them.
        "BESCOM" to "BESCOM", "MSEDCL" to "MSEDCL", "TATAPW" to "Tata Power", "JIOPAY" to "Jio", "AIRTEL" to "Airtel", "LICIND" to "LIC",
        "ONECRD" to "OneCard", "SLCEIT" to "slice", "JUPITR" to "Jupiter", "PAYTMB" to "Paytm Payments Bank", "AIRBNK" to "Airtel Payments Bank",
    )

    /** Roots whose names are not matched in an email's sender: payment apps send no bank email, and "slice" and "Jupiter" are ordinary words that would match too much. */
    private val NOT_NAMED_IN_EMAIL = setOf("GPAY", "PHONEPE", "PAYTM", "BHIM", "SLCEIT", "JUPITR")

    /** Short or long ways a bank writes its own name that [BUILT_IN]'s display names do not cover, each with the header root it stands for. */
    private val EMAIL_ALIASES = listOf(
        "state bank of india" to "SBIINB", "kotak" to "KOTAKB", "idfc first" to "IDFCFB", "indusind" to "INDUSB", "pnb" to "PNBSMS", "amex" to "AMEXIN", "citi" to "CITIBK",
    )

    /** Every phrase an email's sender name may contain, lower case, longest first so "State Bank of India" and "Union Bank of India" win over "Bank of India". */
    private val emailPhrases: List<Pair<String, String>> = (
        BUILT_IN.entries.filter { it.key !in NOT_NAMED_IN_EMAIL }.groupBy { it.value }.map { (name, roots) -> name.lowercase() to roots.first().key } + EMAIL_ALIASES
        ).distinctBy { it.first }.sortedByDescending { it.first.length }

    private fun isWordChar(c: Char) = c.isLetterOrDigit()

    /** Whether [phrase] stands in [text] as whole words ("hsbc" in "HSBC Alerts", not in "xhsbcx"). */
    private fun hasWords(text: String, phrase: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(phrase, from)
            if (at < 0) return false
            val end = at + phrase.length
            if ((at == 0 || !isWordChar(text[at - 1])) && (end == text.length || !isWordChar(text[end]))) return true
            from = at + 1
        }
    }

    /**
     * The header root of the bank an email says it is from, judged by the sender's display name ("HDFC Bank", "ICICI Bank Alerts") or an address written in its
     * words ("alerts@hdfcbank.net"), or null when it names no bank on the built-in list or the person's [extraRoots]. An email app's notification has no SMS header, so
     * this is what stands in for one; nothing else about the email is looked at. A name of seven letters or more also matches with its spaces closed up, which is how
     * an address writes it. **The names are written from public knowledge of the banks, not checked against real emails.**
     */
    fun emailIssuer(senderName: String, extraRoots: Set<String> = emptySet()): String? {
        val lower = senderName.trim().lowercase()
        if (lower.isEmpty()) return null
        val closed = lower.filter { isWordChar(it) }
        for ((phrase, root) in emailPhrases) {
            if (hasWords(lower, phrase)) return root
            val tight = phrase.filter { isWordChar(it) }
            if (tight.length >= 7 && closed.contains(tight)) return root
        }
        return extraRoots.map { it.trim().uppercase() }.filter { it.length >= 4 }.firstOrNull { closed.contains(it.lowercase()) }
    }
}
