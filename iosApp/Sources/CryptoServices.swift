import CommonCrypto
import CryptoKit
import Foundation
import Security
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

    // ---- the Keychain: the key of passphrase-protected automatic copies (IosSecretStore) ----

    private static let keychainService = "app.expenses.tracker.ios.secrets"

    private func keychainQuery(_ name: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: Self.keychainService, kSecAttrAccount as String: name]
    }

    /// Readable after the first unlock, so a background refresh can write a copy while the phone is locked; this device only (never in a backup or iCloud Keychain).
    /// Updates an item that is there and adds one that is not: nothing is deleted first, so a failed write leaves the old value.
    func secretPut(name: String, data: Data) -> Int32 {
        let attributes: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        var status = SecItemUpdate(keychainQuery(name) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(keychainQuery(name).merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        return status
    }

    func secretGet(name: String) -> Data? {
        var query = keychainQuery(name)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? Data
    }

    func secretStatus(name: String) -> Int32 {
        SecItemCopyMatching(keychainQuery(name) as CFDictionary, nil)
    }

    func secretDelete(name: String) -> Bool {
        let status = SecItemDelete(keychainQuery(name) as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    func unseal(key: Data, nonce: Data, aad: Data, sealed: Data) -> Data? {
        guard sealed.count >= 16, let nonce = try? AES.GCM.Nonce(data: nonce) else { return nil }
        let cut = sealed.count - 16
        let ciphertext = sealed.prefix(cut)
        let tag = sealed.suffix(16)
        guard let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag) else { return nil }
        return try? AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: aad)
    }
}
