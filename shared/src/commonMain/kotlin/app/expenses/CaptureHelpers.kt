package app.expenses

import app.expenses.core.domain.BankExpectation
import app.expenses.core.domain.BatteryState
import app.expenses.core.domain.CaptureCheckup
import app.expenses.core.domain.CaptureCount
import app.expenses.core.domain.CaptureHealth
import app.expenses.core.domain.CaptureWay
import app.expenses.core.domain.CheckRow
import app.expenses.core.domain.CheckupHeadline
import app.expenses.core.domain.CheckupInputs
import app.expenses.core.domain.Currency
import app.expenses.core.domain.IgnoredCounts
import app.expenses.core.domain.Money
import app.expenses.core.domain.PhoneMaker
import app.expenses.core.domain.SkippedName
import app.expenses.core.domain.SkippedSenders
import app.expenses.core.domain.formatted
import app.expenses.core.parsers.BankChoices
import app.expenses.core.parsers.CaptureCategory
import app.expenses.core.parsers.IgnoreReason
import app.expenses.core.parsers.MessageCheck
import app.expenses.core.parsers.PayeeKind
import app.expenses.core.parsers.PayeeKinds
import app.expenses.core.parsers.SenderRules
import app.expenses.core.parsers.TrustedSenders
import app.expenses.core.storage.skippedCounters
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * What the phone itself says that the checkup needs and the app cannot get from its own data: read cheaply (a permission check, a power-manager call), again each time the app comes back from the
 * phone's settings. Defaults are for a host that has none of them (the iPhone).
 */
data class PhoneStatus(
    /** Whether the reminders permission is given; null where the phone has no such permission (Android 12 and earlier, iPhone). */
    val postNotifications: Boolean? = null,
    val battery: BatteryState = BatteryState.UNKNOWN,
    /** Whether the notification listener is bound right now. */
    val listenerConnected: Boolean = false,
    val maker: PhoneMaker = PhoneMaker.OTHER,
)

/**
 * Lets a test stand in for what the phone says about the text permission, notification access and the phone's own status (reminders, battery, listener, maker), so every state of the checkup can be
 * shown on any emulator whatever its permissions are, and for the system file picker of "Bring in earlier spending". Null (the default) means the real answer. Public because the tests live in another module; the app never sets it.
 */
object CheckupTestHook {
    var smsPermission: Boolean? by androidx.compose.runtime.mutableStateOf<Boolean?>(null)
    var notifAccess: Boolean? by androidx.compose.runtime.mutableStateOf<Boolean?>(null)
    var phone: PhoneStatus? by androidx.compose.runtime.mutableStateOf<PhoneStatus?>(null)

    /** A statement the "Choose a statement" button of "Bring in earlier spending" uses instead of opening the system file picker, which a test cannot drive. */
    var statement: String? by androidx.compose.runtime.mutableStateOf<String?>(null)
}

/** The checkup as the screen draws it: the lines and what the headline says. */
data class CheckupUi(val rows: List<CheckRow>, val headline: CheckupHeadline)

/**
 * How the app would read one pasted text, as plain lines. Built from [MessageCheck] by [CaptureCheckupModel.check]; nothing in it is stored. Digits of an account are shown masked; no reference or phone
 * number of the text is ever in it.
 */
data class MessageCheckUi(
    /** "add", "bill" or "ignore": what the app would do. */
    val verdict: String,
    val headline: String,
    /** Label and value, in the order shown: sender, bank, direction, amount, payee, account, category. */
    val lines: List<Pair<String, String>>,
    val explain: String,
    /** A sender name that is on no list but would be read if trusted: the offer "Treat NAME as my bank", and what it would then be read as. */
    val trustRoot: String? = null,
    val ifAdded: List<Pair<String, String>> = emptyList(),
)

