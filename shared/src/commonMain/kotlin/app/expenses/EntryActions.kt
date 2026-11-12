package app.expenses

import app.expenses.core.parsers.PayeeGuess
import app.expenses.core.domain.AmountMask
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.DraftResult
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.HistoryFormat
import app.expenses.core.domain.HistoryLine
import app.expenses.core.domain.HistoryRestore
import app.expenses.core.domain.RevisionKind
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.Money
import app.expenses.core.domain.SplitDraft
import app.expenses.core.domain.TagRules
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.OriginKind
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val NAMES = mapOf("GPAY" to "Google Pay", "PHONEPE" to "PhonePe", "PAYTM" to "Paytm", "GMAIL" to "Gmail", "OUTLOOK" to "Outlook", "YAHOO" to "Yahoo Mail", "BHIM" to "BHIM", "AMAZONPAY" to "Amazon Pay")

private const val LAST_CATEGORY = "last_category"
private const val LAST_ACCOUNT = "last_account"

/** The income category used last, remembered apart from [LAST_CATEGORY] so a new income starts on an income category. */
internal const val LAST_INCOME_CATEGORY = "last_income_category"

/** An entry's history for the screen. */
data class HistoryUi(val entryId: String, val title: String, val revisions: List<HistoryRevisionUi>)
data class HistoryRevisionUi(val index: Int, val whenText: String, val what: String, val lines: List<HistoryLine>, val canRestore: Boolean)

/** Where an entry was read from, for its "Where this came from" section: [kindLabel] ("Text message from HDFCBK"), the original [text], and when it arrived ([whenText] is empty when not known). */
data class OriginUi(val kindLabel: String, val text: String, val whenText: String)

/** A category the editor offers for an entry that has none: its name, why ([note]: "you chose it twice for this payer"), and whether it is the person's own choice ([sure]) or a guess. */
data class SuggestedCategoryUi(val categoryId: String, val name: String, val note: String, val sure: Boolean)

/** What the edit screen may do with an entry. */
sealed interface Editable {
    /**
     * An entry the form can edit (or, for a refund, create). [refundOf] is the title of the refunded expense; [canRefund] offers a refund from this expense;
     * [canChangeKind] offers the chips that change an expense, income or transfer into one of the others (see [EntryActions.changeKind]).
     */
    data class Form(val draft: EntryDraft, val title: String, val refundOf: String? = null, val canRefund: Boolean = false, val origin: OriginUi? = null, val canChangeKind: Boolean = false, val suggestion: SuggestedCategoryUi? = null) : Editable

    /** Shown read-only, with the reason it cannot be edited here yet, so nothing is changed by accident. */
    data class ReadOnly(val id: String, val title: String, val reason: String, val origin: OriginUi? = null) : Editable
}

/**
 * The few things users do to the ledger in one step. Shared by the screens and the widget so both behave the same.
 * Call only from [AppContainer.write], which serialises writers.
 */
@OptIn(ExperimentalUuidApi::class)
class EntryActions(private val ok: AppContainer.Opened.Ok, nowMillis: () -> Long = ::nowMillis) {
    private val now: () -> Long = nowMillis

    /** An expense that was just recorded: its id for undo, and the text to show the user. */
    data class Added(val entryId: String, val text: String, val name: String = "", val amount: String = "")

    /** Records an expense and remembers its category and account as the next defaults. Returns its id. */
    fun recordExpense(amount: Money, category: String, note: String?, accountId: String, occurredAt: Long = now(), id: String = Uuid.random().toString()): String {
        ok.store.save(
            Expense(EntryId(id), occurredAt, AccountId(accountId), amount, listOf(CategorySplit(CategoryId(category), amount))),
            EntryDetails(merchant = note, tripTag = true), // a quick add is a new expense by its own date: tagged for the trip it falls in (Travel mode)
        )
        ok.templates.putSettingIfChanged(LAST_CATEGORY, category)
        ok.templates.putSettingIfChanged(LAST_ACCOUNT, accountId)
        return id
    }

    /** Records the expense of a saved quick entry. Null if it does not exist or has no fixed amount. With [maskAmount] (Hide amounts is on) the confirmation text has dots where the amount is. */
    fun quickAdd(templateId: String, occurredAt: Long = now(), id: String = Uuid.random().toString(), maskAmount: Boolean = false): Added? {
        val t = ok.templates.list().firstOrNull { it.id == templateId } ?: return null
        val amount = t.amount ?: return null
        val id = recordExpense(amount, t.category.value, t.merchant ?: t.name, t.account.value, occurredAt, id)
        ok.templates.recordUse(templateId)
        val shown = amount.formatted().let { if (maskAmount) AmountMask.mask(it) else it }
        return Added(id, "Added ${t.name} $shown", t.name, shown)
    }

