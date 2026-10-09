package app.expenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.CaptureEventOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CaptureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() = resetApp()

    private val debit = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private fun ingest(sender: String, body: String, at: Long = 1_000L) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun pending() = runBlocking { container.write { it.inbox.list() } }!!
    private fun events() = runBlocking { container.write { ok -> ok.db.schemaQueries.captureEventCounts(0).executeAsList().associate { (it.sender_root to it.outcome) to it.n } } }!!

    @Test
    fun aBankTransactionBecomesOnePendingItemWithTheParsedFieldsAndItsText() {
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("AX-HDFCBK-S", debit, 5_000))
        val i = pending().single()
        assertEquals("HDFC Bank", i.issuer); assertEquals(125_000, i.amountMinor); assertEquals("DEBIT", i.direction); assertEquals("1234", i.accountLast4)
        assertEquals("628374659283", i.reference); assertEquals("2026-10-05", i.txnDate); assertTrue(i.viaUpi); assertEquals(5_000, i.receivedAt)
        assertTrue(i.body!!.contains("Rs.1,250.00"))
    }

    @Test
    fun theSameTextDeliveredTwiceIsOneItemButTheSameWordsAtAnotherTimeAreAnotherTransaction() {
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("AX-HDFCBK-S", debit, 5_000))
        assertEquals(CaptureEventOutcome.DUPLICATE, ingest("AX-HDFCBK-S", debit, 5_000))
        assertEquals(CaptureEventOutcome.DUPLICATE, ingest("AX-HDFCBK-S", "  $debit  ", 5_000), "spacing does not make it new")
        assertEquals(1, pending().size)
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("AX-HDFCBK-S", debit, 9_000_000))
        assertEquals(2, pending().size)
    }

    @Test
    fun aPaymentAppPostingTheSameNotificationAgainWithinSecondsIsOnePaymentButLaterOrOtherWordsAreAnother() {
        fun notify(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.NOTIFICATION, "PHONEPE", body, at) }
        val words = "Chandu: Chandu. sent ₹10 to you."
        assertEquals(CaptureEventOutcome.CAPTURED, notify(words, 100_000))
        assertEquals(CaptureEventOutcome.DUPLICATE, notify(words, 103_000), "the same words 3 s later: the app posted it twice")
        assertEquals(CaptureEventOutcome.DUPLICATE, notify("  $words ", 109_000), "spacing does not matter; 9 s is inside the window")
        assertEquals(1, pending().size)
        assertEquals(CaptureEventOutcome.CAPTURED, notify(words, 100_000 + Capture.REPOST_WINDOW_MS + 1), "just past the window: a second payment")
        assertEquals(CaptureEventOutcome.CAPTURED, notify("Chandu: Chandu. sent ₹20 to you.", 101_000), "other words straight after: a different payment")
        assertEquals(3, pending().size)
    }

    @Test
    fun theSameBankTextTwiceWithinSecondsIsTwoPaymentsBecauseOnlyNotificationsRepost() {
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("AX-HDFCBK-S", debit, 5_000))
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("AX-HDFCBK-S", debit, 6_000))
        assertEquals(2, pending().size)
    }

    @Test
    fun personsOtpsNoticesAndUnknownHeadersAddNothingAndLeaveOnlyCounts() {
        assertEquals(CaptureEventOutcome.PERSONAL_SENDER, ingest("+919876543210", debit))
        assertEquals(CaptureEventOutcome.NOT_ALLOWLISTED, ingest("AX-SHOPXX-S", debit))
        assertEquals(CaptureEventOutcome.OTP, ingest("AX-HDFCBK-S", "123456 is the OTP for your transaction of Rs.1,250.00 at AMAZON"))
        assertEquals(CaptureEventOutcome.NOT_A_TRANSACTION, ingest("AX-HDFCBK-S", "Update your KYC to continue"))
        assertEquals(CaptureEventOutcome.DECLINED, ingest("AX-HDFCBK-S", "Transaction of Rs.500.00 on card ending 4321 was declined due to insufficient funds."))
        assertEquals(emptyList(), pending())
        val e = events()
        assertEquals(1L, e[("" to "PERSONAL_SENDER")]); assertEquals(1L, e[("SHOPXX" to "NOT_ALLOWLISTED")]); assertEquals(1L, e[("HDFCBK" to "OTP")])
        // nothing in the database, outside the pending item, holds the words or numbers of a text
        val dump = runBlocking { container.write { ok -> ok.db.schemaQueries.captureEventCounts(0).executeAsList().joinToString { it.sender_root + it.outcome } } }!!
        assertTrue(!dump.contains("1250") && !dump.contains("9876"))
    }

    @Test
    fun aHeaderThePersonAddedIsCapturedFromThenOn() {
        assertEquals(CaptureEventOutcome.NOT_ALLOWLISTED, ingest("VM-MYCOOP-S", debit))
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_EXTRA_ROOTS, "mycoop, otherco") } }
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("VM-MYCOOP-S", debit, 2_000))
        assertEquals("MYCOOP", pending().single().issuer)
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_EXTRA_ROOTS, "mycoop\notherco\n") } }
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("VM-OTHERCO-S", debit, 3_000), "one header per line works too")
    }

    @Test
    fun theFingerprintSeparatesSourcesSendersTimesAndWordsButNotSpacingOrCase() {
        val f = { s: String, who: String, t: Long, b: String -> Capture.fingerprint(s, who, t, b) }
        val base = f("SMS", "AX-HDFCBK-S", 1, "a b")
        assertEquals(base, f("SMS", " ax-hdfcbk-s ", 1, "a   b "))
        assertTrue(listOf(f("NOTIFICATION", "AX-HDFCBK-S", 1, "a b"), f("SMS", "AX-ICICIB-S", 1, "a b"), f("SMS", "AX-HDFCBK-S", 2, "a b"), f("SMS", "AX-HDFCBK-S", 1, "a c")).none { it == base })
        assertNull(null)
    }
}
