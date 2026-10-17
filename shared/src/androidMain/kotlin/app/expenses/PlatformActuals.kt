package app.expenses

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

internal actual fun rethrowUncaught(e: Throwable) {
    val t = Thread.currentThread()
    t.uncaughtExceptionHandler?.uncaughtException(t, e) ?: throw e
}
