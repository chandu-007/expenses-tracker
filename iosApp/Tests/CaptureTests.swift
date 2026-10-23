import AppIntents
import XCTest
import Shared
@testable import Expenses

/// The Shortcuts entry points, called as Shortcuts calls them (the Kotlin singleton `IosCapture` and the App Intents), with made-up messages. The ledger the
/// tests run on may hold earlier runs, so every message carries a reference that is new each time and counts are compared as differences.
final class CaptureTests: XCTestCase {
    private let host = CryptoServices()

    private func ref() -> String { String((0..<12).map { _ in "0123456789".randomElement()! }) }

    private func bank(_ amount: String = "450.00", ref: String? = nil) -> String {
        "Rs.\(amount) debited from a/c XX1234 on 18-10-26 to TEST SHOP. Ref \(ref ?? self.ref()). Not you? Call 1800 000 000 -HDFC Bank"
    }

    func testABankTextFromAHeaderBecomesAPendingItem() async throws {
        let before = try await IosCapture.shared.pendingCount(host: host)
        let reply = try await IosCapture.shared.captureMessage(host: host, sender: "AX-HDFCBK-S", message: bank())
        XCTAssertEqual(reply.code, "CAPTURED")
        XCTAssertEqual(reply.message, "Captured, review it in Expenses")
        XCTAssertTrue(reply.kept)
        XCTAssertEqual(reply.pending, before.int64Value + 1)
    }

    func testTheSameTextTwiceIsOneItem() async throws {
        let text = bank()
        let first = try await IosCapture.shared.captureMessage(host: host, sender: "AX-HDFCBK-S", message: text)
        let second = try await IosCapture.shared.captureMessage(host: host, sender: "AX-HDFCBK-S", message: text)
        XCTAssertEqual(first.code, "CAPTURED")
        XCTAssertEqual(second.code, "DUPLICATE")
        XCTAssertFalse(second.kept)
        XCTAssertEqual(second.pending, first.pending)
        // handed over by a paste (no sender) the same day: still the same message
        let pasted = try await IosCapture.shared.captureMessage(host: host, sender: nil, message: text)
        XCTAssertEqual(pasted.code, "DUPLICATE")
        XCTAssertEqual(pasted.pending, first.pending)
    }

    func testAnOtpAPersonAndANoticeLeaveNothingButCounts() async throws {
        let before = try await IosCapture.shared.pendingCount(host: host).int64Value
        let otp = try await IosCapture.shared.captureMessage(host: host, sender: "AX-HDFCBK-S", message: "482913 is your OTP for a payment of Rs.450.00 at TEST SHOP. Do not share it. -HDFC Bank")
        XCTAssertEqual(otp.code, "OTP")
        XCTAssertEqual(otp.message, "Ignored: a one-time password")
        let person = try await IosCapture.shared.captureMessage(host: host, sender: "+919800000000", message: "Rs.450.00 debited from a/c XX1234 for the rent I owe you, \(ref())")
        XCTAssertEqual(person.code, "PERSONAL_SENDER")
        let notice = try await IosCapture.shared.captureMessage(host: host, sender: "AX-HDFCBK-S", message: "Dear customer, update your KYC to keep enjoying services. -HDFC Bank")
        XCTAssertEqual(notice.code, "NOT_A_TRANSACTION")
        XCTAssertEqual(notice.message, "Ignored: not a bank transaction")
        for r in [otp, person, notice] { XCTAssertFalse(r.kept); XCTAssertEqual(r.pending, before) }
    }

