package app.expenses.core.storage

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Lets one piece of work run at a time and tells a second caller at once, instead of making it wait or letting it overlap: two restores must not store
 * the same files twice, and two automatic copies must not write the same file. Not re-entrant.
 */
@OptIn(ExperimentalAtomicApi::class)
class SingleFlight {
    private val busy = AtomicBoolean(false)

    /** True when nothing else was running: the caller now holds it and must call [leave]. */
    fun tryEnter(): Boolean = busy.compareAndSet(false, true)

    fun leave() = busy.store(false)

    val isBusy: Boolean get() = busy.load()
}

/** Runs [block] when nothing else is running (releasing afterwards, even on failure), else calls [onBusy] without waiting. [block] may suspend. */
inline fun <T> SingleFlight.runOrElse(onBusy: () -> T, block: () -> T): T {
    if (!tryEnter()) return onBusy()
    try {
        return block()
    } finally {
        leave()
    }
}
