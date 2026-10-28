package app.expenses.core.storage

/**
 * A small secret kept under the protection of the phone's secure storage: Android's Keystore wraps it, the iPhone keeps it in the Keychain (only after the
 * first unlock, only on this device). Implemented by each platform; common code and its tests use a fake.
 */
interface SecretStore {
    /** Replaces the secret under [name]. Throws when it could not be kept; a failed replacement leaves the old secret as it was. */
    fun put(name: String, secret: ByteArray)

    /**
     * What is kept under [name]: [SecretRead.Found], [SecretRead.NotFound] (nothing there, or it can never be read again: the wrapping key is gone, a new phone, a reset
     * Keystore) or [SecretRead.Unavailable] (it may be there but cannot be read right now: the iPhone before its first unlock, a Keystore that is busy). Never throws.
     */
    fun read(name: String): SecretRead

    /** The secret, or null for anything but [SecretRead.Found]. */
    fun get(name: String): ByteArray? = (read(name) as? SecretRead.Found)?.bytes

    /** Removes the secret; nothing happens when there is none. */
    fun delete(name: String)
}

/** The three answers a [SecretStore] can give. */
sealed interface SecretRead {
    class Found(val bytes: ByteArray) : SecretRead
    data object NotFound : SecretRead
    data object Unavailable : SecretRead
}

/**
 * The key an automatic copy is sealed with, made once from a passphrase: PBKDF2-HMAC-SHA256 ([key], 32 bytes) of the passphrase with a random [salt] and
 * [iterations] rounds. It is what the phone keeps (in a [SecretStore]) instead of the passphrase; the salt and rounds go into the header of every copy, so the
 * passphrase alone gets the key back (see [BackupSeal.keyedSink]).
 */
class KeptKey(val iterations: Int, val salt: ByteArray, val key: ByteArray) {
    init {
        require(BackupSeal.acceptsIterations(iterations) && salt.size == SALT_BYTES && key.size == KEY_BYTES) { "not a key of ours" }
    }

    /** One version byte (2), the rounds (4 bytes, big-endian), the salt, the key, then the first 4 bytes of the SHA-256 of all that: 57 bytes. */
    fun encode(): ByteArray {
        val body = byteArrayOf(VERSION, (iterations ushr 24).toByte(), (iterations ushr 16).toByte(), (iterations ushr 8).toByte(), iterations.toByte()) + salt + key
        return body + Sha256.hash(body).copyOf(CHECK_BYTES)
    }

    companion object {
        const val SALT_BYTES = 16
        const val KEY_BYTES = 32
        const val CHECK_BYTES = 4
        const val RECORD_BYTES = 1 + 4 + SALT_BYTES + KEY_BYTES + CHECK_BYTES

        /** Version 2. (Version 1, without the check, was never shipped, so it is not read.) */
        private const val VERSION: Byte = 2

        /** The key [encode] wrote, or null for anything else (a short or changed blob, another version, a failing check, rounds out of range). */
        fun decode(bytes: ByteArray): KeptKey? {
            if (bytes.size != RECORD_BYTES || bytes[0] != VERSION) return null
            val body = bytes.copyOf(RECORD_BYTES - CHECK_BYTES)
            if (!Sha256.hash(body).copyOf(CHECK_BYTES).contentEquals(bytes.copyOfRange(RECORD_BYTES - CHECK_BYTES, RECORD_BYTES))) return null
            val iterations = ((bytes[1].toInt() and 0xFF) shl 24) or ((bytes[2].toInt() and 0xFF) shl 16) or ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
            if (!BackupSeal.acceptsIterations(iterations)) return null
            return KeptKey(iterations, bytes.copyOfRange(5, 5 + SALT_BYTES), bytes.copyOfRange(5 + SALT_BYTES, 5 + SALT_BYTES + KEY_BYTES))
        }
    }
}

/**
 * Protecting automatic copies with a passphrase. A copy made while the phone is locked and nobody is there cannot ask for the passphrase, so the person types it
 * once: [enable] makes the key from it (PBKDF2, the rounds backups use, a new random salt) and keeps the KEY in the phone's [SecretStore], never the passphrase.
 * Each copy is then sealed under that key without asking ([plan]); a person with only the passphrase can open it, because its header holds the salt and rounds.
 *
 * What this protects: the files in the folder or the cloud drive. It does not protect against someone who holds the unlocked phone and this app: the key is on the phone.
 */
