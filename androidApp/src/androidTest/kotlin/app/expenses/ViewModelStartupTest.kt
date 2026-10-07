package app.expenses

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.EncryptedDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class ViewModelStartupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as ExpensesApp

    private fun resetApp() {
        runBlocking { app.container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()
    @After fun cleanUp() = resetApp()

    /**
     * The view model starts loading on another thread as soon as it is created. If that thread reached a property the constructor had not
     * yet initialised, the load died with a NullPointerException nobody saw and the opening screen stayed up forever. It showed on about
     * 3% of starts in a device test, so this builds and drops it many times and requires every load to finish.
     */
    @Test
    fun everyViewModelFinishesLoadingHoweverQuicklyItIsCreated() {
        val factory = ViewModelProvider.AndroidViewModelFactory(app)
        repeat(400) { n ->
            val store = ViewModelStore()
            val vm = ViewModelProvider(store, factory)[LedgerViewModel::class.java]
            val deadline = System.currentTimeMillis() + 5_000
            while (vm.state.value !is UiState.Ready && System.currentTimeMillis() < deadline) Thread.sleep(1)
            if (vm.state.value !is UiState.Ready) fail("the load of view model number $n never finished: ${vm.state.value}")
            store.clear()
        }
    }
}
