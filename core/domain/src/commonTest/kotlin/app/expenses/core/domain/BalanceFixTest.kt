package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The arithmetic and the words of "Fix this balance". All amounts here are invented. */
class BalanceFixTest {
    private val inr = Currency.INR
    private val money = { m: Long -> Money(m, inr).formatted() }

    // ---- what the bank app says, as a signed balance ----

    @Test
    fun aCardOrLoanAmountIsWhatIsOwedAndTheSecondChoiceIsCredit() {
        for (type in listOf(AccountType.CREDIT_CARD, AccountType.LOAN)) {
            assertEquals(-5_500_00L, BalanceFix.statedBalance(type, 5_500_00, other = false), "owed $type")
            assertEquals(5_500_00L, BalanceFix.statedBalance(type, 5_500_00, other = true), "in credit $type")
            assertEquals(0L, BalanceFix.statedBalance(type, 0, other = false))
            assertEquals(0L, BalanceFix.statedBalance(type, 0, other = true))
        }
    }

    @Test
    fun aBankCashOrWalletAmountIsWhatIsHeldAndTheSecondChoiceIsOverdrawn() {
        for (type in listOf(AccountType.BANK, AccountType.CASH, AccountType.WALLET)) {
            assertEquals(24_100_00L, BalanceFix.statedBalance(type, 24_100_00, other = false), "holds $type")
            assertEquals(-500_00L, BalanceFix.statedBalance(type, 500_00, other = true), "overdrawn $type")
            assertEquals(0L, BalanceFix.statedBalance(type, 0, other = true))
        }
    }

    // ---- the difference ----

    @Test
    fun aCardThatOwesMoreThanTheAppThinksIsMissingSpending() {
        // the app thinks 3,000 is owed; the statement says 5,500
        val plan = BalanceFix.plan(-3_000_00, BalanceFix.statedBalance(AccountType.CREDIT_CARD, 5_500_00, false))!!
        assertEquals(-2_500_00L, plan.deltaMinor)
        assertEquals("Difference: ₹2,500.00 (the app is missing spending of ₹2,500.00)", BalanceFixText.difference(AccountType.CREDIT_CARD, plan.deltaMinor, money))
        assertEquals(-2_500_00L, BalanceFix.newOpening(0, plan.deltaMinor), "an opening balance of nothing becomes 2,500 owed; the 3,000 the app knows stays as it was")
    }

    @Test
    fun theOwnersCaseACardInCreditAfterPayingTheRealBillIsMissingSpendingToo() {
        // the app shows 2,000 EXTRA (credit); the statement says nothing is owed
        val credit = 2_000_00L
        val plan = BalanceFix.plan(credit, BalanceFix.statedBalance(AccountType.CREDIT_CARD, 0, false))!!
        assertEquals(-credit, plan.deltaMinor)
        assertEquals("Difference: ₹2,000.00 (the app is missing spending of ₹2,000.00)", BalanceFixText.difference(AccountType.CREDIT_CARD, plan.deltaMinor, money))
        assertEquals("The app thinks you owe nothing and have ₹2,000.00 extra in credit", BalanceFixText.current("HDFC Card", AccountType.CREDIT_CARD, credit, money))
    }

    @Test
    fun aCardThatOwesLessThanTheAppThinksIsMissingPaymentsOrRefunds() {
        val plan = BalanceFix.plan(-9_000_00, BalanceFix.statedBalance(AccountType.CREDIT_CARD, 7_250_50, false))!!
        assertEquals(1_749_50L, plan.deltaMinor)
        assertEquals("Difference: ₹1,749.50 (the app is missing payments or refunds of ₹1,749.50)", BalanceFixText.difference(AccountType.CREDIT_CARD, plan.deltaMinor, money))
    }

