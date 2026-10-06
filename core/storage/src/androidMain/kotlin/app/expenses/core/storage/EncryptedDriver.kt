package app.expenses.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The encrypted database exists but its key cannot be recovered: the Keystore wrapping key or the wrapped
 * key file is gone or unreadable (restore to a new device, Keystore reset, lock-screen change on some
 * phones). The data cannot be opened. The app must offer recovery (restore from backup or sync, or start
 * fresh with the user's consent via [EncryptedDriver.reset]) instead of crashing or silently creating a
 * new key.
 */
class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Opens the app database encrypted with SQLCipher. The database passphrase is 32 random bytes that
 * never leave the device unwrapped: they are stored encrypted with an AES-GCM key held in the Android
 * Keystore (hardware-backed where available), so the file on disk is useless without the Keystore key.
 *
 * The database is opened through SQLCipher's own bundled SQLite, never the framework one: the system
 * SQLite on Android 8 to 10 is older than 3.24 and cannot run the upserts in the schema.
 *
 * A key is only ever created together with a brand-new database. If a database file already exists and
 * the key is missing or unreadable, [create] throws [DatabaseKeyUnavailableException].
 */
object EncryptedDriver {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "expenses_db_wrap_key"
    private const val WRAPPED_FILE = "db_key.bin"
    const val DEFAULT_NAME = "expenses.db"

    @Volatile private var loaded = false

    @Throws(DatabaseKeyUnavailableException::class)
    fun create(context: Context, name: String = DEFAULT_NAME): SqlDriver {
        loadNative()
        val passphrase = loadOrCreatePassphrase(context, name)
        return AndroidSqliteDriver(ExpensesDb.Schema, context, name, SupportOpenHelperFactory(rawKey(passphrase)))
    }

    /** Deletes the database, its wrapped key and the Keystore key. Irreversible: only after user consent. */
    fun reset(context: Context, name: String = DEFAULT_NAME) {
        context.deleteDatabase(name)
        File(context.noBackupFilesDir, WRAPPED_FILE).delete()
        File(context.noBackupFilesDir, "$WRAPPED_FILE.tmp").delete()
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    /**
     * SQLCipher's raw-key form, `x'<64 hex digits>'`. The key is already 256 random bits, so the default password
     * stretching (256,000 PBKDF2 rounds on every open) adds cost and no security. The salt still lives in the file.
     */
    private fun rawKey(key: ByteArray): ByteArray {
        require(key.size == 32) { "The database key must be 32 bytes" }
        val hex = key.joinToString("") { "%02x".format(it) }
        return "x'$hex'".toByteArray(Charsets.US_ASCII)
    }

    private fun loadNative() {
        if (!loaded) synchronized(this) {
            if (!loaded) {
                System.loadLibrary("sqlcipher")
                loaded = true
            }
        }
    }

    private fun loadOrCreatePassphrase(context: Context, name: String): ByteArray {
        val keyFile = File(context.noBackupFilesDir, WRAPPED_FILE)
        val dbExists = context.getDatabasePath(name).exists()
        if (keyFile.exists()) {
            val wrapKey = existingWrappingKey()
                ?: throw DatabaseKeyUnavailableException("The Keystore key that protects the database key is gone")
            return try {
                unwrap(wrapKey, keyFile.readBytes())
            } catch (e: GeneralSecurityException) {
                throw DatabaseKeyUnavailableException("The stored database key cannot be decrypted", e)
            } catch (e: RuntimeException) {
                throw DatabaseKeyUnavailableException("The stored database key is corrupt", e)
            }
        }
        if (dbExists) throw DatabaseKeyUnavailableException("A database exists but its key file is missing")

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val tmp = File(keyFile.parentFile, "$WRAPPED_FILE.tmp")
        tmp.writeBytes(wrap(newWrappingKey(), passphrase))
        check(tmp.renameTo(keyFile)) { "Could not store the wrapped database key" }
        return passphrase
    }

    private fun existingWrappingKey(): SecretKey? =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getKey(KEY_ALIAS, null) as? SecretKey

    private fun newWrappingKey(): SecretKey {
        existingWrappingKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun wrap(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun unwrap(key: SecretKey, blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return cipher.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }
}
