package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionClaimsTest {
    private fun ids(l: List<PermissionClaims.Claim>) = l.map { it.id }

    @Test
    fun aManifestWithNoSensitivePermissionClaimsEveryKindAsNotRequested() {
        val r = PermissionClaims.of(listOf("android.permission.POST_NOTIFICATIONS", "android.permission.USE_BIOMETRIC", "android.permission.WAKE_LOCK"))
        assertEquals(PermissionClaims.SENSITIVE.map { it.id }, ids(r.notRequested))
        assertTrue(r.requested.isEmpty())
    }

    @Test
    fun aDeclaredPermissionMovesItsKindToRequestedAndOnlyThatKind() {
        val r = PermissionClaims.of(listOf("android.permission.RECEIVE_SMS", "android.permission.POST_NOTIFICATIONS"))
        assertEquals(listOf("texts"), ids(r.requested))
        assertTrue("texts" !in ids(r.notRequested) && "internet" in ids(r.notRequested) && "contacts" in ids(r.notRequested))
        assertEquals(PermissionClaims.SENSITIVE.size, r.requested.size + r.notRequested.size)
    }

    @Test
    fun internetIsOnlyNotRequestedWhileTheManifestLacksIt() {
        assertTrue("internet" in ids(PermissionClaims.of(emptyList()).notRequested))
        val with = PermissionClaims.of(listOf("android.permission.INTERNET"))
        assertEquals(listOf("internet"), ids(with.requested))
        assertTrue("internet" !in ids(with.notRequested))
    }

    @Test
    fun anyOnePermissionOfAKindIsEnoughToRequestIt() {
        assertEquals(listOf("phone"), ids(PermissionClaims.of(listOf("android.permission.READ_CALL_LOG")).requested))
        assertEquals(listOf("location"), ids(PermissionClaims.of(listOf("android.permission.ACCESS_COARSE_LOCATION")).requested))
        assertEquals(listOf("files"), ids(PermissionClaims.of(listOf("android.permission.READ_MEDIA_IMAGES")).requested))
    }

    @Test
    fun noPermissionBelongsToTwoKindsAndTheKindsAreNamed() {
        val all = PermissionClaims.SENSITIVE.flatMap { it.permissions }
        assertEquals(all.size, all.toSet().size)
        assertTrue(PermissionClaims.SENSITIVE.all { it.label.isNotBlank() && it.permissions.isNotEmpty() })
    }

    @Test
    fun theSummaryCountsWhatIsAndIsNotAskedFor() {
        assertEquals("This build asks for none of the 8 kinds of access checked here.", PermissionClaims.summary(PermissionClaims.of(emptyList())))
        assertEquals(
            "Of 8 kinds of access checked here, this build asks for 1 (your text messages) and not for the other 7.",
            PermissionClaims.summary(PermissionClaims.of(listOf("android.permission.RECEIVE_SMS"))),
        )
        assertEquals("This build asks for all 8 kinds of access checked here.", PermissionClaims.summary(PermissionClaims.of(PermissionClaims.SENSITIVE.flatMap { it.permissions })))
    }
}