    @Test
    fun aBankWithLessInItThanTheAppThinksIsMissingSpendingAndWithMoreMissingMoneyReceived() {
        val less = BalanceFix.plan(25_000_00, BalanceFix.statedBalance(AccountType.BANK, 24_100_00, false))!!
        assertEquals(-900_00L, less.deltaMinor)
        assertEquals("Difference: ₹900.00 (the app is missing spending of ₹900.00)", BalanceFixText.difference(AccountType.BANK, less.deltaMinor, money))
        val more = BalanceFix.plan(25_000_00, BalanceFix.statedBalance(AccountType.BANK, 1_26_000_00, false))!!
        assertEquals(1_01_000_00L, more.deltaMinor)
        assertEquals("Difference: ₹1,01,000.00 (the app is missing money that came in: ₹1,01,000.00)", BalanceFixText.difference(AccountType.BANK, more.deltaMinor, money), "lakh grouping")
    }

    @Test
    fun cashAndWalletsUseTheirOwnQuestionsAndWords() {
        assertEquals("The app thinks Cash has ₹850.00", BalanceFixText.current("Cash", AccountType.CASH, 850_00, money))
        assertEquals("How much cash do you have right now?", BalanceFixText.asks(AccountType.CASH))
        assertEquals("Cash you have now", BalanceFixText.fieldLabel(AccountType.CASH))
        assertEquals("What does the wallet app say?", BalanceFixText.asks(AccountType.WALLET))
        assertEquals("What does your bank app say?", BalanceFixText.asks(AccountType.BANK))
        assertEquals("Balance in your bank app", BalanceFixText.fieldLabel(AccountType.BANK))
        assertEquals("What does your card statement or bank app say?", BalanceFixText.asks(AccountType.CREDIT_CARD))
        assertEquals("Amount you owe now", BalanceFixText.fieldLabel(AccountType.CREDIT_CARD))
        assertEquals("The app thinks Wallet is overdrawn by ₹40.00", BalanceFixText.current("Wallet", AccountType.WALLET, -40_00, money))
        assertEquals("The app thinks you owe ₹12,000.00", BalanceFixText.current("Amex", AccountType.CREDIT_CARD, -12_000_00, money))
        assertEquals("The app thinks you owe nothing", BalanceFixText.current("Amex", AccountType.CREDIT_CARD, 0, money))
    }

    @Test
    fun aMatchingBalanceIsSaidSoAndNothingMoves() {
        val plan = BalanceFix.plan(-3_000_00, BalanceFix.statedBalance(AccountType.CREDIT_CARD, 3_000_00, false))!!
        assertTrue(plan.matches)
        assertEquals(0L, plan.deltaMinor)
        assertEquals("Already matches: the app shows the same amount.", BalanceFix.ALREADY_MATCHES)
        assertEquals(0L, BalanceFix.plan(0, BalanceFix.statedBalance(AccountType.BANK, 0, other = true))!!.deltaMinor, "zero overdrawn is zero")
    }

    // ---- what was typed ----

    @Test
    fun typedTextIsReadAsAnAmountOrRefusedInPlainWords() {
        assertEquals(BalanceFix.Input.Empty, BalanceFix.read("", inr))
        assertEquals(BalanceFix.Input.Empty, BalanceFix.read("   ", inr))
        assertEquals(BalanceFix.Input.Amount(5_500_00), BalanceFix.read("5500", inr))
        assertEquals(BalanceFix.Input.Amount(1_23_456_78), BalanceFix.read("1,23,456.78", inr))
        assertEquals(BalanceFix.Input.Amount(5_000_00), BalanceFix.read("₹5,000", inr))
        assertEquals(BalanceFix.Input.Amount(5_000_00), BalanceFix.read("Rs. 5000", inr))
        assertEquals(BalanceFix.Input.Amount(5_000_00), BalanceFix.read("rs 5000", inr))
        assertEquals(BalanceFix.Input.Amount(5_000_00), BalanceFix.read("5000 INR", inr))
        assertEquals(BalanceFix.Input.Amount(50), BalanceFix.read(".5", inr))
        assertEquals(BalanceFix.Input.Amount(0), BalanceFix.read("0", inr))
        assertEquals(BalanceFix.Input.Empty, BalanceFix.read("₹", inr))
    }

