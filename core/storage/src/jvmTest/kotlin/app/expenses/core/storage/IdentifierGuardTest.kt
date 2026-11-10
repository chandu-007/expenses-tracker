package app.expenses.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Names that must NEVER change, whatever the app is called (see docs/RENAME.md): the format line of a backup and the three magic headers of a sealed one, or backups of earlier versions stop
 * opening. (The Android identifiers are guarded by `IdentifierGuardTest` on a device.)
 */
class IdentifierGuardTest {
    @Test
    fun theBackupFormatNameAndTheThreeSealHeadersAreExactlyWhatEarlierVersionsWroteAndRead() {
        assertEquals("expenses-backup", BackupFile.FORMAT)
        assertTrue(BackupSeal.isLegacySealed("EXPENC1\n".encodeToByteArray()) && !BackupSeal.isSealed("EXPENC1\n".encodeToByteArray()), "EXPENC1 is the oldest seal")
        assertTrue(BackupSeal.isSealed("EXPENC2\n".encodeToByteArray()) && !BackupSeal.isKeyedSealed("EXPENC2\n".encodeToByteArray()), "EXPENC2 is the passphrase seal")
        assertTrue(BackupSeal.isSealed("EXPENC3\n".encodeToByteArray()) && BackupSeal.isKeyedSealed("EXPENC3\n".encodeToByteArray()), "EXPENC3 is the keyed seal of the automatic copies")
        assertTrue(!BackupSeal.isSealed("NEWMAGIC\n".encodeToByteArray()), "no new header was made up")
    }
}
