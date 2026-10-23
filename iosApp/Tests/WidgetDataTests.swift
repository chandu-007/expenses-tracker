import XCTest
import Shared
@testable import Expenses

/// What the app hands the Today widget (the widget itself, SwiftUI in iosApp/Widget, only draws it). Read back from the App Group's preferences.
final class WidgetDataTests: XCTestCase {
    private let host = CryptoServices()
    private var defaults: UserDefaults { UserDefaults(suiteName: "group.app.expenses.tracker.ios")! }

    override func tearDown() async throws { try await IosWidgetDebug.shared.setHideAmounts(host: host, on: false) }

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
}
