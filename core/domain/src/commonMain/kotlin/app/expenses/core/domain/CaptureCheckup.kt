package app.expenses.core.domain

/** How this phone brings bank messages in, which decides which links of the chain the checkup looks at. */
enum class CaptureWay {
    /** Android with the text permission (every build except the Play one): texts, and optionally payment apps. */
    TEXTS,

    /** Android without the text permission (the Play build): payment-app notifications and, if the person turns it on, bank emails. */
    APPS_ONLY,

    /** iPhone: no access to texts; a Shortcuts automation hands each text over. */
    SHORTCUTS,

    /** Nothing can be captured on this platform. */
    NONE,
}

/** Android's three battery settings for an app, as the phone's settings call them. [UNKNOWN]: this phone does not say. */
enum class BatteryState { UNRESTRICTED, OPTIMISED, RESTRICTED, UNKNOWN }

/** Phone makers whose own battery managers are known to stop apps from running in the background, and so need their own line in the checklist. */
enum class PhoneMaker {
    /** Samsung: Settings, Battery, Background usage limits, Deep sleeping apps. */
    SAMSUNG,

    /** Xiaomi, Oppo, Vivo, Realme, OnePlus, Huawei, Honor and the like: an auto-start or app-launch list. */
    STRICT,
    OTHER,
    ;

    companion object {
        private val strict = setOf("xiaomi", "redmi", "poco", "oppo", "vivo", "realme", "oneplus", "huawei", "honor", "iqoo", "tecno", "infinix", "itel")

        /** The maker as the phone reports it (Build.MANUFACTURER), any case. */
        fun of(manufacturer: String?): PhoneMaker {
            val m = manufacturer?.trim()?.lowercase().orEmpty()
            return when {
                m == "samsung" -> SAMSUNG
                m in strict -> STRICT
                else -> OTHER
            }
        }
    }
}

/** One bank the person uses (an account's bank, or one chosen in the setup) and whether any text from its sender names has reached the app. */
data class BankExpectation(val issuer: String, val senderNames: List<String>, val seen: Boolean, val lastSeenAt: Long?)

/** A skipped sender name and how many of its texts were skipped. */
data class SkippedName(val root: String, val count: Long)

/** What was ignored in the last 30 days, by reason (counts only). */
data class IgnoredCounts(val otps: Long = 0, val personal: Long = 0, val notices: Long = 0, val declined: Long = 0, val duplicates: Long = 0, val notOnList: Long = 0)

/** Everything the checkup reads, gathered by the caller from cheap reads (a few settings, the capture log's counts, the accounts, the phone's permission state). Nothing here is a message. */
data class CheckupInputs(
    val way: CaptureWay,
    val nowMillis: Long,
    val smsSwitchOn: Boolean = false,
    val smsPermission: Boolean = false,
    val notifSwitchOn: Boolean = false,
    val emailSwitchOn: Boolean = false,
    val notifAccess: Boolean = false,
    val listenerConnected: Boolean = false,
    /** Whether the reminders permission (Android 13 and later) is given; null where the phone has no such permission. */
    val postNotifications: Boolean? = null,
    val battery: BatteryState = BatteryState.UNKNOWN,
    val maker: PhoneMaker = PhoneMaker.OTHER,
    /** The checklist line the app cannot see (Samsung's Deep sleeping apps, or another maker's list) was ticked by the person. */
    val batteryListTicked: Boolean = false,
    val banks: List<BankExpectation> = emptyList(),
    /** Names of the accounts that carry a bank's name but have no last digits. */
    val accountsWithoutDigits: List<String> = emptyList(),
    /** How many accounts have a bank's name and their digits. */
    val accountsWithDigits: Int = 0,
    val trustedNames: List<String> = emptyList(),
    val builtInCount: Int = 0,
    val skipped: List<SkippedName> = emptyList(),
    val foundLast7Days: Long = 0,
    val lastFoundAt: Long? = null,
    val lastSeenAt: Long? = null,
    val ignored: IgnoredCounts = IgnoredCounts(),
    /** iPhone: the Shortcuts steps the person ticked (`shortcut`, `automation`, `locked`). */
    val shortcutTicks: Set<String> = emptySet(),
)

enum class CheckLevel {
    /** Fine as it is. */
    GOOD,