    /**
     * Takes back an entry. With [maxAgeMillis], only if it was recorded that recently, so a stale Undo button cannot
     * delete something the user has long since accepted. Returns whether anything was undone.
     */
    fun undo(entryId: String, maxAgeMillis: Long? = null): Boolean {
        val row = ok.db.schemaQueries.entryById(entryId).executeAsOneOrNull() ?: return false
        if (row.deleted == 1L) return false
        if (maxAgeMillis != null && now() - row.occurred_at > maxAgeMillis) return false
        ok.store.delete(EntryId(entryId))
        return true
    }

    /** Decides how an entry can be edited. Null if it does not exist or is deleted. Carries where the entry was read from, when it was read from something. */
    fun editable(entryId: String): Editable? {
        val e = editableCore(entryId) ?: return null
        val o = originUi(entryId) ?: return e
        return when (e) { is Editable.Form -> e.copy(origin = o); is Editable.ReadOnly -> e.copy(origin = o) }
    }

    /**
     * [e] with a category offered when it is an income that has none (a credit added from To review without one): the same advice To review gives for money in (the person's rules, what they chose
     * or filed for this payer, the words of the text), looked up once when the editor opens. Nothing is changed until they tap it.
     */
    fun withSuggestion(e: Editable?): Editable? {
        val f = e as? Editable.Form ?: return e
        val d = f.draft
        if (d.id == null || d.kind != DraftKind.INCOME || d.category.isNotEmpty()) return e
        val cats = ok.categories.list().filter { !it.hidden && it.kind.forIncome }
        val originText = ok.origins.get(d.id!!)?.text
        val label = d.note.trim().ifEmpty { f.title }
        val payer = originText?.let { PayeeGuess.of(it) } ?: label.takeIf { it.isNotEmpty() }
        val learned = ok.captureRules.learnedRows()
        val aliases = ok.captureRules.aliases()
        val words = app.expenses.core.domain.SimilarEntries.searchWords(label)
        val candidates = if (words.isEmpty()) emptyList() else ok.db.schemaQueries.similarCandidates("INCOME", words.joinToString(" OR ") { app.expenses.core.storage.ftsPhrase(it) }).executeAsList()
            .filter { it.id != d.id }.map { app.expenses.core.domain.SimilarCandidate(it.id, it.label, it.category_id.orEmpty(), it.amount_minor, it.occurred_at, it.currency) }
        val amount = app.expenses.core.domain.parseAmount(d.amountText, BaseCurrency.current)?.minorUnits ?: 0L
        val advice = app.expenses.core.parsers.CaptureCategory.adviseIncome(
            payer, listOfNotNull(label, originText).joinToString(" "), ok.captureRules.rules(),
            app.expenses.core.parsers.CaptureCategory.Taught(learned.associate { it.key to it.categoryId }, learned.associate { it.key to it.uses }, aliases),
            cats.map { it.id }.toSet(), cats.filter { it.parentId == null }.associate { it.name.lowercase() to it.id }, app.expenses.core.domain.SimilarEntries.profile(label, candidates, aliases),
            band = app.expenses.core.domain.AmountBands.of(label, amount, BaseCurrency.current.code, candidates, aliases),
        ) ?: return e
        val name = cats.firstOrNull { it.id == advice.categoryId }?.name ?: return e
        return f.copy(suggestion = SuggestedCategoryUi(advice.categoryId, name, advice.note, advice.certainty == app.expenses.core.domain.Certainty.SURE))
    }

    /** The origin of [entryId] as the screen shows it, or null for an entry typed by hand. */
    fun originUi(entryId: String): OriginUi? {
        val o = ok.origins.get(entryId) ?: return null
        val who = o.sender?.trim()?.takeIf { it.isNotEmpty() }
        val label = when (o.kind) {
            OriginKind.SMS -> if (who != null) "Text message from $who" else "Text message"
            OriginKind.NOTIFICATION -> if (who != null) "Notification from ${friendlyName(who)}" else "Notification"
            OriginKind.EMAIL -> if (who != null) "Email notification from ${friendlyName(who)}" else "Email notification"
            OriginKind.STATEMENT -> "Statement line"
        }
        return OriginUi(label, o.text, o.receivedAt?.let { app.expenses.ui.dateTimeLabel(it) }.orEmpty())
    }

