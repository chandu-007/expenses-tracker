package app.expenses.core.domain

/**
 * What a category looks like (UI S3, backlog U-03): a glyph on a tile of one of 12 stable colour slots. Pure and the same on both phones.
 *
 * - The **slot** comes from the category's id, never its name, so renaming a category never recolours it. The starter categories have fixed slots (the first eight, Food to Other, all differ and the
 *   first seven slots are the Okabe-Ito colour-blind-safe hues); a category the person made takes a stable slot from the id ([slotOf]). The person's own colour ([Category.colorArgb]) wins when set.
 * - The **glyph** is a key into the glyph set the UI draws from Material icons ([GLYPHS]); [defaultGlyph] picks one from the name (a starter, or a few common words), else a plain tag. The person can choose another.
 * - A tile is a tonal container with the glyph in a darker shade of the same hue, for light and dark ([tileFor]); every pair keeps at least 3:1 between glyph and container (host-tested).
 */
object CategoryLook {
    const val SLOTS = 12

    /** The setting a glyph the person chose is kept under: `cat_glyph:<category id>` = a key of [GLYPHS]. No glyph chosen: no setting, and [defaultGlyph] applies. */
    const val GLYPH_PREFIX = "cat_glyph:"

    /** The 12 hues as 0xRRGGBB: the seven Okabe-Ito colours (orange, sky blue, bluish green, yellow, blue, vermillion, reddish purple), then slate, indigo, teal, olive and brown. */
    val SLOT_RGB: IntArray = intArrayOf(
        0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0x0072B2, 0xD55E00, 0xCC79A7,
        0x6B7B8C, 0x5C6BC0, 0x00838F, 0x9E9D24, 0x8D6E63,
    )

    /** A tile's two colours as 0xRRGGBB: [container] behind, [glyph] drawn on it. */
    data class Tile(val container: Int, val glyph: Int)

