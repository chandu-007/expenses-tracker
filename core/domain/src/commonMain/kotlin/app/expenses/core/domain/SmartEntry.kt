package app.expenses.core.domain

/**
 * N-01, the smart entry line on the Add form: one line typed the way people say it ("chai 20", "auto ke 80 diye", "uber 245.50 yesterday", "rent 18000 hdfc") read into
 * the parts of an expense: the amount, the note or payee, category names to try, a day ("today", "yesterday", "kal" only when the line says it is past) and an account
 * named in it. Pure and quick (a line is at most [MAX_LENGTH] characters); it only reads: the form shows what it found as a preview the person taps to fill the form,
 * and nothing is ever saved from here.
 *
 * The amount: digits with Indian (1,00,000) or thousands (100,000) grouping, decimals up to the currency's places, "2k" / "2.5k" / "2 thousand" / "1.5 lakh", a
 * symbol or "rs" before or after it ("₹20", "rs.20", "20rs", "20/-"), or a sum ("120+45", "20x2"). Anything else is refused rather than guessed: two numbers with
 * neither marked as money ("2 samosa 40"), a grouping that is no grouping ("12,34" could be a decimal comma), more decimals than the currency has, zero.
 * When exactly one of several numbers carries a currency mark it is the amount ("2 samosa ₹40"). Common Hinglish and English filler words ("ke", "diye", "ko",
 * "liye", "for", "paid") are left out of the note.
 */
object SmartEntry {
    /** Longer text than this is not read at all (a pasted paragraph is not one expense). */
    const val MAX_LENGTH = 200

    /** The note is cut to this many characters, at a word. */
    const val MAX_NOTE = 60

    enum class Problem { EMPTY, TOO_LONG, NO_AMOUNT, TWO_AMOUNTS, BAD_AMOUNT }

    /** An account the line may name. */
    data class AccountName(val id: String, val name: String)

    /**
     * What a line says. [amountMinor] is null with a [problem]. [daysAgo] is 0 for today, 1 for yesterday, 2 for the day before, null when no day was named (the
     * form keeps its own date); [dateUnclear] is true when a day word could mean more than one day ("kal" is yesterday or tomorrow; "today" and "yesterday" together).
     * [categoryNames] are names to try in order (the caller takes the first the person has); [accountId] is the one account the line names.
     */
    data class Reading(
        val amountMinor: Long?,
        val note: String,
        val categoryNames: List<String>,
        val daysAgo: Int?,
        val dateUnclear: Boolean,
        val accountId: String?,
        val problem: Problem?,
    ) {
        val usable: Boolean get() = problem == null && amountMinor != null
    }

    private val FILLERS = setOf(
        // Hinglish
        "ke", "ka", "ki", "ko", "diye", "diya", "di", "dia", "liye", "liya", "lie", "le", "kiya", "kiye", "mein", "me", "pe", "par", "se", "wala", "wale", "waala", "waale", "wali",
        "tha", "thi", "gaye", "gaya", "gayi", "hua", "hue", "huye", "kharcha", "kharche", "kharch", "rupaye", "rupaiye", "rupay", "rupiya", "rupye",
        // English
        "rupees", "rupee", "rs", "inr", "₹", "bucks", "paid", "pay", "spent", "spend", "bought", "for", "on", "at", "to", "the", "a", "an", "of", "via", "from", "by", "using", "in",
    )

    /** Words that say the money already went, so "kal" is yesterday, not tomorrow. */
    private val PAST = setOf("diye", "diya", "di", "dia", "liya", "kiya", "kiye", "gaye", "gaya", "gayi", "tha", "thi", "hua", "hue", "huye", "paid", "spent", "bought", "kharcha", "kharche", "kharch", "gave")

    private val TODAY = setOf("today", "aaj", "tonight")
    private val YESTERDAY = setOf("yesterday", "yday", "ystrdy", "yest")

    /** Multipliers that follow a number as their own word. */
    private val MULTIPLIER = mapOf("k" to 3, "thousand" to 3, "hazar" to 3, "hazaar" to 3, "hajar" to 3, "lakh" to 5, "lakhs" to 5, "lac" to 5, "lacs" to 5)

    private val CURRENCY_PREFIXES = listOf("₹", "rs.", "rs", "inr")
    private val CURRENCY_SUFFIXES = listOf("/-", "₹", "rs.", "rs", "inr")

