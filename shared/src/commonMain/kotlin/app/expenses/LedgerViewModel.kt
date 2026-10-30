package app.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.InsightLine
import app.expenses.core.domain.Goal
import app.expenses.core.domain.GoalCodec
import app.expenses.core.domain.GoalMath
import app.expenses.core.domain.PeriodCompare
import app.expenses.core.domain.SubscriptionFinder
import app.expenses.core.domain.SubscriptionSample
import app.expenses.core.domain.SpendingInsight
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import app.expenses.core.domain.BackupNudge
import app.expenses.core.domain.Csv
import app.expenses.core.domain.LastBackup
import app.expenses.core.domain.Flow
import app.expenses.core.domain.ParsedStatement
import app.expenses.core.domain.ReportMath
import app.expenses.core.domain.StatementDetector
import app.expenses.core.domain.StatementMapping
import app.expenses.core.domain.StatementParser
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.StatementTextParser
import app.expenses.core.domain.parseAmount
import app.expenses.core.storage.SearchQuery
import app.expenses.core.storage.searchFiltered
import kotlinx.datetime.plus
import app.expenses.core.storage.DrillTarget
import app.expenses.core.storage.ReportGroup
import kotlinx.datetime.minus
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.PaymentsLeftMath
import app.expenses.core.domain.SinkingFunds
import app.expenses.core.storage.RecurringStore
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.Template
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.AccountRules
import app.expenses.core.domain.Appearance
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BaseCurrencyRules
import app.expenses.core.domain.CaptureCount
import app.expenses.core.domain.CaptureHealth
import app.expenses.core.domain.CaptureHealthReport
import app.expenses.core.parsers.AppSources
import app.expenses.core.parsers.SenderRules
import app.expenses.core.domain.DupEntry
import app.expenses.core.domain.DupStrength
import app.expenses.core.domain.Duplicates
import app.expenses.core.domain.TransferPair
import app.expenses.core.domain.TransferPairs
import app.expenses.core.domain.PendingKey
import app.expenses.core.domain.CategorySuggest
import app.expenses.core.domain.CategorySuggestion
import app.expenses.core.domain.SimilarCandidate
import app.expenses.core.domain.SimilarEntries
import app.expenses.core.domain.SuggestionSource
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.CardText
import app.expenses.core.domain.CardSummaryText
import app.expenses.core.domain.CurrencyChoices
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.AttachmentRules
import app.expenses.core.domain.ImportProfiles
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.storage.SavedSearch
import app.expenses.core.storage.resolveCategory
import app.expenses.core.domain.FirstWeek
import app.expenses.core.domain.FirstWeekCapture
import app.expenses.core.domain.FirstWeekInputs
import app.expenses.core.domain.FirstWeekItem
import app.expenses.core.domain.HomeLayout
import app.expenses.core.domain.HomeSection
import app.expenses.core.storage.DrillLine
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.compact
import app.expenses.core.domain.BillOnDay
import app.expenses.core.domain.CalendarMath
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.DraftResult
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.InboxStatus
import app.expenses.core.parsers.BillKind
import app.expenses.core.domain.CardTier
import app.expenses.core.domain.CardNetwork
import app.expenses.core.domain.AccountLooks
import app.expenses.core.domain.AccountLook
import app.expenses.core.parsers.PayeeGuess
import app.expenses.core.parsers.PayeeKind
import app.expenses.core.parsers.PayeeKinds
import app.expenses.core.parsers.KindRules
import app.expenses.core.parsers.MessageKind
import app.expenses.core.parsers.Direction
import app.expenses.core.domain.OwnExport
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.SingleFlight
import app.expenses.core.storage.BudgetStatus
import app.expenses.core.storage.CategoryStore
import app.expenses.core.storage.bulkLoad
import app.expenses.core.storage.db.Entry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class RowUi(val seq: Long, val id: String, val title: String, val subtitle: String, val amount: String,
    /** Money coming in (income, a refund): shown with a plus sign so it is not mistaken for spending. */
    val inflow: Boolean,
    /** The local day of the entry, as an epoch-day number, so the list can be grouped by day. */
    val day: Long = 0,
    /** The category's name, or empty, for the coloured initial beside the row. */
    val category: String = "",
    /** The entry's kind (EXPENSE, INCOME, TRANSFER, REFUND ...): a transfer between your own accounts is neither money in nor out. */
    val kind: String = "",
    /** The amount in minor units (paise, cents), to total a list of results. */
    val minor: Long = 0,
)
data class AccountUi(val id: String, val name: String, val type: String = "")

/** One earlier entry offered the category just chosen for a similar one. */
data class SimilarRowUi(val id: String, val title: String, val dateText: String, val amountText: String, val fromCategory: String)

/** Earlier entries that read like one the person has just filed: [label] is what it was called, [rows] are the ones not yet in [categoryName]. */
data class SimilarUi(val label: String, val categoryId: String, val categoryName: String, val rows: List<SimilarRowUi>)

/** A captured transaction waiting for review. [accountId] and [categoryId] are the suggestions the row starts with. */
data class InboxRowUi(
    val id: String, val issuer: String, val direction: String, val amountText: String, val amountMinor: Long, val dateText: String, val last4: String?, val body: String, val payee: String?,
    val accountId: String, val categoryId: String, val rememberedAccount: Boolean, val confidence: String,
    /** Where the suggested category came from: "your rule", "what you chose last time", or empty. */
    val categoryNote: String = "",
    /** Set when this looks like something already in the ledger (or the same payment as another text): what to tell the person. */
    val duplicateNote: String? = null,
    val duplicateOfEntry: String? = null,
    /** A note that is not a duplicate warning (a refund that carries the original payment's reference): shown, with Add staying the main action. */
    val relatedNote: String? = null,
    /** How the ledger should record it: "PAYMENT" (an expense or income), "TRANSFER" (between the person's own accounts) or "REFUND" (of an earlier expense). */
    val treatment: String = "PAYMENT",
    /** What the treatment is, in words ("cash withdrawal", "credit card payment", "refund of Cafe"). */
    val treatmentLabel: String = "",
    val fromAccountId: String = "",
    val toAccountId: String = "",
    val refundOfEntry: String = "",
    /** "person" or "business" when the payee's UPI address says so, else empty. */
    val payeeKind: String = "",
    /** Whether the person can give this payee a name of their own (there is an address or name in the text to attach it to). */
    val nameable: Boolean = false,
    /** With [treatment] "PAIR": the other waiting text of the same transfer (this row stands for both), its words, and the last four digits it carries; [pairBasis] is what tied them (see PairBasis). */
    val pairedItemId: String = "",
    val pairedBody: String = "",
    val pairedLast4: String = "",
    val pairBasis: String = "",
    /** An entry the person already added from an earlier text that is the other half of this one (an expense for money in, income for money out), and what to say about it: "Make this a transfer". */
    val convertEntryId: String = "",
    val convertNote: String = "",
    /** "Might be a transfer to your other account ..." for a text whose other half has not arrived; empty otherwise. */
    val transferHint: String = "",
    /** "Set the last four digits of your accounts so I can recognise transfers": when the text looks like a transfer but its accounts could not be told; empty otherwise. */
    val setupHint: String = "",
)

/** Someone lent to or borrowed from. */
data class PersonUi(val id: String, val name: String)

/** One person's standing: [minor] is positive when they owe you, negative when you owe them. */
data class LoanRowUi(val id: String, val name: String, val minor: Long, val amountText: String, val state: String, val entries: Int)

/** One lent or borrowed entry with a person. */
data class LoanEntryUi(val id: String, val dateText: String, val amountText: String, val gave: Boolean, val account: String, val note: String)

/** The lent-and-borrowed screen: everyone's standing, the two totals, and the history of the person picked. */
data class LoansUi(val rows: List<LoanRowUi> = emptyList(), val owedToYou: String = "", val youOwe: String = "", val selected: LoanRowUi? = null, val history: List<LoanEntryUi> = emptyList(), /** Expenses still waiting for money to come back. */ val waiting: List<WaitingRowUi> = emptyList())

/** One of the first few items waiting in To review, for the home banner. */
data class InboxPeekRow(val title: String, val amountText: String, val credit: Boolean)

/** What the home banner shows about To review: the first few items, the rest as a count, and what is waiting in total. */
data class InboxPeekUi(val rows: List<InboxPeekRow> = emptyList(), val more: Int = 0, val outText: String = "", val inText: String = "")

/** One day of the home screen's spending line: the local day, what was spent, and the text shown when the person drags a finger to it. */
data class SparkDayUi(val epochDay: Long, val minor: Long, val label: String)

/** The last 14 days of spending, oldest first. Empty until worked out (it is, after a save, once the screen has settled). */
data class SparkUi(val days: List<SparkDayUi> = emptyList(), /** This period's spending against the last one at the same point, or empty. */ val compare: String = "", /** The category furthest above its usual spending, or empty. */ val insight: String = "") { val isEmpty get() = days.none { it.minor > 0 } }

/** One bill as the screens show it. [dueIn] reads "in 12 days", "today" or "3 days overdue"; [cardAccountId] and [payFromId] are the suggestions the payment starts with. */
data class BillRowUi(
    val id: String, val title: String, val kind: String, val issuer: String, val amountText: String, val amountPlain: String, val minDueText: String?, val minDuePlain: String?,
    val dueText: String, val dueIn: String, val dueEpochDay: Long?, val overdue: Boolean, val soon: Boolean, val autoDebit: Boolean, val last4: String?,
    val body: String, val status: String, val cardAccountId: String, val payFromId: String, val paidText: String = "",
    /** Set on a row that is a repeating item waiting for the person (kind [BILL_KIND_REPEATING]): the item and the day of the occurrence, for Post it and Skip. */
    val repeatItemId: String = "", val repeatDay: Long = 0,
) { val isRepeat get() = repeatItemId.isNotEmpty() }

/** [BillRowUi.kind] of a repeating expense listed with the bills. */
const val BILL_KIND_REPEATING = "REPEATING"


/** The bills page: found in messages, being tracked (soonest first), and recently paid. */
data class BillsUi(val found: List<BillRowUi> = emptyList(), val coming: List<BillRowUi> = emptyList(), val paid: List<BillRowUi> = emptyList(), val loaded: Boolean = false)

/** One line of the home banner for bills. */
data class BillPeekRow(val id: String, val title: String, val amountText: String, val dueIn: String, val isNew: Boolean, val overdue: Boolean)

/** What the home banner shows about bills: how many are new, and the first few (new ones first, then the soonest being tracked). */
data class BillPeekUi(val newCount: Int = 0, val rows: List<BillPeekRow> = emptyList(), val more: Int = 0, /** Tracked bills due within a week, overdue ones included. */ val dueThisWeek: Int = 0, /** What they add up to (those with a known amount, in the ledger's currency), or empty. */ val weekTotal: String = "") { val isEmpty get() = rows.isEmpty() && dueThisWeek == 0 }

data class CaptureRuleUi(val id: String, val pattern: String, val categoryId: String, val categoryName: String)

/**
 * [lastReceivedText] is when the last text (or email) reached capture, in words ("3 days ago"), or null when none ever did; [lastReceivedFresh] says it was in the last 7 days.
 * A message sent with "Send a test now" counts as received: it goes through the same door.
 */
data class CaptureSettingsUi(val smsOn: Boolean, val extraHeaders: String, val notifOn: Boolean = false, val emailOn: Boolean = false, val lastReceivedText: String? = null, val lastReceivedFresh: Boolean = false)

/**
 * An account as the accounts screen needs it. [openingText] is what the form shows: for a card or loan it is the amount owed, as a
 * positive number. [usage] is how many entries and saved items refer to it (zero means it can be deleted).
 */
data class AccountRowUi(
    val id: String, val name: String, val type: String, val typeLabel: String, val isLiability: Boolean, val archived: Boolean,
    val balanceText: String, val balanceMinor: Long, val owed: Boolean, val openingText: String, val usage: Long,
    /** Repeating items and quick entries that post to it: while there are any, it cannot be archived. */
    val savedItems: Long = 0,
    /** Billing dates of a credit card, when set. */
    val card: CardRowUi? = null,
    /** What the tile looks like and the digits texts use: a [app.expenses.core.domain.CardNetwork] and [app.expenses.core.domain.CardTier] name for a credit card, and the last four digits. */
    val network: String? = null, val tier: String? = null, val last4: String? = null,
)

/** A card's billing dates and where its latest statement stands, as the accounts screen shows them. */
/** One charge that keeps coming, as the Subscriptions screen shows it. */
data class SubscriptionRowUi(
    val key: String, val name: String, val amountText: String, val cadenceText: String, val nextText: String, val yearlyText: String, /** A repeating item with this name already exists, so no reminder needs making. */ val repeats: Boolean = false,
    /** "Last charged ₹99 on 12 Oct": the latest price and day. */ val lastText: String = "",
    /** "Price went up from ₹75" or "Price went down from ₹99" when the latest price differs from what it was by 5% or more; empty otherwise. */ val priceNote: String = "", val priceUp: Boolean = false,
)

data class SubscriptionsUi(val rows: List<SubscriptionRowUi> = emptyList(), val monthlyText: String = "", val yearlyText: String = "", val ignored: Int = 0)

/** One savings goal as the Goals screen shows it. */
data class GoalRowUi(
    val id: String, val name: String, val savedText: String, val targetText: String, val percent: Int, val leftText: String, val dueText: String, val monthlyText: String,
    /** The name of the account this goal follows, or empty when the amount is kept by hand. */
    val accountName: String, val accountId: String, val targetPlain: String, val monthsPlain: String,
)

data class GoalsUi(val rows: List<GoalRowUi> = emptyList(), val accounts: List<AccountUi> = emptyList())

data class CardRowUi(val statementDay: Int, val dueDay: Int, val minDuePercent: Int, val remindDaysBefore: Int, val termsText: String, val statusText: String, /** The credit limit as typed (plain rupees), or empty. */ val creditLimit: String = "",
    /** The card summary (worked out in [app.expenses.core.domain.CardSummaries]): charges since the statement, the limit use (empty without a limit; [limitFraction] is then 0) and what is left to pay with its due date. */
    val cycleText: String = "", val limitText: String = "", val limitFraction: Float = 0f, val dueLineText: String = "")

val ACCOUNT_TYPE_LABELS = linkedMapOf("BANK" to "Bank", "CASH" to "Cash", "WALLET" to "Wallet", "CREDIT_CARD" to "Credit card", "LOAN" to "Loan")

/** A category as the screens need it. [usage] is how many items refer to it (needed to decide whether it can be deleted). */
/** [kind] says which entries the category is offered for: expenses, income, or both (only for categories made before kinds existed that income already used). */
data class CategoryUi(val id: String, val name: String, val parentId: String?, val colorArgb: Long?, val hidden: Boolean, val usage: Long, val kind: app.expenses.core.domain.CategoryKind = app.expenses.core.domain.CategoryKind.EXPENSE)

/** An attachment as the editor lists it. */
data class AttachmentUi(val id: String, val name: String, val sizeText: String, val mime: String)

/** Automatic export to the user's own folder, as the Data screen shows it. [status] is the last outcome; [protectedCopies] is "Protect automatic copies with a passphrase" being on. */
data class AutoExportUi(val frequency: AutoExportFrequency, val folderName: String?, val lastText: String?, val status: String?, val statusOk: Boolean, val protectedCopies: Boolean = false, val olderExcel: Int = 0)

/** A saved quick entry as the UI needs it. [amountText] is null when the template asks for the amount. */
data class TemplateUi(val id: String, val name: String, val amountText: String?, val category: String, val accountId: String, val note: String)

/**
 * One budget as the screens show it. A null [limit] means no limit is set (the line is still listed so one can be added).
 * [percent] is the share of the limit used, whole percent, and can pass 100.
 */
data class BudgetLineUi(
    val categoryId: String?,
    val name: String,
    val limit: String?,
    /** The limit as plain digits for the edit field, or empty. */
    val limitPlain: String,
    val spent: String,
    /** "₹2,000.00 left" or "₹150.00 over"; empty without a limit. */
    val note: String,
    val percent: Int,
    val state: BudgetState,
)

/** Budget state for the current period. [perDay] is null without an overall limit. */
data class BudgetUi(
    val period: BudgetPeriod,
    /** "1 Oct – 31 Oct". */
    val range: String,
    /** What the period is called in headings: "this month", "this cycle" or "this week". */
    val periodName: String,
    val overall: BudgetLineUi,
    val categories: List<BudgetLineUi>,
    val perDay: String?,
    /** Bills still to come this period, or null when there are none (or no limit). */
    val upcoming: String?,
    /** True when the bills still to come use up everything left under the limit. */
    val billsUseItAll: Boolean,
    val daysLeft: Int,
    /** How many category limits are near or over. */
    val categoriesNeedingAttention: Int,
    /** The limits gain what was left unspent last period. */
    val rollover: Boolean = false,
    /** What the overall limit gained from last period (e.g. "₹2,000.00"), or empty. */
    val carriedText: String = "",
)

/** A repeating expense waiting to be posted or skipped. [amount] is null when the amount differs each time and must be asked for. */
data class DueUi(val itemId: String, val epochDay: Long, val name: String, val amount: String?, val whenText: String, val overdue: Boolean)

/** A repeating expense as the editor needs it. [amountPlain] is empty when the amount is asked each time. */
data class RecurringUi(
    val id: String,
    val name: String,
    val amountPlain: String,
    val categoryId: String,
    val accountId: String,
    val merchant: String,
    val schedule: Schedule,
    val auto: Boolean,
    val kind: RecurringKind,
    val toAccountId: String,
    /** "₹15,000.00 · every month · next 1 Nov". */
    val summary: String,
    /** For one that ends (after a number of payments or on a date): "12 payments left · last on 5 Mar 2028 · ₹60,000.00 still to pay". Payments times the amount; no interest. Empty otherwise. */
    val paymentsLeft: String = "",
    /** For a yearly, half-yearly or quarterly expense with a fixed amount: "set aside ₹1,000.00 a month". Empty otherwise. */
    val setAside: String = "",
)

/** Shown as "Added ₹20 · Undo" after an entry is recorded in one step. */
/** [restore] is true for a deletion: undoing it brings the entry back instead of removing one. */
data class UndoUi(
    val entryId: String, val text: String, val restore: Boolean = false, val recurringId: String? = null, val recurringDay: Long = 0,
    /** For a bulk edit: the earlier versions to put back; for a bulk delete: the ids to bring back, in order. */
    val bulkDrafts: List<EntryDraft> = emptyList(), val bulkRestore: List<String> = emptyList(),
    /** For several entries added in one go (a bill's items): the ids to take back. */
    val bulkCreated: List<String> = emptyList(),
    /** True only for entries added through the Add form: Home then scrolls the new row into view (not for quick entries or a posted repeating item). */
    val reveal: Boolean = false,
    /** For entries whose type was changed: what to put back, each in turn, last first (for one entry [entryId] is the new entry, which Undo removes). */
    val kindChanges: List<app.expenses.core.storage.KindChanges.Changed> = emptyList(),
)

/** One transaction found in a statement, as shown in the preview. A [transfer] is money moving between the person's own accounts. */
data class ImportRowUi(val text: String, val amount: String, val spending: Boolean, val transfer: Boolean = false)

/** What an import did, for the screen. [transfers] of the [imported] entries are transfers between the person's own accounts, together [transferAmount]. */
data class ImportResultUi(val imported: Int, val alreadyThere: Int, val spending: String, val income: String, val matchedExisting: Int = 0, val transfers: Int = 0, val transferAmount: String = "")

/**
 * The lines of a statement that look like paying a credit card's bill, and what to do with them. [side] is "BANK" (money out of a bank account) or "CARD" (money into
 * a card). [choice] is "TRANSFER", "AS_IS" or "SKIP", or empty while the person has not chosen. For a transfer, [account] is the account every line goes to (bank
 * side) or comes from (card side), empty when each line goes to the one its own text names ([eachNamed]). [accounts] are the ones it can go to or come from.
 */
data class CardGroupUi(
    val side: String, val count: Int, val total: String, val rows: List<ImportRowUi>, val choice: String, val account: String,
    val accounts: List<AccountUi>, val eachNamed: Boolean, val skipped: Int, val transfers: Int,
    /** False while the person has not chosen (or chose a transfer and a line has no account to go to): the import stays off. */
    val resolved: Boolean,
)

/** For a file from another app: how many rows found a category and an account of the person's own, and the categories they have none of, which they may choose to create. */
data class ImportCategoriesUi(val newNames: List<String>, val create: Boolean, val withCategory: Int, val onOwnAccount: Int)

/** State of the statement import. [mapping] is null until a file is chosen. Nothing is saved until the user confirms. */
data class ImportUi(
    val active: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val columnNames: List<String> = emptyList(),
    val mapping: StatementMapping? = null,
    val accountId: String = "",
    val dateAmbiguous: Boolean = false,
    val damaged: Boolean = false,
    val rowCount: Int = 0,
    val preview: List<ImportRowUi> = emptyList(),
    val txnCount: Int = 0,
    val spending: String = "",
    val income: String = "",
    val problems: List<String> = emptyList(),
    val problemCount: Int = 0,
    val ignoredRows: Int = 0,
    /** Rows whose description had unquoted commas and were put back together. */
    val repairedRows: Int = 0,
    val mappingProblem: String? = null,
    /** Set when the layout was not guessed but remembered from an earlier file with the same column titles. */
    val profileNote: String? = null,
    val result: ImportResultUi? = null,
    /** "PDF" or "photo" when the rows were read out of a PDF or a picture rather than a spreadsheet: the preview must then be checked. */
    val readFrom: String? = null,
    /** How money in and out was told apart in a PDF or a photo, in words. */
    val readNote: String? = null,
    /** Payments of a credit card's own bill found in a statement and left out, since they are not income. */
    val cardPayments: Int = 0,
    /** For a PDF or photo: a sentence saying the statement's own printed totals differ from what was found, else null. */
    val totalsNote: String? = null,
    /** The group of card payments to decide about, when the file has any. */
    val cardGroup: CardGroupUi? = null,
    /** How many of [txnCount] will be saved as transfers between the person's own accounts. */
    val transfers: Int = 0,
    /** The other app a file was recognised as an export of ("Money Manager"), with what is to be known about reading it, what was left out of it, and whether its date order is a fact. */
    val preset: String? = null, val presetNote: String? = null, val presetLeftOut: String? = null,
    /** For a recognised file: its categories and accounts matched to the person's own. */
    val categories: ImportCategoriesUi? = null,
    /** A PDF that is protected with a password: asks for it. [passwordError] says the one typed did not open it. The password itself is never kept in this state. */
    val needsPassword: Boolean = false,
    val passwordError: String? = null,
)

/** A search kept under a name, ready to apply. */
data class SavedSearchUi(val id: String, val name: String, val input: SearchInput)

enum class SearchKind { ANY, EXPENSE, INCOME }
enum class SearchRange { ANY, LAST_30_DAYS, THIS_PERIOD, THIS_YEAR }

/** The dates Home's list is limited to while it shows one account: everything, this calendar month, or last calendar month. */
enum class HomePeriod { ALL, THIS_MONTH, LAST_MONTH }

/** What the user has typed and chosen on the search screen. Amounts are rupees as typed ("250", "99.50"); empty means no limit. */
data class SearchInput(
    val text: String = "",
    val minRupees: String = "",
    val maxRupees: String = "",
    val categoryId: String? = null,
    val kind: SearchKind = SearchKind.ANY,
    val range: SearchRange = SearchRange.ANY,
    val tag: String? = null,
    /** Chosen days (since 1970-01-01, both included, either may be open). Take the place of [range] when either is set. */
    val fromDay: Long? = null,
    val toDay: Long? = null,
    /** Only what was paid from this account or moved into it. */
    val accountId: String? = null,
)

/** [problem] explains input that cannot be searched; [hasConditions] is false until something is typed or chosen. */
data class SearchUi(
    val results: List<RowUi> = emptyList(),
    val searching: Boolean = false,
    val hasConditions: Boolean = false,
    val truncated: Boolean = false,
    val problem: String? = null,
    /** What the last bulk change from here did, with a way to take it back. */
    val notice: String? = null,
    val undo: UndoUi? = null,
)

/** One day cell of the calendar. [spent] is a short label (empty when nothing was spent). */
data class CalendarCellUi(val day: Int, val epochDay: Long, val spent: String, val spentMinor: Long, val bills: Int, val level: Int, val isToday: Boolean)

/** What the calendar shows: the month grid, its total, and the picked day's entries and bills. */
data class CalendarUi(
    val label: String, val offset: Int, val blanks: Int, val cells: List<CalendarCellUi>, val total: String,
    val selectedEpochDay: Long?, val dayTitle: String, val dayTotal: String, val dayLines: List<DrillLine>, val dayBills: List<String>,
)

enum class ReportMode { PERIOD, YEAR, FINANCIAL_YEAR, CUSTOM }

/** A row of a report grouped by account, tag or merchant. [key] is what drilling in needs (null: entries with no tag). */
data class GroupRowUi(val key: String?, val name: String, val amount: String, val share: Int, val negative: Boolean)

data class ReportRowUi(val categoryId: String?, val name: String, val amount: String, val share: Int, val colorArgb: Long?, val negative: Boolean, val children: List<ReportRowUi> = emptyList())

/** One bar of the trend. [fraction] is its height relative to the tallest bar (0 to 1). Tapping it opens the entries of that period. */
data class TrendBarUi(val label: String, val amount: String, val fraction: Float, val isSelected: Boolean, val startDay: Long, val endDay: Long)

data class DrillLineUi(val entryId: String, val date: String, val title: String, val category: String, val amount: String, val negative: Boolean)

/** The entries behind a figure. [total] is the sum of [lines], shown so the list visibly adds up to what was tapped. */
data class DrillUi(val title: String, val subtitle: String, val total: String, val lines: List<DrillLineUi>, val truncated: Boolean)

data class ReportUi(
    val mode: ReportMode,
    val title: String,
    val canGoNext: Boolean,
    val spent: String,
    val income: String,
    val net: String,
    val netPositive: Boolean,
    /** "12% more than the period before" or null when there is nothing to compare with. */
    val comparison: String?,
    val rows: List<ReportRowUi>,
    val trend: List<TrendBarUi>,
    val drill: DrillUi?,
    val group: ReportGroup = ReportGroup.CATEGORY,
    val groupRows: List<GroupRowUi> = emptyList(),
    /** The custom range as days since 1970-01-01, the end exclusive; set in the custom mode. */
    val customStartDay: Long = 0, val customEndDay: Long = 0,
)

/** A backup file that was opened and checked, waiting for the user to confirm the restore. */
data class PendingRestoreUi(
    val madeAt: String, val entriesInFile: Int, val newEntries: Int, val alreadyHereEntries: Int, val otherNewItems: Int,
    /** Photos and files the backup carries, their size together, and how many of them this phone would take. */
    val filesInFile: Int = 0, val filesBytes: Long = 0, val newFiles: Int = 0,
)

/** The photos and files a backup would carry: how many, how large together, and whether the switch for including them is on. */
data class BackupFilesUi(val count: Int = 0, val bytes: Long = 0, val include: Boolean = true)

/** State of the Data screen. [status] is the outcome of the last action; [needsPassphrase] asks for the passphrase of an encrypted backup. */
data class DataUi(
    val busy: Boolean = false,
    val status: String? = null,
    val statusIsError: Boolean = false,
    val needsPassphrase: Boolean = false,
    val pending: PendingRestoreUi? = null,
    val files: BackupFilesUi = BackupFilesUi(),
)

sealed interface UiState {
    data object Opening : UiState

    /** The database exists but its key is gone; the user must choose to start fresh or restore. */
    data class KeyLost(val message: String) : UiState

