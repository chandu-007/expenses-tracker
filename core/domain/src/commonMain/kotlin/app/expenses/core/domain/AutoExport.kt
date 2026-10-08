package app.expenses.core.domain

enum class AutoExportFrequency(val label: String, val days: Int) { OFF("Off", 0), DAILY("Every day", 1), WEEKLY("Every week", 7), MONTHLY("Every month", 30) }

/** When an automatic export to the user's own folder is due, and which of its old files to remove. Pure, so the rules are tested on their own. */
object AutoExportRules {
    private const val DAY = 86_400_000L
    private const val SLACK = 2 * 3_600_000L // the system runs background work when it likes: a run a little early must not wait a whole extra day

    /** Never before the first run is made, never when off, and never because the phone's clock went backwards. */
    fun isDue(frequency: AutoExportFrequency, lastAtMillis: Long?, nowMillis: Long): Boolean = when {
        frequency == AutoExportFrequency.OFF -> false
        lastAtMillis == null -> true
        nowMillis < lastAtMillis -> false
        else -> nowMillis - lastAtMillis >= frequency.days * DAY - SLACK
    }

    /**
     * Which of [names] to delete so that only the newest [keep] automatic files named `prefix-YYYY-MM-DD.ext` (optionally with the " (1)"
     * the system adds when a name is taken) remain. Names that do not match that pattern are never returned: the user's own files in the
     * same folder are not ours to delete.
     */
    fun filesToDelete(names: List<String>, prefix: String, extension: String, keep: Int): List<String> {
        val pattern = Regex("^" + Regex.escape(prefix) + "-(\\d{4}-\\d{2}-\\d{2})( \\(\\d+\\))?\\." + Regex.escape(extension) + "$")
        val ours = names.mapNotNull { n -> pattern.find(n)?.let { n to it.groupValues[1] } }
        return ours.sortedWith(compareByDescending<Pair<String, String>> { it.second }.thenByDescending { it.first }).drop(keep.coerceAtLeast(1)).map { it.first }
    }
}
