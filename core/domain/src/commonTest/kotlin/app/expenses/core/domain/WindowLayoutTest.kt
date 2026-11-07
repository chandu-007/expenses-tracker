package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One or two panes, by the size of the WINDOW in dp (points on iOS). Every size below is a published screen divided by its usual density (px / (dpi / 160)),
 * or an invented window; nothing here was measured on a real device.
 */
class WindowLayoutTest {
    private fun layout(w: Int, h: Int) = WindowLayout.of(w, h)
    private fun two(name: String, w: Int, h: Int) = assertEquals(WindowLayout.TWO_PANE, layout(w, h), "$name ($w x $h dp) should be two panes")
    private fun one(name: String, w: Int, h: Int) = assertEquals(WindowLayout.PHONE, layout(w, h), "$name ($w x $h dp) should be one pane")

    @Test
    fun phonesAreOnePaneUprightAndOnTheirSide() {
        one("small phone upright", 360, 780)
        one("Pixel 8 upright", 411, 914)
        one("iPhone 17 Pro Max upright", 440, 956)
        one("small phone on its side", 800, 360)
        one("Pixel 8 Pro on its side", 892, 412)
        one("iPhone 17 Pro Max on its side", 956, 440)
        one("iPhone SE on its side", 667, 375)
    }

    @Test
    fun aFoldablesCoverScreenAndAFlipAreOnePane() {
        one("Z Fold 5 cover", 344, 882)
        one("Z Fold 5 cover on its side", 882, 344)
        one("Z Fold 6 cover", 369, 905)
        one("Z Fold 7 cover", 411, 960)
        one("Z Flip 6 inner, 6.7 in tall and narrow", 411, 1005)
        one("Z Flip 6 inner on its side", 1005, 411)
        one("Z Flip cover", 360, 374)
    }

    @Test
    fun anUnfoldedBookStyleFoldableIsTwoPanesAtSamsungsDefaultDisplaySizeInBothPostures() {
        two("Z Fold 3 / 4 / 5 inner, upright (1812 x 2176 px at 420 dpi)", 690, 829)
        two("Z Fold 3 / 4 / 5 inner, on its side", 829, 690)
        two("Z Fold 6 inner, upright", 707, 823)
        two("Z Fold 6 inner, on its side", 823, 707)
        two("Z Fold 7 inner, upright (1968 x 2184 px)", 750, 832)
        two("Z Fold 7 inner, on its side", 832, 750)
        two("Pixel Fold inner on its side (2208 x 1840 px at 420 dpi)", 841, 701)
        two("Pixel 9 Pro Fold inner", 791, 820)
    }

    @Test
    fun theSamsungScreenZoomRangeStaysTwoPanesFromTheSmallestToTheLargestFoldInner() {
        // the display-size / screen-zoom setting moves the density from the 420 dpi default: at 480 dpi the short side of a Fold 5 inner is 604 dp, at 373 dpi it is 777, and the long side 933 (ASSUMED
        // range; Android allows up to 1.3 x, i.e. 546 dpi and 531 dp, which is under Medium and stays one pane on purpose: that is a phone-sized window)
        for (w in listOf(604, 638, 673, 777, 812, 933, 975)) two("Fold 5 inner at another zoom", w, 700)
        one("Fold 5 inner upright at Android's largest display size (546 dpi)", 531, 700)
        for (w in listOf(604, 673, 750, 906)) two("Fold 7 inner at another zoom", w, 900)
        // the large display size on a Fold 5 held upright is 604 x 725 dp: still wide enough
        two("Fold 5 inner upright at the largest zoom", 604, 725)
    }

    @Test
    fun tabletsAreTwoPanesUprightAndOnTheirSide() {
        two("Galaxy Tab S9 upright (800 x 1280 dp)", 800, 1280)
        two("Galaxy Tab S9 on its side", 1280, 800)
        two("iPad mini upright", 744, 1133)
        two("iPad 11 in upright", 834, 1194)
        two("iPad 11 in on its side", 1194, 834)
        two("Pixel Tablet on its side", 1280, 800)
        two("7 in tablet upright", 600, 960)
    }

