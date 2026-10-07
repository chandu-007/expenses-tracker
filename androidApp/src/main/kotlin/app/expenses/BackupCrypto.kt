package app.expenses

import app.expenses.core.storage.BackupException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional passphrase protection for a backup file, so it is safe to keep on a cloud drive or send by mail.
 *
 * Layout: `EXPENC1\n`, iteration count (4 bytes), salt (16), nonce (12), then AES-256-GCM ciphertext with its 16-byte tag. The key is
 * PBKDF2-HMAC-SHA256 of the passphrase. Everything before the ciphertext is authenticated as well, so changing the iteration count or
 * salt makes decryption fail instead of quietly weakening it. A wrong passphrase and a damaged file are indistinguishable by design
 * and both report that. There is no way to recover a forgotten passphrase.
 */
object BackupCrypto {
    private val MAGIC = "EXPENC1\n".encodeToByteArray()
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    const val ITERATIONS = 600_000
    const val MIN_PASSPHRASE = 8

    /** Refuse counts that no version of the app wrote: a tiny count would weaken the key, a huge one would freeze the phone. */
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 5_000_000

    fun isEncrypted(bytes: ByteArray) = bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, passphrase: String, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.length >= MIN_PASSPHRASE) { "The passphrase must be at least $MIN_PASSPHRASE characters" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = header(ITERATIONS, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(passphrase, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
        return header + nonce + cipher.doFinal(plain)
    }

    /** The original bytes, or a [BackupException] saying the passphrase is wrong or the file is damaged. */
    fun decrypt(bytes: ByteArray, passphrase: String): ByteArray {
        val headerSize = MAGIC.size + 4 + SALT_BYTES
        if (!isEncrypted(bytes) || bytes.size < headerSize + NONCE_BYTES + TAG_BITS / 8) throw BackupException("This is not a backup file from this app.")
        val iterations = ByteBuffer.wrap(bytes, MAGIC.size, 4).int
        if (iterations !in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) throw BackupException("This backup file is damaged.")
        val header = bytes.copyOfRange(0, headerSize)
        val salt = bytes.copyOfRange(MAGIC.size + 4, headerSize)
        val nonce = bytes.copyOfRange(headerSize, headerSize + NONCE_BYTES)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(header)
            }.doFinal(bytes, headerSize + NONCE_BYTES, bytes.size - headerSize - NONCE_BYTES)
        } catch (e: GeneralSecurityException) {
            throw BackupException("The passphrase is wrong, or the file is damaged.")
        }
    }

    private fun header(iterations: Int, salt: ByteArray) = MAGIC + ByteBuffer.allocate(4).putInt(iterations).array() + salt

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
