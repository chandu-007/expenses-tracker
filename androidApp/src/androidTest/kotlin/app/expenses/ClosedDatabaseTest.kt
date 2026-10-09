package app.expenses

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The container closes the database on purpose (starting afresh, tearing the app down). Background work already running then meets a closed
 * database; that used to end the whole process with an uncaught exception, which made several full test runs die in unrelated tests.
 */
@RunWith(AndroidJUnit4::class)
class ClosedDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    @Test
    fun theProcessSurvivesBackgroundRefreshesRunningWhileTheDatabaseIsClosedUnderThem() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        Thread.sleep(2_000)
        repeat(6) {
            val bumper = thread { repeat(150) { Capture.changes.value += 1; Thread.sleep(1) } }
            Thread.sleep(40)
            runBlocking { container.close() } // the next write reopens it; work in flight meets the closed one
            bumper.join()
        }
        Thread.sleep(1_500)
        assertTrue(true, "still alive")
    }

    @Test
    fun onlyTheDatabaseBeingClosedIsTreatedAsHarmless() {
        assertTrue(isDatabaseClosed(IllegalStateException("attempt to re-open an already-closed object: SQLiteDatabase: /x")))
        assertTrue(isDatabaseClosed(IllegalStateException("Cannot perform this operation because the connection pool has been closed.")))
        assertFalse(isDatabaseClosed(IllegalStateException("something else")))
        assertFalse(isDatabaseClosed(IllegalArgumentException("attempt to re-open an already-closed object")))
        assertFalse(isDatabaseClosed(RuntimeException("connection pool has been closed")))
        assertEquals(false, isDatabaseClosed(NullPointerException()))
    }
}
