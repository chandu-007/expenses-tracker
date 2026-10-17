package app.expenses.facade

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/**
 * Opens (creating it the first time) the ledger in the app's own storage and returns the object the screens talk to. The file lives in the app's private
 * Application Support area; iOS encrypts it at rest while the phone is locked (Data Protection). There is no separate database key yet.
 */
fun openAppCore(): AppCore {
    val defaults = NSUserDefaults.standardUserDefaults
    val node = defaults.stringForKey("node_id") ?: NSUUID().UUIDString.take(8).also { defaults.setObject(it, forKey = "node_id") }
    return AppCore(NativeSqliteDriver(ExpensesDb.Schema, "expenses.db"), node, { (NSDate().timeIntervalSince1970 * 1000).toLong() })
}

