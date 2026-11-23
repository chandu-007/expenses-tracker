package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N-01: the smart entry line. Invented lines only. */
class SmartEntryTest {
    private val accounts = listOf(
        SmartEntry.AccountName("cash", "Cash"),
        SmartEntry.AccountName("hdfc", "HDFC Bank"),
        SmartEntry.AccountName("icici-card", "ICICI Amazon Pay card"),
    )

    private fun r(text: String, digits: Int = 2) = SmartEntry.read(text, digits, accounts)

    private fun amount(text: String) = r(text).also { assertEquals(null, it.problem, "problem for '$text'") }.amountMinor

    // ---- the four examples of the backlog ----

    @Test fun chaiTwenty() {
        val x = r("chai 20")
        assertEquals(2_000L, x.amountMinor)
        assertEquals("Chai", x.note)
        assertEquals("Food", x.categoryNames.first())
        assertNull(x.daysAgo)
        assertTrue(x.usable)
    }

    @Test fun hinglishFillersAreNotPartOfTheNote() {
        val x = r("auto ke 80 diye")
        assertEquals(8_000L, x.amountMinor)
        assertEquals("Auto", x.note)
        assertEquals("Transport", x.categoryNames.first())
    }

    @Test fun uberWithDecimalsAndYesterday() {
        val x = r("uber 245.50 yesterday")
        assertEquals(24_550L, x.amountMinor)
        assertEquals("Uber", x.note)
        assertEquals(1, x.daysAgo)
        assertTrue("Transport" in x.categoryNames)
    }

    @Test fun rentWithAnAccountNamedByItsFirstWord() {
        val x = r("rent 18000 hdfc")
        assertEquals(1_800_000L, x.amountMinor)
        assertEquals("Rent", x.note)
        assertEquals("hdfc", x.accountId)
        assertEquals(listOf("Rent", "Bills"), x.categoryNames.take(2))
    }

    // ---- amounts ----

    @Test fun indianGrouping() = assertEquals(10_000_000L, amount("rent 1,00,000"))

    @Test fun indianGroupingWithPaise() = assertEquals(123_456_750L, amount("car 12,34,567.50"))

    @Test fun thousandsGrouping() = assertEquals(10_000_000L, amount("rent 100,000"))

