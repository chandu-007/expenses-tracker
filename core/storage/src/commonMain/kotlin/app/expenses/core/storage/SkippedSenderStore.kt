package app.expenses.core.storage

import app.expenses.core.domain.SkippedCounter
import app.expenses.core.domain.SkippedSenders

/**
 * The header-only counters of the sender names that were skipped, in the settings table under `skipped_sender:<ROOT>` (see [SkippedSenders]). Device-local: the keys are not on
 * [BackupStore.SETTINGS], so no backup carries them. Nothing of a message is ever passed in here: only the sender's name and its category suffix. Call inside the write lock.
 */
fun TemplateStore.skippedCounters(): List<SkippedCounter> = SkippedSenders.decodeAll(settingsWithPrefix(SkippedSenders.KEY_PREFIX))

/** Counts one more text from the header [root] (category [suffix], null for none) at [now], and drops the quietest counters beyond [SkippedSenders.MAX_ROOTS]. */
fun TemplateStore.countSkippedSender(root: String, suffix: Char?, now: Long) {
    val key = SkippedSenders.key(root)
    val next = SkippedSenders.counted(setting(key)?.let { SkippedSenders.decode(key, it) }, root, suffix, now)
    putSetting(key, SkippedSenders.encode(next))
    // at most MAX_ROOTS + 1 small rows are read to keep to the cap
    for (r in SkippedSenders.toDrop(skippedCounters(), keep = next.root)) removeSetting(SkippedSenders.key(r))
}

/** Forgets the counter of [root] (the person trusted it). */
fun TemplateStore.clearSkippedSender(root: String) = removeSetting(SkippedSenders.key(root))
