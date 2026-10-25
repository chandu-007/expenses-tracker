package app.expenses.ui

import app.expenses.Platform
import app.expenses.core.domain.FeedbackMail

/**
 * Opens the mail app with the feedback message filled in ([FeedbackMail]: version, system and device model only). The person reads and sends it themselves, so
 * nothing leaves the phone from here. False when there is no mail app to open.
 */
internal fun sendFeedback(platform: Platform): Boolean =
    platform.openExternalUrl(FeedbackMail.build(platform.appVersionText.ifEmpty { "unknown" }, platform.osVersionText.ifEmpty { "unknown" }, platform.deviceModelText.ifEmpty { "unknown" }))

internal const val FEEDBACK_NO_MAIL_APP = "No mail app found. You can write to ${FeedbackMail.ADDRESS}"