    /** "GMAIL" is shown as "Gmail", "PHONEPE" as "Phonepe": the capture side keeps sender names in capitals. A name with lower case in it is left as it is. */
    private fun friendlyName(name: String) = if (name.any { it.isLowerCase() }) name else NAMES[name.uppercase()] ?: name.lowercase().replaceFirstChar { it.uppercase() }

    private fun editableCore(entryId: String): Editable? {
        val q = ok.db.schemaQueries
        val row = q.entryById(entryId).executeAsOneOrNull()?.takeIf { it.deleted == 0L } ?: return null
        val title = row.merchant ?: row.note ?: row.kind.lowercase().replaceFirstChar { it.uppercase() }
        fun readOnly(reason: String) = Editable.ReadOnly(entryId, title, reason)
        if (row.currency != BaseCurrency.current.code) return readOnly("Only entries in ${BaseCurrency.current.code} can be edited here for now.")
        val base = EntryDraft(entryId, DraftKind.EXPENSE, Money(row.amount_minor, BaseCurrency.current).toPlainString(), "", emptyList(), row.account_id.orEmpty(), "", row.merchant.orEmpty(), row.occurred_at, tags = ok.store.tagsOf(EntryId(entryId)))
        return when (row.kind) {
            "EXPENSE" -> {
                val splits = q.splitsOf(entryId).executeAsList()
                when {
                    q.sharesOf(entryId).executeAsList().isNotEmpty() -> readOnly("This expense is shared with other people, which can't be edited here yet.")
                    row.original_amount_minor != null -> readOnly("This expense was paid in ${row.original_currency} (${Money(row.original_amount_minor!!, app.expenses.core.domain.CurrencyChoices.byCode(row.original_currency)).formatted()}) and entered at the rate you chose. Changing it is not possible yet: delete it and add it again to correct it.")
                    q.hasLiveRefunds(entryId).executeAsOne() -> readOnly("This expense has refunds. Delete the refunds first to edit it.")
                    else -> Editable.Form(
                        ok.expects.get(entryId)?.takeIf { !it.closed }.let { x ->
                        base.copy(
                            category = if (splits.size == 1) splits[0].category_id else "",
                            splits = if (splits.size > 1) splits.map { SplitDraft(it.category_id, Money(it.amount_minor, BaseCurrency.current).toPlainString()) } else emptyList(),
                            expectBack = x != null, expectFrom = x?.from.orEmpty(), expectPersonId = x?.personId.orEmpty(),
                            expectAmount = x?.expectedMinor?.takeIf { it != row.amount_minor }?.let { Money(it, BaseCurrency.current).toPlainString() }.orEmpty(),
                        ) },
                        title = "Edit expense", canRefund = true, canChangeKind = true,
                    )
                }
            }
            "INCOME" -> Editable.Form(base.copy(kind = DraftKind.INCOME, category = row.category_id.orEmpty()), "Edit income", canChangeKind = true)
            "TRANSFER" -> Editable.Form(base.copy(kind = DraftKind.TRANSFER, toAccountId = row.counter_account_id.orEmpty()), "Edit transfer", canChangeKind = true)
                .takeIf { row.to_currency == null || row.to_currency == BaseCurrency.current.code && row.to_amount_minor == row.amount_minor }
                ?: readOnly("This transfer is between currencies, which can't be edited here yet.")
            "REFUND" -> {
                val target = row.refunds_entry!!
                val refundable = refundableMinor(target, exceptRefund = entryId)
                Editable.Form(base.copy(kind = DraftKind.REFUND, refundsEntry = target, refundableMinor = refundable), "Edit refund", refundOf = titleOf(target))
            }
            "SETTLEMENT" -> Editable.Form(base.copy(kind = DraftKind.LOAN, personId = row.person_id.orEmpty(), gave = row.direction == "PAID_TO_PERSON", note = row.note.orEmpty()), "Edit lent or borrowed")
            else -> readOnly("This kind of entry can't be edited here yet.")
        }
    }

    private fun titleOf(entryId: String): String {
        val r = ok.db.schemaQueries.entryById(entryId).executeAsOneOrNull() ?: return "an expense"
        return r.merchant ?: r.note ?: "an expense"
    }

    /** What is still refundable on an expense: its amount less the other live refunds (the one being edited is excluded). */
    private fun refundableMinor(expenseId: String, exceptRefund: String?): Long {
        val q = ok.db.schemaQueries
        val total = q.entryById(expenseId).executeAsOneOrNull()?.amount_minor ?: return 0L
        return total - q.refundedExcept(expenseId, exceptRefund.orEmpty()).executeAsOne()
    }

