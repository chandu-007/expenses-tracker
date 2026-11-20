import AppIntents
import SwiftUI
import WidgetKit

/// UI S8 (backlog N-10): an "Add expense" control for Control Center, the Lock Screen's two bottom buttons and the Action button (iOS 18 and later; on iOS 17 the
/// widget bundle leaves it out, see `ExpensesWidgets`). One tap runs `OpenNewExpenseIntent`, which opens Expenses on the Add screen, so the app lock still asks first.
/// It shows nothing from the ledger, so it needs no privacy handling. No entitlement beyond the existing App Group [INFERENCE: free signing is enough; not tried on a device].
@available(iOS 18.0, *)
struct AddExpenseControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "app.expenses.tracker.ios.control.add") {
            ControlWidgetButton(action: OpenNewExpenseIntent()) {
                Label("Add expense", systemImage: "plus")
            }
        }
        .displayName("Add expense")
        .description("Opens Expenses on a new expense.")
    }
}
