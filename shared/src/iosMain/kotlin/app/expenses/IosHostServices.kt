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

    /**
     * Keychain, for the key of passphrase-protected automatic copies ([IosSecretStore]): a generic password item named [name] that is readable after the first unlock
     * (so a background refresh can make a copy while the phone is locked) and never leaves this device (not in a backup, not in iCloud Keychain). Replaces an item that is
     * there without ever deleting it first, so a failed replacement leaves the old value. 0 when it is kept, else the Keychain's OSStatus (for the message).
     */
    fun secretPut(name: String, data: NSData): Int

    /** The item's value, or null when there is none or the Keychain cannot give it (the phone has not been unlocked since it started, the item is gone). */
    fun secretGet(name: String): NSData?

    /** The Keychain's OSStatus for looking the item up: 0 when it is there, -25300 (errSecItemNotFound) when it is not, -25308 (errSecInteractionNotAllowed) before the first unlock after a restart, else another error. */
    fun secretStatus(name: String): Int

    /** Removes the item; true when it is gone (also when it was not there). */
    fun secretDelete(name: String): Boolean
}
