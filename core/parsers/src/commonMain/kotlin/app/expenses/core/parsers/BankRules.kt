package app.expenses.core.parsers

/** What a header root stands for. Only the sender's own words differ by kind; the parser rules are shared. */
enum class SenderKind { BANK, CARD, PAYMENT_APP, UTILITY }

/**
 * One sender the app reads texts or notifications from, as data.
 *
 * @property root the header root (`HDFCBK` for `AX-HDFCBK-S`), upper case; see [SenderRules.headerRoot].
 * @property issuer the name shown to the person. Roots with the same issuer are one bank with several headers.
 * @property kind what sort of sender it is.
 * @property emailNamed false when the issuer's name must not be matched in an email's sender name: payment apps send no bank email, and "slice" and "Jupiter" are ordinary words.
 * @property emailAliases other ways a bank writes its own name in an email's sender name or address, lower case ("kotak", "pnb"), each standing for this root.
 * @property cardTxnLayout HDFC's card alert has no "debited" or "spent": "Txn Rs.20.00 On HDFC Bank Card 0862 At <merchant> by UPI <ref>". For a root with this set, a card used at a
 * merchant is read as money out ([GenericAlertParser]).
 */
data class BankRule(
    val root: String,
    val issuer: String,
    val kind: SenderKind = SenderKind.BANK,
    val emailNamed: Boolean = true,
    val emailAliases: List<String> = emptyList(),
    val cardTxnLayout: Boolean = false,
)

/**
 * The whole list of senders the app knows, in one place. **To add a bank, add one line here and one sample message to `BankRulesTest`**; nothing else in the code names a
 * bank. The self-check in that test refuses a duplicate root, an email phrase claimed by two banks, and an entry without a sample the classifier reads.
 *
 * **Provisional:** written from public knowledge of common headers, not checked against live traffic; the labelled corpus from consenting testers is what confirms or corrects
 * it. A person can add a header the list misses (capture health, "This is my bank"). Payment apps are not text headers: notification capture uses the same list to decide which
 * sources are read. Utilities, telecom and insurers send bills; electricity, water and gas boards each use their own header, so the list is even more provisional for them.
 * The order is the order a pasted message is searched in ([PasteRules]) and the order email names are tried in on equal length.
 */
object BankRules {
    val ALL: List<BankRule> = listOf(
        BankRule("HDFCBK", "HDFC Bank", cardTxnLayout = true), BankRule("HDFCBN", "HDFC Bank", cardTxnLayout = true),
        BankRule("ICICIB", "ICICI Bank"),
        BankRule("SBIINB", "SBI", emailAliases = listOf("state bank of india")), BankRule("SBIPSG", "SBI"),
        BankRule("SBICRD", "SBI Card", SenderKind.CARD), BankRule("SBIUPI", "SBI"),
        BankRule("AXISBK", "Axis Bank"),
        BankRule("KOTAKB", "Kotak Mahindra Bank", emailAliases = listOf("kotak")),
        BankRule("IDFCFB", "IDFC FIRST Bank", emailAliases = listOf("idfc first")),
        BankRule("INDUSB", "IndusInd Bank", emailAliases = listOf("indusind")),
        BankRule("YESBNK", "Yes Bank"),
        BankRule("PNBSMS", "Punjab National Bank", emailAliases = listOf("pnb")),
        BankRule("BOBSMS", "Bank of Baroda"), BankRule("BOIIND", "Bank of India"), BankRule("CANBNK", "Canara Bank"), BankRule("UNIONB", "Union Bank of India"),
        BankRule("IDBIBK", "IDBI Bank"), BankRule("FEDBNK", "Federal Bank"), BankRule("RBLBNK", "RBL Bank"), BankRule("AUBANK", "AU Small Finance Bank"), BankRule("BANDHN", "Bandhan Bank"),
        BankRule("SCBANK", "Standard Chartered"), BankRule("HSBCIN", "HSBC"),
        BankRule("AMEXIN", "American Express", SenderKind.CARD, emailAliases = listOf("amex")),
        BankRule("CITIBK", "Citibank", emailAliases = listOf("citi")),
        BankRule("INDBNK", "Indian Bank"),
        BankRule("GPAY", "Google Pay", SenderKind.PAYMENT_APP, emailNamed = false), BankRule("PHONEPE", "PhonePe", SenderKind.PAYMENT_APP, emailNamed = false),
        BankRule("PAYTM", "Paytm", SenderKind.PAYMENT_APP, emailNamed = false), BankRule("BHIM", "BHIM", SenderKind.PAYMENT_APP, emailNamed = false),
        BankRule("BESCOM", "BESCOM", SenderKind.UTILITY), BankRule("MSEDCL", "MSEDCL", SenderKind.UTILITY), BankRule("TATAPW", "Tata Power", SenderKind.UTILITY),
        BankRule("JIOPAY", "Jio", SenderKind.UTILITY), BankRule("AIRTEL", "Airtel", SenderKind.UTILITY), BankRule("LICIND", "LIC", SenderKind.UTILITY),
        BankRule("ONECRD", "OneCard", SenderKind.CARD), BankRule("SLCEIT", "slice", SenderKind.CARD, emailNamed = false), BankRule("JUPITR", "Jupiter", SenderKind.BANK, emailNamed = false),
        BankRule("PAYTMB", "Paytm Payments Bank"), BankRule("AIRBNK", "Airtel Payments Bank"),
    )

    /** Rules by header root. */
    val byRoot: Map<String, BankRule> = ALL.associateBy { it.root }
}
