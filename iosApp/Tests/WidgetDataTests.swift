import XCTest
import Shared
@testable import Expenses

/// What the app hands the Today widget (the widget itself, SwiftUI in iosApp/Widget, only draws it). Read back from the App Group's preferences.
final class WidgetDataTests: XCTestCase {
    private let host = CryptoServices()
    private var defaults: UserDefaults { UserDefaults(suiteName: "group.app.expenses.tracker.ios")! }

    override func tearDown() async throws {
        try await IosWidgetDebug.shared.setHideAmounts(host: host, on: false)
        try await IosWidgetDebug.shared.setWidgetsAllowed(host: host, on: true)
    }

    func testTodaysSpendingIsWrittenInFullOnlyWhileAmountsAreNotHiddenAndTheLockIsOff() async throws {
        _ = try await IosCapture.shared.addExpense(host: host, amountText: "250", note: "widget test", categoryName: nil)
        try await IosWidgetDebug.shared.setHideAmounts(host: host, on: false)
        try await IosWidgetDebug.shared.publishNow(host: host, lockEnabled: false)
        XCTAssertEqual(defaults.bool(forKey: "can_toggle"), true)
        XCTAssertTrue(defaults.string(forKey: "today_spent")!.contains("250.00") || defaults.string(forKey: "today_spent")!.contains("."), defaults.string(forKey: "today_spent") ?? "nil")
        XCTAssertFalse(defaults.string(forKey: "today_spent_masked")!.contains(where: { $0.isNumber }), "the masked text has no digit")

        // with the app lock on, or Hide amounts on, no figure is written at all: only the masked text, under both keys
        try await IosWidgetDebug.shared.publishNow(host: host, lockEnabled: true)
        XCTAssertEqual(defaults.bool(forKey: "can_toggle"), false)
        XCTAssertFalse(defaults.string(forKey: "today_spent")!.contains(where: { $0.isNumber }))
        try await IosWidgetDebug.shared.setHideAmounts(host: host, on: true)
        try await IosWidgetDebug.shared.publishNow(host: host, lockEnabled: false)
        XCTAssertEqual(defaults.bool(forKey: "can_toggle"), false)
        XCTAssertFalse(defaults.string(forKey: "today_spent")!.contains(where: { $0.isNumber }))
    }

    /// UI S8: "Allow widgets" off writes nothing from the ledger at all (no figure, masked or not, and no quick entry); the App lock leaves the pace out (a bar's length is a figure).
    func testAllowWidgetsOffWritesNothingAndTheLockWritesNoPace() async throws {
        _ = try await IosCapture.shared.addExpense(host: host, amountText: "250", note: "widget test", categoryName: nil)
        try await IosWidgetDebug.shared.setWidgetsAllowed(host: host, on: false)
        try await IosWidgetDebug.shared.publishNow(host: host, lockEnabled: false)
        XCTAssertEqual(defaults.object(forKey: "widgets_allowed") as? Bool, false)
        XCTAssertEqual(defaults.bool(forKey: "can_toggle"), false)
        for key in ["today_spent", "today_spent_masked", "today_per_day", "month_spent", "month_spent_masked", "budget_line", "pace_word", "pace_spoken"] {
            XCTAssertEqual(defaults.string(forKey: key), "", key)
        }
        XCTAssertEqual(defaults.stringArray(forKey: "quick_buttons") ?? [], [])
        XCTAssertEqual(defaults.integer(forKey: "pace_used_permille"), -1)

        try await IosWidgetDebug.shared.setWidgetsAllowed(host: host, on: true)
        try await IosWidgetDebug.shared.publishNow(host: host, lockEnabled: true)
        XCTAssertEqual(defaults.object(forKey: "widgets_allowed") as? Bool, true)
        XCTAssertEqual(defaults.integer(forKey: "pace_used_permille"), -1, "no pace while the lock is on")
        XCTAssertEqual(defaults.string(forKey: "pace_word"), "")
        XCTAssertFalse(defaults.string(forKey: "month_spent")!.contains(where: { $0.isNumber }))
    }
}
