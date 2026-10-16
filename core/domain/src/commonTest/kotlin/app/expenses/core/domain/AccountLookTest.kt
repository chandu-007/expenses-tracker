package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountLookTest {
    @Test
    fun lastFourIsEmptyOrExactlyFourDigits() {
        assertEquals(null, AccountLooks.parseLast4("  ").getOrThrow()); assertEquals("4821", AccountLooks.parseLast4(" 4821 ").getOrThrow())
        for (bad in listOf("482", "48211", "48a1", "XX48", "4 82")) assertTrue(AccountLooks.parseLast4(bad).isFailure, bad)
    }

    @Test
    fun oneCandidateIsTheAnswerAndSeveralNeedACardToBePreferredAndExactlyOne() {
        val bank = "b" to AccountType.BANK; val c1 = "c1" to AccountType.CREDIT_CARD; val c2 = "c2" to AccountType.CREDIT_CARD
        assertEquals("b", AccountLooks.pick(listOf(bank), preferCard = false))
        assertEquals("c1", AccountLooks.pick(listOf(bank, c1), preferCard = true), "a card statement means the card, not the bank account with the same digits")
        assertNull(AccountLooks.pick(listOf(bank, c1), preferCard = false), "ambiguous: ask, do not guess")
        assertNull(AccountLooks.pick(listOf(c1, c2), preferCard = true), "two cards with the same digits")
        assertNull(AccountLooks.pick(emptyList(), preferCard = true))
    }

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
