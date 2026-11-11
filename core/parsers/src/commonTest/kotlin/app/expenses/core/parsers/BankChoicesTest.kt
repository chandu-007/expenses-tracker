package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BankChoicesTest {
    @Test
    fun oneChoiceForEachBankWithEveryOneOfItsSenderNames() {
        val hdfc = BankChoices.all.single { it.issuer == "HDFC Bank" }
        assertEquals(listOf("HDFCBK", "HDFCBN"), hdfc.senderNames)
        assertEquals(BankChoices.all.size, BankChoices.all.map { it.issuer }.toSet().size, "no bank twice")
        assertEquals(listOf("ICICIB", "ICICIT"), BankChoices.senderNamesOf("ICICI Bank"))
        assertEquals(emptyList(), BankChoices.senderNamesOf("Not A Bank"))
    }

    @Test
    fun paymentAppsAndBillersAreNotBanks() {
        val names = BankChoices.issuers.toSet()
        for (r in BankRules.ALL) if (r.kind == SenderKind.PAYMENT_APP || r.kind == SenderKind.UTILITY) assertTrue(r.issuer !in names || BankRules.ALL.any { it.issuer == r.issuer && it.kind == SenderKind.BANK }, r.issuer)
        assertTrue("PhonePe" !in names && "BESCOM" !in names && "Airtel" !in names)
        assertTrue("SBI Card" in names, "a card issuer is offered")
    }

    @Test
    fun everySenderNameOfAChoiceIsOnTheBuiltInList() {
        for (c in BankChoices.all) for (n in c.senderNames) { assertEquals(c.issuer, SenderRules.BUILT_IN[n], n); assertEquals(c.issuer, BankChoices.issuerOfRoot(n.lowercase())) }
        assertEquals(null, BankChoices.issuerOfRoot("SHOPXX")); assertEquals(null, BankChoices.issuerOfRoot("PHONEPE"))
    }

    @Test
    fun theCommonBanksComeFirst() {
        assertTrue(BankChoices.issuers.take(BankChoices.POPULAR).containsAll(listOf("HDFC Bank", "ICICI Bank", "SBI", "Axis Bank")))
    }

    @Test
    fun anAccountsNameSaysWhichBankItIsOrNothing() {
        assertEquals("HDFC Bank", BankChoices.issuerOfAccountName("HDFC Savings"))
        assertEquals("ICICI Bank", BankChoices.issuerOfAccountName("my icici salary"))
        assertEquals("SBI Card", BankChoices.issuerOfAccountName("SBI Card Elite"), "the card issuer's whole name is in it")
        assertEquals("SBI", BankChoices.issuerOfAccountName("SBI Savings"))
        assertEquals(null, BankChoices.issuerOfAccountName("Salary")); assertEquals(null, BankChoices.issuerOfAccountName("Cash")); assertEquals(null, BankChoices.issuerOfAccountName("Wallet"))
    }
}
