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
}
