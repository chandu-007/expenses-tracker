package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UI S8: the widgets' pace, the "Allow widgets" switch, the launcher shortcuts' words and what the iPhone's widgets are given. Pure data; all amounts invented. */
class WidgetFeedTest {
    private val inr = Currency.INR
    private fun rs(n: Long) = Money(n * 100, inr)
    private val november = DatePeriod(LocalDate(2026, 11, 1), LocalDate(2026, 12, 1))
    private fun template(id: String, name: String, rupees: Long?) = Template(id, name, rupees?.let(::rs), CategoryId("Food"), AccountId("cash"))
    private val chai = template("t1", "Chai", 20)
    private val auto = template("t2", "Auto", 80)
    private val noAmount = template("t3", "Groceries", null)
    private fun digitFree(s: String) = s.none { it.isDigit() }

    // ---- pace ----

    @Test
    fun thePaceHasTheWordTheLevelAndWhereTodayIsOnTheBar() {
        // day 16 of 30: 533 permille of the period gone; 31,822 of 60,000 spent is 530 permille: on pace
        val on = WidgetFeeds.pace(rs(31_822), rs(60_000), november, LocalDate(2026, 11, 16), Money.zero(inr))!!
        assertEquals(PaceLevel.ON_PACE, on.level); assertEquals("On pace", on.word); assertEquals(533, on.todayPermille)
        val ahead = WidgetFeeds.pace(rs(36_000), rs(60_000), november, LocalDate(2026, 11, 16), Money.zero(inr))!!
        assertEquals(PaceLevel.A_LITTLE_AHEAD, ahead.level); assertEquals("A little ahead", ahead.word)
        val over = WidgetFeeds.pace(rs(61_655), rs(60_000), november, LocalDate(2026, 11, 20), Money.zero(inr))!!
        assertEquals(PaceLevel.OVER, over.level); assertEquals("Over by ₹1,655", over.word, "the exact amount over, without paise when there are none")
        assertEquals(PaceLevel.EARLY, WidgetFeeds.pace(rs(5_000), rs(60_000), november, LocalDate(2026, 11, 1), Money.zero(inr))!!.level, "the first days say nothing yet")
        assertEquals(1000, WidgetFeeds.pace(rs(1), rs(60_000), november, LocalDate(2026, 11, 30), null)!!.todayPermille)
        assertNull(WidgetFeeds.pace(rs(1), null, november, LocalDate(2026, 11, 3), null)); assertNull(WidgetFeeds.pace(rs(1), Money.zero(inr), november, LocalDate(2026, 11, 3), null))
    }

    @Test
    fun billsStillToComeMakeAnOnPaceMonthALittleAhead() {
        val p = WidgetFeeds.pace(rs(30_000), rs(60_000), november, LocalDate(2026, 11, 16), rs(31_000))!!
        assertEquals(PaceLevel.A_LITTLE_AHEAD, p.level)
    }

    @Test
    fun theBudgetBarCarriesThePaceAndSpeaksTheExactAmounts() {
        val pace = WidgetFeeds.pace(rs(31_822), rs(60_000), november, LocalDate(2026, 11, 16), null)
        val b = WidgetSummary.budget(rs(60_000), rs(31_822), pace)!!
        assertEquals("₹28.1K left of ₹60K", b.text, "the short words stay as they were (cut, never rounded up)")
        assertEquals(pace, b.pace)
        assertEquals("₹28,178.00 left of ₹60,000.00. On pace. Today is 53 percent of the way through the period.", b.spoken)
        val over = WidgetSummary.budget(rs(60_000), rs(61_655))!!
        assertEquals("₹1,655.00 over ₹60,000.00", over.spoken, "without a pace, only the amounts")
        assertNull(WidgetSummary.budget(rs(60_000), rs(1))!!.pace, "the old call draws no marker")
    }

    @Test
    fun thePerDayLineSaysWhatKeepsYouOnPaceAndNothingWithoutSomethingToSpare() {
        assertEquals("₹1,565.00 a day keeps you on pace", WidgetFeeds.perDayLine(rs(1_565)))
        assertNull(WidgetFeeds.perDayLine(Money.zero(inr))); assertNull(WidgetFeeds.perDayLine(null))
        assertTrue(digitFree(AmountMask.mask(WidgetFeeds.perDayLine(rs(1_565))!!)), "the masked line has no digit")
    }

    // ---- the switch ----

    @Test
    fun widgetsAreAllowedUnlessThePersonTurnedThemOff() {
        assertTrue(WidgetAccess.allowed(null), "every install before UI S8 has no setting: nothing changes on an update")
        assertTrue(WidgetAccess.allowed("1")); assertFalse(WidgetAccess.allowed("0"))
        assertEquals("0", WidgetAccess.value(false)); assertTrue(WidgetAccess.allowed(WidgetAccess.value(true)))
    }

    // ---- launcher shortcuts ----

    @Test
    fun theShortcutsAreTheFirstThreeQuickEntriesWithAnAmountWithTheAmountOnlyWhenNothingHidesIt() {
        val five = listOf(chai, noAmount, auto, template("t4", "Lunch", 120), template("t5", "Metro", 40))
        val shown = QuickShortcuts.labels(five, allowed = true, hide = false, locked = false)
        assertEquals(listOf("t1", "t2", "t4"), shown.map { it.templateId }, "no amount, no one-tap shortcut")
        assertEquals(ShortcutLabel("t1", "Chai", "Add Chai ₹20"), shown[0])
        for ((hide, locked) in listOf(true to false, false to true, true to true)) {
            val masked = QuickShortcuts.labels(five, allowed = true, hide = hide, locked = locked)
            assertTrue(masked.all { digitFree(it.long) && digitFree(it.short) && '₹' !in it.long }, "hide=$hide locked=$locked: $masked")
            assertEquals("Add Chai", masked[0].long)
        }
        assertTrue(QuickShortcuts.labels(five, allowed = false, hide = false, locked = false).isEmpty(), "widgets not allowed: no shortcut at all")
    }

