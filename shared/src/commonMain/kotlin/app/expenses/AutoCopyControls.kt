package app.expenses

import app.expenses.core.storage.AutoCopyProtection
import app.expenses.core.storage.BackupException

/**
 * What the Data screen's "Protect automatic copies with a passphrase" switch does. The key is made from the passphrase (slow on purpose: about a second), kept in the
 * phone's secure storage, and only then is protection recorded as on, so a switch that is on always has a key behind it; the passphrase itself is used for the key and
 * dropped, never written anywhere. Runs off the main thread (the caller is [LedgerViewModel.setAutoProtect]).
 */
object AutoCopyControls {
    /** Turns protection on, or, when it is on already, changes the passphrase: a new salt and a new key; the next copies use it, the copies already in the folder keep the old passphrase. */
    suspend fun turnOn(platform: Platform, container: AppContainer, passphrase: String): String {
        val store = platform.secretStore ?: throw BackupException("This phone has no secure storage for the key, so copies cannot be protected.")
        if (passphrase.length < platform.backupMinPassphrase) throw BackupException("The passphrase must be at least ${platform.backupMinPassphrase} characters.")
        // "changed" only when protection was on with a key that can still be read; with the key gone it is turned on afresh
        val changing = container.write { it.templates.setting(AutoExportKeys.PROTECT) == "1" } == true && AutoCopyProtection.load(store) != null
        AutoCopyProtection.enable(store, container.backupPrimitives, passphrase)
        container.write { ok ->
            ok.templates.putSetting(AutoExportKeys.PROTECT, "1")
            clearStaleFailure(ok)
        } ?: throw BackupException("The data is not available.")
        return if (changing) "The passphrase for automatic copies is changed. The next copy uses it; copies already in the folder keep the passphrase they were made with."
        else "Automatic copies are protected from now on. The next copy is sealed with this passphrase; copies already in the folder stay as they were."
    }

    /** Turns protection off and forgets the key: new copies are plain files again. Copies already sealed still open with their passphrase. */
    suspend fun turnOff(platform: Platform, container: AppContainer): String {
        // the switch first: a run that happens in between then writes a plain copy as asked, instead of failing for want of a key
        container.write { ok ->
            ok.templates.putSetting(AutoExportKeys.PROTECT, "0")
            clearStaleFailure(ok)
        } ?: throw BackupException("The data is not available.")
        AutoCopyProtection.disable(platform.secretStore)
        return "Protection is off. New copies are plain files again; copies already in the folder stay sealed and open with the passphrase they were made with."
    }

    /** A "could not protect" notice belongs to the key that is no longer there; a new passphrase (or turning protection off) answers it. */
    private fun clearStaleFailure(ok: AppContainer.Opened.Ok) {
        if (ok.templates.setting(AutoExportKeys.STATUS)?.substringAfter('|')?.let { it in AutoExportKeys.KEY_PROBLEMS } == true) ok.templates.putSetting(AutoExportKeys.STATUS, "ok|")
    }
}
