package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Travel mode rules: the days of a trip, the edges between days in more than one zone, overlap, the form's words, files made by someone else. Invented trips and amounts only. */
class TripRulesTest {
    private val ist = TimeZone.of("Asia/Kolkata")
    private val utc = TimeZone.UTC
    private val london = TimeZone.of("Europe/London")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val inr = Currency.INR

    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).toEpochDays().toLong()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: TimeZone, sec: Int = 0, nano: Int = 0) = LocalDateTime(y, m, d, h, min, sec, nano).toInstant(zone).toEpochMilliseconds()
    private fun goa(start: Long = day(2026, 11, 3), end: Long = day(2026, 11, 9), enabled: Boolean = true) = Trip("goa", "Goa", "Trip: Goa", start, end, enabled)

    // ---- the days of a trip ----

    @Test fun theFirstAndTheLastDayAreInsideWholeAndTheDaysAroundThemAreNot() {
        val t = goa()
        for (zone in listOf(ist, utc, tokyo, london)) {
            assertTrue(TripRules.covers(t, at(2026, 11, 3, 0, 0, zone), zone), "00:00 on the first day is inside in $zone")
            assertFalse(TripRules.covers(t, at(2026, 11, 3, 0, 0, zone) - 1, zone), "one millisecond before the first day is outside in $zone")
            assertTrue(TripRules.covers(t, at(2026, 11, 9, 23, 59, zone, 59, 999_000_000), zone), "23:59:59.999 on the last day is inside in $zone")
            assertFalse(TripRules.covers(t, at(2026, 11, 10, 0, 0, zone), zone), "00:00 the day after the last day is outside in $zone")
        }
    }

    @Test fun theSameInstantIsInsideInOneZoneAndOutsideInAnotherBecauseTheDayIsTheCalendarDayOfThePhone() {
        val t = goa(day(2026, 11, 3), day(2026, 11, 3))
        val instant = at(2026, 11, 3, 23, 30, ist) // 18:00 UTC on 3 November, 03:00 on 4 November in Tokyo
        assertTrue(TripRules.covers(t, instant, ist))
        assertTrue(TripRules.covers(t, instant, utc))
        assertFalse(TripRules.covers(t, instant, tokyo), "03:00 on 4 November in Tokyo is another day")
    }

    @Test fun indiaHasNoDaylightSavingSoEveryDayIsTwentyFourHoursAndTheEdgeIsExact() {
        val t = goa(day(2026, 3, 29), day(2026, 3, 29))
        assertEquals(86_400_000L, TripRules.untilMillis(t, ist) - TripRules.fromMillis(t, ist))
        assertEquals(at(2026, 3, 29, 0, 0, ist), TripRules.fromMillis(t, ist))
    }

    @Test fun aDaylightSavingDayIsStillOneCalendarDay() {
        // London moved its clocks forward on 29 March 2026: that calendar day has 23 hours, and a trip of that one day covers all of them and nothing of the day before or after
        val t = goa(day(2026, 3, 29), day(2026, 3, 29))
        assertEquals(23 * 3_600_000L, TripRules.untilMillis(t, london) - TripRules.fromMillis(t, london))
        assertTrue(TripRules.covers(t, at(2026, 3, 29, 23, 59, london), london))
        assertFalse(TripRules.covers(t, at(2026, 3, 30, 0, 0, london), london))
        assertFalse(TripRules.covers(t, at(2026, 3, 28, 23, 59, london), london))
        val autumn = goa(day(2026, 10, 25), day(2026, 10, 25))
        assertEquals(25 * 3_600_000L, TripRules.untilMillis(autumn, london) - TripRules.fromMillis(autumn, london), "and the day the clocks go back has 25")
    }

    @Test fun aTripOfOneDayStartsAndEndsOnTheSameDay() {
        val t = goa(day(2026, 11, 5), day(2026, 11, 5))
        assertEquals(1, t.length)
        assertTrue(t.covers(day(2026, 11, 5)) && !t.covers(day(2026, 11, 4)) && !t.covers(day(2026, 11, 6)))
    }

    @Test fun onlyATripThatIsOnTagsAndAtMostOneCanBeOn() {
        val on = goa(); val off = goa(enabled = false).copy(id = "off")
        val noon = at(2026, 11, 5, 12, 0, ist)
        assertEquals("goa", TripRules.tripAt(listOf(off, on), noon, ist)?.id)
        assertNull(TripRules.tripAt(listOf(off), noon, ist), "a trip that is off tags nothing")
        assertNull(TripRules.tripAt(listOf(on), at(2026, 11, 10, 12, 0, ist), ist))
        assertNull(TripRules.tripAt(emptyList(), noon, ist))
    }

    // ---- overlap ----

    @Test fun tripsThatShareADayOverlapEvenWhenTheyOnlyTouchAndOnesThatDoNotShareOneDoNot() {
        val a = goa(day(2026, 11, 3), day(2026, 11, 9))
        fun other(s: Long, e: Long, on: Boolean = true) = Trip("o", "Other", "Trip: Other", s, e, on)
        assertNotNull(TripRules.overlapping(other(day(2026, 11, 9), day(2026, 11, 12)), listOf(a)), "the last day of one is the first day of the other")
        assertNotNull(TripRules.overlapping(other(day(2026, 10, 30), day(2026, 11, 3)), listOf(a)))
        assertNotNull(TripRules.overlapping(other(day(2026, 11, 5), day(2026, 11, 6)), listOf(a)), "inside")
        assertNotNull(TripRules.overlapping(other(day(2026, 11, 1), day(2026, 11, 30)), listOf(a)), "around")
        assertNull(TripRules.overlapping(other(day(2026, 11, 10), day(2026, 11, 12)), listOf(a)), "the day after")
        assertNull(TripRules.overlapping(other(day(2026, 10, 25), day(2026, 11, 2)), listOf(a)), "the day before")
        assertNull(TripRules.overlapping(other(day(2026, 11, 5), day(2026, 11, 6), on = false), listOf(a)), "a trip that is off tags nothing, so it may overlap")
        assertNull(TripRules.overlapping(other(day(2026, 11, 5), day(2026, 11, 6)), listOf(a.copy(enabled = false))), "and one that is on may overlap a trip that is off")
        assertNull(TripRules.overlapping(a, listOf(a)), "a trip is not compared with itself")
    }

    @Test fun theFormRefusesAnOverlapInPlainWordsNamingTheOtherTrip() {
        val a = goa()
        val r = TripRules.build("new", "Mumbai", "Trip: Mumbai", day(2026, 11, 8), day(2026, 11, 12), "", true, listOf(a), inr) as TripResult.Problem
        assertEquals(TripField.DATES, r.problem.field)
        assertEquals("These dates overlap your trip \"Goa\" (3 Nov – 9 Nov 2026), which is on. Change the dates, or turn that trip off.", r.problem.message)
        // turned off it is accepted, and so is the same dates for a trip that is being edited (it is not the other one)
        assertTrue(TripRules.build("new", "Mumbai", "Trip: Mumbai", day(2026, 11, 8), day(2026, 11, 12), "", false, listOf(a), inr) is TripResult.Ok)
        assertTrue(TripRules.build("goa", "Goa", "Trip: Goa", day(2026, 11, 3), day(2026, 11, 12), "", true, listOf(a), inr) is TripResult.Ok)
    }

    // ---- the form ----

    private fun problem(name: String = "Goa", tag: String = "Trip: Goa", from: Long? = day(2026, 11, 3), to: Long? = day(2026, 11, 9), budget: String = "") =
        (TripRules.build("t", name, tag, from, to, budget, true, emptyList(), inr) as? TripResult.Problem)?.problem

    @Test fun everyMistakeInTheFormHasAPlainSentenceAndAFieldToShowItUnder() {
        assertEquals(TripProblem(TripField.NAME, "Give the trip a name, for example Goa."), problem(name = "   "))
        assertEquals(TripProblem(TripField.NAME, "A trip name can have up to 40 characters."), problem(name = "x".repeat(41)))
        assertEquals(TripProblem(TripField.TAG, "Type the tag to put on this trip's expenses, for example Trip: Goa."), problem(tag = " #, "))
        assertEquals(TripProblem(TripField.TAG, "A tag can have up to 30 characters. Make it shorter."), problem(tag = "x".repeat(31)))
        assertEquals(TripProblem(TripField.START, "Choose the first day of the trip."), problem(from = null))
        assertEquals(TripProblem(TripField.END, "Choose the last day of the trip."), problem(to = null))
        assertEquals(TripProblem(TripField.DATES, "The last day is before the first day. Change one of them."), problem(from = day(2026, 11, 9), to = day(2026, 11, 3)))
        assertEquals(TripProblem(TripField.DATES, "Choose days between 2000 and 2099."), problem(from = day(1999, 12, 31), to = day(2000, 1, 2)))
        assertEquals(TripProblem(TripField.DATES, "Choose days between 2000 and 2099."), problem(from = day(2099, 12, 30), to = day(2100, 1, 1)))
        assertEquals(TripProblem(TripField.DATES, "A trip can last up to 366 days. For a longer stay, make two trips."), problem(from = day(2026, 1, 1), to = day(2027, 1, 2)))
        assertNull(problem(from = day(2026, 1, 1), to = day(2027, 1, 1)), "366 days is allowed")
        assertEquals(TripProblem(TripField.BUDGET, "Type the daily budget as an amount, or leave it empty."), problem(budget = "abc"))
        assertEquals(TripProblem(TripField.BUDGET, "The daily budget must be more than zero, or leave it empty."), problem(budget = "0"))
        assertEquals(TripProblem(TripField.BUDGET, "That daily budget is too large."), problem(budget = "99999999999"))
    }

    @Test fun aGoodFormBuildsATidyTripWithItsBudgetInMinorUnits() {
        val r = TripRules.build("t", "  Goa   with   friends ", "#Trip:  Goa", day(2026, 11, 3), day(2026, 11, 9), "1,500.50", true, emptyList(), inr) as TripResult.Ok
        assertEquals(Trip("t", "Goa with friends", "Trip: Goa", day(2026, 11, 3), day(2026, 11, 9), true, 150_050L), r.trip)
        val noBudget = TripRules.build("t", "Goa", "Goa", day(2026, 11, 3), day(2026, 11, 3), "  ", false, emptyList(), inr) as TripResult.Ok
        assertNull(noBudget.trip.dailyBudgetMinor); assertFalse(noBudget.trip.enabled)
    }

    @Test fun theSuggestedTagIsTripColonTheNameCutToWhatATagMayHold() {
        assertEquals("Trip: Goa", TripRules.suggestedTag("Goa"))
        assertEquals("Trip: Goa and Gokarna", TripRules.suggestedTag("  Goa   and Gokarna "))
        assertEquals("Trip: ${"x".repeat(24)}", TripRules.suggestedTag("x".repeat(40)), "30 characters at most")
        assertNull(TripRules.suggestedTag("   "))
        assertEquals("Trip: Goa Panjim", TripRules.suggestedTag("Goa, Panjim"), "a comma is a space in a tag")
    }

    // ---- days ----

    @Test fun aTripIsUpcomingActiveOrPastAndTheDayNumberCountsFromOne() {
        val t = goa()
        assertEquals(TripPhase.UPCOMING, TripRules.phase(t, day(2026, 11, 2)))
        assertEquals(TripPhase.ACTIVE, TripRules.phase(t, day(2026, 11, 3)))
        assertEquals(TripPhase.ACTIVE, TripRules.phase(t, day(2026, 11, 9)))
        assertEquals(TripPhase.PAST, TripRules.phase(t, day(2026, 11, 10)))
        assertEquals(1, TripRules.dayNumber(t, day(2026, 11, 3))); assertEquals(7, TripRules.dayNumber(t, day(2026, 11, 9)))
        assertNull(TripRules.dayNumber(t, day(2026, 11, 2))); assertNull(TripRules.dayNumber(t, day(2026, 11, 10)))
        assertEquals("goa", TripRules.activeOn(listOf(t), day(2026, 11, 5))?.id)
        assertNull(TripRules.activeOn(listOf(t.copy(enabled = false)), day(2026, 11, 5)), "a trip that is off has no card on Home")
    }

    // ---- what a changed trip offers to tag ----

    @Test fun aNewTripOffersAllItsDaysAndAnEditedOneOnlyTheDaysItGained() {
        val t = goa(day(2026, 11, 3), day(2026, 11, 9))
        assertEquals(listOf(day(2026, 11, 3)..day(2026, 11, 9)), TripRules.newlyCovered(null, t))
        assertEquals(listOf(day(2026, 11, 10)..day(2026, 11, 12)), TripRules.newlyCovered(t, t.copy(endDay = day(2026, 11, 12))))
        assertEquals(listOf(day(2026, 11, 1)..day(2026, 11, 2)), TripRules.newlyCovered(t, t.copy(startDay = day(2026, 11, 1))))
        assertEquals(listOf(day(2026, 11, 1)..day(2026, 11, 2), day(2026, 11, 10)..day(2026, 11, 12)), TripRules.newlyCovered(t, t.copy(startDay = day(2026, 11, 1), endDay = day(2026, 11, 12))))
        assertEquals(emptyList(), TripRules.newlyCovered(t, t.copy(endDay = day(2026, 11, 6))), "shortened: nothing new")
        assertEquals(emptyList(), TripRules.newlyCovered(t, t.copy(name = "Goa 2")), "renamed: nothing new")
        assertEquals(emptyList(), TripRules.newlyCovered(null, t.copy(enabled = false)), "a trip that is off covers nothing")
        assertEquals(listOf(day(2026, 11, 3)..day(2026, 11, 9)), TripRules.newlyCovered(t.copy(enabled = false), t), "switched on again: all of it")
        assertEquals(listOf(day(2026, 11, 20)..day(2026, 11, 25)), TripRules.newlyCovered(t, t.copy(startDay = day(2026, 11, 20), endDay = day(2026, 11, 25))), "moved clear of the old days: all the new ones")
    }

    // ---- a file made by someone else ----

    @Test fun aTripFromAFileIsMadeFitOrDropped() {
        val ok = TripRules.fromFile("a", "  Goa\n\tTrip ", "#Trip: Goa, Panjim", day(2026, 11, 3), day(2026, 11, 9), true, 150_050L)
        assertEquals(Trip("a", "Goa Trip", "Trip: Goa Panjim", day(2026, 11, 3), day(2026, 11, 9), true, 150_050L), ok)
        assertEquals(40, TripRules.fromFile("a", "n".repeat(500), "t", day(2026, 11, 3), day(2026, 11, 9), true, null)!!.name.length)
        assertEquals(30, TripRules.fromFile("a", "n", "t".repeat(500), day(2026, 11, 3), day(2026, 11, 9), true, null)!!.tag.length)
        assertNull(TripRules.fromFile("a", "n", "t", day(2026, 11, 3), day(2026, 11, 9), true, -5)!!.dailyBudgetMinor)
        assertNull(TripRules.fromFile("a", "n", "t", day(2026, 11, 3), day(2026, 11, 9), true, Long.MAX_VALUE)!!.dailyBudgetMinor)
        for (bad in listOf(
            Triple("", "n", "t"), Triple("a", "   ", "t"), Triple("a", "n", " , "), Triple("x".repeat(65), "n", "t"),
        )) assertNull(TripRules.fromFile(bad.first, bad.second, bad.third, day(2026, 11, 3), day(2026, 11, 9), true, null), "$bad")
        assertNull(TripRules.fromFile("a", "n", "t", day(2026, 11, 9), day(2026, 11, 3), true, null), "ends before it starts")
        assertNull(TripRules.fromFile("a", "n", "t", Long.MIN_VALUE, Long.MAX_VALUE, true, null))
        assertNull(TripRules.fromFile("a", "n", "t", day(1900, 1, 1), day(1900, 1, 5), true, null))
        assertNull(TripRules.fromFile("a", "n", "t", day(2026, 1, 1), day(2027, 1, 2), true, null), "367 days")
    }

    @Test fun theStoreRulesAreTheFormRulesOnABuiltTrip() {
        assertNull(TripRules.problemOf(goa(), emptyList()))
        assertNotNull(TripRules.problemOf(goa().copy(name = " Goa"), emptyList()), "a name must already be tidy")
        assertNotNull(TripRules.problemOf(goa().copy(tag = "a,b"), emptyList()))
        assertNotNull(TripRules.problemOf(goa().copy(dailyBudgetMinor = 0), emptyList()))
        assertNotNull(TripRules.problemOf(goa().copy(id = ""), emptyList()))
        assertNotNull(TripRules.problemOf(goa(), listOf(goa().copy(id = "x", name = "Other", tag = "Trip: Other"))))
        assertNull(TripRules.problemOf(goa(enabled = false), listOf(goa().copy(id = "x", name = "Other", tag = "Trip: Other"))))
    }

    // ---- what a trip's figures say ----

    private fun line(id: String, d: Long, hour: Int, minor: Long, cat: String? = "Food") = TripLine(id, d * 86_400_000L + hour * 3_600_000L, cat, minor)

    @Test fun thePerDayBarsTheTotalTheAverageAndTheBudgetAddUpForATripThatIsOver() {
        val t = goa(day(2026, 11, 3), day(2026, 11, 9)).copy(dailyBudgetMinor = 100_000)
        val d0 = day(2026, 11, 3)
        val lines = listOf(line("a", d0, 10, 40_000), line("b", d0, 20, 60_000), line("c", d0 + 2, 9, 250_000, "Stay"), line("r", d0 + 2, 12, -50_000, "Stay"), line("d", d0 + 6, 23, 10_000, null))
        val s = TripSummaryMath.of(t, lines, utc, today = day(2026, 11, 20))
        assertEquals(TripPhase.PAST, s.phase)
        assertEquals(310_000L, s.totalMinor, "the refund of 500 is taken off")
        assertEquals(5, s.entryCount, "four expenses and the refund: each entry once")
        assertEquals(7, s.daysElapsed); assertEquals(7, s.daysTotal)
        assertEquals(44_286L, s.averagePerDayMinor, "310,000 over 7 days is 44,285.7")
        assertEquals(TripBudgetStatus(100_000, 7, 700_000, 310_000), s.budget)
        assertEquals(390_000L, s.budget!!.differenceMinor, "390,000 under")
        assertEquals(listOf(100_000L, 0L, 200_000L, 0L, 0L, 0L, 10_000L), s.bars.map { it.minor })
        assertTrue(s.bars.none { it.isToday || it.isFuture })
        assertEquals(mapOf<String?, Long>("Food" to 100_000L, "Stay" to 200_000L, null to 10_000L), s.byCategory)
    }

    @Test fun anActiveTripCountsOnlyTheDaysSoFarAndASingleBarPerDayIncludesTheDaysToCome() {
        val t = goa(day(2026, 11, 3), day(2026, 11, 9)).copy(dailyBudgetMinor = 100_000)
        val d0 = day(2026, 11, 3)
        val lines = listOf(line("a", d0, 10, 40_000), line("b", d0 + 1, 20, 160_000), line("c", d0 + 2, 9, 250_000, "Stay"))
        val s = TripSummaryMath.of(t, lines, utc, today = d0 + 2)
        assertEquals(TripPhase.ACTIVE, s.phase)
        assertEquals(3, s.daysElapsed); assertEquals(7, s.daysTotal)
        assertEquals(450_000L, s.totalMinor)
        assertEquals(150_000L, s.averagePerDayMinor)
        assertEquals(TripBudgetStatus(100_000, 3, 300_000, 450_000), s.budget)
        assertEquals(-150_000L, s.budget!!.differenceMinor, "150,000 over")
        assertEquals(7, s.bars.size)
        assertEquals(listOf(40_000L, 160_000L, 250_000L, 0L, 0L, 0L, 0L), s.bars.map { it.minor })
        assertEquals(listOf(false, false, true, false, false, false, false), s.bars.map { it.isToday })
        assertEquals(listOf(false, false, false, true, true, true, true), s.bars.map { it.isFuture })
        assertEquals(mapOf<String?, Long>("Food" to 200_000L, "Stay" to 250_000L), s.byCategory)
        assertEquals("3", TripSummaryMath.barLabel(s.bars[0]))
    }

    @Test fun beforeTheTripThereIsNoAverageAndNoBudgetComparison() {
        val t = goa().copy(dailyBudgetMinor = 100_000)
        val s = TripSummaryMath.of(t, emptyList(), utc, today = day(2026, 11, 1))
        assertEquals(TripPhase.UPCOMING, s.phase); assertEquals(0, s.daysElapsed)
        assertNull(s.averagePerDayMinor); assertNull(s.budget)
        assertEquals(0L, s.totalMinor); assertEquals(0, s.entryCount); assertTrue(s.byCategory.isEmpty())
        assertEquals(7, s.bars.size); assertTrue(s.bars.all { it.isFuture && it.minor == 0L })
    }

    @Test fun theAverageIsRoundedToTheNearestMinorUnitAndARefundBeyondTheSpendingIsNegative() {
        val t = goa(day(2026, 11, 3), day(2026, 11, 5))
        val d0 = day(2026, 11, 3)
        assertEquals(333_334L, TripSummaryMath.of(t, listOf(line("a", d0, 1, 1_000_001)), utc, today = d0 + 2).averagePerDayMinor, "1,000,001 over 3 days is 333,333.67")
        assertEquals(333_333L, TripSummaryMath.of(t, listOf(line("a", d0, 1, 1_000_000)), utc, today = d0 + 2).averagePerDayMinor)
        val net = TripSummaryMath.of(t, listOf(line("a", d0, 1, -2_000)), utc, today = d0 + 2)
        assertEquals(-2_000L, net.totalMinor); assertEquals(-667L, net.averagePerDayMinor)
        assertEquals(mapOf<String?, Long>("Food" to -2_000L), net.byCategory)
    }

    @Test fun aLongTripGetsABarForEachWeekAndTheBarsAddUpToTheTotal() {
        val t = goa(day(2026, 1, 1), day(2026, 3, 15)) // 74 days
        val lines = (0 until 74).map { line("e$it", day(2026, 1, 1) + it, 12, 1_000L + it) }
        val s = TripSummaryMath.of(t, lines, ist, today = day(2026, 4, 1))
        assertEquals(74, s.daysTotal)
        assertEquals(11, s.bars.size, "ten weeks and a part week")
        assertEquals(7L, s.bars[0].endDay - s.bars[0].startDay + 1); assertEquals(4L, s.bars.last().endDay - s.bars.last().startDay + 1)
        assertEquals(s.totalMinor, s.bars.sumOf { it.minor })
        assertEquals(day(2026, 3, 15), s.bars.last().endDay)
        assertEquals("1 Jan", TripSummaryMath.barLabel(s.bars[0]))
        assertEquals(74, s.entryCount)
        val thirtyOne = TripSummaryMath.of(goa(day(2026, 1, 1), day(2026, 1, 31)), emptyList(), utc, day(2026, 2, 1))
        assertEquals(31, thirtyOne.bars.size, "up to 31 days: a bar for each day")
        assertEquals(5, TripSummaryMath.of(goa(day(2026, 1, 1), day(2026, 2, 1)), emptyList(), utc, day(2026, 3, 1)).bars.size, "32 days: weeks (four whole ones and four days)")
    }

    @Test fun aLineAtTheEdgeOfTheDayBelongsToThatLocalDayInThePhonesZone() {
        val t = goa(day(2026, 11, 3), day(2026, 11, 4))
        val justBeforeMidnight = at(2026, 11, 3, 23, 59, ist, 59, 999_000_000)
        val midnight = at(2026, 11, 4, 0, 0, ist)
        val s = TripSummaryMath.of(t, listOf(TripLine("a", justBeforeMidnight, null, 100), TripLine("b", midnight, null, 10)), ist, today = day(2026, 11, 4))
        assertEquals(listOf(100L, 10L), s.bars.map { it.minor })
    }

    @Test fun aTagBelongsToOneTripOnlyComparedAsTagsAreAndWhateverTheOtherTripsStateIs() {
        val a = goa()
        for (other in listOf(a.copy(id = "x", name = "Again", startDay = day(2027, 1, 1), endDay = day(2027, 1, 3)), a.copy(id = "y", name = "Off", enabled = false), a.copy(id = "z", name = "Past", startDay = day(2020, 1, 1), endDay = day(2020, 1, 2)))) {
            for (typed in listOf("Trip: Goa", "  #trip:   GOA ", "TRIP: goa,")) {
                val r = TripRules.build("new", "New", typed, day(2030, 1, 1), day(2030, 1, 2), "", true, listOf(other), inr) as TripResult.Problem
                assertEquals(TripField.TAG, r.problem.field)
                assertEquals("The tag \"Trip: Goa\" already belongs to your trip \"${other.name}\" (${SearchDates.summary(other.startDay, other.endDay)}). A tag can belong to one trip only. Choose another tag.", r.problem.message, typed)
            }
        }
        assertTrue(TripRules.build("goa", "Goa", "Trip: Goa", a.startDay, a.endDay, "", true, listOf(a), inr) is TripResult.Ok, "a trip keeps its own tag when edited")
        assertNotNull(TripRules.problemOf(a.copy(id = "n"), listOf(a)))
        assertNull(TripRules.tagTaken(a, listOf(a.copy(id = "q", tag = "Trip: Goas"))))
    }

    @Test fun whatIsTaggedButDatedOutsideTheTripIsInTheTotalAndShownApartNotInTheBarsAverageOrBudget() {
        val t = goa(day(2026, 11, 3), day(2026, 11, 5)).copy(dailyBudgetMinor = 100_000)
        val d0 = day(2026, 11, 3)
        val lines = listOf(line("in", d0, 10, 300_000), line("flight", d0 - 20, 10, 800_000), line("late", d0 + 10, 10, -50_000), line("flight2", d0 - 20, 11, 100_000))
        val s = TripSummaryMath.of(t, lines, utc, today = d0 + 2)
        assertEquals(1_150_000L, s.totalMinor); assertEquals(4, s.entryCount)
        assertEquals(850_000L, s.outsideMinor); assertEquals(3, s.outsideCount)
        assertEquals(listOf(300_000L, 0L, 0L), s.bars.map { it.minor })
        assertEquals(100_000L, s.averagePerDayMinor); assertEquals(TripBudgetStatus(100_000, 3, 300_000, 300_000), s.budget)
        assertEquals(0L, TripSummaryMath.of(t, listOf(line("in", d0, 10, 5)), utc, d0).outsideMinor)
    }

    // ---- the Home card's progress (arithmetic on the trip total the card already has) ----

    @Test fun theBudgetPercentIsTheSpentShareOfTheDailyBudgetTimesTheDays() {
        val t = Trip("goa", "Goa", "Trip: Goa", day(2026, 11, 3), day(2026, 11, 8), true, 300_000) // 6 days of 3,000 = 18,000
        assertEquals(1_800_000L, TripRules.totalBudgetMinor(t))
        assertEquals(50, TripRules.budgetPercent(t, 900_000))
        assertEquals(0, TripRules.budgetPercent(t, 0))
        assertEquals(100, TripRules.budgetPercent(t, 1_800_000))
        assertEquals(133, TripRules.budgetPercent(t, 2_400_000), "past the budget it goes over 100")
        assertEquals(0, TripRules.budgetPercent(t, -500), "a refund larger than the spending does not show a negative bar")
        assertEquals(999, TripRules.budgetPercent(t, 900_000_000), "capped")
    }

    @Test fun noDailyBudgetMeansNoBudgetPercent() {
        assertNull(TripRules.totalBudgetMinor(goa())); assertNull(TripRules.budgetPercent(goa(), 100_000))
    }
}
