package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureHealthTest {
    private val day = 86_400_000L
    private val names = mapOf("HDFCBK" to "HDFC Bank", "ICICIB" to "ICICI Bank")
    private fun c(root: String, outcome: String, n: Long, at: Long) = CaptureCount(root, outcome, n, at)
    private val now = 100 * day

    @Test
    fun theStatusFollowsTheSwitchThePermissionAndHowRecentTheLastTextWas() {
        val recent = listOf(c("HDFCBK", "CAPTURED", 1, now - day))
        assertEquals(CaptureStatus.OFF, CaptureHealth.report(recent, now, false, true).status)
        assertEquals(CaptureStatus.OFF, CaptureHealth.report(recent, now, false, false).status, "off wins over a missing permission")
        assertEquals(CaptureStatus.PERMISSION_MISSING, CaptureHealth.report(recent, now, true, false).status)
        assertEquals(CaptureStatus.NOTHING_SEEN, CaptureHealth.report(emptyList(), now, true, true).status)
        assertEquals(CaptureStatus.WORKING, CaptureHealth.report(recent, now, true, true).status)
        assertEquals(CaptureStatus.WORKING, CaptureHealth.report(listOf(c("A", "OTP", 1, now - 7 * day)), now, true, true).status, "exactly a week is still working")
        assertEquals(CaptureStatus.QUIET, CaptureHealth.report(listOf(c("A", "OTP", 1, now - 7 * day - 1)), now, true, true).status)
    }

    @Test
    fun theCountsAddUpPerOutcomeAndTheLastTimesAreTheNewest() {
        val r = CaptureHealth.report(listOf(
            c("HDFCBK", "CAPTURED", 3, 50), c("HDFCBK", "CAPTURED", 1, 80), c("HDFCBK", "DUPLICATE", 2, 60), c("HDFCBK", "OTP", 5, 90), c("", "PERSONAL_SENDER", 4, 95),
            c("ICICIB", "NOT_A_TRANSACTION", 2, 40), c("ICICIB", "DECLINED", 1, 45),
        ), 100, true, true, names)
        assertEquals(listOf(4L, 2L, 5L, 4L, 2L, 1L), listOf(r.captured, r.duplicates, r.otps, r.personal, r.notices, r.declined))
        assertEquals(80, r.lastCapturedAt); assertEquals(95, r.lastSeenAt)
    }

    @Test
    fun knownSendersShowCapturedAndIgnoredTheOrderIsByCapturedAndPersonalNumbersNeverAppear() {
        val r = CaptureHealth.report(listOf(
            c("HDFCBK", "CAPTURED", 3, 10), c("HDFCBK", "OTP", 2, 20), c("ICICIB", "CAPTURED", 7, 5), c("", "PERSONAL_SENDER", 9, 30),
        ), 100, true, true, names)
        assertEquals(listOf(SenderRow("ICICIB", "ICICI Bank", 7, 0, 5), SenderRow("HDFCBK", "HDFC Bank", 3, 2, 20)), r.senders)
        assertEquals(emptyList(), r.unknownSenders)
    }

    @Test
    fun unknownSendersAreListedMostFrequentFirstSoAMissingBankCanBeAdded() {
        val r = CaptureHealth.report(listOf(
            c("MYCOOP", "NOT_ALLOWLISTED", 6, 10), c("PROMO1", "NOT_ALLOWLISTED", 20, 11), c("ZETA", "NOT_ALLOWLISTED", 6, 12), c("HDFCBK", "CAPTURED", 1, 5),
        ), 100, true, true, names)
        assertEquals(listOf("PROMO1", "MYCOOP", "ZETA"), r.unknownSenders.map { it.root }, "by count, ties by name")
        assertNull(r.unknownSenders.first().name)
        assertEquals(listOf("HDFCBK"), r.senders.map { it.root }, "an unknown sender is not in the known list")
    }

    @Test
    fun aSenderWithNoNameThatWasNeverIgnoredAsUnlistedIsNotOfferedAndKnownTiesGoByName() {
        val r = CaptureHealth.report(listOf(c("XTRA", "CAPTURED", 4, 10), c("ICICIB", "CAPTURED", 2, 5), c("HDFCBK", "CAPTURED", 2, 6)), 100, true, true, names)
        assertEquals(emptyList(), r.unknownSenders, "added by the person and read: nothing to offer")
        assertEquals(listOf("HDFCBK", "ICICIB"), r.senders.map { it.root }, "equal counts: by name")
    }
}
