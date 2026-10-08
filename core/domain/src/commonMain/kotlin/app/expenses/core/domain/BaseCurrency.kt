package app.expenses.core.domain

/** Currencies offered as the base currency, with their decimal places. INR first: the app is India-first. */
object CurrencyChoices {
    val ALL = listOf(
        Currency("INR", 2), Currency("USD", 2), Currency("EUR", 2), Currency("GBP", 2), Currency("AED", 2), Currency("SGD", 2),
        Currency("AUD", 2), Currency("CAD", 2), Currency("CHF", 2), Currency("JPY", 0), Currency("KWD", 3),
    )

    fun byCode(code: String?): Currency = ALL.firstOrNull { it.code == code } ?: Currency.INR

    fun name(c: Currency) = when (c.code) {
        "INR" -> "Indian rupee"; "USD" -> "US dollar"; "EUR" -> "Euro"; "GBP" -> "Pound sterling"; "AED" -> "UAE dirham"; "SGD" -> "Singapore dollar"
        "AUD" -> "Australian dollar"; "CAD" -> "Canadian dollar"; "CHF" -> "Swiss franc"; "JPY" -> "Japanese yen"; "KWD" -> "Kuwaiti dinar"; else -> c.code
    }
}

/** "₹" for rupees, otherwise the currency code: the label on amount fields, matching how [formatted] writes amounts. */
fun Currency.symbolOrCode(): String = if (code == "INR") "₹" else code

/**
 * The currency every amount in this ledger is in. The ledger holds one currency (entries carry their own currency field for the
 * multi-currency work in a later phase, but nothing converts between currencies yet), so this is set once when the database opens
 * and changes only through [BaseCurrencyRules]-checked code.
 */
object BaseCurrency {
    @Volatile var current: Currency = Currency.INR
}

/** What already exists in the ledger, as far as it bears on changing the base currency. */
data class LedgerFacts(val anyEntryEver: Boolean, val budgets: Int, val repeatingItems: Int, val quickEntries: Int, val accountsWithOpeningBalance: Int)

object BaseCurrencyRules {
    /**
     * Why the base currency cannot change, or null when it can. There is no conversion, so changing it under existing amounts would
     * silently relabel rupees as dollars. It can change only while nothing carries an amount.
     */
    fun blocker(f: LedgerFacts): String? = when {
        f.anyEntryEver -> "The currency can only be changed before the first entry: existing amounts would be relabelled, not converted. Start a fresh ledger in Data if you really want another currency."
        f.budgets > 0 -> "Remove your budgets first: their limits are in the current currency."
        f.repeatingItems > 0 -> "Remove your repeating items first: their amounts are in the current currency."
        f.quickEntries > 0 -> "Remove your quick entries first: their amounts are in the current currency."
        f.accountsWithOpeningBalance > 0 -> "Clear the opening balances of your accounts first: they are in the current currency."
        else -> null
    }
}
