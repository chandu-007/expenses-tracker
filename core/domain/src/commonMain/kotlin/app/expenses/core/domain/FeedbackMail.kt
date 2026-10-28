package app.expenses.core.domain

/**
 * The email the Send feedback row opens in the person's mail app. It names the app version, the system and the device model so a report can be placed,
 * and nothing else: no ledger data, no account, no id. The person reads and edits it in their own mail app before sending; nothing leaves the phone by itself.
 */
object FeedbackMail {
    const val ADDRESS = "nelluri.c@gmail.com"

    /** "Android" for an [os] that starts with Android, otherwise "iPhone" (the only other platform). */
    fun platformName(os: String): String = if (os.trim().startsWith("Android", ignoreCase = true)) "Android" else "iPhone"

    fun subject(version: String, os: String): String = "Expenses feedback (version $version, ${platformName(os)})"

    fun body(version: String, os: String, model: String): String =
        "Describe what happened:\n\n\n\n---\nApp version: $version\nSystem: $os\nDevice: $model\n"

    /** A `mailto:` address with the subject and body percent-encoded (spaces as %20, line breaks as %0A), ready for the system to open. */
    fun build(version: String, os: String, model: String): String =
        "mailto:$ADDRESS?subject=${encode(subject(version, os))}&body=${encode(body(version, os, model))}"

    /**
     * A `mailto:` address for a message the app did not understand: [report] is the already redacted text the person has just read on screen (see `MessageReport`). Nothing is sent
     * until the person sends it from their own mail app.
     */
    fun buildReport(version: String, os: String, report: String): String =
        "mailto:$ADDRESS?subject=${encode("Expenses: a bank message to fix (version $version, ${platformName(os)})")}&body=${encode(report)}"

    /** RFC 3986 percent-encoding of the UTF-8 bytes: letters, digits and `-._~` stay, everything else becomes %XX. */
    internal fun encode(text: String): String {
        val hex = "0123456789ABCDEF"
        val sb = StringBuilder()
        for (b in text.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val plain = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
            if (plain) sb.append(c.toChar()) else sb.append('%').append(hex[c shr 4]).append(hex[c and 15])
        }
        return sb.toString()
    }
}
