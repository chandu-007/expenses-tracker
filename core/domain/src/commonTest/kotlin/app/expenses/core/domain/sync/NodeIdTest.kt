package app.expenses.core.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeIdTest {
    /** What is stored, as [NodeId.loadOrCreate] reads and writes it. */
    private class Stored(var value: String?) {
        var writes = 0
        fun load() = NodeId.loadOrCreate(read = { value }) { value = it; writes++ }
    }

    @Test
    fun anEmptyNodeIdFileGetsANewIdThatTheClockTakesInsteadOfStoppingTheApp() {
        // what a power cut right after the first launch left behind: the file made, its bytes never on the disk. The app read "" and stopped at HlcClock on every launch.
        assertFailsWith<IllegalArgumentException> { HlcClock("") }
        for (left in listOf("", " ", "\n", "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", "ab-cd", "a".repeat(65), "é1b2c3d4")) {
            val stored = Stored(left)
            val id = stored.load()
            HlcClock(id).now(1_000L) // does not throw
            assertEquals(8, id.length, "a new id for '$left'")
            assertEquals(id, stored.value, "the new id is stored for '$left'")
            assertEquals(1, stored.writes)
            assertEquals(id, stored.load(), "and read back unchanged")
            assertEquals(1, stored.writes, "and not made again")
        }
    }

    @Test
    fun aUsableStoredIdIsKeptAsItIsAndNeverRewritten() {
        for ((stored, expected) in listOf("a1b2c3d4" to "a1b2c3d4", "a1b2c3d4\n" to "a1b2c3d4", " a1b2c3d4 \r\n" to "a1b2c3d4", "N" to "N", "x".repeat(64) to "x".repeat(64))) {
            val s = Stored(stored)
            assertEquals(expected, s.load())
            assertEquals(0, s.writes, "'$stored' is not rewritten")
            assertEquals(stored, s.value)
        }
    }

    @Test
    fun noStoredIdMakesOneOnceAndEveryNewIdIsUsable() {
        val s = Stored(null)
        val id = s.load()
        assertEquals(listOf(id, 1), listOf(s.value, s.writes))
        assertEquals(id, s.load())
        assertEquals(1, s.writes)
        val made = List(1_000) { NodeId.generate() }
        assertTrue(made.all { it.length == 8 && NodeId.parse(it) == it && Hlc(0, 0, it).node == it })
        assertTrue(made.toSet().size > 990, "random ids")
    }

    @Test
    fun parseTakesOnlyIdsTheClockCanUse() {
        assertNull(NodeId.parse(null))
        assertNull(NodeId.parse(""))
        assertNull(NodeId.parse("12-34"))
        assertNull(NodeId.parse("12 34"))
        assertNotNull(NodeId.parse("1234\n")).let { HlcClock(it) }
    }
}
