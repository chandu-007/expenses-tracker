package app.expenses.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expenses.AccountRowUi
import app.expenses.core.domain.CardDesign
import app.expenses.core.domain.CardDesigns
import app.expenses.core.domain.CardNetwork
import app.expenses.core.domain.CardTier
import app.expenses.core.domain.ledger.AccountType

private fun designOf(a: AccountRowUi): CardDesign =
    CardDesigns.of(AccountType.valueOf(a.type), a.network?.let { n -> CardNetwork.entries.firstOrNull { it.name == n } }, a.tier?.let { t -> CardTier.entries.firstOrNull { it.name == t } })

/**
 * An account as a card: its name, last four digits, balance and, for a credit card, its network and what is due. The look is our own (see [CardDesigns]): a colour
 * for each kind of account and a finish for each network and tier, with no bank's or network's artwork. The whole tile reads as one line to a screen reader and
 * to the tests, as the plain chip and row did.
 *
 * [compact] is the small tile of the home strip; the full one is for the accounts page.
 */
@Composable
fun AccountTile(a: AccountRowUi, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = designOf(a)
    val ink = Color(d.text)
    val accent = Color(d.accent)
    val network = a.network?.let { n -> CardNetwork.entries.firstOrNull { it.name == n } }
    val isCard = a.type == "CREDIT_CARD"
    val pad = if (compact) 14.dp else 20.dp
    val tag = if (compact) "balance_chip_${a.name}" else "account_${a.name}"
    // what the small chip reads out and what the tests read: masked like every other amount while Hide amounts is on
    val label = privately(a.name + (if (a.owed) " · owed" else "") + " " + a.balanceText)
    Box(
        modifier.clip(RoundedCornerShape(if (compact) 18.dp else 22.dp))
            .background(Brush.linearGradient(listOf(Color(d.top), Color(d.bottom)), start = Offset.Zero, end = Offset(900f, 900f)))
            .clickable(onClick = onClick).testTag(tag)
            // the small chip reads as one line, as it always did; the full tile keeps its parts (balance, status) separate for the accounts page
            .then(if (compact) Modifier.clearAndSetSemantics { text = AnnotatedString(label) } else Modifier),
    ) {
        // a soft shine and, on a premium card, a thin gold edge: all drawn, nothing borrowed
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.55f, center = Offset(size.width * 0.95f, -size.height * 0.15f))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.4f, center = Offset(size.width * 0.1f, size.height * 1.15f))
            if (a.tier == CardTier.PREMIUM.name && isCard) drawRoundRect(accent.copy(alpha = 0.55f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (compact) 18.dp.toPx() else 22.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Text(a.name, color = ink, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val kindLine = if (isCard) listOfNotNull(network?.takeIf { it != CardNetwork.OTHER }?.label, a.tier?.let { t -> CardTier.entries.firstOrNull { it.name == t }?.label?.takeIf { _ -> !compact } }).joinToString(" · ").ifEmpty { "Credit card" } else a.typeLabel
                // the small tile keeps to the name; the network mark below says the rest
                if (!compact) Text(kindLine, color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
            Column {
                if (a.last4 != null) Text("••••  ${a.last4}", color = ink.copy(alpha = 0.85f), style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium).copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp))
                if (!compact) a.card?.statusText?.takeIf { it.isNotEmpty() }?.let { Text(it, color = ink.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.testTag("card_status_${a.name}")) }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (a.owed) Text("owed", color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    Text(a.balanceText, color = ink, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = if (compact) Modifier else Modifier.testTag("balance_${a.name}"))
                }
                if (isCard && network != null) NetworkMark(network, accent, ink, compact)
            }
        }
    }
}

/** A network's mark, drawn as plain shapes or its name in type. These imitate no logo: they say which network the card is on. */
@Composable
private fun NetworkMark(n: CardNetwork, accent: Color, ink: Color, compact: Boolean) {
    val r = if (compact) 11.dp else 15.dp
    when (n) {
        CardNetwork.MASTERCARD -> Box(Modifier.width(r * 3).height(r * 2)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(accent.copy(alpha = 0.9f), radius = r.toPx(), center = Offset(r.toPx(), size.height / 2))
                drawCircle(ink.copy(alpha = 0.6f), radius = r.toPx(), center = Offset(r.toPx() * 2, size.height / 2))
            }
        }
        CardNetwork.VISA -> Text("VISA", color = ink, fontWeight = FontWeight.Black, fontSize = if (compact) 16.sp else 22.sp, style = TextStyle(fontStyle = FontStyle.Italic))
        CardNetwork.RUPAY -> Text("RuPay", color = ink, fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 19.sp)
        CardNetwork.AMEX -> Text("AMEX", color = ink, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 17.sp, style = TextStyle(letterSpacing = 1.sp))
        CardNetwork.OTHER -> Box(Modifier.size(0.dp))
    }
}