    /** Something to fix: counted in "N things need attention". */
    ATTENTION,

    /** Something to read; nothing is broken. */
    NOTE,
}

/** What a button on a row does. The screen maps each to the existing screen or permission flow. */
enum class CheckAct {
    ALLOW_TEXTS, TURN_ON_TEXTS, OPEN_APP_SETTINGS, OPEN_NOTIFICATION_ACCESS, RECONNECT_LISTENER, ALLOW_REMINDERS, OPEN_BATTERY, OPEN_SENDERS, OPEN_ACCOUNTS, TRUST_SENDER,
    OPEN_SHORTCUTS, OPEN_CAPTURE, TURN_ON_PAYMENT_APPS, OPEN_SETUP,
}

data class CheckAction(val act: CheckAct, val label: String, val arg: String = "")

/** A line of a checklist the app cannot check by itself: the person ticks it, and [key] is what is remembered. */
data class CheckTick(val key: String, val ticked: Boolean, val label: String)

/**
 * One line of the checkup. [id] is stable (it is part of the test tags); [title] and [detail] are plain sentences; the [level] is also written out ("OK", "Fix this", "Note") so no meaning
 * rests on a colour.
 */
data class CheckRow(
    val id: String, val level: CheckLevel, val title: String, val detail: String,
    val actions: List<CheckAction> = emptyList(), val tick: CheckTick? = null,
)

data class CheckupHeadline(val text: String, val attention: Int, val notes: Int)

/**
 * "Why isn't capture working?": every link of the chain from a bank's text to a line in To review, checked from what the app can see, with what to do about each in plain words. Pure: the
 * list of rows is a function of [CheckupInputs] alone, so every combination can be tested without a phone.
 */
object CaptureCheckup {
    const val RECENT_DAYS = 7
    private const val DAY = 86_400_000L

    /** The settings key of the ticked battery-list line, and the keys of the iPhone's Shortcuts lines. */
    const val BATTERY_TICK_KEY = "checkup_battery_ticked"
    const val SHORTCUT_TICKS_KEY = "checkup_shortcut_ticks"
    val SHORTCUT_TICK_KEYS = listOf("shortcut", "automation", "locked")

    fun headline(rows: List<CheckRow>): CheckupHeadline {
        val attention = rows.count { it.level == CheckLevel.ATTENTION }
        val notes = rows.count { it.level == CheckLevel.NOTE }
        val text = when (attention) { 0 -> "Everything looks fine"; 1 -> "1 thing needs attention"; else -> "$attention things need attention" }
        return CheckupHeadline(text, attention, notes)
    }

    /** The words that stand with each level, so the state is readable without its colour or icon. */
    fun levelWord(level: CheckLevel): String = when (level) { CheckLevel.GOOD -> "OK"; CheckLevel.ATTENTION -> "Fix this"; CheckLevel.NOTE -> "Note" }

    fun rows(i: CheckupInputs): List<CheckRow> = buildList {
        when (i.way) {
            CaptureWay.NONE -> return@buildList
            CaptureWay.TEXTS -> { addTexts(i); addPaymentApps(i); addReminders(i); addBattery(i); addBanks(i); addSkipped(i); addDigits(i); addActivity(i) }
            CaptureWay.APPS_ONLY -> { addPaymentApps(i); addReminders(i); addBattery(i); addSkipped(i); addDigits(i); addActivity(i) }
            CaptureWay.SHORTCUTS -> { addShortcuts(i); addSenders(i); addDigits(i); addActivity(i) }
        }
    }

    private fun MutableList<CheckRow>.addTexts(i: CheckupInputs) {
        add(
            when {
                !i.smsPermission -> CheckRow(
                    "sms", CheckLevel.ATTENTION, "Reading bank texts is not allowed",
                    "Android has not given this app permission to receive texts, so no bank text can reach it. If Android no longer asks, open the app's settings and allow Text messages (SMS) there.",
                    listOf(CheckAction(CheckAct.ALLOW_TEXTS, "Allow reading texts"), CheckAction(CheckAct.OPEN_APP_SETTINGS, "Open app settings")),
                )
                !i.smsSwitchOn -> CheckRow(
                    "sms", CheckLevel.ATTENTION, "Reading bank texts is switched off",
                    "Android allows it, but the switch in Capture is off, so texts are not looked at.",
                    listOf(CheckAction(CheckAct.TURN_ON_TEXTS, "Turn it on")),
                )
                else -> CheckRow("sms", CheckLevel.GOOD, "Reading bank texts is on", "Android lets this app receive texts and the switch is on. Only texts from banks and billers on the list are read.")
            },
        )
    }