    /** The form for refunding [expenseId]: its account and what is still refundable. Null if it is not a live expense. */
    fun startRefund(expenseId: String): Editable.Form? {
        val row = ok.db.schemaQueries.entryById(expenseId).executeAsOneOrNull()?.takeIf { it.deleted == 0L && it.kind == "EXPENSE" && it.currency == BaseCurrency.current.code } ?: return null
        val remaining = refundableMinor(expenseId, null)
        return Editable.Form(
            EntryDraft(null, DraftKind.REFUND, "", "", emptyList(), row.account_id.orEmpty(), "", "", now(), expenseId, remaining),
            "Refund", refundOf = titleOf(expenseId),
        )
    }

    /**
     * Saves a draft as a new entry ([EntryDraft.id] null) or over an existing one. When editing, the note, source and source reference
     * already stored with the entry are kept, so editing an expense captured from an SMS does not lose where it came from.
     * Throws [IllegalArgumentException] with a reason the user can read.
     *
     * Travel mode: a NEW expense (or the refund of a tagged trip expense) is given the tag of the trip its own date falls in, in the same save, unless the person took that tag off in the form
     * ([EntryDraft.noTripTag]) or the caller says it is not spending on a trip ([tripTag] false: a bill paid). An entry that already exists is never tagged here.
     */
    fun save(draft: EntryDraft, teach: Boolean = true, tripTag: Boolean = true): String {
        val existing = draft.id?.let { ok.db.schemaQueries.entryById(it).executeAsOneOrNull()?.takeIf { r -> r.deleted == 0L } ?: throw IllegalArgumentException("This entry no longer exists") }
        val id = draft.id ?: Uuid.random().toString()
        val entry = when (val r = EntryDrafts.build(draft, EntryId(id), BaseCurrency.current)) {
            is DraftResult.Ok -> r.entry
            is DraftResult.Problem -> throw IllegalArgumentException(r.message)
        }
        // a lent or borrowed entry is titled by the person's name (so search finds it) and keeps what was typed as its note
        val loan = draft.kind == DraftKind.LOAN
        val text = if (loan) ok.people.get(draft.personId)?.name else draft.note.trim().ifEmpty { null }
        val typed = draft.note.trim().ifEmpty { null }
        // read before the save replaces it: the category this captured entry was in
        val categoryBefore = if (existing?.source == "sms") (if (existing.kind == "INCOME") existing.category_id else ok.db.schemaQueries.splitsOf(id).executeAsList().singleOrNull()?.category_id) else null
        ok.store.save(entry, if (existing != null) EntryDetails(merchant = text, note = if (loan) typed else existing.note, source = existing.source, sourceRef = existing.source_ref, tags = draft.tags) else EntryDetails(merchant = text, note = if (loan) typed else null, tags = draft.tags, tripTag = tripTag && !draft.noTripTag))
        // Correcting a captured entry later (in the editor, or by a bulk change of category), teaches the same lesson as choosing in the inbox: next time that payee starts there. Only a changed
        // category teaches (saving the same entry for another reason does not count as a choice), and the payee is read from the original text so that it is the key To review looks up (the
        // entry's own title may be a name the person gave it).
        // Money in is learned for the payer, under its own key (a payer is not a payee).
        val teachable = draft.kind == DraftKind.INCOME || draft.kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(draft)
        if (teach && existing?.source == "sms" && teachable && draft.category.isNotEmpty() && draft.category != categoryBefore) {
            ok.captureRules.learn(ok.origins.get(id)?.text?.let { PayeeGuess.of(it) } ?: text, draft.category, text, income = draft.kind == DraftKind.INCOME)
        }
        if (draft.kind == DraftKind.EXPENSE) {
            val category = if (EntryDrafts.isSplit(draft)) "" else draft.category
            if (category.isNotEmpty()) {
                ok.templates.putSettingIfChanged(LAST_CATEGORY, category)
                ok.templates.putSettingIfChanged(LAST_ACCOUNT, draft.accountId)
            }
        }
        if (draft.kind == DraftKind.INCOME && draft.category.isNotEmpty()) ok.templates.putSettingIfChanged(LAST_INCOME_CATEGORY, draft.category)
        if (draft.kind == DraftKind.EXPENSE) {
            // "I expect this back": kept beside the expense; switched off on an edit, it is forgotten (a new expense has nothing to forget)
            val expected = EntryDrafts.expectedMinor(draft, BaseCurrency.current)
            if (expected != null) ok.expects.set(id, draft.expectFrom, draft.expectPersonId, expected) else if (existing != null) ok.expects.clear(id)
        }
        return id
    }

