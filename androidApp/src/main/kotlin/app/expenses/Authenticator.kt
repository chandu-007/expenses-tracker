package app.expenses

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks the person holding the phone to prove who they are, with a fingerprint or face if the phone has one set up, or with the phone's
 * own screen-lock PIN, pattern or password. The app stores no PIN of its own, so there is nothing extra to forget, no way to be locked
 * out by the app alone, and no second secret to protect.
 */
interface Authenticator {
    enum class Availability { READY, NO_SCREEN_LOCK, UNAVAILABLE }

    sealed interface Result {
        data object Success : Result

        /** [cancelled] is true when the user backed out, as opposed to the phone reporting a problem (too many attempts, no hardware). */
        data class Failed(val message: String, val cancelled: Boolean) : Result
    }

    fun availability(context: Context): Availability

    /** Shows the system prompt. [onResult] is called once, on the main thread. */
    fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, onResult: (Result) -> Unit)
}

class BiometricAuthenticator : Authenticator {
    // Weak biometrics (face unlock on many phones) are enough for a lock on the screen; the phone's own screen lock is the fallback.
    private val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun availability(context: Context): Authenticator.Availability = when (BiometricManager.from(context).canAuthenticate(allowed)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Authenticator.Availability.READY
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Authenticator.Availability.NO_SCREEN_LOCK
        else -> Authenticator.Availability.UNAVAILABLE
    }

    override fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, onResult: (Authenticator.Result) -> Unit) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(Authenticator.Result.Success)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                    onResult(Authenticator.Result.Failed(errString.toString(), cancelled))
                }
                // A wrong fingerprint is not final: the prompt stays open and the user can try again.
            },
        )
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).apply { subtitle?.let(::setSubtitle) }.setAllowedAuthenticators(allowed).build())
    }
}
