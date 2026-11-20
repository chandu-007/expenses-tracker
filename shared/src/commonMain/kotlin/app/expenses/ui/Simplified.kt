package app.expenses.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * UI S7 (U-21, D15): true while the app is simplified for a weak phone (see [app.expenses.core.domain.Simplify]): Quick motion, no blurred glow behind the month card, no draw-in of the 14-day line, no spring on the account
 * stack, and digits that change without rolling. Provided once by the app root. Reduce motion is a separate, stricter switch of the person's own.
 */
val LocalSimplified = compositionLocalOf { false }

/** True when the system calls this a low-RAM phone (Android: `ActivityManager.isLowRamDevice`; iPhone: 3 GB of memory or less). */
@Composable
expect fun rememberLowRamDevice(): Boolean

/**
 * Watches how fast this phone draws and calls [onSlow] once, on the main thread, when it has drawn slowly for long enough (see [app.expenses.core.domain.FrameHealth]). It does nothing on an emulator (they draw slowly in
 * software, which says nothing about a phone) and on the iPhone. It reads the frame times the system already measures; no permission and no extra work per frame beyond adding one number.
 */
@Composable
expect fun SlowFrameWatch(onSlow: () -> Unit)

/**
 * Whether the person asked the phone for less transparency (iPhone: Reduce Transparency; Android: high-contrast text or Android 14's contrast level). The bottom bar is then opaque with a visible edge, and has no
 * scroll-edge fade (UI S7, U-22).
 */
@Composable
expect fun systemReduceTransparency(): Boolean

/**
 * UI S9 (U-25): the phone asks for more contrast (Android: high-contrast text, or Android 14's contrast level above standard; iPhone: Increase Contrast). The theme then makes secondary text as strong
 * as body text and every outline a visible line ([app.expenses.core.domain.ThemeRoles]); cards drop their see-through tint and the bar is opaque. Read once per configuration, never per frame.
 */
@Composable
expect fun systemHighContrast(): Boolean

/** UI S9: [systemHighContrast] as the app's root read it, for the parts that are drawn differently (cards, the bar, the rail). */
val LocalHighContrast = androidx.compose.runtime.compositionLocalOf { false }

/**
 * UI S9 (D18): the phone's own accent colour (0xRRGGBB) for the "Wallpaper" accent, or null where there is none: Android before 12, and the iPhone (which offers no such colour to apps). Android 12 and later:
 * the primary colour of the system's dynamic light scheme (Material 3's own `dynamicLightColorScheme`, already a dependency), which follows the wallpaper.
 */
@Composable
expect fun systemAccentRgb(): Int?
