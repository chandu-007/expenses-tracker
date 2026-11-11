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
 *
 * Two names of one merchant are counted as one in two layers: the built-in same-merchant table ([MerchantAliases], applied inside [of], where wording is unambiguous and the starter category does
 * not change: "Amazon Pay" is "Amazon") and the person's own aliases ([PayeeAliases], applied by the overloads that take them: "Swiggy Instamart" is "Swiggy" when the person said so). What was
 * learned under a name before it was merged is still found ([ownKey], then the members of the shop).
 */
object PayeeKey {
    /** The key [payee] is remembered under, with the built-in same-merchant table applied. Null when there is nothing usable to key on. */
    fun of(payee: String?): String? = ownKey(payee)?.let(MerchantAliases::canonicalKey)

    /** [of] with the person's own aliases followed: the key of the shop [payee] counts as. */
    fun of(payee: String?, aliases: PayeeAliases): String? = of(payee)?.let(aliases::resolve)

    /** The key before the built-in same-merchant table: what a known merchant was remembered under until then ("amazonpay" for "Amazon Pay"). */
    fun ownKey(payee: String?): String? {
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

    /**
     * The key a learned choice for [payee] is stored under in [learned]: the shop's own key ([of] with [aliases] followed), else the key before the built-in merge, else the old letters-and-digits key,
     * else any other name that counts as the same shop (in a stable order). Null when nothing was learned for it.
     */
    fun lookupKey(payee: String?, learned: Map<String, String>, aliases: PayeeAliases = PayeeAliases.NONE): String? {
        val shop = of(payee, aliases)
        if (shop != null && shop in learned) return shop
        ownKey(payee)?.takeIf { it in learned }?.let { return it }
        legacy(payee)?.takeIf { it in learned }?.let { return it }
        if (shop != null && !aliases.isEmpty) aliases.group(shop).firstOrNull { it in learned }?.let { return it }
        if (shop != null) MerchantAliases.groupKeys(shop).firstOrNull { it in learned }?.let { return it }
        return null
    }

    /** The category learned for [payee]: under the key of today, else under an older one, else under another name of the same shop. */
    fun lookup(payee: String?, learned: Map<String, String>, aliases: PayeeAliases = PayeeAliases.NONE): String? = lookupKey(payee, learned, aliases)?.let { learned[it] }

    /** Choices for MONEY IN are kept apart from choices for money out under the same table: the key of an income choice starts with this. A payer is not a payee ("Ravi" paying you is not you paying "Ravi"). */
    const val INCOME_PREFIX = "in:"

    /** The key a payer's income category is remembered under: [of] with [INCOME_PREFIX], and the person's own aliases followed when given. */
    fun incomeOf(payer: String?, aliases: PayeeAliases = PayeeAliases.NONE): String? = of(payer, aliases)?.let { INCOME_PREFIX + it }

    /** The key an income choice for [payer] is stored under in [learned]: the shop's own, else any other name of the same shop. Null when none. */
    fun lookupIncomeKey(payer: String?, learned: Map<String, String>, aliases: PayeeAliases = PayeeAliases.NONE): String? {
        val shop = of(payer, aliases) ?: return null
        (INCOME_PREFIX + shop).takeIf { it in learned }?.let { return it }
        ownKey(payer)?.let { INCOME_PREFIX + it }?.takeIf { it in learned }?.let { return it }
        if (!aliases.isEmpty) aliases.group(shop).firstNotNullOfOrNull { k -> (INCOME_PREFIX + k).takeIf { it in learned } }?.let { return it }
        return MerchantAliases.groupKeys(shop).firstNotNullOfOrNull { k -> (INCOME_PREFIX + k).takeIf { it in learned } }
    }

    /** The income category learned for [payer]; never a choice made for the same name as an expense payee. */
    fun lookupIncome(payer: String?, learned: Map<String, String>, aliases: PayeeAliases = PayeeAliases.NONE): String? = lookupIncomeKey(payer, learned, aliases)?.let { learned[it] }

    fun isIncomeKey(key: String): Boolean = key.startsWith(INCOME_PREFIX)

    /** The payees that sell more than one kind of thing, or stand for many sellers: a marketplace, a wallet, an app store. A category learned for them is a hint, not a rule. */
    private val mixed = setOf(
        "amazon", "amazonpay", "paytm", "paytmwallet", "phonepe", "googlepay", "gpay", "googleplay", "google", "cred", "mobikwik", "freecharge", "bhim", "flipkart", "reliance", "tata", "apple",
        "walmart", "ebay", "olx", "snapdeal",
    )

    /** Whether [payee] is one of [mixed], or counts as one through the person's [aliases] ("Amazon Pay" is "Amazon" without them; a name the person said is the same shop as one is mixed too). */
    fun isMixed(payee: String?, aliases: PayeeAliases = PayeeAliases.NONE): Boolean {
        val key = of(payee) ?: return false
        return key in mixed || (!aliases.isEmpty && aliases.group(key).any { it in mixed })
    }
}
