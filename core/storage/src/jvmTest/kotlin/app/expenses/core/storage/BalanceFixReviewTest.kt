// core/storage/src/jvmTest/kotlin/app/expenses/core/storage/BalanceFixReviewTest.kt
package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceFixReviewTest {
    private fun eraData(version: String): BackupData {
        val payload = checkNotNull(javaClass.classLoader.getResourceAsStream("backup-payload-$version.json")).readBytes()
        val header = Json.encodeToString(BackupHeader.serializer(), BackupHeader(BackupFile.FORMAT, 1, 1L, payload.size, Sha256.hex(payload)))
        return BackupFile.decode(header.encodeToByteArray() + byteArrayOf('\n'.code.toByte()) + payload).second
    }

    /** The real fresh install has Cash and Bank (AppContainer); the era tests restore into a Device that has NO accounts, so they never run the first-start rule. */
    private fun freshApp(node: String) = Device(node).also {
        it.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR))
        it.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR))
    }

    @Test
    fun everyEraRestoresIntoARealFreshInstallWithTheBalancesOfAnEmptyDevice() {
        for (v in listOf("0.9.0", "0.10.4", "0.11.0")) {
            val data = eraData(v)
            val empty = Device("e$v"); empty.backup.restore(data)
            val app = freshApp("a$v"); app.backup.restore(data)
            assertEquals(empty.ledger.balances(), app.ledger.balances(), v)
            assertEquals(500_000L, app.db.schemaQueries.accountById("bank").executeAsOne().opening_minor, v)
            // the second restore must not change anything either
            val before = app.ledger.balances(); app.backup.restore(data)
            assertEquals(before, app.ledger.balances(), v)
        }
    }

    @Test
    fun anEntryAddedBeforeRestoringKeepsTheOpeningBalancesOfThisPhone() {
        val data = eraData("0.11.0")
        val app = freshApp("x")
        app.ledger.save(Expense(EntryId("mine"), 5L, AccountId("cash"), app.m(10_00)))
        app.backup.restore(data)
        assertEquals(0L, app.db.schemaQueries.accountById("bank").executeAsOne().opening_minor)
    }
}
