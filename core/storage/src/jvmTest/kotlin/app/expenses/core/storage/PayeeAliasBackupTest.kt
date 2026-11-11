package app.expenses.core.storage

import app.expenses.core.domain.PayeeAliases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The names the person joined as one shop travel in a backup's `settings` like card limits do, restore only where the phone has none, and never make a cycle. Invented names. */
class PayeeAliasBackupTest {
    private fun roundTrip(d: Device): BackupData = BackupFile.decode(BackupFile.encode(d.backup.export(), 1L)).second

    @Test
    fun anAliasIsWrittenToTheBackupAndComesBackOnANewPhoneAndAgainChangesNothing() {
        val a = Device("a"); a.populate()
        CaptureRuleStore(a.db).addAlias("Swiggy Instamart", "Swiggy"); CaptureRuleStore(a.db).addAlias("Ravi K", "Ravi Kumar")
        val data = roundTrip(a)
        assertEquals("swiggy\tSwiggy Instamart\tSwiggy", data.settings["payee_alias:swiggyinstamart"])
        assertEquals(setOf("payee_alias:swiggyinstamart", "payee_alias:ravik"), data.settings.keys.filter { it.startsWith("payee_alias:") }.toSet())
        val b = Device("b")
        b.backup.restore(data)
        assertEquals(CaptureRuleStore(a.db).aliases().all, CaptureRuleStore(b.db).aliases().all)
        assertEquals(a.backup.export(), b.backup.export(), "exporting the restored phone gives the same backup")
        b.backup.restore(data)
        assertEquals(2, CaptureRuleStore(b.db).aliases().all.size, "restoring twice adds nothing")
    }

    @Test
    fun aBackupWithoutAliasesRestoresExactlyAsBeforeAndLeavesThePhonesOwnAliasesAlone() {
        val a = Device("a"); a.populate()
        assertTrue(roundTrip(a).settings.keys.none { it.startsWith("payee_alias:") })
        val b = Device("b"); CaptureRuleStore(b.db).addAlias("Zepto Cafe", "Zepto")
        b.backup.restore(roundTrip(a))
        assertEquals(listOf("zeptocafe"), CaptureRuleStore(b.db).aliases().all.map { it.from })
    }

    @Test
    fun aNameThisPhoneAlreadyHasAnAliasForKeepsItAndACycleASelfAliasAndGarbageAreLeftOut() {
        val b = Device("b")
        CaptureRuleStore(b.db).addAlias("Swiggy Instamart", "Zomato")
        val incoming = BackupData(settings = mapOf(
            "payee_alias:swiggyinstamart" to "swiggy\tSwiggy Instamart\tSwiggy", // the phone has its own for this name
            "payee_alias:zomato" to "swiggyinstamart\tZomato\tSwiggy Instamart", // would make a cycle with the one here
            "payee_alias:amazonbiz" to "amazon\tAmazon Biz\tAmazon", // fine
            "payee_alias:selfy" to "selfy", // a name counting as itself
            "payee_alias:x" to "amazon", // not a name
            "payee_alias:nothing" to "", // nothing
            "payee_alias_other" to "amazon", // not this prefix
            "skipped_sender:ACMEBK" to "4|1000|S",
        ))
        b.backup.restore(incoming)
        val have = CaptureRuleStore(b.db).aliases()
        assertEquals(setOf("swiggyinstamart", "amazonbiz"), have.all.map { it.from }.toSet())
        assertEquals("zomato", have.resolve("swiggyinstamart"), "the phone's own choice stands")
        assertEquals("amazon", have.resolve("amazonbiz"))
        assertNull(b.db.schemaQueries.settingValue("payee_alias_other").executeAsOneOrNull()); assertNull(b.db.schemaQueries.settingValue("skipped_sender:ACMEBK").executeAsOneOrNull())
    }

    @Test
    fun aHandMadeFileIsReadInKeyOrderSoTheSameFileAlwaysGivesTheSameAliases() {
        val file = BackupData(settings = mapOf("payee_alias:bb" to "aa\tBB\tAA", "payee_alias:aa" to "bb\tAA\tBB"))
        val results = (1..2).map { val d = Device("d$it"); d.backup.restore(file); CaptureRuleStore(d.db).aliases().all.map { a -> a.from to a.to } }
        assertEquals(results[0], results[1])
        assertEquals(listOf("aa" to "bb"), results[0], "aa counts as bb; bb counting as aa would be a cycle")
    }

    @Test
    fun noMoreThanTheCapIsTakenAndAnOldReaderOfTheFormatIgnoresTheKeys() {
        val many = (0 until BackupStore.MAX_ALIASES + 50).associate { i -> "payee_alias:name${i.toString().padStart(5, '0')}" to "shop\tName $i\tShop" }
        val d = Device("d"); d.backup.restore(BackupData(settings = many))
        assertEquals(BackupStore.MAX_ALIASES, CaptureRuleStore(d.db).aliases().all.size)
        // an app without aliases only takes the settings it knows by name or prefix (`SETTINGS`, `card_limit:`, `rate_`): the alias keys are none of these
        assertTrue(BackupStore.SETTINGS.none { it.startsWith("payee_alias") } && !"payee_alias:x".startsWith(BackupStore.CARD_LIMIT_PREFIX) && !"payee_alias:x".startsWith(BackupStore.RATE_PREFIX))
        assertEquals(PayeeAliases.NONE.isEmpty, true)
    }
}
