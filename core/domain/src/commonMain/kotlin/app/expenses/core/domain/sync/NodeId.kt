package app.expenses.core.domain.sync

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * This install's node id: the last part of every [Hlc] it issues, made once per install and kept where the platform keeps it.
 *
 * What is stored is read tolerantly: whitespace around it (a trailing newline) is ignored, and a usable stored id is never changed. Anything that cannot be a node id
 * (an empty file, which a phone that loses power after the file is created and before its bytes reach the disk leaves behind, or garbage) is replaced by a new id
 * instead of stopping the app at [HlcClock] on every launch. That is safe: the id only keeps one device's timestamps apart from another's (it breaks ties and keeps the
 * op log's keys unique; nothing looks a row up by it or compares it with stored ones), so a new random id orders exactly as well, and the rows stamped with the lost
 * one stay valid as they are.
 */
object NodeId {
    private const val MAX_LENGTH = 64

    /** The node id held in [stored], or null when there is none or it is not usable (empty, too long, or anything but ASCII letters and digits). */
    fun parse(stored: String?): String? =
        stored?.trim()?.takeIf { s -> s.length in 1..MAX_LENGTH && s.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' } }

    /** A new random id: the first 8 hex digits of a random UUID, as every version of the app has made them. */
    @OptIn(ExperimentalUuidApi::class)
    fun generate(): String = Uuid.random().toString().take(8)

    /** The stored id ([read] returns null when nothing is stored); when none is usable, a new one, which is given to [write] before it is returned. */
    fun loadOrCreate(read: () -> String?, write: (String) -> Unit): String =
        parse(read()) ?: generate().also(write)
}
