package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.SubscriptionsUi

/** Charges that keep coming (found from your own entries): what each costs, when it is next expected, and what they add up to. */
@Composable
fun SubscriptionsScreen(state: SubscriptionsUi?, onBack: () -> Unit, onNotOne: (String) -> Unit, onShowIgnored: () -> Unit) {
    Scaffold(topBar = { AreaTopBar("Subscriptions", Area.MONEY, Icons.Outlined.Autorenew, onBack, "subscriptions_back") }) { padding ->
        if (state == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("subscriptions_list")) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.rows.isEmpty()) {
                        Text("Nothing found yet.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("subscriptions_empty"))
                        Text("A charge shows up here once the same merchant has charged about the same amount three times, a month or so apart (or twice, a year apart). Add the merchant name to your entries and it will be found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("About ${state.monthlyText} a month", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("subscriptions_monthly"))
                        Text("${state.yearlyText} a year, from ${state.rows.size} ${if (state.rows.size == 1) "charge" else "charges"} that keep coming", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("subscriptions_yearly"))
                    }
                }
                HorizontalDivider()
            }
            items(state.rows, key = { it.key }) { r ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).testTag("subscription_${r.name}"), verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(r.name, r.name, Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${r.amountText} ${r.cadenceText} · ${r.nextText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(r.yearlyText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { onNotOne(r.key) }, modifier = Modifier.testTag("subscription_not_${r.name}")) { Text("Not one") }
                }
            }
            if (state.ignored > 0) item {
                TextButton(onClick = onShowIgnored, modifier = Modifier.padding(8.dp).testTag("subscriptions_show_ignored")) { Text("Show the ${state.ignored} you marked as not subscriptions") }
            }
        }
    }
}
