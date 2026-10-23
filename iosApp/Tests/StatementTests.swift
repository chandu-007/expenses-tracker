import PDFKit
import UIKit
import XCTest
import Shared
@testable import Expenses

/// Statement import on the phone: a PDF read with PDFKit (with and without a password) and a picture read with Vision, then understood by the same parser as
/// on Android. The statements are drawn here with made-up lines; a real bank's layout was checked separately (and is not in the repository).
final class StatementTests: XCTestCase {
    private let lines = [
        "Statement of account",
        "12/09/2026 SWIGGY BANGALORE 450.50 Dr",
        "13/09/2026 SALARY ACME CORP 50000.00 Cr",
        "15/09/2026 AMAZON PAY INDIA MUMBAI 1299.00 Dr",
    ]

    private func pdf(password: String? = nil) -> Data {
        let format = UIGraphicsPDFRendererFormat()
        if let password {
            format.documentInfo = [kCGPDFContextUserPassword as String: password, kCGPDFContextOwnerPassword as String: password]
        }
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        return UIGraphicsPDFRenderer(bounds: page, format: format).pdfData { ctx in
            ctx.beginPage()
            let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 14)]
            for (i, line) in lines.enumerated() { (line as NSString).draw(at: CGPoint(x: 40, y: 60 + CGFloat(i) * 28), withAttributes: attrs) }
        }
    }

    private func picture() -> Data {
        let size = CGSize(width: 1000, height: 400)
        return UIGraphicsImageRenderer(size: size).pngData { ctx in
            UIColor.white.setFill(); ctx.fill(CGRect(origin: .zero, size: size))
            let attrs: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 36), .foregroundColor: UIColor.black]
            for (i, line) in lines.enumerated() { (line as NSString).draw(at: CGPoint(x: 30, y: 30 + CGFloat(i) * 80), withAttributes: attrs) }
        }
    }

    private func transactions(_ answer: String) -> [String] { answer.components(separatedBy: "\n").dropFirst().map { String($0) } }

    func testATextPdfIsReadIntoDatedTransactionsWithTheirDirection() {
        let answer = IosStatementDebug.shared.pdf(data: pdf(), password: nil)
        XCTAssertTrue(answer.hasPrefix("OK recognised=false"), answer)
        XCTAssertEqual(transactions(answer), [
            "12/09/2026 | SWIGGY BANGALORE | 450.50 | Dr",
            "13/09/2026 | SALARY ACME CORP | 50000.00 | Cr",
            "15/09/2026 | AMAZON PAY INDIA MUMBAI | 1299.00 | Dr",
        ])
    }

    func testAPasswordProtectedPdfAsksForItsPasswordAndAWrongOneIsRefused() {
        let locked = pdf(password: "ASHA1990")
        XCTAssertEqual(IosStatementDebug.shared.pdf(data: locked, password: nil), "PASSWORD_NEEDED")
        XCTAssertEqual(IosStatementDebug.shared.pdf(data: locked, password: "wrong"), "WRONG_PASSWORD")
        let answer = IosStatementDebug.shared.pdf(data: locked, password: "ASHA1990")
        XCTAssertTrue(answer.hasPrefix("OK"), answer)
        XCTAssertEqual(transactions(answer).count, 3, answer)
    }

    func testAPictureOfAStatementIsReadOnThePhoneIntoTheSameTransactions() {
        let answer = IosStatementDebug.shared.picture(data: picture())
        XCTAssertTrue(answer.hasPrefix("OK recognised=true"), answer)
        XCTAssertEqual(transactions(answer), [
            "12/09/2026 | SWIGGY BANGALORE | 450.50 | Dr",
            "13/09/2026 | SALARY ACME CORP | 50000.00 | Cr",
            "15/09/2026 | AMAZON PAY INDIA MUMBAI | 1299.00 | Dr",
        ])
    }

    func testAnythingThatIsNotAPictureIsRefusedInWords() {
        let answer = IosStatementDebug.shared.picture(data: Data([1, 2, 3, 4]))
        XCTAssertTrue(answer.hasPrefix("UNREADABLE"), answer)
    }

    func testADamagedPdfIsRefusedInWordsAndTheAppStaysUp() {
        for bytes in [Data("%PDF-1.4\n garbage".utf8), Data("%PDF-".utf8), Data()] {
            let answer = IosStatementDebug.shared.pdf(data: bytes, password: nil)
            XCTAssertTrue(answer.hasPrefix("UNREADABLE"), answer)
        }
    }
}

