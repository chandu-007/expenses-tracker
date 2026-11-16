package app.expenses

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.expenses.core.domain.BudgetState
import app.expenses.ui.AnimatedBudgetBar
import app.expenses.ui.LocalReduceMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Carried over from S1 into UI S2 (docs/PHASE2_PROGRESS.md, Release 0.14.0 "not fixed"): under Reduce motion the budget bar jumps to its value instead of animating over 100 ms; with motion on it still travels.
 */
@RunWith(AndroidJUnit4::class)
class UiS2CarryOverTest {
    @get:Rule val compose = createComposeRule()

    private fun line(percent: Int) = BudgetLineUi(null, "Overall", "₹10,000.00", "10000", "₹${percent * 100}.00", "₹${(100 - percent) * 100}.00 left", percent, BudgetState.OK)
    private fun shown(): Float = compose.onNodeWithTag("bar", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.current

    private fun barAfterOneFrame(reduce: Boolean): Float {
        var percent by androidx.compose.runtime.mutableStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalReduceMotion provides reduce) { AnimatedBudgetBar(line(percent), Modifier.testTag("bar")) } }
        compose.mainClock.advanceTimeByFrame()
        percent = 60
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val afterTwoFrames = shown()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0.6f, shown(), 0.001f, "the bar always ends on its value")
        return afterTwoFrames
    }

    @Test
    fun underReduceMotionTheBudgetBarIsAtItsValueAtOnce() {
        assertEquals(0.6f, barAfterOneFrame(reduce = true), 0.001f, "no animation at all: 0 ms, not a 100 ms fade")
    }

    @Test
    fun withMotionOnTheBudgetBarStillTravelsToItsValue() {
        assertTrue(barAfterOneFrame(reduce = false) < 0.55f, "a frame or two into the animation the bar has not arrived")
    }
}