/** Where this model's background work runs; a closed database (the app is being torn down) is not an error. */
private val IO = ioDispatcher + CoroutineExceptionHandler { _, e -> if (!isDatabaseClosed(e)) rethrowUncaught(e) }

/**
 * The state behind "Why isn't capture working?" and "Check a message". [load] reads what the checkup needs (a few settings, the capture log's counts, the accounts) and works the rows out with the pure
 * [CaptureCheckup]; it runs when the screen opens and again whenever the phone's state changes (the person returns from Settings). [check] reads one pasted text with [MessageCheck] and keeps the
 * answer in memory only: it never writes, never counts, never sends.
 */
class CaptureCheckupModel(private val container: AppContainer, private val platform: Platform, private val scope: CoroutineScope) {
    private val _checkup = MutableStateFlow<CheckupUi?>(null)
    val checkup: StateFlow<CheckupUi?> get() = _checkup

    private val _result = MutableStateFlow<MessageCheckUi?>(null)

    /** The answer to the last "Check a message"; null before one and after [clearResult]. Lives in memory only. */
    val result: StateFlow<MessageCheckUi?> get() = _result

    private var last: Triple<PhoneStatus, Boolean, Boolean>? = null

    fun way(): CaptureWay = when {
        platform.supportsShortcutsCapture -> CaptureWay.SHORTCUTS
        platform.supportsSmsCapture -> CaptureWay.TEXTS
        platform.supportsNotificationCapture || platform.supportsCaptureSources -> CaptureWay.APPS_ONLY
        else -> CaptureWay.NONE
    }

    /** Reads everything and publishes the rows. [smsPermission] and [notifAccess] are what the host says now. */
    fun load(phone: PhoneStatus, smsPermission: Boolean, notifAccess: Boolean) {
        last = Triple(phone, smsPermission, notifAccess)
        scope.launch(IO) {
            val ui = container.write { ok ->
                val t = ok.templates
                val now = nowMillis()
                val q = ok.db.schemaQueries
                val thirty = q.captureEventCounts(now - 30L * DAY).executeAsList().map { CaptureCount(it.sender_root, it.outcome, it.n, it.last_at ?: 0L) }
                val seven = q.captureEventCounts(now - 7L * DAY).executeAsList()
                val all = q.captureEventCounts(0L).executeAsList()
                val health = CaptureHealth.report(thirty, now, true, true, SenderRules.BUILT_IN)
                val live = ok.db.schemaQueries.allAccounts().executeAsList().filter { it.archived == 0L }
                val looks = ok.looks.all().associateBy { it.accountId }
                // the banks the person uses: those their accounts' names say, then those they chose in the setup
                val withBank = live.filter { it.type == "BANK" || it.type == "CREDIT_CARD" }.mapNotNull { a -> BankChoices.issuerOfAccountName(a.name)?.let { a to it } }
                val chosen = CaptureSetupKeys.chosenBanks(t.setting(CaptureSetupKeys.BANKS))
                val issuers = (withBank.map { it.second } + chosen).distinct()
                val banks = issuers.map { issuer ->
                    val names = BankChoices.senderNamesOf(issuer)
                    val rows = all.filter { it.sender_root in names }
                    BankExpectation(issuer, names, rows.isNotEmpty(), rows.mapNotNull { it.last_at }.maxOrNull())
                }
                val extra = TrustedSenders.parse(t.setting(CaptureKeys.SETTING_EXTRA_ROOTS))
                val skipped = SkippedSenders.rows(t.skippedCounters(), SenderRules.BUILT_IN.keys + extra, now).map { SkippedName(it.root, it.count) }
                fun sum(outcome: String) = seven.filter { it.outcome == outcome }.sumOf { it.n }
                val inputs = CheckupInputs(
                    way = way(), nowMillis = now,
                    smsSwitchOn = t.setting(CaptureKeys.SETTING_SMS_ON) == "1", smsPermission = smsPermission,
                    notifSwitchOn = t.setting(CaptureKeys.SETTING_NOTIF_ON) == "1", emailSwitchOn = t.setting(CaptureKeys.SETTING_EMAIL_ON) == "1",
                    notifAccess = notifAccess, listenerConnected = phone.listenerConnected, postNotifications = phone.postNotifications, battery = phone.battery, maker = phone.maker,
                    batteryListTicked = t.setting(CaptureCheckup.BATTERY_TICK_KEY) == "1",
                    banks = banks,
                    accountsWithoutDigits = withBank.filter { (a, _) -> looks[a.id]?.last4.isNullOrBlank() }.map { it.first.name },
                    accountsWithDigits = withBank.count { (a, _) -> !looks[a.id]?.last4.isNullOrBlank() },
                    trustedNames = extra, builtInCount = SenderRules.BUILT_IN.size, skipped = skipped,
                    foundLast7Days = sum("CAPTURED") + sum("BILL"), lastFoundAt = health.lastCapturedAt, lastSeenAt = all.mapNotNull { it.last_at }.maxOrNull(),
                    ignored = IgnoredCounts(health.otps, health.personal, health.notices, health.declined, health.duplicates, thirty.filter { it.outcome == "NOT_ALLOWLISTED" }.sumOf { it.count }),
                    shortcutTicks = t.setting(CaptureCheckup.SHORTCUT_TICKS_KEY).orEmpty().split(',').filter { it in CaptureCheckup.SHORTCUT_TICK_KEYS }.toSet(),
                )
                val rows = CaptureCheckup.rows(inputs)
                CheckupUi(rows, CaptureCheckup.headline(rows))
            }
            _checkup.value = ui
        }
    }

