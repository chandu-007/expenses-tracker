package app.expenses.core.domain

/**
 * A starter list of well-known Indian merchants ([MerchantTable]) and their usual category, used **only** when the person has no rule of their own and nothing learned for
 * the payee (see [CategorySuggest.suggest]). The category is named, not identified: it is used only if a category with that name exists and is not hidden, so renaming or
 * deleting one switches its starters off.
 *
 * Matching is on whole words, so a UPI address like "swiggy@icici" matches "swiggy" but "ola" is not in "cola" and "uber" is not in "suburban". What follows an `@` (the
 * bank's handle: "ola@paytm", "x@airtel") is never read. When two names match, the one with more words wins, then the longer, then the earlier in the text
 * ("Swiggy Instamart" is Instamart's groceries, not Swiggy's food). The names are looked up in an index built once, so a text costs one pass over its words, however long
 * the list grows.
 */
object DefaultRules {
    private class Alias(val words: List<String>, val chars: Int, val merchant: Merchant)

    /** The first word of every alias to the aliases that start with it. */
    private val index: Map<String, List<Alias>> = HashMap<String, MutableList<Alias>>().also { map ->
        for (m in MerchantTable.all) for (a in m.aliases) {
            val words = tokens(a)
            if (words.isNotEmpty()) map.getOrPut(words[0]) { ArrayList(2) } += Alias(words, words.sumOf { it.length }, m)
        }
    }

    /** Words that may stand around a merchant's name in a payee string without making it someone else: company suffixes, places, payment-gateway words. */
    private val noise: Set<String> = setOf(
        "pvt", "ltd", "limited", "private", "india", "indian", "llp", "inc", "co", "corp", "corporation", "company", "services", "service", "solutions", "technologies", "tech", "systems",
        "retail", "online", "ecommerce", "commerce", "payments", "payment", "pay", "upi", "qr", "order", "orders", "app", "store", "stores", "shop", "merchant", "mobile", "digital",
        "internet", "wallet", "bill", "bills", "com", "electricity", "recharge", "broadband", "the", "and", "in", "ind", "rzp", "razorpay", "payu", "billdesk", "cashfree", "ccavenue", "paytm",
        "ka", "mh", "dl", "tn", "ap", "ts", "kl", "gj", "up", "wb", "rj", "hr", "pb", "mp",
        "bangalore", "bengaluru", "mumbai", "delhi", "new", "chennai", "hyderabad", "kolkata", "pune", "gurgaon", "gurugram", "noida", "ahmedabad", "jaipur", "kochi",
    )

