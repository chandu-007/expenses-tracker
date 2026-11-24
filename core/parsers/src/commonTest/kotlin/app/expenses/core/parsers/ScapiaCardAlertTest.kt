package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The Scapia Federal credit card's alerts come from Federal Bank's header FEDSCP ("VM-FEDSCP-S"), not FEDBNK. Up to 0.16.1 the header was not on the list, so the owner's card spends
 * were dropped at the sender check (NOT_ALLOWLISTED) before their words were read; and the words say neither "debited" nor "spent", only "Your txn of ₹x at <shop> ... was successful",
 * so even a trusted FEDSCP or a FEDBNK text was "not a transaction". The three [spends] are the owner's own texts (amounts and shop kept, they carry no card digits or reference);
 * every other text here is invented in the same shape.
 */
class ScapiaCardAlertTest {
    private val today = LocalDate(2026, 11, 24)
    private fun spend(amount: String) = "Hi! Your txn of ₹$amount at grab Bangkok Th on your Scapia Federal Visa credit card was successful. Not you? Go to Scapia support on the app.- Federal Bank"
    private val spends = listOf(spend("897.39") to 89_739L, spend("784.87") to 78_487L, spend("304.62") to 30_462L)

    private fun classify(sender: String, body: String) = CaptureClassifier.classify(sender, body, emptySet(), today)
    private fun tx(sender: String, body: String) = assertIs<CaptureOutcome.Transaction>(classify(sender, body), "$sender: $body")

    @Test
    fun theOwnersThreeCardSpendsAreDebitsOfTheScapiaCardWithAmountAndShopRead() {
        for ((body, paise) in spends) {
            val t = tx("VM-FEDSCP-S", body)
            assertEquals("Scapia", t.issuer); assertEquals("FEDSCP", t.headerRoot)
            val a = t.alert
            assertEquals(Direction.DEBIT, a.direction, body)
            assertEquals(paise, a.amountMinor, body)
            assertEquals("INR", a.currency); assertNull(a.originalCurrency, "a shop abroad, but the card charged rupees")
            assertNull(a.accountLast4, "the text prints no card digits"); assertNull(a.reference); assertNull(a.date)
            assertEquals("grab Bangkok Th", PayeeGuess.of(body))
            assertEquals(MessageKind.PAYMENT, KindRules.of(body, a.direction), "an expense, not a card payment")
        }
    }

    @Test
    fun everyFormOfTheHeaderGetsThroughAndTheSameWordsFromFederalBanksOwnHeaderAreReadToo() {
        val body = spends.first().first
        for (sender in listOf("VM-FEDSCP-S", "AX-FEDSCP-S", "JD-FEDSCP-S", "VM-FEDSCP-T", "AX-FEDSCP-T", "JD-FEDSCP-T", "FEDSCP")) {
            assertEquals("FEDSCP", SenderRules.headerRoot(sender), sender)
            val t = tx(sender, body)
            assertEquals("Scapia", t.issuer, sender); assertEquals(89_739L, t.alert.amountMinor, sender); assertEquals(Direction.DEBIT, t.alert.direction, sender)
        }
        val fed = tx("VM-FEDBNK-S", body)
        assertEquals("Federal Bank", fed.issuer); assertEquals(Direction.DEBIT, fed.alert.direction); assertEquals(89_739L, fed.alert.amountMinor)
    }

    @Test
    fun theScapiaCardIsACardOfItsOwnAndDoesNotTakeFederalBankAccountsAway() {
        assertEquals(BankChoices.Choice("Scapia", listOf("FEDSCP"), isCard = true), BankChoices.all.single { "FEDSCP" in it.senderNames })
        assertEquals("Federal Bank", BankChoices.issuerOfAccountName("Federal Savings"))
        assertEquals("Scapia", BankChoices.issuerOfAccountName("Scapia card"))
        assertNull(SenderRules.emailIssuer("Scapia"), "no Scapia email has been seen: emails are not read as its")
    }

    @Test
    fun aDeclinedOneIsIgnoredAReversalIsACreditAndAForeignAmountKeepsTheRupeesCharged() {
        assertEquals(
            CaptureOutcome.Ignored(IgnoreReason.DECLINED, "FEDSCP"),
            classify("VM-FEDSCP-S", "Hi! Your txn of ₹1,200.00 at ACME STORES on your Scapia Federal Visa credit card was declined. Not you? Go to Scapia support on the app.- Federal Bank"),
        )
        val reversed = "Hi! Your txn of ₹897.39 at grab Bangkok Th on your Scapia Federal Visa credit card has been reversed.- Federal Bank"
        val r = tx("VM-FEDSCP-S", reversed).alert
        assertEquals(Direction.CREDIT, r.direction); assertEquals(89_739L, r.amountMinor)
        assertEquals(MessageKind.REFUND, KindRules.of(reversed, r.direction))

        val forex = tx("VM-FEDSCP-S", "Hi! Your txn of USD 25.00 (₹2,140.50) at SAMPLESTREAM.COM on your Scapia Federal Visa credit card was successful. Not you? Go to Scapia support on the app.- Federal Bank").alert
        assertEquals(Direction.DEBIT, forex.direction)
        assertEquals(214_050L, forex.amountMinor); assertEquals("INR", forex.currency)
        assertEquals("USD", forex.originalCurrency); assertEquals(2_500L, forex.originalAmountMinor)
    }

    @Test
    fun aRefundCreditedToTheCardNamesTheShopItCameFrom() {
        val refund = "Hi! A refund of ₹897.39 from grab Bangkok Th has been credited to your Scapia Federal Visa credit card.- Federal Bank"
        val a = tx("VM-FEDSCP-S", refund).alert
        assertEquals(Direction.CREDIT, a.direction); assertEquals(89_739L, a.amountMinor)
        assertEquals("grab Bangkok Th", PayeeGuess.of(refund))
    }

    @Test
    fun aPasswordATxnUnderReviewAndAnOfferAreNotTransactions() {
        assertEquals(
            CaptureOutcome.Ignored(IgnoreReason.OTP, "FEDSCP"),
            classify("VM-FEDSCP-S", "482913 is the OTP for your txn of ₹897.39 at grab Bangkok Th on your Scapia Federal Visa credit card. Do not share it.- Federal Bank"),
        )
        assertEquals(
            CaptureOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION, "FEDSCP"),
            classify("VM-FEDSCP-S", "Hi! Your txn of ₹897.39 at grab Bangkok Th on your Scapia Federal Visa credit card is under review.- Federal Bank"),
        )
        // the fraud line "Not you?" alone, with an amount but no txn, is no payment
        assertEquals(
            CaptureOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION, "FEDSCP"),
            classify("VM-FEDSCP-S", "Hi! Get flat ₹500 off on your next flight booked on the Scapia app. Not you? Go to Scapia support on the app.- Federal Bank"),
        )
    }

    @Test
    fun theSameWordingFromAnotherListedBankIsAPaymentButADeclinedOneIsStillNot() {
        val t = tx("AX-AXISBK-S", "Your transaction of Rs.2,000.00 at ACME STORES on your card ending 4321 was successful.")
        assertEquals(Direction.DEBIT, t.alert.direction); assertEquals(200_000L, t.alert.amountMinor); assertEquals("4321", t.alert.accountLast4)
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.DECLINED, "AXISBK"), classify("AX-AXISBK-S", "Your transaction of Rs.2,000.00 at ACME STORES on your card ending 4321 was unsuccessful."))
    }
}
