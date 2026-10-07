package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {
    private fun gate(timeout: LockTimeout = LockTimeout.FIVE_MINUTES, enabled: Boolean = true, startLocked: Boolean = false) =
        LockGate(LockSettings(enabled, timeout), startLocked)

    @Test
    fun aColdStartIsLockedOnlyWhenTheLockIsOn() {
        assertTrue(LockGate(LockSettings(true)).isLocked)
        assertFalse(LockGate(LockSettings(false)).isLocked)
        assertFalse(LockGate(LockSettings(false), startLocked = true).isLocked, "a lock that is off never locks, whatever the caller asks")
    }

    @Test
    fun comingBackAfterTheTimeoutLocksAndBeforeItDoesNot() {
        val g = gate(LockTimeout.FIVE_MINUTES)
        g.onBackgrounded(1_000)
        assertFalse(g.onForegrounded(1_000 + 299_999), "one millisecond short")
        g.onBackgrounded(400_000)
        assertTrue(g.onForegrounded(400_000 + 300_000), "exactly the timeout locks")
    }

    @Test
    fun immediatelyLocksEveryTimeTheAppIsLeftEvenForNoTimeAtAll() {
        val g = gate(LockTimeout.IMMEDIATELY)
        g.onBackgrounded(5_000)
        assertTrue(g.onForegrounded(5_000))
    }

    @Test
    fun onceLockedItStaysLockedUntilTheUserUnlocks() {
        val g = gate(LockTimeout.ONE_MINUTE)
        g.onBackgrounded(0)
        assertTrue(g.onForegrounded(120_000))
        g.onBackgrounded(120_001)
        assertTrue(g.onForegrounded(120_002), "a quick glance at a locked app does not unlock it")
        g.unlocked()
        assertFalse(g.isLocked)
        g.onBackgrounded(200_000)
        assertFalse(g.onForegrounded(210_000), "and the count starts afresh after an unlock")
    }

    @Test
    fun leavingTwiceWithoutComingBackKeepsTheFirstTime() {
        val g = gate(LockTimeout.ONE_MINUTE)
        g.onBackgrounded(0)
        g.onBackgrounded(59_000) // a second stop must not make the absence look shorter
        assertTrue(g.onForegrounded(60_000))
    }

    @Test
    fun eachAbsenceIsMeasuredOnItsOwnNotFromAnEarlierOne() {
        val g = gate(LockTimeout.ONE_MINUTE)
        g.onBackgrounded(0)
        assertFalse(g.onForegrounded(10_000)) // a short trip away, no lock
        g.onBackgrounded(50_000)
        assertFalse(g.onForegrounded(70_000), "20 seconds away this time, not 70 since the first time")
    }

    @Test
    fun aClockThatWentBackwardsLocksBecauseWhenInDoubtItAsks() {
        val g = gate(LockTimeout.FIFTEEN_MINUTES)
        g.onBackgrounded(1_000_000)
        assertTrue(g.onForegrounded(10), "e.g. after a reboot, where the clock restarts")
    }

    @Test
    fun comingBackWithoutEverLeavingDoesNothing() {
        val g = gate(LockTimeout.IMMEDIATELY)
        assertFalse(g.onForegrounded(10_000))
    }

    @Test
    fun turningTheLockOffUnlocksAndTurningItOnDoesNotLockTheScreenInUse() {
        val g = gate(startLocked = true)
        assertTrue(g.isLocked)
        g.configure(LockSettings(false))
        assertFalse(g.isLocked)
        g.onBackgrounded(0)
        assertFalse(g.onForegrounded(10_000_000), "off means off")
        g.configure(LockSettings(true, LockTimeout.IMMEDIATELY))
        assertFalse(g.isLocked, "turning it on while using the app does not throw the user out")
        g.onBackgrounded(1); assertTrue(g.onForegrounded(2))
    }

    @Test
    fun changingTheTimeoutTakesEffectForTheNextAbsence() {
        val g = gate(LockTimeout.FIFTEEN_MINUTES)
        g.configure(LockSettings(true, LockTimeout.ONE_MINUTE))
        g.onBackgrounded(0)
        assertTrue(g.onForegrounded(61_000))
    }

    @Test
    fun forAnyTimeoutAndAnyAbsenceItLocksExactlyWhenTheAbsenceReachesTheTimeout() = runTest {
        for (t in LockTimeout.entries) checkAll(Arb.long(0, 5_000_000), Arb.long(0, 5_000_000)) { start, away ->
            val g = gate(t)
            g.onBackgrounded(start)
            assertEquals(away >= t.millis, g.onForegrounded(start + away), "$t away $away")
        }
    }
}