    /**
     * Changes the type of an expense, income or transfer being edited ([draft] is the form as the person left it, its [EntryDraft.kind] the new type): the entry is replaced by one of the
     * new type that carries its date, account, title, note, tags, source, bank reference, original text and attachments, all or nothing (see [app.expenses.core.storage.KindChanges]).
     * Nothing is learned from it (no capture rule, no "last used" category or account). Returns what [undoKindChange] needs. Throws [IllegalArgumentException] with a reason the person can
     * read, changing nothing, when the entry cannot be changed here (shared, foreign currency, refunds, "I expect this back", tied to a bill or a repeating item) or the draft breaks a rule.
     */
    fun changeKind(draft: EntryDraft, fallbackNote: String? = null): app.expenses.core.storage.KindChanges.Changed {
        val oldId = draft.id ?: throw IllegalArgumentException("Only an entry that exists can change its type.")
        val form = editable(oldId) as? Editable.Form
        if (form == null || !form.canChangeKind) throw IllegalArgumentException("This entry's type can't be changed here.")
        if (draft.kind == form.draft.kind || draft.kind != DraftKind.EXPENSE && draft.kind != DraftKind.INCOME && draft.kind != DraftKind.TRANSFER) throw IllegalArgumentException("Choose another type.")
        val newId = Uuid.random().toString()
        val entry = when (val r = EntryDrafts.build(draft, EntryId(newId), BaseCurrency.current)) {
            is DraftResult.Ok -> r.entry
            is DraftResult.Problem -> throw IllegalArgumentException(r.message)
        }
        return ok.kindChanges.change(oldId, entry, draft.note.trim().ifEmpty { null }, draft.tags, fallbackNote)
    }

    /** Puts back what [changeKind] did: the original entry returns as it was and the changed one goes. */
    fun undoKindChange(changed: app.expenses.core.storage.KindChanges.Changed) = ok.kindChanges.undo(changed)

    /**
     * Money came back for an expense that was expected back: it is recorded as a refund of that expense into [accountId] (the refund rules apply: never more than
     * what is still refundable). A blank [amountText] is what is still expected. Returns the refund's id, so Undo can take it back. Throws [IllegalArgumentException]
     * with a reason the user can read.
     */
    fun gotItBack(entryId: String, accountId: String, amountText: String, occurredAt: Long = now()): String {
        val q = ok.db.schemaQueries
        val row = q.entryById(entryId).executeAsOneOrNull()?.takeIf { it.deleted == 0L && it.kind == "EXPENSE" } ?: throw IllegalArgumentException("That expense no longer exists.")
        val x = ok.expects.get(entryId)?.takeIf { !it.closed } ?: throw IllegalArgumentException("Nothing is expected back for that expense any more.")
        if (row.currency != BaseCurrency.current.code) throw IllegalArgumentException("This expense is in ${row.currency}, but your ledger is in ${BaseCurrency.current.code}.")
        val refunded = q.refundedExcept(entryId, "").executeAsOne()
        val left = app.expenses.core.domain.ExpectMath.remaining(x.expectedMinor, row.amount_minor, refunded)
        val text = amountText.trim().ifEmpty { Money(left, BaseCurrency.current).toPlainString() }
        val note = ("Got back" + (ok.people.get(x.personId.orEmpty())?.name ?: x.from)?.let { " from $it" }.orEmpty())
        return save(EntryDraft(null, DraftKind.REFUND, text, "", emptyList(), accountId, "", note, occurredAt, entryId, row.amount_minor - refunded))
    }

