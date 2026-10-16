package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType

/** The payment network printed on a credit card. [OTHER] is for anything not listed. */
enum class CardNetwork(val label: String) { VISA("Visa"), MASTERCARD("Mastercard"), RUPAY("RuPay"), AMEX("American Express"), OTHER("Other") }

/** How premium the card is: sets the finish of its tile (a plain card, a metallic one, a dark premium one). */
enum class CardTier(val label: String) { CLASSIC("Classic"), PLATINUM("Platinum"), PREMIUM("Premium") }

/**
 * What an account looks like and how texts find it. [network] and [tier] only matter for a credit card; [last4] (the last four digits of the card, or of the
 * account number as banks quote it, "A/c XX1234") is optional on any account and is how a bank text or a bill is matched to this account.
 */
data class AccountLook(val accountId: String, val network: CardNetwork? = null, val tier: CardTier? = null, val last4: String? = null) {
    val isEmpty get() = network == null && tier == null && last4 == null
}

object AccountLooks {
    /** Blank means none. Otherwise exactly four digits, or null for "not valid". */
    fun parseLast4(raw: String): Result<String?> {
        val t = raw.trim()
        if (t.isEmpty()) return Result.success(null)
        return if (t.length == 4 && t.all { it in '0'..'9' }) Result.success(t) else Result.failure(IllegalArgumentException("Type the last four digits, like 4821, or leave it empty."))
    }

    /**
     * The account a text's last four digits point at, from the accounts that carry those digits ([candidates]: id and type). One candidate is the answer. With
     * several, a bill or a card statement ([preferCard]) means the credit card when there is exactly one; anything else is ambiguous and gives null, so
     * the person is asked instead of the wrong account being used.
     */
    fun pick(candidates: List<Pair<String, AccountType>>, preferCard: Boolean): String? {
        if (candidates.size == 1) return candidates.single().first
        if (preferCard) candidates.filter { it.second == AccountType.CREDIT_CARD }.takeIf { it.size == 1 }?.let { return it.single().first }
        return null
    }
}

/** The colours of an account's tile, as ARGB. [top] to [bottom] is the gradient; [text] sits on it; [accent] is the network mark's second colour. */
data class CardDesign(val top: Long, val bottom: Long, val text: Long, val accent: Long)

/**
 * Our own tile designs: a colour family for each network and a finish for each tier, and a colour for each other kind of account. They imitate no bank's or network's
 * artwork and use no logo, so they carry no trademark or copyright of anyone else.
 */
object CardDesigns {
    private const val WHITE = 0xFFFFFFFF
    private const val INK = 0xFF1B1B1F

    fun of(type: AccountType, network: CardNetwork?, tier: CardTier?): CardDesign = when (type) {
        AccountType.CREDIT_CARD -> credit(network ?: CardNetwork.OTHER, tier ?: CardTier.CLASSIC)
        AccountType.BANK -> CardDesign(0xFF1565C0, 0xFF0D3C7A, WHITE, 0xFF90CAF9)
        AccountType.CASH -> CardDesign(0xFF2E7D32, 0xFF1B4D1F, WHITE, 0xFFA5D6A7)
        AccountType.WALLET -> CardDesign(0xFFEF6C00, 0xFF9A4500, WHITE, 0xFFFFCC80)
        AccountType.LOAN -> CardDesign(0xFF546E7A, 0xFF2B3A42, WHITE, 0xFFB0BEC5)
    }

    private fun credit(n: CardNetwork, t: CardTier): CardDesign {
        // classic: the network's own colour; platinum: cooler and lighter, like brushed metal; premium: near black with a gold accent
        val (classic, platinum) = when (n) {
            CardNetwork.VISA -> CardDesign(0xFF1A237E, 0xFF0D1457, WHITE, 0xFFFFC107) to CardDesign(0xFFCFD8DC, 0xFF90A4AE, INK, 0xFF1A237E)
            CardNetwork.MASTERCARD -> CardDesign(0xFF37474F, 0xFF1C2429, WHITE, 0xFFFF9800) to CardDesign(0xFFE0E0E0, 0xFF9E9E9E, INK, 0xFFE65100)
            CardNetwork.RUPAY -> CardDesign(0xFF00796B, 0xFF004D40, WHITE, 0xFFFF8F00) to CardDesign(0xFFCFE8E4, 0xFF80CBC4, INK, 0xFF00695C)
            CardNetwork.AMEX -> CardDesign(0xFF0277BD, 0xFF01579B, WHITE, 0xFFB3E5FC) to CardDesign(0xFFD6E4EE, 0xFF90A9BC, INK, 0xFF01579B)
            CardNetwork.OTHER -> CardDesign(0xFF6750A4, 0xFF3D2C73, WHITE, 0xFFD0BCFF) to CardDesign(0xFFE6DFF0, 0xFFB5A8CC, INK, 0xFF3D2C73)
        }
        return when (t) {
            CardTier.CLASSIC -> classic
            CardTier.PLATINUM -> platinum
            CardTier.PREMIUM -> CardDesign(0xFF2B2B30, 0xFF0E0E10, 0xFFF5E6B8, 0xFFD4AF37)
        }
    }
}