    // The email wordings are plausible, not copied from a real bank email.
    func testAnEmailFromAKnownBankIsReadLikeItsTextAndAnyOtherEmailIsDropped() async throws {
        let before = try await IosCapture.shared.pendingCount(host: host).int64Value
        let debit = try await IosCapture.shared.captureEmail(host: host, sender: "HDFC Bank InstaAlerts <alerts@hdfcbank.net>", subject: "Transaction alert", message: "Dear Customer, Rs.450.00 is debited from your a/c XX1234 on 18-10-26 to TEST SHOP. Ref \(ref()). Never share your OTP with anyone.")
        XCTAssertEqual(debit.code, "CAPTURED")
        XCTAssertEqual(debit.pending, before + 1)
        let credit = try await IosCapture.shared.captureEmail(host: host, sender: "HDFC Bank", subject: "Credit alert", message: "Your a/c XX1234 has been credited with INR 5,000.00 on 18-10-26. Ref \(ref()).")
        XCTAssertEqual(credit.code, "CAPTURED")
        let other = try await IosCapture.shared.captureEmail(host: host, sender: "Friendly Shop <offers@shop.example>", subject: "Sale", message: "Rs.450.00 debited from a/c XX1234 for the sale you missed \(ref())")
        XCTAssertEqual(other.code, "NOT_ALLOWLISTED")
        XCTAssertFalse(other.kept)
        XCTAssertEqual(other.pending, before + 2)
    }

    func testUnknownSenderIsNotReadButAHeaderTheListMissesIsOfferedByCountOnly() async throws {
        let r = try await IosCapture.shared.captureMessage(host: host, sender: "AX-NOSUCHBK-S", message: bank())
        XCTAssertEqual(r.code, "NOT_ALLOWLISTED")
        XCTAssertFalse(r.kept)
    }

    func testWithoutASenderTheWordsHaveToSayItIsABankMessage() async throws {
        let before = try await IosCapture.shared.pendingCount(host: host).int64Value
        let friend = try await IosCapture.shared.captureMessage(host: host, sender: nil, message: "I sent you Rs 500 for the tickets, \(ref())")
        XCTAssertEqual(friend.code, "NOT_A_TRANSACTION")
        XCTAssertEqual(friend.pending, before)
        // no bank named, but an amount, money that moved and an account mask
        let masked = try await IosCapture.shared.captureMessage(host: host, sender: "", message: "INR 99.00 spent on card ending 4321 at TEST CAFE. Ref \(ref())")
        XCTAssertEqual(masked.code, "CAPTURED")
        let named = try await IosCapture.shared.captureMessage(host: host, sender: nil, message: bank())
        XCTAssertEqual(named.code, "CAPTURED")
        XCTAssertEqual(named.pending, before + 2)
    }

    func testTheCaptureIntentRunsWithNoScreenAndAnswersInWords() async throws {
        var intent = CaptureBankMessageIntent()
        intent.message = bank()
        intent.sender = "VM-HDFCBK-S"
        _ = try await intent.perform()
        var again = CaptureBankMessageIntent()
        again.message = "Your OTP is 123456. -HDFC Bank"
        again.sender = "VM-HDFCBK-S"
        _ = try await again.perform()
    }

    func testAddExpenseRecordsOrRefusesInWords() async throws {
        let ok = try await IosCapture.shared.addExpense(host: host, amountText: "₹12.50", note: "Test tea", categoryName: nil)
        XCTAssertTrue(ok.added, ok.message)
        XCTAssertTrue(ok.message.hasPrefix("Added ₹12.50"), ok.message)
        let named = try await IosCapture.shared.addExpense(host: host, amountText: "Rs. 99", note: nil, categoryName: "food")
        XCTAssertTrue(named.added, named.message)
        XCTAssertTrue(named.message.hasSuffix("to Food"), named.message)
        let unknown = try await IosCapture.shared.addExpense(host: host, amountText: "10", note: nil, categoryName: "No such category")
        XCTAssertFalse(unknown.added)
        let bad = try await IosCapture.shared.addExpense(host: host, amountText: "12.345", note: nil, categoryName: nil)
        XCTAssertFalse(bad.added)
    }

    func testTheClipboardIsReadOnlyWhenAskedAndOnlyIfItHoldsText() {
        let platform = IosPlatform(host: host)
        UIPasteboard.general.string = "Rs.1.00 debited from a/c XX1234 -HDFC Bank"
        XCTAssertEqual(platform.readClipboardText(), "Rs.1.00 debited from a/c XX1234 -HDFC Bank")
        UIPasteboard.general.string = "   "
        XCTAssertNil(platform.readClipboardText())
        XCTAssertTrue(platform.supportsShortcutsCapture)
    }
}
