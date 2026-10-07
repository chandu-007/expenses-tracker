package app.expenses.core.domain

import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Ledger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRulesTest {
    private val inr = Currency.INR
    private fun acct(id: String, name: String, type: AccountType = AccountType.BANK, opening: Long = 0, archived: Boolean = false) = Account(AccountId(id), name, type, inr, opening, archived)

    @Test
    fun namesAreTidiedAndMustBeUniqueIgnoringCaseAndSpacingArchivedOnesIncluded() {
        val all = listOf(acct("a", "HDFC Savings"), acct("b", "Old card", archived = true))
        assertNull(AccountRules.checkName(all, "SBI"))
        assertEquals("You already have an account called \"HDFC Savings\".", AccountRules.checkName(all, "  hdfc   savings "))
        assertTrue(AccountRules.checkName(all, "OLD CARD")!!.contains("already"), "an archived account still owns its name")
        assertNull(AccountRules.checkName(all, "hdfc savings", excludeId = "a"), "renaming an account to its own name in another case is fine")
        assertEquals("Give the account a name.", AccountRules.checkName(all, "   "))
        assertTrue(AccountRules.checkName(all, "x".repeat(41))!!.contains("40"))
        assertNull(AccountRules.checkName(all, "x".repeat(40)))
        assertEquals("a b", AccountRules.normalizeName("  a \t b "))
    }

    @Test
    fun anOpeningBalanceIsWhereTheBalanceStartsAndTransfersStillNeverChangeNetWorth() {
        var l = Ledger.EMPTY.addAccount(acct("bank", "Bank", opening = 100_000)).addAccount(acct("card", "Card", AccountType.CREDIT_CARD, opening = -25_000)).addAccount(acct("cash", "Cash"))
        l = l.add(Expense(EntryId("e"), 1, AccountId("bank"), Money(10_000, inr))).add(Income(EntryId("i"), 2, AccountId("cash"), Money(500, inr)))
        val b = l.balances()
        assertEquals(Money(90_000, inr), b.getValue(AccountId("bank")))
        assertEquals(Money(-25_000, inr), b.getValue(AccountId("card")), "what is owed on a card counts against net worth")
        assertEquals(Money(65_500, inr), l.netWorth(inr))
        assertTrue(AccountRules.isOwed(acct("card", "Card", AccountType.CREDIT_CARD), -25_000))
        assertTrue(!AccountRules.isOwed(acct("bank", "Bank"), -25_000), "an overdrawn bank account is not 'owed'")
        assertTrue(!AccountRules.isOwed(acct("card", "Card", AccountType.CREDIT_CARD), 100), "a card in credit is not owed")
    }
}