    @Test
    fun anotherCurrencyIsRefusedBecauseTheLedgerHasOne() {
        val wrong = "This ledger is in ₹, so only ₹ amounts can be used here."
        for (t in listOf("$50", "USD 50", "50 usd", "€40", "£3", "¥100")) assertEquals(BalanceFix.Input.Bad(wrong), BalanceFix.read(t, inr), t)
        // in a ledger of dollars the rupee sign is the foreign one, and the code is the ledger's
        assertEquals(BalanceFix.Input.Bad("This ledger is in USD, so only USD amounts can be used here."), BalanceFix.read("₹500", Currency.USD))
        assertEquals(BalanceFix.Input.Amount(5_000), BalanceFix.read("USD 50", Currency.USD))
    }

    @Test
    fun aMinusSignAndNonsenseAreRefusedWithTheirOwnReasons() {
        assertTrue((BalanceFix.read("-500", inr) as BalanceFix.Input.Bad).message.contains("without a minus sign"))
        assertTrue((BalanceFix.read("(500)", inr) as BalanceFix.Input.Bad).message.contains("without a minus sign"))
        for (t in listOf("12abc", "1.2.3", "1,5.555", "five", ".")) assertEquals(BalanceFix.Input.Bad("Enter the amount as a number, like 25000 or 1250.50"), BalanceFix.read(t, inr), t)
        assertEquals(BalanceFix.Input.Bad("Enter the amount as a number, like 25000"), BalanceFix.read("12.5", Currency("JPY", 0)))
    }

    // ---- huge amounts ----

    @Test
    fun theLargestAmountTheFieldTakesIsHandledAndNothingOverflows() {
        assertEquals(BalanceFix.Input.Amount(BalanceFix.LIMIT), BalanceFix.read("90071992547409.91", inr), "2^53 - 1 minor units, the most a balance holds exactly")
        assertEquals(9_007_199_254_740_991L, BalanceFix.LIMIT)
        val past = (BalanceFix.read("999999999999999.99", inr) as BalanceFix.Input.Amount).minor
        assertEquals(99_999_999_999_999_999L, past, "the field reads 15 whole digits")
        assertNull(BalanceFix.plan(0, past), "but that is past what a balance holds exactly: it has no plan")
        assertTrue(BalanceFix.read("1000000000000000", inr) is BalanceFix.Input.Bad, "16 digits is refused by the field")
        val plan = BalanceFix.plan(BalanceFix.LIMIT, -BalanceFix.LIMIT)!!
        assertEquals(-2 * BalanceFix.LIMIT, plan.deltaMinor)
        assertNull(BalanceFix.plan(BalanceFix.LIMIT + 1, 0), "a balance beyond the limit has no plan")
        assertNull(BalanceFix.plan(0, -BalanceFix.LIMIT - 1))
        assertNull(BalanceFix.plan(Long.MIN_VALUE, 0))
        assertNull(BalanceFix.plan(0, Long.MAX_VALUE))
        assertEquals(0L, BalanceFix.newOpening(BalanceFix.LIMIT, -BalanceFix.LIMIT))
        assertNull(BalanceFix.newOpening(BalanceFix.LIMIT, 1), "the opening balance may not pass the limit")
        assertNull(BalanceFix.newOpening(-BalanceFix.LIMIT, -1))
        assertNull(BalanceFix.newOpening(Long.MAX_VALUE, 1))
        assertNull(BalanceFix.newOpening(0, Long.MIN_VALUE))
        assertEquals("That amount is too large for the app to hold.", BalanceFix.TOO_LARGE)
    }

    // ---- the words ----

