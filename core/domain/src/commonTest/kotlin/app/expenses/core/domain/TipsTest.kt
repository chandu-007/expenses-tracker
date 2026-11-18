package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TipsTest {
    @Test
    fun thereAreSixToEightTipsWithShortTextsAndOnePerScreen() {
        assertTrue(Tips.ALL.size in 6..8)
        assertEquals(Tips.ALL.size, Tips.IDS.size)
        assertEquals(Tips.ALL.size, Tips.ALL.map { it.screen }.toSet().size)
        for (t in Tips.ALL) {
            assertTrue(t.text.split(' ').size <= 14, t.text)
            assertTrue(t.text.none { it.isDigit() } && '!' !in t.text, t.text)
        }
    }

    @Test
    fun aTipIsUnseenUntilItsIdIsInTheSeenSet() {
        assertNotNull(Tips.unseen(emptySet(), "reports"))
        assertNull(Tips.unseen(setOf("reports"), "reports"))
        assertNotNull(Tips.unseen(setOf("reports"), "budget"))
        assertNull(Tips.unseen(emptySet(), "no_such_screen"))
    }

    @Test
    fun theSeenSetRoundTripsAndToleratesJunk() {
        val seen = setOf("budget", "reports")
        assertEquals("budget,reports", Tips.encode(seen))
        assertEquals(seen, Tips.decode(Tips.encode(seen)))
        assertEquals(emptySet(), Tips.decode(null))
        assertEquals(emptySet(), Tips.decode(""))
        assertEquals(setOf("a", "b"), Tips.decode(" a,,b, "))
        assertEquals(Tips.IDS, Tips.decode(Tips.encode(Tips.IDS)))
    }
}
