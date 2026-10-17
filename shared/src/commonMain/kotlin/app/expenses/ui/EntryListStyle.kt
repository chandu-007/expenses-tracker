package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/** Where a row sits in its card: entries are drawn as cards (one per day on the home list), so only the first row has round top corners and the last round bottom ones. */
enum class CardPos { ONLY, FIRST, MIDDLE, LAST;
    val top get() = this == ONLY || this == FIRST
    val bottom get() = this == ONLY || this == LAST
    companion object { fun of(first: Boolean, last: Boolean) = when { first && last -> ONLY; first -> FIRST; last -> LAST; else -> MIDDLE } }
}

/** The outside of an entry card: a margin and the rounded corners that clip the row (and a swipe's background behind it). */
fun Modifier.entryCardClip(pos: CardPos): Modifier {
    val r = 22.dp
    return this.padding(horizontal = 16.dp).clip(RoundedCornerShape(topStart = if (pos.top) r else 0.dp, topEnd = if (pos.top) r else 0.dp, bottomStart = if (pos.bottom) r else 0.dp, bottomEnd = if (pos.bottom) r else 0.dp))
}

/** The inside of an entry card: a soft tint of the theme (opaque, so a swipe's red cannot show through) and a fine line under every row but the last, starting after the avatar. */
@Composable
fun Modifier.entryCardFill(pos: CardPos, selected: Boolean = false): Modifier {
    val fill = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f).compositeOver(MaterialTheme.colorScheme.surface)
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    return this.background(fill).drawBehind {
        if (!pos.bottom) drawLine(line, Offset(70.dp.toPx(), size.height - 0.5f), Offset(size.width - 16.dp.toPx(), size.height - 0.5f), strokeWidth = 1.dp.toPx())
    }
}
