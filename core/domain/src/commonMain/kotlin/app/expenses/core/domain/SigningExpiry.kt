package app.expenses.core.domain

/**
 * When a test build signed with a free Apple ID stops opening, and the sentence that says so. Pure: the platform supplies the expiry (iPhone: the app's embedded provisioning profile) and the clock.
 */
object SigningExpiry {
    const val DAY_MS = 86_400_000L

    /** The notice is shown when this many days or fewer remain. */
    const val WARN_DAYS = 2

    /** Whole days left, rounded up: 0 when the expiry is up to a day before [now] (exclusive), negative from a day before, 1 for anything up to 24 hours ahead. */
    fun daysLeft(expiresAt: Long, now: Long): Long = (expiresAt - now + DAY_MS - 1).floorDiv(DAY_MS)

    /** The sentence to show, or null when the expiry is unknown or more than [WARN_DAYS] days away. */
    fun notice(expiresAt: Long?, now: Long): String? {
        if (expiresAt == null) return null
        val remaining = expiresAt - now
        if (remaining > WARN_DAYS * DAY_MS) return null
        val days = daysLeft(expiresAt, now)
        val what = when {
            remaining <= 0 -> "This test build has expired and may stop opening."
            days <= 1 -> "This test build stops opening today."
            else -> "This test build stops opening in $days days."
        }
        return "$what Reinstall it from the Mac (see docs/IOS.md) and make a backup first."
    }
}

/** Reads the expiry out of a provisioning profile (`embedded.mobileprovision`): a signed blob with an XML property list inside. */
object ProvisionProfile {
    /** The `ExpirationDate` of the profile text as epoch milliseconds, or null when it is missing or not an ISO UTC date (`2026-11-03T10:11:12Z`). */
    fun expiresAtMillis(text: String): Long? {
        val start = text.indexOf("<?xml").takeIf { it >= 0 } ?: return null
        val end = text.indexOf("</plist>", start).takeIf { it >= 0 } ?: return null
        val plist = text.substring(start, end)
        val key = plist.indexOf("<key>ExpirationDate</key>").takeIf { it >= 0 } ?: return null
        val open = plist.indexOf("<date>", key).takeIf { it >= 0 } ?: return null
        val close = plist.indexOf("</date>", open).takeIf { it >= 0 } ?: return null
        return parseIsoUtc(plist.substring(open + 6, close).trim())
    }

    private fun parseIsoUtc(s: String): Long? {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})Z""").matchEntire(s) ?: return null
        val n = m.destructured.toList().map { it.toInt() }
        val y = n[0]; val mo = n[1]; val d = n[2]; val h = n[3]; val mi = n[4]; val se = n[5]
        if (mo !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || se > 59) return null
        // days from civil (Howard Hinnant), time-zone free
        val yy = if (mo <= 2) y - 1 else y
        val era = yy.floorDiv(400)
        val yoe = yy - era * 400
        val doy = (153 * (mo + (if (mo > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era.toLong() * 146_097 + doe - 719_468
        return ((days * 24 + h) * 60 + mi) * 60_000 + se * 1000L
    }
}
