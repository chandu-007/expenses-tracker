package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HapticsTest {
    @Test
    fun hapticsAreOnUnlessSwitchedOff() {
        assertTrue(Haptics.isOn(null))
        assertTrue(Haptics.isOn("1"))
        assertTrue(Haptics.isOn(""))
        assertFalse(Haptics.isOn("0"))
        assertEquals("haptics", Haptics.SETTING)
    }

    @Test
    fun aScrubTickIsAllowedOncePer40MillisecondsAndTheFirstAlwaysPasses() {
        val t = HapticThrottle()
        assertTrue(t.allow(0))
        assertFalse(t.allow(10))
        assertFalse(t.allow(39))
        assertTrue(t.allow(40))
        assertFalse(t.allow(79))
        assertTrue(t.allow(500))
    }

    @Test
    fun theFirstTickPassesEvenAtTimeZero() {
        assertTrue(HapticThrottle(40).allow(0))
        assertTrue(HapticThrottle(1_000).allow(Long.MIN_VALUE / 4))
    }
}