    /** The slot the starter categories have, by their fixed id; null for any other id. */
    private val STARTER_SLOTS: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        // Food orange, Groceries green, Transport sky blue, Bills yellow, Shopping purple, Health vermillion, Fun blue, Other slate: the eight that were always first all differ
        val first = listOf("Food" to 0, "Groceries" to 2, "Transport" to 1, "Bills" to 3, "Shopping" to 6, "Health" to 5, "Fun" to 4, "Other" to 7)
        for ((n, s) in first) m[StarterCategories.expenseId(n)] = s
        val more = listOf(
            "Rent" to 11, "Utilities" to 3, "Internet and mobile" to 8, "Insurance" to 9, "Education" to 4, "Fuel" to 0, "Travel" to 1, "Subscriptions" to 6, "Investments" to 2,
            "Loan EMI" to 5, "Personal care" to 10, "Gifts and donations" to 6, "Taxes" to 7, "Fees and charges" to 11, "Cash withdrawal" to 9,
        )
        for ((n, s) in more) m[StarterCategories.expenseId(n)] = s
        val income = listOf(
            "Salary" to 2, "Interest" to 1, "Gifts received" to 6, "Refunds and cashback" to 0, "Other income" to 7, "Dividends" to 4, "Rent received" to 11, "Reimbursement" to 9, "Freelance and business" to 8,
        )
        for ((n, s) in income) m[StarterCategories.incomeId(n)] = s
        m
    }

    /** A stable slot for any text (FNV-1a, so the same on every platform and run). */
    fun slotOf(key: String): Int {
        var h = 0x811C9DC5.toInt()
        for (c in key) { h = h xor c.code; h *= 0x01000193 }
        return (h and 0x7FFFFFFF) % SLOTS
    }

    /** The slot of the category with this [id]: a starter's fixed one, else one from the id. */
    fun slot(id: String): Int = STARTER_SLOTS[id] ?: slotOf(id)

    /** The tile colours of [slot] in the light or dark theme. */
    fun tileFor(slot: Int, dark: Boolean): Tile = tileOfHue(SLOT_RGB[slot.mod(SLOTS)], dark)

    /** The tile colours for a colour the person chose for the category (0xRRGGBB). */
    fun tileOfHue(rgb: Int, dark: Boolean): Tile =
        if (dark) Tile(mix(DARK_BASE, rgb, 0.30), mix(rgb, 0xFFFFFF, 0.45)) else Tile(mix(0xFFFFFF, rgb, 0.22), mix(rgb, 0x000000, 0.55))

    private const val DARK_BASE = 0x1D1B20

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** The glyphs a category can have, by key, in the order the picker shows them. The UI maps each key to a Material icon. */
    val GLYPHS: List<String> = listOf(
        "restaurant", "cafe", "cart", "bag", "bus", "car", "fuel", "train", "flight", "hotel", "beach", "home", "bolt", "water", "wifi", "phone",
        "receipt", "card", "payments", "savings", "bank", "trend", "percent", "shield", "medical", "fitness", "spa", "pets", "child", "school", "book", "movie",
        "music", "games", "gift", "heart", "work", "build", "subscriptions", "swap", "sell", "label",
    )

    /** The glyph a category with this [name] starts with (for the starters, and a few common words), else a plain tag. */
    fun defaultGlyph(name: String): String {
        val n = name.trim().lowercase()
        STARTER_GLYPHS[n]?.let { return it }
        for ((word, glyph) in WORD_GLYPHS) if (word in n) return glyph
        return "label"
    }

    private val STARTER_GLYPHS = mapOf(
        "food" to "restaurant", "groceries" to "cart", "transport" to "bus", "bills" to "receipt", "shopping" to "bag", "health" to "medical", "fun" to "movie", "other" to "label",
        "rent" to "home", "utilities" to "bolt", "internet and mobile" to "phone", "insurance" to "shield", "education" to "school", "fuel" to "fuel", "travel" to "flight",
        "subscriptions" to "subscriptions", "investments" to "trend", "loan emi" to "bank", "personal care" to "spa", "gifts and donations" to "gift", "taxes" to "percent",
        "fees and charges" to "card", "cash withdrawal" to "payments", "salary" to "work", "interest" to "savings", "gifts received" to "gift", "refunds and cashback" to "sell",
        "other income" to "payments", "dividends" to "trend", "rent received" to "home", "reimbursement" to "swap", "freelance and business" to "work",
    )

    /** Words in a category's name that suggest a glyph, in the order they are tried. */
    private val WORD_GLYPHS = listOf(
        "coffee" to "cafe", "tea" to "cafe", "cafe" to "cafe", "restaurant" to "restaurant", "dining" to "restaurant", "eating" to "restaurant", "lunch" to "restaurant", "dinner" to "restaurant",
        "grocer" to "cart", "vegetable" to "cart", "pet" to "pets", "vet" to "pets", "gym" to "fitness", "fitness" to "fitness", "sport" to "fitness", "kid" to "child", "baby" to "child",
        "school" to "school", "college" to "school", "book" to "book", "movie" to "movie", "cinema" to "movie", "music" to "music", "game" to "games", "gift" to "gift", "doctor" to "medical",
        "medic" to "medical", "pharma" to "medical", "hospital" to "medical", "fuel" to "fuel", "petrol" to "fuel", "diesel" to "fuel", "taxi" to "car", "cab" to "car", "car" to "car", "bike" to "car",
        "bus" to "bus", "metro" to "train", "train" to "train", "flight" to "flight", "hotel" to "hotel", "holiday" to "beach", "trip" to "beach", "internet" to "wifi", "wifi" to "wifi",
        "mobile" to "phone", "phone" to "phone", "electric" to "bolt", "power" to "bolt", "water" to "water", "rent" to "home", "house" to "home", "home" to "home", "repair" to "build", "salon" to "spa",
        "loan" to "bank", "bank" to "bank", "tax" to "percent", "insur" to "shield", "invest" to "trend", "saving" to "savings", "shop" to "bag", "cloth" to "bag",
    )
}
