package app.expenses

import app.expenses.core.storage.SecretRead
import app.expenses.core.storage.SecretStore

/**
 * The key of passphrase-protected automatic copies in the iPhone Keychain, through the Swift host ([IosHostServices.secretPut]): accessible after the first unlock, this
 * device only. Unlike the app's files, a Keychain item outlives deleting the app, so a key left from an earlier install is simply replaced when protection is turned on.
 */
internal class IosSecretStore(private val host: IosHostServices) : SecretStore {
    override fun put(name: String, secret: ByteArray) {
        val status = host.secretPut(name, secret.toNSData())
        check(status == 0) { "The Keychain would not keep it (error $status)" }
    }

    /** The Keychain's own answer: errSecItemNotFound (-25300) is [SecretRead.NotFound]; anything else that is not the item (errSecInteractionNotAllowed -25308 before the first unlock, or another error) is [SecretRead.Unavailable]. */
    override fun read(name: String): SecretRead = try {
        val data = host.secretGet(name)
        if (data != null) SecretRead.Found(data.toByteArray()) else if (host.secretStatus(name) == ERR_SEC_ITEM_NOT_FOUND) SecretRead.NotFound else SecretRead.Unavailable
    } catch (e: Exception) {
        SecretRead.Unavailable
    }

    private companion object {
        const val ERR_SEC_ITEM_NOT_FOUND = -25300
    }

    override fun delete(name: String) {
        host.secretDelete(name)
    }
}
