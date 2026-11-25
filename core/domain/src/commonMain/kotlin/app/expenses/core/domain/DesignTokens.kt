package app.expenses.core.domain

/**
 * The numbers of the app's design tokens (UI S1), kept in core so the rules are tested on the host and both phones read the same ones; `shared/ui/Tokens.kt` turns them into `Dp` and shapes.
 * Names for what already existed plus one decision: the app had 14 corner radii; it now has four steps and the pill.
 */
object DesignTokens {
    /** The 4 dp grid, the steps in use: xs, s, m, l, xl, xxl, section. */
    val SPACING_DP = listOf(4, 8, 12, 16, 20, 24, 28, 32)

    const val RADIUS_XS = 8
    const val RADIUS_S = 12
    const val RADIUS_M = 20
    const val RADIUS_L = 24
    val RADII_DP = listOf(RADIUS_XS, RADIUS_S, RADIUS_M, RADIUS_L)

    /**
     * The token a corner radius that was drawn in code before UI S1 becomes (2, 3, 6, 8, 10 to 8; 12, 14, 16, 18 to 14; 20, 21, 22, 24 to 20; 28 to 28): entry cards 22 to 20, nudges 24 to 20, inputs 12 and 16 to 14.
     * A radius of more than 28 is not a step: it is a pill.
     */
    fun radiusFor(legacyDp: Int): Int = when {
        legacyDp <= 10 -> RADIUS_XS
        legacyDp <= 16 -> RADIUS_S
        legacyDp <= 22 -> RADIUS_M
        else -> RADIUS_L
    }

    /** The dimming behind a sheet: one value for the Filters panel, the pickers and every other sheet (percent). */
    const val SCRIM_PERCENT = 32
}

/**
 * An amount as it is drawn in a hero figure: the sign (full size: a minus is information), the currency sign (small), the whole part with its grouping (large), the paise (small and quiet). [sign], [symbol] and [paise]
 * are empty when there is none. The text a screen reader reads is never built from this: it keeps the exact string.
 */
data class MoneyParts(val sign: String, val symbol: String, val body: String, val paise: String) {
    companion object {
        private val PAISE = Regex("""[.,]\d{1,2}$""")

        fun split(text: String): MoneyParts {
            val firstDigit = text.indexOfFirst { it.isDigit() }
            if (firstDigit < 0) return MoneyParts("", "", text, "") // masked ("₹ ••••") or no figure: nothing to style
            val lead = text.substring(0, firstDigit)
            val sign = lead.takeWhile { it == '-' || it == '+' || it == '\u2212' }
            val symbol = lead.removePrefix(sign)
            val rest = text.substring(firstDigit)
            val paise = PAISE.find(rest)?.value ?: ""
            return MoneyParts(sign, symbol, rest.removeSuffix(paise), paise)
        }
    }
}
