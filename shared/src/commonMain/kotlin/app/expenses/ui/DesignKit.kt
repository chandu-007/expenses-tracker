package app.expenses.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.PaceLevel
import app.expenses.core.domain.Signals

/*
 * "Expenses, calm and clear": the shared pieces of the redesign. Screens use these and the tokens (Space, Radius, the theme's roles); they do not draw their own pill, button or card.
 * Every text goes through the shared masking [Text]; amounts are given as the text the screen already has (exact: [WholeText] draws it whole and keeps the exact words for a screen reader).
 */

/** What a status says: [GOOD] on pace (the accent), [WATCH] a little ahead or something coming (amber), [OVER] over a limit (red), [NEUTRAL] only information. Never colour alone: a pill always carries words. */
enum class Tone { GOOD, WATCH, OVER, NEUTRAL }

/** The design's "watch" colour for the theme in use (#A34A06 on light, #F2A65A on dark). */
@Composable
fun watchColor(): Color = Color(0xFF000000.toInt() or Signals.watch(isDarkTheme()))

/** The colour that carries [tone]: the accent, the watch colour, the over colour or the muted text colour. */
@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.GOOD -> MaterialTheme.colorScheme.primary
    Tone.WATCH -> watchColor()
    Tone.OVER -> MaterialTheme.colorScheme.error
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The soft tint a pill of [tone] sits on: accent-soft, a thin amber, a thin red, or a grey. */
@Composable
fun toneSoft(tone: Tone): Color = when (tone) {
    Tone.GOOD -> MaterialTheme.colorScheme.primaryContainer
    Tone.WATCH -> Color(0xFF000000.toInt() or Signals.soft(Signals.watch(isDarkTheme()), isDarkTheme()))
    Tone.OVER -> MaterialTheme.colorScheme.errorContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
}

/** The tone of a pace level: on pace (and "just started") is good, a little ahead or ahead is watch, over is over. */
fun paceTone(level: PaceLevel): Tone = when (level) {
    PaceLevel.EARLY, PaceLevel.ON_PACE -> Tone.GOOD
    PaceLevel.A_LITTLE_AHEAD, PaceLevel.AHEAD -> Tone.WATCH
    PaceLevel.OVER -> Tone.OVER
}

/**
 * A status pill: "On pace" with a dot, "A little ahead", "Over by ₹649" (the words say it, the colour repeats it). One line, never wider than its words; the text shrinks nothing and wraps at large text.
 */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier, dot: Boolean = tone == Tone.GOOD) {
    val ink = toneColor(tone)
    Row(
        modifier.clip(Radius.Full).background(toneSoft(tone)).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).background(ink, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2)
    }
}

/** A white card on the grey ground with a hairline: [hero] cards are 24 dp (Radius.L), rows and groups 20 dp (Radius.M). The caller adds its own padding. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, hero: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = if (hero) Radius.L else Radius.M
    val base = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow, shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
    Column(if (onClick != null) base.clickable(onClick = onClick) else base, content = content)
}

/** The button shapes of the design: 56 dp high, 20 dp corners, the label in the button text style. [PrimaryButton] is the accent, [SecondaryButton] white with a hairline, [InkButton] near-black (the "Add all" bar). */
@Composable
private fun DesignButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, container: Color, content: Color, border: BorderStroke?, leading: (@Composable RowScope.() -> Unit)?) {
    val shape: Shape = Radius.M
    val fill = if (enabled) container else container.copy(alpha = 0.38f)
    Row(
        modifier.heightIn(min = 56.dp).clip(shape).background(fill, shape).then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) content else content.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable RowScope.() -> Unit)? = null) =
    DesignButton(text, onClick, modifier, enabled, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, null, leading)

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable RowScope.() -> Unit)? = null) =
    DesignButton(text, onClick, modifier, enabled, MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurface, BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), leading)

@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable RowScope.() -> Unit)? = null) =
    DesignButton(text, onClick, modifier, enabled, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.background, null, leading)

/**
 * A design chip: a full pill, white with a hairline; on, an accent-soft fill, an accent outline and accent text (and a check mark, so on and off differ by more than colour). [trailing] is for a
 * drop-down arrow ("Bank ▾"). 48 dp high to touch.
 */
@Composable
fun DesignChip(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    tone: Tone = Tone.GOOD,
) {
    val on = toneColor(tone)
    val fill = if (selected) toneSoft(tone) else MaterialTheme.colorScheme.surfaceContainerLow
    val line = if (selected) on else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.heightIn(min = 48.dp).clip(Radius.Full).background(fill, Radius.Full).border(BorderStroke(1.dp, line), Radius.Full)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (selected) on else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/**
 * The pace bar of the design: fill = share spent ([fraction], 0 to 1), tick = share of the period gone ([elapsed], 0 to 1; negative: no tick). A fill short of the tick reads as on pace. Drawn in one
 * pass (nothing recomposes while it draws); the track is the theme's variant surface, the fill the colour of [tone].
 */
@Composable
fun PaceBar(fraction: Float, elapsed: Float, tone: Tone, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 8.dp) {
    val fill = toneColor(tone)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurface
    Box(
        modifier.fillMaxWidth().height(height).drawBehind {
            val r = size.height / 2f
            drawRoundRect(track, size = size, cornerRadius = CornerRadius(r, r))
            val w = size.width * fraction.coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(fill, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = CornerRadius(r, r))
            if (elapsed >= 0f) {
                val x = size.width * elapsed.coerceIn(0f, 1f)
                drawLine(tick.copy(alpha = 0.85f), Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        },
    )
}

/**
 * An entry row of the design: a 40 dp tile, the title and a quiet second line, the amount at the end. [amount] is drawn whole and spoken exact ([WholeText]); [income] draws it in the accent with a
 * "+". The row is one touch target. It is flat: put rows in a [SoftCard] (a day's entries are one card).
 */
@Composable
fun DesignEntryRow(
    title: String, meta: String, amount: String, tile: @Composable () -> Unit, modifier: Modifier = Modifier, income: Boolean = false, onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    val base = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(base.padding(padding).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        tile()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        WholeText(
            if (income && !amount.startsWith("+")) "+$amount" else amount, style = NumberStyle.Row, maxLines = 1,
            color = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
