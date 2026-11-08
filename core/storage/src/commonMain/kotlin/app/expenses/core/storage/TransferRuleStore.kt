package app.expenses.core.storage

import app.expenses.core.domain.TransferLearning

/**
 * What the person taught by making waiting texts transfers ([TransferLearning]), kept as device-local settings: a few short values, one per kind of text, never in a backup and never holding
 * a word of a text (a key is a payee's key or a closed list of transfer words, the value an account id and two counts).
 */
fun TemplateStore.transferRule(key: String): TransferLearning.Rule? = TransferLearning.decode(setting(key))

/** The first of [keys] (most specific first) that holds a rule, with that key. */
fun TemplateStore.firstTransferRule(keys: List<String>): Pair<String, TransferLearning.Rule>? = keys.firstNotNullOfOrNull { k -> transferRule(k)?.let { k to it } }

/** Stores [rule] under [key], or forgets the key when [rule] is null. Unchanged values are not written. */
fun TemplateStore.putTransferRule(key: String, rule: TransferLearning.Rule?) {
    if (rule == null) removeSetting(key) else putSettingIfChanged(key, TransferLearning.encode(rule))
}
