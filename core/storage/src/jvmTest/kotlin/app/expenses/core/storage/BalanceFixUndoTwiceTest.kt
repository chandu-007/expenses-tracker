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
}