    // Starter category names, each word to the names to try (a newer starter first, then the original it used to fall under)
    private val KEYWORDS: Map<String, List<String>> = buildMap {
        fun put(names: List<String>, vararg words: String) = words.forEach { put(it, names) }
        put(listOf("Food"), "chai", "tea", "coffee", "breakfast", "lunch", "dinner", "snack", "snacks", "nashta", "naashta", "khana", "samosa", "biryani", "pizza", "burger", "dosa", "idli",
            "momos", "maggi", "juice", "lassi", "paratha", "thali", "canteen", "mess", "tiffin", "dhaba", "restaurant", "cafe", "sweets", "mithai", "chaat", "vada", "pav", "golgappe", "panipuri",
            "☕", "🍵", "🍕", "🍔", "🍜", "🍛", "🍚", "🥟", "🍩", "🍰", "🍦", "🥤", "🍺", "🍻", "🍷", "🍽")
        put(listOf("Groceries", "Food"), "grocery", "groceries", "kirana", "sabzi", "sabji", "vegetables", "veggies", "fruits", "fruit", "doodh", "milk", "atta", "chawal", "dal", "eggs", "anda",
            "bread", "ration", "🥦", "🥛", "🛒", "🍎", "🍌", "🥚")
        put(listOf("Transport"), "auto", "rickshaw", "cab", "taxi", "bus", "metro", "toll", "parking", "🛺", "🚕", "🚌", "🚇", "🚗")
        put(listOf("Fuel", "Transport"), "petrol", "diesel", "fuel", "cng", "⛽")
        put(listOf("Rent", "Bills"), "rent", "kiraya", "kiraaya")
        put(listOf("Utilities", "Bills"), "electricity", "bijli", "cylinder", "💡")
        put(listOf("Internet and mobile", "Bills"), "recharge", "wifi", "broadband", "internet", "📱")
        put(listOf("Health"), "medicine", "medicines", "dawai", "dawa", "doctor", "chemist", "pharmacy", "hospital", "clinic", "tablets", "💊", "🏥")
        put(listOf("Shopping"), "clothes", "kapde", "kapda", "shoes", "chappal", "shirt", "saree", "kurta", "shopping", "👕", "👗", "👟", "🛍")
        put(listOf("Fun"), "movie", "movies", "cinema", "film", "party", "🎬", "🍿", "🎮")
        put(listOf("Personal care", "Shopping"), "haircut", "salon", "parlour", "parlor", "barber", "💇")
        put(listOf("Education"), "tuition", "books", "school", "college", "course")
        put(listOf("Gifts and donations"), "gift", "donation", "daan", "🎁")
        put(listOf("Travel", "Transport"), "flight", "hotel", "✈", "🏨")
        put(listOf("Loan EMI"), "emi")
    }

    private sealed interface Amount {
        data class Found(val minor: Long, val marked: Boolean) : Amount
        data object Bad : Amount
    }

    private class Token(val raw: String, val lower: String) {
        var used = false
    }

    /** Reads [text] with amounts of [minorDigits] decimal places; [accounts] are the ones the line may name. */
    fun read(text: String, minorDigits: Int, accounts: List<AccountName> = emptyList()): Reading {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return nothing(Problem.EMPTY)
        if (trimmed.length > MAX_LENGTH) return nothing(Problem.TOO_LONG)
        val tokens = trimmed.split(Regex("\\s+")).map { clean(it) }.filter { s -> s.isNotEmpty() && !s.all { it.code < 128 && !it.isLetterOrDigit() } }
            .map { Token(it, it.lowercase().replace("\uFE0F", "")) }

        // amounts: each token on its own; a multiplier word after a plain number joins it ("2 thousand", "1.5 lakh")
        val found = ArrayList<Pair<Amount.Found, List<Token>>>()
        var bad = false
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            val mult = next?.lower?.let { MULTIPLIER[it] }
            val plain = amountOf(t.lower, minorDigits, 0)
            if (plain is Amount.Found && mult != null && !glued(t.lower)) {
                when (val a = amountOf(t.lower, minorDigits, mult)) { is Amount.Found -> found += a to listOf(t, next); else -> bad = true }
                i += 2
                continue
            }
            when (plain) {
                is Amount.Found -> found += plain to listOf(t)
                Amount.Bad -> bad = true
                null -> {}
            }
            i++
        }
        val marked = found.filter { it.first.marked }
        val chosen = when {
            marked.size == 1 -> marked.single()
            marked.size > 1 -> null
            found.size == 1 -> found.single()
            else -> null
        }
        val problem = when {
            bad -> Problem.BAD_AMOUNT
            chosen == null && found.isEmpty() -> Problem.NO_AMOUNT
            chosen == null -> Problem.TWO_AMOUNTS
            else -> null
        }
        chosen?.second?.forEach { it.used = true }