    /** Ticks or unticks a line of a checklist the app cannot see (the maker's battery list, the iPhone's Shortcuts steps), and works the rows out again. Only a short word is kept. */
    fun tick(key: String, on: Boolean) {
        scope.launch(IO) {
            container.write { ok ->
                val t = ok.templates
                if (key == CaptureCheckup.BATTERY_TICK_KEY) t.putSetting(key, if (on) "1" else "0")
                else if (key in CaptureCheckup.SHORTCUT_TICK_KEYS) {
                    val have = t.setting(CaptureCheckup.SHORTCUT_TICKS_KEY).orEmpty().split(',').filter { it in CaptureCheckup.SHORTCUT_TICK_KEYS }.toSet()
                    t.putSetting(CaptureCheckup.SHORTCUT_TICKS_KEY, (if (on) have + key else have - key).joinToString(","))
                }
            }
            last?.let { (p, s, n) -> load(p, s, n) }
        }
    }

    private var generation = 0

    /** Forgets the answer, and any answer still being worked out (it would otherwise appear later, next to an empty text box). */
    fun clearResult() { generation++; _result.value = null }

    /**
     * Reads [text] (as sent by [sender], which may be blank) the way the app would and publishes the answer. Reads the person's accounts, categories and rules to say which account and category it would
     * pick; writes nothing: no inbox item, no count in capture health, no skipped-sender counter, no learned choice.
     */
    fun check(sender: String, text: String) {
        val words = text.take(MessageIngest.MAX_BODY)
        val mine = ++generation
        scope.launch(IO) {
            val answer = container.write { ok ->
                val t = ok.templates
                val extra = TrustedSenders.parse(t.setting(CaptureKeys.SETTING_EXTRA_ROOTS)).toSet()
                val reading = MessageCheck.read(sender, words, extra, todayLocal())
                describe(ok, reading, words)
            } ?: MessageCheckUi("ignore", "Expenses could not open its data.", emptyList(), "Nothing was read.")
            if (mine == generation) _result.value = answer
        }
    }

