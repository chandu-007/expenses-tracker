package app.expenses.core.domain

/**
 * The one key a payee is remembered under, so that what the person chose for a shop the first time is found the next time however the bank spells it.
 *
 * The same shop reaches the app as "SWIGGY BANGALORE KA IN" on a card, "swiggy@icici" in a UPI debit, "UPI-SWIGGY-swiggy@icici-1234" in a narration, "RAZ*SWIGGY" through a gateway and "Swiggy"
 * in a notification. The key is built so these meet:
 * - a merchant the starter list knows is its clean display name ("swiggy");
 * - otherwise the words of the name, lower case, without company and place noise ("pvt", "ltd", "india", a city), without terminal and branch numbers ("kirana mart 2231", "eazydine0000000") and
 *   without the bank's handle after an `@`: "FRESHPIK BANGALORE IN", "Freshpik" and "freshpik@ybl" are all "freshpik";
 * - a UPI address that is not a known merchant keeps its digits whole ("shop4821@ybl" is not "shop"): they say who it is.
 * Entries learned before this key existed were stored under [CategorySuggest.merchantKey] (letters and digits of the payee as read); [legacy] is that key, and a lookup tries both.
 */
object PayeeKey {
    fun of(payee: String?): String? {
        val raw = payee?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        DefaultRules.displayNameFor(raw)?.let { return CategorySuggest.merchantKey(it).takeIf { k -> k.length >= 2 } }
        if ('@' in raw) return CategorySuggest.merchantKey(raw).takeIf { it.length >= 2 }
        val words = DefaultRules.tokens(raw).mapNotNull { w ->
            val letters = w.trimEnd { it in '0'..'9' }
            when {
                w.all { it in '0'..'9' } -> if (w.length <= 2) w else null // "2231" is a terminal; "7" in "sector 7" stays
                w.length - letters.length >= 3 && letters.isNotEmpty() -> letters // "eazydine0000000"
                else -> w
            }
        }.filter { it !in DefaultRules.noise }
        val key = words.joinToString("")
        return (if (key.length >= 2) key else raw.takeIf { r -> r.any { it.isLetter() } }?.let(CategorySuggest::merchantKey)).orEmpty().takeIf { it.length >= 2 }
    }

    /** The key entries learned before [of] existed are stored under. */
    fun legacy(payee: String?): String? = payee?.let(CategorySuggest::merchantKey)?.takeIf { it.length >= 2 }

    /** The category learned for [payee]: under the key of today, else under the old one. */
    fun lookup(payee: String?, learned: Map<String, String>): String? = of(payee)?.let { learned[it] } ?: legacy(payee)?.let { learned[it] }

    /** The payees that sell more than one kind of thing, or stand for many sellers: a marketplace, a wallet, an app store. A category learned for them is a hint, not a rule. */
    private val mixed = setOf(
        "amazon", "amazonpay", "paytm", "paytmwallet", "phonepe", "googlepay", "gpay", "googleplay", "google", "cred", "mobikwik", "freecharge", "bhim", "flipkart", "reliance", "tata", "apple",
        "walmart", "ebay", "olx", "snapdeal",
    )

    /** Whether [payee] is one of [mixed]. */
    fun isMixed(payee: String?): Boolean = of(payee)?.let { it in mixed } == true
}
