package app.expenses

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.expenses.core.storage.CaptureEventOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming texts (Android delivers them only while it has granted the permission) and reads them **only while the person has turned SMS capture on**:
 * the switch is checked first, and with it off the message is never taken out of the broadcast, so not one word or sender of it is read, not even into memory.
 * With it on, it reads the sender and words of each message once, hands them to [Capture] and keeps nothing of a text that is not a bank transaction. It never
 * abandons a long text: the parts of one message are joined before they are looked at.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext
        // null only when called outside a real broadcast (a test)
        val pending: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                capture(app, intent)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        /** Reads the SMS switch and, **only when it is on**, the messages [intent] carries, and hands them to capture. */
        internal suspend fun capture(context: Context, intent: Intent) {
            val app = context.applicationContext
            val container = (app as ExpensesApp).container
            if (container.write { it.templates.setting(Capture.SETTING_SMS_ON) == "1" } != true) return
            val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val texts = parts.filterNotNull().groupBy { it.originatingAddress.orEmpty() }.map { (sender, p) -> Triple(sender, p.joinToString("") { it.messageBody.orEmpty() }, p.first().timestampMillis) }
            if (texts.isEmpty()) return
            val found = texts.map { (sender, body, at) -> Capture.ingest(container, Capture.SMS, sender, body, at) }
            // a bill is told about at once, not at the next hourly check
            if (found.any { it == CaptureEventOutcome.BILL }) NotificationWork.checkNow(app, container)
        }
    }
}