    private fun MutableList<CheckRow>.addPaymentApps(i: CheckupInputs) {
        val wanted = i.notifSwitchOn || i.emailSwitchOn
        val what = if (i.notifSwitchOn && i.emailSwitchOn) "payment apps and bank emails" else if (i.emailSwitchOn) "bank emails" else "payment apps"
        when {
            wanted && !i.notifAccess -> add(
                CheckRow(
                    "listener", CheckLevel.ATTENTION, "Notification access is not given",
                    "To read the alerts of $what, Android must let this app see notifications. Nothing from other apps is read.",
                    listOf(CheckAction(CheckAct.OPEN_NOTIFICATION_ACCESS, "Give notification access")),
                ),
            )
            wanted && !i.listenerConnected -> add(
                CheckRow(
                    "listener", CheckLevel.ATTENTION, "Notification access is given, but the app is not connected",
                    "Android sometimes drops the link after a restart or when the phone closes the app to save memory. Reconnecting usually fixes it; if not, switch the access off and on again in Android's list.",
                    listOf(CheckAction(CheckAct.RECONNECT_LISTENER, "Reconnect"), CheckAction(CheckAct.OPEN_NOTIFICATION_ACCESS, "Open notification access")),
                ),
            )
            wanted -> add(CheckRow("listener", CheckLevel.GOOD, "Reading $what is on", "Notification access is given and the app is connected."))
            i.way == CaptureWay.APPS_ONLY -> add(
                CheckRow(
                    "listener", CheckLevel.ATTENTION, "Nothing is switched on to read payments",
                    "This version reads the alerts of payment apps (Google Pay, PhonePe, Paytm, BHIM). Turn that on in Capture, then give notification access.",
                    listOf(CheckAction(CheckAct.TURN_ON_PAYMENT_APPS, "Turn on payment apps")),
                ),
            )
            else -> add(
                CheckRow(
                    "listener", CheckLevel.NOTE, "Payment app alerts are off",
                    "Optional. If you also pay with Google Pay, PhonePe, Paytm or BHIM, their alerts can be read too. Turn it on in Capture.",
                    listOf(CheckAction(CheckAct.OPEN_CAPTURE, "Open Capture")),
                ),
            )
        }
    }

    private fun MutableList<CheckRow>.addReminders(i: CheckupInputs) {
        val granted = i.postNotifications ?: return
        add(
            if (granted) CheckRow("reminders", CheckLevel.GOOD, "Reminders are allowed", "Android lets this app show bill and budget reminders.")
            else CheckRow(
                "reminders", CheckLevel.ATTENTION, "Reminders are not allowed",
                "Android 13 and later needs your permission to show notifications. Without it, bill and budget reminders cannot appear. Capture itself still works.",
                listOf(CheckAction(CheckAct.ALLOW_REMINDERS, "Allow reminders")),
            ),
        )
    }

