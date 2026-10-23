import XCTest
import Shared
@testable import Expenses

/// Automatic copies into a folder: a backup and an Excel file, read back, only the newest seven of each kept.
final class AutoExportTests: XCTestCase {
    private let host = CryptoServices()

    func testACopyWritesABackupAndAnExcelFileAndOldOnesAreTrimmed() async throws {
        let dir = NSTemporaryDirectory() + "auto-export-\(UUID().uuidString)"
        try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(atPath: dir) }
        _ = try await IosCapture.shared.addExpense(host: host, amountText: "75", note: "export test", categoryName: nil)
        // nine days in a row: each day's files are new, and after the ninth only the newest seven of each kind are left
        let start = Int64(Date().timeIntervalSince1970 * 1000)
        var status = ""
        for day in 0..<9 { status = try await IosAutoExportDebug.shared.exportInto(host: host, folderPath: dir, now: start + Int64(day) * 86_400_000) }
        XCTAssertTrue(status.hasPrefix("ok|Saved"), status)
        let names = try FileManager.default.contentsOfDirectory(atPath: dir)
        XCTAssertEqual(names.filter { $0.hasSuffix(".expbak") }.count, 7, "\(names)")
        XCTAssertEqual(names.filter { $0.hasSuffix(".xlsx") }.count, 7, "\(names)")
        let backup = try Data(contentsOf: URL(fileURLWithPath: dir + "/" + names.first { $0.hasSuffix(".expbak") }!))
        XCTAssertGreaterThan(backup.count, 100)
    }

    func testWithNoFolderTheAnswerIsPlainAndNothingIsWritten() async throws {
        let answer = try await IosAutoExportDebug.shared.exportInto(host: host, folderPath: "/nonexistent-folder-for-test", now: Int64(Date().timeIntervalSince1970 * 1000))
        XCTAssertTrue(answer.hasPrefix("fail|"), answer)
    }
}
