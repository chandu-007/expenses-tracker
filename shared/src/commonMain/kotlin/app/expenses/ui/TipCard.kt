package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.Tip
import app.expenses.core.domain.Tips

/**
 * What a screen needs to show its one-time tip: the ids already seen, whether a tip may be shown right now (not over the welcome, not during the tour), and how to remember one as seen.
 * Provided once by the app root, so a screen only has to place [TipSlot] at the top of its content.
 */
class TipsContext(val seen: Set<String>, val active: Boolean, val onSeen: (String) -> Unit)

/** Without a provider (previews, tests of a single screen) no tip is shown. */
val LocalTips = compositionLocalOf { TipsContext(emptySet(), false) {} }

/**
 * The one-time tip of [screen], if it has not been seen and tips are allowed now; nothing otherwise. It stays until "Got it", and is also counted as seen when the screen is left, so it never comes back.
 * No animation: it is simply there, so Reduce motion changes nothing.
 */
@Composable
fun TipSlot(screen: String) {
    val tips = LocalTips.current
    val tip = Tips.unseen(tips.seen, screen)?.takeIf { tips.active } ?: return
    val id = tip.id
    val onSeen = tips.onSeen
    DisposableEffect(id) { onDispose { onSeen(id) } }
    TipCard(tip, onGotIt = { onSeen(id) })
}

/** A slim card: a short title, one line of advice, and "Got it". From 140% text the button goes under the words so nothing is squeezed. */
@Composable
internal fun TipCard(tip: Tip, onGotIt: () -> Unit) {
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    NudgeSurface("tip_${tip.id}") {
        if (stacked) Column(Modifier.padding(end = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tip.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(tip.text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onGotIt, modifier = Modifier.heightIn(min = 48.dp).testTag("tip_gotit_${tip.id}")) { Text("Got it") }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tip.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(tip.text, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onGotIt, modifier = Modifier.heightIn(min = 48.dp).testTag("tip_gotit_${tip.id}")) { Text("Got it") }
        }
    }
}
