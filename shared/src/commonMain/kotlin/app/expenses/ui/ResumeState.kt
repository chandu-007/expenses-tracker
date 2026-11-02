package app.expenses.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * A cheap status the system can change while the app is away (a permission, notification access, "notifications allowed"). It is read once when composed, again every
 * time the app comes back to the foreground (the lifecycle's ON_RESUME: after the system permission dialog closes, after Android's or the iPhone's Settings, after another
 * app), and whenever [refresh] is called (right after a permission request's result). Only a *changed* value is written, so an unchanged status recomposes nothing.
 *
 * [read] must be a quick call (a permission check, a settings read), never a database query.
 */
@Stable
class ResumeState<T>(private val read: () -> T) {
    var value: T by mutableStateOf(read())
        private set

    fun refresh() {
        val now = read()
        if (now != value) value = now
    }
}

/** See [ResumeState]. The latest [read] is used on each refresh, so it may capture values that change. */
@Composable
fun <T> rememberResumeState(read: () -> T): ResumeState<T> {
    val latest = rememberUpdatedState(read)
    val state = remember { ResumeState { latest.value() } }
    LifecycleResumeEffect(state) { state.refresh(); onPauseOrDispose { } }
    return state
}
