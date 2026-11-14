package app.expenses.ui

import kotlin.concurrent.Volatile

/**
 * The one hook the shared screens give the debug-only frame loggers (Android `FrameLog`, iPhone `IosFrameLog`): the screen host reports each change of screen, and the
 * logger starts a new measuring window at that moment. It is null, and so costs one read of a field, in every build that does not install a logger; nothing here measures anything.
 */
object FrameProbe {
    /** Told of a change of screen: the screen key being left and the one being shown (see [ScreenKeys]; -1 is Home). */
    @Volatile var onScreenChange: ((from: Int, to: Int) -> Unit)? = null

    /** What the person sees a screen key as, for a log line. Keys that are not named here are logged as `screen N`. */
    fun nameOf(key: Int): String = when (key) {
        ScreenKeys.HOME -> "Home"
        ScreenKeys.SEARCH -> "Search"
        16 -> "Reports"
        ScreenKeys.ADD_FORM -> "Add"
        ScreenKeys.EDIT_FORM -> "Entry"
        else -> "screen $key"
    }
}
