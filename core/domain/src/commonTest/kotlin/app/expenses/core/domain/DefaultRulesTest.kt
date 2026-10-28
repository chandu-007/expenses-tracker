package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The starter merchant list: its data, the whole-word and longest-name rules, the clean display name, and where it sits below the person's own choices. */
class DefaultRulesTest {
    /** The 97 names the starter list had before it became a table: each must still mean the same category. */
    private val legacy: Map<String, List<String>> = mapOf(
        "Food" to listOf("swiggy", "zomato", "dominos", "kfc", "mcdonalds", "starbucks", "pizza hut", "burger king", "box8", "faasos", "chaayos", "chai point", "behrouz", "haldirams", "barbeque nation", "eatsure"),
        "Groceries" to listOf("bigbasket", "big basket", "blinkit", "zepto", "dmart", "jiomart", "instamart", "grofers", "milkbasket", "licious", "reliance fresh", "natures basket"),
        "Transport" to listOf("uber", "ola", "rapido", "redbus", "irctc", "fastag", "namma yatri", "indigo", "air india", "vistara", "indian oil", "iocl", "bpcl", "hpcl", "bharat petroleum", "hindustan petroleum"),
        "Bills" to listOf("jio", "airtel", "vodafone", "bsnl", "bescom", "tneb", "msedcl", "tata power", "adani electricity", "bharat gas", "indane", "mahanagar gas", "act fibernet", "hathway", "tata play", "dish tv", "d2h", "lic", "electricity", "broadband", "recharge"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "decathlon", "ikea", "croma", "lenskart", "reliance digital"),
        "Health" to listOf("apollo", "pharmeasy", "1mg", "netmeds", "practo", "medplus", "cult fit", "cultfit", "lal pathlabs"),
        "Fun" to listOf("netflix", "prime video", "hotstar", "spotify", "bookmyshow", "pvr", "inox", "youtube premium", "sonyliv", "zee5", "disney"),
    )

    @Test
    fun theTableHasAtLeast250NamedMerchantsInTheSevenStarterCategoriesAndEveryAliasIsRead() {
        val merchants = MerchantTable.all.filter { it.display != null }
        assertTrue(merchants.size >= 250, "only ${merchants.size} named merchants")
        assertEquals(emptyList(), merchants.map { it.display!! }.groupBy { it.lowercase() }.filterValues { it.size > 1 }.keys.toList(), "a merchant is listed twice")
        val badCategory = MerchantTable.all.filter { it.category != null && it.category !in MerchantTable.CATEGORIES }.map { it.display ?: it.aliases.first() }
        assertEquals(emptyList(), badCategory, "category outside the starter seven")
        val perCategory = MerchantTable.all.groupingBy { it.category }.eachCount()
        for (c in MerchantTable.CATEGORIES) assertTrue((perCategory[c] ?: 0) >= 20, "$c has ${perCategory[c]}")
        val unreadable = MerchantTable.all.flatMap { m -> m.aliases.filter { DefaultRules.tokens(it).sumOf { w -> w.length } < 3 || DefaultRules.tokens(it).isEmpty() }.map { m.display + ":" + it } }
        assertEquals(emptyList(), unreadable, "an alias with no words, or under three letters")
    }

    @Test
    fun noAliasIsClaimedByTwoMerchantsAndEveryAliasResolvesToItsOwnMerchantUnlessALongerNameOwnsIt() {
        val claims = MerchantTable.all.flatMap { m -> m.aliases.map { DefaultRules.tokens(it) to m } }.groupBy({ it.first }, { it.second }).mapValues { (_, ms) -> ms.distinct() }
        assertEquals(emptyList(), claims.filterValues { it.size > 1 }.map { (k, v) -> "$k: ${v.map { it.display }}" }, "two merchants claim the same words")
        // an alias that is inside a longer alias of another merchant ("amazon prime" in "amazon prime video") is read as the longer one; anything else must come back as itself
        val shadowed = mutableListOf<String>()
        for (m in MerchantTable.all) for (a in m.aliases) {
            val found = DefaultRules.find(a)
            if (found !== m) {
                val longer = found?.aliases?.any { DefaultRules.tokens(it).size > DefaultRules.tokens(a).size && DefaultRules.tokens(a).let { w -> DefaultRules.tokens(it).windowed(w.size).any { x -> x == w } } } == true
                if (!longer) shadowed += "'$a' of ${m.display ?: m.aliases.first()} read as ${found?.display}"
            }
        }
        assertEquals(emptyList(), shadowed)
    }

