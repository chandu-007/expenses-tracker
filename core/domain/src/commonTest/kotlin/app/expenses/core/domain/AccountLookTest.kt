package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountLookTest {
    @Test
    fun everyNetworkAndFinishLooksDifferentExceptThePremiumFinishWhichIsOneSharedDarkAndGoldDesign() {
        val finishes = CardNetwork.entries.flatMap { n -> listOf(CardTier.CLASSIC, CardTier.PLATINUM).map { t -> CardDesigns.of(AccountType.CREDIT_CARD, n, t) } }
        assertEquals(finishes.size, finishes.toSet().size, "each network has a classic and a platinum look of its own")
        val premium = CardNetwork.entries.map { CardDesigns.of(AccountType.CREDIT_CARD, it, CardTier.PREMIUM) }.toSet()
        assertEquals(1, premium.size); assertEquals(0xFFD4AF37, premium.single().accent)
        assertEquals(CardDesigns.of(AccountType.CREDIT_CARD, CardNetwork.OTHER, CardTier.CLASSIC), CardDesigns.of(AccountType.CREDIT_CARD, null, null), "an unset card looks like Other, Classic")
        val others = listOf(AccountType.BANK, AccountType.CASH, AccountType.WALLET, AccountType.LOAN).map { CardDesigns.of(it, null, null) }
        assertEquals(4, others.toSet().size, "bank, cash, wallet and loan each have their own colour")
    }
}
