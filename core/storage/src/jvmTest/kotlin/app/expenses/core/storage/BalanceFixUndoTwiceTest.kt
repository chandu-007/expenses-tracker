// core/storage/src/jvmTest/kotlin/app/expenses/core/storage/BalanceFixUndoTwiceTest.kt  (fails today; passes with UndoGuard.diff)
package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BalanceFixUndoTwiceTest {
    @Test
    fun aSecondUndoOfTheSameFixDoesNothing() {
        val d = Device("u")
        d.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR, openingMinor = 100_00))
        val fixes = BalanceFixes(d.db, d.ledger)
        val r = fixes.fix("bank", 40_00, Currency.INR, 7L) as BalanceFixes.Result.Fixed
        assertTrue(fixes.undo("bank", r.deltaMinor, r.atMillis, Currency.INR))
        assertFalse(fixes.undo("bank", r.deltaMinor, r.atMillis, Currency.INR))
        assertEquals(100_00L, d.ledger.balances().getValue(AccountId("bank")).minorUnits)
    }

    @Test
    fun undoShowsTheHintAgainButOnlyIfTheFixHidItAndNotThePerson() {
        val d = Device("hint")
        d.ledger.saveAccount(Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, Currency.INR))
        val fixes = BalanceFixes(d.db, d.ledger)
        val r = fixes.fix("card", -50_00, Currency.INR, 7L) as BalanceFixes.Result.Fixed
        assertTrue("card" in fixes.dismissedHints(), "a fix hides the hint")
        assertTrue(fixes.undo("card", r.deltaMinor, r.atMillis, Currency.INR))
        assertFalse("card" in fixes.dismissedHints(), "Undo brings it back")
        // dismissed by the person first: a fix and its Undo leave it dismissed
        fixes.dismissHint("card")
        val r2 = fixes.fix("card", -80_00, Currency.INR, 9L) as BalanceFixes.Result.Fixed
        assertTrue(fixes.undo("card", r2.deltaMinor, r2.atMillis, Currency.INR))
        assertTrue("card" in fixes.dismissedHints(), "the person's own dismissal stays")
    }
}
