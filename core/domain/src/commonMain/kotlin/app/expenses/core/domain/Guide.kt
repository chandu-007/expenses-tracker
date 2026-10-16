package app.expenses.core.domain

/** The screen a tour step is shown on. Each platform maps these to its own navigation. A step with no screen is shown on the home screen. */
enum class GuideTarget { ADD, SEARCH, REPORTS, INBOX, CAPTURE, BILLS, ACCOUNTS, CATEGORIES, BUDGET, RECURRING, LOANS, CALENDAR, NOTIFICATIONS, PRIVACY, DATA, MORE }

/**
 * One stop of the tour: the app opens the real screen ([target], or home when null), highlights a real control on it when there is one ([spot], a name the
 * screen registers for that control), and explains what the part is for and how to use it. [since] is the guide version that introduced the stop: after an
 * update only the stops newer than what the person has already seen are offered.
 */
data class GuideStep(val id: String, val title: String, val summary: String, val points: List<String>, val where: String, val target: GuideTarget? = null, val spot: String? = null, val since: Int = 1)

enum class GuideMode {
    /** First time: every page. */
    FIRST_RUN,
    /** After an update: only the pages added since the person last saw the guide. */
    WHATS_NEW,
    /** Opened by the person from More: every page, and nothing about it is remembered. */
    MANUAL,
}

data class GuidePlan(val mode: GuideMode, val steps: List<GuideStep>)

/**
 * The interactive tour of the app: it walks through each part on the real screens. Raise [VERSION] when pages are added and give them `since = VERSION`; people who finished or skipped an earlier version
 * are then offered just those pages, once.
 */
object Guide {
    const val VERSION = 1

