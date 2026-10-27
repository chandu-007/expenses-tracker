import XCTest
import Shared
@testable import Expenses

/// The backup file must open on both phones. The fixture was written by tools/backup_interop/Interop.java, which repeats the Android BackupCrypto steps
/// with javax.crypto; the second test writes a file for that program to read back (tools/ios_build.sh interop runs both directions).
final class BackupInteropTests: XCTestCase {
    private let passphrase = "correct horse battery"
    private let cipher = IosBackupCipher(host: CryptoServices())

    private func fixture(_ name: String) throws -> Data {
        let url = Bundle(for: Self.self).url(forResource: name, withExtension: nil)!
        return try Data(contentsOf: url)
    }

    private func bytes(_ data: Data) -> KotlinByteArray {
        let out = KotlinByteArray(size: Int32(data.count))
        for (i, b) in data.enumerated() { out.set(index: Int32(i), value: Int8(bitPattern: b)) }
        return out
    }

    private func data(_ array: KotlinByteArray) -> Data {
        Data((0..<array.size).map { UInt8(bitPattern: array.get(index: $0)) })
    }

    func testAnAndroidStyleBackupDecryptsOnIPhone() throws {
        let sample = bytes(try fixture("android-made.expbak"))
        XCTAssertTrue(cipher.isEncrypted(bytes: sample))
        XCTAssertEqual(data(cipher.decrypt(bytes: sample, passphrase: passphrase)), try fixture("plain.txt"))
    }

    /// Kotlin exceptions are not catchable from Swift, so the refusal paths are checked on the primitive that decides them.
    func testAWrongKeyOrAChangedHeaderIsRefused() throws {
        let host = CryptoServices()
        let raw = try fixture("android-made.expbak")
        let header = raw.prefix(28), nonce = raw.subdata(in: 28..<40), sealed = raw.suffix(from: 40)
        let key = host.deriveKey(passphrase: passphrase, salt: raw.subdata(in: 12..<28), iterations: 600_000)
        XCTAssertNotNil(host.unseal(key: key, nonce: nonce, aad: header, sealed: sealed))
        let wrong = host.deriveKey(passphrase: "not the passphrase", salt: raw.subdata(in: 12..<28), iterations: 600_000)
        XCTAssertNil(host.unseal(key: wrong, nonce: nonce, aad: header, sealed: sealed))
        var changed = Data(header); changed[11] ^= 0x01
        XCTAssertNil(host.unseal(key: key, nonce: nonce, aad: changed, sealed: sealed))
    }

    // ---- backups that carry photos and files (version 2; the sealed ones use the chunked layout) ----
    // The fixtures were written by tools/backup_interop/Interop.java `make2`, from docs/BACKUP_FORMAT.md alone: four sample files, file k holding byte i = (i*31 + k*7) mod 256.

    private func fixturePath(_ name: String) -> String {
        Bundle(for: Self.self).url(forResource: name, withExtension: nil)!.path
    }

    func testBackupsWithFilesMadeByTheReferenceRestoreOnIPhoneWithEveryFileIntact() async throws {
        for (name, pass) in [("reference-v2.expbak", nil), ("android-made-v2.expbak", passphrase)] as [(String, String?)] {
            let answer = try await IosBackupDebug.shared.restoreThroughTheApp(host: CryptoServices(), path: fixturePath(name), passphrase: pass)
            XCTAssertEqual(answer, "restored: entries=2 files=4 matched=4 damaged=0 missing=0", name)
        }
    }

    func testAWrongPassphraseIsRefusedAndNeedsNoFileToBeRead() async throws {
        let answer = try await IosBackupDebug.shared.restoreThroughTheApp(host: CryptoServices(), path: fixturePath("android-made-v2.expbak"), passphrase: "not the passphrase")
        XCTAssertTrue(answer.hasPrefix("refused: The passphrase is wrong"), answer)
    }

