package app.expenses

import app.expenses.core.storage.BackupException
import app.expenses.core.storage.SealPrimitives

/**
 * The same file layout as the Android BackupCrypto, so a backup made on either phone opens on the other:
 * `EXPENC1\n`, iteration count (4 bytes, big-endian), salt (16), nonce (12), then AES-256-GCM ciphertext with its 16-byte tag, the key being
 * PBKDF2-HMAC-SHA256 of the passphrase's UTF-8 bytes and everything before the nonce being authenticated data. The primitives come from the
 * Swift host ([IosHostServices]).
 */
class IosBackupCipher(private val host: IosHostServices) : BackupCipher {
    override val minPassphrase get() = MIN_PASSPHRASE

    /** The primitives of the streaming layout for backups with files ([app.expenses.core.storage.BackupSeal]), from the same Swift host as above. */
    override val primitives: SealPrimitives = object : SealPrimitives {
        override fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray = host.deriveKey(passphrase, salt.toNSData(), iterations).toByteArray()
        override fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray = host.seal(key.toNSData(), nonce.toNSData(), aad.toNSData(), plain.toNSData()).toByteArray()
        override fun unseal(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray? = host.unseal(key.toNSData(), nonce.toNSData(), aad.toNSData(), sealed.toNSData())?.toByteArray()
        override fun randomBytes(count: Int): ByteArray = secureRandom(count)
    }

    override fun isEncrypted(bytes: ByteArray) = bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    override fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        require(passphrase.length >= MIN_PASSPHRASE) { "The passphrase must be at least $MIN_PASSPHRASE characters" }
        val salt = secureRandom(SALT_BYTES)
        val nonce = secureRandom(NONCE_BYTES)
        val header = MAGIC + intBytes(ITERATIONS) + salt
        val key = host.deriveKey(passphrase, salt.toNSData(), ITERATIONS)
        return header + nonce + host.seal(key, nonce.toNSData(), header.toNSData(), plain.toNSData()).toByteArray()
    }

    override fun decrypt(bytes: ByteArray, passphrase: String): ByteArray {
        val headerSize = MAGIC.size + 4 + SALT_BYTES
        if (!isEncrypted(bytes) || bytes.size < headerSize + NONCE_BYTES + TAG_BYTES) throw BackupException("This is not a backup file from this app.")
        val iterations = ((bytes[MAGIC.size].toInt() and 0xFF) shl 24) or ((bytes[MAGIC.size + 1].toInt() and 0xFF) shl 16) or
            ((bytes[MAGIC.size + 2].toInt() and 0xFF) shl 8) or (bytes[MAGIC.size + 3].toInt() and 0xFF)
        if (iterations !in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) throw BackupException("This backup file is damaged.")
        val header = bytes.copyOfRange(0, headerSize)
        val salt = bytes.copyOfRange(MAGIC.size + 4, headerSize)
        val nonce = bytes.copyOfRange(headerSize, headerSize + NONCE_BYTES)
        val sealed = bytes.copyOfRange(headerSize + NONCE_BYTES, bytes.size)
        val key = host.deriveKey(passphrase, salt.toNSData(), iterations)
        val plain = host.unseal(key, nonce.toNSData(), header.toNSData(), sealed.toNSData()) ?: throw BackupException("The passphrase is wrong, or the file is damaged.")
        return plain.toByteArray()
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    companion object {
        private val MAGIC = "EXPENC1\n".encodeToByteArray()
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        const val ITERATIONS = 600_000
        const val MIN_PASSPHRASE = 8
        private const val MIN_ACCEPTED_ITERATIONS = 100_000
        private const val MAX_ACCEPTED_ITERATIONS = 5_000_000
    }
}
