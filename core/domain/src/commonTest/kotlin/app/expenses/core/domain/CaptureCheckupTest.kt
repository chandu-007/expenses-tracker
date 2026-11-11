package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureCheckupTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private fun rows(i: CheckupInputs) = CaptureCheckup.rows(i)
    private fun CheckupInputs.row(id: String) = rows(this).firstOrNull { it.id == id }
    private val working = CheckupInputs(CaptureWay.TEXTS, now, smsSwitchOn = true, smsPermission = true)

    @Test
    fun theTextPermissionAndTheSwitchAreFourCombinationsEachWithItsOwnWordsAndButton() {
        val grid = listOf(
            Triple(false, false, "Reading bank texts is not allowed"), Triple(false, true, "Reading bank texts is not allowed"),
            Triple(true, false, "Reading bank texts is switched off"), Triple(true, true, "Reading bank texts is on"),
        )
        for ((permission, switch, title) in grid) {
            val r = working.copy(smsPermission = permission, smsSwitchOn = switch).row("sms")!!
            assertEquals(title, r.title, "permission=$permission switch=$switch")
            assertEquals(if (permission && switch) CheckLevel.GOOD else CheckLevel.ATTENTION, r.level)
            val acts = r.actions.map { it.act }
            when {
                !permission -> assertEquals(listOf(CheckAct.ALLOW_TEXTS, CheckAct.OPEN_APP_SETTINGS), acts)
                !switch -> assertEquals(listOf(CheckAct.TURN_ON_TEXTS), acts)
                else -> assertTrue(acts.isEmpty())
            }
        }
    }

    @Test
    fun notificationAccessHasFiveStatesFromOffToConnected() {
        val none = working.row("listener")!!
        assertEquals(CheckLevel.NOTE, none.level, "payment apps are optional while the Android build also reads texts")
        val noAccess = working.copy(notifSwitchOn = true).row("listener")!!
        assertEquals(CheckLevel.ATTENTION, noAccess.level); assertEquals(CheckAct.OPEN_NOTIFICATION_ACCESS, noAccess.actions.single().act)
        val notConnected = working.copy(notifSwitchOn = true, notifAccess = true, listenerConnected = false).row("listener")!!
        assertEquals(CheckLevel.ATTENTION, notConnected.level); assertEquals(listOf(CheckAct.RECONNECT_LISTENER, CheckAct.OPEN_NOTIFICATION_ACCESS), notConnected.actions.map { it.act })
        val ok = working.copy(notifSwitchOn = true, notifAccess = true, listenerConnected = true).row("listener")!!
        assertEquals(CheckLevel.GOOD, ok.level)
        val emailOnly = working.copy(emailSwitchOn = true, notifAccess = true, listenerConnected = true).row("listener")!!
        assertEquals("Reading bank emails is on", emailOnly.title)
        assertEquals("Reading payment apps and bank emails is on", working.copy(notifSwitchOn = true, emailSwitchOn = true, notifAccess = true, listenerConnected = true).row("listener")!!.title)
    }

    @Test
    fun thePlayBuildHasNoTextRowAndNeedsSomethingSwitchedOn() {
        val play = CheckupInputs(CaptureWay.APPS_ONLY, now)
        val r = rows(play)
        assertNull(r.firstOrNull { it.id == "sms" }, "no text permission exists in this build")
        assertEquals(CheckLevel.ATTENTION, play.row("listener")!!.level); assertEquals(CheckAct.TURN_ON_PAYMENT_APPS, play.row("listener")!!.actions.single().act)
        assertEquals(CheckLevel.GOOD, play.copy(notifSwitchOn = true, notifAccess = true, listenerConnected = true).row("listener")!!.level)
    }

    @Test
    fun remindersAppearOnlyWhereThePermissionExists() {
        assertNull(working.row("reminders"), "Android 12 and earlier: no such permission")
        assertEquals(CheckLevel.GOOD, working.copy(postNotifications = true).row("reminders")!!.level)
        val denied = working.copy(postNotifications = false).row("reminders")!!
        assertEquals(CheckLevel.ATTENTION, denied.level); assertEquals(CheckAct.ALLOW_REMINDERS, denied.actions.single().act)
        assertTrue(denied.detail.contains("Capture itself still works"))
    }

    @Test
    fun batteryIsThreeStatesAndUnknownIsLeftOut() {
        assertEquals(CheckLevel.GOOD, working.copy(battery = BatteryState.UNRESTRICTED).row("battery")!!.level)
        val optimised = working.copy(battery = BatteryState.OPTIMISED).row("battery")!!
        assertEquals(CheckLevel.NOTE, optimised.level, "Android's usual setting is not a fault"); assertEquals(CheckAct.OPEN_BATTERY, optimised.actions.single().act)
        assertEquals(CheckLevel.ATTENTION, working.copy(battery = BatteryState.RESTRICTED).row("battery")!!.level)
        assertNull(working.copy(battery = BatteryState.UNKNOWN).row("battery"))
    }

    @Test
    fun theMakerLineIsAChecklistTheAppCannotCheckSoItNeedsATick() {
        val samsung = working.copy(maker = PhoneMaker.SAMSUNG)
        val open = samsung.row("battery_list")!!
        assertEquals(CheckLevel.ATTENTION, open.level)
        assertTrue(open.detail.contains("Settings, Battery, Background usage limits, Deep sleeping apps"), "the exact path")
        assertEquals(CaptureCheckup.BATTERY_TICK_KEY, open.tick!!.key); assertEquals(false, open.tick!!.ticked)
        val done = samsung.copy(batteryListTicked = true).row("battery_list")!!
        assertEquals(CheckLevel.GOOD, done.level); assertEquals(true, done.tick!!.ticked)
        val strict = working.copy(maker = PhoneMaker.STRICT)
        assertEquals(CheckLevel.NOTE, strict.row("battery_list")!!.level, "another maker's list is a note, not a fault")
        assertNull(working.copy(maker = PhoneMaker.OTHER).row("battery_list"))
        assertEquals(PhoneMaker.SAMSUNG, PhoneMaker.of(" Samsung ")); assertEquals(PhoneMaker.STRICT, PhoneMaker.of("OnePlus")); assertEquals(PhoneMaker.OTHER, PhoneMaker.of("Google")); assertEquals(PhoneMaker.OTHER, PhoneMaker.of(null))
    }

    @Test
    fun eachBankIsSaidToBeRecognisedOrNotSeenYetWithItsSenderNames() {
        val i = working.copy(banks = listOf(BankExpectation("HDFC Bank", listOf("HDFCBK", "HDFCBN"), true, now - 2 * day), BankExpectation("ICICI Bank", listOf("ICICIB", "ICICIT"), false, null)))
        val hdfc = i.row("bank_HDFC Bank")!!
        assertEquals(CheckLevel.GOOD, hdfc.level); assertEquals("HDFC Bank: texts from HDFCBK or HDFCBN recognised", hdfc.title); assertTrue(hdfc.detail.contains("2 days ago"))
        val icici = i.row("bank_ICICI Bank")!!
        assertEquals(CheckLevel.NOTE, icici.level); assertEquals("No text from ICICI Bank seen yet", icici.title); assertEquals(CheckAct.OPEN_SENDERS, icici.actions.single().act)
        // no bank at all: one note that says what to do, and what is trusted
        val none = working.copy(trustedNames = listOf("MYBANK"), builtInCount = 49).row("banks")!!
        assertEquals(CheckLevel.NOTE, none.level); assertTrue(none.detail.contains("MYBANK") && none.detail.contains("49"))
    }

    @Test
    fun skippedSenderNamesNeedAttentionWithOneButtonEachUpToThree() {
        assertEquals(CheckLevel.GOOD, working.row("skipped")!!.level)
        val one = working.copy(skipped = listOf(SkippedName("ICICIX", 3))).row("skipped")!!
        assertEquals(CheckLevel.ATTENTION, one.level); assertEquals("1 sender name was skipped", one.title)
        assertEquals(listOf(CheckAction(CheckAct.TRUST_SENDER, "Treat ICICIX as my bank", "ICICIX")), one.actions)
        val five = working.copy(skipped = (1..5).map { SkippedName("NAME$it", 1) }).row("skipped")!!
        assertEquals("5 sender names were skipped", five.title); assertEquals(3, five.actions.size)
    }

    @Test
    fun accountDigitsAreAskedForOrPraisedOrLeftOut() {
        assertNull(working.row("digits"), "no bank account: nothing to say")
        val missing = working.copy(accountsWithoutDigits = listOf("HDFC Savings", "ICICI Card")).row("digits")!!
        assertEquals(CheckLevel.ATTENTION, missing.level); assertEquals("2 accounts need their last digits", missing.title); assertEquals(CheckAct.OPEN_ACCOUNTS, missing.actions.single().act)
        assertTrue(missing.detail.contains("HDFC Savings, ICICI Card") && missing.detail.contains("3 or 4"))
        assertEquals("1 account needs its last digits", working.copy(accountsWithoutDigits = listOf("A")).row("digits")!!.title)
        assertTrue(working.copy(accountsWithoutDigits = (1..6).map { "Acct $it" }).row("digits")!!.detail.contains("and 2 more"))
        assertEquals(CheckLevel.GOOD, working.copy(accountsWithDigits = 2).row("digits")!!.level)
    }

    @Test
    fun activityCountsTheLastWeekAndTheReasonsForSkipping() {
        val busy = working.copy(foundLast7Days = 5, lastFoundAt = now - 3_600_000L, lastSeenAt = now - 60_000L, ignored = IgnoredCounts(otps = 3, personal = 1, notOnList = 2)).row("activity")!!
        assertEquals(CheckLevel.GOOD, busy.level); assertEquals("5 payments found in the last 7 days", busy.title)
        assertTrue(busy.detail.contains("3 one-time passwords") && busy.detail.contains("1 text from a person") && busy.detail.contains("2 texts from names not on the list"), busy.detail)
        assertEquals("1 payment found in the last 7 days", working.copy(foundLast7Days = 1, lastFoundAt = now).row("activity")!!.title)
        val quiet = working.copy(lastFoundAt = now - 20 * day).row("activity")!!
        assertEquals(CheckLevel.NOTE, quiet.level); assertEquals("Nothing found in the last 7 days", quiet.title); assertTrue(quiet.detail.contains("Nothing was skipped"))
        assertEquals("Nothing found yet", working.row("activity")!!.title)
    }

    @Test
    fun theIphoneHasNoTextRowsButAShortcutsChecklistUntilAMessageArrives() {
        val phone = CheckupInputs(CaptureWay.SHORTCUTS, now)
        val ids = rows(phone).map { it.id }
        assertTrue("sms" !in ids && "listener" !in ids && "reminders" !in ids && "battery" !in ids)
        assertEquals(listOf("shortcuts", "shortcuts_shortcut", "shortcuts_automation", "shortcuts_locked"), ids.filter { it.startsWith("shortcuts") })
        assertEquals(CheckLevel.ATTENTION, phone.row("shortcuts")!!.level); assertEquals("No message has reached the app yet", phone.row("shortcuts")!!.title)
        assertEquals(listOf(CheckAct.OPEN_SHORTCUTS, CheckAct.OPEN_CAPTURE), phone.row("shortcuts")!!.actions.map { it.act })
        val ticked = phone.copy(shortcutTicks = setOf("shortcut", "automation", "locked"))
        assertTrue(listOf("shortcuts_shortcut", "shortcuts_automation", "shortcuts_locked").all { ticked.row(it)!!.level == CheckLevel.GOOD })
        // a message in the last week proves the automation: the checklist goes
        val received = phone.copy(lastSeenAt = now - 2 * 3_600_000L)
        assertEquals(CheckLevel.GOOD, received.row("shortcuts")!!.level); assertNull(received.row("shortcuts_locked"))
        val old = phone.copy(lastSeenAt = now - 9 * day)
        assertEquals("No message reached the app in the last 7 days", old.row("shortcuts")!!.title)
    }

    @Test
    fun aPlatformWithNothingToCaptureHasNoRows() {
        assertTrue(rows(CheckupInputs(CaptureWay.NONE, now, smsPermission = true)).isEmpty())
    }

    @Test
    fun theHeadlineCountsOnlyWhatNeedsAttentionAndNeverRestsOnColour() {
        fun row(level: CheckLevel) = CheckRow("x", level, "t", "d")
        assertEquals("Everything looks fine", CaptureCheckup.headline(listOf(row(CheckLevel.GOOD), row(CheckLevel.NOTE))).text)
        assertEquals(1, CaptureCheckup.headline(listOf(row(CheckLevel.GOOD), row(CheckLevel.NOTE))).notes)
        assertEquals("1 thing needs attention", CaptureCheckup.headline(listOf(row(CheckLevel.ATTENTION), row(CheckLevel.GOOD))).text)
        assertEquals("3 things need attention", CaptureCheckup.headline(List(3) { row(CheckLevel.ATTENTION) } + row(CheckLevel.NOTE)).text)
        assertEquals(listOf("OK", "Fix this", "Note"), CheckLevel.entries.map { CaptureCheckup.levelWord(it) })
    }

    @Test
    fun everyCombinationOfTheSwitchesAndPermissionsGivesStableUniqueRowsAndAHeadlineThatMatchesThem() {
        var n = 0
        for (way in listOf(CaptureWay.TEXTS, CaptureWay.APPS_ONLY, CaptureWay.SHORTCUTS)) for (sms in listOf(false, true)) for (perm in listOf(false, true))
            for (notif in listOf(false, true)) for (email in listOf(false, true)) for (access in listOf(false, true)) for (conn in listOf(false, true))
                for (post in listOf(null, false, true)) for (bat in BatteryState.entries) for (maker in PhoneMaker.entries) for (tick in listOf(false, true)) {
                    val i = CheckupInputs(way, now, sms, perm, notif, email, access, conn, post, bat, maker, tick)
                    val r = rows(i); n++
                    assertEquals(r.size, r.map { it.id }.toSet().size, "unique ids for $i")
                    assertTrue(r.all { it.title.isNotBlank() && (it.level == CheckLevel.GOOD || it.detail.isNotBlank() || it.tick != null) }, "every problem is explained: $i")
                    assertTrue(r.filter { it.level == CheckLevel.ATTENTION }.all { it.actions.isNotEmpty() || it.tick != null }, "every thing to fix has a one-tap action or a tick: $i")
                    assertEquals(r.count { it.level == CheckLevel.ATTENTION }, CaptureCheckup.headline(r).attention)
                }
        assertTrue(n > 3000, "all combinations ran: $n")
    }

    @Test
    fun nothingInTheTextsOfTheRowsCarriesADigitRunLikeAnAccountNumber() {
        val i = working.copy(banks = listOf(BankExpectation("HDFC Bank", listOf("HDFCBK"), true, now)), accountsWithoutDigits = listOf("HDFC Savings"), skipped = listOf(SkippedName("ICICIX", 2)), foundLast7Days = 2, lastFoundAt = now)
        for (r in rows(i)) assertTrue(!Regex("""\d{5,}""").containsMatchIn(r.title + r.detail), r.id)
    }
}
