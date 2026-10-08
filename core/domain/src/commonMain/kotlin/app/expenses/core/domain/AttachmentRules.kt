package app.expenses.core.domain

/** Limits and naming for photos and files attached to an entry (a receipt, a bill, a warranty). */
object AttachmentRules {
    const val MAX_BYTES = 15L * 1024 * 1024
    const val MAX_PER_ENTRY = 10
    const val MAX_NAME = 80

    /** A name safe to store and show: no folders, no control characters, no leading dots, at most [MAX_NAME] characters, never empty. */
    fun safeName(raw: String?): String {
        val last = (raw ?: "").substringAfterLast('/').substringAfterLast('\\')
        val clean = last.filter { it.code >= 0x20 && it.code != 0x7F }.trim().trimStart('.').trim()
        if (clean.isEmpty()) return "attachment"
        if (clean.length <= MAX_NAME) return clean
        val ext = clean.substringAfterLast('.', "").takeIf { it.length in 1..8 }?.let { ".$it" }.orEmpty()
        return clean.take(MAX_NAME - ext.length).trimEnd() + ext
    }

    /** Why another file cannot be added, or null. [sizeBytes] is the size of the new file when it is known. */
    fun checkAdd(existing: Int, sizeBytes: Long?): String? = when {
        existing >= MAX_PER_ENTRY -> "An entry can have up to $MAX_PER_ENTRY attachments."
        sizeBytes != null && sizeBytes > MAX_BYTES -> "That file is over ${MAX_BYTES / 1024 / 1024} MB, which is more than a receipt needs."
        sizeBytes != null && sizeBytes <= 0 -> "That file is empty."
        else -> null
    }

    fun sizeText(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes * 10 / (1024 * 1024) / 10}.${bytes * 10 / (1024 * 1024) % 10} MB"
    }
}
