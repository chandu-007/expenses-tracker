package app.expenses.core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup file (documented format, version 1). It is two parts: one line of JSON describing the file, a newline, then the payload.
 *
 *     {"format":"expenses-backup","version":1,"createdAt":<epoch millis>,"payloadBytes":<n>,"sha256":"<hex>"}
 *     {"accounts":[...],"categories":[...],"entries":[...],...}
 *
 * `sha256` is the hash of the payload bytes exactly as stored, so a damaged or cut-off file is detected before anything is read from it.
 * All amounts are integers in minor units (paise, cents) with the currency code beside them; dates are epoch milliseconds (UTC) for
 * entries and days since 1970-01-01 for repeating-expense schedules. Unknown fields are ignored on read, so a later version can add fields.
 */
@Serializable
data class BackupHeader(val format: String, val version: Int, val createdAt: Long, val payloadBytes: Int, val sha256: String)

@Serializable data class BackupCurrency(val code: String, val digits: Int)
@Serializable data class BackupAccount(val id: String, val name: String, val type: String, val currency: String, val openingMinor: Long = 0L, val archived: Boolean = false)
@Serializable data class BackupCategory(val id: String, val name: String, val parentId: String? = null, val color: Long? = null, val position: Int, val hidden: Boolean = false, val deleted: Boolean = false, val mergedInto: String? = null)
@Serializable data class BackupSplit(val categoryId: String, val amountMinor: Long)
@Serializable data class BackupShare(val personId: String? = null, val amountMinor: Long)

@Serializable
data class BackupEntry(
    val id: String,
    val kind: String,
    val occurredAt: Long,
    val accountId: String? = null,
    val counterAccountId: String? = null,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String? = null,
    val personId: String? = null,
    val toAmountMinor: Long? = null,
    val toCurrency: String? = null,
    val direction: String? = null,
    val refundsEntry: String? = null,
    val originalAmountMinor: Long? = null,
    val originalCurrency: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val source: String = "manual",
    val sourceRef: String? = null,
    val splits: List<BackupSplit> = emptyList(),
    val shares: List<BackupShare> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable data class BackupTemplate(val id: String, val name: String, val amountMinor: Long? = null, val currency: String, val categoryId: String, val accountId: String, val merchant: String? = null)
@Serializable data class BackupBudget(val scope: String, val limitMinor: Long, val currency: String)
@Serializable data class BackupBudgetPeriod(val kind: String, val startValue: Long)

@Serializable
data class BackupRecurring(
    val id: String, val name: String, val amountMinor: Long? = null, val currency: String, val categoryId: String, val accountId: String,
    val merchant: String? = null, val unit: String, val every: Int, val startDay: Long, val endKind: String, val endValue: Long? = null, val auto: Boolean = false,
)

@Serializable data class BackupDone(val recurringId: String, val day: Long, val outcome: String, val entryId: String? = null)

/** Everything a backup holds. Deleted entries are not included; a deleted category that was merged is, so a late edit still finds its target. */
@Serializable
data class BackupData(
    val currencies: List<BackupCurrency> = emptyList(),
    val accounts: List<BackupAccount> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val entries: List<BackupEntry> = emptyList(),
    val templates: List<BackupTemplate> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val budgetPeriod: BackupBudgetPeriod? = null,
    val recurring: List<BackupRecurring> = emptyList(),
    val recurringDone: List<BackupDone> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

/** A backup file that cannot be used. The message is written for the user. */
class BackupException(message: String) : Exception(message)

object BackupFile {
    const val FORMAT = "expenses-backup"
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(data: BackupData, createdAtMillis: Long): ByteArray {
        val payload = json.encodeToString(BackupData.serializer(), data).encodeToByteArray()
        val header = json.encodeToString(BackupHeader.serializer(), BackupHeader(FORMAT, VERSION, createdAtMillis, payload.size, Sha256.hex(payload)))
        return header.encodeToByteArray() + byteArrayOf('\n'.code.toByte()) + payload
    }

    /** Checks the file and returns its contents, or throws [BackupException] saying what is wrong. Nothing is written anywhere. */
    fun decode(bytes: ByteArray): Pair<BackupHeader, BackupData> {
        val newline = bytes.indexOf('\n'.code.toByte())
        if (newline <= 0) throw BackupException("This is not a backup file from this app.")
        val header = try {
            json.decodeFromString(BackupHeader.serializer(), bytes.copyOfRange(0, newline).decodeToString())
        } catch (e: Exception) {
            throw BackupException("This is not a backup file from this app.")
        }
        if (header.format != FORMAT) throw BackupException("This is not a backup file from this app.")
        if (header.version > VERSION) throw BackupException("This backup was made by a newer version of the app. Update the app to restore it.")
        if (header.version < 1) throw BackupException("This backup file is not valid.")
        val payload = bytes.copyOfRange(newline + 1, bytes.size)
        if (payload.size != header.payloadBytes) throw BackupException("This backup file is incomplete or damaged (it should hold ${header.payloadBytes} bytes of data but holds ${payload.size}).")
        if (Sha256.hex(payload) != header.sha256) throw BackupException("This backup file is damaged: its contents do not match its checksum. Nothing was changed.")
        val data = try {
            json.decodeFromString(BackupData.serializer(), payload.decodeToString())
        } catch (e: Exception) {
            throw BackupException("This backup file could not be read (${e.message?.lineSequence()?.firstOrNull() ?: "unknown error"}).")
        }
        return header to data
    }
}
