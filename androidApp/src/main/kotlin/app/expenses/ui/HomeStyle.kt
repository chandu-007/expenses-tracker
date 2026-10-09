package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** The hero card's background: the accent, fading to a slightly deeper (or, in dark mode where the accent is light, lighter) tone of itself, so the text on it keeps its contrast at both ends. */
@Composable
fun Modifier.heroBackground(): Modifier {
    val p = MaterialTheme.colorScheme.primary
    val second = lerp(p, if (p.luminance() > 0.5f) Color.White else Color.Black, 0.28f)
    val onP = MaterialTheme.colorScheme.onPrimary
    return this.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(p, second)))
        // a soft light in the top corner and a fine edge: the look of a glossy pane, drawn once, with no blur and no cost while scrolling
        .drawBehind { drawCircle(Brush.radialGradient(listOf(onP.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.88f, 0f), radius = size.width * 0.6f), radius = size.width * 0.6f, center = Offset(size.width * 0.88f, 0f)) }
        .border(1.dp, onP.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
}

/** "Today", "Yesterday", or "Mon, 5 Oct" for a local day given as an epoch-day number. */
fun dayLabel(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return when (epochDay) {
        today.toEpochDay() -> "Today"
        today.toEpochDay() - 1 -> "Yesterday"
        else -> java.time.format.DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy").format(d)
    }
}

/** A rounded tinted circle with the category's initial; the tint is the same for the same name every time (a small fixed palette, all pale enough for dark text). */
@Composable
fun CategoryAvatar(name: String, fallback: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val label = name.ifBlank { fallback }
    val tint = AVATAR_TINTS[Math.floorMod(label.lowercase().hashCode(), AVATAR_TINTS.size)]
    Box(modifier.size(size).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Text(label.trim().take(1).uppercase(), color = Color(0xFF1B1B1F), fontWeight = FontWeight.SemiBold)
    }
}

private val AVATAR_TINTS = listOf(Color(0xFFBFE3D0), Color(0xFFC9DAF8), Color(0xFFF4D9B5), Color(0xFFE5CFF0), Color(0xFFF6C9CF), Color(0xFFD7E6A8), Color(0xFFB9E2E8), Color(0xFFE8DDB5))

@Composable
fun DayHeader(epochDay: Long) {
    Text(dayLabel(epochDay), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

/** A short rise-and-fade when a screen opens (not for the home list, whose [key] is negative). Respects the phone's remove-animations setting. */
@Composable
fun ScreenEnter(key: Int, content: @Composable () -> Unit) {
    if (key < 0) { content(); return }
    key(key) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 220)) }
        Box(Modifier.graphicsLayer { val p = progress.value; alpha = 0.35f + 0.65f * p; translationY = (1f - p) * 24.dp.toPx() }) { content() }
    }
}
