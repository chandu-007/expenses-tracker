package app.expenses.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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
fun AccountTile(a: AccountRowUi, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, stripAmount: Float = 0f) {
    val d = designOf(a)
    val ink = Color(d.text)
    val accent = Color(d.accent)
    val network = a.network?.let { n -> CardNetwork.entries.firstOrNull { it.name == n } }
    val isCard = a.type == "CREDIT_CARD"
    val pad = if (compact) 14.dp else 20.dp
    val tag = if (compact) "balance_chip_${a.name}" else "account_${a.name}"
    // what the small chip reads out and what the tests read: masked like every other amount while Hide amounts is on
    val label = privately(a.name + (if (a.owed) " · owed" else "") + " " + a.balanceText)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "tilePress")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(if (compact) 18.dp else 22.dp))
            .background(Brush.linearGradient(listOf(Color(d.top), Color(d.bottom)), start = Offset.Zero, end = Offset(900f, 900f)))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick).testTag(tag)
            // the small chip reads as one line, as it always did; the full tile keeps its parts (balance, status) separate for the accounts page
            .then(if (compact) Modifier.clearAndSetSemantics { text = AnnotatedString(label) } else Modifier),
    ) {
        // a soft shine and, on a premium card, a thin gold edge: all drawn, nothing borrowed
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.55f, center = Offset(size.width * 0.95f, -size.height * 0.15f))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.4f, center = Offset(size.width * 0.1f, size.height * 1.15f))
            if (a.tier == CardTier.PREMIUM.name && isCard) drawRoundRect(accent.copy(alpha = 0.55f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (compact) 18.dp.toPx() else 22.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()))
        }
        // in a stack, the edge that shows is a line of its own: the name, the last digits and the balance ([stripAmount] 0 to 1 is how much of the card is still folded under the next)
        if (stripAmount > 0f) Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 18.dp).graphicsLayer { alpha = stripAmount }, verticalAlignment = Alignment.CenterVertically) {
            Text(a.name + (a.last4?.let { "  ••$it" } ?: ""), color = ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text((if (a.owed) "owed " else "") + a.balanceText, color = ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.fillMaxSize().padding(pad).graphicsLayer { alpha = 1f - stripAmount }, verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Text(a.name, color = ink, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val kindLine = if (isCard) listOfNotNull(network?.takeIf { it != CardNetwork.OTHER }?.label, a.tier?.let { t -> CardTier.entries.firstOrNull { it.name == t }?.label?.takeIf { _ -> !compact } }).joinToString(" · ").ifEmpty { "Credit card" } else a.typeLabel
                // the small tile keeps to the name; the network mark below says the rest
                if (!compact) Text(kindLine, color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
            Column {
                if (a.last4 != null) Text("••••  ${a.last4}", color = ink.copy(alpha = 0.85f), style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium).copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp))
                if (!compact) a.card?.statusText?.takeIf { it.isNotEmpty() }?.let { Text(it, color = ink.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.testTag("card_status_${a.name}")) }
                if (!compact && a.hasCardSummary) a.card?.let { CardSummaryLines(it, Triple("tile_cycle_spend_${a.name}", "tile_limit_use_${a.name}", "tile_due_line_${a.name}"), ink.copy(alpha = 0.9f), Modifier.padding(top = 6.dp)) }
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


/**
 * The accounts as a stack of cards, like a wallet: each card shows only its top edge (name and kind) over the one before, and the last lies fully in front.
 * Tap the stack and the cards slide apart on a spring so each is whole; tap a card then to open it, or "Stack them" (or Back) to put them together again.
 * One account is simply its card. Every card keeps its tag and its parts, so nothing about a card changes: only how they are laid out.
 */
@Composable
fun WalletStack(accounts: List<AccountRowUi>, onOpen: (AccountRowUi) -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    var expanded by rememberSaveable { mutableStateOf(accounts.size <= 1) }
    val open = remember { androidx.compose.animation.core.Animatable(if (expanded) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(expanded, reduce) {
        if (reduce) open.snapTo(if (expanded) 1f else 0f) else open.animateTo(if (expanded) 1f else 0f, Motion.spring(dampingRatio = 0.78f, stiffness = 300f))
    }
    BackHandler(enabled = expanded && accounts.size > 1) { expanded = false }
    val cardH = 168.dp
    val gap = 12.dp
    val n = accounts.size
    // a credit card with its summary is taller than the others; the stack and the spread lay each card out by its own height
    val heights = accounts.map { a -> cardH + (if (a.hasCardSummary) a.card?.let(::cardSummaryExtra) ?: 0.dp else 0.dp) }
    // the edge of each card that shows while stacked: less when there are many, so the stack never grows tall
    // each card's edge is a whole line (name, digits, balance), however many cards there are: the stack grows with them instead of squeezing the lines out
    val peek = if (n <= 1) 0.dp else 46.dp
    val stackedH = (heights.lastOrNull() ?: cardH) + peek * (n - 1)
    val spreadH = heights.fold(0.dp) { sum, h -> sum + h + gap } - gap
    val density = androidx.compose.ui.platform.LocalDensity.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("accounts_stack")) {
        androidx.compose.animation.AnimatedVisibility(expanded && n > 1) {
            androidx.compose.material3.TextButton(onClick = { expanded = false }, modifier = Modifier.align(Alignment.End).testTag("accounts_stack_close")) { Text("Stack them") }
        }
        val shown = with(density) { (stackedH.toPx() + (spreadH.toPx() - stackedH.toPx()) * open.value).toDp() }
        Box(Modifier.fillMaxWidth().height(shown)) {
            accounts.forEachIndexed { i, a ->
                val spreadY = heights.take(i).fold(0.dp) { sum, h -> sum + h + gap }
                val y = with(density) { (peek.toPx() * i + (spreadY.toPx() - peek.toPx() * i) * open.value).toDp() }
                AccountTile(
                    a, compact = false, onClick = { if (expanded) onOpen(a) else expanded = true },
                    stripAmount = if (i == n - 1) 0f else (1f - open.value).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(heights[i]).offset(y = y).shadow(if (i == 0) 0.dp else 8.dp, RoundedCornerShape(22.dp), clip = false),
                )
            }
        }
    }
}
