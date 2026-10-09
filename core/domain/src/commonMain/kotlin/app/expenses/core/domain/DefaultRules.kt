package app.expenses.core.domain

/**
 * A starter list of well-known Indian merchants and their usual category, used **only** when the person has no rule of their own and nothing learned for
 * the payee. The category is named, not identified: it is used only if a category with that name exists and is not hidden, so renaming or deleting one
 * switches its starters off. A name matches whole words only ("ola" is not in "cola"), so a UPI address like "swiggy@icici" matches "swiggy".
 */
object DefaultRules {
    private val table: Map<String, List<String>> = mapOf(
        "Food" to listOf("swiggy", "zomato", "dominos", "kfc", "mcdonalds", "starbucks", "pizza hut", "burger king", "box8", "faasos", "chaayos", "chai point", "behrouz", "haldirams", "barbeque nation", "eatsure"),
        "Groceries" to listOf("bigbasket", "big basket", "blinkit", "zepto", "dmart", "jiomart", "instamart", "grofers", "milkbasket", "licious", "reliance fresh", "natures basket"),
        "Transport" to listOf("uber", "ola", "rapido", "redbus", "irctc", "fastag", "namma yatri", "indigo", "air india", "vistara", "indian oil", "iocl", "bpcl", "hpcl", "bharat petroleum", "hindustan petroleum"),
        "Bills" to listOf("jio", "airtel", "vodafone", "bsnl", "bescom", "tneb", "msedcl", "tata power", "adani electricity", "bharat gas", "indane", "mahanagar gas", "act fibernet", "hathway", "tata play", "dish tv", "d2h", "lic", "electricity", "broadband", "recharge"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "decathlon", "ikea", "croma", "lenskart", "reliance digital"),
        "Health" to listOf("apollo", "pharmeasy", "1mg", "netmeds", "practo", "medplus", "cult fit", "cultfit", "lal pathlabs"),
        "Fun" to listOf("netflix", "prime video", "hotstar", "spotify", "bookmyshow", "pvr", "inox", "youtube premium", "sonyliv", "zee5", "disney"),
    )

    private fun tokens(text: String): List<String> = text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun containsPhrase(text: List<String>, phrase: List<String>): Boolean =
        phrase.isNotEmpty() && (0..text.size - phrase.size).any { i -> phrase.indices.all { text[i + it] == phrase[it] } }

    /** The category name the starter list gives [text] (a payee and the words of the alert), or null. The first list entry that matches wins. */
    fun categoryNameFor(text: String): String? {
        val t = tokens(text)
        return table.entries.firstOrNull { (_, names) -> names.any { containsPhrase(t, tokens(it)) } }?.key
    }
}
