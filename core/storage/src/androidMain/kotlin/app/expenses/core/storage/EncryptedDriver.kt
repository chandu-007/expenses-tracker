package app.expenses.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Opens the app database encrypted with SQLCipher. The database passphrase is 32 random bytes that
 * never leave the device unwrapped: they are stored encrypted with an AES-GCM key held in the Android
 * Keystore (hardware-backed where available), so the file on disk is useless without the Keystore key.
 *
 * The database is opened through SQLCipher's own bundled SQLite, never the framework one: the system
 * SQLite on Android 8 to 10 is older than 3.24 and cannot run the upserts in the schema.
 */
object EncryptedDriver {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "expenses_db_wrap_key"
    private const val WRAPPED_FILE = "db_key.bin"
    private const val DB_NAME = "expenses.db"

    @Volatile private var loaded = false

    fun create(context: Context, name: String = DB_NAME): SqlDriver {
        loadNative()
        val passphrase = loadOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        return AndroidSqliteDriver(ExpensesDb.Schema, context, name, factory)
    }

    private fun loadNative() {
        if (!loaded) synchronized(this) {
            if (!loaded) {
                System.loadLibrary("sqlcipher")
                loaded = true
            }
        }
    }

    private fun loadOrCreatePassphrase(context: Context): ByteArray {
        val file = File(context.noBackupFilesDir, WRAPPED_FILE)
        val key = wrappingKey()
        if (file.exists()) return unwrap(key, file.readBytes())
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val tmp = File(file.parentFile, "$WRAPPED_FILE.tmp")
        tmp.writeBytes(wrap(key, passphrase))
        check(tmp.renameTo(file)) { "Could not store the wrapped database key" }
        return passphrase
    }

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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