    val steps: List<GuideStep> = listOf(
        GuideStep(
            "welcome", "Welcome", "This is a short tour on the real screens, about two minutes. Skip it any time, and find it again in More.",
            listOf("Everything stays on this phone. There is no account and nothing is sent anywhere.", "Tap Next and the app opens each part for you, with a note on what it is for."),
            "More, then How to use this app",
        ),
        GuideStep(
            "home", "Your home screen", "This month's spending sits at the top. Below it: your budget, your accounts and your latest entries.",
            listOf(
                "Tap an entry to change it. Its history shows every edit, and you can go back to an earlier version.",
                "Deleted something by mistake? Tap Undo on the message at the bottom.",
                "Press and hold an entry to select several, then change their category or delete them together.",
                "Make it yours with More, then Customise home: choose and reorder what is shown.",
            ),
            "Home, in the bottom bar", spot = "hero",
        ),
        GuideStep(
            "add", "Add an expense in seconds", "The amount is all it takes. Sort it into a category, account and note now or later.",
            listOf(
                "Tap Add on the home screen, type the amount here, and Save.",
                "Choose Income, Transfer between your accounts, Refund, or Lent and borrowed at the top of the form.",
                "Split one payment across categories, add tags or attach a photo of the bill.",
                "Save a payment you repeat often as a template, and add it again in one tap.",
                "Faster still: the home screen widget and the Quick Settings tile open this form directly.",
            ),
            "The Add button on Home", GuideTarget.ADD, spot = "amount",
        ),
        GuideStep(
            "search", "Search and filters", "Find any entry in a moment, even in years of history.",
            listOf("Search by words in the note or the merchant, or by amount.", "Narrow it by category, account, dates or an amount range.", "Everything found can be exported from Your data."),
            "Search, in the bottom bar", GuideTarget.SEARCH,
        ),
        GuideStep(
            "reports", "Reports", "See where the money goes: by category and over time.",
            listOf("Switch between months and other periods.", "Tap a category to see the entries behind its total.", "Transfers between your own accounts are never counted as spending."),
            "Reports, in the bottom bar", GuideTarget.REPORTS,
        ),
        GuideStep(
            "calendar", "Calendar", "A month at a glance, day by day.",
            listOf("Each day shows what you spent.", "Tap a day to see its entries."),
            "More, then Calendar", GuideTarget.CALENDAR,
        ),
        GuideStep(
            "capture", "Capture from texts", "Let the app turn your bank and card texts into entries, so you do not have to type them.",
            listOf(
                "Turn it on here. Android asks permission to receive texts.",
                "Only texts from banks, cards and a few billers are read, checked by the sender before the text is opened. Texts from people are never read.",
                "It can also read the confirmation notification of payment apps (Google Pay, PhonePe, Paytm, BHIM): allow notification access.",
                "Nothing leaves the phone, and nothing is added to your books until you say so.",
                "If your bank is missing, add its sender name on this screen.",
            ),
            "More, then Capture from texts", GuideTarget.CAPTURE,
        ),
        GuideStep(
            "inbox", "To review", "What was captured waits here for you. You decide what becomes an entry.",
            listOf(
                "Tap Add to accept a suggestion, or change the account and category first. Skip drops it.",
                "Add all the clear ones at once, and look at the rest.",
                "A card payment is suggested as a transfer to your card, and an ATM withdrawal as money moved to cash, so neither counts as spending.",
                "A number on the Home banner and on More shows how many are waiting.",
            ),
            "More, then To review", GuideTarget.INBOX,
        ),
        GuideStep(
            "bills", "Bills", "Credit card statements and electricity, water, gas, mobile, broadband, insurance and EMI bills are found in your texts, and you are told.",
            listOf(
                "A found bill waits under Found in your messages. Add it to be reminded 3 days before, on the day, and if it is overdue.",
                "Mark paid can enter the payment for you: as a transfer to the card for a card bill, otherwise as an expense in Bills. Or it only marks the bill done.",
                "When you later add the payment from To review, the matching bill is marked paid for you.",
                "Skip a bill you do not want, and it will not come back when the same reminder arrives again.",
            ),
            "More, then Bills, and a card on Home", GuideTarget.BILLS,
        ),
        GuideStep(
            "accounts", "Accounts and credit cards", "Bank accounts, cash, wallets and cards, each with its own balance.",
            listOf(
                "Add as many accounts as you like. Move money between them with a Transfer.",
                "For a credit card, set the statement and due days to see what is left on the latest statement, the minimum due, and a reminder before the due date.",
                "Paying the card from your bank is a transfer, so it is not counted twice.",
            ),
            "More, then Accounts", GuideTarget.ACCOUNTS,
        ),
        GuideStep(
            "categories", "Categories", "Group spending the way you think about it.",
            listOf("Starter categories are ready; add your own, with sub-categories and colours.", "Suggested categories offer common ones in one tap.", "Hide a category you no longer use without losing its history."),
            "More, then Categories", GuideTarget.CATEGORIES,
        ),
        GuideStep(
            "budget", "Budget", "Set a limit for the month and always know what is left.",
            listOf("Pick a monthly limit; Home shows what is left and what you can spend a day.", "You can be told when you are close to it."),
            "The budget card on Home, or More, then Budget", GuideTarget.BUDGET,
        ),
        GuideStep(
            "recurring", "Repeating expenses", "Rent, subscriptions and EMIs that come every month.",
            listOf("Set how often, and the next date.", "Be reminded when one is due, then add it with a tap, or let it be added by itself.", "Edit or stop it any time."),
            "More, then Repeating expenses", GuideTarget.RECURRING,
        ),
        GuideStep(
            "loans", "Lent and borrowed", "Keep track of who owes you and whom you owe.",
            listOf("Add a person, then record money you gave or got.", "Repayments reduce what is owed; each person shows a running balance.", "None of it is counted as your own spending."),
            "More, then Lent and borrowed", GuideTarget.LOANS,
        ),
        GuideStep(
            "reminders", "Reminders", "Bills, budgets and a weekly summary, on your terms.",
            listOf("Choose which reminders you want, and the hour you want them.", "Quiet hours keep them silent at night.", "With Hide amounts on, notifications carry no figures."),
            "More, then Notifications", GuideTarget.NOTIFICATIONS,
        ),
        GuideStep(
            "privacy", "Privacy and App lock", "Your money stays private, on the phone and in front of other people.",
            listOf(
                "Hide amounts (in More) masks every figure on screen. Tap it again to show them.",
                "App lock asks for your fingerprint or phone lock when the app opens, and hides it from the recent-apps view and screenshots.",
                "This page lists exactly what the app can and cannot see.",
            ),
            "More, then App lock and Privacy", GuideTarget.PRIVACY,
        ),
        GuideStep(
            "data", "Your data: export, backup and import", "Your data is yours, and leaving is always free.",
            listOf(
                "Export every entry as an Excel or CSV file, free, any time.",
                "Make a backup file now and then: uninstalling the app deletes everything on this phone. A backup can be locked with a passphrase, and restoring it never overwrites what is already here.",
                "Automatic copies can be saved into a folder you choose.",
                "Bring in a bank statement (CSV or Excel) to add past entries.",
            ),
            "More, then Your data", GuideTarget.DATA,
        ),
        GuideStep(
            "more", "Everything else is under More", "More holds every part you just saw, grouped, plus Appearance, Customise home, Currency and this tour.",
            listOf("The widget: press and hold an empty spot on your home screen, choose Widgets, then this app. The amount is hidden until you tap the eye.", "Come back to this tour any time: More, then How to use this app."),
            "More, in the bottom bar", GuideTarget.MORE,
        ),
        GuideStep(
            "done", "You are ready", "That is everything. You can start with one expense.",
            listOf("Tap Add on the home screen and type an amount."),
            "The Add button on Home",
        ),
    )

    fun fullPlan(): GuidePlan = GuidePlan(GuideMode.MANUAL, steps)

    /**
     * What to offer when the app opens. [seen] is the guide version the person last finished or skipped (null: never), and [offForUpdates] is their choice to
     * be left alone after updates. Null means offer nothing.
     */
    fun planOnOpen(seen: Int?, offForUpdates: Boolean, pages: List<GuideStep> = steps, version: Int = VERSION): GuidePlan? {
        if (seen == null) return GuidePlan(GuideMode.FIRST_RUN, pages)
        if (seen >= version || offForUpdates) return null
        val fresh = pages.filter { it.since > seen }
        return if (fresh.isEmpty()) null else GuidePlan(GuideMode.WHATS_NEW, fresh)
    }
}