        // the day
        val words = tokens.filter { !it.used }
        val past = words.any { it.lower in PAST }
        val days = HashSet<Int>()
        var unclear = false
        for ((k, t) in words.withIndex()) {
            when {
                t.lower in TODAY -> { days += 0; t.used = true }
                t.lower in YESTERDAY -> {
                    // "day before yesterday"
                    if (k >= 2 && words[k - 2].lower == "day" && words[k - 1].lower == "before") { days += 2; words[k - 2].used = true; words[k - 1].used = true } else days += 1
                    t.used = true
                }
                t.lower == "kal" -> { if (past) days += 1 else unclear = true; t.used = true }
                t.lower == "parso" || t.lower == "parson" -> { if (past) days += 2 else unclear = true; t.used = true }
            }
        }
        if (days.size > 1) unclear = true
        val daysAgo = if (unclear) null else days.singleOrNull()

        // an account named in the line
        val accountId = matchAccount(tokens.filter { !it.used }, accounts)

        val left = tokens.filter { !it.used && it.lower !in FILLERS && MULTIPLIER[it.lower] == null }
        val note = noteOf(left.map { it.raw })
        val names = (if (note.isEmpty()) emptyList() else DefaultRules.categoryNamesFor(note)) + left.flatMap { t -> KEYWORDS[t.lower] ?: KEYWORDS.entries.firstOrNull { (k, _) -> k.length <= 2 && !k[0].isLetterOrDigit() && t.lower.contains(k) }?.value.orEmpty() }
        return Reading(if (problem == null) chosen?.first?.minor else null, note, names.distinct(), daysAgo, unclear, accountId, problem)
    }

    /**
     * The category the preview offers: what the person usually puts this payee under ([usualId], from their own history) when they still have it, else the first of
     * [names] they have a visible category of ([available] is id to name), else null.
     */
    fun pickCategory(usualId: String?, names: List<String>, available: List<Pair<String, String>>): String? {
        if (usualId != null && available.any { it.first == usualId }) return usualId
        for (n in names) available.firstOrNull { it.second.equals(n, ignoreCase = true) }?.let { return it.first }
        return null
    }

    private fun nothing(p: Problem) = Reading(null, "", emptyList(), null, false, null, p)

    /** "2k": the multiplier is part of the number already. */
    private fun glued(lower: String) = lower.length > 1 && lower.last() == 'k' && lower[lower.length - 2].isDigit()

    /** A token without the punctuation around it ("20," "(chai)" "20."), keeping what belongs to a number or a mark ("/-", "rs."). */
    private fun clean(raw: String): String {
        var s = raw.trim(',', ';', ':', '!', '?', '(', ')', '[', ']', '"', '\'', '“', '”', '‘', '’')
        if (s.endsWith(".") && !s.lowercase().endsWith("rs.")) s = s.dropLast(1)
        return s
    }

    /**
     * The amount [lower] stands for, null when it is not one (a word, or a word with digits in it such as "7eleven"), [Amount.Bad] when it is a number that cannot be
     * read safely. [extraTens] is a multiplier word that follows (3 for thousand, 5 for lakh).
     */
    private fun amountOf(lower: String, minorDigits: Int, extraTens: Int): Amount? {
        if (lower.none { it.isDigit() }) return null
        var s = lower
        var marked = false
        for (p in CURRENCY_PREFIXES) if (s.startsWith(p) && s.length > p.length) { s = s.removePrefix(p); marked = true; break }
        for (p in CURRENCY_SUFFIXES) if (s.endsWith(p) && s.length > p.length) { s = s.removeSuffix(p); marked = true; break }
        if (s.startsWith("-")) return Amount.Bad
        var tens = extraTens
        if (s.endsWith("k") && s.length > 1 && s[s.length - 2].isDigit()) { s = s.dropLast(1); tens = 3 }
        // a sum typed as one word: "120+45", "20x2"
        if (s.first().isDigit() && s.all(AmountMath::isAllowed) && AmountMath.hasOperator(s)) {
            val out = AmountMath.evaluate(s, minorDigits)
            return if (out is AmountMath.Outcome.Value && out.complete) number(out.text, minorDigits, tens)?.let { Amount.Found(it, marked) } ?: Amount.Bad else Amount.Bad
        }
        if (!s.all { it.isDigit() || it == ',' || it == '.' }) return if (marked) Amount.Bad else null
        return number(s, minorDigits, tens)?.let { Amount.Found(it, marked) } ?: Amount.Bad
    }

    /** Digits with an optional grouping and decimal point, times ten to [tens], in minor units; null when it is not a safe amount. */
    private fun number(s: String, minorDigits: Int, tens: Int): Long? {
        if (s.count { it == '.' } > 1) return null
        val whole = s.substringBefore('.')
        val fraction = s.substringAfter('.', "")
        if (fraction.contains(',')) return null
        if (!groupingOk(whole)) return null
        val digits = whole.replace(",", "")
        if (digits.isEmpty() && fraction.isEmpty()) return null
        if (fraction.length > minorDigits + tens) return null
        if (digits.trimStart('0').length + tens > 15) return null
        val shift = minorDigits + tens - fraction.length
        var scale = 1L
        repeat(shift) { scale *= 10 }
        val base = (digits + fraction).ifEmpty { "0" }.toLongOrNull() ?: return null
        if (base > Long.MAX_VALUE / scale) return null
        val minor = base * scale
        return minor.takeIf { it > 0 }
    }

    /** "1,00,000" and "100,000" are groupings; "12,34", "1,0000" and ",500" are not. */
    private fun groupingOk(whole: String): Boolean {
        if (!whole.contains(',')) return true
        val groups = whole.split(',')
        if (groups.last().length != 3 || groups.first().isEmpty()) return false
        val middle = groups.subList(1, groups.size - 1)
        val indian = groups.first().length <= 2 && middle.all { it.length == 2 }
        val thousands = groups.first().length <= 3 && middle.all { it.length == 3 }
        return indian || thousands
    }

    /** The one account the words name: its whole name, or its first word when no other account starts with it; null when none or more than one is named. */
    private fun matchAccount(tokens: List<Token>, accounts: List<AccountName>): String? {
        if (accounts.isEmpty() || tokens.isEmpty()) return null
        val lower = tokens.map { it.lower }
        val hits = HashMap<String, List<Token>>()
        for (a in accounts) {
            val parts = nameWords(a.name)
            if (parts.isEmpty()) continue
            val at = (0..lower.size - parts.size).firstOrNull { s -> parts.indices.all { lower[s + it] == parts[it] } }
            if (at != null) { hits[a.id] = tokens.subList(at, at + parts.size); continue }
            val first = parts.first()
            if (first.length >= 3 && first !in FILLERS && accounts.count { o -> nameWords(o.name).firstOrNull() == first } == 1) {
                tokens.firstOrNull { it.lower == first }?.let { hits[a.id] = listOf(it) }
            }
        }
        if (hits.size != 1) return null
        val (id, used) = hits.entries.single()
        used.forEach { it.used = true }
        return id
    }

    /** The words of an account's name, lower case, split at anything that is not a letter or digit ("ICICI Amazon-Pay card" is icici, amazon, pay, card). */
    private fun nameWords(name: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (c in name.lowercase()) {
            if (c.isLetterOrDigit()) cur.append(c) else if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** The words left, as a note: the merchant's clean name when they are one ("Uber"), else as typed with a capital first letter, cut at a word to [MAX_NOTE]. */
    private fun noteOf(words: List<String>): String {
        if (words.isEmpty()) return ""
        var s = words.joinToString(" ")
        if (s.length > MAX_NOTE) s = s.take(MAX_NOTE).let { cut -> cut.substringBeforeLast(' ', cut) }.trimEnd()
        DefaultRules.displayNameFor(s)?.let { return it }
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
