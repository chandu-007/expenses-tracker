package app.expenses.core.domain

/** Reads an amount aloud in the Indian system: "one lakh twenty-four thousand five hundred rupees and fifty paise". */
object AmountWords {
    private val small = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
        "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** [minorUnits] is in hundredths of the major unit (paise); the sign is ignored. */
    fun of(minorUnits: Long, major: String = "rupees", minor: String = "paise"): String {
        val abs = if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else if (minorUnits < 0) -minorUnits else minorUnits
        val whole = abs / 100
        val fraction = (abs % 100).toInt()
        val parts = ArrayList<String>(2)
        if (whole > 0 || fraction == 0) parts += "${words(whole)} $major"
        if (fraction > 0) parts += "${words(fraction.toLong())} $minor"
        return parts.joinToString(" and ")
    }

    private fun below100(n: Int): String =
        if (n < 20) small[n] else tens[n / 10] + if (n % 10 == 0) "" else "-" + small[n % 10]

    private fun below1000(n: Int): String {
        val h = n / 100
        val r = n % 100
        return when {
            h == 0 -> below100(r)
            r == 0 -> "${small[h]} hundred"
            else -> "${small[h]} hundred ${below100(r)}"
        }
    }

    private fun words(n: Long): String {
        if (n == 0L) return "zero"
        // Indian grouping: last three digits, then pairs: thousand, lakh, crore, arab, kharab, neel, padma, shankh.
        val names = arrayOf("", "thousand", "lakh", "crore", "arab", "kharab", "neel", "padma", "shankh")
        val groups = ArrayList<Int>()
        groups += (n % 1000).toInt()
        var rest = n / 1000
        while (rest > 0) { groups += (rest % 100).toInt(); rest /= 100 }
        val out = ArrayList<String>()
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g == 0) continue
            out += if (i == 0) below1000(g) else "${below100(g)} ${names[i]}"
        }
        return out.joinToString(" ")
    }
}
