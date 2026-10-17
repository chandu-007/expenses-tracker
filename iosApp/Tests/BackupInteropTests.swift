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

    func testIPhoneEncryptionRoundTripsAndIsWrittenForTheReferenceToRead() throws {
        let plain = try fixture("plain.txt")
        let sealed = cipher.encrypt(plain: bytes(plain), passphrase: passphrase)
        XCTAssertEqual(data(cipher.decrypt(bytes: sealed, passphrase: passphrase)), plain)
        try data(sealed).write(to: URL(fileURLWithPath: "/tmp/ios-made.expbak"))
    }
}
