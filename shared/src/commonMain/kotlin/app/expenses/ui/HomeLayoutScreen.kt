package app.expenses.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.AccountRowUi
import app.expenses.core.domain.HomeLayout
import app.expenses.core.domain.HomeSection

/** Which blocks the home screen shows and in what order. Changes apply at once; nothing is deleted by hiding a block. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutScreen(layout: HomeLayout, onChange: (HomeLayout) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { AreaTopBar("Customise home", Area.LOOK, Icons.Outlined.Dashboard, onBack, "home_layout_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Choose what the home screen shows and move blocks up or down. The headline total and the list of entries are always there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            layout.order.forEachIndexed { i, section ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("home_row_${section.name}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Switch(checked = section !in layout.hidden, onCheckedChange = { onChange(layout.withHidden(section, !it)) }, modifier = Modifier.testTag("home_toggle_${section.name}").semantics { contentDescription = "Show ${section.label}" })
                    Text(section.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    TextButton(onClick = { onChange(layout.move(section, -1)) }, enabled = i > 0, modifier = Modifier.testTag("home_up_${section.name}").semantics { contentDescription = "Move ${section.label} up" }) { Text("↑") }
                    TextButton(onClick = { onChange(layout.move(section, 1)) }, enabled = i < layout.order.size - 1, modifier = Modifier.testTag("home_down_${section.name}").semantics { contentDescription = "Move ${section.label} down" }) { Text("↓") }
                }
                HorizontalDivider()
            }
        }
    }
}

/** Every active account with its balance in one scrolling line; a card or loan shows what is owed. Tapping opens the accounts. */
@Composable
fun BalancesStrip(accounts: List<AccountRowUi>, onOpen: () -> Unit) {
    if (accounts.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp).testTag("balances"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { a -> AccountTile(a, compact = true, onClick = onOpen, modifier = Modifier.width(196.dp).height(100.dp)) }
    }
}
