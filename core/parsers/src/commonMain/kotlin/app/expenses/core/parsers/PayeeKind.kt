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
}
