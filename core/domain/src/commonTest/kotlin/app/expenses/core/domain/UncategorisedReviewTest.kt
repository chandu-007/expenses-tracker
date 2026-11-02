package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UncategorisedReviewTest {
    private val day = 86_400_000L

    @Test
    fun theCardShowsFromThreeExpensesAndStaysAwayForAWeekAfterNotNow() {
        assertFalse(UncategorisedNudge.shouldShow(0, null, 1_000)); assertFalse(UncategorisedNudge.shouldShow(2, null, 1_000))
        assertTrue(UncategorisedNudge.shouldShow(3, null, 1_000)); assertTrue(UncategorisedNudge.shouldShow(500, null, 1_000))
        val now = 100 * day
        assertFalse(UncategorisedNudge.shouldShow(10, now - day, now), "snoozed yesterday")
        assertFalse(UncategorisedNudge.shouldShow(10, now - 7 * day + 1, now), "a millisecond short of seven days")
        assertTrue(UncategorisedNudge.shouldShow(10, now - 7 * day, now), "seven days later")
        assertTrue(UncategorisedNudge.shouldShow(10, now + 5 * day, now), "a snooze in the future (the clock moved back) is over")
        assertFalse(UncategorisedNudge.shouldShow(2, now - 30 * day, now), "even after the snooze, too few")
    }

    @Test
    fun theCardSaysTheCountAndTheCap() {
        assertEquals("12 expenses have no category", UncategorisedNudge.text(12))
        assertEquals("500+ expenses have no category", UncategorisedNudge.text(UncategorisedNudge.CAP))
    }

    private fun item(id: String, label: String, at: Long) = ReviewItem(id, label, 100, at, "sms")
    private val key = { l: String -> PayeeKey.of(l) }

    @Test
    fun theBiggestPayeesComeFirstTheirEntriesTogetherNewestFirstThenSingles() {
        val items = listOf(
            item("a1", "Freshpik", 10), item("b1", "Annapurna Mess", 50), item("a2", "FRESHPIK BANGALORE IN", 30), item("c1", "Kirana Mart", 40), item("a3", "freshpik@ybl", 20),
            item("b2", "ANNAPURNA MESS 0042", 60), item("z1", "", 70), item("z2", "", 5),
        )
        val ordered = ReviewSession.order(items, key).map { it.id }
        assertEquals(listOf("a2", "a3", "a1", "b2", "b1", "z1", "c1", "z2"), ordered, "Freshpik (3), Annapurna (2), then the singles newest first")
        assertEquals(items.map { it.id }.toSet(), ordered.toSet(), "nothing lost or doubled")
        assertEquals(emptyList(), ReviewSession.order(emptyList(), key))
    }

    @Test
    fun aPassCountsWhatIsLeftFiledAndSkippedAndOnlyTheLastChoiceCanBeTakenBack() {
        var s = ReviewSession(listOf(item("1", "A", 3), item("2", "B", 2), item("3", "C", 1)))
        assertEquals("1", s.current!!.id); assertEquals(3, s.left); assertFalse(s.done)
        s = s.withFiled(); assertEquals("2", s.current!!.id); assertEquals(2, s.left); assertEquals(1, s.filed)
        s = s.withSkipped(); assertEquals("3", s.current!!.id); assertEquals(1, s.left); assertEquals(1, s.skipped)
        s = s.withFiled(); assertTrue(s.done); assertNull(s.current); assertEquals(0, s.left); assertEquals(2, s.filed); assertEquals(1, s.skipped)
        assertEquals(s, s.withFiled(), "nothing after the end"); assertEquals(s, s.withSkipped())
        val back = s.undone(); assertEquals("3", back.current!!.id); assertEquals(1, back.filed); assertEquals(1, back.skipped); assertEquals(1, back.left)
        val fresh = ReviewSession(listOf(item("1", "A", 3)))
        assertEquals(fresh, fresh.undone(), "nothing to take back at the start")
        assertEquals(ReviewSession(emptyList()).done, true)
    }
}
