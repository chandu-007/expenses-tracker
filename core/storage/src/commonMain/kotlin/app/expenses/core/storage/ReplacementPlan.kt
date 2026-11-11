package app.expenses.core.storage

import app.expenses.core.storage.db.Entry
import app.expenses.core.storage.db.SchemaQueries

/**
 * What the records of replaced entries (this phone's [ReplacedEntries] and the `replaced` list of the backup) decide for ONE restore. It only reads: [BackupStore] shows it in the preview
 * and applies the very same plan, so what the person is told is what happens. See docs/BACKUP_FORMAT.md ("Changing an entry's type").
 *
 * A record says "entry A was replaced by entry B" (an expense made a transfer, say). Records chain (A by B, B by C) and stay when the last entry is deleted. The rules:
 *  - [skipped]: an entry of the file that is not on this phone and whose id has a record is not added, whatever the order of the restores and whether its replacement is here or not (the replacement
 *    may have been deleted since: the original must not come back either). A refund of such an entry goes with it (it would refuse to be added without its expense).
 *  - [removed]: an entry that IS live on this phone, has a record in the file, and has something live after it in its chain (on this phone, or arriving in this file) is taken away (an ordinary
 *    delete), so the amount is not counted twice. Never when nothing live follows it (a chain that ends in a deletion, or in something this restore does not add), and never one the person has
 *    built on since: with refunds, "I expect this back", paying a bill, or posted by a repeating item (see [tied]). The bank reference of a removed entry passes to the arriving replacement
 *    ([releases]), as it does when a type change is made.
 *  - [held]: what would arrive after an entry that must stay (it is [tied]) stays out too, so the two are not both live; so does a replacement of an entry that this phone has already seen
 *    replaced by a different entry (a branch: the type changed again on another phone, or after an Undo). Counted as already here.
 *  - [adopt]: the records of the file that this phone keeps, those whose original is not live here once the restore is done. A record of an original that stays live is never kept, so the
 *    next backup cannot say that a live entry was replaced. A record of the file about an entry the same file lists as live contradicts the file (the app never writes one: it leaves out the
 *    record of a live entry) and is ignored, so a hand-made or damaged file cannot make its own live entries disappear.
 */
internal class ReplacementPlan(
    val skipped: Set<String>,
    val held: Set<String>,
    val removed: List<String>,
    /** The arriving entry's id to the id of the removed entry that holds its bank reference now. */
    val releases: Map<String, String>,
    val adopt: List<BackupReplaced>,
    /** Every record known for this restore (this phone's win over the file's): entry id to the entry that replaced it. */
    val replacedBy: Map<String, String>,
) {
    /** The first entry after [id] in its chain that is live now (after the restore has removed and added), or null. */
    fun firstLiveAfter(id: String, live: (String) -> Boolean): String? {
        var n = replacedBy[id]
        val seen = HashSet<String>()
        while (n != null && seen.add(n)) { if (live(n)) return n; n = replacedBy[n] }
        return null
    }

    companion object {
        /** No record on this phone and none in the file: a restore decides exactly as it did before records existed. */
        val NONE = ReplacementPlan(emptySet(), emptySet(), emptyList(), emptyMap(), emptyList(), emptyMap())

        fun of(q: SchemaQueries, data: BackupData): ReplacementPlan {
            val here = q.allReplaced().executeAsList()
            if (here.isEmpty() && data.replaced.isEmpty()) return NONE
            val fileEntry = HashMap<String, BackupEntry>()
            for (e in data.entries) fileEntry.putIfAbsent(e.id, e)
            val fromFile = LinkedHashMap<String, BackupReplaced>()
            for (r in data.replaced) if (r.entryId.isNotEmpty() && r.replacedBy.isNotEmpty() && r.entryId != r.replacedBy && r.entryId !in fileEntry) fromFile.putIfAbsent(r.entryId, r)
            val replacedBy = HashMap<String, String>(fromFile.size + here.size)
            for ((id, r) in fromFile) replacedBy[id] = r.replacedBy
            for (r in here) replacedBy[r.entry_id] = r.replaced_by // what this phone already knows wins over the file

            val rows = HashMap<String, Entry?>()
            fun row(id: String): Entry? = if (rows.containsKey(id)) rows[id] else q.entryById(id).executeAsOneOrNull().also { rows[id] = it }
            fun live(id: String) = row(id)?.deleted == 0L

            val skipped = HashSet<String>()
            for (e in data.entries) if (e.id in replacedBy && row(e.id) == null) skipped += e.id

            // entries live here that the file says were replaced: each with what follows it in its chain (the last one is never replaced itself). A chain that loops is left alone.
            class Candidate(val id: String, val after: List<String>) { val last get() = after.last() }
            val candidates = ArrayList<Candidate>()
            for (id in fromFile.keys) if (live(id)) {
                val after = ArrayList<String>(); val seen = hashSetOf(id); var n = replacedBy[id]; var loops = false
                while (n != null) { if (!seen.add(n)) { loops = true; break }; after += n; n = replacedBy[n] }
                if (!loops) candidates += Candidate(id, after)
            }
            val tied = candidates.associate { it.id to tied(q, it.id) }
            val held = HashSet<String>()
            for (c in candidates) if (tied.getValue(c.id)) held += c.last
            // an original this phone has already seen replaced by one entry is not replaced a second time: the file's replacement of it by another entry is a branch (a type change made again on
            // another phone, or after an Undo) and stays out, so the two are not both live
            for (r in here) fromFile[r.entry_id]?.let { f -> if (f.replacedBy != r.replaced_by) held += f.replacedBy }
            val lastOf = candidates.associate { it.id to it.last }

            val releases = HashMap<String, String>()
            // whether the last entry of a chain would really be added: it is in the file, not here, not held back, and its bank reference is free or held by an entry of its own chain that is removed for it
            fun arrives(last: String): Boolean {
                val e = fileEntry[last] ?: return false
                if (row(last) != null || last in held) return false
                val ref = e.sourceRef ?: return true
                val holder = q.entryBySourceRef(e.source, ref).executeAsOneOrNull() ?: return true
                if (holder.deleted == 0L && lastOf[holder.id] == last && tied[holder.id] == false) { releases[last] = holder.id; return true }
                return false
            }
            val removed = ArrayList<String>()
            for (c in candidates) if (tied.getValue(c.id).not() && (c.after.any(::live) || arrives(c.last))) removed += c.id
            val removedSet = removed.toHashSet()

            // a refund of an expense that is not coming (replaced) or is going (removed) would be refused for want of its expense: it goes with it
            val refunds = data.entries.filter { it.kind == "REFUND" && it.refundsEntry != null && row(it.id) == null }
            for (r in refunds) {
                val target = r.refundsEntry ?: continue
                if (target in held) held += r.id else if (target in skipped || target in removedSet) skipped += r.id
            }

            val adopt = fromFile.values.filter { r -> row(r.entryId).let { it == null || it.deleted != 0L } || r.entryId in removedSet }
            return ReplacementPlan(skipped, held, removed, releases, adopt, replacedBy)
        }

        /** Whether the person has built on a live entry since it came back: refunds of it, money expected back, a bill it pays, a repeating item that posted it. Such an entry is not removed. */
        private fun tied(q: SchemaQueries, id: String): Boolean =
            q.hasLiveRefunds(id).executeAsOne() || q.expectOf(id).executeAsOneOrNull() != null || q.entryPaysBill(id).executeAsOne() || q.entryFromRepeating(id).executeAsOne()
    }
}
