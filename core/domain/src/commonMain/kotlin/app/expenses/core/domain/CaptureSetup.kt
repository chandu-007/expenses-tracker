package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** The four short steps of "Set up reading bank texts", in order. */
enum class SetupStep(val number: Int) {
    /** Android: allow reading texts (or, for the Play build, payment-app alerts). iPhone: the Shortcuts automation. */
    PERMISSION(1),

    /** Pick the banks you use. */
    BANKS(2),

    /** Add the accounts, with the last 3 or 4 digits. */
    ACCOUNTS(3),

    /** A summary, and the checkup. */
    DONE(4),
    ;

    companion object { const val COUNT = 4 }
}

/**
 * Where the guided setup stands. [NONE]: never offered (an existing install, or one that has not finished its tour). [OFFERED]: shown after the first tour; nothing started. [IN_PROGRESS]: the person
 * began and left. [FINISHED]: reached the last step. [HIDDEN]: the person asked for the Home card to go away; the setup is still in More, Capture.
 */
enum class SetupStatus { NONE, OFFERED, IN_PROGRESS, FINISHED, HIDDEN }

/** The persisted position: a status and the step to open at. */
data class SetupState(val status: SetupStatus = SetupStatus.NONE, val step: SetupStep = SetupStep.PERMISSION) {
    /** One setting value, `STATUS:STEP`. */
    fun encode(): String = "${status.name}:${step.name}"

    companion object {
        /** Anything that is not what [encode] writes (nothing, a damaged value, a name from a later version) is the default: never offered. */
        fun decode(raw: String?): SetupState {
            val parts = raw?.split(':') ?: return SetupState()
            if (parts.size != 2) return SetupState()
            val status = SetupStatus.entries.firstOrNull { it.name == parts[0] } ?: return SetupState()
            val step = SetupStep.entries.firstOrNull { it.name == parts[1] } ?: return SetupState()
            return SetupState(status, step)
        }
    }
}

/**
 * Pure rules of the guided first-run setup and of its Home card. The screens and the settings are in `shared`; nothing here knows either.
 */
object CaptureSetup {
    const val STATE_KEY = "capture_setup"
    const val BANKS_KEY = "capture_setup_banks"

    fun stepAfter(step: SetupStep): SetupStep = SetupStep.entries.getOrElse(step.ordinal + 1) { SetupStep.DONE }
    fun stepBefore(step: SetupStep): SetupStep = SetupStep.entries.getOrElse(step.ordinal - 1) { SetupStep.PERMISSION }

    /** "Step 2 of 4". */
    fun stepWord(step: SetupStep) = "Step ${step.number} of ${SetupStep.COUNT}"

    /** The state after the person moved to [step]: a move to the last step is the end of the setup. */
    fun moved(to: SetupStep): SetupState = SetupState(if (to == SetupStep.DONE) SetupStatus.FINISHED else SetupStatus.IN_PROGRESS, to)

    /** The state after the person left the setup at [step] without finishing: it is remembered there. A finished setup stays finished. */
    fun left(current: SetupState, step: SetupStep): SetupState = when {
        current.status == SetupStatus.FINISHED || current.status == SetupStatus.HIDDEN -> current.copy(step = step)
        // closed on the first page without doing anything (offered, or only looked at from More): nothing changes, and the card keeps saying "Start" or is not drawn
        (current.status == SetupStatus.OFFERED || current.status == SetupStatus.NONE) && step == SetupStep.PERMISSION -> current
        else -> SetupState(SetupStatus.IN_PROGRESS, step)
    }

    /** The setup is offered once, after the first tour: only a state that has never been offered becomes [SetupStatus.OFFERED]. */
    fun offered(current: SetupState): SetupState = if (current.status == SetupStatus.NONE) SetupState(SetupStatus.OFFERED, SetupStep.PERMISSION) else current

    /**
     * How many things the Home card is about: 1 while the setup was offered or begun and left, the platform has a way to capture, and nothing is capturing yet; 0 otherwise. A finished or hidden
     * setup, and a phone where capture already runs, never show the card.
     */
    fun nudgeCount(state: SetupState, wayAvailable: Boolean, captureOn: Boolean): Int =
        if (wayAvailable && !captureOn && (state.status == SetupStatus.OFFERED || state.status == SetupStatus.IN_PROGRESS)) 1 else 0

    /** The line on the Home card: where the person is. */
    fun cardLine(state: SetupState): String =
        if (state.status == SetupStatus.IN_PROGRESS) "${stepWord(state.step)}. Carry on where you stopped." else "${SetupStep.COUNT} short steps. You can stop at any step."

    fun cardButton(state: SetupState): String = if (state.status == SetupStatus.IN_PROGRESS) "Continue" else "Start"

    /** The names of the banks chosen in step 2, as one setting value (one per line), and back. A name that is not on [known] is dropped on reading. */
    fun encodeBanks(issuers: Collection<String>): String = issuers.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
    fun decodeBanks(raw: String?, known: Collection<String>): List<String> = raw.orEmpty().split('\n').map { it.trim() }.filter { it in known }.distinct()
}

/** Pure helpers of "Bring in earlier spending". */
object EarlierSpending {
    /** The last day of the month before the one [today] is in: where the form starts, so a person catching up from a month ago does not have to move the date. */
    fun lastMonthEnd(today: LocalDate): LocalDate = LocalDate(today.year, today.month, 1).minus(1, DateTimeUnit.DAY)

    /** The order the screen recommends: the most accurate way first. */
    val RECOMMENDED = listOf("statement", "manual", "balances")

}
