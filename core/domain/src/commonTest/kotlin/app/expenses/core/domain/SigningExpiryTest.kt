package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SigningExpiryTest {
    private val day = SigningExpiry.DAY_MS
    private val now = 1_800_000_000_000L
    private val tail = " Reinstall it from the Mac (see docs/IOS.md) and make a backup first."

    @Test fun daysLeftTable() {
        val cases = listOf(
            7 * day to 7L, 2 * day + 1 to 3L, 2 * day to 2L, day + 1 to 2L, day to 1L, 1L to 1L, 0L to 0L, -1L to 0L, -day + 1 to 0L, -day to -1L, -day - 1 to -1L,
        )
        for ((delta, days) in cases) assertEquals(days, SigningExpiry.daysLeft(now + delta, now), "delta $delta")
    }

    @Test fun noticeTable() {
        val cases = listOf<Pair<Long, String?>>(
            3 * day to null, 2 * day + 1 to null,
            2 * day to "This test build stops opening in 2 days.$tail",
            day + 1 to "This test build stops opening in 2 days.$tail",
            day to "This test build stops opening today.$tail",
            1L to "This test build stops opening today.$tail",
            0L to "This test build has expired and may stop opening.$tail",
            -5 * day to "This test build has expired and may stop opening.$tail",
        )
        for ((delta, text) in cases) assertEquals(text, SigningExpiry.notice(now + delta, now), "delta $delta")
    }

    @Test fun unknownExpiryHasNoNotice() = assertNull(SigningExpiry.notice(null, now))

    private val profile = "\u0001\u0002binary<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<plist version=\"1.0\"><dict>\n" +
        "<key>CreationDate</key>\n<date>2026-10-27T01:02:03Z</date>\n<key>ExpirationDate</key>\n<date>2026-11-03T01:02:03Z</date>\n" +
        "<key>Name</key><string>iOS Team Provisioning Profile</string></dict></plist>\u0003binary"

    @Test fun readsExpirationNotCreation() {
        // 2026-11-03T01:02:03Z
        assertEquals(1_793_667_723_000L, ProvisionProfile.expiresAtMillis(profile))
    }

    @Test fun epochAndLeapDay() {
        fun p(d: String) = "<?xml?><plist><key>ExpirationDate</key><date>$d</date></plist>"
        assertEquals(0L, ProvisionProfile.expiresAtMillis(p("1970-01-01T00:00:00Z")))
        assertEquals(951_782_400_000L, ProvisionProfile.expiresAtMillis(p("2000-02-29T00:00:00Z")))
        assertEquals(1_709_164_799_000L, ProvisionProfile.expiresAtMillis(p("2024-02-28T23:59:59Z")))
    }

    @Test fun missingOrBadGivesNull() {
        assertNull(ProvisionProfile.expiresAtMillis(""))
        assertNull(ProvisionProfile.expiresAtMillis("no plist here"))
        assertNull(ProvisionProfile.expiresAtMillis("<?xml?><plist><key>Other</key><date>2026-11-03T01:02:03Z</date></plist>"))
        assertNull(ProvisionProfile.expiresAtMillis("<?xml?><plist><key>ExpirationDate</key><date>soon</date></plist>"))
        assertNull(ProvisionProfile.expiresAtMillis("<?xml?><plist><key>ExpirationDate</key><date>2026-13-03T01:02:03Z</date></plist>"))
        assertNull(ProvisionProfile.expiresAtMillis("<?xml?><key>ExpirationDate</key><date>2026-11-03T01:02:03Z</date>"))
    }

    @Test fun profileThenNotice() {
        val exp = assertNotNull(ProvisionProfile.expiresAtMillis(profile))
        assertTrue(SigningExpiry.notice(exp, exp - day)!!.contains("today"))
    }
}
