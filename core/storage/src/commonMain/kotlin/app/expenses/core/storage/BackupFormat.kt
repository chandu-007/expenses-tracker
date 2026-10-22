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
@Serializable data class BackupCategory(val id: String, val name: String, val parentId: String? = null, val color: Long? = null, val position: Int, val hidden: Boolean = false, val deleted: Boolean = false, val mergedInto: String? = null, val kind: String = "EXPENSE")
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
    val kind: String = "EXPENSE", val toAccountId: String? = null,
)

@Serializable data class BackupCaptureRule(val id: String, val position: Int, val pattern: String, val categoryId: String)
@Serializable data class BackupMerchant(val key: String, val categoryId: String, val uses: Int = 1)
/** How an account looks and its last four digits. Both network and tier are names from the app's lists; an unknown name is ignored on restore. */
@Serializable data class BackupAccountLook(val accountId: String, val network: String? = null, val tier: String? = null, val last4: String? = null)
@Serializable data class BackupCardTerms(val accountId: String, val statementDay: Int, val dueDay: Int, val minDuePercent: Int = 5, val remindDaysBefore: Int = 3)
@Serializable data class BackupSavedSearch(val id: String, val name: String, val text: String = "", val minRupees: String = "", val maxRupees: String = "", val categoryId: String? = null, val kind: String = "ANY", val range: String = "ANY", val tag: String? = null)

@Serializable data class BackupPerson(val id: String, val name: String, val deleted: Boolean = false)

@Serializable data class BackupBill(
    val id: String, val issuer: String, val kind: String, val title: String, val amountMinor: Long, val minDueMinor: Long? = null, val currency: String, val dueDate: String? = null,
    val accountLast4: String? = null, val reference: String? = null, val autoDebit: Boolean = false, val receivedAt: Long, val status: String, val paidAt: Long? = null, val dedupeKey: String,
)

@Serializable data class BackupOrigin(val entryId: String, val kind: String, val sender: String? = null, val text: String, val receivedAt: Long? = null)

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
    val savedSearches: List<BackupSavedSearch> = emptyList(),
    val cardTerms: List<BackupCardTerms> = emptyList(),
    val captureRules: List<BackupCaptureRule> = emptyList(),
    val merchantMap: List<BackupMerchant> = emptyList(),
    /** The people lent to and borrowed from. Absent in a backup made before they existed, which then restores as before. */
    val people: List<BackupPerson> = emptyList(),
    /** Bills found in texts, with what became of them. The message and the entry a payment made are not part of the file. Absent in a backup made before bills existed. */
    val bills: List<BackupBill> = emptyList(),
    /** Where entries were read from (message, notification, email, statement line). Absent in a backup made before this existed. */
    val origins: List<BackupOrigin> = emptyList(),
    /** Card networks, tiers and last four digits of accounts. Absent in a backup made before they existed. */
    val accountLooks: List<BackupAccountLook> = emptyList(),
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
    fun decode(raw: ByteArray): Pair<BackupHeader, BackupData> {
        // a copy that went through an email, a chat or a cloud drive can arrive with a byte-order mark or blank space in front, or a newline behind
        val bytes = withoutLeadingNoise(raw)
        if (bytes.isEmpty()) throw BackupException("That file is empty, so it is not a backup. A download that did not finish can leave an empty file: save the backup again and choose it once more.")
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) throw BackupException("That is a zip or Excel file, not a backup. A backup file ends in .expbak. ${describe(raw)}")
        if (bytes[0] == '<'.code.toByte()) throw BackupException("That is a web page, not a backup: the download may have been blocked or asked you to sign in. ${describe(raw)}")
        val newline = bytes.indexOf('\n'.code.toByte())
        if (newline <= 0) throw BackupException("This is not a backup file from this app. ${describe(raw)}")
        val header = try {
            json.decodeFromString(BackupHeader.serializer(), bytes.copyOfRange(0, newline).decodeToString().trim())
        } catch (e: Exception) {
            throw BackupException("This is not a backup file from this app. ${describe(raw)}")
        }
        if (header.format != FORMAT) throw BackupException("This is not a backup file from this app (it says it is \"${header.format.take(40)}\"). ${describe(raw)}")
        if (header.version > VERSION) throw BackupException("This backup was made by a newer version of the app. Update the app to restore it.")
        if (header.version < 1) throw BackupException("This backup file is not valid.")
        var payload = bytes.copyOfRange(newline + 1, bytes.size)
        // blank space after the data (a trailing newline added on the way) is not part of it
        if (payload.size > header.payloadBytes && payload.copyOfRange(header.payloadBytes, payload.size).all { it == '\n'.code.toByte() || it == '\r'.code.toByte() || it == ' '.code.toByte() }) payload = payload.copyOfRange(0, header.payloadBytes)
        if (payload.size != header.payloadBytes) throw BackupException("This backup file is incomplete or damaged (it should hold ${header.payloadBytes} bytes of data but holds ${payload.size}).")
        if (Sha256.hex(payload) != header.sha256) throw BackupException("This backup file is damaged: its contents do not match its checksum. Nothing was changed.")
        val data = try {
            json.decodeFromString(BackupData.serializer(), payload.decodeToString())
        } catch (e: Exception) {
            throw BackupException("This backup file could not be read (${e.message?.lineSequence()?.firstOrNull() ?: "unknown error"}).")
        }
        return header to data
    }

    private fun withoutLeadingNoise(bytes: ByteArray): ByteArray {
        var i = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) i = 3
        while (i < bytes.size && (bytes[i] == ' '.code.toByte() || bytes[i] == '\n'.code.toByte() || bytes[i] == '\r'.code.toByte() || bytes[i] == '\t'.code.toByte())) i++
        return if (i == 0) bytes else bytes.copyOfRange(i, bytes.size)
    }

    /** What to tell about a file that is not a backup, without showing any of its words: its size and the first bytes in hex. */
    private fun describe(bytes: ByteArray): String =
        "(The file has ${bytes.size} bytes and begins ${bytes.take(8).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }}.)"
}
