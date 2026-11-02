package app.expenses.core.domain

/**
 * What the words of a payee's name say when the name is not in the starter list ([DefaultRules]): "SAI BABA TEA STALL" is food, "NEW BOMBAY MEDICALS" health, "SUNRISE HOSPITAL" health.
 * Only whole words of the payee are read (never the bank's text), and only when they agree: a name that holds words of two different categories ("Tea Estate Hospital") gives nothing. It is a
 * guess from a name, so the caller treats it as one to check (see [SuggestionConfidence]), below the person's own choices and the starter list. The result is category names, best first,
 * with the coarse starter as a fallback for a person who does not have the newer category.
 */
object PayeeWords {
    private val words: Map<String, List<String>> = buildMap {
        fun add(names: List<String>, vararg w: String) { for (x in w) put(x, names) }
        add(listOf("Food"), "restaurant", "restaurants", "cafe", "cafes", "bistro", "tiffin", "mess", "bakery", "bakers", "sweets", "dhaba", "biryani", "kitchen", "pizza", "burger", "tea", "chai", "coffee", "juice", "snacks", "canteen", "caterers", "catering", "dosa", "idli", "meals", "foodcourt", "icecream", "ice")
        add(listOf("Groceries"), "kirana", "grocery", "groceries", "provision", "provisions", "supermarket", "dairy", "milk", "vegetables", "fruits", "veg")
        add(listOf("Health"), "pharmacy", "pharma", "medical", "medicals", "medico", "hospital", "hospitals", "clinic", "clinics", "diagnostics", "diagnostic", "dental", "dentist", "chemist", "healthcare", "nursing", "optical", "opticals")
        add(listOf("Fuel", "Transport"), "petrol", "fuel", "diesel")
        add(listOf("Transport"), "metro", "cabs", "taxi", "parking", "toll", "fastag", "autos")
        add(listOf("Personal care", "Shopping"), "salon", "spa", "parlour", "parlor", "barber", "beauty", "cosmetics")
        add(listOf("Education", "Other"), "school", "college", "university", "academy", "tuition", "institute", "classes", "coaching")
        add(listOf("Insurance", "Bills"), "insurance", "assurance")
        add(listOf("Loan EMI", "Bills"), "finserv", "emi", "loan", "loans")
        add(listOf("Utilities", "Bills"), "electricity", "jal")
        add(listOf("Internet and mobile", "Bills"), "broadband", "fibernet", "fiber", "telecom")
        add(listOf("Travel", "Transport"), "airlines", "airways", "travels", "tours", "railways", "holidays")
        add(listOf("Fun"), "cinemas", "cinema", "multiplex", "theatre", "theater", "gaming", "games")
        add(listOf("Shopping"), "fashion", "garments", "textiles", "apparel", "footwear", "jewellers", "jewellery", "electronics", "mobiles", "furniture", "boutique")
        add(listOf("Gifts and donations", "Other"), "temple", "trust", "foundation", "donation", "charity", "ngo")
        add(listOf("Investments"), "broking", "securities", "mutual", "demat")
    }

    /** The word that decided, with the category names best first, or null when no word applies or the words disagree. */
    fun match(payee: String): Pair<String, List<String>>? {
        val hits = DefaultRules.tokens(payee).mapNotNull { w -> words[w]?.let { w to it } }
        if (hits.isEmpty()) return null
        if (hits.map { it.second.first() }.distinct().size > 1) return null
        return hits.first()
    }

    fun categoryNamesFor(payee: String): List<String> = match(payee)?.second.orEmpty()
}