    data class Ready(
        val rows: List<RowUi>,
        /** Spending in the current budget period (the calendar month unless the user chose otherwise). */
        val periodSpend: String,
        /** [periodSpend] as a number of minor units, for the total to roll up to. */
        val periodSpendMinor: Long = 0,
        val budget: BudgetUi,
        val due: List<DueUi>,
        /** Occurrences older than the due window that nobody dealt with; mentioned so they do not vanish silently. */
        val hiddenOlder: Int,
        /** True on a fresh install where nothing has ever been recorded, until the user adds something or skips it. */
        val showWelcome: Boolean,
        /** True when it is time to remind the user to make a backup. */
        val showBackupNudge: Boolean,
        val notifications: NotificationPrefs,
        val recurring: List<RecurringUi>,
        val accounts: List<AccountUi>,
        /** Every account for the accounts screen, archived ones included, with balances. */
        val accountRows: List<AccountRowUi>,
        /** Everything an old entry might refer to (archived accounts included), for showing and editing it. */
        val allAccounts: List<AccountUi>,
        val templates: List<TemplateUi>,
        val homeLayout: HomeLayout,
        val appearance: Appearance,
        val baseCurrency: Currency,
        val inboxPending: Int,
        val inboxPeek: InboxPeekUi = InboxPeekUi(),
        val billPeek: BillPeekUi = BillPeekUi(),
        /** The first-week checklist on Home (see [FirstWeek]); empty when it should not show. */
        val firstWeek: List<FirstWeekItem> = emptyList(),
        val spark: SparkUi = SparkUi(),
        /** The person asked for less motion (Appearance). The screens also honour the phone's own animation setting. */
        val reduceMotion: Boolean = false,
        /** "off", "quick", "relaxed" or "slow": how long the app's movements take. */
        val motionSpeed: String = "relaxed",
        val smsCapture: Boolean,
        val autoExport: AutoExportUi,
        /** "Hide amounts" is on: every amount on screen is hidden. */
        val hideAmounts: Boolean,
        val savedSearches: List<SavedSearchUi>,
        /** The words searched for lately, newest first, to offer again on the search screen. */
        val recentSearches: List<String>,
        val categories: List<CategoryUi>,
        /** Tags in use, most used first, to offer when tagging. */
        val tags: List<String>,
        /** What the add screen starts with: the expense category and account used last, and the income category used last. */
        val lastCategory: String,
        val lastIncomeCategory: String,
        val lastAccountId: String,
        val endReached: Boolean,
        /** The account Home's list is narrowed to, or null for everything. */
        val homeAccountId: String? = null,
        /** The dates Home's list is limited to while it is narrowed to one account. */
        val homePeriod: HomePeriod = HomePeriod.ALL,
        /** The balance-based safe to spend; worked out with the balances (when the accounts page is open, or Home may show it), null until then. */
        val safeToSpend: SafeToSpendUi? = null,
        /** Expenses still waiting for money to come back, for the chip on Home. */
        val waiting: WaitingUi = WaitingUi(),
        /** The person turned on putting yearly bills aside month by month (it is taken off safe to spend). */
        val sinkingFunds: Boolean = false,
        val message: String? = null,
        val undo: UndoUi? = null,
    ) : UiState
}

/** The ids of the default categories, used only to give seeded test data realistic categories. */
val DEFAULT_CATEGORIES = CategoryStore.DEFAULTS

private const val PAGE = 50L

/** How far back an expense can be and still be what a refund text is about. */
private const val REFUND_LOOKBACK_DAYS = 120L

/**
 * Whether [e] is the database saying it has been closed. The container closes the database on purpose (starting a fresh ledger, tearing the app
 * down in tests), and background work that was already running then finds it shut: that is not a bug in the work and must not end the process.
 */
fun isDatabaseClosed(e: Throwable): Boolean =
    e is IllegalStateException && (e.message.orEmpty().contains("already-closed") || e.message.orEmpty().contains("connection pool has been closed"))

/** Where the view model's background work runs. Any failure other than a closed database still reaches the thread's uncaught handler, as before. */
private val BACKGROUND = ioDispatcher + CoroutineExceptionHandler { _, e ->
    if (!isDatabaseClosed(e)) rethrowUncaught(e)
}
private const val SEARCH_LIMIT = 100L
private const val TAG_SUGGESTIONS = 40L
private const val PROFILE_SEARCH_ROWS = 15
private const val BALANCES_DEBOUNCE_MS = 1_500L
private const val SETTING_HOME_LAYOUT = "home_layout"
private const val SETTING_HIDE_AMOUNTS = "hide_amounts"
private const val SETTING_RECENT_SEARCHES = "recent_searches"
private const val SETTING_SUBS_IGNORED = "subs_ignored"
private const val SETTING_GOALS = "goals"
private const val RECENT_SEARCHES_KEPT = 6
private const val SETTING_APPEARANCE = "appearance"
/** The order accounts are shown in: where money sits first, then cards, then loans; by name within each. */
private val ACCOUNT_ORDER = listOf("BANK", "CASH", "WALLET", "CREDIT_CARD", "LOAN")
private const val SETTING_WELCOME_DISMISSED = "welcome_dismissed"
private const val SETTING_REDUCE_MOTION = "reduce_motion"
private const val SETTING_MOTION_SPEED = "motion_speed"
private const val SETTING_SIMILAR_OFF = "similar_off:"
private const val SIMILAR_DELAY_MS = 1_200L
private const val SETTING_NUDGE_SNOOZED_AT = "backup_nudge_snoozed_at"
/** The first-week checklist: "pending" while the ledger has had no entry yet (a new install), then when the first one was noticed; "0" on a ledger that already had entries before the checklist existed. */
private const val SETTING_FIRST_WEEK_STARTED = "first_week_started"
private const val SETTING_FIRST_WEEK_DISMISSED = "first_week_dismissed"
private const val MAX_STATEMENT_BYTES = 20_000_000
private const val MAX_STATEMENT_ROWS = 200_000
private const val PREVIEW_ROWS = 6
private const val CARD_ROWS_LISTED = 200
private const val PROBLEMS_SHOWN = 20
private const val SEARCH_DEBOUNCE_MS = 200L
private const val LAST_CATEGORY = "last_category"
private const val LAST_ACCOUNT = "last_account"

/**
 * Screen state for the ledger. The database lives in [AppContainer]; every read and write here runs off the main
 * thread, and writes are serialised so rapid taps cannot overlap. New rows are requested with keyset paging.
 */
@OptIn(ExperimentalUuidApi::class)
class LedgerViewModel(private val container: AppContainer, private val platform: Platform) : ViewModel() {
    private val inr get() = BaseCurrency.current
    private val _state = MutableStateFlow<UiState>(UiState.Opening)
    val state: StateFlow<UiState> = _state

    private val _editor = MutableStateFlow<Editable?>(null)

    /** The entry being edited, or null. */
    val editor: StateFlow<Editable?> = _editor

    private val _data = MutableStateFlow(DataUi())
    val data: StateFlow<DataUi> = _data
    private var openedUri: String? = null
    private var openedReady: DataTransfer.Opened.Ready? = null
    private val actions get() = DataTransfer(platform, container)

    private val _import = MutableStateFlow(ImportUi())
    val statement: StateFlow<ImportUi> = _import
    private var importTable: app.expenses.core.domain.CsvTable? = null
    private var importParsed: ParsedStatement? = null
    /** For a PDF or a photo: what was read out of it, so another account (a card or not) can read it again; the statement text each row came from; the PDF itself while it waits for a password. */
    private var importText: String? = null
    private var importRawLines: List<String>? = null
    private var importReadFrom: String? = null
    private var importReadNote: String? = null
    private var importCardPayments = 0
    /** The rows to save as the preview shows them (null while card payments await a choice), the categories the person may tick to create, and the file as another app's export. */
    private var importReady: List<StatementTxn>? = null
    private var importNewCategories: List<app.expenses.core.domain.ImportCategories.NewCategory> = emptyList()
    private var importPreset: app.expenses.core.domain.ImportPresets.Read? = null
    private var importPresetOff = false
    private var importSourceTable: app.expenses.core.domain.CsvTable? = null
    private var importCardPick: Pair<app.expenses.core.domain.CardPayments.Side, app.expenses.core.domain.CardPayments.Pick>? = null
    private var importCreateCategories = false
    private var importRead: StatementTextParser.Result? = null
    private var importConvention: StatementTextParser.Convention? = null
    private var importProtectedPdf: ByteArray? = null

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search
    private var searchInput: SearchInput? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    private data class ReportRequest(
        val mode: ReportMode, val offset: Int, val drillTarget: DrillTarget? = null, val drillStart: Long? = null, val drillEnd: Long? = null,
        val group: ReportGroup = ReportGroup.CATEGORY, val customStart: Long? = null, val customEnd: Long? = null, val drillLabel: String? = null,
    )
    private var reportRequest: ReportRequest? = null
    private val _report = MutableStateFlow<ReportUi?>(null)
    val report: StateFlow<ReportUi?> = _report

    /** How many report builds are running: a report over a big ledger takes a moment, and the screen says so (after a short wait) while the old figures are still shown. */
    private val _reportBuilds = MutableStateFlow(0)
    val reportBuilds: StateFlow<Int> get() = _reportBuilds

    private var accountNames = emptyMap<String, String>()
    private var currencyDigits = emptyMap<String, Int>()
    private var categoryNames = emptyMap<String, String>()
    private var loadedCategory = emptyMap<String, String>()
    private var loaded = mutableListOf<Entry>()
    private var endReached = false

    /** Last resort after key loss, only on the user's explicit choice: deletes all data. */
    fun startFresh() {
        viewModelScope.launch(BACKGROUND) {
            _state.value = UiState.Opening
            when (val o = container.startFresh()) {
                is AppContainer.Opened.Ok -> container.write { ok -> refresh(ok) }
                is AppContainer.Opened.KeyLost -> _state.value = UiState.KeyLost(o.message)
            }
        }
    }

    /**
     * Saves the add or edit form: a new entry when [EntryDraft.id] is null, otherwise an edit. A new expense can also become a quick entry.
     * On a rule it breaks, the reason is shown and the form is left as it was (the editor stays open).
     */
    fun saveEntry(draft: EntryDraft, saveAsTemplate: Boolean) {
        // photos and files chosen before saving go with the new entry; taken now, on the caller's thread, so closing the form cannot discard them first
        val carried = if (draft.id == null) takeStaged() else emptyList()
        saveEntryNow(draft, saveAsTemplate, carried)
    }

    private fun saveEntryNow(draft: EntryDraft, saveAsTemplate: Boolean, carried: List<Staged>) = withStore { ok ->
        val startedAt = TimeSource.Monotonic.markNow()
        val id = try { EntryActions(ok).save(draft) } catch (e: Exception) { restoreStaged(carried); throw e } // a refused save keeps what was chosen, to try again
        carried.forEach { ok.attachments.add(it.id, id, it.name, it.mime, it.sizeBytes, it.sha256) }
        if (draft.foreignCode.isNotEmpty() && draft.id == null) { ok.templates.putSetting("rate_${draft.foreignCode}", draft.foreignRate.trim()); loadRates(ok) }
        val amount = parseAmount(draft.amountText, inr)!!
        if (saveAsTemplate && draft.kind == DraftKind.EXPENSE && draft.id == null && !EntryDrafts.isSplit(draft)) {
            val name = draft.note.trim().ifEmpty { draft.category }
            ok.templates.save(Template(Uuid.random().toString(), name, amount, CategoryId(draft.category), AccountId(draft.accountId), draft.note.trim().ifEmpty { null }))
        }
        _editor.value = null
        val verb = when (draft.kind) { DraftKind.EXPENSE -> "expense"; DraftKind.INCOME -> "income"; DraftKind.TRANSFER -> "transfer"; DraftKind.REFUND -> "refund"; DraftKind.LOAN -> if (draft.gave) "money given" else "money received" }
        refreshQuickly(ok, undo = if (draft.id == null) UndoUi(id, "Added $verb ${amount.formatted()}", reveal = true) else null)
        lastSaveMillis = startedAt.elapsedNow().inWholeNanoseconds / 1e6
        offerSimilar(draft, id)
    }

    /**
     * Saves the edit form of an expense, income or transfer whose type was changed: the entry is replaced by one of the new type (all or nothing), "Changed to a transfer · Undo" is offered,
     * and Undo puts the original back exactly. On a rule it breaks, the reason is shown and the form is left as it was (the editor stays open and nothing changed).
     */
    fun saveChangingKind(draft: EntryDraft) = withStore { ok ->
        val startedAt = TimeSource.Monotonic.markNow()
        _editorProblem.value = null
        val changed = try { EntryActions(ok).changeKind(draft) } catch (e: IllegalArgumentException) { _editorProblem.value = e.message ?: "The type could not be changed."; return@withStore }
        _editor.value = null
        val what = when (draft.kind) { DraftKind.EXPENSE -> "an expense"; DraftKind.INCOME -> "income"; else -> "a transfer" }
        refreshQuickly(ok, undo = UndoUi(changed.newId, "Changed to $what", kindChanges = listOf(changed)))
        lastSaveMillis = startedAt.elapsedNow().inWholeNanoseconds / 1e6
    }

    /**
     * Saves several new entries at once (the items of a bill, each as its own entry): all or none. Files chosen on the form, and [photoUri] (the photo the bill was
     * read from), are attached to the first one. One "Undo" takes them all back. On a rule they break, the reason is shown and nothing is saved.
     */
    fun saveEntries(drafts: List<EntryDraft>, photoUri: String?) {
        if (drafts.isEmpty()) return
        val carried = takeStaged()
        viewModelScope.launch(BACKGROUND) {
            val all = carried.toMutableList()
            if (photoUri != null) {
                val id = Uuid.random().toString()
                try {
                    AttachmentRules.checkAdd(all.size, null)?.let { throw IllegalArgumentException(it) }
                    val copied = attachmentFiles.copyIn(id, photoUri)
                    all += Staged(id, AttachmentRules.safeName(platform.displayName(photoUri) ?: photoUri.substringAfterLast('/').ifEmpty { null }), platform.mimeType(photoUri).orEmpty(), copied.sizeBytes, copied.sha256)
                } catch (e: Exception) { attachmentFiles.delete(id) } // the entries matter more than the picture: they are saved without it
            }
            try {
                container.write { ok ->
                    val ids = try { ok.store.atomically { drafts.map { EntryActions(ok).save(it) } } } catch (e: Exception) { restoreStaged(all); throw e }
                    all.forEach { ok.attachments.add(it.id, ids.first(), it.name, it.mime, it.sizeBytes, it.sha256) }
                    _editor.value = null
                    val total = Money(drafts.sumOf { parseAmount(it.amountText, inr)?.minorUnits ?: 0L }, inr)
                    refreshQuickly(ok, undo = UndoUi(ids.first(), "Added ${ids.size} expenses, ${total.formatted()}", bulkCreated = ids, reveal = true))
                }
            } catch (e: IllegalArgumentException) {
                _state.update { (it as? UiState.Ready)?.copy(message = e.message) ?: it }
            }
        }
    }

    /** Opens the refund form for an expense. */
    fun startRefund(expenseId: String) = withStore { ok ->
        _editor.value = EntryActions(ok).startRefund(expenseId)
    }

    /** One tap: records the template's expense right away. Templates without an amount are handled by the add screen. */
    fun quickAdd(templateId: String) = withStore { ok ->
        val added = EntryActions(ok).quickAdd(templateId) ?: return@withStore
        refresh(ok, undo = UndoUi(added.entryId, added.text))
    }

    fun deleteTemplate(templateId: String) = withStore { ok ->
        ok.templates.delete(templateId)
        refresh(ok)
    }

    /** Takes back an entry that was just added. */
    fun undo(u: UndoUi) = withStore { ok ->
        if (u.kindChanges.isNotEmpty()) ok.store.atomically { u.kindChanges.asReversed().forEach { EntryActions(ok).undoKindChange(it) } }
        else if (u.bulkCreated.isNotEmpty()) ok.store.atomically { u.bulkCreated.forEach { EntryActions(ok).undo(it) } }
        else if (u.bulkDrafts.isNotEmpty()) ok.store.atomically { u.bulkDrafts.forEach { EntryActions(ok).save(it) } }
        else if (u.bulkRestore.isNotEmpty()) ok.store.atomically { u.bulkRestore.forEach { EntryActions(ok).restore(it) } }
        else if (u.recurringId != null) ok.recurring.undo(u.recurringId, kotlinx.datetime.LocalDate.fromEpochDays(u.recurringDay))
        else if (u.restore) EntryActions(ok).restore(u.entryId) else EntryActions(ok).undo(u.entryId)
        refresh(ok)
        _search.update { it.copy(notice = null, undo = null) }
    }

    /** Opens the editor for an entry: the simple form, or a read-only explanation. */
    fun openEditor(entryId: String) = withStore { ok ->
        _editor.value = EntryActions(ok).editable(entryId)
        _attachmentProblem.value = null
        _editorProblem.value = null
        _attachments.value = attachmentsOf(ok, entryId)
    }

    // ---- attachments ----

    private val attachmentFiles get() = platform.attachmentFiles
    private val _attachments = MutableStateFlow<List<AttachmentUi>>(emptyList())

    private val _attachmentProblem = MutableStateFlow<String?>(null)

    /** Why the last attachment was refused, shown in the editor itself (the list's message bar is hidden behind it). */
    val attachmentProblem: StateFlow<String?> get() = _attachmentProblem

    private val _editorProblem = MutableStateFlow<String?>(null)

    /** Why the last change of an entry's type was refused: shown in the editor itself (Home's message bar is hidden behind it). */
    val editorProblem: StateFlow<String?> get() = _editorProblem

    /** The attachments of the entry being edited. */
    val attachments: StateFlow<List<AttachmentUi>> get() = _attachments

    private fun attachmentsOf(ok: AppContainer.Opened.Ok, entryId: String) = ok.attachments.list(entryId).map { AttachmentUi(it.id, it.name, AttachmentRules.sizeText(it.sizeBytes), it.mime) }

