package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferPairsTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val t0 = 1_800_000_000_000L

    private fun out(id: String, acct: String?, amount: Long = 4_525_075, at: Long = t0, names: String? = null, hinted: Boolean = true, accepted: Boolean = false) =
        TransferSide(id, true, amount, at, acct, names, hinted, accepted)

    private fun into(id: String, acct: String?, amount: Long = 4_525_075, at: Long = t0 + 40_000, names: String? = null, hinted: Boolean = true, accepted: Boolean = false) =
        TransferSide(id, false, amount, at, acct, names, hinted, accepted)

    @Test
    fun theTwoTextsOfOneTransferArePaired() {
        // each row: the two halves and what ties them, or null when they are not one transfer
        val table: List<Triple<String, Pair<TransferSide, TransferSide>, PairBasis?>> = listOf(
            Triple("the real case: the credit names the source, both say transfer", out("a", "A") to into("b", "B", names = "A"), PairBasis.NAMED_ACCOUNT),
            Triple("only the debit names the destination", out("a", "A", names = "B", hinted = false) to into("b", "B", hinted = false), PairBasis.NAMED_ACCOUNT),
            Triple("a named account allows a day between the texts", out("a", "A") to into("b", "B", names = "A", at = t0 + 23 * hour), PairBasis.NAMED_ACCOUNT),
            Triple("a day and an hour is too late even when named", out("a", "A") to into("b", "B", names = "A", at = t0 + 25 * hour), null),
            Triple("amount, wording and minutes", out("a", "A") to into("b", "B"), PairBasis.AMOUNT_AND_WORDS),
            Triple("one half's wording is enough", out("a", "A", hinted = false) to into("b", "B"), PairBasis.AMOUNT_AND_WORDS),
            Triple("exactly half an hour is still in", out("a", "A") to into("b", "B", at = t0 + 30 * minute), PairBasis.AMOUNT_AND_WORDS),
            Triple("31 minutes with no named account is out", out("a", "A") to into("b", "B", at = t0 + 31 * minute), null),
            Triple("the credit first, then the debit", out("a", "A", at = t0 + 2 * minute) to into("b", "B", at = t0), PairBasis.AMOUNT_AND_WORDS),
            Triple("no transfer wording and no named account: two unrelated items", out("a", "A", hinted = false) to into("b", "B", hinted = false), null),
            Triple("different amounts", out("a", "A") to into("b", "B", amount = 9_071_645), null),
            Triple("same account on both sides", out("a", "A") to into("b", "A"), null),
            Triple("the destination is not one of the person's accounts", out("a", "A") to into("b", null), null),
            Triple("the source is not one of the person's accounts", out("a", null) to into("b", "B"), null),
            Triple("two debits", out("a", "A") to out("b", "B"), null),
            Triple("a credit that names a different account than the debit's", out("a", "A") to into("b", "B", names = "C"), null),
            Triple("a debit that names a different destination", out("a", "A", names = "C") to into("b", "B"), null),
            Triple("zero", out("a", "A", amount = 0) to into("b", "B", amount = 0), null),
            Triple("two entries already in the ledger answer nothing", out("a", "A", accepted = true) to into("b", "B", accepted = true), null),
        )
        for ((what, pair, expected) in table) {
            assertEquals(expected, TransferPairs.basis(pair.first, pair.second), what)
            assertEquals(expected, TransferPairs.basis(pair.second, pair.first), "$what (asked the other way round)")
        }
    }

    @Test
    fun anAcceptedEntryCanBeTheOtherHalf() {
        val p = TransferPairs.pair(listOf(out("e", "A", accepted = true), into("p", "B", names = "A", hinted = true)))
        assertEquals(1, p.size); assertEquals("e", p.single().out.id); assertEquals("p", p.single().into.id)
    }

    @Test
    fun twoSimultaneousTransfersOfOneAmountPairOffOnceEach() {
        val sides = listOf(
            out("o1", "A", at = t0), out("o2", "A", at = t0 + 10 * minute),
            into("i1", "B", at = t0 + 20_000), into("i2", "B", at = t0 + 10 * minute + 20_000),
        )
        val pairs = TransferPairs.pair(sides).associate { it.out.id to it.into.id }
        assertEquals(mapOf("o1" to "i1", "o2" to "i2"), pairs, "each text answers for one")
        // the same, whatever order they were listed in
        assertEquals(pairs, TransferPairs.pair(sides.reversed()).associate { it.out.id to it.into.id })
    }

    @Test
    fun threeHalvesOfOneAmountPairOnlyTwo() {
        val pairs = TransferPairs.pair(listOf(out("o1", "A"), into("i1", "B"), into("i2", "B", at = t0 + minute)))
        assertEquals(1, pairs.size); assertEquals("i1", pairs.single().into.id, "the closer in time")
    }

    @Test
    fun twoPendingTextsAreWorthMoreThanAPendingTextAndAnEntry() {
        val pairs = TransferPairs.pair(listOf(out("e", "A", accepted = true, at = t0 + 5_000), out("p1", "A", at = t0 + 100_000), into("p2", "B")))
        assertEquals("p1", pairs.single().out.id)
    }

    @Test
    fun aNamedAccountBeatsWordingAlone() {
        val pairs = TransferPairs.pair(listOf(out("o", "A"), into("near", "B", at = t0 + 1_000), into("named", "B", names = "A", at = t0 + 20 * minute)))
        assertEquals("named", pairs.single().into.id)
    }

    @Test
    fun aPairThePersonCalledSeparateIsNeverPutTogetherAgain() {
        val sides = listOf(out("o", "A"), into("i", "B"))
        assertEquals(1, TransferPairs.pair(sides).size)
        assertEquals(0, TransferPairs.pair(sides, apart = setOf(TransferPairs.key("i", "o"))).size)
        assertEquals(TransferPairs.key("o", "i"), TransferPairs.key("i", "o"))
    }

    @Test
    fun aLoneTextOnlyGuessesWhenItNamesAnAccountOrThereIsOneOtherToChoose() {
        val one = mapOf("B" to "Savings")
        val two = mapOf("B" to "Savings", "C" to "Wallet")
        assertEquals("Savings", TransferPairs.mightBeTransferTo("B", hinted = false, others = two), "the digits name it")
        assertEquals("Wallet", TransferPairs.mightBeTransferTo("C", hinted = true, others = two))
        assertEquals("Savings", TransferPairs.mightBeTransferTo(null, hinted = true, others = one), "wording and exactly one other account")
        assertNull(TransferPairs.mightBeTransferTo(null, hinted = true, others = two), "two other accounts: which one is not known")
        assertNull(TransferPairs.mightBeTransferTo(null, hinted = false, others = one), "no wording, no digits")
        assertNull(TransferPairs.mightBeTransferTo("Z", hinted = true, others = one), "the digits point at something that is not an account of the person's")
        assertNull(TransferPairs.mightBeTransferTo(null, hinted = true, others = emptyMap()))
    }

    @Test
    fun theBestAssignmentMakesTheMostPairsWhereTheClosestFirstWouldLeaveTwoValidHalvesAlone() {
        val sides = listOf(out("o1", "A", at = t0), out("o2", "A", at = t0 + 10 * minute), into("i1", "B", at = t0 + 9 * minute), into("i2", "B", at = t0 + 40 * minute))
        // o1-i1 (9 min), o2-i1 (1 min) and o2-i2 (30 min) are valid; o1-i2 (40 min) is not: the closest pair o2-i1 would leave o1 and i2 apart
        val pairs = TransferPairs.pair(sides).associate { it.out.id to it.into.id }
        assertEquals(mapOf("o1" to "i1", "o2" to "i2"), pairs)
        assertEquals(pairs, TransferPairs.pair(sides.reversed()).associate { it.out.id to it.into.id })
    }

    @Test
    fun largeNumbersOfWaitingHalvesStillPairOneForOneAndFast() {
        val many = (0 until 300).flatMap { k -> listOf(out("o$k", "A", at = t0 + k * 2 * hour), into("i$k", "B", at = t0 + k * 2 * hour + 20_000)) }
        val pairs = TransferPairs.pair(many)
        assertEquals(300, pairs.size); assertTrue(pairs.all { it.out.id.drop(1) == it.into.id.drop(1) })
    }

    @Test
    fun theRailsAloneAreNotTransferWordingButTheRealWordsAre() {
        for (t in listOf("IMPS INR 5,000.00 sent from A/c XX1234", "NEFT Cr-SAMPLE0INBX01-Employer Ltd-Customer-REF123", "RTGS Money Deposited INR 2,00,000.00", "Update! INR 50,000.00 deposited in A/c XX5678 for NEFT Cr-Acme Payroll"))
            assertEquals(false, TransferWording.says(t), t)
        for (t in listOf("for Money Transfer via Online Banking", "XXXXXXXXXX1234-TPT-note-NAME", "FT- NAME-XXXXXXXXXX1234", "UPI to self", "own account transfer", "Sweep in from FD", "Fund Transfer"))
            assertEquals(true, TransferWording.says(t), t)
        // a statement line is judged more loosely: the rails count there
        assertEquals(true, TransferWording.saysOrRail("NEFT-HDFCN123456-PRIYA SHARMA")); assertEquals(false, TransferWording.saysOrRail("UPI-ZOMATO-zomato@icici-123456789012"))
        // so an unrelated payment and a salary credit of one amount on two accounts are not paired, and a lone salary credit gets no "might be a transfer" hint
        val payment = out("a", "A", hinted = TransferWording.says("Rs.50,000.00 debited from A/c XX1234 to VPA shop@okhdfc"))
        val salary = into("b", "B", hinted = TransferWording.says("INR 50,000.00 deposited in A/c XX5678 for NEFT Cr-Acme Payroll"))
        assertNull(TransferPairs.basis(payment, salary))
        assertNull(TransferPairs.mightBeTransferTo(null, salary.hinted, mapOf("A" to "Main")))
    }
}
