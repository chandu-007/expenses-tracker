package app.expenses.core.domain

/**
 * How the app lays itself out for the window it is in: one pane (a phone) or two (Home beside the entry being opened). One pure rule, used by Android and iPhone/iPad alike,
 * so a test can state every device by its size in dp (points on iOS) without a screen.
 *
 * The rule follows the Material window size classes (width Medium starts at 600 dp, height Compact ends below 480 dp):
 * - two panes need a WINDOW at least [TWO_PANE_MIN_WIDTH_DP] wide AND at least [TWO_PANE_MIN_HEIGHT_DP] high;
 * - the width is the window's own, never the screen's or the device's smallest width: a foldable's inner screen, a tablet, a split-screen half and a free-form (DeX) window
 *   each follow what they really get, and the answer changes with the window as it is folded, unfolded or resized;
 * - the height test keeps a phone on its side (about 800 x 360 dp, a large one 950 x 440) a phone: two panes there would be two strips of 4 rows each.
 *
 * Why 600 and not 840 (the first version): a book-style foldable opened is 690 x 829 dp (Galaxy Z Fold 5), 707 x 823 (Fold 6), 750 x 832 (Fold 7) at Samsung's default
 * display size, near square, so it is never 840 wide in either posture; 840 is only reached with the display size turned smaller. 600 is where Material starts "Medium".
 */
enum class WindowLayout {
    /** One pane, as on a phone. */
    PHONE,

    /** Home on the left, the open entry on the right. */
    TWO_PANE;

    companion object {
        const val TWO_PANE_MIN_WIDTH_DP = 600
        const val TWO_PANE_MIN_HEIGHT_DP = 480

        /** The layout for a window [widthDp] wide and [heightDp] high (dp on Android, points on iOS). */
        fun of(widthDp: Float, heightDp: Float): WindowLayout =
            if (widthDp >= TWO_PANE_MIN_WIDTH_DP && heightDp >= TWO_PANE_MIN_HEIGHT_DP) TWO_PANE else PHONE

        fun of(widthDp: Int, heightDp: Int): WindowLayout = of(widthDp.toFloat(), heightDp.toFloat())

        private const val LIST_SHARE = 0.42f
        private const val LIST_MIN_DP = 340f
        private const val LIST_MAX_DP = 480f

        /**
         * The width of the list pane in a two-pane window [paneAreaDp] wide: 42% of it, at least 340 dp (a phone-sized list) and at most 480; never more than half of the area,
         * so at the narrowest two-pane window (600 dp) the panes are 300 and 300 and from 680 dp on the entry has the larger share.
         */
        fun listPaneWidthDp(paneAreaDp: Float): Float = (paneAreaDp * LIST_SHARE).coerceIn(minOf(LIST_MIN_DP, paneAreaDp / 2f), LIST_MAX_DP)

        /**
         * The list pane's width when a fold that separates the window runs top to bottom at [hingeLeftDp] (a foldable half open like a book): the list is the left half, so Home and the
         * entry sit on either side of the hinge and nothing is drawn across it. Only when both panes stay usable (the list at least [LIST_HINGE_MIN_DP] wide, the entry at least
         * [DETAIL_HINGE_MIN_DP] after the hinge's [hingeWidthDp]); otherwise the ordinary width.
         */
        fun listPaneWidthDp(paneAreaDp: Float, hingeLeftDp: Float?, hingeWidthDp: Float): Float {
            if (hingeLeftDp != null && hingeLeftDp >= LIST_HINGE_MIN_DP && paneAreaDp - hingeLeftDp - hingeWidthDp >= DETAIL_HINGE_MIN_DP) return hingeLeftDp
            return listPaneWidthDp(paneAreaDp)
        }

        private const val LIST_HINGE_MIN_DP = 280f
        private const val DETAIL_HINGE_MIN_DP = 280f

        /**
         * The height the app is drawn in: the window's, or only the part above a fold that runs across it ([hingeTopDp]: a foldable in table-top posture, the lower half lying on the table).
         * Under the 480 dp that gives, the layout is the phone's.
         */
        fun usableHeightDp(windowHeightDp: Float, hingeTopDp: Float?): Float = if (hingeTopDp != null && hingeTopDp > 0f && hingeTopDp < windowHeightDp) hingeTopDp else windowHeightDp

        /** UI S9 (U-24): the navigation rail's width, where it replaces the bar along the bottom. */
        const val RAIL_WIDTH_DP = 80

        /** UI S9: the widest the Add form's number pad is drawn: a 1,000 dp keypad on an unfolded foldable (the audit's `add_expense_empty_w864f10l.png`) is a hard target. */
        const val PAD_MAX_WIDTH_DP = 480

        /**
         * UI S9 (U-24): Home, Search and Reports are reached from a rail along the left edge instead of the bar along the bottom wherever the window has two panes, except while a fold runs top
         * to bottom through it (half open like a book): there the panes are placed on either side of the hinge, measured from the window's left edge, and the bar keeps its place.
         */
        fun usesRail(layout: WindowLayout, verticalHinge: Boolean): Boolean = layout == TWO_PANE && !verticalHinge

        /** UI S9: the narrowest the pane beside the list may be for the Add form to open in it: under this its heading ("New expense") and the date chip no longer fit one line. */
        const val ADD_PANE_MIN_DP = 300

        /** The width of the pane beside the list in a window [windowWidthDp] wide, with the navigation [rail] taking its [RAIL_WIDTH_DP] (the divider is 1 dp). */
        fun detailPaneWidthDp(windowWidthDp: Float, rail: Boolean): Float {
            val area = windowWidthDp - if (rail) RAIL_WIDTH_DP else 0
            return area - listPaneWidthDp(area) - 1f
        }

        /**
         * UI S9 (U-24): the Add form opens in the pane beside the list (not over the whole window) when the window has two panes, no screen other than Home is open under it and the pane is at least
         * [ADD_PANE_MIN_DP] wide: with the rail the panes are 260 and 259 dp at 600 dp, too narrow for the form's heading, so there (up to 680 dp) Add covers the window as it did before. [windowWidthDp] is
         * left out where the width is not known (the pane is then taken to fit).
         */
        fun addBesideList(layout: WindowLayout, adding: Boolean, otherScreenOpen: Boolean, windowWidthDp: Float? = null, rail: Boolean = true): Boolean =
            layout == TWO_PANE && adding && !otherScreenOpen && (windowWidthDp == null || detailPaneWidthDp(windowWidthDp, rail) >= ADD_PANE_MIN_DP)
    }
}
