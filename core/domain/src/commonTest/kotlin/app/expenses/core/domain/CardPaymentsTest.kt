package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardPaymentsTest {
    private fun txn(i: Int, text: String, flow: Flow = Flow.SPENDING, minor: Long = 1_000_00) = StatementTxn(i, LocalDate(2026, 10, 1 + i), flow, minor, text)

    @Test
    fun theWordingOfACardPaymentOnABankStatementIsRecognised() {
        listOf(
            "CREDIT CARD PAYMENT", "Credit Card Bill", "CC PAYMENT", "AUTOPAY CC", "PAYMENT TO CARD XX1234", "NEFT-HDFC0000001-HDFC CREDIT CARD",
            "ICICI CARD PAYMENT", "IMPS-CC-PYMT-ICICI", "CCPAYMENT 4111XXXXXXXX1234", "BBPS-BILLDESK HDFC BANK CARD", "AUTO PAY HDFC CREDIT CARD", "SBI CARD PAYMENT", "UPI-CARD BILL-HDFC",
            "payment towards card ending 4821", "CREDIT CARD BILL PAYMENT HDFC", "CC PAYMENT HDFC", "BILLDESK*HDFC CREDIT CARD",
            // the ones a bank's name or an aggregator's makes safe
            "CRED", "CRED CLUB", "UPI-CRED.CLUB@AXISB-CRED CLUB", "SBICARD PAYMENT", "SBICARD", "AMEX", "AMEX PAYMENT", "AMERICAN EXPRESS", "UPI-AMAZON PAY ICICI CARD", "BILLDESK*ICICI", "BBPS-AXIS BANK CARD",
            "YES BANK CARD PAYMENT", "BOBCARD PAYMENT", "STANDARD CHARTERED CARD BILL", "IDFC FIRST CARD PAYMENT", "RBL CARD DUES",
        ).forEach { assertTrue(CardPayments.looksLikePaymentToCard(it), it) }
    }

    @Test
    fun aShopsOwnCardOrAnythingThatIsNotPayingACreditCardIsLeftAlone() {
        listOf(
            "CARD WORLD", "UPI-CARD WORLD-PAYTM", "MERCHANT CARD WORLD PAYMENT", "SWIGGY", "BILLDESK ELECTRICITY", "ATM CASH WITHDRAWAL", "DEBIT CARD ANNUAL FEE", "DEBIT CARD PAYMENT AT SHOP",
            "GIFT CARD PAYMENT", "PREPAID CARD LOAD", "CREDIT CARD ANNUAL FEE", "CREDIT CARD INTEREST", "CCAVENUE PAYMENT", "CC AVENUE", "POS CARD PAYMENT 1234", "CARDIO GYM", "SALARY", "",
            "CREDIT CARD REFUND", "FOREX CARD RELOAD", "BILLDESK", "AUTOPAY NETFLIX", "REFUND TO CARD", "REFUND CREDIT CARD PAYMENT", "CASHBACK CREDIT CARD", "METRO CARD RECHARGE BILL",
            // the reviewer's: any "<something> card payment" is not a bank's card
            "STARBUCKS CARD PAYMENT", "FUEL CARD PAYMENT IOCL", "SODEXO CARD PAYMENT", "BIG BAZAAR CARD DUES", "IMPS-CARD PAYMENT-RAMESH", "UPI-WORLD CARD PAYMENT-SHOP", "BILLDESK*PVR E CARD",
            "BILLDESK CARD", "UPI-CARD PAYMENT-SHOP", "CARD PAYMENT", "CARD BILL", "BIGBASKET CARD", "TATA NEU CARD RELOAD",
            // a bank's name that is paying the bank for something else
            "BILLDESK*HDFC LIFE", "BBPS-ICICI PRUDENTIAL LIFE INSURANCE", "BILLDESK SBI LOAN", "HDFC BANK ATM WDL", "SBI DEBIT CARD ANNUAL FEE", "YES SIR CAFE", "AU REVOIR BAKERY", "SBI CARD ANNUAL FEE", "CREDIT SOCIETY",
        ).forEach { assertFalse(CardPayments.looksLikePaymentToCard(it), it) }
    }

    @Test
    fun theCreditOnACardStatementThatIsThePaymentIsRecognisedAndARefundIsNot() {
        listOf("PAYMENT RECEIVED - THANK YOU", "THANK YOU", "NEFT PAYMENT", "BBPS PAYMENT RECEIVED", "UPI PAYMENT", "Payment Recd", "AUTOPAY").forEach { assertTrue(CardPayments.looksLikePaymentReceived(it), it) }
        listOf("REFUND AMAZON", "CASHBACK CREDIT", "PAYMENT REVERSAL", "MERCHANT REFUND", "SWIGGY", "REVERSAL OF CHARGES", "").forEach { assertFalse(CardPayments.looksLikePaymentReceived(it), it) }
    }

    @Test
    fun theLastFourDigitsAreReadFromTheWaysABankWritesThem() {
        assertEquals("1234", CardPayments.last4("PAYMENT TO CARD XX1234"))
        assertEquals("1234", CardPayments.last4("CC PAYMENT ****1234"))
        assertEquals("1234", CardPayments.last4("4111XXXXXXXX1234 CREDIT CARD"))
        assertEquals("1234", CardPayments.last4("XXXX XXXX XXXX 1234"))
        assertEquals("4821", CardPayments.last4("payment towards card ending 4821"))
        assertEquals("9876", CardPayments.last4("CARD NO 9876 BILL"))
        assertEquals("1234", CardPayments.last4("card x1234"))
        assertNull(CardPayments.last4("NEFT-HDFCN52026101012345-HDFC CREDIT CARD"))
        assertNull(CardPayments.last4("PAYMENT 12345678"))
        assertNull(CardPayments.last4("XX12345"), "five digits are not a last four")
    }

    private val hdfc = CardRef("hdfc", "HDFC Regalia", "1234")
    private val icici = CardRef("icici", "ICICI Amazon Pay", "8888")
    private val bare = CardRef("bare", "Travel card", null)

    @Test
    fun aLineIsTiedToTheCardByItsDigitsFirstThenByTheBanksName() {
        assertEquals("hdfc", CardPayments.accountFor("PAYMENT TO CARD XX1234", listOf(hdfc, icici)))
        assertEquals("icici", CardPayments.accountFor("ICICI CARD PAYMENT", listOf(hdfc, icici)), "the bank's name when there are no digits")
        assertEquals("icici", CardPayments.accountFor("NEFT-HDFC0000001-ICICI CREDIT CARD", listOf(hdfc, icici)), "an IFSC code is not a name")
        assertNull(CardPayments.accountFor("CREDIT CARD PAYMENT", listOf(hdfc, icici)), "nothing to tell them apart")
        assertNull(CardPayments.accountFor("PAYMENT TO CARD XX9999", listOf(hdfc, icici)), "digits no card carries")
        assertNull(CardPayments.accountFor("PAYMENT TO CARD XX9999", listOf(hdfc, bare)), "a card with no digits is not guessed from digits that are someone else's")
        assertNull(CardPayments.accountFor("HDFC CARD PAYMENT XX9999", listOf(hdfc)), "the bank's name does not override digits that belong to nothing it has")
        assertNull(CardPayments.accountFor("CARD PAYMENT", listOf(hdfc, CardRef("hdfc2", "HDFC Millennia", "5678"))), "two cards, no digits, no name to choose")
        assertNull(CardPayments.accountFor("HDFC CARD PAYMENT", listOf(hdfc, CardRef("hdfc2", "HDFC Millennia", "5678"))), "the bank's name fits both")
    }

    @Test
    fun cardPaymentsOnABankStatementAreFoundWithTheirCards() {
        val txns = listOf(txn(0, "UPI SWIGGY"), txn(1, "ICICI CARD PAYMENT"), txn(2, "SALARY", Flow.INCOME), txn(3, "CC PAYMENT XX1234"), txn(4, "CREDIT CARD BILL REFUND", Flow.INCOME))
        val found = assertNotNull(CardPayments.find(txns, CardPayments.Side.BANK, listOf(hdfc, icici)))
        assertEquals(listOf(1, 3), found.indexes, "money in is never a payment to a card")
        assertEquals(mapOf(1 to "icici", 3 to "hdfc"), found.identified)
        assertNull(CardPayments.find(listOf(txn(0, "SWIGGY")), CardPayments.Side.BANK, listOf(hdfc)))
    }

    @Test
    fun theChoiceStartsAsATransferOnlyWhenTheCardIsKnown() {
        val txns = listOf(txn(0, "CREDIT CARD PAYMENT"), txn(1, "CC PAYMENT"))
        val found = CardPayments.find(txns, CardPayments.Side.BANK, listOf(hdfc))!!
        assertEquals(CardPaymentChoice.TRANSFER, CardPayments.defaultPick(found, listOf(hdfc)).choice, "the only card there is")
        assertNull(CardPayments.defaultPick(CardPayments.find(txns, CardPayments.Side.BANK, listOf(hdfc, icici))!!, listOf(hdfc, icici)).choice, "two cards and no way to tell: the person chooses")
        assertEquals(CardPaymentChoice.AS_IS, CardPayments.defaultPick(CardPayments.find(txns, CardPayments.Side.BANK, emptyList())!!, emptyList()).choice, "no card at all: it can only be spending")
        val named = listOf(txn(0, "PAYMENT TO CARD XX1234"), txn(1, "ICICI CARD PAYMENT"))
        assertEquals(CardPaymentChoice.TRANSFER, CardPayments.defaultPick(CardPayments.find(named, CardPayments.Side.BANK, listOf(hdfc, icici))!!, listOf(hdfc, icici)).choice, "every line names its card")
        val cardSide = CardPayments.find(listOf(txn(0, "PAYMENT RECEIVED", Flow.INCOME)), CardPayments.Side.CARD, listOf(CardRef("bank", "HDFC Bank")))!!
        assertEquals(CardPaymentChoice.SKIP, CardPayments.defaultPick(cardSide, listOf(CardRef("bank", "HDFC Bank"))).choice, "as a card's text statement is read")
    }

    @Test
    fun applyingTheChoiceTiesTransfersToTheirAccountsAndLeavesTheRestAlone() {
        val txns = listOf(txn(0, "UPI SWIGGY"), txn(1, "ICICI CARD PAYMENT"), txn(2, "CREDIT CARD PAYMENT"))
        val others = listOf(hdfc, icici)
        val found = CardPayments.find(txns, CardPayments.Side.BANK, others)!!
        assertNull(CardPayments.apply(txns, found, CardPayments.Pick(null), others), "nothing chosen")
        assertNull(CardPayments.apply(txns, found, CardPayments.Pick(CardPaymentChoice.TRANSFER), others), "the second line names no card and there are two")
        val chosen = CardPayments.apply(txns, found, CardPayments.Pick(CardPaymentChoice.TRANSFER, "hdfc"), others)!!
        assertEquals(listOf(null, "hdfc", "hdfc"), chosen.txns.map { it.counterAccount }, "a card chosen for all overrides what a line says")
        assertEquals(2, chosen.transfers)
        val skipped = CardPayments.apply(txns, found, CardPayments.Pick(CardPaymentChoice.SKIP), others)!!
        assertEquals(listOf("UPI SWIGGY"), skipped.txns.map { it.description }); assertEquals(2, skipped.skipped)
        val asIs = CardPayments.apply(txns, found, CardPayments.Pick(CardPaymentChoice.AS_IS), others)!!
        assertEquals(txns, asIs.txns)
        val one = CardPayments.apply(txns, CardPayments.find(txns, CardPayments.Side.BANK, listOf(hdfc))!!, CardPayments.Pick(CardPaymentChoice.TRANSFER), listOf(hdfc))!!
        assertEquals(listOf(null, "hdfc", "hdfc"), one.txns.map { it.counterAccount }, "with one card every line goes to it")
    }

    @Test
    fun withOneCardALineThatNamesOtherDigitsAnotherBankOrCredIsNotTakenForIt() {
        val only = listOf(hdfc)
        fun pick(vararg lines: String) = CardPayments.defaultPick(CardPayments.find(lines.mapIndexed { i, l -> txn(i, l) }, CardPayments.Side.BANK, only)!!, only).choice
        assertEquals(CardPaymentChoice.TRANSFER, pick("CREDIT CARD PAYMENT"), "names nothing: it can only be the one card")
        assertEquals(CardPaymentChoice.TRANSFER, pick("CC PAYMENT XX1234", "CREDIT CARD PAYMENT"), "the digits are the card's")
        assertEquals(CardPaymentChoice.TRANSFER, pick("HDFC CARD PAYMENT"), "the bank's name is the card's")
        assertNull(pick("CREDIT CARD PAYMENT XX9999"), "other digits")
        assertNull(pick("BILLDESK ICICI CREDIT CARD"), "another bank")
        assertNull(pick("CREDIT CARD PAYMENT", "CREDIT CARD PAYMENT XX9999"), "one line that does not fit keeps the whole choice open")
        assertNull(pick("CRED"), "CRED pays any card")
        assertNull(pick("UPI-AMAZON PAY ICICI CARD"))
        assertEquals(CardPaymentChoice.TRANSFER, CardPayments.defaultPick(CardPayments.find(listOf(txn(0, "CRED CLUB XX1234")), CardPayments.Side.BANK, only)!!, only).choice, "unless the line names the card")
    }

    @Test
    fun theCardCompaniesAreNamedOnceWhateverTheSpelling() {
        assertEquals(setOf("sbi"), CardPayments.issuersIn("SBICARD PAYMENT"))
        assertEquals(setOf("amex"), CardPayments.issuersIn("American Express bill"))
        assertEquals(setOf("yes"), CardPayments.issuersIn("YES BANK CARD")); assertEquals(emptySet(), CardPayments.issuersIn("YES SIR"))
        assertEquals(setOf("hdfc", "icici"), CardPayments.issuersIn("HDFC to ICICI"))
        assertEquals("amex1", CardPayments.accountFor("AMEX PAYMENT", listOf(CardRef("amex1", "American Express Platinum"), hdfc)))
        assertEquals("sbi1", CardPayments.accountFor("SBICARD PAYMENT", listOf(CardRef("sbi1", "SBI Elite"), hdfc)))
        assertTrue(CardPayments.isAggregator("CRED CLUB")); assertFalse(CardPayments.isAggregator("CREDIT CARD"))
    }
}
