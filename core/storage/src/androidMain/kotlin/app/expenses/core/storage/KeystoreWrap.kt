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

    fun unwrap(key: SecretKey, blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return cipher.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }
}
