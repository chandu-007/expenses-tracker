package app.expenses.core.parsers

/** Whether a UPI address belongs to a person or a business, as far as its shape says. */
enum class PayeeKind { PERSON, BUSINESS, UNKNOWN }

object PayeeKinds {
    private val mobile = Regex("""^(?:\+?91)?[6-9]\d{9}$""")
    private val businessPrefix = Regex("""^(?:paytmqr|paytm\.s|paytm-|bharatpe|bpay|razorpay|rzp|gpay-\d|upi-|merchant|pos\.|bbps|q\d{6,}|ybl\.|phonepe\.merchant)""")

    /**
     * A mobile number before the @ is a person's address (money sent to a contact by phone number). Merchant QR codes use a few well-known
     * shapes (a Paytm or BharatPe QR, a Razorpay collect address, a PhonePe "q" followed by digits). Any other address could be either a person
     * who chose a name or a small shop that did, so it is UNKNOWN rather than guessed: a wrong "person" or "business" label would be believed.
     */
    fun of(vpa: String?): PayeeKind {
        val v = vpa?.trim()?.lowercase()?.takeIf { '@' in it } ?: return PayeeKind.UNKNOWN
        val local = v.substringBefore('@')
        return when {
            mobile.matches(local.replace(" ", "").replace("-", "")) -> PayeeKind.PERSON
            businessPrefix.containsMatchIn(local) -> PayeeKind.BUSINESS
            else -> PayeeKind.UNKNOWN
        }
    }

    private val shopWords = setOf(
        "store", "stores", "mart", "shop", "traders", "trading", "enterprises", "hotel", "restaurant", "cafe", "medical", "medicals", "pharmacy", "hospital", "clinic", "stall", "mess", "tiffin", "centre", "center",
        "bakery", "sweets", "foods", "food", "super", "market", "bazaar", "bazar", "fresh", "dairy", "agency", "agencies", "services", "service", "company", "co", "pvt", "ltd", "limited", "private", "llp", "inc", "sons",
        "brothers", "bros", "and", "&", "bhavan", "bhavanam", "palace", "garage", "motors", "tyres", "salon", "studio", "works", "industries", "associates", "solutions", "tech", "online", "retail", "general",
    )

    /**
     * Whether a name that is not an address reads like a person's: two or three words of letters, none of them a shop or company word ("RAVI KUMAR", "Anita Rao"). Used only to be careful: a
     * category learned for such a name is treated as a hint until it has been chosen twice, because a person is paid for different things. A wrong "yes" costs a look, never a wrong category.
     */
    fun looksLikePerson(name: String?): Boolean {
        val n = name?.trim()?.takeIf { it.isNotEmpty() && '@' !in it } ?: return false
        val words = n.split(' ', '.').filter { it.isNotEmpty() }
        return words.size in 2..3 && words.all { w -> w.all { it.isLetter() } && w.lowercase() !in shopWords } && n.none { it.isDigit() } && app.expenses.core.domain.DefaultRules.displayNameFor(n) == null
    }

    /**
     * Whether whoever PAID the person looks like an individual, for how far a learned income category is trusted: a mobile number, a UPI address that is not a known business shape, or a
     * name of letters that reads like a person's or is a single word. A company ("ACME SOFTWARE PVT LTD", "SAMPLE TRADERS") or a business QR is not. Wrongly "yes" only costs a look.
     */
    fun paysLikeAPerson(payer: String?): Boolean {
        val p = payer?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if ('@' in p) return of(p) != PayeeKind.BUSINESS
        if (looksLikePerson(p)) return true
        val words = p.split(' ', '.').filter { it.isNotEmpty() }
        return words.size == 1 && words[0].all { it.isLetter() } && words[0].lowercase() !in shopWords
    }
}
