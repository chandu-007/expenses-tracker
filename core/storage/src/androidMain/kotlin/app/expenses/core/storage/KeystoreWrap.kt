package app.expenses.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Android Keystore wrapping both the database key ([EncryptedDriver]) and the key of automatic copies ([KeystoreSecretStore]): an AES-256-GCM key that never
 * leaves the Keystore (hardware-backed where the phone has it) encrypts the secret, and only that encrypted blob is written to a file. The blob is
 * `ivLength (1 byte) + iv + ciphertext and tag`.
 */
internal object KeystoreWrap {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_BYTES = 16

    fun existingKey(alias: String): SecretKey? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(alias, null) as? SecretKey

    fun newKey(alias: String): SecretKey {
        existingKey(alias)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    fun deleteKey(alias: String) {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias) }
    }

    fun wrap(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    /**
     * A blob too short to hold its length byte, the iv it names and the 16-byte tag (an empty or cut-short file, as a power cut left them before [DurableFiles]) is a
     * [javax.crypto.AEADBadTagException], like any other blob the key does not open, so callers treat it as unreadable rather than as a passing failure.
     */
    fun unwrap(key: SecretKey, blob: ByteArray): ByteArray {
        val ivLen = if (blob.isEmpty()) 0 else blob[0].toInt()
        if (ivLen <= 0 || blob.size < 1 + ivLen + GCM_TAG_BYTES) throw javax.crypto.AEADBadTagException("Malformed wrapped blob (${blob.size} bytes)")
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return cipher.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }
}
