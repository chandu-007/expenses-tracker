package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.Currency
import app.expenses.core.domain.CurrencyChoices

/** The ledger's one currency. Changing it is allowed only while nothing carries an amount, and the reason is shown here when it is not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyScreen(current: Currency, blocker: String?, onChoose: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { AreaTopBar("Currency", Area.LOOK, Icons.Outlined.CurrencyRupee, onBack, "currency_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Every amount in this ledger is in ${CurrencyChoices.name(current)} (${current.code}).", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("currency_current"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyChoices.ALL.forEach { c ->
                    FilterChip(selected = c == current, onClick = { if (c != current) onChoose(c.code) }, label = { Text("${c.code} · ${CurrencyChoices.name(c)}") }, modifier = Modifier.testTag("currency_${c.code}"))
                }
            }
            if (blocker != null) Text(blocker, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("currency_blocker"))
            else Text("You can change this now because nothing has an amount yet. After your first entry it is fixed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Rupees are written with lakh and crore grouping (₹12,34,567); other currencies with thousands grouping and their code (USD 1,234,567). Nothing is converted between currencies.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
