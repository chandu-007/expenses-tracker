package app.expenses

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

internal actual fun rethrowUncaught(e: Throwable) {
    throw e
}
