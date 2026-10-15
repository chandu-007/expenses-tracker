package app.expenses

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming texts, **only while the person has turned SMS capture on** (and Android has granted the permission). It reads the sender
 * and words of each message once, hands them to [Capture] and keeps nothing of a text that is not a bank transaction. It never abandons a
 * long text: the parts of one message are joined before they are looked at.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val texts = parts.filterNotNull().groupBy { it.originatingAddress.orEmpty() }.map { (sender, p) -> Triple(sender, p.joinToString("") { it.messageBody.orEmpty() }, p.first().timestampMillis) }
        if (texts.isEmpty()) return
        val container = (context.applicationContext as ExpensesApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val on = container.write { it.templates.setting(Capture.SETTING_SMS_ON) == "1" } == true
                if (on) {
                    val found = texts.map { (sender, body, at) -> Capture.ingest(container, Capture.SMS, sender, body, at) }
                    // a bill is told about at once, not at the next hourly check
                    if (found.any { it == app.expenses.core.storage.CaptureEventOutcome.BILL }) NotificationWork.checkNow(context.applicationContext, container)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
