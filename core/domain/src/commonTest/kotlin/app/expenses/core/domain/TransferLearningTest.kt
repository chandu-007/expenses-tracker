package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a person teaches by making texts transfers: the keys, the counts, and when the app may add one without a look. All names, accounts and digits are invented. */
class TransferLearningTest {
    private val savings = "acct-savings"
    private val other = "acct-other"

    @Test
    fun aRuleSurvivesEncodingAndAnythingElseIsNoRule() {
        val r = TransferLearning.Rule(savings, 3, 1)
        assertEquals(r, TransferLearning.decode(TransferLearning.encode(r)))
        listOf(null, "", "a|b|c", "x|0|0", "x|1", "|1|0", "x|1|-1", "x|1|0|9").forEach { assertNull(TransferLearning.decode(it), "'$it'") }
    }

    @Test
    fun wordingIsAClosedListOfTransferWordsInAFixedOrder() {
        assertEquals("transfer+tpt", TransferLearning.wording("Fund Transfer to A/c XX5678 TPT-note"))
        assertEquals("self", TransferLearning.wording("Rs.500 sent to self via UPI"))
        assertEquals("own", TransferLearning.wording("Rs.500 to own account 5678"))
        assertEquals("ft", TransferLearning.wording("FT - Cr - 1234 - SAVINGS"))
        assertEquals("sweep", TransferLearning.wording("Auto sweep Rs.5,000 debited"))
        // the rails carry every payment to every person: they never make a key
        assertNull(TransferLearning.wording("Rs.500 debited via IMPS, NEFT, RTGS or UPI to ravi@okbank"))
        assertNull(TransferLearning.wording("Rs.200 debited from A/c XX1234 to VPA shop@ybl (UPI Ref 123456789012)"))
    }

    @Test
    fun theKeysAreThePayeeThenTheWordingAndAnOrdinaryTextHasNone() {
        val ordinary = TransferLearning.keys("HDFC Bank", true, null, "Rs.200 debited from A/c XX1234 on 05-10-26 (UPI Ref 123456789012)", null)
        assertEquals(emptyList(), ordinary, "no payee, no transfer word, no named account: nothing to learn from")
        val payee = TransferLearning.keys("HDFC Bank", true, "ravikumar", "Rs.500 debited via UPI", null)
        assertEquals(listOf("transfer_rule:p:out:ravikumar"), payee)
        val both = TransferLearning.keys("ICICI Bank", false, "anita", "Rs.500 credited. Fund transfer from A/c XX5678", "5678")
        assertEquals(listOf("transfer_rule:p:in:anita", "transfer_rule:w:icici bank|in|transfer|5678"), both)
        // money in and money out of one payee are different rules
        assertTrue(TransferLearning.keys("X", true, "p1", "", null).single() != TransferLearning.keys("X", false, "p1", "", null).single())
        // a named account alone is a key too
        assertEquals(listOf("transfer_rule:w:hdfc bank|out|-|5678"), TransferLearning.keys("HDFC Bank", true, null, "Rs.500 debited to A/c xxxxxxxxxx5678 via IMPS", "5678"))
    }

    @Test
    fun aFirstTransferStartsARuleTheSameAccountCountsAndAnotherAccountStartsOver() {
        var rule = TransferLearning.after(null, savings)
        assertEquals(TransferLearning.Rule(savings, 1, 0), rule)
        rule = TransferLearning.after(rule, savings)
        assertEquals(TransferLearning.Rule(savings, 2, 0), rule)
        rule = TransferLearning.after(rule, other)
        assertEquals(TransferLearning.Rule(other, 1, 0), rule, "the latest choice is what they chose last time")
    }

    @Test
    fun aPlainEntryCountsAgainstARuleAndForgetsItOnceTheExceptionsAreAsManyAsTheTransfers() {
        assertNull(TransferLearning.after(null, null), "nothing learned from a plain entry with no rule")
        val three = TransferLearning.Rule(savings, 3, 0)
        val one = TransferLearning.after(three, null)
        assertEquals(TransferLearning.Rule(savings, 3, 1), one)
        val two = TransferLearning.after(one, null)
        assertEquals(TransferLearning.Rule(savings, 3, 2), two)
        assertNull(TransferLearning.after(two, null), "three exceptions against three transfers: forgotten")
        assertNull(TransferLearning.after(TransferLearning.Rule(savings, 1, 0), null), "one transfer, one exception: forgotten")
    }

    @Test
    fun aSuggestionIsOnlySureAfterThreeConsistentChoicesWithNoException() {
        assertEquals(Certainty.CHECK, TransferLearning.certainty(TransferLearning.Rule(savings, 1, 0)))
        assertEquals(Certainty.CHECK, TransferLearning.certainty(TransferLearning.Rule(savings, 2, 0)))
        assertEquals(Certainty.SURE, TransferLearning.certainty(TransferLearning.Rule(savings, 3, 0)))
        assertEquals(Certainty.SURE, TransferLearning.certainty(TransferLearning.Rule(savings, 9, 0)))
        assertEquals(Certainty.CHECK, TransferLearning.certainty(TransferLearning.Rule(savings, 9, 1)), "a single exception keeps it a suggestion for good")
    }
}
