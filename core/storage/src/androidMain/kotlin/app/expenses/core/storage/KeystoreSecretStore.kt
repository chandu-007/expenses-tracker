package app.expenses.core.storage

import android.content.Context
import java.io.File

/**
 * A [SecretStore] on the Android Keystore, the way the database key is kept ([KeystoreWrap]): each secret is encrypted with an AES-256-GCM key that never leaves
 * the Keystore and only the encrypted blob is written, to the app's no-backup folder (so it is never copied to another phone, where the Keystore key would not
 * exist). Its Keystore key has an alias of its own, apart from the database's, so wiping one never touches the other.
 *
 * A secret whose Keystore key is gone (a reset Keystore, a restored phone) reads as null; [put] then makes a new Keystore key and replaces the blob.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val dir = File((context.applicationContext ?: context).noBackupFilesDir, "secrets")

    private fun file(name: String): File {
        require(NAME.matches(name)) { "not a secret name: $name" }
        return File(dir, "$name.bin")
    }

    @Synchronized
    override fun put(name: String, secret: ByteArray) {
        val target = file(name)
        dir.mkdirs()
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeBytes(KeystoreWrap.wrap(KeystoreWrap.newKey(ALIAS), secret))
        // a rename replaces the old blob whole: a failure before it leaves the old secret as it was
        check(tmp.renameTo(target)) { "Could not store the wrapped secret" }
    }

    /**
     * No blob, or no Keystore key to open it with, or a blob the key does not open (a restored or changed phone): [SecretRead.NotFound], it will not come back. A Keystore
     * that fails some other way (the service is busy, a provider error, an I/O error) is [SecretRead.Unavailable]: try again later, change nothing.
     */
    @Synchronized
    override fun read(name: String): SecretRead = try {
        val target = file(name)
        if (!target.exists()) SecretRead.NotFound
        else {
            val key = KeystoreWrap.existingKey(ALIAS)
            if (key == null) SecretRead.NotFound else SecretRead.Found(KeystoreWrap.unwrap(key, target.readBytes()))
        }
    } catch (e: java.security.GeneralSecurityException) {
        SecretRead.NotFound
    } catch (e: Exception) {
        SecretRead.Unavailable
    }

    @Synchronized
    override fun delete(name: String) {
        file(name).delete()
        File(dir, "${name}.bin.tmp").delete()
        // the Keystore key goes with the last secret it wrapped
        if (dir.list().isNullOrEmpty()) KeystoreWrap.deleteKey(ALIAS)
    }

    private companion object {
        const val ALIAS = "expenses_secrets_wrap_key"
        val NAME = Regex("[a-z0-9_]{1,40}")
    }
}
