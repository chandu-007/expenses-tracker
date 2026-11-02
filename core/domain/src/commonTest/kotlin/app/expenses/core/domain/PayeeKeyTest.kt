package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayeeKeyTest {
    private fun same(vararg payees: String) {
        val keys = payees.map { it to PayeeKey.of(it) }
        assertTrue(keys.all { it.second != null && it.second == keys.first().second }, "one key for all of: $keys")
    }

    @Test
    fun theSameKnownMerchantWrittenManyWaysIsOneKey() {
        same("Swiggy", "SWIGGY", "swiggy@icici", "swiggyupi@axb", "SWIGGY BANGALORE KA IN", "SWIGGY*ORDER", "Swiggy Limited", "SWIGGY BANGALORE 0123", "SWIGGY ORDER")
        same("Zomato", "ZOMATO GURGAON HR IND", "zomato@paytm", "zomato.order@hdfcbank")
        same("Uber", "UBER INDIA SYSTEMS PVT LTD", "UBER INDIA 0123 MUMBAI", "uber@axisbank")
        assertEquals("swiggy", PayeeKey.of("SWIGGY BANGALORE KA IN"))
    }

    @Test
    fun theSameUnknownShopWrittenManyWaysIsOneKey() {
        same("FRESHPIK BANGALORE IN", "Freshpik", "FRESHPIK", "freshpik@ybl", "FRESHPIK 2231", "FRESHPIK PVT LTD")
        same("ANNAPURNA MESS", "Annapurna Mess", "annapurna.mess@okicici", "ANNAPURNA MESS 0042", "ANNAPURNA MESS MUMBAI MH IN")
        same("KIRANA MART 2231", "KIRANA MART", "kirana mart 77010")
        same("EAZYDINE0000000", "EAZYDINE", "eazydine@okaxis")
        same("RAVI KUMAR", "Ravi Kumar", "ravi kumar")
    }

    @Test
    fun differentPayeesStayApart() {
        assertNotEquals(PayeeKey.of("Swiggy"), PayeeKey.of("Swiggy Instamart"), "two merchants of one company")
        assertNotEquals(PayeeKey.of("Amazon"), PayeeKey.of("Amazon Prime"))
        assertNotEquals(PayeeKey.of("Uber"), PayeeKey.of("Uber Singh"), "a person")
        assertNotEquals(PayeeKey.of("SHOP4821@ybl"), PayeeKey.of("shop@ybl"), "digits in an address say who it is")
        assertNotEquals(PayeeKey.of("shop4821@ybl"), PayeeKey.of("shop4822@ybl"))
        assertNotEquals(PayeeKey.of("Sri Lakshmi Stores"), PayeeKey.of("Sri Lakshmi Hotel"))
        assertNotEquals(PayeeKey.of("SECTOR 7 STORES"), PayeeKey.of("SECTOR STORES"), "a small number is part of the name")
    }

    @Test
    fun anAddressWithNothingElseKeepsItsDigitsAndLosesItsBank() {
        assertEquals("9704849321", PayeeKey.of("9704849321@yescred"))
        assertEquals("shop4821", PayeeKey.of("shop4821@okaxis"))
        assertEquals("ravik", PayeeKey.of("ravi.k@oksbi"))
    }

    @Test
    fun nothingUsableGivesNoKey() {
        assertNull(PayeeKey.of(null)); assertNull(PayeeKey.of("")); assertNull(PayeeKey.of("  ")); assertNull(PayeeKey.of("a")); assertNull(PayeeKey.of("12345"))
    }

    @Test
    fun aNameMadeOnlyOfNoiseFallsBackToItsLetters() {
        assertEquals("onlinepayment", PayeeKey.of("Online Payment"))
    }

    @Test
    fun theOldKeyIsStillFoundAndTheNewOneWinsWhenBothExist() {
        val old = mapOf("swiggybangalorekain" to "Groceries")
        assertEquals("Groceries", PayeeKey.lookup("SWIGGY BANGALORE KA IN", old), "a choice learned before the key changed still applies to the same text")
        assertNull(PayeeKey.lookup("SWIGGY", old), "...and only to the same text")
        assertEquals("Food", PayeeKey.lookup("SWIGGY BANGALORE KA IN", old + ("swiggy" to "Food")))
        assertEquals("Food", PayeeKey.lookup("swiggy@icici", mapOf("swiggy" to "Food")))
    }

    @Test
    fun marketplacesAndWalletsAreMixedButOrdinaryShopsAreNot() {
        for (p in listOf("Amazon", "AMAZON PAY INDIA", "amazon.in", "Paytm", "PAYTM", "Google Play", "PhonePe", "Flipkart", "Flipkart Internet", "RELIANCE")) assertTrue(PayeeKey.isMixed(p), p)
        for (p in listOf("Swiggy", "Netflix", "BESCOM", "Freshpik", "Ravi Kumar", "Uber")) assertTrue(!PayeeKey.isMixed(p), p)
    }

    /** The share of "learned from one spelling, found under another" that works, with the key that was used before (letters and digits as read) and the key of today. Printed as evidence. */
    @Test
    fun measureLearningReachAcrossSpellings() {
        val groups = listOf(
            listOf("swiggy@icici", "SWIGGY BANGALORE KA IN", "Swiggy", "SWIGGY*ORDER", "swiggyupi@axb"),
            listOf("zomato@paytm", "ZOMATO GURGAON HR IND", "Zomato"),
            listOf("FRESHPIK BANGALORE IN", "Freshpik", "freshpik@ybl", "FRESHPIK 2231"),
            listOf("ANNAPURNA MESS", "annapurna.mess@okicici", "ANNAPURNA MESS 0042", "Annapurna Mess"),
            listOf("UBER INDIA SYSTEMS PVT LTD", "uber@axisbank", "UBER INDIA 0123 MUMBAI", "Uber"),
            listOf("KIRANA MART 2231", "KIRANA MART", "kirana mart 77010"),
            listOf("RAVI KUMAR", "Ravi Kumar", "ravi kumar"),
            listOf("BIGBASKET", "PAYU*BIGBASKET", "bigbasket@hdfcbank", "BIGBASKET BANGALORE", "BigBasket"),
        )
        var pairs = 0; var oldHits = 0; var newHits = 0
        for (g in groups) for (learnedFrom in g) for (seenAs in g) {
            if (learnedFrom == seenAs) continue
            pairs++
            if (CategorySuggest.merchantKey(learnedFrom) == CategorySuggest.merchantKey(seenAs)) oldHits++
            if (PayeeKey.of(learnedFrom) == PayeeKey.of(seenAs)) newHits++
        }
        println("PAYEE-KEY learning reach across spellings of one payee: $pairs (learned as, seen as) pairs; old key finds ${oldHits} (${100 * oldHits / pairs}%), new key finds ${newHits} (${100 * newHits / pairs}%)")
        assertEquals(pairs, newHits, "the new key reaches every spelling of the invented payees")
        assertTrue(newHits > oldHits * 2)
    }
}
