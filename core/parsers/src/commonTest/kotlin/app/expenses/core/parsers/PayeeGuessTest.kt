package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayeeGuessTest {
    @Test
    fun aVpaIsTheNameWhenOneIsPresentWhoeverTheDirection() {
        assertEquals("shop@okhdfc", PayeeGuess.of("Rs.1,250.00 debited from A/c XX1234 to VPA shop@okhdfc (UPI Ref 628374659283)"))
        assertEquals("ravi.k@ybl", PayeeGuess.of("Rs 500 credited to A/c XX1234 from VPA Ravi.K@ybl on 05-10-26"))
        assertEquals("zomato@icici", PayeeGuess.of("Sent Rs.300.00 from HDFC Bank A/c *1234 To zomato@icici On 05/10/26"))
    }

    @Test
    fun aNamedMerchantAfterAtOrToIsTakenUpToTheNextFixedWord() {
        assertEquals("AMAZON PAY", PayeeGuess.of("Rs.499.00 spent on card ending 4321 at AMAZON PAY on 05-Oct-26. Avl limit Rs.50,000"))
        assertEquals("Swiggy", PayeeGuess.of("INR 250.00 spent using HDFC Bank Card x4321 at Swiggy on 05-10-26"))
        assertEquals("BigBasket", PayeeGuess.of("Your a/c XX1234 debited Rs.800 towards BigBasket via UPI"))
    }

    @Test
    fun theUpiPathNameIsUsedWhenThereIsNoVpa() {
        assertEquals("RAVI KUMAR", PayeeGuess.of("Rs.200 debited A/c XX1234 UPI/P2A/628374659283/RAVI KUMAR/ok"))
    }

    @Test
    fun wordsThatAreNotNamesAndTextWithNoPayeeGiveNothing() {
        assertNull(PayeeGuess.of("Rs.100 debited from your account on 05-10-26"))
        assertNull(PayeeGuess.of("Rs.100 credited to your a/c XX1234 on 05-10-26"))
        assertNull(PayeeGuess.of("Rs.5,000 withdrawn at ATM 12345 on 05-10-26"), "digits are not a name")
        assertNull(PayeeGuess.of(""))
    }

    @Test
    fun aPaymentAppNotificationGivesThePersonsNameFromItsTitleOrLeadingSentence() {
        assertEquals("Chandu", PayeeGuess.of("Chandu: Chandu. sent ₹10 to you."), "the owner's real PhonePe wording")
        assertEquals("Ravi Kumar", PayeeGuess.of("Ravi Kumar: Received ₹250 in your account"))
        assertEquals("Anita", PayeeGuess.of("Anita paid you ₹120 for lunch"))
        assertEquals("Anita Rao", PayeeGuess.of("Anita Rao: paid you Rs.120"))
    }

    @Test
    fun aHeadingOrABankIsNotTakenForAPerson() {
        assertNull(PayeeGuess.of("Payment successful: ₹75 paid"))
        assertNull(PayeeGuess.of("Money received: ₹75 added"))
        assertNull(PayeeGuess.of("HDFC Bank: ₹75 debited"))
        assertNull(PayeeGuess.of("Dear Customer: ₹75 debited"))
        assertNull(PayeeGuess.of("Paid ₹75: to a shop"), "a title with an amount is not a name")
        assertEquals("Chandu", PayeeGuess.of("Chandu: Rs.75 debited to VPA 9704849321@yescred"), "a person's name in the title beats a bare phone-number address")
        assertEquals("shop@okhdfc", PayeeGuess.of("Rs.75 debited to VPA shop@okhdfc"), "with no title the address is still the name")
    }

    @Test
    fun aCreditTextThatSaysSentByNamesTheSender() {
        assertEquals("RAVI KUMAR", PayeeGuess.of("Rs.250.00 received in A/c XX1234 on 10-10-26. Sent by RAVI KUMAR via UPI Ref 628374659001"))
        assertEquals("Anita Rao", PayeeGuess.of("Rs 75 credited. Transferred by Anita Rao on 05-10-26"))
        assertNull(PayeeGuess.of("Rs.499.00 paid by your card on 05-10-26"), "\"paid by your card\" is not a person")
        assertNull(PayeeGuess.of("Rs.100 credited. Sent by 9876543210 via UPI"), "a number is not a name")
    }

    private fun card(shop: String) = "Spent Rs.450 On HDFC Bank Card 4321 At $shop On 2026-10-05:19:22:01.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 4321 to 7308080808"

    @Test
    fun aGatewayPrefixOnACardNameIsDroppedAndTheShopIsWhatFollowsIt() {
        assertEquals("SWIGGY", PayeeGuess.of(card("RAZ*SWIGGY")))
        assertEquals("DOMINOS", PayeeGuess.of(card("PYU*DOMINOS")))
        assertEquals("BIGBASKET", PayeeGuess.of(card("PAYU*BIGBASKET")))
        assertEquals("AIRTEL", PayeeGuess.of(card("BILLDESK*AIRTEL")))
        assertEquals("ZOMATO", PayeeGuess.of(card("PAYTM*ZOMATO")))
        assertEquals("SWIGGY ORDER", PayeeGuess.of(card("SWIGGY*ORDER")), "a star inside the name is a space")
        assertEquals("PAYTM", PayeeGuess.of(card("PAYTM")), "the gateway's own name alone is still a name")
    }

    @Test
    fun terminalAndBranchNumbersAreCutOffANameButASmallNumberStays() {
        assertEquals("KIRANA MART", PayeeGuess.of(card("KIRANA MART 2231")))
        assertEquals("EAZYDINE", PayeeGuess.of(card("EAZYDINE0000000")))
        assertEquals("UBER INDIA MUMBAI", PayeeGuess.of(card("UBER INDIA 0123 MUMBAI")))
        assertEquals("INDIAN OIL FUEL STN", PayeeGuess.of(card("INDIAN OIL FUEL STN 1234")))
        assertEquals("SECTOR 7 STORES", PayeeGuess.of(card("SECTOR 7 STORES")))
        assertNull(PayeeGuess.of("Rs.5,000 withdrawn at ATM 12345 on 05-10-26"))
        assertNull(PayeeGuess.of(card("POS 12345")))
    }

    @Test
    fun aLongCompanyNameIsReadWhole() {
        assertEquals("MYNTRA DESIGNS PVT LTD BANGALORE", PayeeGuess.of(card("MYNTRA DESIGNS PVT LTD BANGALORE")))
        assertEquals("UBER INDIA SYSTEMS PVT LTD", PayeeGuess.of(card("UBER INDIA SYSTEMS PVT LTD")))
    }

    @Test
    fun theNameBeforeCreditedAfterASemicolonIsThePayeeOfAnIciciOrIdfcUpiDebit() {
        assertEquals("Pune Metro", PayeeGuess.of("ICICI Bank Acct XX123 debited for Rs 14.00 on 09-May-26; Pune Metro credited. UPI:628374659283. Call 18002662 for dispute."))
        assertEquals("Swiggy", PayeeGuess.of("ICICI Bank Acct XX123 debited for Rs 250.00 on 05-Oct-26; Swiggy credited. UPI:628374659283."))
        assertEquals("A.B. MART", PayeeGuess.of("Your A/c XX0000 debited by Rs. 750.00 on 09/05/26; M/S A.B. MART credited. RRN 000000000001. Available balance Rs. 10,000.00."))
        assertEquals("CUSTOMER NAME", PayeeGuess.of("Your A/c XX0000 debited by Rs. 20,000.00 on 09/05/26; CUSTOMER NAME credited. RRN 000000000000."))
    }

    @Test
    fun anAxisUpiPathNameStopsBeforeTheWordsThatFollowIt() {
        assertEquals("SWIGGY", PayeeGuess.of("INR 250.00 debited A/c no. XX1234 05-10-26, 14:22:11 UPI/P2M/628374659283/SWIGGY Not you? SMS BLOCKUPI Cust ID to 919900000000 Axis Bank"))
        assertEquals("RAVI KUMAR", PayeeGuess.of("INR 250.00 debited A/c no. XX1234 05-10-26, 14:22:11 UPI/P2A/628374659283/RAVI KUMAR Not you? SMS BLOCKUPI"))
    }

    @Test
    fun aPaymentAppsOwnNameIsNeverThePersonButWhatItPaidIs() {
        assertEquals("Swiggy", PayeeGuess.of("Google Pay: Paid ₹250 to Swiggy"))
        assertNull(PayeeGuess.of("PhonePe: Payment of ₹250 done"))
        assertNull(PayeeGuess.of("Paytm: ₹250 debited"))
    }

    @Test
    fun theShopAfterTheDateAcceptsATerminalNumberAndStillRefusesWording() {
        assertEquals("SHELL PETROL PUMP", PayeeGuess.of("INR 450.00 spent using ICICI Bank Card XX4417 on 05-Oct-26 on SHELL PETROL PUMP 4521. Avl Limit: INR 1,00,000.00."))
    }

    @Test
    fun aMaskedAccountOrTheBanksOwnSentenceIsNeverTheNameAndWordsThatFollowAreCutOff() {
        assertEquals("SampleRide", PayeeGuess.of("Sent Rs.9.00 from XX0000 to SampleRide on 08-Sep-26. UPI ref no. 000000000001."))
        assertEquals("RAHUL SHARMA", PayeeGuess.of("Sent Rs.100.00 from XX1234 to RAHUL SHARMA on 03-Sep-26. UPI ref no. 000000000000."))
        assertEquals("PZCREDIT", PayeeGuess.of("Spent Rs.3000 From HDFC Bank Card x0000 At PZCREDIT0000000 On 2026-05-02:00:17:56 Bal Rs.142.26"))
        assertEquals("SampleMerchant Store", PayeeGuess.of("Rs 100.00 spent on HSBC card XX0000 at SampleMerchant Store for INR 100.00 on 01-05-26."))
        assertEquals("SampleCloud", PayeeGuess.of("Rs 100 was paid to SampleCloud with your BOBCARD ending 0000 on 01-05-26"))
        assertEquals("RAHUL SHARMA", PayeeGuess.of("Rs.100 debited from A/c XX1234 on 05-10-26 to RAHUL SHARMA.RRN 628374659283"))
        assertNull(PayeeGuess.of("Rs.2000 debited from A/c XX1234 on 05-10-26 InfoTRF TO FD no..Avl Bal Rs.99.00"), "a deposit is not a payee")
    }
}