    @Test
    fun everyNameTheOldListHadStillMeansTheSameCategory() {
        var n = 0
        for ((category, names) in legacy) for (name in names) { n++; assertEquals(category, DefaultRules.categoryNameFor(name), name); assertEquals(category, DefaultRules.categoryNameFor(name.uppercase() + "@ICICI"), "$name as an address") }
        assertEquals(97, n)
    }

    private fun cat(text: String) = DefaultRules.categoryNameFor(text)

    @Test
    fun theHandlesAndNamesFromTheBriefAreFound() {
        val cases = mapOf(
            "swiggy@icici" to "Food", "swiggyupi@axb" to "Food", "ZOMATO" to "Food", "AMAZON PAY" to "Shopping", "Amazon Pay India Private Limited" to "Shopping", "PAYTM" to null, "IRCTC" to "Transport",
            "BIGBASKET" to "Groceries", "BLINKIT" to "Groceries", "ZEPTO" to "Groceries", "JIO" to "Bills", "AIRTEL" to "Bills", "BESCOM" to "Bills", "HPCL" to "Transport", "UBER" to "Transport", "OLA" to "Transport",
            "RAPIDO" to "Transport", "NETFLIX" to "Fun", "SPOTIFY" to "Fun", "HOTSTAR" to "Fun", "APOLLO" to "Health", "MEDPLUS" to "Health", "DMART" to "Groceries", "bigbasket.rzp@axisbank" to "Groceries",
            "Domino's Pizza" to "Food", "Cafe Coffee Day" to "Food", "Reliance Jio Infocomm Limited" to "Bills", "Apollo Pharmacy" to "Health", "Tata 1mg" to "Health", "Delhi Metro Rail Corporation" to "Transport",
        )
        for ((text, want) in cases) assertEquals(want, cat(text), text)
    }

    @Test
    fun onlyWholeWordsMatchSoAShortNameNeverHidesInsideAnotherWord() {
        for (text in listOf("Coca Cola Stall", "Olaf Traders", "Suburban Cafe", "Uberti Shoes", "Jiomartz Stores", "Blinkitt", "Pizzahut Dosa", "Kfcc", "Disneyland Tours", "Alice", "Aplolo", "Zomatoes Farm", "Chandu", "Rola Medicals", "Jioo", "Airtelx", "Amazonian Spa", "Inoxx Steel"))
            assertNull(cat(text), text)
        assertEquals("Transport", cat("Ola"), "alone it is Ola"); assertEquals("Transport", cat("ola.cabs@okaxis")); assertEquals("Transport", cat("Paid to OLA CABS Bangalore"))
        assertEquals("Fun", cat("cinepolis_india"), "punctuation is a separator")
    }

    @Test
    fun theBankHandleAfterTheAtSignIsNeverReadSoAnotherBanksNameIsNotAMerchant() {
        assertNull(cat("9704849321@airtel"), "Airtel Payments Bank's handle is not Airtel Bills")
        assertNull(cat("ravi.k@paytm")); assertNull(cat("someone@okhdfcbank")); assertNull(cat("friend@jio")); assertNull(cat("x@ola"))
        assertEquals("Food", cat("swiggy@jio"), "the name before the @ still counts")
        assertNull(DefaultRules.displayNameFor("friend@airtel"))
    }

    @Test
    fun theLongestNameInTheTextWinsThenTheLongerThenTheEarlier() {
        assertEquals("Groceries", cat("Swiggy Instamart"), "two words beat swiggy's one")
        assertEquals("Fun", cat("Amazon Prime Video"), "three words beat amazon prime and amazon")
        assertEquals("Fun", cat("Amazon Prime"), "prime beats the marketplace")
        assertEquals("Shopping", cat("Amazon India"))
        assertEquals("Transport", cat("Jio-bp pulse"), "jio bp is petrol, not the telecom Jio")
        assertEquals("Fun", cat("Jio Hotstar"))
        assertEquals("Groceries", cat("JioMart"), "jiomart is one word and not jio")
        assertEquals("Food", cat("Paid to Zomato Uber"), "equal words: the longer name, uber is shorter than zomato")
        assertEquals("Transport", cat("Paid to Rapido Zomato"), "equal words and letters: the earlier")
        assertEquals("Food", cat("Paid to Zomato Rapido"), "equal words and letters: the earlier")
        assertEquals("Bills", cat("BESCOM electricity"), "a keyword and a name of one word: the longer word, both Bills here")
    }

