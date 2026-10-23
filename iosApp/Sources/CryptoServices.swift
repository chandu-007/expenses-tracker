import CommonCrypto
import CryptoKit
import Foundation
import Shared
import WidgetKit

/// The crypto primitives behind passphrase-protected backups. The file layout is written once, in Kotlin (IosBackupCipher), identical to the Android
/// BackupCrypto; this only does PBKDF2-HMAC-SHA256 and AES-256-GCM.
final class CryptoServices: NSObject, IosHostServices {
    func deriveKey(passphrase: String, salt: Data, iterations: Int32) -> Data {
        let password = Array(passphrase.utf8)
        var key = [UInt8](repeating: 0, count: 32)
        let status = password.withUnsafeBufferPointer { pw in
            salt.withUnsafeBytes { s in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    UnsafeRawPointer(pw.baseAddress!).assumingMemoryBound(to: Int8.self), password.count,
                    s.bindMemory(to: UInt8.self).baseAddress, salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), UInt32(iterations),
                    &key, key.count)
            }
        }
        precondition(status == kCCSuccess, "PBKDF2 failed")
        return Data(key)
    }

    func seal(key: Data, nonce: Data, aad: Data, plain: Data) -> Data {
        let box = try! AES.GCM.seal(plain, using: SymmetricKey(data: key), nonce: AES.GCM.Nonce(data: nonce), authenticating: aad)
        return box.ciphertext + box.tag
    }

    func reloadWidgets() { WidgetCenter.shared.reloadAllTimelines() }

    func unseal(key: Data, nonce: Data, aad: Data, sealed: Data) -> Data? {
        guard sealed.count >= 16, let nonce = try? AES.GCM.Nonce(data: nonce) else { return nil }
        let cut = sealed.count - 16
        let ciphertext = sealed.prefix(cut)
        let tag = sealed.suffix(16)
        guard let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag) else { return nil }
        return try? AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: aad)
    }
}
