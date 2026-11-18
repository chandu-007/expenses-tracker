package app.expenses.core.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureHeartbeatTest {
    private val zone = TimeZone.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime(y, m, d, h, min).toInstant(zone).toEpochMilliseconds()
    private val now = at(2026, 11, 18, 9, 30)
    private fun chip(last: Long?, on: Boolean = true, shortcut: Boolean = false) = CaptureHeartbeat.chip(now, last, on, zone, shortcut)

    @Test
    fun noChipWhenCaptureIsOffOrNothingWasEverSeen() {
        assertNull(chip(at(2026, 11, 17, 20, 14), on = false))
        assertNull(chip(null))
        assertNull(chip(null, on = false))
    }

    @Test
    fun yesterdayAndTodayAreSaidInWordsWithTheTimeOfDay() {
        assertEquals("Last bank text seen yesterday 8:14 pm", chip(at(2026, 11, 17, 20, 14))!!.text)
        assertEquals("Last bank text seen today 12:05 am", chip(at(2026, 11, 18, 0, 5))!!.text)
        assertEquals("Last bank text seen today 9:00 am", chip(at(2026, 11, 18, 9, 0))!!.text)
        assertEquals("Last bank text seen today 12:30 pm", chip(at(2026, 11, 18, 12, 30))!!.text)
    }

    @Test
    fun olderTextsGetADate() {
        assertEquals("Last bank text seen on 14 Nov, 6:02 pm", chip(at(2026, 11, 14, 18, 2))!!.text)
    }

    @Test
    fun freshUntilMoreThanFiveDaysThenQuietNeverMoreThanAState() {
        assertEquals(CaptureHeartbeat.State.FRESH, chip(at(2026, 11, 17, 20, 14))!!.state)
        assertEquals(CaptureHeartbeat.State.FRESH, chip(now - 5 * 86_400_000L)!!.state)
        assertEquals(CaptureHeartbeat.State.QUIET, chip(now - 5 * 86_400_000L - 1)!!.state)
        val quiet = chip(at(2026, 11, 10, 8, 0))!!
        assertEquals(CaptureHeartbeat.State.QUIET, quiet.state)
        assertEquals("Last bank text seen on 10 Nov, 8:00 am", quiet.text)
        assertEquals("No bank text for 8 days. Tap to find out why.", quiet.note)
        assertEquals("Quiet: no bank text for 8 days", quiet.stateWords)
    }

    @Test
    fun aFreshChipHasNoSecondLineAndSaysSoToAScreenReader() {
        val c = chip(at(2026, 11, 17, 20, 14))!!
        assertNull(c.note)
        assertEquals("Seen recently", c.stateWords)
    }

    @Test
    fun iPhoneWordsItAsShortcuts() {
        val c = assertNotNull(chip(at(2026, 11, 17, 20, 14), shortcut = true))
        assertEquals("Last bank text through Shortcuts yesterday 8:14 pm", c.text)
    }

    @Test
    fun theDayIsTheLocalOneNotTheUtcOne() {
        val ist = TimeZone.of("Asia/Kolkata")
        val last = LocalDateTime(2026, 11, 17, 23, 50).toInstant(ist).toEpochMilliseconds()
        val nowIst = LocalDateTime(2026, 11, 18, 0, 20).toInstant(ist).toEpochMilliseconds()
        assertEquals("Last bank text seen yesterday 11:50 pm", CaptureHeartbeat.chip(nowIst, last, true, ist)!!.text)
    }
}
