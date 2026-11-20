import AppIntents
import Foundation

/// UI S8: what the "Add expense" control does (`AddExpenseControl`, iOS 18): opens Expenses on a new expense through the same link as the widgets' "+ Add"
/// (expenses://add, handled by `ExpensesApp.onOpenURL`). This file is in BOTH the widget extension and the app target (iosApp/project.yml): an intent that opens
/// the app must be known to the app as well. Not offered in the Shortcuts app on its own (the app's "Add expense" intent is the one for Siri and Shortcuts).
@available(iOS 18.0, *)
struct OpenNewExpenseIntent: AppIntent {
    static var title: LocalizedStringResource = "New expense"
    static var description = IntentDescription("Opens Expenses on a new expense.")
    static var openAppWhenRun: Bool = true
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult & OpensIntent {
        .result(opensIntent: OpenURLIntent(URL(string: "expenses://add")!))
    }
}
