package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelativeTimeTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    @Test fun words() {
        assertEquals("just now", RelativeTime.ago(0, 30_000))
        assertEquals("a minute ago", RelativeTime.ago(0, min))
        assertEquals("5 minutes ago", RelativeTime.ago(0, 5 * min))
        assertEquals("an hour ago", RelativeTime.ago(0, hour + 10 * min))
        assertEquals("5 hours ago", RelativeTime.ago(0, 5 * hour + 59 * min))
        assertEquals("yesterday", RelativeTime.ago(0, 30 * hour))
        assertEquals("3 days ago", RelativeTime.ago(0, 3 * day + hour))
        assertEquals("40 days ago", RelativeTime.ago(0, 40 * day))
    }

    @Test fun withinDays() {
        assertTrue(RelativeTime.within(0, 7 * day, 7))
        assertFalse(RelativeTime.within(0, 7 * day + 1, 7))
        assertTrue(RelativeTime.within(5 * day, 5 * day, 7))
    }
}
