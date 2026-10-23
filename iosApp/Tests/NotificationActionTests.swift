import XCTest
import Shared
@testable import Expenses

/// The buttons on a notification (Mark paid, the two snoozes, Pay now), pressed the way the notification delegate presses them.
final class NotificationActionTests: XCTestCase {
    private let host = CryptoServices()

    private func press(_ action: String, ref: String?, kind: String = "BILL_TRACKED") async {
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            IosNotifications.shared.performAction(action: action, key: "billtrack:test", ref: ref, kind: kind, title: "A bill is due", body: "Open the app", done: { c.resume() })
        }
    }

    func testMarkPaidMarksTheTrackedBillPaidAndASnoozeLeavesItAlone() async throws {
        IosNotifications.shared.install(host: host)
        let id = try await IosNotificationsDebug.shared.trackedBill(host: host, dueDay: "25-12-2030", seed: Int32.random(in: 1000...8000))
        let before = try await IosNotificationsDebug.shared.billStatus(host: host, id: id)
        XCTAssertEqual(before, "TRACKED")
        await press("snooze_tomorrow", ref: id)
        let afterSnooze = try await IosNotificationsDebug.shared.billStatus(host: host, id: id)
        XCTAssertEqual(afterSnooze, "TRACKED", "a snooze does not pay it")
        await press("mark_paid", ref: id)
        let after = try await IosNotificationsDebug.shared.billStatus(host: host, id: id)
        XCTAssertEqual(after, "PAID")
    }

    func testPayNowAsksTheAppToOpenTheTransferToThatCard() async {
        await press("pay_card", ref: "card-42", kind: "CARD_DUE")
        XCTAssertEqual(IosNotificationsDebug.shared.payRequestFor(), "card-42")
    }
}
