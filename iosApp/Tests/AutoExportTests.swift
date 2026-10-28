import XCTest
import Shared
@testable import Expenses

/// Automatic copies into a folder: a backup and an Excel file, read back, only the newest seven of each kept; and, when the person turned it on, the backup sealed with
/// a passphrase through the Keychain.
final class AutoExportTests: XCTestCase {
    private let host = CryptoServices()
    private let pass = "correct horse battery"
    private let keyName = "auto_copy_key"

    /// Every test starts and ends with protection off (the app's data outlives a test run on the simulator), so the plain tests never meet a protected setting.
    override func setUp() async throws { _ = try await IosAutoProtectDebug.shared.set(host: host, passphrase: nil) }
    override func tearDown() async throws { _ = try await IosAutoProtectDebug.shared.set(host: host, passphrase: nil) }

    private func newFolder() throws -> String {
        let dir = NSTemporaryDirectory() + "auto-protect-\(UUID().uuidString)"
        try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(atPath: dir) }
        return dir
    }

    private func names(_ dir: String) throws -> [String] { try FileManager.default.contentsOfDirectory(atPath: dir).sorted() }

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

    // ---- the Keychain, as the key of protected copies uses it ----

    func testTheKeychainKeepsReplacesAndForgetsAKeyAndAnswersNothingForOneThatIsNotThere() {
        let name = "unit_test_secret_\(UUID().uuidString.prefix(8).lowercased())"
        defer { _ = host.secretDelete(name: name) }
        XCTAssertNil(host.secretGet(name: name), "nothing there yet")
        XCTAssertEqual(host.secretStatus(name: name), -25300, "errSecItemNotFound: there is no key, which is not the same as one that cannot be read yet")
        XCTAssertEqual(host.secretPut(name: name, data: Data([1, 2, 3])), 0, "the Keychain accepted it")
        XCTAssertEqual(host.secretGet(name: name), Data([1, 2, 3]))
        XCTAssertEqual(host.secretStatus(name: name), 0)
        XCTAssertEqual(host.secretPut(name: name, data: Data(repeating: 9, count: 57)), 0, "replaced in place")
        XCTAssertEqual(host.secretGet(name: name), Data(repeating: 9, count: 57))
        XCTAssertTrue(host.secretDelete(name: name))
        XCTAssertNil(host.secretGet(name: name))
        XCTAssertTrue(host.secretDelete(name: name), "removing what is not there is not an error")
    }

    // ---- protected copies ----

    func testAProtectedCopyIsSealedOpensWithJustThePassphraseAndAMissingKeyWritesAndRemovesNothing() async throws {
        let dir = try newFolder()
        _ = try await IosCapture.shared.addExpense(host: host, amountText: "75", note: "protected copy test", categoryName: nil)
        let said = try await IosAutoProtectDebug.shared.set(host: host, passphrase: pass)
        XCTAssertTrue(said.hasPrefix("Automatic copies are protected"), said)
        let kept = try XCTUnwrap(host.secretGet(name: keyName), "the key is in the Keychain")
        XCTAssertEqual(kept.count, 57, "a version byte, the rounds, the salt, the key and a 4-byte check")
        XCTAssertNil(kept.range(of: Data(pass.utf8)), "the passphrase is not what is kept")

        let start = Int64(Date().timeIntervalSince1970 * 1000)
        let status = try await IosAutoExportDebug.shared.exportInto(host: host, folderPath: dir, now: start)
        XCTAssertTrue(status.hasPrefix("ok|Saved") && status.contains("protected backup"), status)
        let made = try names(dir)
        XCTAssertEqual(made.filter { $0.hasSuffix(".expbak") }.count, 1, "\(made)")
        XCTAssertEqual(made.filter { $0.hasSuffix(".xlsx") }.count, 0, "no workbook while copies are protected: \(made)")
        XCTAssertEqual(made.filter { $0.hasSuffix(".part") || $0.hasSuffix(".old") }.count, 0, "\(made)")

        let path = dir + "/" + (try XCTUnwrap(made.first { $0.hasSuffix(".expbak") }, "a sealed backup was written: \(status) \(made)"))
        let bytes = try Data(contentsOf: URL(fileURLWithPath: path))
        XCTAssertEqual(String(decoding: bytes.prefix(8), as: UTF8.self), "EXPENC3\n")
        XCTAssertNil(bytes.range(of: Data("protected copy test".utf8)), "the entries are not readable in the file")
        XCTAssertEqual(kept.subdata(in: 1..<5).reduce(0) { ($0 << 8) | Int($1) }, 600_000, "the real round count")
        XCTAssertEqual(bytes.subdata(in: 12..<28), kept.subdata(in: 5..<21), "the header names the salt the key was made with")
        try bytes.write(to: URL(fileURLWithPath: "/tmp/ios-auto-v3.expbak")) // the Java reference reads it (tools/ios_build.sh interop)

        // restored through "Choose a backup file": it asks for the passphrase, a wrong one is refused, the right one opens it
        let open = IosAutoProtectDebug.shared
        let first = try await open.openThroughTheApp(host: host, path: path, passphrase: nil)
        XCTAssertEqual(first, "needs a passphrase")
        let wrong = try await open.openThroughTheApp(host: host, path: path, passphrase: "wrong passphrase!")
        XCTAssertTrue(wrong.hasPrefix("refused: The passphrase is wrong"), wrong)
        let right = try await open.openThroughTheApp(host: host, path: path, passphrase: pass)
        XCTAssertTrue(right.hasPrefix("ready: entries="), right)

        // a phone that lost the key while protection is on: nothing is written, nothing is removed, and the sentence says what to do
        for day in 1...8 { try Data("older copy \(day)".utf8).write(to: URL(fileURLWithPath: dir + "/expenses-backup-2001-01-0\(day).expbak")) }
        let before = try names(dir)
        IosAutoProtectDebug.shared.forgetKey(host: host)
        XCTAssertNil(host.secretGet(name: keyName))
        let failed = try await IosAutoExportDebug.shared.exportInto(host: host, folderPath: dir, now: start + 86_400_000)
        XCTAssertEqual(failed, "fail|No protection key on this phone: set the passphrase again.")
        XCTAssertEqual(try names(dir), before, "no copy written, none pruned")

        // setting the passphrase again puts it right, and the next run prunes as usual
        _ = try await IosAutoProtectDebug.shared.set(host: host, passphrase: pass)
        let again = try await IosAutoExportDebug.shared.exportInto(host: host, folderPath: dir, now: start + 2 * 86_400_000)
        XCTAssertTrue(again.hasPrefix("ok|Saved") && again.contains("protected backup"), again)
        XCTAssertEqual(try names(dir).filter { $0.hasSuffix(".expbak") }.count, 7)
    }

    func testAProtectedCopyWithAFileIsWrittenInPiecesCheckedAndRestorableByTheReference() async throws {
        let dir = try newFolder()
        _ = try await IosAutoProtectDebug.shared.set(host: host, passphrase: pass)
        let status = try await IosAutoExportFilesDebug.shared.exportWithAFileInto(host: host, folderPath: dir, now: Int64(Date().timeIntervalSince1970 * 1000))
        XCTAssertTrue(status.hasPrefix("ok|Saved") && status.contains("protected backup") && status.contains("photo"), status)
        let made = try names(dir)
        XCTAssertEqual(made.count, 1, "\(made)")
        let only = try XCTUnwrap(made.first, "a copy was written: \(status)")
        let bytes = try Data(contentsOf: URL(fileURLWithPath: dir + "/" + only))
        XCTAssertEqual(String(decoding: bytes.prefix(8), as: UTF8.self), "EXPENC3\n")
        XCTAssertGreaterThan(bytes.count, 70_000)
        try bytes.write(to: URL(fileURLWithPath: "/tmp/ios-auto-v3-files.expbak")) // the Java reference reads it (tools/ios_build.sh interop)
        let open = try await IosAutoProtectDebug.shared.openThroughTheApp(host: host, path: dir + "/" + only, passphrase: pass)
        XCTAssertTrue(open.hasPrefix("ready: entries=") && open.hasSuffix("version=2 files=1"), open)
    }

    func testChangingThePassphraseMakesANewSaltAndOldCopiesKeepTheOldPassphrase() async throws {
        let dir = try newFolder()
        let newPass = "another good passphrase"
        let start = Int64(Date().timeIntervalSince1970 * 1000)
        _ = try await IosAutoProtectDebug.shared.set(host: host, passphrase: pass)
        let oldKey = try XCTUnwrap(host.secretGet(name: keyName))
        let first = try await IosAutoExportDebug.shared.exportInto(host: host, folderPath: dir, now: start)
        XCTAssertTrue(first.hasPrefix("ok|Saved") && first.contains("protected backup"), first)
        let said = try await IosAutoProtectDebug.shared.set(host: host, passphrase: newPass)
        XCTAssertTrue(said.hasPrefix("The passphrase for automatic copies is changed"), said)
        let newKey = try XCTUnwrap(host.secretGet(name: keyName))
        XCTAssertNotEqual(oldKey.subdata(in: 5..<21), newKey.subdata(in: 5..<21), "a new salt")
        XCTAssertNotEqual(oldKey.subdata(in: 21..<53), newKey.subdata(in: 21..<53), "a new key")
        let second = try await IosAutoExportDebug.shared.exportInto(host: host, folderPath: dir, now: start + 86_400_000)
        XCTAssertTrue(second.hasPrefix("ok|Saved") && second.contains("protected backup"), second)
        let copies = try names(dir).filter { $0.hasSuffix(".expbak") }
        XCTAssertEqual(copies.count, 2, "\(copies) \(first) \(second)")
        guard copies.count == 2 else { return }
        let old = dir + "/" + copies[0], fresh = dir + "/" + copies[1]
        let tool = IosAutoProtectDebug.shared
        let a = try await tool.openThroughTheApp(host: host, path: old, passphrase: pass)
        let b = try await tool.openThroughTheApp(host: host, path: old, passphrase: newPass)
        let c = try await tool.openThroughTheApp(host: host, path: fresh, passphrase: newPass)
        let d = try await tool.openThroughTheApp(host: host, path: fresh, passphrase: pass)
        XCTAssertTrue(a.hasPrefix("ready:"), a); XCTAssertTrue(b.hasPrefix("refused: The passphrase is wrong"), b)
        XCTAssertTrue(c.hasPrefix("ready:"), c); XCTAssertTrue(d.hasPrefix("refused: The passphrase is wrong"), d)
    }

    func testAPassphraseThatIsTooShortIsRefusedAndNothingIsKept() async throws {
        let said = try await IosAutoProtectDebug.shared.set(host: host, passphrase: "short")
        XCTAssertTrue(said.hasPrefix("refused: The passphrase must be at least"), said)
        XCTAssertNil(host.secretGet(name: keyName))
    }
}
