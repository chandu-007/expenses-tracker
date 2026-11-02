package app.expenses.core.storage

import app.expenses.core.domain.SkippedHint
import app.expenses.core.domain.SkippedSenders

/**
 * The redacted outline kept per skipped sender, in the settings table under `skipped_hint:<ROOT>` (see [SkippedSenders]). Device-local: the keys are not on [BackupStore.SETTINGS], so no backup
 * carries them. Call inside the write lock.
 */
fun TemplateStore.skippedHints(): List<SkippedHint> = SkippedSenders.decodeAll(settingsWithPrefix(SkippedSenders.KEY_PREFIX))

/** Keeps [hint] as the one for its sender (the newest replaces an older one) and drops the oldest hints beyond [SkippedSenders.MAX_HINTS]. */
fun TemplateStore.recordSkippedHint(hint: SkippedHint) {
    putSetting(SkippedSenders.key(hint.root), SkippedSenders.encode(hint))
    for (root in SkippedSenders.toDrop(skippedHints(), keep = hint.root)) removeSetting(SkippedSenders.key(root))
}

/** Forgets the hint of [root] (the person trusted it). */
fun TemplateStore.clearSkippedHint(root: String) = removeSetting(SkippedSenders.key(root))
