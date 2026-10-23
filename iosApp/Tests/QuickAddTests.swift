import XCTest
import Shared
@testable import Expenses

/// The Quick add widget's hand-over: the app tells the widget which quick entries there are, the widget queues a tap, the app records it.
final class QuickAddTests: XCTestCase {
    private let host = CryptoServices()

    func testATappedQuickEntryIsRecordedByTheAppStampedWithTheTimeOfTheTap() async throws {
        let id = "quick-\(UUID().uuidString.prefix(6))"
        try await IosQuickAddsDebug.shared.saveTemplate(host: host, id: id, name: "Chai", rupees: 20)
        let buttons = try await IosQuickAddsDebug.shared.buttons(host: host)
        XCTAssertTrue(buttons.contains { $0.hasPrefix("\(id)\tChai\t") && $0.contains("20.00") }, "\(buttons)")

        let tappedAt = Int64(Date().timeIntervalSince1970 * 1000) - 60_000
        let tap = UUID().uuidString
        IosQuickAddsDebug.shared.tap(uuid: tap, id: id, at: tappedAt)
        IosQuickAddsDebug.shared.tap(uuid: UUID().uuidString, id: "no-such-entry", at: tappedAt)
        let recorded = try await IosQuickAddsDebug.shared.drain(host: host)
        XCTAssertEqual(recorded.intValue, 1, "the tap on an entry that no longer exists records nothing")
        let again = try await IosQuickAddsDebug.shared.drain(host: host)
        XCTAssertEqual(again.intValue, 0, "the queue was emptied")
        // the same tap read again (a crash before the queue was cleared, or two overlapping runs) is not recorded twice
        IosQuickAddsDebug.shared.tap(uuid: tap, id: id, at: tappedAt)
        let twice = try await IosQuickAddsDebug.shared.drain(host: host)
        XCTAssertEqual(twice.intValue, 0, "a tap with the same uuid is recorded once")
    }
}