    private fun describe(ok: AppContainer.Opened.Ok, r: MessageCheck.Reading, body: String): MessageCheckUi {
        val lines = linesOf(ok, r, body)
        val senderLine = when (r.sender) {
            MessageCheck.SenderStatus.NOT_GIVEN -> "none given" + if (r.bankFromWords && r.issuer != null) ", the words name ${r.issuer}" else ""
            MessageCheck.SenderStatus.ON_LIST -> "${r.senderRoot}, on the list of banks"
            MessageCheck.SenderStatus.ADDED_BY_YOU -> "${r.senderRoot}, a name you added"
            MessageCheck.SenderStatus.NOT_ON_LIST -> "${r.senderRoot}, not on any list"
            MessageCheck.SenderStatus.NOT_A_HEADER -> "a phone number or a person"
        }
        val all = listOf("Sender" to senderLine) + lines
        val (verdict, headline) = when (r.verdict) {
            MessageCheck.Verdict.TRANSACTION -> "add" to "The app would put this in To review"
            MessageCheck.Verdict.BILL -> "bill" to "The app would put this in Bills"
            MessageCheck.Verdict.IGNORED -> "ignore" to "The app would ignore this text"
        }
        val explain = when (r.verdict) {
            MessageCheck.Verdict.IGNORED -> MessageCheck.ignoredBecause(r.ignoreReason ?: IgnoreReason.NOT_A_TRANSACTION, r.sender)
            else -> "It would wait there until you add or skip it. Nothing has been saved or counted, and the text is gone as soon as you leave this screen."
        }
        val offer = r.ifAdded
        return MessageCheckUi(
            verdict, headline, all, explain,
            trustRoot = if (offer != null) r.senderRoot else null,
            ifAdded = if (offer != null) linesOf(ok, offer, body) else emptyList(),
        )
    }

    private fun linesOf(ok: AppContainer.Opened.Ok, r: MessageCheck.Reading, body: String): List<Pair<String, String>> {
        if (r.verdict == MessageCheck.Verdict.IGNORED) return emptyList()
        val money = r.amountMinor?.let { Money(it, Currency(r.currency ?: "INR", 2)).formatted() }
        val out = ArrayList<Pair<String, String>>()
        out += "Bank" to (r.issuer ?: "not told from the words")
        if (r.verdict == MessageCheck.Verdict.BILL) {
            out += "Kind" to (r.billKind?.label ?: "bill").replaceFirstChar { it.uppercase() }
            if (money != null) out += "Amount due" to money
            r.billDue?.let { out += "Due" to formatDate(it, "d MMM yyyy") }
        } else {
            out += "Direction" to (if (r.billPayment) "Payment of a bill, " else "") + if (r.moneyOut == true) "money out" else "money in"
            if (money != null) out += "Amount" to money
            r.rawPayee?.let { raw -> out += "Payee" to (app.expenses.core.domain.DefaultRules.displayNameFor(raw) ?: raw) }
            out += "Read by" to if (r.bankRule) "a rule written for this bank's wording" else "the general rules, so check it"
        }
        out += "Account digits" to (MessageCheck.maskedDigits(r.accountDigits)?.let { "ending $it" } ?: "the text prints none")
        if (r.verdict == MessageCheck.Verdict.TRANSACTION) {
            out += "Your account" to accountLine(ok, r, body)
            categoryLine(ok, r, body)?.let { out += "Category" to it }
        }
        return out
    }

    private fun accountLine(ok: AppContainer.Opened.Ok, r: MessageCheck.Reading, body: String): String {
        val live = ok.db.schemaQueries.allAccounts().executeAsList().filter { it.archived == 0L }
        if (live.isEmpty()) return "you have no account yet"
        val names = live.associate { it.id to it.name }
        val resolver = AccountResolver(ok, live.mapTo(HashSet()) { it.id })
        val issuer = r.issuer ?: return "not known: the bank is not told from the words"
        return when (val m = resolver.resolve(issuer, r.accountDigits, preferCard = body.contains("card", ignoreCase = true))) {
            is app.expenses.core.domain.AccountMatch.Result.Found -> "matches ${names[m.accountId] ?: "an account"}"
            is app.expenses.core.domain.AccountMatch.Result.Several -> "could be " + m.accountIds.mapNotNull { names[it] }.joinToString(" or ") + ": the app would ask which"
            app.expenses.core.domain.AccountMatch.Result.None -> if (r.accountDigits == null) "none: the text prints no digits, so the app would ask which" else "no account has these digits yet: the app would ask, or offer a new one"
        }
    }

