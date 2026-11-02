package app.expenses.core.domain

import app.expenses.core.domain.SkippedSenders.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The header-only list of skipped senders: counting, the category suffix rule, the cap and what is listed. Every sender name here is invented. */
class SkippedSendersTest {
    private val now = 100L * 86_400_000
    private fun c(root: String, count: Long = 1, ago: Long = 0, suffixes: String = "S") = SkippedCounter(root, count, now - ago, suffixes)
    private fun rows(vararg l: SkippedCounter, excluded: Set<String> = emptySet()) = SkippedSenders.rows(l.toList(), excluded, now).map { it.root }

    @Test
    fun aCounterIsStoredAsThreeFieldsAndADamagedRowIsIgnored() {
        val x = SkippedCounter("MYCOOP", 3, 12_345, "ST")
        assertEquals("3|12345|ST", SkippedSenders.encode(x)); assertEquals(x, SkippedSenders.decode("skipped_sender:MYCOOP", "3|12345|ST"))
        for (bad in listOf("", "3|12345", "0|12345|S", "x|1|S", "3|y|S", "3|1|", "3|1|s", "3|1|S|extra", "3|1|S1")) assertNull(SkippedSenders.decode("skipped_sender:MYCOOP", bad), bad)
        assertNull(SkippedSenders.decode("other:MYCOOP", "3|1|S")); assertNull(SkippedSenders.decode("skipped_sender:", "3|1|S"))
        assertEquals("skipped_sender:MYCOOP", SkippedSenders.key(" mycoop "))
    }

    @Test
    fun theStoredValueHoldsNothingButTheNameACountATimeAndSuffixLetters() {
        val v = SkippedSenders.encode(SkippedSenders.counted(null, "MYCOOP", 'S', now))
        assertTrue(v.matches(Regex("""\d+\|\d+\|[A-Z-]+""")), v)
    }

    @Test
    fun countingAddsOneRemembersEverySuffixAndStartsAgainAfterAMonthOfSilence() {
        val first = SkippedSenders.counted(null, "mycoop", 'S', now)
        assertEquals(SkippedCounter("MYCOOP", 1, now, "S"), first)
        val second = SkippedSenders.counted(first, "MYCOOP", 'T', now + 1_000)
        assertEquals(SkippedCounter("MYCOOP", 2, now + 1_000, "ST"), second)
        assertEquals("-ST", SkippedSenders.counted(second, "MYCOOP", null, now + 2_000).suffixes, "a header with no suffix is remembered as -")
        assertEquals("S", SkippedSenders.counted(second, "MYCOOP", 's', now + 2_000).suffixes.filter { it == 'S' }, "a lower-case suffix is the same letter")
        assertEquals("-", SkippedSenders.counted(null, "X", '3', now).suffixes, "anything that is not a letter is no suffix")
        val later = SkippedSenders.counted(second, "MYCOOP", 'P', now + SkippedSenders.WINDOW_MS + 5_000)
        assertEquals(SkippedCounter("MYCOOP", 1, now + SkippedSenders.WINDOW_MS + 5_000, "P"), later, "a sender quiet for over 30 days starts again")
    }

    @Test
    fun transactionalAndServiceHeadersMayBeABankPromotionalAndGovernmentOnesAreNot() {
        assertEquals(Kind.MAYBE_BANK, SkippedSenders.kindOf("S")); assertEquals(Kind.MAYBE_BANK, SkippedSenders.kindOf("T")); assertEquals(Kind.MAYBE_BANK, SkippedSenders.kindOf("PS"), "one service text among promotions")
        assertEquals(Kind.NOT_A_BANK, SkippedSenders.kindOf("P")); assertEquals(Kind.NOT_A_BANK, SkippedSenders.kindOf("G")); assertEquals(Kind.NOT_A_BANK, SkippedSenders.kindOf("-P"))
        assertEquals(Kind.UNCLASSIFIED, SkippedSenders.kindOf("-")); assertEquals(Kind.UNCLASSIFIED, SkippedSenders.kindOf("X"))
    }

    @Test
    fun theListShowsServiceAndTransactionalNamesHidesPromotionalAndGovernmentAndWaitsForThreeTextsFromAnUnclassifiedOne() {
        val all = arrayOf(
            c("SVC", suffixes = "S"), c("TXN", suffixes = "T"), c("PROMO", 9, suffixes = "P"), c("GOVT", 9, suffixes = "G"), c("MIXED", 2, suffixes = "PS"),
            c("NOSUFFIX2", 2, suffixes = "-"), c("NOSUFFIX3", 3, suffixes = "-"), c("ODD", 5, suffixes = "X"), c("NOSUFFIXPROMO", 9, suffixes = "-P"),
        )
        assertEquals(setOf("SVC", "TXN", "MIXED", "NOSUFFIX3", "ODD"), rows(*all).toSet())
    }

    @Test
    fun builtInTrustedAndOldSendersAreLeftOutAndTheRestAreSortedByCountThenRecentThenName() {
        val l = arrayOf(c("ONE", 1), c("FIVE", 5, ago = 1_000), c("FIVEB", 5, ago = 500), c("FIVEA", 5, ago = 500), c("TRUSTED", 9), c("OLD", 9, ago = SkippedSenders.WINDOW_MS + 1))
        assertEquals(listOf("FIVEA", "FIVEB", "FIVE", "ONE"), rows(*l, excluded = setOf("TRUSTED")))
    }

    @Test
    fun atMostTwentyAreKeptTheQuietestAreDroppedAndTheOneJustCountedIsNeverDropped() {
        val twenty = (1..20).map { c("R$it", ago = (100 - it) * 1000L) }
        assertEquals(emptyList(), SkippedSenders.toDrop(twenty, keep = "R1"))
        val over = twenty + c("NEW", ago = 0)
        assertEquals(listOf("R1"), SkippedSenders.toDrop(over, keep = "NEW"), "the quietest goes")
        assertTrue("OLDEST" !in SkippedSenders.toDrop(twenty + c("OLDEST", ago = 99_999_000), keep = "OLDEST"), "the one just written survives even if it looks oldest")
    }
}
