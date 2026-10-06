package app.expenses.core.domain.sync

/**
 * Hybrid logical clock timestamp: physical milliseconds, a counter for events in the same
 * millisecond, and a node id as a final tie-breaker. Total order across devices; encodes to a
 * fixed-width string whose lexicographic order equals the timestamp order.
 */
data class Hlc(val wallMillis: Long, val counter: Int, val node: String) : Comparable<Hlc> {
    init {
        require(wallMillis >= 0) { "wallMillis must not be negative" }
        require(counter in 0..MAX_COUNTER) { "counter out of range" }
        require(node.isNotEmpty() && node.none { it == '-' }) { "node must be non-empty and contain no '-'" }
    }

    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, { it.wallMillis }, { it.counter }, { it.node })

    fun encode(): String =
        wallMillis.toString().padStart(15, '0') + "-" + counter.toString(16).padStart(4, '0') + "-" + node

    companion object {
        const val MAX_COUNTER = 0xFFFF

        fun decode(s: String): Hlc {
            val parts = s.split('-', limit = 3)
            require(parts.size == 3) { "Invalid HLC: $s" }
            return Hlc(parts[0].toLong(), parts[1].toInt(16), parts[2])
        }
    }
}

/** Issues monotonically increasing timestamps for one device and merges remote ones. */
class HlcClock(private val node: String, private val maxDriftMillis: Long = 60_000) {
    private var last = Hlc(0, 0, node)

    fun now(physicalMillis: Long): Hlc {
        val wall = maxOf(physicalMillis, last.wallMillis)
        val counter = if (wall == last.wallMillis) last.counter + 1 else 0
        check(counter <= Hlc.MAX_COUNTER) { "HLC counter overflow" }
        last = Hlc(wall, counter, node)
        return last
    }

    /** Merges a timestamp received from another device. Rejects clocks too far in the future. */
    fun receive(remote: Hlc, physicalMillis: Long): Hlc {
        require(remote.wallMillis - physicalMillis <= maxDriftMillis) { "Remote clock is too far ahead" }
        val wall = maxOf(physicalMillis, last.wallMillis, remote.wallMillis)
        val counter = when {
            wall == last.wallMillis && wall == remote.wallMillis -> maxOf(last.counter, remote.counter) + 1
            wall == last.wallMillis -> last.counter + 1
            wall == remote.wallMillis -> remote.counter + 1
            else -> 0
        }
        check(counter <= Hlc.MAX_COUNTER) { "HLC counter overflow" }
        last = Hlc(wall, counter, node)
        return last
    }
}