    private fun categoryLine(ok: AppContainer.Opened.Ok, r: MessageCheck.Reading, body: String): String? {
        val money = r.moneyOut ?: return null
        val categories = ok.categories.list().filter { !it.hidden }
        val rules = ok.captureRules.rules()
        val rows = ok.captureRules.learnedRows()
        val taught = CaptureCategory.Taught(rows.associate { it.key to it.categoryId }, rows.associate { it.key to it.uses })
        val raw = r.rawPayee
        val named = raw?.let { ok.templates.setting("payee_name:" + app.expenses.core.domain.CategorySuggest.merchantKey(it)) }?.takeIf { it.isNotBlank() }
        val text = listOfNotNull(named, body).joinToString(" ")
        val advice = if (money) {
            val usable = categories.filter { it.kind.forExpense }.map { it.id }.toSet()
            val byName = categories.filter { it.kind.forExpense && it.parentId == null }.associate { it.name.lowercase() to it.id }
            val person = PayeeKinds.of(raw) == PayeeKind.PERSON
            CaptureCategory.advise(raw, text, rules, taught, usable, byName, null, toAPerson = person, personLike = person || PayeeKinds.looksLikePerson(raw) || PayeeKinds.looksLikePerson(named))
        } else {
            val usable = categories.filter { it.kind.forIncome }.map { it.id }.toSet()
            val byName = categories.filter { it.kind.forIncome && it.parentId == null }.associate { it.name.lowercase() to it.id }
            CaptureCategory.adviseIncome(raw, text, rules, taught, usable, byName, null)
        }
        val name = advice?.let { a -> categories.firstOrNull { it.id == a.categoryId }?.name } ?: return "none suggested: it would stay Uncategorised until you choose"
        return "$name (${advice.note})"
    }

    private companion object { const val DAY = 86_400_000L }
}

/** The keys and one decoder of the guided setup that both the model above and the view model use. */
object CaptureSetupKeys {
    const val STATE = app.expenses.core.domain.CaptureSetup.STATE_KEY
    const val BANKS = app.expenses.core.domain.CaptureSetup.BANKS_KEY

    /** The banks chosen in step 2 that are still on the built-in list. */
    fun chosenBanks(raw: String?): List<String> = app.expenses.core.domain.CaptureSetup.decodeBanks(raw, BankChoices.issuers)
}

/** The guided setup as the screen needs it: where it stands and the banks chosen so far. */
data class SetupUi(val state: app.expenses.core.domain.SetupState = app.expenses.core.domain.SetupState(), val banks: List<String> = emptyList())

/**
 * Whether anything is bringing bank messages in already: a capture switch is on (Android), or a message has reached the app (iPhone). Cheap: three setting reads, or one grouped read of the
 * capture log's counts on the iPhone.
 */
internal fun AppContainer.Opened.Ok.captureRunning(platform: Platform): Boolean =
    if (platform.supportsShortcutsCapture) db.schemaQueries.captureEventCounts(0L).executeAsList().isNotEmpty()
    else listOf(CaptureKeys.SETTING_SMS_ON, CaptureKeys.SETTING_NOTIF_ON, CaptureKeys.SETTING_EMAIL_ON).any { templates.setting(it) == "1" }

/** Whether this platform has any way to bring in bank messages. */
internal fun Platform.hasCaptureWay(): Boolean = supportsShortcutsCapture || supportsSmsCapture || supportsNotificationCapture || supportsCaptureSources
