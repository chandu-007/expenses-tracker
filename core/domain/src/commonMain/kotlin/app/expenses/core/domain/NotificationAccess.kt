package app.expenses.core.domain

/**
 * Reads Android's `enabled_notification_listeners` setting: the colon-separated list of listener services the person has given notification access to.
 * Each entry is a flattened component name, `package/class` or `package/.RelativeClass` (the same text `ComponentName.flattenToString` and `flattenToShortString` make);
 * the system also accepts a bare package name. Pure, so the formats can be tested without a phone.
 */
object NotificationAccess {
    /** True when [flat] lists any listener of [packageName]. A null, blank or malformed list, or a package that only shares a prefix, is false. */
    fun granted(flat: String?, packageName: String): Boolean {
        if (flat.isNullOrBlank() || packageName.isEmpty()) return false
        return flat.split(':').any { entry ->
            val e = entry.trim()
            val slash = e.indexOf('/')
            // "pkg/class" needs a class after the slash (what ComponentName.unflattenFromString demands); a bare "pkg" is a whole-package entry
            if (slash < 0) e == packageName else slash > 0 && slash < e.length - 1 && e.substring(0, slash) == packageName
        }
    }
}
