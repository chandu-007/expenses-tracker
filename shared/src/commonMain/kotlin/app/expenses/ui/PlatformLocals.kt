package app.expenses.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.expenses.Platform

/** The host (Android activity, iOS view controller) provides this around the whole UI. */
val LocalPlatform = staticCompositionLocalOf<Platform> { error("LocalPlatform is not provided: the host must wrap the UI in CompositionLocalProvider(LocalPlatform provides ...)") }

/** What the phone can do for the app lock right now. */
enum class LockAvailability { READY, NO_SCREEN_LOCK, UNAVAILABLE }

/** Shown where a feature has no iPhone counterpart. */
const val NOT_ON_IPHONE = "Not available on iPhone"
