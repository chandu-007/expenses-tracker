package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameStatsTest {
    private fun stats(vararg ms: Double) = FrameStats().also { s -> ms.forEach { s.add(it) } }

    @Test
    fun noFramesGiveZerosAndNoDivisionByZero() {
        val s = FrameStats()
        assertEquals(0, s.count)
        assertEquals(0.0, s.percentile(50.0)); assertEquals(0.0, s.percentile(99.0)); assertEquals(0.0, s.max)
        assertEquals("x frames=0 p50=0.0 p90=0.0 p99=0.0 max=0.0 >16.7=0 >8.3=0 >100=0 >700=0", s.line("x"))
    }

    @Test
    fun percentilesAreNearestRankOverTheSortedFrames() {
        val s = stats(*DoubleArray(100) { (it + 1).toDouble() }) // 1.0 .. 100.0 ms, added in order
        assertEquals(50.0, s.percentile(50.0)); assertEquals(90.0, s.percentile(90.0)); assertEquals(99.0, s.percentile(99.0)); assertEquals(100.0, s.percentile(100.0))
        assertEquals(1.0, s.percentile(0.0), "rank is at least the first frame")
        assertEquals(100.0, s.max)
    }

    @Test
    fun theOrderFramesArriveInDoesNotMatter() {
        assertEquals(stats(5.0, 40.0, 12.0, 300.0).line("a"), stats(300.0, 12.0, 5.0, 40.0).line("a"))
    }

    @Test
    fun thresholdsCountOnlyFramesStrictlyOverThem() {
        val s = stats(8.3, 8.4, 16.7, 16.8, 100.0, 100.1, 700.0, 701.0)
        assertEquals(7, s.over(8.3)); assertEquals(5, s.over(16.7)); assertEquals(3, s.over(100.0)); assertEquals(1, s.over(700.0))
    }

    @Test
    fun theLineIsOneRoundedLineAndRunsAreAddedTogether() {
        val a = stats(10.0, 20.0, 30.0); val b = stats(40.0, 450.04)
        val both = a.plus(b)
        assertEquals(5, both.count); assertEquals(3, a.count, "the originals are not changed")
        assertEquals("Add open frames=5 p50=30.0 p90=450.0 p99=450.0 max=450.0 >16.7=4 >8.3=5 >100=1 >700=0", both.line("Add open"))
    }

    @Test
    fun growsPastItsFirstBuffer() {
        val s = FrameStats()
        repeat(1000) { s.add(it.toDouble()) }
        assertEquals(1000, s.count); assertEquals(500.0 - 1, s.percentile(50.0)); assertEquals(999.0, s.max)
    }
}
