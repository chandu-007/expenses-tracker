package app.expenses.core.domain

/** One logged field change of an entry: when (epoch ms), which field, and the value it took (null = cleared). */
data class LoggedChange(val atMillis: Long, val field: String, val value: String?)

enum class RevisionKind { CREATED, EDITED, DELETED, RESTORED }

data class FieldChange(val field: String, val before: String?, val after: String?)

/**
 * One save of an entry. [before] and [after] are the whole stored state of the entry around it (field name to value), so any past
 * version can be put back. [before] is empty for the creation.
 */
data class Revision(val atMillis: Long, val kind: RevisionKind, val changes: List<FieldChange>, val before: Map<String, String?>, val after: Map<String, String?>)

object EntryHistory {
    /** Rebuilds the revisions from the change log, oldest first. Changes with the same time belong to one save. */
    fun revisions(ops: List<LoggedChange>): List<Revision> {
        val out = ArrayList<Revision>()
        var state = LinkedHashMap<String, String?>()
        for ((at, group) in ops.groupBy { it.atMillis }.entries.sortedBy { it.key }) {
            val before = LinkedHashMap(state)
            val changes = ArrayList<FieldChange>()
            for (op in group) {
                val was = state[op.field] ?: if (op.field == DELETED) "0" else null
                if (was != op.value) changes += FieldChange(op.field, state[op.field], op.value)
                state[op.field] = op.value
            }
            // The bank reference moves between an entry and the one that replaces it when its type is changed: that is bookkeeping, not something the person did to this entry,
            // so it is neither a revision of its own nor a reason to call a delete or a restore an edit.
            val said = changes.filter { it.field != SOURCE_REF }
            if (changes.isEmpty() || said.isEmpty() && before.isNotEmpty()) continue
            val kind = when {
                before.isEmpty() -> RevisionKind.CREATED
                said.size == 1 && said[0].field == DELETED -> if (said[0].after == "1") RevisionKind.DELETED else RevisionKind.RESTORED
                else -> RevisionKind.EDITED
            }
            out += Revision(at, kind, changes, before, LinkedHashMap(state))
        }
        return out
    }

    private const val DELETED = "deleted"
    private const val SOURCE_REF = "source_ref"
}

/** A change worded for a person: "Amount: ₹100.00 → ₹150.00". [before] is null when the field was empty. */
data class HistoryLine(val label: String, val before: String?, val after: String?)

/** Turns raw field changes into [HistoryLine]s. Fields a person does not think of as part of an entry (ids, source) are left out. */
class HistoryFormat(
    private val currency: Currency,
    private val accountName: (String) -> String,
    private val categoryName: (String) -> String,
    private val dateText: (Long) -> String,
) {
    fun lines(changes: List<FieldChange>): List<HistoryLine> = changes.mapNotNull { c ->
        val f = fun(label: String, show: (String) -> String) = HistoryLine(label, c.before?.takeIf { it.isNotEmpty() }?.let(show), c.after?.takeIf { it.isNotEmpty() }?.let(show))
        when (c.field) {
            "amount_minor" -> f("Amount") { Money(it.toLong(), currency).formatted() }
            "occurred_at" -> f("Date") { dateText(it.toLong()) }
            "account_id" -> f("Account", accountName)
            "counter_account_id" -> f("To account", accountName)
            "category_id" -> f("Category", categoryName)
            "merchant" -> f("Note or merchant") { it }
            "note" -> f("Note") { it }
            "splits" -> f("Categories") { splitsText(it) }
            "shares" -> f("Shared with") { "others" }
            "tags" -> f("Tags") { it.split(',').joinToString(", ") }
            else -> null
        }
    }

    private fun splitsText(s: String) = s.split(',').filter { it.isNotEmpty() }.joinToString(", ") { part ->
        val i = part.lastIndexOf(':')
        categoryName(unesc(part.substring(0, i))) + " " + Money(part.substring(i + 1).toLong(), currency).formatted()
    }

    private fun unesc(s: String) = s.replace("%3A", ":").replace("%2C", ",").replace("%25", "%")
}

/** Puts an older stored version of an entry back into a draft, so it goes through the same rules as any edit. */
object HistoryRestore {
    /** [state] is a revision's stored fields. Anything the form does not edit (note, source, shares) is not touched. */
    fun apply(draft: EntryDraft, state: Map<String, String?>, currency: Currency): EntryDraft {
        val splits = state["splits"].orEmpty().split(',').filter { it.isNotEmpty() }.map { part ->
            val i = part.lastIndexOf(':')
            SplitDraft(unesc(part.substring(0, i)), Money(part.substring(i + 1).toLong(), currency).toPlainString())
        }
        return draft.copy(
            amountText = state["amount_minor"]?.let { Money(it.toLong(), currency).toPlainString() } ?: draft.amountText,
            occurredAt = state["occurred_at"]?.toLong() ?: draft.occurredAt,
            accountId = state["account_id"] ?: draft.accountId,
            toAccountId = if (draft.kind == DraftKind.TRANSFER) state["counter_account_id"] ?: draft.toAccountId else draft.toAccountId,
            note = state["merchant"].orEmpty(),
            category = when (draft.kind) {
                DraftKind.INCOME -> state["category_id"].orEmpty()
                DraftKind.EXPENSE -> if (splits.size == 1) splits[0].category else ""
                else -> draft.category
            },
            splits = if (draft.kind == DraftKind.EXPENSE && splits.size > 1) splits else emptyList(),
            tags = if ("tags" in state) TagRules.split(state["tags"]) else emptyList(),
        )
    }

    private fun unesc(s: String) = s.replace("%3A", ":").replace("%2C", ",").replace("%25", "%")
}
