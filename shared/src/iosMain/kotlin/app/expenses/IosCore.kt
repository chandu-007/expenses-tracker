package app.expenses

import kotlinx.coroutines.runBlocking
import platform.Foundation.NSLock
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo

/**
 * What exists once per process and needs no screen: the iPhone [platform] and the one [container] that owns the database. The app's view model
 * ([IosApp]) is built on it; so are the things iOS starts in the background without a window (a Shortcuts automation running the
 * "Capture bank message" intent, a background refresh), which would otherwise open a second connection to the same file.
 *
 * Launch arguments for the UI tests: `-reset-data` wipes the ledger (as "start fresh" does, which also counts the guide as seen); `-dismiss-welcome` hides the welcome card.
 */
internal class IosCore private constructor(host: IosHostServices) {
    val platform = IosPlatform(host)
    private val arguments = NSProcessInfo.processInfo.arguments.filterIsInstance<String>()

    // Set up before any screen exists, so nothing has the file open.
    val container = AppContainer(platform).also { c ->
        if ("-reset-data" in arguments) runBlocking { c.startFresh() }
        if ("-dismiss-welcome" in arguments) runBlocking { c.write { it.templates.putSetting("welcome_dismissed", "1") } }
    }

    init {
        // UI tests: `-ingest-message` followed by "sender|words" hands that message to the same entry the Shortcuts intent uses (the sender may be empty).
        arguments.forEachIndexed { i, a ->
            if (a == "-ingest-message" && i + 1 < arguments.size) {
                val (sender, words) = arguments[i + 1].substringBefore('|') to arguments[i + 1].substringAfter('|')
                runBlocking { IosCapture.ingest(this@IosCore, sender, words) }
            }
        }
    }

    companion object {
        private var instance: IosCore? = null
        private val lock = NSLock() // the app's window and a background intent can ask at the same moment

        fun get(host: IosHostServices): IosCore {
            lock.lock()
            try {
                return instance ?: try {
                    IosCore(host).also { instance = it }
                } catch (e: Throwable) {
                    NSLog("Expenses: start-up failed: %@", e.stackTraceToString())
                    throw e
                }
            } finally {
                lock.unlock()
            }
        }
    }
}
