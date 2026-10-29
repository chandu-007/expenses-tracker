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

    func testAnSbiUpiTextWithNoRupeeSignIsCapturedWithOrWithoutASender() async throws {
        let text = "Dear UPI user A/C X8968 debited by 349.00 on date 18Oct26 trf to MYJIO Refno \(ref()) If not u? call-1800111109 for other services-18001234-SBI"
        let before = try await IosCapture.shared.pendingCount(host: host).int64Value
        let a = try await IosCapture.shared.captureMessage(host: host, sender: "AX-SBIUPI-S", message: text)
        XCTAssertEqual(a.code, "CAPTURED")
        let pasted = try await IosCapture.shared.captureMessage(host: host, sender: nil, message: text.replacingOccurrences(of: "MYJIO", with: "MYJIO2"))
        XCTAssertEqual(pasted.code, "CAPTURED", "pasted with no sender, the words still say it is SBI's")
        XCTAssertEqual(pasted.pending, before + 2)
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

    // 0.7.1: this Jio text (invented phone number) reached the iPhone as a debit in To review. It is a bill reminder: money still owed, never money that moved.
    func testABillReminderTextIsABillNotADebit() async throws {
        let amount = "\(Int.random(in: 1000...99999)).\(Int.random(in: 10...99))" // new each run: the same bill twice is one bill, on a ledger that keeps earlier runs
        let text = "Your bill dated 21-10-2026 for JioHome connection with JioFixedVoice Number +914000000000 is due for payment on 30-10-2026.\n" +
            "Total amount payable : Rs.\(amount).\nTo pay your bill, click www.jio.com/r/AbCdEfGhI\n" +
            "Overdue charges will be applicable, in case of delay in payment. Kindly ignore, if already paid."
        let before = try await IosCapture.shared.pendingCount(host: host).int64Value
        let first = try await IosCapture.shared.captureMessage(host: host, sender: nil, message: text)
        XCTAssertEqual(first.code, "BILL")
        XCTAssertEqual(first.message, "Captured a bill, see Bills in Expenses")
        XCTAssertTrue(first.kept)
        XCTAssertEqual(first.pending, before, "nothing waits in To review")
        let again = try await IosCapture.shared.captureMessage(host: host, sender: nil, message: text)
        XCTAssertEqual(again.code, "DUPLICATE")
        let withSender = try await IosCapture.shared.captureMessage(host: host, sender: "JD-JIOPAY-S", message: text)
        XCTAssertEqual(withSender.code, "DUPLICATE", "the same bill from its header is the same bill")
        // paying that bill is a real payment: still a pending debit
        let paid = try await IosCapture.shared.captureMessage(host: host, sender: nil, message: "Rs.\(amount) paid to Jio via UPI. Ref \(ref())")
        XCTAssertEqual(paid.code, "CAPTURED")
        XCTAssertEqual(paid.pending, before + 1)
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

/// "Share to Expenses": the Share extension queues the words, the app takes them when it comes forward.
final class SharedTextTests: XCTestCase {
    private let host = CryptoServices()
    private func ref() -> String { String((0..<12).map { _ in "0123456789".randomElement()! }) }

    func testASharedBankTextGoesToToReviewAndASharedOrdinaryTextIsThrownAway() async throws {
        let before = try await IosCapture.shared.pendingCount(host: host).int64Value
        IosSharedTextsDebug.shared.queue(text: "Rs.320.00 debited from a/c XX1234 on 18-10-26 to SHARE SHOP. Ref \(ref()). Not you? Call 1800 000 000 -HDFC Bank")
        IosSharedTextsDebug.shared.queue(text: "See you at lunch tomorrow, bring the notes")
        let taken = try await IosSharedTextsDebug.shared.drain(host: host)
        XCTAssertEqual(taken.intValue, 2)
        let after = try await IosCapture.shared.pendingCount(host: host).int64Value
        XCTAssertEqual(after, before + 1, "only the bank text is kept")
        let again = try await IosSharedTextsDebug.shared.drain(host: host)
        XCTAssertEqual(again.intValue, 0, "the queue was emptied")
    }
}

/// Own-account transfers: the two texts HDFC Bank sends for one move of money (invented digits 1234 and 5678), through the app's real message path into a scratch database.
final class OwnTransferTests: XCTestCase {
    private let host = CryptoServices()

    func testTheDebitAndTheCreditOfOneTransferArePairedWhenBothAccountsHaveTheirDigits() async throws {
        let answer = try await IosTransferDebug.shared.pairTheTwoTexts(host: host, digitsOnSavings: true)
        XCTAssertEqual(answer, "captured=2 pairs=1 from=Bank to=Savings basis=NAMED_ACCOUNT")
    }

    func testNothingIsPairedWhenTheDestinationAccountHasNoDigits() async throws {
        let answer = try await IosTransferDebug.shared.pairTheTwoTexts(host: host, digitsOnSavings: false)
        XCTAssertEqual(answer, "captured=2 pairs=0", "both texts are still captured as two items")
    }
}
