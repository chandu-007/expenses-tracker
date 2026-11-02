package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkippedSendersTest {
    private val day = 86_400_000L
    private val now = 100 * day
    private fun c(root: String, outcome: String, n: Long, at: Long) = CaptureCount(root, outcome, n, at)
    private fun h(root: String, at: Long, out: Boolean = true, outline: String = "Acct XX### debited") = SkippedHint(root, at, out, outline)
    private val unlisted = SkippedSenders.OUTCOME

    @Test
    fun aHintIsStoredAsTimeDirectionAndOutlineAndReadBackExactly() {
        val hint = h("MYCOOP", 1_234, false, "Rs ###.## credited \u2026")
        assertEquals("1234|IN|Rs ###.## credited \u2026", SkippedSenders.encode(hint))
        assertEquals("skipped_hint:MYCOOP", SkippedSenders.key("mycoop"))
        assertEquals(hint, SkippedSenders.decode(SkippedSenders.key("MYCOOP"), SkippedSenders.encode(hint)))
        assertEquals(h("A1B", 5, true, ""), SkippedSenders.decode("skipped_hint:A1B", "5|OUT|"))
        for ((k, v) in listOf("other:X" to "1|OUT|a", "skipped_hint:" to "1|OUT|a", "skipped_hint:X" to "nonsense", "skipped_hint:X" to "x|OUT|a", "skipped_hint:X" to "1|SIDEWAYS|a", "skipped_hint:X" to "1|OUT"))
            assertNull(SkippedSenders.decode(k, v), "$k=$v")
        assertEquals(listOf(h("X", 1)), SkippedSenders.decodeAll(listOf("skipped_hint:X" to "1|OUT|Acct XX### debited", "skipped_hint:Y" to "junk", "notes" to "1|OUT|a")))
    }

    @Test
    fun severalSendersAreCountedSortedByCountThenNameAndOnlyThoseWithAHintAreListed() {
        val counts = listOf(c("ZETA", unlisted, 2, now - day), c("ALPHA", unlisted, 2, now - 2 * day), c("MYCOOP", unlisted, 7, now - 3 * day), c("NOHINT", unlisted, 4, now - day), c("PROMO", "NOT_ALLOWLISTED", 50, now - day))
        val hints = listOf(h("ZETA", now - day, false), h("ALPHA", now - 2 * day), h("MYCOOP", now - 3 * day, true, "Card ending ####"), h("PROMO", now - day))
        val rows = SkippedSenders.rows(counts, hints, emptySet(), now)
        assertEquals(listOf("MYCOOP", "ALPHA", "ZETA"), rows.map { it.root }, "by count, ties by name; NOHINT has no outline and PROMO was never payment-shaped")
        assertEquals(listOf(7L, 2L, 2L), rows.map { it.count })
        assertEquals("Card ending ####", rows.first().outline); assertEquals(false, rows.last().out); assertEquals(now - 3 * day, rows.first().lastAt)
    }

    @Test
    fun builtInAndTrustedSendersAreLeftOutSoATrustedRootStopsBeingListed() {
        val counts = listOf(c("MYCOOP", unlisted, 3, now - day), c("ICICIT", unlisted, 2, now - day), c("OTHERCO", unlisted, 1, now - day))
        val hints = listOf(h("MYCOOP", now - day), h("ICICIT", now - day), h("OTHERCO", now - day))
        assertEquals(listOf("MYCOOP", "OTHERCO"), SkippedSenders.rows(counts, hints, setOf("ICICIT"), now).map { it.root }, "a built-in root")
        assertEquals(listOf("OTHERCO"), SkippedSenders.rows(counts, hints, setOf("ICICIT", "MYCOOP"), now).map { it.root }, "a root the person trusts")
    }

    @Test
    fun staleHintsAndOldCountsAreNotListedAndOtherOutcomesDoNotCount() {
        val counts = listOf(c("OLD", unlisted, 5, now - 31 * day), c("FRESH", unlisted, 1, now - 29 * day), c("MIXED", unlisted, 1, now - day), c("MIXED", "NOT_ALLOWLISTED", 9, now - day), c("", unlisted, 3, now - day), c("ZERO", unlisted, 0, now - day))
        val hints = listOf(h("OLD", now - day), h("FRESH", now - 29 * day), h("MIXED", now - day), h("STALE", now - day), h("ZERO", now - day))
        assertEquals(listOf("FRESH", "MIXED"), SkippedSenders.rows(counts, hints, emptySet(), now).map { it.root }.sorted())
        assertEquals(1L, SkippedSenders.rows(counts, hints, emptySet(), now).first { it.root == "MIXED" }.count, "only payment-shaped skips are counted, not the other unlisted texts")
        assertEquals(emptyList(), SkippedSenders.rows(listOf(c("FRESH", unlisted, 1, now - 29 * day)), listOf(h("FRESH", now - 40 * day)), emptySet(), now), "a hint older than 30 days is stale")
        assertEquals(emptyList(), SkippedSenders.rows(emptyList(), hints, emptySet(), now), "a hint with no skipped text behind it shows nothing")
    }

    @Test
    fun theNewestHintOfARootIsTheOneShown() {
        val rows = SkippedSenders.rows(listOf(c("MYCOOP", unlisted, 2, now - day)), listOf(h("MYCOOP", now - 5 * day, true, "old"), h("MYCOOP", now - day, false, "new")), emptySet(), now)
        assertEquals(listOf("new"), rows.map { it.outline }); assertEquals(false, rows.single().out)
    }

    @Test
    fun atMostTwentyHintsAreKeptTheOldestGoFirstAndTheNewOneNeverGoes() {
        val full = (1..20).map { h("R$it", it * 10L) }
        assertEquals(emptyList(), SkippedSenders.toDrop(full, "R20"), "20 is allowed")
        assertEquals(listOf("R1"), SkippedSenders.toDrop(full + h("NEW", 5L), "NEW"), "the new hint is older than all, yet it stays and the oldest of the others goes")
        assertEquals(listOf("R1", "R2", "R3"), SkippedSenders.toDrop(full + listOf(h("N1", 500), h("N2", 600), h("N3", 700)), "N3"))
        assertEquals(listOf("A", "B"), SkippedSenders.toDrop((1..22).map { h(if (it == 1) "B" else if (it == 2) "A" else "R$it", 1L) }.let { it.take(2).map { x -> x.copy(at = 1) } + it.drop(2).map { x -> x.copy(at = 2) } }, "R22"), "ties by name")
        assertTrue(SkippedSenders.MAX_HINTS == 20)
    }

    @Test
    fun captureHealthStillListsAnUnlistedPaymentSenderAsNotOnTheListWithItsCount() {
        val r = CaptureHealth.report(listOf(c("MYCOOP", unlisted, 3, 10), c("PROMO1", "NOT_ALLOWLISTED", 5, 11), c("HDFCBK", "CAPTURED", 1, 5)), 100, true, true, mapOf("HDFCBK" to "HDFC Bank"))
        assertEquals(listOf("PROMO1", "MYCOOP"), r.unknownSenders.map { it.root })
        assertEquals(3L, r.unknownSenders.last().ignored)
    }
}