    /** What happened to an entry, newest first, worded for a person. Null if it never existed. */
    fun history(entryId: String): HistoryUi? {
        val revisions = ok.store.history(EntryId(entryId)).takeIf { it.isNotEmpty() } ?: return null
        val q = ok.db.schemaQueries
        val accounts = q.allAccounts().executeAsList().associate { it.id to it.name }
        val categories = q.allCategories().executeAsList().associate { it.id to it.name }
        val fmt = HistoryFormat(BaseCurrency.current, { accounts[it] ?: "an account that was removed" }, { categories[it] ?: it }, { formatMillis(it, "d MMM yyyy") })
        val canRestore = editable(entryId) is Editable.Form
        val title = (editable(entryId) as? Editable.Form)?.title ?: "Entry"
        return HistoryUi(
            entryId, title,
            revisions.mapIndexed { i, r ->
                HistoryRevisionUi(
                    i, formatMillis(r.atMillis, "d MMM yyyy, HH:mm"),
                    when (r.kind) { RevisionKind.CREATED -> "Created"; RevisionKind.EDITED -> "Edited"; RevisionKind.DELETED -> "Deleted"; RevisionKind.RESTORED -> "Brought back" },
                    fmt.lines(r.changes), // for the creation these are all the fields, each with no earlier value,
                    canRestore = canRestore && r.kind == RevisionKind.EDITED,
                )
            }.reversed(),
        )
    }

    /** Puts back the version of an entry as it was before revision [index] (an edit). It is saved as a new edit, so nothing is lost either way. */
    fun restoreVersion(entryId: String, index: Int) {
        val revisions = ok.store.history(EntryId(entryId))
        val r = revisions.getOrNull(index)?.takeIf { it.kind == RevisionKind.EDITED } ?: throw IllegalArgumentException("That change can't be undone any more.")
        val form = editable(entryId) as? Editable.Form ?: throw IllegalArgumentException("This entry can't be edited here.")
        save(HistoryRestore.apply(form.draft, r.before, BaseCurrency.current))
    }

    /**
     * What a bulk change did. [undo] puts everything back; [skipped] counts selected entries the change does not apply to, with [why].
     * [undoDrafts] are the earlier versions of changed entries, [undoRestore] the ids to bring back after a delete (in the order to restore them).
     */
    data class BulkResult(
        val changed: Int, val skipped: Int, val why: String?, val undoDrafts: List<EntryDraft> = emptyList(), val undoRestore: List<String> = emptyList(),
        /** What [bulkMakeTransfer] did, in the order it was done: Undo puts each back (last first). */
        val undoKinds: List<app.expenses.core.storage.KindChanges.Changed> = emptyList(),
    ) {
        fun text(what: String): String {
            if (changed == 0 && skipped == 0) return "Nothing to change."
            val head = if (changed == 0) "Nothing changed." else "$what $changed ${if (changed == 1) "entry" else "entries"}."
            return if (skipped == 0) head else "$head $skipped skipped${why?.let { ": $it" }.orEmpty()}."
        }
    }

    /**
     * Sets the category of every selected entry that can take one (a plain expense or an income); split, shared, refund and transfer entries are skipped.
     * So is an entry the category is not for: an expense category is not applied to income, nor an income category to an expense (a category made for
     * both takes either). All or nothing.
     */
    fun bulkSetCategory(ids: List<String>, category: String, teach: Boolean = true): BulkResult {
        val kind = ok.categories.list().firstOrNull { it.id == category }?.kind
        return bulkEdit(ids, "split, shared, transfers and refunds keep their own categories", teach) { d ->
            val income = d.kind == DraftKind.INCOME
            when {
                !(income || d.kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(d)) -> Skip
                d.category == category -> null
                kind != null && !kind.serves(income) -> SkipFor(if (income) "income can't use an expense category" else "an expense can't use an income category")
                else -> d.copy(category = category)
            }
        }
    }

    /** Moves every selected entry to [account] (a transfer's "from" account). A transfer that would then go from an account to itself is skipped. All or nothing. */
    fun bulkSetAccount(ids: List<String>, account: String): BulkResult = bulkEdit(ids, "a transfer can't start and end in the same account, and shared or foreign-currency entries can't be edited here") { d ->
        if (d.accountId == account) null else d.copy(accountId = account)
    }

    /** Adds [tag] to every selected entry that can be edited here (up to ten tags each). All or nothing. */
    fun bulkAddTag(ids: List<String>, tag: String): BulkResult = bulkEdit(ids, "shared or foreign-currency entries can't be edited here, and an entry holds up to ${TagRules.MAX_PER_ENTRY} tags") { d ->
        val t = TagRules.normalize(tag) ?: return@bulkEdit Skip
        when {
            d.tags.any { it.equals(t, ignoreCase = true) } -> null
            d.tags.size >= TagRules.MAX_PER_ENTRY -> Skip
            else -> d.copy(tags = d.tags + t)
        }
    }

    /** Removes [tag] (ignoring case) from every selected entry that has it. */
    fun bulkRemoveTag(ids: List<String>, tag: String): BulkResult = bulkEdit(ids, "shared or foreign-currency entries can't be edited here") { d ->
        if (d.tags.none { it.equals(tag, ignoreCase = true) }) null else d.copy(tags = d.tags.filterNot { it.equals(tag, ignoreCase = true) })
    }

