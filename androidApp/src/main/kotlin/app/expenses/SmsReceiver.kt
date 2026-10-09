package app.expenses

import android.content.BroadcastReceiver
import android.content.Context
import app.expenses.core.parsers.SmsPart
import app.expenses.core.parsers.SmsParts
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
        // pieces of one long text are joined, but two separate alerts that arrive together stay two (see SmsParts)
        val texts = parts.filterNotNull().groupBy { it.originatingAddress.orEmpty() }.flatMap { (sender, p) ->
            SmsParts.messages(p.map { SmsPart(it.messageBody.orEmpty(), it.timestampMillis) }).map { Triple(sender, it.body, it.at) }
        }
        if (texts.isEmpty()) return
        val container = (context.applicationContext as ExpensesApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val on = container.write { it.templates.setting(Capture.SETTING_SMS_ON) == "1" } == true
                if (on) texts.forEach { (sender, body, at) -> Capture.ingest(container, Capture.SMS, sender, body, at) }
            } finally {
                pending.finish()
            }
        }
    }
}