object AutoCopyProtection {
    /** The name the key is kept under. */
    const val SECRET = "auto_copy_key"

    /** What a run does about protection: write plain, seal with [seal], or write nothing because protection is on and the key cannot be read. */
    sealed interface Plan {
        data object Plain : Plan
        class Sealed(val seal: SealWith) : Plan

        /** Protection is on and the key cannot be used: nothing is written. [why] says which of the four reasons it is. */
        class CannotProtect(val why: Why) : Plan
    }

    /** Why a key cannot be used: no secure storage at all, nothing kept (or lost for good), not readable right now, or kept but damaged. */
    enum class Why { NO_STORE, NOT_FOUND, UNAVAILABLE, CORRUPT }

    /** The result of reading the kept key. */
    sealed interface KeyRead {
        class Found(val key: KeptKey) : KeyRead
        class Missing(val why: Why) : KeyRead
    }

    /**
     * Decides how this run writes. With [protect] off the copy is plain. With it on, either the key is read and the copy is sealed, or [Plan.CannotProtect]: a missing,
     * unreadable or damaged key (or no secure storage at all) never turns into a plain copy.
     */
    fun plan(protect: Boolean, store: SecretStore?, primitives: SealPrimitives): Plan {
        if (!protect) return Plan.Plain
        return when (val r = read(store)) {
            is KeyRead.Found -> Plan.Sealed(SealWith.withKey(r.key, primitives))
            is KeyRead.Missing -> Plan.CannotProtect(r.why)
        }
    }

    /**
     * Makes a key from [passphrase] with a new salt and keeps it, replacing the one kept before (copies made earlier stay under the earlier passphrase). Reads the key
     * back from the store before it returns. Throws [BackupException] when the passphrase is too short or the key could not be kept.
     */
    fun enable(store: SecretStore, primitives: SealPrimitives, passphrase: String, iterations: Int = BackupSeal.ITERATIONS) {
        if (passphrase.length < BackupSeal.MIN_PASSPHRASE) throw BackupException("The passphrase must be at least ${BackupSeal.MIN_PASSPHRASE} characters.")
        val salt = primitives.randomBytes(KeptKey.SALT_BYTES)
        val blob = KeptKey(iterations, salt, primitives.deriveKey(passphrase, salt, iterations)).encode()
        try {
            store.put(SECRET, blob)
        } catch (e: Exception) {
            throw BackupException("The key could not be kept in this phone's secure storage (${e.message ?: e::class.simpleName}). Nothing was changed.")
        }
        val back = (store.read(SECRET) as? SecretRead.Found)?.bytes
        if (back == null || !back.contentEquals(blob)) throw BackupException("The key could not be kept in this phone's secure storage: it did not read back.")
    }

    /** The key kept, or null when there is none or it cannot be read or is not ours. Never throws. */
    fun load(store: SecretStore?): KeptKey? = (read(store) as? KeyRead.Found)?.key

    /** The kept key, or why there is none to use. Never throws, and never creates, replaces or removes anything. */
    fun read(store: SecretStore?): KeyRead {
        if (store == null) return KeyRead.Missing(Why.NO_STORE)
        return try {
            when (val r = store.read(SECRET)) {
                is SecretRead.NotFound -> KeyRead.Missing(Why.NOT_FOUND)
                is SecretRead.Unavailable -> KeyRead.Missing(Why.UNAVAILABLE)
                is SecretRead.Found -> KeptKey.decode(r.bytes)?.let { KeyRead.Found(it) } ?: KeyRead.Missing(Why.CORRUPT)
            }
        } catch (e: Exception) {
            KeyRead.Missing(Why.UNAVAILABLE)
        }
    }

    /** Forgets the key. Copies already made are not touched and still open with their passphrase. */
    fun disable(store: SecretStore?) {
        try { store?.delete(SECRET) } catch (_: Exception) { }
    }
}
