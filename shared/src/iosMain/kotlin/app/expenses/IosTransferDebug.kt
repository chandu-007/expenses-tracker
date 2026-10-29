package app.expenses

import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType

/**
 * Test entry for the Swift unit tests of own-account transfers: the two texts a bank sends for one move of money go through the app's real message path ([MessageIngest.ingest], the one
 * Shortcuts and paste use) into a scratch in-memory database that has two bank accounts, and the answer says what [TransferScan] made of the waiting items. Does nothing outside a test run, and
 * never touches the person's data. The answer is a sentence, because a Kotlin exception cannot be caught from Swift.
 */
object IosTransferDebug {
    private const val OUT = "Amt Deducted!\nRs.45,250.75 from your HDFC Bank A/c XX1234 for Money Transfer via HDFC Bank Online Banking.\nNot you?Call 18002586161/SMS BLOCK OB to 7308080808"
    private const val INTO = "Update! INR 45,250.75 deposited in HDFC Bank A/c XX5678 on 29-OCT-26 for XXXXXXXXXX1234-TPT-Holiday Fund Remembe-SAMPLE TRADERS PRIVATE LIMITED.Avl bal INR 3,21,654.32. Cheque deposits in A/C are subject to clearing"

    /**
     * Ingests both texts (the debit first, the credit 40 seconds later, from header HDFCBK) and reports, as "captured=2 pairs=1 from=Bank to=Savings basis=NAMED_ACCOUNT" (or "pairs=0").
     * Account "Bank" carries the digits 1234 and "Savings" 5678 when [digitsOnSavings], else Savings has no digits set.
     */
    suspend fun pairTheTwoTexts(host: IosHostServices, digitsOnSavings: Boolean): String {
        if (!runningUnderXCTest()) return "refused: this is a test entry and does nothing outside a test run"
        val container = AppContainer(ScratchPlatform(IosCore.get(host).platform, "scratch-transfer-${nowMillis()}.db")) // a name of its own: two runs in one process never share a database
        val at = 1_800_000_000_000L
        container.write { ok ->
            ok.store.saveAccount(Account(AccountId("savings"), "Savings", AccountType.BANK, Currency.INR))
            ok.looks.save(AccountLook("bank", last4 = "1234"))
            if (digitsOnSavings) ok.looks.save(AccountLook("savings", last4 = "5678"))
        } ?: return "the database could not be opened"
        MessageIngest.ingest(container, "HDFCBK", OUT, at)
        MessageIngest.ingest(container, "HDFCBK", INTO, at + 40_000)
        return container.write { ok ->
            val items = ok.inbox.list()
            val accounts = ok.db.schemaQueries.allAccounts().executeAsList().filter { it.archived == 0L }
            val scan = TransferScan.of(ok, items, items.mapTo(HashSet()) { it.id }, accounts)
            val pair = scan?.pairs?.singleOrNull()
            "captured=${items.size} pairs=${scan?.pairs?.size ?: 0}" + (pair?.let { " from=${scan.accountNames[it.out.accountId]} to=${scan.accountNames[it.into.accountId]} basis=${it.basis.name}" } ?: "")
        } ?: "the database could not be opened"
    }
}