    private fun MutableList<CheckRow>.addBattery(i: CheckupInputs) {
        val settings = CheckAction(CheckAct.OPEN_BATTERY, "Open battery settings")
        when (i.battery) {
            BatteryState.UNRESTRICTED -> add(CheckRow("battery", CheckLevel.GOOD, "Battery: Unrestricted", "Android does not limit this app in the background."))
            BatteryState.OPTIMISED -> add(
                CheckRow(
                    "battery", CheckLevel.NOTE, "Battery: Optimised (Android's usual setting)",
                    "This is fine for most phones. If texts show up late or not at all, choose Unrestricted: App info, Battery.", listOf(settings),
                ),
            )
            BatteryState.RESTRICTED -> add(
                CheckRow(
                    "battery", CheckLevel.ATTENTION, "Battery: Restricted",
                    "Android may stop this app in the background, so a text can be missed. Choose Optimised or Unrestricted: App info, Battery.", listOf(settings),
                ),
            )
            BatteryState.UNKNOWN -> Unit
        }
        when (i.maker) {
            PhoneMaker.SAMSUNG -> add(
                CheckRow(
                    "battery_list", if (i.batteryListTicked) CheckLevel.GOOD else CheckLevel.ATTENTION, "Samsung: Deep sleeping apps",
                    "Samsung can put an app to deep sleep without telling it, and no app can see that. Open Settings, Battery, Background usage limits, Deep sleeping apps, and make sure Expenses is not in that list. Tick when you have looked.",
                    listOf(CheckAction(CheckAct.OPEN_BATTERY, "Open battery settings")), CheckTick(BATTERY_TICK_KEY, i.batteryListTicked, "I checked: Expenses is not in Deep sleeping apps"),
                ),
            )
            PhoneMaker.STRICT -> add(
                CheckRow(
                    "battery_list", if (i.batteryListTicked) CheckLevel.GOOD else CheckLevel.NOTE, "Your phone's own battery list",
                    "Some makers keep their own list of apps that may run in the background (it may be called Autostart, App launch or Battery saver). No app can see that list. Allow Expenses there if it is listed. Tick when you have looked.",
                    listOf(CheckAction(CheckAct.OPEN_BATTERY, "Open battery settings")), CheckTick(BATTERY_TICK_KEY, i.batteryListTicked, "I checked: Expenses is allowed to run in the background"),
                ),
            )
            PhoneMaker.OTHER -> Unit
        }
    }

    private fun MutableList<CheckRow>.addBanks(i: CheckupInputs) {
        if (i.banks.isEmpty()) {
            add(
                CheckRow(
                    "banks", CheckLevel.NOTE, "No bank is listed yet",
                    "Add your bank accounts (Accounts, or the setup) and this screen can say whether texts from each bank are arriving. Names you added: ${if (i.trustedNames.isEmpty()) "none" else i.trustedNames.joinToString(", ")}. ${i.builtInCount} banks and billers are on the built-in list.",
                    listOf(CheckAction(CheckAct.OPEN_SENDERS, "Bank sender names")),
                ),
            )
            return
        }
        i.banks.forEach { b ->
            val names = b.senderNames.joinToString(" or ")
            if (b.seen) add(
                CheckRow(
                    "bank_${b.issuer}", CheckLevel.GOOD, "${b.issuer}: texts from $names recognised",
                    "A text from ${b.issuer}'s sender name has reached the app${b.lastSeenAt?.let { " (the last one ${RelativeTime.ago(it, i.nowMillis)})" } ?: ""}.",
                ),
            ) else add(
                CheckRow(
                    "bank_${b.issuer}", CheckLevel.NOTE, "No text from ${b.issuer} seen yet",
                    "Nothing from $names has reached the app. That can just mean no text came since you turned capture on. If ${b.issuer} texts you from a different name, add that name.",
                    listOf(CheckAction(CheckAct.OPEN_SENDERS, "Add a sender name")),
                ),
            )
        }
    }

    private fun MutableList<CheckRow>.addSenders(i: CheckupInputs) {
        add(
            CheckRow(
                "senders", CheckLevel.GOOD, "Bank sender names",
                "${i.builtInCount} banks and billers are on the built-in list. Names you added: ${if (i.trustedNames.isEmpty()) "none" else i.trustedNames.joinToString(", ")}. These are used when the automation passes the Sender.",
                listOf(CheckAction(CheckAct.OPEN_SENDERS, "Bank sender names")),
            ),
        )
    }

    private fun MutableList<CheckRow>.addSkipped(i: CheckupInputs) {
        if (i.skipped.isEmpty()) {
            add(CheckRow("skipped", CheckLevel.GOOD, "No sender names were skipped", "Every text that arrived was from a name on the list, or from someone who is not a bank."))
            return
        }
        val n = i.skipped.size
        add(
            CheckRow(
                "skipped", CheckLevel.ATTENTION, if (n == 1) "1 sender name was skipped" else "$n sender names were skipped",
                "Texts from ${if (n == 1) "this name were" else "these names were"} skipped because ${if (n == 1) "it is" else "they are"} not on the list of banks. If one is your bank, treat it as your bank and its next texts will be read. Only the names are kept, never the words.",
                i.skipped.take(3).map { CheckAction(CheckAct.TRUST_SENDER, "Treat ${it.root} as my bank", it.root) },
            ),
        )
    }

