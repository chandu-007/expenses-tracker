package app.expenses

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.KeystoreSecretStore
import app.expenses.core.storage.SecretRead
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Keystore-protected secret store writes its blob durably (whole, synced, renamed) and in the same format as before, so a blob an earlier version wrote with a
 * plain write still reads. Uses invented secret names of its own and deletes them after.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val dir = File(context.noBackupFilesDir, "secrets")
    private val names = listOf("test_secret_new", "test_secret_old", "test_secret_twice", "test_secret_empty", "test_secret_cut")

    @After fun tearDown() { val store = KeystoreSecretStore(context); names.forEach { store.delete(it) } }

    @Test
    fun aSecretIsWrittenWholeAndReadsBack() {
        val store = KeystoreSecretStore(context)
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        store.put("test_secret_new", secret)
        assertContentEquals(secret, assertIs<SecretRead.Found>(store.read("test_secret_new")).bytes)
        val blob = File(dir, "test_secret_new.bin")
        // ivLength (1 byte) + 12-byte GCM iv + ciphertext + 16-byte tag
        assertEquals(1 + 12 + secret.size + 16, blob.length().toInt())
        assertFalse(File(dir, "test_secret_new.bin.tmp").exists(), "no temporary file is left behind")
        // a second store (the app started again) reads the same
        assertContentEquals(secret, assertIs<SecretRead.Found>(KeystoreSecretStore(context).read("test_secret_new")).bytes)
    }

    @Test
    fun replacingASecretKeepsOnlyTheNewOne() {
        val store = KeystoreSecretStore(context)
        store.put("test_secret_twice", "first".encodeToByteArray())
        store.put("test_secret_twice", "second, longer".encodeToByteArray())
        assertEquals("second, longer", assertIs<SecretRead.Found>(store.read("test_secret_twice")).bytes.decodeToString())
        assertFalse(File(dir, "test_secret_twice.bin.tmp").exists())
    }

    @Test
    fun aBlobWrittenTheOldWayStillReads() {
        val secret = "an invented secret 1234".encodeToByteArray()
        // the earlier put, verbatim: the same Keystore key and blob layout, written with a plain write and a rename, no sync
        val iv: ByteArray
        val sealed = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.ENCRYPT_MODE, wrapKey()); iv = this.iv; doFinal(secret) }
        dir.mkdirs()
        val target = File(dir, "test_secret_old.bin")
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + sealed)
        assertTrue(tmp.renameTo(target))
        assertContentEquals(secret, assertIs<SecretRead.Found>(KeystoreSecretStore(context).read("test_secret_old")).bytes)
    }

    @Test
    fun anEmptyBlobReadsAsNotFoundAndThenAPutReplacesIt() {
        wrapKey() // the key is there, so the read reaches the blob itself
        dir.mkdirs()
        File(dir, "test_secret_empty.bin").writeBytes(ByteArray(0))
        val store = KeystoreSecretStore(context)
        assertEquals(SecretRead.NotFound, store.read("test_secret_empty"), "an empty blob is gone for good, not a passing failure")
        val secret = "after the power cut".encodeToByteArray()
        store.put("test_secret_empty", secret)
        assertContentEquals(secret, assertIs<SecretRead.Found>(store.read("test_secret_empty")).bytes)
    }

    @Test
    fun aBlobCutShortAfterItsIvReadsAsNotFound() {
        wrapKey()
        dir.mkdirs()
        // the length byte and a 12-byte iv, no ciphertext and no tag
        File(dir, "test_secret_cut.bin").writeBytes(byteArrayOf(12) + ByteArray(12) { it.toByte() })
        assertEquals(SecretRead.NotFound, KeystoreSecretStore(context).read("test_secret_cut"))
        // a length byte that points past the end
        File(dir, "test_secret_cut.bin").writeBytes(byteArrayOf(100, 1, 2, 3))
        assertEquals(SecretRead.NotFound, KeystoreSecretStore(context).read("test_secret_cut"))
    }

    /** The store's Keystore key (alias `expenses_secrets_wrap_key`), made with the store's own parameters when it is not there yet. */
    private fun wrapKey(): SecretKey {
        val alias = "expenses_secrets_wrap_key"
        (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
    }
}
