package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideTest {
    @Test
    fun aFirstVisitOffersEveryPageAndIsTheFirstRun() {
        val plan = assertNotNull(Guide.planOnOpen(null, offForUpdates = false))
        assertEquals(GuideMode.FIRST_RUN, plan.mode); assertEquals(Guide.steps, plan.steps)
    }

    @Test
    fun aFirstVisitIsOfferedEvenIfUpdatesWereTurnedOffBecauseNothingWasSeenYet() {
        assertEquals(GuideMode.FIRST_RUN, Guide.planOnOpen(null, offForUpdates = true)?.mode)
    }

    @Test
    fun someoneWhoHasSeenTheCurrentVersionIsLeftAlone() {
        assertNull(Guide.planOnOpen(Guide.VERSION, false)); assertNull(Guide.planOnOpen(Guide.VERSION + 3, false))
    }

    @Test
    fun anUpdateOffersOnlyThePagesAddedSinceAndNothingWhenThereAreNone() {
        val pages = Guide.steps.filter { it.since <= 1 } + listOf(GuideStep("x", "X", "s", emptyList(), "w", since = 2), GuideStep("y", "Y", "s", emptyList(), "w", since = 3))
        val seen1 = assertNotNull(Guide.planOnOpen(1, false, pages, version = 3))
        assertEquals(GuideMode.WHATS_NEW, seen1.mode); assertEquals(listOf("x", "y"), seen1.steps.map { it.id })
        assertEquals(listOf("y"), Guide.planOnOpen(2, false, pages, version = 3)!!.steps.map { it.id })
        assertNull(Guide.planOnOpen(1, offForUpdates = true, pages = pages, version = 3), "the person asked not to be shown guides after updates")
        assertNull(Guide.planOnOpen(3, false, pages, version = 3), "already seen")
        assertNull(Guide.planOnOpen(1, false, Guide.steps.filter { it.since <= 1 }, version = 2), "a newer version with no new pages offers nothing")
    }

    @Test
    fun theManualGuideShowsEveryPage() {
        assertEquals(GuideMode.MANUAL, Guide.fullPlan().mode); assertEquals(Guide.steps, Guide.fullPlan().steps)
    }

    @Test
    fun everyPageIsCompleteAndIdsAreUniqueAndNewPagesNeverClaimAFutureVersion() {
        assertEquals(Guide.steps.size, Guide.steps.map { it.id }.toSet().size)
        for (s in Guide.steps) {
            assertTrue(s.title.isNotBlank() && s.summary.isNotBlank() && s.where.isNotBlank(), s.id)
            assertTrue(s.since in 1..Guide.VERSION, "${s.id} is from a version that does not exist")
            assertTrue(s.points.all { it.isNotBlank() }, s.id)
        }
        assertEquals("welcome", Guide.steps.first().id); assertEquals("done", Guide.steps.last().id)
    }
}