    @Test
    fun aLongNameIsShortenedButAnAmountIsNeverCut() {
        val long = template("t9", "Monthly society maintenance", 12_345)
        val l = QuickShortcuts.labels(listOf(long), allowed = true, hide = false, locked = false).single()
        assertTrue(l.long.length <= QuickShortcuts.LONG_MAX && l.long.endsWith(" ₹12,345"), l.long)
        assertTrue(l.short.length <= QuickShortcuts.SHORT_MAX && l.short.endsWith("…"), l.short)
        assertEquals("Monthly s…", QuickShortcuts.labels(listOf(long), true, hide = true, locked = false).single().short)
        val mid = template("t8", "Petrol for the bike", 500)
        assertEquals("Petrol for the bike ₹500", QuickShortcuts.labels(listOf(mid), true, false, false).single().long, "'Add' is left out when there is no room for it")
    }

    // ---- what the iPhone's widgets are given ----

    private fun feed(allowed: Boolean = true, hide: Boolean = false, locked: Boolean = false, limit: Money? = rs(60_000)) = WidgetFeeds.of(
        allowed, hide, locked, spentToday = rs(1_240), month = rs(31_822), perDay = rs(1_565), limit = limit, budgetSpent = rs(31_822),
        pace = WidgetFeeds.pace(rs(31_822), limit, november, LocalDate(2026, 11, 16), null), templates = listOf(chai, noAmount, auto),
    )

    @Test
    fun withNothingHidingThemTheRealFiguresAndThePaceAreGiven() {
        val f = feed()
        assertTrue(f.allowed && f.canToggle)
        assertEquals("₹1,240.00", f.todaySpent); assertEquals("₹31,822.00", f.monthSpent); assertEquals("₹1,565.00 a day keeps you on pace", f.todayPerDay)
        assertTrue(digitFree(f.todaySpentMasked) && digitFree(f.monthSpentMasked) && digitFree(f.todayPerDayMasked))
        assertEquals("₹28.1K left of ₹60K", f.budgetLine); assertEquals("₹28.1K", f.budgetLeftShort)
        assertEquals("On pace", f.paceWord); assertEquals("ON_PACE", f.paceLevel); assertEquals(530, f.paceUsedPermille); assertEquals(533, f.paceTodayPermille)
        assertTrue(f.paceSpoken.startsWith("₹28,178.00 left of ₹60,000.00"), f.paceSpoken)
        assertEquals(listOf("t1\tChai\t₹20.00", "t2\tAuto\t₹80.00"), f.buttons)
    }

    @Test
    fun hideAmountsOrTheAppLockGiveOnlyMaskedTextAndNoPaceAtAll() {
        for ((hide, locked) in listOf(true to false, false to true, true to true)) {
            val f = feed(hide = hide, locked = locked)
            assertTrue(f.allowed && !f.canToggle)
            for (s in listOf(f.todaySpent, f.monthSpent, f.todayPerDay, f.budgetLine, f.budgetLeftShort, f.paceWord, f.paceSpoken)) assertTrue(digitFree(s), "hide=$hide locked=$locked: '$s'")
            assertEquals("", f.paceWord); assertEquals(-1, f.paceUsedPermille); assertEquals(-1, f.paceTodayPermille)
            assertTrue(f.buttons.all { digitFree(it.substringAfterLast('\t')) }, "the quick entries' amounts are masked too: ${f.buttons}")
            assertEquals(listOf("t1", "t2"), f.buttons.map { it.substringBefore('\t') }, "but the chips stay, so a masked widget still captures")
        }
    }

    @Test
    fun widgetsNotAllowedGiveNothingAtAll() {
        val f = feed(allowed = false)
        assertFalse(f.allowed); assertFalse(f.canToggle)
        for (s in listOf(f.todaySpent, f.todaySpentMasked, f.todayPerDay, f.todayPerDayMasked, f.monthSpent, f.monthSpentMasked, f.budgetLine, f.budgetLeftShort, f.paceWord, f.paceLevel, f.paceSpoken)) assertEquals("", s)
        assertTrue(f.buttons.isEmpty(), "not one quick entry's name")
    }

    @Test
    fun withoutALimitThereIsNoPaceAndAnOverLimitGaugeSaysOver() {
        val none = feed(limit = null)
        assertEquals("", none.budgetLine); assertEquals("", none.budgetLeftShort); assertEquals(-1, none.paceUsedPermille)
        val over = WidgetFeeds.of(true, false, false, rs(1), rs(61_000), null, rs(60_000), rs(61_000), WidgetFeeds.pace(rs(61_000), rs(60_000), november, LocalDate(2026, 11, 20), null), emptyList())
        assertEquals("Over", over.budgetLeftShort); assertEquals(1000, over.paceUsedPermille); assertEquals("OVER", over.paceLevel)
        assertEquals("", over.todayPerDay, "nothing to spare, no per-day line")
    }
}