    /**
     * Makes every selected plain expense a transfer from its own account to [toAccount] (see [changeKind]: each is replaced by a transfer that carries its date, title, note, tags, source and
     * attachments), all in one step that one Undo takes back. Skipped, with the reason named in the result: anything that is not an expense, an expense already on [toAccount], and what
     * cannot change its type (shared, foreign currency, refunds, "I expect this back", tied to a bill or a repeating item). Nothing is learned from it.
     */
    fun bulkMakeTransfer(ids: List<String>, toAccount: String): BulkResult = makeTransfers(ids.distinct().map { TransferMove(it, toAccount) }, allowIncome = false)

    /**
     * One transfer to make from "Transfer candidates": the expense or income [entryId] becomes a transfer between its own account and [otherAccountId] (an expense is money out of its account into
     * [otherAccountId]; an income is money out of [otherAccountId] into its account). With [absorbsIncome] (an expense only) the income [absorbsIncome], the other half of the same move, is
     * deleted (it can be brought back) so the move is counted once: [otherAccountId] must then be the account that income was paid into.
     */
    data class TransferMove(val entryId: String, val otherAccountId: String, val absorbsIncome: String? = null)

    /**
     * Makes each of [moves] a transfer, all in one step that one Undo takes back (see [changeKind]: each entry is replaced by a transfer that carries its date, title, note, tags, source,
     * bank reference, original text and attachments). What cannot be changed is skipped and the result says why ([BulkResult.why]): an entry already part of another move in the same batch,
     * one that is not an expense (or, with [allowIncome], an income), one on the account it would move to, and what cannot change its type (shared, foreign currency, refunds, "I expect this
     * back", tied to a bill or a repeating item). Nothing is learned from it. [BulkResult.undoRestore] holds the incomes deleted as the second half of a pair.
     */
    fun makeCandidateTransfers(moves: List<TransferMove>): BulkResult = makeTransfers(moves, allowIncome = true)

