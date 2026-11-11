package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureSetupTest {
    @Test
    fun theStateIsOneSettingThatRoundTripsAndAnythingOddIsTheDefault() {
        for (status in SetupStatus.entries) for (step in SetupStep.entries) {
            val s = SetupState(status, step)
            assertEquals(s, SetupState.decode(s.encode()))
        }
        for (bad in listOf(null, "", "x", "OFFERED", "OFFERED:NOWHERE", "LATER:BANKS", "OFFERED:BANKS:EXTRA", "offered:banks")) assertEquals(SetupState(), SetupState.decode(bad), "'$bad'")
        assertEquals(SetupStatus.NONE, SetupState().status)
    }

    @Test
    fun fourStepsInOrderAndTheEndsStayPut() {
        assertEquals(listOf(1, 2, 3, 4), SetupStep.entries.map { it.number }); assertEquals(4, SetupStep.COUNT)
        assertEquals(SetupStep.BANKS, CaptureSetup.stepAfter(SetupStep.PERMISSION)); assertEquals(SetupStep.DONE, CaptureSetup.stepAfter(SetupStep.ACCOUNTS))
        assertEquals(SetupStep.DONE, CaptureSetup.stepAfter(SetupStep.DONE)); assertEquals(SetupStep.PERMISSION, CaptureSetup.stepBefore(SetupStep.PERMISSION))
        assertEquals(SetupStep.BANKS, CaptureSetup.stepBefore(SetupStep.ACCOUNTS))
        assertEquals("Step 2 of 4", CaptureSetup.stepWord(SetupStep.BANKS))
    }

    @Test
    fun movingForwardIsProgressAndReachingTheLastStepFinishesIt() {
        assertEquals(SetupState(SetupStatus.IN_PROGRESS, SetupStep.BANKS), CaptureSetup.moved(SetupStep.BANKS))
        assertEquals(SetupState(SetupStatus.FINISHED, SetupStep.DONE), CaptureSetup.moved(SetupStep.DONE))
    }

    @Test
    fun leavingRemembersTheStepAndNeverUndoesAFinishedOrHiddenSetup() {
        assertEquals(SetupState(SetupStatus.IN_PROGRESS, SetupStep.ACCOUNTS), CaptureSetup.left(SetupState(SetupStatus.OFFERED), SetupStep.ACCOUNTS))
        assertEquals(SetupState(SetupStatus.OFFERED, SetupStep.PERMISSION), CaptureSetup.left(SetupState(SetupStatus.OFFERED), SetupStep.PERMISSION), "closed on the first page with nothing done: still only offered")
        assertEquals(SetupState(SetupStatus.NONE, SetupStep.PERMISSION), CaptureSetup.left(SetupState(), SetupStep.PERMISSION), "only looked at from More: no card appears")
        assertEquals(SetupState(SetupStatus.IN_PROGRESS, SetupStep.PERMISSION), CaptureSetup.left(SetupState(SetupStatus.IN_PROGRESS, SetupStep.BANKS), SetupStep.PERMISSION), "went back to the first page after going on")
        assertEquals(SetupState(SetupStatus.FINISHED, SetupStep.BANKS), CaptureSetup.left(SetupState(SetupStatus.FINISHED, SetupStep.DONE), SetupStep.BANKS))
        assertEquals(SetupStatus.HIDDEN, CaptureSetup.left(SetupState(SetupStatus.HIDDEN), SetupStep.BANKS).status)
        assertEquals(SetupStatus.IN_PROGRESS, CaptureSetup.left(SetupState(SetupStatus.NONE), SetupStep.BANKS).status, "started from More, then left")
    }

    @Test
    fun theFirstTourOffersItOnceAndOnlyToAStateThatWasNeverOffered() {
        assertEquals(SetupState(SetupStatus.OFFERED), CaptureSetup.offered(SetupState()))
        for (s in listOf(SetupStatus.OFFERED, SetupStatus.IN_PROGRESS, SetupStatus.FINISHED, SetupStatus.HIDDEN)) assertEquals(SetupState(s, SetupStep.BANKS), CaptureSetup.offered(SetupState(s, SetupStep.BANKS)))
    }

    @Test
    fun theHomeCardIsPresentOnlyWhileOfferedOrBegunWithAWayToCaptureAndNothingCapturing() {
        for (status in SetupStatus.entries) for (way in listOf(false, true)) for (on in listOf(false, true)) {
            val want = way && !on && (status == SetupStatus.OFFERED || status == SetupStatus.IN_PROGRESS)
            assertEquals(if (want) 1 else 0, CaptureSetup.nudgeCount(SetupState(status), way, on), "$status way=$way on=$on")
        }
    }

    @Test
    fun theCardSaysWhereYouAreWithNoPressure() {
        assertEquals("4 short steps. You can stop at any step.", CaptureSetup.cardLine(SetupState(SetupStatus.OFFERED)))
        assertEquals("Step 3 of 4. Carry on where you stopped.", CaptureSetup.cardLine(SetupState(SetupStatus.IN_PROGRESS, SetupStep.ACCOUNTS)))
        assertEquals("Start", CaptureSetup.cardButton(SetupState(SetupStatus.OFFERED))); assertEquals("Continue", CaptureSetup.cardButton(SetupState(SetupStatus.IN_PROGRESS, SetupStep.BANKS)))
    }

    @Test
    fun theChosenBanksAreKeptByNameAndAnUnknownNameIsDropped() {
        val known = listOf("HDFC Bank", "ICICI Bank", "SBI")
        val raw = CaptureSetup.encodeBanks(listOf(" HDFC Bank ", "SBI", "HDFC Bank", ""))
        assertEquals("HDFC Bank\nSBI", raw)
        assertEquals(listOf("HDFC Bank", "SBI"), CaptureSetup.decodeBanks(raw, known))
        assertEquals(listOf("SBI"), CaptureSetup.decodeBanks("Gone Bank\nSBI", known), "a bank the list no longer has is forgotten")
        assertEquals(emptyList(), CaptureSetup.decodeBanks(null, known))
    }

    @Test
    fun earlierSpendingStartsOnTheLastDayOfLastMonthWhateverTheMonth() {
        assertEquals(LocalDate(2026, 10, 31), EarlierSpending.lastMonthEnd(LocalDate(2026, 11, 11)))
        assertEquals(LocalDate(2026, 11, 30), EarlierSpending.lastMonthEnd(LocalDate(2026, 12, 1)))
        assertEquals(LocalDate(2025, 12, 31), EarlierSpending.lastMonthEnd(LocalDate(2026, 1, 31)))
        assertEquals(LocalDate(2028, 2, 29), EarlierSpending.lastMonthEnd(LocalDate(2028, 3, 15)), "a leap year")
        assertEquals(LocalDate(2027, 2, 28), EarlierSpending.lastMonthEnd(LocalDate(2027, 3, 1)))
        assertEquals(listOf("statement", "manual", "balances"), EarlierSpending.RECOMMENDED)
        assertTrue(EarlierSpending.RECOMMENDED.first() == "statement", "the most accurate way is recommended first")
    }
}
