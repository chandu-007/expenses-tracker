package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One choice of a [SegmentedChoice]: the value it stands for, its words and the test tag of its segment. */
class Segment<T>(val value: T, val label: String, val tag: String)

/** What a segmented control does when its labels do not fit one line at the text size in use: [STACK] puts the segments one under another, [SCROLL] lets the track slide sideways (for a control inside a bar that must stay one line high). */
enum class WhenCrowded { STACK, SCROLL }

/**
 * A real segmented control (UI S1): one track with the segments in it and the chosen one filled. It replaces rows of filter chips where the choice is exactly one of a few (Theme, List spacing, Motion, the Reports
 * modes and groupings, an account's period). A radio group to a screen reader ("selected, radio button, 1 of 3": each segment keeps its own [Segment.tag] and `selected`), 48 dp high, and at 200% text it never cuts a word:
 * when the widest word of a label (measured, in bold, at the text size in use) does not fit its share of the width the segments [crowded] (stack or scroll).
 */
@Composable
fun <T> SegmentedChoice(
    segments: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    crowded: WhenCrowded = WhenCrowded.STACK,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    // the widest single word, in the bold the chosen segment is drawn in, at the text size in use: a word is never cut, so when it does not fit its share the segments are crowded
    val widestWord = remember(segments.map { it.label }, labelStyle, density) {
        with(density) { segments.maxOf { s -> s.label.split(' ').maxOf { w -> measurer.measure(w, labelStyle).size.width } }.toDp() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val share = (maxWidth - 6.dp - 2.dp * (segments.size - 1)) / segments.size
        val fits = share >= widestWord + 16.dp
        @Composable
        fun seg(s: Segment<T>, m: Modifier) {
            val on = s.value == selected
            Box(
                m.clip(if (fits) Radius.Full else Radius.S)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, if (fits) Radius.Full else Radius.S) else Modifier)
                    .selectable(selected = on, onClick = { onSelect(s.value) }, role = Role.RadioButton)
                    .testTag(s.tag),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        when {
            fits -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup().clip(Radius.Full).background(track).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                segments.forEach { s -> seg(s, Modifier.weight(1f).fillMaxHeight().heightIn(min = 42.dp)) }
            }
            crowded == WhenCrowded.SCROLL -> Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().clip(Radius.M).background(track).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                segments.forEach { s -> seg(s, Modifier.heightIn(min = 42.dp).widthIn(min = 48.dp)) }
            }
            else -> {
                // as many to a row as keep their widest word whole (a 2 by 2 grid for four labels at large text), one to a row when not even two do
                val perRow = (segments.size - 1 downTo 2).firstOrNull { k -> (maxWidth - 6.dp - 2.dp * (k - 1)) / k >= widestWord + 16.dp } ?: 1
                Column(Modifier.fillMaxWidth().selectableGroup().clip(Radius.M).background(track).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    segments.chunked(perRow).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            rowOf.forEach { s -> seg(s, Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) }
                        }
                    }
                }
            }
        }
    }
}