    /**
     * [allowIncome] is true for Transfer candidates: there a lone entry is also skipped when the other half of its move is already in the ledger (its money-in was reversed, or the credit on the
     * destination is a live income), and the income of a pair must have no attachment; its tags are added to the transfer and its note is carried when the transfer has none.
     */
    private fun makeTransfers(moves: List<TransferMove>, allowIncome: Boolean): BulkResult {
        val q = ok.db.schemaQueries
        val todo = ArrayList<EntryDraft>(); val absorbed = ArrayList<String>(); var skipped = 0; val reasons = LinkedHashSet<String>()
        val used = HashSet<String>(); val carriedNote = HashMap<String, String?>(); val partnerOf = HashMap<String, String>()
        fun skip(why: String) { skipped++; reasons += why.trimEnd('.').replaceFirstChar { it.lowercase() } }
        for (m in moves) {
            val id = m.entryId
            val row = q.entryById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L }
            val income = row?.kind == "INCOME"
            val d = (editable(id) as? Editable.Form)?.takeIf { it.canChangeKind }?.draft
            // a shared, foreign-currency or refunded expense is read-only in the editor, but it is an expense: it is skipped for its own reason, not for not being one
            val problem = when {
                row == null || row.kind != "EXPENSE" && !(allowIncome && income) -> "only expenses can be made transfers"
                !used.add(id) -> "an entry can only be part of one transfer at a time"
                row.account_id == m.otherAccountId -> if (income) "an income can't be a transfer from the account it was paid into" else "an expense can't be a transfer to the account it was paid from"
                allowIncome && m.absorbsIncome == null && ok.transferCandidates.otherHalfLive(id) -> "its matching money-${if (income) "out" else "in"} is already in your ledger"
                else -> ok.kindChanges.refusal(id) ?: if (d == null) "this ${if (income) "income" else "expense"} is not in ${BaseCurrency.current.code}, so it can't be changed here" else null
            }
            val partner = m.absorbsIncome?.let { pid -> q.entryById(pid).executeAsOneOrNull()?.takeIf { it.deleted == 0L && it.kind == "INCOME" } }
            val partnerProblem = when {
                problem != null || m.absorbsIncome == null -> null
                partner == null || income -> "the matching income is no longer there"
                !used.add(partner.id) -> "an entry can only be part of one transfer at a time"
                q.attachmentsOf(partner.id).executeAsList().isNotEmpty() -> "the matching income has attachments, which would be left behind"
                partner.amount_minor != row!!.amount_minor || partner.account_id != m.otherAccountId -> "the matching income does not match any more"
                else -> ok.kindChanges.refusal(partner.id)?.let { "the matching income: " + it.replaceFirstChar { c -> c.lowercase() } }
            }
            // the income of a pair is deleted: what it carried that the transfer can hold (its tags, and its note when the transfer has none) goes to the transfer; its words stay with it in the bin
            val absorbedTags = partner?.let { ok.store.tagsOf(EntryId(it.id)) }.orEmpty()
            val tags = d?.tags.orEmpty().let { own -> (own + absorbedTags.filter { t -> own.none { it.equals(t, ignoreCase = true) } }).take(TagRules.MAX_PER_ENTRY) }
            val draft = d?.copy(kind = DraftKind.TRANSFER, accountId = if (income) m.otherAccountId else d.accountId, toAccountId = if (income) d.accountId else m.otherAccountId, category = "", splits = emptyList(), expectBack = false, expectFrom = "", expectPersonId = "", expectAmount = "", tags = tags)
            when {
                problem != null -> skip(problem)
                partnerProblem != null -> skip(partnerProblem)
                draft == null || EntryDrafts.build(draft, EntryId("preview"), BaseCurrency.current) !is DraftResult.Ok -> skip("it could not be made a transfer")
                else -> { todo += draft; partner?.let { absorbed += it.id; partnerOf[id] = it.id; carriedNote[id] = it.note } }
            }
        }
        val done = ok.store.atomically {
            val kinds = todo.map { changeKind(it, carriedNote[it.id]) }
            // the income of a pair is deleted and recorded as replaced by the transfer that took over its move, so an older backup restored later does not bring it back (Undo restores it, which drops the record)
            kinds.forEach { c -> partnerOf[c.oldId]?.let { ok.kindChanges.absorb(it, c.newId) } }
            kinds
        }
        return BulkResult(done.size, skipped, reasons.joinToString("; ").takeIf { skipped > 0 }, undoKinds = done, undoRestore = absorbed.reversed())
    }

    private object Skip

    /** Skipped for its own reason, named in the result next to the general one. */
    private class SkipFor(val reason: String)

    private fun bulkEdit(ids: List<String>, why: String, teach: Boolean = true, change: (EntryDraft) -> Any?): BulkResult {
        val old = ArrayList<EntryDraft>(); val new = ArrayList<EntryDraft>(); var skipped = 0
        val reasons = LinkedHashSet<String>(); var general = false
        for (id in ids.distinct()) {
            val form = editable(id) as? Editable.Form
            if (form == null) { skipped++; general = true; continue }
            when (val r = change(form.draft)) {
                null -> Unit // already as wanted
                is EntryDraft -> if (EntryDrafts.build(r, EntryId(id), BaseCurrency.current) is DraftResult.Ok) { old += form.draft; new += r } else { skipped++; general = true }
                is SkipFor -> { skipped++; reasons += r.reason }
                else -> { skipped++; general = true }
            }
        }
        ok.store.atomically { new.forEach { save(it, teach) } }
        if (general) reasons += why
        return BulkResult(new.size, skipped, reasons.joinToString("; ").takeIf { skipped > 0 }, undoDrafts = old)
    }

    /** Deletes the selected entries (each can be brought back). An expense with refunds goes only if its refunds are in the selection too. All or nothing. */
    fun bulkDelete(ids: List<String>): BulkResult {
        val q = ok.db.schemaQueries
        val live = ids.distinct().mapNotNull { id -> q.entryById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L } }
        val (refunds, others) = live.partition { it.kind == "REFUND" }
        val deleted = ArrayList<String>(); var skipped = ids.distinct().size - live.size
        ok.store.atomically {
            for (r in refunds + others) {
                if (r.kind == "EXPENSE" && q.hasLiveRefunds(r.id).executeAsOne()) { skipped++; continue }
                ok.store.delete(EntryId(r.id)); deleted += r.id
            }
        }
        return BulkResult(deleted.size, skipped, "an expense with refunds that are not selected can't be deleted".takeIf { skipped > 0 }, undoRestore = deleted.reversed())
    }

    /** Deletes an entry (it can be restored). Refused, with a message, while an expense still has refunds. */
    fun delete(entryId: String) = ok.store.delete(EntryId(entryId))

    fun restore(entryId: String) = ok.store.restore(EntryId(entryId))
}
