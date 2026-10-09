package app.expenses

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.LockTimeout
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stands in for the system prompt: it can succeed, fail, be cancelled, or stay open until the test finishes it. */
private class FakeAuthenticator : Authenticator {
    var availability = Authenticator.Availability.READY
    var next: Authenticator.Result? = Authenticator.Result.Success // null: leave the prompt open
    var pending: ((Authenticator.Result) -> Unit)? = null
    val prompts = mutableListOf<String>()

    override fun availability(context: Context) = availability

    override fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, onResult: (Authenticator.Result) -> Unit) {
        prompts += title
        val r = next
        if (r != null) onResult(r) else pending = onResult
    }

    fun finish(result: Authenticator.Result) { pending?.invoke(result); pending = null }
}

@RunWith(AndroidJUnit4::class)
class AppLockTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as ExpensesApp
    private val container get() = app.container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val fake = FakeAuthenticator()
    private var now = 1_000_000L
    private val minute = 60_000L

    private fun clearLockPrefs() = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE).edit().clear().commit()

    private fun resetApp() {
        runBlocking { container.startFresh() }
    }

    @Before fun fresh() {
        clearLockPrefs()
        app.lock = AppLock(context, fake) { now }
        resetApp()
    }

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        clearLockPrefs()
        app.lock = AppLock(context) // back to the real authenticator and clock, off
        resetApp()
    }

    /** Switches the lock on the way the user does, so the preferences, the gate and the flags all agree. */
    private fun lockOn(timeout: LockTimeout) {
        clearLockPrefs()
        context.getSharedPreferences("app_lock", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).putString("timeout", timeout.name).commit()
        app.lock = AppLock(context, fake) { now }
    }

    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)) }
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun lock() = app.lock
    private fun background() { scenario!!.moveToState(Lifecycle.State.CREATED) }
    private fun foreground() { scenario!!.moveToState(Lifecycle.State.RESUMED) }
    private fun openSecurity() {
        compose.onNodeWithTag("more_menu").performClick()
        waitFor("open_security")
        compose.onNodeWithTag("open_security").performClick()
        waitFor("lock_switch")
    }

    // ---- turning it on and off ----

    @Test
    fun theSwitchStaysOffAndExplainsWhyWhenThePhoneHasNoScreenLock() {
        fake.availability = Authenticator.Availability.NO_SCREEN_LOCK
        launch(); waitFor("add"); openSecurity()
        waitFor("lock_needs_screen_lock")
        compose.onNodeWithTag("lock_switch").assertIsNotEnabled()
        assertFalse(lock().settings.value.enabled)
        assertTrue(fake.prompts.isEmpty())
    }

    @Test
    fun turningItOnAsksForAuthenticationFirstAndOnlyAFailedOrCancelledPromptLeavesItOff() {
        launch(); waitFor("add"); openSecurity()
        fake.next = Authenticator.Result.Failed("Too many attempts", cancelled = false)
        compose.onNodeWithTag("lock_switch").performClick()
        waitText("security_message", "Too many attempts")
        assertFalse(lock().settings.value.enabled)
        fake.next = Authenticator.Result.Failed("Cancelled", cancelled = true)
        compose.onNodeWithTag("lock_switch").performClick()
        waitGone("security_message") // backing out is not an error
        assertFalse(lock().settings.value.enabled)
        fake.next = Authenticator.Result.Success
        compose.onNodeWithTag("lock_switch").performClick()
        compose.waitUntil(10_000) { lock().settings.value.enabled }
        assertEquals(listOf("Turn on app lock", "Turn on app lock", "Turn on app lock"), fake.prompts)
        assertTrue(context.getSharedPreferences("app_lock", Context.MODE_PRIVATE).getBoolean("enabled", false), "saved")
        assertFalse(lock().locked.value, "turning it on does not lock the screen being used")
    }

    @Test
    fun turningItOffNeedsAuthenticationAndAChangedTimeoutDoes() {
        lockOn(LockTimeout.ONE_MINUTE)
        launch(); fake.finishWhenPrompted(); waitFor("add"); openSecurity()
        fake.next = Authenticator.Result.Failed("Not you", cancelled = false)
        compose.onNodeWithTag("lock_switch").performClick()
        waitText("security_message", "Not you")
        assertTrue(lock().settings.value.enabled, "a failed check leaves the lock on")
        compose.onNodeWithTag("lock_timeout_FIVE_MINUTES").performClick()
        compose.waitUntil(10_000) { fake.prompts.last() == "Change lock timing" }
        assertEquals(LockTimeout.ONE_MINUTE, lock().settings.value.timeout, "and a failed check leaves the timeout")
        fake.next = Authenticator.Result.Success
        compose.onNodeWithTag("lock_timeout_FIVE_MINUTES").performClick()
        compose.waitUntil(10_000) { lock().settings.value.timeout == LockTimeout.FIVE_MINUTES }
        compose.onNodeWithTag("lock_switch").performClick()
        compose.waitUntil(10_000) { !lock().settings.value.enabled }
        assertFalse(context.getSharedPreferences("app_lock", Context.MODE_PRIVATE).getBoolean("enabled", true))
    }

    @Test
    fun turningItOnIsRefusedEvenIfTheScreenLockDisappearedAfterTheScreenWasShown() {
        launch(); waitFor("add")
        fake.availability = Authenticator.Availability.NO_SCREEN_LOCK // removed in system settings while this screen was open
        scenario!!.onActivity { lock().enable(it, LockTimeout.ONE_MINUTE) }
        assertFalse(lock().settings.value.enabled)
        assertEquals(AppLock.NEEDS_SCREEN_LOCK, lock().message.value)
        assertTrue(fake.prompts.isEmpty(), "no prompt was opened")
    }

    // ---- locked ----

    @Test
    fun aColdStartIsLockedShowsNothingOfTheLedgerAndUnlocksOnlyAfterAuthentication() {
        runBlocking {
            container.write { it.store.save(Expense(EntryId("s"), System.currentTimeMillis(), AccountId("cash"), Money(12_300, Currency.INR)), EntryDetails(merchant = "Secret Merchant")) }
        }
        lockOn(LockTimeout.ONE_MINUTE)
        fake.next = null // the prompt stays open
        launch()
        waitFor("locked_screen")
        compose.waitUntil(10_000) { fake.pending != null }
        assertEquals(listOf("Unlock"), fake.prompts, "asked straight away, once")
        assertTrue(compose.onAllNodes(hasText("Secret Merchant"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("month_spend"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("add"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        fake.finish(Authenticator.Result.Success)
        waitFor("add"); waitText("month_spend", "₹123.00")
        waitGone("locked_screen")
    }

    @Test
    fun cancellingOrFailingStaysLockedAndTheButtonTriesAgain() {
        lockOn(LockTimeout.ONE_MINUTE)
        fake.next = Authenticator.Result.Failed("Cancelled", cancelled = true)
        launch(); waitFor("locked_screen")
        compose.waitUntil(10_000) { fake.prompts.size == 1 }
        assertTrue(compose.onAllNodes(hasTestTag("lock_message"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "no scolding for backing out")
        fake.next = Authenticator.Result.Failed("Too many attempts. Try again later.", cancelled = false)
        compose.onNodeWithTag("unlock").performClick()
        waitText("lock_message", "Too many attempts")
        fake.next = Authenticator.Result.Success
        compose.onNodeWithTag("unlock").performClick()
        waitFor("add"); waitGone("locked_screen")
        assertEquals(3, fake.prompts.size)
    }

    // ---- when it locks ----

    @Test
    fun leavingAndReturningLocksOnlyAfterTheChosenTimeAndUsesTheCorrectClock() {
        lockOn(LockTimeout.FIVE_MINUTES)
        launch(); waitFor("add")
        assertFalse(lock().locked.value, "unlocked by the fake at start")
        fake.next = null
        background(); now += 4 * minute; foreground()
        waitFor("add"); assertFalse(lock().locked.value, "away 4 of 5 minutes")
        background(); now += 5 * minute; foreground()
        waitFor("locked_screen"); assertTrue(lock().locked.value, "away the full 5 minutes")
        compose.waitUntil(10_000) { fake.pending != null }
    }

    @Test
    fun theImmediateSettingLocksAtOnceButARotationDoesNotLock() {
        lockOn(LockTimeout.IMMEDIATELY)
        launch(); waitFor("add")
        val promptsBefore = fake.prompts.size // the cold start asked once and was let in
        fake.next = null // if the app wrongly locked, the prompt would stay open instead of unlocking at once
        scenario!!.recreate()
        waitFor("add")
        assertFalse(lock().locked.value, "turning the phone is not leaving the app")
        assertEquals(promptsBefore, fake.prompts.size, "and nobody was asked to unlock again")
        background(); foreground()
        waitFor("locked_screen")
    }

    @Test
    fun aStopAndStartWhileThePromptIsOpenDoesNotLockAgainOrAskTwice() {
        lockOn(LockTimeout.IMMEDIATELY)
        fake.next = null
        launch(); waitFor("locked_screen")
        compose.waitUntil(10_000) { fake.pending != null }
        background(); now += 10 * minute; foreground() // older phones show the PIN on a separate screen, which pauses this one
        assertEquals(1, fake.prompts.size, "no second prompt on top of the first")
        fake.finish(Authenticator.Result.Success)
        waitFor("add")
        assertFalse(lock().locked.value)
    }

    @Test
    fun theAppIsNotLockedUnderYourFeetWhileYouAreConfirmingAChangeToTheLock() {
        lockOn(LockTimeout.IMMEDIATELY)
        launch(); waitFor("add"); openSecurity()
        fake.next = null
        compose.onNodeWithTag("lock_switch").performClick() // turn it off: the prompt opens and stays open
        compose.waitUntil(10_000) { fake.pending != null }
        background(); now += 10 * minute; foreground() // the phone's PIN screen paused this one
        assertFalse(lock().locked.value, "the confirmation is in progress; that is not leaving the app")
        assertTrue(compose.onAllNodes(hasTestTag("locked_screen"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        fake.finish(Authenticator.Result.Success)
        compose.waitUntil(10_000) { !lock().settings.value.enabled }
        assertFalse(lock().locked.value)
    }

    // ---- hidden from recents and screenshots ----

    @Test
    fun theWindowIsSecuredWhileTheLockIsOnAndNotOtherwise() {
        launch(); waitFor("add")
        fun secure(): Boolean { var f = false; scenario!!.onActivity { f = (it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0 }; return f }
        assertFalse(secure(), "lock off: ordinary window")
        openSecurity()
        compose.onNodeWithTag("lock_switch").performClick()
        compose.waitUntil(10_000) { secure() }
        compose.onNodeWithTag("lock_switch").performClick()
        compose.waitUntil(10_000) { !secure() }
    }

    private fun FakeAuthenticator.finishWhenPrompted() {
        compose.waitUntil(10_000) { prompts.isNotEmpty() || !lock().locked.value }
    }
}
