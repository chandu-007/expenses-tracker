package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimplifyTest {
    @Test
    fun automaticSimplifiesOnALowRamPhoneOrSlowFramesAndTheSwitchWinsOverBoth() {
        assertFalse(Simplify.active(Simplify.Mode.AUTO, lowRamDevice = false, slowFrames = false))
        assertTrue(Simplify.active(Simplify.Mode.AUTO, lowRamDevice = true, slowFrames = false))
        assertTrue(Simplify.active(Simplify.Mode.AUTO, lowRamDevice = false, slowFrames = true))
        assertTrue(Simplify.active(Simplify.Mode.ON, lowRamDevice = false, slowFrames = false))
        assertFalse(Simplify.active(Simplify.Mode.OFF, lowRamDevice = true, slowFrames = true))
    }

    @Test
    fun anUnknownOrMissingSettingIsAutomatic() {
        assertEquals(Simplify.Mode.AUTO, Simplify.mode(null))
        assertEquals(Simplify.Mode.AUTO, Simplify.mode("banana"))
        assertEquals(Simplify.Mode.ON, Simplify.mode("on"))
        assertEquals(Simplify.Mode.OFF, Simplify.mode("off"))
        Simplify.Mode.entries.forEach { assertEquals(it, Simplify.mode(it.key)) }
    }

    @Test
    fun theLineSaysWhyInWords() {
        assertTrue(Simplify.line(Simplify.Mode.AUTO, lowRamDevice = true, slowFrames = false).startsWith("Simplified for this phone"))
        assertTrue(Simplify.line(Simplify.Mode.AUTO, lowRamDevice = false, slowFrames = true).startsWith("Simplified for this phone"))
        assertTrue(Simplify.line(Simplify.Mode.AUTO, lowRamDevice = false, slowFrames = false).startsWith("Automatic"))
        assertTrue(Simplify.line(Simplify.Mode.ON, false, false).contains("you switched it on"))
        assertTrue(Simplify.line(Simplify.Mode.OFF, true, true).contains("switched simplifying off"))
    }

    @Test
    fun threeSlowWindowsInARowAfterTheWarmUpMakeAPhoneSlow() {
        val h = FrameHealth()
        repeat(120) { assertFalse(h.add(200.0), "warm-up frames are not counted") }
        var flipped = 0
        repeat(3 * 90) { if (h.add(80.0)) flipped++ }
        assertEquals(1, flipped)
        assertTrue(h.slow)
        assertFalse(h.add(80.0), "it flips once")
    }

    @Test
    fun aGoodWindowBetweenSlowOnesStartsTheCountAgain() {
        val h = FrameHealth()
        repeat(120) { h.add(10.0) }
        repeat(2 * 90) { h.add(80.0) }
        repeat(90) { h.add(10.0) }
        repeat(2 * 90) { h.add(80.0) }
        assertFalse(h.slow)
        repeat(90) { h.add(80.0) }
        assertTrue(h.slow)
    }

    @Test
    fun aWindowWithFewSlowFramesAndFrozenFramesAreNotCounted() {
        val h = FrameHealth()
        repeat(120) { h.add(10.0) }
        repeat(10) { w -> repeat(90) { i -> h.add(if (i < 20) 80.0 else 12.0) } } // 20 of 90 is under 30%
        assertFalse(h.slow)
        repeat(3 * 90) { h.add(2_000.0) } // frozen frames (the app paused, a system dialog) say nothing about drawing speed
        assertFalse(h.slow)
    }
}