    @Test fun aGroupingThatIsNoGroupingIsRefused() {
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("chai 12,34").problem, "a decimal comma is not guessed")
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("chai 1,0000").problem)
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("chai 1,00,00").problem)
    }

    @Test fun oneDecimalPlace() = assertEquals(24_550L, amount("lunch 245.5"))

    @Test fun moreDecimalsThanTheCurrencyHasIsRefused() = assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("lunch 245.555").problem)

    @Test fun aCurrencyWithoutDecimals() {
        assertEquals(1_200L, SmartEntry.read("ramen 1200", 0).amountMinor)
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, SmartEntry.read("ramen 12.5", 0).problem)
    }

    @Test fun kIsAThousand() {
        assertEquals(200_000L, amount("shoes 2k"))
        assertEquals(250_000L, amount("shoes 2.5K"))
        assertEquals(200_000L, amount("shoes 2 k"))
        assertEquals(200_000L, amount("shoes 2 thousand"))
        assertEquals(200_000L, amount("shoes 2 hazar"))
    }

    @Test fun lakhAsAWord() = assertEquals(15_000_000L, amount("bike 1.5 lakh"))

    @Test fun currencyMarksBeforeAndAfter() {
        assertEquals(2_000L, amount("chai ₹20"))
        assertEquals(2_000L, amount("chai rs.20"))
        assertEquals(2_000L, amount("chai Rs20"))
        assertEquals(2_000L, amount("chai 20rs"))
        assertEquals(2_000L, amount("chai 20/-"))
        assertEquals(2_000L, amount("chai ₹ 20"))
        assertEquals("Chai", r("chai 20 rupees").note)
    }

    @Test fun aSumTypedAsOneWord() {
        assertEquals(16_500L, amount("groceries 120+45"))
        assertEquals(4_000L, amount("chai 20x2"))
    }

    @Test fun noAmount() {
        val x = r("chai with ravi")
        assertEquals(SmartEntry.Problem.NO_AMOUNT, x.problem)
        assertNull(x.amountMinor)
        assertFalse(x.usable)
        assertEquals("Chai with ravi", x.note, "the words are still read, for the hint")
    }

    @Test fun twoNumbersAreAmbiguous() {
        val x = r("2 samosa 40")
        assertEquals(SmartEntry.Problem.TWO_AMOUNTS, x.problem)
        assertNull(x.amountMinor)
    }

    @Test fun twoNumbersWhereOneIsMarkedAsMoney() {
        val x = r("2 samosa ₹40")
        assertEquals(4_000L, x.amountMinor)
        assertEquals("2 samosa", x.note)
    }

    @Test fun twoMarkedAmountsAreAmbiguous() = assertEquals(SmartEntry.Problem.TWO_AMOUNTS, r("₹20 chai ₹30").problem)

    @Test fun zeroAndNegativeAreRefused() {
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("chai 0").problem)
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("chai -20").problem)
    }

    @Test fun aHugeNumberIsRefusedNotOverflowed() {
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("x 99999999999999999999").problem)
        assertEquals(SmartEntry.Problem.BAD_AMOUNT, r("x 9999999999999999 lakh").problem)
    }

    @Test fun aWordWithDigitsIsAWord() {
        val x = r("7eleven 150")
        assertEquals(15_000L, x.amountMinor)
        assertEquals("7eleven", x.note)
        assertEquals(1_500L, r("milk 2l 15").let { assertEquals("Milk 2l", it.note); it.amountMinor })
    }

    @Test fun punctuationAroundAWordOrNumber() {
        val x = r("chai, 20.")
        assertEquals(2_000L, x.amountMinor)
        assertEquals("Chai", x.note)
        assertEquals("Chai", r("chai - 20").note, "a lone dash is not a word")
    }

    // ---- dates ----

    @Test fun todayAndAaj() {
        assertEquals(0, r("chai 20 today").daysAgo)
        assertEquals(0, r("aaj chai 20").daysAgo)
    }

    @Test fun kalIsYesterdayOnlyWhenTheLineSaysItIsPast() {
        val past = r("kal auto ke 80 diye")
        assertEquals(1, past.daysAgo)
        assertFalse(past.dateUnclear)
        assertEquals("Auto", past.note)
        val unclear = r("kal chai 20")
        assertNull(unclear.daysAgo, "kal can be tomorrow")
        assertTrue(unclear.dateUnclear)
        assertEquals("Chai", unclear.note, "kal is never part of the note")
    }

    @Test fun dayBeforeYesterday() {
        val x = r("movie 500 day before yesterday")
        assertEquals(2, x.daysAgo)
        assertEquals("Movie", x.note)
        assertEquals(2, r("parso movie 500 diye").daysAgo)
    }

    @Test fun twoDayWordsAreUnclear() {
        val x = r("chai 20 today yesterday")
        assertNull(x.daysAgo)
        assertTrue(x.dateUnclear)
    }

    // ---- accounts ----

    @Test fun anAccountByItsWholeName() {
        val x = r("cash 200 auto")
        assertEquals("cash", x.accountId)
        assertEquals("Auto", x.note)
        assertEquals("icici-card", r("amazon order 999 from icici amazon pay card").accountId)
    }

    @Test fun twoAccountsNamedIsNoAccount() {
        val x = r("cash hdfc 200")
        assertNull(x.accountId)
    }

    @Test fun aSharedFirstWordIsNotEnough() {
        val two = listOf(SmartEntry.AccountName("a", "HDFC Bank"), SmartEntry.AccountName("b", "HDFC Card"))
        val x = SmartEntry.read("rent 18000 hdfc", 2, two)
        assertNull(x.accountId)
        assertEquals("Rent hdfc", x.note, "the word stays where it is not an account")
        assertEquals("b", SmartEntry.read("rent 18000 hdfc card", 2, two).accountId)
    }

    // ---- other text ----

    @Test fun emoji() {
        val x = r("☕ 20")
        assertEquals(2_000L, x.amountMinor)
        assertEquals("☕", x.note)
        assertEquals("Food", x.categoryNames.first())
        assertEquals("Food", r("🍕 450").categoryNames.first())
        assertEquals("Transport", r("🛺 80").categoryNames.first())
        assertEquals(2_000L, r("☕️ 20").amountMinor, "with the emoji variation selector")
    }

    @Test fun veryLongTextIsNotRead() {
        val x = r("chai 20 " + "and more ".repeat(40))
        assertEquals(SmartEntry.Problem.TOO_LONG, x.problem)
        assertNull(x.amountMinor)
    }

    @Test fun aLongNoteIsCutAtAWord() {
        val x = r("dinner with whole team after quarterly review celebration near lake 2400")
        assertEquals(240_000L, x.amountMinor)
        assertTrue(x.note.length <= SmartEntry.MAX_NOTE)
        assertEquals("Dinner with whole team after quarterly review celebration", x.note, "cut at a word, not inside one")
    }

    @Test fun emptyAndBlank() {
        assertEquals(SmartEntry.Problem.EMPTY, r("").problem)
        assertEquals(SmartEntry.Problem.EMPTY, r("   ").problem)
    }

    @Test fun onlyAnAmount() {
        val x = r("450")
        assertEquals(45_000L, x.amountMinor)
        assertEquals("", x.note)
        assertTrue(x.categoryNames.isEmpty())
    }

    @Test fun moreHinglish() {
        val x = r("sabzi wale ko 60 diye")
        assertEquals(6_000L, x.amountMinor)
        assertEquals("Sabzi", x.note)
        assertEquals(listOf("Groceries", "Food"), x.categoryNames)
        val y = r("dawai ke liye 340")
        assertEquals("Dawai", y.note)
        assertEquals("Health", y.categoryNames.first())
        assertEquals("Petrol", r("petrol 500 bhara").note.substringBefore(' '))
    }

    @Test fun aKnownMerchantGetsItsCleanNameAndCategory() {
        val x = r("swiggy 389")
        assertEquals("Swiggy", x.note)
        assertEquals("Food", x.categoryNames.first())
    }

    // ---- the category the preview offers ----

    @Test fun theUsualCategoryFromHistoryComesFirst() {
        val available = listOf("Food" to "Food", "cat:rent" to "Rent", "Bills" to "Bills")
        assertEquals("Bills", SmartEntry.pickCategory("Bills", listOf("Rent", "Bills"), available), "what the person usually does wins")
        assertEquals("cat:rent", SmartEntry.pickCategory(null, listOf("Rent", "Bills"), available))
        assertEquals("Bills", SmartEntry.pickCategory(null, listOf("Rent", "Bills"), available.filter { it.second != "Rent" }), "the older starter when the newer one is gone")
        assertEquals("Food", SmartEntry.pickCategory("gone", listOf("food"), available), "a usual category that is gone is passed over; names match in any case")
        assertNull(SmartEntry.pickCategory(null, listOf("Travel"), available))
    }
}
