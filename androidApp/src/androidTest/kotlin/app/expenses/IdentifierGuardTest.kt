package app.expenses

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Identifiers that must stay the same whatever the app is called (docs/RENAME.md): the application id, the package of the classes, the widget receivers, the intent actions, the notification channel
 * ids (a channel keeps the person's own sound and on/off settings by id), the names of the automatic copies, the file provider authority. An update loses data, placed widgets or settings if one changes.
 */
@RunWith(AndroidJUnit4::class)
class IdentifierGuardTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theWidgetNamesStaySpendingAndTheLabelOfTheAppIsWhatThePhoneShows() {
        assertEquals(context.getString(R.string.app_name), context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        val providers = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
        assertEquals(5, providers.size)
        assertTrue(providers.all { it.loadLabel(context.packageManager).toString().startsWith("Spending") }, "the widget names stay 'Spending: ...'")
    }

    @Test
    fun theApplicationIdThePackageOfTheClassesAndTheActionsAreUnchanged() {
        assertTrue(context.packageName in setOf("app.expenses", "io.github.chandu007.expenses"), context.packageName)
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
        assertEquals("app.expenses", MainActivity::class.java.`package`!!.name)
        assertEquals("app.expenses.action.ADD_EXPENSE", MainActivity.ACTION_ADD_EXPENSE)
        assertEquals("app.expenses.action.OPEN_BILLS", MainActivity.ACTION_OPEN_BILLS)
        assertEquals("app.expenses.action.PAY_CARD", MainActivity.ACTION_PAY_CARD)
        assertEquals("app.expenses.ExpensesApp", ExpensesApp::class.java.name)
        assertEquals("${context.packageName}.files", context.packageManager.getProviderInfo(android.content.ComponentName(context, androidx.core.content.FileProvider::class.java), 0).authority)
    }

    @Test
    fun theWidgetReceiversKeepTheirClassNamesSoPlacedWidgetsSurviveTheUpdate() {
        val names = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null).map { it.provider.className }.toSet()
        assertEquals(
            setOf("app.expenses.widget.QuickAddReceiver", "app.expenses.widget.QuickAddSmallReceiver", "app.expenses.widget.QuickAddWideReceiver", "app.expenses.widget.QuickAddLargeReceiver", "app.expenses.widget.TodayReceiver"),
            names,
        )
        assertTrue(context.packageManager.getReceiverInfo(android.content.ComponentName(context.packageName, "app.expenses.widget.QuickAddReceiver"), 0) != null)
    }

    @Test
    fun theNotificationChannelIdsAndTheNamesOfAutoExportCopiesAreUnchanged() {
        assertEquals(listOf("bills", "recap", "budgets"), listOf(Notifier.CHANNEL_BILLS, Notifier.CHANNEL_RECAP, Notifier.CHANNEL_BUDGETS))
        // the visible names of the channels carry no app name and did not change
        Notifier.createChannels(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        assertEquals(setOf("Bill reminders", "Weekly recap", "Budget alerts"), listOf("bills", "recap", "budgets").map { manager.getNotificationChannel(it).name.toString() }.toSet())
        // the automatic copies are found and pruned by these names: copies of earlier versions would be left behind if they changed
        assertEquals("expenses-backup", AutoExportKeys.BACKUP_PREFIX); assertEquals("expenses", AutoExportKeys.XLSX_PREFIX)
    }

    @Test
    fun noSecondStoreIsCreatedAndTheAppLockStoreKeepsItsName() {
        // the database, the secrets and the lock are found by their old names; a database made under another name would mean an update started a second, empty store
        val names = context.databaseList().toList() + (context.noBackupFilesDir.list()?.toList() ?: emptyList()) + (context.filesDir.list()?.toList() ?: emptyList())
        assertTrue(names.none { it.contains("newname", ignoreCase = true) }, "nothing was created under a second name: $names")
        assertTrue(context.getSharedPreferences("app_lock", android.content.Context.MODE_PRIVATE) != null)
    }
}