    /** Copies a picked photo or file in and attaches it to [entryId]. A refusal is shown as a message and leaves nothing behind. */
    fun addAttachment(entryId: String, uri: String) {
        viewModelScope.launch(BACKGROUND) {
            val id = Uuid.random().toString()
            _attachmentProblem.value = null
            try {
                val mime = platform.mimeType(uri).orEmpty()
                val name = platform.displayName(uri)
                val copied = attachmentFiles.copyIn(id, uri)
                container.write { ok ->
                    ok.attachments.add(id, entryId, name ?: uri.substringAfterLast('/').ifEmpty { null } ?: "attachment", mime, copied.sizeBytes, copied.sha256)
                    _attachments.value = attachmentsOf(ok, entryId)
                }
            } catch (e: Exception) { // any failure: the copy is removed and the reason shown, never an app crash from a background coroutine
                attachmentFiles.delete(id)
                _attachmentProblem.value = if (e is IllegalArgumentException) e.message else "Could not attach that file: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    // ---- files chosen while adding a new entry, before it exists ----

    private class Staged(val id: String, val name: String, val mime: String, val sizeBytes: Long, val sha256: String)
    private val staged = MutableStateFlow<List<Staged>>(emptyList())
    private val _stagedUi = MutableStateFlow<List<AttachmentUi>>(emptyList())

    /** The photos and files chosen so far for the entry being added; they are attached when it is saved and deleted if the form is closed. */
    val stagedAttachments: StateFlow<List<AttachmentUi>> get() = _stagedUi

    private fun publishStaged() { _stagedUi.value = staged.value.map { AttachmentUi(it.id, it.name, AttachmentRules.sizeText(it.sizeBytes), it.mime) } }
    private fun takeStaged(): List<Staged> = staged.getAndUpdate { emptyList() }.also { publishStaged() }
    private fun restoreStaged(items: List<Staged>) { staged.update { items + it }; publishStaged() }

    /** Copies a picked or photographed file in for the entry about to be added. A refusal is shown as a message and leaves nothing behind. */
    fun stageAttachment(uri: String) {
        viewModelScope.launch(BACKGROUND) {
            val id = Uuid.random().toString()
            _attachmentProblem.value = null
            try {
                AttachmentRules.checkAdd(staged.value.size, null)?.let { throw IllegalArgumentException(it) }
                val mime = platform.mimeType(uri).orEmpty()
                val name = platform.displayName(uri)
                val copied = attachmentFiles.copyIn(id, uri)
                staged.update { it + Staged(id, AttachmentRules.safeName(name ?: uri.substringAfterLast('/').ifEmpty { null }), mime, copied.sizeBytes, copied.sha256) }
                publishStaged()
            } catch (e: Exception) {
                attachmentFiles.delete(id)
                _attachmentProblem.value = if (e is IllegalArgumentException) e.message else "Could not attach that file: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    fun unstageAttachment(id: String) { staged.update { list -> list.filterNot { it.id == id } }; publishStaged(); attachmentFiles.delete(id) }

    /** The add form was closed without saving: the files chosen for it are deleted. */
    fun discardStaged() { takeStaged().forEach { attachmentFiles.delete(it.id) }; _attachmentProblem.value = null }

    fun removeAttachment(entryId: String, id: String) = withStore { ok ->
        ok.attachments.delete(id)
        attachmentFiles.delete(id)
        _attachments.value = attachmentsOf(ok, entryId)
    }

    /** The address to hand to a viewer app for one attachment. */
    fun attachmentUri(id: String): String = attachmentFiles.shareUri(id)

    fun closeEditor() {
        _editor.value = null
        _editorProblem.value = null
        _attachments.value = emptyList()
        _history.value = null
    }

    /** The history of the entry being edited, or null when not shown. */
    val history: StateFlow<HistoryUi?> get() = _history
    private val _history = MutableStateFlow<HistoryUi?>(null)

    fun openHistory(entryId: String) = withStore { ok -> _history.value = EntryActions(ok).history(entryId) }
    fun closeHistory() { _history.value = null }

    /** Puts back the version before an edit, then returns to the list. */
    fun restoreVersion(entryId: String, index: Int) = withStore { ok ->
        EntryActions(ok).restoreVersion(entryId, index)
        _history.value = null
        _editor.value = null
        refresh(ok, undo = null)
    }

    // ---- bulk edit ----

    /** Applies a bulk change and reports it: a snackbar on the list and a notice on the search screen, both with Undo. */
    private fun bulk(what: String, run: (EntryActions) -> EntryActions.BulkResult) = withStore { ok ->
        val r = run(EntryActions(ok))
        val undo = if (r.undoDrafts.isNotEmpty() || r.undoRestore.isNotEmpty()) UndoUi("", r.text(what), bulkDrafts = r.undoDrafts, bulkRestore = r.undoRestore) else null
        refresh(ok, undo = undo)
        _search.update { it.copy(notice = r.text(what), undo = undo) }
    }

    // ---- similar entries: after a category is chosen, offer it to the earlier entries that read alike ----

    private val _similar = MutableStateFlow<SimilarUi?>(null)
    /** What to offer after a save, or null. The person decides: nothing is changed until they accept. */
    val similar: StateFlow<SimilarUi?> get() = _similar
    private var similarJob: kotlinx.coroutines.Job? = null
    private val similarQuiet = mutableSetOf<String>() // "not now" for the rest of this session: label key + category

    private fun similarKey(label: String) = CategorySuggest.merchantKey(label)

    private fun similarCandidates(ok: AppContainer.Opened.Ok, label: String, kind: String): List<SimilarCandidate> {
        val words = SimilarEntries.searchWords(label)
        if (words.isEmpty()) return emptyList()
        return ok.db.schemaQueries.similarCandidates(kind, words.joinToString(" OR ") { app.expenses.core.storage.ftsPhrase(it) }).executeAsList()
            .map { SimilarCandidate(it.id, it.label, it.category_id.orEmpty(), it.amount_minor, it.occurred_at) }
    }

    /**
     * Looks for earlier entries like the one just saved, a moment after the save (so the save is never slowed and a quick second save replaces the look).
     * Only an expense or an income with one category is considered.
     */
    private fun offerSimilar(draft: EntryDraft, entryId: String) {
        if (draft.kind != DraftKind.EXPENSE && draft.kind != DraftKind.INCOME) return
        if (EntryDrafts.isSplit(draft) || draft.category.isEmpty()) return
        val label = draft.note.trim()
        if (SimilarEntries.searchWords(label).isEmpty()) return
        similarJob?.cancel()
        similarJob = viewModelScope.launch(BACKGROUND) {
            kotlinx.coroutines.delay(SIMILAR_DELAY_MS)
            container.write { ok ->
                val key = similarKey(label)
                if ("$key|${draft.category}" in similarQuiet || ok.templates.setting(SETTING_SIMILAR_OFF + key) != null) return@write
                val found = SimilarEntries.similar(label, draft.category, entryId, similarCandidates(ok, label, if (draft.kind == DraftKind.INCOME) "INCOME" else "EXPENSE"))
                if (found.isEmpty()) return@write
                val names = ok.categories.list().associate { it.id to it.name }
                val fmt = DatePattern("d MMM yyyy")
                val zone = systemZone()
                _similar.value = SimilarUi(
                    label, draft.category, names[draft.category] ?: draft.category,
                    found.map { SimilarRowUi(it.id, it.label, fmt.format(it.occurredAt), Money(it.amountMinor, BaseCurrency.current).formatted(), names[it.categoryId] ?: it.categoryId) },
                )
            }
        }
    }

    /** Leaves the offer alone: nothing changes. [stopAsking] also stops offering for this name from now on. */
    fun dismissSimilar(stopAsking: Boolean = false) {
        val s = _similar.value ?: return
        _similar.value = null
        similarQuiet += "${similarKey(s.label)}|${s.categoryId}"
        if (stopAsking) withStore { ok -> ok.templates.putSetting(SETTING_SIMILAR_OFF + similarKey(s.label), s.label.trim().ifEmpty { "?" }) }
    }

    private val _stoppedSimilar = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    /** The names the person told the app to stop offering a category for: (key, name as it was written). */
    val stoppedSimilar: StateFlow<List<Pair<String, String>>> get() = _stoppedSimilar

    fun loadStoppedSimilar() {
        viewModelScope.launch(BACKGROUND) { container.write { ok -> _stoppedSimilar.value = ok.templates.settingsWithPrefix(SETTING_SIMILAR_OFF).map { (k, v) -> k.removePrefix(SETTING_SIMILAR_OFF) to v } } }
    }

    /** Turns the offer back on for a name. */
    fun askAgain(key: String) {
        viewModelScope.launch(BACKGROUND) {
            container.write { ok ->
                ok.templates.removeSetting(SETTING_SIMILAR_OFF + key)
                _stoppedSimilar.value = ok.templates.settingsWithPrefix(SETTING_SIMILAR_OFF).map { (k, v) -> k.removePrefix(SETTING_SIMILAR_OFF) to v }
            }
        }
        similarQuiet.removeAll { it.startsWith("$key|") }
    }

    /** Files the chosen earlier entries under the offered category, as one change that Undo takes back. */
    fun applySimilar(ids: List<String>) {
        val s = _similar.value ?: return
        _similar.value = null
        similarQuiet += "${similarKey(s.label)}|${s.categoryId}"
        if (ids.isNotEmpty()) bulkSetCategory(ids, s.categoryId)
    }

    fun bulkSetCategory(ids: List<String>, category: String) = bulk("Changed the category of") { it.bulkSetCategory(ids, category) }
    fun bulkSetAccount(ids: List<String>, account: String) = bulk("Moved") { it.bulkSetAccount(ids, account) }
    fun bulkAddTag(ids: List<String>, tag: String) = bulk("Tagged") { it.bulkAddTag(ids, tag) }
    fun bulkRemoveTag(ids: List<String>, tag: String) = bulk("Removed the tag from") { it.bulkRemoveTag(ids, tag) }
    fun bulkDelete(ids: List<String>) = bulk("Deleted") { it.bulkDelete(ids) }

    /**
     * Makes the selected expenses transfers from the account each was paid from to [toAccount] (a cash withdrawal, money moved to another account), all in one step with one Undo; the
     * ones that can't be are skipped and the notice says why. Nothing is learned from it.
     */
    fun bulkMakeTransfers(ids: List<String>, toAccount: String) = withStore { ok ->
        val r = EntryActions(ok).bulkMakeTransfer(ids, toAccount)
        val name = ok.db.schemaQueries.selectAccounts().executeAsList().firstOrNull { it.id == toAccount }?.name ?: "that account"
        val head = if (r.changed == 0) "Nothing changed." else "Changed ${r.changed} ${if (r.changed == 1) "expense" else "expenses"} to ${if (r.changed == 1) "a transfer" else "transfers"} to $name."
        val text = if (r.skipped == 0) head else "$head ${r.skipped} skipped${r.why?.let { ": $it" }.orEmpty()}."
        val undo = if (r.undoKinds.isNotEmpty()) UndoUi("", text, kindChanges = r.undoKinds) else null
        refresh(ok, undo = undo)
        _search.update { it.copy(notice = text, undo = undo) }
    }

    /** Deletes an entry and offers to bring it back. */
    fun deleteEntry(row: RowUi) = withStore { ok ->
        EntryActions(ok).delete(row.id)
        refresh(ok, undo = UndoUi(row.id, "Deleted ${row.title} ${row.amount}", restore = true))
    }

    /** The account Home's list shows, or null for all of them. Read and changed only under the write lock. */
    @Volatile private var homeAccount: String? = null

    /** The dates Home's list is limited to while it shows one account. Read and changed only under the write lock. */
    @Volatile private var homePeriod: HomePeriod = HomePeriod.ALL

    /** `[from, until)` in epoch milliseconds for the chosen [homePeriod] in the phone's zone as of now, or null for every date. */
    private fun homePeriodRange(): Pair<Long, Long>? = when (homePeriod) {
        HomePeriod.ALL -> null
        HomePeriod.THIS_MONTH -> app.expenses.core.domain.SearchDates.monthMillis(todayLocal(), 0, systemZone())
        HomePeriod.LAST_MONTH -> app.expenses.core.domain.SearchDates.monthMillis(todayLocal(), 1, systemZone())
    }

    private fun recentRows(q: app.expenses.core.storage.db.SchemaQueries, want: Long) =
        homeAccount?.let { a ->
            val r = homePeriodRange()
            if (r == null) merged(q.recentEntriesByAccount(a, want).executeAsList(), q.recentEntriesByCounter(a, want).executeAsList(), want)
            else merged(q.recentEntriesByAccountInRange(a, r.first, r.second, want).executeAsList(), q.recentEntriesByCounterInRange(a, r.first, r.second, want).executeAsList(), want)
        } ?: q.recentEntries(want).executeAsList()

    /** The two indexed lookups of an account's entries (paid from it, transferred into it) as one list in the order of Home's list, [limit] long. */
    private fun merged(a: List<app.expenses.core.storage.db.Entry>, b: List<app.expenses.core.storage.db.Entry>, limit: Long) =
        (a + b).sortedWith(compareByDescending<app.expenses.core.storage.db.Entry> { it.occurred_at }.thenByDescending { it.seq }).take(limit.toInt())

    /** Narrows Home's list to one account (what went out of it or into it), or back to every account with null. */
    fun showAccountOnHome(accountId: String?) {
        viewModelScope.launch(BACKGROUND) {
            container.write { ok ->
                homeAccount = accountId
                homePeriod = HomePeriod.ALL // a period belongs to the account view it was chosen in
                loaded = mutableListOf() // start the list from the top for the new view
                refresh(ok)
            }
        }
    }

    /** Limits Home's list, while it shows one account, to a month (or lifts the limit with [HomePeriod.ALL]). Does nothing on Home's all-accounts list. */
    fun setHomePeriod(period: HomePeriod) {
        viewModelScope.launch(BACKGROUND) {
            container.write { ok ->
                if (homeAccount == null) return@write
                homePeriod = period
                loaded = mutableListOf()
                refresh(ok)
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? UiState.Ready ?: return
        if (endReached) return
        // Through the write lock like every other change to [loaded]: a refresh replaces and reads that list, and adding to it from outside the
        // lock threw ConcurrentModificationException when the two overlapped (seen in the timing gate, which then crashed the app process).
        viewModelScope.launch(BACKGROUND) {
            container.write { ok ->
                if (endReached) return@write
                val last = loaded.lastOrNull() ?: return@write
                val page = homeAccount?.let { a ->
                    val q = ok.db.schemaQueries
                    val r = homePeriodRange()
                    if (r == null) merged(q.entriesBeforeByAccount(a, last.occurred_at, last.seq, PAGE).executeAsList(), q.entriesBeforeByCounter(a, last.occurred_at, last.seq, PAGE).executeAsList(), PAGE)
                    else merged(q.entriesBeforeByAccountInRange(a, r.first, r.second, last.occurred_at, last.seq, PAGE).executeAsList(), q.entriesBeforeByCounterInRange(a, r.first, r.second, last.occurred_at, last.seq, PAGE).executeAsList(), PAGE)
                } ?: ok.db.schemaQueries.entriesBefore(last.occurred_at, last.seq, PAGE).executeAsList()
                if (page.size < PAGE) endReached = true
                if (page.isEmpty()) return@write
                loaded.addAll(page)
                loadedCategory = loadedCategory + firstCategories(ok, page)
                _state.update { s -> (s as? UiState.Ready)?.copy(rows = s.rows + page.map(::toRow), endReached = endReached) ?: current }
            }
        }
    }

    // ---- categories ----

    /** Creates a category and, once saved, tells the caller its id (used to select it in the add form). */
    fun createCategory(name: String, parentId: String?, colorArgb: Long?, kind: app.expenses.core.domain.CategoryKind = app.expenses.core.domain.CategoryKind.EXPENSE, onCreated: (String) -> Unit = {}) = withStore { ok ->
        val created = ok.categories.create(name, parentId, colorArgb, kind)
        refresh(ok)
        viewModelScope.launch(Dispatchers.Main) { onCreated(created.id) }
    }

    /** Adds the ready-made categories the person ticked ([picks]: group name to the subcategories wanted), in one step, skipping any that already exist. */
    fun addSuggestedCategories(picks: Map<String, Set<String>>) = withStore { ok ->
        var added = 0
        ok.db.transaction {
            val ids = HashMap<String, String>()
            for (p in app.expenses.core.domain.CategoryCatalog.plan(ok.categories.list(), picks)) {
                val parent = ids[p.parent] ?: ok.categories.list().firstOrNull { it.parentId == null && it.name.equals(p.parent, ignoreCase = true) }?.id
                val child = p.child
                if (child == null) { ids[p.parent] = ok.categories.create(p.parent, kind = if (p.income) app.expenses.core.domain.CategoryKind.INCOME else app.expenses.core.domain.CategoryKind.EXPENSE).id; added++ }
                else { ok.categories.create(child, parent ?: error("The group ${p.parent} was not created")); added++ }
            }
        }
        refresh(ok)
        _state.update { (it as? UiState.Ready)?.copy(message = if (added == 0) "Nothing new to add" else if (added == 1) "Added 1 category" else "Added $added categories") ?: it }
    }

    /** Applies all the edits of the category dialog as one change: either every part is saved or none. */
    fun updateCategory(id: String, name: String, colorArgb: Long?, hidden: Boolean, parentId: String?) = withStore { ok ->
        ok.db.transaction {
            ok.categories.rename(id, name)
            ok.categories.setColor(id, colorArgb)
            ok.categories.setHidden(id, hidden)
            ok.categories.setParent(id, parentId)
        }
        refresh(ok)
    }

    /** Changes what a top-level category is offered for (its subcategories follow). [onDone] gets null, or the reason it was refused, on the main thread. */
    fun setCategoryKind(id: String, kind: app.expenses.core.domain.CategoryKind, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try { container.write { ok -> ok.categories.setKind(id, kind); refresh(ok) }; null } catch (e: IllegalArgumentException) { e.message ?: "That can't be changed" }
            launch(Dispatchers.Main) { onDone(problem) }
        }
    }

    fun moveCategory(id: String, direction: Int) = withStore { ok ->
        ok.categories.move(id, direction)
        refresh(ok)
    }

    fun mergeCategory(fromId: String, intoId: String) = withStore { ok ->
        ok.categories.merge(fromId, intoId)
        refresh(ok)
    }

    fun deleteCategory(id: String) = withStore { ok ->
        ok.categories.delete(id)
        refresh(ok)
    }

    // ---- accounts ----

    // Balances read the whole ledger (about 25 ms on a 100,000-entry ledger even with covering indexes), so they are not part of the
    // save path: they are worked out only while something shows them, a moment after the last write, and the screen updates when ready.
    private var accountRowsCache: List<AccountRowUi> = emptyList()
    private var sparkCache: SparkUi = SparkUi()
    private var categoryUsageCache: Map<String, Long> = emptyMap()
    private var categoriesOpen = false
    private var nudgeCache = false
    private var safeCache: SafeToSpendUi? = null
    private var figuresReady = false
    private var accountsOpen = false
    private var balancesVisible = false
    private var balancesJob: kotlinx.coroutines.Job? = null

    fun setAccountsScreenOpen(open: Boolean) { accountsOpen = open; if (open) scheduleSlowFigures() }
    fun setCategoriesScreenOpen(open: Boolean) { categoriesOpen = open; if (open) scheduleSlowFigures() }

    /**
     * The two figures that read the whole ledger, the account balances and the live entry count behind the backup reminder, are not part of a
     * write: they are worked out a moment after the last one, and the screen updates when they are ready. Balances are skipped while nothing
     * shows them. (On a 100,000-entry ledger the count alone took 27 ms and the balances 25 ms, against a 50 ms save budget.)
     */
    private fun scheduleSlowFigures() {
        balancesJob?.cancel()
        val wait = if (accountsOpen || categoriesOpen || !figuresReady) 0L else BALANCES_DEBOUNCE_MS
        balancesJob = viewModelScope.launch(BACKGROUND) {
            kotlinx.coroutines.delay(wait)
            val figures = container.write { ok ->
                val live = ok.db.schemaQueries.entryCount().executeAsOne()
                val nudge = BackupNudge.shouldShow(
                    live,
                    ok.templates.setting(DataTransfer.SETTING_BACKUP_AT)?.toLongOrNull()?.let { at -> LastBackup(at, ok.templates.setting(DataTransfer.SETTING_BACKUP_COUNT)?.toLongOrNull() ?: 0L) },
                    ok.templates.setting(SETTING_NUDGE_SNOOZED_AT)?.toLongOrNull(), nowMillis(),
                )
                // the balances are read once for whatever shows them: the accounts, Home's strip, and the safe-to-spend line (unless the person turned that off)
                // (by default the line is decided from the balances the Home strip reads anyway; with the strip hidden and no choice made, nothing extra is read for it)
                val safeChoice = ok.templates.setting(SETTING_SAFE_HOME)
                val wantSafe = accountsOpen || safeChoice == "1" || (safeChoice == null && balancesVisible)
                val accountList = ok.db.schemaQueries.selectAccounts().executeAsList()
                val balances = if (accountsOpen || balancesVisible || wantSafe) ok.store.balances() else null
                val rows = if (balances != null && (accountsOpen || balancesVisible)) accountRows(ok, accountList, balances) else accountRowsCache
                val safe = if (balances != null && wantSafe) safeToSpendUi(ok, accountList, balances, todayLocal(), inr) else null
                val usage = if (categoriesOpen) ok.categories.list().associate { it.id to ok.categories.usage(it.id) } else categoryUsageCache
                Figures(nudge, rows, usage, sparkOf(ok), safe)
            } ?: return@launch
            nudgeCache = figures.nudge; safeCache = figures.safe; accountRowsCache = figures.rows; categoryUsageCache = figures.usage; sparkCache = figures.spark; figuresReady = true
            withContext(Dispatchers.Main) { // published on the main thread, like the search result: see search()
                _state.update { st ->
                    (st as? UiState.Ready)?.copy(accountRows = figures.rows, safeToSpend = figures.safe, spark = figures.spark, showBackupNudge = figures.nudge, categories = st.categories.map { c -> c.copy(usage = figures.usage[c.id] ?: c.usage) }) ?: st
                }
            }
        }
    }

    private class Figures(val nudge: Boolean, val rows: List<AccountRowUi>, val usage: Map<String, Long>, val spark: SparkUi, val safe: SafeToSpendUi?)

    /** Spending on each of the last 14 days, for the line on the home screen. One range query; worked out with the other slow figures, never on the save path. */
    private fun sparkOf(ok: AppContainer.Opened.Ok): SparkUi {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = todayLocal()
        val today = t
        val first = today.minus(13, kotlinx.datetime.DateTimeUnit.DAY)
        val perDay = ok.reports.spendPoints(DatePeriod(first, today.plus(1, kotlinx.datetime.DateTimeUnit.DAY)), zone, inr)
            .groupBy { dateOfMillis(it.atMillis).epochDay() }.mapValues { (_, v) -> v.sumOf { it.minor } }
        val dayFmt = DatePattern("d MMM")
        // this period against the last one at the same point in it
        val budgetPeriod = ok.budgets.period()
        val compare = PeriodCompare.lastName(budgetPeriod)?.let { last ->
            val cur = budgetPeriod.containing(today)
            val before = budgetPeriod.containing(cur.start.minus(1, kotlinx.datetime.DateTimeUnit.DAY))
            val daysIn = cur.start.daysUntil(today) + 1
            val comparableEnd = minOf(before.start.plus(daysIn, kotlinx.datetime.DateTimeUnit.DAY), before.endExclusive)
            val now = ok.reports.spent(DatePeriod(cur.start, today.plus(1, kotlinx.datetime.DateTimeUnit.DAY)), zone, inr).minorUnits
            val earlier = ok.reports.spent(DatePeriod(before.start, comparableEnd), zone, inr).minorUnits
            PeriodCompare.describe(now, earlier, last) { Money(it, inr).formatted() }
        }.orEmpty()
        // where spending differs from usual: each category so far against the average of the earlier periods at the same point (needs two earlier periods with spending)
        val insight = PeriodCompare.lastName(budgetPeriod)?.let {
            val cur = budgetPeriod.containing(today)
            val daysIn = cur.start.daysUntil(today) + 1
            val now = ok.reports.breakdown(DatePeriod(cur.start, today.plus(1, kotlinx.datetime.DateTimeUnit.DAY)), zone, inr).associate { it.name to it.total.minorUnits }
            var edge = cur.start
            val earlier = (1..3).map {
                val p = budgetPeriod.containing(edge.minus(1, kotlinx.datetime.DateTimeUnit.DAY)); edge = p.start
                ok.reports.breakdown(DatePeriod(p.start, minOf(p.start.plus(daysIn, kotlinx.datetime.DateTimeUnit.DAY), p.endExclusive)), zone, inr).associate { r -> r.name to r.total.minorUnits }
            }.filter { m -> m.values.sum() > 0 }
            if (earlier.size < 2) null else SpendingInsight.best(now.map { (name, minor) -> InsightLine(name, minor, earlier.sumOf { m -> m[name] ?: 0L } / earlier.size) }) { Money(it, inr).formatted() }
        }.orEmpty()
        return SparkUi((0..13).map { i ->
            val d = first.plus(i, kotlinx.datetime.DateTimeUnit.DAY)
            val minor = perDay[d.toEpochDays()] ?: 0L
            SparkDayUi(d.toEpochDays(), minor, dayFmt.format(localDateOfEpochDay(d.toEpochDays())) + " · " + Money(minor, inr).formatted())
        }, compare, insight)
    }

    private fun accountRows(ok: AppContainer.Opened.Ok, rows: List<app.expenses.core.storage.db.Account>, balances: Map<AccountId, Money>): List<AccountRowUi> {
        val looks = ok.looks.all().associateBy { it.accountId }
        val today = todayLocal()
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        return rows.map { a ->
            val type = AccountType.valueOf(a.type)
            val bal = balances[AccountId(a.id)] ?: Money.zero(inr)
            val shownOpening = if (type.isLiability) -a.opening_minor else a.opening_minor
            AccountRowUi(
                a.id, a.name, a.type, ACCOUNT_TYPE_LABELS.getValue(a.type), type.isLiability, a.archived == 1L,
                bal.formatted(), bal.minorUnits, AccountRules.isOwed(Account(AccountId(a.id), a.name, type, inr), bal.minorUnits),
                if (shownOpening == 0L) "" else Money(shownOpening, inr).toPlainString(), ok.store.accountUsage(AccountId(a.id)),
                ok.db.schemaQueries.accountSavedItemUsage(a.id).executeAsOne(),
                if (type != AccountType.CREDIT_CARD) null else ok.cards.terms(a.id)?.let { t ->
                    val summary = ok.cards.summary(a.id, today, zone)
                    // the summary carries the statement's standing (the same figures standing() reads), so it is not worked out a second time
                    CardRowUi(t.statementDay, t.dueDay, t.minDuePercent, t.remindDaysBefore, CardText.terms(t), summary?.standing?.let { CardText.status(it, today) { m -> Money(m, inr).formatted() } }.orEmpty(), ok.cards.creditLimit(a.id)?.let { Money(it, inr).toPlainString() }.orEmpty(),
                        summary?.let { CardSummaryText.cycle(it) { m -> Money(m, inr).formatted() } }.orEmpty(), summary?.let { CardSummaryText.limit(it) { m -> Money(m, inr).formatted() } }.orEmpty(), summary?.limitFraction ?: 0f,
                        summary?.let { CardSummaryText.due(it) { m -> Money(m, inr).formatted() } }.orEmpty())
                },
                looks[a.id]?.network?.name, looks[a.id]?.tier?.name, looks[a.id]?.last4,
            )
        }.sortedWith(compareBy({ it.archived }, { ACCOUNT_ORDER.indexOf(it.type).let { i -> if (i < 0) ACCOUNT_ORDER.size else i } }, { it.name.lowercase() }))
    }

    /**
     * Creates ([id] null) or edits an account. [openingText] is the amount held, or for a card or loan the amount owed. [onDone] gets null
     * on success, otherwise the reason it was refused (shown in the form, which stays open).
     */
    fun saveAccount(id: String?, name: String, type: String, openingText: String, network: String?, tier: String?, last4Text: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    val all = ok.db.schemaQueries.selectAccounts().executeAsList()
                    val clean = AccountRules.normalizeName(name)
                    AccountRules.checkName(all.map { Account(AccountId(it.id), it.name, AccountType.valueOf(it.type), inr) }, clean, id)?.let { return@write it }
                    val kind = AccountType.valueOf(type)
                    val digits = AccountLooks.parseLast4(last4Text).getOrElse { return@write it.message }
                    val typed = openingText.trim()
                    val opening = if (typed.isEmpty()) 0L else (parseAmount(typed, inr)?.minorUnits ?: return@write "Enter the amount as a number, like 25000 or 1250.50")
                    if (id != null && all.first { it.id == id }.type.let { AccountType.valueOf(it).isLiability } != kind.isLiability &&
                        ok.store.accountUsage(AccountId(id)) > 0) return@write "A card or loan cannot become a bank, cash or wallet account once it has entries, or the other way round."
                    val existing = id?.let { ok.db.schemaQueries.accountById(it).executeAsOneOrNull() }
                    val accountId = id ?: Uuid.random().toString()
                    ok.store.saveAccount(Account(AccountId(accountId), clean, kind, inr, if (kind.isLiability) -opening else opening, existing?.archived == 1L))
                    // the network and tier are a credit card's; the digits belong to any account
                    val isCard = kind == AccountType.CREDIT_CARD
                    ok.looks.save(AccountLook(accountId, if (isCard) CardNetwork.entries.firstOrNull { it.name == network } else null, if (isCard) CardTier.entries.firstOrNull { it.name == tier } else null, digits))
                    if (id != null && kind != AccountType.CREDIT_CARD) ok.cards.clear(id) // billing dates belong to a credit card only
                    refresh(ok)
                    null
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    /** Sets or, with blank days, removes a credit card's billing dates. [onDone] gets null on success, otherwise the reason (shown in the dialog). */
    fun saveCardTerms(accountId: String, statementDay: String, dueDay: String, minPercent: String, remindDays: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    val s = statementDay.trim().toIntOrNull(); val d = dueDay.trim().toIntOrNull()
                    val p = minPercent.trim().ifEmpty { "5" }.toIntOrNull(); val r = remindDays.trim().ifEmpty { "3" }.toIntOrNull()
                    when {
                        s == null || s !in 1..28 -> "The statement day is the day of the month your statement is made, 1 to 28."
                        d == null || d !in 1..28 -> "The due day is the day of the month payment is due, 1 to 28."
                        p == null || p !in 0..100 -> "The minimum due percentage is 0 to 100 (most cards: 5)."
                        r == null || r !in 0..10 -> "Reminders can start 0 to 10 days before the due date."
                        else -> { ok.cards.save(CardTerms(accountId, s, d, p, r)); refresh(ok); null }
                    }
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    fun clearCardTerms(accountId: String) = withStore { ok -> ok.cards.clear(accountId); ok.cards.setCreditLimit(accountId, null); refresh(ok) }

    /** Sets (or, with empty text, removes) a card's credit limit, for the "near the limit" notice. Text that is not a positive amount removes it. */
    fun setCreditLimit(accountId: String, limit: String) = withStore { ok ->
        val minor = limit.trim().replace(",", "").takeIf { it.isNotEmpty() }?.let { app.expenses.core.domain.parseAmount(it, BaseCurrency.current)?.minorUnits }
        ok.cards.setCreditLimit(accountId, minor)
        refresh(ok)
    }

    /**
     * What Pay now on a card notice opens: "fromAccount|toAccount|amount|note" for a transfer to the card [cardAccountId] of what is left on its statement
     * (empty when nothing is known), from the account used last (or the first other one). Null when the card is gone or there is no account to pay from.
     */
    fun payCardDraft(cardAccountId: String, onReady: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val draft = container.write { ok ->
                val card = ok.db.schemaQueries.accountById(cardAccountId).executeAsOneOrNull() ?: return@write null
                if (card.archived != 0L) return@write null
                val active = ok.db.schemaQueries.selectAccounts().executeAsList().filter { it.archived == 0L && it.id != cardAccountId }
                val last = ok.templates.setting(LAST_ACCOUNT)?.takeIf { id -> active.any { it.id == id && it.type != "CREDIT_CARD" } } // the account used last is often a card itself
                val from = last ?: active.firstOrNull { it.type != "CREDIT_CARD" }?.id ?: active.firstOrNull()?.id ?: return@write null
                val left = ok.cards.standing(cardAccountId, todayLocal(), kotlinx.datetime.TimeZone.currentSystemDefault())?.remaining ?: 0L
                val amount = if (left > 0) Money(left, BaseCurrency.current).toPlainString() else ""
                listOf(from, cardAccountId, amount, "Card payment: ${card.name}").joinToString("|")
            }
            withContext(Dispatchers.Main) { onReady(draft) }
        }
    }

    fun setAccountArchived(id: String, archived: Boolean) = withStore { ok ->
        val row = ok.db.schemaQueries.accountById(id).executeAsOneOrNull() ?: return@withStore
        require(!archived || ok.db.schemaQueries.accountSavedItemUsage(id).executeAsOne() == 0L) { "Repeating items or quick entries still use this account. Change them first, then archive it." }
        require(!archived || ok.db.schemaQueries.selectAccounts().executeAsList().count { it.archived == 0L && it.id != id } > 0) { "Keep at least one active account." }
        ok.store.saveAccount(Account(AccountId(id), row.name, AccountType.valueOf(row.type), inr, row.opening_minor, archived))
        refresh(ok)
    }

    fun deleteAccount(id: String) = withStore { ok ->
        ok.store.deleteAccount(AccountId(id))
        refresh(ok)
    }

    // ---- saved searches ----

    /** Keeps the current search under [name]. [onDone] gets null on success or the reason it was refused (shown in the dialog, which stays open). */
    fun saveSearch(name: String, input: SearchInput, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    ok.savedSearches.save(SavedSearch(Uuid.random().toString(), name, input.text, input.minRupees, input.maxRupees, input.categoryId, input.kind.name, input.range.name, input.tag, input.fromDay, input.toDay, input.accountId))
                    refresh(ok)
                }
                null
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    fun deleteSavedSearch(id: String) = withStore { ok -> ok.savedSearches.delete(id); refresh(ok) }

    /** Keeps [text] among the recent searches (newest first, no repeats, the last few only). Words shorter than two letters are not kept. */
    fun rememberSearch(text: String) = withStore { ok ->
        val t = text.trim().replace('\n', ' ')
        if (t.length < 2) return@withStore
        val now = ok.templates.setting(SETTING_RECENT_SEARCHES).orEmpty().split('\n').filter { it.isNotBlank() }
        val next = (listOf(t) + now.filterNot { it.equals(t, ignoreCase = true) }).take(RECENT_SEARCHES_KEPT)
        if (next != now) { ok.templates.putSetting(SETTING_RECENT_SEARCHES, next.joinToString("\n")); refresh(ok) }
    }

    fun forgetSearch(text: String) = withStore { ok ->
        val now = ok.templates.setting(SETTING_RECENT_SEARCHES).orEmpty().split('\n').filter { it.isNotBlank() }
        ok.templates.putSetting(SETTING_RECENT_SEARCHES, now.filterNot { it == text }.joinToString("\n")); refresh(ok)
    }

    fun clearRecentSearches() = withStore { ok -> ok.templates.putSetting(SETTING_RECENT_SEARCHES, ""); refresh(ok) }

    // ---- automatic export ----

    private fun autoExportUi(ok: AppContainer.Opened.Ok): AutoExportUi {
        val t = ok.templates
        val freq = AutoExportFrequency.entries.firstOrNull { it.name == t.setting(AutoExportKeys.FREQ) } ?: AutoExportFrequency.OFF
        val folder = t.setting(AutoExportKeys.FOLDER)?.let { platform.folderDisplayName(it) ?: "the chosen folder" }
        val last = t.setting(AutoExportKeys.LAST_AT)?.toLongOrNull()?.let { DatePattern("d MMM yyyy, HH:mm").format(it) }
        val status = t.setting(AutoExportKeys.STATUS)
        return AutoExportUi(freq, folder, last, status?.substringAfter('|')?.ifEmpty { null }, status?.startsWith("ok|") ?: true, t.setting(AutoExportKeys.PROTECT) == "1", t.setting(AutoExportKeys.OLD_EXCEL)?.toIntOrNull() ?: 0)
    }

    /** "Delete them": removes the Excel files from before protection that the last protected run found in the folder (only the app's own file names), after the person asked. */
    fun deleteOldAutoExcel() {
        viewModelScope.launch(BACKGROUND) {
            val uri = container.write { it.templates.setting(AutoExportKeys.FOLDER) }
            platform.deleteOldAutoExcel(container, uri)
            container.write { refresh(it) }
        }
    }

    /** Turns "Protect automatic copies with a passphrase" on, or changes its passphrase, with [passphrase]; off with null. The key goes to the phone's secure storage, the passphrase nowhere ([AutoCopyControls]). */
    fun setAutoProtect(passphrase: String?) = dataAction {
        val message = if (passphrase != null) AutoCopyControls.turnOn(platform, container, passphrase) else AutoCopyControls.turnOff(platform, container)
        container.write { ok -> refresh(ok) }
        message
    }

    fun setAutoExportFolder(uri: String) = withStore { ok ->
        ok.templates.putSetting(AutoExportKeys.FOLDER, uri)
        if (ok.templates.setting(AutoExportKeys.FREQ) == null || ok.templates.setting(AutoExportKeys.FREQ) == AutoExportFrequency.OFF.name) ok.templates.putSetting(AutoExportKeys.FREQ, AutoExportFrequency.WEEKLY.name)
        refresh(ok)
    }

    fun setAutoExportFrequency(f: AutoExportFrequency) = withStore { ok -> ok.templates.putSetting(AutoExportKeys.FREQ, f.name); refresh(ok) }

    /** Runs an export now, whatever the schedule says, and shows how it went. */
    fun exportNow() {
        viewModelScope.launch(BACKGROUND) {
            val uri = container.write { it.templates.setting(AutoExportKeys.FOLDER) }
            platform.runAutoExport(container, uri, nowMillis(), force = true)
            container.write { refresh(it) }
        }
    }

    // ---- capture inbox ----

    private val _people = MutableStateFlow<List<PersonUi>>(emptyList())
    val people: StateFlow<List<PersonUi>> get() = _people
    private val _loans = MutableStateFlow(LoansUi())
    val loans: StateFlow<LoansUi> get() = _loans
    @Volatile private var loansWatching = false
    @Volatile private var selectedPerson: String? = null

    /** The lent-and-borrowed screen asks to be kept up to date while it is open (and only then: the figures cost a query). */
    fun watchLoans(on: Boolean) {
        loansWatching = on
        if (on) withStore { loadLoans(it) } else { selectedPerson = null; _loans.value = LoansUi() }
    }

    fun selectLoanPerson(id: String?) { selectedPerson = id; withStore { loadLoans(it) } }

    fun createPerson(name: String, onCreated: (String) -> Unit = {}) = withStore { ok ->
        val p = ok.people.create(name)
        refresh(ok)
        viewModelScope.launch(Dispatchers.Main) { onCreated(p.id) }
    }

    fun removePerson(id: String) = withStore { ok ->
        ok.people.remove(id); selectedPerson = null
        refresh(ok)
    }

    private fun loadLoans(ok: AppContainer.Opened.Ok) {
        val rows = ok.people.balances(inr.code).map { b ->
            LoanRowUi(b.person.id, b.person.name, b.minor, Money(kotlin.math.abs(b.minor), inr).formatted(), when { b.minor > 0 -> "owes you"; b.minor < 0 -> "you owe"; else -> "settled up" }, b.entries)
        }
        val selected = rows.firstOrNull { it.id == selectedPerson }
        val history = selected?.let { s ->
            // lent and borrowed entries, and the shares of bills they were part of, newest first (a share reads as money they owe you, like money lent)
            val lent = ok.db.schemaQueries.loanEntriesOfPerson(s.id).executeAsList().map { e ->
                e.occurred_at to LoanEntryUi(e.id, dateFormat.format(e.occurred_at), Money(e.amount_minor, Currency(e.currency, currencyDigits[e.currency] ?: 2)).formatted(), e.direction == "PAID_TO_PERSON", e.account_id?.let { accountNames[it] }.orEmpty(), e.note.orEmpty())
            }
            val shares = ok.db.schemaQueries.sharedEntriesOfPerson(s.id).executeAsList().map { e ->
                e.occurred_at to LoanEntryUi(e.id, dateFormat.format(e.occurred_at), Money(e.share_minor, Currency(e.currency, currencyDigits[e.currency] ?: 2)).formatted(), true, e.account_id?.let { accountNames[it] }.orEmpty(), "Their share of " + (e.merchant ?: e.note ?: "a bill"))
            }
            val paidForYou = ok.db.schemaQueries.friendPaidOfPerson(s.id).executeAsList().map { e ->
                e.occurred_at to LoanEntryUi(e.id, dateFormat.format(e.occurred_at), Money(e.amount_minor, Currency(e.currency, currencyDigits[e.currency] ?: 2)).formatted(), false, "", "They paid; your share of " + (e.merchant ?: e.note ?: "a bill"))
            }
            (lent + shares + paidForYou).sortedByDescending { it.first }.map { it.second }
        }.orEmpty()
        _loans.value = LoansUi(rows, Money(rows.filter { it.minor > 0 }.sumOf { it.minor }, inr).formatted(), Money(rows.filter { it.minor < 0 }.sumOf { -it.minor }, inr).formatted(), selected, history,
            waitingRows(ok, currencyDigits, ok.db.schemaQueries.selectAccounts().executeAsList().filter { it.archived == 0L }.mapTo(HashSet()) { it.id }))
    }

    // ---- waiting to get back ("I expect this back") ----

    private val _waitingUndo = MutableStateFlow<ScreenUndo?>(null)

    /** "Got X back · Undo" or "Not expecting it · Undo", shown on the Lent and borrowed page. The id says what Undo does: `refund:<id>` deletes that refund, `close:<expense id>` reopens it. */
    val waitingUndo: StateFlow<ScreenUndo?> get() = _waitingUndo

    /**
     * Money came back for the expense [entryId]: it is recorded as a refund of it into [accountId] ([amountText] blank is what is still expected). [onDone] gets null
     * on success, otherwise the reason (shown in the dialog, which stays open).
     */
    fun gotItBack(entryId: String, accountId: String, amountText: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    val refundId = ok.store.atomically { EntryActions(ok).gotItBack(entryId, accountId, amountText) }
                    val minor = ok.db.schemaQueries.entryById(refundId).executeAsOne().amount_minor
                    refresh(ok)
                    _waitingUndo.value = ScreenUndo("refund:$refundId", "Got ${Money(minor, inr).formatted()} back")
                    null
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    /** "Not expecting it any more": it leaves the list without any money; Undo brings it back. */
    fun notExpectingBack(entryId: String) = withStore { ok ->
        val title = ok.expects.waiting().firstOrNull { it.entryId == entryId }?.title
        if (ok.expects.close(entryId)) {
            refresh(ok)
            _waitingUndo.value = ScreenUndo("close:$entryId", "Not expecting ${title ?: "it"} back any more")
        }
    }

    fun undoWaiting(id: String) = withStore { ok ->
        when {
            id.startsWith("refund:") -> ok.store.delete(EntryId(id.removePrefix("refund:")))
            id.startsWith("close:") -> ok.expects.reopen(id.removePrefix("close:"))
        }
        refresh(ok)
    }

    fun waitingUndoGone(id: String) { if (_waitingUndo.value?.id == id) _waitingUndo.value = null }

    // ---- safe to spend and yearly bills ----

    /** The switch in the safe-to-spend dialog: show the line under the budget on Home, or not. */
    fun setSafeToSpendOnHome(on: Boolean) = withStore { ok ->
        ok.templates.putSetting(SETTING_SAFE_HOME, if (on) "1" else "0")
        safeCache = safeCache?.copy(homeSetting = on)
        _state.update { (it as? UiState.Ready)?.copy(safeToSpend = safeCache) ?: it }
        scheduleSlowFigures() // switched back on, it is worked out again
    }

    /** The switch on the Repeating page: take the monthly amount for yearly bills off safe to spend. */
    fun setSinkingFunds(on: Boolean) = withStore { ok ->
        ok.templates.putSetting(SETTING_SINKING_FUNDS, if (on) "1" else "0")
        refresh(ok)
        scheduleSlowFigures() // the figure is worked out again with or without it
    }

    private val _inbox = MutableStateFlow<List<InboxRowUi>>(emptyList())

    /** Pending captured transactions, newest first. Loaded when the inbox opens and kept current while it is open. */
    val inbox: StateFlow<List<InboxRowUi>> get() = _inbox

    private val _captureSettings = MutableStateFlow(CaptureSettingsUi(false, ""))
    val captureSettings: StateFlow<CaptureSettingsUi> get() = _captureSettings

    fun loadInbox() = withStore { ok ->
        // waiting texts that the rules now read as bills (0.7.1) move to Bills before the list is shown
        if (MessageIngest.reclassifyWaiting(ok) > 0) refresh(ok)
        _inbox.value = inboxRows(ok)
    }

    fun loadCaptureSettings() = withStore { ok ->
        // the capture log keeps 90 days: the newest event of any outcome is the last time something arrived
        val now = nowMillis()
        val lastAt = ok.db.schemaQueries.captureEventCounts(0).executeAsList().mapNotNull { it.last_at }.maxOrNull()
        _captureSettings.value = CaptureSettingsUi(
            ok.templates.setting(CaptureKeys.SETTING_SMS_ON) == "1", ok.templates.setting(CaptureKeys.SETTING_EXTRA_ROOTS).orEmpty(), ok.templates.setting(CaptureKeys.SETTING_NOTIF_ON) == "1", ok.templates.setting(CaptureKeys.SETTING_EMAIL_ON) == "1",
            lastReceivedText = lastAt?.let { app.expenses.core.domain.RelativeTime.ago(it, now) }, lastReceivedFresh = lastAt != null && app.expenses.core.domain.RelativeTime.within(lastAt, now, 7),
        )
    }

    fun setSmsCapture(on: Boolean) = withStore { ok ->
        ok.templates.putSetting(CaptureKeys.SETTING_SMS_ON, if (on) "1" else "0")
        _captureSettings.value = _captureSettings.value.copy(smsOn = on)
        refresh(ok)
    }

    fun setNotificationCapture(on: Boolean) = withStore { ok ->
        ok.templates.putSetting(CaptureKeys.SETTING_NOTIF_ON, if (on) "1" else "0")
        _captureSettings.value = _captureSettings.value.copy(notifOn = on)
        refresh(ok) // the first-week checklist on Home ticks "capture" from this switch
    }

    fun setEmailCapture(on: Boolean) = withStore { ok ->
        ok.templates.putSetting(CaptureKeys.SETTING_EMAIL_ON, if (on) "1" else "0")
        _captureSettings.value = _captureSettings.value.copy(emailOn = on)
        refresh(ok) // the first-week checklist on Home ticks "capture" from this switch
    }

    fun setExtraHeaders(text: String) = withStore { ok ->
        ok.templates.putSetting(CaptureKeys.SETTING_EXTRA_ROOTS, text.trim())
        _captureSettings.value = _captureSettings.value.copy(extraHeaders = text.trim())
    }

    /** The first three waiting items by name and amount, and the totals, for the home banner. Nothing is done when nothing is waiting. */
    private fun inboxPeek(ok: AppContainer.Opened.Ok): InboxPeekUi {
        // three rows and one sum, not the whole backlog: this runs after every save
        val waiting = ok.inbox.list(app.expenses.core.storage.InboxStatus.PENDING, 3)
        if (waiting.isEmpty()) return InboxPeekUi()
        val (out, `in`) = ok.inbox.pendingTotals()
        val count = ok.inbox.pendingCount().toInt()
        fun title(i: app.expenses.core.storage.InboxItem): String {
            val raw = PayeeGuess.of(i.body.orEmpty())
            return raw?.let { ok.templates.setting(payeeNameKey(it))?.takeIf { n -> n.isNotBlank() } } ?: raw?.let(::cleanPayee) ?: i.issuer
        }
        fun money(minor: Long) = Money(minor, inr).formatted()
        return InboxPeekUi(
            rows = waiting.map { InboxPeekRow(title(it), money(it.amountMinor), it.direction == "CREDIT") },
            more = (count - waiting.size).coerceAtLeast(0),
            outText = money(out),
            inText = money(`in`),
        )
    }

    // ---- the guide ----

    private val _guide = MutableStateFlow<app.expenses.core.domain.GuidePlan?>(null)
    /** The guide being shown, or null. Set when the app opens (first time, or after an update), or by the person from More. */
    val guide: StateFlow<app.expenses.core.domain.GuidePlan?> get() = _guide

    @Volatile private var guideDeferred = false

    /**
     * The app was opened to do something (the widget, the Quick Settings tile, a bill notification): the tour waits for a plain launch, and one that is already
     * showing goes away without being recorded as seen. Quick entry must never land in a tour.
     */
    fun deferGuide() { guideDeferred = true; _guide.value = null }

    /** Decides, once the store is open, whether the guide is offered now. Nothing is offered over a lock screen: the screen shows it only when unlocked. */
    private fun offerGuide(ok: AppContainer.Opened.Ok) {
        if (guideDeferred) return
        val seen = ok.templates.setting(GuideSettings.SEEN)?.toIntOrNull()
        val plan = app.expenses.core.domain.Guide.planOnOpen(seen, ok.templates.setting(GuideSettings.OFF) == "1")
        _guide.value = plan
        // an update that added nothing to show is not shown again either
        if (plan == null && seen != null && seen < app.expenses.core.domain.Guide.VERSION && ok.templates.setting(GuideSettings.OFF) != "1") ok.templates.putSetting(GuideSettings.SEEN, app.expenses.core.domain.Guide.VERSION.toString())
    }

    /** More, then How to use this app: every page, and nothing about it is remembered. */
    /** Appearance: less motion. Takes effect at once, on every screen. */
    /** [speed] is "off", "quick", "relaxed" or "slow". Off is Reduce motion: everything changes at once. */
    fun setMotionSpeed(speed: String) = withStore { ok ->
        ok.templates.putSetting(SETTING_REDUCE_MOTION, if (speed == "off") "1" else "0")
        if (speed != "off") ok.templates.putSetting(SETTING_MOTION_SPEED, speed)
        refresh(ok)
    }

    fun openGuide() { _guide.value = app.expenses.core.domain.Guide.fullPlan() }

    /** After an update the person asked for the whole guide, not just what is new. */
    fun showWholeGuide() { _guide.update { it?.copy(steps = app.expenses.core.domain.Guide.steps) } }

    /** Finished, or skipped: either way it is not offered again for this version. [neverAgain] also stops it being offered after later updates. */
    fun finishGuide(neverAgain: Boolean) {
        val mode = _guide.value?.mode ?: return
        _guide.value = null
        if (mode == app.expenses.core.domain.GuideMode.MANUAL) return
        withStore { ok ->
            ok.templates.putSetting(GuideSettings.SEEN, app.expenses.core.domain.Guide.VERSION.toString())
            if (neverAgain) ok.templates.putSetting(GuideSettings.OFF, "1")
        }
    }

    // ---- bills found in messages ----

    private val _bills = MutableStateFlow(BillsUi())
    val bills: StateFlow<BillsUi> get() = _bills
    @Volatile private var billsWatching = false

    /** The bills page asks to be kept up to date while it is open. */
    fun watchBills(on: Boolean) {
        billsWatching = on
        if (on) withStore { _bills.value = billsUi(it) } else _bills.value = BillsUi()
    }

    private val billDay = DatePattern("EEE, d MMM")

    private fun billRow(ok: AppContainer.Opened.Ok, b: app.expenses.core.storage.Bill, accounts: List<app.expenses.core.storage.db.Account>, today: LocalDate): BillRowUi {
        val currency = Currency(b.currency, currencyDigits[b.currency] ?: 2)
        val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val days = due?.let { it.epochDay() - today.epochDay() }
        val dueIn = when { days == null -> ""; days == 0L -> "today"; days == 1L -> "tomorrow"; days > 1 -> "in $days days"; days == -1L -> "1 day overdue"; else -> "${-days} days overdue" }
        // the account that carries the bill's last four digits, else the one remembered for its issuer and digits
        val byDigits = ok.looks.accountFor(b.accountLast4, preferCard = b.kind == BillKind.CREDIT_CARD.name)?.let { id -> accounts.firstOrNull { it.id == id } }
        val remembered = byDigits ?: ok.templates.setting(accountKey(b.issuer, b.accountLast4))?.let { id -> accounts.firstOrNull { it.id == id } }
        val cards = accounts.filter { it.type == "CREDIT_CARD" && it.archived == 0L }
        val bank = accounts.firstOrNull { it.type == "BANK" && it.archived == 0L } ?: accounts.firstOrNull { it.archived == 0L }
        val isCard = b.kind == BillKind.CREDIT_CARD.name
        return BillRowUi(
            b.id, b.title, b.kind, b.issuer, Money(b.amountMinor, currency).formatted(), Money(b.amountMinor, currency).toPlainString(),
            b.minDueMinor?.let { Money(it, currency).formatted() }, b.minDueMinor?.let { Money(it, currency).toPlainString() },
            due?.let { "Due ${billDay.format(it)}" } ?: "No due date in the message", dueIn, due?.epochDay(), days != null && days < 0, days != null && days in 0..3, b.autoDebit, b.accountLast4,
            b.body.orEmpty(), b.status.name,
            if (isCard) (remembered?.takeIf { it.type == "CREDIT_CARD" } ?: cards.singleOrNull())?.id.orEmpty() else "",
            (if (isCard) bank else remembered?.takeIf { it.type != "CREDIT_CARD" } ?: bank)?.id.orEmpty(),
            b.paidAt?.let { "Paid " + DatePattern("d MMM").format(it) }.orEmpty(),
        )
    }

    private fun billsUi(ok: AppContainer.Opened.Ok): BillsUi {
        ok.bills.reconcileWithEntries()
        val accounts = ok.db.schemaQueries.allAccounts().executeAsList()
        val today = todayLocal()
        fun rows(status: app.expenses.core.storage.BillStatus, limit: Long) = ok.bills.list(status, limit).map { billRow(ok, it, accounts, today) }
        val coming = (rows(app.expenses.core.storage.BillStatus.TRACKED, 100) + cardDueRows(ok, accounts, today) + repeatingRows(ok, today)).sortedBy { it.dueEpochDay ?: Long.MAX_VALUE }
        return BillsUi(rows(app.expenses.core.storage.BillStatus.FOUND, 50), coming, rows(app.expenses.core.storage.BillStatus.PAID, 5).sortedByDescending { it.id }, loaded = true)
    }

    /** Prefix of the id of a credit card's statement shown among the bills although no message brought it (an imported statement, or entries typed in). */
    private val CARD_DUE_PREFIX = "card:"

    /**
     * A card with something left on its latest statement is a bill too, whether or not a text said so: the home banner counts it, so the bills page lists it, with
     * Mark paid. Not when the card's own bill from a message is already tracked for about the same day.
     */
    private fun cardDueRows(ok: AppContainer.Opened.Ok, accounts: List<app.expenses.core.storage.db.Account>, today: LocalDate): List<BillRowUi> {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val cardBills = ok.bills.list(app.expenses.core.storage.BillStatus.TRACKED).filter { it.kind == "CREDIT_CARD" }.mapNotNull { b -> b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
        val bank = accounts.firstOrNull { it.type == "BANK" && it.archived == 0L } ?: accounts.firstOrNull { it.type != "CREDIT_CARD" && it.archived == 0L }
        return ok.cards.all().mapNotNull { terms ->
            val account = accounts.firstOrNull { it.id == terms.accountId && it.archived == 0L } ?: return@mapNotNull null
            val st = ok.cards.standing(terms.accountId, today, zone)?.takeIf { it.remaining > 0 && it.cycle.dueDate < today.plusDays(45) } ?: return@mapNotNull null
            if (cardBills.any { kotlin.math.abs(it.toEpochDays() - st.cycle.dueDate.toEpochDays()) <= 2 }) return@mapNotNull null
            val days = st.cycle.dueDate.epochDay() - today.epochDay()
            val dueIn = when { days == 0L -> "today"; days == 1L -> "tomorrow"; days > 1 -> "in $days days"; days == -1L -> "1 day overdue"; else -> "${-days} days overdue" }
            val left = Money(st.remaining, inr)
            BillRowUi(
                CARD_DUE_PREFIX + account.id, account.name + " statement", BillKind.CREDIT_CARD.name, account.name, left.formatted(), left.toPlainString(),
                st.minimumDue.takeIf { it > 0 && it < st.remaining }?.let { Money(it, inr).formatted() }, st.minimumDue.takeIf { it > 0 && it < st.remaining }?.let { Money(it, inr).toPlainString() },
                "Due ${billDay.format(st.cycle.dueDate)}", dueIn, st.cycle.dueDate.epochDay(), days < 0, days in 0..3, false, null, "", "TRACKED", account.id, bank?.id.orEmpty(),
            )
        }
    }

    /** Prefix of the id of a repeating item's occurrence listed among the bills: `rec:<item id>_<epoch day>`. */
    private val REPEAT_DUE_PREFIX = "rec:"

    /**
     * The repeating expenses that wait for the person (not ones that post by themselves), from two weeks back to a week ahead: the home banner counts exactly these
     * ([weekAhead]) and the bills page lists exactly these ([repeatingRows]), so the two always agree.
     */
    private fun waitingRepeats(ok: AppContainer.Opened.Ok, today: LocalDate) =
        ok.recurring.unhandled(today.minusDays(14), today.plusDays(8)).filter { !it.item.auto && it.item.kind == app.expenses.core.domain.RecurringKind.EXPENSE }

    /** A waiting repeating expense as a bill row with Post it and Skip; an item whose amount differs each time has no amount until posted. */
    private fun repeatingRows(ok: AppContainer.Opened.Ok, today: LocalDate): List<BillRowUi> = waitingRepeats(ok, today).map { due ->
        val days = due.date.epochDay() - today.epochDay()
        val dueIn = when { days == 0L -> "today"; days == 1L -> "tomorrow"; days > 1 -> "in $days days"; days == -1L -> "1 day overdue"; else -> "${-days} days overdue" }
        val amount = due.item.amount
        BillRowUi(
            REPEAT_DUE_PREFIX + due.item.id + "_" + due.date.epochDay(), due.item.name, BILL_KIND_REPEATING, due.item.name, amount?.formatted() ?: "Amount?", amount?.toPlainString().orEmpty(), null, null,
            "Due ${billDay.format(due.date)}", dueIn, due.date.epochDay(), days < 0, days in 0..3, false, null, "", "TRACKED", "", "",
            repeatItemId = due.item.id, repeatDay = due.date.epochDay(),
        )
    }

    /** What the home banner shows: three small queries, whatever the size of the history. */
    private fun billPeek(ok: AppContainer.Opened.Ok): BillPeekUi {
        ok.bills.reconcileWithEntries()
        val fresh = ok.bills.count(app.expenses.core.storage.BillStatus.FOUND).toInt()
        val tracked = ok.bills.count(app.expenses.core.storage.BillStatus.TRACKED).toInt()
        val today = todayLocal()
        val week = weekAhead(ok, today)
        if (fresh + tracked == 0) return if (week.first == 0) BillPeekUi() else BillPeekUi(dueThisWeek = week.first, weekTotal = week.second)
        val accounts = ok.db.schemaQueries.allAccounts().executeAsList()
        val soon = todayLocal().plusDays(14)
        val shown = ok.bills.list(app.expenses.core.storage.BillStatus.FOUND, 3).map { it to true } +
            ok.bills.list(app.expenses.core.storage.BillStatus.TRACKED, 3).filter { b -> b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it <= soon } == true }.map { it to false }
        val rows = shown.take(3).map { (b, isNew) -> billRow(ok, b, accounts, today).let { BillPeekRow(it.id, it.title, it.amountText, it.dueIn, isNew, it.overdue) } }
        if (rows.isEmpty()) return BillPeekUi(newCount = fresh, dueThisWeek = week.first, weekTotal = week.second)
        return BillPeekUi(fresh, rows, more = (fresh + shown.count { !it.second } - rows.size).coerceAtLeast(0), dueThisWeek = week.first, weekTotal = week.second)
    }

    /**
     * Everything that falls due within a week, overdue included, counted once: tracked bills, repeating items that wait for the person (one set to post by
     * itself needs nothing from them), and credit cards with something left on the statement (not a card whose bill is already tracked). The count and the
     * total of what has a known amount.
     */
    private fun weekAhead(ok: AppContainer.Opened.Ok, today: LocalDate): Pair<Int, String> {
        val until = today.plusDays(8)
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        var count = 0; var total = 0L
        val trackedBills = ok.bills.list(app.expenses.core.storage.BillStatus.TRACKED)
        val trackedDue = trackedBills.mapNotNull { b -> b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { d -> b to d } }
        trackedDue.filter { (_, d) -> d < until }.forEach { (b, _) -> count++; if (b.currency == inr.code) total += b.amountMinor }
        waitingRepeats(ok, today).forEach { due ->
            count++; due.item.amount?.takeIf { it.currency == inr }?.let { total += it.minorUnits }
        }
        val cardBills = trackedDue.filter { (b, _) -> b.kind == "CREDIT_CARD" }.map { it.second }
        ok.cards.all().forEach { terms ->
            // the due date follows from the billing dates alone: a card whose statement is not due within the week costs no balance queries (they read the card's whole history)
            if (app.expenses.core.domain.CardBilling.cycleOn(today, terms).dueDate >= until) return@forEach
            val st = ok.cards.standing(terms.accountId, today, zone)?.takeIf { it.remaining > 0 && it.cycle.dueDate < until } ?: return@forEach
            if (cardBills.any { kotlin.math.abs(it.toEpochDays() - st.cycle.dueDate.toEpochDays()) <= 2 }) return@forEach // its own bill is already counted
            count++; total += st.remaining
        }
        return count to (if (count > 0 && total > 0) Money(total, inr).formatted() else "")
    }

    private fun billsChanged(ok: AppContainer.Opened.Ok, message: String? = null) {
        refresh(ok) // also reloads the bills page when it is open
        if (message != null) _state.update { (it as? UiState.Ready)?.copy(message = message) ?: it }
    }

    /** The person adds a found bill to the ones they track. */
    fun trackBill(id: String) = withStore { ok -> if (ok.bills.track(id)) billsChanged(ok, "Added to your bills") }

    /** The skip of a bill that Undo can take back: the bill as it was (its state and its message; a skip forgets the message in the database). */
    private var skippedBill: app.expenses.core.storage.Bill? = null
    private val _billUndo = MutableStateFlow<ScreenUndo?>(null)
    val billUndo: StateFlow<ScreenUndo?> get() = _billUndo

    /** Skips (a found bill) or removes (a tracked one), and offers to bring it back. */
    fun dismissBill(id: String) = withStore { ok ->
        val before = ok.bills.get(id)
        if (ok.bills.dismiss(id)) {
            billsChanged(ok)
            if (before != null) { skippedBill = before; _billUndo.value = ScreenUndo(id, "Skipped ${before.title}") }
        }
    }

    /** Puts back the bill whose skip was just offered for Undo, in the state it was skipped from. */
    fun undoDismissBill(id: String) {
        val held = skippedBill?.takeIf { it.id == id } ?: return // taken now: see [undoDismissCapture]
        withStore { ok -> if (ok.bills.undismiss(id, held.status, held.body)) billsChanged(ok) }
    }

    fun billUndoGone(id: String) {
        if (_billUndo.value?.id == id) { _billUndo.value = null; skippedBill = null }
    }

    fun setBillDue(id: String, epochDay: Long) = withStore { ok -> ok.bills.setDueDate(id, localDateOfEpochDay(epochDay).toString()); billsChanged(ok) }

    fun unpayBill(id: String) = withStore { ok -> ok.bills.unpay(id); billsChanged(ok, "Back in your bills") }

    /**
     * Marks a bill paid. With [record] the payment is also entered in the ledger: a transfer from [payFromId] to the card [cardAccountId] for a credit card
     * bill, otherwise an expense in the Bills category. Without it the bill is only marked (it was recorded already, or will be from the bank's text).
     */
    fun payBill(id: String, payFromId: String, cardAccountId: String, amountText: String, record: Boolean, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    if (id.startsWith(CARD_DUE_PREFIX)) {
                        // a card's statement with no message behind it: only the payment is recorded, as a transfer to the card, never as spending
                        if (!record) return@write "Turn on entering the payment: there is no message to mark, so this is how it is paid."
                        if (cardAccountId.isEmpty() || payFromId.isEmpty()) return@write "Choose the account it was paid from."
                        val draft = EntryDraft(null, DraftKind.TRANSFER, amountText, "", emptyList(), payFromId, cardAccountId, "Card bill payment", nowMillis())
                        try { ok.store.atomically { EntryActions(ok).save(draft) } } catch (e: IllegalArgumentException) { return@write e.message ?: "That could not be recorded." }
                        billsChanged(ok, "Recorded the payment to the card")
                        return@write null
                    }
                    val b = ok.bills.get(id) ?: return@write "That bill is no longer here."
                    if (b.status == app.expenses.core.storage.BillStatus.PAID || b.status == app.expenses.core.storage.BillStatus.DISMISSED) return@write "That bill was already handled."
                    var entryId: String? = null
                    if (record) {
                        if (b.currency != BaseCurrency.current.code) return@write "This bill is in ${b.currency}, but your ledger is in ${BaseCurrency.current.code}. Mark it paid without recording."
                        val isCard = b.kind == BillKind.CREDIT_CARD.name && cardAccountId.isNotEmpty()
                        val billsCategory = ok.categories.list().firstOrNull { it.parentId == null && !it.hidden && it.name.equals("Bills", ignoreCase = true) }?.id.orEmpty()
                        val draft = if (isCard) EntryDraft(null, DraftKind.TRANSFER, amountText, "", emptyList(), payFromId, cardAccountId, b.title, nowMillis())
                        else EntryDraft(null, DraftKind.EXPENSE, amountText, billsCategory, emptyList(), payFromId, "", b.title, nowMillis())
                        entryId = try { ok.store.atomically { EntryActions(ok).save(draft) } } catch (e: IllegalArgumentException) { return@write e.message ?: "That could not be recorded." }
                    }
                    // the bill's words are cleared once it is paid, so an entry recorded from it keeps them first
                    if (entryId != null) b.body?.let { ok.origins.put(entryId, app.expenses.core.storage.OriginStore.kindOf(b.source, b.sender), b.sender, it, b.receivedAt) }
                    ok.bills.markPaid(id, entryId, nowMillis())
                    billsChanged(ok, if (record) "Recorded ${b.title}" else "Marked ${b.title} as paid")
                    null
                }
            } catch (e: Exception) { e.message ?: "That could not be saved." }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    /**
     * A payment text that was just added settles the bill it is for: a tracked bill for the same amount from the same card or account, in a window around its
     * due date. Only marks the bill; the entry the person just added is the record. Returns the bill's title when one was settled.
     */
    private fun settleBillFor(ok: AppContainer.Opened.Ok, item: app.expenses.core.storage.InboxItem, entryId: String): String? {
        val today = todayLocal()
        val hit = ok.bills.list(app.expenses.core.storage.BillStatus.TRACKED).firstOrNull { b ->
            if (b.currency != item.currency) return@firstOrNull false
            val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (due != null && (today < due.minusDays(30) || today > due.plusDays(15))) return@firstOrNull false
            val sameAccount = if (b.accountLast4 != null && item.accountLast4 != null) b.accountLast4 == item.accountLast4 else b.issuer.equals(item.issuer, ignoreCase = true) && b.accountLast4 == null
            val card = b.kind == BillKind.CREDIT_CARD.name
            // a payment into a card may be anything from the minimum to the whole; any other bill is paid in full
            val amountOk = if (card && item.direction == "CREDIT") item.amountMinor >= (b.minDueMinor ?: b.amountMinor) && item.amountMinor <= b.amountMinor else item.amountMinor == b.amountMinor
            sameAccount && amountOk && (if (card) item.direction == "CREDIT" else item.direction == "DEBIT")
        } ?: return null
        ok.bills.markPaid(hit.id, entryId, nowMillis())
        return hit.title
    }

    private fun inboxRows(ok: AppContainer.Opened.Ok): List<InboxRowUi> {
        val accounts = ok.db.schemaQueries.allAccounts().executeAsList().filter { it.archived == 0L }
        val usable = ok.categories.list().filter { !it.hidden && it.kind.forExpense }.map { it.id }.toSet() // suggestions are for debits, which are expenses
        val rules = ok.captureRules.rules()
        val learned = ok.captureRules.learned()
        val zone = systemZone()
        val fmt = DatePattern("d MMM")
        val items = ok.inbox.list()
        // a mail app's notification is not "an app posting the same notification twice": two emails of one amount from one mail app need the same reference to count as one
        val repeats = Duplicates.repeatsOfPending(items.map { PendingKey(it.id, it.direction, it.amountMinor, it.receivedAt, it.reference, it.sender.trim().uppercase(), it.source == CaptureKeys.NOTIFICATION && !AppSources.isEmailName(it.sender), it.accountLast4, AppSources.isEmailName(it.sender)) })
        val byId = items.associateBy { it.id }
        val dayMs = 86_400_000L
        val base = items.map { i ->
            val day = (i.txnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateOfMillis(i.receivedAt, zone))
            val dayStart = { d: LocalDate -> d.startOfDayMillis(zone) }
            val from = dayStart(day.minusDays(Duplicates.DAY_WINDOW)); val until = dayStart(day.plusDays(Duplicates.DAY_WINDOW + 1))
            fun dup(id: String, at: Long, ref: String?, merchant: String?) = DupEntry(id, i.amountMinor, dateOfMillis(at, zone).epochDay(), ref?.removeSuffix("/reversal"), merchant ?: "an entry")
            val near = if (i.direction == "CREDIT") ok.db.schemaQueries.entriesNearCredit(i.amountMinor, from, until).executeAsList().map { dup(it.id, it.occurred_at, it.source_ref, it.merchant) }
                else ok.db.schemaQueries.entriesNearDebit(i.amountMinor, from, until).executeAsList().map { dup(it.id, it.occurred_at, it.source_ref, it.merchant) }
            // A bank reference ties two texts to one transaction only when they point the same way: a reversal or refund text carries the reference of the
            // payment it undoes, and calling that a duplicate would have the person skip money coming back.
            val sameRefAll = i.reference?.let { r -> (ok.db.schemaQueries.entriesWithReference(r).executeAsList() + ok.db.schemaQueries.entriesWithReference("$r/reversal").executeAsList()).filter { it.amount_minor == i.amountMinor } }.orEmpty()
            val isCredit = i.direction == "CREDIT"
            val (alike, opposite) = sameRefAll.partition { e -> e.kind == "TRANSFER" || (e.kind == "INCOME" || e.kind == "REFUND") == isCredit }
            val match = Duplicates.best(day.epochDay(), i.reference, (near + alike.map { dup(it.id, it.occurred_at, it.source_ref, it.merchant) }).distinctBy { it.id })
            val reverses = if (match == null) opposite.firstOrNull() else null
            val first = repeats[i.id]?.let { byId[it] }
            val note = when {
                match != null -> (if (match.strength == DupStrength.SAME_REFERENCE) "Already in your ledger: " else "Looks like one you already added: ") + "${match.entry.label} on ${fmt.format(localDateOfEpochDay(match.entry.day))}"
                first != null -> "Probably the same payment as the ${if (AppSources.isEmailName(first.sender)) "email" else if (first.source == CaptureKeys.NOTIFICATION) "notification" else "text"} from ${first.issuer} above"
                reverses != null -> "Carries the reference of ${if (isCredit) "the payment" else "the money received"} \"${reverses.merchant ?: "an entry"}\" on ${fmt.format(dateOfMillis(reverses.occurred_at, zone))}: looks like its refund or reversal, not a repeat"
                else -> null
            }
            // the account that carries the text's last four digits (a card when the text says card), else the one remembered for this bank and digits
            val byDigits = ok.looks.accountFor(i.accountLast4, preferCard = i.body.orEmpty().contains("card", ignoreCase = true))?.takeIf { id -> accounts.any { it.id == id } }
            val remembered = byDigits ?: ok.templates.setting(accountKey(i.issuer, i.accountLast4))?.takeIf { id -> accounts.any { it.id == id } }
            val kind = KindRules.of(i.body.orEmpty(), if (i.direction == "CREDIT") Direction.CREDIT else Direction.DEBIT)
            val cards = accounts.filter { it.type == "CREDIT_CARD" }
            val cashAccounts = accounts.filter { it.type == "CASH" }
            val ownBank = accounts.firstOrNull { it.id == remembered && it.type != "CREDIT_CARD" && it.type != "CASH" } ?: accounts.firstOrNull { it.type == "BANK" }
            var treatment = "PAYMENT"; var treatmentLabel = ""; var fromAcct = ""; var toAcct = ""; var refundOf = ""
            when {
                kind == MessageKind.ATM_WITHDRAWAL && cashAccounts.isNotEmpty() && ownBank != null -> { treatment = "TRANSFER"; treatmentLabel = "cash withdrawal"; fromAcct = ownBank.id; toAcct = cashAccounts.first().id }
                kind == MessageKind.CARD_PAYMENT && cards.isNotEmpty() && ownBank != null -> {
                    treatment = "TRANSFER"; treatmentLabel = "credit card payment"
                    toAcct = (remembered?.takeIf { id -> cards.any { it.id == id } } ?: cards.first().id); fromAcct = ownBank.id
                }
                // money into a credit card is almost always a payment of the bill (a refund is caught above): not income, which would inflate the reports
                kind == MessageKind.PAYMENT && i.direction == "CREDIT" && remembered != null && cards.any { it.id == remembered } && accounts.any { it.type == "BANK" } -> {
                    treatment = "TRANSFER"; treatmentLabel = "payment received on the card"; toAcct = remembered; fromAcct = accounts.first { it.type == "BANK" }.id
                }
                kind == MessageKind.REFUND -> {
                    val day = (i.txnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateOfMillis(i.receivedAt, zone))
                    val end = day.plusDays(1).startOfDayMillis(zone)
                    val lower = i.body.orEmpty().lowercase()
                    val pick = ok.db.schemaQueries.refundCandidates(i.amountMinor, day.minusDays(REFUND_LOOKBACK_DAYS).startOfDayMillis(zone), end).executeAsList()
                        .firstOrNull { c -> (c.amount_minor == i.amountMinor || (c.merchant ?: "").lowercase().let { m -> m.length >= 3 && lower.contains(m) }) && (EntryActions(ok).startRefund(c.id)?.draft?.refundableMinor ?: 0L) >= i.amountMinor }
                    if (pick != null) { treatment = "REFUND"; treatmentLabel = "refund of ${pick.merchant ?: pick.note ?: "an earlier expense"}"; refundOf = pick.id }
                }
            }
            val money = Money(i.amountMinor, Currency(i.currency, currencyDigits[i.currency] ?: 2))
            val rawPayee = PayeeGuess.of(i.body.orEmpty())
            val named = rawPayee?.let { ok.templates.setting(payeeNameKey(it)) }?.takeIf { it.isNotBlank() }
            val payee = named ?: rawPayee?.let(::cleanPayee)
            // top-level categories only: a subcategory called "Other" under Travel must not stand in for the top-level one the starter list means
            val byName = ok.categories.list().filter { !it.hidden && it.kind.forExpense && it.parentId == null }.associate { it.name.lowercase() to it.id }
            // entries filed from a captured text carry the clean merchant name (cleanPayee), older ones the raw address: look under both
            val history = if (i.direction == "DEBIT" && rawPayee != null) cleanPayee(rawPayee).let { clean ->
                SimilarEntries.topCategory(clean, (similarCandidates(ok, clean, "EXPENSE") + if (clean != rawPayee) similarCandidates(ok, rawPayee, "EXPENSE") else emptyList()).distinctBy { it.id })
            } else null
            val suggestion = if (i.direction == "DEBIT") CategorySuggest.suggest(rawPayee, listOfNotNull(named, i.body).joinToString(" "), rules, learned, usable, byName, toAPerson = PayeeKinds.of(rawPayee) == PayeeKind.PERSON, history = history)
                // cash taken out at an ATM that is not moved to a cash account (the person has none): "Cash withdrawal", when they have that category
                ?: byName["cash withdrawal"]?.takeIf { it in usable && kind == MessageKind.ATM_WITHDRAWAL && treatment == "PAYMENT" }?.let { CategorySuggestion(it, SuggestionSource.WORDS) }
            else null
            InboxRowUi(
                i.id, i.issuer, i.direction, money.formatted(), i.amountMinor, i.txnDate?.let { runCatching { fmt.format(LocalDate.parse(it)) }.getOrNull() } ?: fmt.format(i.receivedAt),
                i.accountLast4, i.body.orEmpty(), payee, remembered ?: accounts.firstOrNull()?.id.orEmpty(), suggestion?.categoryId.orEmpty(),
                rememberedAccount = remembered != null, confidence = i.confidence,
                duplicateNote = note.takeIf { reverses == null }, relatedNote = note.takeIf { reverses != null }, duplicateOfEntry = match?.entry?.id,
                nameable = rawPayee != null,
                payeeKind = when (PayeeKinds.of(rawPayee)) { PayeeKind.PERSON -> "person"; PayeeKind.BUSINESS -> "business"; PayeeKind.UNKNOWN -> "" },
                treatment = treatment, treatmentLabel = treatmentLabel, fromAccountId = fromAcct, toAccountId = toAcct, refundOfEntry = refundOf,
                categoryNote = when (suggestion?.source) { SuggestionSource.RULE -> "your rule"; SuggestionSource.LEARNED -> "what you chose last time"; SuggestionSource.HISTORY -> "your earlier entries"; SuggestionSource.STARTER -> "a common merchant"; SuggestionSource.PERSON -> "a payment to a person"; SuggestionSource.WORDS -> "the words of the text"; null -> "" },
            )
        }
        return withTransfers(ok, items, base, accounts)
    }

    // ---- own-account transfers: one card for the two texts of one move of money ----

    /**
     * Puts together the two waiting texts of one move of money between the person's own accounts, and marks the rest. [rows] are the ordinary rows of [items]. Only a plain payment that is
     * not a flagged repeat takes part; the accounts must both be known by the digits the texts carry (or the account remembered for that bank and digits), so a person who has not set the last
     * four digits on an account gets no pairing and is told so on the card. What comes back:
     *  - the two rows of a pair replaced by one row ([InboxRowUi.treatment] "PAIR", [InboxRowUi.pairedItemId] the other text);
     *  - a row whose other half was already added as an expense or income gets [InboxRowUi.convertEntryId];
     *  - a lone row gets [InboxRowUi.transferHint] (it might be a transfer) or [InboxRowUi.setupHint] (the digits would let this be recognised).
     * Nothing is recorded here; adding anything is the person's tap. Costs a few lookups per waiting item, and nothing when the person has fewer than two accounts to move money between.
     */
    private fun withTransfers(ok: AppContainer.Opened.Ok, items: List<app.expenses.core.storage.InboxItem>, rows: List<InboxRowUi>, accounts: List<app.expenses.core.storage.db.Account>): List<InboxRowUi> {
        val scan = TransferScan.of(ok, items, rows.filter { it.treatment == "PAYMENT" && it.duplicateNote == null }.mapTo(HashSet()) { it.id }, accounts) ?: return rows
        val byId = items.associateBy { it.id }
        val rowById = rows.associateBy { it.id }
        val names = scan.accountNames
        fun money(minor: Long) = Money(minor, inr).formatted()
        val pairAt = HashMap<String, TransferPair>()
        for (p in scan.pairs) { pairAt[p.out.id] = p; pairAt[p.into.id] = p }
        val emitted = HashSet<String>()
        val out = ArrayList<InboxRowUi>(rows.size)
        for (r in rows) {
            val p = pairAt[r.id]
            if (p != null) {
                if (!emitted.add(p.out.id)) continue
                val o = rowById.getValue(p.out.id); val n = rowById.getValue(p.into.id)
                out += o.copy(
                    payee = PayeeGuess.of(n.body) ?: PayeeGuess.of(o.body), direction = "DEBIT", accountId = p.out.accountId.orEmpty(), categoryId = "", treatment = "PAIR", treatmentLabel = "transfer",
                    fromAccountId = p.out.accountId.orEmpty(), toAccountId = p.into.accountId.orEmpty(), pairedItemId = n.id, pairedBody = n.body, pairedLast4 = n.last4.orEmpty(),
                    pairBasis = p.basis.name, nameable = false, payeeKind = "", categoryNote = "", relatedNote = null,
                )
                continue
            }
            val offer = scan.offers[r.id]
            if (offer != null) {
                val (pair, e) = offer
                val earlier = if (pair.out.accepted) pair.out else pair.into
                val amount = money(byId.getValue(r.id).amountMinor)
                val note = "Make this a transfer: it matches the earlier $amount ${if (e.outgoing) "expense from" else "income into"} ${names[earlier.accountId]}"
                out += r.copy(convertEntryId = e.entryId, convertNote = note, fromAccountId = pair.out.accountId.orEmpty(), toAccountId = pair.into.accountId.orEmpty())
                continue
            }
            val lone = scan.lone[r.id]
            if (lone == null) { out += r; continue }
            val might = lone.mightBeTo
            val setup = scan.digitsWouldHelp && (lone.side.hinted || lone.namesOther || lone.side.namesAccountId != null) && (lone.side.accountId == null || lone.namesOther)
            out += r.copy(
                transferHint = might?.let { "Might be a transfer ${if (lone.side.outgoing) "to" else "from"} your other account, $it. If its other text arrives, the two are put together." }.orEmpty(),
                setupHint = if (might == null && setup) "Might be a transfer between your own accounts. Set the last four digits of your accounts (More > Accounts) so I can recognise transfers." else "",
            )
        }
        return out
    }

    /** "These are separate": the person says these two waiting texts are not one transfer; they are never put together again. */
    fun keepApart(outId: String, intoId: String) = withStore { ok ->
        ok.templates.putSetting(TransferScan.apartKey(outId, intoId), "1")
        _inbox.value = inboxRows(ok)
    }

    /**
     * Adds the two waiting texts [outId] (money out of [fromId]) and [intoId] (money into [toId]) as ONE transfer: neither becomes an expense or income. [onDone] gets null, or why not.
     * The person has to tap for this; nothing is added by itself.
     */
    fun confirmTransferPair(outId: String, intoId: String, fromId: String, toId: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    val out = ok.inbox.get(outId) ?: return@write "That item is no longer in the inbox."
                    val into = ok.inbox.get(intoId) ?: return@write "That item is no longer in the inbox."
                    if (out.currency != BaseCurrency.current.code || into.currency != out.currency) return@write "This is in ${out.currency}, but your ledger is in ${BaseCurrency.current.code}. Dismiss it and add it by hand."
                    if (out.direction != "DEBIT" || into.direction != "CREDIT" || out.amountMinor != into.amountMinor) return@write "These two texts are not the same amount going out and coming in."
                    val at = app.expenses.core.domain.CaptureTime.entryTime(out.txnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, out.receivedAt, kotlinx.datetime.TimeZone.currentSystemDefault())
                    val title = PayeeGuess.of(into.body.orEmpty()) ?: PayeeGuess.of(out.body.orEmpty()) ?: "Transfer to ${accountNames[toId] ?: "your account"}"
                    val draft = EntryDraft(null, DraftKind.TRANSFER, Money(out.amountMinor, BaseCurrency.current).toPlainString(), "", emptyList(), fromId, toId, title, at)
                    val entry = (when (val r = EntryDrafts.build(draft, EntryId(Uuid.random().toString()), BaseCurrency.current)) { is DraftResult.Ok -> r.entry; is DraftResult.Problem -> return@write r.message }) as app.expenses.core.domain.ledger.Transfer
                    ok.transfers.addPair(entry, title, out.reference ?: into.reference ?: out.id, outId, intoId)
                    // the account each text's own digits stand for is remembered, as for any added item
                    if (out.accountLast4 != null) ok.templates.putSettingIfChanged(accountKey(out.issuer, out.accountLast4), fromId)
                    if (into.accountLast4 != null) ok.templates.putSettingIfChanged(accountKey(into.issuer, into.accountLast4), toId)
                    _inbox.value = inboxRows(ok)
                    refresh(ok)
                    null
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    /** What "Make it a transfer" changed, held for the few seconds its Undo is offered. */
    @Volatile private var madeTransfer: app.expenses.core.storage.CaptureTransfers.Replaced? = null

    /**
     * The waiting text [itemId] is the other half of the entry [earlierId], which the person added earlier as an expense or income: the earlier entry is deleted (it can be restored) and
     * one transfer from [fromId] to [toId] takes its place, all or nothing. "Made it a transfer · Undo" is offered. [onDone] gets null, or why not. Never done without a tap.
     */
    fun makeTransfer(itemId: String, earlierId: String, fromId: String, toId: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    val item = ok.inbox.get(itemId) ?: return@write "That item is no longer in the inbox."
                    if (item.currency != BaseCurrency.current.code) return@write "This is in ${item.currency}, but your ledger is in ${BaseCurrency.current.code}. Dismiss it and add it by hand."
                    val earlier = ok.transfers.earlier(item.amountMinor, item.currency, item.receivedAt, TransferScan.EARLIER_QUERY_MS).firstOrNull { it.entryId == earlierId } ?: return@write "The earlier entry is no longer there, or has changed."
                    // the earlier entry must be the half on the account the card said
                    if (earlier.accountId != (if (earlier.outgoing) fromId else toId)) return@write "The earlier entry is on another account than this card said."
                    val at = earlier.occurredAt
                    val title = PayeeGuess.of(item.body.orEmpty()) ?: earlier.originText?.let { PayeeGuess.of(it) } ?: "Transfer to ${accountNames[toId] ?: "your account"}"
                    val draft = EntryDraft(null, DraftKind.TRANSFER, Money(item.amountMinor, BaseCurrency.current).toPlainString(), "", emptyList(), fromId, toId, title, at)
                    val entry = (when (val r = EntryDrafts.build(draft, EntryId(Uuid.random().toString()), BaseCurrency.current)) { is DraftResult.Ok -> r.entry; is DraftResult.Problem -> return@write r.message }) as app.expenses.core.domain.ledger.Transfer
                    madeTransfer = ok.transfers.replaceEarlier(entry, title, item.reference ?: item.id, itemId, earlierId)
                    if (item.accountLast4 != null) ok.templates.putSettingIfChanged(accountKey(item.issuer, item.accountLast4), if (item.direction == "CREDIT") toId else fromId)
                    _inbox.value = inboxRows(ok)
                    refresh(ok)
                    _inboxUndo.value = ScreenUndo("$MADE_TRANSFER_UNDO$itemId", "Made it a transfer")
                    null
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    private fun undoMadeTransfer(itemId: String) {
        val held = madeTransfer?.takeIf { it.itemId == itemId } ?: return // taken now: the bar clears it as soon as it is gone
        madeTransfer = null
        withStore { ok ->
            try { ok.transfers.undoReplace(held) } catch (e: IllegalArgumentException) { _state.update { st -> (st as? UiState.Ready)?.copy(message = e.message ?: "That could not be undone.") ?: st } }
            _inbox.value = inboxRows(ok); refresh(ok)
        }
    }

    private companion object {
        /** The id of the Undo bar of "Make it a transfer": this and the item's id. */
        const val MADE_TRANSFER_UNDO = "transfer:"
    }

    private fun accountKey(issuer: String, last4: String?) = app.expenses.core.storage.CaptureAccounts.key(issuer, last4)
    private fun payeeNameKey(raw: String) = "payee_name:" + app.expenses.core.domain.CategorySuggest.merchantKey(raw)

    /** The name an entry from a captured text is filed under when the person gave none: the starter list's clean merchant name ("Swiggy" for "swiggyupi@axb"), else the payee as read. The text itself is kept as it was. */
    private fun cleanPayee(raw: String): String = app.expenses.core.domain.DefaultRules.displayNameFor(raw) ?: raw

    /**
     * Turns a pending captured transaction into an entry in [accountId] (and [categoryId] for a debit). [onDone] gets null on success, otherwise
     * why not (shown on the row). A reference already in the ledger from the same source is refused rather than added twice.
     */
    fun confirmCapture(id: String, accountId: String, categoryId: String, onDone: (String?) -> Unit) = confirmCapture(id, accountId, categoryId, "PAYMENT", "", "", "", onDone)

    /** As above, but recorded as [treatment]: a TRANSFER from [fromId] to [toId] (the person's own accounts), or a REFUND of the entry [refundOf]. */
    fun confirmCapture(id: String, accountId: String, categoryId: String, treatment: String, fromId: String, toId: String, refundOf: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok ->
                    val i = ok.inbox.get(id) ?: return@write "That item is no longer in the inbox."
                    if (i.status != InboxStatus.PENDING) return@write "That item was already handled."
                    if (i.currency != BaseCurrency.current.code) return@write "This is in ${i.currency}, but your ledger is in ${BaseCurrency.current.code}. Dismiss it and add it by hand."
                    val kind = if (i.direction == "CREDIT") DraftKind.INCOME else DraftKind.EXPENSE
                    val at = app.expenses.core.domain.CaptureTime.entryTime(i.txnDate?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }, i.receivedAt, kotlinx.datetime.TimeZone.currentSystemDefault())
                    val rawPayee = PayeeGuess.of(i.body.orEmpty())
                    val payee = rawPayee?.let { ok.templates.setting(payeeNameKey(it)) }?.takeIf { it.isNotBlank() } ?: rawPayee?.let(::cleanPayee) ?: i.issuer
                    val amountText = Money(i.amountMinor, BaseCurrency.current).toPlainString()
                    val draft = when (treatment) {
                        "TRANSFER" -> EntryDraft(null, DraftKind.TRANSFER, amountText, "", emptyList(), fromId, toId, payee, at)
                        "REFUND" -> (EntryActions(ok).startRefund(refundOf)?.draft ?: return@write "The expense this refunds is no longer there.").copy(amountText = amountText, occurredAt = at)
                        else -> EntryDraft(null, kind, amountText, categoryId, emptyList(), accountId, "", payee, at)
                    }
                    val entry = when (val r = EntryDrafts.build(draft, EntryId(Uuid.random().toString()), BaseCurrency.current)) {
                        is DraftResult.Ok -> r.entry
                        is DraftResult.Problem -> return@write r.message
                    }
                    var sourceRef = i.reference ?: i.id
                    // the same reference on an entry that points the other way is the payment this one undoes: it gets its own key, not a refusal
                    ok.db.schemaQueries.entryBySourceRef("sms", sourceRef).executeAsOneOrNull()?.let { existing ->
                        val opposite = existing.kind != "TRANSFER" && (existing.kind == "INCOME" || existing.kind == "REFUND") != (i.direction == "CREDIT")
                        if (opposite) sourceRef = "$sourceRef/reversal"
                    }
                    // the unique index on (source, reference) covers deleted entries too: say why instead of failing on it
                    ok.db.schemaQueries.entryBySourceRef("sms", sourceRef).executeAsOneOrNull()?.let { existing ->
                        return@write if (existing.deleted == 1L) "You deleted an entry from this message earlier, so it is not added again. Skip this one."
                        else "An entry from this message is already in your ledger (reference $sourceRef). Skip this one."
                    }
                    ok.store.atomically {
                        ok.store.save(entry, EntryDetails(merchant = payee, note = null, source = "sms", sourceRef = sourceRef))
                        i.body?.let { ok.origins.put(entry.id.value, app.expenses.core.storage.OriginStore.kindOf(i.source, i.sender), i.sender, it, i.receivedAt) }
                        ok.inbox.confirm(id, entry.id.value)
                    }
                    if (treatment == "PAYMENT") ok.templates.putSettingIfChanged(accountKey(i.issuer, i.accountLast4), accountId)
                    settleBillFor(ok, i, entry.id.value)?.let { title -> _state.update { st -> (st as? UiState.Ready)?.copy(message = "Marked $title as paid") ?: st } }
                    // the account on the text's own side of a transfer is the one this bank and last four digits stand for
                    if (treatment == "TRANSFER" && i.accountLast4 != null) ok.templates.putSettingIfChanged(accountKey(i.issuer, i.accountLast4), if (i.direction == "CREDIT") toId else fromId)
                    if (treatment == "PAYMENT" && kind == DraftKind.EXPENSE) ok.captureRules.learn(PayeeGuess.of(i.body.orEmpty()), categoryId)
                    _inbox.value = inboxRows(ok)
                    refresh(ok)
                    null
                }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    private val _rules = MutableStateFlow<List<CaptureRuleUi>>(emptyList())
    val captureRules: StateFlow<List<CaptureRuleUi>> get() = _rules

    private fun ruleRows(ok: AppContainer.Opened.Ok): List<CaptureRuleUi> {
        val names = ok.categories.list().associate { it.id to it.name }
        return ok.captureRules.rules().map { CaptureRuleUi(it.id, it.pattern, it.categoryId, names[it.categoryId] ?: "a category that was removed") }
    }

    fun loadCaptureRules() = withStore { ok -> _rules.value = ruleRows(ok) }

    fun addCaptureRule(pattern: String, categoryId: String, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = try {
                container.write { ok -> ok.captureRules.add(Uuid.random().toString(), pattern, categoryId); _rules.value = ruleRows(ok); _inbox.value = inboxRows(ok); null }
            } catch (e: IllegalArgumentException) { e.message }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    fun removeCaptureRule(id: String) = withStore { ok -> ok.captureRules.remove(id); _rules.value = ruleRows(ok); _inbox.value = inboxRows(ok) }

    fun moveCaptureRule(id: String, up: Boolean) = withStore { ok -> ok.captureRules.move(id, up); _rules.value = ruleRows(ok); _inbox.value = inboxRows(ok) }

    /** How many of the past entries a rule with this pattern would have caught, for the "test against history" line. */
    fun testCaptureRule(pattern: String, onResult: (Long) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val n = container.write { ok -> ok.captureRules.matchesInHistory(pattern) } ?: 0L
            withContext(Dispatchers.Main) { onResult(n) }
        }
    }

    private val _health = MutableStateFlow<CaptureHealthReport?>(null)

    /** How capture has been doing over the last 30 days; null until loaded. Counts only: no text is ever in it. */
    val captureHealth: StateFlow<CaptureHealthReport?> get() = _health

    fun loadCaptureHealth(permissionGranted: Boolean) = withStore { ok ->
        val now = nowMillis()
        val counts = ok.db.schemaQueries.captureEventCounts(now - 30L * 86_400_000).executeAsList().map { CaptureCount(it.sender_root, it.outcome, it.n, it.last_at ?: 0L) }
        // where messages are handed over by a Shortcuts automation there is no switch and no permission to be missing
        val handedOver = platform.supportsShortcutsCapture
        _health.value = CaptureHealth.report(counts, now, handedOver || ok.templates.setting(CaptureKeys.SETTING_SMS_ON) == "1", handedOver || permissionGranted, SenderRules.BUILT_IN)
    }

    /** Adds a sender name the list was missing, so its texts are read from now on. */
    fun addSenderHeader(root: String, permissionGranted: Boolean) = withStore { ok ->
        val have = ok.templates.setting(CaptureKeys.SETTING_EXTRA_ROOTS).orEmpty().split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (have.none { it.equals(root, ignoreCase = true) }) ok.templates.putSetting(CaptureKeys.SETTING_EXTRA_ROOTS, (have + root).joinToString("\n"))
        _captureSettings.value = _captureSettings.value.copy(extraHeaders = ok.templates.setting(CaptureKeys.SETTING_EXTRA_ROOTS).orEmpty())
        val now = nowMillis()
        val counts = ok.db.schemaQueries.captureEventCounts(now - 30L * 86_400_000).executeAsList().map { CaptureCount(it.sender_root, it.outcome, it.n, it.last_at ?: 0L) }
        val extra = ok.templates.setting(CaptureKeys.SETTING_EXTRA_ROOTS).orEmpty().split('\n').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.associateWith { it }
        val handedOver = platform.supportsShortcutsCapture
        _health.value = CaptureHealth.report(counts, now, handedOver || ok.templates.setting(CaptureKeys.SETTING_SMS_ON) == "1", handedOver || permissionGranted, SenderRules.BUILT_IN + extra)
    }

    /** Gives the payee in this text a name of your own ("Chandu" for a phone-number address); every later text for the same address shows it. A blank name takes it off. */
    fun namePayee(id: String, name: String) = withStore { ok ->
        val raw = ok.inbox.get(id)?.body?.let { PayeeGuess.of(it) } ?: return@withStore
        ok.templates.putSetting(payeeNameKey(raw), name.trim())
        _inbox.value = inboxRows(ok)
    }

    /** The text was read the wrong way round (money out as in, or the reverse): turns it, which also re-works the account, category and pairing suggestions. */
    fun flipCaptureDirection(id: String) = withStore { ok -> ok.inbox.flipDirection(id); _inbox.value = inboxRows(ok) }

    /** The skip of a captured item that Undo can take back: its id and the text it had (a skip forgets the text in the database; this copy lives in memory until the bar is gone). */
    private var skippedCapture: Pair<String, String?>? = null
    private val _inboxUndo = MutableStateFlow<ScreenUndo?>(null)
    val inboxUndo: StateFlow<ScreenUndo?> get() = _inboxUndo

    /** Skips a captured item, and offers to bring it back. */
    fun dismissCapture(id: String) = withStore { ok ->
        val item = ok.inbox.get(id)
        val label = _inbox.value.firstOrNull { it.id == id }?.let { r -> r.payee?.takeIf { it.isNotBlank() } ?: r.issuer.takeIf { it.isNotBlank() } } ?: "the item"
        val skipped = ok.inbox.dismiss(id)
        _inbox.value = inboxRows(ok); refresh(ok)
        if (skipped && item != null) { skippedCapture = id to item.body; _inboxUndo.value = ScreenUndo(id, "Skipped $label") }
    }

    /** Brings back the item whose skip was just offered for Undo, as it was (its text included). */
    fun undoDismissCapture(id: String) {
        if (id.startsWith(MADE_TRANSFER_UNDO)) { undoMadeTransfer(id.removePrefix(MADE_TRANSFER_UNDO)); return }
        val held = skippedCapture?.takeIf { it.first == id } // taken now: the bar clears it as soon as it is gone, before the store work below runs
        if (held == null) return
        withStore { ok ->
            ok.inbox.undismiss(id, held.second)
            _inbox.value = inboxRows(ok); refresh(ok)
        }
    }

    /** The Undo bar for a skip has gone (used, or timed out): what was kept for it is let go. A newer skip's own offer is left alone. */
    fun inboxUndoGone(id: String) {
        if (_inboxUndo.value?.id == id) { _inboxUndo.value = null; skippedCapture = null; madeTransfer = null }
    }

    /**
     * Where [Platform.supportsShortcutsCapture] is true (iPhone): puts one pasted bank message through [MessageIngest], with no sender (the words have to
     * say it is a bank's, see [PasteRules]). [text] null reads the clipboard now (call this from the button press, on the main thread); a clipboard with no text answers
     * with [PasteOutcome.clipboardEmpty]. [onResult] runs on the main thread. The words are read on this phone only and kept only if they are a transaction or a bill.
     */
    fun pasteMessage(text: String?, onResult: (PasteOutcome) -> Unit) {
        val words = text ?: platform.readClipboardText()
        if (words.isNullOrBlank()) { onResult(PasteOutcome("There is no text on the clipboard. Copy a bank message first, or type or paste it below.", null, kept = false, clipboardEmpty = true)); return }
        viewModelScope.launch(BACKGROUND) {
            val r = MessageIngest.ingest(container, null, words)
            if (r.kept) container.write { ok -> _inbox.value = inboxRows(ok); refresh(ok) }
            withContext(Dispatchers.Main) { onResult(PasteOutcome(r.message, r.detail, r.kept, clipboardEmpty = false)) }
        }
    }

    // ---- currency ----

    private val _currencyBlocker = MutableStateFlow<String?>(null)

    /** Why the currency cannot be changed right now, or null when it can. Loaded when the currency screen opens. */
    val currencyBlocker: StateFlow<String?> get() = _currencyBlocker

    fun loadCurrencyBlocker() = withStore { ok -> _currencyBlocker.value = BaseCurrencyRules.blocker(ok.currency.facts()) }

    fun setBaseCurrency(code: String) = withStore { ok ->
        val refusal = ok.currency.change(CurrencyChoices.byCode(code))
        BaseCurrency.current = ok.currency.current()
        _currencyBlocker.value = refusal ?: BaseCurrencyRules.blocker(ok.currency.facts())
        refresh(ok)
    }

    // ---- appearance ----

    fun setAppearance(a: Appearance) = withStore { ok -> ok.templates.putSetting(SETTING_APPEARANCE, a.encode()); refresh(ok) }

    // ---- privacy ----

    fun setHideAmounts(hide: Boolean) = withStore { ok ->
        ok.templates.putSetting(SETTING_HIDE_AMOUNTS, if (hide) "1" else "0")
        refresh(ok)
    }

    // ---- home layout ----

    /** Saves which home blocks are shown and in what order; takes effect at once. */
    fun setHomeLayout(layout: HomeLayout) = withStore { ok ->
        ok.templates.putSetting(SETTING_HOME_LAYOUT, layout.encode())
        refresh(ok)
    }

    // ---- first run and reminders ----

    /** "Skip" on the first-run card: it is not shown again. */
    fun dismissWelcome() = withStore { ok ->
        ok.templates.putSetting(SETTING_WELCOME_DISMISSED, "1")
        refresh(ok)
    }

    /** "Later" on the backup reminder: quiet for [BackupNudge.SNOOZE_DAYS] days. */
    fun snoozeBackupNudge() = withStore { ok ->
        ok.templates.putSetting(SETTING_NUDGE_SNOOZED_AT, nowMillis().toString())
        refresh(ok)
    }

    // ---- repeating expenses ----

    /** Posts a due occurrence. [amount] is required only for items whose amount differs each time. */
    fun postDue(itemId: String, epochDay: Long, amount: Money?) = withStore { ok ->
        val day = kotlinx.datetime.LocalDate.fromEpochDays(epochDay)
        val item = ok.recurring.list().firstOrNull { it.id == itemId }
        val entryId = ok.recurring.post(itemId, day, kotlinx.datetime.TimeZone.currentSystemDefault(), amount)
        refresh(ok, undo = entryId?.let { UndoUi(it, "Posted ${item?.name.orEmpty()} ${(amount ?: item?.amount)?.formatted().orEmpty()}".trim(), recurringId = itemId, recurringDay = epochDay) })
    }

    fun skipDue(itemId: String, epochDay: Long) = withStore { ok ->
        val item = ok.recurring.list().firstOrNull { it.id == itemId }
        ok.recurring.skip(itemId, kotlinx.datetime.LocalDate.fromEpochDays(epochDay))
        refresh(ok, undo = UndoUi("", "Skipped ${item?.name.orEmpty()}", recurringId = itemId, recurringDay = epochDay))
    }

    /** Creates ([id] null) or changes a repeating expense. Changes apply to occurrences not yet posted; posted ones stay as they are. */
    fun saveRecurring(id: String?, name: String, amount: Money?, category: String, account: String, merchant: String, schedule: Schedule, auto: Boolean, kind: RecurringKind = RecurringKind.EXPENSE, toAccount: String = "") = withStore { ok ->
        ok.recurring.save(RecurringItem(
            id ?: Uuid.random().toString(), name.trim(), amount, category.takeIf { kind != RecurringKind.TRANSFER }?.let(::CategoryId), AccountId(account),
            merchant.trim().ifEmpty { null }, schedule, auto && amount != null, kind, toAccount.takeIf { kind == RecurringKind.TRANSFER }?.let(::AccountId),
        ))
        refresh(ok)
    }

    fun deleteRecurring(id: String) = withStore { ok ->
        ok.recurring.delete(id)
        refresh(ok)
    }

    /** Posts occurrences of items set to post by themselves. Called when the app comes to the front. */
    fun postAutomatic() = withStore { ok ->
        val today = todayLocal()
        val done = ok.recurring.autoPost(today, kotlinx.datetime.TimeZone.currentSystemDefault())
        if (done.isNotEmpty()) {
            refresh(ok)
            _state.update { (it as? UiState.Ready)?.copy(message = if (done.size == 1) "Posted ${done[0].first.item.name}" else "Posted ${done.size} repeating expenses") ?: it }
        }
    }

    // ---- statement import ----

    /** Reads a statement file the user picked and guesses its layout. Nothing is saved. */
    fun startImport(uri: String) {
        _import.value = ImportUi(active = true, busy = true)
        viewModelScope.launch(BACKGROUND) {
            try {
                val bytes = platform.readBytes(uri)
                require(bytes.size <= MAX_STATEMENT_BYTES) { "That file is too large to import (over ${MAX_STATEMENT_BYTES / 1_000_000} MB). Is it a bank statement?" }
                // a PDF or a picture: the text is read on this device and turned into the same kind of table, so the rest of the screen works unchanged
                if (platform.looksLikePdf(bytes) || platform.looksLikeImage(bytes)) {
                    // the screen says what is being read ("Reading the PDF on this phone"): the note under the spinner needs to know, and the file was only just seen to be one
                    _import.update { it.copy(readFrom = if (platform.looksLikePdf(bytes)) "PDF" else "photo") }
                    readTextStatement(bytes, null); return@launch
                }
                clearTextImport()
                val table = when {
                    platform.looksLikeXlsx(bytes) -> app.expenses.core.domain.CsvTable(platform.readXlsx(bytes), ',', false)
                    // the older Excel format (.xls), which many banks still hand out
                    app.expenses.core.domain.XlsReader.looksLikeXls(bytes) -> app.expenses.core.domain.CsvTable(app.expenses.core.domain.XlsReader.read(bytes), ',', false)
                    app.expenses.core.domain.XlsReader.looksLikeWebPageOrXml(bytes) -> throw IllegalArgumentException(app.expenses.core.domain.XlsReader.WEB_PAGE_MESSAGE)
                    else -> Csv.parse(platform.decodeStatementText(bytes))
                }
                require(!OwnExport.matches(table.rows)) { OwnExport.MESSAGE }
                require(table.rows.size >= 2) { "That file has no rows to import. A statement CSV has a header row and at least one transaction." }
                require(table.rows.size <= MAX_STATEMENT_ROWS) { "That file has too many rows to import (over $MAX_STATEMENT_ROWS)." }
                importTable = table
                importSourceTable = table; importPreset = null; importPresetOff = false; importCardPick = null; importCreateCategories = false
                val accounts = container.write { it.db.schemaQueries.selectAccounts().executeAsList().filter { a -> a.archived == 0L } }.orEmpty()
                val account = (_state.value as? UiState.Ready)?.lastAccountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id.orEmpty()
                // a file that is another app's export is read by what its columns mean; anything else is guessed as a bank statement
                val preset = app.expenses.core.domain.ImportPresets.read(table.rows, table.lines, inr.code, inr.minorUnitDigits)
                if (preset != null) {
                    importPreset = preset; importTable = preset.table; importRawLines = preset.rawLines
                    recomputeImport(preset.mapping, account)
                    return@launch
                }
                readAsBankFile(table, account)
            } catch (e: IllegalArgumentException) {
                _import.value = ImportUi(active = true, error = e.message)
            } catch (e: app.expenses.core.domain.XlsReader.Unreadable) {
                _import.value = ImportUi(active = true, error = e.message)
            } catch (e: Exception) {
                _import.value = ImportUi(active = true, error = "That file could not be read: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    /** Reads [table] as a bank statement: the way the person set up the last file with the same column titles, else as guessed. */
    private suspend fun readAsBankFile(table: app.expenses.core.domain.CsvTable, account: String) {
        val guessed = StatementDetector.detect(table.rows)
        // The header may not be where the detector thinks (it cannot place unusual titles), so every one of the first rows is tried.
        val width = table.rows.maxOf { r -> r.size }
        val saved = container.write { ok -> table.rows.take(PROFILE_SEARCH_ROWS).firstNotNullOfOrNull { row -> ImportProfiles.signature(row)?.let { sig -> ImportProfiles.decode(ok.templates.setting(sig), width) } } }
        recomputeImport(saved ?: guessed, account, profileNote = if (saved != null) "Read the way you set it up for the last file with these column titles. Change anything below if it is wrong." else null)
    }

    /** The file was recognised as another app's export but the person wants it read as a plain statement, with the columns chosen by hand. */
    fun readImportAsPlainFile() {
        val table = importSourceTable ?: return
        importPresetOff = true; importPreset = null; importTable = table; importRawLines = null
        viewModelScope.launch(BACKGROUND) { readAsBankFile(table, _import.value.accountId) }
    }

    /** The password for the PDF that asked for one. It is used once to open the file and is neither stored nor logged. */
    fun submitImportPassword(password: String) {
        val bytes = importProtectedPdf ?: return
        _import.value = ImportUi(active = true, busy = true, readFrom = "PDF")
        viewModelScope.launch(BACKGROUND) {
            try {
                readTextStatement(bytes, password)
            } catch (e: IllegalArgumentException) {
                _import.value = ImportUi(active = true, error = e.message)
            } catch (e: Exception) {
                _import.value = ImportUi(active = true, error = "That file could not be read: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    private fun clearTextImport() {
        importText = null; importRawLines = null; importReadFrom = null; importReadNote = null; importCardPayments = 0; importConvention = null; importProtectedPdf = null; importRead = null
    }

    /** Reads the text of a PDF (with [password] when it has one) or of a picture, and shows the rows found in it. Runs on the background dispatcher. */
    private suspend fun readTextStatement(bytes: ByteArray, password: String?) {
        clearTextImport()
        val pdf = platform.looksLikePdf(bytes)
        var kind = if (pdf) "PDF" else "photo"
        val text = if (pdf) when (val r = platform.readPdfText(bytes, password)) {
            is PdfText.Text -> { if (r.recognised) kind = "scanned PDF"; r.text }
            PdfText.PasswordNeeded, PdfText.WrongPassword -> {
                importProtectedPdf = bytes
                _import.value = ImportUi(active = true, readFrom = kind, needsPassword = true, passwordError = if (r == PdfText.WrongPassword) "That password did not open the PDF. Check it and try again." else null)
                return
            }
            is PdfText.Unreadable -> throw IllegalArgumentException(r.reason)
        } else platform.recognizeText(bytes)
        val accountId = importAccountId()
        val convention = importConventionFor(accountId)
        val read = StatementTextParser.parse(text, convention)
        if (read.rowCount == 0) {
            throw IllegalArgumentException(
                if (pdf) "Could not find transactions in this PDF. Is it a scanned statement? Try a photo or the bank's CSV."
                else "Could not find transactions in this photo. Try a sharper, straighter picture that shows whole lines, or the bank's CSV.",
            )
        }
        importText = text; importReadFrom = kind
        applyTextRead(read)
        recomputeImport(StatementDetector.detect(read.table.rows), accountId)
    }

    private fun applyTextRead(read: StatementTextParser.Result) {
        importTable = read.table; importRawLines = read.rawLines; importCardPayments = read.cardPayments; importReadNote = read.note; importConvention = read.convention; importRead = read
    }

    /** The account to start a statement on: the one used last, else the first. */
    private suspend fun importAccountId(): String {
        val accounts = container.write { it.db.schemaQueries.selectAccounts().executeAsList().filter { a -> a.archived == 0L } }.orEmpty()
        return (_state.value as? UiState.Ready)?.lastAccountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id.orEmpty()
    }

    /** A credit card account reads an amount with no mark as spending; for any other account the words in the statement decide (null). */
    private suspend fun importConventionFor(accountId: String): StatementTextParser.Convention? =
        if (container.write { ok -> ok.db.schemaQueries.selectAccounts().executeAsList().firstOrNull { it.id == accountId }?.type } == AccountType.CREDIT_CARD.name) StatementTextParser.Convention.CARD else null

    /** The account was changed on a PDF or photo import: a card reads the same text the card way, so read it again. */
    private fun rereadTextImport(accountId: String) {
        val text = importText ?: return
        viewModelScope.launch(BACKGROUND) {
            val convention = importConventionFor(accountId)
            val read = StatementTextParser.parse(text, convention)
            if (read.convention != importConvention || read.cardPayments != importCardPayments) applyTextRead(read)
            recomputeImport(_import.value.mapping ?: StatementDetector.detect(read.table.rows), accountId, _import.value.profileNote)
        }
    }

    fun changeImportMapping(change: (StatementMapping) -> StatementMapping) {
        val m = _import.value.mapping ?: return
        viewModelScope.launch(BACKGROUND) { recomputeImport(change(m), _import.value.accountId) }
    }

    fun changeImportAccount(id: String) {
        _import.update { it.copy(accountId = id) }
        if (importText != null) rereadTextImport(id)
        // the account decides which side of a card payment the statement is, so the preview is made again
        else viewModelScope.launch(BACKGROUND) { _import.value.mapping?.let { recomputeImport(it, id, _import.value.profileNote) } }
    }

    /**
     * What to do with the lines that look like paying a card's bill: [choice] is TRANSFER, AS_IS (spending on a bank statement, income on a card's) or SKIP. For a
     * transfer, [account] is the account all of them go to (or come from, on a card's statement); empty lets each line's own text decide.
     */
    fun chooseCardPayments(choice: String, account: String) {
        val chosen = app.expenses.core.domain.CardPaymentChoice.entries.firstOrNull { it.name == choice } ?: return
        val side = _import.value.cardGroup?.side?.let { n -> app.expenses.core.domain.CardPayments.Side.entries.firstOrNull { it.name == n } } ?: return
        importCardPick = side to app.expenses.core.domain.CardPayments.Pick(chosen, account.ifEmpty { null })
        viewModelScope.launch(BACKGROUND) { _import.value.mapping?.let { recomputeImport(it, _import.value.accountId, _import.value.profileNote) } }
    }

    /** The person's tick to create the categories of a recognised file that they have none of. */
    fun setCreateImportCategories(create: Boolean) {
        importCreateCategories = create
        viewModelScope.launch(BACKGROUND) { _import.value.mapping?.let { recomputeImport(it, _import.value.accountId, _import.value.profileNote) } }
    }

    /** The person's live accounts in the base currency (the ones a statement can go to or be matched with) and their visible categories. */
    private suspend fun importContext(): Pair<List<app.expenses.core.domain.ImportAccount>, List<app.expenses.core.domain.CategoryRef>> =
        container.write { ok ->
            val last4 = ok.looks.all().associate { it.accountId to it.last4 }
            val accounts = ok.db.schemaQueries.selectAccounts().executeAsList().filter { it.archived == 0L && it.currency == inr.code }
                .map { app.expenses.core.domain.ImportAccount(it.id, it.name, it.type == AccountType.CREDIT_CARD.name, last4[it.id]) }
            val categories = ok.categories.list().filter { !it.hidden }.map { app.expenses.core.domain.CategoryRef(it.id, it.name, it.parentId, it.kind.forExpense, it.kind.forIncome) }
            accounts to categories
        } ?: (emptyList<app.expenses.core.domain.ImportAccount>() to emptyList<app.expenses.core.domain.CategoryRef>())

    private suspend fun recomputeImport(mapping: StatementMapping, accountId: String, profileNote: String? = null) {
        val table = importTable ?: return
        val names = StatementDetector.columnNames(table.rows, mapping.headerRows)
        val missing = mapping.missing()
        val raw = importRawLines
        val parsed = if (missing == null) StatementParser.parse(table.rows, mapping, inr.minorUnitDigits, { table.lines[it] }, { raw?.getOrNull(it) }) else null
        importParsed = parsed
        val preset = importPreset
        val plan = parsed?.let { p ->
            val (accounts, categories) = importContext()
            val into = accounts.firstOrNull { it.id == accountId } ?: app.expenses.core.domain.ImportAccount(accountId, "", false)
            app.expenses.core.domain.ImportPlanner.plan(p.txns, into, accounts, categories, preset != null, importCardPick, importCreateCategories)
        }
        importReady = plan?.txns
        importNewCategories = plan?.newCategories.orEmpty()
        val shown = plan?.shown.orEmpty()
        val dateFmt = DatePattern("d MMM yyyy")
        fun row(it: StatementTxn) = ImportRowUi(
            dateFmt.format(localDateOfEpochDay(it.date.toEpochDays())) + (if (it.description.isNotEmpty()) " · " + it.description else ""), Money(it.minor, inr).formatted(),
            it.flow == Flow.SPENDING, transfer = it.counterAccount != null,
        )
        fun sum(flow: Flow) = Money(shown.filter { it.flow == flow && it.counterAccount == null }.sumOf { it.minor }, inr)
        val found = plan?.found
        val group = if (plan == null || found == null || parsed == null) null else {
            val rows = found.indexes.map { parsed.txns[it] }
            val targets = found.indexes.map { app.expenses.core.domain.CardPayments.target(found, it, plan.pick, plan.others) }
            val common = targets.distinct().singleOrNull()
            val transfer = plan.pick.choice == app.expenses.core.domain.CardPaymentChoice.TRANSFER
            CardGroupUi(
                side = found.side.name, count = rows.size, total = Money(rows.sumOf { it.minor }, inr).formatted(), rows = rows.take(CARD_ROWS_LISTED).map(::row),
                choice = plan.pick.choice?.name.orEmpty(), account = if (transfer) common.orEmpty() else "", accounts = plan.others.map { AccountUi(it.id, it.name) },
                eachNamed = transfer && plan.pick.account == null && targets.distinct().size > 1, skipped = plan.skippedCardPayments, transfers = plan.transfers,
                resolved = plan.txns != null,
            )
        }
        val leftOut = preset?.leftOut
        _import.value = ImportUi(
            active = true, columnNames = names, mapping = mapping, accountId = accountId,
            dateAmbiguous = StatementDetector.dateIsAmbiguous(table.rows, mapping) && preset?.dateOrderKnown != true, damaged = table.damaged, rowCount = table.rows.size - mapping.headerRows,
            preview = shown.take(PREVIEW_ROWS).map(::row),
            txnCount = shown.size, spending = sum(Flow.SPENDING).formatted(), income = sum(Flow.INCOME).formatted(),
            problems = parsed?.problems?.take(PROBLEMS_SHOWN)?.map { "Line ${it.row}: ${it.reason}" }.orEmpty(), problemCount = parsed?.problems?.size ?: 0,
            ignoredRows = parsed?.ignoredRows ?: 0, repairedRows = parsed?.repairedRows ?: 0, mappingProblem = missing, profileNote = profileNote,
            readFrom = importReadFrom, readNote = importReadNote, cardPayments = importCardPayments,
            totalsNote = if (importReadFrom != null && parsed != null) importRead?.check(parsed.txns) else null,
            cardGroup = group, transfers = plan?.transfers ?: 0,
            preset = preset?.app?.label, presetNote = preset?.note, presetLeftOut = leftOut,
            categories = if (preset == null || plan == null) null else ImportCategoriesUi(plan.newCategories.map { it.name }, importCreateCategories, plan.withCategory, plan.onOwnAccount),
        )
    }

    /** Saves what the preview showed. Already-imported rows are skipped, so doing it twice is harmless. */
    fun confirmImport() {
        val txns = importReady ?: return
        val account = _import.value.accountId
        if (account.isEmpty() || txns.isEmpty()) return
        val create = if (importCreateCategories) importNewCategories else emptyList()
        _import.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(BACKGROUND) {
            try {
                container.write { ok ->
                    // the categories the person ticked are made in the same all-or-nothing step as the entries that use them
                    val r = ok.store.atomically {
                        var rows = txns
                        if (create.isNotEmpty()) {
                            val all = ok.categories.list()
                            val made = HashMap<Pair<String, Boolean>, String>()
                            for (n in create) {
                                if (app.expenses.core.domain.CategoryRules.checkName(all, n.name, null) != null) continue // one with that name is there already (hidden, or for the other kind)
                                made[n.name.trim().lowercase() to n.income] = ok.categories.create(n.name, null, null, if (n.income) app.expenses.core.domain.CategoryKind.INCOME else app.expenses.core.domain.CategoryKind.EXPENSE).id
                            }
                            rows = rows.map { t ->
                                if (t.categoryId != null) t
                                else app.expenses.core.domain.ImportCategories.nameOf(t.category, t.subcategory)?.let { made[it.trim().lowercase() to (t.flow == Flow.INCOME)] }?.let { id -> t.copy(categoryId = id) } ?: t
                            }
                        }
                        ok.statements.import(rows, AccountId(account), inr, kotlinx.datetime.TimeZone.currentSystemDefault())
                    }
                    // Remember how this bank's file was read, now that it is known to have worked, for the next one with the same column titles.
                    val mapping = _import.value.mapping
                    val signature = mapping?.takeIf { it.headerRows > 0 && importText == null && importPreset == null }?.let { m -> importTable?.rows?.getOrNull(m.headerRows - 1)?.let(ImportProfiles::signature) }
                    if (mapping != null && signature != null) ok.templates.putSettingIfChanged(signature, ImportProfiles.encode(mapping))
                    refresh(ok)
                    _import.update { it.copy(busy = false, result = ImportResultUi(r.imported, r.alreadyThere, Money(r.spendingMinor, inr).formatted(), Money(r.incomeMinor, inr).formatted(), r.matchedExisting, r.transfers, Money(r.transferMinor, inr).formatted())) }
                }
            } catch (e: Exception) {
                _import.update { it.copy(busy = false, error = "Nothing was imported: ${e.message ?: e::class.simpleName}") }
            }
        }
    }

    fun closeImport() {
        importTable = null
        importParsed = null
        importReady = null; importNewCategories = emptyList(); importPreset = null; importPresetOff = false; importSourceTable = null; importCardPick = null; importCreateCategories = false
        clearTextImport()
        _import.value = ImportUi()
    }

    // ---- search ----

    /** Searches as the user types: waits a moment for more typing, then looks. Checks the amounts first and says what is wrong. */
    fun search(input: SearchInput) {
        searchInput = input
        searchJob?.cancel()
        val min = input.minRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) ?: return problem("Enter amounts as numbers, like 250 or 99.50") }
        val max = input.maxRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) ?: return problem("Enter amounts as numbers, like 250 or 99.50") }
        if (min != null && max != null && min.minorUnits > max.minorUnits) return problem("The smallest amount is above the largest")
        app.expenses.core.domain.SearchDates.problem(input.fromDay, input.toDay)?.let { return problem(it) }
        val conditions = input.text.isNotBlank() || min != null || max != null || input.categoryId != null || input.kind != SearchKind.ANY || input.range != SearchRange.ANY || input.tag != null ||
            input.fromDay != null || input.toDay != null || input.accountId != null
        if (!conditions) { _search.value = SearchUi(); return }
        _search.update { it.copy(searching = true, hasConditions = true, problem = null) }
        searchJob = viewModelScope.launch(BACKGROUND) {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            // the result is published on the main thread: a state write from the database thread woke the screen's collector off the main thread,
            // which under the test clock killed the process (CalledFromWrongThreadException, then the slot-table crash)
            val result = container.write { ok -> runSearch(ok, input, min, max) }
            if (result != null) withContext(Dispatchers.Main) { _search.value = result }
        }
    }

    private fun problem(message: String) { _search.value = SearchUi(hasConditions = true, problem = message) }

    fun closeSearch() {
        searchJob?.cancel()
        searchInput = null
        _search.value = SearchUi()
    }

    private fun runSearch(ok: AppContainer.Opened.Ok, input: SearchInput, min: Money?, max: Money?): SearchUi {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = todayLocal()
        val today = t
        val period: app.expenses.core.domain.DatePeriod? = if (input.fromDay != null || input.toDay != null) null else when (input.range) {
            SearchRange.ANY -> null
            SearchRange.LAST_30_DAYS -> app.expenses.core.domain.DatePeriod(today.minus(29, kotlinx.datetime.DateTimeUnit.DAY), today.plus(1, kotlinx.datetime.DateTimeUnit.DAY))
            SearchRange.THIS_PERIOD -> ReportMath.periodAt(ok.budgets.period(), today, 0)
            SearchRange.THIS_YEAR -> ReportMath.yearAt(today, 0)
        }
        val cats = ok.categories.list()
        // A chosen category also covers its subcategories.
        val categoryIds = input.categoryId?.let { id -> listOf(id) + cats.filter { it.parentId == id }.map { it.id } }
        val query = SearchQuery(
            text = input.text, minMinor = min?.minorUnits, maxMinor = max?.minorUnits, currency = if (min != null || max != null) inr.code else null,
            tag = input.tag, categoryIds = categoryIds, kind = when (input.kind) { SearchKind.ANY -> null; SearchKind.EXPENSE -> "EXPENSE"; SearchKind.INCOME -> "INCOME" },
            accountId = input.accountId,
            // chosen days take the place of the period chips: the first day from its start, the last day to its end (both in the phone's zone)
            fromMillis = if (period == null) app.expenses.core.domain.SearchDates.fromMillis(input.fromDay, zone) else period.startMillis(zone),
            untilMillis = if (period == null) app.expenses.core.domain.SearchDates.untilMillis(input.toDay, zone) else period.endMillis(zone),
        )
        val found = ok.db.searchFiltered(query, SEARCH_LIMIT + 1)
        val shown = found.take(SEARCH_LIMIT.toInt())
        accountNames = ok.db.schemaQueries.selectAccounts().executeAsList().associate { it.id to it.name }
        val firsts = firstCategories(ok, shown)
        return SearchUi(shown.map { toRow(it, firsts) }, searching = false, hasConditions = true, truncated = found.size > SEARCH_LIMIT)
    }

    // ---- reports ----

    fun openReports() = reportAction { ReportRequest(ReportMode.PERIOD, 0) }
    fun setReportMode(mode: ReportMode) = reportAction { r -> r.copy(mode = mode, offset = 0, drillTarget = null, drillStart = null, drillEnd = null) }
    fun setReportGroup(group: ReportGroup) = reportAction { r -> r.copy(group = group, drillTarget = null, drillStart = null, drillEnd = null) }
    fun stepReport(delta: Int) = reportAction { r -> if (r.mode == ReportMode.CUSTOM) r else r.copy(offset = minOf(0, r.offset + delta), drillTarget = null, drillStart = null, drillEnd = null) }

    /** A report over any dates: [startDay] to [endDay] inclusive, as days since 1970-01-01. An end before the start is swapped. */
    fun setReportRange(startDay: Long, endDay: Long) = reportAction { r ->
        val a = minOf(startDay, endDay); val b = maxOf(startDay, endDay)
        r.copy(mode = ReportMode.CUSTOM, customStart = a, customEnd = b + 1, drillTarget = null, drillStart = null, drillEnd = null)
    }

    /** Opens the entries behind a figure: a category, the whole period, uncategorised, or income. [startDay] and [endDay] pick another period (a trend bar). */
    fun drill(target: DrillTarget, startDay: Long? = null, endDay: Long? = null, label: String? = null) = reportAction { r -> r.copy(drillTarget = target, drillStart = startDay, drillEnd = endDay, drillLabel = label) }

    /** Leaves the entry list and returns to the report. */
    fun closeDrill() = reportAction { r -> r.copy(drillTarget = null, drillStart = null, drillEnd = null) }

    fun closeReports() {
        reportRequest = null
        _report.value = null
    }

    // ---- calendar ----

    private data class CalendarRequest(val offset: Int, val selectedEpochDay: Long?)
    private var calendarRequest: CalendarRequest? = null
    private val _calendar = MutableStateFlow<CalendarUi?>(null)
    val calendar: StateFlow<CalendarUi?> get() = _calendar

    private fun calendarAction(next: (CalendarRequest) -> CalendarRequest) {
        calendarRequest = next(calendarRequest ?: CalendarRequest(0, null))
        viewModelScope.launch(BACKGROUND) { container.write { ok -> calendarRequest?.let { _calendar.value = buildCalendar(ok, it) } } }
    }

    // ---- subscriptions: charges that keep coming ----

    private val _subscriptions = MutableStateFlow<SubscriptionsUi?>(null)
    val subscriptions: StateFlow<SubscriptionsUi?> get() = _subscriptions

    private fun subscriptionsUi(ok: AppContainer.Opened.Ok): SubscriptionsUi {
        val today = todayLocal()
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val since = today.minus(400, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        val samples = ok.db.schemaQueries.expenseSamples(inr.code, since).executeAsList().map { SubscriptionSample(it.name.orEmpty(), dateOfMillis(it.occurred_at).epochDay().toInt(), it.amount_minor) }
        val ignored = ok.templates.setting(SETTING_SUBS_IGNORED).orEmpty().split('\n').filter { it.isNotBlank() }.toSet()
        val all = SubscriptionFinder.find(samples, today.toEpochDays().toInt())
        val shown = all.filter { !it.ignoredBy(ignored) } // by merchant and cadence: a changed typical price does not bring a hidden row back
        val dayFmt = DatePattern("d MMM")
        fun cadence(d: Int) = when (d) { 28 -> "every 28 days"; 30 -> "every month"; 84 -> "every 12 weeks"; 91 -> "every 3 months"; 182 -> "every 6 months"; else -> "every year" }
        val existing = ok.recurring.list().map { it.name.trim().lowercase() }.toSet()
        return SubscriptionsUi(
            shown.map {
                SubscriptionRowUi(
                    it.key, it.name, Money(it.amountMinor, inr).formatted(), cadence(it.cadenceDays), "next about " + dayFmt.format(localDateOfEpochDay(it.nextDay.toLong())), Money(it.yearlyMinor, inr).formatted() + " a year",
                    repeats = it.name.trim().lowercase() in existing, lastText = "Last charged ${Money(it.amountMinor, inr).formatted()} on ${dayFmt.format(localDateOfEpochDay(it.lastDay.toLong()))}",
                    priceNote = it.previousAmountMinor?.let { before -> (if (it.priceWentUp) "Price went up from " else "Price went down from ") + Money(before, inr).formatted() }.orEmpty(), priceUp = it.priceWentUp,
                )
            },
            Money(shown.sumOf { it.monthlyMinor }, inr).formatted(), Money(shown.sumOf { it.yearlyMinor }, inr).formatted(), all.size - shown.size,
        )
    }

    /**
     * Makes a charge that keeps coming into a repeating expense, so the app reminds about it when it is due (it is not posted by itself): same name, amount,
     * category and account as the latest one, on its usual gap from its next expected date. Does nothing when one with that name is already there.
     */
    fun repeatSubscription(key: String) = withStore { ok ->
        val today = todayLocal()
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val since = today.minus(400, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        val samples = ok.db.schemaQueries.expenseSamples(inr.code, since).executeAsList().map { SubscriptionSample(it.name.orEmpty(), dateOfMillis(it.occurred_at).epochDay().toInt(), it.amount_minor) }
        val sub = SubscriptionFinder.find(samples, today.toEpochDays().toInt()).firstOrNull { it.key == key } ?: return@withStore
        if (ok.recurring.list().any { it.name.trim().lowercase() == sub.name.trim().lowercase() }) return@withStore
        // the latest charge is looked up by the merchant as it was written on it (the finder's key may be a clean name for several spellings)
        val latest = ok.db.schemaQueries.latestExpenseNamed(inr.code, sub.lastName.trim().lowercase()).executeAsOneOrNull() ?: return@withStore
        val category = ok.db.schemaQueries.splitsOf(latest.id).executeAsList().firstOrNull()?.category_id
        val (unit, every) = when (sub.cadenceDays) { 28 -> RepeatUnit.WEEK to 4; 30 -> RepeatUnit.MONTH to 1; 84 -> RepeatUnit.WEEK to 12; 91 -> RepeatUnit.MONTH to 3; 182 -> RepeatUnit.MONTH to 6; else -> RepeatUnit.YEAR to 1 }
        // the next date on the gap that is not in the past
        var start = localDateOfEpochDay(sub.nextDay.toLong())
        while (start < today) start = when (unit) { RepeatUnit.YEAR -> start.plus(1, kotlinx.datetime.DateTimeUnit.YEAR); RepeatUnit.WEEK -> start.plus(every * 7, kotlinx.datetime.DateTimeUnit.DAY); else -> start.plus(every, kotlinx.datetime.DateTimeUnit.MONTH) }
        ok.recurring.save(RecurringItem(Uuid.random().toString(), sub.name, Money(sub.amountMinor, inr), category?.let(::CategoryId), AccountId(latest.account_id ?: return@withStore), sub.name, Schedule(unit, every, start)))
        _subscriptions.value = subscriptionsUi(ok)
        refresh(ok)
    }

    // ---- the rates chosen last for each foreign currency ----

    private val _rates = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The rate used last for each foreign currency, to start the next entry in it from. */
    val foreignRates: StateFlow<Map<String, String>> get() = _rates

    private fun loadRates(ok: AppContainer.Opened.Ok) { _rates.value = CurrencyChoices.ALL.mapNotNull { c -> ok.templates.setting("rate_${c.code}")?.takeIf { it.isNotBlank() }?.let { c.code to it } }.toMap() }

    // ---- savings goals ----

    private val _goals = MutableStateFlow<GoalsUi?>(null)
    val goals: StateFlow<GoalsUi?> get() = _goals

    private fun goalsOf(ok: AppContainer.Opened.Ok) = GoalCodec.decode(ok.templates.setting(SETTING_GOALS))
    private fun putGoals(ok: AppContainer.Opened.Ok, goals: List<Goal>) { ok.templates.putSetting(SETTING_GOALS, GoalCodec.encode(goals)); _goals.value = goalsUi(ok) }

    private fun goalsUi(ok: AppContainer.Opened.Ok): GoalsUi {
        val today = todayLocal().toEpochDays().toInt()
        val balances = ok.store.balances()
        val names = ok.db.schemaQueries.allAccounts().executeAsList().associate { it.id to it.name }
        val dayFmt = DatePattern("d MMM yyyy")
        val rows = goalsOf(ok).map { g ->
            val linked = g.accountId?.takeIf { it in names } // a goal restored on a phone without that account keeps what was put aside by hand
            val saved = (linked?.let { balances[AccountId(it)]?.minorUnits } ?: g.savedMinor).coerceAtLeast(0)
            val monthly = GoalMath.monthly(saved, g.targetMinor, g.dueDay, today)
            GoalRowUi(
                g.id, g.name, Money(saved, inr).formatted(), Money(g.targetMinor, inr).formatted(), GoalMath.percent(saved, g.targetMinor),
                if (saved >= g.targetMinor) "Reached" else Money(GoalMath.remaining(saved, g.targetMinor), inr).formatted() + " to go",
                g.dueDay?.let { "by " + dayFmt.format(localDateOfEpochDay(it.toLong())) }.orEmpty(),
                monthly?.let { Money(it, inr).formatted() + " a month to get there" }.orEmpty(),
                linked?.let { names[it] }.orEmpty(), linked.orEmpty(), Money(g.targetMinor, inr).toPlainString(),
                g.dueDay?.let { ((it - today + 29) / 30).coerceAtLeast(1).toString() }.orEmpty(),
            )
        }
        return GoalsUi(rows, ok.db.schemaQueries.selectAccounts().executeAsList().filter { it.archived == 0L && AccountType.valueOf(it.type) != AccountType.CREDIT_CARD }.map { AccountUi(it.id, it.name, it.type) })
    }

    fun openGoals() = withStore { ok -> _goals.value = goalsUi(ok) }
    fun closeGoals() { _goals.value = null }

    /** Adds a goal ([id] null) or changes one. [monthsText] is how many months from now to reach it by, or empty. Reports a problem as text, null when saved. */
    fun saveGoal(id: String?, name: String, targetText: String, monthsText: String, accountId: String?, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = container.write { ok ->
                val clean = GoalCodec.clean(name)
                val target = parseAmount(targetText, inr)?.minorUnits
                val months = monthsText.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull()?.takeIf { m -> m in 1..600 } ?: return@write "Months must be a whole number from 1 to 600, or empty." }
                when {
                    clean.isEmpty() -> "Give the goal a name."
                    target == null || target <= 0 -> "Enter how much you want to reach, like 50000."
                    else -> {
                        val goals = goalsOf(ok)
                        val old = goals.firstOrNull { it.id == id }
                        val due = months?.let { todayLocal().toEpochDays().toInt() + it * 30 }
                        val goal = Goal(old?.id ?: Uuid.random().toString(), clean, target, old?.savedMinor ?: 0, due, accountId?.ifEmpty { null })
                        putGoals(ok, if (old == null) goals + goal else goals.map { if (it.id == old.id) goal else it })
                        null
                    }
                }
            }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    /** Puts money towards a goal kept by hand ([add]) or takes some back; this is a note of what is set aside, no entry is made. */
    fun adjustGoal(id: String, amountText: String, add: Boolean, onDone: (String?) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            val problem = container.write { ok ->
                val amount = parseAmount(amountText, inr)?.minorUnits?.takeIf { it > 0 } ?: return@write "Enter an amount like 2000."
                val goals = goalsOf(ok)
                val g = goals.firstOrNull { it.id == id } ?: return@write "That goal is gone."
                if (g.accountId != null) return@write "This goal follows an account; its money moves with that account."
                putGoals(ok, goals.map { if (it.id == id) it.copy(savedMinor = (it.savedMinor + if (add) amount else -amount).coerceAtLeast(0)) else it })
                null
            }
            withContext(Dispatchers.Main) { onDone(problem) }
        }
    }

    fun deleteGoal(id: String) = withStore { ok -> putGoals(ok, goalsOf(ok).filterNot { it.id == id }) }

    fun openSubscriptions() = withStore { ok -> _subscriptions.value = subscriptionsUi(ok) }
    fun closeSubscriptions() { _subscriptions.value = null }

    /** "Not a subscription": hides this guess from the list. */
    fun ignoreSubscription(key: String) = withStore { ok ->
        val now = ok.templates.setting(SETTING_SUBS_IGNORED).orEmpty().split('\n').filter { it.isNotBlank() }
        ok.templates.putSetting(SETTING_SUBS_IGNORED, (now + key).distinct().joinToString("\n")); _subscriptions.value = subscriptionsUi(ok)
    }

    fun showIgnoredSubscriptions() = withStore { ok -> ok.templates.putSetting(SETTING_SUBS_IGNORED, ""); _subscriptions.value = subscriptionsUi(ok) }

    fun openCalendar() = calendarAction { CalendarRequest(0, null) }
    fun closeCalendar() { calendarRequest = null; _calendar.value = null }
    fun shiftCalendar(delta: Int) = calendarAction { CalendarRequest(it.offset + delta, null) }
    fun selectCalendarDay(epochDay: Long?) = calendarAction { it.copy(selectedEpochDay = if (it.selectedEpochDay == epochDay) null else epochDay) }

    private fun buildCalendar(ok: AppContainer.Opened.Ok, r: CalendarRequest): CalendarUi {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = todayLocal()
        val today = t
        val (year, month) = CalendarMath.monthAt(today, r.offset)
        val first = kotlinx.datetime.LocalDate(year, month, 1)
        val period = DatePeriod(first, first.plus(1, kotlinx.datetime.DateTimeUnit.MONTH))
        val due = ok.recurring.unhandled(period.start, period.endExclusive).filter { it.item.kind == RecurringKind.EXPENSE }
        val m = CalendarMath.month(year, month, zone, ok.reports.spendPoints(period, zone, inr), due.map { BillOnDay(it.date, it.item.amount?.minorUnits) }, today)
        val selected = r.selectedEpochDay?.let { kotlinx.datetime.LocalDate.fromEpochDays(it) }?.takeIf { it in period }
        val lines = selected?.let { ok.reports.drill(DatePeriod(it, it.plus(1, kotlinx.datetime.DateTimeUnit.DAY)), zone, inr, DrillTarget.All, 200) }.orEmpty()
        val dayFmt = DatePattern("EEEE, d MMM")
        return CalendarUi(
            label = DatePattern("MMMM yyyy").format(LocalDate(year, month, 1)), offset = r.offset, blanks = m.leadingBlanks,
            cells = m.days.map { CalendarCellUi(it.date.dayOfMonth, it.date.toEpochDays(), if (it.spentMinor == 0L) "" else Money(it.spentMinor, inr).compact(), it.spentMinor, it.bills, it.level, it.isToday) },
            total = Money(m.totalSpentMinor, inr).formatted(),
            selectedEpochDay = selected?.toEpochDays(),
            dayTitle = selected?.let { dayFmt.format(localDateOfEpochDay(it.toEpochDays())) }.orEmpty(),
            dayTotal = selected?.let { Money(m.days[it.dayOfMonth - 1].spentMinor, inr).formatted() }.orEmpty(),
            dayLines = lines,
            dayBills = due.filter { it.date == selected }.map { b -> b.item.name + (b.item.amount?.let { " ${it.formatted()}" } ?: " (amount each time)") },
        )
    }

    private fun reportAction(next: (ReportRequest) -> ReportRequest) {
        val current = reportRequest ?: ReportRequest(ReportMode.PERIOD, 0)
        reportRequest = next(current)
        _reportBuilds.update { it + 1 }
        viewModelScope.launch(BACKGROUND) {
            try { container.write { ok -> reportRequest?.let { _report.value = buildReport(ok, it) } } } finally { _reportBuilds.update { it - 1 } }
        }
    }

    private fun buildReport(ok: AppContainer.Opened.Ok, r: ReportRequest): ReportUi {
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = todayLocal()
        val today = t
        val budgetPeriod = ok.budgets.period()
        val customStart = kotlinx.datetime.LocalDate.fromEpochDays((r.customStart ?: (today.toEpochDays() - 29L)).toInt())
        val customEnd = kotlinx.datetime.LocalDate.fromEpochDays((r.customEnd ?: (today.toEpochDays() + 1L)).toInt())
        fun periodAt(offset: Int) = when (r.mode) {
            ReportMode.PERIOD -> ReportMath.periodAt(budgetPeriod, today, offset)
            ReportMode.YEAR -> ReportMath.yearAt(today, offset)
            ReportMode.FINANCIAL_YEAR -> BudgetPeriod.Yearly().containing(today.plus(offset, kotlinx.datetime.DateTimeUnit.YEAR))
            ReportMode.CUSTOM -> app.expenses.core.domain.DatePeriod(customStart, customEnd)
        }
        val period = periodAt(r.offset)
        // The same length of time just before (for a custom range); the year or period before otherwise.
        val previous = if (r.mode == ReportMode.CUSTOM) app.expenses.core.domain.DatePeriod(customStart.minus(period.days, kotlinx.datetime.DateTimeUnit.DAY), customStart) else periodAt(r.offset - 1)
        val rangeFmt = DatePattern("d MMM")
        val title = if (r.mode == ReportMode.YEAR) period.start.year.toString()
        else if (r.mode == ReportMode.FINANCIAL_YEAR) "FY ${period.start.year}–${(period.start.year + 1) % 100}"
        else if (r.mode == ReportMode.CUSTOM) DatePattern("d MMM yyyy").let { f -> f.format(period.start) + " – " + f.format(period.endExclusive.minusDays(1)) }
        else rangeFmt.format(period.start) + " – " + rangeFmt.format(period.endExclusive.minusDays(1)) + if (period.start.year != period.endExclusive.minus(1, kotlinx.datetime.DateTimeUnit.DAY).year) " ${period.endExclusive.year}" else ""

        val reports = ok.reports
        val spent = reports.spent(period, zone, inr)
        val income = reports.income(period, zone, inr)
        val net = income - spent
        val colors = ok.categories.list().associate { it.id to it.colorArgb }
        fun row(c: app.expenses.core.domain.CategoryRow): ReportRowUi = ReportRowUi(c.categoryId, c.name, c.total.formatted(), c.share, c.categoryId?.let { colors[it] }, c.total.minorUnits < 0, c.children.map(::row))

        // Trend: the six periods ending at this one, or the twelve months of the year.
        val bars: List<Triple<String, kotlinx.datetime.LocalDate, app.expenses.core.domain.DatePeriod>> = if (r.mode == ReportMode.YEAR) {
            ReportMath.monthsOf(period.start.year).map { Triple(DatePattern("MMM").format(it.start), it.start, it) }
        } else if (r.mode == ReportMode.FINANCIAL_YEAR || r.mode == ReportMode.CUSTOM) {
            ReportMath.monthBars(period).map { Triple(DatePattern("MMM").format(it.start), it.start, it) }
        } else {
            (5 downTo 0).map { k -> periodAt(r.offset - k).let { p -> Triple(DatePattern(if (p.days >= 28) "MMM" else "d MMM").format(p.start), p.start, p) } }
        }
        val totals = reports.trend(bars.map { it.third }, zone, inr)
        val tallest = totals.maxOfOrNull { it.minorUnits }?.takeIf { it > 0 } ?: 1L
        val trend = bars.mapIndexed { i, (label, _, p) ->
            TrendBarUi(label, totals[i].formatted(), (totals[i].minorUnits.coerceAtLeast(0).toFloat() / tallest), p == period, p.start.toEpochDays(), p.endExclusive.toEpochDays())
        }

        val change = ReportMath.percentChange(spent, reports.spent(previous, zone, inr))
        val comparison = change?.let {
            val what = when (r.mode) { ReportMode.YEAR, ReportMode.FINANCIAL_YEAR -> "the year before"; ReportMode.CUSTOM -> "the same length of time before"; else -> "the period before" }
            when { it > 0 -> "$it% more than $what"; it < 0 -> "${-it}% less than $what"; else -> "The same as $what" }
        }

        val drill = r.drillTarget?.let { target ->
            val dp = if (r.drillStart != null && r.drillEnd != null) app.expenses.core.domain.DatePeriod(kotlinx.datetime.LocalDate.fromEpochDays(r.drillStart.toInt()), kotlinx.datetime.LocalDate.fromEpochDays(r.drillEnd.toInt())) else period
            val limit = 500L
            val lines = reports.drill(dp, zone, inr, target, limit)
            val dateFmt = DatePattern("d MMM, HH:mm")
            val what = when (target) {
                DrillTarget.All -> "All spending"
                DrillTarget.Income -> "Income"
                DrillTarget.Uncategorised -> ReportMath.UNCATEGORISED
                is DrillTarget.InCategory -> categoryNames[target.id] ?: target.id
                is DrillTarget.InAccount -> r.drillLabel ?: accountNames[target.id] ?: "Account"
                is DrillTarget.WithTag -> "Tag: ${r.drillLabel ?: target.tag}"
                DrillTarget.Untagged -> "No tag"
                is DrillTarget.AtMerchant -> r.drillLabel ?: target.key.ifEmpty { "No merchant" }
            }
            val range = if (dp == period) title else rangeFmt.format(dp.start) + " – " + rangeFmt.format(dp.endExclusive.minusDays(1))
            val total = Money(lines.sumOf { it.amount.minorUnits }, inr)
            DrillUi(
                what, range, total.formatted(),
                lines.map { DrillLineUi(it.entryId, dateFmt.format(it.occurredAt), it.title, it.categoryName.orEmpty(), it.amount.formatted(), it.amount.minorUnits < 0) },
                truncated = lines.size.toLong() == limit,
            )
        }
        val groupRows = if (r.group == ReportGroup.CATEGORY) emptyList() else reports.groups(r.group, period, zone, inr).map { GroupRowUi(it.key, it.name, it.total.formatted(), it.share, it.total.minorUnits < 0) }
        return ReportUi(
            r.mode, title, r.mode != ReportMode.CUSTOM && r.offset < 0, spent.formatted(), income.formatted(), net.formatted(), net.minorUnits >= 0, comparison,
            reports.breakdown(period, zone, inr).map(::row), trend, drill, r.group, groupRows, period.start.toEpochDays().toLong(), period.endExclusive.toEpochDays().toLong(),
        )
    }

    // ---- export, backup and restore ----

    /** Runs a file action off the main thread, showing "working" and then its outcome. A [BackupException] message is written for the user. */
    private fun dataAction(block: suspend () -> String) {
        _data.update { it.copy(busy = true, status = null, statusIsError = false) }
        viewModelScope.launch(BACKGROUND) {
            try {
                val message = block()
                _data.update { it.copy(busy = false, status = message, statusIsError = false) }
            } catch (e: BackupException) {
                _data.update { it.copy(busy = false, status = e.message, statusIsError = true) }
            } catch (e: Exception) {
                _data.update { it.copy(busy = false, status = "That did not work: ${e.message ?: e::class.simpleName}", statusIsError = true) }
            }
        }
    }

    fun exportXlsx(uri: String) = dataAction { "Exported ${actions.exportXlsx(uri)} entries as an Excel workbook." }
    fun exportFinancialYear(uri: String, yearsBack: Int) = dataAction { "Saved the ${actions.exportFinancialYear(uri, yearsBack)} summary as an Excel workbook." }
    fun exportCsv(uri: String) = dataAction { "Exported ${actions.exportCsv(uri)} entries as a spreadsheet file." }

    /** How many photos and files a backup would carry and whether the switch is on: read when the Data screen opens and after anything that changes them. */
    fun loadBackupFiles() {
        viewModelScope.launch(BACKGROUND) {
            val files = container.write { ok -> ok.backup.attachmentTotals().let { BackupFilesUi(it.count, it.bytes, ok.templates.setting(BackupKeys.INCLUDE_FILES) != "0") } } ?: return@launch
            _data.update { it.copy(files = files) }
        }
    }

    /** The switch "Include photos and files": kept as a setting, so automatic copies follow it too. */
    fun setIncludeBackupFiles(on: Boolean) {
        _data.update { it.copy(files = it.files.copy(include = on)) }
        viewModelScope.launch(BACKGROUND) {
            val stored = container.write { ok -> ok.templates.putSetting(BackupKeys.INCLUDE_FILES, if (on) "1" else "0"); ok.templates.setting(BackupKeys.INCLUDE_FILES) != "0" } ?: return@launch
            _data.update { it.copy(files = it.files.copy(include = stored)) }
        }
    }

    private fun savedFilesText(f: DataTransfer.FilesSaved) = (if (f.included > 0) ", ${fileCountText(f.included)} (${AttachmentRules.sizeText(f.includedBytes)})" else "")
    private fun savedFilesNote(f: DataTransfer.FilesSaved) = f.notes().takeIf { it.isNotEmpty() }?.let { " Note: ${it.joinToString("; ")}." }.orEmpty()

    fun createBackup(uri: String, passphrase: String?) = dataAction {
        val saved = actions.backup(uri, passphrase)
        container.write { ok -> refresh(ok) } // the first-week checklist on Home ticks "backup" from this
        "Backup saved and checked: ${saved.counts.entries} entries, ${saved.counts.categories} categories${savedFilesText(saved.files)}" +
            (if (saved.encrypted) ", protected by your passphrase." else ". It is not encrypted; keep it somewhere private.") + savedFilesNote(saved.files)
    }

    /**
     * Makes a backup and hands it to [deliver] (on the main thread) to be sent through the share sheet: Google Drive, email, a chat. Checked first like a
     * saved one, but not counted as a backup made, because whether it was sent is out of our hands.
     */
    fun shareBackup(name: String, passphrase: String?, deliver: (name: String, bytes: ByteArray) -> Unit) = dataAction {
        val (bytes, saved) = actions.backupForSharing(passphrase)
        withContext(Dispatchers.Main) { deliver(name, bytes) }
        "Backup ready: ${saved.counts.entries} entries${savedFilesText(saved.files)}" + (if (saved.encrypted) ", protected by your passphrase. " else ", not encrypted. ") + "Choose where to send it; this app cannot tell whether it arrived, so check there." + savedFilesNote(saved.files)
    }

    /** Opens a backup file. Nothing changes until [confirmRestore]. */
    fun openBackup(uri: String) = dataAction { showOpened(actions.open(uri, null)) }

    fun unlockBackup(passphrase: String) = dataAction {
        val uri = openedUri ?: throw BackupException("Choose the backup file again.")
        showOpened(actions.open(uri, passphrase))
    }

    private fun showOpened(o: DataTransfer.Opened): String {
        when (o) {
            is DataTransfer.Opened.NeedsPassphrase -> {
                openedUri = o.uri
                _data.update { it.copy(needsPassphrase = true, pending = null) }
                return "This backup is protected by a passphrase."
            }
            is DataTransfer.Opened.Ready -> {
                openedUri = null
                openedReady = o
                val made = DatePattern("d MMM yyyy, HH:mm").format(o.header.createdAt)
                val r = o.report
                _data.update {
                    it.copy(
                        needsPassphrase = false,
                        pending = PendingRestoreUi(
                            made, o.data.entries.size, r.added.entries, r.alreadyHere.entries, r.added.accounts + r.added.categories + r.added.templates + r.added.budgets + r.added.recurring,
                            o.data.attachments.size, o.data.attachments.sumOf { a -> a.sizeBytes }, r.added.attachments,
                        ),
                    )
                }
                // a version 2 file has had only its header and entries checked here; the photos and files are read, and checked one by one, when the restore is confirmed
                return if (o.data.attachments.isEmpty()) "Backup checked: it is complete and not damaged." else "Backup checked: the entries are complete. Photos and files are checked when you restore."
            }
        }
    }

    /** A second tap on Restore while one is running does nothing: two restores would store the same files twice. (The button is off while busy; this is the same rule where it is decided.) */
    private val restoreFlight = SingleFlight()

    fun confirmRestore() {
        val ready = openedReady ?: return
        if (_data.value.busy || !restoreFlight.tryEnter()) return
        dataAction {
            try {
                val restored = actions.restore(ready)
                val report = restored.report
                openedReady = null
                _data.update { it.copy(pending = null) }
                container.write { ok -> refresh(ok) }
                loadBackupFiles()
                "Restored: ${report.added.entries} entries added, ${report.alreadyHere.entries} were already here and were left as they are." + (restored.files?.let { restoredFilesText(it, report.added.attachments, restored.skipped) } ?: "")
            } finally {
                restoreFlight.leave()
            }
        }
    }

    /** True while an automatic copy is being written (by this screen's button, the periodic worker or a foreground check): the button waits. */
    val autoExportRunning: StateFlow<Boolean> get() = AutoExportRunner.running

    /** The sentence about photos and files after a restore: how many were put back, and plainly how many were not and why. */
    private fun restoredFilesText(f: app.expenses.core.storage.FilesRestored, putBack: Int, skipped: app.expenses.core.storage.AttachmentPlan): String {
        val sb = StringBuilder(" ").append(if (putBack == 1) "1 photo or file was put back" else "$putBack photos and files were put back")
        val problems = listOfNotNull(
            f.damaged.takeIf { it > 0 }?.let { "$it damaged in the backup (skipped)" },
            f.missing.takeIf { it > 0 }?.let { "$it missing from the backup file (skipped)" },
            f.notStored.takeIf { it > 0 }?.let { "$it could not be stored on this phone" },
        )
        // the ones that were never tried, and why
        val left = listOfNotNull(
            skipped.alreadyHere.takeIf { it > 0 }?.let { "$it already here" },
            skipped.removedHere.takeIf { it > 0 }?.let { "$it removed here earlier" },
            skipped.noEntry.takeIf { it > 0 }?.let { "$it for entries that are not here or are deleted" },
            skipped.noRoom.takeIf { it > 0 }?.let { "$it with no room (an entry holds at most ${AttachmentRules.MAX_PER_ENTRY})" },
            skipped.invalid.takeIf { it > 0 }?.let { "$it not valid (unsafe name, size or checksum)" },
            skipped.repeated.takeIf { it > 0 }?.let { "$it repeating the name of another" },
        )
        if (problems.isNotEmpty()) sb.append("; ").append(problems.joinToString(", "))
        if (left.isNotEmpty()) sb.append("; not put back: ").append(left.joinToString(", "))
        return sb.append('.').toString()
    }

    fun cancelRestore() {
        openedReady = null; openedUri = null
        _data.update { it.copy(pending = null, needsPassphrase = false) }
    }

    fun dismissDataStatus() = _data.update { it.copy(status = null) }

    // ---- notifications ----

    /**
     * Changes the notification choices relative to what is *stored*, inside the write. Basing a change on the value on screen would lose
     * taps: two quick taps on "+" both read the same old time and write the same new one.
     */
    fun updateNotificationPrefs(change: (NotificationPrefs) -> NotificationPrefs) = withStore { ok ->
        ok.notifications.setPrefs(change(ok.notifications.prefs()))
        refresh(ok)
    }

    /** Shows one notice immediately, so the user can see that permission and channels work. Not recorded; it is not an event. */
    fun sendTestNotification() {
        val shown = platform.showNotices(listOf(Notice("test:${nowMillis()}", NoticeKind.BILL_DUE, "Test notification", "If you can read this, reminders and alerts will reach you.")))
        if (shown.isEmpty()) _state.update { (it as? UiState.Ready)?.copy(message = "Notifications are blocked for this app") ?: it }
    }

    // ---- budgets ----

    /** Sets a limit for a category, or for everything when [categoryId] is null. */
    fun setBudget(categoryId: String?, amount: Money) = withStore { ok ->
        ok.budgets.setLimit(categoryId, amount)
        refresh(ok)
    }

    /** Lengthens or shortens a custom period by [delta] days, relative to what is stored (quick taps all count). */
    fun shiftCustomDays(delta: Int) = withStore { ok ->
        val current = ok.budgets.period() as? BudgetPeriod.EveryDays ?: return@withStore
        ok.budgets.setPeriod(BudgetPeriod.EveryDays((current.days + delta).coerceIn(1, BudgetPeriod.EveryDays.MAX_DAYS), current.anchor))
        refresh(ok)
    }

    fun clearBudget(categoryId: String?) = withStore { ok ->
        ok.budgets.clearLimit(categoryId)
        refresh(ok)
    }

    fun setBudgetRollover(on: Boolean) = withStore { ok -> ok.budgets.setRollover(on); refresh(ok) }

    fun setBudgetPeriod(period: BudgetPeriod) = withStore { ok ->
        ok.budgets.setPeriod(period)
        refresh(ok)
    }

    /** Moves a monthly period's start day by [delta], relative to the stored day (see [updateNotificationPrefs] for why), staying within 1 to 28. */
    fun shiftBudgetStartDay(delta: Int) = withStore { ok ->
        val current = ok.budgets.period() as? BudgetPeriod.Monthly ?: return@withStore
        ok.budgets.setPeriod(BudgetPeriod.Monthly((current.startDay + delta).coerceIn(1, BudgetPeriod.Monthly.MAX_START_DAY)))
        refresh(ok)
    }

    fun dismissMessage() {
        _state.update { (it as? UiState.Ready)?.copy(message = null) ?: it }
    }

    fun dismissUndo() {
        _state.update { (it as? UiState.Ready)?.copy(undo = null) ?: it }
    }

    /** Debug and benchmark builds only: fills the database with realistic rows to measure speed at scale. */
    /** [full] also adds the rest of the §6.1 dataset: 500 more categories, 500 tags on a third of the entries, and 1,000 attachment records (no files). */
    fun seed(rows: Int, full: Boolean = false) {
        check(platform.seedingEnabled) { "Seeding is not available in this build" }
        withStore { ok ->
            val q = ok.db.schemaQueries
            val step = 3_650L * 86_400_000L / rows
            val start = nowMillis() - 3_650L * 86_400_000L
            val extraCategories = if (full) (0 until 500).map { "Category $it" } else emptyList()
            ok.db.bulkLoad(ok.driver) {
                extraCategories.forEachIndexed { n, name -> q.insertCategoryRaw(name, name, null, null, (100 + n).toLong(), 0, 0, null, "EXPENSE") }
                val categories = DEFAULT_CATEGORIES + extraCategories
                for (i in 0 until rows) {
                    val at = start + i * step
                    val amount = 100L + (i * 37L) % 90_000L
                    q.insertEntry("seed$i", "EXPENSE", at, "cash", null, amount, "INR", null, "Merchant ${i % 400}", "seed note $i", "seed", null)
                    q.insertSpendLine("seed$i", 0, at, "INR", categories[i % categories.size], amount, amount)
                    if (full && i % 3 == 0) q.insertTag("seed$i", "tag${i % 500}")
                    if (full && i % (rows / 1000).coerceAtLeast(1) == 0) q.insertAttachment("seedatt$i", "seed$i", "receipt$i.jpg", "image/jpeg", 250_000, "0".repeat(64), at)
                }
            }
            refresh(ok)
        }
    }

    // ---- internals ----

    /** Runs [block] off the main thread through the container's process-wide write lock, once the database is open. */
    private fun withStore(block: (AppContainer.Opened.Ok) -> Unit) {
        viewModelScope.launch(BACKGROUND) {
            try {
                container.write(block)
            } catch (e: IllegalArgumentException) {
                _state.update { (it as? UiState.Ready)?.copy(message = e.message) ?: it }
            }
        }
    }

    /** How long the last save took until the new entry and the period total were on screen (the write plus the quick update), in milliseconds. Read by the timing gate. */
    @Volatile var lastSaveMillis: Double = 0.0

    /** How long the rebuild that follows a save took after the new entry was already on screen, in milliseconds. Read by the timing gate. */
    @Volatile var lastSettleMillis: Double = 0.0

    // Formatted list rows by the stored row they came from: an unchanged entry is not formatted again after every save, and an edited one is a
    // different key, so it is. Cleared by every full rebuild.
    private val rowCache = HashMap<Entry, RowUi>()
    private fun rowsOf(entries: List<Entry>): List<RowUi> {
        if (rowCache.size > 2_000) rowCache.clear()
        return entries.map { e -> rowCache.getOrPut(e) { toRow(e) } }
    }

    /**
     * After a save: put on screen what the person is waiting to see (the new entry in the list and this period's total) at once, then rebuild
     * everything else right behind it. On a 100,000-entry ledger the full rebuild is about 10 ms, which would otherwise sit inside the 50 ms
     * save budget. Everything else is still correct within a few milliseconds; nothing is skipped.
     */
    private fun refreshQuickly(ok: AppContainer.Opened.Ok, undo: UndoUi?) {
        val current = _state.value as? UiState.Ready
        if (current == null) { refresh(ok, undo); return }
        val q = ok.db.schemaQueries
        val want = maxOf(loaded.size.toLong(), PAGE)
        loaded = recentRows(q, want).toMutableList()
        endReached = loaded.size < want
        loadedCategory = firstCategories(ok, loaded)
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val t = todayLocal()
        val period = ok.budgets.period().containing(t)
        val spent = ok.store.spendByCategory(inr, from = period.startMillis(zone), until = period.endMillis(zone)).values.fold(Money.zero(inr)) { a, m -> a + m }
        _state.value = current.copy(rows = rowsOf(loaded), periodSpend = spent.formatted(), periodSpendMinor = spent.minorUnits, endReached = endReached, undo = undo)
        val quickDone = TimeSource.Monotonic.markNow()
        viewModelScope.launch(BACKGROUND) {
            container.write { ok2 -> refresh(ok2, undo = (_state.value as? UiState.Ready)?.undo) }
            lastSettleMillis = quickDone.elapsedNow().inWholeNanoseconds / 1e6
        }
    }

    private fun refresh(ok: AppContainer.Opened.Ok, undo: UndoUi? = null) {
        rowCache.clear() // names (accounts, categories) may have changed since the rows were formatted
        _people.value = ok.people.list().map { PersonUi(it.id, it.name) }
        if (loansWatching) loadLoans(ok)
        if (billsWatching) _bills.value = billsUi(ok)
        val q = ok.db.schemaQueries
        val accounts = q.selectAccounts().executeAsList()
        accountNames = accounts.associate { it.id to it.name }
        currencyDigits = q.selectCurrencies().executeAsList().associate { it.code to it.digits.toInt() }
        // Reload as many rows as are already shown, so a change does not throw the user back to the top of a long list.
        val want = maxOf(loaded.size.toLong(), PAGE)
        loaded = recentRows(q, want).toMutableList()
        endReached = loaded.size < want
        val zone = systemZone()
        val today = todayLocal(zone)
        val status = ok.budgets.status(today, kotlinx.datetime.TimeZone.currentSystemDefault(), inr)
        val spent = ok.store.spendByCategory(inr, from = status.period.startMillis(kotlinx.datetime.TimeZone.currentSystemDefault()), until = status.period.endMillis(kotlinx.datetime.TimeZone.currentSystemDefault()))
            .values.fold(Money.zero(inr)) { a, m -> a + m }
        val cats = ok.categories.list()
        categoryNames = cats.associate { it.id to it.name }
        reportRequest?.let { _report.value = buildReport(ok, it) } // keep an open report in step with edits made from its entry list
        calendarRequest?.let { _calendar.value = buildCalendar(ok, it) }
        searchInput?.let { i -> // and an open search, so an entry edited from the results is shown as it now is
            val min = i.minRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) }
            val max = i.maxRupees.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it, inr) }
            if (_search.value.hasConditions && _search.value.problem == null) _search.value = runSearch(ok, i, min, max)
        }
        loadedCategory = firstCategories(ok, loaded)
        val visible = cats.filter { !it.hidden }
        val visibleExpense = visible.filter { it.kind.forExpense }
        val visibleIncome = visible.filter { it.kind.forIncome }
        val lastCategory = ok.templates.setting(LAST_CATEGORY)?.takeIf { id -> visibleExpense.any { it.id == id } } ?: visibleExpense.firstOrNull()?.id ?: ""
        val lastIncomeCategory = ok.templates.setting(LAST_INCOME_CATEGORY)?.takeIf { id -> visibleIncome.any { it.id == id } } ?: visibleIncome.firstOrNull()?.id ?: ""
        val active = accounts.filter { it.archived == 0L }
        val lastAccount = ok.templates.setting(LAST_ACCOUNT)?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id ?: "cash"
        val layout = HomeLayout.decode(ok.templates.setting(SETTING_HOME_LAYOUT))
        balancesVisible = HomeSection.BALANCES in layout.visible()
        scheduleSlowFigures()
        _state.value = UiState.Ready(
            rows = rowsOf(loaded),
            periodSpend = spent.formatted(),
            periodSpendMinor = spent.minorUnits,
            budget = budgetUi(ok, status, cats),
            due = dueUi(ok, today),
            notifications = ok.notifications.prefs(),
            // 'ever' counts deleted entries too: someone who added something and deleted it is not new to the app.
            showWelcome = !ok.db.schemaQueries.entryCountEver().executeAsOne() && ok.templates.setting(SETTING_WELCOME_DISMISSED) != "1",
            showBackupNudge = nudgeCache,
            hiddenOlder = ok.recurring.hiddenOlder(today),
            recurring = recurringUi(ok, today),
            accounts = active.map { AccountUi(it.id, it.name, it.type) },
            allAccounts = accounts.map { AccountUi(it.id, it.name, it.type) },
            accountRows = accountRowsCache,
            recentSearches = ok.templates.setting(SETTING_RECENT_SEARCHES).orEmpty().split('\n').filter { it.isNotBlank() },
            savedSearches = ok.savedSearches.list().let { kept -> val known = ok.db.schemaQueries.selectAccounts().executeAsList().map { x -> x.id }.toSet(); kept.map { SavedSearchUi(it.id, it.name, SearchInput(it.text, it.minRupees, it.maxRupees, it.categoryId?.let { c -> ok.db.resolveCategory(c) }, SearchKind.valueOf(it.kind), SearchRange.valueOf(it.range), it.tag, it.fromDay, it.toDay, it.accountId?.takeIf { a -> a in known })) } },
            appearance = Appearances.decode(ok.templates.setting(SETTING_APPEARANCE)),
            baseCurrency = BaseCurrency.current,
            inboxPending = ok.inbox.pendingCount().toInt(),
            inboxPeek = inboxPeek(ok),
            billPeek = billPeek(ok),
            firstWeek = firstWeekUi(ok, accounts),
            spark = sparkCache,
            reduceMotion = ok.templates.setting(SETTING_REDUCE_MOTION) == "1",
            motionSpeed = if (ok.templates.setting(SETTING_REDUCE_MOTION) == "1") "off" else ok.templates.setting(SETTING_MOTION_SPEED) ?: "relaxed",
            smsCapture = ok.templates.setting(CaptureKeys.SETTING_SMS_ON) == "1",
            hideAmounts = ok.templates.setting(SETTING_HIDE_AMOUNTS) == "1",
            homeLayout = HomeLayout.decode(ok.templates.setting(SETTING_HOME_LAYOUT)),
            autoExport = autoExportUi(ok),
            tags = ok.store.tagsInUse(TAG_SUGGESTIONS).map { it.first },
            // Usage (which decides whether Delete is offered) is worked out in the background only while the categories screen is open.
            categories = cats.map { CategoryUi(it.id, it.name, it.parentId, it.colorArgb, it.hidden, categoryUsageCache[it.id] ?: 0L, it.kind) },
            templates = ok.templates.list().map { TemplateUi(it.id, it.name, it.amount?.formatted(), it.category.value, it.account.value, it.merchant ?: "") },
            lastCategory = lastCategory,
            lastIncomeCategory = lastIncomeCategory,
            lastAccountId = lastAccount,
            endReached = endReached,
            homeAccountId = homeAccount,
            homePeriod = homePeriod,
            safeToSpend = safeCache,
            waiting = waitingSummary(ok, inr),
            sinkingFunds = ok.templates.setting(SETTING_SINKING_FUNDS) == "1",
            undo = undo,
        )
    }

    private fun budgetUi(ok: AppContainer.Opened.Ok, status: BudgetStatus, cats: List<app.expenses.core.domain.Category>): BudgetUi {
        val byScope = status.progress.associateBy { it.categoryId }
        val names = cats.associate { it.id to it.name }
        fun line(categoryId: String?, name: String): BudgetLineUi {
            val p = byScope[categoryId]
            return BudgetLineUi(
                categoryId, name,
                limit = p?.limit?.formatted(), limitPlain = p?.limit?.toPlainString().orEmpty(),
                spent = (p?.spent ?: Money.zero(inr)).formatted(),
                note = p?.let { if (it.remaining.minorUnits >= 0) "${it.remaining.formatted()} left" else "${Money(-it.remaining.minorUnits, inr).formatted()} over" }.orEmpty(),
                percent = p?.usedPercent ?: 0,
                state = p?.state ?: BudgetState.OK,
            )
        }
        // Only categories that serve expenses can carry a limit (one that already has a limit stays, so it can still be removed).
        val lines = cats.filter { (it.kind.forExpense && !it.hidden) || byScope.containsKey(it.id) }
            .map { c -> line(c.id, c.parentId?.let { pid -> names[pid]?.let { "$it › ${c.name}" } } ?: c.name) }
        val period = ok.budgets.period()
        val fmt = DatePattern("d MMM")
        return BudgetUi(
            period = period,
            range = fmt.format(status.period.start) + " – " + fmt.format(status.period.endExclusive.minusDays(1)),
            periodName = when (period) {
                is BudgetPeriod.Weekly -> "this week"
                is BudgetPeriod.Monthly -> if (period.startDay == 1) "this month" else "this cycle"
                is BudgetPeriod.BiWeekly -> "this fortnight"
                BudgetPeriod.SemiMonthly -> "this half-month"
                is BudgetPeriod.EveryDays -> "this period"
                is BudgetPeriod.Yearly -> if (period.startMonth == 4) "this financial year" else if (period.startMonth == 1) "this year" else "this year of the budget"
            },
            overall = line(null, "Everything"),
            categories = lines,
            perDay = status.safeToSpend?.takeIf { byScope[null]?.state != BudgetState.OVER }?.perDay?.formatted(),
            upcoming = status.safeToSpend?.upcoming?.takeIf { it.minorUnits > 0 }?.formatted(),
            billsUseItAll = status.safeToSpend?.let { it.available.minorUnits <= 0 && it.remaining.minorUnits > 0 } ?: false,
            daysLeft = status.safeToSpend?.daysLeft ?: 0,
            categoriesNeedingAttention = status.progress.count { it.categoryId != null && it.state != BudgetState.OK },
            rollover = ok.budgets.rollover(),
            carriedText = status.carried[null]?.formatted().orEmpty(),
        )
    }

    /**
     * The first-week checklist for Home. Cheap on purpose (this runs on every refresh): a few setting reads and existence checks. The clock starts when the first
     * entry is noticed on a ledger that was empty when this version first looked at it; a ledger that already had entries then belongs to someone who is not new, so
     * the card is never shown for it.
     */
    private fun firstWeekUi(ok: AppContainer.Opened.Ok, accounts: List<app.expenses.core.storage.db.Account>): List<FirstWeekItem> {
        val t = ok.templates
        val q = ok.db.schemaQueries
        if (t.setting(SETTING_FIRST_WEEK_DISMISSED) == "1") return emptyList()
        val hasEntry = q.entryCountEver().executeAsOne()
        val marker = t.setting(SETTING_FIRST_WEEK_STARTED)
        val now = nowMillis()
        val started: Long = when {
            marker == null -> { t.putSetting(SETTING_FIRST_WEEK_STARTED, if (hasEntry) "0" else "pending"); if (hasEntry) 0L else return emptyList() }
            marker == "pending" -> if (hasEntry) now.also { t.putSetting(SETTING_FIRST_WEEK_STARTED, it.toString()) } else return emptyList()
            else -> marker.toLongOrNull() ?: return emptyList()
        }
        if (now - started >= FirstWeek.DAYS * 86_400_000L) return emptyList() // the common case for an old ledger: nothing more to read
        val live = accounts.filter { it.archived == 0L }.map { it.id }.toSet()
        val capture = when {
            platform.supportsShortcutsCapture -> FirstWeekCapture.SHORTCUT
            platform.supportsSmsCapture || platform.supportsNotificationCapture || platform.supportsCaptureSources -> FirstWeekCapture.SWITCH
            else -> FirstWeekCapture.NONE
        }
        val captureOn = if (capture == FirstWeekCapture.SHORTCUT) q.captureEventCounts(0L).executeAsList().isNotEmpty()
        else listOf(CaptureKeys.SETTING_SMS_ON, CaptureKeys.SETTING_NOTIF_ON, CaptureKeys.SETTING_EMAIL_ON).any { t.setting(it) == "1" }
        val folderChosen = t.setting(AutoExportKeys.FOLDER) != null && t.setting(AutoExportKeys.FREQ) != AutoExportFrequency.OFF.name
        return FirstWeek.items(FirstWeekInputs(
            startedAtMillis = started, nowMillis = now, dismissed = false,
            accountWithDigits = ok.looks.all().any { it.accountId in live && !it.last4.isNullOrBlank() },
            budgetSet = ok.budgets.limits().isNotEmpty(),
            capture = capture, captureOn = captureOn,
            backupDone = t.setting(DataTransfer.SETTING_BACKUP_AT)?.toLongOrNull() != null || folderChosen,
            hasEntry = hasEntry,
        ))
    }

    /** The close button on the first-week card: it is not shown again. */
    fun dismissFirstWeek() = withStore { ok ->
        ok.templates.putSetting(SETTING_FIRST_WEEK_DISMISSED, "1")
        refresh(ok)
    }

    private fun dueUi(ok: AppContainer.Opened.Ok, today: LocalDate): List<DueUi> {
        val now = today
        val fmt = DatePattern("d MMM")
        return ok.recurring.due(now).map { d ->
            val late = (now.toEpochDays() - d.date.toEpochDays()).toInt()
            DueUi(
                d.item.id, d.date.toEpochDays(), d.item.name, d.item.amount?.formatted(),
                when { late <= 0 -> "Due today"; late == 1 -> "Due yesterday"; else -> "Due ${fmt.format(localDateOfEpochDay(d.date.toEpochDays()))} · $late days ago" },
                overdue = late > 0,
            )
        }
    }

    private fun recurringUi(ok: AppContainer.Opened.Ok, today: LocalDate): List<RecurringUi> {
        val now = today
        val fmt = DatePattern("d MMM yyyy")
        return ok.recurring.list().map { i ->
            val s = i.schedule
            val cadence = if (s.every == 1) "every ${s.unit.name.lowercase()}" else "every ${s.every} ${s.unit.name.lowercase()}s"
            val next = s.nextOnOrAfter(now)?.let { "next ${fmt.format(localDateOfEpochDay(it.date.toEpochDays()))}" } ?: "ended"
            RecurringUi(
                i.id, i.name, i.amount?.toPlainString().orEmpty(), i.category?.value.orEmpty(), i.account.value, i.merchant.orEmpty(), s, i.auto, i.kind, i.toAccount?.value.orEmpty(),
                listOfNotNull(i.amount?.formatted() ?: "amount each time", cadence, next, if (i.auto) "posts by itself" else null).joinToString(" · "),
                paymentsLeft = if (i.kind == RecurringKind.INCOME) "" else PaymentsLeftMath.of(s, now.minusDays(RecurringStore.DUE_WINDOW_DAYS.toLong()), if (s.end == ScheduleEnd.Never) emptySet() else ok.recurring.handledDays(i.id, now.minusDays(RecurringStore.DUE_WINDOW_DAYS.toLong())))
                    ?.let { left -> PaymentsLeftMath.text(left, i.amount?.minorUnits, { m -> Money(m, inr).formatted() }, { d -> fmt.format(localDateOfEpochDay(d.toEpochDays())) }) }.orEmpty(),
                setAside = if (i.kind != RecurringKind.EXPENSE) "" else SinkingFunds.forItem(i.amount?.minorUnits, s, now)?.let { SinkingFunds.text(it.perMonthMinor) { m -> Money(m, inr).formatted() } }.orEmpty(),
            )
        }
    }

    /** entry id -> id of its main category, for the given entries. */
    private fun firstCategories(ok: AppContainer.Opened.Ok, entries: List<Entry>): Map<String, String> =
        if (entries.isEmpty()) emptyMap()
        else ok.db.schemaQueries.firstSplits(entries.map { it.id }).executeAsList().associate { it.entry_id to it.category_id }

    private val dateFormat = DatePattern("d MMM yyyy")

    private fun toRow(e: Entry, firstCategory: Map<String, String> = loadedCategory): RowUi = homeAccount.let { shown ->
        val row = toRowPlain(e, firstCategory)
        // narrowed to one account, a transfer says which way it went for that account: into it is money in, out of it is money out
        if (shown != null && e.kind == "TRANSFER") row.copy(kind = if (e.counter_account_id == shown) "TRANSFER_IN" else "TRANSFER_OUT") else row
    }

    private fun toRowPlain(e: Entry, firstCategory: Map<String, String>): RowUi = RowUi(
        seq = e.seq,
        id = e.id,
        title = e.merchant ?: e.note ?: e.kind.lowercase().replaceFirstChar { it.uppercase() },
        subtitle = dateFormat.format(e.occurred_at) +
            (e.account_id?.let { accountNames[it] }?.let { " · $it" } ?: "") +
            (if (e.kind == "SETTLEMENT") " · " + (if (e.direction == "PAID_TO_PERSON") "Gave" else "Got") else ((firstCategory[e.id] ?: e.category_id)?.let { categoryNames[it] }?.let { " · $it" } ?: "")) +
            (e.original_amount_minor?.let { minor -> e.original_currency?.let { code -> " · " + Money(minor, Currency(code, currencyDigits[code] ?: CurrencyChoices.byCode(code).minorUnitDigits)).formatted() } }.orEmpty()),
        amount = Money(e.amount_minor, Currency(e.currency, currencyDigits[e.currency] ?: 2)).formatted(),
        inflow = e.kind == "INCOME" || e.kind == "REFUND" || (e.kind == "SETTLEMENT" && e.direction == "RECEIVED_FROM_PERSON"),
        day = dateOfMillis(e.occurred_at).epochDay(),
        category = ((firstCategory[e.id] ?: e.category_id)?.let { categoryNames[it] }).orEmpty(),
        kind = e.kind,
        minor = e.amount_minor,
    )

    // Last on purpose. This starts work on another thread that reads the properties above and below it; declared earlier in the class it
    // could run before they were initialised (a date formatter below it was still null on about 3% of starts, and the load died silently,
    // leaving the opening screen up forever).
    init {
        viewModelScope.launch(BACKGROUND) {
            when (val o = container.open()) {
                // Through the write lock like every other refresh: a load that read the data before an automatic post but published after the
                // post's own refresh would overwrite the screen with stale totals.
                is AppContainer.Opened.Ok -> {
                    container.write { ok -> MessageIngest.reclassifyWaiting(ok); refresh(ok); offerGuide(ok); loadRates(ok) }; platform.trace("open: first page loaded")
                    viewModelScope.launch(BACKGROUND) { platform.captureChanges.collect { container.write { ok -> _inbox.value = inboxRows(ok); refresh(ok) } } }
                    // Once at start, before anything can be adding a file: copies that were interrupted before their record was written.
                    container.write { ok -> attachmentFiles.sweep(ok.attachments.allIds()) }
                }
                is AppContainer.Opened.KeyLost -> _state.value = UiState.KeyLost(o.message)
            }
        }
    }
}
