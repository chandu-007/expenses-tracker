package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb

/**
 * Quick-entry templates and small device-local settings. Template edits are written to the sync log field by field,
 * like entries; settings and use counts are local to this device and are not.
 */
class TemplateStore(
    private val db: ExpensesDb,
    private val clock: HlcClock,
    private val nowMillis: () -> Long,
) {
    private val q get() = db.schemaQueries

    fun save(requested: Template) {
        db.transaction {
            val template = requested.copy(category = CategoryId(db.resolveCategory(requested.category.value)))
            val before = q.templateById(template.id).executeAsOneOrNull()
            require(before == null || before.deleted == 0L) { "Template ${template.id} was deleted" }
            val currency = template.amount?.currency ?: Currency.INR
            q.insertCurrency(currency.code, currency.minorUnitDigits.toLong())
            val fields = fieldsOf(template)
            q.upsertTemplate(template.id, template.name, template.amount?.minorUnits, currency.code, template.category.value, template.account.value, template.merchant)
            val beforeFields = before?.let {
                fieldsOf(Template(it.id, it.name, it.amount_minor?.let { m -> Money(m, Currency(it.currency, currencyDigits(it.currency))) }, CategoryId(it.category_id), AccountId(it.account_id), it.merchant))
            }
            for ((field, value) in fields) {
                val changed = if (beforeFields == null) value != null else beforeFields[field] != value
                if (changed) q.insertOp(clock.now(nowMillis()).encode(), "template", template.id, field, value)
            }
        }
    }

    fun delete(id: String) {
        db.transaction {
            val row = q.templateById(id).executeAsOneOrNull() ?: return@transaction
            if (row.deleted == 1L) return@transaction
            q.softDeleteTemplate(id)
            q.insertOp(clock.now(nowMillis()).encode(), "template", id, "deleted", "1")
        }
    }

    /** Most used first, then by name. */
    fun list(): List<Template> = q.liveTemplates().executeAsList().map {
        Template(
            it.id, it.name,
            it.amount_minor?.let { m -> Money(m, Currency(it.currency, currencyDigits(it.currency))) },
            CategoryId(it.category_id), AccountId(it.account_id), it.merchant,
        )
    }

    fun recordUse(id: String) = q.bumpTemplateUse(id)

    fun setting(key: String): String? = q.settingValue(key).executeAsOneOrNull()

    fun putSetting(key: String, value: String) = q.putSetting(key, value)

    /** Every setting whose key starts with [prefix], by key. */
    fun settingsWithPrefix(prefix: String): List<Pair<String, String>> = q.settingsWithPrefix(prefix).executeAsList().map { it.key to it.value_ }

    fun removeSetting(key: String) = q.removeSetting(key)

    /** Writes only when the value differs: each write is its own commit, and settings like "last category" almost never change between saves. */
    fun putSettingIfChanged(key: String, value: String) { if (setting(key) != value) q.putSetting(key, value) }

    private fun currencyDigits(code: String): Int = q.selectCurrencies().executeAsList().firstOrNull { it.code == code }?.digits?.toInt() ?: 2

    private fun fieldsOf(t: Template): Map<String, String?> = linkedMapOf(
        "name" to t.name,
        "amount_minor" to t.amount?.minorUnits?.toString(),
        "currency" to (t.amount?.currency ?: Currency.INR).code,
        "category_id" to t.category.value,
        "account_id" to t.account.value,
        "merchant" to t.merchant,
    )
}