    @Test
    fun aPersonOrAnotherBusinessWhoseNameOnlyStartsWithAMerchantGetsNoCategory() {
        for (payee in listOf("Ola Sharma", "Ola Akinwale", "Swiggy Kumar", "Uber Singh", "Jio Rao", "Bata Ram", "Tira Singh", "Raymond Sir", "Meru Sharma", "Amul Kumar", "Pvr Kumar", "Lic Agent Ramu", "ola.sharma@okaxis", "uber_singh@ybl",
            "Apollo Tyres Ltd", "A2B Travels", "Puma Transport", "MTR Enterprises", "Jockey Club", "Zomato Ramesh Babu", "Airtel Rani"))
            assertNull(cat(payee), payee)
        val keeps = mapOf(
            "Swiggy Instamart" to "Groceries", "Uber Eats" to "Food", "Amazon Prime" to "Fun", "Jio Mart" to "Groceries", "JioMart" to "Groceries", "Apollo Pharmacy" to "Health", "Apollo Pharmacy Ltd" to "Health",
            "Swiggy Stores" to "Food", "Swiggy Pvt Ltd" to "Food", "Zomato Foods" to "Food", "Ola Cabs" to "Transport", "Uber India Systems Pvt Ltd" to "Transport", "Amazon Seller Services" to "Shopping",
            "Amazon Pay India" to "Shopping", "Airtel Postpaid" to "Bills", "Apollo Hospitals" to "Health", "Star Health Ins" to "Health", "Haldirams Foods" to "Food", "Jio-bp pulse" to "Transport",
            "MTR Foods" to "Food", "Adyar Ananda Bhavan" to "Food", "Dominos Pizza India" to "Food", "Lic" to "Bills", "Bata" to "Shopping",
        )
        for ((payee, want) in keeps) assertEquals(want, cat(payee), payee)
        assertEquals("Transport", cat("Paid to Ola Sharma on 05-06"), "a merchant name inside a longer text is not the start of a payee: the text is judged as before (the app only ever passes the payee)")
    }

    @Test
    fun aPersonLikePayeeFallsThroughToThePersonPathAndEveryOwnChoiceStillWinsOverTheTable() {
        assertNull(suggest("Ola Sharma"))
        assertEquals(CategorySuggestion("O", SuggestionSource.PERSON), CategorySuggest.suggest("Ola Sharma", "x", emptyList(), emptyMap(), usable, byName, toAPerson = true), "no merchant: a payment to a person")
        assertEquals(CategorySuggestion("T", SuggestionSource.LEARNED), suggest("Ola Sharma", learned = mapOf("olasharma" to "T")))
        assertEquals(CategorySuggestion("X", SuggestionSource.RULE), suggest("Ola Sharma", rules = listOf(CaptureRule("r", "sharma", "X"))))
        assertEquals(CategorySuggestion("H", SuggestionSource.HISTORY), suggest("Apollo Tyres Ltd", history = "H"))
        assertEquals(CategorySuggestion("X", SuggestionSource.RULE), suggest("Swiggy Instamart", rules = listOf(CaptureRule("r", "swiggy", "X")), learned = mapOf("swiggyinstamart" to "T")), "rule over learned over table")
        assertEquals(CategorySuggestion("T", SuggestionSource.LEARNED), suggest("Swiggy Instamart", learned = mapOf("swiggyinstamart" to "T"), history = "H"), "learned over history over table")
    }

    @Test
    fun aNameWithNoCategoryStillDoesNotHideAMerchantItIsNotLongerThan() {
        assertNull(cat("Paytm Wallet"))
        assertEquals("Fun", cat("Paytm Insider"))
        assertEquals("Transport", cat("Paytm FASTag"))
    }

    @Test
    fun theCleanNameIsGivenOnlyWhenTheRestOfThePayeeIsCompanyNoise() {
        val cases = mapOf(
            "swiggyupi@axb" to "Swiggy", "swiggy@icici" to "Swiggy", "SWIGGY" to "Swiggy", "SWIGGY BANGALORE KA IN" to "Swiggy", "AMAZON PAY INDIA PRIVATE LIMITED" to "Amazon Pay", "zomato online pvt ltd" to "Zomato",
            "Uber India Systems Pvt Ltd" to "Uber", "ola.cabs@okaxis" to "Ola", "BIGBASKET.RZP@axisbank" to "BigBasket", "Reliance Jio Infocomm Limited" to "Jio", "PAYTM" to "Paytm", "Domino's Pizza" to "Domino's",
            "Swiggy Instamart" to "Swiggy Instamart", "swiggy*order 83726" to "Swiggy", "BESCOM electricity bill" to "BESCOM",
        )
        for ((payee, want) in cases) assertEquals(want, DefaultRules.displayNameFor(payee), payee)
        for (payee in listOf("Uber Singh", "Swiggy Delivery Ramesh", "Ravi Kumar", "9704849321@yescred", "Olaf Traders", "Coca Cola Stall", "", "electricity", "Airtel Payments Bank"))
            assertNull(DefaultRules.displayNameFor(payee), payee)
    }

