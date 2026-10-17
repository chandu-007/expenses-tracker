package app.expenses.core.storage

/**
 * The encrypted database exists but its key cannot be recovered: the Keystore wrapping key or the wrapped
 * key file is gone or unreadable (restore to a new device, Keystore reset, lock-screen change on some
 * phones). The data cannot be opened. The app must offer recovery (restore from backup or sync, or start
 * fresh with the user's consent via [EncryptedDriver.reset]) instead of crashing or silently creating a
 * new key.
 */
class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