    @Test
    fun splitScreenHalvesAndFreeformWindowsFollowTheirOwnSize() {
        // a Fold 5 inner, 829 x 690 on its side, split down the middle: each half is a phone
        one("Fold inner half, left or right", 414, 690)
        // a Fold 7 held upright, split into a top and a bottom app
        one("Fold 7 inner top half", 750, 416)
        // a tablet in landscape split in two equal halves
        two("Tab S9 landscape, half", 640, 800)
        one("Tab S9 landscape, a 1/3 window", 427, 800)
        // DeX / free-form: the window is what counts
        two("DeX window 1000 x 700", 1000, 700)
        one("DeX window 500 x 700", 500, 700)
        one("DeX window 1000 x 450 (short)", 1000, 450)
        two("DeX full screen 1920 x 1080", 1920, 1080)
        one("a pop-up view 400 x 600", 400, 600)
    }

    @Test
    fun theEdgesAre600WideAnd480High() {
        assertEquals(600, WindowLayout.TWO_PANE_MIN_WIDTH_DP); assertEquals(480, WindowLayout.TWO_PANE_MIN_HEIGHT_DP)
        two("exactly the minimum", 600, 480)
        one("a dp short in width", 599, 480)
        one("a dp short in height", 600, 479)
        assertEquals(WindowLayout.PHONE, WindowLayout.of(599.9f, 900f))
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.of(600f, 480f))
        one("zero (before the first layout)", 0, 0)
    }

    @Test
    fun noBookStyleFoldableIs840WideAtTheDefaultDisplaySizeSoTheOldBreakpointNeverHeldTwoPanesOnOne() {
        // the first version of the rule asked for 840 dp; the widest side of these is below it, which is why a Fold never showed two panes
        assertTrue(listOf(690, 829, 707, 823, 750, 832).all { it < 840 })
    }

    @Test
    fun theListPaneIs42PercentBetween340And480AndNeverMoreThanHalf() {
        assertEquals(300f, WindowLayout.listPaneWidthDp(600f), "at the narrowest two-pane window: half and half")
        assertEquals(340f, WindowLayout.listPaneWidthDp(690f), "a Fold 5 upright: a phone-sized list, the entry gets 350")
        assertEquals(829 * 0.42f, WindowLayout.listPaneWidthDp(829f), "a Fold 5 on its side: 348 for the list, 481 for the entry")
        assertEquals(1000 * 0.42f, WindowLayout.listPaneWidthDp(1000f))
        assertEquals(480f, WindowLayout.listPaneWidthDp(1280f))
        assertEquals(480f, WindowLayout.listPaneWidthDp(2000f))
        for (area in listOf(600f, 640f, 690f, 750f, 840f, 1000f, 1280f)) {
            val list = WindowLayout.listPaneWidthDp(area)
            assertTrue(list <= area / 2f, "the list never takes more than half at $area")
            assertTrue(area - list >= list * 0.85f, "the entry is not much narrower than the list at $area dp (list $list)")
        }
    }

    @Test
    fun aVerticalFoldSplitsTheTwoPanesAtTheHingeWhenBothSidesStayUsable() {
        // a Fold half open like a book, 829 dp wide: the hinge at 414.5, no width of its own
        assertEquals(414.5f, WindowLayout.listPaneWidthDp(829f, 414.5f, 0f))
        // with a 8 dp hinge the entry starts after it
        assertEquals(410f, WindowLayout.listPaneWidthDp(829f, 410f, 8f))
        // no fold: the ordinary width
        assertEquals(WindowLayout.listPaneWidthDp(829f), WindowLayout.listPaneWidthDp(829f, null, 0f))
        // a fold too near an edge leaves a side too narrow: the ordinary width
        assertEquals(WindowLayout.listPaneWidthDp(829f), WindowLayout.listPaneWidthDp(829f, 200f, 0f))
        assertEquals(WindowLayout.listPaneWidthDp(829f), WindowLayout.listPaneWidthDp(829f, 600f, 0f))
    }

    @Test
    fun aFoldAcrossTheWindowKeepsTheAppAboveItAndThePhoneLayoutFollowsFromTheHeightLeft() {
        assertEquals(829f, WindowLayout.usableHeightDp(829f, null))
        assertEquals(345f, WindowLayout.usableHeightDp(690f, 345f), "a Fold on its side in table-top: the top half")
        assertEquals(690f, WindowLayout.usableHeightDp(690f, 0f)); assertEquals(690f, WindowLayout.usableHeightDp(690f, 700f))
        assertEquals(WindowLayout.PHONE, WindowLayout.of(829f, WindowLayout.usableHeightDp(690f, 345f)))
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.of(829f, WindowLayout.usableHeightDp(690f, null)))
    }
}