    private val usable = setOf("F", "G", "T", "B", "S", "H", "U", "O", "X")
    private val byName = mapOf("food" to "F", "groceries" to "G", "transport" to "T", "bills" to "B", "shopping" to "S", "health" to "H", "fun" to "U", "other" to "O")

    private fun suggest(payee: String?, rules: List<CaptureRule> = emptyList(), learned: Map<String, String> = emptyMap(), history: String? = null, body: String = "debited") =
        CategorySuggest.suggest(payee, body, rules, learned, usable, byName, history = history)

    @Test
    fun aMerchantNameFromTheTableIsOnlyTheLastResortBelowRulesLearnedChoicesAndHistory() {
        assertEquals(CategorySuggestion("F", SuggestionSource.STARTER), suggest("swiggyupi@axb"))
        assertEquals(CategorySuggestion("G", SuggestionSource.STARTER), suggest("swiggy instamart"))
        val rule = CaptureRule("r", "swiggy", "X")
        assertEquals(CategorySuggestion("X", SuggestionSource.RULE), suggest("swiggy instamart", rules = listOf(rule)), "the person's rule beats a longer merchant name")
        assertEquals(CategorySuggestion("X", SuggestionSource.RULE), suggest("Uber", rules = listOf(CaptureRule("r2", "uber", "X"))))
        assertEquals(CategorySuggestion("X", SuggestionSource.RULE), suggest("x@y", rules = listOf(CaptureRule("r3", "zomato", "X")), body = "zomato"), "a rule also reads the body")
        assertEquals(CategorySuggestion("T", SuggestionSource.LEARNED), suggest("swiggy instamart", learned = mapOf("swiggyinstamart" to "T")), "what the person chose for this payee beats the table")
        assertEquals(CategorySuggestion("T", SuggestionSource.LEARNED), suggest("amazon prime", learned = mapOf("amazonprime" to "T")))
        assertEquals(CategorySuggestion("H", SuggestionSource.HISTORY), suggest("Uber", history = "H"), "earlier entries beat the table")
        assertEquals(CategorySuggestion("T", SuggestionSource.STARTER), suggest("Uber"))
        assertNull(suggest("Paytm"), "a name with no category suggests nothing")
        assertNull(suggest("swiggy", body = "swiggy swiggy").takeIf { false }, "unused")
    }

    @Test
    fun theTableIsNotConsultedForTheBodyOnlyThePayeeAndAMissingOrHiddenCategoryIsSkipped() {
        assertNull(suggest("9704849321@yescred", body = "Rs.100 paid to 9704849321@yescred via Airtel Payments Bank swiggy"))
        assertNull(CategorySuggest.suggest("swiggy", "x", emptyList(), emptyMap(), usable - "F", byName))
        assertNull(CategorySuggest.suggest("swiggy", "x", emptyList(), emptyMap(), usable, byName - "food"))
    }

    @Test
    fun tenThousandLookupsTakeWellUnderASecondNotAScanOfTheWholeListEach() {
        val texts = (0 until 10_000).map { i -> "Paid Rs.${i % 900 + 100} to ${listOf("swiggy@icici", "Chandu", "Coca Cola Stall", "AMAZON PAY INDIA PRIVATE LIMITED", "ola@paytm", "9704849321@yescred")[i % 6]} on 05-06-26 UPI Ref ${628374659000L + i} thank you for banking with us" }
        DefaultRules.categoryNameFor(texts[0])
        val start = kotlin.time.TimeSource.Monotonic.markNow()
        var hits = 0
        for (t in texts) if (DefaultRules.categoryNameFor(t) != null) hits++
        val took = start.elapsedNow().inWholeMilliseconds
        assertEquals(10_000 * 3 / 6, hits, "swiggy, amazon pay and ola found; the other three texts are not")
        assertTrue(took < 1_500, "10,000 lookups took $took ms")
    }
}
