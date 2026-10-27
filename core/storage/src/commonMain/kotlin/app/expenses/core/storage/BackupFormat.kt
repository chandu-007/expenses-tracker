package app.expenses.core.storage

import app.expenses.core.domain.AttachmentRules
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup file (documented format, versions 1 and 2). It is a line of JSON describing the file, a newline, then the payload:
 *
 *     {"format":"expenses-backup","version":1,"createdAt":<epoch millis>,"payloadBytes":<n>,"sha256":"<hex>"}
 *     {"accounts":[...],"categories":[...],"entries":[...],...}
 *
 * `sha256` is the hash of the payload bytes exactly as stored, so a damaged or cut-off file is detected before anything is read from it.
 * All amounts are integers in minor units (paise, cents) with the currency code beside them; dates are epoch milliseconds (UTC) for
 * entries and days since 1970-01-01 for repeating-expense schedules. Unknown fields are ignored on read, so a later version can add fields.
 *
 * A backup that also carries photos and files is version 2: the payload lists them (`attachments`) and their bytes follow the payload, one after
 * the other in the order of that list, `sizeBytes` each. A backup without files is written exactly as version 1 was, so any older version of the app reads it.
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
@Serializable data class BackupSavedSearch(val id: String, val name: String, val text: String = "", val minRupees: String = "", val maxRupees: String = "", val categoryId: String? = null, val kind: String = "ANY", val range: String = "ANY", val tag: String? = null, val fromDay: Long? = null, val toDay: Long? = null, val accountId: String? = null)

@Serializable data class BackupPerson(val id: String, val name: String, val deleted: Boolean = false)

@Serializable data class BackupBill(
    val id: String, val issuer: String, val kind: String, val title: String, val amountMinor: Long, val minDueMinor: Long? = null, val currency: String, val dueDate: String? = null,
    val accountLast4: String? = null, val reference: String? = null, val autoDebit: Boolean = false, val receivedAt: Long, val status: String, val paidAt: Long? = null, val dedupeKey: String,
)

/** A photo or file attached to an entry. Its [sizeBytes] bytes follow the payload (version 2 files); [sha256] is checked before the file is put back. */
@Serializable data class BackupAttachment(val id: String, val entryId: String, val name: String, val mime: String, val sizeBytes: Long, val sha256: String, val createdAt: Long = 0L) {
    /** The id as this phone keeps it: lower case, because it becomes a file name and some file systems do not tell upper from lower case apart. */
    fun localId(): String = id.lowercase()

    /** What a listed file must look like to be put back: a plain id (it becomes a file name), a size within the limit, a SHA-256 to check it against. */
    fun isRestorable(): Boolean =
        ID_PATTERN.matches(id) && sizeBytes in 1..AttachmentRules.MAX_BYTES && sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }

    /** The type as it is kept: `type/subtype` of plain characters (it is handed to the viewer chooser), else a generic one. */
    fun safeMime(): String = mime.trim().takeIf { MIME_PATTERN.matches(it) }?.lowercase() ?: "application/octet-stream"
}

/** The app makes lower-case UUIDs; a restore also takes upper case and lower-cases it ([BackupAttachment.localId]). */
private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
private val MIME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9!#&^_.+-]{0,126}/[A-Za-z0-9][A-Za-z0-9!#&^_.+-]{0,126}")

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
    /** The photos and files attached to the entries in this file, when the backup carries them (version 2; their bytes follow the payload). Left out of the JSON when empty, so a backup without files is exactly a version 1 file. */
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val attachments: List<BackupAttachment> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

/** A backup file that cannot be used. The message is written for the user. */
class BackupException(message: String) : Exception(message)

