package app.expenses.core.parsers

import app.expenses.core.parsers.CaptureGate.Kind
import app.expenses.core.parsers.CaptureGate.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureGateTest {
    private val own = "app.expenses"

    @Test
    fun theSourceIsKnownFromThePackageAloneAndUnlistedAppsHaveNone() {
        assertEquals(Source(Kind.PAYMENT_APP, "PHONEPE"), CaptureGate.notificationSource("com.phonepe.app", own, testPost = false))
        assertEquals(Source(Kind.PAYMENT_APP, "GPAY"), CaptureGate.notificationSource("com.google.android.apps.nbu.paisa.user", own, testPost = false))
        assertEquals(Source(Kind.EMAIL_APP, "GMAIL"), CaptureGate.notificationSource("com.google.android.gm", own, testPost = false))
        assertEquals(Source(Kind.EMAIL_APP, "OUTLOOK"), CaptureGate.notificationSource("com.microsoft.office.outlook", own, testPost = false))
        assertNull(CaptureGate.notificationSource("com.whatsapp", own, testPost = false))
        assertNull(CaptureGate.notificationSource("", own, testPost = false))
        // a stand-in package is only honoured for a marked test post
        assertNull(CaptureGate.notificationSource("com.whatsapp", own, testPost = false, testStandsFor = "com.google.android.gm"))
    }

    @Test
    fun thisAppsOwnNotificationsAreNeverReadUnlessATestPostMarksThem() {
        assertNull(CaptureGate.notificationSource(own, own, testPost = false))
        assertNull(CaptureGate.notificationSource(own, own, testPost = false, testStandsFor = "com.google.android.gm"))
        assertEquals(Source(Kind.PAYMENT_APP, CaptureGate.TEST_POST_NAME), CaptureGate.notificationSource(own, own, testPost = true))
        assertEquals(Source(Kind.EMAIL_APP, "YAHOO"), CaptureGate.notificationSource(own, own, testPost = true, testStandsFor = "com.yahoo.mobile.client.android.mail"))
        assertEquals(Source(Kind.PAYMENT_APP, CaptureGate.TEST_POST_NAME), CaptureGate.notificationSource(own, own, testPost = true, testStandsFor = "com.phonepe.app"))
    }

    @Test
    fun eachSourceIsReadOnlyWhileItsOwnSwitchIsOn() {
        val pay = Source(Kind.PAYMENT_APP, "PAYTM")
        val mail = Source(Kind.EMAIL_APP, "GMAIL")
        assertFalse(CaptureGate.mayRead(pay, paymentAppsOn = false, emailOn = false))
        assertFalse(CaptureGate.mayRead(pay, paymentAppsOn = false, emailOn = true), "the email switch does not open payment apps")
        assertTrue(CaptureGate.mayRead(pay, paymentAppsOn = true, emailOn = false))
        assertFalse(CaptureGate.mayRead(mail, paymentAppsOn = false, emailOn = false))
        assertFalse(CaptureGate.mayRead(mail, paymentAppsOn = true, emailOn = false), "the payment-app switch does not open email")
        assertTrue(CaptureGate.mayRead(mail, paymentAppsOn = false, emailOn = true))
    }
}