    @Test
    fun theEffectSaysNothingIsSpentAndACardMentionsItsStatements() {
        assertEquals(
            "After fixing, HDFC Card will show ₹5,500.00 owed. No spending or income is added, so your budgets and reports stay as they are. The balance counts as owed since before your first entry here, so the statement and limit figures follow.",
            BalanceFixText.effect("HDFC Card", AccountType.CREDIT_CARD, -5_500_00, money),
        )
        assertEquals("After fixing, Savings will show ₹24,100.00. No spending or income is added, so your budgets and reports stay as they are.", BalanceFixText.effect("Savings", AccountType.BANK, 24_100_00, money))
    }

    @Test
    fun theHistoryLineAndTheUndoBarNameBothBalances() {
        val e = BalanceFixEntry(1_760_000_000_000, -3_000_00, -5_500_00)
        assertEquals("Balance fixed: ₹3,000.00 owed → ₹5,500.00 owed", BalanceFixText.fixed(AccountType.CREDIT_CARD, e.beforeMinor, e.afterMinor, money))
        assertEquals("Balance fixed: ₹3,000.00 owed → ₹5,500.00 owed on 11 Nov 2026", BalanceFixText.historyLine(AccountType.CREDIT_CARD, e, "11 Nov 2026", money))
        assertEquals(-2_500_00L, e.deltaMinor)
        assertEquals("Balance fixed: ₹25,000.00 → ₹24,100.00", BalanceFixText.fixed(AccountType.BANK, 25_000_00, 24_100_00, money))
        assertEquals("Balance fixed: ₹2,000.00 in credit → nothing owed", BalanceFixText.fixed(AccountType.CREDIT_CARD, 2_000_00, 0, money))
        assertEquals("Balance fixed: ₹500.00 overdrawn → ₹100.00", BalanceFixText.fixed(AccountType.BANK, -500_00, 100_00, money))
        assertEquals("Looks like some earlier spending is missing.", BalanceFixText.HINT)
        assertEquals("Fix this balance", BalanceFixText.TITLE)
        assertEquals("Fix balance", BalanceFixText.CONFIRM)
        assertEquals("I owe" to "In credit (nothing owed)", BalanceFixText.choices(AccountType.CREDIT_CARD))
        assertEquals("It has money" to "Overdrawn", BalanceFixText.choices(AccountType.BANK))
    }

    // ---- the line a fix leaves ----

    @Test
    fun theLogRoundTripsKeepsTheNewestTwentyAndDropsWhatIsNotALine() {
        val entries = (1..25).map { BalanceFixEntry(it * 1000L, it * 100L, it * 100L + 7) }
        val text = BalanceFixLog.format(entries)
        val back = BalanceFixLog.parse(text)
        assertEquals(entries.takeLast(20), back)
        assertEquals(emptyList(), BalanceFixLog.parse(null))
        assertEquals(emptyList(), BalanceFixLog.parse(""))
        val damaged = "1000,5,9;nonsense;2000,1;3000,x,9;-1,5,9;4000,5,5;5000,${BalanceFix.LIMIT + 1},0;6000,-10,20"
        assertEquals(listOf(BalanceFixEntry(1000, 5, 9), BalanceFixEntry(6000, -10, 20)), BalanceFixLog.parse(damaged), "only readable, in-range, changing lines stay")
        assertEquals("balance_fix:abc", BalanceFixLog.key("abc"))
    }

    @Test
    fun addingAndRemovingALineKeepsTheOthers() {
        val a = BalanceFixEntry(1, 0, 10); val b = BalanceFixEntry(2, 10, 30)
        assertEquals(listOf(a, b), BalanceFixLog.add(listOf(a), b))
        assertEquals(listOf(a), BalanceFixLog.remove(listOf(a, b), 2))
        assertEquals(listOf(a, b), BalanceFixLog.remove(listOf(a, b), 99))
        assertEquals(20, BalanceFixLog.add((1..20).map { BalanceFixEntry(it.toLong(), 0, it.toLong()) }, b).size)
    }
}
