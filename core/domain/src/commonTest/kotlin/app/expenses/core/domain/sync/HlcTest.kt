package app.expenses.core.domain.sync

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HlcTest {
    @Test
    fun timestampsIncreaseEvenWhenPhysicalClockStandsStillOrGoesBack() = runTest {
        checkAll(Arb.list(Arb.long(0, 1_000_000), 2..50)) { physical ->
            val clock = HlcClock("a")
            val stamps = physical.map { clock.now(it) }
            assertTrue(stamps.zipWithNext().all { (x, y) -> x < y })
        }
    }

    @Test
    fun receiveOrdersAfterBothLocalAndRemote() = runTest {
        checkAll(Arb.long(0, 1_000_000), Arb.long(0, 1_000_000), Arb.int(0, 1000)) { local, remoteWall, c ->
            val clock = HlcClock("a")
            val l = clock.now(local)
            val remote = Hlc(remoteWall.coerceAtMost(local + 60_000), c, "b")
            val merged = clock.receive(remote, local)
            assertTrue(merged > l)
            assertTrue(merged > remote)
        }
    }

    @Test
    fun encodingRoundTripsAndSortsLikeTheTimestamp() = runTest {
        checkAll(Arb.long(0, 9_999_999_999_999L), Arb.int(0, Hlc.MAX_COUNTER), Arb.long(0, 9_999_999_999_999L), Arb.int(0, Hlc.MAX_COUNTER)) { w1, c1, w2, c2 ->
            val a = Hlc(w1, c1, "n1")
            val b = Hlc(w2, c2, "n2")
            assertEquals(a, Hlc.decode(a.encode()))
            assertEquals(a.compareTo(b).coerceIn(-1, 1), a.encode().compareTo(b.encode()).coerceIn(-1, 1))
        }
    }

    @Test
    fun nodeBreaksTiesAndFarFutureClocksAreRejected() {
        assertTrue(Hlc(5, 1, "a") < Hlc(5, 1, "b"))
        val clock = HlcClock("a", maxDriftMillis = 1_000)
        assertFailsWith<IllegalArgumentException> { clock.receive(Hlc(10_000, 0, "b"), 0) }
    }
}
