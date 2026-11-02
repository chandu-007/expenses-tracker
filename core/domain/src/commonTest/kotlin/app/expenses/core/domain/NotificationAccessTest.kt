package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationAccessTest {
    private val pkg = "app.example.tracker"
    private val full = "app.example.tracker/app.example.tracker.CaptureListener"

    @Test
    fun theFullAndTheShortComponentFormsBothCount() {
        assertTrue(NotificationAccess.granted(full, pkg))
        assertTrue(NotificationAccess.granted("app.example.tracker/.CaptureListener", pkg))
        assertTrue(NotificationAccess.granted("app.example.tracker/app.example.tracker.sub.Other", pkg))
    }

    @Test
    fun anEntryAmongSeveralIsFoundWhereverItSits() {
        val other = "com.vendor.one/com.vendor.one.Listener"
        val another = "com.vendor.two/.Svc"
        assertTrue(NotificationAccess.granted("$other:$full:$another", pkg))
        assertTrue(NotificationAccess.granted("$full:$other", pkg))
        assertTrue(NotificationAccess.granted("$other:$another:$full", pkg))
        assertTrue(NotificationAccess.granted(" $other : $full ", pkg), "spaces around an entry")
    }

    @Test
    fun aBarePackageEntryCounts() {
        assertTrue(NotificationAccess.granted("com.vendor.one/.A:app.example.tracker", pkg))
    }

    @Test
    fun nothingOrOtherAppsMeansNo() {
        assertFalse(NotificationAccess.granted(null, pkg))
        assertFalse(NotificationAccess.granted("", pkg))
        assertFalse(NotificationAccess.granted("   ", pkg))
        assertFalse(NotificationAccess.granted(":::", pkg))
        assertFalse(NotificationAccess.granted("com.vendor.one/com.vendor.one.Listener:com.vendor.two/.Svc", pkg))
    }

    @Test
    fun aPackageThatOnlySharesAPrefixOrSuffixIsNotThisApp() {
        assertFalse(NotificationAccess.granted("app.example.tracker.pro/.CaptureListener", pkg))
        assertFalse(NotificationAccess.granted("app.example/.CaptureListener", pkg))
        assertFalse(NotificationAccess.granted("x.app.example.tracker/.CaptureListener", pkg))
        assertFalse(NotificationAccess.granted("com.vendor.one/app.example.tracker.Fake", pkg), "the package is the part before the slash, not a class name that mentions it")
    }

    @Test
    fun malformedEntriesAreIgnoredAndDoNotHideAGoodOne() {
        assertFalse(NotificationAccess.granted("app.example.tracker/", pkg), "no class")
        assertFalse(NotificationAccess.granted("/app.example.tracker.CaptureListener", pkg), "no package")
        assertEquals(true, NotificationAccess.granted("/:app.example.tracker/:$full", pkg))
    }
}