    /**
     * The lower-case ASCII words of [text]: letters and digits, everything else a separator, and the bank handle after an `@` skipped. Every consumer of the index reads text
     * through this one function so aliases and payees are split the same way.
     */
    internal fun tokens(text: String): List<String> {
        val out = ArrayList<String>(8)
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '@') { i++; while (i < n && isWordChar(text[i])) i++; continue }
            if (!isWordChar(c)) { i++; continue }
            val start = i
            while (i < n && isWordChar(text[i])) i++
            out += text.substring(start, i).lowercase()
        }
        return out
    }

    private fun isWordChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private class Found(val alias: Alias, val at: Int)

    /** The best alias in [words]: most words, then most letters, then first in the text. Only merchants with a display name when [named]. */
    private fun best(words: List<String>, named: Boolean): Found? {
        var best: Found? = null
        for (i in words.indices) {
            val candidates = index[words[i]] ?: continue
            for (a in candidates) {
                if (named && a.merchant.display == null) continue
                if (i + a.words.size > words.size) continue
                var k = 1
                while (k < a.words.size && words[i + k] == a.words[k]) k++
                if (k < a.words.size) continue
                val b = best
                if (b == null || a.words.size > b.alias.words.size || (a.words.size == b.alias.words.size && a.chars > b.alias.chars)) best = Found(a, i)
            }
        }
        return best
    }

    /**
     * Words that may follow a merchant's name in a payee that is still the merchant: what it sells or is ("Jio Mart", "Swiggy Stores", "Haldiram Foods", "Apollo Pharmacy Ltd"). Anything
     * else after the first word ("Ola Sharma", "Uber Singh", "Apollo Tyres") makes it a person or another business, and the starter list stays out of it.
     */
    private val shopWords: Set<String> = setOf(
        "store", "stores", "mart", "foods", "food", "restaurant", "restaurants", "cafe", "cafes", "hotel", "hotels", "pharmacy", "pharmacies", "medical", "medicals", "traders", "trading", "enterprises",
        "supermarket", "hypermarket", "bazaar", "bazar", "mall", "kitchen", "bakery", "sweets", "bhavan", "hospital", "hospitals", "clinic", "clinics", "labs", "lab", "diagnostics", "healthcare",
        "insurance", "ins", "allied", "life", "general", "seller", "marketplace", "cabs", "taxi", "fuel", "petrol", "pump", "station", "recharge", "prepaid", "postpaid", "bill", "payments", "dairy",
        "pulse", "express", "fresh", "daily", "bank", "cinemas", "cinema", "studios", "entertainment", "network", "networks", "energy", "power", "gas", "oil", "sports", "fashion", "electronics", "digital",
    )

    /**
     * The merchant [text] names, by the rules above, or null. A name that opens with a merchant and goes on with a word that is not company noise and not a shop word is a person ("Ola Sharma") or another business ("Apollo Tyres"): null, so the person's own choices and "a payment to a person" decide.
     */
    internal fun find(text: String): Merchant? {
        val words = tokens(text)
        val found = best(words, named = false) ?: return null
        if (found.at == 0 && wordsBeyond(words, found).any { it !in shopWords }) return null
        return found.alias.merchant
    }

    /** The words of a payee outside the matched name that are neither company noise nor digits. */
    private fun wordsBeyond(words: List<String>, found: Found): List<String> {
        val inside = found.at until found.at + found.alias.words.size
        return words.filterIndexed { i, w -> i !in inside && w !in noise && !w.all { it in '0'..'9' } }
    }

    /** The category name the starter list gives [text] (a payee and the words of the alert), or null. The longest merchant name in the text wins. */
    fun categoryNameFor(text: String): String? = find(text)?.category

    /**
     * The category names the starter list would put [text] in, best first: the merchant's better home among the newer starter categories ("Subscriptions" for Netflix, "Fuel" for Indian Oil), then the
     * coarse starter it has always had ("Fun", "Transport"). The caller takes the first one the person has a visible category of, so a person without the newer categories gets exactly what
     * [categoryNameFor] says. Empty when the text names no merchant.
     */
    private val recurringHomes = setOf("Subscriptions", "Internet and mobile", "Insurance", "Loan EMI", "Investments", "Rent")

    /**
     * Whether [text] names a service that is billed again and again (a streaming, cloud or software subscription, a mobile or broadband plan, an insurer, a lender, an investment platform, a housing
     * society) as opposed to a shop or a delivery app: the subscription finder trusts such a name with less evidence than an unknown merchant.
     */
    fun isRecurringService(text: String): Boolean = displayNameFor(text) != null && find(text)?.home in recurringHomes

    fun categoryNamesFor(text: String): List<String> = find(text)?.let { listOfNotNull(it.home, it.category).distinct() }.orEmpty()

    /**
     * The clean name of the merchant a payee string stands for ("Swiggy" for "swiggyupi@axb" or "SWIGGY BANGALORE KA IN"), or null when it names none or names it among words that
     * are not company noise ("Uber Singh" is a person, not Uber). The caller shows it as the entry's merchant; the text the payee came from is kept as it was.
     */
    fun displayNameFor(payee: String): String? {
        val words = tokens(payee)
        val found = best(words, named = true) ?: return null
        return if (wordsBeyond(words, found).isEmpty()) found.alias.merchant.display else null
    }
}
