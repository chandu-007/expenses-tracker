package app.expenses.core.domain

/**
 * The built-in "same merchant" table: names the starter list ([MerchantTable]) holds as separate merchants because each is worded differently, but that are ONE merchant to the person
 * (a streaming service that was renamed or merged into another, a wallet that is the merchant's own, two cinema chains that merged). Each group names one canonical merchant and the others
 * that count as it. **Written from public knowledge of the brands, not from anyone's transactions.**
 *
 * A group is only here where the wording is unambiguous AND the starter category does not change: every member has the same category and the same better home as the canonical one
 * (`MerchantAliasesTest` refuses a group that would move a suggestion). So merging can pool what the person learned and what their earlier entries say, and never turns a Groceries
 * payee into Food. Merchants of one company that are filed differently stay apart on purpose: "Swiggy Instamart" (Groceries) is not "Swiggy" (Food), "Amazon Fresh" is not "Amazon", "Jio-bp"
 * and "JioMart" are not "Jio", "Uber Eats" is not "Uber". A person who files them as one says so with a user alias ([PayeeAliases]).
 */
internal object MerchantAliases {
    /** The canonical merchant, then the other starter-list names that count as it. */
    val groups: List<Pair<String, List<String>>> = listOf(
        // Amazon Pay is Amazon's own wallet and UPI: its card and bank wording ("AMAZON PAY INDIA") and "AMZN" are the one marketplace-and-wallet payee, which is a mixed payee either way (always a check)
        "Amazon" to listOf("Amazon Pay"),
        // one membership: the video and the Prime plan are billed as the same subscription
        "Amazon Prime" to listOf("Prime Video"),
        // JioCinema and Disney+ Hotstar became one service, JioHotstar; Voot's shows moved into it
        "JioHotstar" to listOf("Disney+ Hotstar", "JioCinema", "Voot"),
        // a FASTag recharge through Paytm is the same FASTag
        "FASTag" to listOf("Paytm FASTag"),
        // PVR and INOX merged into one cinema company
        "PVR" to listOf("INOX"),
        // Vistara's flights are Air India's since the merger
        "Air India" to listOf("Vistara"),
        "ixigo" to listOf("IXIGO Trains"),
    )

    private fun key(display: String) = CategorySuggest.merchantKey(display)

    /** The key of a member to the key of its canonical merchant. */
    private val canonical: Map<String, String> by lazy {
        buildMap { for ((head, others) in groups) for (o in others) put(key(o), key(head)) }
    }

    /** The members of each canonical key (not the canonical one itself). */
    private val members: Map<String, List<String>> by lazy {
        buildMap<String, MutableList<String>> { for ((head, others) in groups) getOrPut(key(head)) { ArrayList(others.size) }.addAll(others.map(::key)) }
    }

    /** [payeeKey] as its canonical merchant's key when it is a member of a group, else itself. */
    fun canonicalKey(payeeKey: String): String = canonical[payeeKey] ?: payeeKey

    /** Whether [payeeKey] is in a group, as the canonical merchant or as a member. */
    fun inGroup(payeeKey: String): Boolean = payeeKey in canonical || payeeKey in members

    /** The keys of every member of the group [payeeKey] is in, the canonical one included (just [payeeKey] when it is in none). */
    fun groupKeys(payeeKey: String): List<String> {
        val head = canonicalKey(payeeKey)
        return listOf(head) + members[head].orEmpty()
    }
}
