import AppIntents
import Shared

/// iOS gives apps no access to text messages. What it does allow is a Shortcuts personal automation ("When I get a message", run immediately) whose action
/// hands the message to an app. This is that action. It runs in this app's own process, started in the background by Shortcuts with no window, so everything
/// is done by the Kotlin singleton (`IosCapture`, which opens the database once per process through `IosCore`): the same pipeline as Android's receiver. The
/// sender (when passed) is judged before the words; a message that is not a bank transaction is counted and its words are thrown away.
struct CaptureBankMessageIntent: AppIntent {
    static var title: LocalizedStringResource = "Capture bank message"
    static var description = IntentDescription(
        "Hands a bank text message to Expenses. It is read on this phone: a transaction goes to To review, a bill to Bills, anything else is thrown away and only counted.",
        categoryName: "Expenses")
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Message", description: "The words of the text message: choose Shortcut Input.")
    var message: String

    @Parameter(title: "Sender", description: "Who sent it, from Shortcut Input. Optional.")
    var sender: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Capture bank message \(\.$message) from \(\.$sender)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let reply = try await IosCapture.shared.captureMessage(host: CryptoServices(), sender: sender, message: message)
        return .result(dialog: IntentDialog(stringLiteral: reply.message))
    }
}

/// The same for a bank's alert email. Shortcuts has an automation "When I get an email" (Mail app); its action hands the email here. Only an email whose sender
/// is a known bank is kept, and it is read by the same rules as a text from that bank. Any other email is dropped without being read or counted.
struct CaptureBankEmailIntent: AppIntent {
    static var title: LocalizedStringResource = "Capture bank email"
    static var description = IntentDescription(
        "Hands a bank's alert email to Expenses. It is read on this phone: a transaction goes to To review, a bill to Bills, anything else is thrown away.",
        categoryName: "Expenses")
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Email", description: "The words of the email: choose Shortcut Input (its Content).")
    var message: String

    @Parameter(title: "Sender", description: "Who sent it, from Shortcut Input (its Sender). Needed: only emails from known banks are kept.")
    var sender: String?

    @Parameter(title: "Subject", description: "The email's subject, from Shortcut Input. Optional.")
    var subject: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Capture bank email \(\.$message) from \(\.$sender) with subject \(\.$subject)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let reply = try await IosCapture.shared.captureEmail(host: CryptoServices(), sender: sender, subject: subject, message: message)
        return .result(dialog: IntentDialog(stringLiteral: reply.message))
    }
}

/// Records an expense now, with no screen: for Siri ("Add expense in Expenses"), the Action button and Shortcuts. The amount is text so that "250", "99.50" or
/// "₹250" all work; the category is optional (the one used last is used when it is empty) and must be one that exists.
struct AddExpenseIntent: AppIntent {
    static var title: LocalizedStringResource = "Add expense"
    static var description = IntentDescription(
        "Adds an expense to Expenses without opening it, in the account and (unless you name one) the category you used last.",
        categoryName: "Expenses")
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Amount", description: "For example 250 or 99.50.", requestValueDialog: "How much was it?")
    var amount: String

    @Parameter(title: "Note", description: "What it was for. Optional.")
    var note: String?

    @Parameter(title: "Category", description: "The name of one of your expense categories. Optional.")
    var category: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Add expense of \(\.$amount) for \(\.$note) in \(\.$category)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let reply = try await IosCapture.shared.addExpense(host: CryptoServices(), amountText: amount, note: note, categoryName: category)
        return .result(dialog: IntentDialog(stringLiteral: reply.message))
    }
}

/// Makes both actions appear in the Shortcuts app and in Siri without the person setting anything up.
struct ExpensesShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CaptureBankMessageIntent(),
            phrases: ["Capture bank message in \(.applicationName)", "Capture a bank message with \(.applicationName)"],
            shortTitle: "Capture bank message",
            systemImageName: "text.bubble")
        AppShortcut(
            intent: CaptureBankEmailIntent(),
            phrases: ["Capture bank email in \(.applicationName)", "Capture a bank email with \(.applicationName)"],
            shortTitle: "Capture bank email",
            systemImageName: "envelope")
        AppShortcut(
            intent: AddExpenseIntent(),
            phrases: ["Add expense in \(.applicationName)", "Add an expense to \(.applicationName)", "Log an expense in \(.applicationName)"],
            shortTitle: "Add expense",
            systemImageName: "plus.circle")
    }
}
