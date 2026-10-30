package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which entries already in the ledger are suggested as transfers. Invented data only. Precision comes first: every false friend below must stay out, and the false-positive count of the whole
 * adversarial set is asserted to be zero.
 */
class TransferCandidatesTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val t0 = 1_800_000_000_000L

    private val accounts = listOf(
        CandidateAccount("bank", "Main bank", AccountType.BANK), CandidateAccount("save", "Savings", AccountType.BANK), CandidateAccount("wallet", "Wallet", AccountType.WALLET),
        CandidateAccount("cash", "Cash", AccountType.CASH), CandidateAccount("card", "Card", AccountType.CREDIT_CARD), CandidateAccount("loan", "Car loan", AccountType.LOAN),
    )

    private fun out(id: String, title: String?, account: String = "bank", amount: Long = 500_000, at: Long = t0, note: String? = null, origin: String? = null, arrived: Long? = null) =
        CandidateEntry(id, true, account, at, arrived, amount, title, note, origin)

    private fun into(id: String, title: String?, account: String = "save", amount: Long = 500_000, at: Long = t0, note: String? = null, origin: String? = null, arrived: Long? = null) =
        CandidateEntry(id, false, account, at, arrived, amount, title, note, origin)

    private fun find(
        entries: List<CandidateEntry> = emptyList(), pairs: List<Pair<CandidateEntry, CandidateEntry>> = emptyList(), dismissed: Set<String> = emptySet(), refused: Set<String> = emptySet(),
        named: Map<String, String> = emptyMap(), otherHalf: Set<String> = emptySet(), attached: Set<String> = emptySet(),
    ) = TransferCandidates.find(entries, pairs, accounts, dismissed, { it in refused }, { named[it.id] }, { it.id in otherHalf }, { it.id !in attached })

    private fun ids(c: List<TransferCandidate>) = c.map { it.entry.id }

    // ---- cash withdrawals -------------------------------------------------------------------------------------------------------------------------

    @Test
    fun atmWordingInTheTitleTheNoteOrTheOriginalTextIsACashWithdrawal() {
        val found = find(entries = listOf(
            out("t1", "ATM cash"), out("t2", "ATM WDL"), out("t3", "Cash withdrawal"), out("t4", "Cash"), out("t5", "ATM"),
            out("n1", "Bank", note = "atm withdrawal for rent"),
            out("o1", "Bank", origin = "Rs.5,000.00 withdrawn at ATM 12345 on 05-10-26 from A/c XX1234"),
            out("o2", "SBI", origin = "INR 2,000 ATM WDL at SBI ATM on 05-10-26"),
            out("o3", "Bank", origin = "Rs.2,000.00 debited from A/c XX1234 on 05-10-26. Info: ATM-CASH. Avl Bal Rs.100"),
            out("s1", "NWD-123456XXXXXX7890-HDFC ATM KORAMANGALA"), out("s2", "AWL-789012-BANK ATM"),
        ))
        assertEquals(setOf("t1", "t2", "t3", "t4", "t5", "n1", "o1", "o2", "o3", "s1", "s2"), ids(found.cashWithdrawals).toSet())
        assertTrue(found.ownAccounts.isEmpty() && found.selfTransfers.isEmpty())
    }

    @Test
    fun aCashAdvanceOnACardIsACandidateToo_becauseTheCashIsSpentLaterAndWouldBeCountedTwice() {
        val found = find(entries = listOf(out("c1", "ATM cash", account = "card"), out("w1", "ATM cash", account = "wallet")))
        assertEquals(setOf("c1", "w1"), ids(found.cashWithdrawals).toSet())
        assertEquals("Reads as a cash advance on a card", found.cashWithdrawals.single { it.entry.id == "c1" }.reason)
    }

    @Test
    fun falseFriendsAreNeverSuggested() {
        // names and words that contain ATM, CASH or WITHDRAW without being a cash withdrawal, and entries that are excluded for their own reason
        val falseFriends = listOf(
            out("f1", "ATMOS cafe"), out("f2", "Batman store"), out("f3", "Treatment centre"), out("f4", "ATM Bakery"), out("f5", "Tatmandir Sweets"),
            out("f6", "Cash and Carry"), out("f7", "Cashew World"), out("f8", "Cashify"), out("f9", "Matmaker"),
            out("g1", "Swiggy", origin = "Rs.800 spent on your HDFC Debit/ATM Card xx1234 at SWIGGY on 05-10-26"),
            out("g2", "Pharmacy", origin = "Rs.2,345.00 debited from your A/C towards Debit Card Purchase at ATMA PHARMACY"),
            out("g3", "Shop", origin = "Rs.500.00 spent on card at SHOP. For any query on your ATM card call 1800"),
            // a reversal or refund of a withdrawal is money back, never a withdrawal
            out("r1", "ATM cash reversal"), out("r2", "Bank", origin = "Rs.2,000.00 withdrawn at ATM 4455. Cash not dispensed, reversal will be processed"),
            // cash taken out of the Cash account itself, and a loan account, are not ATM withdrawals
            out("a1", "ATM cash", account = "cash"), out("a2", "ATM cash", account = "loan"),
            // an income is never a withdrawal
            into("i1", "ATM cash", account = "bank"), into("i2", "Cash deposit", account = "bank"),
            // zero amount, an account that is not known
            out("z1", "ATM cash", amount = 0), out("z2", "ATM cash", account = "gone"),
        )
        val found = find(entries = falseFriends)
        assertEquals(emptyList(), ids(found.cashWithdrawals) + ids(found.selfTransfers) + ids(found.ownAccounts), "false positives")
    }

    @Test
    fun anEntryThatCannotChangeItsTypeOrWasAnsweredNotATransferIsLeftOut() {
        val rows = listOf(out("shared", "ATM cash"), out("foreign", "ATM cash"), out("dismissed", "ATM cash"), out("ok", "ATM cash"))
        val found = find(entries = rows, dismissed = setOf("dismissed"), refused = setOf("shared", "foreign"))
        assertEquals(listOf("ok"), ids(found.cashWithdrawals))
    }

    @Test
    fun theNewestComeFirst() {
        val found = find(entries = listOf(out("old", "ATM cash", at = t0), out("new", "ATM cash", at = t0 + 5 * day), out("mid", "ATM cash", at = t0 + day)))
        assertEquals(listOf("new", "mid", "old"), ids(found.cashWithdrawals))
    }

    // ---- moves between own accounts ---------------------------------------------------------------------------------------------------------------

    @Test
    fun anExpenseAndAnIncomeOfTheSameAmountMinutesApartWithTransferWordingAreOneMove() {
        val x = out("x", "Funds transfer to savings", note = null, at = t0)
        val i = into("i", "Transfer received", at = t0 + 2 * minute)
        val found = find(pairs = listOf(x to i))
        val c = found.ownAccounts.single()
        assertEquals("x", c.entry.id); assertEquals("i", c.partner?.id)
        assertTrue(c.reason.contains("2 min"), c.reason)
        // neither half is also suggested on its own
        assertTrue(found.selfTransfers.isEmpty() && found.cashWithdrawals.isEmpty())
    }

    @Test
    fun theWordingMayBeInTheOriginalTextButBothHalvesMustSayIt() {
        val x = out("x", "Bank", origin = "Rs.5,000.00 debited from A/c XX1234 Money Transfer via Online Banking")
        val i = into("i", "Credit", origin = "Rs.5,000.00 credited to A/c XX5678")
        assertTrue(find(pairs = listOf(x to i)).isEmpty, "one half's wording is thin evidence for deleting a real income")
        val both = into("i2", "Credit", origin = "Rs.5,000.00 credited to A/c XX5678 by transfer from A/c XX1234")
        assertEquals(1, find(pairs = listOf(x to both)).ownAccounts.size)
    }

    @Test
    fun anAtmWithdrawalAndARealIncomeFromAPersonAreNeverAPair() {
        // the review's case: 5,000 out as an ATM withdrawal, 5,000 in twenty minutes later on another account "Transfer from RAVI KUMAR"
        val x = out("x", "ATM cash", amount = 5_000_00, at = t0); val i = into("i", "Transfer from RAVI KUMAR", amount = 5_000_00, at = t0 + 20 * minute)
        assertTrue(find(pairs = listOf(x to i)).ownAccounts.isEmpty())
        // even when both say transfer, a half that reads as an ATM withdrawal is not a move
        val y = out("y", "ATM WDL transfer"); assertTrue(find(pairs = listOf(y to into("j", "Transfer received"))).ownAccounts.isEmpty())
        // a transfer-worded expense against a person's credit that says nothing of the kind: only one half says it
        assertTrue(find(pairs = listOf(out("a", "Funds transfer to Ravi") to into("b", "Ravi paid back"))).ownAccounts.isEmpty())
    }

    @Test
    fun theIncomeOfAPairWithAnAttachmentIsNotOfferedBecauseDeletingItWouldLeaveTheFileBehind() {
        val x = out("x", "Transfer to savings"); val i = into("i", "Transfer received", at = t0 + minute)
        assertTrue(find(pairs = listOf(x to i), attached = setOf("i")).ownAccounts.isEmpty())
        assertEquals(1, find(pairs = listOf(x to i), attached = setOf("x")).ownAccounts.size, "an attachment on the expense moves with it")
    }

    @Test
    fun aLoneEntryWhoseOtherHalfIsAlreadyInTheLedgerIsNotOffered() {
        val rows = listOf(out("atm", "ATM cash"), out("self", "Self transfer"), into("from", "From self", account = "bank"), out("free", "ATM cash"))
        val found = find(entries = rows, otherHalf = setOf("atm", "self", "from"))
        assertEquals(listOf("free"), ids(found.cashWithdrawals)); assertTrue(found.selfTransfers.isEmpty())
    }

    @Test
    fun chargesWalletMovesAndLimitBoilerplateAreNotWithdrawals() {
        val notAtm = listOf(
            out("c1", "ATM WDL CHGS"), out("c2", "Bank", origin = "Charges for cash withdrawal at other bank ATM"), out("c3", "Bank", origin = "Rs.23.60 debited towards ATM cash withdrawal charges"),
            out("c4", "Bank", origin = "Rs.20 debited: ATM withdrawal fee"), out("c5", "Bank", origin = "ATM cash withdrawal, GST on charges debited"),
            out("w1", "Paytm", account = "wallet", origin = "Rs.2,000 withdrawn from your Paytm Wallet to bank account"),
            out("w2", "Paytm", account = "bank", origin = "Rs.2,000 withdrawn from your Paytm Wallet to bank account"),
            out("w3", "Wallet", account = "wallet", note = "withdrawn"),
            out("l1", "Shop", account = "card", origin = "Rs.800 spent on card at SHOP. Cash withdrawal limit reset to Rs 20,000"),
            out("l2", "Shop", account = "bank", origin = "Rs.800 debited for purchase. Your daily limit for cash withdrawal is Rs 40,000"),
        )
        assertEquals(emptyList(), ids(find(entries = notAtm).cashWithdrawals), "false positives")
        // the real thing still works, with a limit line after it, from a bank and from a card
        val yes = listOf(
            out("a1", "Bank", origin = "Rs.5,000.00 cash withdrawn at ATM 12345 on 05-10-26. Daily withdrawal limit Rs 40,000"),
            out("a2", "Card", account = "card", origin = "Rs.10,000 withdrawn at ATM on your card ending 4321"), out("a3", "Bank", origin = "Rs.3,000 ATM WDL at SBI ATM"),
        )
        assertEquals(setOf("a1", "a2", "a3"), ids(find(entries = yes).cashWithdrawals).toSet())
        // a bare "withdrawn" from a wallet account is the wallet's word for moving money, so it does not count there, while an ATM wording still does
        assertEquals(listOf("w4"), ids(find(entries = listOf(out("w3", "Wallet", account = "wallet", origin = "Rs.500 withdrawn"), out("w4", "Wallet", account = "wallet", origin = "Rs.500 ATM WDL"))).cashWithdrawals))
    }

    @Test
    fun toSelfMustEndThereAndMyAccountInAFreeNoteIsNotEnough() {
        val no = listOf(out("n1", "Paid to Self Drive Cars"), out("n2", "Self Help Group"), out("n3", "Rent", note = "from my account at the old bank"), out("n4", "Bank", note = "my account"), out("n5", "Self Drive Cars"))
        assertEquals(emptyList(), ids(find(entries = no).selfTransfers), "false positives")
        val yes = listOf(out("y1", "UPI to self"), out("y2", "Paid to self."), into("y3", "From self"), out("y4", "Bank", origin = "Sent to own name"), out("y5", "Self transfer"))
        assertEquals(setOf("y1", "y2", "y3", "y4", "y5"), ids(find(entries = yes).selfTransfers).toSet())
    }

    @Test
    fun withNoTransferWordingTheSameAmountAtTheSameMomentIsNotEnough() {
        val x = out("x", "Ravi"); val i = into("i", "Anita")
        assertTrue(find(pairs = listOf(x to i)).isEmpty, "two unrelated people at the same amount")
    }

    @Test
    fun thirtyOneMinutesIsTooFarApartWithoutANamedAccountAndADayIsFineWithOne() {
        val x = out("x", "Transfer to savings")
        assertTrue(find(pairs = listOf(x to into("i", "Transfer", at = t0 + 31 * minute))).isEmpty)
        assertEquals(1, find(pairs = listOf(x to into("i", "Transfer", at = t0 + 30 * minute))).ownAccounts.size)
        // a text that names the other account's digits: up to a day
        val named = find(pairs = listOf(out("x", "Bank") to into("i", "Credit", at = t0 + 20 * hour)), named = mapOf("i" to "bank", "x" to "save"))
        assertEquals(1, named.ownAccounts.size)
        assertTrue(named.ownAccounts.single().reason.startsWith("A text names the other account"))
        assertTrue(find(pairs = listOf(out("x", "Bank") to into("i", "Credit", at = t0 + 26 * hour)), named = mapOf("i" to "bank", "x" to "save")).isEmpty)
        assertTrue(find(pairs = listOf(out("x", "Bank") to into("i", "Credit", at = t0 + 20 * hour)), named = mapOf("i" to "bank")).isEmpty, "naming only one account is thin evidence")
    }

    @Test
    fun theTimeTheTextArrivedStandsForWhenItHappenedAsInTheLivePairing() {
        // both were entered at noon on the bank's date, but the texts arrived two minutes apart
        val x = out("x", "Transfer", at = t0, arrived = t0 + 100 * minute)
        val i = into("i", "Transfer", at = t0 + 4 * hour, arrived = t0 + 102 * minute)
        assertEquals(1, find(pairs = listOf(x to i)).ownAccounts.size)
        assertTrue(find(pairs = listOf(out("x", "Transfer", at = t0) to into("i", "Transfer", at = t0 + 4 * hour))).isEmpty)
    }

    @Test
    fun aSameAmountPairOnTheSameAccountOrWithACardOrLoanOrARefusedHalfIsNotOffered() {
        assertTrue(find(pairs = listOf(out("x", "Transfer", account = "bank") to into("i", "Transfer", account = "bank"))).isEmpty, "same account")
        assertTrue(find(pairs = listOf(out("x", "Transfer", account = "bank") to into("i", "Transfer", account = "card"))).isEmpty, "a card has its own payment handling")
        assertTrue(find(pairs = listOf(out("x", "Transfer", account = "bank") to into("i", "Transfer", account = "loan"))).isEmpty)
        assertTrue(find(pairs = listOf(out("x", "Transfer") to into("i", "Transfer")), refused = setOf("i")).isEmpty, "a half that cannot change its type")
        assertTrue(find(pairs = listOf(out("x", "Transfer") to into("i", "Transfer")), dismissed = setOf("x")).isEmpty, "answered not a transfer")
        assertTrue(find(pairs = listOf(out("x", "Transfer", amount = 100) to into("i", "Transfer", amount = 200))).isEmpty, "different amounts")
    }

    @Test
    fun aRefusedHalfDoesNotTakeAnotherHalfsPlace() {
        // two incomes could be the other half of one expense; the closer one is refused, so the other is paired
        val x = out("x", "Transfer", at = t0)
        val close = into("close", "Transfer", at = t0 + minute); val far = into("far", "Transfer", at = t0 + 10 * minute)
        val found = find(pairs = listOf(x to close, x to far), refused = setOf("close"))
        assertEquals("far", found.ownAccounts.single().partner?.id)
    }

    @Test
    fun twoMovesOfTheSameAmountPairOffOneForOne() {
        val x1 = out("x1", "Transfer", at = t0); val x2 = out("x2", "Transfer", at = t0 + 10 * minute)
        val i1 = into("i1", "Transfer", at = t0 + 9 * minute); val i2 = into("i2", "Transfer", at = t0 + 40 * minute)
        val found = find(pairs = listOf(x1 to i1, x2 to i1, x2 to i2))
        assertEquals(setOf("x1" to "i1", "x2" to "i2"), found.ownAccounts.map { it.entry.id to it.partner!!.id }.toSet())
    }

    // ---- transfers to oneself ---------------------------------------------------------------------------------------------------------------------

    @Test
    fun explicitSelfWordingIsASelfTransferAndNothingLooserIs() {
        val yes = listOf(
            out("y1", "Self transfer"), out("y2", "UPI to self"), out("y3", "To own account"), out("y4", "Bank", note = "own account"), out("y5", "Bank", origin = "Rs.1,000 sent to own name via UPI own VPA"),
            into("y6", "From self"), into("y7", "Self a/c"),
        )
        assertEquals(setOf("y1", "y2", "y3", "y4", "y5", "y6", "y7"), ids(find(entries = yes).selfTransfers).toSet())
        val no = listOf(
            out("n1", "Self Help Group dues"), out("n2", "Myself Foods"), out("n3", "Selfie Studio"), out("n4", "Money Transfer to RAVI KUMAR"), out("n5", "NEFT transfer to landlord"),
            out("n6", "Ownership fee"), out("n7", "Town clock", origin = "paid to own..."), out("n8", "Rent", origin = "IMPS transfer 123456 to ANITA RAO"),
            out("n9", "Self transfer", account = "card"), out("n10", "Self transfer", account = "loan"),
        )
        assertEquals(emptyList(), ids(find(entries = no).selfTransfers), "false positives")
    }

    @Test
    fun anEntryWithBothAnAtmWordAndSelfWordingIsOnlyOneCandidate() {
        val found = find(entries = listOf(out("both", "ATM cash", note = "to self")))
        assertEquals(1, found.total)
    }

    @Test
    fun adversarialSetFalsePositiveCountIsZeroAndEveryRealOneIsFound() {
        val real = listOf(out("r1", "ATM cash"), out("r2", "ATM WDL", account = "card"), out("r3", "Self transfer"))
        val noise = listOf(
            out("a", "ATMOS cafe"), out("b", "Batman store"), out("c", "Cash and Carry"), out("d", "Self Help Group"), out("e", "Money Transfer to RAVI"), out("f", "Ravi"), out("g", "Treatment centre"),
        )
        val pairNoise = listOf(out("p1", "Ravi", amount = 7_500) to into("p2", "Anita", amount = 7_500), out("p3", "Transfer", amount = 9_000) to into("p4", "Transfer", amount = 9_000, at = t0 + 2 * day))
        val found = find(entries = real + noise, pairs = pairNoise)
        val falsePositives = found.total - 3
        assertEquals(0, falsePositives)
        assertEquals(setOf("r1", "r2", "r3"), (ids(found.cashWithdrawals) + ids(found.selfTransfers)).toSet())
    }
}