    private fun MutableList<CheckRow>.addDigits(i: CheckupInputs) {
        when {
            i.accountsWithoutDigits.isNotEmpty() -> {
                val n = i.accountsWithoutDigits.size
                add(
                    CheckRow(
                        "digits", CheckLevel.ATTENTION, if (n == 1) "1 account needs its last digits" else "$n accounts need their last digits",
                        "Bank texts print the last 3 or 4 digits of the account. Add them to ${i.accountsWithoutDigits.take(4).joinToString(", ")}${if (n > 4) " and ${n - 4} more" else ""} so each text finds its account by itself.",
                        listOf(CheckAction(CheckAct.OPEN_ACCOUNTS, "Add the digits")),
                    ),
                )
            }
            i.accountsWithDigits > 0 -> add(CheckRow("digits", CheckLevel.GOOD, "Your bank accounts have their last digits", "Texts can find their account by those digits."))
        }
    }

    private fun MutableList<CheckRow>.addActivity(i: CheckupInputs) {
        val found = i.foundLast7Days
        val last = i.lastFoundAt?.let { RelativeTime.ago(it, i.nowMillis) }
        val skippedWords = buildList {
            fun said(n: Long, one: String, many: String) { if (n > 0) add("$n ${if (n == 1L) one else many}") }
            said(i.ignored.otps, "one-time password", "one-time passwords"); said(i.ignored.personal, "text from a person", "texts from people")
            said(i.ignored.notices, "notice that is not a payment", "notices that are not payments"); said(i.ignored.declined, "declined payment", "declined payments")
            said(i.ignored.duplicates, "text seen twice", "texts seen twice"); said(i.ignored.notOnList, "text from a name not on the list", "texts from names not on the list")
        }
        val skipped = if (skippedWords.isEmpty()) "Nothing was skipped in the last 30 days." else "Skipped in the last 30 days: ${skippedWords.joinToString(", ")}."
        val newest = i.lastSeenAt?.let { " The newest message of any kind arrived ${RelativeTime.ago(it, i.nowMillis)}." }.orEmpty()
        add(
            when {
                found > 0 -> CheckRow("activity", CheckLevel.GOOD, "${if (found == 1L) "1 payment" else "$found payments"} found in the last $RECENT_DAYS days", "The last one was found $last. $skipped$newest")
                i.lastFoundAt != null -> CheckRow("activity", CheckLevel.NOTE, "Nothing found in the last $RECENT_DAYS days", "The last payment was found $last. That is normal if no bank text came since. $skipped$newest")
                else -> CheckRow("activity", CheckLevel.NOTE, "Nothing found yet", "No payment has been found in a message so far. $skipped$newest")
            },
        )
    }

    private fun MutableList<CheckRow>.addShortcuts(i: CheckupInputs) {
        val recent = i.lastSeenAt?.let { i.nowMillis - it < RECENT_DAYS * DAY } == true
        if (recent) {
            add(CheckRow("shortcuts", CheckLevel.GOOD, "A message reached the app ${RelativeTime.ago(i.lastSeenAt!!, i.nowMillis)}", "The Shortcuts automation is working: it handed a message to this app in the last $RECENT_DAYS days."))
            return
        }
        add(
            CheckRow(
                "shortcuts", CheckLevel.ATTENTION, if (i.lastSeenAt == null) "No message has reached the app yet" else "No message reached the app in the last $RECENT_DAYS days",
                "iPhone gives no app your texts. A Shortcuts automation hands each bank text to this app. Either no text came, or the automation is not set up or not running. Go through these three lines and tick each one when it is true.",
                listOf(CheckAction(CheckAct.OPEN_SHORTCUTS, "Open Shortcuts"), CheckAction(CheckAct.OPEN_CAPTURE, "See the steps")),
            ),
        )
        listOf(
            "shortcut" to "The shortcut Capture bank message exists in Shortcuts, with the action Capture bank message from Expenses",
            "automation" to "An automation with the Message trigger runs that shortcut, with words your bank's texts always have (debited, credited)",
            "locked" to "The shortcut's Allow Running When Locked is on, and Always Allow was chosen",
        ).forEach { (key, label) ->
            val ticked = key in i.shortcutTicks
            add(CheckRow("shortcuts_$key", if (ticked) CheckLevel.GOOD else CheckLevel.ATTENTION, label, "", tick = CheckTick(key, ticked, "Done")))
        }
    }
}