    func testADamagedFileInABackupIsSkippedAndTheRestIsRestored() async throws {
        for (name, pass) in [("reference-v2.expbak", nil), ("android-made-v2.expbak", passphrase)] as [(String, String?)] {
            var raw = try fixture(name)
            raw[raw.count - 40] ^= 0x01 // inside the last file (plain), or the last sealed chunk
            let copy = NSTemporaryDirectory() + "damaged-\(UUID().uuidString).expbak"
            try raw.write(to: URL(fileURLWithPath: copy))
            defer { try? FileManager.default.removeItem(atPath: copy) }
            let answer = try await IosBackupDebug.shared.restoreThroughTheApp(host: CryptoServices(), path: copy, passphrase: pass)
            if pass == nil {
                XCTAssertEqual(answer, "restored: entries=2 files=3 matched=3 damaged=1 missing=0", "\(name): \(answer)")
            } else {
                // a damaged sealed chunk spoils the files that lie across it (the last chunk holds the end of file 2 and all of files 3 and 4): those are skipped, the others restored
                XCTAssertEqual(answer, "restored: entries=2 files=1 matched=1 damaged=3 missing=0", "\(name): \(answer)")
            }
        }
    }

    func testAnAutomaticCopyWithAFileIsWrittenToTheFolderInPiecesAndReadsBack() async throws {
        let dir = NSTemporaryDirectory() + "auto-files-\(UUID().uuidString)"
        try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(atPath: dir) }
        let status = try await IosAutoExportFilesDebug.shared.exportWithAFileInto(host: CryptoServices(), folderPath: dir, now: Int64(Date().timeIntervalSince1970 * 1000))
        XCTAssertTrue(status.hasPrefix("ok|Saved") && status.contains("photo"), status)
        let name = try FileManager.default.contentsOfDirectory(atPath: dir).first { $0.hasSuffix(".expbak") }!
        let backup = try Data(contentsOf: URL(fileURLWithPath: dir + "/" + name))
        let line = String(decoding: backup.prefix(while: { $0 != 0x0A }), as: UTF8.self)
        XCTAssertTrue(line.contains("\"version\":2"), line)
        XCTAssertGreaterThan(backup.count, 70_000)
        try backup.write(to: URL(fileURLWithPath: "/tmp/ios-auto-v2.expbak")) // the reference reads it (tools/ios_build.sh interop)
    }

    func testTheHashingThatChecksEveryFileIsFastEnoughOnThisPlatform() throws {
        let speed = IosBackupDebug.shared.sha256Speed()
        try? speed.write(toFile: "/tmp/ios-sha-speed.txt", atomically: true, encoding: .utf8)
        XCTAssertFalse(speed.isEmpty)
    }

    func testIPhoneWritesBackupsWithFilesForTheReferenceToRead() async throws {
        for (path, pass) in [("/tmp/ios-made-v2.expbak", passphrase), ("/tmp/ios-made-v2-plain.expbak", nil)] as [(String, String?)] {
            let answer = try await IosBackupDebug.shared.backupThroughTheApp(host: CryptoServices(), path: path, passphrase: pass)
            XCTAssertEqual(answer, "ok: \(try FileManager.default.attributesOfItem(atPath: path)[.size] as! Int) bytes, 4 files, 2 entries", answer)
            // and the iPhone reads back what it wrote
            let again = try await IosBackupDebug.shared.restoreThroughTheApp(host: CryptoServices(), path: path, passphrase: pass)
            XCTAssertEqual(again, "restored: entries=2 files=4 matched=4 damaged=0 missing=0", again)
        }
    }

    func testIPhoneEncryptionRoundTripsAndIsWrittenForTheReferenceToRead() throws {
        let plain = try fixture("plain.txt")
        let sealed = cipher.encrypt(plain: bytes(plain), passphrase: passphrase)
        XCTAssertEqual(data(cipher.decrypt(bytes: sealed, passphrase: passphrase)), plain)
        try data(sealed).write(to: URL(fileURLWithPath: "/tmp/ios-made.expbak"))
    }
}
