package app.expenses.core.storage

import kotlinx.serialization.KSerializer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The format is documented in docs/BACKUP_FORMAT.md. A field added to the file but not to the document fails here. */
class BackupFormatDocTest {
    private val doc = listOf(File("../../docs/BACKUP_FORMAT.md"), File("docs/BACKUP_FORMAT.md")).first { it.exists() }.readText()

    private fun names(s: KSerializer<*>) = (0 until s.descriptor.elementsCount).map { s.descriptor.getElementName(it) }

    @Test
    fun everyFieldOfTheFileIsDescribedInTheDocument() {
        val all = listOf(
            BackupHeader.serializer(), BackupCurrency.serializer(), BackupAccount.serializer(), BackupCategory.serializer(), BackupSplit.serializer(),
            BackupShare.serializer(), BackupEntry.serializer(), BackupTemplate.serializer(), BackupBudget.serializer(), BackupBudgetPeriod.serializer(),
            BackupRecurring.serializer(), BackupDone.serializer(), BackupPerson.serializer(), BackupData.serializer(),
        ).flatMap(::names).toSet()
        val missing = all.filter { "`$it`" !in doc }
        assertTrue(missing.isEmpty(), "fields missing from docs/BACKUP_FORMAT.md: $missing")
    }
}
