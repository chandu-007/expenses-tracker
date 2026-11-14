package app.expenses.core.domain

/**
 * The frame times of one interaction (one screen change, one scroll), for the debug-only frame logger of both phones (Android: `FrameMetrics`, iPhone: a display link).
 * Durations are in milliseconds. This only counts and ranks what it is given; it reads no clock, so the same rules hold on both platforms and are tested on the host.
 *
 * "Over 16.7" is a frame that missed a 60 Hz deadline, "over 8.3" one that missed a 120 Hz deadline, "over 700" a frozen one (the thresholds Android's own vitals use).
 */
class FrameStats {
    private var times = DoubleArray(64)
    var count = 0
        private set

    fun add(ms: Double) {
        if (count == times.size) times = times.copyOf(count * 2)
        times[count++] = ms
    }

    /** The nearest-rank percentile (0 to 100) of the frames added so far; 0 with none. */
    fun percentile(p: Double): Double {
        if (count == 0) return 0.0
        val sorted = times.copyOf(count).also { it.sort() }
        val rank = kotlin.math.ceil(p / 100.0 * count).toInt().coerceIn(1, count)
        return sorted[rank - 1]
    }

    val max: Double get() = if (count == 0) 0.0 else times.copyOf(count).max()

    /** How many frames took longer than [ms]. */
    fun over(ms: Double): Int { var n = 0; for (i in 0 until count) if (times[i] > ms) n++; return n }

    /** This and [other] as one set of frames (several runs of the same interaction). */
    fun plus(other: FrameStats): FrameStats = FrameStats().also { s -> for (i in 0 until count) s.add(times[i]); for (i in 0 until other.count) s.add(other.times[i]) }

    /** One line for the log: `label frames=N p50=.. p90=.. p99=.. max=.. >16.7=a >8.3=b >100=c >700=d` (milliseconds, one decimal). */
    fun line(label: String): String =
        "$label frames=$count p50=${f(percentile(50.0))} p90=${f(percentile(90.0))} p99=${f(percentile(99.0))} max=${f(max)} >16.7=${over(16.7)} >8.3=${over(8.3)} >100=${over(100.0)} >700=${over(700.0)}"

    private fun f(v: Double): String { val t = kotlin.math.round(v * 10).toLong(); return "${t / 10}.${t % 10}" }
}
