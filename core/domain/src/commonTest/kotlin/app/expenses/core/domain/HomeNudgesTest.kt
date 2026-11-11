package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeNudgesTest {
    private val hour = 3_600_000L
    private val now = 1_000 * 24 * hour
    private fun visible(present: Map<HomeNudge, Int>, snoozes: Map<HomeNudge, NudgeSnooze> = emptyMap(), at: Long = now) = HomeNudges.visible(present, snoozes, at)

    @Test
    fun theOrderIsBillsDueThenReviewThenNewBillsThenMoneyBackThenCategoriesThenSetupThenGettingStarted() {
        assertEquals(
            listOf("bills_due", "inbox", "bills_new", "waiting", "categorise", "setup", "first_week"),
            HomeNudge.entries.map { it.key },
            "the enum order is the documented priority order (docs/HOME_NUDGES.md)",
        )
        val all = HomeNudge.entries.associateWith { 3 }
        assertEquals(HomeNudge.entries.toList(), visible(all))
    }

    @Test
    fun oneCardIsShownAndTheRestAreNamedInOrder() {
        val v = visible(mapOf(HomeNudge.CATEGORISE to 12, HomeNudge.INBOX to 2, HomeNudge.FIRST_WEEK to 5))
        assertEquals(HomeNudge.INBOX, HomeNudges.card(v))
        assertEquals(listOf(HomeNudge.CATEGORISE, HomeNudge.FIRST_WEEK), HomeNudges.alsoWaiting(v))
    }

    @Test
    fun aBillDueThisWeekOutranksTextsWaitingAndANewBillDoesNot() {
        assertEquals(HomeNudge.BILLS_DUE, HomeNudges.card(visible(mapOf(HomeNudge.INBOX to 9, HomeNudge.BILLS_DUE to 1))))
        assertEquals(HomeNudge.INBOX, HomeNudges.card(visible(mapOf(HomeNudge.INBOX to 1, HomeNudge.BILLS_NEW to 4))))
    }

    @Test
    fun nothingPresentMeansNoCardAndNoLine() {
        val v = visible(emptyMap())
        assertNull(HomeNudges.card(v)); assertTrue(HomeNudges.alsoWaiting(v).isEmpty())
        assertTrue(visible(mapOf(HomeNudge.INBOX to 0, HomeNudge.WAITING to -1)).isEmpty(), "a count of nothing is not present")
    }

    @Test
    fun aNudgePutOffGivesWayToTheNextOneForADayAndComesBackAfterwards() {
        val present = mapOf(HomeNudge.INBOX to 3, HomeNudge.WAITING to 1)
        val put = mapOf(HomeNudge.INBOX to NudgeSnooze(now - 2 * hour, 3))
        assertEquals(HomeNudge.WAITING, HomeNudges.card(visible(present, put)), "inbox put off two hours ago: the next one shows")
        assertEquals(HomeNudge.INBOX, HomeNudges.card(visible(present, put, at = now + 23 * hour)), "still inside the day")
        assertEquals(HomeNudge.WAITING, HomeNudges.card(visible(present, put, at = now + 21 * hour)))
        assertEquals(HomeNudge.INBOX, HomeNudges.card(visible(present, put, at = now + 22 * hour + 1)), "24 hours after it was put off it is back")
    }

    @Test
    fun moreArrivingBringsAPutOffNudgeBackAtOnce() {
        val put = mapOf(HomeNudge.INBOX to NudgeSnooze(now - hour, 3))
        assertTrue(visible(mapOf(HomeNudge.INBOX to 3), put).isEmpty(), "same number: still put off")
        assertTrue(visible(mapOf(HomeNudge.INBOX to 2), put).isEmpty(), "fewer: still put off")
        assertEquals(listOf(HomeNudge.INBOX), visible(mapOf(HomeNudge.INBOX to 4), put), "a new text: it comes back")
    }

    @Test
    fun aClockThatWentBackwardsKeepsThePutOffInForce() {
        assertTrue(HomeNudges.isPutOff(NudgeSnooze(now + 5 * hour, 2), 2, now))
        assertFalse(HomeNudges.isPutOff(null, 2, now))
    }

    @Test
    fun aPutOffIsStoredAsTwoNumbersAndReadBackAndJunkIsNothing() {
        val s = NudgeSnooze(1_730_000_000_000, 7)
        assertEquals("1730000000000|7", s.encode())
        assertEquals(s, NudgeSnooze.decode(s.encode()))
        listOf(null, "", "7", "a|b", "1|2|3", "1730000000000|x", "|").forEach { assertNull(NudgeSnooze.decode(it), "junk: $it") }
        assertEquals("nudge_snoozed:inbox", HomeNudges.settingKey(HomeNudge.INBOX))
    }

    @Test
    fun theMorePageShowsOneNoticeTheSigningWarningFirst() {
        assertEquals(HomeNudges.MoreNotice.SIGNING, HomeNudges.moreNotice(signingShowing = true, backupDue = true))
        assertEquals(HomeNudges.MoreNotice.BACKUP, HomeNudges.moreNotice(signingShowing = false, backupDue = true))
        assertEquals(HomeNudges.MoreNotice.SIGNING, HomeNudges.moreNotice(signingShowing = true, backupDue = false))
        assertNull(HomeNudges.moreNotice(signingShowing = false, backupDue = false))
    }
}
