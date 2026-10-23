package app.expenses

import platform.Foundation.NSData

/**
 * What the Swift host provides because only Swift can reach it (CryptoKit's AES-GCM and CommonCrypto's PBKDF2 are not in Kotlin/Native's
 * platform libraries). The backup file layout itself is written once, in Kotlin ([IosBackupCipher]); these are only the primitives, so the
 * format cannot drift between the two languages. Implemented in iosApp/Sources/CryptoServices.swift.
 */
interface IosHostServices {
    /** PBKDF2-HMAC-SHA256 of the passphrase's UTF-8 bytes, 32 bytes long. */
    fun deriveKey(passphrase: String, salt: NSData, iterations: Int): NSData

    /** AES-256-GCM with a 12-byte [nonce] and [aad] authenticated: the ciphertext followed by the 16-byte tag. */
    fun seal(key: NSData, nonce: NSData, aad: NSData, plain: NSData): NSData

    /** The inverse of [seal], or null when the tag does not match (wrong key or damaged data). */
    fun unseal(key: NSData, nonce: NSData, aad: NSData, sealed: NSData): NSData?

    /** Asks the system to redraw the Home Screen widgets from what the app just wrote ([IosWidgetData]); WidgetKit is Swift-only. */
    fun reloadWidgets()
}
